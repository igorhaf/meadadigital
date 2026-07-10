package com.meada.profiles.floricultura.orders;

import com.meada.profiles.floricultura.catalog.FloriculturaCatalogOption;
import com.meada.profiles.floricultura.catalog.FloriculturaCatalogOptionRepository;
import com.meada.profiles.floricultura.coupons.FloriculturaCoupon;
import com.meada.profiles.floricultura.coupons.FloriculturaCouponRepository;
import com.meada.profiles.floricultura.loyalty.FloriculturaLoyaltyConfig;
import com.meada.profiles.floricultura.loyalty.FloriculturaLoyaltyConfigRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code floricultura_orders} + {@code floricultura_order_items} + {@code floricultura_order_item_options}
 * (camada 8.4). Clone de {@link com.meada.profiles.sushi.orders.SushiOrderRepository} com
 * as DUAS escapadas: (1) {@code rejection_reason} na transição de status; (2) opções por item, com
 * recálculo do unit_price = base + Σ deltas (descarta o total da IA). Opera via service_role; o
 * escopo por company_id no WHERE é a defesa.
 */
@Repository
public class FloriculturaOrderRepository {

    /** Alguma opção pedida é inválida/indisponível/de outro item — o pedido NÃO é criado. */
    public static class InvalidOptionException extends RuntimeException {}

    private final JdbcTemplate jdbcTemplate;
    private final FloriculturaCatalogOptionRepository optionRepository;
    private final FloriculturaCouponRepository couponRepository;
    private final FloriculturaLoyaltyConfigRepository loyaltyRepository;

    public FloriculturaOrderRepository(JdbcTemplate jdbcTemplate,
                                 FloriculturaCatalogOptionRepository optionRepository,
                                      FloriculturaCouponRepository couponRepository,
                                      FloriculturaLoyaltyConfigRepository loyaltyRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.optionRepository = optionRepository;
        this.couponRepository = couponRepository;
        this.loyaltyRepository = loyaltyRepository;
    }

    private final RowMapper<FloriculturaOrderItemOption> ITEM_OPTION_MAPPER = (rs, rn) -> new FloriculturaOrderItemOption(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("catalog_option_id"),
        rs.getString("group_label_snapshot"),
        rs.getString("option_label_snapshot"),
        rs.getInt("price_delta_cents"));

    /** Mapeia a row de order_item SEM as opções (carregadas à parte). */
    private final RowMapper<FloriculturaOrderItem> ITEM_MAPPER = (rs, rn) -> new FloriculturaOrderItem(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("catalog_item_id"),
        rs.getString("item_name_snapshot"),
        rs.getInt("qtd"),
        rs.getInt("unit_price_cents"),
        List.of());

