package com.meada.whatsapp.profiles.estetica.appointments;

import com.meada.whatsapp.profiles.estetica.AestheticAppointmentStatus;
import com.meada.whatsapp.profiles.estetica.EsteticaContextCache;
import com.meada.whatsapp.profiles.estetica.config.AestheticConfig;
import com.meada.whatsapp.profiles.estetica.config.AestheticConfigRepository;
import com.meada.whatsapp.profiles.estetica.packages.AestheticPackage;
import com.meada.whatsapp.profiles.estetica.packages.AestheticPackageRepository;
import com.meada.whatsapp.profiles.estetica.procedures.AestheticProcedure;
import com.meada.whatsapp.profiles.estetica.procedures.AestheticProcedureRepository;
import com.meada.whatsapp.profiles.estetica.professionals.AestheticProfessional;
import com.meada.whatsapp.profiles.estetica.professionals.AestheticProfessionalRepository;
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
 * Regras dos agendamentos do tenant estetica (camada 8.3).
 *
 * <p>{@link #create} valida profissional + procedimento (ativos), janela de funcionamento, e — SE
 * houver packageId — valida que o pacote é do MESMO contato, está 'ativo' e tem saldo (senão 403/400/
 * 409). Delega ao repositório, que re-verifica conflito por profissional E consome a sessão DENTRO da
 * transação. {@link #updateStatus} valida a transição, devolve a sessão ao CANCELAR (no repo) e
 * notifica confirmado/cancelado.
 */
@Service
public class AestheticAppointmentService {

    static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final AestheticAppointmentRepository appointmentRepository;
    private final AestheticProfessionalRepository professionalRepository;
    private final AestheticProcedureRepository procedureRepository;
    private final AestheticPackageRepository packageRepository;
    private final AestheticConfigRepository configRepository;
    private final AestheticAppointmentNotifier notifier;
    private final EsteticaContextCache contextCache;

    public AestheticAppointmentService(AestheticAppointmentRepository appointmentRepository,
                                       AestheticProfessionalRepository professionalRepository,
                                       AestheticProcedureRepository procedureRepository,
                                       AestheticPackageRepository packageRepository,
                                       AestheticConfigRepository configRepository,
                                       AestheticAppointmentNotifier notifier,
                                       EsteticaContextCache contextCache) {
        this.appointmentRepository = appointmentRepository;
        this.professionalRepository = professionalRepository;
        this.procedureRepository = procedureRepository;
        this.packageRepository = packageRepository;
        this.configRepository = configRepository;
        this.notifier = notifier;
        this.contextCache = contextCache;
    }

    public static class AppointmentNotFoundException extends RuntimeException {}
    public static class ProfessionalNotFoundException extends RuntimeException {}
    public static class ProcedureNotFoundException extends RuntimeException {}
    public static class InactiveProfessionalException extends RuntimeException {}
    public static class InactiveProcedureException extends RuntimeException {}
    public static class OutsideHoursException extends RuntimeException {}
    public static class InvalidStatusException extends RuntimeException {}
    public static class InvalidStatusTransitionException extends RuntimeException {}
    public static class PackageNotFoundException extends RuntimeException {}
    public static class PackageWrongContactException extends RuntimeException {}
    public static class PackageNotActiveException extends RuntimeException {}
    public static class PackageExhaustedException extends RuntimeException {}

    /** Conflito de slot (→ 409 conflict_slot). */
    public static class ConflictException extends RuntimeException {
        private final transient AestheticAppointmentConflict conflict;

        public ConflictException(AestheticAppointmentConflict conflict) {
            this.conflict = conflict;
        }

        public AestheticAppointmentConflict conflict() {
            return conflict;
        }
    }

    /**
     * Cria um agendamento (status agendado). Valida profissional/procedimento (ativos), janela, e — se
     * packageId != null — o pacote (mesmo contato, ativo, com saldo). Delega ao repo (re-check de
     * conflito + consumo de sessão transacional). Snapshots de nome/duração de professional+procedure.
     */
    @Transactional
    public AestheticAppointment create(UUID companyId, UUID professionalId, UUID procedureId, UUID packageId,
                                       UUID contactId, UUID conversationId, Instant startAt, String guestName,
                                       String guestPhone, String notes) {
        AestheticProfessional prof = professionalRepository.findById(companyId, professionalId)
            .orElseThrow(ProfessionalNotFoundException::new);
        if (!prof.active()) {
            throw new InactiveProfessionalException();
        }
        AestheticProcedure procedure = procedureRepository.findById(companyId, procedureId)
            .orElseThrow(ProcedureNotFoundException::new);
        if (!procedure.active()) {
            throw new InactiveProcedureException();
        }
        // Pré-validação do pacote (defesa amigável; o repo ainda re-checa o consumo na transação).
        if (packageId != null) {
            AestheticPackage pkg = packageRepository.findById(companyId, packageId)
                .orElseThrow(PackageNotFoundException::new);
            if (contactId == null || pkg.contactId() == null || !pkg.contactId().equals(contactId)) {
                throw new PackageWrongContactException();
            }
            // 'esgotado' é sem saldo (remaining 0): reporta exhausted (mais preciso que not_active).
            // A checagem de saldo vem ANTES da de 'ativo' por isso (esgotado também não é 'ativo').
            if ("esgotado".equals(pkg.status()) || pkg.sessionsRemaining() <= 0) {
                throw new PackageExhaustedException();
            }
            if (!"ativo".equals(pkg.status())) {
                throw new PackageNotActiveException();
            }
        }
        AestheticConfig config = configRepository.findByCompany(companyId);
        requireInsideHours(startAt, procedure.durationMinutes(), config);

        AestheticAppointment created;
        try {
            created = appointmentRepository.insertAppointment(companyId, professionalId, prof.name(),
                procedureId, procedure.name(), procedure.durationMinutes(), packageId, conversationId,
                contactId, guestName, guestPhone, startAt, notes);
        } catch (AestheticAppointmentRepository.SlotConflictException e) {
            throw new ConflictException(e.conflict());
        } catch (AestheticAppointmentRepository.PackageConsumeException e) {
            // corrida: o pacote esgotou/desativou entre a pré-validação e o consumo transacional.
            throw new PackageExhaustedException();
        }
        contextCache.invalidate(companyId);
        return created;
    }

    public List<AestheticAppointment> list(UUID companyId, String status, Instant dateFrom, Instant dateTo,
                                           UUID professionalId, UUID contactId, int limit, int offset) {
        return appointmentRepository.listByCompany(companyId, status, dateFrom, dateTo, professionalId, contactId, limit, offset);
    }

    public long count(UUID companyId, String status, Instant dateFrom, Instant dateTo,
                      UUID professionalId, UUID contactId) {
        return appointmentRepository.countByCompany(companyId, status, dateFrom, dateTo, professionalId, contactId);
    }

    public Optional<AestheticAppointment> get(UUID companyId, UUID id) {
        return appointmentRepository.findById(companyId, id);
    }

    @Transactional
    public AestheticAppointment updateStatus(UUID companyId, UUID id, String newStatusId) {
        AestheticAppointmentStatus newStatus = AestheticAppointmentStatus.fromId(newStatusId)
            .orElseThrow(InvalidStatusException::new);
        AestheticAppointment current = appointmentRepository.findById(companyId, id)
            .orElseThrow(AppointmentNotFoundException::new);
        AestheticAppointmentStatus from = AestheticAppointmentStatus.fromId(current.status())
            .orElseThrow(InvalidStatusException::new);

        if (!from.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException();
        }

        // cancelar devolve a sessão consumida ao pacote (no repo, transacional).
        appointmentRepository.updateStatus(companyId, id, newStatus.id(), newStatus.isCancelled());

        ZonedDateTime z = current.startAt().atZone(TENANT_ZONE);
        String text = newStatus.notificationText(DATE_FMT.format(z), TIME_FMT.format(z), current.professionalName());
        notifier.notifyStatus(companyId, current.conversationId(), text);

        contextCache.invalidate(companyId);
        return appointmentRepository.findById(companyId, id).orElseThrow(AppointmentNotFoundException::new);
    }

    private void requireInsideHours(Instant startAt, int durationMinutes, AestheticConfig config) {
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
