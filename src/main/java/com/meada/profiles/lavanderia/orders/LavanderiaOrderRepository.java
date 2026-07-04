package com.meada.profiles.lavanderia.orders;

import com.meada.profiles.lavanderia.coupons.LavanderiaCoupon;
import com.meada.profiles.lavanderia.coupons.LavanderiaCouponRepository;
import com.meada.profiles.lavanderia.loyalty.LavanderiaLoyaltyConfig;
import com.meada.profiles.lavanderia.loyalty.LavanderiaLoyaltyConfigRepository;
import com.meada.profiles.lavanderia.services.LavanderiaServiceOption;
import com.meada.profiles.lavanderia.services.LavanderiaServiceOptionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code lavanderia_orders} + {@code lavanderia_order_items} + {@code
 * lavanderia_order_item_options} (camada 8.10). Clone do FloriculturaOrderRepository com a ESCAPADA:
 * DUAS DATAS acopladas — {@code delivery_date} MATERIALIZADA = collect + MAX(turnaround_snapshot dos
 * itens). MAX (não soma): processamento paralelo, vale o serviço mais lento. Recálculo do
 * unit_price = base + Σ deltas (descarta o total da IA). Opera via service_role.
 */
@Repository
public class LavanderiaOrderRepository {

    /** Alguma opção pedida é inválida/indisponível/de outro serviço — o pedido NÃO é criado. */
    public static class InvalidOptionException extends RuntimeException {}

    /** Subtotal abaixo do pedido mínimo (→ 422 below_minimum). */
    public static class BelowMinimumException extends RuntimeException {
        private final int minOrderCents;
        public BelowMinimumException(int minOrderCents) { this.minOrderCents = minOrderCents; }
        public int minOrderCents() { return minOrderCents; }
    }

    /**
     * A delivery_date pedida pela IA é anterior à primeira possível (collect + MAX(turnaround)) —
     * → 422 turnaround_violation, devolvendo a primeira data possível.
     */
    public static class TurnaroundViolationException extends RuntimeException {
        private final LocalDate firstPossibleDeliveryDate;
        public TurnaroundViolationException(LocalDate firstPossibleDeliveryDate) {
            this.firstPossibleDeliveryDate = firstPossibleDeliveryDate;
        }
        public LocalDate firstPossibleDeliveryDate() { return firstPossibleDeliveryDate; }
    }

    private final JdbcTemplate jdbcTemplate;
    private final LavanderiaServiceOptionRepository optionRepository;
    private final LavanderiaCouponRepository couponRepository;
    private final LavanderiaLoyaltyConfigRepository loyaltyRepository;

    public LavanderiaOrderRepository(JdbcTemplate jdbcTemplate,
                                     LavanderiaServiceOptionRepository optionRepository,
                                     LavanderiaCouponRepository couponRepository,
                                     LavanderiaLoyaltyConfigRepository loyaltyRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.optionRepository = optionRepository;
        this.couponRepository = couponRepository;
        this.loyaltyRepository = loyaltyRepository;
    }

    private final RowMapper<LavanderiaOrderItemOption> ITEM_OPTION_MAPPER = (rs, rn) -> new LavanderiaOrderItemOption(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("service_option_id"),
        rs.getString("group_label_snapshot"),
        rs.getString("option_label_snapshot"),
        rs.getInt("price_delta_cents"));

    private final RowMapper<LavanderiaOrderItem> ITEM_MAPPER = (rs, rn) -> new LavanderiaOrderItem(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("service_id"),
        rs.getString("service_name_snapshot"),
        rs.getInt("qty"),
        rs.getInt("unit_price_cents"),
        rs.getInt("turnaround_snapshot"),
        List.of());

