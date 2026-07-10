package com.meada.profiles.comida;

/**
 * Config do delivery comida (camada 8.4): taxa de entrega + pedido mínimo, em centavos. Clone de
 * {@link com.meada.profiles.sushi.SushiRestaurantConfig}. Quando o tenant não tem linha em
 * {@code comida_config}, usa-se {@link #ZERO} (taxa/mínimo 0).
 */
public record ComidaConfig(
    int deliveryFeeCents,
    int minOrderCents,
    java.time.LocalTime opensAt,
    java.time.LocalTime closesAt,
    Integer autoDeliverHours,
    boolean reactivationEnabled,
    int reactivationDays,
    String reactivationCouponCode) {

    public static final ComidaConfig ZERO =
        new ComidaConfig(0, 0, null, null, null, false, 30, null);
}
