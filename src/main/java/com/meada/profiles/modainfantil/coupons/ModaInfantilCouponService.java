package com.meada.profiles.modainfantil.coupons;

import com.meada.common.audit.AuditLogger;
import com.meada.common.coupons.CouponRepositoryBase;
import com.meada.common.coupons.CouponServiceBase;
import org.springframework.stereotype.Service;

/**
 * Cupons do perfil modainfantil (camada 8.9) — subclasse FINA do motor comum {@link CouponServiceBase} (unificação
 * 2026-07). CRUD/validações/auditoria vivem na base; a APLICAÇÃO do cupom (validade/min/max +
 * cálculo do desconto) continua no fluxo de pedido/proposta do perfil, como sempre.
 */
@Service
public class ModaInfantilCouponService extends CouponServiceBase<ModaInfantilCoupon> {

    private final ModaInfantilCouponRepository repository;

    public ModaInfantilCouponService(ModaInfantilCouponRepository repository, AuditLogger auditLogger) {
        super(auditLogger);
        this.repository = repository;
    }

    @Override
    protected CouponRepositoryBase<ModaInfantilCoupon> repo() {
        return repository;
    }

    @Override
    protected String entity() {
        return "moda_infantil_coupon";
    }
}
