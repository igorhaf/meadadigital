package com.meada.whatsapp.profiles.viagens.proposals;

import com.meada.whatsapp.profiles.viagens.TravelProposalStatus;
import com.meada.whatsapp.profiles.viagens.ViagensContextCache;
import com.meada.whatsapp.profiles.viagens.consultants.TravelConsultant;
import com.meada.whatsapp.profiles.viagens.consultants.TravelConsultantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras das propostas de viagem (camada 8.18 / perfil viagens) — CLONE do EventProposalService
 * (chassi eventos 8.2).
 *
 * <p>{@link #open} valida o consultor (ativo, se informado), tira snapshot de cliente (do contact), e
 * abre a proposta em 'rascunho' (total 0). Cliente NÃO é entidade própria (snapshots).
 *
 * <p>Itens de COTAÇÃO ({@link #addItem}/{@link #updateItem}/{@link #deleteItem}) e dias de ITINERÁRIO
 * ({@link #addItineraryDay}/{@link #updateItineraryDay}/{@link #deleteItineraryDay}/
 * {@link #reorderItinerary}): só mutáveis enquanto a proposta NÃO estiver travada
 * ({@link TravelProposalStatus#itemsLocked()} — fechada/realizada/recusada/cancelada → 409
 * proposal_locked). Itens de cotação recalculam o total_cents na mesma transação (no repo); dias de
 * itinerário NÃO entram no total. O ITINERÁRIO é a escapada multi-dia — gerenciado SÓ no painel (sem
 * tag de IA), mas trava JUNTO com a cotação.
 *
 * <p>{@link #updateStatus}: valida a transição; a passagem para 'orcada' exige total_cents &gt; 0
 * (400 empty_budget); notifica em orcada (com total + destino), aprovada, fechada, recusada.
 */
@Service
public class TravelProposalService {

    private final TravelProposalRepository repository;
    private final TravelConsultantRepository consultantRepository;
    private final TravelProposalNotifier notifier;
    private final ViagensContextCache contextCache;

    public TravelProposalService(TravelProposalRepository repository,
                                 TravelConsultantRepository consultantRepository,
                                 TravelProposalNotifier notifier,
                                 ViagensContextCache contextCache) {
        this.repository = repository;
        this.consultantRepository = consultantRepository;
        this.notifier = notifier;
        this.contextCache = contextCache;
    }

    public static class ProposalNotFoundException extends RuntimeException {}
    public static class ConsultantNotFoundException extends RuntimeException {}
    public static class InactiveConsultantException extends RuntimeException {}
    public static class ItemNotFoundException extends RuntimeException {}
    public static class ItineraryDayNotFoundException extends RuntimeException {}
    public static class ProposalLockedException extends RuntimeException {}
    public static class EmptyBudgetException extends RuntimeException {}
    public static class InvalidStatusException extends RuntimeException {}
    public static class InvalidStatusTransitionException extends RuntimeException {}

    /** Abre uma proposta (status rascunho, total 0). Snapshot de cliente (do contact). */
    @Transactional
    public TravelProposal open(UUID companyId, UUID contactId, String customerNameOverride, UUID consultantId,
                               UUID conversationId, String destination, LocalDate startDate, LocalDate endDate,
                               Integer numTravelers, String travelStyle, String briefing, String notes) {
        if (consultantId != null) {
            TravelConsultant c = consultantRepository.findById(companyId, consultantId)
                .orElseThrow(ConsultantNotFoundException::new);
            if (!c.active()) {
                throw new InactiveConsultantException();
            }
        }
        String customerName = customerNameOverride != null && !customerNameOverride.isBlank()
            ? customerNameOverride.trim()
            : repository.contactName(companyId, contactId).orElse("Cliente");
        String customerPhone = repository.contactPhone(companyId, contactId).orElse(null);

        TravelProposal created = repository.insertProposal(companyId, contactId, customerName, customerPhone,
            consultantId, conversationId, destination, startDate, endDate, numTravelers, travelStyle, briefing, notes);
        contextCache.invalidate(companyId);
        return created;
    }

    public List<TravelProposal> list(UUID companyId, String status, UUID consultantId, UUID contactId,
                                     LocalDate dateFrom, LocalDate dateTo, int limit, int offset) {
        return repository.listByCompany(companyId, status, consultantId, contactId, dateFrom, dateTo, limit, offset);
    }

    public long count(UUID companyId, String status, UUID consultantId, UUID contactId,
                      LocalDate dateFrom, LocalDate dateTo) {
        return repository.countByCompany(companyId, status, consultantId, contactId, dateFrom, dateTo);
    }

    public Optional<TravelProposal> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }

    @Transactional
    public TravelProposal updateFields(UUID companyId, UUID id, UUID consultantId, boolean consultantProvided,
                                       String destination, boolean destinationProvided, LocalDate startDate,
                                       boolean startProvided, LocalDate endDate, boolean endProvided,
                                       Integer numTravelers, boolean travelersProvided, String travelStyle,
                                       boolean styleProvided, String briefing, String notes) {
        if (consultantProvided && consultantId != null) {
            TravelConsultant c = consultantRepository.findById(companyId, consultantId)
                .orElseThrow(ConsultantNotFoundException::new);
            if (!c.active()) {
                throw new InactiveConsultantException();
            }
        }
        TravelProposal updated = repository.updateFields(companyId, id, consultantId, consultantProvided, destination,
            destinationProvided, startDate, startProvided, endDate, endProvided, numTravelers, travelersProvided,
            travelStyle, styleProvided, briefing, notes).orElseThrow(ProposalNotFoundException::new);
        contextCache.invalidate(companyId);
        return updated;
    }

    // -------------------------------------------------------------------------
    // ITENS DE COTAÇÃO
    // -------------------------------------------------------------------------

    private void requireMutableProposal(UUID companyId, UUID proposalId) {
        TravelProposal proposal = repository.findById(companyId, proposalId).orElseThrow(ProposalNotFoundException::new);
        TravelProposalStatus status = TravelProposalStatus.fromId(proposal.status()).orElseThrow(InvalidStatusException::new);
        if (status.itemsLocked()) {
            throw new ProposalLockedException();
        }
    }

    @Transactional
    public TravelProposalItem addItem(UUID companyId, UUID proposalId, String category, String description,
                                      int quantity, int unitPriceCents) {
        requireMutableProposal(companyId, proposalId);
        TravelProposalItem item = repository.addItem(companyId, proposalId, category, description, quantity, unitPriceCents);
        contextCache.invalidate(companyId);
        return item;
    }

    @Transactional
    public TravelProposalItem updateItem(UUID companyId, UUID proposalId, UUID itemId, String category,
                                         String description, Integer quantity, Integer unitPriceCents) {
        requireMutableProposal(companyId, proposalId);
        TravelProposalItem item = repository.updateItem(companyId, proposalId, itemId, category, description, quantity, unitPriceCents)
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
    // DIAS DE ITINERÁRIO (a escapada multi-dia) — mesma trava de estado, mas NÃO mexem no total.
    // -------------------------------------------------------------------------

    @Transactional
    public TravelItineraryDay addItineraryDay(UUID companyId, UUID proposalId, LocalDate dayDate,
                                              String title, String description) {
        requireMutableProposal(companyId, proposalId);
        TravelItineraryDay day = repository.addItineraryDay(companyId, proposalId, dayDate, title, description);
        contextCache.invalidate(companyId);
        return day;
    }

    @Transactional
    public TravelItineraryDay updateItineraryDay(UUID companyId, UUID proposalId, UUID dayId, Integer dayNumber,
                                                 boolean dayNumberProvided, LocalDate dayDate, boolean dateProvided,
                                                 String title, String description, boolean descProvided) {
        requireMutableProposal(companyId, proposalId);
        TravelItineraryDay day = repository.updateItineraryDay(companyId, proposalId, dayId, dayNumber, dayNumberProvided,
            dayDate, dateProvided, title, description, descProvided).orElseThrow(ItineraryDayNotFoundException::new);
        contextCache.invalidate(companyId);
        return day;
    }

    @Transactional
    public void deleteItineraryDay(UUID companyId, UUID proposalId, UUID dayId) {
        requireMutableProposal(companyId, proposalId);
        if (!repository.deleteItineraryDay(companyId, proposalId, dayId)) {
            throw new ItineraryDayNotFoundException();
        }
        contextCache.invalidate(companyId);
    }

    /**
     * Re-ordena o itinerário, re-materializando day_number sequencial 1..N na ordem recebida (mesma
     * transação). Sob a mesma trava (proposal_locked). Algum id não pertencente à proposta →
     * ItineraryDayNotFoundException.
     */
    @Transactional
    public List<TravelItineraryDay> reorderItinerary(UUID companyId, UUID proposalId, List<UUID> orderedIds) {
        requireMutableProposal(companyId, proposalId);
        if (!repository.reorderItinerary(companyId, proposalId, orderedIds)) {
            throw new ItineraryDayNotFoundException();
        }
        contextCache.invalidate(companyId);
        return repository.listItinerary(proposalId);
    }

    // -------------------------------------------------------------------------
    // STATUS
    // -------------------------------------------------------------------------

    @Transactional
    public TravelProposal updateStatus(UUID companyId, UUID id, String newStatusId) {
        TravelProposalStatus newStatus = TravelProposalStatus.fromId(newStatusId).orElseThrow(InvalidStatusException::new);
        TravelProposal current = repository.findById(companyId, id).orElseThrow(ProposalNotFoundException::new);
        TravelProposalStatus from = TravelProposalStatus.fromId(current.status()).orElseThrow(InvalidStatusException::new);

        if (!from.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException();
        }
        // não dá pra orçar uma proposta sem item de cotação (total derivado > 0).
        if (newStatus == TravelProposalStatus.ORCADA && current.totalCents() <= 0) {
            throw new EmptyBudgetException();
        }

        repository.updateStatus(companyId, id, newStatus.id(), newStatus.isTerminal());

        String text = newStatus.notificationText(travelLabel(current), brl(current.totalCents()));
        notifier.notifyStatus(companyId, current.conversationId(), text);

        contextCache.invalidate(companyId);
        return repository.findById(companyId, id).orElseThrow(ProposalNotFoundException::new);
    }

    private static String travelLabel(TravelProposal p) {
        if (p.destination() != null && !p.destination().isBlank()) {
            return "viagem para " + p.destination();
        }
        return "viagem";
    }

    private static String brl(int cents) {
        return "R$ " + String.format("%d,%02d", cents / 100, cents % 100);
    }
}
