package com.meada.profiles.lavanderia.coupons;

import com.meada.common.audit.AuditLogger;
import com.meada.common.coupons.CouponRepositoryBase;
import com.meada.common.coupons.CouponServiceBase;
import org.springframework.stereotype.Service;

/**
 * Cupons do perfil lavanderia (camada 8.10) — subclasse FINA do motor comum {@link CouponServiceBase} (unificação
 * 2026-07). CRUD/validações/auditoria vivem na base; a APLICAÇÃO do cupom (validade/min/max +
 * cálculo do desconto) continua no fluxo de pedido/proposta do perfil, como sempre.
 */
@Service
public class LavanderiaCouponService extends CouponServiceBase<LavanderiaCoupon> {

    private final LavanderiaCouponRepository repository;

    public LavanderiaCouponService(LavanderiaCouponRepository repository, AuditLogger auditLogger) {
        super(auditLogger);
        this.repository = repository;
    }

    @Override
    protected CouponRepositoryBase<LavanderiaCoupon> repo() {
        return repository;
    }

    @Override
    protected String entity() {
        return "lavanderia_coupon";
    }
}
