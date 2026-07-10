package com.meada.profiles.lavanderia.config;

import com.meada.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o LavanderiaConfigService (camada 8.10): GET fallback (ausente → 0/0/1), PUT upsert com
 * turnaround default.
 */
class LavanderiaConfigServiceTest extends AbstractIntegrationTest {

    @Autowired
    private LavanderiaConfigService service;

    private static final UUID COMPANY = UUID.fromString("1a000000-0000-0000-0000-000000000074");
    private static final UUID USER = UUID.fromString("1b000000-0000-0000-0000-000000000074");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'lavanderia')",
            COMPANY, "Lavanderia Cfg", "lavanderia-cfg");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@lavanderia-cfg.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("GET sem config → fallback 0/0/1 (turnaround default 1)")
    void getFallback() {
        LavanderiaConfig cfg = service.get(COMPANY);
        assertThat(cfg.deliveryFeeCents()).isZero();
        assertThat(cfg.minOrderCents()).isZero();
        assertThat(cfg.turnaroundDaysDefault()).isEqualTo(1);
    }

    @Test
    @DisplayName("PUT upsert grava taxa+mínimo+turnaround default e GET reflete")
    void putUpsert() {
        LavanderiaConfig saved = service.update(COMPANY, USER, 700, 3000, 3, true, 50, 1, true, true, 2, false, 30, null);
        assertThat(saved.deliveryFeeCents()).isEqualTo(700);
        assertThat(saved.minOrderCents()).isEqualTo(3000);
        assertThat(saved.turnaroundDaysDefault()).isEqualTo(3);

        LavanderiaConfig fetched = service.get(COMPANY);
        assertThat(fetched.deliveryFeeCents()).isEqualTo(700);
        assertThat(fetched.turnaroundDaysDefault()).isEqualTo(3);
    }
}
