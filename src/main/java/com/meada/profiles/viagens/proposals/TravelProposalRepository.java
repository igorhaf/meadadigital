package com.meada.profiles.viagens.proposals;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code travel_proposals} + {@code travel_proposal_items} + {@code travel_itinerary_days}
 * (camada 8.18 / perfil viagens). Opera via service_role.
 *
 * <p>O {@code total_cents} da proposta e o {@code line_total_cents} de cada item de COTAÇÃO são
 * MATERIALIZADOS: cada mutação de item de cotação (add/update/delete) roda numa transação que grava
 * a linha e re-soma o total a partir do banco — nunca de um valor vindo de fora (lição end_at / total
 * chutado). Os dias de ITINERÁRIO ({@code travel_itinerary_days}) NÃO entram no total — são ordenados
 * por day_date asc NULLS LAST, day_number asc na leitura. Espelho do EventProposalRepository (chassi
 * eventos 8.2), timeline→itinerary multi-dia + category nos itens.
 */
@Repository
public class TravelProposalRepository {

    private final JdbcTemplate jdbcTemplate;

    public TravelProposalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<TravelProposalItem> ITEM_MAPPER = (rs, rn) -> new TravelProposalItem(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("proposal_id"),
        rs.getString("category"),
        rs.getString("description"),
        rs.getInt("quantity"),
        rs.getInt("unit_price_cents"),
        rs.getInt("line_total_cents"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private static final RowMapper<TravelItineraryDay> ITINERARY_MAPPER = (rs, rn) -> {
        Date dd = rs.getDate("day_date");
        return new TravelItineraryDay(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("proposal_id"),
            rs.getInt("day_number"),
            dd == null ? null : dd.toLocalDate(),
            rs.getString("title"),
            rs.getString("description"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());
    };

    private static final String PROPOSAL_SELECT =
        "select p.id, p.contact_id, p.consultant_id, p.conversation_id, p.customer_name, p.customer_phone, "
            + "c.name as consultant_name, p.destination, p.start_date, p.end_date, p.num_travelers, "
            + "p.travel_style, p.briefing, p.total_cents, p.status, p.notes, p.opened_at, p.closed_at, "
            + "p.status_updated_at, p.deposit_cents, p.deposit_paid, p.deposit_paid_at "
            + "from travel_proposals p left join travel_consultants c on c.id = p.consultant_id ";

    private TravelProposal mapProposal(java.sql.ResultSet rs, List<TravelProposalItem> items,
                                       List<TravelItineraryDay> itinerary) throws java.sql.SQLException {
        Date sd = rs.getDate("start_date");
        Date ed = rs.getDate("end_date");
        java.sql.Timestamp closed = rs.getTimestamp("closed_at");
        Integer depositCents = rs.getObject("deposit_cents") == null ? null : rs.getInt("deposit_cents");
        java.sql.Timestamp depositPaidAt = rs.getTimestamp("deposit_paid_at");
        return new TravelProposal(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("contact_id"),
            (UUID) rs.getObject("consultant_id"),
            (UUID) rs.getObject("conversation_id"),
            rs.getString("customer_name"),
            rs.getString("customer_phone"),
            rs.getString("consultant_name"),
            rs.getString("destination"),
            sd == null ? null : sd.toLocalDate(),
            ed == null ? null : ed.toLocalDate(),
            rs.getInt("num_travelers"),
            rs.getString("travel_style"),
            rs.getString("briefing"),
            rs.getInt("total_cents"),
            rs.getString("status"),
            rs.getString("notes"),
            rs.getTimestamp("opened_at").toInstant(),
            closed == null ? null : closed.toInstant(),
            rs.getTimestamp("status_updated_at").toInstant(),
            depositCents,
            rs.getBoolean("deposit_paid"),
            depositPaidAt == null ? null : depositPaidAt.toInstant(),
            items,
            itinerary);
    }

    /** Registra/atualiza o sinal (onda #1 — clone atelie). deposit_paid_at preservado enquanto pago. */
    public Optional<TravelProposal> updateDeposit(UUID companyId, UUID id, Integer depositCents, boolean depositPaid) {
        int n = jdbcTemplate.update(
            "update travel_proposals set deposit_cents = ?, deposit_paid = ?, "
                + "deposit_paid_at = case when ? then coalesce(deposit_paid_at, now()) end, "
                + "updated_at = now() where company_id = ? and id = ?",
            depositCents, depositPaid, depositPaid, companyId, id);
        if (n == 0) {
            return Optional.empty();
        }
        return findById(companyId, id);
    }

    // -------------------------------------------------------------------------
    // Snapshots de cliente (do contact da conversa/informado) — não há sub-entidade de cliente.
    // -------------------------------------------------------------------------

    public Optional<String> contactName(UUID companyId, UUID contactId) {
        if (contactId == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query("select name from contacts where company_id = ? and id = ?",
                (rs, rn) -> rs.getString("name"), companyId, contactId)
            .stream().findFirst();
    }

    public Optional<String> contactPhone(UUID companyId, UUID contactId) {
        if (contactId == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query("select phone_number from contacts where company_id = ? and id = ?",
                (rs, rn) -> rs.getString("phone_number"), companyId, contactId)
            .stream().findFirst();
    }

    // -------------------------------------------------------------------------
    // LISTAGEM / DETALHE
    // -------------------------------------------------------------------------

    public List<TravelProposal> listByCompany(UUID companyId, String status, UUID consultantId, UUID contactId,
                                              LocalDate dateFrom, LocalDate dateTo, int limit, int offset) {
        StringBuilder sql = new StringBuilder(PROPOSAL_SELECT + "where p.company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) { sql.append(" and p.status = ?"); args.add(status); }
        if (consultantId != null) { sql.append(" and p.consultant_id = ?"); args.add(consultantId); }
        if (contactId != null) { sql.append(" and p.contact_id = ?"); args.add(contactId); }
        if (dateFrom != null) { sql.append(" and p.start_date >= ?"); args.add(Date.valueOf(dateFrom)); }
        if (dateTo != null) { sql.append(" and p.start_date <= ?"); args.add(Date.valueOf(dateTo)); }
        sql.append(" order by p.opened_at desc limit ? offset ?");
        args.add(limit);
        args.add(offset);
        List<TravelProposal> proposals = jdbcTemplate.query(sql.toString(),
            (rs, rn) -> mapProposal(rs, List.of(), List.of()), args.toArray());
        List<TravelProposal> hydrated = new ArrayList<>(proposals.size());
        for (TravelProposal p : proposals) {
            hydrated.add(withChildren(p));
        }
        return hydrated;
    }

    public long countByCompany(UUID companyId, String status, UUID consultantId, UUID contactId,
                               LocalDate dateFrom, LocalDate dateTo) {
        StringBuilder sql = new StringBuilder("select count(*) from travel_proposals where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) { sql.append(" and status = ?"); args.add(status); }
        if (consultantId != null) { sql.append(" and consultant_id = ?"); args.add(consultantId); }
        if (contactId != null) { sql.append(" and contact_id = ?"); args.add(contactId); }
        if (dateFrom != null) { sql.append(" and start_date >= ?"); args.add(Date.valueOf(dateFrom)); }
        if (dateTo != null) { sql.append(" and start_date <= ?"); args.add(Date.valueOf(dateTo)); }
        Long n = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0L : n;
    }

    public Optional<TravelProposal> findById(UUID companyId, UUID id) {
        Optional<TravelProposal> base = jdbcTemplate.query(PROPOSAL_SELECT + "where p.company_id = ? and p.id = ?",
                (rs, rn) -> mapProposal(rs, List.of(), List.of()), companyId, id)
            .stream().findFirst();
        return base.map(this::withChildren);
    }

    private TravelProposal withChildren(TravelProposal p) {
        return new TravelProposal(p.id(), p.contactId(), p.consultantId(), p.conversationId(),
            p.customerName(), p.customerPhone(), p.consultantName(), p.destination(), p.startDate(),
            p.endDate(), p.numTravelers(), p.travelStyle(), p.briefing(), p.totalCents(), p.status(),
            p.notes(), p.openedAt(), p.closedAt(), p.statusUpdatedAt(),
            p.depositCents(), p.depositPaid(), p.depositPaidAt(),
            listItems(p.id()), listItinerary(p.id()));
    }

    /**
     * Abre a proposta (status 'rascunho', total 0). Snapshots de cliente (name/phone do contact).
     * consultantId/conversationId/destination/startDate/endDate/numTravelers/travelStyle/briefing
     * opcionais (numTravelers default 1 quando null).
     */
    public TravelProposal insertProposal(UUID companyId, UUID contactId, String customerName,
                                         String customerPhone, UUID consultantId, UUID conversationId,
                                         String destination, LocalDate startDate, LocalDate endDate,
                                         Integer numTravelers, String travelStyle, String briefing,
                                         String notes) {
        int travelers = numTravelers == null || numTravelers < 1 ? 1 : numTravelers;
        UUID id = jdbcTemplate.queryForObject(
            "insert into travel_proposals (company_id, contact_id, consultant_id, conversation_id, "
                + "customer_name, customer_phone, destination, start_date, end_date, num_travelers, "
                + "travel_style, briefing, notes, total_cents, status) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 'rascunho') returning id",
            UUID.class, companyId, contactId, consultantId, conversationId, customerName, customerPhone,
            destination, startDate == null ? null : Date.valueOf(startDate),
            endDate == null ? null : Date.valueOf(endDate), travelers, travelStyle, briefing, notes);
        return findById(companyId, id).orElseThrow();
    }

    /** Atualiza campos editáveis da proposta (consultant/destination/datas/travelers/style/briefing/notes). */
    public Optional<TravelProposal> updateFields(UUID companyId, UUID id, UUID consultantId,
                                                 boolean consultantProvided, String destination,
                                                 boolean destinationProvided, LocalDate startDate,
                                                 boolean startProvided, LocalDate endDate, boolean endProvided,
                                                 Integer numTravelers, boolean travelersProvided,
                                                 String travelStyle, boolean styleProvided, String briefing,
                                                 String notes) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (consultantProvided) { sets.add("consultant_id = ?"); args.add(consultantId); }
        if (destinationProvided) { sets.add("destination = ?"); args.add(destination); }
        if (startProvided) { sets.add("start_date = ?"); args.add(startDate == null ? null : Date.valueOf(startDate)); }
        if (endProvided) { sets.add("end_date = ?"); args.add(endDate == null ? null : Date.valueOf(endDate)); }
        if (travelersProvided && numTravelers != null) { sets.add("num_travelers = ?"); args.add(numTravelers); }
        if (styleProvided) { sets.add("travel_style = ?"); args.add(travelStyle); }
        if (briefing != null) { sets.add("briefing = ?"); args.add(briefing); }
        if (notes != null) { sets.add("notes = ?"); args.add(notes); }
        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update("update travel_proposals set " + String.join(", ", sets)
                + " where company_id = ? and id = ?", args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    /**
     * Persiste a transição de status + status_updated_at. Preenche closed_at em terminais
     * (realizada/recusada/cancelada). Service já validou a transição.
     */
    public void updateStatus(UUID companyId, UUID id, String newStatus, boolean terminal) {
        if (terminal) {
            jdbcTemplate.update("update travel_proposals set status = ?, status_updated_at = now(), "
                + "closed_at = now(), updated_at = now() where company_id = ? and id = ?",
                newStatus, companyId, id);
        } else {
            jdbcTemplate.update("update travel_proposals set status = ?, status_updated_at = now(), "
                + "updated_at = now() where company_id = ? and id = ?",
                newStatus, companyId, id);
        }
    }

    // -------------------------------------------------------------------------
    // ITENS DE COTAÇÃO — cada mutação recalcula o total_cents da proposta na MESMA transação.
    // -------------------------------------------------------------------------

    public List<TravelProposalItem> listItems(UUID proposalId) {
        return jdbcTemplate.query(
            "select id, proposal_id, category, description, quantity, unit_price_cents, line_total_cents, "
                + "created_at, updated_at from travel_proposal_items where proposal_id = ? order by created_at asc",
            ITEM_MAPPER, proposalId);
    }

    public Optional<TravelProposalItem> findItem(UUID companyId, UUID proposalId, UUID itemId) {
        return jdbcTemplate.query(
            "select id, proposal_id, category, description, quantity, unit_price_cents, line_total_cents, "
                + "created_at, updated_at from travel_proposal_items where company_id = ? and proposal_id = ? and id = ?",
            ITEM_MAPPER, companyId, proposalId, itemId).stream().findFirst();
    }

    @Transactional
    public TravelProposalItem addItem(UUID companyId, UUID proposalId, String category, String description,
                                      int quantity, int unitPriceCents) {
        int lineTotal = quantity * unitPriceCents;
        UUID id = jdbcTemplate.queryForObject(
            "insert into travel_proposal_items (company_id, proposal_id, category, description, quantity, "
                + "unit_price_cents, line_total_cents) values (?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, proposalId, category, description.trim(), quantity, unitPriceCents, lineTotal);
        recalcTotal(companyId, proposalId);
        return findItem(companyId, proposalId, id).orElseThrow();
    }

    @Transactional
    public Optional<TravelProposalItem> updateItem(UUID companyId, UUID proposalId, UUID itemId, String category,
                                                   String description, Integer quantity, Integer unitPriceCents) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (category != null && !category.isBlank()) { sets.add("category = ?"); args.add(category); }
        if (description != null && !description.isBlank()) { sets.add("description = ?"); args.add(description.trim()); }
        if (quantity != null) { sets.add("quantity = ?"); args.add(quantity); }
        if (unitPriceCents != null) { sets.add("unit_price_cents = ?"); args.add(unitPriceCents); }
        if (sets.isEmpty()) {
            return findItem(companyId, proposalId, itemId);
        }
        // recalcula line_total_cents = quantity * unit_price (com os valores finais).
        sets.add("line_total_cents = quantity * unit_price_cents");
        sets.add("updated_at = now()");
        args.add(companyId);
        args.add(proposalId);
        args.add(itemId);
        int n = jdbcTemplate.update("update travel_proposal_items set " + String.join(", ", sets)
            + " where company_id = ? and proposal_id = ? and id = ?", args.toArray());
        if (n == 0) {
            return Optional.empty();
        }
        recalcTotal(companyId, proposalId);
        return findItem(companyId, proposalId, itemId);
    }

    @Transactional
    public boolean deleteItem(UUID companyId, UUID proposalId, UUID itemId) {
        int n = jdbcTemplate.update("delete from travel_proposal_items where company_id = ? and proposal_id = ? and id = ?",
            companyId, proposalId, itemId);
        if (n == 0) {
            return false;
        }
        recalcTotal(companyId, proposalId);
        return true;
    }

    /** Re-soma o total da proposta a partir das linhas de COTAÇÃO (materializa o derivado). */
    private void recalcTotal(UUID companyId, UUID proposalId) {
        jdbcTemplate.update(
            "update travel_proposals set total_cents = coalesce("
                + "(select sum(line_total_cents) from travel_proposal_items where proposal_id = ?), 0), "
                + "updated_at = now() where company_id = ? and id = ?",
            proposalId, companyId, proposalId);
    }

    // -------------------------------------------------------------------------
    // DIAS DE ITINERÁRIO (a escapada multi-dia) — NÃO recalculam total. Ordenados por
    // day_date asc NULLS LAST, day_number asc, created_at asc na leitura.
    // -------------------------------------------------------------------------

    public List<TravelItineraryDay> listItinerary(UUID proposalId) {
        return jdbcTemplate.query(
            "select id, proposal_id, day_number, day_date, title, description, created_at, updated_at "
                + "from travel_itinerary_days where proposal_id = ? "
                + "order by day_date asc nulls last, day_number asc, created_at asc",
            ITINERARY_MAPPER, proposalId);
    }

    public Optional<TravelItineraryDay> findItineraryDay(UUID companyId, UUID proposalId, UUID dayId) {
        return jdbcTemplate.query(
            "select id, proposal_id, day_number, day_date, title, description, created_at, updated_at "
                + "from travel_itinerary_days where company_id = ? and proposal_id = ? and id = ?",
            ITINERARY_MAPPER, companyId, proposalId, dayId).stream().findFirst();
    }

    /** day_number = max(day_number)+1 dos dias da proposta (sequência). day_date opcional. */
    public TravelItineraryDay addItineraryDay(UUID companyId, UUID proposalId, LocalDate dayDate,
                                              String title, String description) {
        Integer maxDay = jdbcTemplate.queryForObject(
            "select coalesce(max(day_number), 0) from travel_itinerary_days where proposal_id = ?",
            Integer.class, proposalId);
        int nextDay = (maxDay == null ? 0 : maxDay) + 1;
        UUID id = jdbcTemplate.queryForObject(
            "insert into travel_itinerary_days (company_id, proposal_id, day_number, day_date, title, description) "
                + "values (?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, proposalId, nextDay,
            dayDate == null ? null : Date.valueOf(dayDate), title.trim(), description);
        return findItineraryDay(companyId, proposalId, id).orElseThrow();
    }

    public Optional<TravelItineraryDay> updateItineraryDay(UUID companyId, UUID proposalId, UUID dayId,
                                                           Integer dayNumber, boolean dayNumberProvided,
                                                           LocalDate dayDate, boolean dateProvided,
                                                           String title, String description, boolean descProvided) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (dayNumberProvided && dayNumber != null) { sets.add("day_number = ?"); args.add(dayNumber); }
        if (dateProvided) { sets.add("day_date = ?"); args.add(dayDate == null ? null : Date.valueOf(dayDate)); }
        if (title != null && !title.isBlank()) { sets.add("title = ?"); args.add(title.trim()); }
        if (descProvided) { sets.add("description = ?"); args.add(description); }
        if (sets.isEmpty()) {
            return findItineraryDay(companyId, proposalId, dayId);
        }
        sets.add("updated_at = now()");
        args.add(companyId);
        args.add(proposalId);
        args.add(dayId);
        int n = jdbcTemplate.update("update travel_itinerary_days set " + String.join(", ", sets)
            + " where company_id = ? and proposal_id = ? and id = ?", args.toArray());
        if (n == 0) {
            return Optional.empty();
        }
        return findItineraryDay(companyId, proposalId, dayId);
    }

    public boolean deleteItineraryDay(UUID companyId, UUID proposalId, UUID dayId) {
        return jdbcTemplate.update(
            "delete from travel_itinerary_days where company_id = ? and proposal_id = ? and id = ?",
            companyId, proposalId, dayId) > 0;
    }

    /**
     * Re-materializa day_number sequencial 1..N na ORDEM recebida (lista de ids), na MESMA transação.
     * Ids inexistentes / de outra proposta são ignorados (UPDATE retorna 0). Devolve true se todos os
     * ids da lista pertenciam à proposta.
     */
    @Transactional
    public boolean reorderItinerary(UUID companyId, UUID proposalId, List<UUID> orderedIds) {
        int day = 1;
        boolean allFound = true;
        for (UUID dayId : orderedIds) {
            int n = jdbcTemplate.update(
                "update travel_itinerary_days set day_number = ?, updated_at = now() "
                    + "where company_id = ? and proposal_id = ? and id = ?",
                day, companyId, proposalId, dayId);
            if (n == 0) {
                allFound = false;
            } else {
                day++;
            }
        }
        return allFound;
    }
}