    /** Mapeia a row de order (sem os itens — carregados à parte). */
    private FloriculturaOrder mapOrder(java.sql.ResultSet rs, List<FloriculturaOrderItem> items) throws java.sql.SQLException {
        java.sql.Date dd = rs.getDate("delivery_date");
        return new FloriculturaOrder(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("conversation_id"),
            rs.getString("status"),
            rs.getInt("subtotal_cents"),
            rs.getInt("discount_cents"),
            rs.getInt("delivery_fee_cents"),
            rs.getInt("total_cents"),
            rs.getString("coupon_code_snapshot"),
            rs.getBoolean("loyalty_applied"),
            rs.getBoolean("anonymous"),
            rs.getString("delivery_address"),
            rs.getString("notes"),
            rs.getString("rejection_reason"),
            dd == null ? null : dd.toLocalDate(),
            rs.getString("delivery_period"),
            rs.getString("recipient_name"),
            rs.getString("card_message"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("status_updated_at").toInstant(),
            rs.getString("contact_name"),
            rs.getString("contact_phone"),
            items);
    }

    private static final String ORDER_SELECT =
        "select o.id, o.conversation_id, o.status, o.subtotal_cents, o.discount_cents, "
            + "o.delivery_fee_cents, o.total_cents, o.coupon_code_snapshot, o.loyalty_applied, "
            + "o.anonymous, o.delivery_address, o.notes, o.rejection_reason, "
            + "o.delivery_date, o.delivery_period, o.recipient_name, o.card_message, o.created_at, "
            + "o.status_updated_at, ct.name as contact_name, ct.phone_number as contact_phone "
            + "from floricultura_orders o join contacts ct on ct.id = o.contact_id ";

    /** Lista pedidos do tenant (filtro opcional por status), paginado, mais recentes primeiro. */
    public List<FloriculturaOrder> listByCompany(UUID companyId, String status, int limit, int offset) {
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

        List<FloriculturaOrder> orders = jdbcTemplate.query(sql.toString(),
            (rs, rn) -> mapOrder(rs, List.of()), args.toArray());
        // Hidrata itens (e suas opções) de cada pedido (lista pequena — N+1 aceitável no Kanban).
        List<FloriculturaOrder> withItems = new ArrayList<>(orders.size());
        for (FloriculturaOrder o : orders) {
            withItems.add(withItems(o));
        }
        return withItems;
    }

    public long countByCompany(UUID companyId, String status) {
        StringBuilder sql = new StringBuilder("select count(*) from floricultura_orders where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) {
            sql.append(" and status = ?");
            args.add(status);
        }
        Long n = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0L : n;
    }

    public Optional<FloriculturaOrder> findById(UUID companyId, UUID id) {
        Optional<FloriculturaOrder> base = jdbcTemplate.query(ORDER_SELECT + "where o.company_id = ? and o.id = ?",
                (rs, rn) -> mapOrder(rs, List.of()), companyId, id)
            .stream().findFirst();
        return base.map(this::withItems);
    }

    private FloriculturaOrder withItems(FloriculturaOrder o) {
        List<FloriculturaOrderItem> bare = jdbcTemplate.query(
            "select id, catalog_item_id, item_name_snapshot, qtd, unit_price_cents "
                + "from floricultura_order_items where order_id = ? order by id", ITEM_MAPPER, o.id());
        List<FloriculturaOrderItem> withOpts = new ArrayList<>(bare.size());
        for (FloriculturaOrderItem it : bare) {
            List<FloriculturaOrderItemOption> options = jdbcTemplate.query(
                "select id, catalog_option_id, group_label_snapshot, option_label_snapshot, price_delta_cents "
                    + "from floricultura_order_item_options where order_item_id = ? order by id",
                ITEM_OPTION_MAPPER, it.id());
            withOpts.add(new FloriculturaOrderItem(it.id(), it.catalogItemId(), it.itemName(), it.qtd(),
                it.unitPriceCents(), options));
        }
        return new FloriculturaOrder(o.id(), o.conversationId(), o.status(), o.subtotalCents(),
            o.discountCents(), o.deliveryFeeCents(), o.totalCents(), o.couponCode(),
            o.loyaltyApplied(), o.anonymous(),
            o.deliveryAddress(), o.notes(), o.rejectionReason(),
            o.deliveryDate(), o.deliveryPeriod(), o.recipientName(), o.cardMessage(),
            o.createdAt(), o.statusUpdatedAt(), o.contactName(), o.contactPhone(), withOpts);
    }

    /**
     * Cria o pedido + itens + opções numa transação. Os preços/nomes são lidos do cardápio AGORA
     * (snapshot); para cada linha, {@code unit_price = base + Σ deltas} das opções escolhidas. O
     * subtotal é a soma de unit_price × qtd; o total = subtotal + delivery_fee. Linhas cujo
     * catalog_item não existe/não é do tenant são IGNORADAS (o handler já validou). Se alguma opção
     * pedida é inválida/indisponível/de outro item, lança {@link InvalidOptionException} (pedido NÃO
     * criado com opção fantasma). Lança IllegalArgumentException se, após filtrar, não sobrar linha.
     */
    @Transactional
    public FloriculturaOrder createOrder(UUID companyId, UUID conversationId, UUID contactId,
                                   String deliveryAddress, List<OrderLineInput> lines,
                                   int deliveryFeeCents, String notes,
                                   java.time.LocalDate deliveryDate, String deliveryPeriod,
                                   String recipientName, String cardMessage,
                                   String couponCode, boolean anonymous) {
        // Snapshot de preço+nome+opções por linha (lê do cardápio do tenant).
        record OptSnap(UUID catalogOptionId, String groupLabel, String optionLabel, int delta) {}
        record Snap(UUID catalogItemId, String name, int unitPrice, int qtd, List<OptSnap> options) {}

        List<Snap> snaps = new ArrayList<>();
        int subtotal = 0;
        for (OrderLineInput line : lines) {
            record Base(String name, int price) {}
            List<Base> found = jdbcTemplate.query(
                "select name, price_cents from floricultura_catalog_items where company_id = ? and id = ?",
                (rs, rn) -> new Base(rs.getString("name"), rs.getInt("price_cents")),
                companyId, line.catalogItemId());
            if (found.isEmpty()) {
                continue;   // item inexistente/de outro tenant: ignora a linha (defesa).
            }
            Base base = found.get(0);

            // Resolve as opções escolhidas (ESCAPADA 2). Tamanho divergente → opção fantasma.
            List<UUID> optionIds = line.optionIds() == null ? List.of() : line.optionIds();
            List<OptSnap> optSnaps = new ArrayList<>();
            int deltaSum = 0;
            if (!optionIds.isEmpty()) {
                List<FloriculturaCatalogOption> resolved =
                    optionRepository.findByIdsForItem(companyId, line.catalogItemId(), optionIds);
                if (resolved.size() != optionIds.size()) {
                    throw new InvalidOptionException();
                }
                for (FloriculturaCatalogOption opt : resolved) {
                    optSnaps.add(new OptSnap(opt.id(), opt.groupLabel(), opt.optionLabel(), opt.priceDeltaCents()));
                    deltaSum += opt.priceDeltaCents();
                }
            }

            int unitPrice = base.price() + deltaSum;
            snaps.add(new Snap(line.catalogItemId(), base.name(), unitPrice, line.qtd(), optSnaps));
            subtotal += unitPrice * line.qtd();
        }
        if (snaps.isEmpty()) {
            throw new IllegalArgumentException("nenhum item válido no pedido");
        }

        // Onda 1 (backlog #7/#8): cupom + fidelidade na MESMA transação (clone adega).
        UUID couponId = null;
        String couponSnapshot = null;
        int couponDiscount = 0;
        if (couponCode != null && !couponCode.isBlank()) {
            java.util.Optional<FloriculturaCoupon> maybe = couponRepository.findByCode(companyId, couponCode);
            if (maybe.isPresent()) {
                FloriculturaCoupon c = maybe.get();
                boolean valid = c.active()
                    && (c.validUntil() == null || !c.validUntil().isBefore(java.time.LocalDate.now()))
                    && (c.maxUses() == null || c.uses() < c.maxUses())
                    && subtotal >= c.minOrderCents();
                if (valid) {
                    couponDiscount = "percent".equals(c.kind()) ? subtotal * c.value() / 100 : c.value();
                    couponId = c.id();
                    couponSnapshot = c.code();
                    couponRepository.incrementUses(companyId, c.id());
                }
            }
        }

        boolean loyaltyApplied = false;
        int loyaltyDiscount = 0;
        FloriculturaLoyaltyConfig loyalty = loyaltyRepository.findByCompany(companyId);
        if (loyalty.enabled()) {
            Long delivered = jdbcTemplate.queryForObject(
                "select count(*) from floricultura_orders where company_id = ? and contact_id = ? "
                    + "and status = 'entregue'", Long.class, companyId, contactId);
            long deliveredCount = delivered == null ? 0 : delivered;
            if (deliveredCount > 0 && deliveredCount % loyalty.thresholdOrders() == 0) {
                loyaltyApplied = true;
                loyaltyDiscount = "percent".equals(loyalty.rewardKind())
                    ? subtotal * loyalty.rewardValue() / 100
                    : loyalty.rewardValue();
            }
        }

        int discount = Math.min(subtotal, couponDiscount + loyaltyDiscount);
        int total = subtotal - discount + deliveryFeeCents;

        // status default 'aguardando' (não passamos status — gate de aceite). + ESCAPADA: entrega
        // agendada (data/período), destinatário e cartão; anonymous = presente surpresa (#13).
        UUID orderId = jdbcTemplate.queryForObject(
            "insert into floricultura_orders (company_id, conversation_id, contact_id, subtotal_cents, "
                + "discount_cents, delivery_fee_cents, total_cents, coupon_id, coupon_code_snapshot, "
                + "loyalty_applied, anonymous, delivery_address, notes, "
                + "delivery_date, delivery_period, recipient_name, card_message) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, conversationId, contactId, subtotal, discount, deliveryFeeCents,
            total, couponId, couponSnapshot, loyaltyApplied, anonymous, deliveryAddress, notes,
            java.sql.Date.valueOf(deliveryDate), deliveryPeriod, recipientName, cardMessage);

        for (Snap s : snaps) {
            UUID orderItemId = jdbcTemplate.queryForObject(
                "insert into floricultura_order_items (order_id, catalog_item_id, qtd, unit_price_cents, "
                    + "item_name_snapshot) values (?, ?, ?, ?, ?) returning id",
                UUID.class, orderId, s.catalogItemId(), s.qtd(), s.unitPrice(), s.name());
            for (OptSnap opt : s.options()) {
                jdbcTemplate.update(
                    "insert into floricultura_order_item_options (order_item_id, catalog_option_id, "
                        + "group_label_snapshot, option_label_snapshot, price_delta_cents) "
                        + "values (?, ?, ?, ?, ?)",
                    orderItemId, opt.catalogOptionId(), opt.groupLabel(), opt.optionLabel(), opt.delta());
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
                "update floricultura_orders set status = ?, rejection_reason = ?, status_updated_at = now() "
                    + "where company_id = ? and id = ?",
                newStatus, rejectionReason, companyId, id);
        } else {
            jdbcTemplate.update(
                "update floricultura_orders set status = ?, status_updated_at = now() "
                    + "where company_id = ? and id = ?",
                newStatus, companyId, id);
        }
    }
}
