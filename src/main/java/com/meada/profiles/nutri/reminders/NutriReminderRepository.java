package com.meada.profiles.nutri.reminders;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Varreduras do NutriReminderJob (onda 1, backlog #1/#2/#5). service_role, cruza TODOS os tenants
 * nutri numa query só (toggles na config; ausência de linha = reminder/auto ON, régua OFF).
 * Datas no fuso America/Sao_Paulo.
 */
@Repository
public class NutriReminderRepository {

    private static final RowMapper<DueNutriWork> MAPPER = (rs, rn) -> new DueNutriWork(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("company_id"),
        (UUID) rs.getObject("conversation_id"),
        rs.getString("person_name"),
        rs.getString("professional_name"),
        rs.getTimestamp("start_at") == null ? null : rs.getTimestamp("start_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public NutriReminderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** #1 — consultas de {@code targetDate} ainda não lembradas pro start_at atual. */
    public List<DueNutriWork> findDueReminders(LocalDate targetDate) {
        return jdbcTemplate.query(
            "select a.id, a.company_id, a.conversation_id, a.patient_name as person_name, "
                + "a.professional_name, a.start_at "
                + "from nutri_appointments a "
                + "join companies c on c.id = a.company_id "
                + "left join nutri_config cfg on cfg.company_id = a.company_id "
                + "where c.profile_id = 'nutri' "
                + "and coalesce(cfg.reminder_enabled, true) "
                + "and a.status in ('agendado','confirmado') "
                + "and (a.start_at at time zone 'America/Sao_Paulo')::date = ? "
                + "and (a.reminded_start_at is null or a.reminded_start_at <> a.start_at) "
                + "order by a.company_id, a.start_at",
            MAPPER, Date.valueOf(targetDate));
    }

    public void markReminded(UUID appointmentId, Instant startAt) {
        jdbcTemplate.update(
            "update nutri_appointments set reminded_start_at = ? where id = ?",
            Timestamp.from(startAt), appointmentId);
    }

    /** #5 — confirmadas com end_at vencido (folga do chamador), auto-transição ON. */
    public List<DueNutriWork> findConfirmedPast(Instant cutoff) {
        return jdbcTemplate.query(
            "select a.id, a.company_id, a.conversation_id, a.patient_name as person_name, "
                + "a.professional_name, a.start_at "
                + "from nutri_appointments a "
                + "join companies c on c.id = a.company_id "
                + "left join nutri_config cfg on cfg.company_id = a.company_id "
                + "where c.profile_id = 'nutri' "
                + "and coalesce(cfg.auto_complete_enabled, true) "
                + "and a.status = 'confirmado' and a.end_at < ? "
                + "order by a.company_id",
            MAPPER, Timestamp.from(cutoff));
    }

    /**
     * #2 — pacientes ATIVOS de tenants com a régua LIGADA (opt-in), SEM consulta futura ativa,
     * com a última REALIZADA além da janela e sem toque neste ciclo
     * (reengagement_sent_at null ou anterior à última realizada). Canal = conversa mais
     * recente do CONTATO do paciente.
     */
    public List<DueNutriWork> findDueReengagements() {
        return jdbcTemplate.query(
            "select p.id, p.company_id, "
                + "(select conv.id from conversations conv "
                + "  where conv.company_id = p.company_id and conv.contact_id = p.contact_id "
                + "  order by conv.created_at desc limit 1) as conversation_id, "
                + "p.name as person_name, null as professional_name, "
                + "(select max(a.start_at) from nutri_appointments a "
                + "  where a.company_id = p.company_id and a.patient_id = p.id "
                + "  and a.status = 'realizado') as start_at "
                + "from nutri_patients p "
                + "join companies c on c.id = p.company_id "
                + "join nutri_config cfg on cfg.company_id = p.company_id "
                + "  and cfg.reengagement_enabled "
                + "where c.profile_id = 'nutri' and p.active = true "
                + "and not exists (select 1 from nutri_appointments f "
                + "  where f.company_id = p.company_id and f.patient_id = p.id "
                + "  and f.status in ('agendado','confirmado') and f.start_at >= now()) "
                + "and (select max(a.start_at) from nutri_appointments a "
                + "  where a.company_id = p.company_id and a.patient_id = p.id "
                + "  and a.status = 'realizado') "
                + "  < now() - make_interval(days => cfg.reengagement_days) "
                + "and (p.reengagement_sent_at is null "
                + "  or p.reengagement_sent_at < (select max(a.start_at) from nutri_appointments a "
                + "    where a.company_id = p.company_id and a.patient_id = p.id "
                + "    and a.status = 'realizado')) "
                + "order by p.company_id",
            MAPPER);
    }

    public void markReengaged(UUID patientId) {
        jdbcTemplate.update(
            "update nutri_patients set reengagement_sent_at = now() where id = ?", patientId);
    }
}
