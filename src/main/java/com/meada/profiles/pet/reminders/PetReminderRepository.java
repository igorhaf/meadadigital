package com.meada.profiles.pet.reminders;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Varredura e marcação do lembrete de véspera do pet shop (onda 1, backlog #1). service_role,
 * cruza TODOS os tenants pet numa query só (toggle na config; ausência de linha = ligado).
 * Datas comparadas no fuso America/Sao_Paulo.
 */
@Repository
public class PetReminderRepository {

    private static final RowMapper<DuePetAppointment> MAPPER = (rs, rn) -> new DuePetAppointment(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("company_id"),
        (UUID) rs.getObject("conversation_id"),
        rs.getString("tutor_name"),
        rs.getString("animal_name"),
        rs.getString("service_name"),
        rs.getString("professional_name"),
        rs.getTimestamp("start_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public PetReminderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Agendamentos de {@code targetDate} ainda não lembrados pro start_at atual (remarcar rearma). */
    public List<DuePetAppointment> findDueReminders(LocalDate targetDate) {
        return jdbcTemplate.query(
            "select a.id, a.company_id, a.conversation_id, a.tutor_name, a.animal_name, "
                + "a.service_name, a.professional_name, a.start_at "
                + "from pet_appointments a "
                + "join companies c on c.id = a.company_id "
                + "left join pet_config cfg on cfg.company_id = a.company_id "
                + "where c.profile_id = 'pet' "
                + "and coalesce(cfg.reminder_enabled, true) "
                + "and a.status in ('agendado','confirmado') "
                + "and (a.start_at at time zone 'America/Sao_Paulo')::date = ? "
                + "and (a.reminded_start_at is null or a.reminded_start_at <> a.start_at) "
                + "order by a.company_id, a.start_at",
            MAPPER, Date.valueOf(targetDate));
    }

    /** Marca o start_at como lembrado (idempotência por agendamento+horário; remarcar rearma). */
    public void markReminded(UUID appointmentId, Instant startAt) {
        jdbcTemplate.update(
            "update pet_appointments set reminded_start_at = ? where id = ?",
            java.sql.Timestamp.from(startAt), appointmentId);
    }
}
