package com.meada.whatsapp.profiles.academia.payments;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.profiles.academia.payments.AcademiaPaymentService.DuplicatePaymentException;
import com.meada.whatsapp.profiles.academia.payments.AcademiaPaymentService.MembershipNotActiveException;
import com.meada.whatsapp.profiles.academia.payments.AcademiaPaymentService.PaymentSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o AcademiaPaymentService (camada 7.7): record + summary, duplicate → 409, pagamento em
 * matrícula cancelada → 400.
 */
class AcademiaPaymentServiceTest extends AbstractIntegrationTest {

    @Autowired
    private AcademiaPaymentService service;

    private static final UUID COMPANY = UUID.fromString("cc000000-0000-0000-0000-000000000005");
    private static final UUID USER = UUID.fromString("dc000000-0000-0000-0000-000000000005");
    private UUID membership;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'academia')",
            COMPANY, "Academia P", "academia-p");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@aca-p.dev', 'admin')",
            USER, COMPANY);
        UUID plan = UUID.randomUUID();
        jdbcTemplate.update("insert into academia_plans (id, company_id, name, monthly_cents) values (?, ?, 'Mensal', 20000)", plan, COMPANY);
        membership = UUID.randomUUID();
        jdbcTemplate.update("insert into academia_memberships (id, company_id, plan_id, student_name, plan_name, plan_monthly_cents, start_date) "
            + "values (?, ?, ?, 'Aluno', 'Mensal', 20000, current_date)", membership, COMPANY, plan);
    }

    @Test
    @DisplayName("record + summary: registra pagamento e resume último mês/em aberto")
    void recordAndSummary() {
        LocalDate ref = LocalDate.now().withDayOfMonth(1);
        AcademiaPayment p = service.record(COMPANY, USER, membership, ref, 20000, "Pix", null);
        assertThat(p.amountCents()).isEqualTo(20000);
        assertThat(p.referenceMonth()).isEqualTo(ref);

        PaymentSummary s = service.summary(COMPANY, membership, LocalDate.now());
        assertThat(s.totalPayments()).isEqualTo(1);
        assertThat(s.lastPaidMonth()).isEqualTo(ref);
        assertThat(s.monthsOpen()).isEqualTo(0);   // 1 mês decorrido, 1 pago.
    }

    @Test
    @DisplayName("record duplicado mesmo reference_month → DuplicatePaymentException (409)")
    void duplicate() {
        LocalDate ref = LocalDate.now().withDayOfMonth(1);
        service.record(COMPANY, USER, membership, ref, 20000, "Pix", null);
        assertThatThrownBy(() -> service.record(COMPANY, USER, membership, ref, 20000, "dinheiro", null))
            .isInstanceOf(DuplicatePaymentException.class);
    }

    @Test
    @DisplayName("record em matrícula cancelada → MembershipNotActiveException (400)")
    void cancelledMembership() {
        jdbcTemplate.update("update academia_memberships set status = 'cancelada', end_date = current_date where id = ?", membership);
        LocalDate ref = LocalDate.now().withDayOfMonth(1);
        assertThatThrownBy(() -> service.record(COMPANY, USER, membership, ref, 20000, "Pix", null))
            .isInstanceOf(MembershipNotActiveException.class);
    }
}
