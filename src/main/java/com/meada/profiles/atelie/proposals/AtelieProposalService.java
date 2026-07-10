package com.meada.profiles.atelie.proposals;

import com.meada.profiles.atelie.AtelieContextCache;
import com.meada.profiles.atelie.AtelieProjectType;
import com.meada.profiles.atelie.AtelieProposalStatus;
import com.meada.profiles.atelie.artisans.AtelieArtisan;
import com.meada.profiles.atelie.artisans.AtelieArtisanRepository;
import com.meada.profiles.atelie.coupons.AtelieCoupon;
import com.meada.profiles.atelie.coupons.AtelieCouponRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras das propostas de ateliê (camada 8.14) — CLONE do EventProposalService do Eventos.
 *
 * <p>{@link #open} valida o artesão (ativo, se informado), normaliza o project_type
 * (costura|arte|design, default 'costura'), tira snapshot de cliente (do contact), e abre a proposta
 * em 'rascunho' (total 0). Cliente NÃO é entidade própria (snapshots).
 *
 * <p>Itens de ORÇAMENTO ({@link #addItem}/{@link #updateItem}/{@link #deleteItem}) e provas/ajustes
 * ({@link #addFitting}/{@link #updateFitting}/{@link #deleteFitting}/{@link #reorderFittings}/
 * {@link #transitionFitting}): só mutáveis enquanto a proposta NÃO estiver travada
 * ({@link AtelieProposalStatus#itemsLocked()} — fechada/realizada/recusada/cancelada → 409
 * proposal_locked). Itens de orçamento recalculam o total_cents na mesma transação (no repo);
 * provas/ajustes NÃO entram no total.
 *
 * <p>{@link #updateStatus}: valida a transição; a passagem para 'orcada' exige total_cents &gt; 0
 * (400 empty_budget); notifica em orcada (com total + tipo de projeto), aprovada, fechada, recusada.
 */
@Service
public class AtelieProposalService {

    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");

    private final AtelieProposalRepository repository;
    private final AtelieArtisanRepository artisanRepository;
    private final AtelieCouponRepository couponRepository;
    private final AtelieProposalNotifier notifier;
    private final com.meada.profiles.atelie.config.AtelieConfigRepository configRepository;
    private final AtelieContextCache contextCache;

    public AtelieProposalService(AtelieProposalRepository repository,
                                 AtelieArtisanRepository artisanRepository,
                                 AtelieCouponRepository couponRepository,
                                 AtelieProposalNotifier notifier,
                                 AtelieContextCache contextCache,
                                com.meada.profiles.atelie.config.AtelieConfigRepository configRepository) {
        this.repository = repository;
        this.artisanRepository = artisanRepository;
        this.couponRepository = couponRepository;
        this.notifier = notifier;
        this.configRepository = configRepository;
        this.contextCache = contextCache;
    }

    public static class ProposalNotFoundException extends RuntimeException {}
    public static class ArtisanNotFoundException extends RuntimeException {}
    public static class InactiveArtisanException extends RuntimeException {}
    public static class ItemNotFoundException extends RuntimeException {}
    public static class FittingNotFoundException extends RuntimeException {}
    public static class ProposalLockedException extends RuntimeException {}
    public static class EmptyBudgetException extends RuntimeException {}
    public static class InvalidDateException extends RuntimeException {}
    public static class InvalidStatusException extends RuntimeException {}
    public static class InvalidStatusTransitionException extends RuntimeException {}
    public static class InvalidFittingStatusException extends RuntimeException {}
    public static class InvalidDepositException extends RuntimeException {}
    public static class DepositRequiredException extends RuntimeException {}
    public static class InvalidProposalCouponException extends RuntimeException {}

    /** Normaliza o project_type contra {@link AtelieProjectType}; ausente/inválido → 'costura'. */
    private static String normalizeProjectType(String raw) {
        return AtelieProjectType.fromId(raw).map(AtelieProjectType::id).orElse(AtelieProjectType.COSTURA.id());
    }

    /** Abre uma proposta (status rascunho, total 0). Snapshot de cliente (do contact). */
    @Transactional
    public AtelieProposal open(UUID companyId, UUID contactId, String customerNameOverride, UUID artisanId,
                               UUID conversationId, String projectType, String occasion, LocalDate estimatedDate,
                               String briefing, String notes) {
        if (artisanId != null) {
            AtelieArtisan a = artisanRepository.findById(companyId, artisanId).orElseThrow(ArtisanNotFoundException::new);
            if (!a.active()) {
                throw new InactiveArtisanException();
            }
        }
        String customerName = customerNameOverride != null && !customerNameOverride.isBlank()
            ? customerNameOverride.trim()
            : repository.contactName(companyId, contactId).orElse("Cliente");
        String customerPhone = repository.contactPhone(companyId, contactId).orElse(null);

        AtelieProposal created = repository.insertProposal(companyId, contactId, customerName, customerPhone,
            artisanId, conversationId, normalizeProjectType(projectType), occasion, estimatedDate, briefing, notes);
        contextCache.invalidate(companyId);
        return created;
    }

    public List<AtelieProposal> list(UUID companyId, String status, UUID artisanId, UUID contactId,
                                     String projectType, int limit, int offset) {
        return repository.listByCompany(companyId, status, artisanId, contactId, projectType, limit, offset);
    }

    public long count(UUID companyId, String status, UUID artisanId, UUID contactId, String projectType) {
        return repository.countByCompany(companyId, status, artisanId, contactId, projectType);
    }

    public Optional<AtelieProposal> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }

    @Transactional
    public AtelieProposal updateFields(UUID companyId, UUID id, UUID artisanId, boolean artisanProvided,
                                       String projectType, String occasion, LocalDate estimatedDate,
                                       boolean dateProvided, String briefing, String notes) {
        if (artisanProvided && artisanId != null) {
            AtelieArtisan a = artisanRepository.findById(companyId, artisanId).orElseThrow(ArtisanNotFoundException::new);
            if (!a.active()) {
                throw new InactiveArtisanException();
            }
        }
        String normalizedType = projectType == null ? null : normalizeProjectType(projectType);
        AtelieProposal updated = repository.updateFields(companyId, id, artisanId, artisanProvided, normalizedType,
            occasion, estimatedDate, dateProvided, briefing, notes).orElseThrow(ProposalNotFoundException::new);
        contextCache.invalidate(companyId);
        return updated;
    }

    // -------------------------------------------------------------------------
    // ITENS DE ORÇAMENTO
    // -------------------------------------------------------------------------

    private void requireMutableProposal(UUID companyId, UUID proposalId) {
        AtelieProposal proposal = repository.findById(companyId, proposalId).orElseThrow(ProposalNotFoundException::new);
        AtelieProposalStatus status = AtelieProposalStatus.fromId(proposal.status()).orElseThrow(InvalidStatusException::new);
        if (status.itemsLocked()) {
            throw new ProposalLockedException();
        }
    }

    @Transactional
    public AtelieProposalItem addItem(UUID companyId, UUID proposalId, String description,
                                      int quantity, int unitPriceCents) {
        requireMutableProposal(companyId, proposalId);
        AtelieProposalItem item = repository.addItem(companyId, proposalId, description, quantity, unitPriceCents);
        contextCache.invalidate(companyId);
        return item;
    }

    @Transactional
    public AtelieProposalItem updateItem(UUID companyId, UUID proposalId, UUID itemId, String description,
                                         Integer quantity, Integer unitPriceCents) {
        requireMutableProposal(companyId, proposalId);
        AtelieProposalItem item = repository.updateItem(companyId, proposalId, itemId, description, quantity, unitPriceCents)
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
    // PROVAS/AJUSTES (a escapada) — mesma trava de estado, mas NÃO mexem no total.
    // -------------------------------------------------------------------------

    @Transactional
    public AtelieFitting addFitting(UUID companyId, UUID proposalId, String title, String description,
                                    LocalDate dueDate) {
        requireMutableProposal(companyId, proposalId);
        AtelieFitting fitting = repository.addFitting(companyId, proposalId, title, description, dueDate);
        contextCache.invalidate(companyId);
        return fitting;
    }

    @Transactional
    public AtelieFitting updateFitting(UUID companyId, UUID proposalId, UUID fittingId, String title,
                                       String description, boolean descProvided, LocalDate dueDate,
                                       boolean dueProvided) {
        requireMutableProposal(companyId, proposalId);
        AtelieFitting fitting = repository.updateFitting(companyId, proposalId, fittingId, title, description,
            descProvided, dueDate, dueProvided).orElseThrow(FittingNotFoundException::new);
        contextCache.invalidate(companyId);
        return fitting;
    }

    @Transactional
    public void deleteFitting(UUID companyId, UUID proposalId, UUID fittingId) {
        requireMutableProposal(companyId, proposalId);
        if (!repository.deleteFitting(companyId, proposalId, fittingId)) {
            throw new FittingNotFoundException();
        }
        contextCache.invalidate(companyId);
    }

    @Transactional
    public List<AtelieFitting> reorderFittings(UUID companyId, UUID proposalId, List<UUID> orderedIds) {
        requireMutableProposal(companyId, proposalId);
        repository.reorderFittings(companyId, proposalId, orderedIds);
        contextCache.invalidate(companyId);
        return repository.listFittings(proposalId);
    }

    /** Transição BINÁRIA pendente⇄realizada (livre). 'realizada' grava completed_at; 'pendente' zera. */
    @Transactional
    public AtelieFitting transitionFitting(UUID companyId, UUID proposalId, UUID fittingId, String newStatus) {
        requireMutableProposal(companyId, proposalId);
        if (newStatus == null || (!"pendente".equals(newStatus) && !"realizada".equals(newStatus))) {
            throw new InvalidFittingStatusException();
        }
        // garante que a prova pertence à proposta informada antes de transicionar.
        repository.findFitting(companyId, proposalId, fittingId).orElseThrow(FittingNotFoundException::new);
        AtelieFitting fitting = repository.transitionFitting(companyId, fittingId, newStatus)
            .orElseThrow(FittingNotFoundException::new);
        contextCache.invalidate(companyId);
        return fitting;
    }

    // -------------------------------------------------------------------------
    // CUPOM NA PROPOSTA (onda 2, backlog #13) — aplicado PELO PAINEL; a IA não toca preço.
    // -------------------------------------------------------------------------

    /**
     * Aplica um cupom pelo code (case-insensitive): valida active + validade + mínimo (sobre o
     * total do orçamento) + max_uses, grava o vínculo + snapshot, re-deriva o desconto e incrementa
     * uses — tudo na MESMA transação. Cupom inválido → 400 invalid_coupon (no painel o erro é
     * explícito; ≠ adega, onde a IA passa o cupom e o inválido é silencioso). Proposta com cupom já
     * aplicado troca de cupom (o anterior tem o uso devolvido).
     */
    @Transactional
    public AtelieProposal applyCoupon(UUID companyId, UUID proposalId, String code) {
        requireMutableProposal(companyId, proposalId);
        AtelieProposal current = repository.findById(companyId, proposalId).orElseThrow(ProposalNotFoundException::new);
        AtelieCoupon coupon = couponRepository.findByCode(companyId, code)
            .orElseThrow(InvalidProposalCouponException::new);
        boolean expired = coupon.validUntil() != null && coupon.validUntil().isBefore(LocalDate.now(TENANT_ZONE));
        boolean maxedOut = coupon.maxUses() != null && coupon.uses() >= coupon.maxUses();
        boolean belowMin = current.totalCents() < coupon.minOrderCents();
        if (!coupon.active() || expired || maxedOut || belowMin) {
            throw new InvalidProposalCouponException();
        }
        // troca de cupom: devolve o uso do anterior antes de aplicar o novo.
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
    public AtelieProposal removeCoupon(UUID companyId, UUID proposalId) {
        requireMutableProposal(companyId, proposalId);
        AtelieProposal current = repository.findById(companyId, proposalId).orElseThrow(ProposalNotFoundException::new);
        if (current.couponId() != null) {
            couponRepository.decrementUses(companyId, current.couponId());
        }
        repository.removeCoupon(companyId, proposalId);
        contextCache.invalidate(companyId);
        return repository.findById(companyId, proposalId).orElseThrow(ProposalNotFoundException::new);
    }

    // -------------------------------------------------------------------------
    // SINAL/ENTRADA (onda backlog #2) — registro manual até o gateway #50.
    // -------------------------------------------------------------------------

    /**
     * Registra o sinal combinado e/ou marca como recebido. null/0 = sem sinal (fechamento livre).
     * Marcar como pago EXIGE valor registrado (&gt; 0) — senão 400 invalid_deposit. Mesma trava de
     * estado dos sub-itens (a partir de 'fechada' o sinal congela → 409 proposal_locked).
     */
    @Transactional
    public AtelieProposal setDeposit(UUID companyId, UUID id, Integer depositCents, boolean depositPaid) {
        requireMutableProposal(companyId, id);
        if (depositCents != null && depositCents < 0) {
            throw new InvalidDepositException();
        }
        if (depositPaid && (depositCents == null || depositCents <= 0)) {
            throw new InvalidDepositException();
        }
        AtelieProposal updated = repository.updateDeposit(companyId, id, depositCents, depositPaid)
            .orElseThrow(ProposalNotFoundException::new);
        contextCache.invalidate(companyId);
        return updated;
    }

    // -------------------------------------------------------------------------
    // STATUS
    // -------------------------------------------------------------------------

    @Transactional
    public AtelieProposal updateStatus(UUID companyId, UUID id, String newStatusId) {
        AtelieProposalStatus newStatus = AtelieProposalStatus.fromId(newStatusId).orElseThrow(InvalidStatusException::new);
        AtelieProposal current = repository.findById(companyId, id).orElseThrow(ProposalNotFoundException::new);
        AtelieProposalStatus from = AtelieProposalStatus.fromId(current.status()).orElseThrow(InvalidStatusException::new);

        if (!from.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException();
        }
        // não dá pra orçar uma proposta sem item de orçamento (total derivado > 0).
        if (newStatus == AtelieProposalStatus.ORCADA && current.totalCents() <= 0) {
            throw new EmptyBudgetException();
        }
        // GATE DE SINAL (onda backlog #2): com sinal REGISTRADO e não pago, o fechamento é bloqueado
        // (protege o caixa — o ateliê compra tecido/material com o sinal). Sem sinal registrado
        // (null/0), o fechamento segue livre — nem todo ateliê cobra sinal.
        if (newStatus == AtelieProposalStatus.FECHADA && current.depositCents() != null
            && current.depositCents() > 0 && !current.depositPaid()) {
            throw new DepositRequiredException();
        }

        repository.updateStatus(companyId, id, newStatus.id(), newStatus.isTerminal());

        // total LÍQUIDO na notificação (cupom aplicado pelo painel — onda 2, backlog #13).
        String text = newStatus.notificationText(pieceLabel(current),
            brl(current.totalCents() - current.discountCents()));
        notifier.notifyStatus(companyId, current.conversationId(), text);

        // Onda 3 (backlog #7): a ENTREGA (realizada) encadeia o pós-entrega — agradecimento +
        // avaliação + indicação (relacionamento; toggle + review_link na config).
        if (newStatus == AtelieProposalStatus.REALIZADA) {
            var config = configRepository.findByCompany(companyId);
            if (config.postDeliveryEnabled()) {
                StringBuilder pos = new StringBuilder("Sua peça foi feita com todo o carinho — "
                    + "esperamos que ame o resultado! 🪡 Obrigado pela confiança. ");
                if (config.reviewLink() != null) {
                    pos.append("Se puder, deixe sua avaliação — ajuda muito o ateliê: ")
                        .append(config.reviewLink()).append(" ");
                }
                pos.append("E se conhecer alguém querendo uma peça sob medida, ficaremos felizes "
                    + "com a indicação!");
                notifier.notifyStatus(companyId, current.conversationId(), pos.toString());
            }
        }

        contextCache.invalidate(companyId);
        return repository.findById(companyId, id).orElseThrow(ProposalNotFoundException::new);
    }

    private static String pieceLabel(AtelieProposal p) {
        String type = AtelieProjectType.fromId(p.projectType()).map(AtelieProjectType::label).orElse("peça");
        return type.toLowerCase();
    }

    private static String brl(int cents) {
        return "R$ " + String.format("%d,%02d", cents / 100, cents % 100);
    }
}
