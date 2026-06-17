package com.meada.whatsapp.profiles.pet.animals;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code pet_animals} (camada 7.8). Sub-entidade do tutor (contact). Opera via service_role;
 * escopo por company_id. {@link #contactExists} valida que o tutor é do company (sem estender o
 * ContactRepository do core, que é compartilhado).
 */
@Repository
public class PetAnimalRepository {

    private static final RowMapper<PetAnimal> MAPPER = (rs, rn) -> new PetAnimal(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("contact_id"),
        rs.getString("name"),
        rs.getString("species"),
        rs.getString("breed"),
        rs.getString("sex"),
        (Integer) rs.getObject("birth_year"),
        rs.getString("notes"),
        rs.getBoolean("active"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private static final String COLS =
        "id, contact_id, name, species, breed, sex, birth_year, notes, active, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    public PetAnimalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** True se o contato existe e é do company (valida o tutor antes de criar o animal). */
    public boolean contactExists(UUID companyId, UUID contactId) {
        Integer n = jdbcTemplate.queryForObject(
            "select count(*) from contacts where id = ? and company_id = ?", Integer.class, contactId, companyId);
        return n != null && n > 0;
    }

    /** Nome do contato (tutor) — para snapshot no agendamento. */
    public Optional<String> contactName(UUID companyId, UUID contactId) {
        return jdbcTemplate.query("select name from contacts where id = ? and company_id = ?",
                (rs, rn) -> rs.getString("name"), contactId, companyId)
            .stream().findFirst();
    }

    /** Telefone do contato (tutor) — para snapshot no agendamento. */
    public Optional<String> contactPhone(UUID companyId, UUID contactId) {
        return jdbcTemplate.query("select phone_number from contacts where id = ? and company_id = ?",
                (rs, rn) -> rs.getString("phone_number"), contactId, companyId)
            .stream().findFirst();
    }

    public List<PetAnimal> listByCompany(UUID companyId, UUID contactId, String species, Boolean active, String search) {
        StringBuilder sql = new StringBuilder("select " + COLS + " from pet_animals where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (contactId != null) { sql.append(" and contact_id = ?"); args.add(contactId); }
        if (species != null && !species.isBlank()) { sql.append(" and species = ?"); args.add(species); }
        if (active != null) { sql.append(" and active = ?"); args.add(active); }
        if (search != null && !search.isBlank()) { sql.append(" and name ilike ?"); args.add("%" + search.trim() + "%"); }
        sql.append(" order by name asc");
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public List<PetAnimal> listByContact(UUID companyId, UUID contactId, boolean onlyActive) {
        return listByCompany(companyId, contactId, null, onlyActive ? Boolean.TRUE : null, null);
    }

    public Optional<PetAnimal> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query("select " + COLS + " from pet_animals where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    public PetAnimal insert(UUID companyId, UUID contactId, String name, String species, String breed,
                            String sex, Integer birthYear, String notes) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into pet_animals (company_id, contact_id, name, species, breed, sex, birth_year, notes) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, contactId, name.trim(), species, breed,
            sex == null || sex.isBlank() ? "desconhecido" : sex, birthYear, notes);
        return findById(companyId, id).orElseThrow();
    }

    public Optional<PetAnimal> update(UUID companyId, UUID id, String name, String species, String breed,
                                      String sex, Integer birthYear, String notes, Boolean active) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (name != null && !name.isBlank()) { sets.add("name = ?"); args.add(name.trim()); }
        if (species != null && !species.isBlank()) { sets.add("species = ?"); args.add(species); }
        if (breed != null) { sets.add("breed = ?"); args.add(breed); }
        if (sex != null && !sex.isBlank()) { sets.add("sex = ?"); args.add(sex); }
        if (birthYear != null) { sets.add("birth_year = ?"); args.add(birthYear); }
        if (notes != null) { sets.add("notes = ?"); args.add(notes); }
        if (active != null) { sets.add("active = ?"); args.add(active); }
        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update("update pet_animals set " + String.join(", ", sets)
                + " where company_id = ? and id = ?", args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    public Optional<PetAnimal> archive(UUID companyId, UUID id) {
        int n = jdbcTemplate.update("update pet_animals set active = false, updated_at = now() "
            + "where company_id = ? and id = ?", companyId, id);
        return n == 0 ? Optional.empty() : findById(companyId, id);
    }

    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update("delete from pet_animals where company_id = ? and id = ?", companyId, id) > 0;
    }
}
