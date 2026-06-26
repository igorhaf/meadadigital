package com.meada.whatsapp.profiles.suplementos.orders;

import com.meada.whatsapp.profiles.suplementos.catalog.SupVariant;
import com.meada.whatsapp.profiles.suplementos.catalog.SupVariantRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code sup_orders} + {@code sup_order_items} (camada 8.24). Clone do
 * {@link com.meada.whatsapp.profiles.lingerie.orders.LingerieOrderRepository} (gate de aceite com
 * rejection_reason; recálculo de total no backend, total da IA descartado; ⭐ DECREMENTO TRANSACIONAL
 * de estoque da variante na criação) com 2 diferenças: (1) SÓ ENTREGA — sem coluna fulfillment,
 * delivery_address obrigatório; ausente → {@link AddressRequiredException}; (2) o order_item guarda
 * product_id E variant_id + um único variant_label_snapshot ("Chocolate 900g"). Opera via
 * service_role; o escopo por company_id no WHERE é a defesa.
 */
@Repository
public class SupOrderRepository {

    /** ⭐ Alguma variante pedida está esgotada (qtd > estoque) — o pedido inteiro é ABORTADO (rollback). */
    public static class OutOfStockException extends RuntimeException {}

    /** Pedido de entrega sem endereço (→ 422 address_required). SÓ ENTREGA nesta SM. */
    public static class AddressRequiredException extends RuntimeException {}

    private final JdbcTemplate jdbcTemplate;
    private final SupVariantRepository variantRepository;

    public SupOrderRepository(JdbcTemplate jdbcTemplate, SupVariantRepository variantRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.variantRepository = variantRepository;
    }

    /** Mapeia a row de order_item. */
    private final RowMapper<SupOrderItem> ITEM_MAPPER = (rs, rn) -> new SupOrderItem(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("product_id"),
        (UUID) rs.getObject("variant_id"),
        rs.getString("product_name_snapshot"),
        rs.getString("variant_label_snapshot"),
        rs.getInt("qtd"),
        rs.getInt("unit_price_cents"));

