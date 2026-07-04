package com.meada.profiles.padaria.orders;

import com.meada.profiles.padaria.menu.PadariaMenuOption;
import com.meada.profiles.padaria.menu.PadariaMenuOptionRepository;
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
 * Acesso a {@code padaria_orders} + {@code padaria_order_items} + {@code padaria_order_item_options}
 * (camada 8.8 / perfil padaria). Clone de
 * {@link com.meada.profiles.floricultura.orders.FloriculturaOrderRepository} com as escapadas
 * da padaria: (1) ESCAPADA 1 — itens sob encomenda (made_to_order) exigem
 * {@code pickup_or_delivery_date} que respeite o lead time (today + MAX dos leads); ausente/cedo demais
 * → {@link LeadTimeViolationException} com a 1ª data possível. (2) fulfillment — 'entrega' exige
 * delivery_address (→ {@link AddressRequiredException}) e soma a taxa; 'retirada' não. (3) ESCAPADA 2 —
 * opções por item + cake_message (snapshot), recálculo do unit_price = base + Σ deltas (descarta o
 * total da IA). Opera via service_role; o escopo por company_id no WHERE é a defesa.
 */
@Repository
public class PadariaOrderRepository {

    /** Alguma opção pedida é inválida/indisponível/de outro item — o pedido NÃO é criado. */
    public static class InvalidOptionException extends RuntimeException {}

    /**
     * Há item sob encomenda (made_to_order) mas a data está ausente ou cedo demais (antes de
     * today + MAX(lead)). Carrega a {@code earliestDate} (1ª data possível) para a resposta defensiva.
     */
    public static class LeadTimeViolationException extends RuntimeException {
        private final LocalDate earliestDate;
        public LeadTimeViolationException(LocalDate earliestDate) {
            this.earliestDate = earliestDate;
        }
        public LocalDate earliestDate() {
            return earliestDate;
        }
    }

    /** Pedido 'entrega' sem delivery_address — o pedido NÃO é criado. */
    public static class AddressRequiredException extends RuntimeException {}

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private final JdbcTemplate jdbcTemplate;
    private final PadariaMenuOptionRepository optionRepository;

    public PadariaOrderRepository(JdbcTemplate jdbcTemplate,
                                  PadariaMenuOptionRepository optionRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.optionRepository = optionRepository;
    }

    private final RowMapper<PadariaOrderItemOption> ITEM_OPTION_MAPPER = (rs, rn) -> new PadariaOrderItemOption(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("menu_option_id"),
        rs.getString("group_label_snapshot"),
        rs.getString("option_label_snapshot"),
        rs.getInt("price_delta_cents"));

    /** Mapeia a row de order_item SEM as opções (carregadas à parte). */
    private final RowMapper<PadariaOrderItem> ITEM_MAPPER = (rs, rn) -> new PadariaOrderItem(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("menu_item_id"),
        rs.getString("item_name_snapshot"),
        rs.getInt("qtd"),
        rs.getInt("unit_price_cents"),
        rs.getBoolean("made_to_order_snapshot"),
        rs.getString("cake_message"),
        List.of());

