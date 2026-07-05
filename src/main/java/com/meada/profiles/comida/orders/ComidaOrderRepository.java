package com.meada.profiles.comida.orders;

import com.meada.profiles.comida.coupons.ComidaCoupon;
import com.meada.profiles.comida.coupons.ComidaCouponRepository;
import com.meada.profiles.comida.loyalty.ComidaLoyaltyConfig;
import com.meada.profiles.comida.loyalty.ComidaLoyaltyConfigRepository;
import com.meada.profiles.comida.menu.ComidaMenuOption;
import com.meada.profiles.comida.menu.ComidaMenuOptionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code comida_orders} + {@code comida_order_items} + {@code comida_order_item_options}
 * (camada 8.4). Clone de {@link com.meada.profiles.sushi.orders.SushiOrderRepository} com
 * as DUAS escapadas: (1) {@code rejection_reason} na transição de status; (2) opções por item, com
 * recálculo do unit_price = base + Σ deltas (descarta o total da IA). Opera via service_role; o
 * escopo por company_id no WHERE é a defesa.
 */
@Repository
public class ComidaOrderRepository {

    /** Alguma opção pedida é inválida/indisponível/de outro item — o pedido NÃO é criado. */
    public static class InvalidOptionException extends RuntimeException {}

    private static final ZoneId BR = ZoneId.of("America/Sao_Paulo");

    private final JdbcTemplate jdbcTemplate;
    private final ComidaMenuOptionRepository optionRepository;
    private final ComidaCouponRepository couponRepository;
    private final ComidaLoyaltyConfigRepository loyaltyRepository;

