package com.meada.profiles.academia.coupons;

import com.meada.common.audit.AuditLogger;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras dos cupons da academia (camada 7.7), espelho do SushiCouponService. CRUD do catálogo de
 * cupons (percent 1..100, fixed &gt;= 0; min_cents/max_uses/valid_until opcionais) + a validação
 * {@link #validate(UUID, String, int)} que checa active + validade + pedido mínimo + limite de usos
 * e devolve o desconto (com clamp ao subtotal). code duplicado (UNIQUE por company) → 409.
 */
@Service
public class AcademiaCouponService {

    private final AcademiaCouponRepository repository;
    private final AuditLogger auditLogger;

    public AcademiaCouponService(AcademiaCouponRepository repository, AuditLogger auditLogger) {
        this.repository = repository;
        this.auditLogger = auditLogger;
    }

    public static class CouponNotFoundException extends RuntimeException {}
    public static class DuplicateCouponException extends RuntimeException {}
    public static class InvalidCouponException extends RuntimeException {}

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

    @Transactional
    public AcademiaCoupon create(UUID companyId, UUID userId, String code, String kind, int value,
                                 Integer minCents, Integer maxUses, LocalDate validUntil, Boolean active) {
        requireValidCode(code);
        requireValidKindValue(kind, value);
        int min = minCents == null ? 0 : minCents;
        if (min < 0 || (maxUses != null && maxUses < 0)) {
            throw new InvalidCouponException();
        }
        AcademiaCoupon created;
        try {
            created = repository.insert(companyId, code, kind, value, min, maxUses, validUntil,
                active == null || active);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateCouponException();
        }
        auditLogger.log(companyId, userId, "academia_coupon_created", "academia_coupon",
            created.id(), Map.of("code", created.code()));
        return created;
    }

    @Transactional
    public AcademiaCoupon update(UUID companyId, UUID userId, UUID id, String code, String kind,
                                 Integer value, Integer minCents, Integer maxUses, boolean maxUsesProvided,
                                 LocalDate validUntil, boolean validUntilProvided, Boolean active) {
        if (code != null && !code.isBlank()) {
            requireValidCode(code);
        } else if (code != null) {
            throw new InvalidCouponException();
        }
        if (kind != null || value != null) {
            AcademiaCoupon current = repository.findById(companyId, id)
                .orElseThrow(CouponNotFoundException::new);
            String effKind = kind != null && !kind.isBlank() ? kind : current.kind();
            int effValue = value != null ? value : current.value();
            requireValidKindValue(effKind, effValue);
        }
        if (minCents != null && minCents < 0) {
            throw new InvalidCouponException();
        }
        if (maxUsesProvided && maxUses != null && maxUses < 0) {
            throw new InvalidCouponException();
        }
        AcademiaCoupon updated;
        try {
            updated = repository.update(companyId, id, code, kind, value, minCents, maxUses,
                    maxUsesProvided, validUntil, validUntilProvided, active)
                .orElseThrow(CouponNotFoundException::new);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateCouponException();
        }
        auditLogger.log(companyId, userId, "academia_coupon_updated", "academia_coupon", id, Map.of());
        return updated;
    }

    @Transactional
    public AcademiaCoupon toggle(UUID companyId, UUID userId, UUID id, boolean active) {
        AcademiaCoupon c = repository.toggle(companyId, id, active)
            .orElseThrow(CouponNotFoundException::new);
        auditLogger.log(companyId, userId, "academia_coupon_updated", "academia_coupon", id,
            Map.of("active", active));
        return c;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        if (!repository.delete(companyId, id)) {
            throw new CouponNotFoundException();
        }
        auditLogger.log(companyId, userId, "academia_coupon_deleted", "academia_coupon", id, Map.of());
    }

    public List<AcademiaCoupon> list(UUID companyId) {
        return repository.listByCompany(companyId);
    }

    public Optional<AcademiaCoupon> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }

    /**
     * Valida o {@code code} contra o {@code subtotalCents} e devolve o desconto quando aplicável.
     * Checa (nesta ordem): existência, active, validade (valid_until &gt;= hoje), pedido mínimo
     * (subtotal &gt;= min_cents) e limite de usos (uses &lt; max_uses). NÃO incrementa uses (isso é
     * feito na aplicação real via {@link #apply(UUID, UUID)}). Desconto sempre com clamp ao subtotal.
     */
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

    private static void requireValidCode(String code) {
        if (code == null || code.isBlank() || code.trim().length() > 40) {
            throw new InvalidCouponException();
        }
    }

    private static void requireValidKindValue(String kind, int value) {
        if (!"percent".equals(kind) && !"fixed".equals(kind)) {
            throw new InvalidCouponException();
        }
        if ("percent".equals(kind)) {
            if (value < 1 || value > 100) {
                throw new InvalidCouponException();
            }
        } else { // fixed
            if (value < 0) {
                throw new InvalidCouponException();
            }
        }
    }
}
