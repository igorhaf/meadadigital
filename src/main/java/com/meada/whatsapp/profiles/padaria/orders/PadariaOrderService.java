package com.meada.whatsapp.profiles.padaria.orders;

import com.meada.whatsapp.profiles.padaria.PadariaConfig;
import com.meada.whatsapp.profiles.padaria.PadariaConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras dos pedidos padaria (camada 8.8 / perfil padaria). Clone de
 * {@link com.meada.whatsapp.profiles.floricultura.orders.FloriculturaOrderService} + as escapadas
 * (lead time da data condicional + fulfillment retirada/entrega). As exceções de validação
 * ({@link PadariaOrderRepository.LeadTimeViolationException},
 * {@link PadariaOrderRepository.AddressRequiredException},
 * {@link PadariaOrderRepository.InvalidOptionException}) sobem do repositório.
 *
 * <p>{@link #create} é chamado pelo {@code EncomendaPadariaConfirmHandler} (vindo da IA). Lê a config
 * do tenant (taxa + lead default) e delega ao repositório — que recalcula os totais a partir do
 * cardápio + opções (IGNORA o total que a IA mandou) e aplica as travas das escapadas.
 *
 * <p>{@link #updateStatus} valida a transição (→ 409 se inválida) e, ao persistir, dispara a
 * notificação outbound do novo status via {@link PadariaOrderNotifier}. O aceite/recusa é AÇÃO HUMANA
 * (a IA não transiciona).
 */
@Service
public class PadariaOrderService {

    private final PadariaOrderRepository orderRepository;
    private final PadariaConfigRepository configRepository;
    private final PadariaOrderNotifier notifier;

    public PadariaOrderService(PadariaOrderRepository orderRepository,
                               PadariaConfigRepository configRepository,
                               PadariaOrderNotifier notifier) {
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
     * Cria um pedido a partir das linhas confirmadas pela IA. A taxa de entrega + o lead default vêm
     * da config (0/1 se ausente). O repositório faz o snapshot de preço+nome+made_to_order+opções,
     * recalcula os totais (descarta o total da IA), valida o lead time (ESCAPADA 1) e o endereço
     * conforme o fulfillment. Propaga {@code LeadTimeViolationException}/{@code AddressRequiredException}/
     * {@code InvalidOptionException} para o handler decidir (que aborta sem criar — devolve empty).
     */
    @Transactional
    public PadariaOrder create(UUID companyId, UUID conversationId, UUID contactId,
                               String fulfillment, String deliveryAddress, List<OrderLineInput> lines,
                               LocalDate pickupOrDeliveryDate, String deliveryPeriod, String notes) {
        PadariaConfig config = configRepository.findByCompany(companyId);
        return orderRepository.createOrder(
            companyId, conversationId, contactId, fulfillment, deliveryAddress, lines,
            config.deliveryFeeCents(), config.leadTimeDaysDefault(),
            pickupOrDeliveryDate, deliveryPeriod, notes);
    }

    public List<PadariaOrder> list(UUID companyId, String status, int limit, int offset) {
        return orderRepository.listByCompany(companyId, status, limit, offset);
    }

    public long count(UUID companyId, String status) {
        return orderRepository.countByCompany(companyId, status);
    }

    public Optional<PadariaOrder> get(UUID companyId, UUID id) {
        return orderRepository.findById(companyId, id);
    }

    /**
     * Transiciona o status do pedido (gate humano). Valida o alvo (enum) e a transição. Persiste —
     * gravando rejection_reason SÓ quando o alvo é {@code recusado} — e notifica o cliente com o texto
     * fixo do novo status (concatenando o motivo na recusa, defensivamente). A notificação é
     * best-effort (não reverte).
     */
    @Transactional
    public PadariaOrder updateStatus(UUID companyId, UUID id, String newStatusId, String rejectionReason) {
        PadariaOrderStatus newStatus = PadariaOrderStatus.fromId(newStatusId)
            .orElseThrow(InvalidStatusException::new);

        PadariaOrder current = orderRepository.findById(companyId, id)
            .orElseThrow(OrderNotFoundException::new);
        PadariaOrderStatus from = PadariaOrderStatus.fromId(current.status())
            .orElseThrow(InvalidStatusException::new);

        if (!from.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException();
        }

        // rejection_reason só faz sentido na recusa; nas demais transições passa null.
        String reasonToPersist = newStatus == PadariaOrderStatus.RECUSADO ? rejectionReason : null;
        orderRepository.updateStatus(companyId, id, newStatus.id(), reasonToPersist);

        // Notificação outbound do novo status (best-effort).
        String text = newStatus.notificationText();
        if (newStatus == PadariaOrderStatus.RECUSADO && rejectionReason != null && !rejectionReason.isBlank()) {
            text = text + " Motivo: " + rejectionReason.strip();
        }
        if (text != null) {
            notifier.notifyStatus(companyId, current.conversationId(), text);
        }

        return orderRepository.findById(companyId, id).orElseThrow(OrderNotFoundException::new);
    }
}
