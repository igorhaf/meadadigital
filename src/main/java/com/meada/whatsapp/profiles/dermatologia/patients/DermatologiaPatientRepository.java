package com.meada.whatsapp.profiles.dermatologia.patients;

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
 * Acesso a {@code dermatologia_patients} (camada 8.11). Sub-entidade do contact. service_role;
 * escopo por company_id. {@link #contactExists} valida que o cliente é do company.
 */
@Repository
public class DermatologiaPatientRepository {

    private static final RowMapper<DermatologiaPatient> MAPPER = (rs, rn) -> {
        Date bd = rs.getDate("birth_date");
        return new DermatologiaPatient(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("contact_id"),
            rs.getString("name"),
            bd == null ? null : bd.toLocalDate(),
            rs.getString("notes"),
            rs.getBoolean("active"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());
    };

    private static final String COLS =
        "id, contact_id, name, birth_date, notes, active, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    public DermatologiaPatientRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** True se o contato existe e é do company (valida o cliente antes de criar o paciente). */
    public boolean contactExists(UUID companyId, UUID contactId) {
        Integer n = jdbcTemplate.queryForObject(
            "select count(*) from contacts where id = ? and company_id = ?", Integer.class, contactId, companyId);
        return n != null && n > 0;
    }

    /** Telefone do contato (cliente) — para snapshot na consulta. */
    public Optional<String> contactPhone(UUID companyId, UUID contactId) {
        return jdbcTemplate.query("select phone_number from contacts where id = ? and company_id = ?",
                (rs, rn) -> rs.getString("phone_number"), contactId, companyId)
            .stream().findFirst();
    }

    public List<DermatologiaPatient> listByCompany(UUID companyId, UUID contactId, Boolean active, String search) {
        StringBuilder sql = new StringBuilder("select " + COLS + " from dermatologia_patients where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (contactId != null) { sql.append(" and contact_id = ?"); args.add(contactId); }
        if (active != null) { sql.append(" and active = ?"); args.add(active); }
        if (search != null && !search.isBlank()) { sql.append(" and name ilike ?"); args.add("%" + search.trim() + "%"); }
        sql.append(" order by name asc");
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public List<DermatologiaPatient> listByContact(UUID companyId, UUID contactId, boolean onlyActive) {
        return listByCompany(companyId, contactId, onlyActive ? Boolean.TRUE : null, null);
    }

    public Optional<DermatologiaPatient> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query("select " + COLS + " from dermatologia_patients where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    public DermatologiaPatient insert(UUID companyId, UUID contactId, String name, LocalDate birthDate, String notes) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into dermatologia_patients (company_id, contact_id, name, birth_date, notes) "
                + "values (?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, contactId, name.trim(),
            birthDate == null ? null : Date.valueOf(birthDate), notes);
        return findById(companyId, id).orElseThrow();
    }

    public Optional<DermatologiaPatient> update(UUID companyId, UUID id, String name, LocalDate birthDate,
                                                boolean birthProvided, String notes, Boolean active) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (name != null && !name.isBlank()) { sets.add("name = ?"); args.add(name.trim()); }
        if (birthProvided) { sets.add("birth_date = ?"); args.add(birthDate == null ? null : Date.valueOf(birthDate)); }
        if (notes != null) { sets.add("notes = ?"); args.add(notes); }
        if (active != null) { sets.add("active = ?"); args.add(active); }
        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update("update dermatologia_patients set " + String.join(", ", sets)
                + " where company_id = ? and id = ?", args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    public Optional<DermatologiaPatient> archive(UUID companyId, UUID id) {
        int n = jdbcTemplate.update("update dermatologia_patients set active = false, updated_at = now() "
            + "where company_id = ? and id = ?", companyId, id);
        return n == 0 ? Optional.empty() : findById(companyId, id);
    }

    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update("delete from dermatologia_patients where company_id = ? and id = ?", companyId, id) > 0;
    }
}
