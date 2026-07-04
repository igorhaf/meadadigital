package com.meada.profiles.papelaria.orders;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.papelaria.PapelariaConfigRepository;
import com.meada.profiles.papelaria.catalog.PapelariaCatalogItem;
import com.meada.profiles.papelaria.catalog.PapelariaCatalogOption;
import com.meada.profiles.papelaria.catalog.PapelariaCatalogService;
import com.meada.profiles.papelaria.orders.PapelariaOrderRepository.AddressRequiredException;
import com.meada.profiles.papelaria.orders.PapelariaOrderRepository.InvalidOptionException;
import com.meada.profiles.papelaria.orders.PapelariaOrderRepository.LeadTimeViolationException;
import com.meada.profiles.papelaria.orders.PapelariaOrderService.ArtNotApprovedException;
import com.meada.profiles.papelaria.orders.PapelariaOrderService.InvalidStatusTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Testa o PapelariaOrderService (camada 8.15 / perfil papelaria): a data condicional com lead time, a
 * personalização (custom_text) + recálculo que DESCARTA o total da IA, a TIRAGEM (line = unit ×
 * quantity), o fulfillment (retirada sem taxa/endereço × entrega exige endereço + soma taxa), a máquina
 * de status / gate de aceite com o funil que DIVERGE no fim, e a ESCAPADA PROVA DE ARTE (nasce
 * art_approved=false; aceito→arte_aprovacao via setArtUrl; arte_aprovacao→em_producao sem aprovação →
 * 409 art_not_approved; approveArt → em_producao + notifica; caminho pronta-entrega aceito→em_producao
 * sem arte). EvolutionSender é um fake que registra os envios. Clone do PadariaOrderServiceTest + a
 * escapada da arte + tiragem.
 */
@Import(PapelariaOrderServiceTest.TestConfig.class)
class PapelariaOrderServiceTest extends AbstractIntegrationTest {

    @Autowired
    private PapelariaOrderService service;
    @Autowired
    private PapelariaCatalogService catalogService;
    @Autowired
    private PapelariaConfigRepository configRepository;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private static final UUID COMPANY = UUID.fromString("c8150000-0000-0000-0000-000000000073");
    private static final UUID USER = UUID.fromString("d8150000-0000-0000-0000-000000000073");
    private UUID conversationId;
    private UUID contactId;

