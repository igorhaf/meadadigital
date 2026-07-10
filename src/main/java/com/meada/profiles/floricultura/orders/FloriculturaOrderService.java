package com.meada.profiles.floricultura.orders;

import com.meada.profiles.floricultura.FloriculturaConfig;
import com.meada.profiles.floricultura.FloriculturaConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras dos pedidos floricultura (camada 8.4). Clone de
 * {@link com.meada.profiles.sushi.orders.SushiOrderService} + a ESCAPADA 1 (rejection_reason
 * no gate de recusa).
 *
 * <p>{@link #create} é chamado pelo {@code PedidoFlorConfirmHandler} (vindo da IA). Lê a taxa de
 * entrega do {@link FloriculturaConfig} do tenant e delega ao repositório — que recalcula os totais a
 * partir do cardápio + opções (IGNORA o total que a IA mandou).
 *
 * <p>{@link #updateStatus} valida a transição (→ 409 se inválida) e, ao persistir, dispara a
 * notificação outbound do novo status via {@link FloriculturaOrderNotifier}. O aceite/recusa é AÇÃO
 * HUMANA (a IA não transiciona).
 */
@Service
public class FloriculturaOrderService {

    private static final Logger log = LoggerFactory.getLogger(FloriculturaOrderService.class);

    private final FloriculturaOrderRepository orderRepository;
    private final FloriculturaConfigRepository configRepository;
    private final FloriculturaOrderNotifier notifier;

    public FloriculturaOrderService(FloriculturaOrderRepository orderRepository,
                              FloriculturaConfigRepository configRepository,
                              FloriculturaOrderNotifier notifier) {
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
     * restaurante (0 se ausente). O repositório faz o snapshot de preço+nome+opções e recalcula os
     * totais (descarta o total da IA).
     */
    @Transactional
    public FloriculturaOrder create(UUID companyId, UUID conversationId, UUID contactId,
                              String deliveryAddress, List<OrderLineInput> lines, String notes,
                              java.time.LocalDate deliveryDate, String deliveryPeriod,
                              String recipientName, String cardMessage,
                              String couponCode, boolean anonymous) {
        FloriculturaConfig config = configRepository.findByCompany(companyId);
        return orderRepository.createOrder(
            companyId, conversationId, contactId, deliveryAddress, lines, config.deliveryFeeCents(), notes,
            deliveryDate, deliveryPeriod, recipientName, cardMessage, couponCode, anonymous);
    }

    public List<FloriculturaOrder> list(UUID companyId, String status, int limit, int offset) {
        return orderRepository.listByCompany(companyId, status, limit, offset);
    }

    public long count(UUID companyId, String status) {
        return orderRepository.countByCompany(companyId, status);
    }

    public Optional<FloriculturaOrder> get(UUID companyId, UUID id) {
        return orderRepository.findById(companyId, id);
    }

    /**
     * Transiciona o status do pedido (gate humano). Valida o alvo (enum) e a transição. Persiste —
     * gravando rejection_reason SÓ quando o alvo é {@code recusado} (ESCAPADA 1) — e notifica o
     * cliente com o texto fixo do novo status (concatenando o motivo na recusa, defensivamente). A
     * notificação é best-effort (não reverte).
     */
    @Transactional
    public FloriculturaOrder updateStatus(UUID companyId, UUID id, String newStatusId, String rejectionReason) {
        FloriculturaOrderStatus newStatus = FloriculturaOrderStatus.fromId(newStatusId)
            .orElseThrow(InvalidStatusException::new);

        FloriculturaOrder current = orderRepository.findById(companyId, id)
            .orElseThrow(OrderNotFoundException::new);
        FloriculturaOrderStatus from = FloriculturaOrderStatus.fromId(current.status())
            .orElseThrow(InvalidStatusException::new);

        if (!from.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException();
        }

        // rejection_reason só faz sentido na recusa; nas demais transições passa null.
        String reasonToPersist = newStatus == FloriculturaOrderStatus.RECUSADO ? rejectionReason : null;
        orderRepository.updateStatus(companyId, id, newStatus.id(), reasonToPersist);

        // Notificação outbound do novo status (best-effort; aguardando não chega aqui como alvo válido).
        String text = newStatus.notificationText();
        if (newStatus == FloriculturaOrderStatus.RECUSADO && rejectionReason != null && !rejectionReason.isBlank()) {
            text = text + " Motivo: " + rejectionReason.strip();
        }
        if (text != null) {
            notifier.notifyStatus(companyId, current.conversationId(), text);
        }

        return orderRepository.findById(companyId, id).orElseThrow(OrderNotFoundException::new);
    }
}
