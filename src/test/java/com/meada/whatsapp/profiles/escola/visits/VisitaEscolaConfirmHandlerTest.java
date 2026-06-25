package com.meada.whatsapp.profiles.escola.visits;

import com.meada.whatsapp.AbstractIntegrationTest;
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
 * Testa o VisitaEscolaConfirmHandler (camada 8.19, ESCAPADA 2): tag válida → cria visita agendada;
 * data passada → empty; período inválido → empty; sem tag → empty; stripOrderTag remove a tag.
 */
class VisitaEscolaConfirmHandlerTest extends AbstractIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private VisitaEscolaConfirmHandler handler;

    private static final UUID COMPANY = UUID.fromString("ce000000-0000-0000-0000-000000000008");
    private UUID conversationId;
    private UUID contactId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'escola')",
            COMPANY, "Escola VH", "escola-vh");
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990800", "Maria");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
    }

    @Test
    @DisplayName("tag válida (data futura + período) → cria visita agendada")
    void parseAndCreate_ok() {
        String future = LocalDate.now(ZONE).plusDays(6).toString();
        String aiText = "Combinado! Agendei a visita.\n"
            + "<visita_escola>{\"visit_date\":\"" + future + "\",\"period\":\"manha\",\"num_people\":2}</visita_escola>";

        Optional<EscolaVisit> v = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(v).isPresent();
        assertThat(v.get().status()).isEqualTo("agendada");
        assertThat(v.get().period()).isEqualTo("manha");
        assertThat(v.get().visitorName()).isEqualTo("Maria");
    }

    @Test
    @DisplayName("data passada na tag → Optional.empty (não criado)")
    void parseAndCreate_pastDate() {
        String past = LocalDate.now(ZONE).minusDays(1).toString();
        String aiText = "Agendado!\n<visita_escola>{\"visit_date\":\"" + past + "\",\"period\":\"manha\"}</visita_escola>";
        Optional<EscolaVisit> v = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(v).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from escola_visits", Long.class);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("período inválido na tag → Optional.empty (não criado)")
    void parseAndCreate_invalidPeriod() {
        String future = LocalDate.now(ZONE).plusDays(4).toString();
        String aiText = "Agendado!\n<visita_escola>{\"visit_date\":\"" + future + "\",\"period\":\"noite\"}</visita_escola>";
        Optional<EscolaVisit> v = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(v).isEmpty();
    }

    @Test
    @DisplayName("texto sem tag → Optional.empty (conversa normal)")
    void parseAndCreate_noTag() {
        Optional<EscolaVisit> v = handler.parseAndCreate(
            COMPANY, conversationId, contactId, "Quer conhecer a escola? Posso agendar uma visita.");
        assertThat(v).isEmpty();
    }

    @Test
    @DisplayName("stripOrderTag remove a tag <visita_escola>")
    void stripOrderTag_removes() {
        String future = LocalDate.now(ZONE).plusDays(3).toString();
        String aiText = "Combinado!\n<visita_escola>{\"visit_date\":\"" + future + "\",\"period\":\"tarde\"}</visita_escola>";
        assertThat(handler.hasOrderTag(aiText)).isTrue();
        String stripped = handler.stripOrderTag(aiText);
        assertThat(stripped).isEqualTo("Combinado!");
        assertThat(handler.hasOrderTag(stripped)).isFalse();
    }
}