    /** Mapeia a row de order (sem os itens — carregados à parte). */
    private PadariaOrder mapOrder(java.sql.ResultSet rs, List<PadariaOrderItem> items) throws java.sql.SQLException {
        java.sql.Date dd = rs.getDate("pickup_or_delivery_date");
        return new PadariaOrder(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("conversation_id"),
            rs.getString("status"),
            rs.getString("fulfillment"),
            rs.getInt("subtotal_cents"),
            rs.getInt("delivery_fee_cents"),
            rs.getInt("total_cents"),
            rs.getString("delivery_address"),
            dd == null ? null : dd.toLocalDate(),
            rs.getString("delivery_period"),
            rs.getObject("deposit_cents") == null ? null : rs.getInt("deposit_cents"),
            rs.getBoolean("deposit_paid"),
            rs.getTimestamp("deposit_paid_at") == null ? null : rs.getTimestamp("deposit_paid_at").toInstant(),
            rs.getString("notes"),
            rs.getString("rejection_reason"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("status_updated_at").toInstant(),
            rs.getString("contact_name"),
            rs.getString("contact_phone"),
            items);
    }

    private static final String ORDER_SELECT =
        "select o.id, o.conversation_id, o.status, o.fulfillment, o.subtotal_cents, o.delivery_fee_cents, "
            + "o.total_cents, o.delivery_address, o.pickup_or_delivery_date, o.delivery_period, "
            + "o.deposit_cents, o.deposit_paid, o.deposit_paid_at, o.notes, "
            + "o.rejection_reason, o.created_at, o.status_updated_at, "
            + "ct.name as contact_name, ct.phone_number as contact_phone "
            + "from padaria_orders o join contacts ct on ct.id = o.contact_id ";

    /** Lista pedidos do tenant (filtro opcional por status), paginado, mais recentes primeiro. */
    public List<PadariaOrder> listByCompany(UUID companyId, String status, int limit, int offset) {
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

        List<PadariaOrder> orders = jdbcTemplate.query(sql.toString(),
            (rs, rn) -> mapOrder(rs, List.of()), args.toArray());
        // Hidrata itens (e suas opções) de cada pedido (lista pequena — N+1 aceitável no Kanban).
        List<PadariaOrder> withItems = new ArrayList<>(orders.size());
        for (PadariaOrder o : orders) {
            withItems.add(withItems(o));
        }
        return withItems;
    }

    public long countByCompany(UUID companyId, String status) {
        StringBuilder sql = new StringBuilder("select count(*) from padaria_orders where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) {
            sql.append(" and status = ?");
            args.add(status);
        }
        Long n = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0L : n;
    }

    public Optional<PadariaOrder> findById(UUID companyId, UUID id) {
        Optional<PadariaOrder> base = jdbcTemplate.query(ORDER_SELECT + "where o.company_id = ? and o.id = ?",
                (rs, rn) -> mapOrder(rs, List.of()), companyId, id)
            .stream().findFirst();
        return base.map(this::withItems);
    }

    private PadariaOrder withItems(PadariaOrder o) {
        List<PadariaOrderItem> bare = jdbcTemplate.query(
            "select id, menu_item_id, item_name_snapshot, qtd, unit_price_cents, made_to_order_snapshot, cake_message "
                + "from padaria_order_items where order_id = ? order by id", ITEM_MAPPER, o.id());
        List<PadariaOrderItem> withOpts = new ArrayList<>(bare.size());
        for (PadariaOrderItem it : bare) {
            List<PadariaOrderItemOption> options = jdbcTemplate.query(
                "select id, menu_option_id, group_label_snapshot, option_label_snapshot, price_delta_cents "
                    + "from padaria_order_item_options where order_item_id = ? order by id",
                ITEM_OPTION_MAPPER, it.id());
            withOpts.add(new PadariaOrderItem(it.id(), it.menuItemId(), it.itemName(), it.qtd(),
                it.unitPriceCents(), it.madeToOrder(), it.cakeMessage(), options));
        }
        return new PadariaOrder(o.id(), o.conversationId(), o.status(), o.fulfillment(), o.subtotalCents(),
            o.deliveryFeeCents(), o.totalCents(), o.deliveryAddress(), o.pickupOrDeliveryDate(),
            o.deliveryPeriod(), o.depositCents(), o.depositPaid(), o.depositPaidAt(), o.notes(), o.rejectionReason(), o.createdAt(), o.statusUpdatedAt(),
            o.contactName(), o.contactPhone(), withOpts);
    }

    /**
     * Cria o pedido + itens + opções numa transação. Os preços/nomes são lidos do cardápio AGORA
     * (snapshot); para cada linha, {@code unit_price = base + Σ deltas} das opções escolhidas. O
     * subtotal é a soma de unit_price × qtd; o total = subtotal + delivery_fee (só em 'entrega').
     *
     * <p>ESCAPADA 1 — lead time: enquanto monta os snapshots, calcula a antecedência mínima exigida =
     * MAX(lead de cada item made_to_order; lead do item, com fallback no {@code leadTimeDaysDefault} da
     * config). Se há ALGUM item sob encomenda, a data é OBRIGATÓRIA e deve ser >= today + MAX(lead);
     * caso contrário lança {@link LeadTimeViolationException} com a 1ª data possível. Pedido só de
     * pronta-entrega não exige data.
     *
     * <p>fulfillment — 'entrega' exige {@code deliveryAddress} não-vazio (senão
     * {@link AddressRequiredException}) e soma {@code deliveryFeeCents}; 'retirada' ignora taxa/endereço.
     *
     * <p>Linhas cujo menu_item não existe/não é do tenant são IGNORADAS (o handler já validou). Se
     * alguma opção pedida é inválida/indisponível/de outro item, lança {@link InvalidOptionException}.
     * Lança IllegalArgumentException se, após filtrar, não sobrar linha.
     */
    @Transactional
    public PadariaOrder createOrder(UUID companyId, UUID conversationId, UUID contactId,
                                    String fulfillment, String deliveryAddress, List<OrderLineInput> lines,
                                    int deliveryFeeCents, int leadTimeDaysDefault,
                                    LocalDate pickupOrDeliveryDate, String deliveryPeriod, String notes) {
        // Snapshot de preço+nome+made_to_order+opções por linha (lê do cardápio do tenant).
        record OptSnap(UUID menuOptionId, String groupLabel, String optionLabel, int delta) {}
        record Snap(UUID menuItemId, String name, int unitPrice, int qtd, boolean madeToOrder,
                    String cakeMessage, List<OptSnap> options) {}

        List<Snap> snaps = new ArrayList<>();
        int subtotal = 0;
        int maxLead = 0;            // MAX dos leads dos itens sob encomenda (ESCAPADA 1).
        boolean anyMadeToOrder = false;
        for (OrderLineInput line : lines) {
            record Base(String name, int price, boolean madeToOrder, Integer leadTimeDays) {}
            List<Base> found = jdbcTemplate.query(
                "select name, price_cents, made_to_order, lead_time_days from padaria_menu_items "
                    + "where company_id = ? and id = ?",
                (rs, rn) -> {
                    Object leadObj = rs.getObject("lead_time_days");
                    Integer lead = leadObj == null ? null : ((Number) leadObj).intValue();
                    return new Base(rs.getString("name"), rs.getInt("price_cents"),
                        rs.getBoolean("made_to_order"), lead);
                },
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
                List<PadariaMenuOption> resolved =
                    optionRepository.findByIdsForItem(companyId, line.menuItemId(), optionIds);
                if (resolved.size() != optionIds.size()) {
                    throw new InvalidOptionException();
                }
                for (PadariaMenuOption opt : resolved) {
                    optSnaps.add(new OptSnap(opt.id(), opt.groupLabel(), opt.optionLabel(), opt.priceDeltaCents()));
                    deltaSum += opt.priceDeltaCents();
                }
            }

            // ESCAPADA 1: antecedência mínima do item (com fallback no default da config).
            if (base.madeToOrder()) {
                anyMadeToOrder = true;
                int lead = base.leadTimeDays() != null ? base.leadTimeDays() : leadTimeDaysDefault;
                maxLead = Math.max(maxLead, lead);
            }

            int unitPrice = base.price() + deltaSum;
            snaps.add(new Snap(line.menuItemId(), base.name(), unitPrice, line.qtd(),
                base.madeToOrder(), line.cakeMessage(), optSnaps));
            subtotal += unitPrice * line.qtd();
        }
        if (snaps.isEmpty()) {
            throw new IllegalArgumentException("nenhum item válido no pedido");
        }

        // ESCAPADA 1: valida a data CONDICIONAL (só obrigatória se há item sob encomenda).
        if (anyMadeToOrder) {
            LocalDate hoje = LocalDate.now(SAO_PAULO);
            LocalDate earliest = hoje.plusDays(maxLead);
            if (pickupOrDeliveryDate == null || pickupOrDeliveryDate.isBefore(earliest)) {
                throw new LeadTimeViolationException(earliest);
            }
        }

        // fulfillment: 'entrega' exige endereço + soma taxa; 'retirada' não.
        boolean isDelivery = PadariaOrderRepository.isEntrega(fulfillment);
        if (isDelivery && (deliveryAddress == null || deliveryAddress.isBlank())) {
            throw new AddressRequiredException();
        }
        int fee = isDelivery ? deliveryFeeCents : 0;
        String addressToPersist = isDelivery ? deliveryAddress : null;
        int total = subtotal + fee;

        // status default 'aguardando' (não passamos status — gate de aceite).
        UUID orderId = jdbcTemplate.queryForObject(
            "insert into padaria_orders (company_id, conversation_id, contact_id, fulfillment, subtotal_cents, "
                + "delivery_fee_cents, total_cents, delivery_address, pickup_or_delivery_date, delivery_period, notes) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, conversationId, contactId, fulfillment, subtotal, fee, total,
            addressToPersist, pickupOrDeliveryDate == null ? null : java.sql.Date.valueOf(pickupOrDeliveryDate),
            deliveryPeriod, notes);

        for (Snap s : snaps) {
            UUID orderItemId = jdbcTemplate.queryForObject(
                "insert into padaria_order_items (order_id, menu_item_id, qtd, unit_price_cents, "
                    + "item_name_snapshot, made_to_order_snapshot, cake_message) "
                    + "values (?, ?, ?, ?, ?, ?, ?) returning id",
                UUID.class, orderId, s.menuItemId(), s.qtd(), s.unitPrice(), s.name(),
                s.madeToOrder(), s.cakeMessage());
            for (OptSnap opt : s.options()) {
                jdbcTemplate.update(
                    "insert into padaria_order_item_options (order_item_id, menu_option_id, "
                        + "group_label_snapshot, option_label_snapshot, price_delta_cents) "
                        + "values (?, ?, ?, ?, ?)",
                    orderItemId, opt.menuOptionId(), opt.groupLabel(), opt.optionLabel(), opt.delta());
            }
        }
        return findById(companyId, orderId).orElseThrow();
    }

    /** True quando o fulfillment é 'entrega' (qualquer outro valor — incluindo null — é retirada/balcão). */
    private static boolean isEntrega(String fulfillment) {
        return "entrega".equals(fulfillment);
    }

    /**
     * Persiste a transição de status + status_updated_at. Service já validou a transição. Quando
     * {@code rejectionReason != null} (recusa), grava também o motivo.
     */
    public void updateStatus(UUID companyId, UUID id, String newStatus, String rejectionReason) {
        if (rejectionReason != null) {
            jdbcTemplate.update(
                "update padaria_orders set status = ?, rejection_reason = ?, status_updated_at = now() "
                    + "where company_id = ? and id = ?",
                newStatus, rejectionReason, companyId, id);
        } else {
            jdbcTemplate.update(
                "update padaria_orders set status = ?, status_updated_at = now() "
                    + "where company_id = ? and id = ?",
                newStatus, companyId, id);
        }
    }

    /** Registra/atualiza o sinal da encomenda (onda #1). deposit_paid_at preservado enquanto pago. */
    public Optional<PadariaOrder> updateDeposit(UUID companyId, UUID id, Integer depositCents, boolean depositPaid) {
        int n = jdbcTemplate.update(
            "update padaria_orders set deposit_cents = ?, deposit_paid = ?, "
                + "deposit_paid_at = case when ? then coalesce(deposit_paid_at, now()) end "
                + "where company_id = ? and id = ?",
            depositCents, depositPaid, depositPaid, companyId, id);
        if (n == 0) {
            return Optional.empty();
        }
        return findById(companyId, id);
    }
}
