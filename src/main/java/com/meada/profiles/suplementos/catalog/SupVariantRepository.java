package com.meada.profiles.suplementos.catalog;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Acesso a {@code sup_variants} (camada 8.24, ⭐ chassi de varejo). Clone do
 * {@link com.meada.profiles.lingerie.catalog.LingerieVariantRepository} adaptado (sabor×peso
 * em vez de tamanho×cor; {@code price_cents} NOT NULL; {@code expiry_date} administrativo). A operação
 * CHAVE desta SM é {@link #decrementStock}: o UPDATE condicional {@code stock_quantity >= qtd} que
 * fecha a janela de corrida (duas compras concorrentes da última unidade — só uma decrementa). Opera
 * via service_role; o escopo por company_id no WHERE é a defesa.
 */
@Repository
public class SupVariantRepository {

    private static final String COLS =
        "id, product_id, flavor, size_label, sku, price_cents, stock_quantity, expiry_date, active, "
            + "created_at, updated_at";

    private static final RowMapper<SupVariant> MAPPER = (rs, rn) -> new SupVariant(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("product_id"),
        rs.getString("flavor"),
        rs.getString("size_label"),
        rs.getString("sku"),
        rs.getInt("price_cents"),
        rs.getInt("stock_quantity"),
        rs.getObject("expiry_date") != null ? rs.getDate("expiry_date").toLocalDate() : null,
        rs.getBoolean("active"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public SupVariantRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Variantes de um produto (qualquer disponibilidade), ordenadas por sabor/peso. */
    public List<SupVariant> listByProduct(UUID companyId, UUID productId) {
        return jdbcTemplate.query(
            "select " + COLS + " from sup_variants "
                + "where company_id = ? and product_id = ? order by size_label asc, flavor asc, created_at asc",
            MAPPER, companyId, productId);
    }

    public Optional<SupVariant> findById(UUID companyId, UUID productId, UUID id) {
        return jdbcTemplate.query(
                "select " + COLS + " from sup_variants "
                    + "where company_id = ? and product_id = ? and id = ?",
                MAPPER, companyId, productId, id)
            .stream().findFirst();
    }

    /**
     * Resolve as variantes do tenant cujo id está em {@code variantIds} (qualquer produto). Usado na
     * criação do pedido para o snapshot/recálculo. A disponibilidade é validada no repositório de
     * pedido (precisa do produto também). Lista vazia de entrada → lista vazia.
     */
    public List<SupVariant> findByIdsForOrder(UUID companyId, Collection<UUID> variantIds) {
        if (variantIds == null || variantIds.isEmpty()) {
            return List.of();
        }
        String placeholders = variantIds.stream().map(v -> "?").collect(Collectors.joining(", "));
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        args.addAll(variantIds);
        return jdbcTemplate.query(
            "select " + COLS + " from sup_variants "
                + "where company_id = ? and id in (" + placeholders + ")",
            MAPPER, args.toArray());
    }

    public SupVariant insert(UUID companyId, UUID productId, String flavor, String sizeLabel,
                             String sku, int priceCents, int stockQuantity, LocalDate expiryDate) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into sup_variants (company_id, product_id, flavor, size_label, sku, price_cents, "
                + "stock_quantity, expiry_date) values (?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, productId, flavor, sizeLabel.trim(), sku, priceCents, stockQuantity,
            expiryDate != null ? Date.valueOf(expiryDate) : null);
        return findById(companyId, productId, id).orElseThrow();
    }

    /**
     * Atualiza campos não-null (PATCH parcial). Inclui ajuste de estoque ({@code stockQuantity}),
     * preço ({@code priceCents}) e validade ({@code expiryDate}; {@code clearExpiry=true} limpa).
     * Retorna a variante atualizada, ou empty se não existir/pertencer ao produto+tenant.
     */
    public Optional<SupVariant> update(UUID companyId, UUID productId, UUID id, String flavor,
                                       String sizeLabel, String sku, Integer priceCents,
                                       Integer stockQuantity, LocalDate expiryDate, boolean clearExpiry,
                                       Boolean active, boolean clearFlavor) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (clearFlavor) {
            sets.add("flavor = null");
        } else if (flavor != null && !flavor.isBlank()) {
            sets.add("flavor = ?"); args.add(flavor.trim());
        }
        if (sizeLabel != null && !sizeLabel.isBlank()) { sets.add("size_label = ?"); args.add(sizeLabel.trim()); }
        if (sku != null) { sets.add("sku = ?"); args.add(sku.isBlank() ? null : sku.trim()); }
        if (priceCents != null) { sets.add("price_cents = ?"); args.add(priceCents); }
        if (stockQuantity != null) { sets.add("stock_quantity = ?"); args.add(stockQuantity); }
        if (clearExpiry) {
            sets.add("expiry_date = null");
        } else if (expiryDate != null) {
            sets.add("expiry_date = ?"); args.add(Date.valueOf(expiryDate));
        }
        if (active != null) { sets.add("active = ?"); args.add(active); }

        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(productId);
            args.add(id);
            int n = jdbcTemplate.update(
                "update sup_variants set " + String.join(", ", sets)
                    + " where company_id = ? and product_id = ? and id = ?",
                args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, productId, id);
    }

    /** Atalho dedicado para o toggle de disponibilidade (active). */
    public Optional<SupVariant> toggle(UUID companyId, UUID productId, UUID id, boolean active) {
        int n = jdbcTemplate.update(
            "update sup_variants set active = ?, updated_at = now() "
                + "where company_id = ? and product_id = ? and id = ?",
            active, companyId, productId, id);
        return n == 0 ? Optional.empty() : findById(companyId, productId, id);
    }

    /** Hard delete. Lança DataIntegrityViolation se houver sup_order_item referenciando (FK restrict). */
    public boolean delete(UUID companyId, UUID productId, UUID id) {
        return jdbcTemplate.update(
            "delete from sup_variants where company_id = ? and product_id = ? and id = ?",
            companyId, productId, id) > 0;
    }

    /**
     * ⭐ DECREMENTO TRANSACIONAL DE ESTOQUE (o coração da ESCAPADA 1, espelho do
     * {@link com.meada.profiles.lingerie.catalog.LingerieVariantRepository#decrementStock}).
     * UPDATE condicional: só decrementa se {@code stock_quantity >= qtd} (a variante tem estoque
     * suficiente) e está {@code active}. Retorna {@code true} se a linha foi decrementada, {@code false}
     * se 0 linhas afetadas (estoque insuficiente / variante inexistente / inativa / de outro tenant).
     * O {@code false} sinaliza out-of-stock ao chamador, que ABORTA o pedido inteiro (rollback do
     * @Transactional). Esta condicional fecha a janela de corrida da última unidade.
     */
    public boolean decrementStock(UUID companyId, UUID variantId, int qtd) {
        int n = jdbcTemplate.update(
            "update sup_variants set stock_quantity = stock_quantity - ?, updated_at = now() "
                + "where id = ? and company_id = ? and active = true and stock_quantity >= ?",
            qtd, variantId, companyId, qtd);
        return n > 0;
    }

    /** Devolve estoque ao cancelar/recusar o pedido (onda #9 — restock idempotente no chamador). */
    public void restockStock(UUID companyId, UUID variantId, int qtd) {
        jdbcTemplate.update(
            "update sup_variants set stock_quantity = stock_quantity + ?, updated_at = now() "
                + "where id = ? and company_id = ?",
            qtd, variantId, companyId);
    }
}
