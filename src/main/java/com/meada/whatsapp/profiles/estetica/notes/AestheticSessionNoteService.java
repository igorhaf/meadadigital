package com.meada.whatsapp.profiles.estetica.notes;

import com.meada.whatsapp.profiles.estetica.appointments.AestheticAppointment;
import com.meada.whatsapp.profiles.estetica.appointments.AestheticAppointmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Regras da ficha/evolução por sessão (camada 8.3). 1:1 com o agendamento. NÃO editável se o
 * agendamento está cancelado (a sessão não aconteceu). Sem cache (a ficha não entra no contexto da IA).
 */
@Service
public class AestheticSessionNoteService {

    private final AestheticSessionNoteRepository repository;
    private final AestheticAppointmentRepository appointmentRepository;

    public AestheticSessionNoteService(AestheticSessionNoteRepository repository,
                                       AestheticAppointmentRepository appointmentRepository) {
        this.repository = repository;
        this.appointmentRepository = appointmentRepository;
    }

    public static class AppointmentNotFoundException extends RuntimeException {}
    public static class AppointmentCancelledException extends RuntimeException {}

    public Optional<AestheticSessionNote> get(UUID companyId, UUID appointmentId) {
        return repository.findByAppointment(companyId, appointmentId);
    }

    @Transactional
    public AestheticSessionNote upsert(UUID companyId, UUID appointmentId, String treatedArea,
                                       String deviceParams, String observations) {
        AestheticAppointment appt = appointmentRepository.findById(companyId, appointmentId)
            .orElseThrow(AppointmentNotFoundException::new);
        if ("cancelado".equals(appt.status())) {
            throw new AppointmentCancelledException();
        }
        return repository.upsert(companyId, appointmentId, treatedArea, deviceParams, observations);
    }
}
