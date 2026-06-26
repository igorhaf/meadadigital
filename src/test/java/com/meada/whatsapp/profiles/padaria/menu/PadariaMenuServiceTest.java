package com.meada.whatsapp.profiles.padaria.menu;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.profiles.padaria.menu.PadariaMenuService.MenuItemInUseException;
import com.meada.whatsapp.profiles.padaria.menu.PadariaMenuService.OptionNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o PadariaMenuService (camada 8.8 / perfil padaria): create + audit, validação de categoria,
 * update parcial (incl. made_to_order/lead_time da ESCAPADA 1), toggle, delete em uso → 409, e o CRUD
 * de OPÇÕES (ESCAPADA 2). Clone do FloriculturaCatalogServiceTest (catalog→menu) + os campos da
 * escapada.
 */
class PadariaMenuServiceTest extends AbstractIntegrationTest {

    @Autowired
    private PadariaMenuService service;

    private static final UUID COMPANY = UUID.fromString("c8800000-0000-0000-0000-000000000071");
    private static final UUID USER = UUID.fromString("d8800000-0000-0000-0000-000000000071");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'padaria')",
            COMPANY, "Padaria Teste", "padaria-teste");
        // USER precisa existir em users (FK audit_log_user_id_fkey) — senão o INSERT de audit
        // falha e, mesmo engolido, marca a transação @Transactional como rollback-only.
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@padaria.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("create válido → persiste + audita padaria_menu_item_created")
    void create_persistsAndAudits() {
        PadariaMenuItem item = service.create(COMPANY, USER, "Pão Francês", "Crocante", 100, "paes",
            false, null, "Glúten");
        assertThat(item.name()).isEqualTo("Pão Francês");
        assertThat(item.priceCents()).isEqualTo(100);
        assertThat(item.madeToOrder()).isFalse();
        assertThat(item.allergens()).isEqualTo("Glúten");
        assertThat(item.available()).isTrue();

        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'padaria_menu_item_created' and entity_id = ?",
            Long.class, item.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("create de bolo SOB ENCOMENDA com lead próprio (ESCAPADA 1) → persiste made_to_order + lead")
    void create_madeToOrderWithLead() {
        PadariaMenuItem bolo = service.create(COMPANY, USER, "Bolo de Chocolate", null, 8000,
            "bolos_encomenda", true, 3, null);
        assertThat(bolo.madeToOrder()).isTrue();
        assertThat(bolo.leadTimeDays()).isEqualTo(3);
    }

    @Test
    @DisplayName("create com categoria inválida → InvalidCategoryException")
    void create_invalidCategory() {
        assertThatThrownBy(() -> service.create(COMPANY, USER, "X", null, 100, "hot_rolls", false, null, null))
            .isInstanceOf(PadariaMenuService.InvalidCategoryException.class);
    }

    @Test
    @DisplayName("update parcial (só preço) preserva os demais campos")
    void update_partial() {
        PadariaMenuItem item = service.create(COMPANY, USER, "Coxinha", "Frango", 600, "salgados", false, null, null);
        PadariaMenuItem updated = service.update(COMPANY, USER, item.id(), null, null, 700, null,
            null, null, false, null, null);
        assertThat(updated.priceCents()).isEqualTo(700);
        assertThat(updated.name()).isEqualTo("Coxinha");      // preservado
        assertThat(updated.category()).isEqualTo("salgados"); // preservado
    }

    @Test
    @DisplayName("update clearLeadTime zera lead_time_days (volta ao default da config)")
    void update_clearLeadTime() {
        PadariaMenuItem bolo = service.create(COMPANY, USER, "Torta", null, 5000, "tortas", true, 5, null);
        assertThat(bolo.leadTimeDays()).isEqualTo(5);
        PadariaMenuItem cleared = service.update(COMPANY, USER, bolo.id(), null, null, null, null,
            null, null, true, null, null);
        assertThat(cleared.leadTimeDays()).isNull();
    }

    @Test
    @DisplayName("toggle desliga available")
    void toggle() {
        PadariaMenuItem item = service.create(COMPANY, USER, "Sonho", null, 450, "doces_balcao", false, null, null);
        PadariaMenuItem off = service.toggle(COMPANY, USER, item.id(), false);
        assertThat(off.available()).isFalse();
    }

    @Test
    @DisplayName("delete de item referenciado por pedido → MenuItemInUseException (409)")
    void delete_inUse() {
        PadariaMenuItem item = service.create(COMPANY, USER, "Bolo Família", null, 9000, "bolos_encomenda", true, 2, null);
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
            "insert into padaria_orders (company_id, conversation_id, contact_id, fulfillment, subtotal_cents, total_cents, "
                + "pickup_or_delivery_date, delivery_period) "
                + "values (?, ?, ?, 'retirada', 9000, 9000, current_date + 2, 'manha') returning id",
            UUID.class, COMPANY, conv, contact);
        jdbcTemplate.update("insert into padaria_order_items (order_id, menu_item_id, qtd, unit_price_cents, item_name_snapshot, made_to_order_snapshot) "
            + "values (?, ?, 1, 9000, 'Bolo Família', true)", order, item.id());

        assertThatThrownBy(() -> service.delete(COMPANY, USER, item.id()))
            .isInstanceOf(MenuItemInUseException.class);
    }

    // ---- Opções (ESCAPADA 2) ------------------------------------------------

    @Test
    @DisplayName("addOption → persiste a opção + audita padaria_menu_option_created; cache invalidado implícito")
    void addOption_persistsAndAudits() {
        PadariaMenuItem item = service.create(COMPANY, USER, "Bolo", null, 7000, "bolos_encomenda", true, 2, null);
        PadariaMenuOption opt = service.addOption(COMPANY, USER, item.id(), "Recheio", "Brigadeiro", 500, 0);
        assertThat(opt.groupLabel()).isEqualTo("Recheio");
        assertThat(opt.optionLabel()).isEqualTo("Brigadeiro");
        assertThat(opt.priceDeltaCents()).isEqualTo(500);
        assertThat(opt.available()).isTrue();

        List<PadariaMenuOption> options = service.listOptions(COMPANY, item.id());
        assertThat(options).hasSize(1);

        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'padaria_menu_option_created' and entity_id = ?",
            Long.class, opt.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("updateOption parcial (só delta) preserva os demais campos")
    void updateOption_partial() {
        PadariaMenuItem item = service.create(COMPANY, USER, "Bolo Grande", null, 9000, "bolos_encomenda", true, 3, null);
        PadariaMenuOption opt = service.addOption(COMPANY, USER, item.id(), "Tamanho", "1kg", 800, 1);
        PadariaMenuOption updated = service.updateOption(COMPANY, USER, item.id(), opt.id(),
            null, null, 1000, null, null);
        assertThat(updated.priceDeltaCents()).isEqualTo(1000);
        assertThat(updated.groupLabel()).isEqualTo("Tamanho");   // preservado
        assertThat(updated.optionLabel()).isEqualTo("1kg");      // preservado
    }

    @Test
    @DisplayName("toggleOption desliga available; deleteOption remove; opção inexistente → OptionNotFoundException")
    void toggleAndDeleteOption() {
        PadariaMenuItem item = service.create(COMPANY, USER, "Bolo Festa", null, 12000, "bolos_encomenda", true, 4, null);
        PadariaMenuOption opt = service.addOption(COMPANY, USER, item.id(), "Sabor", "Morango", 300, 0);

        PadariaMenuOption off = service.toggleOption(COMPANY, USER, item.id(), opt.id(), false);
        assertThat(off.available()).isFalse();

        service.deleteOption(COMPANY, USER, item.id(), opt.id());
        assertThat(service.listOptions(COMPANY, item.id())).isEmpty();

        // deletar de novo (já não existe) → OptionNotFoundException.
        assertThatThrownBy(() -> service.deleteOption(COMPANY, USER, item.id(), opt.id()))
            .isInstanceOf(OptionNotFoundException.class);
    }
}
