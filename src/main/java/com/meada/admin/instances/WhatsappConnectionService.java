package com.meada.admin.instances;

import com.meada.admin.instances.EvolutionInstanceApi.CreatedInstance;
import com.meada.admin.instances.EvolutionInstanceApi.InstanceState;
import com.meada.admin.instances.WhatsappInstanceAdminRepository.InstanceRow;
import com.meada.common.audit.AuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Conexão do WhatsApp do TENANT (o número que os clientes dele usam para falar com a IA).
 *
 * <p>O contrato central: <b>o número não é digitado, é PAREADO</b>. O tenant clica em conectar, o
 * backend provisiona a instância na Evolution, o painel mostra o QR, o tenant escaneia com o celular
 * do número dele — e só então o número existe (a Evolution devolve o {@code ownerJid}, que vira
 * {@code whatsapp_instances.phone_number}). Um campo de texto "digite seu número" seria uma mentira:
 * salvaria o número e nada funcionaria.
 *
 * <p>A chave de roteamento multi-tenant do inbound continua sendo o {@code instance_name}
 * ({@code WebhookService}), não o número.
 *
 * <p><b>Segurança (RISKS.md, incidente 2026-06-10):</b> toda instância nasce com
 * {@code syncFullHistory=false} ({@link EvolutionInstanceClient#applySafetySettings}) — sem isso, o
 * pareamento re-emite o histórico do Baileys como mensagens novas e a IA responde a contatos reais.
 *
 * <p><b>Desconectar ≠ apagar:</b> a linha em {@code whatsapp_instances} permanece (FK
 * {@code on delete restrict} das conversas). Desconectar faz logout na Evolution e marca
 * {@code status='disconnected'}; o histórico fica íntegro e reconectar reusa a mesma instância.
 */
@Service
public class WhatsappConnectionService {

    private static final Logger log = LoggerFactory.getLogger(WhatsappConnectionService.class);

    /** A conexão pelo painel não está configurada no servidor (sem API key global da Evolution). */
    public static class WhatsappUnavailableException extends RuntimeException {}

    /** Já existe um número conectado — desconecte antes de parear outro. */
    public static class AlreadyConnectedException extends RuntimeException {}

    /** O instance_name já existe na Evolution mas não pertence a este tenant (resíduo/colisão). */
    public static class InstanceNameTakenException extends RuntimeException {}

    private final EvolutionInstanceApi evolution;
    private final WhatsappInstanceAdminRepository repository;
    private final AuditLogger auditLogger;
    private final String webhookUrl;
    private final String webhookSecret;

    public WhatsappConnectionService(EvolutionInstanceApi evolution,
                                     WhatsappInstanceAdminRepository repository,
                                     AuditLogger auditLogger,
                                     @Value("${evolution.webhook-url:}") String webhookUrl,
                                     @Value("${webhook.secret}") String webhookSecret) {
        this.evolution = evolution;
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.webhookUrl = webhookUrl == null ? "" : webhookUrl.trim();
        this.webhookSecret = webhookSecret;
    }

    /**
     * Estado atual, SINCRONIZADO com a Evolution (a fonte da verdade do pareamento é ela, não o
     * nosso banco — o tenant pode desconectar pelo celular a qualquer momento). O status e o número
     * lidos são materializados de volta em {@code whatsapp_instances}.
     */
    @Transactional
    public WhatsappConnection status(UUID companyId) {
        if (!evolution.isAvailable()) {
            return WhatsappConnection.unavailable();
        }
        Optional<InstanceRow> row = repository.findByCompany(companyId);
        if (row.isEmpty()) {
            return WhatsappConnection.notConfigured();
        }
        InstanceRow instance = row.get();
        Optional<InstanceState> remote = safeFetchState(instance.instanceName());
        if (remote.isEmpty()) {
            // A instância sumiu da Evolution (resíduo do nosso lado). Reconectar recria.
            return new WhatsappConnection(true, WhatsappConnection.DISCONNECTED,
                instance.phoneNumber(), null, instance.instanceName());
        }
        return syncFromRemote(instance, remote.get());
    }

    /**
     * Inicia (ou retoma) o pareamento e devolve o QR para o painel exibir.
     *
     * <p>Idempotente: se a instância já existe e está aguardando pareamento, apenas re-obtém o QR
     * (a Evolution rotaciona o código). Se já está CONECTADA → 409 (desconecte antes de trocar de
     * número). Se sumiu da Evolution, recria e atualiza o token.
     */
    @Transactional
    public String connect(UUID companyId, UUID userId) {
        if (!evolution.isAvailable()) {
            throw new WhatsappUnavailableException();
        }
        Optional<InstanceRow> existing = repository.findByCompany(companyId);

        if (existing.isPresent()) {
            InstanceRow instance = existing.get();
            Optional<InstanceState> remote = safeFetchState(instance.instanceName());
            if (remote.isPresent()) {
                if (EvolutionInstanceApi.STATE_OPEN.equals(remote.get().state())) {
                    throw new AlreadyConnectedException();
                }
                // Existe e está aguardando pareamento → só re-emite o QR (com os guards reaplicados).
                hardenAndWire(instance.instanceName());
                String qr = evolution.fetchQrCode(instance.instanceName())
                    .orElseThrow(() -> new EvolutionInstanceException("Evolution não devolveu QR"));
                repository.updateStatusAndNumber(instance.id(), WhatsappConnection.CONNECTING, null);
                return qr;
            }
            // Sumiu da Evolution → recria com o MESMO nome e atualiza o token.
            CreatedInstance created = createOnEvolution(instance.instanceName());
            repository.updateToken(instance.id(), created.token());
            repository.updateStatusAndNumber(instance.id(), WhatsappConnection.CONNECTING, null);
            audit(companyId, userId, "whatsapp_instance_recreated", instance.instanceName());
            return created.qrCodeBase64();
        }

        // Primeira conexão do tenant: provisiona do zero.
        String instanceName = repository.findCompanySlug(companyId)
            .orElseThrow(() -> new IllegalStateException("empresa sem slug: " + companyId));
        CreatedInstance created = createOnEvolution(instanceName);
        UUID id = repository.insert(companyId, instanceName, created.token());
        audit(companyId, userId, "whatsapp_instance_created", instanceName);
        log.info("whatsapp: instância provisionada company={} instance={} id={}", companyId, instanceName, id);
        return created.qrCodeBase64();
    }

    /** Encerra a sessão do WhatsApp. A instância e o histórico permanecem. */
    @Transactional
    public void disconnect(UUID companyId, UUID userId) {
        if (!evolution.isAvailable()) {
            throw new WhatsappUnavailableException();
        }
        InstanceRow instance = repository.findByCompany(companyId)
            .orElseThrow(() -> new IllegalStateException("tenant sem instância: " + companyId));
        evolution.logout(instance.instanceName());
        repository.updateStatusAndNumber(instance.id(), WhatsappConnection.DISCONNECTED, null);
        audit(companyId, userId, "whatsapp_instance_disconnected", instance.instanceName());
        log.info("whatsapp: instância desconectada company={} instance={}", companyId, instance.instanceName());
    }

    // ---- internos ------------------------------------------------------------

    private CreatedInstance createOnEvolution(String instanceName) {
        CreatedInstance created;
        try {
            created = evolution.createInstance(instanceName);
        } catch (EvolutionInstanceException e) {
            // 400 = nome já existe na Evolution (resíduo de outro ambiente ou de um purge incompleto).
            // NÃO adotamos a instância existente às cegas — poderia ser de outro tenant.
            if (e.evolutionStatus() == 400) {
                throw new InstanceNameTakenException();
            }
            throw e;
        }
        hardenAndWire(instanceName);
        return created;
    }

    /** Aplica o guard do histórico e aponta o webhook. Idempotente. */
    private void hardenAndWire(String instanceName) {
        evolution.applySafetySettings(instanceName);   // syncFullHistory=false — GUARD do incidente
        if (webhookUrl.isBlank()) {
            log.warn("whatsapp: evolution.webhook-url vazio — webhook da instância {} NÃO foi apontado "
                + "(mensagens recebidas não chegarão ao Meada)", instanceName);
            return;
        }
        evolution.setWebhook(instanceName, webhookUrl, webhookSecret);
    }

    /** Traduz o estado da Evolution para o nosso e materializa no banco. */
    private WhatsappConnection syncFromRemote(InstanceRow instance, InstanceState remote) {
        String status = switch (remote.state() == null ? "" : remote.state()) {
            case EvolutionInstanceApi.STATE_OPEN -> WhatsappConnection.CONNECTED;
            case EvolutionInstanceApi.STATE_CONNECTING -> WhatsappConnection.CONNECTING;
            default -> WhatsappConnection.DISCONNECTED;
        };
        String phone = toE164(remote.ownerJid());
        repository.updateStatusAndNumber(instance.id(), status, phone);
        return new WhatsappConnection(true, status,
            phone != null ? phone : instance.phoneNumber(),
            remote.profileName(), instance.instanceName());
    }

    /**
     * {@code ownerJid} ("5511999999999@s.whatsapp.net" ou com sufixo :N de multi-device) → E.164.
     * null enquanto não houver pareamento.
     */
    static String toE164(String ownerJid) {
        if (ownerJid == null || ownerJid.isBlank()) {
            return null;
        }
        String digits = ownerJid.split("@", 2)[0].split(":", 2)[0].replaceAll("\\D", "");
        return digits.isBlank() ? null : "+" + digits;
    }

    /** Falha ao consultar a Evolution não pode derrubar a tela de status — degrada para "sumiu". */
    private Optional<InstanceState> safeFetchState(String instanceName) {
        try {
            return evolution.fetchState(instanceName);
        } catch (EvolutionInstanceException e) {
            log.warn("whatsapp: falha ao consultar estado da instância {} ({}) — tratando como indisponível",
                instanceName, e.getMessage());
            return Optional.empty();
        }
    }

    private void audit(UUID companyId, UUID userId, String action, String instanceName) {
        auditLogger.log(companyId, userId, action, "whatsapp_instances", companyId,
            Map.of("instance_name", instanceName));
    }
}
