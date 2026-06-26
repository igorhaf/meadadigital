package com.meada.whatsapp.profiles.las.catalog;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code las_products} (camada 8.23). Clone do
 * {@link com.meada.whatsapp.profiles.lingerie.catalog.LingerieProductRepository} (chassi de varejo) +
 * hidratação das VARIANTES (⭐ a grade COR × DYE_LOT) por produto via {@link LasVariantRepository}
 * (N+1 aceitável — catálogo é pequeno). Opera via service_role; o escopo por company_id no WHERE de
 * cada query é a defesa.
 */
@Repository
public class LasProductRepository {

    private static final String COLS =
        "id, name, description, category, base_price_cents, available, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;
    private final LasVariantRepository variantRepository;

    public LasProductRepository(JdbcTemplate jdbcTemplate,
                                LasVariantRepository variantRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.variantRepository = variantRepository;
    }

    /** Mapeia a row do produto SEM as variantes (hidratadas à parte por {@link #withVariants}). */
    private final RowMapper<LasProduct> bareMapper = (rs, rn) -> new LasProduct(
        (UUID) rs.getObject("id"),
        rs.getString("name"),
        rs.getString("description"),
        rs.getString("category"),
        rs.getInt("base_price_cents"),
        rs.getBoolean("available"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant(),
        List.of());

    /** Lista produtos do tenant, opcionalmente filtrando por categoria e/ou só disponíveis. */
    public List<LasProduct> listByCompany(UUID companyId, String category, boolean onlyAvailable) {
        StringBuilder sql = new StringBuilder(
            "select " + COLS + " from las_products where company_id = ?");
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
        List<LasProduct> bare = jdbcTemplate.query(sql.toString(), bareMapper, args.toArray());
        List<LasProduct> withVar = new ArrayList<>(bare.size());
        for (LasProduct p : bare) {
            withVar.add(withVariants(companyId, p));
        }
        return withVar;
    }

    public Optional<LasProduct> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query(
                "select " + COLS + " from las_products where company_id = ? and id = ?",
                bareMapper, companyId, id)
            .stream().findFirst()
            .map(p -> withVariants(companyId, p));
    }

    private LasProduct withVariants(UUID companyId, LasProduct p) {
        List<LasVariant> variants = variantRepository.listByProduct(companyId, p.id());
        return new LasProduct(p.id(), p.name(), p.description(), p.category(),
            p.basePriceCents(), p.available(), p.createdAt(), p.updatedAt(), variants);
    }

    public LasProduct insert(UUID companyId, String name, String description,
                             String category, int basePriceCents) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into las_products (company_id, name, description, category, base_price_cents) "
                + "values (?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, name.trim(), description, category, basePriceCents);
        return findById(companyId, id).orElseThrow();
    }

    /**
     * Atualiza campos não-null (PATCH parcial). category já validada no service. Retorna o produto
     * atualizado, ou empty se não existir/pertencer ao tenant.
     */
    public Optional<LasProduct> update(UUID companyId, UUID id, String name, String description,
                                       String category, Integer basePriceCents, Boolean available) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (name != null && !name.isBlank()) { sets.add("name = ?"); args.add(name.trim()); }
        // description: null = não mexe. Para limpar, o frontend manda string vazia.
        if (description != null) { sets.add("description = ?"); args.add(description); }
        if (category != null && !category.isBlank()) { sets.add("category = ?"); args.add(category); }
        if (basePriceCents != null) { sets.add("base_price_cents = ?"); args.add(basePriceCents); }
        if (available != null) { sets.add("available = ?"); args.add(available); }

        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update(
                "update las_products set " + String.join(", ", sets)
                    + " where company_id = ? and id = ?",
                args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    /** Atalho dedicado para o toggle de disponibilidade. */
    public Optional<LasProduct> toggle(UUID companyId, UUID id, boolean available) {
        int n = jdbcTemplate.update(
            "update las_products set available = ?, updated_at = now() "
                + "where company_id = ? and id = ?",
            available, companyId, id);
        return n == 0 ? Optional.empty() : findById(companyId, id);
    }

    /**
     * Hard delete do produto (cascateia as variantes via FK on delete cascade). Lança
     * DataIntegrityViolation se alguma variante for referenciada por order_item (FK restrict).
     */
    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update(
            "delete from las_products where company_id = ? and id = ?", companyId, id) > 0;
    }
}
