package com.meada.whatsapp.profiles.comida.orders;

import com.meada.whatsapp.profiles.comida.menu.ComidaMenuOption;
import com.meada.whatsapp.profiles.comida.menu.ComidaMenuOptionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code comida_orders} + {@code comida_order_items} + {@code comida_order_item_options}
 * (camada 8.4). Clone de {@link com.meada.whatsapp.profiles.sushi.orders.SushiOrderRepository} com
 * as DUAS escapadas: (1) {@code rejection_reason} na transição de status; (2) opções por item, com
 * recálculo do unit_price = base + Σ deltas (descarta o total da IA). Opera via service_role; o
 * escopo por company_id no WHERE é a defesa.
 */
@Repository
public class ComidaOrderRepository {

    /** Alguma opção pedida é inválida/indisponível/de outro item — o pedido NÃO é criado. */
    public static class InvalidOptionException extends RuntimeException {}

    private final JdbcTemplate jdbcTemplate;
    private final ComidaMenuOptionRepository optionRepository;

    public ComidaOrderRepository(JdbcTemplate jdbcTemplate,
                                 ComidaMenuOptionRepository optionRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.optionRepository = optionRepository;
    }

    private final RowMapper<ComidaOrderItemOption> ITEM_OPTION_MAPPER = (rs, rn) -> new ComidaOrderItemOption(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("menu_option_id"),
        rs.getString("group_label_snapshot"),
        rs.getString("option_label_snapshot"),
        rs.getInt("price_delta_cents"));

    /** Mapeia a row de order_item SEM as opções (carregadas à parte). */
    private final RowMapper<ComidaOrderItem> ITEM_MAPPER = (rs, rn) -> new ComidaOrderItem(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("menu_item_id"),
        rs.getString("item_name_snapshot"),
        rs.getInt("qtd"),
        rs.getInt("unit_price_cents"),
        List.of());

