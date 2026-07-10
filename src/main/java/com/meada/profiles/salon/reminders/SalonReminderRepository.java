package com.meada.profiles.salon.reminders;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Varredura e marcação do lembrete de véspera + auto-transição do salon (onda 1, backlog #1/#7).
 * service_role, cruza TODOS os tenants salon numa query só. Datas comparadas no fuso
 * America/Sao_Paulo (o "amanhã" do salão, não UTC).
 */
@Repository
public class SalonReminderRepository {

    private final JdbcTemplate jdbcTemplate;

    public SalonReminderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Agendamentos de {@code targetDate} (a véspera é responsabilidade do chamador) ainda não
     * lembrados para o start_at atual, dos tenants salon com o lembrete LIGADO (ausência de linha
     * de config = ligado).
     */
    public List<DueSalonAppointment> findDueReminders(LocalDate targetDate) {
        return jdbcTemplate.query(
            "select a.id as appointment_id, a.company_id, a.conversation_id, a.guest_name, "
                + "a.professional_name, a.service_name, a.start_at "
                + "from salon_appointments a "
                + "join companies c on c.id = a.company_id "
                + "left join salon_config cfg on cfg.company_id = a.company_id "
                + "where c.profile_id = 'salon' "
                + "and coalesce(cfg.reminder_enabled, true) "
                + "and a.status in ('agendado','confirmado') "
                + "and (a.start_at at time zone 'America/Sao_Paulo')::date = ? "
                + "and (a.reminded_start_at is null or a.reminded_start_at <> a.start_at) "
                + "order by a.company_id, a.start_at",
            (rs, rn) -> new DueSalonAppointment(
                (UUID) rs.getObject("appointment_id"),
                (UUID) rs.getObject("company_id"),
                (UUID) rs.getObject("conversation_id"),
                rs.getString("guest_name"),
                rs.getString("professional_name"),
                rs.getString("service_name"),
                rs.getTimestamp("start_at").toInstant()),
            Date.valueOf(targetDate));
    }

    /** Marca o start_at como lembrado (idempotência por agendamento+horário; remarcar rearma). */
    public void markReminded(UUID appointmentId, Instant startAt) {
        jdbcTemplate.update(
            "update salon_appointments set reminded_start_at = ? where id = ?",
            java.sql.Timestamp.from(startAt), appointmentId);
    }

    /**
     * Auto-transição opt-in (#7): confirmado com end_at no passado → realizado (transição VÁLIDA
     * da máquina; realizado é silencioso). UMA sweep SQL — retorna quantos viraram.
     */
    public int autoCompletePastConfirmed() {
        return jdbcTemplate.update(
            "update salon_appointments a set status = 'realizado', status_updated_at = now() "
                + "from companies c, salon_config cfg "
                + "where c.id = a.company_id and c.profile_id = 'salon' "
                + "and cfg.company_id = a.company_id and cfg.auto_complete_enabled "
                + "and a.status = 'confirmado' and a.end_at < now()");
    }
}
