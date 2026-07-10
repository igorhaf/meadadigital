package com.meada.profiles.lingerie.orders;

import com.meada.profiles.lingerie.catalog.LingerieVariant;
import com.meada.profiles.lingerie.catalog.LingerieVariantRepository;
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
 * Acesso a {@code lingerie_orders} + {@code lingerie_order_items} (camada 8.21). Análogo ao
 * {@link com.meada.profiles.adega.orders.AdegaOrderRepository} (gate de aceite com
 * rejection_reason; recálculo de total no backend, total da IA descartado), com a ⭐ ESCAPADA desta
 * SM: o DECREMENTO TRANSACIONAL DE ESTOQUE da variante na criação. Opera via service_role; o escopo
 * por company_id no WHERE é a defesa.
 */
@Repository
public class LingerieOrderRepository {

    /** ⭐ Alguma variante pedida está esgotada (qtd > estoque) — o pedido inteiro é ABORTADO (rollback). */
    public static class OutOfStockException extends RuntimeException {}

    private final JdbcTemplate jdbcTemplate;
    private final LingerieVariantRepository variantRepository;

    private final com.meada.profiles.lingerie.coupons.LingerieCouponRepository couponRepository;
    private static final java.time.ZoneId BR = java.time.ZoneId.of("America/Sao_Paulo");

    public LingerieOrderRepository(JdbcTemplate jdbcTemplate,
                                   com.meada.profiles.lingerie.coupons.LingerieCouponRepository couponRepository,
                                   LingerieVariantRepository variantRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.couponRepository = couponRepository;
        this.variantRepository = variantRepository;
    }

    /** Mapeia a row de order_item. */
    private final RowMapper<LingerieOrderItem> ITEM_MAPPER = (rs, rn) -> new LingerieOrderItem(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("variant_id"),
        rs.getString("product_name_snapshot"),
        rs.getString("size_snapshot"),
        rs.getString("color_snapshot"),
        rs.getInt("qtd"),
        rs.getInt("unit_price_cents"));

