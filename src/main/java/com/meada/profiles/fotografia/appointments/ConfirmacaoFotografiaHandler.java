package com.meada.profiles.fotografia.appointments;

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
 * Extrai a tag {@code <confirmacao_foto>{"session_id":"...","decisao":"confirmada|cancelada"}
 * </confirmacao_foto>} e aplica a decisão da cliente à sessão (onda Fotografia 1, backlog #2 —
 * fecha o loop do lembrete D-2/D-1). BARREIRA DE CONTATO: só a dona da sessão decide pela
 * conversa. A máquina de status valida a transição (confirmada não regride). Best-effort.
 */
@Component
public class ConfirmacaoFotografiaHandler {

    private static final Logger log = LoggerFactory.getLogger(ConfirmacaoFotografiaHandler.class);

    private static final Pattern TAG = Pattern.compile(
        "<confirmacao_foto>\\s*(\\{.*?\\})\\s*</confirmacao_foto>", Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final FotografiaAppointmentService appointmentService;

    public ConfirmacaoFotografiaHandler(ObjectMapper objectMapper,
                                        FotografiaAppointmentService appointmentService) {
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
     * inválidos, sessão inexistente, contato divergente (barreira) ou transição inválida.
     */
    public Optional<FotografiaSessionAppointment> parseAndApply(UUID companyId, UUID conversationId,
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
            log.warn("fotografia: tag <confirmacao_foto> com JSON inválido p/ conversa {} ({})",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String rawId = root.path("session_id").asText(null);
        String decisao = root.path("decisao").asText(null);
        if (rawId == null || (!"confirmada".equals(decisao) && !"cancelada".equals(decisao))) {
            log.warn("fotografia: tag <confirmacao_foto> com campos inválidos p/ conversa {}", conversationId);
            return Optional.empty();
        }
        UUID sessionId;
        try {
            sessionId = UUID.fromString(rawId);
        } catch (IllegalArgumentException e) {
            log.warn("fotografia: <confirmacao_foto> com session_id inválido p/ conversa {}", conversationId);
            return Optional.empty();
        }

        Optional<FotografiaSessionAppointment> current = appointmentService.get(companyId, sessionId);
        if (current.isEmpty()) {
            log.warn("fotografia: <confirmacao_foto> p/ sessão inexistente {} (conversa {})",
                sessionId, conversationId);
            return Optional.empty();
        }
        // BARREIRA DE CONTATO: só a dona da sessão confirma/cancela pela conversa.
        if (current.get().contactId() == null || !current.get().contactId().equals(contactId)) {
            log.warn("fotografia: <confirmacao_foto> de contato divergente p/ sessão {} (conversa {}) — ignorada",
                sessionId, conversationId);
            return Optional.empty();
        }

        try {
            FotografiaSessionAppointment updated = appointmentService.updateStatus(companyId, sessionId, decisao);
            log.info("fotografia: sessão {} → {} pela resposta da cliente (conversa {})",
                sessionId, decisao, conversationId);
            return Optional.of(updated);
        } catch (RuntimeException e) {
            log.warn("fotografia: <confirmacao_foto> não aplicada p/ sessão {} ({})", sessionId, e.getMessage());
            return Optional.empty();
        }
    }
}
