package com.meada.profiles.lavanderia.orders;

import com.meada.AbstractIntegrationTest;
import com.meada.profiles.lavanderia.config.LavanderiaConfigService;
import com.meada.profiles.lavanderia.services.LavanderiaService;
import com.meada.profiles.lavanderia.services.LavanderiaServiceCatalogService;
import com.meada.profiles.lavanderia.services.LavanderiaServiceOption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o PedidoLavanderiaConfirmHandler (camada 8.10): parse da tag {@code <pedido_lavanderia>} +
 * create, com a ESCAPADA das DUAS DATAS (collect_date >= hoje + delivery_date opcional materializada /
 * validada por turnaround) e os modifiers herdados (unit_price = base + Σ deltas; option fantasma
 * ABORTA). O {@code total_cents} mentiroso da IA é sempre DESCARTADO.
 */
class PedidoLavanderiaConfirmHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private PedidoLavanderiaConfirmHandler handler;
    @Autowired
    private LavanderiaServiceCatalogService catalogService;
    @Autowired
    private LavanderiaConfigService configService;

    private static final UUID COMPANY = UUID.fromString("1a000000-0000-0000-0000-000000000072");
    private static final UUID USER = UUID.fromString("1b000000-0000-0000-0000-000000000072");
    private UUID conversationId;
    private UUID contactId;

    private static LocalDate today() {
        return LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    }

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'lavanderia')",
            COMPANY, "Lavanderia H", "lavanderia-h");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@lavanderia-h.dev', 'admin')",
            USER, COMPANY);
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990172", "Cliente");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
        configService.update(COMPANY, USER, 700, 0, 1, true, 50, 1, true, true, 2, false, 30, null);   // taxa 700.
    }

    private LavanderiaService svc(String name, int price, int turnaround) {
        return catalogService.create(COMPANY, USER, name, null, price, "lavar", turnaround, null);
    }

    @Test
    @DisplayName("tag simples: cria pedido, delivery_date omitida materializa = collect + turnaround")
    void parseAndCreate_simple_materializes() {
        LavanderiaService s = svc("Lavar camisa", 800, 2);
        String collect = today().plusDays(1).toString();
        String aiText = "Pronto!\n<pedido_lavanderia>{\"collect_date\":\"" + collect + "\",\"period\":\"manha\","
            + "\"delivery_address\":\"Rua das Roupas 10\",\"delivery_date\":null,"
            + "\"items\":[{\"service_id\":\"" + s.id() + "\",\"qty\":3}],\"total_cents\":99999}</pedido_lavanderia>";

        Optional<LavanderiaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(order).isPresent();
        assertThat(order.get().collectDate().toString()).isEqualTo(collect);
        assertThat(order.get().deliveryDate()).isEqualTo(today().plusDays(1).plusDays(2));
        assertThat(order.get().subtotalCents()).isEqualTo(2400);
        assertThat(order.get().totalCents()).isEqualTo(3100);   // 2400 + 700 (descarta 99999 da IA)
        assertThat(order.get().status()).isEqualTo("aguardando");
        assertThat(order.get().period()).isEqualTo("manha");
    }

    @Test
    @DisplayName("tag com opções + delivery_date explícita válida → cria; unit_price = base + Σ deltas")
    void parseAndCreate_withOptions_twoDates() {
        LavanderiaService s = svc("Lavar e passar", 1200, 2);
        LavanderiaServiceOption engomar = catalogService.addOption(COMPANY, USER, s.id(), "Acabamento", "Engomar", 300, 0);
        String collect = today().plusDays(1).toString();
        String delivery = today().plusDays(4).toString();   // >= collect + 2.
        String aiText = "Fechado!\n<pedido_lavanderia>{\"collect_date\":\"" + collect + "\",\"period\":\"tarde\","
            + "\"delivery_address\":\"Rua Y 20\",\"delivery_date\":\"" + delivery + "\","
            + "\"items\":[{\"service_id\":\"" + s.id() + "\",\"qty\":2,\"options\":[\"" + engomar.id() + "\"]}]"
            + ",\"total_cents\":0}</pedido_lavanderia>";

        Optional<LavanderiaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(order).isPresent();
        assertThat(order.get().items().get(0).unitPriceCents()).isEqualTo(1500);
        assertThat(order.get().items().get(0).options()).hasSize(1);
        assertThat(order.get().subtotalCents()).isEqualTo(3000);
        assertThat(order.get().totalCents()).isEqualTo(3700);
        assertThat(order.get().deliveryDate().toString()).isEqualTo(delivery);
        assertThat(order.get().period()).isEqualTo("tarde");
    }

    @Test
    @DisplayName("delivery_date INVÁLIDA (antes de collect+turnaround) → Optional.empty + 0 pedidos")
    void parseAndCreate_deliveryTooSoon_empty() {
        LavanderiaService s = svc("Lavagem a seco", 3000, 3);
        String collect = today().plusDays(1).toString();
        String delivery = today().plusDays(2).toString();   // antes de collect + 3.
        String aiText = "<pedido_lavanderia>{\"collect_date\":\"" + collect + "\",\"period\":\"manha\","
            + "\"delivery_address\":\"Rua Z\",\"delivery_date\":\"" + delivery + "\","
            + "\"items\":[{\"service_id\":\"" + s.id() + "\",\"qty\":1}]}</pedido_lavanderia>";
        Optional<LavanderiaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isEmpty();
        assertThat(jdbcTemplate.queryForObject("select count(*) from lavanderia_orders", Long.class)).isZero();
    }

    @Test
    @DisplayName("collect_date no PASSADO → Optional.empty + 0 pedidos")
    void parseAndCreate_pastCollect_empty() {
        LavanderiaService s = svc("Lavar", 800, 1);
        String ontem = today().minusDays(1).toString();
        String aiText = "<pedido_lavanderia>{\"collect_date\":\"" + ontem + "\",\"period\":\"manha\","
            + "\"delivery_address\":\"Rua X\",\"items\":[{\"service_id\":\"" + s.id() + "\",\"qty\":1}]}</pedido_lavanderia>";
        Optional<LavanderiaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isEmpty();
        assertThat(jdbcTemplate.queryForObject("select count(*) from lavanderia_orders", Long.class)).isZero();
    }

    @Test
    @DisplayName("option_id fantasma → Optional.empty + 0 pedidos")
    void parseAndCreate_invalidOption_empty() {
        LavanderiaService s = svc("Lavar", 800, 1);
        LavanderiaService outro = svc("Passar", 500, 1);
        LavanderiaServiceOption optDeOutro = catalogService.addOption(COMPANY, USER, outro.id(), "Acabamento", "Vapor", 100, 0);
        String collect = today().plusDays(1).toString();
        String aiText = "<pedido_lavanderia>{\"collect_date\":\"" + collect + "\",\"period\":\"tarde\","
            + "\"delivery_address\":\"Rua Z\",\"items\":[{\"service_id\":\"" + s.id() + "\",\"qty\":1,"
            + "\"options\":[\"" + optDeOutro.id() + "\"]}]}</pedido_lavanderia>";
        Optional<LavanderiaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isEmpty();
        assertThat(jdbcTemplate.queryForObject("select count(*) from lavanderia_orders", Long.class)).isZero();
    }

    @Test
    @DisplayName("texto sem tag → Optional.empty (conversa normal)")
    void parseAndCreate_noTag() {
        Optional<LavanderiaOrder> order = handler.parseAndCreate(
            COMPANY, conversationId, contactId, "Oi! Quer ver nossos serviços de lavanderia?");
        assertThat(order).isEmpty();
    }

    @Test
    @DisplayName("tag sem endereço → Optional.empty + 0 pedidos")
    void parseAndCreate_noAddress() {
        LavanderiaService s = svc("Lavar", 800, 1);
        String collect = today().plusDays(1).toString();
        String aiText = "<pedido_lavanderia>{\"collect_date\":\"" + collect + "\",\"period\":\"manha\","
            + "\"items\":[{\"service_id\":\"" + s.id() + "\",\"qty\":1}]}</pedido_lavanderia>";
        Optional<LavanderiaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isEmpty();
        assertThat(jdbcTemplate.queryForObject("select count(*) from lavanderia_orders", Long.class)).isZero();
    }

    @Test
    @DisplayName("item_id inexistente → Optional.empty + 0 pedidos")
    void parseAndCreate_invalidItem() {
        String collect = today().plusDays(1).toString();
        String aiText = "<pedido_lavanderia>{\"collect_date\":\"" + collect + "\",\"period\":\"manha\","
            + "\"delivery_address\":\"Rua X\",\"items\":[{\"service_id\":\"" + UUID.randomUUID() + "\",\"qty\":1}]}</pedido_lavanderia>";
        Optional<LavanderiaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isEmpty();
        assertThat(jdbcTemplate.queryForObject("select count(*) from lavanderia_orders", Long.class)).isZero();
    }
}
