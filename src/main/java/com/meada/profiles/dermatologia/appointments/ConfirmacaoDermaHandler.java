package com.meada.profiles.dermatologia.appointments;

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
 * Extrai a tag {@code <confirmacao_derma>{"appointment_id":"...","decisao":"confirmada|cancelada"}
 * </confirmacao_derma>} e aplica a decisão do paciente (onda Dermatologia 1, backlog #1 — fecha o
 * loop do lembrete D-1). BARREIRA DE CONTATO: só o dono da consulta decide pela conversa. Trava
 * clínica intacta (confirmação operacional). Best-effort.
 */
@Component
public class ConfirmacaoDermaHandler {

    private static final Logger log = LoggerFactory.getLogger(ConfirmacaoDermaHandler.class);

    private static final Pattern TAG = Pattern.compile(
        "<confirmacao_derma>\\s*(\\{.*?\\})\\s*</confirmacao_derma>", Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final DermatologiaAppointmentService appointmentService;

    public ConfirmacaoDermaHandler(ObjectMapper objectMapper,
                                   DermatologiaAppointmentService appointmentService) {
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
     * inválidos, consulta inexistente, contato divergente (barreira) ou transição inválida.
     */
    public Optional<DermatologiaAppointment> parseAndApply(UUID companyId, UUID conversationId,
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
            log.warn("dermatologia: tag <confirmacao_derma> com JSON inválido p/ conversa {} ({})",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String rawId = root.path("appointment_id").asText(null);
        String decisao = root.path("decisao").asText(null);
        if (rawId == null || (!"confirmada".equals(decisao) && !"cancelada".equals(decisao))) {
            log.warn("dermatologia: tag <confirmacao_derma> com campos inválidos p/ conversa {}", conversationId);
            return Optional.empty();
        }
        UUID appointmentId;
        try {
            appointmentId = UUID.fromString(rawId);
        } catch (IllegalArgumentException e) {
            log.warn("dermatologia: <confirmacao_derma> com appointment_id inválido p/ conversa {}", conversationId);
            return Optional.empty();
        }

        Optional<DermatologiaAppointment> current = appointmentService.get(companyId, appointmentId);
        if (current.isEmpty()) {
            log.warn("dermatologia: <confirmacao_derma> p/ consulta inexistente {} (conversa {})",
                appointmentId, conversationId);
            return Optional.empty();
        }
        // BARREIRA DE CONTATO: só o dono da consulta confirma/cancela pela conversa.
        if (current.get().contactId() == null || !current.get().contactId().equals(contactId)) {
            log.warn("dermatologia: <confirmacao_derma> de contato divergente p/ consulta {} (conversa {}) — ignorada",
                appointmentId, conversationId);
            return Optional.empty();
        }

        try {
            DermatologiaAppointment updated = appointmentService.updateStatus(companyId, appointmentId, decisao);
            log.info("dermatologia: consulta {} → {} pela resposta do paciente (conversa {})",
                appointmentId, decisao, conversationId);
            return Optional.of(updated);
        } catch (RuntimeException e) {
            log.warn("dermatologia: <confirmacao_derma> não aplicada p/ consulta {} ({})",
                appointmentId, e.getMessage());
            return Optional.empty();
        }
    }
}
