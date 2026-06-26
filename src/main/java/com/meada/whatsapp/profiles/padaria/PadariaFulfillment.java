package com.meada.whatsapp.profiles.padaria;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Forma de entrega de um pedido padaria (camada 8.8 / perfil padaria, ESCAPADA) — MATERIALIZADO,
 * espelho 1:1 de {@code frontend/profiles/padaria/padaria-fulfillment.ts}. Não há paralelo direto na
 * floricultura (que é sempre entrega); aqui o cliente escolhe RETIRADA (balcão, sem taxa/endereço) ou
 * ENTREGA (exige endereço + soma a taxa). O {@code PadariaFulfillmentParityTest} garante a paridade
 * Java↔TS; a CHECK de {@code padaria_orders.fulfillment} (migration 52) trava os mesmos ids no banco.
 */
public enum PadariaFulfillment {
    RETIRADA("retirada", "Retirada"),
    ENTREGA("entrega", "Entrega");

    private final String id;
    private final String label;

    PadariaFulfillment(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    /** Resolve uma forma de entrega pelo id estável. Optional vazio se inválido/null. */
    public static Optional<PadariaFulfillment> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(f -> f.id.equals(id)).findFirst();
    }

    /** Todas as formas de entrega, na ordem de declaração. */
    public static List<PadariaFulfillment> allActive() {
        return List.of(values());
    }
}
