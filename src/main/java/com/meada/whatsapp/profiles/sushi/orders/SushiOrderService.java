package com.meada.whatsapp.profiles.sushi.orders;

import com.meada.whatsapp.profiles.sushi.SushiRestaurantConfig;
import com.meada.whatsapp.profiles.sushi.SushiRestaurantConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras dos pedidos sushi (camada 7.1).
 *
 * <p>{@link #create} é chamado pelo {@code OrderConfirmHandler} (vindo da IA). Lê a taxa de
 * entrega do {@link SushiRestaurantConfig} do tenant e cria o pedido — o repositório recalcula
 * os totais a partir do cardápio (IGNORA o total que a IA mandou).
 *
 * <p>{@link #updateStatus} valida a transição (decisão 5 → 409 se inválida) e, ao persistir,
 * dispara a notificação outbound do novo status (decisão 6) via {@link SushiOrderNotifier}.
 */
@Service
public class SushiOrderService {

    private static final Logger log = LoggerFactory.getLogger(SushiOrderService.class);

    private final SushiOrderRepository orderRepository;
    private final SushiRestaurantConfigRepository configRepository;
    private final SushiOrderNotifier notifier;

    public SushiOrderService(SushiOrderRepository orderRepository,
                             SushiRestaurantConfigRepository configRepository,
                             SushiOrderNotifier notifier) {
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
     * restaurante (0 se ausente). O repositório faz o snapshot de preço+nome e recalcula os totais.
     */
    @Transactional
    public SushiOrder create(UUID companyId, UUID conversationId, UUID contactId,
                             String deliveryAddress, List<OrderLineInput> lines, String notes) {
        SushiRestaurantConfig config = configRepository.findByCompany(companyId);
        return orderRepository.createOrder(
            companyId, conversationId, contactId, deliveryAddress, lines, config.deliveryFeeCents(), notes);
    }

    public List<SushiOrder> list(UUID companyId, String status, int limit, int offset) {
        return orderRepository.listByCompany(companyId, status, limit, offset);
    }

    public long count(UUID companyId, String status) {
        return orderRepository.countByCompany(companyId, status);
    }

    public Optional<SushiOrder> get(UUID companyId, UUID id) {
        return orderRepository.findById(companyId, id);
    }

    /**
     * Transiciona o status do pedido. Valida o alvo (enum) e a transição (decisão 5). Persiste e
     * notifica o cliente com o texto fixo do novo status. A notificação é best-effort (não reverte).
     */
    @Transactional
    public SushiOrder updateStatus(UUID companyId, UUID id, String newStatusId) {
        SushiOrderStatus newStatus = SushiOrderStatus.fromId(newStatusId)
            .orElseThrow(InvalidStatusException::new);

        SushiOrder current = orderRepository.findById(companyId, id)
            .orElseThrow(OrderNotFoundException::new);
        SushiOrderStatus from = SushiOrderStatus.fromId(current.status())
            .orElseThrow(InvalidStatusException::new);

        if (!from.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException();
        }

        orderRepository.updateStatus(companyId, id, newStatus.id());

        // Notificação outbound do novo status (best-effort; recebido não notifica).
        notifier.notifyStatus(companyId, current.conversationId(), newStatus.notificationText());

        return orderRepository.findById(companyId, id).orElseThrow(OrderNotFoundException::new);
    }
}
