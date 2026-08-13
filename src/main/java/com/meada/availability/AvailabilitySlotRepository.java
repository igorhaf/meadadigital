package com.meada.availability;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Time;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Acesso a {@code availability_slots} (camada 5.17 #61). service_role; isolamento por
 * companyId no WHERE (defesa em profundidade — o RLS também isola).
 */
@Repository
public class AvailabilitySlotRepository {

    private static final RowMapper<AvailabilitySlot> ROW_MAPPER = (rs, rowNum) ->
        new AvailabilitySlot(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("company_id"),
            rs.getInt("weekday"),
            rs.getTime("starts_at").toLocalTime(),
            rs.getTime("ends_at").toLocalTime(),
            rs.getInt("slot_minutes"),
            rs.getBoolean("active"));

    private static final String COLUMNS =
        "id, company_id, weekday, starts_at, ends_at, slot_minutes, active";

    private final JdbcTemplate jdbcTemplate;

    public AvailabilitySlotRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Janelas ATIVAS da empresa, ordenadas por weekday/starts_at. */
    public List<AvailabilitySlot> findActiveByCompany(UUID companyId) {
        Objects.requireNonNull(companyId, "companyId");
        return jdbcTemplate.query(
            "select " + COLUMNS + " from availability_slots where company_id = ? and active "
                + "order by weekday, starts_at",
            ROW_MAPPER, companyId);
    }

    /** Todas as janelas da empresa (inclui inativas) — para a tela de gestão. */
    public List<AvailabilitySlot> findAllByCompany(UUID companyId) {
        Objects.requireNonNull(companyId, "companyId");
        return jdbcTemplate.query(
            "select " + COLUMNS + " from availability_slots where company_id = ? "
                + "order by weekday, starts_at",
            ROW_MAPPER, companyId);
    }

    public AvailabilitySlot insert(UUID companyId, int weekday, LocalTime startsAt,
                                   LocalTime endsAt, int slotMinutes) {
        Objects.requireNonNull(companyId, "companyId");
        return jdbcTemplate.queryForObject(
            "insert into availability_slots (company_id, weekday, starts_at, ends_at, slot_minutes) "
                + "values (?, ?, ?, ?, ?) returning " + COLUMNS,
            ROW_MAPPER, companyId, weekday, Time.valueOf(startsAt), Time.valueOf(endsAt), slotMinutes);
    }

    /** Atualiza uma janela da empresa. Retorna true se atualizou (existe + é da empresa). */
    public boolean update(UUID id, UUID companyId, int weekday, LocalTime startsAt,
                          LocalTime endsAt, int slotMinutes, boolean active) {
        int n = jdbcTemplate.update(
            "update availability_slots set weekday=?, starts_at=?, ends_at=?, slot_minutes=?, "
                + "active=?, updated_at=now() where id=? and company_id=?",
            weekday, Time.valueOf(startsAt), Time.valueOf(endsAt), slotMinutes, active, id, companyId);
        return n > 0;
    }

    public boolean delete(UUID id, UUID companyId) {
        int n = jdbcTemplate.update(
            "delete from availability_slots where id=? and company_id=?", id, companyId);
        return n > 0;
    }
}
