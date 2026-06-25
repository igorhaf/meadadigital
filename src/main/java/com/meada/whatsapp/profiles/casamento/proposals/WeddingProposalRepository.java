package com.meada.whatsapp.profiles.casamento.proposals;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code wedding_proposals} + {@code wedding_proposal_items} + {@code wedding_timeline_items}
 * + {@code wedding_checklist_tasks} (camada 8.7). Opera via service_role. Espelho do
 * EventProposalRepository + a 3ª sub-entidade (checklist).
 *
 * <p>O {@code total_cents} da proposta e o {@code line_total_cents} de cada item de ORÇAMENTO são
 * MATERIALIZADOS: cada mutação de item de orçamento (add/update/delete) roda numa transação que grava
 * a linha e re-soma o total a partir do banco — nunca de um valor vindo de fora (lição end_at / total
 * chutado). Os marcos de CRONOGRAMA ({@code wedding_timeline_items}) e as tarefas de CHECKLIST
 * ({@code wedding_checklist_tasks}) NÃO entram no total.
 */
@Repository
public class WeddingProposalRepository {

    private final JdbcTemplate jdbcTemplate;

    public WeddingProposalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<WeddingProposalItem> ITEM_MAPPER = (rs, rn) -> new WeddingProposalItem(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("proposal_id"),
        rs.getString("description"),
        rs.getInt("quantity"),
        rs.getInt("unit_price_cents"),
        rs.getInt("line_total_cents"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private static final RowMapper<WeddingTimelineItem> TIMELINE_MAPPER = (rs, rn) -> new WeddingTimelineItem(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("proposal_id"),
        rs.getObject("start_time", LocalTime.class),
        rs.getString("title"),
        rs.getString("description"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private static final RowMapper<WeddingChecklistTask> CHECKLIST_MAPPER = (rs, rn) -> {
        Date due = rs.getDate("due_date");
        java.sql.Timestamp doneAt = rs.getTimestamp("done_at");
        return new WeddingChecklistTask(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("proposal_id"),
            rs.getString("title"),
            rs.getString("description"),
            due == null ? null : due.toLocalDate(),
            rs.getBoolean("done"),
            doneAt == null ? null : doneAt.toInstant());
    };

    private static final String PROPOSAL_SELECT =
        "select p.id, p.contact_id, p.planner_id, p.conversation_id, p.customer_name, p.customer_phone, "
            + "pl.name as planner_name, p.wedding_style, p.wedding_date, p.guest_count, p.briefing, "
            + "p.total_cents, p.status, p.notes, p.opened_at, p.closed_at, p.status_updated_at "
            + "from wedding_proposals p left join wedding_planners pl on pl.id = p.planner_id ";

    private WeddingProposal mapProposal(java.sql.ResultSet rs, List<WeddingProposalItem> items,
                                        List<WeddingTimelineItem> timeline,
                                        List<WeddingChecklistTask> checklist) throws java.sql.SQLException {
        Date wd = rs.getDate("wedding_date");
        java.sql.Timestamp closed = rs.getTimestamp("closed_at");
        int guests = rs.getInt("guest_count");
        Integer guestCount = rs.wasNull() ? null : guests;
        return new WeddingProposal(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("contact_id"),
            (UUID) rs.getObject("planner_id"),
            (UUID) rs.getObject("conversation_id"),
            rs.getString("customer_name"),
            rs.getString("customer_phone"),
            rs.getString("planner_name"),
            rs.getString("wedding_style"),
            wd == null ? null : wd.toLocalDate(),
            guestCount,
            rs.getString("briefing"),
            rs.getInt("total_cents"),
            rs.getString("status"),
            rs.getString("notes"),
            rs.getTimestamp("opened_at").toInstant(),
            closed == null ? null : closed.toInstant(),
            rs.getTimestamp("status_updated_at").toInstant(),
            items,
            timeline,
            checklist);
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

    public List<WeddingProposal> listByCompany(UUID companyId, String status, UUID plannerId, UUID contactId,
                                               LocalDate dateFrom, LocalDate dateTo, int limit, int offset) {
        StringBuilder sql = new StringBuilder(PROPOSAL_SELECT + "where p.company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) { sql.append(" and p.status = ?"); args.add(status); }
        if (plannerId != null) { sql.append(" and p.planner_id = ?"); args.add(plannerId); }
        if (contactId != null) { sql.append(" and p.contact_id = ?"); args.add(contactId); }
        if (dateFrom != null) { sql.append(" and p.wedding_date >= ?"); args.add(Date.valueOf(dateFrom)); }
        if (dateTo != null) { sql.append(" and p.wedding_date <= ?"); args.add(Date.valueOf(dateTo)); }
        sql.append(" order by p.opened_at desc limit ? offset ?");
        args.add(limit);
        args.add(offset);
        List<WeddingProposal> proposals = jdbcTemplate.query(sql.toString(),
            (rs, rn) -> mapProposal(rs, List.of(), List.of(), List.of()), args.toArray());
        List<WeddingProposal> hydrated = new ArrayList<>(proposals.size());
        for (WeddingProposal p : proposals) {
            hydrated.add(withChildren(p));
        }
        return hydrated;
    }

    public long countByCompany(UUID companyId, String status, UUID plannerId, UUID contactId,
                               LocalDate dateFrom, LocalDate dateTo) {
        StringBuilder sql = new StringBuilder("select count(*) from wedding_proposals where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) { sql.append(" and status = ?"); args.add(status); }
        if (plannerId != null) { sql.append(" and planner_id = ?"); args.add(plannerId); }
        if (contactId != null) { sql.append(" and contact_id = ?"); args.add(contactId); }
        if (dateFrom != null) { sql.append(" and wedding_date >= ?"); args.add(Date.valueOf(dateFrom)); }
        if (dateTo != null) { sql.append(" and wedding_date <= ?"); args.add(Date.valueOf(dateTo)); }
        Long n = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0L : n;
    }

    public Optional<WeddingProposal> findById(UUID companyId, UUID id) {
        Optional<WeddingProposal> base = jdbcTemplate.query(PROPOSAL_SELECT + "where p.company_id = ? and p.id = ?",
                (rs, rn) -> mapProposal(rs, List.of(), List.of(), List.of()), companyId, id)
            .stream().findFirst();
        return base.map(this::withChildren);
    }

    private WeddingProposal withChildren(WeddingProposal p) {
        return new WeddingProposal(p.id(), p.contactId(), p.plannerId(), p.conversationId(),
            p.customerName(), p.customerPhone(), p.plannerName(), p.weddingStyle(), p.weddingDate(),
            p.guestCount(), p.briefing(), p.totalCents(), p.status(), p.notes(),
            p.openedAt(), p.closedAt(), p.statusUpdatedAt(),
            listItems(p.id()), listTimeline(p.id()), listChecklist(p.id()));
    }

    /**
     * Abre a proposta (status 'rascunho', total 0). Snapshots de cliente (name/phone do contact).
     * plannerId/conversationId/weddingStyle/weddingDate/guestCount/briefing opcionais.
     */
    public WeddingProposal insertProposal(UUID companyId, UUID contactId, String customerName,
                                          String customerPhone, UUID plannerId, UUID conversationId,
                                          String weddingStyle, LocalDate weddingDate, Integer guestCount,
                                          String briefing, String notes) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into wedding_proposals (company_id, contact_id, planner_id, conversation_id, "
                + "customer_name, customer_phone, wedding_style, wedding_date, guest_count, briefing, "
                + "notes, total_cents, status) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 'rascunho') returning id",
            UUID.class, companyId, contactId, plannerId, conversationId, customerName, customerPhone,
            weddingStyle, weddingDate == null ? null : Date.valueOf(weddingDate), guestCount, briefing, notes);
        return findById(companyId, id).orElseThrow();
    }

    /** Atualiza campos editáveis da proposta (planner/weddingStyle/weddingDate/guestCount/briefing/notes). */
    public Optional<WeddingProposal> updateFields(UUID companyId, UUID id, UUID plannerId, boolean plannerProvided,
                                                  String weddingStyle, LocalDate weddingDate, boolean dateProvided,
                                                  Integer guestCount, boolean guestProvided, String briefing,
                                                  String notes) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (plannerProvided) { sets.add("planner_id = ?"); args.add(plannerId); }
        if (weddingStyle != null) { sets.add("wedding_style = ?"); args.add(weddingStyle); }
        if (dateProvided) { sets.add("wedding_date = ?"); args.add(weddingDate == null ? null : Date.valueOf(weddingDate)); }
        if (guestProvided) { sets.add("guest_count = ?"); args.add(guestCount); }
        if (briefing != null) { sets.add("briefing = ?"); args.add(briefing); }
        if (notes != null) { sets.add("notes = ?"); args.add(notes); }
        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update("update wedding_proposals set " + String.join(", ", sets)
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
            jdbcTemplate.update("update wedding_proposals set status = ?, status_updated_at = now(), "
                + "closed_at = now(), updated_at = now() where company_id = ? and id = ?",
                newStatus, companyId, id);
        } else {
            jdbcTemplate.update("update wedding_proposals set status = ?, status_updated_at = now(), "
                + "updated_at = now() where company_id = ? and id = ?",
                newStatus, companyId, id);
        }
    }

    // -------------------------------------------------------------------------
    // ITENS DE ORÇAMENTO — cada mutação recalcula o total_cents da proposta na MESMA transação.
    // -------------------------------------------------------------------------

    public List<WeddingProposalItem> listItems(UUID proposalId) {
        return jdbcTemplate.query(
            "select id, proposal_id, description, quantity, unit_price_cents, line_total_cents, "
                + "created_at, updated_at from wedding_proposal_items where proposal_id = ? order by created_at asc",
            ITEM_MAPPER, proposalId);
    }

    public Optional<WeddingProposalItem> findItem(UUID companyId, UUID proposalId, UUID itemId) {
        return jdbcTemplate.query(
            "select id, proposal_id, description, quantity, unit_price_cents, line_total_cents, "
                + "created_at, updated_at from wedding_proposal_items where company_id = ? and proposal_id = ? and id = ?",
            ITEM_MAPPER, companyId, proposalId, itemId).stream().findFirst();
    }

    @Transactional
    public WeddingProposalItem addItem(UUID companyId, UUID proposalId, String description,
                                       int quantity, int unitPriceCents) {
        int lineTotal = quantity * unitPriceCents;
        UUID id = jdbcTemplate.queryForObject(
            "insert into wedding_proposal_items (company_id, proposal_id, description, quantity, "
                + "unit_price_cents, line_total_cents) values (?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, proposalId, description.trim(), quantity, unitPriceCents, lineTotal);
        recalcTotal(companyId, proposalId);
        return findItem(companyId, proposalId, id).orElseThrow();
    }

    @Transactional
    public Optional<WeddingProposalItem> updateItem(UUID companyId, UUID proposalId, UUID itemId,
                                                    String description, Integer quantity, Integer unitPriceCents) {
        // Lê o item atual (valores OLD) para resolver os campos não informados (null = mantém) e
        // materializar line_total a partir dos valores FINAIS. (Em Postgres, um SET que referencia
        // quantity*unit_price usaria os valores ANTIGOS da linha — por isso calculamos em Java.)
        Optional<WeddingProposalItem> current = findItem(companyId, proposalId, itemId);
        if (current.isEmpty()) {
            return Optional.empty();
        }
        WeddingProposalItem old = current.get();
        String finalDesc = (description != null && !description.isBlank()) ? description.trim() : old.description();
        int finalQty = quantity != null ? quantity : old.quantity();
        int finalUnit = unitPriceCents != null ? unitPriceCents : old.unitPriceCents();
        int finalLineTotal = finalQty * finalUnit;
        int n = jdbcTemplate.update(
            "update wedding_proposal_items set description = ?, quantity = ?, unit_price_cents = ?, "
                + "line_total_cents = ?, updated_at = now() "
                + "where company_id = ? and proposal_id = ? and id = ?",
            finalDesc, finalQty, finalUnit, finalLineTotal, companyId, proposalId, itemId);
        if (n == 0) {
            return Optional.empty();
        }
        recalcTotal(companyId, proposalId);
        return findItem(companyId, proposalId, itemId);
    }

    @Transactional
    public boolean deleteItem(UUID companyId, UUID proposalId, UUID itemId) {
        int n = jdbcTemplate.update("delete from wedding_proposal_items where company_id = ? and proposal_id = ? and id = ?",
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
            "update wedding_proposals set total_cents = coalesce("
                + "(select sum(line_total_cents) from wedding_proposal_items where proposal_id = ?), 0), "
                + "updated_at = now() where company_id = ? and id = ?",
            proposalId, companyId, proposalId);
    }

    // -------------------------------------------------------------------------
    // MARCOS DE CRONOGRAMA — NÃO recalculam total. Ordenados por start_time na leitura.
    // -------------------------------------------------------------------------

    public List<WeddingTimelineItem> listTimeline(UUID proposalId) {
        return jdbcTemplate.query(
            "select id, proposal_id, start_time, title, description, created_at, updated_at "
                + "from wedding_timeline_items where proposal_id = ? order by start_time asc, created_at asc",
            TIMELINE_MAPPER, proposalId);
    }

    public Optional<WeddingTimelineItem> findTimelineItem(UUID companyId, UUID proposalId, UUID itemId) {
        return jdbcTemplate.query(
            "select id, proposal_id, start_time, title, description, created_at, updated_at "
                + "from wedding_timeline_items where company_id = ? and proposal_id = ? and id = ?",
            TIMELINE_MAPPER, companyId, proposalId, itemId).stream().findFirst();
    }

    public WeddingTimelineItem addTimelineItem(UUID companyId, UUID proposalId, LocalTime startTime,
                                               String title, String description) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into wedding_timeline_items (company_id, proposal_id, start_time, title, description) "
                + "values (?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, proposalId, Time.valueOf(startTime), title.trim(), description);
        return findTimelineItem(companyId, proposalId, id).orElseThrow();
    }

    public Optional<WeddingTimelineItem> updateTimelineItem(UUID companyId, UUID proposalId, UUID itemId,
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
        int n = jdbcTemplate.update("update wedding_timeline_items set " + String.join(", ", sets)
            + " where company_id = ? and proposal_id = ? and id = ?", args.toArray());
        if (n == 0) {
            return Optional.empty();
        }
        return findTimelineItem(companyId, proposalId, itemId);
    }

    public boolean deleteTimelineItem(UUID companyId, UUID proposalId, UUID itemId) {
        return jdbcTemplate.update(
            "delete from wedding_timeline_items where company_id = ? and proposal_id = ? and id = ?",
            companyId, proposalId, itemId) > 0;
    }

    // -------------------------------------------------------------------------
    // CHECKLIST PRÉ-CASAMENTO (a escapada) — NÃO recalcula total. Ordenado por due_date asc NULLS
    // LAST, created_at asc (tarefa sem prazo vai ao fim). Estado BINÁRIO (done).
    // -------------------------------------------------------------------------

    public List<WeddingChecklistTask> listChecklist(UUID proposalId) {
        return jdbcTemplate.query(
            "select id, proposal_id, title, description, due_date, done, done_at "
                + "from wedding_checklist_tasks where proposal_id = ? "
                + "order by due_date asc nulls last, created_at asc",
            CHECKLIST_MAPPER, proposalId);
    }

    public Optional<WeddingChecklistTask> findChecklistTask(UUID companyId, UUID taskId) {
        return jdbcTemplate.query(
            "select id, proposal_id, title, description, due_date, done, done_at "
                + "from wedding_checklist_tasks where company_id = ? and id = ?",
            CHECKLIST_MAPPER, companyId, taskId).stream().findFirst();
    }

    public Optional<WeddingChecklistTask> findChecklistTask(UUID companyId, UUID proposalId, UUID taskId) {
        return jdbcTemplate.query(
            "select id, proposal_id, title, description, due_date, done, done_at "
                + "from wedding_checklist_tasks where company_id = ? and proposal_id = ? and id = ?",
            CHECKLIST_MAPPER, companyId, proposalId, taskId).stream().findFirst();
    }

    /** Adiciona uma tarefa de checklist: done default false (pendente), done_at null. */
    public WeddingChecklistTask addChecklistTask(UUID companyId, UUID proposalId, String title,
                                                 String description, LocalDate dueDate) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into wedding_checklist_tasks (company_id, proposal_id, title, description, due_date, done) "
                + "values (?, ?, ?, ?, ?, false) returning id",
            UUID.class, companyId, proposalId, title.trim(), description,
            dueDate == null ? null : Date.valueOf(dueDate));
        return findChecklistTask(companyId, proposalId, id).orElseThrow();
    }

    public Optional<WeddingChecklistTask> updateChecklistTask(UUID companyId, UUID proposalId, UUID taskId,
                                                              String title, String description, boolean descProvided,
                                                              LocalDate dueDate, boolean dueProvided) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (title != null && !title.isBlank()) { sets.add("title = ?"); args.add(title.trim()); }
        if (descProvided) { sets.add("description = ?"); args.add(description); }
        if (dueProvided) { sets.add("due_date = ?"); args.add(dueDate == null ? null : Date.valueOf(dueDate)); }
        if (sets.isEmpty()) {
            return findChecklistTask(companyId, proposalId, taskId);
        }
        sets.add("updated_at = now()");
        args.add(companyId);
        args.add(proposalId);
        args.add(taskId);
        int n = jdbcTemplate.update("update wedding_checklist_tasks set " + String.join(", ", sets)
            + " where company_id = ? and proposal_id = ? and id = ?", args.toArray());
        if (n == 0) {
            return Optional.empty();
        }
        return findChecklistTask(companyId, proposalId, taskId);
    }

    public boolean deleteChecklistTask(UUID companyId, UUID proposalId, UUID taskId) {
        return jdbcTemplate.update(
            "delete from wedding_checklist_tasks where company_id = ? and proposal_id = ? and id = ?",
            companyId, proposalId, taskId) > 0;
    }

    /**
     * Marca/desmarca a tarefa (estado BINÁRIO): done=true grava done_at = now(); done=false zera
     * done_at (null). Sem máquina de status (pendente⇄concluída livre). Escopo por company.
     */
    public Optional<WeddingChecklistTask> toggleChecklistTask(UUID companyId, UUID taskId, boolean done) {
        int n;
        if (done) {
            n = jdbcTemplate.update(
                "update wedding_checklist_tasks set done = true, done_at = now(), updated_at = now() "
                    + "where company_id = ? and id = ?", companyId, taskId);
        } else {
            n = jdbcTemplate.update(
                "update wedding_checklist_tasks set done = false, done_at = null, updated_at = now() "
                    + "where company_id = ? and id = ?", companyId, taskId);
        }
        return n == 0 ? Optional.empty() : findChecklistTask(companyId, taskId);
    }
}
