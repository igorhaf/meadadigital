package com.meada.whatsapp.profiles.estetica.packages;

import com.meada.whatsapp.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o CompraPacoteConfirmHandler (camada 8.3): cria pacote pendente pela tag; o PREÇO vem do
 * catálogo (NÃO da tag); procedimento inexistente → empty; total_sessions <= 0 → empty.
 */
class CompraPacoteConfirmHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private CompraPacoteConfirmHandler handler;

    private static final UUID COMPANY = UUID.fromString("cf000000-0000-0000-0000-000000000004");
    private UUID conversationId;
    private UUID contactId;
    private UUID procedureId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'estetica')",
            COMPANY, "Estetica H2", "estetica-h2");
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990330", "Marina");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
        procedureId = UUID.randomUUID();
        jdbcTemplate.update("insert into aesthetic_procedures (id, company_id, name, duration_minutes, unit_price_cents) "
            + "values (?, ?, 'Drenagem', 50, 12000)", procedureId, COMPANY);
    }

    @Test
    @DisplayName("tag cria pacote pendente; preço vem do catálogo (5 × 12000 = 60000), NÃO da tag")
    void parseAndCreate_priceFromCatalog() {
        // a tag NÃO carrega preço — mesmo que carregasse, o backend ignora e usa o do procedimento.
        String aiText = "Beleza!\n<compra_pacote>{\"procedure_id\":\"" + procedureId
            + "\",\"total_sessions\":5}</compra_pacote>";

        Optional<AestheticPackage> pkg = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(pkg).isPresent();
        assertThat(pkg.get().status()).isEqualTo("pendente");
        assertThat(pkg.get().totalSessions()).isEqualTo(5);
        assertThat(pkg.get().totalCents()).isEqualTo(60000);
        assertThat(pkg.get().sessionsRemaining()).isEqualTo(5);
        assertThat(pkg.get().customerName()).isEqualTo("Marina");
    }

    @Test
    @DisplayName("procedimento inexistente → Optional.empty")
    void parseAndCreate_unknownProcedure() {
        String aiText = "Ok!\n<compra_pacote>{\"procedure_id\":\"" + UUID.randomUUID()
            + "\",\"total_sessions\":5}</compra_pacote>";
        Optional<AestheticPackage> pkg = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(pkg).isEmpty();
    }

    @Test
    @DisplayName("total_sessions <= 0 → Optional.empty")
    void parseAndCreate_invalidSessions() {
        String aiText = "Ok!\n<compra_pacote>{\"procedure_id\":\"" + procedureId
            + "\",\"total_sessions\":0}</compra_pacote>";
        Optional<AestheticPackage> pkg = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(pkg).isEmpty();
        Long n = jdbcTemplate.queryForObject("select count(*) from aesthetic_packages where company_id = ?", Long.class, COMPANY);
        assertThat(n).isZero();
    }
}
