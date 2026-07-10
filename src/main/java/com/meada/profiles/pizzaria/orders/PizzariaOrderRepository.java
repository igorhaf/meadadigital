package com.meada.profiles.pizzaria.orders;

import com.meada.profiles.pizzaria.menu.PizzariaMenuOption;
import com.meada.profiles.pizzaria.menu.PizzariaMenuOptionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code pizzaria_orders} + {@code pizzaria_order_items} + {@code pizzaria_order_item_options}
 * + {@code pizzaria_order_item_flavors} (camada 8.6). Clone do chassi comida com as escapadas:
 * (1) {@code rejection_reason} na transição de status (gate de aceite); (2) opções/modifiers por
 * item, recálculo do unit_price descartando o total da IA; (3) ESCAPADA NOVA — pizza MEIO-A-MEIO:
 * uma linha-pizza tem N frações (sabores); o preço da pizza usa a REGRA DO MAIOR VALOR —
 * {@code unit_price = MAX(preço dos sabores no tamanho) + Σ deltas dos modifiers} (NÃO soma, NÃO
 * média), recalculado no backend. Opera via service_role; o escopo por company_id no WHERE é a
 * defesa.
 */
@Repository
public class PizzariaOrderRepository {

    /** Alguma opção pedida é inválida/indisponível/de outro item — o pedido NÃO é criado. */
    public static class InvalidOptionException extends RuntimeException {}

    /** Algum sabor (fração) pedido não existe/não é do tenant/não é pizza — o pedido NÃO é criado. */
    public static class InvalidFlavorException extends RuntimeException {}

    private static final java.time.ZoneId BR = java.time.ZoneId.of("America/Sao_Paulo");

    private final JdbcTemplate jdbcTemplate;
    private final PizzariaMenuOptionRepository optionRepository;
    private final com.meada.profiles.pizzaria.coupons.PizzariaCouponRepository couponRepository;
    private final com.meada.profiles.pizzaria.loyalty.PizzariaLoyaltyConfigRepository loyaltyRepository;

    public PizzariaOrderRepository(JdbcTemplate jdbcTemplate,
                                 PizzariaMenuOptionRepository optionRepository,
                                 com.meada.profiles.pizzaria.coupons.PizzariaCouponRepository couponRepository,
                                 com.meada.profiles.pizzaria.loyalty.PizzariaLoyaltyConfigRepository loyaltyRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.optionRepository = optionRepository;
        this.couponRepository = couponRepository;
        this.loyaltyRepository = loyaltyRepository;
    }

    private final RowMapper<PizzariaOrderItemOption> ITEM_OPTION_MAPPER = (rs, rn) -> new PizzariaOrderItemOption(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("menu_option_id"),
        rs.getString("group_label_snapshot"),
        rs.getString("option_label_snapshot"),
        rs.getInt("price_delta_cents"));

    /** Mapeia a row de uma fração (sabor) de pizza meio-a-meio (ESCAPADA). */
    private final RowMapper<PizzariaOrderItemFlavor> ITEM_FLAVOR_MAPPER = (rs, rn) -> new PizzariaOrderItemFlavor(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("menu_item_id"),
        rs.getInt("fraction_index"),
        rs.getString("flavor_name_snapshot"),
        rs.getInt("flavor_price_cents_snapshot"));

    /** Mapeia a row de order_item SEM as opções/sabores (carregados à parte). */
    private final RowMapper<PizzariaOrderItem> ITEM_MAPPER = (rs, rn) -> new PizzariaOrderItem(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("menu_item_id"),
        rs.getString("item_name_snapshot"),
        rs.getInt("qtd"),
        rs.getInt("unit_price_cents"),
        List.of(),
        List.of());

