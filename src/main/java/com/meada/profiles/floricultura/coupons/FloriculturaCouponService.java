package com.meada.profiles.floricultura.coupons;

import com.meada.common.audit.AuditLogger;
import com.meada.common.coupons.CouponRepositoryBase;
import com.meada.common.coupons.CouponServiceBase;
import org.springframework.stereotype.Service;

/**
 * Cupons do perfil floricultura (camada 8.5) — subclasse FINA do motor comum {@link CouponServiceBase} (unificação
 * 2026-07). CRUD/validações/auditoria vivem na base; a APLICAÇÃO do cupom (validade/min/max +
 * cálculo do desconto) continua no fluxo de pedido/proposta do perfil, como sempre.
 */
@Service
public class FloriculturaCouponService extends CouponServiceBase<FloriculturaCoupon> {

    private final FloriculturaCouponRepository repository;

    public FloriculturaCouponService(FloriculturaCouponRepository repository, AuditLogger auditLogger) {
        super(auditLogger);
        this.repository = repository;
    }

    @Override
    protected CouponRepositoryBase<FloriculturaCoupon> repo() {
        return repository;
    }

    @Override
    protected String entity() {
        return "floricultura_coupon";
    }
}
