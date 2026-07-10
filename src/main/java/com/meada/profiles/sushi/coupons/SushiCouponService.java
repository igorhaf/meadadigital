package com.meada.profiles.sushi.coupons;

import com.meada.common.audit.AuditLogger;
import com.meada.common.coupons.CouponRepositoryBase;
import com.meada.common.coupons.CouponServiceBase;
import org.springframework.stereotype.Service;

/**
 * Cupons do perfil sushi (camada 7.1 / sushi funcional) — subclasse FINA do motor comum {@link CouponServiceBase} (unificação
 * 2026-07). CRUD/validações/auditoria vivem na base; a APLICAÇÃO do cupom (validade/min/max +
 * cálculo do desconto) continua no fluxo de pedido/proposta do perfil, como sempre.
 */
@Service
public class SushiCouponService extends CouponServiceBase<SushiCoupon> {

    private final SushiCouponRepository repository;

    public SushiCouponService(SushiCouponRepository repository, AuditLogger auditLogger) {
        super(auditLogger);
        this.repository = repository;
    }

    @Override
    protected CouponRepositoryBase<SushiCoupon> repo() {
        return repository;
    }

    @Override
    protected String entity() {
        return "sushi_coupon";
    }
}