    /** Mapeia a row de order (sem os itens — carregados à parte). */
    private SupOrder mapOrder(java.sql.ResultSet rs, List<SupOrderItem> items) throws java.sql.SQLException {
        return new SupOrder(
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
            + "o.total_cents, o.delivery_address, o.notes, o.rejection_reason, "
            + "o.created_at, o.status_updated_at, ct.name as contact_name, ct.phone_number as contact_phone "
            + "from sup_orders o join contacts ct on ct.id = o.contact_id ";

    /** Lista pedidos do tenant (filtro opcional por status), paginado, mais recentes primeiro. */
    public List<SupOrder> listByCompany(UUID companyId, String status, int limit, int offset) {
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

        List<SupOrder> orders = jdbcTemplate.query(sql.toString(),
            (rs, rn) -> mapOrder(rs, List.of()), args.toArray());
        List<SupOrder> withItems = new ArrayList<>(orders.size());
        for (SupOrder o : orders) {
            withItems.add(withItems(o));
        }
        return withItems;
    }

    public long countByCompany(UUID companyId, String status) {
        StringBuilder sql = new StringBuilder("select count(*) from sup_orders where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) {
            sql.append(" and status = ?");
            args.add(status);
        }
        Long n = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0L : n;
    }

    public Optional<SupOrder> findById(UUID companyId, UUID id) {
        Optional<SupOrder> base = jdbcTemplate.query(ORDER_SELECT + "where o.company_id = ? and o.id = ?",
                (rs, rn) -> mapOrder(rs, List.of()), companyId, id)
            .stream().findFirst();
        return base.map(this::withItems);
    }

    private SupOrder withItems(SupOrder o) {
        List<SupOrderItem> items = jdbcTemplate.query(
            "select id, product_id, variant_id, product_name_snapshot, variant_label_snapshot, qtd, unit_price_cents "
                + "from sup_order_items where order_id = ? order by id", ITEM_MAPPER, o.id());
        return new SupOrder(o.id(), o.conversationId(), o.status(), o.subtotalCents(), o.deliveryFeeCents(),
            o.totalCents(), o.deliveryAddress(), o.notes(), o.rejectionReason(), o.createdAt(),
            o.statusUpdatedAt(), o.contactName(), o.contactPhone(), items);
    }

    /**
     * ⭐ Cria o pedido + itens numa transação, DECREMENTANDO O ESTOQUE de cada variante. Para cada
     * linha: resolve a variante (active, do tenant) e o produto; computa {@code unit_price =
     * variant.price_cents}; faz o snapshot de product_name/variant_label; e DECREMENTA O ESTOQUE via
     * {@link SupVariantRepository#decrementStock} — se o UPDATE condicional afeta 0 linhas (estoque
     * insuficiente), lança {@link OutOfStockException} e o @Transactional faz ROLLBACK (NENHUM pedido
     * parcial). Linhas cuja variante não existe/não é do tenant/está inativa são IGNORADAS (defesa — o
     * handler já validou). subtotal = Σ unit_price × qtd; total = subtotal + delivery_fee (SEMPRE
     * entrega). O total da IA é DESCARTADO. {@code deliveryAddress} obrigatório → {@link
     * AddressRequiredException} se ausente. Lança IllegalArgumentException se, após filtrar, não sobrar
     * linha válida.
     */
    @Transactional
    public SupOrder createOrder(UUID companyId, UUID conversationId, UUID contactId,
                                String deliveryAddress, List<OrderLineInput> lines,
                                int deliveryFeeCents, String notes) {
        if (deliveryAddress == null || deliveryAddress.isBlank()) {
            throw new AddressRequiredException();   // SÓ entrega nesta SM.
        }
        record Snap(UUID productId, UUID variantId, String productName, String variantLabel,
                    int unitPrice, int qtd) {}

        // Resolve as variantes do tenant uma vez (uma query) e indexa por id.
        List<UUID> variantIds = new ArrayList<>();
        for (OrderLineInput line : lines) {
            variantIds.add(line.variantId());
        }
        Map<UUID, SupVariant> byId = new HashMap<>();
        for (SupVariant v : variantRepository.findByIdsForOrder(companyId, variantIds)) {
            byId.put(v.id(), v);
        }

        List<Snap> snaps = new ArrayList<>();
        int subtotal = 0;
        for (OrderLineInput line : lines) {
            SupVariant variant = byId.get(line.variantId());
            if (variant == null || !variant.active()) {
                continue;   // variante inexistente/de outro tenant/inativa: ignora a linha (defesa).
            }
            // Resolve o produto (nome) — também valida disponibilidade do produto.
            record Prod(String name, boolean active) {}
            List<Prod> found = jdbcTemplate.query(
                "select name, active from sup_products where company_id = ? and id = ?",
                (rs, rn) -> new Prod(rs.getString("name"), rs.getBoolean("active")),
                companyId, variant.productId());
            if (found.isEmpty() || !found.get(0).active()) {
                continue;   // produto inexistente/inativo: ignora.
            }
            Prod prod = found.get(0);
            int unitPrice = variant.priceCents();

            // ⭐ DECREMENTO TRANSACIONAL — UPDATE condicional stock_quantity >= qtd. 0 linhas → esgotado →
            // aborta o pedido inteiro (rollback do @Transactional).
            boolean decremented = variantRepository.decrementStock(companyId, variant.id(), line.qtd());
            if (!decremented) {
                throw new OutOfStockException();
            }

            snaps.add(new Snap(variant.productId(), variant.id(), prod.name(), variant.label(),
                unitPrice, line.qtd()));
            subtotal += unitPrice * line.qtd();
        }
        if (snaps.isEmpty()) {
            throw new IllegalArgumentException("nenhum item válido no pedido");
        }

        int total = subtotal + deliveryFeeCents;   // SEMPRE entrega — taxa sempre soma.

        UUID orderId = jdbcTemplate.queryForObject(
            "insert into sup_orders (company_id, conversation_id, contact_id, "
                + "subtotal_cents, delivery_fee_cents, total_cents, delivery_address, notes) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, conversationId, contactId, subtotal, deliveryFeeCents, total,
            deliveryAddress.strip(), notes);

        for (Snap s : snaps) {
            jdbcTemplate.update(
                "insert into sup_order_items (order_id, product_id, variant_id, qtd, unit_price_cents, "
                    + "product_name_snapshot, variant_label_snapshot) "
                    + "values (?, ?, ?, ?, ?, ?, ?)",
                orderId, s.productId(), s.variantId(), s.qtd(), s.unitPrice(), s.productName(),
                s.variantLabel());
        }
        return findById(companyId, orderId).orElseThrow();
    }

    /**
     * Persiste a transição de status + status_updated_at. Service já validou a transição. Quando
     * {@code rejectionReason != null} (recusa), grava também o motivo.
     */
    public void updateStatus(UUID companyId, UUID id, String newStatus, String rejectionReason) {
        if (rejectionReason != null) {
            jdbcTemplate.update(
                "update sup_orders set status = ?, rejection_reason = ?, status_updated_at = now() "
                    + "where company_id = ? and id = ?",
                newStatus, rejectionReason, companyId, id);
        } else {
            jdbcTemplate.update(
                "update sup_orders set status = ?, status_updated_at = now() "
                    + "where company_id = ? and id = ?",
                newStatus, companyId, id);
        }
    }
}
