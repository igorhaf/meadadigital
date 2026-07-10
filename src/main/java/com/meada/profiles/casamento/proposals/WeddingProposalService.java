package com.meada.profiles.casamento.proposals;

import com.meada.profiles.casamento.CasamentoContextCache;
import com.meada.profiles.casamento.WeddingProposalStatus;
import com.meada.profiles.casamento.coupons.WeddingCoupon;
import com.meada.profiles.casamento.coupons.WeddingCouponRepository;
import com.meada.profiles.casamento.payments.WeddingPaymentRepository;
import com.meada.profiles.casamento.planners.WeddingPlanner;
import com.meada.profiles.casamento.planners.WeddingPlannerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras das propostas de casamento (camada 8.7) — CLONE do EventProposalService + a 3ª sub-entidade
 * (checklist).
 *
 * <p>{@link #open} valida o assessor (ativo, se informado), tira snapshot de cliente (do contact), e
 * abre a proposta em 'rascunho' (total 0). Cliente NÃO é entidade própria (snapshots).
 *
 * <p>Itens de ORÇAMENTO, marcos de CRONOGRAMA e tarefas de CHECKLIST: só mutáveis enquanto a proposta
 * NÃO estiver travada ({@link WeddingProposalStatus#itemsLocked()} — fechada/realizada/recusada/
 * cancelada → 409 proposal_locked). Itens de orçamento recalculam o total_cents na mesma transação (no
 * repo); cronograma e checklist NÃO entram no total.
 *
 * <p>{@link #updateStatus}: valida a transição; a passagem para 'orcada' exige total_cents &gt; 0
 * (400 empty_budget); notifica em orcada (com total + estilo), aprovada, fechada, recusada.
 */
@Service
public class WeddingProposalService {

    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");

    private final WeddingProposalRepository repository;
    private final WeddingPlannerRepository plannerRepository;
    private final WeddingCouponRepository couponRepository;
    private final WeddingPaymentRepository paymentRepository;
    private final WeddingProposalNotifier notifier;
    private final com.meada.profiles.casamento.config.WeddingConfigRepository configRepository;
    private final CasamentoContextCache contextCache;

    public WeddingProposalService(WeddingProposalRepository repository,
                                  WeddingPlannerRepository plannerRepository,
                                  WeddingCouponRepository couponRepository,
                                  WeddingPaymentRepository paymentRepository,
                                  WeddingProposalNotifier notifier,
                                  CasamentoContextCache contextCache,
                                  com.meada.profiles.casamento.config.WeddingConfigRepository configRepository) {
        this.repository = repository;
        this.plannerRepository = plannerRepository;
        this.couponRepository = couponRepository;
        this.paymentRepository = paymentRepository;
        this.notifier = notifier;
        this.configRepository = configRepository;
        this.contextCache = contextCache;
    }

    public static class ProposalNotFoundException extends RuntimeException {}
    public static class PlannerNotFoundException extends RuntimeException {}
    public static class InactivePlannerException extends RuntimeException {}
    public static class ItemNotFoundException extends RuntimeException {}
    public static class TimelineItemNotFoundException extends RuntimeException {}
    public static class ChecklistTaskNotFoundException extends RuntimeException {}
    public static class ProposalLockedException extends RuntimeException {}
    public static class EmptyBudgetException extends RuntimeException {}
    public static class InvalidStatusException extends RuntimeException {}
    public static class InvalidStatusTransitionException extends RuntimeException {}
    public static class InvalidProposalCouponException extends RuntimeException {}
    public static class DepositRequiredException extends RuntimeException {}

    /** Abre uma proposta (status rascunho, total 0). Snapshot de cliente (do contact). */
    @Transactional
    public WeddingProposal open(UUID companyId, UUID contactId, String customerNameOverride, UUID plannerId,
                                UUID conversationId, String weddingStyle, LocalDate weddingDate, Integer guestCount,
                                String briefing, String notes) {
        if (plannerId != null) {
            WeddingPlanner p = plannerRepository.findById(companyId, plannerId).orElseThrow(PlannerNotFoundException::new);
            if (!p.active()) {
                throw new InactivePlannerException();
            }
        }
        String customerName = customerNameOverride != null && !customerNameOverride.isBlank()
            ? customerNameOverride.trim()
            : repository.contactName(companyId, contactId).orElse("Cliente");
        String customerPhone = repository.contactPhone(companyId, contactId).orElse(null);

        WeddingProposal created = repository.insertProposal(companyId, contactId, customerName, customerPhone,
            plannerId, conversationId, weddingStyle, weddingDate, guestCount, briefing, notes);
        contextCache.invalidate(companyId);
        return created;
    }

    public List<WeddingProposal> list(UUID companyId, String status, UUID plannerId, UUID contactId,
                                      LocalDate dateFrom, LocalDate dateTo, int limit, int offset) {
        return repository.listByCompany(companyId, status, plannerId, contactId, dateFrom, dateTo, limit, offset);
    }

    public long count(UUID companyId, String status, UUID plannerId, UUID contactId,
                      LocalDate dateFrom, LocalDate dateTo) {
        return repository.countByCompany(companyId, status, plannerId, contactId, dateFrom, dateTo);
    }

    public Optional<WeddingProposal> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }

    @Transactional
    public WeddingProposal updateFields(UUID companyId, UUID id, UUID plannerId, boolean plannerProvided,
                                        String weddingStyle, LocalDate weddingDate, boolean dateProvided,
                                        Integer guestCount, boolean guestProvided, String briefing, String notes) {
        if (plannerProvided && plannerId != null) {
            WeddingPlanner p = plannerRepository.findById(companyId, plannerId).orElseThrow(PlannerNotFoundException::new);
            if (!p.active()) {
                throw new InactivePlannerException();
            }
        }
        WeddingProposal updated = repository.updateFields(companyId, id, plannerId, plannerProvided, weddingStyle,
            weddingDate, dateProvided, guestCount, guestProvided, briefing, notes).orElseThrow(ProposalNotFoundException::new);
        contextCache.invalidate(companyId);
        return updated;
    }

    // -------------------------------------------------------------------------
    // Trava de estado: itens de orçamento, cronograma E checklist só mutáveis antes de 'fechada'.
    // -------------------------------------------------------------------------

    private void requireMutableProposal(UUID companyId, UUID proposalId) {
        WeddingProposal proposal = repository.findById(companyId, proposalId).orElseThrow(ProposalNotFoundException::new);
        WeddingProposalStatus status = WeddingProposalStatus.fromId(proposal.status()).orElseThrow(InvalidStatusException::new);
        if (status.itemsLocked()) {
            throw new ProposalLockedException();
        }
    }

    // -------------------------------------------------------------------------
    // ITENS DE ORÇAMENTO
    // -------------------------------------------------------------------------

    @Transactional
    public WeddingProposalItem addItem(UUID companyId, UUID proposalId, String description,
                                       int quantity, int unitPriceCents) {
        requireMutableProposal(companyId, proposalId);
        WeddingProposalItem item = repository.addItem(companyId, proposalId, description, quantity, unitPriceCents);
        contextCache.invalidate(companyId);
        return item;
    }

    @Transactional
    public WeddingProposalItem updateItem(UUID companyId, UUID proposalId, UUID itemId, String description,
                                          Integer quantity, Integer unitPriceCents) {
        requireMutableProposal(companyId, proposalId);
        WeddingProposalItem item = repository.updateItem(companyId, proposalId, itemId, description, quantity, unitPriceCents)
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
    // MARCOS DE CRONOGRAMA — mesma trava de estado, mas NÃO mexem no total.
    // -------------------------------------------------------------------------

    @Transactional
    public WeddingTimelineItem addTimelineItem(UUID companyId, UUID proposalId, LocalTime startTime,
                                               String title, String description) {
        requireMutableProposal(companyId, proposalId);
        WeddingTimelineItem item = repository.addTimelineItem(companyId, proposalId, startTime, title, description);
        contextCache.invalidate(companyId);
        return item;
    }

    @Transactional
    public WeddingTimelineItem updateTimelineItem(UUID companyId, UUID proposalId, UUID itemId,
                                                  LocalTime startTime, boolean timeProvided, String title,
                                                  String description, boolean descProvided) {
        requireMutableProposal(companyId, proposalId);
        WeddingTimelineItem item = repository.updateTimelineItem(companyId, proposalId, itemId, startTime,
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
    // CHECKLIST PRÉ-CASAMENTO (a escapada) — mesma trava de estado, NÃO mexe no total. Estado binário.
    // -------------------------------------------------------------------------

    @Transactional
    public WeddingChecklistTask addChecklistTask(UUID companyId, UUID proposalId, String title,
                                                 String description, LocalDate dueDate) {
        requireMutableProposal(companyId, proposalId);
        WeddingChecklistTask task = repository.addChecklistTask(companyId, proposalId, title, description, dueDate);
        contextCache.invalidate(companyId);
        return task;
    }

    @Transactional
    public WeddingChecklistTask updateChecklistTask(UUID companyId, UUID proposalId, UUID taskId,
                                                    String title, String description, boolean descProvided,
                                                    LocalDate dueDate, boolean dueProvided) {
        requireMutableProposal(companyId, proposalId);
        WeddingChecklistTask task = repository.updateChecklistTask(companyId, proposalId, taskId, title,
            description, descProvided, dueDate, dueProvided).orElseThrow(ChecklistTaskNotFoundException::new);
        contextCache.invalidate(companyId);
        return task;
    }

    @Transactional
    public void deleteChecklistTask(UUID companyId, UUID proposalId, UUID taskId) {
        requireMutableProposal(companyId, proposalId);
        if (!repository.deleteChecklistTask(companyId, proposalId, taskId)) {
            throw new ChecklistTaskNotFoundException();
        }
        contextCache.invalidate(companyId);
    }

    /** Marca/desmarca a tarefa (binário pendente⇄concluída). done=true grava done_at; false zera. */
    @Transactional
    public WeddingChecklistTask toggleChecklistTask(UUID companyId, UUID proposalId, UUID taskId, boolean done) {
        requireMutableProposal(companyId, proposalId);
        // garante que a tarefa pertence à proposta informada antes de alternar.
        repository.findChecklistTask(companyId, proposalId, taskId).orElseThrow(ChecklistTaskNotFoundException::new);
        WeddingChecklistTask task = repository.toggleChecklistTask(companyId, taskId, done)
            .orElseThrow(ChecklistTaskNotFoundException::new);
        contextCache.invalidate(companyId);
        return task;
    }

    // -------------------------------------------------------------------------
    // CUPOM NA PROPOSTA (onda 1, backlog #10) — aplicado PELO PAINEL; a IA não negocia preço.
    // -------------------------------------------------------------------------

    /**
     * Aplica um cupom pelo code (case-insensitive): valida active + validade + mínimo (sobre o
     * total do orçamento) + max_uses, grava o vínculo + snapshot, re-deriva o desconto e incrementa
     * uses — tudo na MESMA transação. Cupom inválido → 400 invalid_coupon. Proposta com cupom já
     * aplicado troca de cupom (o anterior tem o uso devolvido). Espelho do applyCoupon do ateliê.
     */
    @Transactional
    public WeddingProposal applyCoupon(UUID companyId, UUID proposalId, String code) {
        requireMutableProposal(companyId, proposalId);
        WeddingProposal current = repository.findById(companyId, proposalId).orElseThrow(ProposalNotFoundException::new);
        WeddingCoupon coupon = couponRepository.findByCode(companyId, code)
            .orElseThrow(InvalidProposalCouponException::new);
        boolean expired = coupon.validUntil() != null && coupon.validUntil().isBefore(LocalDate.now(TENANT_ZONE));
        boolean maxedOut = coupon.maxUses() != null && coupon.uses() >= coupon.maxUses();
        boolean belowMin = current.totalCents() < coupon.minOrderCents();
        if (!coupon.active() || expired || maxedOut || belowMin) {
            throw new InvalidProposalCouponException();
        }
        if (current.couponId() != null) {
            couponRepository.decrementUses(companyId, current.couponId());
        }
        repository.applyCoupon(companyId, proposalId, coupon.id(), coupon.code());
        couponRepository.incrementUses(companyId, coupon.id());
        contextCache.invalidate(companyId);
        return repository.findById(companyId, proposalId).orElseThrow(ProposalNotFoundException::new);
    }

    /** Remove o cupom da proposta (zera desconto) e devolve o uso ao cupom. */
    @Transactional
    public WeddingProposal removeCoupon(UUID companyId, UUID proposalId) {
        requireMutableProposal(companyId, proposalId);
        WeddingProposal current = repository.findById(companyId, proposalId).orElseThrow(ProposalNotFoundException::new);
        if (current.couponId() != null) {
            couponRepository.decrementUses(companyId, current.couponId());
        }
        repository.removeCoupon(companyId, proposalId);
        contextCache.invalidate(companyId);
        return repository.findById(companyId, proposalId).orElseThrow(ProposalNotFoundException::new);
    }

    // -------------------------------------------------------------------------
    // STATUS
    // -------------------------------------------------------------------------

    @Transactional
    public WeddingProposal updateStatus(UUID companyId, UUID id, String newStatusId) {
        WeddingProposalStatus newStatus = WeddingProposalStatus.fromId(newStatusId).orElseThrow(InvalidStatusException::new);
        WeddingProposal current = repository.findById(companyId, id).orElseThrow(ProposalNotFoundException::new);
        WeddingProposalStatus from = WeddingProposalStatus.fromId(current.status()).orElseThrow(InvalidStatusException::new);

        if (!from.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException();
        }
        // não dá pra orçar uma proposta sem item de orçamento (total derivado > 0).
        if (newStatus == WeddingProposalStatus.ORCADA && current.totalCents() <= 0) {
            throw new EmptyBudgetException();
        }
        // GATE DE SINAL (onda 1, backlog #1): com plano de pagamento contendo 'sinal' NÃO pago, o
        // fechamento é bloqueado — "fechada = sinal recebido". Sem sinal no plano, fechamento livre.
        if (newStatus == WeddingProposalStatus.FECHADA && paymentRepository.existsUnpaidSinal(companyId, id)) {
            throw new DepositRequiredException();
        }

        repository.updateStatus(companyId, id, newStatus.id(), newStatus.isTerminal());

        // total LÍQUIDO na notificação (cupom aplicado pelo painel — onda 1, backlog #10).
        String text = newStatus.notificationText(styleLabel(current),
            brl(current.totalCents() - current.discountCents()));
        notifier.notifyStatus(companyId, current.conversationId(), text);

        // Onda 2 (backlog #6): o grande dia aconteceu — pós-casamento no auge da emoção
        // (agradecimento + avaliação + indicação; toggle + review_link na config).
        if (newStatus == WeddingProposalStatus.REALIZADA) {
            var config = configRepository.findByCompany(companyId);
            if (config.postEventEnabled()) {
                StringBuilder pos = new StringBuilder("Que dia inesquecível! Foi uma honra fazer "
                    + "parte do casamento de vocês. 💍 Obrigado pela confiança! ");
                if (config.reviewLink() != null) {
                    pos.append("Se puderem, deixem um depoimento — ajuda muito os próximos casais: ")
                        .append(config.reviewLink()).append(" ");
                }
                pos.append("E se conhecerem alguém noivando, ficaremos felizes com a indicação!");
                notifier.notifyStatus(companyId, current.conversationId(), pos.toString());
            }
        }

        contextCache.invalidate(companyId);
        return repository.findById(companyId, id).orElseThrow(ProposalNotFoundException::new);
    }

    private static String styleLabel(WeddingProposal p) {
        if (p.weddingStyle() != null && !p.weddingStyle().isBlank()) {
            return "(" + p.weddingStyle() + ")";
        }
        return "";
    }

    private static String brl(int cents) {
        return "R$ " + String.format("%d,%02d", cents / 100, cents % 100);
    }
}
