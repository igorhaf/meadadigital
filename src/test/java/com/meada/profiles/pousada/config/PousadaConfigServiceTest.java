package com.meada.profiles.pousada.config;

import com.meada.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o PousadaConfigService (camada 7.6): get com fallback aos defaults, update audita + persiste.
 */
class PousadaConfigServiceTest extends AbstractIntegrationTest {

    @Autowired
    private PousadaConfigService service;

    private static final UUID COMPANY = UUID.fromString("cb000000-0000-0000-0000-000000000002");
    private static final UUID USER = UUID.fromString("db000000-0000-0000-0000-000000000002");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'pousada')",
            COMPANY, "Pousada C", "pousada-c");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@pousada-c.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("get sem linha → defaults (14:00/11:00/null)")
    void get_defaults() {
        PousadaConfig config = service.get(COMPANY);
        assertThat(config.checkInTime()).isEqualTo(LocalTime.of(14, 0));
        assertThat(config.checkOutTime()).isEqualTo(LocalTime.of(11, 0));
        assertThat(config.cancellationPolicy()).isNull();
    }

    @Test
    @DisplayName("update faz upsert + audita pousada_config_updated")
    void update_audits() {
        PousadaConfig saved = service.update(COMPANY, USER, LocalTime.of(15, 0), LocalTime.of(12, 0),
            "Cancelamento grátis até 7 dias antes.", true, false);
        assertThat(saved.checkInTime()).isEqualTo(LocalTime.of(15, 0));
        assertThat(saved.cancellationPolicy()).contains("7 dias");
        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'pousada_config_updated' and company_id = ?",
            Long.class, COMPANY);
        assertThat(audit).isEqualTo(1L);
    }
}
