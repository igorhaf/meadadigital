package com.meada.admin.users;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Acesso GLOBAL a {@code users} para o painel super-admin (camada 6.2). service_role
 * (BYPASSRLS) — visão de todos os tenants. Exclui usuários soft-deleted (deleted_at) da
 * listagem por padrão.
 */
@Repository
public class UserAdminRepository {

    private static final RowMapper<UserListItem> LIST_MAPPER = (rs, rowNum) ->
        new UserListItem(
            (UUID) rs.getObject("id"),
            rs.getString("email"),
            rs.getString("role"),
            rs.getString("company_name"),
            rs.getBoolean("suspended"),
            rs.getTimestamp("last_login_at") != null
                ? rs.getTimestamp("last_login_at").toInstant() : null,
            rs.getTimestamp("created_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public UserAdminRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Total para a paginação, aplicando os mesmos filtros do findPage. */
    public long count(String q, UUID companyId, String role, Boolean suspended) {
        StringBuilder sql = new StringBuilder(
            "select count(*) from users u where u.deleted_at is null");
        List<Object> args = new ArrayList<>();
        appendFilters(sql, args, q, companyId, role, suspended);
        Long n = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0L : n;
    }

    /** Página de usuários (join companies para o nome), mais recentes primeiro. */
    public List<UserListItem> findPage(String q, UUID companyId, String role, Boolean suspended,
                                       int page, int pageSize) {
        StringBuilder sql = new StringBuilder(
            "select u.id, u.email, u.role, c.name as company_name, u.suspended, "
                + "u.last_login_at, u.created_at "
                + "from users u join companies c on c.id = u.company_id "
                + "where u.deleted_at is null");
        List<Object> args = new ArrayList<>();
        appendFilters(sql, args, q, companyId, role, suspended);
        sql.append(" order by u.created_at desc limit ? offset ?");
        args.add(pageSize);
        args.add((long) page * pageSize);
        return jdbcTemplate.query(sql.toString(), LIST_MAPPER, args.toArray());
    }

    private void appendFilters(StringBuilder sql, List<Object> args, String q, UUID companyId,
                               String role, Boolean suspended) {
        if (q != null && !q.isBlank()) {
            sql.append(" and u.email ilike ?");
            args.add("%" + q.trim() + "%");
        }
        if (companyId != null) {
            sql.append(" and u.company_id = ?");
            args.add(companyId);
        }
        if (role != null && !role.isBlank()) {
            sql.append(" and u.role = ?");
            args.add(role.trim());
        }
        if (suspended != null) {
            sql.append(" and u.suspended = ?");
            args.add(suspended);
        }
    }

    /** Detalhe de um usuário ativo (não soft-deleted). Optional vazio se não existe. */
    public java.util.Optional<UserDetail> findDetail(UUID id) {
        Objects.requireNonNull(id, "id");
        return jdbcTemplate.query(
                "select u.id, u.email, u.role, u.company_id, c.name as company_name, "
                    + "u.suspended, u.suspended_at, u.suspended_reason, u.last_login_at, u.created_at "
                    + "from users u join companies c on c.id = u.company_id "
                    + "where u.id = ? and u.deleted_at is null",
                (rs, rowNum) -> new UserDetail(
                    (UUID) rs.getObject("id"), rs.getString("email"), rs.getString("role"),
                    (UUID) rs.getObject("company_id"), rs.getString("company_name"),
                    rs.getBoolean("suspended"),
                    rs.getTimestamp("suspended_at") != null ? rs.getTimestamp("suspended_at").toInstant() : null,
                    rs.getString("suspended_reason"),
                    rs.getTimestamp("last_login_at") != null ? rs.getTimestamp("last_login_at").toInstant() : null,
                    rs.getTimestamp("created_at").toInstant()),
                id)
            .stream().findFirst();
    }

    /** true se o usuário está suspenso (para o guard de idempotência do suspend). */
    public boolean isSuspended(UUID id) {
        Boolean s = jdbcTemplate.query(
                "select suspended from users where id = ? and deleted_at is null",
                (rs, rowNum) -> rs.getBoolean("suspended"), id)
            .stream().findFirst().orElse(null);
        return Boolean.TRUE.equals(s);
    }

    /** true se o usuário existe e não está soft-deleted. */
    public boolean exists(UUID id) {
        Long n = jdbcTemplate.queryForObject(
            "select count(*) from users where id = ? and deleted_at is null", Long.class, id);
        return n != null && n > 0;
    }

    public void setSuspended(UUID id, boolean suspended, String reason) {
        if (suspended) {
            jdbcTemplate.update(
                "update users set suspended = true, suspended_at = now(), suspended_reason = ?, "
                    + "updated_at = now() where id = ?", reason, id);
        } else {
            jdbcTemplate.update(
                "update users set suspended = false, suspended_at = null, suspended_reason = null, "
                    + "updated_at = now() where id = ?", id);
        }
    }

    public void softDelete(UUID id) {
        jdbcTemplate.update(
            "update users set deleted_at = now(), updated_at = now() where id = ?", id);
    }

    /** Últimas N ações do super-admin sobre este usuário (admin_action_log). */
    public List<RecentAction> recentActions(UUID userId, int limit) {
        return jdbcTemplate.query(
            "select action, payload, created_at from admin_action_log "
                + "where target_type = 'user' and target_id = ? order by created_at desc limit ?",
            (rs, rowNum) -> new RecentAction(
                rs.getString("action"),
                rs.getString("payload"),
                rs.getTimestamp("created_at").toInstant()),
            userId, limit);
    }

    /** Item da lista global de usuários. */
    public record UserListItem(UUID id, String email, String role, String companyName,
                               boolean suspended, Instant lastLoginAt, Instant createdAt) {
    }

    /** Detalhe completo (sem as ações — buscadas à parte). */
    public record UserDetail(UUID id, String email, String role, UUID companyId, String companyName,
                             boolean suspended, Instant suspendedAt, String suspendedReason,
                             Instant lastLoginAt, Instant createdAt) {
    }

    /** Linha do histórico de ações do super-admin sobre o usuário (payload como String JSON). */
    public record RecentAction(String action, String payload, Instant createdAt) {
    }
}
