package com.meada.profiles.comida.coupons;

import com.meada.common.audit.AuditLogger;
import com.meada.common.coupons.CouponRepositoryBase;
import com.meada.common.coupons.CouponServiceBase;
import org.springframework.stereotype.Service;

/**
 * Cupons do perfil comida (onda 1) — subclasse FINA do motor comum {@link CouponServiceBase} (unificação
 * 2026-07). CRUD/validações/auditoria vivem na base; a APLICAÇÃO do cupom (validade/min/max +
 * cálculo do desconto) continua no fluxo de pedido/proposta do perfil, como sempre.
 */
@Service
public class ComidaCouponService extends CouponServiceBase<ComidaCoupon> {

    private final ComidaCouponRepository repository;

    public ComidaCouponService(ComidaCouponRepository repository, AuditLogger auditLogger) {
        super(auditLogger);
        this.repository = repository;
    }

    @Override
    protected CouponRepositoryBase<ComidaCoupon> repo() {
        return repository;
    }

    @Override
    protected String entity() {
        return "comida_coupon";
    }
}
