package com.meada.profiles.academia.daypasses;

import com.meada.AbstractIntegrationTest;
import com.meada.profiles.academia.daypasses.AcademiaDayPassService.DayPassNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o AcademiaDayPassService (camada 7.7): cria passe NÃO pago (+ audita) e marca pago.
 */
class AcademiaDayPassServiceTest extends AbstractIntegrationTest {

    @Autowired
    private AcademiaDayPassService service;

    private static final UUID COMPANY = UUID.fromString("cc000000-0000-0000-0000-0000000000d5");
    private static final UUID USER = UUID.fromString("dc000000-0000-0000-0000-0000000000d5");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'academia')",
            COMPANY, "Academia DayPass", "academia-daypass");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'dp@aca.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("create → passe nasce NÃO pago + audita academia_day_pass_created")
    void create_notPaidAndAudits() {
        AcademiaDayPass p = service.create(COMPANY, USER, null, "Visitante", "+5511999990000",
            null, LocalDate.now(), 3000);

        assertThat(p.guestName()).isEqualTo("Visitante");
        assertThat(p.priceCents()).isEqualTo(3000);
        assertThat(p.paid()).isFalse();

        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'academia_day_pass_created' and entity_id = ?",
            Long.class, p.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("markPaid → passe fica pago + audita academia_day_pass_paid")
    void markPaid_setsPaid() {
        AcademiaDayPass p = service.create(COMPANY, USER, null, "Avulso", null, null, LocalDate.now(), 2500);
        assertThat(p.paid()).isFalse();

        AcademiaDayPass paid = service.markPaid(COMPANY, USER, p.id());
        assertThat(paid.paid()).isTrue();

        Boolean dbPaid = jdbcTemplate.queryForObject(
            "select paid from academia_day_passes where id = ?", Boolean.class, p.id());
        assertThat(dbPaid).isTrue();

        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'academia_day_pass_paid' and entity_id = ?",
            Long.class, p.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("markPaid de passe inexistente → DayPassNotFoundException (404)")
    void markPaid_notFound() {
        assertThatThrownBy(() -> service.markPaid(COMPANY, USER, UUID.randomUUID()))
            .isInstanceOf(DayPassNotFoundException.class);
    }
}
