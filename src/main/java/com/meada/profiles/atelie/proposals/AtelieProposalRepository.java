package com.meada.profiles.atelie.proposals;

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
 * Acesso a {@code atelie_proposals} + {@code atelie_proposal_items} + {@code atelie_fittings}
 * (camada 8.14). Opera via service_role. Espelho do EventProposalRepository.
 *
 * <p>O {@code total_cents} da proposta e o {@code line_total_cents} de cada item de ORÇAMENTO são
 * MATERIALIZADOS: cada mutação de item de orçamento (add/update/delete) roda numa transação que
 * grava a linha e re-soma o total a partir do banco — nunca de um valor vindo de fora (lição
 * end_at / total chutado). As provas/ajustes ({@code atelie_fittings}) NÃO entram no total — são
 * ordenadas por {@code position} na leitura.
 */
@Repository
public class AtelieProposalRepository {

    private final JdbcTemplate jdbcTemplate;

    public AtelieProposalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<AtelieProposalItem> ITEM_MAPPER = (rs, rn) -> new AtelieProposalItem(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("proposal_id"),
        rs.getString("description"),
        rs.getInt("quantity"),
        rs.getInt("unit_price_cents"),
        rs.getInt("line_total_cents"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private static final RowMapper<AtelieFitting> FITTING_MAPPER = (rs, rn) -> {
        Date due = rs.getDate("due_date");
        java.sql.Timestamp completed = rs.getTimestamp("completed_at");
        return new AtelieFitting(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("proposal_id"),
            rs.getString("title"),
            rs.getString("description"),
            due == null ? null : due.toLocalDate(),
            rs.getString("status"),
            rs.getInt("position"),
            completed == null ? null : completed.toInstant(),
            rs.getTimestamp("confirmed_at") == null ? null : rs.getTimestamp("confirmed_at").toInstant(),
            rs.getDate("confirmed_due_date") == null ? null : rs.getDate("confirmed_due_date").toLocalDate());
    };

    private static final String PROPOSAL_SELECT =
        "select p.id, p.contact_id, p.artisan_id, p.conversation_id, p.customer_name, p.customer_phone, "
            + "ar.name as artisan_name, p.project_type, p.occasion, p.briefing, p.estimated_date, "
            + "p.total_cents, p.discount_cents, p.coupon_id, p.coupon_code_snapshot, "
            + "p.status, p.notes, p.deposit_cents, p.deposit_paid, p.deposit_paid_at, "
            + "p.opened_at, p.closed_at, p.status_updated_at "
            + "from atelie_proposals p left join atelie_artisans ar on ar.id = p.artisan_id ";

    private AtelieProposal mapProposal(java.sql.ResultSet rs, List<AtelieProposalItem> items,
                                       List<AtelieFitting> fittings) throws java.sql.SQLException {
        Date ed = rs.getDate("estimated_date");
        java.sql.Timestamp closed = rs.getTimestamp("closed_at");
        Integer depositCents = rs.getObject("deposit_cents") == null ? null : rs.getInt("deposit_cents");
        java.sql.Timestamp depositPaidAt = rs.getTimestamp("deposit_paid_at");
        return new AtelieProposal(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("contact_id"),
            (UUID) rs.getObject("artisan_id"),
            (UUID) rs.getObject("conversation_id"),
            rs.getString("customer_name"),
            rs.getString("customer_phone"),
            rs.getString("artisan_name"),
            rs.getString("project_type"),
            rs.getString("occasion"),
            rs.getString("briefing"),
            ed == null ? null : ed.toLocalDate(),
            rs.getInt("total_cents"),
            rs.getInt("discount_cents"),
            (UUID) rs.getObject("coupon_id"),
            rs.getString("coupon_code_snapshot"),
            rs.getString("status"),
            rs.getString("notes"),
            depositCents,
            rs.getBoolean("deposit_paid"),
            depositPaidAt == null ? null : depositPaidAt.toInstant(),
            rs.getTimestamp("opened_at").toInstant(),
            closed == null ? null : closed.toInstant(),
            rs.getTimestamp("status_updated_at").toInstant(),
            items,
            fittings);
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

    public List<AtelieProposal> listByCompany(UUID companyId, String status, UUID artisanId, UUID contactId,
                                              String projectType, int limit, int offset) {
        StringBuilder sql = new StringBuilder(PROPOSAL_SELECT + "where p.company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) { sql.append(" and p.status = ?"); args.add(status); }
        if (artisanId != null) { sql.append(" and p.artisan_id = ?"); args.add(artisanId); }
        if (contactId != null) { sql.append(" and p.contact_id = ?"); args.add(contactId); }
        if (projectType != null && !projectType.isBlank()) { sql.append(" and p.project_type = ?"); args.add(projectType); }
        sql.append(" order by p.opened_at desc limit ? offset ?");
        args.add(limit);
        args.add(offset);
        List<AtelieProposal> proposals = jdbcTemplate.query(sql.toString(),
            (rs, rn) -> mapProposal(rs, List.of(), List.of()), args.toArray());
        List<AtelieProposal> hydrated = new ArrayList<>(proposals.size());
        for (AtelieProposal p : proposals) {
            hydrated.add(withChildren(p));
        }
        return hydrated;
    }

    public long countByCompany(UUID companyId, String status, UUID artisanId, UUID contactId, String projectType) {
        StringBuilder sql = new StringBuilder("select count(*) from atelie_proposals where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) { sql.append(" and status = ?"); args.add(status); }
        if (artisanId != null) { sql.append(" and artisan_id = ?"); args.add(artisanId); }
        if (contactId != null) { sql.append(" and contact_id = ?"); args.add(contactId); }
        if (projectType != null && !projectType.isBlank()) { sql.append(" and project_type = ?"); args.add(projectType); }
        Long n = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0L : n;
    }

    public Optional<AtelieProposal> findById(UUID companyId, UUID id) {
        Optional<AtelieProposal> base = jdbcTemplate.query(PROPOSAL_SELECT + "where p.company_id = ? and p.id = ?",
                (rs, rn) -> mapProposal(rs, List.of(), List.of()), companyId, id)
            .stream().findFirst();
        return base.map(this::withChildren);
    }

    private AtelieProposal withChildren(AtelieProposal p) {
        return new AtelieProposal(p.id(), p.contactId(), p.artisanId(), p.conversationId(),
            p.customerName(), p.customerPhone(), p.artisanName(), p.projectType(), p.occasion(),
            p.briefing(), p.estimatedDate(), p.totalCents(),
            p.discountCents(), p.couponId(), p.couponCodeSnapshot(), p.status(), p.notes(),
            p.depositCents(), p.depositPaid(), p.depositPaidAt(),
            p.openedAt(), p.closedAt(), p.statusUpdatedAt(), listItems(p.id()), listFittings(p.id()));
    }

    /**
     * Abre a proposta (status 'rascunho', total 0). Snapshots de cliente (name/phone do contact).
     * artisanId/conversationId/projectType/occasion/estimatedDate/briefing opcionais.
     */
    public AtelieProposal insertProposal(UUID companyId, UUID contactId, String customerName,
                                         String customerPhone, UUID artisanId, UUID conversationId,
                                         String projectType, String occasion, LocalDate estimatedDate,
                                         String briefing, String notes) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into atelie_proposals (company_id, contact_id, artisan_id, conversation_id, "
                + "customer_name, customer_phone, project_type, occasion, estimated_date, briefing, "
                + "notes, total_cents, status) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 'rascunho') returning id",
            UUID.class, companyId, contactId, artisanId, conversationId, customerName, customerPhone,
            projectType, occasion, estimatedDate == null ? null : Date.valueOf(estimatedDate), briefing, notes);
        return findById(companyId, id).orElseThrow();
    }

    /** Atualiza campos editáveis da proposta (artisan/projectType/occasion/estimatedDate/briefing/notes). */
    public Optional<AtelieProposal> updateFields(UUID companyId, UUID id, UUID artisanId, boolean artisanProvided,
                                                 String projectType, String occasion, LocalDate estimatedDate,
                                                 boolean dateProvided, String briefing, String notes) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (artisanProvided) { sets.add("artisan_id = ?"); args.add(artisanId); }
        if (projectType != null) { sets.add("project_type = ?"); args.add(projectType); }
        if (occasion != null) { sets.add("occasion = ?"); args.add(occasion); }
        if (dateProvided) { sets.add("estimated_date = ?"); args.add(estimatedDate == null ? null : Date.valueOf(estimatedDate)); }
        if (briefing != null) { sets.add("briefing = ?"); args.add(briefing); }
        if (notes != null) { sets.add("notes = ?"); args.add(notes); }
        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update("update atelie_proposals set " + String.join(", ", sets)
                + " where company_id = ? and id = ?", args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    /**
     * Grava o SINAL da proposta (onda backlog #2): valor combinado + marcação de pago. deposit_paid_at
     * é materializado aqui — preservado enquanto pago (não re-carimba) e zerado ao desmarcar.
     */
    public Optional<AtelieProposal> updateDeposit(UUID companyId, UUID id, Integer depositCents, boolean depositPaid) {
        int n = jdbcTemplate.update(
            "update atelie_proposals set deposit_cents = ?, deposit_paid = ?, "
                + "deposit_paid_at = case when ? then coalesce(deposit_paid_at, now()) end, "
                + "updated_at = now() where company_id = ? and id = ?",
            depositCents, depositPaid, depositPaid, companyId, id);
        if (n == 0) {
            return Optional.empty();
        }
        return findById(companyId, id);
    }

    /**
     * Persiste a transição de status + status_updated_at. Preenche closed_at em terminais
     * (realizada/recusada/cancelada). Service já validou a transição.
     */
    public void updateStatus(UUID companyId, UUID id, String newStatus, boolean terminal) {
        if (terminal) {
            jdbcTemplate.update("update atelie_proposals set status = ?, status_updated_at = now(), "
                + "closed_at = now(), updated_at = now() where company_id = ? and id = ?",
                newStatus, companyId, id);
        } else {
            jdbcTemplate.update("update atelie_proposals set status = ?, status_updated_at = now(), "
                + "updated_at = now() where company_id = ? and id = ?",
                newStatus, companyId, id);
        }
    }

    // -------------------------------------------------------------------------
    // ITENS DE ORÇAMENTO — cada mutação recalcula o total_cents da proposta na MESMA transação.
    // -------------------------------------------------------------------------

    public List<AtelieProposalItem> listItems(UUID proposalId) {
        return jdbcTemplate.query(
            "select id, proposal_id, description, quantity, unit_price_cents, line_total_cents, "
                + "created_at, updated_at from atelie_proposal_items where proposal_id = ? order by created_at asc",
            ITEM_MAPPER, proposalId);
    }

    public Optional<AtelieProposalItem> findItem(UUID companyId, UUID proposalId, UUID itemId) {
        return jdbcTemplate.query(
            "select id, proposal_id, description, quantity, unit_price_cents, line_total_cents, "
                + "created_at, updated_at from atelie_proposal_items where company_id = ? and proposal_id = ? and id = ?",
            ITEM_MAPPER, companyId, proposalId, itemId).stream().findFirst();
    }

    @Transactional
    public AtelieProposalItem addItem(UUID companyId, UUID proposalId, String description,
                                      int quantity, int unitPriceCents) {
        int lineTotal = quantity * unitPriceCents;
        UUID id = jdbcTemplate.queryForObject(
            "insert into atelie_proposal_items (company_id, proposal_id, description, quantity, "
                + "unit_price_cents, line_total_cents) values (?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, proposalId, description.trim(), quantity, unitPriceCents, lineTotal);
        recalcTotal(companyId, proposalId);
        return findItem(companyId, proposalId, id).orElseThrow();
    }

    @Transactional
    public Optional<AtelieProposalItem> updateItem(UUID companyId, UUID proposalId, UUID itemId,
                                                   String description, Integer quantity, Integer unitPriceCents) {
        // Lê o item atual (valores OLD) para resolver os campos não informados (null = mantém) e
        // materializar line_total a partir dos valores FINAIS. (Em Postgres, um SET que referencia
        // quantity*unit_price usaria os valores ANTIGOS da linha — por isso calculamos em Java.)
        Optional<AtelieProposalItem> current = findItem(companyId, proposalId, itemId);
        if (current.isEmpty()) {
            return Optional.empty();
        }
        AtelieProposalItem old = current.get();
        String finalDesc = (description != null && !description.isBlank()) ? description.trim() : old.description();
        int finalQty = quantity != null ? quantity : old.quantity();
        int finalUnit = unitPriceCents != null ? unitPriceCents : old.unitPriceCents();
        int finalLineTotal = finalQty * finalUnit;
        int n = jdbcTemplate.update(
            "update atelie_proposal_items set description = ?, quantity = ?, unit_price_cents = ?, "
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
        int n = jdbcTemplate.update("delete from atelie_proposal_items where company_id = ? and proposal_id = ? and id = ?",
            companyId, proposalId, itemId);
        if (n == 0) {
            return false;
        }
        recalcTotal(companyId, proposalId);
        return true;
    }

    /**
     * Re-soma o total da proposta a partir das linhas de ORÇAMENTO (materializa o derivado) e
     * RE-DERIVA o desconto do cupom aplicado, se houver (onda 2, backlog #13) — percent recalcula
     * sobre o novo total; fixed é clampado ao novo total. Tudo na MESMA transação da mutação do item.
     */
    private void recalcTotal(UUID companyId, UUID proposalId) {
        jdbcTemplate.update(
            "update atelie_proposals set total_cents = coalesce("
                + "(select sum(line_total_cents) from atelie_proposal_items where proposal_id = ?), 0), "
                + "updated_at = now() where company_id = ? and id = ?",
            proposalId, companyId, proposalId);
        recomputeDiscount(companyId, proposalId);
    }

    /** Re-deriva discount_cents a partir do cupom aplicado e do total_cents ATUAL (clampado). */
    private void recomputeDiscount(UUID companyId, UUID proposalId) {
        jdbcTemplate.update(
            "update atelie_proposals p set discount_cents = coalesce("
                + "(select least(case when c.kind = 'percent' "
                + "  then (p.total_cents::bigint * c.value / 100)::integer else c.value end, p.total_cents) "
                + " from atelie_coupons c where c.id = p.coupon_id), 0) "
                + "where p.company_id = ? and p.id = ?",
            companyId, proposalId);
    }

    /**
     * Aplica o cupom na proposta (onda 2, backlog #13): grava coupon_id + snapshot do code e
     * re-deriva o desconto. O service já validou (active/validade/mínimo/max_uses) e incrementa
     * uses na MESMA transação.
     */
    public void applyCoupon(UUID companyId, UUID proposalId, UUID couponId, String codeSnapshot) {
        jdbcTemplate.update(
            "update atelie_proposals set coupon_id = ?, coupon_code_snapshot = ?, updated_at = now() "
                + "where company_id = ? and id = ?",
            couponId, codeSnapshot, companyId, proposalId);
        recomputeDiscount(companyId, proposalId);
    }

    /** Remove o cupom da proposta (zera desconto + vínculo). O service decrementa uses na transação. */
    public void removeCoupon(UUID companyId, UUID proposalId) {
        jdbcTemplate.update(
            "update atelie_proposals set coupon_id = null, coupon_code_snapshot = null, "
                + "discount_cents = 0, updated_at = now() where company_id = ? and id = ?",
            companyId, proposalId);
    }

    // -------------------------------------------------------------------------
    // PROVAS/AJUSTES (a escapada) — NÃO recalculam total. Ordenadas por position na leitura.
    // -------------------------------------------------------------------------

    public List<AtelieFitting> listFittings(UUID proposalId) {
        return jdbcTemplate.query(
            "select id, proposal_id, title, description, due_date, status, position, completed_at, confirmed_at, confirmed_due_date "
                + "from atelie_fittings where proposal_id = ? order by position asc, created_at asc",
            FITTING_MAPPER, proposalId);
    }

    public Optional<AtelieFitting> findFitting(UUID companyId, UUID fittingId) {
        return jdbcTemplate.query(
            "select id, proposal_id, title, description, due_date, status, position, completed_at, confirmed_at, confirmed_due_date "
                + "from atelie_fittings where company_id = ? and id = ?",
            FITTING_MAPPER, companyId, fittingId).stream().findFirst();
    }

    public Optional<AtelieFitting> findFitting(UUID companyId, UUID proposalId, UUID fittingId) {
        return jdbcTemplate.query(
            "select id, proposal_id, title, description, due_date, status, position, completed_at, confirmed_at, confirmed_due_date "
                + "from atelie_fittings where company_id = ? and proposal_id = ? and id = ?",
            FITTING_MAPPER, companyId, proposalId, fittingId).stream().findFirst();
    }

    /** Adiciona uma prova/ajuste: status default 'pendente', position = max(position)+1 da proposta. */
    public AtelieFitting addFitting(UUID companyId, UUID proposalId, String title, String description,
                                    LocalDate dueDate) {
        Integer maxPos = jdbcTemplate.queryForObject(
            "select coalesce(max(position), -1) from atelie_fittings where proposal_id = ?",
            Integer.class, proposalId);
        int nextPos = (maxPos == null ? -1 : maxPos) + 1;
        UUID id = jdbcTemplate.queryForObject(
            "insert into atelie_fittings (company_id, proposal_id, title, description, due_date, status, position) "
                + "values (?, ?, ?, ?, ?, 'pendente', ?) returning id",
            UUID.class, companyId, proposalId, title.trim(), description,
            dueDate == null ? null : Date.valueOf(dueDate), nextPos);
        return findFitting(companyId, proposalId, id).orElseThrow();
    }

    public Optional<AtelieFitting> updateFitting(UUID companyId, UUID proposalId, UUID fittingId,
                                                 String title, String description, boolean descProvided,
                                                 LocalDate dueDate, boolean dueProvided) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (title != null && !title.isBlank()) { sets.add("title = ?"); args.add(title.trim()); }
        if (descProvided) { sets.add("description = ?"); args.add(description); }
        if (dueProvided) { sets.add("due_date = ?"); args.add(dueDate == null ? null : Date.valueOf(dueDate)); }
        if (sets.isEmpty()) {
            return findFitting(companyId, proposalId, fittingId);
        }
        sets.add("updated_at = now()");
        args.add(companyId);
        args.add(proposalId);
        args.add(fittingId);
        int n = jdbcTemplate.update("update atelie_fittings set " + String.join(", ", sets)
            + " where company_id = ? and proposal_id = ? and id = ?", args.toArray());
        if (n == 0) {
            return Optional.empty();
        }
        return findFitting(companyId, proposalId, fittingId);
    }

    public boolean deleteFitting(UUID companyId, UUID proposalId, UUID fittingId) {
        return jdbcTemplate.update(
            "delete from atelie_fittings where company_id = ? and proposal_id = ? and id = ?",
            companyId, proposalId, fittingId) > 0;
    }

    /**
     * Re-materializa position 0..N sequencialmente a partir da lista ordenada de ids, na MESMA
     * transação. Ids fora da proposta são ignorados (o where escopa por company+proposal). Espelha a
     * intenção do reorder (não há UPDATE de reordenação por delta — só set explícito do índice).
     */
    @Transactional
    public void reorderFittings(UUID companyId, UUID proposalId, List<UUID> orderedIds) {
        int pos = 0;
        for (UUID fittingId : orderedIds) {
            jdbcTemplate.update(
                "update atelie_fittings set position = ?, updated_at = now() "
                    + "where company_id = ? and proposal_id = ? and id = ?",
                pos, companyId, proposalId, fittingId);
            pos++;
        }
    }

    /**
     * Transição BINÁRIA da prova: ao entrar em 'realizada' grava completed_at = now(); ao voltar a
     * 'pendente' zera completed_at (null). Sem máquina rígida (pendente⇄realizada livre). Escopo por
     * company.
     */
    public Optional<AtelieFitting> transitionFitting(UUID companyId, UUID fittingId, String newStatus) {
        int n;
        if ("realizada".equals(newStatus)) {
            n = jdbcTemplate.update(
                "update atelie_fittings set status = 'realizada', completed_at = now(), updated_at = now() "
                    + "where company_id = ? and id = ?", companyId, fittingId);
        } else {
            n = jdbcTemplate.update(
                "update atelie_fittings set status = 'pendente', completed_at = null, updated_at = now() "
                    + "where company_id = ? and id = ?", companyId, fittingId);
        }
        return n == 0 ? Optional.empty() : findFitting(companyId, fittingId);
    }
}
