package com.meada.profiles.escola.reminders;

import com.meada.admin.health.ScheduledJobRunRepository;
import com.meada.profiles.escola.visits.EscolaVisitNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Automações da onda Escola 1 num tick diário (backlog #2/#4/#10): lembrete de visita em D-1 e
 * no DIA (a visita é o topo do funil de matrícula — no-show alto sem lembrete; remarcar REARMA),
 * auto-transição de visita passada → realizada (silenciosa; quem faltou a secretaria marca
 * cancelada) e a RÉGUA DE MENSALIDADE EM ABERTO (opt-in OFF — cobrança em massa é decisão
 * consciente): matrícula ativa sem pagamento do mês corrente após o dia de vencimento recebe 1
 * lembrete gentil por mês, com o valor JÁ cadastrado da turma (sem multa/juros — a IA nunca
 * inventa). Best-effort.
 */
@Component
public class EscolaReminderJob {

    private static final Logger log = LoggerFactory.getLogger(EscolaReminderJob.class);
    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");

    private final JdbcTemplate jdbcTemplate;
    private final EscolaVisitNotifier notifier;
    private final ScheduledJobRunRepository jobRunRepository;

    public EscolaReminderJob(JdbcTemplate jdbcTemplate, EscolaVisitNotifier notifier,
                             ScheduledJobRunRepository jobRunRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.notifier = notifier;
        this.jobRunRepository = jobRunRepository;
    }

    /** Tick agendado (cron configurável; default diário às 8h10). Delega aos públicos p/ os testes. */
    @Scheduled(cron = "${escola.reminder-cron:0 10 8 * * *}")
    public void scheduledRun() {
        var runId = jobRunRepository.start("EscolaReminderJob");
        try {
            runVisitReminders();
            runVisitAutoComplete();
            runPaymentReminders();
            jobRunRepository.finishSuccess(runId);
        } catch (RuntimeException e) {
            jobRunRepository.finishFailed(runId, e.getMessage());
            throw e;
        }
    }

    /** Lembretes de visita D-1 e no DIA (#2). Remarcar REARMA. Público p/ testes. */
    public int runVisitReminders() {
        LocalDate today = LocalDate.now(TENANT_ZONE);
        int touched = 0;
        touched += sweepVisits(today.plusDays(1), "reminded1_visit_date", true);
        touched += sweepVisits(today, "reminded0_visit_date", false);
        return touched;
    }

    private int sweepVisits(LocalDate target, String marker, boolean dayBefore) {
        record Due(UUID id, UUID companyId, UUID conversationId, String period, LocalDate visitDate) {}
        List<Due> due = jdbcTemplate.query(
            "select v.id, v.company_id, v.conversation_id, v.period, v.visit_date "
                + "from escola_visits v "
                + "join companies co on co.id = v.company_id and co.profile_id = 'escola' "
                + "left join escola_config cfg on cfg.company_id = v.company_id "
                + "where coalesce(cfg.visit_reminder_enabled, true) "
                + "and v.status = 'agendada' "
                + "and v.visit_date = ? "
                + "and (v." + marker + " is null or v." + marker + " <> v.visit_date) "
                + "order by v.company_id",
            (rs, rn) -> new Due((UUID) rs.getObject("id"), (UUID) rs.getObject("company_id"),
                (UUID) rs.getObject("conversation_id"), rs.getString("period"),
                rs.getDate("visit_date").toLocalDate()),
            Date.valueOf(target));
        int touched = 0;
        for (Due d : due) {
            try {
                String periodo = "manha".equals(d.period()) ? "de manhã" : "à tarde";
                String text = dayBefore
                    ? "Lembrete: a visita de vocês à escola é AMANHÃ " + periodo
                        + ". Estamos ansiosos pra receber a família! Se precisar remarcar, "
                        + "é só me avisar por aqui. 🏫"
                    : "É HOJE " + periodo + " que recebemos a visita de vocês na escola! "
                        + "Qualquer imprevisto, me avisa por aqui. 🏫";
                notifier.notifyStatus(d.companyId(), d.conversationId(), text);
                jdbcTemplate.update("update escola_visits set " + marker + " = ? where id = ?",
                    Date.valueOf(d.visitDate()), d.id());
                touched++;
            } catch (Exception e) {
                log.warn("escola-visit-reminder: failed {} ({})", d.id(), e.getMessage());
            }
        }
        return touched;
    }

    /** Visita agendada com data passada → realizada, silenciosa (#10). Público p/ testes. */
    public int runVisitAutoComplete() {
        return jdbcTemplate.update(
            "update escola_visits v set status = 'realizada', status_updated_at = now() "
                + "from companies co "
                + "left join escola_config cfg on cfg.company_id = co.id "
                + "where co.id = v.company_id and co.profile_id = 'escola' "
                + "and coalesce(cfg.visit_auto_complete_enabled, true) "
                + "and v.status = 'agendada' "
                + "and v.visit_date < (now() at time zone 'America/Sao_Paulo')::date");
    }

    /**
     * Régua de mensalidade em aberto (#4, opt-in OFF): matrícula ATIVA sem pagamento do mês
     * corrente após o dia de vencimento → 1 lembrete por mês (payment_reminded_month). Valor da
     * turma (snapshot da matrícula), sem multa/juros. Público p/ testes.
     */
    public int runPaymentReminders() {
        LocalDate today = LocalDate.now(TENANT_ZONE);
        LocalDate refMonth = today.withDayOfMonth(1);
        record Due(UUID id, UUID companyId, UUID conversationId, String studentName,
                   String className, int monthlyCents) {}
        List<Due> due = jdbcTemplate.query(
            "select e.id, e.company_id, e.conversation_id, e.student_name, e.class_name, "
                + "e.class_monthly_cents "
                + "from escola_enrollments e "
                + "join companies co on co.id = e.company_id and co.profile_id = 'escola' "
                + "join escola_config cfg on cfg.company_id = e.company_id "
                + "  and cfg.payment_reminder_enabled "
                + "where e.status = 'ativa' "
                + "and extract(day from (now() at time zone 'America/Sao_Paulo')) >= cfg.payment_due_day "
                + "and e.start_date < ? "
                + "and (e.payment_reminded_month is null or e.payment_reminded_month <> ?) "
                + "and not exists (select 1 from escola_payments p "
                + "  where p.enrollment_id = e.id and p.reference_month = ?) "
                + "order by e.company_id",
            (rs, rn) -> new Due((UUID) rs.getObject("id"), (UUID) rs.getObject("company_id"),
                (UUID) rs.getObject("conversation_id"), rs.getString("student_name"),
                rs.getString("class_name"), rs.getInt("class_monthly_cents")),
            Date.valueOf(refMonth.plusMonths(1)), Date.valueOf(refMonth), Date.valueOf(refMonth));
        int touched = 0;
        for (Due d : due) {
            try {
                notifier.notifyStatus(d.companyId(), d.conversationId(),
                    "Oi! Passando pra lembrar que a mensalidade de " + d.studentName()
                        + " (" + d.className() + ") deste mês está em aberto — R$ "
                        + String.format("%d,%02d", d.monthlyCents() / 100, d.monthlyCents() % 100)
                        + ". Qualquer dúvida sobre o pagamento, é só falar com a secretaria por aqui. 🙂");
                jdbcTemplate.update(
                    "update escola_enrollments set payment_reminded_month = ? where id = ?",
                    Date.valueOf(refMonth), d.id());
                touched++;
            } catch (Exception e) {
                log.warn("escola-payment-reminder: failed {} ({})", d.id(), e.getMessage());
            }
        }
        return touched;
    }
}
