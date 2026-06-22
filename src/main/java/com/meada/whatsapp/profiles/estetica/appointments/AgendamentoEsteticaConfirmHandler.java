package com.meada.whatsapp.profiles.estetica.appointments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meada.whatsapp.messaging.ContactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai a tag {@code <agendamento_estetica>{...}</agendamento_estetica>} e cria o agendamento (camada
 * 8.3). Clone do AgendamentoConfirmHandler do salon + {@code package_id} opcional (consome saldo do
 * pacote). NÃO usa tool calling. Qualquer falha → {@link Optional#empty()} + warn.
 */
@Component
public class AgendamentoEsteticaConfirmHandler {

    private static final Logger log = LoggerFactory.getLogger(AgendamentoEsteticaConfirmHandler.class);
    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");

    private static final Pattern TAG = Pattern.compile("<agendamento_estetica>\\s*(\\{.*?\\})\\s*</agendamento_estetica>",
        Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final ContactRepository contactRepository;
    private final AestheticAppointmentService appointmentService;

    public AgendamentoEsteticaConfirmHandler(ObjectMapper objectMapper, ContactRepository contactRepository,
                                             AestheticAppointmentService appointmentService) {
        this.objectMapper = objectMapper;
        this.contactRepository = contactRepository;
        this.appointmentService = appointmentService;
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

    public Optional<AestheticAppointment> parseAndCreate(UUID companyId, UUID conversationId, UUID contactId,
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
            log.warn("estetica: tag <agendamento_estetica> com JSON inválido p/ conversa {} ({}) — não criado",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String rawProf = root.path("professional_id").asText(null);
        String rawProc = root.path("procedure_id").asText(null);
        String date = root.path("date").asText(null);
        String startTime = root.path("start_time").asText(null);
        String notes = root.path("notes").asText(null);
        if (rawProf == null || rawProc == null || date == null || startTime == null) {
            log.warn("estetica: tag <agendamento_estetica> com campos faltando p/ conversa {} — não criado", conversationId);
            return Optional.empty();
        }

        UUID professionalId;
        UUID procedureId;
        UUID packageId;
        Instant startAt;
        try {
            professionalId = UUID.fromString(rawProf);
            procedureId = UUID.fromString(rawProc);
            packageId = parseUuid(root.path("package_id").asText(null));
            LocalDate d = LocalDate.parse(date);
            LocalTime t = LocalTime.parse(startTime);
            startAt = d.atTime(t).atZone(TENANT_ZONE).toInstant();
        } catch (RuntimeException e) {
            log.warn("estetica: tag <agendamento_estetica> com ids/data inválidos p/ conversa {} ({}) — não criado",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String guestName = contactRepository.findNameByConversationId(conversationId)
            .filter(n -> n != null && !n.isBlank())
            .orElseGet(() -> contactRepository.findPhoneByConversationId(conversationId).orElse("Cliente"));
        String guestPhone = contactRepository.findPhoneByConversationId(conversationId).orElse(null);

        try {
            AestheticAppointment a = appointmentService.create(companyId, professionalId, procedureId,
                packageId, contactId, conversationId, startAt, guestName, guestPhone, notes);
            log.info("estetica: agendamento {} criado p/ conversa {} (prof {}, proc {}, pacote {})",
                a.id(), conversationId, professionalId, procedureId, packageId);
            return Optional.of(a);
        } catch (AestheticAppointmentService.ConflictException e) {
            log.warn("estetica: <agendamento_estetica> conflitou no slot do profissional p/ conversa {} — não criado", conversationId);
            return Optional.empty();
        } catch (AestheticAppointmentService.OutsideHoursException e) {
            log.warn("estetica: <agendamento_estetica> fora do horário p/ conversa {} — não criado", conversationId);
            return Optional.empty();
        } catch (AestheticAppointmentService.PackageWrongContactException
                 | AestheticAppointmentService.PackageNotActiveException
                 | AestheticAppointmentService.PackageExhaustedException
                 | AestheticAppointmentService.PackageNotFoundException e) {
            log.warn("estetica: <agendamento_estetica> com pacote inválido/sem saldo p/ conversa {} — não criado", conversationId);
            return Optional.empty();
        } catch (AestheticAppointmentService.ProfessionalNotFoundException
                 | AestheticAppointmentService.ProcedureNotFoundException
                 | AestheticAppointmentService.InactiveProfessionalException
                 | AestheticAppointmentService.InactiveProcedureException e) {
            log.warn("estetica: <agendamento_estetica> com profissional/procedimento inválido ou inativo p/ conversa {} — não criado",
                conversationId);
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("estetica: falha ao criar agendamento p/ conversa {} ({}) — mensagem segue sem agendamento",
                conversationId, e.getMessage());
            return Optional.empty();
        }
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank() || "null".equalsIgnoreCase(raw)) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