    private LavanderiaOrder mapOrder(java.sql.ResultSet rs, List<LavanderiaOrderItem> items) throws java.sql.SQLException {
        java.sql.Date cd = rs.getDate("collect_date");
        java.sql.Date dd = rs.getDate("delivery_date");
        return new LavanderiaOrder(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("conversation_id"),
            rs.getString("status"),
            rs.getInt("subtotal_cents"),
            rs.getInt("discount_cents"),
            rs.getInt("delivery_fee_cents"),
            rs.getInt("total_cents"),
            rs.getString("coupon_code_snapshot"),
            rs.getBoolean("loyalty_applied"),
            rs.getBoolean("express"),
            rs.getInt("express_surcharge_cents"),
            rs.getString("delivery_address"),
            rs.getString("notes"),
            rs.getString("rejection_reason"),
            cd == null ? null : cd.toLocalDate(),
            dd == null ? null : dd.toLocalDate(),
            rs.getString("period"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("status_updated_at").toInstant(),
            rs.getString("contact_name"),
            rs.getString("contact_phone"),
            items);
    }

    private static final String ORDER_SELECT =
        "select o.id, o.conversation_id, o.status, o.subtotal_cents, o.discount_cents, "
            + "o.delivery_fee_cents, o.total_cents, o.coupon_code_snapshot, o.loyalty_applied, "
            + "o.express, o.express_surcharge_cents, o.delivery_address, o.notes, o.rejection_reason, "
            + "o.collect_date, o.delivery_date, o.period, o.created_at, o.status_updated_at, "
            + "ct.name as contact_name, ct.phone_number as contact_phone "
            + "from lavanderia_orders o join contacts ct on ct.id = o.contact_id ";

    public List<LavanderiaOrder> listByCompany(UUID companyId, String status, int limit, int offset) {
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

        List<LavanderiaOrder> orders = jdbcTemplate.query(sql.toString(),
            (rs, rn) -> mapOrder(rs, List.of()), args.toArray());
        List<LavanderiaOrder> withItems = new ArrayList<>(orders.size());
        for (LavanderiaOrder o : orders) {
            withItems.add(withItems(o));
        }
        return withItems;
    }

    public long countByCompany(UUID companyId, String status) {
        StringBuilder sql = new StringBuilder("select count(*) from lavanderia_orders where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) {
            sql.append(" and status = ?");
            args.add(status);
        }
        Long n = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0L : n;
    }

    public Optional<LavanderiaOrder> findById(UUID companyId, UUID id) {
        Optional<LavanderiaOrder> base = jdbcTemplate.query(ORDER_SELECT + "where o.company_id = ? and o.id = ?",
                (rs, rn) -> mapOrder(rs, List.of()), companyId, id)
            .stream().findFirst();
        return base.map(this::withItems);
    }

    private LavanderiaOrder withItems(LavanderiaOrder o) {
        List<LavanderiaOrderItem> bare = jdbcTemplate.query(
            "select id, service_id, service_name_snapshot, qty, unit_price_cents, turnaround_snapshot "
                + "from lavanderia_order_items where order_id = ? order by id", ITEM_MAPPER, o.id());
        List<LavanderiaOrderItem> withOpts = new ArrayList<>(bare.size());
        for (LavanderiaOrderItem it : bare) {
            List<LavanderiaOrderItemOption> options = jdbcTemplate.query(
                "select id, service_option_id, group_label_snapshot, option_label_snapshot, price_delta_cents "
                    + "from lavanderia_order_item_options where order_item_id = ? order by id",
                ITEM_OPTION_MAPPER, it.id());
            withOpts.add(new LavanderiaOrderItem(it.id(), it.serviceId(), it.serviceName(), it.qty(),
                it.unitPriceCents(), it.turnaroundSnapshot(), options));
        }
        return new LavanderiaOrder(o.id(), o.conversationId(), o.status(), o.subtotalCents(),
            o.discountCents(), o.deliveryFeeCents(), o.totalCents(), o.couponCode(),
            o.loyaltyApplied(), o.express(), o.expressSurchargeCents(),
            o.deliveryAddress(), o.notes(), o.rejectionReason(),
            o.collectDate(), o.deliveryDate(), o.period(),
            o.createdAt(), o.statusUpdatedAt(), o.contactName(), o.contactPhone(), withOpts);
    }

    /**
     * Cria o pedido + itens + opções numa transação. Os preços/nomes/turnaround são lidos do catálogo
     * AGORA (snapshot); para cada linha, {@code unit_price = base + Σ deltas} das opções escolhidas. O
     * subtotal é a soma de unit_price × qty; o total = subtotal + delivery_fee.
     *
     * <p>ESCAPADA — a delivery_date é MATERIALIZADA = {@code collectDate + MAX(turnaround_snapshot)}
     * (MAX, não soma: processamento paralelo). Se {@code requestedDeliveryDate != null} e é ANTERIOR à
     * primeira possível, lança {@link TurnaroundViolationException} (com a primeira data possível). Se
     * omitida (null), materializa-se a primeira possível.
     *
     * <p>Linhas cujo serviço não existe/não é do tenant são IGNORADAS. Opção inválida →
     * {@link InvalidOptionException}. Subtotal abaixo do mínimo → {@link BelowMinimumException}.
     */
    @Transactional
    public LavanderiaOrder createOrder(UUID companyId, UUID conversationId, UUID contactId,
                                       String deliveryAddress, List<OrderLineInput> lines,
                                       int deliveryFeeCents, int minOrderCents, String notes,
                                       LocalDate collectDate, LocalDate requestedDeliveryDate, String period,
                                       String couponCode, boolean express,
                                       int expressSurchargePct, int expressTurnaroundDays) {
        record OptSnap(UUID serviceOptionId, String groupLabel, String optionLabel, int delta) {}
        record Snap(UUID serviceId, String name, int unitPrice, int qty, int turnaround, List<OptSnap> options) {}

        List<Snap> snaps = new ArrayList<>();
        int subtotal = 0;
        int maxTurnaround = 0;
        for (OrderLineInput line : lines) {
            record Base(String name, int price, int turnaround) {}
            List<Base> found = jdbcTemplate.query(
                "select name, price_cents, turnaround_days from lavanderia_services where company_id = ? and id = ?",
                (rs, rn) -> new Base(rs.getString("name"), rs.getInt("price_cents"), rs.getInt("turnaround_days")),
                companyId, line.serviceId());
            if (found.isEmpty()) {
                continue;   // serviço inexistente/de outro tenant: ignora a linha (defesa).
            }
            Base base = found.get(0);

            List<UUID> optionIds = line.optionIds() == null ? List.of() : line.optionIds();
            List<OptSnap> optSnaps = new ArrayList<>();
            int deltaSum = 0;
            if (!optionIds.isEmpty()) {
                List<LavanderiaServiceOption> resolved =
                    optionRepository.findByIdsForService(companyId, line.serviceId(), optionIds);
                if (resolved.size() != optionIds.size()) {
                    throw new InvalidOptionException();
                }
                for (LavanderiaServiceOption opt : resolved) {
                    optSnaps.add(new OptSnap(opt.id(), opt.groupLabel(), opt.optionLabel(), opt.priceDeltaCents()));
                    deltaSum += opt.priceDeltaCents();
                }
            }

            int unitPrice = base.price() + deltaSum;
            snaps.add(new Snap(line.serviceId(), base.name(), unitPrice, line.qty(), base.turnaround(), optSnaps));
            subtotal += unitPrice * line.qty();
            maxTurnaround = Math.max(maxTurnaround, base.turnaround());
        }
        if (snaps.isEmpty()) {
            throw new IllegalArgumentException("nenhum serviço válido no pedido");
        }
        if (minOrderCents > 0 && subtotal < minOrderCents) {
            throw new BelowMinimumException(minOrderCents);
        }

        // Onda 1 (backlog #6/#5): cupom + fidelidade, na MESMA transação (clone adega).
        UUID couponId = null;
        String couponSnapshot = null;
        int couponDiscount = 0;
        if (couponCode != null && !couponCode.isBlank()) {
            Optional<LavanderiaCoupon> maybe = couponRepository.findByCode(companyId, couponCode);
            if (maybe.isPresent()) {
                LavanderiaCoupon c = maybe.get();
                boolean valid = c.active()
                    && (c.validUntil() == null || !c.validUntil().isBefore(LocalDate.now()))
                    && (c.maxUses() == null || c.uses() < c.maxUses())
                    && subtotal >= c.minOrderCents();
                if (valid) {
                    couponDiscount = "percent".equals(c.kind())
                        ? subtotal * c.value() / 100
                        : c.value();
                    couponId = c.id();
                    couponSnapshot = c.code();
                    couponRepository.incrementUses(companyId, c.id());
                }
            }
        }

        boolean loyaltyApplied = false;
        int loyaltyDiscount = 0;
        LavanderiaLoyaltyConfig loyalty = loyaltyRepository.findByCompany(companyId);
        if (loyalty.enabled()) {
            Long delivered = jdbcTemplate.queryForObject(
                "select count(*) from lavanderia_orders where company_id = ? and contact_id = ? "
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

        // Onda 1 (backlog #2): EXPRESS substitui o turnaround pelos dias da config e soma a
        // sobretaxa (% do subtotal) — materializados em Java.
        int effectiveTurnaround = express ? expressTurnaroundDays : maxTurnaround;
        int expressSurcharge = express ? subtotal * expressSurchargePct / 100 : 0;
        int total = subtotal - discount + deliveryFeeCents + expressSurcharge;

        // ESCAPADA: delivery_date MATERIALIZADA = collect + turnaround efetivo. Em Java (date +
        // interval não é IMMUTABLE — lição end_at reaplicada às DATAS).
        LocalDate firstPossible = collectDate.plusDays(effectiveTurnaround);
        if (requestedDeliveryDate != null && requestedDeliveryDate.isBefore(firstPossible)) {
            throw new TurnaroundViolationException(firstPossible);
        }
        LocalDate deliveryDate = requestedDeliveryDate != null ? requestedDeliveryDate : firstPossible;

        UUID orderId = jdbcTemplate.queryForObject(
            "insert into lavanderia_orders (company_id, conversation_id, contact_id, subtotal_cents, "
                + "discount_cents, delivery_fee_cents, total_cents, coupon_id, coupon_code_snapshot, "
                + "loyalty_applied, express, express_surcharge_cents, delivery_address, notes, "
                + "collect_date, delivery_date, period) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, conversationId, contactId, subtotal, discount, deliveryFeeCents,
            total, couponId, couponSnapshot, loyaltyApplied, express, expressSurcharge,
            deliveryAddress, notes,
            java.sql.Date.valueOf(collectDate), java.sql.Date.valueOf(deliveryDate), period);

        for (Snap s : snaps) {
            UUID orderItemId = jdbcTemplate.queryForObject(
                "insert into lavanderia_order_items (order_id, service_id, qty, unit_price_cents, "
                    + "service_name_snapshot, turnaround_snapshot) values (?, ?, ?, ?, ?, ?) returning id",
                UUID.class, orderId, s.serviceId(), s.qty(), s.unitPrice(), s.name(), s.turnaround());
            for (OptSnap opt : s.options()) {
                jdbcTemplate.update(
                    "insert into lavanderia_order_item_options (order_item_id, service_option_id, "
                        + "group_label_snapshot, option_label_snapshot, price_delta_cents) "
                        + "values (?, ?, ?, ?, ?)",
                    orderItemId, opt.serviceOptionId(), opt.groupLabel(), opt.optionLabel(), opt.delta());
            }
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
                "update lavanderia_orders set status = ?, rejection_reason = ?, status_updated_at = now() "
                    + "where company_id = ? and id = ?",
                newStatus, rejectionReason, companyId, id);
        } else {
            jdbcTemplate.update(
                "update lavanderia_orders set status = ?, status_updated_at = now() "
                    + "where company_id = ? and id = ?",
                newStatus, companyId, id);
        }
    }
}
