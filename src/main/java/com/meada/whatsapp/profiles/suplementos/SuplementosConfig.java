package com.meada.whatsapp.profiles.suplementos;

/**
 * Config do delivery suplementos (camada 8.24): taxa de entrega + pedido mínimo, em centavos. Clone
 * de {@link com.meada.whatsapp.profiles.lingerie.LingerieConfig} /
 * {@link com.meada.whatsapp.profiles.adega.AdegaConfig}. Quando o tenant não tem linha em
 * {@code sup_config}, usa-se {@link #ZERO} (taxa/mínimo 0).
 */
public record SuplementosConfig(int deliveryFeeCents, int minOrderCents) {

    public static final SuplementosConfig ZERO = new SuplementosConfig(0, 0);
}
