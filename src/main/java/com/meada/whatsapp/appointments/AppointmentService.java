package com.meada.whatsapp.appointments;

import com.meada.whatsapp.ai.AppointmentAction;
import com.meada.whatsapp.availability.AvailabilitySlot;
import com.meada.whatsapp.availability.AvailabilitySlotRepository;
import com.meada.whatsapp.messaging.ConversationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Lógica de agendamento da camada 5.19 (#60 book / #64 reschedule/cancel). Aplica a ação que a
 * IA decidiu ({@link AppointmentAction}) sobre {@code appointments}, validando contra as janelas
 * de disponibilidade (availability_slots da 5.17) e contra conflitos de horário.
 *
 * <p>Fuso do tenant HARDCODED em America/Sao_Paulo (mesma decisão do OutboundService — tenants
 * BR no MVP). O whenIso da IA é ISO local ("yyyy-MM-ddTHH:mm"); convertemos para Instant nesse
 * fuso antes de persistir (a coluna é timestamptz).
 *
 * <p>{@link #applyAppointmentAction} NUNCA lança: erros viram log.warn e a IA segue (o reply ao
 * cliente já pode ter oferecido alternativas; o agendamento é best-effort do lado do backend).
 */
@Service
public class AppointmentService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);

    // Mesmo fuso do OutboundService.TENANT_ZONE — tenants BR no MVP.
    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");

    // Horizonte de busca de slots livres (nextFreeSlots): ~14 dias à frente.
    private static final int LOOKAHEAD_DAYS = 14;

    private final AppointmentRepository appointmentRepository;
    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final ConversationRepository conversationRepository;

    public AppointmentService(AppointmentRepository appointmentRepository,
                              AvailabilitySlotRepository availabilitySlotRepository,
                              ConversationRepository conversationRepository) {
        this.appointmentRepository = appointmentRepository;
        this.availabilitySlotRepository = availabilitySlotRepository;
        this.conversationRepository = conversationRepository;
    }

    /**
     * Dispatcher resiliente da ação de agendamento da IA. Resolve o contato da conversa e
     * delega para book/reschedule/cancel conforme o {@code kind}. NUNCA propaga exceção: log
     * .warn em qualquer falha (o atendimento ao cliente não pode quebrar por causa do
     * agendamento). No-op silencioso quando action ou kind são inválidos.
     */
    public void applyAppointmentAction(UUID companyId, UUID conversationId, AppointmentAction action) {
        if (action == null || action.kind() == null) {
            return;
        }
        try {
            Optional<UUID> contactId =
                conversationRepository.findContactIdByConversation(conversationId);
            if (contactId.isEmpty()) {
                log.warn("appointments: contact not found for conversation {} — action '{}' skipped",
                    conversationId, action.kind());
                return;
            }
            switch (action.kind()) {
                case "book" -> book(companyId, conversationId, contactId.get(), action);
                case "reschedule" -> reschedule(companyId, contactId.get(), action);
                case "cancel" -> cancel(companyId, contactId.get());
                default -> log.warn("appointments: unknown action kind '{}' for conversation {}",
                    action.kind(), conversationId);
            }
        } catch (Exception e) {
            // Defesa total: qualquer erro inesperado vira warn, jamais quebra o outbound.
            log.warn("appointments: failed to apply action '{}' for conversation {} ({})",
                action.kind(), conversationId, e.getMessage());
        }
    }

    /**
     * Confirma um novo agendamento (#60). Valida o horário contra uma janela ativa do dia da
     * semana E contra conflito (unique parcial no banco). serviceHint é casado best-effort a um
     * service_id; sem match, fica null. {@link Optional#empty()} quando o slot é inválido (fora
     * de janela / sem whenIso) ou já está tomado — o caller só loga.
     */
    public Optional<Appointment> book(UUID companyId, UUID conversationId, UUID contactId,
                                      AppointmentAction action) {
        Optional<Instant> when = parseWhen(action.whenIso());
        if (when.isEmpty()) {
            log.warn("appointments: book sem whenIso válido (conversation {}, whenIso '{}')",
                conversationId, action.whenIso());
            return Optional.empty();
        }
        if (!isWithinActiveWindow(companyId, when.get())) {
            log.warn("appointments: book fora de janela ativa (company {}, when {})",
                companyId, when.get());
            return Optional.empty();
        }
        UUID serviceId =
            appointmentRepository.findServiceIdByNameHint(companyId, action.serviceHint()).orElse(null);
        Optional<Appointment> created =
            appointmentRepository.insert(companyId, contactId, conversationId, serviceId, when.get(), null);
        if (created.isEmpty()) {
            log.warn("appointments: book conflitou com horário ocupado (company {}, when {})",
                companyId, when.get());
        } else {
            log.info("appointments: booked {} for contact {} at {}",
                created.get().id(), contactId, when.get());
        }
        return created;
    }

    /**
     * Remarca o agendamento ativo do contato (#64) para um novo horário, validado como no book.
     * No-op (com warn) se o contato não tem agendamento ativo, o whenIso é inválido, o novo
     * horário cai fora de janela, ou conflita.
     */
    public boolean reschedule(UUID companyId, UUID contactId, AppointmentAction action) {
        Optional<Appointment> active = appointmentRepository.findActiveByContact(contactId);
        if (active.isEmpty()) {
            log.warn("appointments: reschedule sem agendamento ativo (contact {})", contactId);
            return false;
        }
        Optional<Instant> when = parseWhen(action.whenIso());
        if (when.isEmpty() || !isWithinActiveWindow(companyId, when.get())) {
            log.warn("appointments: reschedule com horário inválido/fora de janela "
                + "(contact {}, whenIso '{}')", contactId, action.whenIso());
            return false;
        }
        boolean ok = appointmentRepository.reschedule(active.get().id(), companyId, when.get());
        if (!ok) {
            log.warn("appointments: reschedule falhou (conflito?) appointment {} → {}",
                active.get().id(), when.get());
        }
        return ok;
    }

    /**
     * Cancela o agendamento ativo do contato (#64). No-op (com warn) se não há agendamento ativo.
     */
    public boolean cancel(UUID companyId, UUID contactId) {
        Optional<Appointment> active = appointmentRepository.findActiveByContact(contactId);
        if (active.isEmpty()) {
            log.warn("appointments: cancel sem agendamento ativo (contact {})", contactId);
            return false;
        }
        boolean ok = appointmentRepository.updateStatus(active.get().id(), companyId, "cancelled");
        if (ok) {
            log.info("appointments: cancelled {} for contact {}", active.get().id(), contactId);
        }
        return ok;
    }

    /**
     * Próximos {@code count} horários LIVRES a partir de {@code fromInstant}, gerados a partir das
     * janelas ativas de availability_slots em passos de slot_minutes, ao longo dos próximos ~14
     * dias, pulando os já ocupados por agendamentos 'scheduled'. Suporta a IA oferecer
     * alternativas (#60). Lista vazia se não há janelas ativas ou nenhum slot livre na janela.
     */
    public List<Instant> nextFreeSlots(UUID companyId, Instant fromInstant, int count) {
        Objects.requireNonNull(companyId, "companyId");
        Objects.requireNonNull(fromInstant, "fromInstant");
        if (count <= 0) {
            return List.of();
        }
        List<AvailabilitySlot> windows = availabilitySlotRepository.findActiveByCompany(companyId);
        if (windows.isEmpty()) {
            return List.of();
        }
        LocalDateTime fromLocal = LocalDateTime.ofInstant(fromInstant, TENANT_ZONE);
        Instant horizonInstant = fromInstant.plusSeconds((long) LOOKAHEAD_DAYS * 86_400);
        // Ocupados no horizonte — para pular rápido (Set de instants exatos).
        Set<Instant> taken =
            new HashSet<>(appointmentRepository.findTakenSlots(companyId, fromInstant, horizonInstant));

        List<Instant> free = new ArrayList<>();
        for (int dayOffset = 0; dayOffset <= LOOKAHEAD_DAYS && free.size() < count; dayOffset++) {
            LocalDate date = fromLocal.toLocalDate().plusDays(dayOffset);
            int weekday = date.getDayOfWeek().getValue() % 7;   // ISO Mon=1..Sun=7 → Sun=0..Sat=6
            for (AvailabilitySlot w : windows) {
                if (w.weekday() != weekday || free.size() >= count) {
                    continue;
                }
                for (LocalTime t = w.startsAt();
                     t.isBefore(w.endsAt()) && free.size() < count;
                     t = t.plusMinutes(w.slotMinutes())) {
                    LocalDateTime candidate = LocalDateTime.of(date, t);
                    Instant instant = candidate.atZone(TENANT_ZONE).toInstant();
                    if (!instant.isAfter(fromInstant) || taken.contains(instant)) {
                        continue;   // já passou ou já ocupado
                    }
                    free.add(instant);
                }
            }
        }
        return free;
    }

    // ---- helpers ------------------------------------------------------------

    /** Converte o whenIso (ISO local "yyyy-MM-ddTHH:mm") para Instant no fuso do tenant. */
    private Optional<Instant> parseWhen(String whenIso) {
        if (whenIso == null || whenIso.isBlank()) {
            return Optional.empty();
        }
        try {
            LocalDateTime local = LocalDateTime.parse(whenIso.trim());
            return Optional.of(local.atZone(TENANT_ZONE).toInstant());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * True se o instante cai dentro de alguma janela ativa do dia da semana correspondente (no
     * fuso do tenant). Checa só a janela [startsAt, endsAt); o alinhamento exato ao passo de
     * slot_minutes não é exigido (a IA pode propor :15 numa janela de 30min — o que importa é
     * estar dentro do horário de atendimento e não conflitar).
     */
    private boolean isWithinActiveWindow(UUID companyId, Instant when) {
        LocalDateTime local = LocalDateTime.ofInstant(when, TENANT_ZONE);
        int weekday = local.getDayOfWeek().getValue() % 7;   // Sun=0..Sat=6
        LocalTime time = local.toLocalTime();
        for (AvailabilitySlot w : availabilitySlotRepository.findActiveByCompany(companyId)) {
            if (w.weekday() == weekday
                && !time.isBefore(w.startsAt()) && time.isBefore(w.endsAt())) {
                return true;
            }
        }
        return false;
    }
}
