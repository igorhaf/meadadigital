package com.meada.whatsapp.profiles.padaria;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Período de retirada/entrega do perfil padaria (camada 8.8 / perfil padaria, ESCAPADA 1) —
 * MATERIALIZADO, espelho 1:1 de {@code frontend/profiles/padaria/padaria-period.ts}. Clone de
 * {@link com.meada.whatsapp.profiles.floricultura.FloriculturaPeriod}. Quando o pedido tem item sob
 * encomenda, carrega o dia ({@code pickup_or_delivery_date}) + a FAIXA do dia (este enum). Duas faixas
 * hardcoded — não slot por minuto. O {@code PadariaPeriodParityTest} garante a paridade Java↔TS; a
 * CHECK de {@code padaria_orders.delivery_period} (migration 52) trava os mesmos ids no banco.
 */
public enum PadariaPeriod {
    MANHA("manha", "Manhã (8h–12h)"),
    TARDE("tarde", "Tarde (13h–18h)");

    private final String id;
    private final String label;

    PadariaPeriod(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    /** Resolve um período pelo id estável. Optional vazio se inválido/null. */
    public static Optional<PadariaPeriod> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(p -> p.id.equals(id)).findFirst();
    }

    /** Todos os períodos, na ordem de declaração. */
    public static List<PadariaPeriod> allActive() {
        return List.of(values());
    }
}
