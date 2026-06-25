package com.meada.whatsapp.profiles.escola.payments;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code escola_payments} (camada 8.19). Opera via service_role; escopo por company_id.
 * O insert pode violar o UNIQUE (enrollment, reference_month) — o service mapeia para 409.
 */
@Repository
public class EscolaPaymentRepository {

    private static final RowMapper<EscolaPayment> MAPPER = (rs, rn) -> new EscolaPayment(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("enrollment_id"),
        rs.getObject("reference_month", LocalDate.class),
        rs.getTimestamp("paid_at").toInstant(),
        rs.getInt("amount_cents"),
        rs.getString("method"),
        rs.getString("notes"));

    private static final String COLS =
        "id, enrollment_id, reference_month, paid_at, amount_cents, method, notes";

    private final JdbcTemplate jdbcTemplate;

    public EscolaPaymentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<EscolaPayment> listByEnrollment(UUID companyId, UUID enrollmentId) {
        return jdbcTemplate.query(
            "select " + COLS + " from escola_payments where company_id = ? and enrollment_id = ? "
                + "order by reference_month desc",
            MAPPER, companyId, enrollmentId);
    }

    public EscolaPayment insert(UUID companyId, UUID enrollmentId, LocalDate referenceMonth,
                                int amountCents, String method, String notes) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into escola_payments (company_id, enrollment_id, reference_month, amount_cents, method, notes) "
                + "values (?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, enrollmentId, Date.valueOf(referenceMonth), amountCents, method, notes);
        return jdbcTemplate.query("select " + COLS + " from escola_payments where id = ?", MAPPER, id)
            .stream().findFirst().orElseThrow();
    }

    public boolean delete(UUID companyId, UUID paymentId) {
        return jdbcTemplate.update("delete from escola_payments where company_id = ? and id = ?",
            companyId, paymentId) > 0;
    }

    /** Último mês pago (mais recente reference_month) de uma matrícula, se houver. */
    public Optional<LocalDate> lastPaidMonth(UUID companyId, UUID enrollmentId) {
        return jdbcTemplate.query(
                "select max(reference_month) as m from escola_payments where company_id = ? and enrollment_id = ?",
                (rs, rn) -> rs.getObject("m", LocalDate.class), companyId, enrollmentId)
            .stream().findFirst().map(Optional::ofNullable).orElse(Optional.empty());
    }

    /** Quantos pagamentos a matrícula tem. */
    public int countByEnrollment(UUID companyId, UUID enrollmentId) {
        Integer n = jdbcTemplate.queryForObject(
            "select count(*) from escola_payments where company_id = ? and enrollment_id = ?",
            Integer.class, companyId, enrollmentId);
        return n == null ? 0 : n;
    }
}
