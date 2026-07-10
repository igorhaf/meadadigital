package com.meada.profiles.dermatologia.reminders;

import com.meada.admin.health.ScheduledJobRunRepository;
import com.meada.profiles.dermatologia.appointments.DermatologiaAppointmentNotifier;
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
 * Automações da onda Dermatologia 1 num tick diário (backlog #1/#2/#5): lembrete D-1 pedindo
 * confirmação — com a NOTA DE PREPARO enviada junto quando o tipo tem prep_instructions
 * (verbatim, a mesma garantia do EntregaPreparoHandler; preparo mal feito queima dois slots) —,
 * auto-transição confirmada vencida → realizada (silenciosa; FALTA continua ação humana) e o
 * RECALL DE RETORNO (opt-in OFF): paciente sem consulta realizada há recall_months e sem
 * agendamento futuro recebe 1 convite por episódio. Textos administrativos — a trava clínica
 * fica intacta (sem conteúdo clínico gerado). Best-effort.
 */
@Component
public class DermatologiaReminderJob {

    private static final Logger log = LoggerFactory.getLogger(DermatologiaReminderJob.class);
    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final JdbcTemplate jdbcTemplate;
    private final DermatologiaAppointmentNotifier notifier;
    private final ScheduledJobRunRepository jobRunRepository;

    public DermatologiaReminderJob(JdbcTemplate jdbcTemplate, DermatologiaAppointmentNotifier notifier,
                                   ScheduledJobRunRepository jobRunRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.notifier = notifier;
        this.jobRunRepository = jobRunRepository;
    }

    /** Tick agendado (cron configurável; default diário às 11h10). Delega aos públicos p/ os testes. */
    @Scheduled(cron = "${dermatologia.reminder-cron:0 10 11 * * *}")
    public void scheduledRun() {
        var runId = jobRunRepository.start("DermatologiaReminderJob");
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

    /** Lembrete D-1 + preparo quando houver (#1). Remarcar REARMA. Público p/ testes. */
    public int runReminders() {
        LocalDate tomorrow = LocalDate.now(TENANT_ZONE).plusDays(1);
        record Due(UUID id, UUID companyId, UUID conversationId, String procedureName,
                   String professionalName, Instant startAt, String prep) {}
        List<Due> due = jdbcTemplate.query(
            "select a.id, a.company_id, a.conversation_id, a.procedure_type_name, a.professional_name, "
                + "a.start_at, pt.prep_instructions "
                + "from dermatologia_appointments a "
                + "join companies co on co.id = a.company_id and co.profile_id = 'dermatologia' "
                + "left join dermatologia_config cfg on cfg.company_id = a.company_id "
                + "left join dermatologia_procedure_types pt on pt.id = a.procedure_type_id "
                + "where coalesce(cfg.reminder_enabled, true) "
                + "and a.status in ('agendada','confirmada') "
                + "and (a.start_at at time zone 'America/Sao_Paulo')::date = ? "
                + "and (a.reminded_start_at is null or a.reminded_start_at <> a.start_at) "
                + "order by a.company_id",
            (rs, rn) -> new Due((UUID) rs.getObject("id"), (UUID) rs.getObject("company_id"),
                (UUID) rs.getObject("conversation_id"), rs.getString("procedure_type_name"),
                rs.getString("professional_name"), rs.getTimestamp("start_at").toInstant(),
                rs.getString("prep_instructions")),
            java.sql.Date.valueOf(tomorrow));
        int touched = 0;
        for (Due d : due) {
            try {
                var local = d.startAt().atZone(TENANT_ZONE);
                notifier.notifyStatus(d.companyId(), d.conversationId(),
                    "Sua consulta de " + d.procedureName() + " é AMANHÃ às "
                        + HORA.format(local.toLocalTime()) + " com " + d.professionalName()
                        + ". Confirma sua presença? Responda SIM ou, se precisar desmarcar, avise "
                        + "por aqui. 🩺");
                if (d.prep() != null && !d.prep().isBlank()) {
                    // Nota de preparo VERBATIM (gravada pelo médico — nunca gerada/reescrita).
                    notifier.sendText(d.companyId(), d.conversationId(), d.prep());
                }
                jdbcTemplate.update(
                    "update dermatologia_appointments set reminded_start_at = ? where id = ?",
                    Timestamp.from(d.startAt()), d.id());
                touched++;
            } catch (Exception e) {
                log.warn("dermatologia-reminder: failed {} ({})", d.id(), e.getMessage());
            }
        }
        return touched;
    }

    /** Confirmada vencida → realizada, silenciosa (#5 — FALTA segue humana). Público p/ testes. */
    public int runAutoComplete() {
        return jdbcTemplate.update(
            "update dermatologia_appointments a set status = 'realizada', status_updated_at = now() "
                + "from companies co "
                + "left join dermatologia_config cfg on cfg.company_id = co.id "
                + "where co.id = a.company_id and co.profile_id = 'dermatologia' "
                + "and coalesce(cfg.auto_complete_enabled, true) "
                + "and a.status = 'confirmada' and a.end_at < now()");
    }

    /**
     * Recall de retorno (#2, opt-in OFF): paciente ativo cuja última consulta REALIZADA é mais
     * antiga que recall_months, sem consulta futura ativa — 1 convite por episódio (marker
     * re-armado por consulta realizada mais nova). Público p/ testes.
     */
    public int runRecalls() {
        record Due(UUID patientId, UUID companyId, UUID conversationId, String patientName) {}
        List<Due> due = jdbcTemplate.query(
            "select p.id, p.company_id, p.name, "
                + "(select conv.id from conversations conv "
                + "  where conv.company_id = p.company_id and conv.contact_id = p.contact_id "
                + "  order by conv.created_at desc limit 1) as conversation_id "
                + "from dermatologia_patients p "
                + "join companies co on co.id = p.company_id and co.profile_id = 'dermatologia' "
                + "join dermatologia_config cfg on cfg.company_id = p.company_id and cfg.recall_enabled "
                + "where p.active = true "
                + "and (select max(a.start_at) from dermatologia_appointments a "
                + "     where a.company_id = p.company_id and a.patient_id = p.id "
                + "     and a.status = 'realizada') "
                + "    < now() - make_interval(months => cfg.recall_months) "
                + "and not exists (select 1 from dermatologia_appointments f "
                + "  where f.company_id = p.company_id and f.patient_id = p.id "
                + "  and f.status in ('agendada','confirmada') and f.start_at > now()) "
                + "and (p.recall_reminded_at is null or p.recall_reminded_at < "
                + "  (select max(a2.start_at) from dermatologia_appointments a2 "
                + "   where a2.company_id = p.company_id and a2.patient_id = p.id "
                + "   and a2.status = 'realizada')) "
                + "order by p.company_id",
            (rs, rn) -> new Due((UUID) rs.getObject("id"), (UUID) rs.getObject("company_id"),
                (UUID) rs.getObject("conversation_id"), rs.getString("name")));
        int touched = 0;
        for (Due d : due) {
            try {
                if (d.conversationId() == null) {
                    log.info("dermatologia-recall: paciente {} sem conversa — marcado sem envio",
                        d.patientId());
                } else {
                    notifier.notifyStatus(d.companyId(), d.conversationId(),
                        "Olá! Já faz um tempo desde a última consulta de " + d.patientName()
                            + " aqui na clínica. Que tal agendar uma reavaliação? É só responder "
                            + "por aqui que eu ajudo com o horário. 🩺");
                }
                jdbcTemplate.update(
                    "update dermatologia_patients set recall_reminded_at = now() where id = ?",
                    d.patientId());
                touched++;
            } catch (Exception e) {
                log.warn("dermatologia-recall: failed patient {} ({})", d.patientId(), e.getMessage());
            }
        }
        return touched;
    }
}
