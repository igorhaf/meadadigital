package com.meada.whatsapp.profiles.dermatologia.appointments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meada.whatsapp.profiles.dermatologia.patients.DermatologiaPatient;
import com.meada.whatsapp.profiles.dermatologia.patients.DermatologiaPatientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai a tag {@code <consulta_derma>{...}</consulta_derma>} da resposta da IA e cria a consulta
 * (camada 8.11). Espelho do AgendamentoNutriConfirmHandler, com os 2 MODOS:
 *
 * <ul>
 *   <li><b>patient_id</b> existente: agenda direto para um paciente já cadastrado.</li>
 *   <li><b>new_patient</b> {name, birth_date?}: cadastra o paciente (sub-entidade do contato da
 *       conversa) e, em seguida, agenda — tudo no mesmo turno.</li>
 * </ul>
 *
 * <p>A consulta referencia {@code procedure_type_id} (a duração vem do tipo). NÃO usa tool calling /
 * responseSchema (mesma restrição da Gemini). {@code date}+{@code start_time} → instante
 * America/Sao_Paulo (hardcoded). O paciente vem do contato da conversa. Qualquer falha →
 * {@link Optional#empty()} + warn (a mensagem da IA segue sem consulta).
 */
@Component
public class AgendamentoDermaConfirmHandler {

    private static final Logger log = LoggerFactory.getLogger(AgendamentoDermaConfirmHandler.class);
    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");

    private static final Pattern TAG = Pattern.compile("<consulta_derma>\\s*(\\{.*?\\})\\s*</consulta_derma>",
        Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final DermatologiaPatientService patientService;
    private final DermatologiaAppointmentService appointmentService;

    public AgendamentoDermaConfirmHandler(ObjectMapper objectMapper, DermatologiaPatientService patientService,
                                          DermatologiaAppointmentService appointmentService) {
        this.objectMapper = objectMapper;
        this.patientService = patientService;
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

    /**
     * Extrai a tag e cria a consulta. Resolve o paciente por um dos 2 modos. {@link Optional#empty()}
     * quando: não há tag, JSON inválido, campos faltando, paciente/cadastro inválido, ou a criação da
     * consulta falha (profissional/paciente/tipo inválido/inativo, fora do horário, conflito). O
     * {@code contactId} vem da conversa.
     */
    public Optional<DermatologiaAppointment> parseAndCreate(UUID companyId, UUID conversationId, UUID contactId,
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
            log.warn("dermatologia: tag <consulta_derma> com JSON inválido p/ conversa {} ({}) — consulta não criada",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String rawProf = root.path("professional_id").asText(null);
        String rawType = root.path("procedure_type_id").asText(null);
        String date = root.path("date").asText(null);
        String startTime = root.path("start_time").asText(null);
        String notes = root.path("notes").asText(null);
        if (rawProf == null || rawType == null || date == null || startTime == null) {
            log.warn("dermatologia: tag <consulta_derma> com campos faltando p/ conversa {} — consulta não criada",
                conversationId);
            return Optional.empty();
        }

        UUID professionalId;
        UUID procedureTypeId;
        Instant startAt;
        try {
            professionalId = UUID.fromString(rawProf);
            procedureTypeId = UUID.fromString(rawType);
            LocalDate d = LocalDate.parse(date);
            LocalTime t = LocalTime.parse(startTime);
            startAt = d.atTime(t).atZone(TENANT_ZONE).toInstant();
        } catch (RuntimeException e) {
            log.warn("dermatologia: tag <consulta_derma> com id/data inválidos p/ conversa {} ({}) — consulta não criada",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        // Resolve o paciente: modo patient_id (existente) OU new_patient (cadastra e agenda).
        UUID patientId;
        try {
            patientId = resolvePatient(companyId, contactId, conversationId, root);
        } catch (ResolvePatientException e) {
            return Optional.empty();
        }
        if (patientId == null) {
            return Optional.empty();
        }

        try {
            DermatologiaAppointment a = appointmentService.create(companyId, professionalId, patientId,
                procedureTypeId, conversationId, startAt, notes);
            log.info("dermatologia: consulta {} criada p/ conversa {} (prof {}, paciente {}, tipo {})",
                a.id(), conversationId, professionalId, patientId, procedureTypeId);
            return Optional.of(a);
        } catch (DermatologiaAppointmentService.ConflictException e) {
            log.warn("dermatologia: <consulta_derma> conflitou no slot do profissional p/ conversa {} — não criada", conversationId);
            return Optional.empty();
        } catch (DermatologiaAppointmentService.OutsideHoursException e) {
            log.warn("dermatologia: <consulta_derma> fora do horário p/ conversa {} — não criada", conversationId);
            return Optional.empty();
        } catch (DermatologiaAppointmentService.ProfessionalNotFoundException
                 | DermatologiaAppointmentService.PatientNotFoundException
                 | DermatologiaAppointmentService.ProcedureTypeNotFoundException
                 | DermatologiaAppointmentService.InactiveProfessionalException
                 | DermatologiaAppointmentService.InactivePatientException
                 | DermatologiaAppointmentService.InactiveProcedureTypeException e) {
            log.warn("dermatologia: <consulta_derma> com profissional/paciente/tipo inválido ou inativo p/ conversa {} — não criada",
                conversationId);
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("dermatologia: falha ao criar consulta p/ conversa {} ({}) — mensagem segue sem consulta",
                conversationId, e.getMessage());
            return Optional.empty();
        }
    }

    private static class ResolvePatientException extends RuntimeException {}

    /**
     * Modo patient_id: valida UUID e usa direto (a criação da consulta revalida que é do tenant).
     * Modo new_patient: cadastra o paciente como sub-entidade do contato da conversa e retorna o id
     * criado. Sem contato resolvido → não dá pra cadastrar. Dados inválidos → empty.
     */
    private UUID resolvePatient(UUID companyId, UUID contactId, UUID conversationId, JsonNode root) {
        String rawPatient = root.path("patient_id").asText(null);
        if (rawPatient != null && !rawPatient.isBlank()) {
            try {
                return UUID.fromString(rawPatient);
            } catch (RuntimeException e) {
                log.warn("dermatologia: <consulta_derma> com patient_id inválido p/ conversa {} — não criada", conversationId);
                throw new ResolvePatientException();
            }
        }

        JsonNode newPatient = root.path("new_patient");
        if (newPatient.isMissingNode() || !newPatient.isObject()) {
            log.warn("dermatologia: <consulta_derma> sem patient_id nem new_patient p/ conversa {} — não criada", conversationId);
            throw new ResolvePatientException();
        }
        if (contactId == null) {
            log.warn("dermatologia: <consulta_derma> new_patient sem contato resolvido p/ conversa {} — não criada", conversationId);
            throw new ResolvePatientException();
        }
        String name = newPatient.path("name").asText(null);
        if (name == null || name.isBlank()) {
            log.warn("dermatologia: <consulta_derma> new_patient com nome faltando p/ conversa {} — não criada", conversationId);
            throw new ResolvePatientException();
        }
        LocalDate birthDate = null;
        String rawBirth = newPatient.path("birth_date").asText(null);
        if (rawBirth != null && !rawBirth.isBlank()) {
            try {
                birthDate = LocalDate.parse(rawBirth);
            } catch (DateTimeException e) {
                birthDate = null;  // data inválida no cadastro pela IA → ignora (administrativo)
            }
        }
        try {
            // userId null: cadastro disparado pela IA (sem ator humano). Audita com actor nulo.
            DermatologiaPatient created = patientService.create(companyId, null, contactId, name, birthDate, null);
            log.info("dermatologia: paciente {} cadastrado pela IA p/ conversa {} (contato {})", created.id(), conversationId, contactId);
            return created.id();
        } catch (DermatologiaPatientService.ContactNotFoundException e) {
            log.warn("dermatologia: <consulta_derma> new_patient com contato inexistente p/ conversa {} — não criada", conversationId);
            throw new ResolvePatientException();
        } catch (RuntimeException e) {
            log.warn("dermatologia: falha ao cadastrar new_patient p/ conversa {} ({}) — não criada", conversationId, e.getMessage());
            throw new ResolvePatientException();
        }
    }
}
