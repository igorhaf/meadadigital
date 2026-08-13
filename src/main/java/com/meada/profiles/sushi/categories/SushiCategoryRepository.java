package com.meada.profiles.sushi.categories;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code sushi_categories} (camada 7.1 / sushi funcional). Opera via service_role; o
 * escopo por company_id no WHERE de cada query é a defesa (o backend não passa pelo RLS do tenant).
 */
@Repository
public class SushiCategoryRepository {

    private static final RowMapper<SushiCategoryEntity> MAPPER = (rs, rn) -> new SushiCategoryEntity(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("company_id"),
        rs.getString("name"),
        rs.getInt("sort_order"),
        rs.getBoolean("active"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private static final String COLS =
        "id, company_id, name, sort_order, active, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    public SushiCategoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Lista categorias do tenant, ordenadas por sort_order; filtro opcional só-ativas. */
    public List<SushiCategoryEntity> listByCompany(UUID companyId, boolean onlyActive) {
        StringBuilder sql = new StringBuilder(
            "select " + COLS + " from sushi_categories where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (onlyActive) {
            sql.append(" and active = true");
        }
        sql.append(" order by sort_order asc, name asc");
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public Optional<SushiCategoryEntity> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query(
                "select " + COLS + " from sushi_categories where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    /** True se a categoria existe, pertence ao tenant e está ATIVA (validação do cardápio). */
    public boolean existsActive(UUID companyId, UUID id) {
        Long n = jdbcTemplate.queryForObject(
            "select count(*) from sushi_categories where company_id = ? and id = ? and active = true",
            Long.class, companyId, id);
        return n != null && n > 0;
    }

    /** True se há itens de cardápio apontando para esta categoria (bloqueia o delete → 409). */
    public boolean hasMenuItems(UUID companyId, UUID categoryId) {
        Long n = jdbcTemplate.queryForObject(
            "select count(*) from sushi_menu_items where company_id = ? and category = ?",
            Long.class, companyId, categoryId);
        return n != null && n > 0;
    }

    public SushiCategoryEntity insert(UUID companyId, String name, int sortOrder, boolean active) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into sushi_categories (company_id, name, sort_order, active) "
                + "values (?, ?, ?, ?) returning id",
            UUID.class, companyId, name.trim(), sortOrder, active);
        return findById(companyId, id).orElseThrow();
    }

    /** PATCH parcial (campos null = não mexe). name validado no service. */
    public Optional<SushiCategoryEntity> update(UUID companyId, UUID id, String name,
                                                Integer sortOrder, Boolean active) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (name != null && !name.isBlank()) { sets.add("name = ?"); args.add(name.trim()); }
        if (sortOrder != null) { sets.add("sort_order = ?"); args.add(sortOrder); }
        if (active != null) { sets.add("active = ?"); args.add(active); }
        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update(
                "update sushi_categories set " + String.join(", ", sets)
                    + " where company_id = ? and id = ?",
                args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    public Optional<SushiCategoryEntity> toggle(UUID companyId, UUID id, boolean active) {
        int n = jdbcTemplate.update(
            "update sushi_categories set active = ?, updated_at = now() "
                + "where company_id = ? and id = ?",
            active, companyId, id);
        return n == 0 ? Optional.empty() : findById(companyId, id);
    }

    /** Hard delete. Lança DataIntegrityViolation se houver menu_item referenciando (FK restrict). */
    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update(
            "delete from sushi_categories where company_id = ? and id = ?", companyId, id) > 0;
    }
}