    /** Mapeia a row de order (sem os itens — carregados à parte). */
    private LingerieOrder mapOrder(java.sql.ResultSet rs, List<LingerieOrderItem> items) throws java.sql.SQLException {
        return new LingerieOrder(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("conversation_id"),
            rs.getString("status"),
            rs.getString("fulfillment"),
            rs.getInt("subtotal_cents"),
            rs.getInt("discount_cents"),
            rs.getInt("delivery_fee_cents"),
            rs.getInt("total_cents"),
            rs.getString("coupon_code_snapshot"),
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
        "select o.id, o.conversation_id, o.status, o.fulfillment, o.subtotal_cents, o.discount_cents, "
            + "o.delivery_fee_cents, o.total_cents, o.coupon_code_snapshot, "
            + "o.delivery_address, o.notes, o.rejection_reason, "
            + "o.created_at, o.status_updated_at, ct.name as contact_name, ct.phone_number as contact_phone "
            + "from lingerie_orders o join contacts ct on ct.id = o.contact_id ";

    /** Lista pedidos do tenant (filtro opcional por status), paginado, mais recentes primeiro. */
    public List<LingerieOrder> listByCompany(UUID companyId, String status, int limit, int offset) {
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

        List<LingerieOrder> orders = jdbcTemplate.query(sql.toString(),
            (rs, rn) -> mapOrder(rs, List.of()), args.toArray());
        List<LingerieOrder> withItems = new ArrayList<>(orders.size());
        for (LingerieOrder o : orders) {
            withItems.add(withItems(o));
        }
        return withItems;
    }

    public long countByCompany(UUID companyId, String status) {
        StringBuilder sql = new StringBuilder("select count(*) from lingerie_orders where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) {
            sql.append(" and status = ?");
            args.add(status);
        }
        Long n = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0L : n;
    }

    public Optional<LingerieOrder> findById(UUID companyId, UUID id) {
        Optional<LingerieOrder> base = jdbcTemplate.query(ORDER_SELECT + "where o.company_id = ? and o.id = ?",
                (rs, rn) -> mapOrder(rs, List.of()), companyId, id)
            .stream().findFirst();
        return base.map(this::withItems);
    }

    private LingerieOrder withItems(LingerieOrder o) {
        List<LingerieOrderItem> items = jdbcTemplate.query(
            "select id, variant_id, product_name_snapshot, size_snapshot, color_snapshot, qtd, unit_price_cents "
                + "from lingerie_order_items where order_id = ? order by id", ITEM_MAPPER, o.id());
        return new LingerieOrder(o.id(), o.conversationId(), o.status(), o.fulfillment(),
            o.subtotalCents(), o.discountCents(), o.deliveryFeeCents(), o.totalCents(),
            o.couponCode(), o.deliveryAddress(), o.notes(),
            o.rejectionReason(), o.createdAt(), o.statusUpdatedAt(), o.contactName(), o.contactPhone(), items);
    }

    /**
     * ⭐ Cria o pedido + itens numa transação, DECREMENTANDO O ESTOQUE de cada variante. Para cada
     * linha: resolve a variante (available, do tenant) e o produto; computa {@code unit_price =
     * variant.price ?? product.base_price}; faz o snapshot de product_name/size/color; e DECREMENTA O
     * ESTOQUE via {@link LingerieVariantRepository#decrementStock} — se o UPDATE condicional afeta 0
     * linhas (estoque insuficiente), lança {@link OutOfStockException} e o @Transactional faz
     * ROLLBACK (NENHUM pedido parcial). Linhas cuja variante não existe/não é do tenant/está
     * indisponível são IGNORADAS (defesa — o handler já validou). subtotal = Σ unit_price × qtd;
     * total = subtotal + (entrega ? delivery_fee : 0). O total da IA é DESCARTADO. Lança
     * IllegalArgumentException se, após filtrar, não sobrar linha válida.
     */
    @Transactional
    public LingerieOrder createOrder(UUID companyId, UUID conversationId, UUID contactId,
                                     String fulfillment, String deliveryAddress, List<OrderLineInput> lines,
                                     String couponCode, int deliveryFeeCents, String notes) {
        record Snap(UUID variantId, String productName, String size, String color, int unitPrice, int qtd) {}

        // Resolve as variantes do tenant uma vez (uma query) e indexa por id.
        List<UUID> variantIds = new ArrayList<>();
        for (OrderLineInput line : lines) {
            variantIds.add(line.variantId());
        }
        Map<UUID, LingerieVariant> byId = new HashMap<>();
        for (LingerieVariant v : variantRepository.findByIdsForOrder(companyId, variantIds)) {
            byId.put(v.id(), v);
        }

        List<Snap> snaps = new ArrayList<>();
        int subtotal = 0;
        for (OrderLineInput line : lines) {
            LingerieVariant variant = byId.get(line.variantId());
            if (variant == null || !variant.available()) {
                continue;   // variante inexistente/de outro tenant/indisponível: ignora a linha (defesa).
            }
            // Resolve o produto (nome + base_price) — também valida disponibilidade do produto.
            record Prod(String name, int basePrice, boolean available) {}
            List<Prod> found = jdbcTemplate.query(
                "select name, base_price_cents, available from lingerie_products where company_id = ? and id = ?",
                (rs, rn) -> new Prod(rs.getString("name"), rs.getInt("base_price_cents"), rs.getBoolean("available")),
                companyId, variant.productId());
            if (found.isEmpty() || !found.get(0).available()) {
                continue;   // produto inexistente/indisponível: ignora.
            }
            Prod prod = found.get(0);
            int unitPrice = variant.priceCents() != null ? variant.priceCents() : prod.basePrice();

            // ⭐ DECREMENTO TRANSACIONAL — UPDATE condicional stock_qty >= qtd. 0 linhas → esgotado →
            // aborta o pedido inteiro (rollback do @Transactional).
            boolean decremented = variantRepository.decrementStock(companyId, variant.id(), line.qtd());
            if (!decremented) {
                throw new OutOfStockException();
            }

            snaps.add(new Snap(variant.id(), prod.name(), variant.size(), variant.color(), unitPrice, line.qtd()));
            subtotal += unitPrice * line.qtd();
        }
        if (snaps.isEmpty()) {
            throw new IllegalArgumentException("nenhum item válido no pedido");
        }

        // Cupom (onda 1, best-effort — inválido NÃO aborta, o pedido sai sem o desconto).
        UUID couponId = null;
        String couponSnapshot = null;
        int discount = 0;
        if (couponCode != null && !couponCode.isBlank()) {
            java.util.Optional<com.meada.profiles.lingerie.coupons.LingerieCoupon> maybe =
                couponRepository.findByCode(companyId, couponCode);
            if (maybe.isPresent()) {
                com.meada.profiles.lingerie.coupons.LingerieCoupon c = maybe.get();
                java.time.LocalDate today = java.time.LocalDate.now(BR);
                boolean valid = c.active()
                    && (c.validUntil() == null || !c.validUntil().isBefore(today))
                    && subtotal >= c.minOrderCents()
                    && (c.maxUses() == null || c.uses() < c.maxUses());
                if (valid) {
                    int raw = "percent".equals(c.kind()) ? subtotal * c.value() / 100 : c.value();
                    discount = Math.min(subtotal, raw);
                    couponId = c.id();
                    couponSnapshot = c.code();
                }
            }
        }

        boolean isEntrega = "entrega".equals(fulfillment);
        int fee = isEntrega ? deliveryFeeCents : 0;
        int total = subtotal - discount + fee;
        String address = isEntrega ? deliveryAddress : null;   // retirada não tem endereço.

        UUID orderId = jdbcTemplate.queryForObject(
            "insert into lingerie_orders (company_id, conversation_id, contact_id, fulfillment, "
                + "subtotal_cents, discount_cents, delivery_fee_cents, total_cents, coupon_id, "
                + "coupon_code_snapshot, delivery_address, notes) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, conversationId, contactId, fulfillment, subtotal, discount, fee,
            total, couponId, couponSnapshot, address, notes);

        // Incrementa uses do cupom aplicado (mesma transação).
        if (couponId != null) {
            couponRepository.incrementUses(companyId, couponId);
        }

        for (Snap s : snaps) {
            jdbcTemplate.update(
                "insert into lingerie_order_items (order_id, variant_id, qtd, unit_price_cents, "
                    + "product_name_snapshot, size_snapshot, color_snapshot) "
                    + "values (?, ?, ?, ?, ?, ?, ?)",
                orderId, s.variantId(), s.qtd(), s.unitPrice(), s.productName(), s.size(), s.color());
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
                "update lingerie_orders set status = ?, rejection_reason = ?, status_updated_at = now() "
                    + "where company_id = ? and id = ?",
                newStatus, rejectionReason, companyId, id);
        } else {
            jdbcTemplate.update(
                "update lingerie_orders set status = ?, status_updated_at = now() "
                    + "where company_id = ? and id = ?",
                newStatus, companyId, id);
        }
    }
}
