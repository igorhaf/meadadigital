package com.meada.profiles.academia.waitlist;

import com.meada.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test da lista de espera da academia (migration 74) contra PostgreSQL real. Valida a
 * POSIÇÃO DERIVADA: enfileira 2 (posições 1 e 2); chamar o 1º recomputa a fila (o 2º vira posição 1),
 * sem nenhum UPDATE de reordenação — a posição é sempre calculada por query.
 */
class AcademiaWaitlistServiceIntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY = UUID.fromString("cc000000-0000-0000-0000-00000000a071");

    @Autowired
    private AcademiaWaitlistService service;

    private static final UUID USER = UUID.fromString("aa000000-0000-0000-0000-00000000a074");

    private UUID classId;
    private UUID contactAna;
    private UUID contactBruno;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'academia')",
            COMPANY, "Academia W", "academia-w");
        // USER precisa existir em auth.users: o AuditLogger insere audit_log.user_id (FK) DENTRO da
        // transação do service — user inexistente aborta a tx e o commit vira rollback silencioso.
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@aca-fila.dev', 'admin')",
            USER, COMPANY);
        classId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into academia_classes (id, company_id, name, modality, day_of_week, start_time, "
                + "duration_minutes, capacity) values (?, ?, 'Spinning', 'Bike', 1, '07:00', 60, 10)",
            classId, COMPANY);
        // Contatos REAIS: academia_class_waitlist.contact_id tem FK para contacts (on delete set null).
        contactAna = UUID.randomUUID();
        contactBruno = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into contacts (id, company_id, phone_number, name) values (?, ?, '+5511990000001', 'Ana')",
            contactAna, COMPANY);
        jdbcTemplate.update(
            "insert into contacts (id, company_id, phone_number, name) values (?, ?, '+5511990000002', 'Bruno')",
            contactBruno, COMPANY);
    }

    @Test
    @DisplayName("enfileira 2 → posições 1 e 2; chamar o 1º recomputa (o 2º vira posição 1)")
    void enqueueTwo_positionsDerived_andRecomputeOnCall() {
        AcademiaWaitlistEntry first = service.enqueue(COMPANY, USER, classId, contactAna, "Ana", null);
        AcademiaWaitlistEntry second = service.enqueue(COMPANY, USER, classId, contactBruno, "Bruno", null);

        assertThat(first.position()).isEqualTo(1);
        assertThat(second.position()).isEqualTo(2);

        List<AcademiaWaitlistEntry> queue = service.list(COMPANY, classId, true);
        assertThat(queue).extracting(AcademiaWaitlistEntry::studentName).containsExactly("Ana", "Bruno");
        assertThat(queue).extracting(AcademiaWaitlistEntry::position).containsExactly(1, 2);

        // Chama o 1º (sai de 'aguardando') → a fila recomputa sem UPDATE de reordenação.
        AcademiaWaitlistEntry called = service.updateStatus(COMPANY, USER, first.id(), "chamado");
        assertThat(called.status()).isEqualTo("chamado");

        List<AcademiaWaitlistEntry> stillWaiting = service.list(COMPANY, classId, true);
        assertThat(stillWaiting).extracting(AcademiaWaitlistEntry::studentName).containsExactly("Bruno");
        assertThat(stillWaiting.get(0).position()).isEqualTo(1);
    }

    @Test
    @DisplayName("mesmo contato 2× 'aguardando' na mesma aula → already_waiting")
    void duplicateWaiting_rejected() {
        service.enqueue(COMPANY, USER, classId, contactAna, "Ana", null);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> service.enqueue(COMPANY, USER, classId, contactAna, "Ana", null))
            .isInstanceOf(AcademiaWaitlistService.AlreadyWaitingException.class);
    }
}
