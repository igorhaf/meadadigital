package com.meada.profiles.sushi.statuses;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code sushi_order_statuses} (camada 7.1 / sushi funcional). service_role; escopo por
 * company_id. O índice parcial UNIQUE {@code is_initial=true} (≤1 por company) é respeitado pelo
 * {@link #clearInitial} (zera o anterior ANTES de gravar/setar o novo, na mesma transação do service).
 */
@Repository
public class SushiOrderStatusRepository {

    private static final RowMapper<SushiOrderStatusEntity> MAPPER = (rs, rn) -> new SushiOrderStatusEntity(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("company_id"),
        rs.getString("name"),
        rs.getInt("sort_order"),
        rs.getBoolean("is_initial"),
        rs.getBoolean("is_terminal"),
        rs.getBoolean("notify_enabled"),
        rs.getString("notify_text"),
        rs.getString("color"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private static final String COLS =
        "id, company_id, name, sort_order, is_initial, is_terminal, notify_enabled, notify_text, "
            + "color, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    public SushiOrderStatusRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Lista os estados do tenant, ordenados por sort_order. */
    public List<SushiOrderStatusEntity> listByCompany(UUID companyId) {
        return jdbcTemplate.query(
            "select " + COLS + " from sushi_order_statuses where company_id = ? "
                + "order by sort_order asc, name asc",
            MAPPER, companyId);
    }

    public Optional<SushiOrderStatusEntity> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query(
                "select " + COLS + " from sushi_order_statuses where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    /** O estado inicial (is_initial=true) do tenant, se houver. */
    public Optional<SushiOrderStatusEntity> findInitial(UUID companyId) {
        return jdbcTemplate.query(
                "select " + COLS + " from sushi_order_statuses where company_id = ? and is_initial = true",
                MAPPER, companyId)
            .stream().findFirst();
    }

    /** True se há pedidos com este status (bloqueia o delete → 409 status_in_use). */
    public boolean hasOrders(UUID companyId, UUID statusId) {
        Long n = jdbcTemplate.queryForObject(
            "select count(*) from sushi_orders where company_id = ? and status = ?",
            Long.class, companyId, statusId);
        return n != null && n > 0;
    }

    /**
     * Zera o estado inicial atual do tenant (UPDATE ... is_initial=false WHERE is_initial=true) —
     * chamado pelo service ANTES de gravar/setar o novo inicial, para respeitar o índice parcial
     * UNIQUE (≤1 inicial por company), tudo na mesma transação.
     */
    public void clearInitial(UUID companyId) {
        jdbcTemplate.update(
            "update sushi_order_statuses set is_initial = false, updated_at = now() "
                + "where company_id = ? and is_initial = true",
            companyId);
    }

    public SushiOrderStatusEntity insert(UUID companyId, String name, int sortOrder, boolean isInitial,
                                         boolean isTerminal, boolean notifyEnabled, String notifyText,
                                         String color) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into sushi_order_statuses (company_id, name, sort_order, is_initial, is_terminal, "
                + "notify_enabled, notify_text, color) values (?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, name.trim(), sortOrder, isInitial, isTerminal, notifyEnabled,
            notifyText, color);
        return findById(companyId, id).orElseThrow();
    }

    /** PATCH parcial (campos null = não mexe). isInitial é tratado pelo service (clearInitial antes). */
    public Optional<SushiOrderStatusEntity> update(UUID companyId, UUID id, String name, Integer sortOrder,
                                                   Boolean isInitial, Boolean isTerminal,
                                                   Boolean notifyEnabled, String notifyText,
                                                   boolean notifyTextProvided, String color,
                                                   boolean colorProvided) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (name != null && !name.isBlank()) { sets.add("name = ?"); args.add(name.trim()); }
        if (sortOrder != null) { sets.add("sort_order = ?"); args.add(sortOrder); }
        if (isInitial != null) { sets.add("is_initial = ?"); args.add(isInitial); }
        if (isTerminal != null) { sets.add("is_terminal = ?"); args.add(isTerminal); }
        if (notifyEnabled != null) { sets.add("notify_enabled = ?"); args.add(notifyEnabled); }
        if (notifyTextProvided) { sets.add("notify_text = ?"); args.add(notifyText); }
        if (colorProvided) { sets.add("color = ?"); args.add(color); }
        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update(
                "update sushi_order_statuses set " + String.join(", ", sets)
                    + " where company_id = ? and id = ?",
                args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    /** Hard delete. Lança DataIntegrityViolation se houver pedido referenciando (FK restrict). */
    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update(
            "delete from sushi_order_statuses where company_id = ? and id = ?", companyId, id) > 0;
    }
}
