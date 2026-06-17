package com.meada.whatsapp.profiles.academia.classes;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code academia_classes} (camada 7.7). Opera via service_role; escopo por company_id.
 * {@link #countActiveMembers} conta as matrículas ATIVAS naquela aula (via junction) — base da
 * lógica de vaga.
 */
@Repository
public class AcademiaClassRepository {

    private static final RowMapper<AcademiaClass> MAPPER = (rs, rn) -> new AcademiaClass(
        (UUID) rs.getObject("id"),
        rs.getString("name"),
        rs.getString("modality"),
        rs.getInt("day_of_week"),
        rs.getObject("start_time", LocalTime.class),
        rs.getInt("duration_minutes"),
        rs.getInt("capacity"),
        rs.getString("instructor"),
        rs.getBoolean("active"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private static final String COLS =
        "id, name, modality, day_of_week, start_time, duration_minutes, capacity, instructor, active, "
            + "created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    public AcademiaClassRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Lista aulas do tenant, opcionalmente só ativas e/ou de um dia da semana. Ordena por dia+hora. */
    public List<AcademiaClass> listByCompany(UUID companyId, boolean onlyActive, Integer dayOfWeek) {
        StringBuilder sql = new StringBuilder("select " + COLS + " from academia_classes where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (onlyActive) {
            sql.append(" and active = true");
        }
        if (dayOfWeek != null) {
            sql.append(" and day_of_week = ?");
            args.add(dayOfWeek);
        }
        sql.append(" order by day_of_week asc, start_time asc");
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public Optional<AcademiaClass> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query("select " + COLS + " from academia_classes where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    /**
     * Conta as matrículas ATIVAS que ocupam vaga nesta aula (via junction). Suspensa MANTÉM a vaga
     * (decisão 6) — por isso o filtro é {@code status <> 'cancelada'}: ativa E suspensa ocupam;
     * só cancelada libera. NOTA: a junction só existe enquanto a matrícula existe (ON DELETE CASCADE),
     * mas cancelar NÃO apaga a matrícula — então filtramos por status.
     */
    public int countActiveMembers(UUID classId) {
        Integer n = jdbcTemplate.queryForObject(
            "select count(*) from academia_membership_classes mc "
                + "join academia_memberships m on m.id = mc.membership_id "
                + "where mc.class_id = ? and m.status <> 'cancelada'",
            Integer.class, classId);
        return n == null ? 0 : n;
    }

    public AcademiaClass insert(UUID companyId, String name, String modality, int dayOfWeek,
                                LocalTime startTime, int durationMinutes, int capacity, String instructor) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into academia_classes (company_id, name, modality, day_of_week, start_time, "
                + "duration_minutes, capacity, instructor) values (?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, name.trim(), modality.trim(), dayOfWeek, java.sql.Time.valueOf(startTime),
            durationMinutes, capacity, instructor);
        return findById(companyId, id).orElseThrow();
    }

    public Optional<AcademiaClass> update(UUID companyId, UUID id, String name, String modality,
                                          Integer dayOfWeek, LocalTime startTime, Integer durationMinutes,
                                          Integer capacity, String instructor, Boolean active) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (name != null && !name.isBlank()) { sets.add("name = ?"); args.add(name.trim()); }
        if (modality != null && !modality.isBlank()) { sets.add("modality = ?"); args.add(modality.trim()); }
        if (dayOfWeek != null) { sets.add("day_of_week = ?"); args.add(dayOfWeek); }
        if (startTime != null) { sets.add("start_time = ?"); args.add(java.sql.Time.valueOf(startTime)); }
        if (durationMinutes != null) { sets.add("duration_minutes = ?"); args.add(durationMinutes); }
        if (capacity != null) { sets.add("capacity = ?"); args.add(capacity); }
        if (instructor != null) { sets.add("instructor = ?"); args.add(instructor); }
        if (active != null) { sets.add("active = ?"); args.add(active); }
        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update("update academia_classes set " + String.join(", ", sets)
                + " where company_id = ? and id = ?", args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    public Optional<AcademiaClass> toggle(UUID companyId, UUID id, boolean active) {
        int n = jdbcTemplate.update("update academia_classes set active = ?, updated_at = now() "
            + "where company_id = ? and id = ?", active, companyId, id);
        return n == 0 ? Optional.empty() : findById(companyId, id);
    }

    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update("delete from academia_classes where company_id = ? and id = ?", companyId, id) > 0;
    }
}