    /** Mapeia a row de order (sem os itens — carregados à parte). */
    private ComidaOrder mapOrder(java.sql.ResultSet rs, List<ComidaOrderItem> items) throws java.sql.SQLException {
        return new ComidaOrder(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("conversation_id"),
            rs.getString("status"),
            rs.getInt("subtotal_cents"),
            rs.getInt("delivery_fee_cents"),
            rs.getInt("total_cents"),
            rs.getString("delivery_address"),
            rs.getString("notes"),
            rs.getString("rejection_reason"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("status_updated_at").toInstant(),
            rs.getString("contact_name"),
            rs.getString("contact_phone"),
            items);
    }

    private static final String ORDER_SELECT =
        "select o.id, o.conversation_id, o.status, o.subtotal_cents, o.delivery_fee_cents, "
            + "o.total_cents, o.delivery_address, o.notes, o.rejection_reason, o.created_at, "
            + "o.status_updated_at, ct.name as contact_name, ct.phone_number as contact_phone "
            + "from comida_orders o join contacts ct on ct.id = o.contact_id ";

    /** Lista pedidos do tenant (filtro opcional por status), paginado, mais recentes primeiro. */
    public List<ComidaOrder> listByCompany(UUID companyId, String status, int limit, int offset) {
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

        List<ComidaOrder> orders = jdbcTemplate.query(sql.toString(),
            (rs, rn) -> mapOrder(rs, List.of()), args.toArray());
        // Hidrata itens (e suas opções) de cada pedido (lista pequena — N+1 aceitável no Kanban).
        List<ComidaOrder> withItems = new ArrayList<>(orders.size());
        for (ComidaOrder o : orders) {
            withItems.add(withItems(o));
        }
        return withItems;
    }

    public long countByCompany(UUID companyId, String status) {
        StringBuilder sql = new StringBuilder("select count(*) from comida_orders where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) {
            sql.append(" and status = ?");
            args.add(status);
        }
        Long n = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0L : n;
    }

    public Optional<ComidaOrder> findById(UUID companyId, UUID id) {
        Optional<ComidaOrder> base = jdbcTemplate.query(ORDER_SELECT + "where o.company_id = ? and o.id = ?",
                (rs, rn) -> mapOrder(rs, List.of()), companyId, id)
            .stream().findFirst();
        return base.map(this::withItems);
    }

    private ComidaOrder withItems(ComidaOrder o) {
        List<ComidaOrderItem> bare = jdbcTemplate.query(
            "select id, menu_item_id, item_name_snapshot, qtd, unit_price_cents "
                + "from comida_order_items where order_id = ? order by id", ITEM_MAPPER, o.id());
        List<ComidaOrderItem> withOpts = new ArrayList<>(bare.size());
        for (ComidaOrderItem it : bare) {
            List<ComidaOrderItemOption> options = jdbcTemplate.query(
                "select id, menu_option_id, group_label_snapshot, option_label_snapshot, price_delta_cents "
                    + "from comida_order_item_options where order_item_id = ? order by id",
                ITEM_OPTION_MAPPER, it.id());
            withOpts.add(new ComidaOrderItem(it.id(), it.menuItemId(), it.itemName(), it.qtd(),
                it.unitPriceCents(), options));
        }
        return new ComidaOrder(o.id(), o.conversationId(), o.status(), o.subtotalCents(),
            o.deliveryFeeCents(), o.totalCents(), o.deliveryAddress(), o.notes(), o.rejectionReason(),
            o.createdAt(), o.statusUpdatedAt(), o.contactName(), o.contactPhone(), withOpts);
    }

    /**
     * Cria o pedido + itens + opções numa transação. Os preços/nomes são lidos do cardápio AGORA
     * (snapshot); para cada linha, {@code unit_price = base + Σ deltas} das opções escolhidas. O
     * subtotal é a soma de unit_price × qtd; o total = subtotal + delivery_fee. Linhas cujo
     * menu_item não existe/não é do tenant são IGNORADAS (o handler já validou). Se alguma opção
     * pedida é inválida/indisponível/de outro item, lança {@link InvalidOptionException} (pedido NÃO
     * criado com opção fantasma). Lança IllegalArgumentException se, após filtrar, não sobrar linha.
     */
    @Transactional
    public ComidaOrder createOrder(UUID companyId, UUID conversationId, UUID contactId,
                                   String deliveryAddress, List<OrderLineInput> lines,
                                   int deliveryFeeCents, String notes) {
        // Snapshot de preço+nome+opções por linha (lê do cardápio do tenant).
        record OptSnap(UUID menuOptionId, String groupLabel, String optionLabel, int delta) {}
        record Snap(UUID menuItemId, String name, int unitPrice, int qtd, List<OptSnap> options) {}

        List<Snap> snaps = new ArrayList<>();
        int subtotal = 0;
        for (OrderLineInput line : lines) {
            record Base(String name, int price) {}
            List<Base> found = jdbcTemplate.query(
                "select name, price_cents from comida_menu_items where company_id = ? and id = ?",
                (rs, rn) -> new Base(rs.getString("name"), rs.getInt("price_cents")),
                companyId, line.menuItemId());
            if (found.isEmpty()) {
                continue;   // item inexistente/de outro tenant: ignora a linha (defesa).
            }
            Base base = found.get(0);

            // Resolve as opções escolhidas (ESCAPADA 2). Tamanho divergente → opção fantasma.
            List<UUID> optionIds = line.optionIds() == null ? List.of() : line.optionIds();
            List<OptSnap> optSnaps = new ArrayList<>();
            int deltaSum = 0;
            if (!optionIds.isEmpty()) {
                List<ComidaMenuOption> resolved =
                    optionRepository.findByIdsForItem(companyId, line.menuItemId(), optionIds);
                if (resolved.size() != optionIds.size()) {
                    throw new InvalidOptionException();
                }
                for (ComidaMenuOption opt : resolved) {
                    optSnaps.add(new OptSnap(opt.id(), opt.groupLabel(), opt.optionLabel(), opt.priceDeltaCents()));
                    deltaSum += opt.priceDeltaCents();
                }
            }

            int unitPrice = base.price() + deltaSum;
            snaps.add(new Snap(line.menuItemId(), base.name(), unitPrice, line.qtd(), optSnaps));
            subtotal += unitPrice * line.qtd();
        }
        if (snaps.isEmpty()) {
            throw new IllegalArgumentException("nenhum item válido no pedido");
        }
        int total = subtotal + deliveryFeeCents;

        // status default 'aguardando' (não passamos status — ESCAPADA 1).
        UUID orderId = jdbcTemplate.queryForObject(
            "insert into comida_orders (company_id, conversation_id, contact_id, subtotal_cents, "
                + "delivery_fee_cents, total_cents, delivery_address, notes) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, conversationId, contactId, subtotal, deliveryFeeCents, total,
            deliveryAddress, notes);

        for (Snap s : snaps) {
            UUID orderItemId = jdbcTemplate.queryForObject(
                "insert into comida_order_items (order_id, menu_item_id, qtd, unit_price_cents, "
                    + "item_name_snapshot) values (?, ?, ?, ?, ?) returning id",
                UUID.class, orderId, s.menuItemId(), s.qtd(), s.unitPrice(), s.name());
            for (OptSnap opt : s.options()) {
                jdbcTemplate.update(
                    "insert into comida_order_item_options (order_item_id, menu_option_id, "
                        + "group_label_snapshot, option_label_snapshot, price_delta_cents) "
                        + "values (?, ?, ?, ?, ?)",
                    orderItemId, opt.menuOptionId(), opt.groupLabel(), opt.optionLabel(), opt.delta());
            }
        }
        return findById(companyId, orderId).orElseThrow();
    }

    /**
     * Persiste a transição de status + status_updated_at. Service já validou a transição. Quando
     * {@code rejectionReason != null} (recusa — ESCAPADA 1), grava também o motivo.
     */
    public void updateStatus(UUID companyId, UUID id, String newStatus, String rejectionReason) {
        if (rejectionReason != null) {
            jdbcTemplate.update(
                "update comida_orders set status = ?, rejection_reason = ?, status_updated_at = now() "
                    + "where company_id = ? and id = ?",
                newStatus, rejectionReason, companyId, id);
        } else {
            jdbcTemplate.update(
                "update comida_orders set status = ?, status_updated_at = now() "
                    + "where company_id = ? and id = ?",
                newStatus, companyId, id);
        }
    }
}
