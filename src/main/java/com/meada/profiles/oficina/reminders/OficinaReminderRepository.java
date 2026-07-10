package com.meada.profiles.oficina.reminders;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Varredura do lembrete de retorno/revisão da oficina (onda 1, backlog #2). service_role, cruza
 * TODOS os tenants oficina numa query só (toggle na config; ausência de linha = ligado). 1x por OS.
 */
@Repository
public class OficinaReminderRepository {

    private static final RowMapper<DueReturn> MAPPER = (rs, rn) -> new DueReturn(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("company_id"),
        (UUID) rs.getObject("conversation_id"),
        rs.getString("customer_name"),
        rs.getString("vehicle_plate"),
        rs.getString("vehicle_model"),
        rs.getDate("next_return_date").toLocalDate());

    private final JdbcTemplate jdbcTemplate;

    public OficinaReminderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** OS entregues com retorno vencido e ainda não lembradas (1x por OS). */
    public List<DueReturn> findDueReturns(LocalDate today) {
        return jdbcTemplate.query(
            "select o.id, o.company_id, o.conversation_id, o.customer_name, o.vehicle_plate, "
                + "o.vehicle_model, o.next_return_date "
                + "from service_orders o "
                + "join companies c on c.id = o.company_id "
                + "left join os_config cfg on cfg.company_id = o.company_id "
                + "where c.profile_id = 'oficina' "
                + "and coalesce(cfg.return_reminder_enabled, true) "
                + "and o.status = 'entregue' "
                + "and o.next_return_date is not null and o.next_return_date <= ? "
                + "and o.return_reminded_at is null "
                + "order by o.company_id",
            MAPPER, Date.valueOf(today));
    }

    public void markReminded(UUID orderId) {
        jdbcTemplate.update(
            "update service_orders set return_reminded_at = now() where id = ?", orderId);
    }
}
