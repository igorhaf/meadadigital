package com.meada.profiles.pizzaria.loyalty;

import com.meada.AbstractIntegrationTest;
import com.meada.profiles.pizzaria.loyalty.PizzariaLoyaltyConfigService.InvalidLoyaltyConfigException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o PizzariaLoyaltyConfigService (camada 8.9, backlog #2 — clone do chassi sushi): get com
 * fallback p/ defaults (enabled=false), update (upsert) e validação do reward.
 */
class PizzariaLoyaltyConfigServiceTest extends AbstractIntegrationTest {

    @Autowired
    private PizzariaLoyaltyConfigService service;

    private static final UUID COMPANY = UUID.fromString("d8f00000-0000-0000-0000-000000000093");
    private static final UUID USER = UUID.fromString("d8f00000-0000-0000-0000-000000000093");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'pizzaria')",
            COMPANY, "Pizzaria Loy", "pizzaria-loy-pz");
        // USER em auth.users + users (FK audit_log_user_id_fkey) — lição AuditLogger.
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@pizzaria-loy.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("get sem linha → defaults (enabled=false)")
    void getFallback() {
        PizzariaLoyaltyConfig cfg = service.get(COMPANY);
        assertThat(cfg.enabled()).isFalse();
        assertThat(cfg.companyId()).isEqualTo(COMPANY);
    }

    @Test
    @DisplayName("update (upsert) grava e re-update altera")
    void upsert() {
        PizzariaLoyaltyConfig saved = service.update(COMPANY, USER, true, 10, "percent", 15);
        assertThat(saved.enabled()).isTrue();
        assertThat(saved.thresholdOrders()).isEqualTo(10);
        assertThat(saved.rewardValue()).isEqualTo(15);

        PizzariaLoyaltyConfig again = service.update(COMPANY, USER, false, 5, "fixed", 1000);
        assertThat(again.enabled()).isFalse();
        assertThat(again.rewardKind()).isEqualTo("fixed");
        assertThat(again.rewardValue()).isEqualTo(1000);
    }

    @Test
    @DisplayName("reward percent fora de 0..100 → InvalidLoyaltyConfigException")
    void invalidReward() {
        assertThatThrownBy(() -> service.update(COMPANY, USER, true, 10, "percent", 101))
            .isInstanceOf(InvalidLoyaltyConfigException.class);
    }

    @Test
    @DisplayName("threshold < 1 → InvalidLoyaltyConfigException")
    void invalidThreshold() {
        assertThatThrownBy(() -> service.update(COMPANY, USER, true, 0, "percent", 10))
            .isInstanceOf(InvalidLoyaltyConfigException.class);
    }
}
