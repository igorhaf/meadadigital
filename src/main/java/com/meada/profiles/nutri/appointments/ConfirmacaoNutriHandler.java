package com.meada.profiles.nutri.appointments;

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
 * Extrai a tag {@code <confirmacao_nutri>{...}</confirmacao_nutri>} da resposta da IA e MUTA o
 * status da agendamento (onda Nutri 1, backlog #1 — fecha o loop do lembrete de véspera
 * do NutriReminderJob). A IA só REFLETE a decisão do CLIENTE: {@code decisao} confirmado (de
 * 'agendamentodo') ou cancelado (de 'agendamentodo'/'confirmado' — a máquina de status valida; cancelar
 * LIBERA o slot do nutricionista; a notificação padrão dispara pelo service).
 *
 * <p>BARREIRA DE CONTATO: a agendamento tem de pertencer ao MESMO contato da conversa. Transição
 * inválida, id inexistente, decisão desconhecida ou contato divergente → {@link Optional#empty()}
 * + warn (a mensagem da IA segue sem mutação). O OutboundService remove a tag antes de enviar.
 * Clone do ConfirmacaoReservaHandler/ConfirmacaoPousadaHandler (migs 91/92).
 */
@Component
public class ConfirmacaoNutriHandler {

    private static final Logger log = LoggerFactory.getLogger(ConfirmacaoNutriHandler.class);

    private static final Pattern TAG = Pattern.compile(
        "<confirmacao_nutri>\\s*(\\{.*?\\})\\s*</confirmacao_nutri>", Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final NutriAppointmentService appointmentService;

    public ConfirmacaoNutriHandler(ObjectMapper objectMapper, NutriAppointmentService appointmentService) {
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
     * Extrai a tag e aplica a decisão do cliente à agendamento. {@link Optional#empty()} quando: não há
     * tag, JSON/campos inválidos, agendamento inexistente, contato divergente (barreira) ou transição
     * inválida na máquina de status.
     */
    public Optional<NutriAppointment> parseAndApply(UUID companyId, UUID conversationId, UUID contactId,
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
            log.warn("nutri: tag <confirmacao_nutri> com JSON inválido p/ conversa {} ({})",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String rawId = root.path("appointment_id").asText(null);
        String decisao = root.path("decisao").asText(null);
        if (rawId == null || (!"confirmado".equals(decisao) && !"cancelado".equals(decisao))) {
            log.warn("nutri: tag <confirmacao_nutri> com campos inválidos p/ conversa {}", conversationId);
            return Optional.empty();
        }
        UUID appointmentId;
        try {
            appointmentId = UUID.fromString(rawId);
        } catch (IllegalArgumentException e) {
            log.warn("nutri: <confirmacao_nutri> com appointment_id inválido p/ conversa {}", conversationId);
            return Optional.empty();
        }

        Optional<NutriAppointment> current = appointmentService.get(companyId, appointmentId);
        if (current.isEmpty()) {
            log.warn("nutri: <confirmacao_nutri> p/ agendamento inexistente {} (conversa {})",
                appointmentId, conversationId);
            return Optional.empty();
        }
        // BARREIRA DE CONTATO: só o dono da agendamento confirma/cancela pela conversa.
        if (current.get().contactId() == null || !current.get().contactId().equals(contactId)) {
            log.warn("nutri: <confirmacao_nutri> de contato divergente p/ agendamento {} (conversa {}) — ignorada",
                appointmentId, conversationId);
            return Optional.empty();
        }

        try {
            NutriAppointment updated = appointmentService.updateStatus(companyId, appointmentId, decisao);
            log.info("nutri: agendamento {} → {} pela resposta do cliente (conversa {})",
                appointmentId, decisao, conversationId);
            return Optional.of(updated);
        } catch (NutriAppointmentService.InvalidStatusTransitionException e) {
            log.warn("nutri: <confirmacao_nutri> com transição inválida p/ agendamento {} (status atual não permite {})",
                appointmentId, decisao);
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("nutri: falha ao aplicar <confirmacao_nutri> p/ agendamento {} ({})",
                appointmentId, e.getMessage());
            return Optional.empty();
        }
    }
}
