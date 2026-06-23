package com.meada.whatsapp.profiles.comida;

/**
 * Config do delivery comida (camada 8.4): taxa de entrega + pedido mínimo, em centavos. Clone de
 * {@link com.meada.whatsapp.profiles.sushi.SushiRestaurantConfig}. Quando o tenant não tem linha em
 * {@code comida_config}, usa-se {@link #ZERO} (taxa/mínimo 0).
 */
public record ComidaConfig(int deliveryFeeCents, int minOrderCents) {

    public static final ComidaConfig ZERO = new ComidaConfig(0, 0);
}
