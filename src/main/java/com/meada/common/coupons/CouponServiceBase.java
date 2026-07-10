package com.meada.common.coupons;

import com.meada.common.audit.AuditLogger;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Motor comum do CRUD de cupons (unificação 2026-07 dos 7 clones). Valida code/kind/value
 * (percent 1..100, fixed &gt;= 0) e min/max_uses, audita ({@code <entity>_created|_updated|_deleted}).
 * code duplicado (UNIQUE case-insensitive) → {@link DuplicateCouponException} (409 duplicate_coupon).
 * A APLICAÇÃO do cupom (validade/min/max + cálculo do desconto) continua no fluxo de pedido/proposta
 * de cada perfil, não aqui — aqui é só o CRUD do catálogo.
 *
 * <p>Cada perfil mantém um service FINO que declara {@link #repo()} e {@link #entity()} (e extensões
 * próprias, como o {@code validate()} da academia). As exceções aninhadas moram AQUI — os imports nos
 * controllers/testes usam o nome canônico desta classe.
 */
public abstract class CouponServiceBase<T extends CouponRecord> {

    public static class CouponNotFoundException extends RuntimeException {}

    public static class DuplicateCouponException extends RuntimeException {}

    public static class InvalidCouponException extends RuntimeException {}

    protected final AuditLogger auditLogger;

    protected CouponServiceBase(AuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }

    protected abstract CouponRepositoryBase<T> repo();

    /** Entity type da auditoria (ex.: {@code sushi_coupon}); ações derivam dele. */
    protected abstract String entity();

    @Transactional
    public T create(UUID companyId, UUID userId, String code, String kind, int value,
                    Integer minCents, Integer maxUses, LocalDate validUntil, Boolean active) {
        requireValidCode(code);
        requireValidKindValue(kind, value);
        int min = minCents == null ? 0 : minCents;
        if (min < 0 || (maxUses != null && maxUses < 0)) {
            throw new InvalidCouponException();
        }
        T created;
        try {
            created = repo().insert(companyId, code, kind, value, min, maxUses, validUntil,
                active == null || active);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateCouponException();
        }
        auditLogger.log(companyId, userId, entity() + "_created", entity(),
            created.id(), Map.of("code", created.code()));
        return created;
    }

    @Transactional
    public T update(UUID companyId, UUID userId, UUID id, String code, String kind,
                    Integer value, Integer minCents, Integer maxUses, boolean maxUsesProvided,
                    LocalDate validUntil, boolean validUntilProvided, Boolean active) {
        if (code != null && !code.isBlank()) {
            requireValidCode(code);
        } else if (code != null) {
            throw new InvalidCouponException();
        }
        // Para validar kind/value coerentes, resolve o estado efetivo (mistura o que veio + o atual).
        if (kind != null || value != null) {
            T current = repo().findById(companyId, id)
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
        T updated;
        try {
            updated = repo().update(companyId, id, code, kind, value, minCents, maxUses,
                    maxUsesProvided, validUntil, validUntilProvided, active)
                .orElseThrow(CouponNotFoundException::new);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateCouponException();
        }
        auditLogger.log(companyId, userId, entity() + "_updated", entity(), id, Map.of());
        return updated;
    }

    @Transactional
    public T toggle(UUID companyId, UUID userId, UUID id, boolean active) {
        T c = repo().toggle(companyId, id, active)
            .orElseThrow(CouponNotFoundException::new);
        auditLogger.log(companyId, userId, entity() + "_updated", entity(), id,
            Map.of("active", active));
        return c;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        if (!repo().delete(companyId, id)) {
            throw new CouponNotFoundException();
        }
        auditLogger.log(companyId, userId, entity() + "_deleted", entity(), id, Map.of());
    }

    public List<T> list(UUID companyId) {
        return repo().listByCompany(companyId);
    }

    public Optional<T> get(UUID companyId, UUID id) {
        return repo().findById(companyId, id);
    }

    protected static void requireValidCode(String code) {
        if (code == null || code.isBlank() || code.trim().length() > 40) {
            throw new InvalidCouponException();
        }
    }

    protected static void requireValidKindValue(String kind, int value) {
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
