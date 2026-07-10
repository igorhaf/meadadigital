package com.meada.profiles.academia.coupons;

import com.meada.common.audit.AuditLogger;
import com.meada.common.coupons.CouponRepositoryBase;
import com.meada.common.coupons.CouponServiceBase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Cupons da academia (camada 7.7) — subclasse do motor comum {@link CouponServiceBase} (unificação
 * 2026-07). Além do CRUD da base, a academia mantém suas EXTENSÕES próprias: a validação
 * {@link #validate(UUID, String, int)} que checa active + validade + pedido mínimo + limite de usos
 * e devolve o desconto (com clamp ao subtotal), e o {@link #apply(UUID, UUID)} que incrementa uses.
 */
@Service
public class AcademiaCouponService extends CouponServiceBase<AcademiaCoupon> {

    private final AcademiaCouponRepository repository;

    public AcademiaCouponService(AcademiaCouponRepository repository, AuditLogger auditLogger) {
        super(auditLogger);
        this.repository = repository;
    }

    @Override
    protected CouponRepositoryBase<AcademiaCoupon> repo() {
        return repository;
    }

    @Override
    protected String entity() {
        return "academia_coupon";
    }

    /**
     * Resultado da validação de um cupom contra um subtotal. Quando {@code valid} é false,
     * {@code discountCents} é 0 e {@code reason} explica a recusa
     * (coupon_not_found / coupon_inactive / coupon_expired / below_minimum / max_uses_reached).
     */
    public record CouponValidation(boolean valid, int discountCents, String reason, UUID couponId, String code) {
        static CouponValidation reject(String reason) {
            return new CouponValidation(false, 0, reason, null, null);
        }
        static CouponValidation accept(AcademiaCoupon c, int discountCents) {
            return new CouponValidation(true, discountCents, null, c.id(), c.code());
        }
    }

    public CouponValidation validate(UUID companyId, String code, int subtotalCents) {
        Optional<AcademiaCoupon> found = repository.findByCode(companyId, code);
        if (found.isEmpty()) {
            return CouponValidation.reject("coupon_not_found");
        }
        AcademiaCoupon c = found.get();
        if (!c.active()) {
            return CouponValidation.reject("coupon_inactive");
        }
        if (c.validUntil() != null && c.validUntil().isBefore(LocalDate.now())) {
            return CouponValidation.reject("coupon_expired");
        }
        if (subtotalCents < c.minCents()) {
            return CouponValidation.reject("below_minimum");
        }
        if (c.maxUses() != null && c.uses() >= c.maxUses()) {
            return CouponValidation.reject("max_uses_reached");
        }
        int discount = computeDiscount(c, subtotalCents);
        return CouponValidation.accept(c, discount);
    }

    /** Incrementa uses do cupom (chamado quando um desconto é efetivamente aplicado em um pedido). */
    @Transactional
    public void apply(UUID companyId, UUID couponId) {
        repository.incrementUses(companyId, couponId);
    }

    /** Desconto do cupom sobre o subtotal, com clamp: percent = subtotal*value/100; fixed = value. */
    private static int computeDiscount(AcademiaCoupon c, int subtotalCents) {
        int raw = "percent".equals(c.kind())
            ? (int) ((long) subtotalCents * c.value() / 100)
            : c.value();
        return Math.max(0, Math.min(raw, subtotalCents));
    }
}
