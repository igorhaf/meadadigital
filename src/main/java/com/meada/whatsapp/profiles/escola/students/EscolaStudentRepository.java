package com.meada.whatsapp.profiles.escola.students;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code escola_students} (camada 8.19). Sub-entidade do responsável (contact). Opera via
 * service_role; escopo por company_id. {@link #contactExists} valida que o responsável é do company
 * (sem estender o ContactRepository do core, que é compartilhado).
 */
@Repository
public class EscolaStudentRepository {

    private static final RowMapper<EscolaStudent> MAPPER = (rs, rn) -> new EscolaStudent(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("company_id"),
        (UUID) rs.getObject("contact_id"),
        rs.getString("name"),
        rs.getObject("birth_date", LocalDate.class),
        rs.getString("intended_grade"),
        rs.getString("notes"),
        rs.getBoolean("active"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private static final String COLS =
        "id, company_id, contact_id, name, birth_date, intended_grade, notes, active, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    public EscolaStudentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** True se o contato existe e é do company (valida o responsável antes de criar o aluno). */
    public boolean contactExists(UUID companyId, UUID contactId) {
        Integer n = jdbcTemplate.queryForObject(
            "select count(*) from contacts where id = ? and company_id = ?", Integer.class, contactId, companyId);
        return n != null && n > 0;
    }

    /** Nome do contato (responsável) — para snapshot na matrícula. */
    public Optional<String> contactName(UUID companyId, UUID contactId) {
        if (contactId == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query("select name from contacts where id = ? and company_id = ?",
                (rs, rn) -> rs.getString("name"), contactId, companyId)
            .stream().findFirst();
    }

    public List<EscolaStudent> listByCompany(UUID companyId, UUID contactId, Boolean active, String search) {
        StringBuilder sql = new StringBuilder("select " + COLS + " from escola_students where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (contactId != null) { sql.append(" and contact_id = ?"); args.add(contactId); }
        if (active != null) { sql.append(" and active = ?"); args.add(active); }
        if (search != null && !search.isBlank()) { sql.append(" and name ilike ?"); args.add("%" + search.trim() + "%"); }
        sql.append(" order by name asc");
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public List<EscolaStudent> listByContact(UUID companyId, UUID contactId, boolean onlyActive) {
        return listByCompany(companyId, contactId, onlyActive ? Boolean.TRUE : null, null);
    }

    public Optional<EscolaStudent> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query("select " + COLS + " from escola_students where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    public EscolaStudent insert(UUID companyId, UUID contactId, String name, LocalDate birthDate,
                                String intendedGrade, String notes) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into escola_students (company_id, contact_id, name, birth_date, intended_grade, notes) "
                + "values (?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, contactId, name.trim(),
            birthDate == null ? null : Date.valueOf(birthDate), intendedGrade, notes);
        return findById(companyId, id).orElseThrow();
    }

    public Optional<EscolaStudent> update(UUID companyId, UUID id, String name, LocalDate birthDate,
                                          String intendedGrade, String notes, Boolean active) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (name != null && !name.isBlank()) { sets.add("name = ?"); args.add(name.trim()); }
        if (birthDate != null) { sets.add("birth_date = ?"); args.add(Date.valueOf(birthDate)); }
        if (intendedGrade != null) { sets.add("intended_grade = ?"); args.add(intendedGrade); }
        if (notes != null) { sets.add("notes = ?"); args.add(notes); }
        if (active != null) { sets.add("active = ?"); args.add(active); }
        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update("update escola_students set " + String.join(", ", sets)
                + " where company_id = ? and id = ?", args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    public Optional<EscolaStudent> archive(UUID companyId, UUID id) {
        int n = jdbcTemplate.update("update escola_students set active = false, updated_at = now() "
            + "where company_id = ? and id = ?", companyId, id);
        return n == 0 ? Optional.empty() : findById(companyId, id);
    }

    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update("delete from escola_students where company_id = ? and id = ?", companyId, id) > 0;
    }
}
