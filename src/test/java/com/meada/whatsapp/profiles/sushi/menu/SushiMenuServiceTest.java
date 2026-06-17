package com.meada.whatsapp.profiles.sushi.menu;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.profiles.sushi.menu.SushiMenuService.MenuItemInUseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o SushiMenuService (camada 7.1): create + audit, update parcial, toggle, delete em uso → 409.
 */
class SushiMenuServiceTest extends AbstractIntegrationTest {

    @Autowired
    private SushiMenuService service;

    private static final UUID COMPANY = UUID.fromString("c5000000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("d5000000-0000-0000-0000-000000000001");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'sushi')",
            COMPANY, "Sushi Teste", "sushi-teste");
        // USER precisa existir em users (FK audit_log_user_id_fkey) — senão o INSERT de audit
        // falha e, mesmo engolido, marca a transação @Transactional como rollback-only.
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@sushi.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("create válido → persiste + audita sushi_menu_item_created")
    void create_persistsAndAudits() {
        SushiMenuItem item = service.create(COMPANY, USER, "Hot Filadélfia", "Cream + salmão",
            3200, "hot_rolls");
        assertThat(item.name()).isEqualTo("Hot Filadélfia");
        assertThat(item.priceCents()).isEqualTo(3200);
        assertThat(item.available()).isTrue();

        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'sushi_menu_item_created' and entity_id = ?",
            Long.class, item.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("create com categoria inválida → InvalidCategoryException")
    void create_invalidCategory() {
        assertThatThrownBy(() -> service.create(COMPANY, USER, "X", null, 100, "pizza"))
            .isInstanceOf(SushiMenuService.InvalidCategoryException.class);
    }

    @Test
    @DisplayName("update parcial (só preço) preserva os demais campos")
    void update_partial() {
        SushiMenuItem item = service.create(COMPANY, USER, "Edamame", "Com sal", 1800, "entradas");
        SushiMenuItem updated = service.update(COMPANY, USER, item.id(), null, null, 2000, null, null);
        assertThat(updated.priceCents()).isEqualTo(2000);
        assertThat(updated.name()).isEqualTo("Edamame");          // preservado
        assertThat(updated.category()).isEqualTo("entradas");     // preservado
    }

    @Test
    @DisplayName("toggle desliga available")
    void toggle() {
        SushiMenuItem item = service.create(COMPANY, USER, "Sashimi", null, 4000, "sashimi");
        SushiMenuItem off = service.toggle(COMPANY, USER, item.id(), false);
        assertThat(off.available()).isFalse();
    }

    @Test
    @DisplayName("delete de item referenciado por pedido → MenuItemInUseException (409)")
    void delete_inUse() {
        SushiMenuItem item = service.create(COMPANY, USER, "Combinado 20", null, 9000, "combinados");
        // Semeia uma conversa+contato+pedido+order_item referenciando o item.
        UUID contact = UUID.randomUUID();
        UUID instance = UUID.randomUUID();
        UUID conv = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contact, COMPANY, "+5511999990001", "C");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conv, COMPANY, contact, instance);
        UUID order = jdbcTemplate.queryForObject(
            "insert into sushi_orders (company_id, conversation_id, contact_id, subtotal_cents, total_cents, delivery_address) "
                + "values (?, ?, ?, 9000, 9000, 'Rua X') returning id", UUID.class, COMPANY, conv, contact);
        jdbcTemplate.update("insert into sushi_order_items (order_id, menu_item_id, qtd, unit_price_cents, item_name_snapshot) "
            + "values (?, ?, 1, 9000, 'Combinado 20')", order, item.id());

        assertThatThrownBy(() -> service.delete(COMPANY, USER, item.id()))
            .isInstanceOf(MenuItemInUseException.class);
    }
}
