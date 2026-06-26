package com.meada.whatsapp.profiles.suplementos.orders;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Status de um pedido suplementos (camada 8.24) — clone da máquina do
 * {@link com.meada.whatsapp.profiles.adega.orders.AdegaOrderStatus} (gate de aceite humano), com o
 * vocabulário do COMIDA (em_preparo/saiu_entrega). SÓ ENTREGA nesta SM.
 * <pre>
 *   aguardando    → em_preparo (ACEITE, humano), recusado (RECUSA, humano, com rejection_reason), cancelado
 *   em_preparo    → saiu_entrega, cancelado
 *   saiu_entrega  → entregue, cancelado
 *   entregue      → (terminal)
 *   recusado      → (terminal)
 *   cancelado     → (terminal)
 * </pre>
 * Transição inválida → 409 invalid_status_transition no controller. Espelhado 1:1 por
 * {@code frontend/profiles/suplementos/suplementos-order-status.ts}
 * ({@code SuplementosOrderStatusParityTest}). A IA NÃO aceita/recusa — o gate é humano (PATCH no
 * painel).
 */
public enum SuplementosOrderStatus {
    AGUARDANDO("aguardando"),
    EM_PREPARO("em_preparo"),
    SAIU_ENTREGA("saiu_entrega"),
    ENTREGUE("entregue"),
    RECUSADO("recusado"),
    CANCELADO("cancelado");

    private final String id;

    SuplementosOrderStatus(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<SuplementosOrderStatus> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(s -> s.id.equals(id)).findFirst();
    }

    /** Transições permitidas a partir deste status. */
    public Set<SuplementosOrderStatus> allowedNext() {
        return switch (this) {
            case AGUARDANDO -> Set.of(EM_PREPARO, RECUSADO, CANCELADO);
            case EM_PREPARO -> Set.of(SAIU_ENTREGA, CANCELADO);
            case SAIU_ENTREGA -> Set.of(ENTREGUE, CANCELADO);
            case ENTREGUE, RECUSADO, CANCELADO -> Set.of();
        };
    }

    public boolean canTransitionTo(SuplementosOrderStatus next) {
        return allowedNext().contains(next);
    }

    /**
     * Texto fixo da notificação outbound disparada ao ENTRAR neste status. null = não notifica
     * ({@code aguardando} e {@code cancelado} são silenciosos — a IA já confirmou o RECEBIMENTO na
     * própria mensagem; cancelamento é decisão administrativa que não vira sermão ao cliente). Os
     * textos são DEFENSIVOS — SEM conteúdo de saúde/dosagem/recomendação (trava). No caso de
     * {@code recusado}, o MOTIVO (rejection_reason) é concatenado pelo Service, não aqui.
     */
    public String notificationText() {
        return switch (this) {
            case EM_PREPARO -> "Seu pedido foi aceito! 💪 Já estamos separando.";
            case SAIU_ENTREGA -> "Seu pedido saiu pra entrega. Já já chega aí!";
            case ENTREGUE -> "Pedido entregue. Obrigado pela preferência!";
            case RECUSADO -> "Infelizmente não conseguimos aceitar seu pedido agora. Pedimos desculpa pelo transtorno.";
            // aguardando e cancelado não notificam.
            case AGUARDANDO, CANCELADO -> null;
        };
    }
}
