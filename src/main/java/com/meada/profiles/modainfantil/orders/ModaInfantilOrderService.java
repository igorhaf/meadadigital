package com.meada.profiles.modainfantil.orders;

import com.meada.profiles.modainfantil.ModaInfantilConfig;
import com.meada.profiles.modainfantil.ModaInfantilConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras dos pedidos moda_infantil (camada 8.22). Clone do
 * {@link com.meada.profiles.lingerie.orders.LingerieOrderService} (gate de aceite com
 * rejection_reason na recusa; estoque decrementado no repositório → OutOfStockException aborta), COM a
 * ⭐ ADAPTAÇÃO 8.22: a transição pra {@code recusado}/{@code cancelado} DEVOLVE o estoque das variantes
 * (restock) — delegado ao {@link ModaInfantilOrderRepository#updateStatus}, transacional e idempotente.
 *
 * <p>{@link #create} é chamado pelo {@code PedidoModaInfantilConfirmHandler} (vindo da IA): lê a taxa
 * de entrega do {@link ModaInfantilConfig} e delega ao repositório — que recalcula os totais a partir
 * do catálogo (IGNORA o total que a IA mandou) e decrementa o estoque.
 *
 * <p>{@link #updateStatus} valida a transição (→ 409 se inválida), aplica o restock-on-cancel e dispara
 * a notificação outbound do novo status via {@link ModaInfantilOrderNotifier}. O aceite/recusa é AÇÃO
 * HUMANA (a IA não transiciona).
 */
@Service
public class ModaInfantilOrderService {

    private final ModaInfantilOrderRepository orderRepository;
    private final ModaInfantilConfigRepository configRepository;
    private final ModaInfantilOrderNotifier notifier;

    public ModaInfantilOrderService(ModaInfantilOrderRepository orderRepository,
                                    ModaInfantilConfigRepository configRepository,
                                    ModaInfantilOrderNotifier notifier) {
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
     * produto+variante+preço, DECREMENTA o estoque (esgotado aborta tudo), recalcula os totais
     * (descarta o total da IA).
     */
    @Transactional
    public ModaInfantilOrder create(UUID companyId, UUID conversationId, UUID contactId,
                                    String fulfillment, String deliveryAddress, List<OrderLineInput> lines,
                                    String couponCode, String notes) {
        ModaInfantilConfig config = configRepository.findByCompany(companyId);
        return orderRepository.createOrder(
            companyId, conversationId, contactId, fulfillment, deliveryAddress, lines,
            couponCode, config.deliveryFeeCents(), notes);
    }

    public List<ModaInfantilOrder> list(UUID companyId, String status, int limit, int offset) {
        return orderRepository.listByCompany(companyId, status, limit, offset);
    }

    public long count(UUID companyId, String status) {
        return orderRepository.countByCompany(companyId, status);
    }

    public Optional<ModaInfantilOrder> get(UUID companyId, UUID id) {
        return orderRepository.findById(companyId, id);
    }

    /**
     * Transiciona o status do pedido (gate humano). Valida o alvo (enum) e a transição. Persiste —
     * gravando rejection_reason SÓ quando o alvo é {@code recusado}, e ⭐ DEVOLVENDO o estoque das
     * variantes quando o alvo é {@code recusado}/{@code cancelado} (transacional + idempotente, no
     * repositório) — e notifica a cliente com o texto fixo do novo status (concatenando o motivo na
     * recusa, defensivamente; e considerando o fulfillment no {@code enviado}: entrega × retirada). A
     * notificação é best-effort (não reverte).
     */
    @Transactional
    public ModaInfantilOrder updateStatus(UUID companyId, UUID id, String newStatusId, String rejectionReason) {
        ModaInfantilOrderStatus newStatus = ModaInfantilOrderStatus.fromId(newStatusId)
            .orElseThrow(InvalidStatusException::new);

        ModaInfantilOrder current = orderRepository.findById(companyId, id)
            .orElseThrow(OrderNotFoundException::new);
        ModaInfantilOrderStatus from = ModaInfantilOrderStatus.fromId(current.status())
            .orElseThrow(InvalidStatusException::new);

        if (!from.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException();
        }

        // rejection_reason só faz sentido na recusa; nas demais transições passa null. O repositório
        // aplica o restock-on-cancel (recusado/cancelado) na mesma transação, de forma idempotente.
        String reasonToPersist = newStatus == ModaInfantilOrderStatus.RECUSADO ? rejectionReason : null;
        orderRepository.updateStatus(companyId, id, newStatus.id(), reasonToPersist);

        // Notificação outbound do novo status (best-effort; aguardando não chega aqui como alvo válido).
        String text = newStatus.notificationText(current.fulfillment());
        if (newStatus == ModaInfantilOrderStatus.RECUSADO && rejectionReason != null && !rejectionReason.isBlank()) {
            text = text + " Motivo: " + rejectionReason.strip();
        }
        if (text != null) {
            notifier.notifyStatus(companyId, current.conversationId(), text);
        }

        return orderRepository.findById(companyId, id).orElseThrow(OrderNotFoundException::new);
    }
}
