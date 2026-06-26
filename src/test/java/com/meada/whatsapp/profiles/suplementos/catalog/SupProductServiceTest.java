package com.meada.whatsapp.profiles.suplementos.catalog;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.profiles.suplementos.catalog.SupProductService.DuplicateVariantException;
import com.meada.whatsapp.profiles.suplementos.catalog.SupProductService.InvalidCategoryException;
import com.meada.whatsapp.profiles.suplementos.catalog.SupProductService.ProductInUseException;
import com.meada.whatsapp.profiles.suplementos.catalog.SupProductService.VariantInUseException;
import com.meada.whatsapp.profiles.suplementos.catalog.SupProductService.VariantNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o SupProductService (camada 8.24): create + audit, category inválida, update parcial, toggle,
 * delete em uso → 409; e o CRUD de VARIANTES (⭐ a grade sabor×peso): add/update/toggle/delete,
 * duplicate_variant 409 (SKU UNIQUE), variant_in_use 409, e a invalidação de cache. Análogo ao
 * LingerieProductServiceTest, adaptado pras categorias de suplementos + a camada de variantes (SEM
 * enum de tamanho — flavor/sizeLabel são texto livre).
 */
class SupProductServiceTest extends AbstractIntegrationTest {

    @Autowired
    private SupProductService service;

