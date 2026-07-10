package com.meada.profiles.lingerie.orders;

import com.meada.profiles.lingerie.LingerieConfig;
import com.meada.profiles.lingerie.LingerieConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras dos pedidos lingerie (camada 8.21). Análogo ao
 * {@link com.meada.profiles.adega.orders.AdegaOrderService} (gate de aceite com
 * rejection_reason na recusa) — sem a trava +18 do adega, mas com a ⭐ ESCAPADA de estoque (delegada
 * ao repositório: o decremento transacional → OutOfStockException aborta o pedido).
 *
 * <p>{@link #create} é chamado pelo {@code PedidoLingerieConfirmHandler} (vindo da IA): lê a taxa de
 * entrega do {@link LingerieConfig} e delega ao repositório — que recalcula os totais a partir do
 * catálogo (IGNORA o total que a IA mandou) e decrementa o estoque.
 *
 * <p>{@link #updateStatus} valida a transição (→ 409 se inválida) e dispara a notificação outbound do
 * novo status via {@link LingerieOrderNotifier}. O aceite/recusa é AÇÃO HUMANA (a IA não transiciona).
 */
@Service
public class LingerieOrderService {

    private final LingerieOrderRepository orderRepository;
    private final LingerieConfigRepository configRepository;
    private final LingerieOrderNotifier notifier;

    public LingerieOrderService(LingerieOrderRepository orderRepository,
                                LingerieConfigRepository configRepository,
                                LingerieOrderNotifier notifier) {
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
     * tenant (0 se ausente) e só entra no total em ENTREGA. O repositório faz o snapshot de
     * produto+variante+preço, DECREMENTA o estoque (⭐ — esgotado aborta tudo), recalcula os totais
     * (descarta o total da IA).
     */
    @Transactional
    public LingerieOrder create(UUID companyId, UUID conversationId, UUID contactId,
                                String fulfillment, String deliveryAddress, List<OrderLineInput> lines,
                                String couponCode, String notes) {
        LingerieConfig config = configRepository.findByCompany(companyId);
        return orderRepository.createOrder(
            companyId, conversationId, contactId, fulfillment, deliveryAddress, lines,
            couponCode, config.deliveryFeeCents(), notes);
    }

    public List<LingerieOrder> list(UUID companyId, String status, int limit, int offset) {
        return orderRepository.listByCompany(companyId, status, limit, offset);
    }

    public long count(UUID companyId, String status) {
        return orderRepository.countByCompany(companyId, status);
    }

    public Optional<LingerieOrder> get(UUID companyId, UUID id) {
        return orderRepository.findById(companyId, id);
    }

    /**
     * Transiciona o status do pedido (gate humano). Valida o alvo (enum) e a transição. Persiste —
     * gravando rejection_reason SÓ quando o alvo é {@code recusado} — e notifica a cliente com o
     * texto fixo do novo status (concatenando o motivo na recusa, defensivamente; e considerando o
     * fulfillment no {@code enviado}: entrega × retirada). A notificação é best-effort (não reverte).
     */
    @Transactional
    public LingerieOrder updateStatus(UUID companyId, UUID id, String newStatusId, String rejectionReason) {
        LingerieOrderStatus newStatus = LingerieOrderStatus.fromId(newStatusId)
            .orElseThrow(InvalidStatusException::new);

        LingerieOrder current = orderRepository.findById(companyId, id)
            .orElseThrow(OrderNotFoundException::new);
        LingerieOrderStatus from = LingerieOrderStatus.fromId(current.status())
            .orElseThrow(InvalidStatusException::new);

        if (!from.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException();
        }

        // rejection_reason só faz sentido na recusa; nas demais transições passa null.
        String reasonToPersist = newStatus == LingerieOrderStatus.RECUSADO ? rejectionReason : null;
        orderRepository.updateStatus(companyId, id, newStatus.id(), reasonToPersist);

        // Notificação outbound do novo status (best-effort; aguardando não chega aqui como alvo válido).
        String text = newStatus.notificationText(current.fulfillment());
        if (newStatus == LingerieOrderStatus.RECUSADO && rejectionReason != null && !rejectionReason.isBlank()) {
            text = text + " Motivo: " + rejectionReason.strip();
        }
        if (text != null) {
            notifier.notifyStatus(companyId, current.conversationId(), text);
        }

        return orderRepository.findById(companyId, id).orElseThrow(OrderNotFoundException::new);
    }
}
