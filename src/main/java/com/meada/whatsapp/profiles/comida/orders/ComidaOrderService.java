package com.meada.whatsapp.profiles.comida.orders;

import com.meada.whatsapp.profiles.comida.ComidaConfig;
import com.meada.whatsapp.profiles.comida.ComidaConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras dos pedidos comida (camada 8.4). Clone de
 * {@link com.meada.whatsapp.profiles.sushi.orders.SushiOrderService} + a ESCAPADA 1 (rejection_reason
 * no gate de recusa).
 *
 * <p>{@link #create} é chamado pelo {@code PedidoComidaConfirmHandler} (vindo da IA). Lê a taxa de
 * entrega do {@link ComidaConfig} do tenant e delega ao repositório — que recalcula os totais a
 * partir do cardápio + opções (IGNORA o total que a IA mandou).
 *
 * <p>{@link #updateStatus} valida a transição (→ 409 se inválida) e, ao persistir, dispara a
 * notificação outbound do novo status via {@link ComidaOrderNotifier}. O aceite/recusa é AÇÃO
 * HUMANA (a IA não transiciona).
 */
@Service
public class ComidaOrderService {

    private static final Logger log = LoggerFactory.getLogger(ComidaOrderService.class);

    private final ComidaOrderRepository orderRepository;
    private final ComidaConfigRepository configRepository;
    private final ComidaOrderNotifier notifier;

    public ComidaOrderService(ComidaOrderRepository orderRepository,
                              ComidaConfigRepository configRepository,
                              ComidaOrderNotifier notifier) {
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
    public ComidaOrder create(UUID companyId, UUID conversationId, UUID contactId,
                              String deliveryAddress, List<OrderLineInput> lines, String notes) {
        ComidaConfig config = configRepository.findByCompany(companyId);
        return orderRepository.createOrder(
            companyId, conversationId, contactId, deliveryAddress, lines, config.deliveryFeeCents(), notes);
    }

    public List<ComidaOrder> list(UUID companyId, String status, int limit, int offset) {
        return orderRepository.listByCompany(companyId, status, limit, offset);
    }

    public long count(UUID companyId, String status) {
        return orderRepository.countByCompany(companyId, status);
    }

    public Optional<ComidaOrder> get(UUID companyId, UUID id) {
        return orderRepository.findById(companyId, id);
    }

    /**
     * Transiciona o status do pedido (gate humano). Valida o alvo (enum) e a transição. Persiste —
     * gravando rejection_reason SÓ quando o alvo é {@code recusado} (ESCAPADA 1) — e notifica o
     * cliente com o texto fixo do novo status (concatenando o motivo na recusa, defensivamente). A
     * notificação é best-effort (não reverte).
     */
    @Transactional
    public ComidaOrder updateStatus(UUID companyId, UUID id, String newStatusId, String rejectionReason) {
        ComidaOrderStatus newStatus = ComidaOrderStatus.fromId(newStatusId)
            .orElseThrow(InvalidStatusException::new);

        ComidaOrder current = orderRepository.findById(companyId, id)
            .orElseThrow(OrderNotFoundException::new);
        ComidaOrderStatus from = ComidaOrderStatus.fromId(current.status())
            .orElseThrow(InvalidStatusException::new);

        if (!from.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException();
        }

        // rejection_reason só faz sentido na recusa; nas demais transições passa null.
        String reasonToPersist = newStatus == ComidaOrderStatus.RECUSADO ? rejectionReason : null;
        orderRepository.updateStatus(companyId, id, newStatus.id(), reasonToPersist);

        // Notificação outbound do novo status (best-effort; aguardando não chega aqui como alvo válido).
        String text = newStatus.notificationText();
        if (newStatus == ComidaOrderStatus.RECUSADO && rejectionReason != null && !rejectionReason.isBlank()) {
            text = text + " Motivo: " + rejectionReason.strip();
        }
        if (text != null) {
            notifier.notifyStatus(companyId, current.conversationId(), text);
        }

        return orderRepository.findById(companyId, id).orElseThrow(OrderNotFoundException::new);
    }
}
