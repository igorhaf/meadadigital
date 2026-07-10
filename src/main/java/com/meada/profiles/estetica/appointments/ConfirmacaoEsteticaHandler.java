package com.meada.profiles.estetica.appointments;

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
 * Extrai a tag {@code <confirmacao_estetica>{"appointment_id":"...","decisao":"confirmado|cancelado"}
 * </confirmacao_estetica>} e aplica a decisão da cliente (onda Estética 1, backlog #1 — fecha o
 * loop do lembrete SIM/NÃO). O CANCELAMENTO devolve a sessão ao pacote pela mecânica já existente
 * do updateStatus. BARREIRA DE CONTATO: só a dona do agendamento decide pela conversa. Trava
 * intacta (confirmação operacional — a IA não opina nem recomenda). Best-effort.
 */
@Component
public class ConfirmacaoEsteticaHandler {

    private static final Logger log = LoggerFactory.getLogger(ConfirmacaoEsteticaHandler.class);

    private static final Pattern TAG = Pattern.compile(
        "<confirmacao_estetica>\\s*(\\{.*?\\})\\s*</confirmacao_estetica>", Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final AestheticAppointmentService appointmentService;

    public ConfirmacaoEsteticaHandler(ObjectMapper objectMapper,
                                      AestheticAppointmentService appointmentService) {
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
     * Extrai a tag e aplica a decisão. {@link Optional#empty()} quando: não há tag, JSON/campos
     * inválidos, agendamento inexistente, contato divergente (barreira) ou transição inválida.
     */
    public Optional<AestheticAppointment> parseAndApply(UUID companyId, UUID conversationId,
                                                        UUID contactId, String aiResponseText) {
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
            log.warn("estetica: tag <confirmacao_estetica> com JSON inválido p/ conversa {} ({})",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String rawId = root.path("appointment_id").asText(null);
        String decisao = root.path("decisao").asText(null);
        if (rawId == null || (!"confirmado".equals(decisao) && !"cancelado".equals(decisao))) {
            log.warn("estetica: tag <confirmacao_estetica> com campos inválidos p/ conversa {}", conversationId);
            return Optional.empty();
        }
        UUID appointmentId;
        try {
            appointmentId = UUID.fromString(rawId);
        } catch (IllegalArgumentException e) {
            log.warn("estetica: <confirmacao_estetica> com appointment_id inválido p/ conversa {}", conversationId);
            return Optional.empty();
        }

        Optional<AestheticAppointment> current = appointmentService.get(companyId, appointmentId);
        if (current.isEmpty()) {
            log.warn("estetica: <confirmacao_estetica> p/ agendamento inexistente {} (conversa {})",
                appointmentId, conversationId);
            return Optional.empty();
        }
        // BARREIRA DE CONTATO: só a dona do agendamento confirma/cancela pela conversa.
        if (current.get().contactId() == null || !current.get().contactId().equals(contactId)) {
            log.warn("estetica: <confirmacao_estetica> de contato divergente p/ agendamento {} (conversa {}) — ignorada",
                appointmentId, conversationId);
            return Optional.empty();
        }

        try {
            AestheticAppointment updated = appointmentService.updateStatus(companyId, appointmentId, decisao);
            log.info("estetica: agendamento {} → {} pela resposta da cliente (conversa {})",
                appointmentId, decisao, conversationId);
            return Optional.of(updated);
        } catch (RuntimeException e) {
            log.warn("estetica: <confirmacao_estetica> não aplicada p/ agendamento {} ({})",
                appointmentId, e.getMessage());
            return Optional.empty();
        }
    }
}
