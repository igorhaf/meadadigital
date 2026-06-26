package com.meada.whatsapp.profiles.dermatologia.appointments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meada.whatsapp.profiles.dermatologia.proceduretypes.DermatologiaProcedureType;
import com.meada.whatsapp.profiles.dermatologia.proceduretypes.DermatologiaProcedureTypeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai a tag {@code <entrega_preparo>{...}</entrega_preparo>} da resposta da IA e ENTREGA a nota
 * de PREPARO (prep_instructions) do tipo de atendimento da consulta (camada 8.11) — padrão de
 * ENTREGA READ-ONLY (espelho EXATO do EntregaPlanoHandler do nutri).
 *
 * <p>Diferente dos confirm handlers que CRIAM ou MUTAM: aqui a IA NUNCA gera o conteúdo. O texto
 * entregue é a {@code prep_instructions} do procedure_type, gravada pelo médico no painel, enviada
 * VERBATIM ao paciente — sem reescrita, sem geração pela IA. A tag só referencia qual consulta; o
 * backend resolve o tipo dela e dispara o envio.
 *
 * <p>BARREIRA DE SEGURANÇA: a nota só é entregue se o {@code contactId} da consulta coincidir com o
 * contato DA PRÓPRIA CONVERSA. Isso impede que a IA, induzida por um appointment_id de outra pessoa,
 * vaze a orientação para o contato errado. Sem nota de preparo (vazia) → {@link Optional#empty()}.
 * Qualquer falha → empty + warn. Devolve o texto entregue em caso de sucesso.
 */
@Component
public class EntregaPreparoHandler {

    private static final Logger log = LoggerFactory.getLogger(EntregaPreparoHandler.class);

    private static final Pattern TAG = Pattern.compile("<entrega_preparo>\\s*(\\{.*?\\})\\s*</entrega_preparo>",
        Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final DermatologiaAppointmentService appointmentService;
    private final DermatologiaProcedureTypeService procedureTypeService;
    private final DermatologiaAppointmentNotifier notifier;

    public EntregaPreparoHandler(ObjectMapper objectMapper, DermatologiaAppointmentService appointmentService,
                                 DermatologiaProcedureTypeService procedureTypeService, DermatologiaAppointmentNotifier notifier) {
        this.objectMapper = objectMapper;
        this.appointmentService = appointmentService;
        this.procedureTypeService = procedureTypeService;
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
     * Extrai a tag e entrega a nota de preparo do tipo da consulta. Devolve o texto entregue em caso
     * de sucesso. {@link Optional#empty()} quando: não há tag, JSON inválido, appointment_id
     * faltando/inválido, consulta inexistente, consulta de OUTRO contato (barreira de segurança), tipo
     * SEM preparo, ou o envio falha. O texto é a prep_instructions VERBATIM — nunca passa por geração
     * da IA.
     */
    public Optional<String> parseAndDeliver(UUID companyId, UUID conversationId, UUID contactId,
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
            log.warn("dermatologia: tag <entrega_preparo> com JSON inválido p/ conversa {} ({}) — não entregue",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String rawAppt = root.path("appointment_id").asText(null);
        if (rawAppt == null || rawAppt.isBlank()) {
            log.warn("dermatologia: tag <entrega_preparo> sem appointment_id p/ conversa {} — não entregue", conversationId);
            return Optional.empty();
        }
        UUID appointmentId;
        try {
            appointmentId = UUID.fromString(rawAppt);
        } catch (RuntimeException e) {
            log.warn("dermatologia: <entrega_preparo> com appointment_id inválido p/ conversa {} — não entregue", conversationId);
            return Optional.empty();
        }

        Optional<DermatologiaAppointment> appointment = appointmentService.get(companyId, appointmentId);
        if (appointment.isEmpty()) {
            log.warn("dermatologia: <entrega_preparo> referencia consulta inexistente {} p/ conversa {} — não entregue",
                appointmentId, conversationId);
            return Optional.empty();
        }

        // BARREIRA DE SEGURANÇA: a nota só sai para o contato dono da consulta.
        if (!Objects.equals(appointment.get().contactId(), contactId)) {
            log.warn("dermatologia: <entrega_preparo> de consulta de outro contato (consulta {} contato {} ≠ conversa {}) — bloqueado, não entregue",
                appointmentId, appointment.get().contactId(), contactId);
            return Optional.empty();
        }

        Optional<DermatologiaProcedureType> type = procedureTypeService.get(companyId, appointment.get().procedureTypeId());
        if (type.isEmpty()) {
            log.warn("dermatologia: <entrega_preparo> tipo da consulta {} inexistente p/ conversa {} — não entregue",
                appointmentId, conversationId);
            return Optional.empty();
        }

        String prep = type.get().prepInstructions();
        if (prep == null || prep.isBlank()) {
            log.warn("dermatologia: <entrega_preparo> tipo {} sem preparo p/ consulta {} (conversa {}) — não entregue",
                type.get().id(), appointmentId, conversationId);
            return Optional.empty();
        }

        if (notifier.sendText(companyId, conversationId, prep)) {
            log.info("dermatologia: preparo do tipo {} entregue VERBATIM p/ conversa {} (consulta {})",
                type.get().id(), conversationId, appointmentId);
            return Optional.of(prep);
        }
        log.warn("dermatologia: <entrega_preparo> falhou ao enviar o preparo p/ conversa {} (consulta {}) — não entregue",
            conversationId, appointmentId);
        return Optional.empty();
    }
}
