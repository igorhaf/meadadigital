package com.meada.whatsapp.profiles.viagens.proposals;

import com.meada.whatsapp.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o PropostaViagemConfirmHandler (camada 8.18 / perfil viagens): cria a proposta em 'rascunho'
 * pela tag, resolvendo o snapshot do contato da conversa; sem tag → empty; briefing vazio → empty;
 * consultant_id inválido → ignora consultor mas ABRE; data inválida → ignora a data mas ABRE. Espelho
 * do PropostaEventoConfirmHandlerTest (chassi eventos 8.2).
 */
class PropostaViagemConfirmHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private PropostaViagemConfirmHandler handler;

    private static final UUID COMPANY = UUID.fromString("ce100000-0000-0000-0000-000000000004");
    private UUID conversationId;
    private UUID contactId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'viagens')",
            COMPANY, "Viagens H1", "viagens-h1");
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999991291", "Marina Costa");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
    }

    @Test
    @DisplayName("tag <proposta_viagem> cria proposta em rascunho com snapshot do contato")
    void parseAndCreate_createsRascunho() {
        String aiText = "Perfeito, registrei o briefing da sua viagem!\n"
            + "<proposta_viagem>{\"destination\":\"Paris\",\"start_date\":\"2026-12-01\",\"end_date\":\"2026-12-08\","
            + "\"num_travelers\":2,\"travel_style\":\"lua-de-mel\",\"briefing\":\"Lua de mel romântica\","
            + "\"consultant_id\":null,\"notes\":null}</proposta_viagem>";

        Optional<TravelProposal> o = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(o).isPresent();
        TravelProposal p = o.get();
        assertThat(p.status()).isEqualTo("rascunho");
        assertThat(p.totalCents()).isZero();
        assertThat(p.customerName()).isEqualTo("Marina Costa");
        assertThat(p.destination()).isEqualTo("Paris");
        assertThat(p.numTravelers()).isEqualTo(2);
        assertThat(p.startDate().toString()).isEqualTo("2026-12-01");
        assertThat(p.endDate().toString()).isEqualTo("2026-12-08");
        assertThat(p.items()).isEmpty();
        assertThat(p.itinerary()).isEmpty();
    }

    @Test
    @DisplayName("sem tag → Optional.empty (nada criado)")
    void parseAndCreate_noTag() {
        Optional<TravelProposal> o = handler.parseAndCreate(COMPANY, conversationId, contactId,
            "Claro, posso te ajudar com sua viagem!");
        assertThat(o).isEmpty();
    }

    @Test
    @DisplayName("briefing vazio na tag → Optional.empty (não cria)")
    void parseAndCreate_emptyBriefing() {
        String aiText = "Vou abrir aqui.\n"
            + "<proposta_viagem>{\"destination\":\"Paris\",\"briefing\":\"\"}</proposta_viagem>";
        Optional<TravelProposal> o = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(o).isEmpty();
        Long n = jdbcTemplate.queryForObject("select count(*) from travel_proposals where company_id = ?",
            Long.class, COMPANY);
        assertThat(n).isZero();
    }

    @Test
    @DisplayName("consultant_id inválido na tag → ignora consultor mas ABRE a proposta")
    void parseAndCreate_invalidConsultantStillOpens() {
        String aiText = "Registrado!\n"
            + "<proposta_viagem>{\"destination\":\"Lisboa\",\"consultant_id\":\"" + UUID.randomUUID()
            + "\",\"briefing\":\"Roteiro de 7 dias\"}</proposta_viagem>";

        Optional<TravelProposal> o = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(o).isPresent();
        assertThat(o.get().status()).isEqualTo("rascunho");
        assertThat(o.get().consultantId()).isNull();
        assertThat(o.get().destination()).isEqualTo("Lisboa");
    }

    @Test
    @DisplayName("data inválida na tag → ignora a data mas ABRE a proposta")
    void parseAndCreate_invalidDateStillOpens() {
        String aiText = "Registrado!\n"
            + "<proposta_viagem>{\"destination\":\"Roma\",\"start_date\":\"nao-eh-data\","
            + "\"briefing\":\"Viagem cultural\"}</proposta_viagem>";

        Optional<TravelProposal> o = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(o).isPresent();
        assertThat(o.get().status()).isEqualTo("rascunho");
        assertThat(o.get().startDate()).isNull();
        assertThat(o.get().destination()).isEqualTo("Roma");
    }
}
