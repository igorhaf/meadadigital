package com.meada.whatsapp.profiles.padaria.orders;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Status de um pedido padaria (camada 8.8 / perfil padaria) — clone de
 * {@link com.meada.whatsapp.profiles.floricultura.orders.FloriculturaOrderStatus} + a forma de
 * {@code AestheticAppointmentStatus} (canTransitionTo + notificationText). Inclui o GATE DE ACEITE
 * humano (aguardando → em_preparo / recusado) e o FUNIL QUE DIVERGE no fim pela forma de entrega
 * (retirada → retirado; entrega → saiu_entrega → entregue).
 * <pre>
 *   aguardando    → em_preparo, recusado, cancelado
 *   em_preparo    → pronto, cancelado
 *   pronto        → retirado, saiu_entrega, cancelado
 *   saiu_entrega  → entregue, cancelado
 *   retirado      → (terminal)
 *   entregue      → (terminal)
 *   recusado      → (terminal)
 *   cancelado     → (terminal)
 * </pre>
 * Transição inválida → 409 invalid_status_transition no controller. Espelhado 1:1 por
 * {@code frontend/profiles/padaria/padaria-order-status.ts} (PadariaOrderStatusParityTest).
 */
public enum PadariaOrderStatus {
    AGUARDANDO("aguardando"),
    EM_PREPARO("em_preparo"),
    PRONTO("pronto"),
    RETIRADO("retirado"),
    SAIU_ENTREGA("saiu_entrega"),
    ENTREGUE("entregue"),
    RECUSADO("recusado"),
    CANCELADO("cancelado");

    private final String id;

    PadariaOrderStatus(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<PadariaOrderStatus> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(s -> s.id.equals(id)).findFirst();
    }

    /** Transições permitidas a partir deste status. */
    public Set<PadariaOrderStatus> allowedNext() {
        return switch (this) {
            case AGUARDANDO -> Set.of(EM_PREPARO, RECUSADO, CANCELADO);
            case EM_PREPARO -> Set.of(PRONTO, CANCELADO);
            case PRONTO -> Set.of(RETIRADO, SAIU_ENTREGA, CANCELADO);
            case SAIU_ENTREGA -> Set.of(ENTREGUE, CANCELADO);
            case RETIRADO, ENTREGUE, RECUSADO, CANCELADO -> Set.of();
        };
    }

    public boolean canTransitionTo(PadariaOrderStatus next) {
        return allowedNext().contains(next);
    }

    /**
     * Texto fixo da notificação outbound disparada ao ENTRAR neste status. null = não notifica
     * ({@code aguardando} e {@code cancelado} são silenciosos — a IA já confirmou o RECEBIMENTO na
     * própria mensagem; quem cancela não recebe sermão). No caso de {@code recusado}, o MOTIVO
     * (rejection_reason) é concatenado pelo Service, não aqui — o enum é estático.
     */
    public String notificationText() {
        return switch (this) {
            case EM_PREPARO -> "Pedido aceito! 🍞 Já entrou em preparo. Em breve avisamos quando estiver pronto.";
            case PRONTO -> "Seu pedido está pronto pra retirada! Pode vir buscar quando quiser.";
            case SAIU_ENTREGA -> "Seu pedido saiu pra entrega. Já já chega aí!";
            case ENTREGUE -> "Pedido entregue, bom apetite! 😋";
            case RECUSADO -> "Infelizmente não conseguimos atender seu pedido. Pedimos desculpa pelo transtorno.";
            // aguardando/cancelado não notificam (a IA já confirmou o recebimento; cancelado é silencioso).
            case AGUARDANDO, RETIRADO, CANCELADO -> null;
        };
    }
}
