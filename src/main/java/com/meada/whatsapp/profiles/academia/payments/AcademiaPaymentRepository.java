package com.meada.whatsapp.profiles.academia.payments;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code academia_payments} (camada 7.7). Opera via service_role; escopo por company_id.
 * O insert pode violar o UNIQUE (membership, reference_month) — o service mapeia para 409.
 */
@Repository
public class AcademiaPaymentRepository {

    private static final RowMapper<AcademiaPayment> MAPPER = (rs, rn) -> new AcademiaPayment(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("membership_id"),
        rs.getObject("reference_month", LocalDate.class),
        rs.getTimestamp("paid_at").toInstant(),
        rs.getInt("amount_cents"),
        rs.getString("method"),
        rs.getString("notes"));

    private static final String COLS =
        "id, membership_id, reference_month, paid_at, amount_cents, method, notes";

    private final JdbcTemplate jdbcTemplate;

    public AcademiaPaymentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AcademiaPayment> listByMembership(UUID companyId, UUID membershipId) {
        return jdbcTemplate.query(
            "select " + COLS + " from academia_payments where company_id = ? and membership_id = ? "
                + "order by reference_month desc",
            MAPPER, companyId, membershipId);
    }

    public AcademiaPayment insert(UUID companyId, UUID membershipId, LocalDate referenceMonth,
                                  int amountCents, String method, String notes) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into academia_payments (company_id, membership_id, reference_month, amount_cents, method, notes) "
                + "values (?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, membershipId, Date.valueOf(referenceMonth), amountCents, method, notes);
        return jdbcTemplate.query("select " + COLS + " from academia_payments where id = ?", MAPPER, id)
            .stream().findFirst().orElseThrow();
    }

    public boolean delete(UUID companyId, UUID paymentId) {
        return jdbcTemplate.update("delete from academia_payments where company_id = ? and id = ?",
            companyId, paymentId) > 0;
    }

    /** Último mês pago (mais recente reference_month) de uma matrícula, se houver. */
    public Optional<LocalDate> lastPaidMonth(UUID companyId, UUID membershipId) {
        return jdbcTemplate.query(
                "select max(reference_month) as m from academia_payments where company_id = ? and membership_id = ?",
                (rs, rn) -> rs.getObject("m", LocalDate.class), companyId, membershipId)
            .stream().findFirst().map(Optional::ofNullable).orElse(Optional.empty());
    }

    /** Quantos pagamentos a matrícula tem. */
    public int countByMembership(UUID companyId, UUID membershipId) {
        Integer n = jdbcTemplate.queryForObject(
            "select count(*) from academia_payments where company_id = ? and membership_id = ?",
            Integer.class, companyId, membershipId);
        return n == null ? 0 : n;
    }
}