    private static final UUID COMPANY = UUID.fromString("c8240000-0000-0000-0000-000000000091");
    private static final UUID USER = UUID.fromString("d8240000-0000-0000-0000-000000000091");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'suplementos')",
            COMPANY, "Suplementos Teste", "suplementos-teste");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@sup.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("create válido → persiste + audita sup_product_created")
    void create_persistsAndAudits() {
        SupProduct p = service.create(COMPANY, USER, "Whey Protein", "Growth", "proteinas", "Concentrado");
        assertThat(p.name()).isEqualTo("Whey Protein");
        assertThat(p.brand()).isEqualTo("Growth");
        assertThat(p.active()).isTrue();

        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'sup_product_created' and entity_id = ?",
            Long.class, p.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("create com categoria inválida → InvalidCategoryException")
    void create_invalidCategory() {
        assertThatThrownBy(() -> service.create(COMPANY, USER, "X", null, "anabolizante", null))
            .isInstanceOf(InvalidCategoryException.class);
    }

    @Test
    @DisplayName("update parcial (só marca) preserva os demais campos")
    void update_partial() {
        SupProduct p = service.create(COMPANY, USER, "BCAA", "Max Titanium", "aminoacidos", null);
        SupProduct updated = service.update(COMPANY, USER, p.id(), null, "Integral Medica", null, null, null);
        assertThat(updated.brand()).isEqualTo("Integral Medica");
        assertThat(updated.name()).isEqualTo("BCAA");          // preservado
        assertThat(updated.category()).isEqualTo("aminoacidos"); // preservado
    }

    @Test
    @DisplayName("toggle desliga active")
    void toggle() {
        SupProduct p = service.create(COMPANY, USER, "Pré-treino", null, "pre_treino", null);
        SupProduct off = service.toggle(COMPANY, USER, p.id(), false);
        assertThat(off.active()).isFalse();
    }

    // ---- Variantes (⭐ a grade sabor×peso) -----------------------------------

    @Test
    @DisplayName("addVariant → persiste a variante + audita + aparece no produto")
    void addVariant_persistsAndAudits() {
        SupProduct p = service.create(COMPANY, USER, "Whey", null, "proteinas", null);
        SupVariant v = service.addVariant(COMPANY, USER, p.id(), "Chocolate", "900g", "SKU-1", 14990, 5, null);
        assertThat(v.flavor()).isEqualTo("Chocolate");
        assertThat(v.sizeLabel()).isEqualTo("900g");
        assertThat(v.priceCents()).isEqualTo(14990);
        assertThat(v.stockQuantity()).isEqualTo(5);
        assertThat(v.active()).isTrue();
        assertThat(v.label()).isEqualTo("Chocolate 900g");

        List<SupVariant> variants = service.listVariants(COMPANY, p.id());
        assertThat(variants).hasSize(1);

        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'sup_variant_created' and entity_id = ?",
            Long.class, v.id());
        assertThat(audit).isEqualTo(1L);

        // produto hidratado já traz a variante embutida.
        assertThat(service.get(COMPANY, p.id()).orElseThrow().variants()).hasSize(1);
    }

    @Test
    @DisplayName("addVariant sem sabor → label = só o peso (acessório/cápsula)")
    void addVariant_noFlavor() {
        SupProduct p = service.create(COMPANY, USER, "Coqueteleira", null, "acessorios", null);
        SupVariant v = service.addVariant(COMPANY, USER, p.id(), null, "600ml", null, 2990, 10, null);
        assertThat(v.flavor()).isNull();
        assertThat(v.label()).isEqualTo("600ml");
    }

    @Test
    @DisplayName("addVariant com SKU duplicado (mesmo sku no tenant) → DuplicateVariantException (409)")
    void addVariant_duplicateSku() {
        SupProduct p = service.create(COMPANY, USER, "Creatina", null, "aminoacidos", null);
        service.addVariant(COMPANY, USER, p.id(), null, "300g", "SKU-CR", 9990, 3, null);
        assertThatThrownBy(() -> service.addVariant(COMPANY, USER, p.id(), null, "150g", "SKU-CR", 5990, 9, null))
            .isInstanceOf(DuplicateVariantException.class);
    }

    @Test
    @DisplayName("updateVariant parcial (só estoque) preserva sabor/peso; clearFlavor limpa o sabor")
    void updateVariant_partialAndClearFlavor() {
        SupProduct p = service.create(COMPANY, USER, "Whey", null, "proteinas", null);
        SupVariant v = service.addVariant(COMPANY, USER, p.id(), "Baunilha", "2kg", null, 29990, 2, null);

        SupVariant stocked = service.updateVariant(COMPANY, USER, p.id(), v.id(),
            null, null, null, null, 8, null, false, null, false);
        assertThat(stocked.stockQuantity()).isEqualTo(8);
        assertThat(stocked.flavor()).isEqualTo("Baunilha");   // preservado
        assertThat(stocked.sizeLabel()).isEqualTo("2kg");     // preservado
        assertThat(stocked.priceCents()).isEqualTo(29990);

        // clearFlavor=true → flavor volta a null.
        SupVariant cleared = service.updateVariant(COMPANY, USER, p.id(), v.id(),
            null, null, null, null, null, null, false, null, true);
        assertThat(cleared.flavor()).isNull();
    }

    @Test
    @DisplayName("toggleVariant desliga; deleteVariant remove; variante inexistente → VariantNotFoundException")
    void toggleAndDeleteVariant() {
        SupProduct p = service.create(COMPANY, USER, "Multivitamínico", null, "vitaminas", null);
        SupVariant v = service.addVariant(COMPANY, USER, p.id(), null, "120 caps", null, 4990, 4, null);

        SupVariant off = service.toggleVariant(COMPANY, USER, p.id(), v.id(), false);
        assertThat(off.active()).isFalse();

        service.deleteVariant(COMPANY, USER, p.id(), v.id());
        assertThat(service.listVariants(COMPANY, p.id())).isEmpty();

        assertThatThrownBy(() -> service.deleteVariant(COMPANY, USER, p.id(), v.id()))
            .isInstanceOf(VariantNotFoundException.class);
    }

    @Test
    @DisplayName("delete de variante referenciada por pedido → VariantInUseException (409); delete do produto → ProductInUseException")
    void delete_inUse() {
        SupProduct p = service.create(COMPANY, USER, "Whey", null, "proteinas", null);
        SupVariant v = service.addVariant(COMPANY, USER, p.id(), "Morango", "900g", null, 14990, 10, null);

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
            "insert into sup_orders (company_id, conversation_id, contact_id, subtotal_cents, total_cents, delivery_address) "
                + "values (?, ?, ?, 14990, 14990, 'Rua X') returning id", UUID.class, COMPANY, conv, contact);
        jdbcTemplate.update("insert into sup_order_items (order_id, product_id, variant_id, qtd, unit_price_cents, "
            + "product_name_snapshot, variant_label_snapshot) values (?, ?, ?, 1, 14990, 'Whey', 'Morango 900g')",
            order, p.id(), v.id());

        assertThatThrownBy(() -> service.deleteVariant(COMPANY, USER, p.id(), v.id()))
            .isInstanceOf(VariantInUseException.class);
        assertThatThrownBy(() -> service.delete(COMPANY, USER, p.id()))
            .isInstanceOf(ProductInUseException.class);
    }
}
