package com.meada.whatsapp.profiles.pet.appointments;

import com.meada.whatsapp.profiles.pet.PetAppointmentStatus;
import com.meada.whatsapp.profiles.pet.PetContextCache;
import com.meada.whatsapp.profiles.pet.animals.PetAnimal;
import com.meada.whatsapp.profiles.pet.animals.PetAnimalRepository;
import com.meada.whatsapp.profiles.pet.config.PetConfig;
import com.meada.whatsapp.profiles.pet.config.PetConfigRepository;
import com.meada.whatsapp.profiles.pet.professionals.PetProfessional;
import com.meada.whatsapp.profiles.pet.professionals.PetProfessionalRepository;
import com.meada.whatsapp.profiles.pet.services.PetService;
import com.meada.whatsapp.profiles.pet.services.PetServiceRepository;
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
 * Regras dos agendamentos de pet (camada 7.8).
 *
 * <p>{@link #create} valida professional (ativo), service (ativo), animal (ativo + do tenant),
 * SPECIES MATCH (service.speciesRestriction != null → animal.species igual), e a janela do config.
 * Delega ao repo (conflito por profissional na transação). Snapshots de tutor (do contact do animal)
 * + animal + professional + service. Status inicial = agendado.
 *
 * <p>{@link #updateStatus} valida a transição e notifica (confirmado/cancelado) com animal +
 * profissional + data/hora. Fuso HARDCODED America/Sao_Paulo.
 */
@Service
public class PetAppointmentService {

    static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final PetAppointmentRepository appointmentRepository;
    private final PetProfessionalRepository professionalRepository;
    private final PetServiceRepository serviceRepository;
    private final PetAnimalRepository animalRepository;
    private final PetConfigRepository configRepository;
    private final PetAppointmentNotifier notifier;
    private final PetContextCache contextCache;

    public PetAppointmentService(PetAppointmentRepository appointmentRepository,
                                 PetProfessionalRepository professionalRepository,
                                 PetServiceRepository serviceRepository,
                                 PetAnimalRepository animalRepository,
                                 PetConfigRepository configRepository,
                                 PetAppointmentNotifier notifier,
                                 PetContextCache contextCache) {
        this.appointmentRepository = appointmentRepository;
        this.professionalRepository = professionalRepository;
        this.serviceRepository = serviceRepository;
        this.animalRepository = animalRepository;
        this.configRepository = configRepository;
        this.notifier = notifier;
        this.contextCache = contextCache;
    }

    public static class AppointmentNotFoundException extends RuntimeException {}
    public static class ProfessionalNotFoundException extends RuntimeException {}
    public static class ServiceNotFoundException extends RuntimeException {}
    public static class AnimalNotFoundException extends RuntimeException {}
    public static class InactiveProfessionalException extends RuntimeException {}
    public static class InactiveServiceException extends RuntimeException {}
    public static class InactiveAnimalException extends RuntimeException {}
    public static class SpeciesMismatchException extends RuntimeException {}
    public static class OutsideHoursException extends RuntimeException {}
    public static class InvalidStatusException extends RuntimeException {}
    public static class InvalidStatusTransitionException extends RuntimeException {}

    /** Conflito de slot (→ 409 conflict_slot). Carrega o conflito p/ o controller. */
    public static class ConflictException extends RuntimeException {
        private final transient PetAppointmentConflict conflict;

        public ConflictException(PetAppointmentConflict conflict) {
            this.conflict = conflict;
        }

        public PetAppointmentConflict conflict() {
            return conflict;
        }
    }

    /**
     * Cria um agendamento (status agendado). Valida professional/service/animal (ativos, do tenant),
     * species match, janela. O tutor vem do contact do animal (snapshot de name/phone). O repo
     * re-verifica o conflito por profissional na transação.
     */
    public PetAppointment create(UUID companyId, UUID professionalId, UUID serviceId, UUID animalId,
                                 UUID conversationId, Instant startAt, String notes) {
        PetProfessional prof = professionalRepository.findById(companyId, professionalId)
            .orElseThrow(ProfessionalNotFoundException::new);
        if (!prof.active()) {
            throw new InactiveProfessionalException();
        }
        PetService svc = serviceRepository.findById(companyId, serviceId).orElseThrow(ServiceNotFoundException::new);
        if (!svc.active()) {
            throw new InactiveServiceException();
        }
        PetAnimal animal = animalRepository.findById(companyId, animalId).orElseThrow(AnimalNotFoundException::new);
        if (!animal.active()) {
            throw new InactiveAnimalException();
        }
        // SPECIES MATCH: serviço restrito a uma espécie só aceita animal daquela espécie.
        if (svc.speciesRestriction() != null && !svc.speciesRestriction().equals(animal.species())) {
            throw new SpeciesMismatchException();
        }
        PetConfig config = configRepository.findByCompany(companyId);
        requireInsideHours(startAt, svc.durationMinutes(), config);

        // tutor = contact dono do animal (snapshot).
        String tutorName = animalRepository.contactName(companyId, animal.contactId()).orElse("Tutor");
        String tutorPhone = animalRepository.contactPhone(companyId, animal.contactId()).orElse(null);

        PetAppointment created;
        try {
            created = appointmentRepository.insertAppointment(companyId, professionalId, prof.name(),
                serviceId, svc.name(), svc.category(), svc.priceCents(), svc.durationMinutes(),
                animalId, animal.name(), animal.species(), animal.contactId(), conversationId,
                tutorName, tutorPhone, startAt, notes);
        } catch (PetAppointmentRepository.SlotConflictException e) {
            throw new ConflictException(e.conflict());
        }
        contextCache.invalidate(companyId);
        return created;
    }

    public List<PetAppointment> list(UUID companyId, String status, Instant dateFrom, Instant dateTo,
                                     UUID professionalId, UUID animalId, UUID contactId, int limit, int offset) {
        return appointmentRepository.listByCompany(companyId, status, dateFrom, dateTo, professionalId, animalId, contactId, limit, offset);
    }

    public long count(UUID companyId, String status, Instant dateFrom, Instant dateTo,
                      UUID professionalId, UUID animalId, UUID contactId) {
        return appointmentRepository.countByCompany(companyId, status, dateFrom, dateTo, professionalId, animalId, contactId);
    }

    public Optional<PetAppointment> get(UUID companyId, UUID id) {
        return appointmentRepository.findById(companyId, id);
    }

    @Transactional
    public PetAppointment updateStatus(UUID companyId, UUID id, String newStatusId) {
        PetAppointmentStatus newStatus = PetAppointmentStatus.fromId(newStatusId).orElseThrow(InvalidStatusException::new);

        PetAppointment current = appointmentRepository.findById(companyId, id).orElseThrow(AppointmentNotFoundException::new);
        PetAppointmentStatus from = PetAppointmentStatus.fromId(current.status()).orElseThrow(InvalidStatusException::new);

        if (!from.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException();
        }

        appointmentRepository.updateStatus(companyId, id, newStatus.id());

        ZonedDateTime z = current.startAt().atZone(TENANT_ZONE);
        String text = newStatus.notificationText(current.serviceName(), current.animalName(),
            current.professionalName(), DATE_FMT.format(z), TIME_FMT.format(z));
        notifier.notifyStatus(companyId, current.conversationId(), text);

        contextCache.invalidate(companyId);
        return appointmentRepository.findById(companyId, id).orElseThrow(AppointmentNotFoundException::new);
    }

    /** Valida que o agendamento inteiro (início → início+duração) cabe na janela, no fuso do tenant. */
    private void requireInsideHours(Instant startAt, int durationMinutes, PetConfig config) {
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
