package com.meada.profiles.barbearia.queue;

import com.meada.profiles.barbearia.BarberContextCache;
import com.meada.profiles.barbearia.BarberQueueStatus;
import com.meada.profiles.barbearia.appointments.BarberAppointmentNotifier;
import com.meada.profiles.barbearia.barbers.BarberBarber;
import com.meada.profiles.barbearia.barbers.BarberBarberRepository;
import com.meada.profiles.barbearia.config.BarberConfig;
import com.meada.profiles.barbearia.config.BarberConfigRepository;
import com.meada.profiles.barbearia.services.BarberService;
import com.meada.profiles.barbearia.services.BarberServiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras da FILA DE WALK-IN da barbearia (camada 8.1) — a peça NOVA desta SM.
 *
 * <p>POSIÇÃO DERIVADA (a escapada estrutural): a posição de um ticket 'aguardando' NÃO é persistida.
 * É calculada por query — {@code position = (#tickets aguardando à frente no mesmo escopo) + 1}.
 * Atender/desistir de quem está à frente RECOMPUTA todas as posições sem nenhum UPDATE de
 * reordenação. Regra de escopo do "qualquer barbeiro":
 * <ul>
 *   <li>Ticket GERAL (barber_id null): concorre com TODOS os 'aguardando' à frente (pode ser
 *       atendido por qualquer barbeiro).</li>
 *   <li>Ticket de barbeiro X: concorre com os 'aguardando' do barbeiro X E com os GERAIS à frente
 *       (um geral à frente pode "pegar" o barbeiro X).</li>
 * </ul>
 *
 * <p>ETA estimado (informativo, sem promessa) = soma das durações dos tickets à frente no mesmo
 * escopo. A IA é instruída a sempre apresentar como ESTIMATIVA ("aproximadamente").
 *
 * <p>A transição aguardando→chamado é AÇÃO HUMANA no painel (a IA não move ticket) e dispara a
 * notificação "chegou sua vez" (a notificação crítica do walk-in). NÃO há callNext automático.
 */
@Service
public class BarberQueueService {

    private final BarberQueueRepository queueRepository;
    private final com.meada.profiles.barbearia.appointments.BarberAppointmentService appointmentService;
    private final BarberBarberRepository barberRepository;
    private final BarberServiceRepository serviceRepository;
    private final BarberConfigRepository configRepository;
    private final BarberAppointmentNotifier notifier;
    private final BarberContextCache contextCache;

    public BarberQueueService(BarberQueueRepository queueRepository,
                              BarberBarberRepository barberRepository,
                              BarberServiceRepository serviceRepository,
                              BarberConfigRepository configRepository,
                              BarberAppointmentNotifier notifier,
                              BarberContextCache contextCache,
                              com.meada.profiles.barbearia.appointments.BarberAppointmentService appointmentService) {
        this.queueRepository = queueRepository;
        this.appointmentService = appointmentService;
        this.barberRepository = barberRepository;
        this.serviceRepository = serviceRepository;
        this.configRepository = configRepository;
        this.notifier = notifier;
        this.contextCache = contextCache;
    }

    public static class TicketNotFoundException extends RuntimeException {}
    public static class BarberNotFoundException extends RuntimeException {}
    public static class ServiceNotFoundException extends RuntimeException {}
    public static class InactiveBarberException extends RuntimeException {}
    public static class InactiveServiceException extends RuntimeException {}
    public static class QueueDisabledException extends RuntimeException {}
    public static class InvalidStatusException extends RuntimeException {}
    public static class InvalidStatusTransitionException extends RuntimeException {}

    /**
     * Enfileira um cliente (status inicial 'aguardando'). Valida que a fila está ligada
     * (queue_enabled), que o serviço existe + ativo (dá a duração snapshot), e — se barberId informado
     * (null = qualquer) — que o barbeiro existe + ativo. Retorna o ticket JÁ com posição + ETA
     * derivados preenchidos.
     */
    public BarberQueueTicket enqueue(UUID companyId, UUID barberId, UUID serviceId, UUID contactId,
                                     UUID conversationId, String guestName, String guestPhone, String notes) {
        BarberConfig config = configRepository.findByCompany(companyId);
        if (!config.queueEnabled()) {
            throw new QueueDisabledException();
        }
        BarberService service = serviceRepository.findById(companyId, serviceId)
            .orElseThrow(ServiceNotFoundException::new);
        if (!service.active()) {
            throw new InactiveServiceException();
        }
        String barberName = null;
        if (barberId != null) {
            BarberBarber barber = barberRepository.findById(companyId, barberId)
                .orElseThrow(BarberNotFoundException::new);
            if (!barber.active()) {
                throw new InactiveBarberException();
            }
            barberName = barber.name();
        }
        BarberQueueTicket created = queueRepository.insert(companyId, barberId, barberName, serviceId,
            service.name(), service.durationMinutes(), conversationId, contactId, guestName, guestPhone, notes);
        contextCache.invalidate(companyId);
        return withDerived(companyId, created);
    }

    /** Tamanho atual da fila (aguardando). */
    public int queueSize(UUID companyId) {
        return queueRepository.countWaiting(companyId);
    }

    /** Lista os tickets ATIVOS (aguardando + chamado) COM posição/ETA derivados preenchidos. */
    public List<BarberQueueTicket> listActive(UUID companyId) {
        List<BarberQueueTicket> active = queueRepository.listActive(companyId);
        List<BarberQueueTicket> out = new ArrayList<>(active.size());
        for (BarberQueueTicket t : active) {
            out.add(withDerived(companyId, t));
        }
        return out;
    }

    /** Detalhe de um ticket COM posição/ETA derivados (se 'aguardando'). */
    public Optional<BarberQueueTicket> get(UUID companyId, UUID id) {
        return queueRepository.findById(companyId, id).map(t -> withDerived(companyId, t));
    }

    /**
     * Transição manual do ticket (PATCH pelo painel — a IA NÃO move). Valida a transição. Quando vira
     * 'chamado', grava called_at e NOTIFICA o cliente ("chegou sua vez"). Os demais não notificam.
     */
    /** Ticket sem barbeiro definido não converte (→ 400 barber_required no controller). */
    public static class BarberRequiredException extends RuntimeException {}

    /**
     * Onda 2 (backlog #8): converte um ticket CHAMADO em atendimento IMEDIATO do barbeiro —
     * cria o agendamento (start=agora, snapshots do ticket, conflito re-verificado pelo service
     * da agenda) e muta o ticket pra 'atendido'. Une fila e agenda: o corte entra no funil
     * (fidelidade conta, relatórios enxergam). {@code barberId} pode sobrepor o do ticket
     * ("qualquer barbeiro" → o barbeiro livre que puxou).
     */
    @org.springframework.transaction.annotation.Transactional
    public com.meada.profiles.barbearia.appointments.BarberAppointment convertToAppointment(
            UUID companyId, UUID ticketId, UUID barberId) {
        BarberQueueTicket current = queueRepository.findById(companyId, ticketId)
            .orElseThrow(TicketNotFoundException::new);
        BarberQueueStatus from = BarberQueueStatus.fromId(current.status())
            .orElseThrow(InvalidStatusException::new);
        if (from != BarberQueueStatus.CHAMADO && from != BarberQueueStatus.AGUARDANDO) {
            throw new InvalidStatusTransitionException();
        }
        UUID effectiveBarber = barberId != null ? barberId : current.barberId();
        if (effectiveBarber == null) {
            throw new BarberRequiredException();
        }
        // Cria o atendimento AGORA (conflito por barbeiro re-verificado na transação da agenda;
        // sem cupom — walk-in). Snapshots vêm do catálogo pelo próprio create.
        com.meada.profiles.barbearia.appointments.BarberAppointment appointment =
            appointmentService.create(companyId, effectiveBarber, current.serviceId(),
                current.contactId(), current.conversationId(), java.time.Instant.now(),
                current.guestName(), current.guestPhone(),
                current.notes() == null ? "walk-in (fila)" : current.notes() + " · walk-in (fila)",
                null);
        queueRepository.updateStatus(companyId, ticketId, BarberQueueStatus.ATENDIDO.id(), false);
        contextCache.invalidate(companyId);
        return appointment;
    }

    @Transactional
    public BarberQueueTicket updateStatus(UUID companyId, UUID id, String newStatusId) {
        BarberQueueStatus newStatus = BarberQueueStatus.fromId(newStatusId)
            .orElseThrow(InvalidStatusException::new);

        BarberQueueTicket current = queueRepository.findById(companyId, id)
            .orElseThrow(TicketNotFoundException::new);
        BarberQueueStatus from = BarberQueueStatus.fromId(current.status())
            .orElseThrow(InvalidStatusException::new);

        if (!from.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException();
        }

        boolean setCalledAt = newStatus == BarberQueueStatus.CHAMADO;
        queueRepository.updateStatus(companyId, id, newStatus.id(), setCalledAt);

        String text = newStatus.notificationText(current.barberName());
        notifier.notifyStatus(companyId, current.conversationId(), text);

        contextCache.invalidate(companyId);
        return queueRepository.findById(companyId, id)
            .map(t -> withDerived(companyId, t))
            .orElseThrow(TicketNotFoundException::new);
    }

    /**
     * Preenche position + etaMinutes DERIVADOS para um ticket. Só faz sentido pra 'aguardando' — pros
     * demais devolve sem posição (null). É aqui que a recomputação acontece (sem UPDATE no banco).
     */
    private BarberQueueTicket withDerived(UUID companyId, BarberQueueTicket t) {
        if (!"aguardando".equals(t.status())) {
            return t;
        }
        int ahead;
        int durationAhead;
        if (t.barberId() == null) {
            ahead = queueRepository.countAheadGeneral(companyId, t.enqueuedAt());
            durationAhead = queueRepository.sumDurationAhead(companyId, null, t.enqueuedAt());
        } else {
            ahead = queueRepository.countAheadForBarber(companyId, t.barberId(), t.enqueuedAt());
            durationAhead = queueRepository.sumDurationAhead(companyId, t.barberId(), t.enqueuedAt());
        }
        return t.withPosition(ahead + 1, durationAhead);
    }
}
