package com.meada.profiles.fotografia.appointments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai a tag {@code <entrega_material>{...}</entrega_material>} da resposta da IA e ENTREGA o
 * {@code delivery_link} da sessão (camada 8.16) — padrão de ENTREGA READ-ONLY (espelho do
 * EntregaPreparoHandler do dermatologia / EntregaPlanoHandler do nutri).
 *
 * <p>Diferente dos confirm handlers que CRIAM ou MUTAM: aqui a IA NUNCA gera o conteúdo. O texto
 * entregue é o {@code delivery_link} da PRÓPRIA SESSÃO (gravado pelo estúdio no painel DEPOIS da
 * sessão), enviado VERBATIM ao cliente — sem reescrita, sem geração pela IA. A tag só referencia qual
 * sessão; o backend lê o link dela e dispara o envio. (Diferença do dermatologia: lá o texto vinha do
 * procedure_type; aqui o link mora NA PRÓPRIA SESSÃO.)
 *
 * <p>BARREIRA DE SEGURANÇA: o link só é entregue se o {@code contactId} da sessão coincidir com o
 * contato DA PRÓPRIA CONVERSA. Isso impede que a IA, induzida por um session_id de outra pessoa, vaze
 * o material para o contato errado. Sem link (vazio) → {@code false}. Qualquer falha → {@code false} +
 * warn.
 */
@Component
public class EntregaMaterialHandler {

    private static final Logger log = LoggerFactory.getLogger(EntregaMaterialHandler.class);

    private static final Pattern TAG = Pattern.compile("<entrega_material>\\s*(\\{.*?\\})\\s*</entrega_material>",
        Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final FotografiaAppointmentRepository appointmentRepository;
    private final FotografiaAppointmentNotifier notifier;

    public EntregaMaterialHandler(ObjectMapper objectMapper, FotografiaAppointmentRepository appointmentRepository,
                                  FotografiaAppointmentNotifier notifier) {
        this.objectMapper = objectMapper;
        this.appointmentRepository = appointmentRepository;
        this.notifier = notifier;
    }

    public boolean hasTag(String text) {
        return text != null && TAG.matcher(text).find();
    }

    public String stripTag(String text) {
        if (text == null) {
            return null;
        }
        return TAG.matcher(text).replaceAll("").stripTrailing();
    }

    /**
     * Extrai a tag e entrega o delivery_link da sessão. Devolve {@code true} se entregou. {@code false}
     * quando: não há tag, JSON inválido, session_id faltando/inválido, sessão inexistente, sessão de
     * OUTRO contato (barreira de segurança), sessão SEM link, ou o envio falha. O texto é o
     * delivery_link VERBATIM — nunca passa por geração da IA.
     */
    public boolean parseAndDeliver(UUID companyId, UUID conversationId, UUID contactId, String aiResponseText) {
        if (aiResponseText == null) {
            return false;
        }
        Matcher m = TAG.matcher(aiResponseText);
        if (!m.find()) {
            return false;
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(m.group(1));
        } catch (Exception e) {
            log.warn("fotografia: tag <entrega_material> com JSON inválido p/ conversa {} ({}) — não entregue",
                conversationId, e.getMessage());
            return false;
        }

        String rawSession = root.path("session_id").asText(null);
        if (rawSession == null || rawSession.isBlank()) {
            log.warn("fotografia: tag <entrega_material> sem session_id p/ conversa {} — não entregue", conversationId);
            return false;
        }
        UUID sessionId;
        try {
            sessionId = UUID.fromString(rawSession);
        } catch (RuntimeException e) {
            log.warn("fotografia: <entrega_material> com session_id inválido p/ conversa {} — não entregue", conversationId);
            return false;
        }

        // Best-effort DE VERDADE: lookups/envio dentro do catch-all — uma RuntimeException
        // transitória (banco, rede) NÃO pode derrubar o envio da resposta da IA ao cliente.
        try {
            Optional<FotografiaSessionAppointment> session = appointmentRepository.findDeliverable(companyId, sessionId);
            if (session.isEmpty()) {
                log.warn("fotografia: <entrega_material> referencia sessão inexistente {} p/ conversa {} — não entregue",
                    sessionId, conversationId);
                return false;
            }

            // BARREIRA DE SEGURANÇA: o material só sai para o contato dono da sessão.
            if (!Objects.equals(session.get().contactId(), contactId)) {
                log.warn("fotografia: <entrega_material> de sessão de outro contato (sessão {} contato {} ≠ conversa {}) — bloqueado, não entregue",
                    sessionId, session.get().contactId(), contactId);
                return false;
            }

            String link = session.get().deliveryLink();
            if (link == null || link.isBlank()) {
                log.warn("fotografia: <entrega_material> sessão {} sem delivery_link p/ conversa {} — não entregue",
                    sessionId, conversationId);
                return false;
            }

            if (notifier.sendText(companyId, conversationId, link)) {
                log.info("fotografia: material da sessão {} entregue VERBATIM p/ conversa {}", sessionId, conversationId);
                return true;
            }
            log.warn("fotografia: <entrega_material> falhou ao enviar o link p/ conversa {} (sessão {}) — não entregue",
                conversationId, sessionId);
            return false;
        } catch (RuntimeException e) {
            log.warn("fotografia: <entrega_material> falhou inesperadamente p/ conversa {} ({}) — não entregue (best-effort)",
                conversationId, e.getMessage());
            return false;
        }
    }
}
