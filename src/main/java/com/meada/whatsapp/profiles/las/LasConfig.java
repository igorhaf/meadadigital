package com.meada.whatsapp.profiles.las;

/**
 * Config do varejo las (camada 8.23): taxa de entrega + pedido mínimo, em centavos. Clone de
 * {@link com.meada.whatsapp.profiles.lingerie.LingerieConfig} (chassi de varejo). Quando o tenant não
 * tem linha em {@code las_config}, usa-se {@link #ZERO} (taxa/mínimo 0).
 */
public record LasConfig(int deliveryFeeCents, int minOrderCents) {

    public static final LasConfig ZERO = new LasConfig(0, 0);
}
