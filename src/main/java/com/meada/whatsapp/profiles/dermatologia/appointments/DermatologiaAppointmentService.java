package com.meada.whatsapp.profiles.dermatologia.appointments;

import com.meada.whatsapp.profiles.dermatologia.DermatologiaAppointmentStatus;
import com.meada.whatsapp.profiles.dermatologia.DermatologiaContextCache;
import com.meada.whatsapp.profiles.dermatologia.config.DermatologiaConfig;
import com.meada.whatsapp.profiles.dermatologia.config.DermatologiaConfigRepository;
import com.meada.whatsapp.profiles.dermatologia.patients.DermatologiaPatient;
import com.meada.whatsapp.profiles.dermatologia.patients.DermatologiaPatientRepository;
import com.meada.whatsapp.profiles.dermatologia.proceduretypes.DermatologiaProcedureType;
import com.meada.whatsapp.profiles.dermatologia.proceduretypes.DermatologiaProcedureTypeRepository;
import com.meada.whatsapp.profiles.dermatologia.professionals.DermatologiaProfessional;
import com.meada.whatsapp.profiles.dermatologia.professionals.DermatologiaProfessionalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras das consultas de dermatologia (camada 8.11).
 *
 * <p>{@link #create} valida professional (ativo), patient (ativo, do tenant), procedure_type (ativo,
 * do tenant) e a janela do config. A duração vem do procedure_type (SNAPSHOT — escapada). Delega ao
 * repo (conflito por profissional na transação). Snapshots de paciente (do contact do paciente) +
 * professional + tipo. Status inicial agendada.
 *
 * <p>{@link #updateStatus} valida a transição e notifica (confirmada/cancelada). Fuso HARDCODED
 * America/Sao_Paulo.
 */
@Service
public class DermatologiaAppointmentService {

    static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final DermatologiaAppointmentRepository appointmentRepository;
    private final DermatologiaProfessionalRepository professionalRepository;
    private final DermatologiaPatientRepository patientRepository;
    private final DermatologiaProcedureTypeRepository procedureTypeRepository;
    private final DermatologiaConfigRepository configRepository;
    private final DermatologiaAppointmentNotifier notifier;
    private final DermatologiaContextCache contextCache;

    public DermatologiaAppointmentService(DermatologiaAppointmentRepository appointmentRepository,
                                          DermatologiaProfessionalRepository professionalRepository,
                                          DermatologiaPatientRepository patientRepository,
                                          DermatologiaProcedureTypeRepository procedureTypeRepository,
                                          DermatologiaConfigRepository configRepository,
                                          DermatologiaAppointmentNotifier notifier,
                                          DermatologiaContextCache contextCache) {
        this.appointmentRepository = appointmentRepository;
        this.professionalRepository = professionalRepository;
        this.patientRepository = patientRepository;
        this.procedureTypeRepository = procedureTypeRepository;
        this.configRepository = configRepository;
        this.notifier = notifier;
        this.contextCache = contextCache;
    }

    public static class AppointmentNotFoundException extends RuntimeException {}
    public static class ProfessionalNotFoundException extends RuntimeException {}
    public static class PatientNotFoundException extends RuntimeException {}
    public static class ProcedureTypeNotFoundException extends RuntimeException {}
    public static class InactiveProfessionalException extends RuntimeException {}
    public static class InactivePatientException extends RuntimeException {}
    public static class InactiveProcedureTypeException extends RuntimeException {}
    public static class OutsideHoursException extends RuntimeException {}
    public static class InvalidStatusException extends RuntimeException {}
    public static class InvalidStatusTransitionException extends RuntimeException {}

    /** Conflito de slot (→ 409 conflict_slot). Carrega o conflito p/ o controller. */
    public static class ConflictException extends RuntimeException {
        private final transient DermatologiaAppointmentConflict conflict;

        public ConflictException(DermatologiaAppointmentConflict conflict) {
            this.conflict = conflict;
        }

        public DermatologiaAppointmentConflict conflict() {
            return conflict;
        }
    }

    /**
     * Cria uma consulta (status agendada). Valida professional/patient/procedure_type (ativos, do
     * tenant) e janela. A duração vem do procedure_type (snapshot). O snapshot de paciente
     * (name/phone do contact) vem do contact do paciente. O repo re-verifica o conflito por
     * profissional na transação.
     */
    public DermatologiaAppointment create(UUID companyId, UUID professionalId, UUID patientId, UUID procedureTypeId,
                                          UUID conversationId, Instant startAt, String notes) {
        DermatologiaProfessional prof = professionalRepository.findById(companyId, professionalId)
            .orElseThrow(ProfessionalNotFoundException::new);
        if (!prof.active()) {
            throw new InactiveProfessionalException();
        }
        DermatologiaPatient patient = patientRepository.findById(companyId, patientId).orElseThrow(PatientNotFoundException::new);
        if (!patient.active()) {
            throw new InactivePatientException();
        }
        DermatologiaProcedureType type = procedureTypeRepository.findById(companyId, procedureTypeId)
            .orElseThrow(ProcedureTypeNotFoundException::new);
        if (!type.active()) {
            throw new InactiveProcedureTypeException();
        }
        int duration = type.durationMinutes();
        DermatologiaConfig config = configRepository.findByCompany(companyId);
        requireInsideHours(startAt, duration, config);

        String patientName = patient.name();
        String patientPhone = patientRepository.contactPhone(companyId, patient.contactId()).orElse(null);

        DermatologiaAppointment created;
        try {
            created = appointmentRepository.insertAppointment(companyId, professionalId, prof.name(), patientId,
                patientName, patientPhone, procedureTypeId, type.name(), patient.contactId(), conversationId,
                duration, startAt, notes);
        } catch (DermatologiaAppointmentRepository.SlotConflictException e) {
            throw new ConflictException(e.conflict());
        }
        contextCache.invalidate(companyId);
        return created;
    }

    public List<DermatologiaAppointment> list(UUID companyId, String status, Instant dateFrom, Instant dateTo,
                                              UUID professionalId, UUID patientId, UUID contactId, int limit, int offset) {
        return appointmentRepository.listByCompany(companyId, status, dateFrom, dateTo, professionalId, patientId, contactId, limit, offset);
    }

    public long count(UUID companyId, String status, Instant dateFrom, Instant dateTo,
                      UUID professionalId, UUID patientId, UUID contactId) {
        return appointmentRepository.countByCompany(companyId, status, dateFrom, dateTo, professionalId, patientId, contactId);
    }

    public Optional<DermatologiaAppointment> get(UUID companyId, UUID id) {
        return appointmentRepository.findById(companyId, id);
    }

    @Transactional
    public DermatologiaAppointment updateStatus(UUID companyId, UUID id, String newStatusId) {
        DermatologiaAppointmentStatus newStatus = DermatologiaAppointmentStatus.fromId(newStatusId).orElseThrow(InvalidStatusException::new);

        DermatologiaAppointment current = appointmentRepository.findById(companyId, id).orElseThrow(AppointmentNotFoundException::new);
        DermatologiaAppointmentStatus from = DermatologiaAppointmentStatus.fromId(current.status()).orElseThrow(InvalidStatusException::new);

        if (!from.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException();
        }

        appointmentRepository.updateStatus(companyId, id, newStatus.id());

        ZonedDateTime z = current.startAt().atZone(TENANT_ZONE);
        String text = newStatus.notificationText(current.procedureTypeName(), current.professionalName(),
            DATE_FMT.format(z), TIME_FMT.format(z));
        notifier.notifyStatus(companyId, current.conversationId(), text);

        contextCache.invalidate(companyId);
        return appointmentRepository.findById(companyId, id).orElseThrow(AppointmentNotFoundException::new);
    }

    /** Valida que a consulta inteira (início → início+duração) cabe na janela, no fuso do tenant. */
    private void requireInsideHours(Instant startAt, int durationMinutes, DermatologiaConfig config) {
        ZonedDateTime start = startAt.atZone(TENANT_ZONE);
        LocalTime startTime = start.toLocalTime();
        LocalTime endTime = start.plusMinutes(durationMinutes).toLocalTime();
        boolean startsOk = !startTime.isBefore(config.opensAt());
        boolean endsOk = !endTime.isAfter(config.closesAt()) && !endTime.isBefore(startTime);
        if (!startsOk || !endsOk) {
            throw new OutsideHoursException();
        }
    }
}