    public ComidaOrderRepository(JdbcTemplate jdbcTemplate,
                                 ComidaMenuOptionRepository optionRepository,
                                 ComidaCouponRepository couponRepository,
                                 ComidaLoyaltyConfigRepository loyaltyRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.optionRepository = optionRepository;
        this.couponRepository = couponRepository;
        this.loyaltyRepository = loyaltyRepository;
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
            rs.getInt("discount_cents"),
            rs.getInt("delivery_fee_cents"),
            rs.getInt("total_cents"),
            rs.getString("coupon_code_snapshot"),
            rs.getBoolean("loyalty_applied"),
            rs.getString("zone_name_snapshot"),
            rs.getString("fulfillment"),
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
        "select o.id, o.conversation_id, o.status, o.subtotal_cents, o.discount_cents, "
            + "o.delivery_fee_cents, o.total_cents, o.coupon_code_snapshot, o.loyalty_applied, "
            + "o.zone_name_snapshot, o.fulfillment, o.delivery_address, o.notes, o.rejection_reason, o.created_at, "
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
            o.discountCents(), o.deliveryFeeCents(), o.totalCents(), o.couponCodeSnapshot(),
            o.loyaltyApplied(), o.zoneNameSnapshot(), o.fulfillment(),
            o.deliveryAddress(), o.notes(), o.rejectionReason(),
            o.createdAt(), o.statusUpdatedAt(), o.contactName(), o.contactPhone(), withOpts);
    }

    /**
     * Cria o pedido + itens + opções numa transação. Os preços/nomes são lidos do cardápio AGORA
     * (snapshot); para cada linha, {@code unit_price = base + Σ deltas} das opções escolhidas. O
     * subtotal é a soma de unit_price × qtd. Aplica cupom (onda 1 #1, best-effort: inválido NÃO
     * aborta — apenas não desconta) + fidelidade (onda 1 #2 — conta os entregues do contato ANTES
     * de inserir); discount = min(subtotal, cupom+fidelidade); total = subtotal − discount +
     * delivery_fee (a taxa já vem resolvida pelo service — zona #8 ou flat). Linhas cujo
     * menu_item não existe/não é do tenant são IGNORADAS (o handler já validou). Se alguma opção
     * pedida é inválida/indisponível/de outro item, lança {@link InvalidOptionException} (pedido NÃO
     * criado com opção fantasma). Lança IllegalArgumentException se, após filtrar, não sobrar linha.
     */
    @Transactional
    public ComidaOrder createOrder(UUID companyId, UUID conversationId, UUID contactId,
                                   String deliveryAddress, List<OrderLineInput> lines,
                                   String couponCode, int deliveryFeeCents, String zoneNameSnapshot,
                                   String fulfillment, String notes) {
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

        // Cupom (onda 1 #1, best-effort — inválido NÃO aborta, o pedido sai sem o desconto).
        UUID couponId = null;
        String couponSnapshot = null;
        int couponDiscount = 0;
        if (couponCode != null && !couponCode.isBlank()) {
            Optional<ComidaCoupon> maybe = couponRepository.findByCode(companyId, couponCode);
            if (maybe.isPresent()) {
                ComidaCoupon c = maybe.get();
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

        // Fidelidade (onda 1 #2) — conta os pedidos ENTREGUES do contato ANTES de inserir o novo.
        boolean loyaltyApplied = false;
        int loyaltyDiscount = 0;
        ComidaLoyaltyConfig loyalty = loyaltyRepository.findByCompany(companyId);
        if (loyalty.enabled()) {
            long deliveredCount = countDeliveredForContact(companyId, contactId);
            if (deliveredCount > 0 && deliveredCount % loyalty.thresholdOrders() == 0) {
                loyaltyApplied = true;
                loyaltyDiscount = "percent".equals(loyalty.rewardKind())
                    ? subtotal * loyalty.rewardValue() / 100
                    : loyalty.rewardValue();
            }
        }

        // Desconto total clampado ao subtotal (total nunca negativo). RETIRADA (onda 2, #3)
        // dispensa taxa (nem zona nem flat) e endereço.
        boolean retirada = "retirada".equals(fulfillment);
        int fee = retirada ? 0 : deliveryFeeCents;
        int discount = Math.min(subtotal, couponDiscount + loyaltyDiscount);
        int total = subtotal - discount + fee;

        // status default 'aguardando' (não passamos status — ESCAPADA 1).
        UUID orderId = jdbcTemplate.queryForObject(
            "insert into comida_orders (company_id, conversation_id, contact_id, subtotal_cents, "
                + "discount_cents, delivery_fee_cents, total_cents, coupon_id, coupon_code_snapshot, "
                + "loyalty_applied, zone_name_snapshot, fulfillment, delivery_address, notes) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, conversationId, contactId, subtotal, discount, fee,
            total, couponId, couponSnapshot, loyaltyApplied, zoneNameSnapshot,
            retirada ? "retirada" : "entrega", retirada ? null : deliveryAddress, notes);

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
        // Incrementa uses do cupom aplicado (mesma transação).
        if (couponId != null) {
            couponRepository.incrementUses(companyId, couponId);
        }

        return findById(companyId, orderId).orElseThrow();
    }

    /**
     * Conta os pedidos ENTREGUES de um contato ({@code status = 'entregue'}). Usado pela fidelidade
     * (onda 1 #2) e pelo progresso que a IA anuncia no contexto.
     */
    public long countDeliveredForContact(UUID companyId, UUID contactId) {
        Long n = jdbcTemplate.queryForObject(
            "select count(*) from comida_orders "
                + "where company_id = ? and contact_id = ? and status = 'entregue'",
            Long.class, companyId, contactId);
        return n == null ? 0L : n;
    }

    /** Endereço do ÚLTIMO pedido do contato (onda 1 #10 — a IA oferece reusar). */
    public Optional<String> findLastAddressForContact(UUID companyId, UUID contactId) {
        if (contactId == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
                "select delivery_address from comida_orders "
                    + "where company_id = ? and contact_id = ? and delivery_address is not null "
                    + "order by created_at desc limit 1",
                (rs, rn) -> rs.getString("delivery_address"), companyId, contactId)
            .stream().findFirst();
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
