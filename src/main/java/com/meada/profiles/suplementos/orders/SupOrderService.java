package com.meada.profiles.suplementos.orders;

import com.meada.profiles.suplementos.SuplementosConfig;
import com.meada.profiles.suplementos.SuplementosConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras dos pedidos suplementos (camada 8.24). Clone do
 * {@link com.meada.profiles.lingerie.orders.LingerieOrderService} (gate de aceite com
 * rejection_reason na recusa; ⭐ ESCAPADA de estoque delegada ao repositório: o decremento
 * transacional → OutOfStockException aborta o pedido). SÓ ENTREGA — a taxa de entrega SEMPRE soma.
 *
 * <p>{@link #create} é chamado pelo {@code PedidoSuplementosConfirmHandler} (vindo da IA): lê a taxa
 * de entrega do {@link SuplementosConfig} e delega ao repositório — que recalcula os totais a partir
 * do catálogo (IGNORA o total que a IA mandou) e decrementa o estoque.
 *
 * <p>{@link #updateStatus} valida a transição (→ 409 se inválida) e dispara a notificação outbound do
 * novo status via {@link SupOrderNotifier}. O aceite/recusa é AÇÃO HUMANA (a IA não transiciona).
 */
@Service
public class SupOrderService {

    private final SupOrderRepository orderRepository;
    private final SuplementosConfigRepository configRepository;
    private final SupOrderNotifier notifier;

    public SupOrderService(SupOrderRepository orderRepository,
                           SuplementosConfigRepository configRepository,
                           SupOrderNotifier notifier) {
        this.orderRepository = orderRepository;
        this.configRepository = configRepository;
        this.notifier = notifier;
    }

    /** Pedido não encontrado / de outro tenant (→ 404). */
    public static class OrderNotFoundException extends RuntimeException {}

    /** Transição de status inválida (→ 409 invalid_status_transition). */
    public static class InvalidStatusTransitionException extends RuntimeException {}

    /** Status alvo desconhecido (→ 400 invalid_status). */
    public static class InvalidStatusException extends RuntimeException {}

    /**
     * Cria um pedido a partir das linhas confirmadas pela IA. A taxa de entrega vem do config do
     * tenant (0 se ausente) e SEMPRE entra no total (SÓ entrega nesta SM). O repositório faz o snapshot
     * de produto+variante+preço, DECREMENTA o estoque (⭐ — esgotado aborta tudo), recalcula os totais
     * (descarta o total da IA), e exige delivery_address (→ AddressRequiredException se ausente).
     */
    @Transactional
    public SupOrder create(UUID companyId, UUID conversationId, UUID contactId,
                           String deliveryAddress, List<OrderLineInput> lines, String notes) {
        SuplementosConfig config = configRepository.findByCompany(companyId);
        return orderRepository.createOrder(
            companyId, conversationId, contactId, deliveryAddress, lines,
            config.deliveryFeeCents(), config.freeShippingThresholdCents(), notes);
    }

    public List<SupOrder> list(UUID companyId, String status, int limit, int offset) {
        return orderRepository.listByCompany(companyId, status, limit, offset);
    }

    public long count(UUID companyId, String status) {
        return orderRepository.countByCompany(companyId, status);
    }

    public Optional<SupOrder> get(UUID companyId, UUID id) {
        return orderRepository.findById(companyId, id);
    }

    /**
     * Transiciona o status do pedido (gate humano). Valida o alvo (enum) e a transição. Persiste —
     * gravando rejection_reason SÓ quando o alvo é {@code recusado} — e notifica o cliente com o texto
     * fixo DEFENSIVO do novo status (concatenando o motivo na recusa, SEM conteúdo de saúde). A
     * notificação é best-effort (não reverte). Cancel NÃO devolve estoque nesta SM (fase futura).
     */
    @Transactional
    public SupOrder updateStatus(UUID companyId, UUID id, String newStatusId, String rejectionReason) {
        SuplementosOrderStatus newStatus = SuplementosOrderStatus.fromId(newStatusId)
            .orElseThrow(InvalidStatusException::new);

        SupOrder current = orderRepository.findById(companyId, id)
            .orElseThrow(OrderNotFoundException::new);
        SuplementosOrderStatus from = SuplementosOrderStatus.fromId(current.status())
            .orElseThrow(InvalidStatusException::new);

        if (!from.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException();
        }

        // rejection_reason só faz sentido na recusa; nas demais transições passa null.
        String reasonToPersist = newStatus == SuplementosOrderStatus.RECUSADO ? rejectionReason : null;
        orderRepository.updateStatus(companyId, id, newStatus.id(), reasonToPersist);

        // Notificação outbound do novo status (best-effort; aguardando/cancelado não notificam).
        String text = newStatus.notificationText();
        if (newStatus == SuplementosOrderStatus.RECUSADO && rejectionReason != null && !rejectionReason.isBlank()) {
            text = text + " Motivo: " + rejectionReason.strip();
        }
        if (text != null) {
            notifier.notifyStatus(companyId, current.conversationId(), text);
        }

        return orderRepository.findById(companyId, id).orElseThrow(OrderNotFoundException::new);
    }
}
