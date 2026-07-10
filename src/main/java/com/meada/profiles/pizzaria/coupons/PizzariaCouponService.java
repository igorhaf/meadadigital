package com.meada.profiles.pizzaria.coupons;

import com.meada.common.audit.AuditLogger;
import com.meada.common.coupons.CouponRepositoryBase;
import com.meada.common.coupons.CouponServiceBase;
import org.springframework.stereotype.Service;

/**
 * Cupons do perfil pizzaria (camada 8.9) — subclasse FINA do motor comum {@link CouponServiceBase} (unificação
 * 2026-07). CRUD/validações/auditoria vivem na base; a APLICAÇÃO do cupom (validade/min/max +
 * cálculo do desconto) continua no fluxo de pedido/proposta do perfil, como sempre.
 */
@Service
public class PizzariaCouponService extends CouponServiceBase<PizzariaCoupon> {

    private final PizzariaCouponRepository repository;

    public PizzariaCouponService(PizzariaCouponRepository repository, AuditLogger auditLogger) {
        super(auditLogger);
        this.repository = repository;
    }

    @Override
    protected CouponRepositoryBase<PizzariaCoupon> repo() {
        return repository;
    }

    @Override
    protected String entity() {
        return "pizzaria_coupon";
    }
}
