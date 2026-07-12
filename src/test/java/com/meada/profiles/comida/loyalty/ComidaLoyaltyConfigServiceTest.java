package com.meada.profiles.comida.loyalty;

import com.meada.AbstractIntegrationTest;
import com.meada.profiles.comida.ComidaMenuCache;
import com.meada.profiles.comida.loyalty.ComidaLoyaltyConfigService.InvalidLoyaltyConfigException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o ComidaLoyaltyConfigService (onda 1 do comida, backlog #2): validação de config e,
 * principalmente, a INVALIDAÇÃO do ComidaMenuCache no update — o segmento do prompt embute o
 * bloco FIDELIDADE e não pode anunciar config velha até o TTL (regressão real: o update não
 * invalidava; o irmão BarberLoyaltyConfigService invalida).
 */
class ComidaLoyaltyConfigServiceTest extends AbstractIntegrationTest {

    @Autowired
    private ComidaLoyaltyConfigService service;
    @Autowired
    private ComidaMenuCache menuCache;

    private static final UUID COMPANY = UUID.fromString("c4000000-0000-0000-0000-000000000086");
    private static final UUID USER = UUID.fromString("d4000000-0000-0000-0000-000000000086");
    private UUID contactId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'comida')",
            COMPANY, "Comida Loy", "comida-loy");
        // USER em auth.users + users (FK audit_log_user_id_fkey) — lição AuditLogger.
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@comida-loy.dev', 'admin')",
            USER, COMPANY);
        // O bloco FIDELIDADE do segmento só renderiza com contactId presente.
        contactId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990286", "Cliente Fiel");
    }

    @Test
    @DisplayName("update da fidelidade INVALIDA o ComidaMenuCache — a IA não anuncia config velha até o TTL")
    void update_invalidatesMenuCache() {
        service.update(COMPANY, USER, true, 5, "percent", 10);
        String before = menuCache.menuSegment(COMPANY, contactId);   // popula o cache (threshold 5)
        assertThat(before).contains("a cada 5");

        service.update(COMPANY, USER, true, 9, "percent", 10);
        String after = menuCache.menuSegment(COMPANY, contactId);

        // Sem a invalidação, o cache devolveria o segmento VELHO ("a cada 5") até o TTL.
        assertThat(after).contains("a cada 9");
    }

    @Test
    @DisplayName("config inválida (threshold < 1 / kind desconhecido / percent > 100) → InvalidLoyaltyConfigException")
    void update_invalidConfigs() {
        assertThatThrownBy(() -> service.update(COMPANY, USER, true, 0, "percent", 10))
            .isInstanceOf(InvalidLoyaltyConfigException.class);
        assertThatThrownBy(() -> service.update(COMPANY, USER, true, 5, "cashback", 10))
            .isInstanceOf(InvalidLoyaltyConfigException.class);
        assertThatThrownBy(() -> service.update(COMPANY, USER, true, 5, "percent", 101))
            .isInstanceOf(InvalidLoyaltyConfigException.class);
    }
}
