package com.meada.profiles.academia.birthday;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test do {@link AcademiaAniversarioJob} (backlog Academia #14) contra PostgreSQL real.
 * A lógica roda via {@link AcademiaAniversarioJob#runBirthdayGreetings()} direto (sem o scheduler).
 * EvolutionSender é um FAKE que só registra os envios.
 *
 * <p>Cenários: aniversariante de hoje ainda não saudado → saudação enviada 1x + ano marcado +
 * idempotente na 2ª passada; contato cujo aniversário NÃO é hoje → nada.
 */
@Import(AcademiaAniversarioJobIntegrationTest.TestConfig.class)
class AcademiaAniversarioJobIntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY = UUID.fromString("cc000000-0000-0000-0000-0000000000b7");
    private static final UUID INSTANCE = UUID.fromString("c1000000-0000-0000-0000-0000000000b7");

    @Autowired
    private AcademiaAniversarioJob job;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'academia')",
            COMPANY, "Academia Aniversário", "academia-aniv");
        jdbcTemplate.update(
            "insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            INSTANCE, COMPANY, "inst-b7", "tok-b7");
    }

    /**
     * Contato com data de nascimento no dia/mês informado, com conversa (canal resolúvel). O ANO da
     * birth_date é irrelevante para o filtro (extract month/day); usamos um ano antigo qualquer.
     */
    private UUID seedContactWithBirthday(LocalDate birthMonthDay) {
        UUID contact = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into contacts (id, company_id, phone_number, name, birth_date) values (?, ?, ?, 'Aluno', ?)",
            contact, COMPANY,
            "+5511970" + Integer.toString((int) (Math.abs(contact.getLeastSignificantBits()) % 1000000)),
            java.sql.Date.valueOf(birthMonthDay.withYear(1990)));
        UUID conv = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
                + "values (?, ?, ?, ?, 'open', 'ai')",
            conv, COMPANY, contact, INSTANCE);
        return contact;
    }

    private Integer greetedYearOf(UUID contact) {
        return jdbcTemplate.queryForObject(
            "select academia_birthday_greeted_year from contacts where id = ?", Integer.class, contact);
    }

    @Test
    @DisplayName("aniversariante de hoje → saudação enviada 1x + ano marcado + idempotente")
    void birthdayToday_greetedOnceThenIdempotent() {
        LocalDate today = LocalDate.now();
        UUID contact = seedContactWithBirthday(today);

        int greeted = job.runBirthdayGreetings();

        assertThat(greeted).isEqualTo(1);
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("Feliz aniversário");
        assertThat(greetedYearOf(contact)).isEqualTo(today.getYear());

        // 2ª passada no mesmo dia/ano → não reenvia.
        fakeEvolution.reset();
        assertThat(job.runBirthdayGreetings()).isZero();
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("contato sem aniversário hoje → nada")
    void notBirthdayToday_nothing() {
        UUID contact = seedContactWithBirthday(LocalDate.now().plusDays(3));

        int greeted = job.runBirthdayGreetings();

        assertThat(greeted).isZero();
        assertThat(fakeEvolution.sent()).isEmpty();
        assertThat(greetedYearOf(contact)).isNull();
    }

    // ---- fake ---------------------------------------------------------------

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();

        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }

        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-birthday";
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
