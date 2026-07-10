package com.meada.profiles.las.orders;

import com.meada.profiles.las.catalog.LasVariant;
import com.meada.profiles.las.catalog.LasVariantRepository;
import com.meada.profiles.las.coupons.LasCoupon;
import com.meada.profiles.las.coupons.LasCouponRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Acesso a {@code las_orders} + {@code las_order_items} (camada 8.23). Clone do
 * {@link com.meada.profiles.lingerie.orders.LingerieOrderRepository} (gate de aceite com
 * rejection_reason; recálculo de total no backend, total da IA descartado; decremento transacional de
 * estoque), com a ⭐ ESCAPADA desta SM: a validação {@code same_lot_guaranteed} (lote de tingimento
 * único por cor — senão {@link MixedDyeLotsException} aborta o pedido). Opera via service_role; o
 * escopo por company_id no WHERE é a defesa.
 */
@Repository
public class LasOrderRepository {

    /** Alguma variante pedida está esgotada (qtd > estoque) — o pedido inteiro é ABORTADO (rollback). */
    public static class OutOfStockException extends RuntimeException {}

    /**
     * ⭐ ESCAPADA: com {@code same_lot_guaranteed=true}, alguma COR do pedido tem itens de DOIS OU MAIS
     * dye_lots (lotes de tingimento) diferentes — o que daria variação de tom. O pedido inteiro é
     * ABORTADO (rollback). As cores ofensoras ficam em {@link #colors()} para a resposta 422.
     */
    public static class MixedDyeLotsException extends RuntimeException {
        private final Set<String> colors;

        public MixedDyeLotsException(Set<String> colors) {
            this.colors = colors;
        }

        public Set<String> colors() {
            return colors;
        }
    }

    private final JdbcTemplate jdbcTemplate;
    private final LasVariantRepository variantRepository;
    private final LasCouponRepository couponRepository;

    public LasOrderRepository(JdbcTemplate jdbcTemplate,
                              LasVariantRepository variantRepository,
                              LasCouponRepository couponRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.variantRepository = variantRepository;
        this.couponRepository = couponRepository;
    }

    /** Mapeia a row de order_item. */
    private final RowMapper<LasOrderItem> ITEM_MAPPER = (rs, rn) -> new LasOrderItem(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("variant_id"),
        rs.getString("product_name_snapshot"),
        rs.getString("color_snapshot"),
        rs.getString("dye_lot_snapshot"),
        rs.getInt("qtd"),
        rs.getInt("unit_price_cents"));

