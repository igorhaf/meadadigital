package com.meada.whatsapp.profiles.las.catalog;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.profiles.las.catalog.LasProductService.DuplicateVariantException;
import com.meada.whatsapp.profiles.las.catalog.LasProductService.InvalidCategoryException;
import com.meada.whatsapp.profiles.las.catalog.LasProductService.ProductInUseException;
import com.meada.whatsapp.profiles.las.catalog.LasProductService.VariantInUseException;
import com.meada.whatsapp.profiles.las.catalog.LasProductService.VariantNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o LasProductService (camada 8.23): create + audit, category inválida, update parcial, toggle,
 * delete em uso → 409; e o CRUD de VARIANTES (⭐ a grade COR × DYE_LOT): add/update/toggle/delete,
 * duplicate_variant 409 (mesma cor × lote), variant_in_use 409, e a invalidação de cache. Clone do
 * LingerieProductServiceTest, com o eixo de variante TROCADO (size→dye_lot) — SEM teste de invalid_size
 * (color/dye_lot são texto livre).
 */
class LasProductServiceTest extends AbstractIntegrationTest {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private LasProductService service;

    private static final UUID COMPANY = UUID.fromString("c8230000-0000-0000-0000-000000000091");
    private static final UUID USER = UUID.fromString("d8230000-0000-0000-0000-000000000091");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'las')",
            COMPANY, "Las Teste", "las-teste");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@las.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("create válido → persiste + audita las_product_created")
    void create_persistsAndAudits() {
        LasProduct p = service.create(COMPANY, USER, "Lã Merino", "100% lã", "las", 1990);
        assertThat(p.name()).isEqualTo("Lã Merino");
        assertThat(p.basePriceCents()).isEqualTo(1990);
        assertThat(p.available()).isTrue();

        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'las_product_created' and entity_id = ?",
            Long.class, p.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("create com categoria inválida → InvalidCategoryException")
    void create_invalidCategory() {
        assertThatThrownBy(() -> service.create(COMPANY, USER, "X", null, "fralda", 100))
            .isInstanceOf(InvalidCategoryException.class);
    }

    @Test
    @DisplayName("update parcial (só preço base) preserva os demais campos")
    void update_partial() {
        LasProduct p = service.create(COMPANY, USER, "Linha Crochê", "algodão", "linhas", 1290);
        LasProduct updated = service.update(COMPANY, USER, p.id(), null, null, null, 1490, null);
        assertThat(updated.basePriceCents()).isEqualTo(1490);
        assertThat(updated.name()).isEqualTo("Linha Crochê");   // preservado
        assertThat(updated.category()).isEqualTo("linhas");     // preservado
    }

    @Test
    @DisplayName("toggle desliga available")
    void toggle() {
        LasProduct p = service.create(COMPANY, USER, "Kit Iniciante", null, "kits", 12900);
        LasProduct off = service.toggle(COMPANY, USER, p.id(), false);
        assertThat(off.available()).isFalse();
    }

    // ---- Variantes (⭐ a grade COR × DYE_LOT) --------------------------------

    @Test
    @DisplayName("addVariant → persiste a variante (cor × lote) + audita + aparece no produto")
    void addVariant_persistsAndAudits() {
        LasProduct p = service.create(COMPANY, USER, "Lã Merino", null, "las", 1990);
        LasVariant v = service.addVariant(COMPANY, USER, p.id(), "Azul", "L2024-A", "SKU-1", 2190, 5);
        assertThat(v.color()).isEqualTo("Azul");
        assertThat(v.dyeLot()).isEqualTo("L2024-A");
        assertThat(v.priceCents()).isEqualTo(2190);
        assertThat(v.stockQty()).isEqualTo(5);
        assertThat(v.available()).isTrue();

        List<LasVariant> variants = service.listVariants(COMPANY, p.id());
        assertThat(variants).hasSize(1);

        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'las_variant_created' and entity_id = ?",
            Long.class, v.id());
        assertThat(audit).isEqualTo(1L);

        // produto hidratado já traz a variante embutida.
        assertThat(service.get(COMPANY, p.id()).orElseThrow().variants()).hasSize(1);
    }

    @Test
    @DisplayName("addVariant duplicada (mesma cor × dye_lot) → DuplicateVariantException (409)")
    void addVariant_duplicate() {
        LasProduct p = service.create(COMPANY, USER, "Lã", null, "las", 1990);
        service.addVariant(COMPANY, USER, p.id(), "Branco", "L2024-A", null, null, 3);
        // mesma cor + mesmo lote → colide com o UNIQUE(product_id, color, dye_lot).
        assertThatThrownBy(() -> service.addVariant(COMPANY, USER, p.id(), "Branco", "L2024-A", null, null, 9))
            .isInstanceOf(DuplicateVariantException.class);
        // mesma cor, lote DIFERENTE → permitido (SKU próprio).
        LasVariant outroLote = service.addVariant(COMPANY, USER, p.id(), "Branco", "L2024-B", null, null, 7);
        assertThat(outroLote.dyeLot()).isEqualTo("L2024-B");
        assertThat(service.listVariants(COMPANY, p.id())).hasSize(2);
    }

    @Test
    @DisplayName("updateVariant parcial (só estoque) preserva cor/lote; clearPrice volta a herdar o base")
    void updateVariant_partialAndClearPrice() {
        LasProduct p = service.create(COMPANY, USER, "Lã Grossa", null, "las", 2990);
        LasVariant v = service.addVariant(COMPANY, USER, p.id(), "Rosa", "L2024-C", null, 3290, 2);

        LasVariant stocked = service.updateVariant(COMPANY, USER, p.id(), v.id(),
            null, null, null, null, 8, null, false);
        assertThat(stocked.stockQty()).isEqualTo(8);
        assertThat(stocked.color()).isEqualTo("Rosa");      // preservado
        assertThat(stocked.dyeLot()).isEqualTo("L2024-C");  // preservado
        assertThat(stocked.priceCents()).isEqualTo(3290);

        // clearPrice=true → priceCents volta a null (herda o base do produto).
        LasVariant cleared = service.updateVariant(COMPANY, USER, p.id(), v.id(),
            null, null, null, null, null, null, true);
        assertThat(cleared.priceCents()).isNull();
    }

    @Test
    @DisplayName("toggleVariant desliga; deleteVariant remove; variante inexistente → VariantNotFoundException")
    void toggleAndDeleteVariant() {
        LasProduct p = service.create(COMPANY, USER, "Agulha", null, "agulhas", 1990);
        LasVariant v = service.addVariant(COMPANY, USER, p.id(), "Nº 4", "—", null, null, 4);

        LasVariant off = service.toggleVariant(COMPANY, USER, p.id(), v.id(), false);
        assertThat(off.available()).isFalse();

        service.deleteVariant(COMPANY, USER, p.id(), v.id());
        assertThat(service.listVariants(COMPANY, p.id())).isEmpty();

        assertThatThrownBy(() -> service.deleteVariant(COMPANY, USER, p.id(), v.id()))
            .isInstanceOf(VariantNotFoundException.class);
    }

    @Test
    @DisplayName("delete de variante referenciada por pedido → VariantInUseException (409); delete do produto → ProductInUseException")
    void delete_inUse() {
        LasProduct p = service.create(COMPANY, USER, "Lã Merino", null, "las", 1990);
        LasVariant v = service.addVariant(COMPANY, USER, p.id(), "Vermelho", "L2024-D", null, null, 10);

        // Semeia conversa+contato+pedido+order_item referenciando a variante.
        UUID contact = UUID.randomUUID();
        UUID instance = UUID.randomUUID();
        UUID conv = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contact, COMPANY, "+5511999990191", "C");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conv, COMPANY, contact, instance);
        UUID order = jdbcTemplate.queryForObject(
            "insert into las_orders (company_id, conversation_id, contact_id, fulfillment, subtotal_cents, total_cents, delivery_address) "
                + "values (?, ?, ?, 'entrega', 1990, 1990, 'Rua X') returning id", UUID.class, COMPANY, conv, contact);
        jdbcTemplate.update("insert into las_order_items (order_id, variant_id, qtd, unit_price_cents, "
            + "product_name_snapshot, color_snapshot, dye_lot_snapshot) values (?, ?, 1, 1990, 'Lã Merino', 'Vermelho', 'L2024-D')",
            order, v.id());

        assertThatThrownBy(() -> service.deleteVariant(COMPANY, USER, p.id(), v.id()))
            .isInstanceOf(VariantInUseException.class);
        assertThatThrownBy(() -> service.delete(COMPANY, USER, p.id()))
            .isInstanceOf(ProductInUseException.class);
    }
}
