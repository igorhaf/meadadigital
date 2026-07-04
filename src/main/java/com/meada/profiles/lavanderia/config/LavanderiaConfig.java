package com.meada.profiles.lavanderia.config;

/**
 * Config do delivery lavanderia (camada 8.10): taxa de entrega + pedido mínimo + turnaround default.
 * Onda 1 do backlog somou: EXPRESS (#2 — toggle + sobretaxa % + turnaround curto), toggles dos
 * lembretes de coleta D-1 (#7) e de pronto-parado (#14, com janela em dias) e a REATIVAÇÃO de
 * inativos (#3 — opt-in DESLIGADO por default, lição Baileys; janela em dias + cupom opcional).
 * Quando o tenant não tem linha em {@code lavanderia_config}, usa-se {@link #DEFAULT}.
 */
public record LavanderiaConfig(
    int deliveryFeeCents,
    int minOrderCents,
    int turnaroundDaysDefault,
    boolean expressEnabled,
    int expressSurchargePct,
    int expressTurnaroundDays,
    boolean collectReminderEnabled,
    boolean readyReminderEnabled,
    int readyReminderDays,
    boolean reactivationEnabled,
    int reactivationDays,
    String reactivationCouponCode) {

    /** Default sem linha de config: sem taxa/mínimo, turnaround 1 dia, express ON 50%/1d, lembretes ON, reativação OFF. */
    public static final LavanderiaConfig DEFAULT =
        new LavanderiaConfig(0, 0, 1, true, 50, 1, true, true, 2, false, 30, null);
}
