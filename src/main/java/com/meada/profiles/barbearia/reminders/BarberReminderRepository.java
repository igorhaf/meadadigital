package com.meada.profiles.barbearia.reminders;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Varreduras dos jobs da barbearia (onda 1, backlog #1/#7). service_role, cruza TODOS os tenants
 * barbearia numa query só, respeitando os toggles da config (ausência de linha = default ligado).
 */
@Repository
public class BarberReminderRepository {

    private final JdbcTemplate jdbcTemplate;

    public BarberReminderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * #1 — agendamentos 'agendado' começando em [from, to) ainda não lembrados, de tenants barbearia
     * com o lembrete LIGADO. O job pergunta "confirma? SIM/CANCELAR" e marca reminded_24h.
     */
    public List<DueBarberAppointment> findDueReminders(Instant from, Instant to) {
        return jdbcTemplate.query(
            "select a.id, a.company_id, a.conversation_id, a.guest_name, a.service_name, "
                + "a.barber_name, a.start_at "
                + "from barber_appointments a "
                + "join companies c on c.id = a.company_id "
                + "left join barber_config cfg on cfg.company_id = a.company_id "
                + "where c.profile_id = 'barbearia' "
                + "and coalesce(cfg.reminder_enabled, true) "
                + "and a.status = 'agendado' "
                + "and a.reminded_24h = false "
                + "and a.start_at >= ? and a.start_at < ? "
                + "order by a.company_id, a.start_at",
            (rs, rn) -> new DueBarberAppointment(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("company_id"),
                (UUID) rs.getObject("conversation_id"),
                rs.getString("guest_name"),
                rs.getString("service_name"),
                rs.getString("barber_name"),
                rs.getTimestamp("start_at").toInstant()),
            Timestamp.from(from), Timestamp.from(to));
    }

    /** Marca o lembrete como enviado (idempotência — inclusive sem canal resolúvel). */
    public void markReminded(UUID appointmentId) {
        jdbcTemplate.update(
            "update barber_appointments set reminded_24h = true where id = ?", appointmentId);
    }

    /**
     * #7 — agendamentos 'confirmado' cujo end_at já passou (com folga), de tenants barbearia com a
     * auto-transição LIGADA. O job move cada um pra 'realizado' via o service (validação + silêncio).
     */
    public List<DueBarberAppointment> findConfirmedPast(Instant cutoff) {
        return jdbcTemplate.query(
            "select a.id, a.company_id, a.conversation_id, a.guest_name, a.service_name, "
                + "a.barber_name, a.start_at "
                + "from barber_appointments a "
                + "join companies c on c.id = a.company_id "
                + "left join barber_config cfg on cfg.company_id = a.company_id "
                + "where c.profile_id = 'barbearia' "
                + "and coalesce(cfg.auto_complete_enabled, true) "
                + "and a.status = 'confirmado' "
                + "and a.end_at < ? "
                + "order by a.company_id, a.end_at",
            (rs, rn) -> new DueBarberAppointment(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("company_id"),
                (UUID) rs.getObject("conversation_id"),
                rs.getString("guest_name"),
                rs.getString("service_name"),
                rs.getString("barber_name"),
                rs.getTimestamp("start_at").toInstant()),
            Timestamp.from(cutoff));
    }

    /**
     * #7 — expira tickets 'aguardando' enfileirados ANTES do início do dia corrente (walk-in não
     * atravessa a noite), de tenants barbearia com a auto-transição LIGADA. Transição
     * aguardando→expirado é válida na máquina da fila e é SILENCIOSA (só 'chamado' notifica).
     *
     * @return nº de tickets expirados
     */
    public int expireStaleTickets(Instant startOfToday) {
        return jdbcTemplate.update(
            "update barber_queue_tickets t set status = 'expirado', status_updated_at = now() "
                + "from companies c left join barber_config cfg on cfg.company_id = c.id "
                + "where t.company_id = c.id and c.profile_id = 'barbearia' "
                + "and coalesce(cfg.auto_complete_enabled, true) "
                + "and t.status = 'aguardando' and t.enqueued_at < ?",
            Timestamp.from(startOfToday));
    }
}
