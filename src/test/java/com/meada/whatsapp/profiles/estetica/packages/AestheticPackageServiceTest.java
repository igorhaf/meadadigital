package com.meada.whatsapp.profiles.estetica.packages;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.outbound.EvolutionSender;
import com.meada.whatsapp.profiles.estetica.packages.AestheticPackageService.InvalidStatusTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o AestheticPackageService (camada 8.3): compra cria pacote pendente com total = sessões ×
 * preço do CATÁLOGO (não chutado) e remaining = total; ativar materializa activated_at + notifica;
 * transição inválida → 409.
 */
@Import(AestheticPackageServiceTest.TestConfig.class)
class AestheticPackageServiceTest extends AbstractIntegrationTest {

    @Autowired
    private AestheticPackageService service;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private static final UUID COMPANY = UUID.fromString("cf000000-0000-0000-0000-000000000002");
    private UUID contactId;
    private UUID conversationId;
    private UUID procedureId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'estetica')",
            COMPANY, "Estetica Pkg", "estetica-pkg");
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990310", "Marina");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
        procedureId = UUID.randomUUID();
        jdbcTemplate.update("insert into aesthetic_procedures (id, company_id, name, duration_minutes, unit_price_cents) "
            + "values (?, ?, 'Limpeza de pele', 60, 15000)", procedureId, COMPANY);
    }

    @Test
    @DisplayName("compra cria pacote pendente; total = sessões × preço do catálogo; remaining = total")
    void create_pendingTotalFromCatalog() {
        AestheticPackage pkg = service.create(COMPANY, contactId, "Marina", "+5511999990310",
            procedureId, conversationId, 5, null);
        assertThat(pkg.status()).isEqualTo("pendente");
        assertThat(pkg.totalCents()).isEqualTo(5 * 15000);   // preço do catálogo, não chutado
        assertThat(pkg.sessionsRemaining()).isEqualTo(5);
        assertThat(pkg.sessionsUsed()).isZero();
        assertThat(pkg.procedureName()).isEqualTo("Limpeza de pele");
    }

    @Test
    @DisplayName("ativar (pendente→ativo) materializa activated_at + notifica o cliente")
    void activate_notifies() {
        AestheticPackage pkg = service.create(COMPANY, contactId, "Marina", null, procedureId, conversationId, 5, null);
        AestheticPackage active = service.updateStatus(COMPANY, pkg.id(), "ativo");
        assertThat(active.status()).isEqualTo("ativo");
        assertThat(active.activatedAt()).isNotNull();
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("Limpeza de pele");
    }

    @Test
    @DisplayName("transição inválida (pendente→esgotado) → InvalidStatusTransitionException")
    void invalidTransition() {
        AestheticPackage pkg = service.create(COMPANY, contactId, "Marina", null, procedureId, null, 5, null);
        assertThatThrownBy(() -> service.updateStatus(COMPANY, pkg.id(), "esgotado"))
            .isInstanceOf(InvalidStatusTransitionException.class);
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-estetica";
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
