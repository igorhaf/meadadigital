package com.meada.profiles.lingerie.coupons;

import com.meada.common.audit.AuditLogger;
import com.meada.common.coupons.CouponRepositoryBase;
import com.meada.common.coupons.CouponServiceBase;
import org.springframework.stereotype.Service;

/**
 * Cupons do perfil lingerie (camada 8.9) — subclasse FINA do motor comum {@link CouponServiceBase} (unificação
 * 2026-07). CRUD/validações/auditoria vivem na base; a APLICAÇÃO do cupom (validade/min/max +
 * cálculo do desconto) continua no fluxo de pedido/proposta do perfil, como sempre.
 */
@Service
public class LingerieCouponService extends CouponServiceBase<LingerieCoupon> {

    private final LingerieCouponRepository repository;

    public LingerieCouponService(LingerieCouponRepository repository, AuditLogger auditLogger) {
        super(auditLogger);
        this.repository = repository;
    }

    @Override
    protected CouponRepositoryBase<LingerieCoupon> repo() {
        return repository;
    }

    @Override
    protected String entity() {
        return "lingerie_coupon";
    }
}
