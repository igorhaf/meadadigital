package com.meada.profiles.floricultura.orders;

import com.meada.AbstractIntegrationTest;
import com.meada.profiles.floricultura.catalog.FloriculturaCatalogItem;
import com.meada.profiles.floricultura.catalog.FloriculturaCatalogOption;
import com.meada.profiles.floricultura.catalog.FloriculturaCatalogService;
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
 * Testa o PedidoFlorConfirmHandler (camada 8.5): parse da tag {@code <pedido_flor>} + create, com a
 * ESCAPADA da floricultura (entrega AGENDADA: data_entrega >= hoje + período + destinatário + cartão)
 * e os modifiers herdados do comida (unit_price = base + Σ deltas; option_id fantasma ABORTA). O
 * {@code total_cents} mentiroso da IA é sempre DESCARTADO. Clone do PedidoComidaConfirmHandlerTest +
 * a validação da data de entrega.
 */
class PedidoFlorConfirmHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private PedidoFlorConfirmHandler handler;
    @Autowired
    private FloriculturaCatalogService catalogService;

    private static final UUID COMPANY = UUID.fromString("c8000000-0000-0000-0000-000000000072");
    private static final UUID USER = UUID.fromString("d8000000-0000-0000-0000-000000000072");
    private UUID conversationId;
    private UUID contactId;

    /** Uma data de entrega futura (amanhã, fuso America/Sao_Paulo) — sempre válida. */
    private static String amanha() {
        return LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(1).toString();
    }

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'floricultura')",
            COMPANY, "Floricultura H", "floricultura-h");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@floricultura-h.dev', 'admin')",
            USER, COMPANY);
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990072", "Cliente");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
        jdbcTemplate.update("insert into floricultura_config (company_id, delivery_fee_cents) values (?, 700)", COMPANY);
    }

    @Test
    @DisplayName("tag com itens válidos COM opções + entrega agendada → cria pedido, unit_price = base + Σ deltas, total descarta o da IA")
    void parseAndCreate_withOptions() {
        FloriculturaCatalogItem buque = catalogService.create(COMPANY, USER, "Buquê de Rosas", null, 2500, "buques", false);
        FloriculturaCatalogOption grande = catalogService.addOption(COMPANY, USER, buque.id(), "Tamanho", "Grande", 300, 0);
        FloriculturaCatalogOption vermelho = catalogService.addOption(COMPANY, USER, buque.id(), "Cor", "Vermelho", 200, 1);

        String aiText = "Confirmado: Buquê de Rosas (Grande, Vermelho), entrega amanhã de manhã para Maria. Total R$ 67.\n"
            + "<pedido_flor>{\"items\":[{\"item_id\":\"" + buque.id() + "\",\"qtd\":2,"
            + "\"options\":[\"" + grande.id() + "\",\"" + vermelho.id() + "\"]}],"
            + "\"endereco\":\"Rua das Flores 10\",\"data_entrega\":\"" + amanha() + "\",\"periodo\":\"manha\","
            + "\"destinatario\":\"Maria\",\"cartao\":\"Feliz aniversário!\",\"total_cents\":99999}</pedido_flor>";

        Optional<FloriculturaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(order).isPresent();
        // unit_price = 2500 + 300 + 200 = 3000; subtotal = 3000*2 = 6000; total = 6000 + 700 = 6700.
        assertThat(order.get().items()).hasSize(1);
        assertThat(order.get().items().get(0).unitPriceCents()).isEqualTo(3000);
        assertThat(order.get().items().get(0).options()).hasSize(2);
        assertThat(order.get().subtotalCents()).isEqualTo(6000);
        assertThat(order.get().totalCents()).isEqualTo(6700);
        assertThat(order.get().status()).isEqualTo("aguardando");
        // ESCAPADA: dados de entrega agendada gravados.
        assertThat(order.get().deliveryDate().toString()).isEqualTo(amanha());
        assertThat(order.get().deliveryPeriod()).isEqualTo("manha");
        assertThat(order.get().recipientName()).isEqualTo("Maria");
        assertThat(order.get().cardMessage()).isEqualTo("Feliz aniversário!");
    }

    @Test
    @DisplayName("item sem opções + sem cartão → cria com unit_price = base, cardMessage null")
    void parseAndCreate_noOptions() {
        FloriculturaCatalogItem planta = catalogService.create(COMPANY, USER, "Suculenta", null, 600, "plantas", false);
        String aiText = "Beleza!\n<pedido_flor>{\"items\":[{\"item_id\":\"" + planta.id() + "\",\"qtd\":3}],"
            + "\"endereco\":\"Rua Y 20\",\"data_entrega\":\"" + amanha() + "\",\"periodo\":\"tarde\","
            + "\"destinatario\":\"João\",\"total_cents\":0}</pedido_flor>";

        Optional<FloriculturaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(order).isPresent();
        assertThat(order.get().items().get(0).unitPriceCents()).isEqualTo(600);
        assertThat(order.get().items().get(0).options()).isEmpty();
        assertThat(order.get().subtotalCents()).isEqualTo(1800);
        assertThat(order.get().totalCents()).isEqualTo(2500);
        assertThat(order.get().cardMessage()).isNull();
        assertThat(order.get().deliveryPeriod()).isEqualTo("tarde");
    }

    @Test
    @DisplayName("data_entrega no PASSADO → Optional.empty + 0 pedidos (ESCAPADA: entrega agendada)")
    void parseAndCreate_pastDate_aborts() {
        FloriculturaCatalogItem buque = catalogService.create(COMPANY, USER, "Buquê", null, 2000, "buques", false);
        String ontem = LocalDate.now(ZoneId.of("America/Sao_Paulo")).minusDays(1).toString();
        String aiText = "Confirmado!\n<pedido_flor>{\"items\":[{\"item_id\":\"" + buque.id() + "\",\"qtd\":1}],"
            + "\"endereco\":\"Rua X\",\"data_entrega\":\"" + ontem + "\",\"periodo\":\"manha\","
            + "\"destinatario\":\"Ana\",\"total_cents\":2000}</pedido_flor>";
        Optional<FloriculturaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isEmpty();
        assertThat(jdbcTemplate.queryForObject("select count(*) from floricultura_orders", Long.class)).isZero();
    }

    @Test
    @DisplayName("tag sem destinatário → Optional.empty (ESCAPADA: flor é pra alguém)")
    void parseAndCreate_noRecipient_aborts() {
        FloriculturaCatalogItem buque = catalogService.create(COMPANY, USER, "Buquê", null, 2000, "buques", false);
        String aiText = "Confirmado!\n<pedido_flor>{\"items\":[{\"item_id\":\"" + buque.id() + "\",\"qtd\":1}],"
            + "\"endereco\":\"Rua X\",\"data_entrega\":\"" + amanha() + "\",\"periodo\":\"manha\","
            + "\"total_cents\":2000}</pedido_flor>";
        Optional<FloriculturaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isEmpty();
        assertThat(jdbcTemplate.queryForObject("select count(*) from floricultura_orders", Long.class)).isZero();
    }

    @Test
    @DisplayName("item_id inexistente na tag → Optional.empty (pedido não criado)")
    void parseAndCreate_invalidItem() {
        String aiText = "Confirmado!\n<pedido_flor>{\"items\":[{\"item_id\":\""
            + UUID.randomUUID() + "\",\"qtd\":1}],\"endereco\":\"Rua X\",\"data_entrega\":\"" + amanha()
            + "\",\"periodo\":\"manha\",\"destinatario\":\"Ana\",\"total_cents\":1000}</pedido_flor>";
        Optional<FloriculturaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isEmpty();
        assertThat(jdbcTemplate.queryForObject("select count(*) from floricultura_orders", Long.class)).isZero();
    }

    @Test
    @DisplayName("option_id fantasma (não pertence ao item) → Optional.empty + 0 pedidos")
    void parseAndCreate_invalidOption_aborts() {
        FloriculturaCatalogItem buque = catalogService.create(COMPANY, USER, "Buquê Misto", null, 3000, "buques", false);
        FloriculturaCatalogItem outro = catalogService.create(COMPANY, USER, "Cesta", null, 4000, "cestas", false);
        FloriculturaCatalogOption optDeOutroItem = catalogService.addOption(COMPANY, USER, outro.id(), "Conteúdo", "Chocolates", 500, 0);

        String aiText = "Confirmado!\n<pedido_flor>{\"items\":[{\"item_id\":\"" + buque.id() + "\",\"qtd\":1,"
            + "\"options\":[\"" + optDeOutroItem.id() + "\"]}],\"endereco\":\"Rua Z\",\"data_entrega\":\"" + amanha()
            + "\",\"periodo\":\"tarde\",\"destinatario\":\"Ana\",\"total_cents\":3500}</pedido_flor>";

        Optional<FloriculturaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isEmpty();
        assertThat(jdbcTemplate.queryForObject("select count(*) from floricultura_orders", Long.class)).isZero();
    }

    @Test
    @DisplayName("texto sem tag → Optional.empty (conversa normal)")
    void parseAndCreate_noTag() {
        Optional<FloriculturaOrder> order = handler.parseAndCreate(
            COMPANY, conversationId, contactId, "Oi! Quer ver nosso catálogo de buquês?");
        assertThat(order).isEmpty();
    }

    @Test
    @DisplayName("tag sem endereço → Optional.empty (pedido não criado)")
    void parseAndCreate_noAddress() {
        FloriculturaCatalogItem item = catalogService.create(COMPANY, USER, "Arranjo", null, 1500, "arranjos", false);
        String aiText = "Confirmado!\n<pedido_flor>{\"items\":[{\"item_id\":\"" + item.id()
            + "\",\"qtd\":1}],\"data_entrega\":\"" + amanha() + "\",\"periodo\":\"manha\","
            + "\"destinatario\":\"Ana\",\"total_cents\":1500}</pedido_flor>";
        Optional<FloriculturaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isEmpty();
        assertThat(jdbcTemplate.queryForObject("select count(*) from floricultura_orders", Long.class)).isZero();
    }
}
