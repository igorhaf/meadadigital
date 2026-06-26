package com.meada.whatsapp.profiles.las.orders;

import com.meada.whatsapp.profiles.las.LasConfig;
import com.meada.whatsapp.profiles.las.LasConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras dos pedidos las (camada 8.23). Clone do
 * {@link com.meada.whatsapp.profiles.lingerie.orders.LingerieOrderService} (gate de aceite com
 * rejection_reason na recusa; ESCAPADA de estoque delegada ao repositório) + o repasse do flag ⭐
 * {@code sameLotGuaranteed} ao repositório (a regra do mesmo lote de tingimento por cor →
 * MixedDyeLotsException aborta o pedido).
 *
 * <p>{@link #create} é chamado pelo {@code PedidoLasConfirmHandler} (vindo da IA): lê a taxa de
 * entrega do {@link LasConfig} e delega ao repositório — que recalcula os totais a partir do catálogo
 * (IGNORA o total que a IA mandou), decrementa o estoque e valida o lote único por cor.
 *
 * <p>{@link #updateStatus} valida a transição (→ 409 se inválida) e dispara a notificação outbound do
 * novo status via {@link LasOrderNotifier}. O aceite/recusa é AÇÃO HUMANA (a IA não transiciona).
 */
@Service
public class LasOrderService {

    private final LasOrderRepository orderRepository;
    private final LasConfigRepository configRepository;
    private final LasOrderNotifier notifier;

    public LasOrderService(LasOrderRepository orderRepository,
                           LasConfigRepository configRepository,
                           LasOrderNotifier notifier) {
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
     * produto+variante+preço, DECREMENTA o estoque (esgotado aborta tudo), valida o lote único por cor
     * quando {@code sameLotGuaranteed} (⭐ — lotes misturados abortam tudo), recalcula os totais
     * (descarta o total da IA).
     */
    @Transactional
    public LasOrder create(UUID companyId, UUID conversationId, UUID contactId,
                           String fulfillment, boolean sameLotGuaranteed, String deliveryAddress,
                           List<OrderLineInput> lines, String notes) {
        LasConfig config = configRepository.findByCompany(companyId);
        return orderRepository.createOrder(
            companyId, conversationId, contactId, fulfillment, sameLotGuaranteed, deliveryAddress, lines,
            config.deliveryFeeCents(), notes);
    }

    public List<LasOrder> list(UUID companyId, String status, int limit, int offset) {
        return orderRepository.listByCompany(companyId, status, limit, offset);
    }

    public long count(UUID companyId, String status) {
        return orderRepository.countByCompany(companyId, status);
    }

    public Optional<LasOrder> get(UUID companyId, UUID id) {
        return orderRepository.findById(companyId, id);
    }

    /**
     * Transiciona o status do pedido (gate humano). Valida o alvo (enum) e a transição. Persiste —
     * gravando rejection_reason SÓ quando o alvo é {@code recusado} — e notifica a cliente com o
     * texto fixo do novo status (concatenando o motivo na recusa, defensivamente; e considerando o
     * fulfillment no {@code enviado}: entrega × retirada). A notificação é best-effort (não reverte).
     */
    @Transactional
    public LasOrder updateStatus(UUID companyId, UUID id, String newStatusId, String rejectionReason) {
        LasOrderStatus newStatus = LasOrderStatus.fromId(newStatusId)
            .orElseThrow(InvalidStatusException::new);

        LasOrder current = orderRepository.findById(companyId, id)
            .orElseThrow(OrderNotFoundException::new);
        LasOrderStatus from = LasOrderStatus.fromId(current.status())
            .orElseThrow(InvalidStatusException::new);

        if (!from.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException();
        }

        // rejection_reason só faz sentido na recusa; nas demais transições passa null.
        String reasonToPersist = newStatus == LasOrderStatus.RECUSADO ? rejectionReason : null;
        orderRepository.updateStatus(companyId, id, newStatus.id(), reasonToPersist);

        // Notificação outbound do novo status (best-effort; aguardando não chega aqui como alvo válido).
        String text = newStatus.notificationText(current.fulfillment());
        if (newStatus == LasOrderStatus.RECUSADO && rejectionReason != null && !rejectionReason.isBlank()) {
            text = text + " Motivo: " + rejectionReason.strip();
        }
        if (text != null) {
            notifier.notifyStatus(companyId, current.conversationId(), text);
        }

        return orderRepository.findById(companyId, id).orElseThrow(OrderNotFoundException::new);
    }
}
