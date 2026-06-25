package com.meada.whatsapp.profiles.casamento.proposals;

import com.meada.whatsapp.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o PropostaCasamentoConfirmHandler (camada 8.7): cria a proposta em 'rascunho' pela tag,
 * resolvendo o snapshot do contato da conversa; sem tag → empty; planner_id inválido → ignora o
 * assessor mas abre a proposta; stripTag remove a tag.
 */
class PropostaCasamentoConfirmHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private PropostaCasamentoConfirmHandler handler;

    private static final UUID COMPANY = UUID.fromString("cf000000-0000-0000-0000-000000000004");
    private UUID conversationId;
    private UUID contactId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'casamento')",
            COMPANY, "Casamento H1", "casamento-h1");
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990191", "Marina Costa");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
    }

    @Test
    @DisplayName("tag <proposta_casamento> cria proposta em rascunho com snapshot do contato")
    void parseAndCreate_createsRascunho() {
        String aiText = "Perfeito, registrei o briefing do seu casamento!\n"
            + "<proposta_casamento>{\"wedding_style\":\"clássico\",\"wedding_date\":\"2026-12-20\","
            + "\"guest_count\":150,\"briefing\":\"Casamento ao ar livre\",\"planner_id\":null,\"notes\":null}"
            + "</proposta_casamento>";

        Optional<WeddingProposal> o = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(o).isPresent();
        WeddingProposal p = o.get();
        assertThat(p.status()).isEqualTo("rascunho");
        assertThat(p.totalCents()).isZero();
        assertThat(p.customerName()).isEqualTo("Marina Costa");
        assertThat(p.weddingStyle()).isEqualTo("clássico");
        assertThat(p.guestCount()).isEqualTo(150);
        assertThat(p.weddingDate().toString()).isEqualTo("2026-12-20");
        assertThat(p.items()).isEmpty();
        assertThat(p.timeline()).isEmpty();
        assertThat(p.checklist()).isEmpty();
    }

    @Test
    @DisplayName("sem tag → Optional.empty (nada criado)")
    void parseAndCreate_noTag() {
        Optional<WeddingProposal> o = handler.parseAndCreate(COMPANY, conversationId, contactId,
            "Claro, posso te ajudar com o seu casamento!");
        assertThat(o).isEmpty();
    }

    @Test
    @DisplayName("briefing vazio na tag → Optional.empty (não cria)")
    void parseAndCreate_emptyBriefing() {
        String aiText = "Vou abrir aqui.\n"
            + "<proposta_casamento>{\"wedding_style\":\"clássico\",\"briefing\":\"\"}</proposta_casamento>";
        Optional<WeddingProposal> o = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(o).isEmpty();
        Long n = jdbcTemplate.queryForObject("select count(*) from wedding_proposals where company_id = ?",
            Long.class, COMPANY);
        assertThat(n).isZero();
    }

    @Test
    @DisplayName("planner_id inexistente na tag → abre a proposta SEM assessor (não bloqueia)")
    void parseAndCreate_invalidPlannerIgnored() {
        String aiText = "Registrado!\n"
            + "<proposta_casamento>{\"briefing\":\"Casamento na praia\",\"planner_id\":\""
            + UUID.randomUUID() + "\"}</proposta_casamento>";

        Optional<WeddingProposal> o = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(o).isPresent();
        assertThat(o.get().status()).isEqualTo("rascunho");
        assertThat(o.get().plannerId()).isNull();
    }

    @Test
    @DisplayName("stripTag remove a tag <proposta_casamento> do texto da IA")
    void stripTag_removesTag() {
        String aiText = "Pronto, abri sua proposta.\n"
            + "<proposta_casamento>{\"briefing\":\"Casamento ao ar livre\"}</proposta_casamento>";
        String stripped = handler.stripTag(aiText);
        assertThat(stripped).doesNotContain("<proposta_casamento>");
        assertThat(stripped).contains("Pronto, abri sua proposta.");
    }
}
