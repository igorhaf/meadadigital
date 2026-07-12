package com.meada.profiles.estetica.appointments;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.estetica.appointments.AestheticAppointmentService.ConflictException;
import com.meada.profiles.estetica.appointments.AestheticAppointmentService.OutsideHoursException;
import com.meada.profiles.estetica.appointments.AestheticAppointmentService.PackageExhaustedException;
import com.meada.profiles.estetica.appointments.AestheticAppointmentService.PackageNotActiveException;
import com.meada.profiles.estetica.appointments.AestheticAppointmentService.PackageWrongContactException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o AestheticAppointmentService (camada 8.3) — A ESCAPADA (consumo de saldo):
 * agendar com pacote consome 1 sessão (materializado); a última sessão esgota o pacote; pacote
 * esgotado → package_exhausted; pacote de outro contato → package_wrong_contact; pacote pendente →
 * package_not_active; CANCELAR devolve a sessão (esgotado→ativo); avulso não mexe em saldo; conflito
 * por profissional (profissionais distintos no mesmo horário não conflitam).
 */
@org.springframework.context.annotation.Import(AestheticAppointmentServiceTest.TestConfig.class)
class AestheticAppointmentServiceTest extends AbstractIntegrationTest {

    private static final ZoneId TZ = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private AestheticAppointmentService service;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private static final UUID COMPANY = UUID.fromString("cf000000-0000-0000-0000-000000000001");
    private UUID contactId;
    private UUID otherContactId;
    private UUID prof1;
    private UUID prof2;
    private UUID procedureId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'estetica')",
            COMPANY, "Estetica Svc", "estetica-svc");
        jdbcTemplate.update("insert into aesthetic_config (company_id, opens_at, closes_at, slot_minutes) "
            + "values (?, '08:00', '20:00', 30)", COMPANY);
        contactId = UUID.randomUUID();
        otherContactId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990301", "Marina");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            otherContactId, COMPANY, "+5511999990302", "Pedro");
        prof1 = UUID.randomUUID();
        prof2 = UUID.randomUUID();
        jdbcTemplate.update("insert into aesthetic_professionals (id, company_id, name) values (?, ?, 'Camila')", prof1, COMPANY);
        jdbcTemplate.update("insert into aesthetic_professionals (id, company_id, name) values (?, ?, 'Tatiane')", prof2, COMPANY);
        procedureId = UUID.randomUUID();
        jdbcTemplate.update("insert into aesthetic_procedures (id, company_id, name, duration_minutes, unit_price_cents) "
            + "values (?, ?, 'Drenagem', 50, 12000)", procedureId, COMPANY);
    }

    /** Cria um pacote direto no banco com o status e saldo dados. */
    private UUID seedPackage(UUID contact, String status, int total, int used) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into aesthetic_packages (id, company_id, contact_id, procedure_id, customer_name, "
                + "procedure_name, unit_price_cents, total_sessions, sessions_used, sessions_remaining, "
                + "total_cents, status, activated_at) "
                + "values (?, ?, ?, ?, 'Cliente', 'Drenagem', 12000, ?, ?, ?, ?, ?, now())",
            id, COMPANY, contact, procedureId, total, used, total - used, total * 12000, status);
        return id;
    }

    private Instant slot(int plusDays, int hour) {
        return LocalDate.now(TZ).plusDays(plusDays).atTime(LocalTime.of(hour, 0)).atZone(TZ).toInstant();
    }

    @Test
    @DisplayName("fora do horário: antes do opens (06:00) e terminando após o closes → OutsideHoursException, nada persiste")
    void create_outsideHours() {
        // Único perfil de agenda que estava sem exercitar a validação outside_hours.
        assertThatThrownBy(() -> service.create(COMPANY, prof1, procedureId, null, contactId, null,
            slot(1, 6), "Marina", null, null))
            .isInstanceOf(OutsideHoursException.class);
        // 19:30 + 50min de duração termina 20:20, depois do closes 20:00 → também fora.
        assertThatThrownBy(() -> service.create(COMPANY, prof1, procedureId, null, contactId, null,
            slot(1, 19).plusSeconds(30 * 60), "Marina", null, null))
            .isInstanceOf(OutsideHoursException.class);
        Long count = jdbcTemplate.queryForObject("select count(*) from aesthetic_appointments", Long.class);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("agendar com pacote ativo → consome 1 sessão (remaining decrementado, materializado)")
    void create_consumesSession() {
        UUID pkg = seedPackage(contactId, "ativo", 10, 2);   // 8 restantes
        AestheticAppointment a = service.create(COMPANY, prof1, procedureId, pkg, contactId, null,
            slot(1, 10), "Marina", "+5511999990301", null);
        assertThat(a.consumedSession()).isTrue();
        int remaining = jdbcTemplate.queryForObject(
            "select sessions_remaining from aesthetic_packages where id = ?", Integer.class, pkg);
        int used = jdbcTemplate.queryForObject(
            "select sessions_used from aesthetic_packages where id = ?", Integer.class, pkg);
        assertThat(remaining).isEqualTo(7);
        assertThat(used).isEqualTo(3);
    }

    @Test
    @DisplayName("agendar a ÚLTIMA sessão → pacote vira 'esgotado'")
    void create_lastSessionExhausts() {
        UUID pkg = seedPackage(contactId, "ativo", 3, 2);   // 1 restante
        service.create(COMPANY, prof1, procedureId, pkg, contactId, null, slot(1, 10), "Marina", null, null);
        String status = jdbcTemplate.queryForObject("select status from aesthetic_packages where id = ?", String.class, pkg);
        int remaining = jdbcTemplate.queryForObject("select sessions_remaining from aesthetic_packages where id = ?", Integer.class, pkg);
        assertThat(status).isEqualTo("esgotado");
        assertThat(remaining).isZero();
    }

    @Test
    @DisplayName("agendar com pacote esgotado → PackageExhaustedException")
    void create_exhausted() {
        UUID pkg = seedPackage(contactId, "esgotado", 3, 3);
        assertThatThrownBy(() -> service.create(COMPANY, prof1, procedureId, pkg, contactId, null,
            slot(1, 10), "Marina", null, null)).isInstanceOf(PackageExhaustedException.class);
    }

    @Test
    @DisplayName("agendar com pacote de OUTRO contato → PackageWrongContactException")
    void create_wrongContact() {
        UUID pkg = seedPackage(otherContactId, "ativo", 10, 0);
        assertThatThrownBy(() -> service.create(COMPANY, prof1, procedureId, pkg, contactId, null,
            slot(1, 10), "Marina", null, null)).isInstanceOf(PackageWrongContactException.class);
    }

    @Test
    @DisplayName("agendar com pacote 'pendente' (não ativo) → PackageNotActiveException")
    void create_notActive() {
        UUID pkg = seedPackage(contactId, "pendente", 10, 0);
        assertThatThrownBy(() -> service.create(COMPANY, prof1, procedureId, pkg, contactId, null,
            slot(1, 10), "Marina", null, null)).isInstanceOf(PackageNotActiveException.class);
    }

    @Test
    @DisplayName("CANCELAR agendamento que consumiu → devolve a sessão (esgotado→ativo)")
    void cancel_returnsSession() {
        UUID pkg = seedPackage(contactId, "ativo", 3, 2);   // 1 restante
        AestheticAppointment a = service.create(COMPANY, prof1, procedureId, pkg, contactId, null,
            slot(1, 10), "Marina", null, null);
        // após criar: esgotado, 0 restantes.
        assertThat(jdbcTemplate.queryForObject("select status from aesthetic_packages where id = ?", String.class, pkg)).isEqualTo("esgotado");
        // cancela → devolve.
        service.updateStatus(COMPANY, a.id(), "cancelado");
        int remaining = jdbcTemplate.queryForObject("select sessions_remaining from aesthetic_packages where id = ?", Integer.class, pkg);
        String status = jdbcTemplate.queryForObject("select status from aesthetic_packages where id = ?", String.class, pkg);
        boolean consumed = jdbcTemplate.queryForObject("select consumed_session from aesthetic_appointments where id = ?", Boolean.class, a.id());
        assertThat(remaining).isEqualTo(1);
        assertThat(status).isEqualTo("ativo");
        assertThat(consumed).isFalse();   // não devolve duas vezes
    }

    @Test
    @DisplayName("agendamento AVULSO (package_id null) → consumed_session=false, nenhum pacote mexido")
    void create_avulso() {
        AestheticAppointment a = service.create(COMPANY, prof1, procedureId, null, contactId, null,
            slot(1, 10), "Marina", null, null);
        assertThat(a.consumedSession()).isFalse();
        assertThat(a.packageId()).isNull();
    }

    @Test
    @DisplayName("conflito por profissional → ConflictException; outro profissional mesmo horário → OK")
    void conflict_byProfessional() {
        Instant when = slot(1, 14);
        service.create(COMPANY, prof1, procedureId, null, contactId, null, when, "Marina", null, null);
        // mesmo prof, mesmo horário → conflito.
        assertThatThrownBy(() -> service.create(COMPANY, prof1, procedureId, null, otherContactId, null,
            when, "Pedro", null, null)).isInstanceOf(ConflictException.class);
        // outro profissional, mesmo horário → OK.
        AestheticAppointment ok = service.create(COMPANY, prof2, procedureId, null, otherContactId, null,
            when, "Pedro", null, null);
        assertThat(ok.id()).isNotNull();
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-estetica";
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
