package com.meada.whatsapp.appointments;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.ai.AppointmentAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test do {@link AppointmentService} (camada 5.19 #60/#64) contra PostgreSQL real
 * (Testcontainers). Cobre book (válido + conflito), cancel e reschedule.
 *
 * <p>Estratégia de horário: escolhemos uma data FUTURA fixa (daqui ~7 dias, num horário canônico)
 * e semeamos uma janela availability_slots ATIVA cobrindo o weekday/horário dessa data no fuso
 * America/Sao_Paulo — assim o {@code isWithinActiveWindow} do service deixa passar, e
 * {@code findActiveByContact} (scheduled_at > now()) enxerga o agendamento (é futuro).
 */
class AppointmentServiceIntegrationTest extends AbstractIntegrationTest {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    private static final UUID COMPANY = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID CONTACT = UUID.fromString("b2000000-0000-0000-0000-000000000001");
    private static final UUID INSTANCE = UUID.fromString("b1000000-0000-0000-0000-000000000001");
    private static final UUID CONV = UUID.fromString("b3000000-0000-0000-0000-000000000001");

    @Autowired
    private AppointmentService service;
    @Autowired
    private AppointmentRepository repository;

    /** Horário-alvo: daqui ~7 dias, às 10:00 local SP (cai numa janela 09:00–12:00 que semeamos). */
    private LocalDateTime targetLocal;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug) values (?, ?, ?)",
            COMPANY, "Empresa B", "empresa-b");
        jdbcTemplate.update(
            "insert into whatsapp_instances (id, company_id, instance_name, evolution_token) "
                + "values (?, ?, ?, ?)", INSTANCE, COMPANY, "inst-b", "tok-b");
        jdbcTemplate.update(
            "insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            CONTACT, COMPANY, "+5511988880001", "Cliente B");
        jdbcTemplate.update(
            "insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
                + "values (?, ?, ?, ?, 'open', 'ai')",
            CONV, COMPANY, CONTACT, INSTANCE);

        // Data futura fixa às 10:00 local, daqui 7 dias. Janela 09:00–12:00 no weekday dessa data.
        LocalDate targetDate = LocalDate.now(SP).plusDays(7);
        targetLocal = LocalDateTime.of(targetDate, LocalTime.of(10, 0));
        int weekday = targetDate.getDayOfWeek().getValue() % 7;   // Sun=0..Sat=6
        jdbcTemplate.update(
            "insert into availability_slots (company_id, weekday, starts_at, ends_at, slot_minutes, active) "
                + "values (?, ?, '09:00'::time, '12:00'::time, 30, true)",
            COMPANY, weekday);
    }

    /** whenIso ISO local "yyyy-MM-ddTHH:mm" do horário-alvo (sem segundos). */
    private String targetIso() {
        return targetLocal.withSecond(0).withNano(0).toString();
    }

    private Instant targetInstant() {
        return targetLocal.atZone(SP).toInstant();
    }

    private long countScheduled() {
        return jdbcTemplate.queryForObject(
            "select count(*) from appointments where company_id = ? and status = 'scheduled'",
            Long.class, COMPANY);
    }

    @Test
    @DisplayName("book em slot válido → persiste um appointment 'scheduled' do contato")
    void book_validSlot_persists() {
        AppointmentAction action = new AppointmentAction("book", targetIso(), "Corte");

        service.applyAppointmentAction(COMPANY, CONV, action);

        assertThat(countScheduled()).isEqualTo(1);
        Optional<Appointment> active = repository.findActiveByContact(CONTACT);
        assertThat(active).isPresent();
        assertThat(active.get().status()).isEqualTo("scheduled");
        assertThat(active.get().scheduledAt()).isEqualTo(targetInstant());
        assertThat(active.get().conversationId()).isEqualTo(CONV);
    }

    @Test
    @DisplayName("book em horário já tomado → não duplica (unique parcial), segue 1 agendamento")
    void book_takenSlot_doesNotDuplicate() {
        // 1º book ocupa o horário.
        service.applyAppointmentAction(COMPANY, CONV, new AppointmentAction("book", targetIso(), null));
        assertThat(countScheduled()).isEqualTo(1);

        // 2º book no MESMO horário (mesmo contato) → conflito barrado pelo unique → sem duplicar.
        service.applyAppointmentAction(COMPANY, CONV, new AppointmentAction("book", targetIso(), null));

        assertThat(countScheduled()).isEqualTo(1);
    }

    @Test
    @DisplayName("cancel → o agendamento ativo do contato vira 'cancelled'")
    void cancel_setsStatusCancelled() {
        service.applyAppointmentAction(COMPANY, CONV, new AppointmentAction("book", targetIso(), null));
        UUID apptId = repository.findActiveByContact(CONTACT).orElseThrow().id();

        service.applyAppointmentAction(COMPANY, CONV, new AppointmentAction("cancel", null, null));

        String status = jdbcTemplate.queryForObject(
            "select status from appointments where id = ?", String.class, apptId);
        assertThat(status).isEqualTo("cancelled");
        assertThat(repository.findActiveByContact(CONTACT)).isEmpty();   // não há mais ativo
    }

    @Test
    @DisplayName("reschedule → move o agendamento ativo para um novo horário válido")
    void reschedule_movesTime() {
        service.applyAppointmentAction(COMPANY, CONV, new AppointmentAction("book", targetIso(), null));
        UUID apptId = repository.findActiveByContact(CONTACT).orElseThrow().id();

        // Novo horário às 11:00 (mesma janela 09:00–12:00 do mesmo dia).
        LocalDateTime newLocal = targetLocal.withHour(11).withMinute(0).withSecond(0).withNano(0);
        Instant newInstant = newLocal.atZone(SP).toInstant();
        service.applyAppointmentAction(
            COMPANY, CONV, new AppointmentAction("reschedule", newLocal.toString(), null));

        Timestamp persisted = jdbcTemplate.queryForObject(
            "select scheduled_at from appointments where id = ?", Timestamp.class, apptId);
        assertThat(persisted.toInstant().truncatedTo(ChronoUnit.SECONDS))
            .isEqualTo(newInstant.truncatedTo(ChronoUnit.SECONDS));
        assertThat(countScheduled()).isEqualTo(1);
    }
}
