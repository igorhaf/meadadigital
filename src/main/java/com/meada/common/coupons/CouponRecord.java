package com.meada.common.coupons;

import java.util.UUID;

/**
 * Contrato mínimo que os records de cupom por perfil ({@code SushiCoupon}, {@code AdegaCoupon}…)
 * satisfazem por construção (componentes do record). É o que o motor comum
 * ({@link CouponServiceBase}/{@link CouponRepositoryBase}) precisa enxergar — o record por perfil
 * CONTINUA existindo porque é contrato JSON da API (a academia expõe {@code minCents}; os demais,
 * {@code minOrderCents}).
 */
public interface CouponRecord {

    UUID id();

    String code();

    String kind();

    int value();
}
