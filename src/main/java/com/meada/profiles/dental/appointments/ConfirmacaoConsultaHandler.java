package com.meada.profiles.dental.appointments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai a tag {@code <confirmacao_consulta>{"appointment_id":"..."}</confirmacao_consulta>} e
 * CONFIRMA a consulta (onda Dental 1, backlog #1 — fecha o loop do lembrete D-1). SÓ confirma
 * (agendada→confirmada): o CANCELAMENTO pela IA segue BLOQUEADO (trava original do dental —
 * desmarcar é com o consultório). BARREIRA DE CONTATO via paciente. Best-effort.
 */
@Component
public class ConfirmacaoConsultaHandler {

    private static final Logger log = LoggerFactory.getLogger(ConfirmacaoConsultaHandler.class);

    private static final Pattern TAG = Pattern.compile(
        "<confirmacao_consulta>\\s*(\\{.*?\\})\\s*</confirmacao_consulta>", Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final DentalAppointmentService appointmentService;
    private final JdbcTemplate jdbcTemplate;

    public ConfirmacaoConsultaHandler(ObjectMapper objectMapper,
                                      DentalAppointmentService appointmentService,
                                      JdbcTemplate jdbcTemplate) {
        this.objectMapper = objectMapper;
        this.appointmentService = appointmentService;
        this.jdbcTemplate = jdbcTemplate;
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

    /** Extrai a tag e CONFIRMA (só agendada→confirmada; barreira via paciente↔contato). */
    public Optional<DentalAppointment> parseAndConfirm(UUID companyId, UUID conversationId,
                                                       UUID contactId, String aiResponseText) {
        if (aiResponseText == null || contactId == null) {
            return Optional.empty();
        }
        Matcher m = TAG.matcher(aiResponseText);
        if (!m.find()) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(m.group(1));
            UUID appointmentId = UUID.fromString(root.path("appointment_id").asText());
            // BARREIRA: a consulta precisa ser de paciente VINCULADO ao contato da conversa.
            Long owns = jdbcTemplate.queryForObject(
                "select count(*) from dental_appointments a "
                    + "join dental_patients p on p.id = a.patient_id "
                    + "where a.id = ? and a.company_id = ? and p.contact_id = ?",
                Long.class, appointmentId, companyId, contactId);
            if (owns == null || owns == 0) {
                log.warn("dental: <confirmacao_consulta> de contato divergente p/ consulta {} (conversa {}) — ignorada",
                    appointmentId, conversationId);
                return Optional.empty();
            }
            DentalAppointment updated = appointmentService.updateStatus(companyId, appointmentId, "confirmada");
            log.info("dental: consulta {} confirmada pela resposta do paciente (conversa {})",
                appointmentId, conversationId);
            return Optional.of(updated);
        } catch (RuntimeException e) {
            log.warn("dental: <confirmacao_consulta> não aplicada p/ conversa {} ({})",
                conversationId, e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("dental: tag <confirmacao_consulta> inválida p/ conversa {} ({})",
                conversationId, e.getMessage());
            return Optional.empty();
        }
    }
}
