package com.meada.profiles.cursos.coupons;

import com.meada.common.audit.AuditLogger;
import com.meada.common.coupons.CouponRepositoryBase;
import com.meada.common.coupons.CouponServiceBase;
import org.springframework.stereotype.Service;

/**
 * Cupons do perfil cursos (camada 8.20) — subclasse FINA do motor comum {@link CouponServiceBase} (unificação
 * 2026-07). CRUD/validações/auditoria vivem na base; a APLICAÇÃO do cupom (validade/min/max +
 * cálculo do desconto) continua no fluxo de pedido/proposta do perfil, como sempre.
 */
@Service
public class CursosCouponService extends CouponServiceBase<CursosCoupon> {

    private final CursosCouponRepository repository;

    public CursosCouponService(CursosCouponRepository repository, AuditLogger auditLogger) {
        super(auditLogger);
        this.repository = repository;
    }

    @Override
    protected CouponRepositoryBase<CursosCoupon> repo() {
        return repository;
    }

    @Override
    protected String entity() {
        return "cursos_coupon";
    }
}
