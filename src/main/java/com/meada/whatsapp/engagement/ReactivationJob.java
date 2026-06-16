package com.meada.whatsapp.engagement;

import com.meada.whatsapp.messaging.AiSettingsRepository;
import com.meada.whatsapp.messaging.ContactRepository;
import com.meada.whatsapp.messaging.ConversationRepository;
import com.meada.whatsapp.messaging.EvolutionCredentials;
import com.meada.whatsapp.messaging.WhatsappInstanceRepository;
import com.meada.whatsapp.outbound.EvolutionSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Job de reativação automática (camada 5.21 #81). Diariamente (cron configurável), para cada
 * empresa com reativação configurada (ai_settings.reactivation_days + reactivation_message),
 * encontra os contatos que ficaram sem mensagem por {@code reactivation_days} dias ou mais e
 * ainda não foram reativados nesta janela, e envia a mensagem de reativação pela Evolution.
 *
 * <p>Disparo único por janela: {@code contacts.reactivated_at} é marcado após o envio (ou
 * tentativa) — um contato não recebe a reativação duas vezes na mesma janela de silêncio. Se o
 * contato voltar a falar e silenciar de novo, uma NOVA janela o torna elegível outra vez (a
 * query compara {@code reactivated_at < last_activity}).
 *
 * <p>Envio: resolve telefone (do contato) + credenciais (da instância da conversa mais recente
 * do contato) e usa o {@link EvolutionSender}. Quando o canal é irresolúvel (sem conversa, sem
 * credenciais), LOGAMOS e marcamos {@code reactivated_at} mesmo assim — evita revarredura eterna
 * da mesma linha (igual ao ReminderJob). EVOLUTION_DRY_RUN (em dev) é honrado pela própria
 * implementação do EvolutionSender (loga em vez de enviar).
 *
 * <p>{@code @Scheduled(cron)} com default diário às 9h; os testes NÃO dependem do scheduler —
 * chamam {@link #runReactivation()} direto. O job só age sobre empresas com config de reativação,
 * então não dispara efeitos em testes que não a semeiam.
 */
@Component
public class ReactivationJob {

    private static final Logger log = LoggerFactory.getLogger(ReactivationJob.class);

    private final AiSettingsRepository aiSettingsRepository;
    private final ReactivationRepository reactivationRepository;
    private final ConversationRepository conversationRepository;
    private final ContactRepository contactRepository;
    private final WhatsappInstanceRepository whatsappInstanceRepository;
    private final EvolutionSender evolutionSender;

    public ReactivationJob(AiSettingsRepository aiSettingsRepository,
                           ReactivationRepository reactivationRepository,
                           ConversationRepository conversationRepository,
                           ContactRepository contactRepository,
                           WhatsappInstanceRepository whatsappInstanceRepository,
                           EvolutionSender evolutionSender) {
        this.aiSettingsRepository = aiSettingsRepository;
        this.reactivationRepository = reactivationRepository;
        this.conversationRepository = conversationRepository;
        this.contactRepository = contactRepository;
        this.whatsappInstanceRepository = whatsappInstanceRepository;
        this.evolutionSender = evolutionSender;
    }

    /** Tick agendado (cron configurável; default diário às 9h). Delega ao método público
     *  {@link #runReactivation()} para os testes poderem rodar a lógica sem o scheduler. */
    @Scheduled(cron = "${engagement.reactivation-cron:0 0 9 * * *}")
    public void scheduledRun() {
        runReactivation();
    }

    /**
     * Varre todas as empresas com reativação configurada e reativa os contatos elegíveis.
     * Público e direto para os testes exercitarem a lógica sem depender do scheduler.
     *
     * @return número de contatos reativados (marcados) neste run — útil para asserts/observabilidade
     */
    public int runReactivation() {
        List<ReactivationConfig> configs = aiSettingsRepository.findReactivationConfigs();
        int total = 0;
        for (ReactivationConfig config : configs) {
            try {
                total += reactivateCompany(config);
            } catch (Exception e) {
                // Uma empresa problemática não pode derrubar o run inteiro.
                log.warn("reactivation: failed for company {} ({})",
                    config.companyId(), e.getMessage());
            }
        }
        return total;
    }

    /** Reativa os contatos due de UMA empresa. Retorna quantos foram marcados. */
    private int reactivateCompany(ReactivationConfig config) {
        List<DueContact> due = reactivationRepository.findDue(
            config.companyId(), config.reactivationDays());
        int marked = 0;
        for (DueContact contact : due) {
            try {
                sendAndMark(contact, config.reactivationMessage());
                marked++;
            } catch (Exception e) {
                // Um contato problemático não pode derrubar o processamento dos demais.
                log.warn("reactivation: failed to reactivate contact {} ({})",
                    contact.contactId(), e.getMessage());
            }
        }
        return marked;
    }

    /**
     * Resolve o canal (telefone + credenciais via a conversa mais recente do contato) e envia a
     * mensagem de reativação; depois marca {@code reactivated_at}. Canal irresolúvel (sem conversa
     * ou sem credenciais) → log + marca mesmo assim (evita revarredura eterna). Falha de envio →
     * log + marca (best-effort, igual ao ReminderJob).
     */
    private void sendAndMark(DueContact contact, String message) {
        UUID conversationId = contact.conversationId();
        Optional<EvolutionCredentials> creds = Optional.empty();
        if (conversationId != null) {
            Optional<UUID> instanceId =
                conversationRepository.findInstanceIdByConversation(conversationId);
            creds = instanceId.isPresent()
                ? whatsappInstanceRepository.findEvolutionCredentials(instanceId.get())
                : Optional.empty();
        }
        String phone = contact.phone();
        if (phone == null || phone.isBlank() || creds.isEmpty()) {
            log.info("reactivation: contact {} sem canal resolúvel (phone/creds) — marcado sem envio",
                contact.contactId());
            reactivationRepository.markReactivated(contact.contactId());
            return;
        }
        try {
            evolutionSender.sendText(
                creds.get().instanceName(), creds.get().token(), phone, message);
            log.info("reactivation: sent reactivation message to contact {}", contact.contactId());
        } catch (RuntimeException e) {
            // Falha de envio (transient/fatal): logamos e marcamos assim mesmo — best-effort,
            // não vale retentar indefinidamente o mesmo contato.
            log.warn("reactivation: send failed for contact {} ({}) — marking anyway",
                contact.contactId(), e.getMessage());
        }
        reactivationRepository.markReactivated(contact.contactId());
    }
}
