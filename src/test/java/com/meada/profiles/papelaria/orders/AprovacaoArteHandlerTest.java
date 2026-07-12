package com.meada.profiles.papelaria.orders;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.papelaria.PapelariaConfigRepository;
import com.meada.profiles.papelaria.catalog.PapelariaCatalogItem;
import com.meada.profiles.papelaria.catalog.PapelariaCatalogService;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o AprovacaoArteHandler (camada 8.15 / perfil papelaria): parse da tag {@code <aprovacao_arte>}
 * + aplicação da aprovação da arte (ESCAPADA). Pedido em 'arte_aprovacao' + tag → art_approved=true +
 * transição para em_producao; pedido em outro estado → no-op; sem tag → empty. Mirror conceitual do
 * AprovacaoOsHandler da oficina (camada 7.9). EvolutionSender é um fake que registra os envios.
 */
@Import(AprovacaoArteHandlerTest.TestConfig.class)
class AprovacaoArteHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private AprovacaoArteHandler handler;
    @Autowired
    private PapelariaOrderService orderService;
    @Autowired
    private PapelariaCatalogService catalogService;
    @Autowired
    private PapelariaConfigRepository configRepository;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private static final UUID COMPANY = UUID.fromString("c8150000-0000-0000-0000-000000000074");
    private static final UUID USER = UUID.fromString("d8150000-0000-0000-0000-000000000074");
    private UUID conversationId;
    private UUID contactId;

    private static String inDays(int n) {
        return LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(n).toString();
    }

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'papelaria')",
            COMPANY, "Papelaria A", "papelaria-a");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@papelaria-a.dev', 'admin')",
            USER, COMPANY);
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990074", "Cliente");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
        configRepository.upsert(COMPANY, 0, 0, 5);
    }

    /** Cria um pedido e o leva até 'arte_aprovacao' (aceito + arte subida). */
    private PapelariaOrder orderInArteAprovacao() {
        PapelariaCatalogItem convite = catalogService.create(COMPANY, USER, "Convite", null, 800, "convites", true, 7, null);
        PapelariaOrder order = orderService.create(COMPANY, conversationId, contactId, "retirada", null,
            List.of(new OrderLineInput(convite.id(), 100, List.of(), "Noivos")), LocalDate.parse(inDays(7)), "manha", null);
        orderService.updateStatus(COMPANY, order.id(), "aceito", null);
        orderService.setArtUrl(COMPANY, order.id(), "https://arte.example/abc.png");
        fakeEvolution.reset();
        return order;
    }

    @Test
    @DisplayName("pedido em 'arte_aprovacao' + <aprovacao_arte>{order_id} → art_approved=true + em_producao + notifica")
    void approveWithExplicitId() {
        PapelariaOrder order = orderInArteAprovacao();
        String aiText = "Perfeito, aprovado!\n<aprovacao_arte>{\"order_id\":\"" + order.id() + "\"}</aprovacao_arte>";

        Optional<PapelariaOrder> result = handler.parseAndApply(COMPANY, conversationId, contactId, aiText);

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo("em_producao");
        assertThat(result.get().artApproved()).isTrue();
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("Arte aprovada");
    }

    @Test
    @DisplayName("<aprovacao_arte>{order_id:null} resolve o pedido em arte_aprovacao da conversa → em_producao")
    void approveByConversationFallback() {
        orderInArteAprovacao();
        String aiText = "Aprovado!\n<aprovacao_arte>{\"order_id\":null}</aprovacao_arte>";

        Optional<PapelariaOrder> result = handler.parseAndApply(COMPANY, conversationId, contactId, aiText);

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo("em_producao");
        assertThat(result.get().artApproved()).isTrue();
    }

    @Test
    @DisplayName("pedido em OUTRO estado (aguardando) → no-op (Optional.empty), nada muda")
    void otherState_noOp() {
        PapelariaCatalogItem bloco = catalogService.create(COMPANY, USER, "Bloco", null, 100, "papelaria", false, null, null);
        PapelariaOrder order = orderService.create(COMPANY, conversationId, contactId, "retirada", null,
            List.of(new OrderLineInput(bloco.id(), 1, List.of(), null)), null, null, null);
        fakeEvolution.reset();
        String aiText = "Aprovado!\n<aprovacao_arte>{\"order_id\":\"" + order.id() + "\"}</aprovacao_arte>";

        Optional<PapelariaOrder> result = handler.parseAndApply(COMPANY, conversationId, contactId, aiText);

        assertThat(result).isEmpty();
        assertThat(orderService.get(COMPANY, order.id()).orElseThrow().status()).isEqualTo("aguardando");
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("texto sem tag → Optional.empty")
    void noTag_empty() {
        Optional<PapelariaOrder> result = handler.parseAndApply(
            COMPANY, conversationId, contactId, "Que bom que gostou! Posso confirmar?");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("BARREIRA DE CONTATO: aprovação com order_id explícito vinda de OUTRO contato → empty + estado intacto")
    void contactBarrier_blocksOtherContact() {
        PapelariaOrder order = orderInArteAprovacao();
        UUID otherContact = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            otherContact, COMPANY, "+5511999990075", "Outro Cliente");
        String aiText = "Aprovado!\n<aprovacao_arte>{\"order_id\":\"" + order.id() + "\"}</aprovacao_arte>";

        Optional<PapelariaOrder> result = handler.parseAndApply(COMPANY, conversationId, otherContact, aiText);

        assertThat(result).isEmpty();
        assertThat(orderService.get(COMPANY, order.id()).orElseThrow().status()).isEqualTo("arte_aprovacao");
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("hasTag/stripTag detectam e removem a tag")
    void hasAndStrip() {
        String aiText = "Aprovado!\n<aprovacao_arte>{\"order_id\":null}</aprovacao_arte>";
        assertThat(handler.hasTag(aiText)).isTrue();
        assertThat(handler.stripTag(aiText)).isEqualTo("Aprovado!");
        assertThat(handler.hasTag("sem tag aqui")).isFalse();
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-papelaria-arte";
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
