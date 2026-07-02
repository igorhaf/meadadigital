package com.meada.profiles.academia.checkins;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code academia_checkins} (camada 7.7, feature #4). Opera via service_role; escopo por
 * company_id. O insert pode violar o UNIQUE (membership_id, class_id, checkin_date) — o service
 * mapeia para 409 duplicate_checkin. Métodos de existência validam que matrícula/aula são do tenant.
 */
@Repository
public class AcademiaCheckinRepository {

    private static final RowMapper<AcademiaCheckin> MAPPER = (rs, rn) -> new AcademiaCheckin(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("membership_id"),
        (UUID) rs.getObject("class_id"),
        rs.getObject("checkin_date", LocalDate.class),
        rs.getTimestamp("checkin_at").toInstant(),
        rs.getString("source"),
        rs.getString("notes"));

    private static final String COLS =
        "id, membership_id, class_id, checkin_date, checkin_at, source, notes";

    private final JdbcTemplate jdbcTemplate;

    public AcademiaCheckinRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** True se a matrícula existe e é do tenant. */
    public boolean membershipExists(UUID companyId, UUID membershipId) {
        Integer n = jdbcTemplate.queryForObject(
            "select count(*) from academia_memberships where company_id = ? and id = ?",
            Integer.class, companyId, membershipId);
        return n != null && n > 0;
    }

    /** contact_id da matrícula (vazio se a matrícula não tem contato vinculado — cadastro manual). */
    public Optional<UUID> findMembershipContactId(UUID companyId, UUID membershipId) {
        return jdbcTemplate.query(
                "select contact_id from academia_memberships where company_id = ? and id = ?",
                (rs, rn) -> (UUID) rs.getObject("contact_id"), companyId, membershipId)
            .stream().filter(Objects::nonNull).findFirst();
    }

    /** True se a aula existe e é do tenant. */
    public boolean classExists(UUID companyId, UUID classId) {
        Integer n = jdbcTemplate.queryForObject(
            "select count(*) from academia_classes where company_id = ? and id = ?",
            Integer.class, companyId, classId);
        return n != null && n > 0;
    }

    public AcademiaCheckin insert(UUID companyId, UUID membershipId, UUID classId,
                                  LocalDate checkinDate, String source, String notes) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into academia_checkins (company_id, membership_id, class_id, checkin_date, source, notes) "
                + "values (?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, membershipId, classId, Date.valueOf(checkinDate), source, notes);
        return jdbcTemplate.query("select " + COLS + " from academia_checkins where id = ?", MAPPER, id)
            .stream().findFirst().orElseThrow();
    }

    /** Lista por company, filtrando opcionalmente por aula e janela [from, to] (checkin_date). */
    public List<AcademiaCheckin> list(UUID companyId, UUID classId, LocalDate from, LocalDate to) {
        StringBuilder sql = new StringBuilder(
            "select " + COLS + " from academia_checkins where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (classId != null) { sql.append(" and class_id = ?"); args.add(classId); }
        if (from != null) { sql.append(" and checkin_date >= ?"); args.add(Date.valueOf(from)); }
        if (to != null) { sql.append(" and checkin_date <= ?"); args.add(Date.valueOf(to)); }
        sql.append(" order by checkin_date desc, checkin_at desc");
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }
}
