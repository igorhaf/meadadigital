package com.meada.profiles.otica.appointments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai a tag {@code <confirmacao_exame>{...}</confirmacao_exame>} da resposta da IA e MUTA o
 * status da exame (onda Ótica 1, backlog #1 — fecha o loop do lembrete de véspera
 * do OticaReminderJob). A IA só REFLETE a decisão do CLIENTE: {@code decisao} confirmado (de
 * 'examedo') ou cancelado (de 'examedo'/'confirmado' — a máquina de status valida; cancelar
 * LIBERA o slot do optometrista; a notificação padrão dispara pelo service).
 *
 * <p>BARREIRA DE CONTATO: a exame tem de pertencer ao MESMO contato da conversa. Transição
 * inválida, id inexistente, decisão desconhecida ou contato divergente → {@link Optional#empty()}
 * + warn (a mensagem da IA segue sem mutação). O OutboundService remove a tag antes de enviar.
 * Clone do ConfirmacaoReservaHandler/ConfirmacaoPousadaHandler (migs 91/92).
 */
@Component
public class ConfirmacaoExameHandler {

    private static final Logger log = LoggerFactory.getLogger(ConfirmacaoExameHandler.class);

    private static final Pattern TAG = Pattern.compile(
        "<confirmacao_exame>\\s*(\\{.*?\\})\\s*</confirmacao_exame>", Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final OticaExamService appointmentService;

    public ConfirmacaoExameHandler(ObjectMapper objectMapper, OticaExamService appointmentService) {
        this.objectMapper = objectMapper;
        this.appointmentService = appointmentService;
    }

    public boolean hasConfirmacaoTag(String text) {
        return text != null && TAG.matcher(text).find();
    }

    public String stripConfirmacaoTag(String text) {
        if (text == null) {
            return null;
        }
        return TAG.matcher(text).replaceAll("").stripTrailing();
    }

    /**
     * Extrai a tag e aplica a decisão do cliente à exame. {@link Optional#empty()} quando: não há
     * tag, JSON/campos inválidos, exame inexistente, contato divergente (barreira) ou transição
     * inválida na máquina de status.
     */
    public Optional<OticaExamAppointment> parseAndApply(UUID companyId, UUID conversationId, UUID contactId,
                                               String aiResponseText) {
        if (aiResponseText == null) {
            return Optional.empty();
        }
        Matcher m = TAG.matcher(aiResponseText);
        if (!m.find()) {
            return Optional.empty();
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(m.group(1));
        } catch (Exception e) {
            log.warn("otica: tag <confirmacao_exame> com JSON inválido p/ conversa {} ({})",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String rawId = root.path("exam_id").asText(null);
        String decisao = root.path("decisao").asText(null);
        if (rawId == null || (!"confirmado".equals(decisao) && !"cancelado".equals(decisao))) {
            log.warn("otica: tag <confirmacao_exame> com campos inválidos p/ conversa {}", conversationId);
            return Optional.empty();
        }
        UUID appointmentId;
        try {
            appointmentId = UUID.fromString(rawId);
        } catch (IllegalArgumentException e) {
            log.warn("otica: <confirmacao_exame> com exam_id inválido p/ conversa {}", conversationId);
            return Optional.empty();
        }

        Optional<OticaExamAppointment> current = appointmentService.get(companyId, appointmentId);
        if (current.isEmpty()) {
            log.warn("otica: <confirmacao_exame> p/ exame inexistente {} (conversa {})",
                appointmentId, conversationId);
            return Optional.empty();
        }
        // BARREIRA DE CONTATO: só o dono da exame confirma/cancela pela conversa.
        if (current.get().contactId() == null || !current.get().contactId().equals(contactId)) {
            log.warn("otica: <confirmacao_exame> de contato divergente p/ exame {} (conversa {}) — ignorada",
                appointmentId, conversationId);
            return Optional.empty();
        }

        try {
            OticaExamAppointment updated = appointmentService.updateStatus(companyId, appointmentId, decisao);
            log.info("otica: exame {} → {} pela resposta do cliente (conversa {})",
                appointmentId, decisao, conversationId);
            return Optional.of(updated);
        } catch (OticaExamService.InvalidStatusTransitionException e) {
            log.warn("otica: <confirmacao_exame> com transição inválida p/ exame {} (status atual não permite {})",
                appointmentId, decisao);
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("otica: falha ao aplicar <confirmacao_exame> p/ exame {} ({})",
                appointmentId, e.getMessage());
            return Optional.empty();
        }
    }
}
