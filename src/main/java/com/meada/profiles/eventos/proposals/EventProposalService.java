package com.meada.profiles.eventos.proposals;

import com.meada.profiles.eventos.EventProposalStatus;
import com.meada.profiles.eventos.EventosContextCache;
import com.meada.profiles.eventos.planners.EventPlanner;
import com.meada.profiles.eventos.planners.EventPlannerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras das propostas de evento (camada 8.2) — CLONE do ServiceOrderService do Oficina.
 *
 * <p>{@link #open} valida o cerimonialista (ativo, se informado), tira snapshot de cliente (do
 * contact), e abre a proposta em 'rascunho' (total 0). Cliente NÃO é entidade própria (snapshots).
 *
 * <p>Itens de ORÇAMENTO ({@link #addItem}/{@link #updateItem}/{@link #deleteItem}) e marcos de
 * CRONOGRAMA ({@link #addTimelineItem}/{@link #updateTimelineItem}/{@link #deleteTimelineItem}): só
 * mutáveis enquanto a proposta NÃO estiver travada ({@link EventProposalStatus#itemsLocked()} —
 * fechada/realizada/recusada/cancelada → 409 proposal_locked). Itens de orçamento recalculam o
 * total_cents na mesma transação (no repo); marcos de cronograma NÃO entram no total.
 *
 * <p>{@link #updateStatus}: valida a transição; a passagem para 'orcada' exige total_cents &gt; 0
 * (400 empty_budget); notifica em orcada (com total + tipo de evento), aprovada, fechada, recusada.
 */
@Service
public class EventProposalService {

    private final EventProposalRepository repository;
    private final EventPlannerRepository plannerRepository;
    private final EventProposalNotifier notifier;
    private final com.meada.profiles.eventos.config.EventConfigRepository configRepository;
    private final EventosContextCache contextCache;

    public EventProposalService(EventProposalRepository repository,
                                EventPlannerRepository plannerRepository,
                                EventProposalNotifier notifier,
                                EventosContextCache contextCache,
                                com.meada.profiles.eventos.config.EventConfigRepository configRepository) {
        this.repository = repository;
        this.plannerRepository = plannerRepository;
        this.notifier = notifier;
        this.configRepository = configRepository;
        this.contextCache = contextCache;
    }

    public static class ProposalNotFoundException extends RuntimeException {}
    public static class PlannerNotFoundException extends RuntimeException {}
    public static class InactivePlannerException extends RuntimeException {}
    public static class ItemNotFoundException extends RuntimeException {}
    public static class TimelineItemNotFoundException extends RuntimeException {}
    public static class ProposalLockedException extends RuntimeException {}
    public static class EmptyBudgetException extends RuntimeException {}
    public static class InvalidStatusException extends RuntimeException {}
    public static class InvalidStatusTransitionException extends RuntimeException {}

    /** Abre uma proposta (status rascunho, total 0). Snapshot de cliente (do contact). */
    @Transactional
    public EventProposal open(UUID companyId, UUID contactId, String customerNameOverride, UUID plannerId,
                              UUID conversationId, String eventType, LocalDate eventDate, Integer guestCount,
                              String briefing, String notes) {
        if (plannerId != null) {
            EventPlanner p = plannerRepository.findById(companyId, plannerId).orElseThrow(PlannerNotFoundException::new);
            if (!p.active()) {
                throw new InactivePlannerException();
            }
        }
        String customerName = customerNameOverride != null && !customerNameOverride.isBlank()
            ? customerNameOverride.trim()
            : repository.contactName(companyId, contactId).orElse("Cliente");
        String customerPhone = repository.contactPhone(companyId, contactId).orElse(null);

        EventProposal created = repository.insertProposal(companyId, contactId, customerName, customerPhone,
            plannerId, conversationId, eventType, eventDate, guestCount, briefing, notes);
        contextCache.invalidate(companyId);
        return created;
    }

    public List<EventProposal> list(UUID companyId, String status, UUID plannerId, UUID contactId,
                                    LocalDate dateFrom, LocalDate dateTo, int limit, int offset) {
        return repository.listByCompany(companyId, status, plannerId, contactId, dateFrom, dateTo, limit, offset);
    }

    public long count(UUID companyId, String status, UUID plannerId, UUID contactId,
                      LocalDate dateFrom, LocalDate dateTo) {
        return repository.countByCompany(companyId, status, plannerId, contactId, dateFrom, dateTo);
    }

    public Optional<EventProposal> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }

    @Transactional
    public EventProposal updateFields(UUID companyId, UUID id, UUID plannerId, boolean plannerProvided,
                                      String eventType, LocalDate eventDate, boolean dateProvided,
                                      Integer guestCount, boolean guestProvided, String briefing, String notes) {
        if (plannerProvided && plannerId != null) {
            EventPlanner p = plannerRepository.findById(companyId, plannerId).orElseThrow(PlannerNotFoundException::new);
            if (!p.active()) {
                throw new InactivePlannerException();
            }
        }
        EventProposal updated = repository.updateFields(companyId, id, plannerId, plannerProvided, eventType,
            eventDate, dateProvided, guestCount, guestProvided, briefing, notes).orElseThrow(ProposalNotFoundException::new);
        contextCache.invalidate(companyId);
        return updated;
    }

    // -------------------------------------------------------------------------
    // ITENS DE ORÇAMENTO
    // -------------------------------------------------------------------------

    private void requireMutableProposal(UUID companyId, UUID proposalId) {
        EventProposal proposal = repository.findById(companyId, proposalId).orElseThrow(ProposalNotFoundException::new);
        EventProposalStatus status = EventProposalStatus.fromId(proposal.status()).orElseThrow(InvalidStatusException::new);
        if (status.itemsLocked()) {
            throw new ProposalLockedException();
        }
    }

    @Transactional
    public EventProposalItem addItem(UUID companyId, UUID proposalId, String description,
                                     int quantity, int unitPriceCents) {
        requireMutableProposal(companyId, proposalId);
        EventProposalItem item = repository.addItem(companyId, proposalId, description, quantity, unitPriceCents);
        contextCache.invalidate(companyId);
        return item;
    }

    @Transactional
    public EventProposalItem updateItem(UUID companyId, UUID proposalId, UUID itemId, String description,
                                        Integer quantity, Integer unitPriceCents) {
        requireMutableProposal(companyId, proposalId);
        EventProposalItem item = repository.updateItem(companyId, proposalId, itemId, description, quantity, unitPriceCents)
            .orElseThrow(ItemNotFoundException::new);
        contextCache.invalidate(companyId);
        return item;
    }

    @Transactional
    public void deleteItem(UUID companyId, UUID proposalId, UUID itemId) {
        requireMutableProposal(companyId, proposalId);
        if (!repository.deleteItem(companyId, proposalId, itemId)) {
            throw new ItemNotFoundException();
        }
        contextCache.invalidate(companyId);
    }

    // -------------------------------------------------------------------------
    // MARCOS DE CRONOGRAMA (a escapada) — mesma trava de estado, mas NÃO mexem no total.
    // -------------------------------------------------------------------------

    @Transactional
    public EventTimelineItem addTimelineItem(UUID companyId, UUID proposalId, LocalTime startTime,
                                             String title, String description) {
        requireMutableProposal(companyId, proposalId);
        EventTimelineItem item = repository.addTimelineItem(companyId, proposalId, startTime, title, description);
        contextCache.invalidate(companyId);
        return item;
    }

    @Transactional
    public EventTimelineItem updateTimelineItem(UUID companyId, UUID proposalId, UUID itemId,
                                                LocalTime startTime, boolean timeProvided, String title,
                                                String description, boolean descProvided) {
        requireMutableProposal(companyId, proposalId);
        EventTimelineItem item = repository.updateTimelineItem(companyId, proposalId, itemId, startTime,
            timeProvided, title, description, descProvided).orElseThrow(TimelineItemNotFoundException::new);
        contextCache.invalidate(companyId);
        return item;
    }

    @Transactional
    public void deleteTimelineItem(UUID companyId, UUID proposalId, UUID itemId) {
        requireMutableProposal(companyId, proposalId);
        if (!repository.deleteTimelineItem(companyId, proposalId, itemId)) {
            throw new TimelineItemNotFoundException();
        }
        contextCache.invalidate(companyId);
    }

    // -------------------------------------------------------------------------
    // STATUS
    // -------------------------------------------------------------------------

    /** Onda 1 (backlog #3): quantas propostas aprovada/fechada/realizada existem nesta data. */
    public long countOccupied(UUID companyId, java.time.LocalDate date, UUID excludeId) {
        return repository.countByEventDate(companyId, date, excludeId);
    }

    @Transactional
    public EventProposal updateStatus(UUID companyId, UUID id, String newStatusId) {
        EventProposalStatus newStatus = EventProposalStatus.fromId(newStatusId).orElseThrow(InvalidStatusException::new);
        EventProposal current = repository.findById(companyId, id).orElseThrow(ProposalNotFoundException::new);
        EventProposalStatus from = EventProposalStatus.fromId(current.status()).orElseThrow(InvalidStatusException::new);

        if (!from.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException();
        }
        // não dá pra orçar uma proposta sem item de orçamento (total derivado > 0).
        if (newStatus == EventProposalStatus.ORCADA && current.totalCents() <= 0) {
            throw new EmptyBudgetException();
        }

        repository.updateStatus(companyId, id, newStatus.id(), newStatus.isTerminal());

        String text = newStatus.notificationText(eventLabel(current), brl(current.totalCents()));
        notifier.notifyStatus(companyId, current.conversationId(), text);

        // Onda 1 (backlog #7): REALIZADA encadeia o pós-venda (agradecimento + avaliação +
        // indicação — relacionamento, sem promessa; toggle + review_link na config).
        if (newStatus == EventProposalStatus.REALIZADA) {
            var config = configRepository.findByCompany(companyId);
            if (config.postEventEnabled()) {
                StringBuilder pos = new StringBuilder("Esperamos que a festa tenha sido inesquecível! "
                    + "Obrigado por celebrar com a gente. 🎉 ");
                if (config.reviewLink() != null) {
                    pos.append("Se puder, deixe sua avaliação — ajuda muito: ")
                        .append(config.reviewLink()).append(" ");
                }
                pos.append("E se conhecer alguém planejando um evento, ficaremos felizes com a indicação!");
                notifier.notifyStatus(companyId, current.conversationId(), pos.toString());
            }
        }

        contextCache.invalidate(companyId);
        return repository.findById(companyId, id).orElseThrow(ProposalNotFoundException::new);
    }

    private static String eventLabel(EventProposal p) {
        if (p.eventType() != null && !p.eventType().isBlank()) {
            return "evento (" + p.eventType() + ")";
        }
        return "evento";
    }

    private static String brl(int cents) {
        return "R$ " + String.format("%d,%02d", cents / 100, cents % 100);
    }
}
