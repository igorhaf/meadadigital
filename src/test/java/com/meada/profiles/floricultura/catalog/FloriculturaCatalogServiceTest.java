package com.meada.profiles.floricultura.catalog;

import com.meada.AbstractIntegrationTest;
import com.meada.profiles.floricultura.catalog.FloriculturaCatalogService.CatalogItemInUseException;
import com.meada.profiles.floricultura.catalog.FloriculturaCatalogService.OptionNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o FloriculturaCatalogService (camada 8.4): create + audit, update parcial, toggle, delete em uso → 409,
 * e o CRUD de OPÇÕES (ESCAPADA 2: addOption/updateOption/toggleOption/deleteOption). Clone do
 * SushiCatalogServiceTest + as opções.
 */
class FloriculturaCatalogServiceTest extends AbstractIntegrationTest {

    @Autowired
    private FloriculturaCatalogService service;

    private static final UUID COMPANY = UUID.fromString("c8000000-0000-0000-0000-000000000071");
    private static final UUID USER = UUID.fromString("d8000000-0000-0000-0000-000000000071");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'floricultura')",
            COMPANY, "Floricultura Teste", "floricultura-teste");
        // USER precisa existir em users (FK audit_log_user_id_fkey) — senão o INSERT de audit
        // falha e, mesmo engolido, marca a transação @Transactional como rollback-only.
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@floricultura.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("create válido → persiste + audita floricultura_catalog_item_created")
    void create_persistsAndAudits() {
        FloriculturaCatalogItem item = service.create(COMPANY, USER, "X-Burger", "Pão, carne, queijo",
            2500, "buques", false);
        assertThat(item.name()).isEqualTo("X-Burger");
        assertThat(item.priceCents()).isEqualTo(2500);
        assertThat(item.available()).isTrue();

        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'floricultura_catalog_item_created' and entity_id = ?",
            Long.class, item.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("create com categoria inválida → InvalidCategoryException")
    void create_invalidCategory() {
        assertThatThrownBy(() -> service.create(COMPANY, USER, "X", null, 100, "hot_rolls", false))
            .isInstanceOf(FloriculturaCatalogService.InvalidCategoryException.class);
    }

    @Test
    @DisplayName("update parcial (só preço) preserva os demais campos")
    void update_partial() {
        FloriculturaCatalogItem item = service.create(COMPANY, USER, "Coca-Cola", "Lata 350ml", 600, "coroas", false);
        FloriculturaCatalogItem updated = service.update(COMPANY, USER, item.id(), null, null, 700, null, null, null);
        assertThat(updated.priceCents()).isEqualTo(700);
        assertThat(updated.name()).isEqualTo("Coca-Cola");      // preservado
        assertThat(updated.category()).isEqualTo("coroas");    // preservado
    }

    @Test
    @DisplayName("toggle desliga available")
    void toggle() {
        FloriculturaCatalogItem item = service.create(COMPANY, USER, "Pizza Calabresa", null, 4500, "arranjos", false);
        FloriculturaCatalogItem off = service.toggle(COMPANY, USER, item.id(), false);
        assertThat(off.available()).isFalse();
    }

    @Test
    @DisplayName("delete de item referenciado por pedido → CatalogItemInUseException (409)")
    void delete_inUse() {
        FloriculturaCatalogItem item = service.create(COMPANY, USER, "Combo Família", null, 9000, "buques", false);
        // Semeia uma conversa+contato+pedido+order_item referenciando o item.
        UUID contact = UUID.randomUUID();
        UUID instance = UUID.randomUUID();
        UUID conv = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contact, COMPANY, "+5511999990071", "C");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conv, COMPANY, contact, instance);
        UUID order = jdbcTemplate.queryForObject(
            "insert into floricultura_orders (company_id, conversation_id, contact_id, subtotal_cents, total_cents, delivery_address, "
                + "delivery_date, delivery_period, recipient_name) "
                + "values (?, ?, ?, 9000, 9000, 'Rua X', current_date + 1, 'manha', 'Maria') returning id",
            UUID.class, COMPANY, conv, contact);
        jdbcTemplate.update("insert into floricultura_order_items (order_id, catalog_item_id, qtd, unit_price_cents, item_name_snapshot) "
            + "values (?, ?, 1, 9000, 'Combo Família')", order, item.id());

        assertThatThrownBy(() -> service.delete(COMPANY, USER, item.id()))
            .isInstanceOf(CatalogItemInUseException.class);
    }

    // ---- Opções (ESCAPADA 2) ------------------------------------------------

    @Test
    @DisplayName("addOption → persiste a opção + audita floricultura_catalog_option_created")
    void addOption_persistsAndAudits() {
        FloriculturaCatalogItem item = service.create(COMPANY, USER, "X-Salada", null, 2700, "buques", false);
        FloriculturaCatalogOption opt = service.addOption(COMPANY, USER, item.id(), "Adicionais", "Bacon", 300, 0);
        assertThat(opt.groupLabel()).isEqualTo("Adicionais");
        assertThat(opt.optionLabel()).isEqualTo("Bacon");
        assertThat(opt.priceDeltaCents()).isEqualTo(300);
        assertThat(opt.available()).isTrue();

        List<FloriculturaCatalogOption> options = service.listOptions(COMPANY, item.id());
        assertThat(options).hasSize(1);

        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'floricultura_catalog_option_created' and entity_id = ?",
            Long.class, opt.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("updateOption parcial (só delta) preserva os demais campos")
    void updateOption_partial() {
        FloriculturaCatalogItem item = service.create(COMPANY, USER, "Pizza Grande", null, 5000, "arranjos", false);
        FloriculturaCatalogOption opt = service.addOption(COMPANY, USER, item.id(), "Borda", "Catupiry", 800, 1);
        FloriculturaCatalogOption updated = service.updateOption(COMPANY, USER, item.id(), opt.id(),
            null, null, 1000, null, null);
        assertThat(updated.priceDeltaCents()).isEqualTo(1000);
        assertThat(updated.groupLabel()).isEqualTo("Borda");        // preservado
        assertThat(updated.optionLabel()).isEqualTo("Catupiry");    // preservado
    }

    @Test
    @DisplayName("toggleOption desliga available; deleteOption remove; opção inexistente → OptionNotFoundException")
    void toggleAndDeleteOption() {
        FloriculturaCatalogItem item = service.create(COMPANY, USER, "Porção Fritas", null, 2000, "plantas", false);
        FloriculturaCatalogOption opt = service.addOption(COMPANY, USER, item.id(), "Tamanho", "Grande", 500, 0);

        FloriculturaCatalogOption off = service.toggleOption(COMPANY, USER, item.id(), opt.id(), false);
        assertThat(off.available()).isFalse();

        service.deleteOption(COMPANY, USER, item.id(), opt.id());
        assertThat(service.listOptions(COMPANY, item.id())).isEmpty();

        // deletar de novo (já não existe) → OptionNotFoundException.
        assertThatThrownBy(() -> service.deleteOption(COMPANY, USER, item.id(), opt.id()))
            .isInstanceOf(OptionNotFoundException.class);
    }
}
