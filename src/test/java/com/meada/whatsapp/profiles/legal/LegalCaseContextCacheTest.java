package com.meada.whatsapp.profiles.legal;

import com.meada.whatsapp.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Testa o LegalCaseContextCache (camada 7.2): invalida ao mutar — vê a mudança na hora. */
class LegalCaseContextCacheTest extends AbstractIntegrationTest {

    @Autowired
    private LegalCaseContextCache cache;

    private static final UUID COMPANY = UUID.fromString("ca000000-0000-0000-0000-000000000001");
    private UUID contactId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'legal')",
            COMPANY, "Adv Cache", "adv-cache");
        contactId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511966665555", "Cliente Cache");
        jdbcTemplate.update("insert into legal_clients (company_id, name, contact_id) values (?, 'Cliente Cache', ?)",
            COMPANY, contactId);
    }

    @Test
    @DisplayName("cache reflete a mudança após invalidação explícita")
    void invalidatesOnMutate() {
        String first = cache.contextSegment(COMPANY, contactId);
        assertThat(first).contains("Cliente Cache");
        assertThat(first).doesNotContain("Ação Cacheada");

        // adiciona um processo + invalida.
        UUID clientId = jdbcTemplate.queryForObject(
            "select id from legal_clients where company_id=? and contact_id=?", UUID.class, COMPANY, contactId);
        jdbcTemplate.update("insert into legal_cases (company_id, legal_client_id, cnj_number, title) "
            + "values (?, ?, '07102331520258070019', 'Ação Cacheada')", COMPANY, clientId);
        cache.invalidate(COMPANY, contactId);

        String second = cache.contextSegment(COMPANY, contactId);
        assertThat(second).contains("Ação Cacheada");
    }

    @Test
    @DisplayName("contato não identificado → bloco pede identificação")
    void unidentified() {
        String seg = cache.contextSegment(COMPANY, UUID.randomUUID());
        assertThat(seg).contains("CLIENTE NÃO IDENTIFICADO");
    }
}
