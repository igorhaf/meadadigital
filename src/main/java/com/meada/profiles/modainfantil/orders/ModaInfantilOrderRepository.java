package com.meada.profiles.modainfantil.orders;

import com.meada.profiles.modainfantil.catalog.ModaInfantilVariant;
import com.meada.profiles.modainfantil.catalog.ModaInfantilVariantRepository;
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
 * Acesso a {@code moda_infantil_orders} + {@code moda_infantil_order_items} (camada 8.22). Clone do
 * {@link com.meada.profiles.lingerie.orders.LingerieOrderRepository} (gate de aceite com
 * rejection_reason; recálculo de total no backend, total da IA descartado; DECREMENTO TRANSACIONAL DE
 * ESTOQUE na criação), COM a ⭐ ADAPTAÇÃO da camada 8.22: o {@link #updateStatus} DEVOLVE o estoque das
 * variantes quando o pedido vai pra {@code recusado}/{@code cancelado} — na MESMA transação da
 * mudança de status, e de forma IDEMPOTENTE (só devolve se {@code stock_returned} ainda for false, e
 * marca true). Opera via service_role; o escopo por company_id no WHERE é a defesa.
 */
@Repository
public class ModaInfantilOrderRepository {

    /** Alguma variante pedida está esgotada (qtd > estoque) — o pedido inteiro é ABORTADO (rollback). */
    public static class OutOfStockException extends RuntimeException {}

    private final JdbcTemplate jdbcTemplate;
    private final ModaInfantilVariantRepository variantRepository;

    private final com.meada.profiles.modainfantil.coupons.ModaInfantilCouponRepository couponRepository;
    private static final java.time.ZoneId BR = java.time.ZoneId.of("America/Sao_Paulo");

    public ModaInfantilOrderRepository(JdbcTemplate jdbcTemplate,
                                       com.meada.profiles.modainfantil.coupons.ModaInfantilCouponRepository couponRepository,
                                       ModaInfantilVariantRepository variantRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.couponRepository = couponRepository;
        this.variantRepository = variantRepository;
    }

    /** Mapeia a row de order_item. */
    private final RowMapper<ModaInfantilOrderItem> ITEM_MAPPER = (rs, rn) -> new ModaInfantilOrderItem(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("variant_id"),
        rs.getString("product_name_snapshot"),
        rs.getString("size_snapshot"),
        rs.getString("color_snapshot"),
        rs.getInt("qtd"),
        rs.getInt("unit_price_cents"));

    /** Mapeia a row de order (sem os itens — carregados à parte). */
    private ModaInfantilOrder mapOrder(java.sql.ResultSet rs, List<ModaInfantilOrderItem> items) throws java.sql.SQLException {
        return new ModaInfantilOrder(
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
            rs.getBoolean("stock_returned"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("status_updated_at").toInstant(),
            rs.getString("contact_name"),
            rs.getString("contact_phone"),
            items);
    }

    private static final String ORDER_SELECT =
        "select o.id, o.conversation_id, o.status, o.fulfillment, o.subtotal_cents, o.discount_cents, "
            + "o.delivery_fee_cents, o.total_cents, o.coupon_code_snapshot, "
            + "o.delivery_address, o.notes, o.rejection_reason, o.stock_returned, "
            + "o.created_at, o.status_updated_at, ct.name as contact_name, ct.phone_number as contact_phone "
            + "from moda_infantil_orders o join contacts ct on ct.id = o.contact_id ";

    /** Lista pedidos do tenant (filtro opcional por status), paginado, mais recentes primeiro. */
    public List<ModaInfantilOrder> listByCompany(UUID companyId, String status, int limit, int offset) {
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

        List<ModaInfantilOrder> orders = jdbcTemplate.query(sql.toString(),
            (rs, rn) -> mapOrder(rs, List.of()), args.toArray());
        List<ModaInfantilOrder> withItems = new ArrayList<>(orders.size());
        for (ModaInfantilOrder o : orders) {
            withItems.add(withItems(o));
        }
        return withItems;
    }

    public long countByCompany(UUID companyId, String status) {
        StringBuilder sql = new StringBuilder("select count(*) from moda_infantil_orders where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) {
            sql.append(" and status = ?");
            args.add(status);
        }
        Long n = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0L : n;
    }

    public Optional<ModaInfantilOrder> findById(UUID companyId, UUID id) {
        Optional<ModaInfantilOrder> base = jdbcTemplate.query(ORDER_SELECT + "where o.company_id = ? and o.id = ?",
                (rs, rn) -> mapOrder(rs, List.of()), companyId, id)
            .stream().findFirst();
        return base.map(this::withItems);
    }

    private ModaInfantilOrder withItems(ModaInfantilOrder o) {
        List<ModaInfantilOrderItem> items = jdbcTemplate.query(
            "select id, variant_id, product_name_snapshot, size_snapshot, color_snapshot, qtd, unit_price_cents "
                + "from moda_infantil_order_items where order_id = ? order by id", ITEM_MAPPER, o.id());
        return new ModaInfantilOrder(o.id(), o.conversationId(), o.status(), o.fulfillment(),
            o.subtotalCents(), o.discountCents(), o.deliveryFeeCents(), o.totalCents(),
            o.couponCode(), o.deliveryAddress(), o.notes(),
            o.rejectionReason(), o.stockReturned(), o.createdAt(), o.statusUpdatedAt(),
            o.contactName(), o.contactPhone(), items);
    }

    /**
     * Cria o pedido + itens numa transação, DECREMENTANDO O ESTOQUE de cada variante. Para cada
     * linha: resolve a variante (available, do tenant) e o produto; computa {@code unit_price =
     * variant.price ?? product.base_price}; faz o snapshot de product_name/size/color; e DECREMENTA O
     * ESTOQUE via {@link ModaInfantilVariantRepository#decrementStock} — se o UPDATE condicional afeta
     * 0 linhas (estoque insuficiente), lança {@link OutOfStockException} e o @Transactional faz
     * ROLLBACK (NENHUM pedido parcial). Linhas cuja variante não existe/não é do tenant/está
     * indisponível são IGNORADAS (defesa — o handler já validou). subtotal = Σ unit_price × qtd;
     * total = subtotal + (entrega ? delivery_fee : 0). O total da IA é DESCARTADO. Lança
     * IllegalArgumentException se, após filtrar, não sobrar linha válida.
     */
    @Transactional
    public ModaInfantilOrder createOrder(UUID companyId, UUID conversationId, UUID contactId,
                                         String fulfillment, String deliveryAddress, List<OrderLineInput> lines,
                                         String couponCode, int deliveryFeeCents, String notes) {
        record Snap(UUID variantId, String productName, String size, String color, int unitPrice, int qtd) {}

        // Resolve as variantes do tenant uma vez (uma query) e indexa por id.
        List<UUID> variantIds = new ArrayList<>();
        for (OrderLineInput line : lines) {
            variantIds.add(line.variantId());
        }
        Map<UUID, ModaInfantilVariant> byId = new HashMap<>();
        for (ModaInfantilVariant v : variantRepository.findByIdsForOrder(companyId, variantIds)) {
            byId.put(v.id(), v);
        }

        List<Snap> snaps = new ArrayList<>();
        int subtotal = 0;
        for (OrderLineInput line : lines) {
            ModaInfantilVariant variant = byId.get(line.variantId());
            if (variant == null || !variant.available()) {
                continue;   // variante inexistente/de outro tenant/indisponível: ignora a linha (defesa).
            }
            // Resolve o produto (nome + base_price) — também valida disponibilidade do produto.
            record Prod(String name, int basePrice, boolean available) {}
            List<Prod> found = jdbcTemplate.query(
                "select name, base_price_cents, available from moda_infantil_products where company_id = ? and id = ?",
                (rs, rn) -> new Prod(rs.getString("name"), rs.getInt("base_price_cents"), rs.getBoolean("available")),
                companyId, variant.productId());
            if (found.isEmpty() || !found.get(0).available()) {
                continue;   // produto inexistente/indisponível: ignora.
            }
            Prod prod = found.get(0);
            int unitPrice = variant.priceCents() != null ? variant.priceCents() : prod.basePrice();

            // DECREMENTO TRANSACIONAL — UPDATE condicional stock_qty >= qtd. 0 linhas → esgotado →
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
            java.util.Optional<com.meada.profiles.modainfantil.coupons.ModaInfantilCoupon> maybe =
                couponRepository.findByCode(companyId, couponCode);
            if (maybe.isPresent()) {
                com.meada.profiles.modainfantil.coupons.ModaInfantilCoupon c = maybe.get();
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
            "insert into moda_infantil_orders (company_id, conversation_id, contact_id, fulfillment, "
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
                "insert into moda_infantil_order_items (order_id, variant_id, qtd, unit_price_cents, "
                    + "product_name_snapshot, size_snapshot, color_snapshot) "
                    + "values (?, ?, ?, ?, ?, ?, ?)",
                orderId, s.variantId(), s.qtd(), s.unitPrice(), s.productName(), s.size(), s.color());
        }
        return findById(companyId, orderId).orElseThrow();
    }

    /**
     * ⭐ Persiste a transição de status + status_updated_at, E — quando o novo status é
     * {@code recusado}/{@code cancelado} — DEVOLVE o estoque das variantes do pedido (restock), tudo
     * na MESMA transação. Service já validou a transição. Quando {@code rejectionReason != null}
     * (recusa), grava também o motivo.
     *
     * <p>IDEMPOTÊNCIA: o restock só acontece se {@code stock_returned} ainda for false (lido junto, no
     * mesmo método); ao devolver, marca {@code stock_returned = true}. Assim, um duplo cancelamento ou
     * um cancelar-depois-de-recusar não devolve o estoque duas vezes. Para os status que não devolvem
     * estoque, o {@code stock_returned} fica intacto.
     */
    @Transactional
    public void updateStatus(UUID companyId, UUID id, String newStatus, String rejectionReason) {
        boolean restocks = ModaInfantilOrderStatus.fromId(newStatus)
            .map(ModaInfantilOrderStatus::restocksOnEnter).orElse(false);

        // ⭐ RESTOCK ON CANCEL (idempotente): só devolve se ainda não devolveu (stock_returned=false).
        if (restocks) {
            Boolean alreadyReturned = jdbcTemplate.query(
                    "select stock_returned from moda_infantil_orders where company_id = ? and id = ?",
                    (rs, rn) -> rs.getBoolean("stock_returned"), companyId, id)
                .stream().findFirst().orElse(Boolean.TRUE);   // ausente → trata como já devolvido (no-op).
            if (Boolean.FALSE.equals(alreadyReturned)) {
                List<ModaInfantilOrderItem> items = jdbcTemplate.query(
                    "select id, variant_id, product_name_snapshot, size_snapshot, color_snapshot, qtd, unit_price_cents "
                        + "from moda_infantil_order_items where order_id = ?", ITEM_MAPPER, id);
                for (ModaInfantilOrderItem item : items) {
                    variantRepository.restockStock(companyId, item.variantId(), item.qtd());
                }
            }
        }

        if (rejectionReason != null) {
            jdbcTemplate.update(
                "update moda_infantil_orders set status = ?, rejection_reason = ?, status_updated_at = now()"
                    + (restocks ? ", stock_returned = true" : "")
                    + " where company_id = ? and id = ?",
                newStatus, rejectionReason, companyId, id);
        } else {
            jdbcTemplate.update(
                "update moda_infantil_orders set status = ?, status_updated_at = now()"
                    + (restocks ? ", stock_returned = true" : "")
                    + " where company_id = ? and id = ?",
                newStatus, companyId, id);
        }
    }
}
