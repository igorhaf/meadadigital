package com.meada.profiles.sushi;

/**
 * Config do restaurante sushi (camada 7.1 / sushi funcional): taxa de entrega + pedido mínimo (em
 * centavos) + {@code schedulingEnabled} (quando true, aceita pedidos agendados — data+período).
 * Quando o tenant não tem linha em sushi_restaurant_config, usa-se {@link #ZERO}.
 */
public record SushiRestaurantConfig(int deliveryFeeCents, int minOrderCents, boolean schedulingEnabled,
                                    boolean upsellEnabled, boolean reactivationEnabled,
                                    int reactivationDays, String reactivationCouponCode) {

    public static final SushiRestaurantConfig ZERO =
        new SushiRestaurantConfig(0, 0, false, true, false, 21, null);
}
