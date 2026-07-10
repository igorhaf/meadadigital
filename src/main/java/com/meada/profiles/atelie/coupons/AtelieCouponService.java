package com.meada.profiles.atelie.coupons;

import com.meada.common.audit.AuditLogger;
import com.meada.common.coupons.CouponRepositoryBase;
import com.meada.common.coupons.CouponServiceBase;
import org.springframework.stereotype.Service;

/**
 * Cupons do perfil atelie (onda 2) — subclasse FINA do motor comum {@link CouponServiceBase} (unificação
 * 2026-07). CRUD/validações/auditoria vivem na base; a APLICAÇÃO do cupom (validade/min/max +
 * cálculo do desconto) continua no fluxo de pedido/proposta do perfil, como sempre.
 */
@Service
public class AtelieCouponService extends CouponServiceBase<AtelieCoupon> {

    private final AtelieCouponRepository repository;

    public AtelieCouponService(AtelieCouponRepository repository, AuditLogger auditLogger) {
        super(auditLogger);
        this.repository = repository;
    }

    @Override
    protected CouponRepositoryBase<AtelieCoupon> repo() {
        return repository;
    }

    @Override
    protected String entity() {
        return "atelie_coupon";
    }
}
