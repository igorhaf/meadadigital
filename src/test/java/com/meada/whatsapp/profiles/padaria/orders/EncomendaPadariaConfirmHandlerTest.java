package com.meada.whatsapp.profiles.padaria.orders;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.profiles.padaria.PadariaConfigRepository;
import com.meada.whatsapp.profiles.padaria.menu.PadariaMenuItem;
import com.meada.whatsapp.profiles.padaria.menu.PadariaMenuOption;
import com.meada.whatsapp.profiles.padaria.menu.PadariaMenuService;
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
 * Testa o EncomendaPadariaConfirmHandler (camada 8.8 / perfil padaria): parse da tag
 * {@code <encomenda_padaria>} + create, com as escapadas (fulfillment retirada/entrega, data
 * condicional + lead time, personalização/cake_message) e os modifiers (unit_price = base + Σ deltas;
 * option fantasma ABORTA). O {@code total} mentiroso da IA é sempre DESCARTADO. Clone do
 * PedidoFlorConfirmHandlerTest + as escapadas.
 */
class EncomendaPadariaConfirmHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private EncomendaPadariaConfirmHandler handler;
    @Autowired
    private PadariaMenuService menuService;
    @Autowired
    private PadariaConfigRepository configRepository;

    private static final UUID COMPANY = UUID.fromString("c8800000-0000-0000-0000-000000000072");
    private static final UUID USER = UUID.fromString("d8800000-0000-0000-0000-000000000072");
    private UUID conversationId;
    private UUID contactId;

    /** Uma data futura (amanhã, fuso America/Sao_Paulo). */
    private static String amanha() {
        return LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(1).toString();
    }

    /** Uma data daqui a N dias. */
    private static String inDays(int n) {
        return LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(n).toString();
    }

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'padaria')",
            COMPANY, "Padaria H", "padaria-h");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@padaria-h.dev', 'admin')",
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
        configRepository.upsert(COMPANY, 700, 0, 1);
    }

    @Test
    @DisplayName("pronta-entrega (retirada, sem data) → cria pedido, total descarta o da IA")
    void parseAndCreate_readyPickup() {
        PadariaMenuItem pao = menuService.create(COMPANY, USER, "Pão Francês", null, 100, "paes", false, null, null);
        String aiText = "Confirmado: 5 pães, retirada. Total R$ 5.\n"
            + "<encomenda_padaria>{\"fulfillment\":\"retirada\",\"pickup_or_delivery_date\":null,"
            + "\"delivery_period\":null,\"delivery_address\":null,"
            + "\"items\":[{\"menu_item_id\":\"" + pao.id() + "\",\"quantity\":5}],\"notes\":\"\"}</encomenda_padaria>";

        Optional<PadariaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(order).isPresent();
        assertThat(order.get().fulfillment()).isEqualTo("retirada");
        assertThat(order.get().subtotalCents()).isEqualTo(500);
        assertThat(order.get().deliveryFeeCents()).isZero();
        assertThat(order.get().totalCents()).isEqualTo(500);
        assertThat(order.get().pickupOrDeliveryDate()).isNull();
        assertThat(order.get().status()).isEqualTo("aguardando");
    }

    @Test
    @DisplayName("sob encomenda (entrega) com personalização + data válida → cria, unit_price = base + Σ deltas, cake_message")
    void parseAndCreate_madeToOrderWithPersonalization() {
        PadariaMenuItem bolo = menuService.create(COMPANY, USER, "Bolo", null, 8000, "bolos_encomenda", true, 2, null);
        PadariaMenuOption recheio = menuService.addOption(COMPANY, USER, bolo.id(), "Recheio", "Brigadeiro", 500, 0);
        PadariaMenuOption tam = menuService.addOption(COMPANY, USER, bolo.id(), "Tamanho", "1kg", 1500, 1);

        String aiText = "Confirmado: 1 Bolo (Brigadeiro, 1kg), entrega dia X de manhã, placa 'Parabéns'. Total R$ 107.\n"
            + "<encomenda_padaria>{\"fulfillment\":\"entrega\",\"pickup_or_delivery_date\":\"" + inDays(2) + "\","
            + "\"delivery_period\":\"manha\",\"delivery_address\":\"Rua das Flores 10\","
            + "\"items\":[{\"menu_item_id\":\"" + bolo.id() + "\",\"quantity\":1,"
            + "\"options\":[{\"option_id\":\"" + recheio.id() + "\"},{\"option_id\":\"" + tam.id() + "\"}],"
            + "\"cake_message\":\"Parabéns\"}],\"notes\":\"\"}</encomenda_padaria>";

        Optional<PadariaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(order).isPresent();
        // unit_price = 8000 + 500 + 1500 = 10000; subtotal = 10000; total = 10000 + 700 (entrega).
        assertThat(order.get().items().get(0).unitPriceCents()).isEqualTo(10000);
        assertThat(order.get().items().get(0).options()).hasSize(2);
        assertThat(order.get().items().get(0).cakeMessage()).isEqualTo("Parabéns");
        assertThat(order.get().items().get(0).madeToOrder()).isTrue();
        assertThat(order.get().subtotalCents()).isEqualTo(10000);
        assertThat(order.get().deliveryFeeCents()).isEqualTo(700);
        assertThat(order.get().totalCents()).isEqualTo(10700);
        assertThat(order.get().pickupOrDeliveryDate().toString()).isEqualTo(inDays(2));
        assertThat(order.get().deliveryPeriod()).isEqualTo("manha");
    }

    @Test
    @DisplayName("sob encomenda com data ANTES do lead → Optional.empty + 0 pedidos (lead_time_violation)")
    void parseAndCreate_leadViolation_aborts() {
        PadariaMenuItem bolo = menuService.create(COMPANY, USER, "Bolo", null, 8000, "bolos_encomenda", true, 3, null);
        String aiText = "Confirmado!\n<encomenda_padaria>{\"fulfillment\":\"retirada\","
            + "\"pickup_or_delivery_date\":\"" + amanha() + "\",\"delivery_period\":\"manha\",\"delivery_address\":null,"
            + "\"items\":[{\"menu_item_id\":\"" + bolo.id() + "\",\"quantity\":1}],\"notes\":\"\"}</encomenda_padaria>";
        Optional<PadariaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isEmpty();
        assertThat(jdbcTemplate.queryForObject("select count(*) from padaria_orders", Long.class)).isZero();
    }

    @Test
    @DisplayName("entrega SEM endereço → Optional.empty + 0 pedidos (address_required)")
    void parseAndCreate_deliveryNoAddress_aborts() {
        PadariaMenuItem pao = menuService.create(COMPANY, USER, "Pão", null, 100, "paes", false, null, null);
        String aiText = "Confirmado!\n<encomenda_padaria>{\"fulfillment\":\"entrega\","
            + "\"pickup_or_delivery_date\":null,\"delivery_period\":null,\"delivery_address\":null,"
            + "\"items\":[{\"menu_item_id\":\"" + pao.id() + "\",\"quantity\":1}],\"notes\":\"\"}</encomenda_padaria>";
        Optional<PadariaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isEmpty();
        assertThat(jdbcTemplate.queryForObject("select count(*) from padaria_orders", Long.class)).isZero();
    }

    @Test
    @DisplayName("fulfillment inválido → Optional.empty")
    void parseAndCreate_invalidFulfillment() {
        PadariaMenuItem pao = menuService.create(COMPANY, USER, "Pão", null, 100, "paes", false, null, null);
        String aiText = "Confirmado!\n<encomenda_padaria>{\"fulfillment\":\"voando\","
            + "\"items\":[{\"menu_item_id\":\"" + pao.id() + "\",\"quantity\":1}]}</encomenda_padaria>";
        Optional<PadariaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isEmpty();
        assertThat(jdbcTemplate.queryForObject("select count(*) from padaria_orders", Long.class)).isZero();
    }

    @Test
    @DisplayName("data no PASSADO → Optional.empty + 0 pedidos")
    void parseAndCreate_pastDate_aborts() {
        PadariaMenuItem bolo = menuService.create(COMPANY, USER, "Bolo", null, 2000, "bolos_encomenda", true, 1, null);
        String ontem = LocalDate.now(ZoneId.of("America/Sao_Paulo")).minusDays(1).toString();
        String aiText = "Confirmado!\n<encomenda_padaria>{\"fulfillment\":\"retirada\","
            + "\"pickup_or_delivery_date\":\"" + ontem + "\",\"delivery_period\":\"manha\","
            + "\"items\":[{\"menu_item_id\":\"" + bolo.id() + "\",\"quantity\":1}]}</encomenda_padaria>";
        Optional<PadariaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isEmpty();
        assertThat(jdbcTemplate.queryForObject("select count(*) from padaria_orders", Long.class)).isZero();
    }

    @Test
    @DisplayName("item_id inexistente → Optional.empty + 0 pedidos")
    void parseAndCreate_invalidItem() {
        String aiText = "Confirmado!\n<encomenda_padaria>{\"fulfillment\":\"retirada\","
            + "\"items\":[{\"menu_item_id\":\"" + UUID.randomUUID() + "\",\"quantity\":1}]}</encomenda_padaria>";
        Optional<PadariaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isEmpty();
        assertThat(jdbcTemplate.queryForObject("select count(*) from padaria_orders", Long.class)).isZero();
    }

    @Test
    @DisplayName("option_id fantasma (não pertence ao item) → Optional.empty + 0 pedidos")
    void parseAndCreate_invalidOption_aborts() {
        PadariaMenuItem bolo = menuService.create(COMPANY, USER, "Bolo", null, 3000, "bolos_encomenda", true, 1, null);
        PadariaMenuItem outro = menuService.create(COMPANY, USER, "Torta", null, 4000, "tortas", true, 1, null);
        PadariaMenuOption optDeOutro = menuService.addOption(COMPANY, USER, outro.id(), "Sabor", "Limão", 500, 0);

        String aiText = "Confirmado!\n<encomenda_padaria>{\"fulfillment\":\"retirada\","
            + "\"pickup_or_delivery_date\":\"" + inDays(1) + "\",\"delivery_period\":\"tarde\","
            + "\"items\":[{\"menu_item_id\":\"" + bolo.id() + "\",\"quantity\":1,"
            + "\"options\":[{\"option_id\":\"" + optDeOutro.id() + "\"}]}]}</encomenda_padaria>";
        Optional<PadariaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isEmpty();
        assertThat(jdbcTemplate.queryForObject("select count(*) from padaria_orders", Long.class)).isZero();
    }

    @Test
    @DisplayName("texto sem tag → Optional.empty (conversa normal)")
    void parseAndCreate_noTag() {
        Optional<PadariaOrder> order = handler.parseAndCreate(
            COMPANY, conversationId, contactId, "Oi! Quer ver nosso cardápio de pães e bolos?");
        assertThat(order).isEmpty();
    }
}
