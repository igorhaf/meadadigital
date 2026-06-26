package com.meada.whatsapp.profiles.las.orders;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Status de um pedido las (camada 8.23) — clone literal da máquina de
 * {@link com.meada.whatsapp.profiles.lingerie.orders.LingerieOrderStatus} (gate de aceite humano), com
 * o mesmo vocabulário de varejo.
 * <pre>
 *   aguardando → separando (ACEITE, humano), recusado (RECUSA, humano, com rejection_reason)
 *   separando  → enviado, cancelado
 *   enviado    → entregue, cancelado
 *   entregue   → (terminal)
 *   recusado   → (terminal)
 *   cancelado  → (terminal)
 * </pre>
 * Transição inválida → 409 invalid_status_transition no controller. Espelhado 1:1 por
 * {@code frontend/profiles/las/las-order-status.ts} (LasOrderStatusParityTest). A IA NÃO aceita/recusa
 * — o gate é humano (PATCH no painel).
 */
public enum LasOrderStatus {
    AGUARDANDO("aguardando"),
    SEPARANDO("separando"),
    ENVIADO("enviado"),
    ENTREGUE("entregue"),
    RECUSADO("recusado"),
    CANCELADO("cancelado");

    private final String id;

    LasOrderStatus(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<LasOrderStatus> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(s -> s.id.equals(id)).findFirst();
    }

    /** Transições permitidas a partir deste status. */
    public Set<LasOrderStatus> allowedNext() {
        return switch (this) {
            case AGUARDANDO -> Set.of(SEPARANDO, RECUSADO);
            case SEPARANDO -> Set.of(ENVIADO, CANCELADO);
            case ENVIADO -> Set.of(ENTREGUE, CANCELADO);
            case ENTREGUE, RECUSADO, CANCELADO -> Set.of();
        };
    }

    public boolean canTransitionTo(LasOrderStatus next) {
        return allowedNext().contains(next);
    }

    /**
     * Texto fixo da notificação outbound disparada ao ENTRAR neste status. null = não notifica
     * ({@code aguardando} é silencioso — a IA já confirmou o RECEBIMENTO na própria mensagem). No
     * caso de {@code enviado}, o texto depende do {@code fulfillment} (entrega × retirada) — por isso
     * há um overload {@link #notificationText(String)}; este sem-arg devolve a variante de entrega.
     * No caso de {@code recusado}, o MOTIVO (rejection_reason) é concatenado pelo Service, não aqui.
     */
    public String notificationText() {
        return notificationText("entrega");
    }

    /** Texto da notificação considerando o fulfillment (afeta só {@code enviado}). */
    public String notificationText(String fulfillment) {
        return switch (this) {
            case SEPARANDO -> "Seu pedido foi aceito e já estamos separando! 🧶";
            case ENVIADO -> "retirada".equals(fulfillment)
                ? "Seu pedido está pronto para retirada!"
                : "Seu pedido foi enviado. Já já chega aí!";
            case ENTREGUE -> "Pedido entregue. Bom trabalho! Obrigada pela preferência!";
            case RECUSADO -> "Infelizmente não conseguimos aceitar seu pedido agora. Pedimos desculpa pelo transtorno.";
            case CANCELADO -> "Seu pedido foi cancelado. Se quiser refazer, é só chamar.";
            // aguardando não notifica (a IA já confirmou o recebimento na mensagem).
            case AGUARDANDO -> null;
        };
    }
}
