package com.meada.profiles.sushi.orders;

import com.meada.profiles.sushi.coupons.SushiCoupon;
import com.meada.profiles.sushi.coupons.SushiCouponRepository;
import com.meada.profiles.sushi.loyalty.SushiLoyaltyConfig;
import com.meada.profiles.sushi.loyalty.SushiLoyaltyConfigRepository;
import com.meada.profiles.sushi.statuses.SushiOrderStatusEntity;
import com.meada.profiles.sushi.statuses.SushiOrderStatusRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code sushi_orders} + {@code sushi_order_items} (camada 7.1 / sushi funcional). Opera via
 * service_role. O pedido é criado com SNAPSHOT de preço+nome, e os totais são calculados AQUI a
 * partir do banco — nunca do que a IA mandou (defesa contra total chutado).
 *
 * <p>A criação aplica CUPOM (valida code+validade+min+max, calcula desconto, incrementa uses) e
 * FIDELIDADE (conta os pedidos entregues do contato), com o desconto CLAMPADO ao subtotal. O status
 * inicial vem de {@code sushi_order_statuses.is_initial}; o status name é resolvido via join.
 */
@Repository
public class SushiOrderRepository {

    private static final ZoneId BR = ZoneId.of("America/Sao_Paulo");

    private final JdbcTemplate jdbcTemplate;
    private final SushiOrderStatusRepository statusRepository;
    private final SushiCouponRepository couponRepository;
    private final SushiLoyaltyConfigRepository loyaltyRepository;

    public SushiOrderRepository(JdbcTemplate jdbcTemplate,
                                SushiOrderStatusRepository statusRepository,
                                SushiCouponRepository couponRepository,
                                SushiLoyaltyConfigRepository loyaltyRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.statusRepository = statusRepository;
        this.couponRepository = couponRepository;
        this.loyaltyRepository = loyaltyRepository;
    }

    private final RowMapper<SushiOrderItem> ITEM_MAPPER = (rs, rn) -> new SushiOrderItem(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("menu_item_id"),
        rs.getString("item_name_snapshot"),
        rs.getInt("qtd"),
        rs.getInt("unit_price_cents"));

