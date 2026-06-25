package com.meada.whatsapp.profiles.escola.payments;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.profiles.escola.payments.EscolaPaymentService.DuplicatePaymentException;
import com.meada.whatsapp.profiles.escola.payments.EscolaPaymentService.EnrollmentCancelledException;
import com.meada.whatsapp.profiles.escola.payments.EscolaPaymentService.PaymentSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o EscolaPaymentService (camada 8.19): record + summary, mesmo mês 2× → DuplicatePaymentException
 * (409), pagamento em matrícula cancelada → EnrollmentCancelledException (400).
 */
class EscolaPaymentServiceTest extends AbstractIntegrationTest {

    @Autowired
    private EscolaPaymentService service;

    private static final UUID COMPANY = UUID.fromString("ce000000-0000-0000-0000-000000000005");
    private static final UUID USER = UUID.fromString("de000000-0000-0000-0000-000000000005");
    private UUID enrollment;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'escola')",
            COMPANY, "Escola P", "escola-p");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@escola-p.dev', 'admin')",
            USER, COMPANY);
        UUID classId = UUID.randomUUID();
        jdbcTemplate.update("insert into escola_classes (id, company_id, name, grade, shift, capacity, monthly_cents) "
            + "values (?, ?, 'Jardim I', 'Infantil', 'manha', 20, 50000)", classId, COMPANY);
        UUID contactId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990500", "Responsável");
        UUID studentId = UUID.randomUUID();
        jdbcTemplate.update("insert into escola_students (id, company_id, contact_id, name) values (?, ?, ?, 'Lucas')",
            studentId, COMPANY, contactId);
        enrollment = UUID.randomUUID();
        jdbcTemplate.update("insert into escola_enrollments (id, company_id, class_id, student_id, student_name, "
            + "class_name, class_grade, class_shift, class_monthly_cents, status, start_date) "
            + "values (?, ?, ?, ?, 'Lucas', 'Jardim I', 'Infantil', 'manha', 50000, 'ativa', current_date)",
            enrollment, COMPANY, classId, studentId);
    }

    @Test
    @DisplayName("record + summary: registra mensalidade e resume último mês/em aberto")
    void recordAndSummary() {
        LocalDate ref = LocalDate.now().withDayOfMonth(1);
        EscolaPayment p = service.record(COMPANY, USER, enrollment, ref, 50000, "Pix", null);
        assertThat(p.amountCents()).isEqualTo(50000);
        assertThat(p.referenceMonth()).isEqualTo(ref);

        PaymentSummary s = service.summary(COMPANY, enrollment, LocalDate.now());
        assertThat(s.totalPayments()).isEqualTo(1);
        assertThat(s.lastPaidMonth()).isEqualTo(ref);
        assertThat(s.monthsOpen()).isEqualTo(0);   // 1 mês decorrido, 1 pago.
    }

    @Test
    @DisplayName("record duplicado mesmo reference_month → DuplicatePaymentException (409)")
    void duplicate() {
        LocalDate ref = LocalDate.now().withDayOfMonth(1);
        service.record(COMPANY, USER, enrollment, ref, 50000, "Pix", null);
        assertThatThrownBy(() -> service.record(COMPANY, USER, enrollment, ref, 50000, "dinheiro", null))
            .isInstanceOf(DuplicatePaymentException.class);
    }

    @Test
    @DisplayName("record em matrícula cancelada → EnrollmentCancelledException (400)")
    void cancelledEnrollment() {
        jdbcTemplate.update("update escola_enrollments set status = 'cancelada', end_date = current_date where id = ?",
            enrollment);
        LocalDate ref = LocalDate.now().withDayOfMonth(1);
        assertThatThrownBy(() -> service.record(COMPANY, USER, enrollment, ref, 50000, "Pix", null))
            .isInstanceOf(EnrollmentCancelledException.class);
    }
}
