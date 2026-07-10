package com.meada.common.coupons;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Motor comum de acesso à tabela de cupons de um perfil (unificação 2026-07 dos 7 clones —
 * sushi/adega/atelie/barber/wedding/comida/academia). Cada perfil mantém um repository FINO que
 * só declara {@link #table()}, {@link #minColumn()} e {@link #mapper()} — o SQL e a semântica
 * (escopo por company_id em todo WHERE, findByCode case-insensitive via lower(code),
 * increment/decrement na MESMA transação de aplicar/remover o cupom) vivem aqui, uma vez.
 *
 * <p>{@code table()}/{@code minColumn()} são CONSTANTES de código dos subtipos — nunca input do
 * usuário — por isso a interpolação na SQL é segura.
 */
public abstract class CouponRepositoryBase<T extends CouponRecord> {

    protected final JdbcTemplate jdbcTemplate;

    protected CouponRepositoryBase(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Nome da tabela de cupons do perfil (ex.: {@code sushi_coupons}). */
    protected abstract String table();

    /** Coluna de pedido mínimo: {@code min_order_cents} (padrão) ou {@code min_cents} (academia). */
    protected abstract String minColumn();

    protected abstract RowMapper<T> mapper();

    private String cols() {
        return "id, company_id, code, kind, value, " + minColumn()
            + ", max_uses, uses, valid_until, active, created_at, updated_at";
    }

    public List<T> listByCompany(UUID companyId) {
        return jdbcTemplate.query(
            "select " + cols() + " from " + table() + " where company_id = ? order by code asc",
            mapper(), companyId);
    }

    public Optional<T> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query(
                "select " + cols() + " from " + table() + " where company_id = ? and id = ?",
                mapper(), companyId, id)
            .stream().findFirst();
    }

    /** Busca por code case-insensitive (lower(code)). */
    public Optional<T> findByCode(UUID companyId, String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
                "select " + cols() + " from " + table()
                    + " where company_id = ? and lower(code) = lower(?)",
                mapper(), companyId, code.trim())
            .stream().findFirst();
    }

    public T insert(UUID companyId, String code, String kind, int value, int minCents,
                    Integer maxUses, LocalDate validUntil, boolean active) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into " + table() + " (company_id, code, kind, value, " + minColumn()
                + ", max_uses, valid_until, active) values (?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, code.trim(), kind, value, minCents, maxUses,
            validUntil == null ? null : Date.valueOf(validUntil), active);
        return findById(companyId, id).orElseThrow();
    }

    /** PATCH parcial. validUntil/maxUses controlados por flags "provided" (podem ser limpos p/ null). */
    public Optional<T> update(UUID companyId, UUID id, String code, String kind, Integer value,
                              Integer minCents, Integer maxUses, boolean maxUsesProvided,
                              LocalDate validUntil, boolean validUntilProvided, Boolean active) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (code != null && !code.isBlank()) { sets.add("code = ?"); args.add(code.trim()); }
        if (kind != null && !kind.isBlank()) { sets.add("kind = ?"); args.add(kind); }
        if (value != null) { sets.add("value = ?"); args.add(value); }
        if (minCents != null) { sets.add(minColumn() + " = ?"); args.add(minCents); }
        if (maxUsesProvided) { sets.add("max_uses = ?"); args.add(maxUses); }
        if (validUntilProvided) {
            sets.add("valid_until = ?");
            args.add(validUntil == null ? null : Date.valueOf(validUntil));
        }
        if (active != null) { sets.add("active = ?"); args.add(active); }
        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update(
                "update " + table() + " set " + String.join(", ", sets)
                    + " where company_id = ? and id = ?",
                args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    public Optional<T> toggle(UUID companyId, UUID id, boolean active) {
        int n = jdbcTemplate.update(
            "update " + table() + " set active = ?, updated_at = now() "
                + "where company_id = ? and id = ?",
            active, companyId, id);
        return n == 0 ? Optional.empty() : findById(companyId, id);
    }

    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update(
            "delete from " + table() + " where company_id = ? and id = ?", companyId, id) > 0;
    }

    /** Incrementa uses do cupom (na transação da criação do pedido/aplicação na proposta). */
    public void incrementUses(UUID companyId, UUID id) {
        jdbcTemplate.update(
            "update " + table() + " set uses = uses + 1, updated_at = now() "
                + "where company_id = ? and id = ?",
            companyId, id);
    }

    /** Decrementa uses com piso 0 (na transação de remover o cupom da proposta). */
    public void decrementUses(UUID companyId, UUID id) {
        jdbcTemplate.update(
            "update " + table() + " set uses = greatest(uses - 1, 0), updated_at = now() "
                + "where company_id = ? and id = ?",
            companyId, id);
    }
}
