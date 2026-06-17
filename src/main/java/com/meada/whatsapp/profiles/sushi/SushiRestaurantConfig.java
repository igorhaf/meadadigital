package com.meada.whatsapp.profiles.sushi;

/**
 * Config do restaurante sushi (camada 7.1): taxa de entrega + pedido mínimo, em centavos.
 * Quando o tenant não tem linha em sushi_restaurant_config, usa-se {@link #ZERO} (taxa/mínimo 0).
 */
public record SushiRestaurantConfig(int deliveryFeeCents, int minOrderCents) {

    public static final SushiRestaurantConfig ZERO = new SushiRestaurantConfig(0, 0);
}
