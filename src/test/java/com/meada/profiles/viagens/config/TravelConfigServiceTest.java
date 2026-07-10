package com.meada.profiles.viagens.config;

import com.meada.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o TravelConfigService (camada 8.18 / perfil viagens): GET fallback (ausente → nulls), PUT
 * upsert grava nome da agência + notas. Espelho do EventConfig/lavanderia config test.
 */
class TravelConfigServiceTest extends AbstractIntegrationTest {

    @Autowired
    private TravelConfigService service;

    private static final UUID COMPANY = UUID.fromString("ce100000-0000-0000-0000-000000000002");
    private static final UUID USER = UUID.fromString("df100000-0000-0000-0000-000000000002");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'viagens')",
            COMPANY, "Viagens Cfg", "viagens-cfg");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@viagens-cfg.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("GET sem config → fallback (businessName/notes null)")
    void getFallback() {
        TravelConfig cfg = service.get(COMPANY);
        assertThat(cfg.businessName()).isNull();
        assertThat(cfg.notes()).isNull();
    }

    @Test
    @DisplayName("PUT upsert grava nome da agência + notas e GET reflete")
    void putUpsert() {
        TravelConfig saved = service.update(COMPANY, USER, "Agência Modelo", "Atendimento 9h-18h",
            true, true, 2);
        assertThat(saved.businessName()).isEqualTo("Agência Modelo");
        assertThat(saved.notes()).isEqualTo("Atendimento 9h-18h");

        TravelConfig fetched = service.get(COMPANY);
        assertThat(fetched.businessName()).isEqualTo("Agência Modelo");
        assertThat(fetched.notes()).isEqualTo("Atendimento 9h-18h");
        assertThat(fetched.tripReminderEnabled()).isTrue();
        assertThat(fetched.quoteFollowupEnabled()).isTrue();
        assertThat(fetched.quoteFollowupDays()).isEqualTo(2);
    }
}
