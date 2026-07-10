package com.meada.profiles.papelaria.orders;

import com.meada.profiles.papelaria.catalog.PapelariaCatalogOption;
import com.meada.profiles.papelaria.catalog.PapelariaCatalogOptionRepository;
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
 * Acesso a {@code papelaria_orders} + {@code papelaria_order_items} + {@code papelaria_order_item_options}
 * (camada 8.15 / perfil papelaria). Clone de
 * {@link com.meada.profiles.padaria.orders.PadariaOrderRepository} (camada 8.8) com as
 * escapadas da papelaria: (1) itens sob encomenda (made_to_order) exigem
 * {@code pickup_or_delivery_date} que respeite o lead time (today + MAX dos leads); ausente/cedo demais
 * → {@link LeadTimeViolationException} com a 1ª data possível. (2) fulfillment — 'entrega' exige
 * delivery_address (→ {@link AddressRequiredException}) e soma a taxa; 'retirada' não. (3) opções por
 * item + custom_text (snapshot); recálculo do unit_price = base + Σ deltas, e o line = unit ×
 * QUANTITY (a TIRAGEM — eixo de negócio; descarta o total da IA). (4) PROVA DE ARTE — nasce
 * {@code art_approved=false}/{@code art_url=null}; {@link #setArtUrl} (aceito→arte_aprovacao, ação
 * humana) e {@link #setArtApproved} (seta o flag). Opera via service_role; o escopo por company_id no
 * WHERE é a defesa.
 */
@Repository
public class PapelariaOrderRepository {

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
    private final PapelariaCatalogOptionRepository optionRepository;

    public PapelariaOrderRepository(JdbcTemplate jdbcTemplate,
                                    PapelariaCatalogOptionRepository optionRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.optionRepository = optionRepository;
    }

    private final RowMapper<PapelariaOrderItemOption> ITEM_OPTION_MAPPER = (rs, rn) -> new PapelariaOrderItemOption(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("catalog_option_id"),
        rs.getString("group_label_snapshot"),
        rs.getString("option_label_snapshot"),
        rs.getInt("price_delta_cents"));

    /** Mapeia a row de order_item SEM as opções (carregadas à parte). */
    private final RowMapper<PapelariaOrderItem> ITEM_MAPPER = (rs, rn) -> new PapelariaOrderItem(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("catalog_item_id"),
        rs.getString("item_name_snapshot"),
        rs.getInt("quantity"),
        rs.getInt("unit_price_cents"),
        rs.getBoolean("made_to_order_snapshot"),
        rs.getString("custom_text"),
        List.of());

    /** Mapeia a row de order (sem os itens — carregados à parte). */
    private PapelariaOrder mapOrder(java.sql.ResultSet rs, List<PapelariaOrderItem> items) throws java.sql.SQLException {
        java.sql.Date dd = rs.getDate("pickup_or_delivery_date");
        return new PapelariaOrder(
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
            rs.getBoolean("art_approved"),
            rs.getString("art_url"),
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
            + "o.art_approved, o.art_url, o.deposit_cents, o.deposit_paid, o.deposit_paid_at, "
            + "o.notes, o.rejection_reason, o.created_at, o.status_updated_at, "
            + "ct.name as contact_name, ct.phone_number as contact_phone "
            + "from papelaria_orders o join contacts ct on ct.id = o.contact_id ";

    /** Lista pedidos do tenant (filtro opcional por status), paginado, mais recentes primeiro. */
    public List<PapelariaOrder> listByCompany(UUID companyId, String status, int limit, int offset) {
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

        List<PapelariaOrder> orders = jdbcTemplate.query(sql.toString(),
            (rs, rn) -> mapOrder(rs, List.of()), args.toArray());
        // Hidrata itens (e suas opções) de cada pedido (lista pequena — N+1 aceitável no Kanban).
        List<PapelariaOrder> withItems = new ArrayList<>(orders.size());
        for (PapelariaOrder o : orders) {
            withItems.add(withItems(o));
        }
        return withItems;
    }

    public long countByCompany(UUID companyId, String status) {
        StringBuilder sql = new StringBuilder("select count(*) from papelaria_orders where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) {
            sql.append(" and status = ?");
            args.add(status);
        }
        Long n = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0L : n;
    }

    public Optional<PapelariaOrder> findById(UUID companyId, UUID id) {
        Optional<PapelariaOrder> base = jdbcTemplate.query(ORDER_SELECT + "where o.company_id = ? and o.id = ?",
                (rs, rn) -> mapOrder(rs, List.of()), companyId, id)
            .stream().findFirst();
        return base.map(this::withItems);
    }

    private PapelariaOrder withItems(PapelariaOrder o) {
        List<PapelariaOrderItem> bare = jdbcTemplate.query(
            "select id, catalog_item_id, item_name_snapshot, quantity, unit_price_cents, made_to_order_snapshot, custom_text "
                + "from papelaria_order_items where order_id = ? order by id", ITEM_MAPPER, o.id());
        List<PapelariaOrderItem> withOpts = new ArrayList<>(bare.size());
        for (PapelariaOrderItem it : bare) {
            List<PapelariaOrderItemOption> options = jdbcTemplate.query(
                "select id, catalog_option_id, group_label_snapshot, option_label_snapshot, price_delta_cents "
                    + "from papelaria_order_item_options where order_item_id = ? order by id",
                ITEM_OPTION_MAPPER, it.id());
            withOpts.add(new PapelariaOrderItem(it.id(), it.catalogItemId(), it.itemName(), it.qtd(),
                it.unitPriceCents(), it.madeToOrder(), it.customText(), options));
        }
        return new PapelariaOrder(o.id(), o.conversationId(), o.status(), o.fulfillment(), o.subtotalCents(),
            o.deliveryFeeCents(), o.totalCents(), o.deliveryAddress(), o.pickupOrDeliveryDate(),
            o.deliveryPeriod(), o.artApproved(), o.artUrl(), o.depositCents(), o.depositPaid(),
            o.depositPaidAt(), o.notes(), o.rejectionReason(), o.createdAt(),
            o.statusUpdatedAt(), o.contactName(), o.contactPhone(), withOpts);
    }

    /**
     * Cria o pedido + itens + opções numa transação. Os preços/nomes são lidos do catálogo AGORA
     * (snapshot); para cada linha, {@code unit_price = base + Σ deltas} das opções escolhidas, e o
     * {@code line = unit_price × quantity} (a TIRAGEM). O subtotal é a soma dos lines; o total =
     * subtotal + delivery_fee (só em 'entrega'). Nasce {@code art_approved=false} / {@code art_url=null}.
     *
     * <p>ESCAPADA lead time: enquanto monta os snapshots, calcula a antecedência mínima exigida =
     * MAX(lead de cada item made_to_order; lead do item, com fallback no {@code leadTimeDaysDefault} da
     * config). Se há ALGUM item sob encomenda, a data é OBRIGATÓRIA e deve ser >= today + MAX(lead);
     * caso contrário lança {@link LeadTimeViolationException} com a 1ª data possível. Pedido só de
     * pronta-entrega não exige data.
     *
     * <p>fulfillment — 'entrega' exige {@code deliveryAddress} não-vazio (senão
     * {@link AddressRequiredException}) e soma {@code deliveryFeeCents}; 'retirada' ignora taxa/endereço.
     *
     * <p>Linhas cujo catalog_item não existe/não é do tenant são IGNORADAS (o handler já validou). Se
     * alguma opção pedida é inválida/indisponível/de outro item, lança {@link InvalidOptionException}.
     * Lança IllegalArgumentException se, após filtrar, não sobrar linha.
     */
    @Transactional
    public PapelariaOrder createOrder(UUID companyId, UUID conversationId, UUID contactId,
                                      String fulfillment, String deliveryAddress, List<OrderLineInput> lines,
                                      int deliveryFeeCents, int leadTimeDaysDefault,
                                      LocalDate pickupOrDeliveryDate, String deliveryPeriod, String notes) {
        // Snapshot de preço+nome+made_to_order+opções por linha (lê do catálogo do tenant).
        record OptSnap(UUID catalogOptionId, String groupLabel, String optionLabel, int delta) {}
        record Snap(UUID catalogItemId, String name, int unitPrice, int qtd, boolean madeToOrder,
                    String customText, List<OptSnap> options) {}

        List<Snap> snaps = new ArrayList<>();
        int subtotal = 0;
        int maxLead = 0;            // MAX dos leads dos itens sob encomenda.
        boolean anyMadeToOrder = false;
        for (OrderLineInput line : lines) {
            record Base(String name, int price, boolean madeToOrder, Integer leadTimeDays) {}
            List<Base> found = jdbcTemplate.query(
                "select name, price_cents, made_to_order, lead_time_days from papelaria_catalog_items "
                    + "where company_id = ? and id = ?",
                (rs, rn) -> {
                    Object leadObj = rs.getObject("lead_time_days");
                    Integer lead = leadObj == null ? null : ((Number) leadObj).intValue();
                    return new Base(rs.getString("name"), rs.getInt("price_cents"),
                        rs.getBoolean("made_to_order"), lead);
                },
                companyId, line.catalogItemId());
            if (found.isEmpty()) {
                continue;   // item inexistente/de outro tenant: ignora a linha (defesa).
            }
            Base base = found.get(0);

            // Resolve as opções escolhidas. Tamanho divergente → opção fantasma.
            List<UUID> optionIds = line.optionIds() == null ? List.of() : line.optionIds();
            List<OptSnap> optSnaps = new ArrayList<>();
            int deltaSum = 0;
            if (!optionIds.isEmpty()) {
                List<PapelariaCatalogOption> resolved =
                    optionRepository.findByIdsForItem(companyId, line.catalogItemId(), optionIds);
                if (resolved.size() != optionIds.size()) {
                    throw new InvalidOptionException();
                }
                for (PapelariaCatalogOption opt : resolved) {
                    optSnaps.add(new OptSnap(opt.id(), opt.groupLabel(), opt.optionLabel(), opt.priceDeltaCents()));
                    deltaSum += opt.priceDeltaCents();
                }
            }

            // Antecedência mínima do item (com fallback no default da config).
            if (base.madeToOrder()) {
                anyMadeToOrder = true;
                int lead = base.leadTimeDays() != null ? base.leadTimeDays() : leadTimeDaysDefault;
                maxLead = Math.max(maxLead, lead);
            }

            // Onda #2: faixa de TIRAGEM (maior min_qty <= quantity) substitui o preço-base;
            // sem faixa cadastrada → unit_price do item (compat). Trava intacta: tudo do catálogo.
            Integer tierPrice = jdbcTemplate.query(
                    "select unit_price_cents from papelaria_item_tiers "
                        + "where item_id = ? and min_qty <= ? order by min_qty desc limit 1",
                    (trs, trn) -> trs.getInt(1), line.catalogItemId(), line.qtd())
                .stream().findFirst().orElse(null);
            int unitPrice = (tierPrice != null ? tierPrice : base.price()) + deltaSum;
            snaps.add(new Snap(line.catalogItemId(), base.name(), unitPrice, line.qtd(),
                base.madeToOrder(), line.customText(), optSnaps));
            subtotal += unitPrice * line.qtd();   // TIRAGEM: line = unit × quantity.
        }
        if (snaps.isEmpty()) {
            throw new IllegalArgumentException("nenhum item válido no pedido");
        }

        // Valida a data CONDICIONAL (só obrigatória se há item sob encomenda).
        if (anyMadeToOrder) {
            LocalDate hoje = LocalDate.now(SAO_PAULO);
            LocalDate earliest = hoje.plusDays(maxLead);
            if (pickupOrDeliveryDate == null || pickupOrDeliveryDate.isBefore(earliest)) {
                throw new LeadTimeViolationException(earliest);
            }
        }

        // fulfillment: 'entrega' exige endereço + soma taxa; 'retirada' não.
        boolean isDelivery = isEntrega(fulfillment);
        if (isDelivery && (deliveryAddress == null || deliveryAddress.isBlank())) {
            throw new AddressRequiredException();
        }
        int fee = isDelivery ? deliveryFeeCents : 0;
        String addressToPersist = isDelivery ? deliveryAddress : null;
        int total = subtotal + fee;

        // status default 'aguardando' (gate de aceite); art_approved default false; art_url null.
        UUID orderId = jdbcTemplate.queryForObject(
            "insert into papelaria_orders (company_id, conversation_id, contact_id, fulfillment, subtotal_cents, "
                + "delivery_fee_cents, total_cents, delivery_address, pickup_or_delivery_date, delivery_period, notes) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, conversationId, contactId, fulfillment, subtotal, fee, total,
            addressToPersist, pickupOrDeliveryDate == null ? null : java.sql.Date.valueOf(pickupOrDeliveryDate),
            deliveryPeriod, notes);

        for (Snap s : snaps) {
            UUID orderItemId = jdbcTemplate.queryForObject(
                "insert into papelaria_order_items (order_id, catalog_item_id, quantity, unit_price_cents, "
                    + "item_name_snapshot, made_to_order_snapshot, custom_text) "
                    + "values (?, ?, ?, ?, ?, ?, ?) returning id",
                UUID.class, orderId, s.catalogItemId(), s.qtd(), s.unitPrice(), s.name(),
                s.madeToOrder(), s.customText());
            for (OptSnap opt : s.options()) {
                jdbcTemplate.update(
                    "insert into papelaria_order_item_options (order_item_id, catalog_option_id, "
                        + "group_label_snapshot, option_label_snapshot, price_delta_cents) "
                        + "values (?, ?, ?, ?, ?)",
                    orderItemId, opt.catalogOptionId(), opt.groupLabel(), opt.optionLabel(), opt.delta());
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
                "update papelaria_orders set status = ?, rejection_reason = ?, status_updated_at = now() "
                    + "where company_id = ? and id = ?",
                newStatus, rejectionReason, companyId, id);
        } else {
            jdbcTemplate.update(
                "update papelaria_orders set status = ?, status_updated_at = now() "
                    + "where company_id = ? and id = ?",
                newStatus, companyId, id);
        }
    }

    /**
     * ESCAPADA — sobe a arte (link) e move o pedido para 'arte_aprovacao' na MESMA atualização (ação
     * HUMANA do painel). Grava {@code art_url} + status='arte_aprovacao' + status_updated_at. O Service
     * já validou que o pedido estava 'aceito' (transição aceito→arte_aprovacao).
     */
    public void setArtUrl(UUID companyId, UUID id, String artUrl) {
        jdbcTemplate.update(
            "update papelaria_orders set art_url = ?, status = 'arte_aprovacao', status_updated_at = now() "
                + "where company_id = ? and id = ?",
            artUrl, companyId, id);
    }

    /** Seta o flag de aprovação da arte (sem mexer no status; a transição é feita à parte). */
    public void setArtApproved(UUID companyId, UUID id, boolean approved) {
        jdbcTemplate.update(
            "update papelaria_orders set art_approved = ? where company_id = ? and id = ?",
            approved, companyId, id);
    }

    /**
     * Resolve o pedido em 'arte_aprovacao' da conversa do contato (o caminho do
     * {@code <aprovacao_arte>} sem order_id). Se houver mais de um, pega o mais recente. Empty se não
     * houver nenhum aguardando aprovação de arte naquela conversa.
     */
    public Optional<PapelariaOrder> findArteAprovacaoByConversation(UUID companyId, UUID conversationId) {
        return jdbcTemplate.query(
                ORDER_SELECT + "where o.company_id = ? and o.conversation_id = ? and o.status = 'arte_aprovacao' "
                    + "order by o.created_at desc limit 1",
                (rs, rn) -> mapOrder(rs, List.of()), companyId, conversationId)
            .stream().findFirst()
            .map(this::withItems);
    }

    /** Registra/atualiza o sinal (onda #1). deposit_paid_at preservado enquanto pago. */
    public Optional<PapelariaOrder> updateDeposit(UUID companyId, UUID id, Integer depositCents, boolean depositPaid) {
        int n = jdbcTemplate.update(
            "update papelaria_orders set deposit_cents = ?, deposit_paid = ?, "
                + "deposit_paid_at = case when ? then coalesce(deposit_paid_at, now()) end, "
                + "status_updated_at = status_updated_at where company_id = ? and id = ?",
            depositCents, depositPaid, depositPaid, companyId, id);
        if (n == 0) {
            return Optional.empty();
        }
        return findById(companyId, id);
    }
}
