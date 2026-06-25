package com.meada.whatsapp.profiles.atelie.proposals;

import com.meada.whatsapp.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o PropostaAtelieConfirmHandler (camada 8.14): abre proposta em 'rascunho' pela tag,
 * resolvendo o snapshot do contato da conversa; project_type ausente/inválido → 'costura';
 * artisan_id inválido → ignora o artesão mas ainda abre; sem tag → empty; stripOrderTag remove a tag.
 */
class PropostaAtelieConfirmHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private PropostaAtelieConfirmHandler handler;

    private static final UUID COMPANY = UUID.fromString("a7000000-0000-0000-0000-000000000003");
    private UUID conversationId;
    private UUID contactId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'atelie')",
            COMPANY, "Atelie H1", "atelie-h1");
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990181", "Marina Costa");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
    }

    @Test
    @DisplayName("tag <proposta_atelie> cria proposta em rascunho com snapshot do contato")
    void parseAndCreate_createsRascunho() {
        String aiText = "Perfeito, registrei o briefing do seu vestido!\n"
            + "<proposta_atelie>{\"project_type\":\"costura\",\"occasion\":\"casamento\","
            + "\"estimated_date\":\"2026-12-20\",\"briefing\":\"Vestido de noiva\",\"artisan_id\":null,\"notes\":null}"
            + "</proposta_atelie>";

        Optional<AtelieProposal> o = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(o).isPresent();
        AtelieProposal p = o.get();
        assertThat(p.status()).isEqualTo("rascunho");
        assertThat(p.totalCents()).isZero();
        assertThat(p.customerName()).isEqualTo("Marina Costa");
        assertThat(p.projectType()).isEqualTo("costura");
        assertThat(p.estimatedDate().toString()).isEqualTo("2026-12-20");
        assertThat(p.items()).isEmpty();
        assertThat(p.fittings()).isEmpty();
    }

    @Test
    @DisplayName("project_type ausente na tag → normaliza para 'costura'")
    void parseAndCreate_absentProjectTypeDefaults() {
        String aiText = "Vou abrir aqui.\n"
            + "<proposta_atelie>{\"briefing\":\"Quadro grande\"}</proposta_atelie>";
        Optional<AtelieProposal> o = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(o).isPresent();
        assertThat(o.get().projectType()).isEqualTo("costura");
    }

    @Test
    @DisplayName("project_type inválido na tag → normaliza para 'costura'")
    void parseAndCreate_invalidProjectTypeDefaults() {
        String aiText = "Ok.\n"
            + "<proposta_atelie>{\"project_type\":\"xpto\",\"briefing\":\"Bolsa sob medida\"}</proposta_atelie>";
        Optional<AtelieProposal> o = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(o).isPresent();
        assertThat(o.get().projectType()).isEqualTo("costura");
    }

    @Test
    @DisplayName("artisan_id inválido → ignora o artesão mas abre a proposta")
    void parseAndCreate_invalidArtisanStillOpens() {
        String aiText = "Ok.\n"
            + "<proposta_atelie>{\"project_type\":\"design\",\"artisan_id\":\"" + UUID.randomUUID()
            + "\",\"briefing\":\"Logo da marca\"}</proposta_atelie>";
        Optional<AtelieProposal> o = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(o).isPresent();
        assertThat(o.get().status()).isEqualTo("rascunho");
        assertThat(o.get().artisanId()).isNull();
    }

    @Test
    @DisplayName("sem tag → Optional.empty (nada criado)")
    void parseAndCreate_noTag() {
        Optional<AtelieProposal> o = handler.parseAndCreate(COMPANY, conversationId, contactId,
            "Claro, posso te ajudar com sua peça!");
        assertThat(o).isEmpty();
        Long n = jdbcTemplate.queryForObject("select count(*) from atelie_proposals where company_id = ?",
            Long.class, COMPANY);
        assertThat(n).isZero();
    }

    @Test
    @DisplayName("stripOrderTag remove a tag do texto da IA")
    void stripOrderTag_removesTag() {
        String aiText = "Beleza!\n<proposta_atelie>{\"briefing\":\"Vestido\"}</proposta_atelie>";
        assertThat(handler.stripOrderTag(aiText)).isEqualTo("Beleza!");
    }
}