    /** Mapeia a row de order (sem os itens — carregados à parte). */
    private PizzariaOrder mapOrder(java.sql.ResultSet rs, List<PizzariaOrderItem> items) throws java.sql.SQLException {
        return new PizzariaOrder(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("conversation_id"),
            rs.getString("status"),
            rs.getInt("subtotal_cents"),
            rs.getInt("discount_cents"),
            rs.getInt("delivery_fee_cents"),
            rs.getInt("total_cents"),
            rs.getString("coupon_code_snapshot"),
            rs.getBoolean("loyalty_applied"),
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
            + "o.delivery_address, o.notes, o.rejection_reason, o.created_at, "
            + "o.status_updated_at, ct.name as contact_name, ct.phone_number as contact_phone "
            + "from pizzaria_orders o join contacts ct on ct.id = o.contact_id ";

    /** Lista pedidos do tenant (filtro opcional por status), paginado, mais recentes primeiro. */
    public List<PizzariaOrder> listByCompany(UUID companyId, String status, int limit, int offset) {
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

        List<PizzariaOrder> orders = jdbcTemplate.query(sql.toString(),
            (rs, rn) -> mapOrder(rs, List.of()), args.toArray());
        // Hidrata itens (e suas opções) de cada pedido (lista pequena — N+1 aceitável no Kanban).
        List<PizzariaOrder> withItems = new ArrayList<>(orders.size());
        for (PizzariaOrder o : orders) {
            withItems.add(withItems(o));
        }
        return withItems;
    }

    public long countByCompany(UUID companyId, String status) {
        StringBuilder sql = new StringBuilder("select count(*) from pizzaria_orders where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) {
            sql.append(" and status = ?");
            args.add(status);
        }
        Long n = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0L : n;
    }

    public Optional<PizzariaOrder> findById(UUID companyId, UUID id) {
        Optional<PizzariaOrder> base = jdbcTemplate.query(ORDER_SELECT + "where o.company_id = ? and o.id = ?",
                (rs, rn) -> mapOrder(rs, List.of()), companyId, id)
            .stream().findFirst();
        return base.map(this::withItems);
    }

    private PizzariaOrder withItems(PizzariaOrder o) {
        List<PizzariaOrderItem> bare = jdbcTemplate.query(
            "select id, menu_item_id, item_name_snapshot, qtd, unit_price_cents "
                + "from pizzaria_order_items where order_id = ? order by id", ITEM_MAPPER, o.id());
        List<PizzariaOrderItem> withOpts = new ArrayList<>(bare.size());
        for (PizzariaOrderItem it : bare) {
            List<PizzariaOrderItemOption> options = jdbcTemplate.query(
                "select id, menu_option_id, group_label_snapshot, option_label_snapshot, price_delta_cents "
                    + "from pizzaria_order_item_options where order_item_id = ? order by id",
                ITEM_OPTION_MAPPER, it.id());
            List<PizzariaOrderItemFlavor> flavors = jdbcTemplate.query(
                "select id, menu_item_id, fraction_index, flavor_name_snapshot, flavor_price_cents_snapshot "
                    + "from pizzaria_order_item_flavors where order_item_id = ? order by fraction_index",
                ITEM_FLAVOR_MAPPER, it.id());
            withOpts.add(new PizzariaOrderItem(it.id(), it.menuItemId(), it.itemName(), it.qtd(),
                it.unitPriceCents(), options, flavors));
        }
        return new PizzariaOrder(o.id(), o.conversationId(), o.status(), o.subtotalCents(),
            o.discountCents(), o.deliveryFeeCents(), o.totalCents(), o.couponCode(),
            o.loyaltyApplied(), o.deliveryAddress(), o.notes(), o.rejectionReason(),
            o.createdAt(), o.statusUpdatedAt(), o.contactName(), o.contactPhone(), withOpts);
    }

    /**
     * Cria o pedido + itens + opções + sabores numa transação. Os preços/nomes são lidos do cardápio
     * AGORA (snapshot). Para cada linha o preço da linha é recalculado no backend (descarta o total
     * da IA), em DOIS modos:
     * <ul>
     *   <li>Linha NÃO-pizza ({@code !hasFlavors()}): {@code unit_price = base + Σ deltas} das
     *       opções escolhidas — o chassi comida. {@code menuItemId} é o item.</li>
     *   <li>Linha PIZZA ({@code hasFlavors()}) — ESCAPADA meio-a-meio: cada {@code flavorItemId}
     *       é um sabor (uma row de {@code pizzaria_menu_items} de categoria pizza). O preço da pizza
     *       segue a REGRA DO MAIOR VALOR: {@code unit_price = MAX(price_cents dos sabores) + Σ deltas}
     *       dos modifiers (Tamanho/Borda). O {@code menu_item_id} do order_item é o sabor de MAIOR
     *       preço (o "principal"); cada fração vira uma row em {@code pizzaria_order_item_flavors}
     *       com snapshot de nome+preço.</li>
     * </ul>
     * O subtotal é a soma de unit_price × qtd; o total = subtotal + delivery_fee. Linhas sem item/sem
     * sabor válido são tratadas defensivamente: item inexistente em linha não-pizza é IGNORADO; opção
     * fantasma → {@link InvalidOptionException}; sabor fantasma/de outro tenant → {@link
     * InvalidFlavorException} (pedido NÃO criado). Lança IllegalArgumentException se, após filtrar,
     * não sobrar linha.
     */
    @Transactional
    public PizzariaOrder createOrder(UUID companyId, UUID conversationId, UUID contactId,
                                   String deliveryAddress, List<OrderLineInput> lines,
                                   String couponCode, int deliveryFeeCents, String notes) {
        // Snapshot de preço+nome+opções+sabores por linha (lê do cardápio do tenant).
        record OptSnap(UUID menuOptionId, String groupLabel, String optionLabel, int delta) {}
        record FlavorSnap(UUID menuItemId, int fractionIndex, String name, int priceCents) {}
        record Snap(UUID menuItemId, String name, int unitPrice, int qtd,
                    List<OptSnap> options, List<FlavorSnap> flavors) {}

        List<Snap> snaps = new ArrayList<>();
        int subtotal = 0;
        for (OrderLineInput line : lines) {
            record Base(String name, int price) {}

            // Resolve as opções escolhidas (modifiers: Tamanho/Borda). Vale nos dois modos.
            List<UUID> optionIds = line.optionIds() == null ? List.of() : line.optionIds();
            List<OptSnap> optSnaps = new ArrayList<>();
            int deltaSum = 0;
            String name;
            UUID itemId;
            int basePrice;
            List<FlavorSnap> flavorSnaps = new ArrayList<>();

            if (line.hasFlavors()) {
                // ESCAPADA meio-a-meio: cada flavorItemId é um sabor (item-pizza do cardápio).
                int maxPrice = -1;
                FlavorSnap principal = null;
                int idx = 1;
                for (UUID flavorId : line.flavorItemIds()) {
                    List<Base> f = jdbcTemplate.query(
                        "select name, price_cents from pizzaria_menu_items "
                            + "where company_id = ? and id = ? and available = true",
                        (rs, rn) -> new Base(rs.getString("name"), rs.getInt("price_cents")),
                        companyId, flavorId);
                    if (f.isEmpty()) {
                        throw new InvalidFlavorException();  // sabor fantasma → aborta o pedido.
                    }
                    Base fb = f.get(0);
                    FlavorSnap fs = new FlavorSnap(flavorId, idx++, fb.name(), fb.price());
                    flavorSnaps.add(fs);
                    if (fb.price() > maxPrice) {            // REGRA DO MAIOR VALOR (não soma, não média).
                        maxPrice = fb.price();
                        principal = fs;
                    }
                }
                basePrice = maxPrice;                       // o preço da pizza = MAX dos sabores.
                name = principal.name();
                itemId = principal.menuItemId();            // o order_item aponta pro sabor principal.

                // Modifiers da pizza são resolvidos pelo sabor PRINCIPAL (Tamanho/Borda vivem nele).
                if (!optionIds.isEmpty()) {
                    List<PizzariaMenuOption> resolved =
                        optionRepository.findByIdsForItem(companyId, itemId, optionIds);
                    if (resolved.size() != optionIds.size()) {
                        throw new InvalidOptionException();
                    }
                    for (PizzariaMenuOption opt : resolved) {
                        optSnaps.add(new OptSnap(opt.id(), opt.groupLabel(), opt.optionLabel(), opt.priceDeltaCents()));
                        deltaSum += opt.priceDeltaCents();
                    }
                }
            } else {
                // Chassi comida: linha de item simples (bebida, sobremesa, borda avulsa, combo).
                List<Base> found = jdbcTemplate.query(
                    "select name, price_cents from pizzaria_menu_items where company_id = ? and id = ?",
                    (rs, rn) -> new Base(rs.getString("name"), rs.getInt("price_cents")),
                    companyId, line.menuItemId());
                if (found.isEmpty()) {
                    continue;   // item inexistente/de outro tenant: ignora a linha (defesa).
                }
                Base base = found.get(0);
                name = base.name();
                itemId = line.menuItemId();
                basePrice = base.price();

                if (!optionIds.isEmpty()) {
                    List<PizzariaMenuOption> resolved =
                        optionRepository.findByIdsForItem(companyId, line.menuItemId(), optionIds);
                    if (resolved.size() != optionIds.size()) {
                        throw new InvalidOptionException();
                    }
                    for (PizzariaMenuOption opt : resolved) {
                        optSnaps.add(new OptSnap(opt.id(), opt.groupLabel(), opt.optionLabel(), opt.priceDeltaCents()));
                        deltaSum += opt.priceDeltaCents();
                    }
                }
            }

            int unitPrice = basePrice + deltaSum;
            snaps.add(new Snap(itemId, name, unitPrice, line.qtd(), optSnaps, flavorSnaps));
            subtotal += unitPrice * line.qtd();
        }
        if (snaps.isEmpty()) {
            throw new IllegalArgumentException("nenhum item válido no pedido");
        }
        // Cupom (backlog #1, best-effort — inválido NÃO aborta, o pedido sai sem o desconto).
        UUID couponId = null;
        String couponSnapshot = null;
        int couponDiscount = 0;
        if (couponCode != null && !couponCode.isBlank()) {
            java.util.Optional<com.meada.profiles.pizzaria.coupons.PizzariaCoupon> maybe =
                couponRepository.findByCode(companyId, couponCode);
            if (maybe.isPresent()) {
                com.meada.profiles.pizzaria.coupons.PizzariaCoupon c = maybe.get();
                java.time.LocalDate today = java.time.LocalDate.now(BR);
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

        // Fidelidade (backlog #2) — conta os ENTREGUES do contato ANTES de inserir o novo.
        boolean loyaltyApplied = false;
        int loyaltyDiscount = 0;
        com.meada.profiles.pizzaria.loyalty.PizzariaLoyaltyConfig loyalty =
            loyaltyRepository.findByCompany(companyId);
        if (loyalty.enabled()) {
            long deliveredCount = countDeliveredForContact(companyId, contactId);
            if (deliveredCount > 0 && deliveredCount % loyalty.thresholdOrders() == 0) {
                loyaltyApplied = true;
                loyaltyDiscount = "percent".equals(loyalty.rewardKind())
                    ? subtotal * loyalty.rewardValue() / 100
                    : loyalty.rewardValue();
            }
        }

        // Desconto total clampado ao subtotal (total nunca negativo).
        int discount = Math.min(subtotal, couponDiscount + loyaltyDiscount);
        int total = subtotal - discount + deliveryFeeCents;

        // status default 'aguardando' (não passamos status — ESCAPADA 1).
        UUID orderId = jdbcTemplate.queryForObject(
            "insert into pizzaria_orders (company_id, conversation_id, contact_id, subtotal_cents, "
                + "discount_cents, delivery_fee_cents, total_cents, coupon_id, coupon_code_snapshot, "
                + "loyalty_applied, delivery_address, notes) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, conversationId, contactId, subtotal, discount, deliveryFeeCents,
            total, couponId, couponSnapshot, loyaltyApplied, deliveryAddress, notes);

        // Incrementa uses do cupom aplicado (mesma transação).
        if (couponId != null) {
            couponRepository.incrementUses(companyId, couponId);
        }

        for (Snap s : snaps) {
            UUID orderItemId = jdbcTemplate.queryForObject(
                "insert into pizzaria_order_items (order_id, menu_item_id, qtd, unit_price_cents, "
                    + "item_name_snapshot) values (?, ?, ?, ?, ?) returning id",
                UUID.class, orderId, s.menuItemId(), s.qtd(), s.unitPrice(), s.name());
            for (OptSnap opt : s.options()) {
                jdbcTemplate.update(
                    "insert into pizzaria_order_item_options (order_item_id, menu_option_id, "
                        + "group_label_snapshot, option_label_snapshot, price_delta_cents) "
                        + "values (?, ?, ?, ?, ?)",
                    orderItemId, opt.menuOptionId(), opt.groupLabel(), opt.optionLabel(), opt.delta());
            }
            for (FlavorSnap fl : s.flavors()) {
                jdbcTemplate.update(
                    "insert into pizzaria_order_item_flavors (order_item_id, menu_item_id, "
                        + "fraction_index, flavor_name_snapshot, flavor_price_cents_snapshot) "
                        + "values (?, ?, ?, ?, ?)",
                    orderItemId, fl.menuItemId(), fl.fractionIndex(), fl.name(), fl.priceCents());
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
                "update pizzaria_orders set status = ?, rejection_reason = ?, status_updated_at = now() "
                    + "where company_id = ? and id = ?",
                newStatus, rejectionReason, companyId, id);
        } else {
            jdbcTemplate.update(
                "update pizzaria_orders set status = ?, status_updated_at = now() "
                    + "where company_id = ? and id = ?",
                newStatus, companyId, id);
        }
    }

    /**
     * Conta os pedidos ENTREGUES de um contato ({@code status = 'entregue'} — terminal
     * não-recusado/não-cancelado do chassi). Usado pela fidelidade (backlog #2).
     */
    public long countDeliveredForContact(UUID companyId, UUID contactId) {
        Long n = jdbcTemplate.queryForObject(
            "select count(*) from pizzaria_orders "
                + "where company_id = ? and contact_id = ? and status = 'entregue'",
            Long.class, companyId, contactId);
        return n == null ? 0L : n;
    }
}
