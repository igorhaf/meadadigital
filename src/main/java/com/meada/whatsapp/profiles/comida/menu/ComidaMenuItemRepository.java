package com.meada.whatsapp.profiles.comida.menu;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code comida_menu_items} (camada 8.4). Clone de
 * {@link com.meada.whatsapp.profiles.sushi.menu.SushiMenuItemRepository} + hidratação das opções
 * (ESCAPADA 2) por item via {@link ComidaMenuOptionRepository} (N+1 aceitável — cardápio é pequeno).
 * Opera via service_role; o escopo por company_id no WHERE de cada query é a defesa.
 */
@Repository
public class ComidaMenuItemRepository {

    private static final String COLS =
        "id, name, description, price_cents, category, available, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;
    private final ComidaMenuOptionRepository optionRepository;

    public ComidaMenuItemRepository(JdbcTemplate jdbcTemplate,
                                    ComidaMenuOptionRepository optionRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.optionRepository = optionRepository;
    }

    /** Mapeia a row do item SEM as opções (hidratadas à parte por {@link #withOptions}). */
    private final RowMapper<ComidaMenuItem> bareMapper = (rs, rn) -> new ComidaMenuItem(
        (UUID) rs.getObject("id"),
        rs.getString("name"),
        rs.getString("description"),
        rs.getInt("price_cents"),
        rs.getString("category"),
        rs.getBoolean("available"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant(),
        List.of());

    /** Lista itens do tenant, opcionalmente filtrando por categoria e/ou só disponíveis. */
    public List<ComidaMenuItem> listByCompany(UUID companyId, String category, boolean onlyAvailable) {
        StringBuilder sql = new StringBuilder(
            "select " + COLS + " from comida_menu_items where company_id = ?");
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
        List<ComidaMenuItem> bare = jdbcTemplate.query(sql.toString(), bareMapper, args.toArray());
        List<ComidaMenuItem> withOpts = new ArrayList<>(bare.size());
        for (ComidaMenuItem it : bare) {
            withOpts.add(withOptions(companyId, it));
        }
        return withOpts;
    }

    public Optional<ComidaMenuItem> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query(
                "select " + COLS + " from comida_menu_items where company_id = ? and id = ?",
                bareMapper, companyId, id)
            .stream().findFirst()
            .map(it -> withOptions(companyId, it));
    }

    private ComidaMenuItem withOptions(UUID companyId, ComidaMenuItem it) {
        List<ComidaMenuOption> options = optionRepository.listByItem(companyId, it.id());
        return new ComidaMenuItem(it.id(), it.name(), it.description(), it.priceCents(),
            it.category(), it.available(), it.createdAt(), it.updatedAt(), options);
    }

    public ComidaMenuItem insert(UUID companyId, String name, String description,
                                 int priceCents, String category) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into comida_menu_items (company_id, name, description, price_cents, category) "
                + "values (?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, name.trim(), description, priceCents, category);
        return findById(companyId, id).orElseThrow();
    }

    /**
     * Atualiza campos não-null (PATCH parcial). category já validada no service. Retorna o item
     * atualizado, ou empty se não existir/pertencer ao tenant.
     */
    public Optional<ComidaMenuItem> update(UUID companyId, UUID id, String name, String description,
                                           Integer priceCents, String category, Boolean available) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (name != null && !name.isBlank()) { sets.add("name = ?"); args.add(name.trim()); }
        // description: null = não mexe. Para limpar, o frontend manda string vazia.
        if (description != null) { sets.add("description = ?"); args.add(description); }
        if (priceCents != null) { sets.add("price_cents = ?"); args.add(priceCents); }
        if (category != null && !category.isBlank()) { sets.add("category = ?"); args.add(category); }
        if (available != null) { sets.add("available = ?"); args.add(available); }

        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update(
                "update comida_menu_items set " + String.join(", ", sets)
                    + " where company_id = ? and id = ?",
                args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    /** Atalho dedicado para o toggle de disponibilidade. */
    public Optional<ComidaMenuItem> toggle(UUID companyId, UUID id, boolean available) {
        int n = jdbcTemplate.update(
            "update comida_menu_items set available = ?, updated_at = now() "
                + "where company_id = ? and id = ?",
            available, companyId, id);
        return n == 0 ? Optional.empty() : findById(companyId, id);
    }

    /** Hard delete. Lança DataIntegrityViolation se houver order_item referenciando (FK restrict). */
    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update(
            "delete from comida_menu_items where company_id = ? and id = ?", companyId, id) > 0;
    }
}
