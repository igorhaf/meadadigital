package com.meada.profiles.eventos.proposals;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Time;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code event_proposals} + {@code event_proposal_items} + {@code event_timeline_items}
 * (camada 8.2). Opera via service_role.
 *
 * <p>O {@code total_cents} da proposta e o {@code line_total_cents} de cada item de ORÇAMENTO são
 * MATERIALIZADOS: cada mutação de item de orçamento (add/update/delete) roda numa transação que
 * grava a linha e re-soma o total a partir do banco — nunca de um valor vindo de fora (lição
 * end_at / total chutado). Os marcos de CRONOGRAMA ({@code event_timeline_items}) NÃO entram no
 * total — são ordenados por start_time na leitura.
 */
@Repository
public class EventProposalRepository {

    private final JdbcTemplate jdbcTemplate;

    public EventProposalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<EventProposalItem> ITEM_MAPPER = (rs, rn) -> new EventProposalItem(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("proposal_id"),
        rs.getString("description"),
        rs.getInt("quantity"),
        rs.getInt("unit_price_cents"),
        rs.getInt("line_total_cents"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private static final RowMapper<EventTimelineItem> TIMELINE_MAPPER = (rs, rn) -> new EventTimelineItem(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("proposal_id"),
        rs.getObject("start_time", LocalTime.class),
        rs.getString("title"),
        rs.getString("description"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private static final String PROPOSAL_SELECT =
        "select p.id, p.contact_id, p.planner_id, p.conversation_id, p.customer_name, p.customer_phone, "
            + "pl.name as planner_name, p.event_type, p.event_date, p.guest_count, p.briefing, "
            + "p.total_cents, p.status, p.notes, p.opened_at, p.closed_at, p.status_updated_at "
            + "from event_proposals p left join event_planners pl on pl.id = p.planner_id ";

    private EventProposal mapProposal(java.sql.ResultSet rs, List<EventProposalItem> items,
                                      List<EventTimelineItem> timeline) throws java.sql.SQLException {
        Date ed = rs.getDate("event_date");
        java.sql.Timestamp closed = rs.getTimestamp("closed_at");
        int guests = rs.getInt("guest_count");
        Integer guestCount = rs.wasNull() ? null : guests;
        return new EventProposal(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("contact_id"),
            (UUID) rs.getObject("planner_id"),
            (UUID) rs.getObject("conversation_id"),
            rs.getString("customer_name"),
            rs.getString("customer_phone"),
            rs.getString("planner_name"),
            rs.getString("event_type"),
            ed == null ? null : ed.toLocalDate(),
            guestCount,
            rs.getString("briefing"),
            rs.getInt("total_cents"),
            rs.getString("status"),
            rs.getString("notes"),
            rs.getTimestamp("opened_at").toInstant(),
            closed == null ? null : closed.toInstant(),
            rs.getTimestamp("status_updated_at").toInstant(),
            items,
            timeline);
    }

    // -------------------------------------------------------------------------
    // Snapshots de cliente (do contact da conversa/informado) — não há sub-entidade de veículo.
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

    public List<EventProposal> listByCompany(UUID companyId, String status, UUID plannerId, UUID contactId,
                                             LocalDate dateFrom, LocalDate dateTo, int limit, int offset) {
        StringBuilder sql = new StringBuilder(PROPOSAL_SELECT + "where p.company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) { sql.append(" and p.status = ?"); args.add(status); }
        if (plannerId != null) { sql.append(" and p.planner_id = ?"); args.add(plannerId); }
        if (contactId != null) { sql.append(" and p.contact_id = ?"); args.add(contactId); }
        if (dateFrom != null) { sql.append(" and p.event_date >= ?"); args.add(Date.valueOf(dateFrom)); }
        if (dateTo != null) { sql.append(" and p.event_date <= ?"); args.add(Date.valueOf(dateTo)); }
        sql.append(" order by p.opened_at desc limit ? offset ?");
        args.add(limit);
        args.add(offset);
        List<EventProposal> proposals = jdbcTemplate.query(sql.toString(),
            (rs, rn) -> mapProposal(rs, List.of(), List.of()), args.toArray());
        List<EventProposal> hydrated = new ArrayList<>(proposals.size());
        for (EventProposal p : proposals) {
            hydrated.add(withChildren(p));
        }
        return hydrated;
    }

    public long countByCompany(UUID companyId, String status, UUID plannerId, UUID contactId,
                               LocalDate dateFrom, LocalDate dateTo) {
        StringBuilder sql = new StringBuilder("select count(*) from event_proposals where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) { sql.append(" and status = ?"); args.add(status); }
        if (plannerId != null) { sql.append(" and planner_id = ?"); args.add(plannerId); }
        if (contactId != null) { sql.append(" and contact_id = ?"); args.add(contactId); }
        if (dateFrom != null) { sql.append(" and event_date >= ?"); args.add(Date.valueOf(dateFrom)); }
        if (dateTo != null) { sql.append(" and event_date <= ?"); args.add(Date.valueOf(dateTo)); }
        Long n = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0L : n;
    }

    public Optional<EventProposal> findById(UUID companyId, UUID id) {
        Optional<EventProposal> base = jdbcTemplate.query(PROPOSAL_SELECT + "where p.company_id = ? and p.id = ?",
                (rs, rn) -> mapProposal(rs, List.of(), List.of()), companyId, id)
            .stream().findFirst();
        return base.map(this::withChildren);
    }

    private EventProposal withChildren(EventProposal p) {
        return new EventProposal(p.id(), p.contactId(), p.plannerId(), p.conversationId(),
            p.customerName(), p.customerPhone(), p.plannerName(), p.eventType(), p.eventDate(),
            p.guestCount(), p.briefing(), p.totalCents(), p.status(), p.notes(),
            p.openedAt(), p.closedAt(), p.statusUpdatedAt(), listItems(p.id()), listTimeline(p.id()));
    }

    /**
     * Abre a proposta (status 'rascunho', total 0). Snapshots de cliente (name/phone do contact).
     * plannerId/conversationId/eventType/eventDate/guestCount/briefing opcionais.
     */
    public EventProposal insertProposal(UUID companyId, UUID contactId, String customerName,
                                        String customerPhone, UUID plannerId, UUID conversationId,
                                        String eventType, LocalDate eventDate, Integer guestCount,
                                        String briefing, String notes) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into event_proposals (company_id, contact_id, planner_id, conversation_id, "
                + "customer_name, customer_phone, event_type, event_date, guest_count, briefing, "
                + "notes, total_cents, status) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 'rascunho') returning id",
            UUID.class, companyId, contactId, plannerId, conversationId, customerName, customerPhone,
            eventType, eventDate == null ? null : Date.valueOf(eventDate), guestCount, briefing, notes);
        return findById(companyId, id).orElseThrow();
    }

    /** Atualiza campos editáveis da proposta (planner/eventType/eventDate/guestCount/briefing/notes). */
    public Optional<EventProposal> updateFields(UUID companyId, UUID id, UUID plannerId, boolean plannerProvided,
                                                String eventType, LocalDate eventDate, boolean dateProvided,
                                                Integer guestCount, boolean guestProvided, String briefing,
                                                String notes) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (plannerProvided) { sets.add("planner_id = ?"); args.add(plannerId); }
        if (eventType != null) { sets.add("event_type = ?"); args.add(eventType); }
        if (dateProvided) { sets.add("event_date = ?"); args.add(eventDate == null ? null : Date.valueOf(eventDate)); }
        if (guestProvided) { sets.add("guest_count = ?"); args.add(guestCount); }
        if (briefing != null) { sets.add("briefing = ?"); args.add(briefing); }
        if (notes != null) { sets.add("notes = ?"); args.add(notes); }
        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update("update event_proposals set " + String.join(", ", sets)
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
    /** Propostas aprovada/fechada/realizada na data (onda 1, backlog #3 — aviso de ocupação). */
    public long countByEventDate(UUID companyId, java.time.LocalDate date, UUID excludeId) {
        StringBuilder sql = new StringBuilder(
            "select count(*) from event_proposals where company_id = ? and event_date = ? "
                + "and status in ('aprovada','fechada','realizada')");
        java.util.List<Object> args = new java.util.ArrayList<>();
        args.add(companyId);
        args.add(java.sql.Date.valueOf(date));
        if (excludeId != null) {
            sql.append(" and id <> ?");
            args.add(excludeId);
        }
        Long n = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0L : n;
    }

    public void updateStatus(UUID companyId, UUID id, String newStatus, boolean terminal) {
        if (terminal) {
            jdbcTemplate.update("update event_proposals set status = ?, status_updated_at = now(), "
                + "closed_at = now(), updated_at = now() where company_id = ? and id = ?",
                newStatus, companyId, id);
        } else {
            jdbcTemplate.update("update event_proposals set status = ?, status_updated_at = now(), "
                + "updated_at = now() where company_id = ? and id = ?",
                newStatus, companyId, id);
        }
    }

    // -------------------------------------------------------------------------
    // ITENS DE ORÇAMENTO — cada mutação recalcula o total_cents da proposta na MESMA transação.
    // -------------------------------------------------------------------------

    public List<EventProposalItem> listItems(UUID proposalId) {
        return jdbcTemplate.query(
            "select id, proposal_id, description, quantity, unit_price_cents, line_total_cents, "
                + "created_at, updated_at from event_proposal_items where proposal_id = ? order by created_at asc",
            ITEM_MAPPER, proposalId);
    }

    public Optional<EventProposalItem> findItem(UUID companyId, UUID proposalId, UUID itemId) {
        return jdbcTemplate.query(
            "select id, proposal_id, description, quantity, unit_price_cents, line_total_cents, "
                + "created_at, updated_at from event_proposal_items where company_id = ? and proposal_id = ? and id = ?",
            ITEM_MAPPER, companyId, proposalId, itemId).stream().findFirst();
    }

    @Transactional
    public EventProposalItem addItem(UUID companyId, UUID proposalId, String description,
                                     int quantity, int unitPriceCents) {
        int lineTotal = quantity * unitPriceCents;
        UUID id = jdbcTemplate.queryForObject(
            "insert into event_proposal_items (company_id, proposal_id, description, quantity, "
                + "unit_price_cents, line_total_cents) values (?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, proposalId, description.trim(), quantity, unitPriceCents, lineTotal);
        recalcTotal(companyId, proposalId);
        return findItem(companyId, proposalId, id).orElseThrow();
    }

    @Transactional
    public Optional<EventProposalItem> updateItem(UUID companyId, UUID proposalId, UUID itemId,
                                                  String description, Integer quantity, Integer unitPriceCents) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
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
        int n = jdbcTemplate.update("update event_proposal_items set " + String.join(", ", sets)
            + " where company_id = ? and proposal_id = ? and id = ?", args.toArray());
        if (n == 0) {
            return Optional.empty();
        }
        recalcTotal(companyId, proposalId);
        return findItem(companyId, proposalId, itemId);
    }

    @Transactional
    public boolean deleteItem(UUID companyId, UUID proposalId, UUID itemId) {
        int n = jdbcTemplate.update("delete from event_proposal_items where company_id = ? and proposal_id = ? and id = ?",
            companyId, proposalId, itemId);
        if (n == 0) {
            return false;
        }
        recalcTotal(companyId, proposalId);
        return true;
    }

    /** Re-soma o total da proposta a partir das linhas de ORÇAMENTO (materializa o derivado). */
    private void recalcTotal(UUID companyId, UUID proposalId) {
        jdbcTemplate.update(
            "update event_proposals set total_cents = coalesce("
                + "(select sum(line_total_cents) from event_proposal_items where proposal_id = ?), 0), "
                + "updated_at = now() where company_id = ? and id = ?",
            proposalId, companyId, proposalId);
    }

    // -------------------------------------------------------------------------
    // MARCOS DE CRONOGRAMA — NÃO recalculam total. Ordenados por start_time na leitura.
    // -------------------------------------------------------------------------

    public List<EventTimelineItem> listTimeline(UUID proposalId) {
        return jdbcTemplate.query(
            "select id, proposal_id, start_time, title, description, created_at, updated_at "
                + "from event_timeline_items where proposal_id = ? order by start_time asc, created_at asc",
            TIMELINE_MAPPER, proposalId);
    }

    public Optional<EventTimelineItem> findTimelineItem(UUID companyId, UUID proposalId, UUID itemId) {
        return jdbcTemplate.query(
            "select id, proposal_id, start_time, title, description, created_at, updated_at "
                + "from event_timeline_items where company_id = ? and proposal_id = ? and id = ?",
            TIMELINE_MAPPER, companyId, proposalId, itemId).stream().findFirst();
    }

    public EventTimelineItem addTimelineItem(UUID companyId, UUID proposalId, LocalTime startTime,
                                             String title, String description) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into event_timeline_items (company_id, proposal_id, start_time, title, description) "
                + "values (?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, proposalId, Time.valueOf(startTime), title.trim(), description);
        return findTimelineItem(companyId, proposalId, id).orElseThrow();
    }

    public Optional<EventTimelineItem> updateTimelineItem(UUID companyId, UUID proposalId, UUID itemId,
                                                          LocalTime startTime, boolean timeProvided,
                                                          String title, String description, boolean descProvided) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (timeProvided && startTime != null) { sets.add("start_time = ?"); args.add(Time.valueOf(startTime)); }
        if (title != null && !title.isBlank()) { sets.add("title = ?"); args.add(title.trim()); }
        if (descProvided) { sets.add("description = ?"); args.add(description); }
        if (sets.isEmpty()) {
            return findTimelineItem(companyId, proposalId, itemId);
        }
        sets.add("updated_at = now()");
        args.add(companyId);
        args.add(proposalId);
        args.add(itemId);
        int n = jdbcTemplate.update("update event_timeline_items set " + String.join(", ", sets)
            + " where company_id = ? and proposal_id = ? and id = ?", args.toArray());
        if (n == 0) {
            return Optional.empty();
        }
        return findTimelineItem(companyId, proposalId, itemId);
    }

    public boolean deleteTimelineItem(UUID companyId, UUID proposalId, UUID itemId) {
        return jdbcTemplate.update(
            "delete from event_timeline_items where company_id = ? and proposal_id = ? and id = ?",
            companyId, proposalId, itemId) > 0;
    }
}
