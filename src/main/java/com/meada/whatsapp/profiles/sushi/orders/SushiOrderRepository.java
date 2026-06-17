package com.meada.whatsapp.profiles.sushi.orders;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code sushi_orders} + {@code sushi_order_items} (camada 7.1). Opera via service_role.
 * O pedido é criado com SNAPSHOT de preço+nome (lidos do cardápio no momento), e os totais são
 * calculados AQUI a partir do banco — nunca do que a IA mandou (defesa contra total chutado).
 */
@Repository
public class SushiOrderRepository {

    private final JdbcTemplate jdbcTemplate;

    public SushiOrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<SushiOrderItem> ITEM_MAPPER = (rs, rn) -> new SushiOrderItem(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("menu_item_id"),
        rs.getString("item_name_snapshot"),
        rs.getInt("qtd"),
        rs.getInt("unit_price_cents"));

    /** Mapeia a row de order (sem os itens — carregados à parte). */
    private SushiOrder mapOrder(java.sql.ResultSet rs, List<SushiOrderItem> items) throws java.sql.SQLException {
        return new SushiOrder(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("conversation_id"),
            rs.getString("status"),
            rs.getInt("subtotal_cents"),
            rs.getInt("delivery_fee_cents"),
            rs.getInt("total_cents"),
            rs.getString("delivery_address"),
            rs.getString("notes"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("status_updated_at").toInstant(),
            rs.getString("contact_name"),
            rs.getString("contact_phone"),
            items);
    }

    private static final String ORDER_SELECT =
        "select o.id, o.conversation_id, o.status, o.subtotal_cents, o.delivery_fee_cents, "
            + "o.total_cents, o.delivery_address, o.notes, o.created_at, o.status_updated_at, "
            + "ct.name as contact_name, ct.phone_number as contact_phone "
            + "from sushi_orders o join contacts ct on ct.id = o.contact_id ";

    /** Lista pedidos do tenant (filtro opcional por status), paginado, mais recentes primeiro. */
    public List<SushiOrder> listByCompany(UUID companyId, String status, int limit, int offset) {
        StringBuilder sql = new StringBuilder(ORDER_SELECT + "where o.company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) {
            sql.append(" and o.status = ?");
            args.add(status);
        }
        sql.append(" order by o.created_at desc limit ? offset ?");
        args.add(limit);
        args.add(offset);

        List<SushiOrder> orders = jdbcTemplate.query(sql.toString(),
            (rs, rn) -> mapOrder(rs, List.of()), args.toArray());
        // Hidrata os itens de cada pedido (lista costuma ser pequena — N+1 aceitável no Kanban).
        List<SushiOrder> withItems = new ArrayList<>(orders.size());
        for (SushiOrder o : orders) {
            withItems.add(withItems(o));
        }
        return withItems;
    }

    public long countByCompany(UUID companyId, String status) {
        StringBuilder sql = new StringBuilder("select count(*) from sushi_orders where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) {
            sql.append(" and status = ?");
            args.add(status);
        }
        Long n = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0L : n;
    }

    public Optional<SushiOrder> findById(UUID companyId, UUID id) {
        Optional<SushiOrder> base = jdbcTemplate.query(ORDER_SELECT + "where o.company_id = ? and o.id = ?",
                (rs, rn) -> mapOrder(rs, List.of()), companyId, id)
            .stream().findFirst();
        return base.map(this::withItems);
    }

    private SushiOrder withItems(SushiOrder o) {
        List<SushiOrderItem> items = jdbcTemplate.query(
            "select id, menu_item_id, item_name_snapshot, qtd, unit_price_cents "
                + "from sushi_order_items where order_id = ? order by id", ITEM_MAPPER, o.id());
        return new SushiOrder(o.id(), o.conversationId(), o.status(), o.subtotalCents(),
            o.deliveryFeeCents(), o.totalCents(), o.deliveryAddress(), o.notes(),
            o.createdAt(), o.statusUpdatedAt(), o.contactName(), o.contactPhone(), items);
    }

    /**
     * Cria o pedido + itens numa transação. Os preços/nomes são lidos do cardápio AGORA
     * (snapshot); o subtotal é a soma das linhas; o total = subtotal + delivery_fee. Linhas
     * cujo menu_item não existe/não é do tenant são IGNORADAS (o service já validou, mas
     * defendemos de novo). Lança se, após filtrar, não sobrar nenhuma linha válida.
     */
    @Transactional
    public SushiOrder createOrder(UUID companyId, UUID conversationId, UUID contactId,
                                  String deliveryAddress, List<OrderLineInput> lines,
                                  int deliveryFeeCents, String notes) {
        // Snapshot de preço+nome por linha (lê do cardápio do tenant).
        record Snap(UUID menuItemId, String name, int price, int qtd) {}
        List<Snap> snaps = new ArrayList<>();
        int subtotal = 0;
        for (OrderLineInput line : lines) {
            List<Snap> found = jdbcTemplate.query(
                "select name, price_cents from sushi_menu_items where company_id = ? and id = ?",
                (rs, rn) -> new Snap(line.menuItemId(), rs.getString("name"),
                    rs.getInt("price_cents"), line.qtd()),
                companyId, line.menuItemId());
            if (!found.isEmpty()) {
                Snap s = found.get(0);
                snaps.add(s);
                subtotal += s.price() * s.qtd();
            }
        }
        if (snaps.isEmpty()) {
            throw new IllegalArgumentException("nenhum item válido no pedido");
        }
        int total = subtotal + deliveryFeeCents;

        UUID orderId = jdbcTemplate.queryForObject(
            "insert into sushi_orders (company_id, conversation_id, contact_id, subtotal_cents, "
                + "delivery_fee_cents, total_cents, delivery_address, notes) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, conversationId, contactId, subtotal, deliveryFeeCents, total,
            deliveryAddress, notes);

        for (Snap s : snaps) {
            jdbcTemplate.update(
                "insert into sushi_order_items (order_id, menu_item_id, qtd, unit_price_cents, "
                    + "item_name_snapshot) values (?, ?, ?, ?, ?)",
                orderId, s.menuItemId(), s.qtd(), s.price(), s.name());
        }
        return findById(companyId, orderId).orElseThrow();
    }

    /** Persiste a transição de status + status_updated_at. Service já validou a transição. */
    public void updateStatus(UUID companyId, UUID id, String newStatus) {
        jdbcTemplate.update(
            "update sushi_orders set status = ?, status_updated_at = now() "
                + "where company_id = ? and id = ?",
            newStatus, companyId, id);
    }
}
