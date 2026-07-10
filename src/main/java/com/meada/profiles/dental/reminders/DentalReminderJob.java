package com.meada.profiles.dental.reminders;

import com.meada.admin.health.ScheduledJobRunRepository;
import com.meada.profiles.dental.appointments.DentalAppointmentNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Automações da onda Dental 1 num tick diário (backlog #1/#3/#5): lembrete D-1 pedindo SIM (o
 * loop fecha via ConfirmacaoConsultaHandler — desmarcar SEGUE com o consultório, trava original),
 * auto-transição confirmada vencida → realizada (silenciosa) e RECALL de manutenção/limpeza
 * (opt-in OFF): paciente sem consulta realizada há recall_months e sem consulta futura → 1
 * convite por episódio. Textos administrativos, sem conteúdo clínico. Best-effort.
 */
@Component
public class DentalReminderJob {

    private static final Logger log = LoggerFactory.getLogger(DentalReminderJob.class);
    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final JdbcTemplate jdbcTemplate;
    private final DentalAppointmentNotifier notifier;
    private final ScheduledJobRunRepository jobRunRepository;

    public DentalReminderJob(JdbcTemplate jdbcTemplate, DentalAppointmentNotifier notifier,
                             ScheduledJobRunRepository jobRunRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.notifier = notifier;
        this.jobRunRepository = jobRunRepository;
    }

    /** Tick agendado (cron configurável; default diário às 12h10). Delega aos públicos p/ os testes. */
    @Scheduled(cron = "${dental.reminder-cron:0 10 12 * * *}")
    public void scheduledRun() {
        var runId = jobRunRepository.start("DentalReminderJob");
        try {
            runReminders();
            runAutoComplete();
            runRecalls();
            jobRunRepository.finishSuccess(runId);
        } catch (RuntimeException e) {
            jobRunRepository.finishFailed(runId, e.getMessage());
            throw e;
        }
    }

    /** Lembrete D-1 pedindo confirmação (#1). Remarcar REARMA. Público p/ testes. */
    public int runReminders() {
        LocalDate tomorrow = LocalDate.now(TENANT_ZONE).plusDays(1);
        record Due(UUID id, UUID companyId, UUID conversationId, String type, Instant startAt) {}
        List<Due> due = jdbcTemplate.query(
            "select a.id, a.company_id, a.conversation_id, a.type, a.start_at "
                + "from dental_appointments a "
                + "join companies co on co.id = a.company_id and co.profile_id = 'dental' "
                + "left join dental_clinic_config cfg on cfg.company_id = a.company_id "
                + "where coalesce(cfg.reminder_enabled, true) "
                + "and a.status in ('agendada','confirmada') "
                + "and a.conversation_id is not null "
                + "and (a.start_at at time zone 'America/Sao_Paulo')::date = ? "
                + "and (a.reminded_start_at is null or a.reminded_start_at <> a.start_at) "
                + "order by a.company_id",
            (rs, rn) -> new Due((UUID) rs.getObject("id"), (UUID) rs.getObject("company_id"),
                (UUID) rs.getObject("conversation_id"), rs.getString("type"),
                rs.getTimestamp("start_at").toInstant()),
            java.sql.Date.valueOf(tomorrow));
        int touched = 0;
        for (Due d : due) {
            try {
                var local = d.startAt().atZone(TENANT_ZONE);
                notifier.notifyStatus(d.companyId(), d.conversationId(),
                    "Sua consulta (" + d.type() + ") é AMANHÃ às " + HORA.format(local.toLocalTime())
                        + ". Responda SIM para confirmar — se precisar remarcar, o consultório "
                        + "combina com você por aqui. 🦷");
                jdbcTemplate.update(
                    "update dental_appointments set reminded_start_at = ? where id = ?",
                    Timestamp.from(d.startAt()), d.id());
                touched++;
            } catch (Exception e) {
                log.warn("dental-reminder: failed {} ({})", d.id(), e.getMessage());
            }
        }
        return touched;
    }

    /** Confirmada vencida → realizada, silenciosa (#5 — falta segue humana). Público p/ testes. */
    public int runAutoComplete() {
        return jdbcTemplate.update(
            "update dental_appointments a set status = 'realizada', status_updated_at = now() "
                + "from companies co "
                + "left join dental_clinic_config cfg on cfg.company_id = co.id "
                + "where co.id = a.company_id and co.profile_id = 'dental' "
                + "and coalesce(cfg.auto_complete_enabled, true) "
                + "and a.status = 'confirmada' and a.end_at < now()");
    }

    /** Recall de manutenção (#3, opt-in OFF): 1 convite por episódio. Público p/ testes. */
    public int runRecalls() {
        record Due(UUID patientId, UUID companyId, UUID conversationId, String patientName) {}
        List<Due> due = jdbcTemplate.query(
            "select p.id, p.company_id, p.name, "
                + "(select conv.id from conversations conv "
                + "  where conv.company_id = p.company_id and conv.contact_id = p.contact_id "
                + "  order by conv.created_at desc limit 1) as conversation_id "
                + "from dental_patients p "
                + "join companies co on co.id = p.company_id and co.profile_id = 'dental' "
                + "join dental_clinic_config cfg on cfg.company_id = p.company_id and cfg.recall_enabled "
                + "where p.contact_id is not null "
                + "and (select max(a.start_at) from dental_appointments a "
                + "     where a.company_id = p.company_id and a.patient_id = p.id "
                + "     and a.status = 'realizada') "
                + "    < now() - make_interval(months => cfg.recall_months) "
                + "and not exists (select 1 from dental_appointments f "
                + "  where f.company_id = p.company_id and f.patient_id = p.id "
                + "  and f.status in ('agendada','confirmada') and f.start_at > now()) "
                + "and (p.recall_reminded_at is null or p.recall_reminded_at < "
                + "  (select max(a2.start_at) from dental_appointments a2 "
                + "   where a2.company_id = p.company_id and a2.patient_id = p.id "
                + "   and a2.status = 'realizada')) "
                + "order by p.company_id",
            (rs, rn) -> new Due((UUID) rs.getObject("id"), (UUID) rs.getObject("company_id"),
                (UUID) rs.getObject("conversation_id"), rs.getString("name")));
        int touched = 0;
        for (Due d : due) {
            try {
                if (d.conversationId() != null) {
                    notifier.notifyStatus(d.companyId(), d.conversationId(),
                        "Olá! Já faz um tempo desde a última consulta de " + d.patientName()
                            + " — está na hora da revisão/limpeza. Quer que eu já veja um horário? 🦷");
                }
                jdbcTemplate.update(
                    "update dental_patients set recall_reminded_at = now() where id = ?", d.patientId());
                touched++;
            } catch (Exception e) {
                log.warn("dental-recall: failed patient {} ({})", d.patientId(), e.getMessage());
            }
        }
        return touched;
    }
}
