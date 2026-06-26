package com.meada.whatsapp.profiles.suplementos.catalog;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code sup_products} (camada 8.24). Clone do
 * {@link com.meada.whatsapp.profiles.lingerie.catalog.LingerieProductRepository} (chassi de varejo) +
 * hidratação das VARIANTES (⭐ a grade sabor×peso) por produto via {@link SupVariantRepository} (N+1
 * aceitável — catálogo é pequeno). Diferenças vs lingerie: campo {@code brand} (não base_price);
 * coluna de disponibilidade é {@code active} (não available). Opera via service_role; o escopo por
 * company_id no WHERE de cada query é a defesa.
 */
@Repository
public class SupProductRepository {

    private static final String COLS =
        "id, name, brand, category, description, active, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;
    private final SupVariantRepository variantRepository;

    public SupProductRepository(JdbcTemplate jdbcTemplate, SupVariantRepository variantRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.variantRepository = variantRepository;
    }

    /** Mapeia a row do produto SEM as variantes (hidratadas à parte por {@link #withVariants}). */
    private final RowMapper<SupProduct> bareMapper = (rs, rn) -> new SupProduct(
        (UUID) rs.getObject("id"),
        rs.getString("name"),
        rs.getString("brand"),
        rs.getString("category"),
        rs.getString("description"),
        rs.getBoolean("active"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant(),
        List.of());

    /** Lista produtos do tenant, opcionalmente filtrando por categoria e/ou só ativos. */
    public List<SupProduct> listByCompany(UUID companyId, String category, boolean onlyActive) {
        StringBuilder sql = new StringBuilder(
            "select " + COLS + " from sup_products where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (category != null && !category.isBlank()) {
            sql.append(" and category = ?");
            args.add(category);
        }
        if (onlyActive) {
            sql.append(" and active = true");
        }
        sql.append(" order by category asc, name asc");
        List<SupProduct> bare = jdbcTemplate.query(sql.toString(), bareMapper, args.toArray());
        List<SupProduct> withVar = new ArrayList<>(bare.size());
        for (SupProduct p : bare) {
            withVar.add(withVariants(companyId, p));
        }
        return withVar;
    }

    public Optional<SupProduct> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query(
                "select " + COLS + " from sup_products where company_id = ? and id = ?",
                bareMapper, companyId, id)
            .stream().findFirst()
            .map(p -> withVariants(companyId, p));
    }

    private SupProduct withVariants(UUID companyId, SupProduct p) {
        List<SupVariant> variants = variantRepository.listByProduct(companyId, p.id());
        return new SupProduct(p.id(), p.name(), p.brand(), p.category(), p.description(),
            p.active(), p.createdAt(), p.updatedAt(), variants);
    }

    public SupProduct insert(UUID companyId, String name, String brand, String category, String description) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into sup_products (company_id, name, brand, category, description) "
                + "values (?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, name.trim(), brand, category, description);
        return findById(companyId, id).orElseThrow();
    }

    /**
     * Atualiza campos não-null (PATCH parcial). category já validada no service. Retorna o produto
     * atualizado, ou empty se não existir/pertencer ao tenant.
     */
    public Optional<SupProduct> update(UUID companyId, UUID id, String name, String brand,
                                       String category, String description, Boolean active) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (name != null && !name.isBlank()) { sets.add("name = ?"); args.add(name.trim()); }
        // brand/description: null = não mexe. Para limpar, o frontend manda string vazia.
        if (brand != null) { sets.add("brand = ?"); args.add(brand.isBlank() ? null : brand); }
        if (category != null && !category.isBlank()) { sets.add("category = ?"); args.add(category); }
        if (description != null) { sets.add("description = ?"); args.add(description); }
        if (active != null) { sets.add("active = ?"); args.add(active); }

        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update(
                "update sup_products set " + String.join(", ", sets)
                    + " where company_id = ? and id = ?",
                args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    /** Atalho dedicado para o toggle de disponibilidade (active). */
    public Optional<SupProduct> toggle(UUID companyId, UUID id, boolean active) {
        int n = jdbcTemplate.update(
            "update sup_products set active = ?, updated_at = now() "
                + "where company_id = ? and id = ?",
            active, companyId, id);
        return n == 0 ? Optional.empty() : findById(companyId, id);
    }

    /**
     * Hard delete do produto (cascateia as variantes via FK on delete cascade). Lança
     * DataIntegrityViolation se alguma variante for referenciada por sup_order_item (FK restrict).
     */
    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update(
            "delete from sup_products where company_id = ? and id = ?", companyId, id) > 0;
    }
}
