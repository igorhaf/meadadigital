package com.meada.profiles.lavanderia.orders;

import com.meada.profiles.lavanderia.config.LavanderiaConfig;
import com.meada.profiles.lavanderia.config.LavanderiaConfigRepository;
import com.meada.profiles.lavanderia.orders.LavanderiaOrderRepository.BelowMinimumException;
import com.meada.profiles.lavanderia.orders.LavanderiaOrderRepository.TurnaroundViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras dos pedidos lavanderia (camada 8.10). Clone do FloriculturaOrderService + a ESCAPADA das DUAS
 * DATAS (coleta + entrega materializada por turnaround) com validações em 422.
 *
 * <p>{@link #create} é chamado pelo {@code PedidoLavanderiaConfirmHandler} (vindo da IA). Valida
 * endereço (422 address_required) e collect_date (>= hoje, fuso America/Sao_Paulo → 422
 * collect_date_in_past / invalid_collect_date) ANTES de delegar ao repositório — que recalcula os
 * totais, materializa a delivery_date (collect + MAX(turnaround)) e valida turnaround/mínimo.
 */
@Service
public class LavanderiaOrderService {

    private static final Logger log = LoggerFactory.getLogger(LavanderiaOrderService.class);
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final LavanderiaOrderRepository orderRepository;
    private final LavanderiaConfigRepository configRepository;
    private final LavanderiaOrderNotifier notifier;

    public LavanderiaOrderService(LavanderiaOrderRepository orderRepository,
                                  LavanderiaConfigRepository configRepository,
                                  LavanderiaOrderNotifier notifier) {
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

    /** Endereço de entrega ausente (→ 422 address_required). Sempre coleta+entrega. */
    public static class AddressRequiredException extends RuntimeException {}

    /** collect_date ausente/no passado (→ 422 collect_date_in_past). */
    public static class CollectDateInPastException extends RuntimeException {}

    /** subtotal abaixo do mínimo (→ 422 below_minimum). */
    public static class BelowMinimumOrderException extends RuntimeException {
        private final int minOrderCents;
        public BelowMinimumOrderException(int minOrderCents) { this.minOrderCents = minOrderCents; }
        public int minOrderCents() { return minOrderCents; }
    }

    /** delivery_date pedida anterior à primeira possível (→ 422 turnaround_violation). */
    public static class TurnaroundViolationOrderException extends RuntimeException {
        private final LocalDate firstPossibleDeliveryDate;
        public TurnaroundViolationOrderException(LocalDate firstPossibleDeliveryDate) {
            this.firstPossibleDeliveryDate = firstPossibleDeliveryDate;
        }
        public LocalDate firstPossibleDeliveryDate() { return firstPossibleDeliveryDate; }
    }

    /**
     * Cria um pedido a partir das linhas confirmadas pela IA. A taxa de entrega + mínimo + turnaround
     * default vêm do config do tenant. O repositório faz o snapshot e recalcula os totais (descarta o
     * total da IA), materializando a delivery_date.
     */
    @Transactional
    public LavanderiaOrder create(UUID companyId, UUID conversationId, UUID contactId,
                                  String deliveryAddress, List<OrderLineInput> lines, String notes,
                                  LocalDate collectDate, LocalDate requestedDeliveryDate, String period,
                                  String couponCode, boolean express) {
        if (deliveryAddress == null || deliveryAddress.isBlank()) {
            throw new AddressRequiredException();
        }
        if (collectDate == null) {
            throw new CollectDateInPastException();
        }
        if (collectDate.isBefore(LocalDate.now(ZONE))) {
            throw new CollectDateInPastException();
        }
        LavanderiaConfig config = configRepository.findByCompany(companyId);
        // express só quando habilitado na config (tag com express:true e toggle off → pedido normal).
        boolean effectiveExpress = express && config.expressEnabled();
        try {
            return orderRepository.createOrder(companyId, conversationId, contactId, deliveryAddress.strip(),
                lines, config.deliveryFeeCents(), config.minOrderCents(), notes,
                collectDate, requestedDeliveryDate, period, couponCode, effectiveExpress,
                config.expressSurchargePct(), config.expressTurnaroundDays());
        } catch (BelowMinimumException e) {
            throw new BelowMinimumOrderException(e.minOrderCents());
        } catch (TurnaroundViolationException e) {
            throw new TurnaroundViolationOrderException(e.firstPossibleDeliveryDate());
        }
    }

    public List<LavanderiaOrder> list(UUID companyId, String status, int limit, int offset) {
        return orderRepository.listByCompany(companyId, status, limit, offset);
    }

    public long count(UUID companyId, String status) {
        return orderRepository.countByCompany(companyId, status);
    }

    public Optional<LavanderiaOrder> get(UUID companyId, UUID id) {
        return orderRepository.findById(companyId, id);
    }

    /**
     * Transiciona o status do pedido (gate humano). Valida o alvo (enum) e a transição. Persiste —
     * gravando rejection_reason SÓ quando o alvo é {@code recusado} — e notifica o cliente com o texto
     * fixo do novo status (concatenando o motivo na recusa, defensivamente). Best-effort.
     */
    @Transactional
    public LavanderiaOrder updateStatus(UUID companyId, UUID id, String newStatusId, String rejectionReason) {
        LavanderiaOrderStatus newStatus = LavanderiaOrderStatus.fromId(newStatusId)
            .orElseThrow(InvalidStatusException::new);

        LavanderiaOrder current = orderRepository.findById(companyId, id)
            .orElseThrow(OrderNotFoundException::new);
        LavanderiaOrderStatus from = LavanderiaOrderStatus.fromId(current.status())
            .orElseThrow(InvalidStatusException::new);

        if (!from.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException();
        }

        String reasonToPersist = newStatus == LavanderiaOrderStatus.RECUSADO ? rejectionReason : null;
        orderRepository.updateStatus(companyId, id, newStatus.id(), reasonToPersist);

        String text = newStatus.notificationText();
        if (newStatus == LavanderiaOrderStatus.RECUSADO && rejectionReason != null && !rejectionReason.isBlank()) {
            text = text + " Motivo: " + rejectionReason.strip();
        }
        if (text != null) {
            notifier.notifyStatus(companyId, current.conversationId(), text);
        }

        return orderRepository.findById(companyId, id).orElseThrow(OrderNotFoundException::new);
    }
}
