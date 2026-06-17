package com.meada.whatsapp.profiles.sushi.menu;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code sushi_menu_items} (camada 7.1). Opera via service_role; o escopo por
 * company_id no WHERE de cada query é a defesa (o backend não passa pelo RLS do tenant).
 */
@Repository
public class SushiMenuItemRepository {

    private static final RowMapper<SushiMenuItem> MAPPER = (rs, rn) -> new SushiMenuItem(
        (UUID) rs.getObject("id"),
        rs.getString("name"),
        rs.getString("description"),
        rs.getInt("price_cents"),
        rs.getString("category"),
        rs.getBoolean("available"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private static final String COLS =
        "id, name, description, price_cents, category, available, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    public SushiMenuItemRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Lista itens do tenant, opcionalmente filtrando por categoria e/ou só disponíveis. */
    public List<SushiMenuItem> listByCompany(UUID companyId, String category, boolean onlyAvailable) {
        StringBuilder sql = new StringBuilder(
            "select " + COLS + " from sushi_menu_items where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (category != null && !category.isBlank()) {
            sql.append(" and category = ?");
            args.add(category);
        }
        if (onlyAvailable) {
            sql.append(" and available = true");
        }
        sql.append(" order by category asc, name asc");
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public Optional<SushiMenuItem> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query(
                "select " + COLS + " from sushi_menu_items where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    public SushiMenuItem insert(UUID companyId, String name, String description,
                                int priceCents, String category) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into sushi_menu_items (company_id, name, description, price_cents, category) "
                + "values (?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, name.trim(), description, priceCents, category);
        return findById(companyId, id).orElseThrow();
    }

    /**
     * Atualiza campos não-null (PATCH parcial). category já validada no service. Retorna o
     * item atualizado, ou empty se não existir/pertencer ao tenant.
     */
    public Optional<SushiMenuItem> update(UUID companyId, UUID id, String name, String description,
                                          Integer priceCents, String category, Boolean available) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (name != null && !name.isBlank()) { sets.add("name = ?"); args.add(name.trim()); }
        // description: aceitar explicitamente vazio/null para limpar exigiria sentinela; aqui
        // null = não mexe. Para limpar, o frontend manda string vazia.
        if (description != null) { sets.add("description = ?"); args.add(description); }
        if (priceCents != null) { sets.add("price_cents = ?"); args.add(priceCents); }
        if (category != null && !category.isBlank()) { sets.add("category = ?"); args.add(category); }
        if (available != null) { sets.add("available = ?"); args.add(available); }

        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update(
                "update sushi_menu_items set " + String.join(", ", sets)
                    + " where company_id = ? and id = ?",
                args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    /** Atalho dedicado para o toggle de disponibilidade. */
    public Optional<SushiMenuItem> toggle(UUID companyId, UUID id, boolean available) {
        int n = jdbcTemplate.update(
            "update sushi_menu_items set available = ?, updated_at = now() "
                + "where company_id = ? and id = ?",
            available, companyId, id);
        return n == 0 ? Optional.empty() : findById(companyId, id);
    }

    /** Hard delete. Lança DataIntegrityViolation se houver order_item referenciando (FK restrict). */
    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update(
            "delete from sushi_menu_items where company_id = ? and id = ?", companyId, id) > 0;
    }
}
