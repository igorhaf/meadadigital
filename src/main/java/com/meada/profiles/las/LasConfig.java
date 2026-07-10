package com.meada.profiles.las;

/**
 * Config do varejo las (camada 8.23): taxa de entrega + pedido mínimo, em centavos. Clone de
 * {@link com.meada.profiles.lingerie.LingerieConfig} (chassi de varejo). Quando o tenant não
 * tem linha em {@code las_config}, usa-se {@link #ZERO} (taxa/mínimo 0).
 */
public record LasConfig(
    int deliveryFeeCents,
    int minOrderCents,
    boolean reactivationEnabled,
    int reactivationDays,
    String reactivationCouponCode) {

    public static final LasConfig ZERO = new LasConfig(0, 0, false, 45, null);
}