    private static LocalDate today() {
        return LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    }

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'papelaria')",
            COMPANY, "Papelaria O", "papelaria-o");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@papelaria-o.dev', 'admin')",
            USER, COMPANY);
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990073", "Cliente");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
        // taxa de entrega 700, lead default 5.
        configRepository.upsert(COMPANY, 700, 0, 5);
    }

    private PapelariaCatalogItem readyItem() {
        return catalogService.create(COMPANY, USER, "Bloco de Notas", null, 100, "papelaria", false, null, null);
    }

    private PapelariaCatalogItem conviteItem(int leadDays) {
        return catalogService.create(COMPANY, USER, "Convite", null, 800, "convites", true, leadDays, null);
    }

    // ===== data condicional + lead time ======================================

    @Test
    @DisplayName("pronta-entrega SEM data → OK (data null, retirada sem taxa)")
    void readyOnly_noDate_ok() {
        PapelariaCatalogItem bloco = readyItem();
        PapelariaOrder order = service.create(COMPANY, conversationId, contactId, "retirada", null,
            List.of(new OrderLineInput(bloco.id(), 2, List.of(), null)), null, null, null);
        assertThat(order.status()).isEqualTo("aguardando");
        assertThat(order.artApproved()).isFalse();             // nasce com a arte NÃO aprovada.
        assertThat(order.pickupOrDeliveryDate()).isNull();
        assertThat(order.subtotalCents()).isEqualTo(200);
        assertThat(order.deliveryFeeCents()).isZero();         // retirada sem taxa.
        assertThat(order.totalCents()).isEqualTo(200);
        assertThat(fakeEvolution.sent()).isEmpty();            // aguardando é silencioso.
    }

    @Test
    @DisplayName("item sob encomenda SEM data → LeadTimeViolationException (422) com a 1ª data possível")
    void madeToOrder_noDate_violates() {
        PapelariaCatalogItem convite = conviteItem(7);
        LeadTimeViolationException ex = catchThrowableOfType(
            () -> service.create(COMPANY, conversationId, contactId, "retirada", null,
                List.of(new OrderLineInput(convite.id(), 100, List.of(), "Ana & João")), null, null, null),
            LeadTimeViolationException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.earliestDate()).isEqualTo(today().plusDays(7));
        assertThat(jdbcTemplate.queryForObject("select count(*) from papelaria_orders", Long.class)).isZero();
    }

    @Test
    @DisplayName("item sob encomenda com data ANTES do lead → LeadTimeViolationException (422) com earliest")
    void madeToOrder_dateTooSoon_violates() {
        PapelariaCatalogItem convite = conviteItem(10);
        LocalDate tooSoon = today().plusDays(3);   // lead é 10.
        LeadTimeViolationException ex = catchThrowableOfType(
            () -> service.create(COMPANY, conversationId, contactId, "retirada", null,
                List.of(new OrderLineInput(convite.id(), 50, List.of(), null)), tooSoon, "manha", null),
            LeadTimeViolationException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.earliestDate()).isEqualTo(today().plusDays(10));
    }

    @Test
    @DisplayName("item sob encomenda com data válida (>= today+lead) → OK; custom_text snapshot")
    void madeToOrder_validDate_ok() {
        PapelariaCatalogItem convite = conviteItem(7);
        LocalDate ok = today().plusDays(7);
        PapelariaOrder order = service.create(COMPANY, conversationId, contactId, "retirada", null,
            List.of(new OrderLineInput(convite.id(), 100, List.of(), "Maria & Pedro · 20/12")), ok, "tarde", null);
        assertThat(order.pickupOrDeliveryDate()).isEqualTo(ok);
        assertThat(order.deliveryPeriod()).isEqualTo("tarde");
        assertThat(order.items().get(0).madeToOrder()).isTrue();
        assertThat(order.items().get(0).customText()).isEqualTo("Maria & Pedro · 20/12");
    }

    @Test
    @DisplayName("data exigida = MAX dos leads quando há múltiplos itens sob encomenda")
    void madeToOrder_maxOfLeads() {
        PapelariaCatalogItem convite7 = conviteItem(7);
        PapelariaCatalogItem convite15 = catalogService.create(COMPANY, USER, "Convite Premium", null, 3000,
            "convites", true, 15, null);
        // data = today+10 é OK pro de lead 7, mas NÃO pro de lead 15 → viola, earliest = today+15.
        LeadTimeViolationException ex = catchThrowableOfType(
            () -> service.create(COMPANY, conversationId, contactId, "retirada", null,
                List.of(new OrderLineInput(convite7.id(), 50, List.of(), null),
                        new OrderLineInput(convite15.id(), 50, List.of(), null)),
                today().plusDays(10), "manha", null),
            LeadTimeViolationException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.earliestDate()).isEqualTo(today().plusDays(15));
    }

    @Test
    @DisplayName("item sob encomenda usa lead_time_days_default da config quando o item não tem lead próprio")
    void madeToOrder_usesConfigDefault() {
        configRepository.upsert(COMPANY, 700, 0, 8);   // lead default 8.
        PapelariaCatalogItem convite = catalogService.create(COMPANY, USER, "Convite padrão", null, 600,
            "convites", true, null, null);       // sem lead próprio → usa o default.
        LeadTimeViolationException ex = catchThrowableOfType(
            () -> service.create(COMPANY, conversationId, contactId, "retirada", null,
                List.of(new OrderLineInput(convite.id(), 50, List.of(), null)), today().plusDays(5), "manha", null),
            LeadTimeViolationException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.earliestDate()).isEqualTo(today().plusDays(8));
    }

    // ===== personalização + recálculo + TIRAGEM ==============================

    @Test
    @DisplayName("personalização: unit_price = base + Σ deltas; custom_text snapshot; total da IA descartado")
    void personalization_recalc() {
        PapelariaCatalogItem convite = conviteItem(7);
        PapelariaCatalogOption papel = catalogService.addOption(COMPANY, USER, convite.id(), "Papel", "Perolado", 500, 0);
        PapelariaCatalogOption acabamento = catalogService.addOption(COMPANY, USER, convite.id(), "Acabamento", "Verniz", 1500, 1);

        PapelariaOrder order = service.create(COMPANY, conversationId, contactId, "retirada", null,
            List.of(new OrderLineInput(convite.id(), 1, List.of(papel.id(), acabamento.id()), "Os noivos")),
            today().plusDays(7), "manha", null);

        // unit_price = 800 + 500 + 1500 = 2800; subtotal = 2800 * 1 = 2800; retirada sem taxa.
        assertThat(order.items().get(0).unitPriceCents()).isEqualTo(2800);
        assertThat(order.items().get(0).options()).hasSize(2);
        assertThat(order.items().get(0).customText()).isEqualTo("Os noivos");
        assertThat(order.subtotalCents()).isEqualTo(2800);
        assertThat(order.totalCents()).isEqualTo(2800);
    }

    @Test
    @DisplayName("TIRAGEM: line = unit × quantity (100 convites escala o total)")
    void tiragem_scalesLineTotal() {
        PapelariaCatalogItem convite = conviteItem(7);
        PapelariaCatalogOption papel = catalogService.addOption(COMPANY, USER, convite.id(), "Papel", "Perolado", 200, 0);

        // unit = 800 + 200 = 1000; tiragem 100 → line = 100000.
        PapelariaOrder order = service.create(COMPANY, conversationId, contactId, "retirada", null,
            List.of(new OrderLineInput(convite.id(), 100, List.of(papel.id()), null)),
            today().plusDays(7), "manha", null);

        assertThat(order.items().get(0).qtd()).isEqualTo(100);
        assertThat(order.items().get(0).unitPriceCents()).isEqualTo(1000);
        assertThat(order.subtotalCents()).isEqualTo(100000);
        assertThat(order.totalCents()).isEqualTo(100000);
    }

    @Test
    @DisplayName("option_id de outro item → InvalidOptionException, pedido NÃO criado")
    void invalidOption_aborts() {
        PapelariaCatalogItem convite = conviteItem(7);
        PapelariaCatalogItem outro = catalogService.create(COMPANY, USER, "Cartão", null, 400, "cartoes", true, 7, null);
        PapelariaCatalogOption optDeOutro = catalogService.addOption(COMPANY, USER, outro.id(), "Cor", "Azul", 300, 0);

        assertThatThrownBy(() -> service.create(COMPANY, conversationId, contactId, "retirada", null,
            List.of(new OrderLineInput(convite.id(), 50, List.of(optDeOutro.id()), null)),
            today().plusDays(7), "manha", null))
            .isInstanceOf(InvalidOptionException.class);
        assertThat(jdbcTemplate.queryForObject("select count(*) from papelaria_orders", Long.class)).isZero();
    }

    // ===== fulfillment retirada × entrega ====================================

    @Test
    @DisplayName("entrega SEM endereço → AddressRequiredException (422), pedido NÃO criado")
    void delivery_noAddress_violates() {
        PapelariaCatalogItem bloco = readyItem();
        assertThatThrownBy(() -> service.create(COMPANY, conversationId, contactId, "entrega", null,
            List.of(new OrderLineInput(bloco.id(), 1, List.of(), null)), null, null, null))
            .isInstanceOf(AddressRequiredException.class);
        assertThat(jdbcTemplate.queryForObject("select count(*) from papelaria_orders", Long.class)).isZero();
    }

    @Test
    @DisplayName("entrega COM endereço → soma a delivery_fee da config")
    void delivery_withAddress_addsFee() {
        PapelariaCatalogItem bloco = readyItem();
        PapelariaOrder order = service.create(COMPANY, conversationId, contactId, "entrega", "Rua X 1",
            List.of(new OrderLineInput(bloco.id(), 3, List.of(), null)), null, null, null);
        assertThat(order.fulfillment()).isEqualTo("entrega");
        assertThat(order.deliveryAddress()).isEqualTo("Rua X 1");
        assertThat(order.subtotalCents()).isEqualTo(300);
        assertThat(order.deliveryFeeCents()).isEqualTo(700);    // taxa somada só na entrega.
        assertThat(order.totalCents()).isEqualTo(1000);
    }

    @Test
    @DisplayName("retirada ignora endereço/taxa (mesmo se endereço vier no payload, não persiste taxa)")
    void pickup_noFee() {
        PapelariaCatalogItem bloco = readyItem();
        PapelariaOrder order = service.create(COMPANY, conversationId, contactId, "retirada", "Rua Y 9",
            List.of(new OrderLineInput(bloco.id(), 1, List.of(), null)), null, null, null);
        assertThat(order.deliveryFeeCents()).isZero();
        assertThat(order.deliveryAddress()).isNull();           // retirada não persiste endereço.
        assertThat(order.totalCents()).isEqualTo(100);
    }

    // ===== Máquina de status / gate de aceite ================================

    @Test
    @DisplayName("pedido nasce 'aguardando' (art_approved=false) e NÃO dispara notificação na criação")
    void create_isAguardando_andSilent() {
        service.create(COMPANY, conversationId, contactId, "retirada", null,
            List.of(new OrderLineInput(readyItem().id(), 1, List.of(), null)), null, null, null);
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("recusa (aguardando → recusado) com motivo → terminal + notificação contém o motivo defensivo")
    void reject_withReason() {
        PapelariaOrder order = service.create(COMPANY, conversationId, contactId, "retirada", null,
            List.of(new OrderLineInput(readyItem().id(), 1, List.of(), null)), null, null, null);

        PapelariaOrder rejected = service.updateStatus(COMPANY, order.id(), "recusado", "papel esgotado");
        assertThat(rejected.status()).isEqualTo("recusado");
        assertThat(rejected.rejectionReason()).isEqualTo("papel esgotado");
        assertThat(fakeEvolution.sent()).hasSize(1);
        String text = fakeEvolution.sent().get(0).text();
        assertThat(text).contains("não conseguimos atender seu pedido");
        assertThat(text).contains("papel esgotado");
    }

    @Test
    @DisplayName("transição inválida (aguardando → entregue) → InvalidStatusTransitionException (409), nada enviado")
    void invalidTransition() {
        PapelariaOrder order = service.create(COMPANY, conversationId, contactId, "retirada", null,
            List.of(new OrderLineInput(readyItem().id(), 1, List.of(), null)), null, null, null);
        assertThatThrownBy(() -> service.updateStatus(COMPANY, order.id(), "entregue", null))
            .isInstanceOf(InvalidStatusTransitionException.class);
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    // ===== ESCAPADA — PROVA DE ARTE ==========================================

    @Test
    @DisplayName("aguardando→aceito (notifica), aceito→arte_aprovacao via setArtUrl (notifica + grava art_url)")
    void aceiteAndArtUpload() {
        PapelariaOrder order = service.create(COMPANY, conversationId, contactId, "retirada", null,
            List.of(new OrderLineInput(conviteItem(7).id(), 100, List.of(), "Noivos")), today().plusDays(7), "manha", null);

        PapelariaOrder aceito = service.updateStatus(COMPANY, order.id(), "aceito", null);   // 1 envio (aceito).
        assertThat(aceito.status()).isEqualTo("aceito");
        assertThat(fakeEvolution.sent().get(0).text()).contains("Recebemos seu pedido");

        PapelariaOrder emArte = service.setArtUrl(COMPANY, order.id(), "https://arte.example/abc.png");  // 2 (arte_aprovacao).
        assertThat(emArte.status()).isEqualTo("arte_aprovacao");
        assertThat(emArte.artUrl()).isEqualTo("https://arte.example/abc.png");
        assertThat(emArte.artApproved()).isFalse();
        assertThat(fakeEvolution.sent()).hasSize(2);
        assertThat(fakeEvolution.sent().get(1).text()).contains("arte está pronta");
    }

    @Test
    @DisplayName("arte_aprovacao→em_producao SEM aprovar (art_approved=false) → 409 art_not_approved")
    void artNotApproved_blocks() {
        PapelariaOrder order = service.create(COMPANY, conversationId, contactId, "retirada", null,
            List.of(new OrderLineInput(conviteItem(7).id(), 100, List.of(), null)), today().plusDays(7), "manha", null);
        service.updateStatus(COMPANY, order.id(), "aceito", null);
        service.setArtUrl(COMPANY, order.id(), "https://arte.example/abc.png");
        fakeEvolution.reset();

        assertThatThrownBy(() -> service.updateStatus(COMPANY, order.id(), "em_producao", null))
            .isInstanceOf(ArtNotApprovedException.class);
        assertThat(fakeEvolution.sent()).isEmpty();
        // continua em arte_aprovacao.
        assertThat(service.get(COMPANY, order.id()).orElseThrow().status()).isEqualTo("arte_aprovacao");
    }

    @Test
    @DisplayName("approveArt (art_approved=true) → arte_aprovacao→em_producao OK + notifica 'arte aprovada'")
    void approveArt_thenProduction() {
        PapelariaOrder order = service.create(COMPANY, conversationId, contactId, "retirada", null,
            List.of(new OrderLineInput(conviteItem(7).id(), 100, List.of(), null)), today().plusDays(7), "manha", null);
        service.updateStatus(COMPANY, order.id(), "aceito", null);
        service.setArtUrl(COMPANY, order.id(), "https://arte.example/abc.png");
        fakeEvolution.reset();

        PapelariaOrder produced = service.approveArt(COMPANY, order.id());
        assertThat(produced.status()).isEqualTo("em_producao");
        assertThat(produced.artApproved()).isTrue();
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("Arte aprovada");

        // completa o funil: em_producao → pronto → retirado (retirado silencioso).
        service.updateStatus(COMPANY, order.id(), "pronto", null);
        PapelariaOrder retirado = service.updateStatus(COMPANY, order.id(), "retirado", null);
        assertThat(retirado.status()).isEqualTo("retirado");
    }

    @Test
    @DisplayName("caminho pronta-entrega: aceito → em_producao direto (sem arte) → OK")
    void readyPath_skipsArt() {
        PapelariaOrder order = service.create(COMPANY, conversationId, contactId, "entrega", "Rua Z 3",
            List.of(new OrderLineInput(readyItem().id(), 1, List.of(), null)), null, null, null);
        service.updateStatus(COMPANY, order.id(), "aceito", null);
        PapelariaOrder produced = service.updateStatus(COMPANY, order.id(), "em_producao", null);
        assertThat(produced.status()).isEqualTo("em_producao");   // não precisa de arte aprovada.
        service.updateStatus(COMPANY, order.id(), "pronto", null);
        service.updateStatus(COMPANY, order.id(), "saiu_entrega", null);
        PapelariaOrder entregue = service.updateStatus(COMPANY, order.id(), "entregue", null);
        assertThat(entregue.status()).isEqualTo("entregue");
    }

    // -------------------------------------------------------------------------
    // Onda 1 do backlog (#1 sinal no gate da arte · #2 faixas de tiragem)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("sinal registrado e não pago: aprovar a arte NÃO produz; pagar move automaticamente")
    void depositGate_approveWaitsAndPaymentMoves() {
        PapelariaCatalogItem bloco = readyItem();
        PapelariaOrder order = service.create(COMPANY, conversationId, contactId, "retirada", null,
            List.of(new OrderLineInput(bloco.id(), 2, List.of(), null)), null, null, null);
        service.updateStatus(COMPANY, order.id(), "aceito", null);
        service.setArtUrl(COMPANY, order.id(), "https://arte.exemplo/v1.png");  // → arte_aprovacao

        service.setDeposit(COMPANY, order.id(), 10000, false);

        // aprovar com sinal pendente: arte aprovada, mas fica em arte_aprovacao (aguardando sinal).
        PapelariaOrder waiting = service.approveArt(COMPANY, order.id());
        assertThat(waiting.artApproved()).isTrue();
        assertThat(waiting.status()).isEqualTo("arte_aprovacao");

        // transição manual também bloqueada → deposit_required.
        assertThatThrownBy(() -> service.updateStatus(COMPANY, order.id(), "em_producao", null))
            .isInstanceOf(PapelariaOrderService.DepositRequiredException.class);

        // sinal pago com arte aprovada → move automaticamente pra em_producao.
        PapelariaOrder producing = service.setDeposit(COMPANY, order.id(), 10000, true);
        assertThat(producing.status()).isEqualTo("em_producao");
        assertThat(producing.depositPaidAt()).isNotNull();
    }

    @Test
    @DisplayName("marcar sinal pago sem valor → invalid_deposit; sem sinal registrado a produção é livre")
    void depositValidationAndFreeFlow() {
        PapelariaCatalogItem bloco = readyItem();
        PapelariaOrder order = service.create(COMPANY, conversationId, contactId, "retirada", null,
            List.of(new OrderLineInput(bloco.id(), 1, List.of(), null)), null, null, null);

        assertThatThrownBy(() -> service.setDeposit(COMPANY, order.id(), null, true))
            .isInstanceOf(PapelariaOrderService.InvalidDepositException.class);

        service.updateStatus(COMPANY, order.id(), "aceito", null);
        service.setArtUrl(COMPANY, order.id(), "https://arte.exemplo/v1.png");
        assertThat(service.approveArt(COMPANY, order.id()).status()).isEqualTo("em_producao");
    }

    @Test
    @DisplayName("faixa de tiragem: maior min_qty <= quantity vira o preço-base; sem faixa, compat")
    void tierPricing() {
        PapelariaCatalogItem convite = catalogService.create(COMPANY, USER, "Convite Faixa", null, 800,
            "convites", false, null, null);
        catalogService.replaceTiers(COMPANY, USER, convite.id(), List.of(
            new com.meada.profiles.papelaria.catalog.PapelariaItemTier(50, 600),
            new com.meada.profiles.papelaria.catalog.PapelariaItemTier(100, 450)));

        // 30 un < 50 → preço-base do item (800).
        PapelariaOrder small = service.create(COMPANY, conversationId, contactId, "retirada", null,
            List.of(new OrderLineInput(convite.id(), 30, List.of(), null)), null, null, null);
        assertThat(small.subtotalCents()).isEqualTo(30 * 800);

        // 120 un → faixa 100+ (450/un).
        PapelariaOrder big = service.create(COMPANY, conversationId, contactId, "retirada", null,
            List.of(new OrderLineInput(convite.id(), 120, List.of(), null)), null, null, null);
        assertThat(big.subtotalCents()).isEqualTo(120 * 450);
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-papelaria";
        }
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        FakeEvolutionSender fakeEvolutionSender() {
            return new FakeEvolutionSender();
        }
    }
}
