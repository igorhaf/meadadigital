package com.meada.profiles.sushi.orders;

import com.meada.profiles.sushi.SushiRestaurantConfig;
import com.meada.profiles.sushi.SushiRestaurantConfigRepository;
import com.meada.profiles.sushi.statuses.SushiOrderStatusEntity;
import com.meada.profiles.sushi.statuses.SushiOrderStatusRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras dos pedidos sushi (camada 7.1 / sushi funcional).
 *
 * <p>{@link #create} (chamado pelo {@code OrderConfirmHandler}) lê a taxa de entrega + a flag de
 * agendamento da config; valida fulfillment (entrega exige endereço → 422 address_required) e
 * agendamento (data passada → 422 invalid_schedule_date; agendamento desligado → ignora data/período);
 * o repositório recalcula totais + aplica cupom/fidelidade (IGNORA o total que a IA mandou).
 *
 * <p>{@link #updateStatus} resolve o status alvo na tabela (400 invalid_status), aplica a regra de
 * transição LIVRE (mirror do legal — bloqueia só quando o status ATUAL é terminal → 409
 * invalid_status_transition) e, ao persistir, dispara a notificação outbound do status ALVO quando
 * {@code notify_enabled} + {@code notify_text} estão setados (best-effort) via {@link SushiOrderNotifier}.
 */
@Service
public class SushiOrderService {

    private static final ZoneId BR = ZoneId.of("America/Sao_Paulo");

    private final SushiOrderRepository orderRepository;
    private final SushiRestaurantConfigRepository configRepository;
    private final SushiOrderStatusRepository statusRepository;
    private final SushiOrderNotifier notifier;

    public SushiOrderService(SushiOrderRepository orderRepository,
                             SushiRestaurantConfigRepository configRepository,
                             SushiOrderStatusRepository statusRepository,
                             SushiOrderNotifier notifier) {
        this.orderRepository = orderRepository;
        this.configRepository = configRepository;
        this.statusRepository = statusRepository;
        this.notifier = notifier;
    }

    /** Pedido não encontrado / de outro tenant (→ 404). */
    public static class OrderNotFoundException extends RuntimeException {}

    /** Transição de status inválida (status atual é terminal) (→ 409 invalid_status_transition). */
    public static class InvalidStatusTransitionException extends RuntimeException {}

    /** Status alvo desconhecido / de outro tenant (→ 400 invalid_status). */
    public static class InvalidStatusException extends RuntimeException {}

    /** fulfillment=entrega sem endereço (→ 422 address_required). */
    public static class AddressRequiredException extends RuntimeException {}

    /** Data agendada no passado (→ 422 invalid_schedule_date). */
    public static class InvalidScheduleException extends RuntimeException {}

    /**
     * Cria um pedido a partir das linhas confirmadas pela IA. fulfillment 'entrega' (default) exige
     * endereço; 'retirada' dispensa endereço e taxa. Se a config não tem agendamento, scheduledDate/
     * scheduledPeriod são IGNORADOS (tratados como "agora"/null). Se tem agendamento e a data está no
     * passado → 422. O repositório recalcula totais + aplica cupom/fidelidade.
     */
    @Transactional
    public SushiOrder create(UUID companyId, UUID conversationId, UUID contactId, String deliveryAddress,
                             List<OrderLineInput> lines, String fulfillment, LocalDate scheduledDate,
                             String scheduledPeriod, String couponCode, String notes) {
        SushiRestaurantConfig config = configRepository.findByCompany(companyId);

        String ff = "retirada".equals(fulfillment) ? "retirada" : "entrega";
        if ("entrega".equals(ff) && (deliveryAddress == null || deliveryAddress.isBlank())) {
            throw new AddressRequiredException();
        }

        LocalDate effDate = scheduledDate;
        String effPeriod = scheduledPeriod;
        if (!config.schedulingEnabled()) {
            // Agendamento desligado: ignora data/período (pedido "para agora").
            effDate = null;
            effPeriod = "agora".equals(scheduledPeriod) ? "agora" : null;
        } else if (effDate != null && effDate.isBefore(LocalDate.now(BR))) {
            throw new InvalidScheduleException();
        }

        return orderRepository.createOrder(companyId, conversationId, contactId, deliveryAddress, lines,
            ff, effDate, effPeriod, couponCode, config.deliveryFeeCents(), notes);
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
     * Transiciona o status do pedido. Resolve o alvo na tabela (400 se desconhecido), carrega o
     * pedido + seu status atual, aplica a regra LIVRE (bloqueia só se o status ATUAL é terminal → 409),
     * persiste e notifica com o texto do status ALVO (se notify_enabled + notify_text). Best-effort.
     */
    @Transactional
    public SushiOrder updateStatus(UUID companyId, UUID id, String newStatusId) {
        UUID targetId;
        try {
            targetId = UUID.fromString(newStatusId == null ? "" : newStatusId.trim());
        } catch (IllegalArgumentException e) {
            throw new InvalidStatusException();
        }
        SushiOrderStatusEntity target = statusRepository.findById(companyId, targetId)
            .orElseThrow(InvalidStatusException::new);

        SushiOrder current = orderRepository.findById(companyId, id)
            .orElseThrow(OrderNotFoundException::new);

        // Transição LIVRE (mirror do legal): bloqueia só quando o status ATUAL é terminal.
        SushiOrderStatusEntity from = statusRepository.findById(companyId, current.status())
            .orElseThrow(InvalidStatusException::new);
        if (from.isTerminal()) {
            throw new InvalidStatusTransitionException();
        }

        orderRepository.updateStatus(companyId, id, target.id());

        // Notificação outbound do status alvo (best-effort; só quando habilitada e com texto).
        if (target.notifyEnabled() && target.notifyText() != null && !target.notifyText().isBlank()) {
            notifier.notifyStatus(companyId, current.conversationId(), target.notifyText());
        }

        return orderRepository.findById(companyId, id).orElseThrow(OrderNotFoundException::new);
    }
}
