package com.meada.whatsapp.profiles.academia.config;

import com.meada.whatsapp.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o AcademiaConfigService (camada 7.7): get com fallback aos defaults + update audita.
 */
class AcademiaConfigServiceTest extends AbstractIntegrationTest {

    @Autowired
    private AcademiaConfigService service;

    private static final UUID COMPANY = UUID.fromString("cc000000-0000-0000-0000-000000000003");
    private static final UUID USER = UUID.fromString("dc000000-0000-0000-0000-000000000003");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'academia')",
            COMPANY, "Academia F", "academia-f");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@aca-f.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("get sem linha → defaults (06:00/22:00); update faz upsert + audita")
    void getDefaultsAndUpdate() {
        AcademiaConfig def = service.get(COMPANY);
        assertThat(def.opensAt()).isEqualTo(LocalTime.of(6, 0));
        assertThat(def.closesAt()).isEqualTo(LocalTime.of(22, 0));

        AcademiaConfig saved = service.update(COMPANY, USER, LocalTime.of(5, 30), LocalTime.of(23, 0));
        assertThat(saved.opensAt()).isEqualTo(LocalTime.of(5, 30));
        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'academia_config_updated' and company_id = ?",
            Long.class, COMPANY);
        assertThat(audit).isEqualTo(1L);
    }
}