    /** Mapeia a row de order (sem os itens — carregados à parte). */
    private LasOrder mapOrder(java.sql.ResultSet rs, List<LasOrderItem> items) throws java.sql.SQLException {
        return new LasOrder(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("conversation_id"),
            rs.getString("status"),
            rs.getString("fulfillment"),
            rs.getBoolean("same_lot_guaranteed"),
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
        "select o.id, o.conversation_id, o.status, o.fulfillment, o.same_lot_guaranteed, o.subtotal_cents, "
            + "o.discount_cents, o.delivery_fee_cents, o.total_cents, o.coupon_code_snapshot, "
            + "o.delivery_address, o.notes, o.rejection_reason, "
            + "o.created_at, o.status_updated_at, ct.name as contact_name, ct.phone_number as contact_phone "
            + "from las_orders o join contacts ct on ct.id = o.contact_id ";

    /** Lista pedidos do tenant (filtro opcional por status), paginado, mais recentes primeiro. */
    public List<LasOrder> listByCompany(UUID companyId, String status, int limit, int offset) {
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

        List<LasOrder> orders = jdbcTemplate.query(sql.toString(),
            (rs, rn) -> mapOrder(rs, List.of()), args.toArray());
        List<LasOrder> withItems = new ArrayList<>(orders.size());
        for (LasOrder o : orders) {
            withItems.add(withItems(o));
        }
        return withItems;
    }

    public long countByCompany(UUID companyId, String status) {
        StringBuilder sql = new StringBuilder("select count(*) from las_orders where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) {
            sql.append(" and status = ?");
            args.add(status);
        }
        Long n = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0L : n;
    }

    public Optional<LasOrder> findById(UUID companyId, UUID id) {
        Optional<LasOrder> base = jdbcTemplate.query(ORDER_SELECT + "where o.company_id = ? and o.id = ?",
                (rs, rn) -> mapOrder(rs, List.of()), companyId, id)
            .stream().findFirst();
        return base.map(this::withItems);
    }

    private LasOrder withItems(LasOrder o) {
        List<LasOrderItem> items = jdbcTemplate.query(
            "select id, variant_id, product_name_snapshot, color_snapshot, dye_lot_snapshot, qtd, unit_price_cents "
                + "from las_order_items where order_id = ? order by id", ITEM_MAPPER, o.id());
        return new LasOrder(o.id(), o.conversationId(), o.status(), o.fulfillment(), o.sameLotGuaranteed(),
            o.subtotalCents(), o.discountCents(), o.deliveryFeeCents(), o.totalCents(),
            o.couponCode(), o.deliveryAddress(), o.notes(),
            o.rejectionReason(), o.createdAt(), o.statusUpdatedAt(), o.contactName(), o.contactPhone(), items);
    }

    /**
     * ⭐ Cria o pedido + itens numa transação, DECREMENTANDO O ESTOQUE de cada variante e — quando
     * {@code sameLotGuaranteed} — VALIDANDO o lote único por cor. Para cada linha: resolve a variante
     * (available, do tenant) e o produto; computa {@code unit_price = variant.price ??
     * product.base_price}; faz o snapshot de product_name/color/dye_lot; e DECREMENTA O ESTOQUE via
     * {@link LasVariantRepository#decrementStock} — se o UPDATE condicional afeta 0 linhas (estoque
     * insuficiente), lança {@link OutOfStockException} e o @Transactional faz ROLLBACK (NENHUM pedido
     * parcial). Linhas cuja variante não existe/não é do tenant/está indisponível são IGNORADAS
     * (defesa — o handler já validou).
     *
     * <p>Quando {@code sameLotGuaranteed=true}, após montar os snapshots, agrupa por {@code color_snapshot}
     * e exige um ÚNICO {@code dye_lot_snapshot} por cor; se alguma cor abranger 2+ lotes, lança
     * {@link MixedDyeLotsException} (com as cores ofensoras) AINDA dentro da transação → ROLLBACK
     * (estoque devolvido). subtotal = Σ unit_price × qtd; total = subtotal + (entrega ? delivery_fee : 0).
     * O total da IA é DESCARTADO. Lança IllegalArgumentException se, após filtrar, não sobrar linha válida.
     */
    @Transactional
    public LasOrder createOrder(UUID companyId, UUID conversationId, UUID contactId,
                                String fulfillment, boolean sameLotGuaranteed, String deliveryAddress,
                                List<OrderLineInput> lines, String couponCode,
                                int deliveryFeeCents, String notes) {
        record Snap(UUID variantId, String productName, String color, String dyeLot, int unitPrice, int qtd) {}

        // Resolve as variantes do tenant uma vez (uma query) e indexa por id.
        List<UUID> variantIds = new ArrayList<>();
        for (OrderLineInput line : lines) {
            variantIds.add(line.variantId());
        }
        Map<UUID, LasVariant> byId = new HashMap<>();
        for (LasVariant v : variantRepository.findByIdsForOrder(companyId, variantIds)) {
            byId.put(v.id(), v);
        }

        List<Snap> snaps = new ArrayList<>();
        int subtotal = 0;
        for (OrderLineInput line : lines) {
            LasVariant variant = byId.get(line.variantId());
            if (variant == null || !variant.available()) {
                continue;   // variante inexistente/de outro tenant/indisponível: ignora a linha (defesa).
            }
            // Resolve o produto (nome + base_price) — também valida disponibilidade do produto.
            record Prod(String name, int basePrice, boolean available) {}
            List<Prod> found = jdbcTemplate.query(
                "select name, base_price_cents, available from las_products where company_id = ? and id = ?",
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

            snaps.add(new Snap(variant.id(), prod.name(), variant.color(), variant.dyeLot(), unitPrice, line.qtd()));
            subtotal += unitPrice * line.qtd();
        }
        if (snaps.isEmpty()) {
            throw new IllegalArgumentException("nenhum item válido no pedido");
        }

        // ⭐ ESCAPADA — regra do MESMO LOTE: agrupa por cor (snapshot) e exige um único dye_lot por cor.
        if (sameLotGuaranteed) {
            Map<String, Set<String>> lotsByColor = new LinkedHashMap<>();
            for (Snap s : snaps) {
                lotsByColor.computeIfAbsent(s.color(), k -> new LinkedHashSet<>()).add(s.dyeLot());
            }
            Set<String> offending = new LinkedHashSet<>();
            for (Map.Entry<String, Set<String>> e : lotsByColor.entrySet()) {
                if (e.getValue().size() > 1) {
                    offending.add(e.getKey());
                }
            }
            if (!offending.isEmpty()) {
                // Ainda dentro da transação → o rollback devolve o estoque decrementado.
                throw new MixedDyeLotsException(offending);
            }
        }

        // Onda 1 (backlog #5): cupom na MESMA transação — validado aqui; inválido NÃO aborta.
        UUID couponId = null;
        String couponSnapshot = null;
        int discount = 0;
        if (couponCode != null && !couponCode.isBlank()) {
            java.util.Optional<LasCoupon> maybe = couponRepository.findByCode(companyId, couponCode);
            if (maybe.isPresent()) {
                LasCoupon c = maybe.get();
                boolean valid = c.active()
                    && (c.validUntil() == null || !c.validUntil().isBefore(java.time.LocalDate.now()))
                    && (c.maxUses() == null || c.uses() < c.maxUses())
                    && subtotal >= c.minOrderCents();
                if (valid) {
                    int raw = "percent".equals(c.kind()) ? subtotal * c.value() / 100 : c.value();
                    discount = Math.min(subtotal, raw);
                    couponId = c.id();
                    couponSnapshot = c.code();
                    couponRepository.incrementUses(companyId, c.id());
                }
            }
        }

        boolean isEntrega = "entrega".equals(fulfillment);
        int fee = isEntrega ? deliveryFeeCents : 0;
        int total = subtotal - discount + fee;
        String address = isEntrega ? deliveryAddress : null;   // retirada não tem endereço.

        UUID orderId = jdbcTemplate.queryForObject(
            "insert into las_orders (company_id, conversation_id, contact_id, fulfillment, same_lot_guaranteed, "
                + "subtotal_cents, discount_cents, delivery_fee_cents, total_cents, coupon_id, "
                + "coupon_code_snapshot, delivery_address, notes) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, conversationId, contactId, fulfillment, sameLotGuaranteed,
            subtotal, discount, fee, total, couponId, couponSnapshot, address, notes);

        for (Snap s : snaps) {
            jdbcTemplate.update(
                "insert into las_order_items (order_id, variant_id, qtd, unit_price_cents, "
                    + "product_name_snapshot, color_snapshot, dye_lot_snapshot) "
                    + "values (?, ?, ?, ?, ?, ?, ?)",
                orderId, s.variantId(), s.qtd(), s.unitPrice(), s.productName(), s.color(), s.dyeLot());
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
                "update las_orders set status = ?, rejection_reason = ?, status_updated_at = now() "
                    + "where company_id = ? and id = ?",
                newStatus, rejectionReason, companyId, id);
        } else {
            jdbcTemplate.update(
                "update las_orders set status = ?, status_updated_at = now() "
                    + "where company_id = ? and id = ?",
                newStatus, companyId, id);
        }
    }
}
