package com.meada.profiles.barbearia.reports;

import com.meada.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa as agregações do BarberReportsRepository (onda 1, backlog #15): faturamento = SÓ realizados,
 * líquido (preço − desconto; corte grátis da fidelidade fatura 0); faltas/cancelados contados à
 * parte; ranking por serviço e por barbeiro (snapshots).
 */
class BarberReportsRepositoryTest extends AbstractIntegrationTest {

    private static final UUID COMPANY = UUID.fromString("cbe00000-0000-0000-0000-000000000084");

    @Autowired
    private BarberReportsRepository repository;

    private UUID barberId;
    private UUID serviceId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'barbearia')",
            COMPANY, "Barbearia Rep", "barbearia-rep");
        barberId = UUID.randomUUID();
        serviceId = UUID.randomUUID();
        jdbcTemplate.update("insert into barber_barbers (id, company_id, name) values (?, ?, 'Marcelo')",
            barberId, COMPANY);
        jdbcTemplate.update("insert into barber_services (id, company_id, name, duration_minutes, price_cents) "
            + "values (?, ?, 'Corte', 30, 4000)", serviceId, COMPANY);
    }

    private void seedAppointment(String status, String serviceName, Integer price, int discount, boolean loyalty) {
        jdbcTemplate.update(
            "insert into barber_appointments (company_id, barber_id, service_id, guest_name, start_at, "
                + "duration_minutes, end_at, service_name, barber_name, price_cents, discount_cents, "
                + "loyalty_applied, status) "
                + "values (?, ?, ?, 'Cliente', now() - interval '2 days', 30, "
                + "now() - interval '2 days' + interval '30 minutes', ?, 'Marcelo', ?, ?, ?, ?)",
            COMPANY, barberId, serviceId, serviceName, price, discount, loyalty, status);
    }

    @Test
    @DisplayName("faturamento líquido só de realizados; grátis da fidelidade fatura 0; faltas contadas à parte")
    void totals_netRevenue() {
        seedAppointment("realizado", "Corte", 4000, 400, false);   // cupom: fatura 3600
        seedAppointment("realizado", "Corte", 4000, 4000, true);   // grátis fidelidade: fatura 0
        seedAppointment("realizado", "Barba", 2500, 0, false);     // fatura 2500
        seedAppointment("falta", "Corte", 4000, 0, false);         // não fatura, conta como falta
        seedAppointment("cancelado", "Corte", 4000, 0, false);     // não fatura
        seedAppointment("agendado", "Corte", 4000, 0, false);      // aberto, fora

        Instant since = Instant.now().minus(30, ChronoUnit.DAYS);
        BarberReportsRepository.Totals totals = repository.totals(COMPANY, since);
        assertThat(totals.realized()).isEqualTo(3);
        assertThat(totals.noShows()).isEqualTo(1);
        assertThat(totals.cancelled()).isEqualTo(1);
        assertThat(totals.totalCents()).isEqualTo(6100);

        List<Map<String, Object>> byService = repository.byService(COMPANY, since);
        assertThat(byService.get(0)).containsEntry("serviceName", "Corte").containsEntry("count", 2L)
            .containsEntry("totalCents", 3600L);
        assertThat(byService.get(1)).containsEntry("serviceName", "Barba").containsEntry("totalCents", 2500L);

        List<Map<String, Object>> byBarber = repository.byBarber(COMPANY, since);
        assertThat(byBarber).hasSize(1);
        assertThat(byBarber.get(0)).containsEntry("barberName", "Marcelo")
            .containsEntry("count", 3L).containsEntry("noShows", 1L).containsEntry("totalCents", 6100L);

        List<Map<String, Object>> byMonth = repository.byMonth(COMPANY, since);
        assertThat(byMonth).hasSize(1);
        assertThat(byMonth.get(0)).containsEntry("count", 3L).containsEntry("totalCents", 6100L);
    }
}
