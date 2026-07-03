package com.meada.profiles.barbearia.appointments;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.barbearia.appointments.BarberAppointmentService.ConflictException;
import com.meada.profiles.barbearia.appointments.BarberAppointmentService.InactiveBarberException;
import com.meada.profiles.barbearia.appointments.BarberAppointmentService.OutsideHoursException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o BarberAppointmentService (camada 8.1, clone do salon): create válida (snapshots), fora do
 * horário, barbeiro inativo, conflito MESMO BARBEIRO, MESMO HORÁRIO BARBEIRO DIFERENTE = OK
 * (paralelismo), e transição com notificação (com nome do barbeiro). EvolutionSender fake.
 */
@Import(BarberAppointmentServiceTest.TestConfig.class)
class BarberAppointmentServiceTest extends AbstractIntegrationTest {

    @Autowired
    private BarberAppointmentService service;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private static final UUID COMPANY = UUID.fromString("cb000000-0000-0000-0000-000000000003");
    private UUID barberMarcelo;
    private UUID barberJunior;
    private UUID serviceCorte;
    private UUID contactId;
    private UUID conversationId;

    // 2026-07-01T12:00-03:00 (BRT) → dentro de 09:00–20:00; Corte dura 30min.
    private static final Instant START = Instant.parse("2026-07-01T15:00:00Z");

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'barbearia')",
            COMPANY, "Barbearia B", "barbearia-b");
        barberMarcelo = UUID.randomUUID();
        barberJunior = UUID.randomUUID();
        jdbcTemplate.update("insert into barber_barbers (id, company_id, name, specialty) values (?, ?, 'Marcelo', 'corte/barba')",
            barberMarcelo, COMPANY);
        jdbcTemplate.update("insert into barber_barbers (id, company_id, name, specialty) values (?, ?, 'Júnior', 'degradê')",
            barberJunior, COMPANY);
        serviceCorte = UUID.randomUUID();
        jdbcTemplate.update("insert into barber_services (id, company_id, name, category, duration_minutes, price_cents) "
            + "values (?, ?, 'Corte', 'Cabelo', 30, 4000)", serviceCorte, COMPANY);
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990060", "Cliente");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
    }

    private BarberAppointment seedWithMarcelo() {
        return service.create(COMPANY, barberMarcelo, serviceCorte, contactId, conversationId, START, "Cliente", "+5511999990060", null, null);
    }

    @Test
    @DisplayName("create válida → agendado, com snapshots de barbeiro/serviço/preço/duração e end_at materializado")
    void create_agendado() {
        BarberAppointment a = seedWithMarcelo();
        assertThat(a.status()).isEqualTo("agendado");
        assertThat(a.barberName()).isEqualTo("Marcelo");
        assertThat(a.serviceName()).isEqualTo("Corte");
        assertThat(a.priceCents()).isEqualTo(4000);
        assertThat(a.durationMinutes()).isEqualTo(30);
        // end_at materializado = start + 30min.
        assertThat(a.endAt()).isEqualTo(a.startAt().plusSeconds(30 * 60L));
    }

    @Test
    @DisplayName("create fora do horário (07:00 BRT) → OutsideHoursException (400)")
    void create_outsideHours() {
        Instant early = Instant.parse("2026-07-01T10:00:00Z");   // 07:00 BRT, antes de 09:00
        assertThatThrownBy(() -> service.create(COMPANY, barberMarcelo, serviceCorte, null, null, early, "C", null, null, null))
            .isInstanceOf(OutsideHoursException.class);
    }

    @Test
    @DisplayName("create com barbeiro inativo → InactiveBarberException (400)")
    void create_inactiveBarber() {
        jdbcTemplate.update("update barber_barbers set active = false where id = ?", barberMarcelo);
        assertThatThrownBy(() -> service.create(COMPANY, barberMarcelo, serviceCorte, null, null, START, "C", null, null, null))
            .isInstanceOf(InactiveBarberException.class);
    }

    @Test
    @DisplayName("conflito MESMO BARBEIRO (Marcelo, mesmo horário) → ConflictException (409)")
    void create_conflictSameBarber() {
        seedWithMarcelo();
        assertThatThrownBy(() -> service.create(COMPANY, barberMarcelo, serviceCorte, null, null, START, "Outro", null, null, null))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("MESMO HORÁRIO, BARBEIRO DIFERENTE (Júnior) → OK (paralelismo)")
    void create_sameSlotDifferentBarber() {
        seedWithMarcelo();
        assertThatCode(() -> service.create(COMPANY, barberJunior, serviceCorte, null, null, START, "Outra", null, null, null))
            .doesNotThrowAnyException();
        Long count = jdbcTemplate.queryForObject("select count(*) from barber_appointments where company_id = ?",
            Long.class, COMPANY);
        assertThat(count).isEqualTo(2L);
    }

    @Test
    @DisplayName("updateStatus agendado→confirmado → notifica com nome do barbeiro")
    void confirm_notifies() {
        BarberAppointment a = seedWithMarcelo();
        BarberAppointment confirmed = service.updateStatus(COMPANY, a.id(), "confirmado");
        assertThat(confirmed.status()).isEqualTo("confirmado");
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("confirmado").contains("Marcelo");
    }

    // -------------------------------------------------------------------------
    // ONDA 1 DO BACKLOG: fidelidade (#3) e cupom (#12) aplicados na criação.
    // -------------------------------------------------------------------------

    /** Insere N agendamentos REALIZADOS do contato direto no banco (alimenta a contagem da fidelidade). */
    private void seedRealized(int n) {
        for (int i = 0; i < n; i++) {
            jdbcTemplate.update(
                "insert into barber_appointments (company_id, barber_id, service_id, contact_id, guest_name, "
                    + "start_at, duration_minutes, end_at, service_name, barber_name, price_cents, status) "
                    + "values (?, ?, ?, ?, 'Cliente', now() - interval '10 days', 30, "
                    + "now() - interval '10 days' + interval '30 minutes', 'Corte', 'Marcelo', 4000, 'realizado')",
                COMPANY, barberMarcelo, serviceCorte, contactId);
        }
    }

    private UUID seedCoupon(String code, String kind, int value, int minOrderCents, boolean active,
                            java.time.LocalDate validUntil) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into barber_coupons (id, company_id, code, kind, value, min_order_cents, valid_until, active) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?)",
            id, COMPANY, code, kind, value, minOrderCents,
            validUntil == null ? null : java.sql.Date.valueOf(validUntil), active);
        return id;
    }

    @Test
    @DisplayName("FIDELIDADE: ao completar o ciclo (N realizados), o próximo agendamento sai GRÁTIS")
    void loyalty_freeCutOnCycle() {
        jdbcTemplate.update("insert into barber_loyalty_config (company_id, enabled, threshold_cuts) values (?, true, 2)", COMPANY);
        seedRealized(2);   // 2 % 2 == 0 → o próximo é grátis.

        BarberAppointment free = seedWithMarcelo();
        assertThat(free.loyaltyApplied()).isTrue();
        assertThat(free.discountCents()).isEqualTo(4000);   // desconto = preço → a cobrar 0.
    }

    @Test
    @DisplayName("FIDELIDADE: fora do ciclo (1 realizado de 2) ou desligada → sem desconto")
    void loyalty_notDue() {
        jdbcTemplate.update("insert into barber_loyalty_config (company_id, enabled, threshold_cuts) values (?, true, 2)", COMPANY);
        seedRealized(1);
        BarberAppointment a = seedWithMarcelo();
        assertThat(a.loyaltyApplied()).isFalse();
        assertThat(a.discountCents()).isZero();
    }

    @Test
    @DisplayName("CUPOM percent válido (case-insensitive) → desconto aplicado + uses incrementado")
    void coupon_percentApplied() {
        UUID coupon = seedCoupon("DEZ", "percent", 10, 0, true, null);
        BarberAppointment a = service.create(COMPANY, barberMarcelo, serviceCorte, contactId,
            conversationId, START, "Cliente", null, null, "dez");
        assertThat(a.discountCents()).isEqualTo(400);   // 10% de 4000.
        assertThat(a.couponCodeSnapshot()).isEqualTo("DEZ");
        Integer uses = jdbcTemplate.queryForObject("select uses from barber_coupons where id = ?", Integer.class, coupon);
        assertThat(uses).isEqualTo(1);
    }

    @Test
    @DisplayName("CUPOM inválido (vencido/inativo/mínimo) → agendamento sai SEM desconto (não aborta)")
    void coupon_invalidSilentlyIgnored() {
        seedCoupon("VENCIDO", "percent", 10, 0, true, java.time.LocalDate.now().minusDays(1));
        seedCoupon("OFF", "percent", 10, 0, false, null);
        seedCoupon("MINIMO", "percent", 10, 999999, true, null);
        String[] codes = {"VENCIDO", "OFF", "MINIMO", "NAOEXISTE"};
        for (int i = 0; i < codes.length; i++) {
            // dias diferentes pra não conflitar o slot do mesmo barbeiro (mesma hora, dentro da janela).
            BarberAppointment a = service.create(COMPANY, barberJunior, serviceCorte, contactId,
                conversationId, START.plus(java.time.Duration.ofDays(i)), "Cliente", null, null, codes[i]);
            assertThat(a.discountCents()).isZero();
            assertThat(a.couponCodeSnapshot()).isNull();
        }
    }

    @Test
    @DisplayName("FIDELIDADE tem precedência: corte grátis não acumula cupom (uses não incrementa)")
    void loyalty_precedesCoupon() {
        jdbcTemplate.update("insert into barber_loyalty_config (company_id, enabled, threshold_cuts) values (?, true, 2)", COMPANY);
        seedRealized(2);
        UUID coupon = seedCoupon("DEZ", "percent", 10, 0, true, null);
        BarberAppointment a = service.create(COMPANY, barberMarcelo, serviceCorte, contactId,
            conversationId, START, "Cliente", null, null, "DEZ");
        assertThat(a.loyaltyApplied()).isTrue();
        assertThat(a.discountCents()).isEqualTo(4000);
        assertThat(a.couponCodeSnapshot()).isNull();
        Integer uses = jdbcTemplate.queryForObject("select uses from barber_coupons where id = ?", Integer.class, coupon);
        assertThat(uses).isZero();
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-barber";
        }
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        FakeEvolutionSender fakeEvolutionSender() {
            return new FakeEvolutionSender();
        }
    }
}
