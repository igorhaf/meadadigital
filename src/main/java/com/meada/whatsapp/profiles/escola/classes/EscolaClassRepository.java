package com.meada.whatsapp.profiles.escola.classes;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code escola_classes} (camada 8.19). Opera via service_role; escopo por company_id.
 * {@link #countActiveEnrollments} conta as matrículas não-canceladas (ativa+suspensa) que ocupam
 * vaga numa turma — base da lógica de vaga (suspensa MANTÉM a vaga, só cancelada libera).
 */
@Repository
public class EscolaClassRepository {

    private static final RowMapper<EscolaClass> MAPPER = (rs, rn) -> new EscolaClass(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("company_id"),
        rs.getString("name"),
        rs.getString("grade"),
        rs.getString("shift"),
        rs.getInt("capacity"),
        rs.getInt("monthly_cents"),
        (Integer) rs.getObject("year"),
        rs.getString("description"),
        rs.getBoolean("active"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private static final String COLS =
        "id, company_id, name, grade, shift, capacity, monthly_cents, year, description, active, "
            + "created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    public EscolaClassRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<EscolaClass> listByCompany(UUID companyId, boolean onlyActive, String shift) {
        StringBuilder sql = new StringBuilder("select " + COLS + " from escola_classes where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (onlyActive) {
            sql.append(" and active = true");
        }
        if (shift != null && !shift.isBlank()) {
            sql.append(" and shift = ?");
            args.add(shift);
        }
        sql.append(" order by grade asc, name asc");
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public Optional<EscolaClass> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query("select " + COLS + " from escola_classes where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    /**
     * Conta as matrículas que ocupam vaga nesta turma. Suspensa MANTÉM a vaga (decisão cravada da
     * academia) — por isso o filtro é {@code status <> 'cancelada'}: ativa E suspensa ocupam; só
     * cancelada libera.
     */
    public int countActiveEnrollments(UUID classId) {
        Integer n = jdbcTemplate.queryForObject(
            "select count(*) from escola_enrollments where class_id = ? and status <> 'cancelada'",
            Integer.class, classId);
        return n == null ? 0 : n;
    }

    public EscolaClass insert(UUID companyId, String name, String grade, String shift, int capacity,
                              int monthlyCents, Integer year, String description) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into escola_classes (company_id, name, grade, shift, capacity, monthly_cents, year, description) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, name.trim(), grade.trim(), shift, capacity, monthlyCents, year, description);
        return findById(companyId, id).orElseThrow();
    }

    public Optional<EscolaClass> update(UUID companyId, UUID id, String name, String grade, String shift,
                                        Integer capacity, Integer monthlyCents, Integer year,
                                        String description, Boolean active) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (name != null && !name.isBlank()) { sets.add("name = ?"); args.add(name.trim()); }
        if (grade != null && !grade.isBlank()) { sets.add("grade = ?"); args.add(grade.trim()); }
        if (shift != null && !shift.isBlank()) { sets.add("shift = ?"); args.add(shift); }
        if (capacity != null) { sets.add("capacity = ?"); args.add(capacity); }
        if (monthlyCents != null) { sets.add("monthly_cents = ?"); args.add(monthlyCents); }
        if (year != null) { sets.add("year = ?"); args.add(year); }
        if (description != null) { sets.add("description = ?"); args.add(description); }
        if (active != null) { sets.add("active = ?"); args.add(active); }
        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update("update escola_classes set " + String.join(", ", sets)
                + " where company_id = ? and id = ?", args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    public Optional<EscolaClass> toggle(UUID companyId, UUID id, boolean active) {
        int n = jdbcTemplate.update("update escola_classes set active = ?, updated_at = now() "
            + "where company_id = ? and id = ?", active, companyId, id);
        return n == 0 ? Optional.empty() : findById(companyId, id);
    }

    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update("delete from escola_classes where company_id = ? and id = ?", companyId, id) > 0;
    }
}
