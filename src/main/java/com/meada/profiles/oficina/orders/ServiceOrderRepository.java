package com.meada.profiles.oficina.orders;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code service_orders} + {@code os_items} (camada 7.9). Opera via service_role.
 *
 * <p>O {@code total_cents} da OS e o {@code line_total_cents} de cada item são MATERIALIZADOS: cada
 * mutação de item (add/update/delete) roda numa transação que grava a linha e re-soma o total da OS
 * a partir do banco — nunca de um valor vindo de fora (lição end_at / total chutado do sushi).
 */
@Repository
public class ServiceOrderRepository {

    private final JdbcTemplate jdbcTemplate;

    public ServiceOrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<OsItem> ITEM_MAPPER = (rs, rn) -> new OsItem(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("service_order_id"),
        rs.getString("kind"),
        rs.getString("description"),
        rs.getInt("quantity"),
        rs.getInt("unit_price_cents"),
        rs.getInt("line_total_cents"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private static final String ORDER_SELECT =
        "select o.id, o.contact_id, o.vehicle_id, o.mechanic_id, o.conversation_id, "
            + "o.customer_name, o.customer_phone, o.vehicle_plate, o.vehicle_model, "
            + "m.name as mechanic_name, o.complaint, o.diagnosis, o.total_cents, o.status, "
            + "o.expected_delivery, o.notes, o.opened_at, o.closed_at, o.status_updated_at "
            + "from service_orders o left join os_mechanics m on m.id = o.mechanic_id ";

    private ServiceOrder mapOrder(java.sql.ResultSet rs, List<OsItem> items) throws java.sql.SQLException {
        Date ed = rs.getDate("expected_delivery");
        java.sql.Timestamp closed = rs.getTimestamp("closed_at");
        return new ServiceOrder(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("contact_id"),
            (UUID) rs.getObject("vehicle_id"),
            (UUID) rs.getObject("mechanic_id"),
            (UUID) rs.getObject("conversation_id"),
            rs.getString("customer_name"),
            rs.getString("customer_phone"),
            rs.getString("vehicle_plate"),
            rs.getString("vehicle_model"),
            rs.getString("mechanic_name"),
            rs.getString("complaint"),
            rs.getString("diagnosis"),
            rs.getInt("total_cents"),
            rs.getString("status"),
            ed == null ? null : ed.toLocalDate(),
            rs.getString("notes"),
            rs.getTimestamp("opened_at").toInstant(),
            closed == null ? null : closed.toInstant(),
            rs.getTimestamp("status_updated_at").toInstant(),
            items);
    }

    public List<ServiceOrder> listByCompany(UUID companyId, String status, UUID mechanicId, UUID vehicleId,
                                            UUID contactId, Instant dateFrom, Instant dateTo, int limit, int offset) {
        StringBuilder sql = new StringBuilder(ORDER_SELECT + "where o.company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) { sql.append(" and o.status = ?"); args.add(status); }
        if (mechanicId != null) { sql.append(" and o.mechanic_id = ?"); args.add(mechanicId); }
        if (vehicleId != null) { sql.append(" and o.vehicle_id = ?"); args.add(vehicleId); }
        if (contactId != null) { sql.append(" and o.contact_id = ?"); args.add(contactId); }
        if (dateFrom != null) { sql.append(" and o.opened_at >= ?"); args.add(java.sql.Timestamp.from(dateFrom)); }
        if (dateTo != null) { sql.append(" and o.opened_at <= ?"); args.add(java.sql.Timestamp.from(dateTo)); }
        sql.append(" order by o.opened_at desc limit ? offset ?");
        args.add(limit);
        args.add(offset);
        List<ServiceOrder> orders = jdbcTemplate.query(sql.toString(),
            (rs, rn) -> mapOrder(rs, List.of()), args.toArray());
        List<ServiceOrder> withItems = new ArrayList<>(orders.size());
        for (ServiceOrder o : orders) {
            withItems.add(withItems(o));
        }
        return withItems;
    }

    public long countByCompany(UUID companyId, String status, UUID mechanicId, UUID vehicleId,
                               UUID contactId, Instant dateFrom, Instant dateTo) {
        StringBuilder sql = new StringBuilder("select count(*) from service_orders where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) { sql.append(" and status = ?"); args.add(status); }
        if (mechanicId != null) { sql.append(" and mechanic_id = ?"); args.add(mechanicId); }
        if (vehicleId != null) { sql.append(" and vehicle_id = ?"); args.add(vehicleId); }
        if (contactId != null) { sql.append(" and contact_id = ?"); args.add(contactId); }
        if (dateFrom != null) { sql.append(" and opened_at >= ?"); args.add(java.sql.Timestamp.from(dateFrom)); }
        if (dateTo != null) { sql.append(" and opened_at <= ?"); args.add(java.sql.Timestamp.from(dateTo)); }
        Long n = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0L : n;
    }

    public Optional<ServiceOrder> findById(UUID companyId, UUID id) {
        Optional<ServiceOrder> base = jdbcTemplate.query(ORDER_SELECT + "where o.company_id = ? and o.id = ?",
                (rs, rn) -> mapOrder(rs, List.of()), companyId, id)
            .stream().findFirst();
        return base.map(this::withItems);
    }

    private ServiceOrder withItems(ServiceOrder o) {
        List<OsItem> items = jdbcTemplate.query(
            "select id, service_order_id, kind, description, quantity, unit_price_cents, line_total_cents, "
                + "created_at, updated_at from os_items where service_order_id = ? order by created_at asc",
            ITEM_MAPPER, o.id());
        return new ServiceOrder(o.id(), o.contactId(), o.vehicleId(), o.mechanicId(), o.conversationId(),
            o.customerName(), o.customerPhone(), o.vehiclePlate(), o.vehicleModel(), o.mechanicName(),
            o.complaint(), o.diagnosis(), o.totalCents(), o.status(), o.expectedDelivery(), o.notes(),
            o.openedAt(), o.closedAt(), o.statusUpdatedAt(), items);
    }

    /**
     * Abre a OS (status 'aberta', total 0). Snapshots de cliente (name/phone do contact do veículo)
     * + veículo (plate/model). mechanicId/conversationId/diagnosis/expectedDelivery opcionais.
     */
    public ServiceOrder insertOrder(UUID companyId, UUID vehicleId, UUID contactId, String customerName,
                                    String customerPhone, String vehiclePlate, String vehicleModel,
                                    UUID mechanicId, UUID conversationId, String complaint,
                                    String diagnosis, LocalDate expectedDelivery, String notes) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into service_orders (company_id, contact_id, vehicle_id, mechanic_id, conversation_id, "
                + "customer_name, customer_phone, vehicle_plate, vehicle_model, complaint, diagnosis, "
                + "expected_delivery, notes, total_cents, status) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 'aberta') returning id",
            UUID.class, companyId, contactId, vehicleId, mechanicId, conversationId, customerName,
            customerPhone, vehiclePlate, vehicleModel, complaint, diagnosis,
            expectedDelivery == null ? null : Date.valueOf(expectedDelivery), notes);
        return findById(companyId, id).orElseThrow();
    }

    /** Atualiza campos editáveis da OS (diagnosis/mechanic/expectedDelivery/notes). */
    public Optional<ServiceOrder> updateFields(UUID companyId, UUID id, String diagnosis, UUID mechanicId,
                                               boolean mechanicProvided, LocalDate expectedDelivery,
                                               boolean expectedProvided, String notes) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (diagnosis != null) { sets.add("diagnosis = ?"); args.add(diagnosis); }
        if (mechanicProvided) { sets.add("mechanic_id = ?"); args.add(mechanicId); }
        if (expectedProvided) { sets.add("expected_delivery = ?"); args.add(expectedDelivery == null ? null : Date.valueOf(expectedDelivery)); }
        if (notes != null) { sets.add("notes = ?"); args.add(notes); }
        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update("update service_orders set " + String.join(", ", sets)
                + " where company_id = ? and id = ?", args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    /**
     * Persiste a transição de status + status_updated_at. Preenche closed_at em terminais
     * (concluida/entregue/cancelada/recusada); limpa-o ao sair de um terminal não é possível
     * (são terminais). Service já validou a transição.
     */
    public void updateStatus(UUID companyId, UUID id, String newStatus, boolean terminal) {
        if (terminal) {
            jdbcTemplate.update("update service_orders set status = ?, status_updated_at = now(), "
                + "closed_at = now(), updated_at = now() where company_id = ? and id = ?",
                newStatus, companyId, id);
        } else {
            jdbcTemplate.update("update service_orders set status = ?, status_updated_at = now(), "
                + "updated_at = now() where company_id = ? and id = ?",
                newStatus, companyId, id);
        }
    }

    // -------------------------------------------------------------------------
    // ITENS — cada mutação recalcula o total_cents da OS na MESMA transação.
    // -------------------------------------------------------------------------

    public List<OsItem> listItems(UUID serviceOrderId) {
        return jdbcTemplate.query(
            "select id, service_order_id, kind, description, quantity, unit_price_cents, line_total_cents, "
                + "created_at, updated_at from os_items where service_order_id = ? order by created_at asc",
            ITEM_MAPPER, serviceOrderId);
    }

    public Optional<OsItem> findItem(UUID companyId, UUID serviceOrderId, UUID itemId) {
        return jdbcTemplate.query(
            "select id, service_order_id, kind, description, quantity, unit_price_cents, line_total_cents, "
                + "created_at, updated_at from os_items where company_id = ? and service_order_id = ? and id = ?",
            ITEM_MAPPER, companyId, serviceOrderId, itemId).stream().findFirst();
    }

    @Transactional
    public OsItem addItem(UUID companyId, UUID serviceOrderId, String kind, String description,
                          int quantity, int unitPriceCents) {
        int lineTotal = quantity * unitPriceCents;
        UUID id = jdbcTemplate.queryForObject(
            "insert into os_items (company_id, service_order_id, kind, description, quantity, "
                + "unit_price_cents, line_total_cents) values (?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, serviceOrderId, kind, description.trim(), quantity, unitPriceCents, lineTotal);
        recalcTotal(companyId, serviceOrderId);
        return findItem(companyId, serviceOrderId, id).orElseThrow();
    }

    @Transactional
    public Optional<OsItem> updateItem(UUID companyId, UUID serviceOrderId, UUID itemId, String kind,
                                       String description, Integer quantity, Integer unitPriceCents) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (kind != null && !kind.isBlank()) { sets.add("kind = ?"); args.add(kind); }
        if (description != null && !description.isBlank()) { sets.add("description = ?"); args.add(description.trim()); }
        if (quantity != null) { sets.add("quantity = ?"); args.add(quantity); }
        if (unitPriceCents != null) { sets.add("unit_price_cents = ?"); args.add(unitPriceCents); }
        if (sets.isEmpty()) {
            return findItem(companyId, serviceOrderId, itemId);
        }
        // recalcula line_total_cents = quantity * unit_price (com os valores finais).
        sets.add("line_total_cents = quantity * unit_price_cents");
        sets.add("updated_at = now()");
        args.add(companyId);
        args.add(serviceOrderId);
        args.add(itemId);
        int n = jdbcTemplate.update("update os_items set " + String.join(", ", sets)
            + " where company_id = ? and service_order_id = ? and id = ?", args.toArray());
        if (n == 0) {
            return Optional.empty();
        }
        recalcTotal(companyId, serviceOrderId);
        return findItem(companyId, serviceOrderId, itemId);
    }

    @Transactional
    public boolean deleteItem(UUID companyId, UUID serviceOrderId, UUID itemId) {
        int n = jdbcTemplate.update("delete from os_items where company_id = ? and service_order_id = ? and id = ?",
            companyId, serviceOrderId, itemId);
        if (n == 0) {
            return false;
        }
        recalcTotal(companyId, serviceOrderId);
        return true;
    }

    /** Re-soma o total da OS a partir das linhas (materializa o derivado). */
    private void recalcTotal(UUID companyId, UUID serviceOrderId) {
        jdbcTemplate.update(
            "update service_orders set total_cents = coalesce("
                + "(select sum(line_total_cents) from os_items where service_order_id = ?), 0), "
                + "updated_at = now() where company_id = ? and id = ?",
            serviceOrderId, companyId, serviceOrderId);
    }

    /** Materializa o retorno sugerido na ENTREGA (onda 1, backlog #2). */
    public void setNextReturnDate(UUID companyId, UUID id, java.time.LocalDate nextReturnDate) {
        jdbcTemplate.update(
            "update service_orders set next_return_date = ? where company_id = ? and id = ?",
            java.sql.Date.valueOf(nextReturnDate), companyId, id);
    }
}
