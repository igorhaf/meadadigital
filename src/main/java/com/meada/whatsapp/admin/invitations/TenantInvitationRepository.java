package com.meada.whatsapp.admin.invitations;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code tenant_invitations} (camada 5.16 #6). Opera como service_role (o backend
 * é o escritor; o accept roda fora do RLS porque o convidado ainda não tem company_id
 * resolvido). A leitura/criação pelo painel admin também passa por aqui via service_role.
 */
@Repository
public class TenantInvitationRepository {

    private static final RowMapper<TenantInvitation> ROW_MAPPER = (rs, rowNum) ->
        new TenantInvitation(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("company_id"),
            rs.getString("email"),
            rs.getString("token"),
            (UUID) rs.getObject("invited_by"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("expires_at").toInstant(),
            rs.getTimestamp("used_at") != null ? rs.getTimestamp("used_at").toInstant() : null,
            (UUID) rs.getObject("used_by"));

    private static final String COLUMNS =
        "id, company_id, email, token, invited_by, created_at, expires_at, used_at, used_by";

    private final JdbcTemplate jdbcTemplate;

    public TenantInvitationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Insere um convite e retorna a linha criada (RETURNING). */
    public TenantInvitation insert(UUID companyId, String email, String token,
                                   UUID invitedBy, Instant expiresAt) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(token, "token must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        return jdbcTemplate.queryForObject(
            "insert into tenant_invitations (company_id, email, token, invited_by, expires_at) "
                + "values (?, ?, ?, ?, ?) returning " + COLUMNS,
            ROW_MAPPER, companyId, email, token, invitedBy, Timestamp.from(expiresAt));
    }

    /**
     * Convites da empresa, mais recentes primeiro. Inclui usados E expirados (a UI mostra
     * o histórico com o status de cada um). Isolamento por companyId no WHERE (defesa em
     * profundidade — o RLS também isola, mas o backend é service_role e não aplica RLS).
     */
    public List<TenantInvitation> findByCompany(UUID companyId) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        return jdbcTemplate.query(
            "select " + COLUMNS + " from tenant_invitations where company_id = ? "
                + "order by created_at desc",
            ROW_MAPPER, companyId);
    }

    /**
     * Convite ATIVO por token: existe, não usado, não expirado. Usado pelo accept e pelo
     * lookup público. Optional vazio = token inválido/usado/expirado (a UI/serviço
     * decide a mensagem; aqui não distinguimos os três — o serviço refина o erro).
     */
    public Optional<TenantInvitation> findActiveByToken(String token) {
        Objects.requireNonNull(token, "token must not be null");
        return jdbcTemplate.query(
                "select " + COLUMNS + " from tenant_invitations "
                    + "where token = ? and used_at is null and expires_at > now()",
                ROW_MAPPER, token)
            .stream()
            .findFirst();
    }

    /**
     * Convite por token SEM filtrar ativo (qualquer estado). Usado pelo serviço para
     * distinguir "não existe" de "expirado/usado" e dar o erro certo. Optional vazio só
     * quando o token realmente não existe.
     */
    public Optional<TenantInvitation> findByToken(String token) {
        Objects.requireNonNull(token, "token must not be null");
        return jdbcTemplate.query(
                "select " + COLUMNS + " from tenant_invitations where token = ?",
                ROW_MAPPER, token)
            .stream()
            .findFirst();
    }

    /**
     * Marca o convite como usado. {@code AND used_at is null} torna idempotente sob race
     * (dois accepts concorrentes): só o primeiro marca; o segundo atualiza 0 linhas e
     * retorna false (o serviço trata como already_used).
     *
     * @return true se marcou agora; false se já estava usado (ou token inexistente).
     */
    public boolean markUsed(String token, UUID userId) {
        Objects.requireNonNull(token, "token must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        int updated = jdbcTemplate.update(
            "update tenant_invitations set used_at = now(), used_by = ? "
                + "where token = ? and used_at is null",
            userId, token);
        return updated > 0;
    }

    /**
     * Cancela um convite expirando-o imediatamente (expires_at = now()). Só da própria
     * empresa e só se ainda não usado. Idempotente.
     *
     * @return true se cancelou; false se não existe / outra empresa / já usado.
     */
    public boolean cancel(UUID id, UUID companyId) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        int updated = jdbcTemplate.update(
            "update tenant_invitations set expires_at = now() "
                + "where id = ? and company_id = ? and used_at is null",
            id, companyId);
        return updated > 0;
    }
}