    /** Mapeia a row de order (sem os itens — carregados à parte). Join traz o status name. */
    private SushiOrder mapOrder(java.sql.ResultSet rs, List<SushiOrderItem> items) throws java.sql.SQLException {
        Date sched = rs.getDate("scheduled_date");
        return new SushiOrder(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("conversation_id"),
            (UUID) rs.getObject("status"),
            rs.getString("status_name"),
            rs.getInt("subtotal_cents"),
            rs.getInt("discount_cents"),
            rs.getInt("delivery_fee_cents"),
            rs.getInt("total_cents"),
            rs.getString("coupon_code_snapshot"),
            rs.getBoolean("loyalty_applied"),
            rs.getString("fulfillment"),
            sched == null ? null : sched.toLocalDate().toString(),
            rs.getString("scheduled_period"),
            rs.getString("delivery_address"),
            rs.getString("notes"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("status_updated_at").toInstant(),
            rs.getString("contact_name"),
            rs.getString("contact_phone"),
            items);
    }

    private static final String ORDER_SELECT =
        "select o.id, o.conversation_id, o.status, st.name as status_name, o.subtotal_cents, "
            + "o.discount_cents, o.delivery_fee_cents, o.total_cents, o.coupon_code_snapshot, "
            + "o.loyalty_applied, o.fulfillment, o.scheduled_date, o.scheduled_period, "
            + "o.delivery_address, o.notes, o.created_at, o.status_updated_at, "
            + "ct.name as contact_name, ct.phone_number as contact_phone "
            + "from sushi_orders o "
            + "join contacts ct on ct.id = o.contact_id "
            + "left join sushi_order_statuses st on st.id = o.status ";

    /**
     * Lista pedidos do tenant (filtro opcional por status — aceita o UUID do status OU o nome),
     * paginado, mais recentes primeiro.
     */
    public List<SushiOrder> listByCompany(UUID companyId, String status, int limit, int offset) {
        StringBuilder sql = new StringBuilder(ORDER_SELECT + "where o.company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        appendStatusFilter(sql, args, status);
        sql.append(" order by o.created_at desc limit ? offset ?");
        args.add(limit);
        args.add(offset);

        List<SushiOrder> orders = jdbcTemplate.query(sql.toString(),
            (rs, rn) -> mapOrder(rs, List.of()), args.toArray());
        List<SushiOrder> withItems = new ArrayList<>(orders.size());
        for (SushiOrder o : orders) {
            withItems.add(withItems(o));
        }
        return withItems;
    }

    public long countByCompany(UUID companyId, String status) {
        StringBuilder sql = new StringBuilder(
            "select count(*) from sushi_orders o "
                + "left join sushi_order_statuses st on st.id = o.status "
                + "where o.company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        appendStatusFilter(sql, args, status);
        Long n = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0L : n;
    }

    /** Filtro por status: se o valor é um UUID, casa por o.status; senão por st.name (case-insens.). */
    private void appendStatusFilter(StringBuilder sql, List<Object> args, String status) {
        if (status == null || status.isBlank()) {
            return;
        }
        if (isUuid(status)) {
            sql.append(" and o.status = ?::uuid");
            args.add(status.trim());
        } else {
            sql.append(" and lower(st.name) = lower(?)");
            args.add(status.trim());
        }
    }

    private static boolean isUuid(String s) {
        try {
            UUID.fromString(s.trim());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
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
        return new SushiOrder(o.id(), o.conversationId(), o.status(), o.statusName(), o.subtotalCents(),
            o.discountCents(), o.deliveryFeeCents(), o.totalCents(), o.couponCode(), o.loyaltyApplied(),
            o.fulfillment(), o.scheduledDate(), o.scheduledPeriod(), o.deliveryAddress(), o.notes(),
            o.createdAt(), o.statusUpdatedAt(), o.contactName(), o.contactPhone(), items);
    }

    /** Status inicial do pedido não configurado (→ o service trata como erro de config). */
    public static class NoInitialStatusException extends RuntimeException {}

    /**
     * Cria o pedido + itens numa transação. Snapshot de preço+nome (lê do cardápio AGORA); subtotal
     * = Σ price*qtd; delivery_fee = entrega ? config : 0. Aplica cupom (best-effort: cupom inválido
     * NÃO aborta — apenas não desconta) + fidelidade (conta os entregues do contato ANTES de inserir
     * o novo). discount = min(subtotal, cupom+fidelidade). total = subtotal - discount + delivery_fee.
     */
    @Transactional
    public SushiOrder createOrder(UUID companyId, UUID conversationId, UUID contactId,
                                  String deliveryAddress, List<OrderLineInput> lines,
                                  String fulfillment, LocalDate scheduledDate, String scheduledPeriod,
                                  String couponCode, int deliveryFeeCents, String notes) {
        // 1) Snapshot de preço+nome por linha + subtotal.
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

        boolean isRetirada = "retirada".equals(fulfillment);
        int fee = isRetirada ? 0 : deliveryFeeCents;

        // 2) Cupom (best-effort — inválido NÃO aborta).
        UUID couponId = null;
        String couponSnapshot = null;
        int couponDiscount = 0;
        if (couponCode != null && !couponCode.isBlank()) {
            Optional<SushiCoupon> maybe = couponRepository.findByCode(companyId, couponCode);
            if (maybe.isPresent()) {
                SushiCoupon c = maybe.get();
                LocalDate today = LocalDate.now(BR);
                boolean valid = c.active()
                    && (c.validUntil() == null || !c.validUntil().isBefore(today))
                    && subtotal >= c.minOrderCents()
                    && (c.maxUses() == null || c.uses() < c.maxUses());
                if (valid) {
                    couponDiscount = "percent".equals(c.kind())
                        ? subtotal * c.value() / 100
                        : c.value();
                    couponId = c.id();
                    couponSnapshot = c.code();
                }
            }
        }

        // 3) Fidelidade — conta os pedidos ENTREGUES (terminais não-cancelados) do contato ANTES de
        // inserir o novo (o novo não conta a si mesmo).
        boolean loyaltyApplied = false;
        int loyaltyDiscount = 0;
        SushiLoyaltyConfig loyalty = loyaltyRepository.findByCompany(companyId);
        if (loyalty.enabled()) {
            long deliveredCount = countDeliveredForContact(companyId, contactId);
            if (deliveredCount > 0 && deliveredCount % loyalty.thresholdOrders() == 0) {
                loyaltyApplied = true;
                loyaltyDiscount = "percent".equals(loyalty.rewardKind())
                    ? subtotal * loyalty.rewardValue() / 100
                    : loyalty.rewardValue();
            }
        }

        // 4) Desconto total clampado ao subtotal (total nunca negativo).
        int discount = Math.min(subtotal, couponDiscount + loyaltyDiscount);
        int total = subtotal - discount + fee;

        // 5) Status inicial.
        SushiOrderStatusEntity initial = statusRepository.findInitial(companyId)
            .orElseThrow(NoInitialStatusException::new);

        UUID orderId = jdbcTemplate.queryForObject(
            "insert into sushi_orders (company_id, conversation_id, contact_id, status, subtotal_cents, "
                + "discount_cents, delivery_fee_cents, total_cents, coupon_id, coupon_code_snapshot, "
                + "loyalty_applied, fulfillment, scheduled_date, scheduled_period, delivery_address, notes) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, conversationId, contactId, initial.id(), subtotal, discount, fee, total,
            couponId, couponSnapshot, loyaltyApplied, fulfillment,
            scheduledDate == null ? null : Date.valueOf(scheduledDate), scheduledPeriod,
            deliveryAddress, notes);

        for (Snap s : snaps) {
            jdbcTemplate.update(
                "insert into sushi_order_items (order_id, menu_item_id, qtd, unit_price_cents, "
                    + "item_name_snapshot) values (?, ?, ?, ?, ?)",
                orderId, s.menuItemId(), s.qtd(), s.price(), s.name());
        }

        // 6) Incrementa uses do cupom aplicado (mesma transação).
        if (couponId != null) {
            couponRepository.incrementUses(companyId, couponId);
        }

        return findById(companyId, orderId).orElseThrow();
    }

    /**
     * Conta os pedidos ENTREGUES de um contato: aqueles cujo status é TERMINAL e o nome NÃO contém
     * "cancel" (regra pragmática — "entregue" não é um flag; um pedido terminal não-cancelado chegou
     * ao fim "bem"). Usado pela fidelidade.
     */
    public long countDeliveredForContact(UUID companyId, UUID contactId) {
        Long n = jdbcTemplate.queryForObject(
            "select count(*) from sushi_orders o "
                + "join sushi_order_statuses st on st.id = o.status "
                + "where o.company_id = ? and o.contact_id = ? "
                + "and st.is_terminal = true and st.name not ilike '%cancel%'",
            Long.class, companyId, contactId);
        return n == null ? 0L : n;
    }

    /** Persiste a transição de status + status_updated_at. Service já validou. */
    public void updateStatus(UUID companyId, UUID id, UUID newStatusId) {
        jdbcTemplate.update(
            "update sushi_orders set status = ?, status_updated_at = now() "
                + "where company_id = ? and id = ?",
            newStatusId, companyId, id);
    }
}
