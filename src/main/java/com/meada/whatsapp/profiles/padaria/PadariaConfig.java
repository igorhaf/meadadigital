package com.meada.whatsapp.profiles.padaria;

/**
 * Config da padaria (camada 8.8 / perfil padaria): taxa de entrega + pedido mínimo +
 * {@code leadTimeDaysDefault} (antecedência default para itens sob encomenda), em centavos/dias. Clone
 * de {@link com.meada.whatsapp.profiles.floricultura.FloriculturaConfig} + o lead default (ESCAPADA 1).
 * Quando o tenant não tem linha em {@code padaria_config}, usa-se {@link #DEFAULT} (taxa/mínimo 0,
 * lead default 1, espelhando o {@code default 1} da migration).
 */
public record PadariaConfig(int deliveryFeeCents, int minOrderCents, int leadTimeDaysDefault) {

    /** Default usado quando não há linha de config (taxa/mínimo 0, lead default 1 — igual à migration). */
    public static final PadariaConfig DEFAULT = new PadariaConfig(0, 0, 1);
}
