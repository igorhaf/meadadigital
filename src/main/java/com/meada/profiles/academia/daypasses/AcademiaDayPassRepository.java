package com.meada.profiles.academia.daypasses;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code academia_day_passes} (camada 7.7). Opera via service_role; escopo por company_id.
 */
@Repository
public class AcademiaDayPassRepository {

    private static final RowMapper<AcademiaDayPass> MAPPER = (rs, rn) -> new AcademiaDayPass(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("contact_id"),
        rs.getString("guest_name"),
        rs.getString("guest_phone"),
        (UUID) rs.getObject("class_id"),
        rs.getDate("pass_date").toLocalDate(),
        rs.getInt("price_cents"),
        rs.getBoolean("paid"),
        rs.getTimestamp("created_at").toInstant());

    private static final String COLS =
        "id, contact_id, guest_name, guest_phone, class_id, pass_date, price_cents, paid, created_at";

    private final JdbcTemplate jdbcTemplate;

    public AcademiaDayPassRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AcademiaDayPass> listByCompany(UUID companyId) {
        return jdbcTemplate.query(
            "select " + COLS + " from academia_day_passes where company_id = ? "
                + "order by pass_date desc, created_at desc",
            MAPPER, companyId);
    }

    public Optional<AcademiaDayPass> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query(
            "select " + COLS + " from academia_day_passes where company_id = ? and id = ?",
            MAPPER, companyId, id).stream().findFirst();
    }

    public AcademiaDayPass insert(UUID companyId, UUID contactId, String guestName, String guestPhone,
                                  UUID classId, LocalDate passDate, int priceCents) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into academia_day_passes "
                + "(company_id, contact_id, guest_name, guest_phone, class_id, pass_date, price_cents) "
                + "values (?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, contactId, guestName.trim(), guestPhone, classId,
            Date.valueOf(passDate), priceCents);
        return findById(companyId, id).orElseThrow();
    }

    /** Marca o passe como pago. Devolve o passe atualizado, ou empty se não existe no tenant. */
    public Optional<AcademiaDayPass> markPaid(UUID companyId, UUID id) {
        int n = jdbcTemplate.update(
            "update academia_day_passes set paid = true where company_id = ? and id = ?",
            companyId, id);
        return n == 0 ? Optional.empty() : findById(companyId, id);
    }
}
