package com.meada.profiles.oficina.orders;

import com.meada.profiles.oficina.OficinaContextCache;
import com.meada.profiles.oficina.OsStatus;
import com.meada.profiles.oficina.mechanics.OsMechanic;
import com.meada.profiles.oficina.mechanics.OsMechanicRepository;
import com.meada.profiles.oficina.vehicles.OsVehicle;
import com.meada.profiles.oficina.vehicles.OsVehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras das ordens de serviço (camada 7.9).
 *
 * <p>{@link #open} valida o veículo (ativo, do tenant) e o mecânico (ativo, se informado), tira
 * snapshots de cliente (do contact do veículo) + veículo, e abre a OS em 'aberta' (total 0).
 *
 * <p>Itens ({@link #addItem}/{@link #updateItem}/{@link #deleteItem}): só mutáveis enquanto a OS NÃO
 * estiver travada ({@link OsStatus#itemsLocked()} — em_execucao/concluida/entregue/recusada/cancelada
 * → 409 order_locked). Cada mutação recalcula o total_cents na mesma transação (no repo).
 *
 * <p>{@link #updateStatus}: valida a transição; a passagem para 'orcada' exige total_cents &gt; 0
 * (400 empty_budget); notifica em orcada (com total + veículo), aprovada, concluida, entregue.
 */
@Service
public class ServiceOrderService {

    private final ServiceOrderRepository orderRepository;
    private final OsVehicleRepository vehicleRepository;
    private final OsMechanicRepository mechanicRepository;
    private final ServiceOrderNotifier notifier;
    private final OficinaContextCache contextCache;
    private final com.meada.profiles.oficina.catalog.OficinaCatalogRepository catalogRepository;
    private final com.meada.profiles.oficina.config.OficinaConfigRepository configRepository;

    public ServiceOrderService(ServiceOrderRepository orderRepository,
                               OsVehicleRepository vehicleRepository,
                               OsMechanicRepository mechanicRepository,
                               ServiceOrderNotifier notifier,
                               OficinaContextCache contextCache,
                               com.meada.profiles.oficina.catalog.OficinaCatalogRepository catalogRepository,
                               com.meada.profiles.oficina.config.OficinaConfigRepository configRepository) {
        this.orderRepository = orderRepository;
        this.vehicleRepository = vehicleRepository;
        this.mechanicRepository = mechanicRepository;
        this.notifier = notifier;
        this.contextCache = contextCache;
        this.catalogRepository = catalogRepository;
        this.configRepository = configRepository;
    }

    public static class OrderNotFoundException extends RuntimeException {}
    public static class VehicleNotFoundException extends RuntimeException {}
    public static class InactiveVehicleException extends RuntimeException {}
    public static class MechanicNotFoundException extends RuntimeException {}
    public static class InactiveMechanicException extends RuntimeException {}
    public static class ItemNotFoundException extends RuntimeException {}
    public static class OrderLockedException extends RuntimeException {}
    public static class EmptyBudgetException extends RuntimeException {}
    public static class InvalidKindException extends RuntimeException {}
    public static class InvalidStatusException extends RuntimeException {}
    public static class InvalidStatusTransitionException extends RuntimeException {}

    /** Abre uma OS (status aberta, total 0). Snapshots de cliente (do contact do veículo) + veículo. */
    @Transactional
    public ServiceOrder open(UUID companyId, UUID vehicleId, UUID mechanicId, UUID conversationId,
                             String complaint, String diagnosis, LocalDate expectedDelivery, String notes) {
        OsVehicle vehicle = vehicleRepository.findById(companyId, vehicleId).orElseThrow(VehicleNotFoundException::new);
        if (!vehicle.active()) {
            throw new InactiveVehicleException();
        }
        if (mechanicId != null) {
            OsMechanic m = mechanicRepository.findById(companyId, mechanicId).orElseThrow(MechanicNotFoundException::new);
            if (!m.active()) {
                throw new InactiveMechanicException();
            }
        }
        String customerName = vehicleRepository.contactName(companyId, vehicle.contactId()).orElse("Cliente");
        String customerPhone = vehicleRepository.contactPhone(companyId, vehicle.contactId()).orElse(null);

        ServiceOrder created = orderRepository.insertOrder(companyId, vehicleId, vehicle.contactId(),
            customerName, customerPhone, vehicle.plate(), vehicle.model(), mechanicId, conversationId,
            complaint, diagnosis, expectedDelivery, notes);
        contextCache.invalidate(companyId);
        return created;
    }

    /**
     * Onda 1 (backlog #1): abre a OS já PRÉ-PREENCHIDA com serviços TABELADOS do catálogo do
     * tenant (a IA passa só os ids no campo `servicos` da tag — o preço vem do catálogo, a trava
     * "IA não inventa preço" segue intacta). Item inexistente/inativo é IGNORADO (best-effort);
     * a OS continua nascendo 'aberta' (o mecânico revisa e move pra orcada).
     */
    @Transactional
    public ServiceOrder openWithCatalogItems(UUID companyId, UUID vehicleId, UUID mechanicId,
                                             UUID conversationId, String complaint, String diagnosis,
                                             LocalDate expectedDelivery, String notes,
                                             List<CatalogLine> catalogLines) {
        ServiceOrder created = open(companyId, vehicleId, mechanicId, conversationId, complaint,
            diagnosis, expectedDelivery, notes);
        if (catalogLines != null) {
            for (CatalogLine line : catalogLines) {
                var item = catalogRepository.findById(companyId, line.catalogItemId()).orElse(null);
                if (item == null || !item.active() || line.qtd() < 1) {
                    continue;   // best-effort: tabelado inexistente/inativo não aborta a abertura.
                }
                orderRepository.addItem(companyId, created.id(), "mao_de_obra", item.name(),
                    line.qtd(), item.unitPriceCents());
            }
        }
        contextCache.invalidate(companyId);
        return orderRepository.findById(companyId, created.id()).orElse(created);
    }

    /** Linha de serviço tabelado vinda da tag (onda 1, backlog #1). */
    public record CatalogLine(UUID catalogItemId, int qtd) {}

    public List<ServiceOrder> list(UUID companyId, String status, UUID mechanicId, UUID vehicleId,
                                   UUID contactId, Instant dateFrom, Instant dateTo, int limit, int offset) {
        return orderRepository.listByCompany(companyId, status, mechanicId, vehicleId, contactId, dateFrom, dateTo, limit, offset);
    }

    public long count(UUID companyId, String status, UUID mechanicId, UUID vehicleId, UUID contactId,
                      Instant dateFrom, Instant dateTo) {
        return orderRepository.countByCompany(companyId, status, mechanicId, vehicleId, contactId, dateFrom, dateTo);
    }

    public Optional<ServiceOrder> get(UUID companyId, UUID id) {
        return orderRepository.findById(companyId, id);
    }

    @Transactional
    public ServiceOrder updateFields(UUID companyId, UUID id, String diagnosis, UUID mechanicId,
                                     boolean mechanicProvided, LocalDate expectedDelivery,
                                     boolean expectedProvided, String notes) {
        if (mechanicProvided && mechanicId != null) {
            OsMechanic m = mechanicRepository.findById(companyId, mechanicId).orElseThrow(MechanicNotFoundException::new);
            if (!m.active()) {
                throw new InactiveMechanicException();
            }
        }
        ServiceOrder updated = orderRepository.updateFields(companyId, id, diagnosis, mechanicId,
            mechanicProvided, expectedDelivery, expectedProvided, notes).orElseThrow(OrderNotFoundException::new);
        contextCache.invalidate(companyId);
        return updated;
    }

    // -------------------------------------------------------------------------
    // ITENS
    // -------------------------------------------------------------------------

    private ServiceOrder requireMutableOrder(UUID companyId, UUID orderId) {
        ServiceOrder order = orderRepository.findById(companyId, orderId).orElseThrow(OrderNotFoundException::new);
        OsStatus status = OsStatus.fromId(order.status()).orElseThrow(InvalidStatusException::new);
        if (status.itemsLocked()) {
            throw new OrderLockedException();
        }
        return order;
    }

    private static void validateKind(String kind) {
        if (!"peca".equals(kind) && !"mao_de_obra".equals(kind)) {
            throw new InvalidKindException();
        }
    }

    @Transactional
    public OsItem addItem(UUID companyId, UUID orderId, String kind, String description,
                          int quantity, int unitPriceCents) {
        requireMutableOrder(companyId, orderId);
        validateKind(kind);
        OsItem item = orderRepository.addItem(companyId, orderId, kind, description, quantity, unitPriceCents);
        contextCache.invalidate(companyId);
        return item;
    }

    @Transactional
    public OsItem updateItem(UUID companyId, UUID orderId, UUID itemId, String kind, String description,
                             Integer quantity, Integer unitPriceCents) {
        requireMutableOrder(companyId, orderId);
        if (kind != null && !kind.isBlank()) {
            validateKind(kind);
        }
        OsItem item = orderRepository.updateItem(companyId, orderId, itemId, kind, description, quantity, unitPriceCents)
            .orElseThrow(ItemNotFoundException::new);
        contextCache.invalidate(companyId);
        return item;
    }

    @Transactional
    public void deleteItem(UUID companyId, UUID orderId, UUID itemId) {
        requireMutableOrder(companyId, orderId);
        if (!orderRepository.deleteItem(companyId, orderId, itemId)) {
            throw new ItemNotFoundException();
        }
        contextCache.invalidate(companyId);
    }

    // -------------------------------------------------------------------------
    // STATUS
    // -------------------------------------------------------------------------

    @Transactional
    public ServiceOrder updateStatus(UUID companyId, UUID id, String newStatusId) {
        OsStatus newStatus = OsStatus.fromId(newStatusId).orElseThrow(InvalidStatusException::new);
        ServiceOrder current = orderRepository.findById(companyId, id).orElseThrow(OrderNotFoundException::new);
        OsStatus from = OsStatus.fromId(current.status()).orElseThrow(InvalidStatusException::new);

        if (!from.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException();
        }
        // não dá pra orçar uma OS sem item (total derivado > 0).
        if (newStatus == OsStatus.ORCADA && current.totalCents() <= 0) {
            throw new EmptyBudgetException();
        }

        orderRepository.updateStatus(companyId, id, newStatus.id(), newStatus.isTerminal());

        // Onda #2: a ENTREGA materializa o retorno sugerido (hoje + return_reminder_days da config).
        if (newStatus == OsStatus.ENTREGUE) {
            var config = configRepository.findByCompany(companyId);
            orderRepository.setNextReturnDate(companyId, id,
                LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"))
                    .plusDays(config.returnReminderDays()));
        }

        String text = newStatus.notificationText(vehicleLabel(current), brl(current.totalCents()));
        notifier.notifyStatus(companyId, current.conversationId(), text);

        contextCache.invalidate(companyId);
        return orderRepository.findById(companyId, id).orElseThrow(OrderNotFoundException::new);
    }

    private static String vehicleLabel(ServiceOrder o) {
        if (o.vehicleModel() != null && !o.vehicleModel().isBlank()) {
            return o.vehicleModel() + " (" + o.vehiclePlate() + ")";
        }
        return o.vehiclePlate();
    }

    private static String brl(int cents) {
        return "R$ " + String.format("%d,%02d", cents / 100, cents % 100);
    }
}
