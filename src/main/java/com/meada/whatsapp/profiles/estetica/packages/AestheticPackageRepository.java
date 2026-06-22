package com.meada.whatsapp.profiles.estetica.packages;

import com.meada.whatsapp.profiles.estetica.AestheticPackageStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code aesthetic_packages} (camada 8.3) — A ESCAPADA. Opera via service_role.
 *
 * <p>{@code sessions_remaining} e {@code total_cents} são MATERIALIZADOS. {@link #consumeSession} e
 * {@link #returnSession} re-derivam o saldo e flipam o status (ativo↔esgotado) — chamados DENTRO da
 * transação do agendamento (defesa de corrida). A condição {@code where sessions_remaining > 0} no
 * UPDATE de consumo fecha a janela de corrida no nível do banco.
 */
@Repository
public class AestheticPackageRepository {

    private static final RowMapper<AestheticPackage> MAPPER = (rs, rn) -> new AestheticPackage(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("contact_id"),
        (UUID) rs.getObject("procedure_id"),
        (UUID) rs.getObject("conversation_id"),
        rs.getString("customer_name"),
        rs.getString("customer_phone"),
        rs.getString("procedure_name"),
        rs.getInt("unit_price_cents"),
        rs.getInt("total_sessions"),
        rs.getInt("sessions_used"),
        rs.getInt("sessions_remaining"),
        rs.getInt("total_cents"),
        rs.getString("status"),
        rs.getString("notes"),
        rs.getTimestamp("purchased_at").toInstant(),
        rs.getTimestamp("activated_at") == null ? null : rs.getTimestamp("activated_at").toInstant(),
        rs.getTimestamp("status_updated_at").toInstant());

    private static final String COLS =
        "id, contact_id, procedure_id, conversation_id, customer_name, customer_phone, procedure_name, "
            + "unit_price_cents, total_sessions, sessions_used, sessions_remaining, total_cents, status, "
            + "notes, purchased_at, activated_at, status_updated_at";

    private final JdbcTemplate jdbcTemplate;

    public AestheticPackageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AestheticPackage> listByCompany(UUID companyId, String status, UUID contactId,
                                                UUID procedureId, int limit, int offset) {
        StringBuilder sql = new StringBuilder("select " + COLS + " from aesthetic_packages where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) { sql.append(" and status = ?"); args.add(status); }
        if (contactId != null) { sql.append(" and contact_id = ?"); args.add(contactId); }
        if (procedureId != null) { sql.append(" and procedure_id = ?"); args.add(procedureId); }
        sql.append(" order by purchased_at desc limit ? offset ?");
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public long countByCompany(UUID companyId, String status, UUID contactId, UUID procedureId) {
        StringBuilder sql = new StringBuilder("select count(*) from aesthetic_packages where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) { sql.append(" and status = ?"); args.add(status); }
        if (contactId != null) { sql.append(" and contact_id = ?"); args.add(contactId); }
        if (procedureId != null) { sql.append(" and procedure_id = ?"); args.add(procedureId); }
        Long n = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0L : n;
    }

    public Optional<AestheticPackage> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query("select " + COLS + " from aesthetic_packages where company_id = ? and id = ?",
                MAPPER, companyId, id).stream().findFirst();
    }

    /** Pacotes ATIVOS de um contato com saldo > 0 (pra IA agendar consumindo). */
    public List<AestheticPackage> listActiveWithBalanceByContact(UUID companyId, UUID contactId) {
        return jdbcTemplate.query(
            "select " + COLS + " from aesthetic_packages where company_id = ? and contact_id = ? "
                + "and status = 'ativo' and sessions_remaining > 0 order by purchased_at asc",
            MAPPER, companyId, contactId);
    }

    /**
     * Cria o pacote em 'pendente'. total_cents = total_sessions * unit_price (materializado);
     * sessions_remaining = total_sessions (materializado). O unit_price/procedure_name são snapshots
     * (passados pelo service a partir do catálogo — a IA não manda preço).
     */
    public AestheticPackage insert(UUID companyId, UUID contactId, UUID procedureId, UUID conversationId,
                                   String customerName, String customerPhone, String procedureName,
                                   int unitPriceCents, int totalSessions, String notes) {
        int totalCents = unitPriceCents * totalSessions;
        UUID id = jdbcTemplate.queryForObject(
            "insert into aesthetic_packages (company_id, contact_id, procedure_id, conversation_id, "
                + "customer_name, customer_phone, procedure_name, unit_price_cents, total_sessions, "
                + "sessions_used, sessions_remaining, total_cents, status) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, 'pendente') returning id",
            UUID.class, companyId, contactId, procedureId, conversationId, customerName, customerPhone,
            procedureName, unitPriceCents, totalSessions, totalSessions, totalCents);
        return findById(companyId, id).orElseThrow();
    }

    /** Persiste a transição de status do pacote (PATCH manual). Preenche activated_at ao ir pra 'ativo'. */
    public void updateStatus(UUID companyId, UUID id, String newStatus) {
        if (AestheticPackageStatus.ATIVO.id().equals(newStatus)) {
            jdbcTemplate.update("update aesthetic_packages set status = ?, activated_at = coalesce(activated_at, now()), "
                + "status_updated_at = now(), updated_at = now() where company_id = ? and id = ?",
                newStatus, companyId, id);
        } else {
            jdbcTemplate.update("update aesthetic_packages set status = ?, status_updated_at = now(), "
                + "updated_at = now() where company_id = ? and id = ?", newStatus, companyId, id);
        }
    }

    /**
     * CONSOME 1 sessão (chamado DENTRO da transação do agendamento). UPDATE condicional
     * (status='ativo' and sessions_remaining > 0) fecha a corrida no banco: used+1, remaining
     * re-derivado, e se zerar → status 'esgotado'. Retorna true se consumiu (1 linha afetada), false
     * se o pacote não estava elegível (não-ativo ou esgotado) — o service traduz pro erro certo.
     */
    public boolean consumeSession(UUID companyId, UUID packageId) {
        int n = jdbcTemplate.update(
            "update aesthetic_packages set sessions_used = sessions_used + 1, "
                + "sessions_remaining = total_sessions - (sessions_used + 1), "
                + "status = case when total_sessions - (sessions_used + 1) <= 0 then 'esgotado' else status end, "
                + "status_updated_at = case when total_sessions - (sessions_used + 1) <= 0 then now() else status_updated_at end, "
                + "updated_at = now() "
                + "where company_id = ? and id = ? and status = 'ativo' and sessions_remaining > 0",
            companyId, packageId);
        return n > 0;
    }

    /**
     * DEVOLVE 1 sessão ao cancelar um agendamento que havia consumido (DENTRO da transação do
     * cancelamento). used-1, remaining re-derivado; se estava 'esgotado' e voltou a ter saldo, reabre
     * pra 'ativo'. Só age se sessions_used > 0 (defesa). Status terminais 'expirado'/'cancelado' NÃO
     * reabrem (o pacote foi encerrado por outra razão) — só 'esgotado'→'ativo'.
     */
    public void returnSession(UUID companyId, UUID packageId) {
        jdbcTemplate.update(
            "update aesthetic_packages set sessions_used = sessions_used - 1, "
                + "sessions_remaining = total_sessions - (sessions_used - 1), "
                + "status = case when status = 'esgotado' then 'ativo' else status end, "
                + "status_updated_at = case when status = 'esgotado' then now() else status_updated_at end, "
                + "updated_at = now() "
                + "where company_id = ? and id = ? and sessions_used > 0",
            companyId, packageId);
    }
}
