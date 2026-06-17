package com.meada.whatsapp.profiles.sushi.orders;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Status de um pedido sushi (camada 7.1) com as transições válidas cravadas (decisão 5):
 * <pre>
 *   recebido          → preparo, cancelado
 *   preparo           → saiu_pra_entrega, cancelado
 *   saiu_pra_entrega  → entregue, cancelado
 *   entregue          → (terminal)
 *   cancelado         → (terminal)
 * </pre>
 * Transição inválida → 409 invalid_status_transition no controller.
 */
public enum SushiOrderStatus {
    RECEBIDO("recebido"),
    PREPARO("preparo"),
    SAIU_PRA_ENTREGA("saiu_pra_entrega"),
    ENTREGUE("entregue"),
    CANCELADO("cancelado");

    private final String id;

    SushiOrderStatus(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<SushiOrderStatus> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(s -> s.id.equals(id)).findFirst();
    }

    /** Transições permitidas a partir deste status. */
    public Set<SushiOrderStatus> allowedNext() {
        return switch (this) {
            case RECEBIDO -> Set.of(PREPARO, CANCELADO);
            case PREPARO -> Set.of(SAIU_PRA_ENTREGA, CANCELADO);
            case SAIU_PRA_ENTREGA -> Set.of(ENTREGUE, CANCELADO);
            case ENTREGUE, CANCELADO -> Set.of();
        };
    }

    public boolean canTransitionTo(SushiOrderStatus next) {
        return allowedNext().contains(next);
    }

    /** Texto fixo da notificação outbound disparada ao ENTRAR neste status (decisão 6). */
    public String notificationText() {
        return switch (this) {
            case PREPARO -> "Seu pedido entrou em preparo. Já já começa o sushi a aparecer.";
            case SAIU_PRA_ENTREGA -> "Seu pedido saiu pra entrega. Em instantes chega aí.";
            case ENTREGUE -> "Pedido entregue. Bom apetite e obrigado pela preferência.";
            case CANCELADO -> "Seu pedido foi cancelado. Se quiser refazer, é só me chamar.";
            // recebido não notifica (a confirmação do pedido já foi a mensagem da IA).
            case RECEBIDO -> null;
        };
    }
}
