package com.meada.profiles.fotografia.reminders;

import com.meada.admin.health.ScheduledJobRunRepository;
import com.meada.profiles.fotografia.appointments.FotografiaAppointmentNotifier;
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
 * Automações da onda Fotografia 1 num tick diário (backlog #2/#3): lembrete de sessão em D-2 e
 * D-1 pedindo confirmação (a resposta fecha o loop via ConfirmacaoFotografiaHandler), auto-
 * transição confirmada vencida → realizada (silenciosa) e ENTREGA NO PRAZO — no
 * delivery_due_date, sessão realizada COM delivery_link é entregue automaticamente: o link sai
 * VERBATIM (nunca passa pela IA, mesma garantia do EntregaMaterialHandler), a sessão vira
 * "entregue" e um convite pós-entrega oferece extras SEM preço (o momento mais quente de
 * compra). Remarcar REARMA os lembretes (markers por janela = start_at lembrado).
 */
@Component
public class FotografiaReminderJob {

    private static final Logger log = LoggerFactory.getLogger(FotografiaReminderJob.class);
    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM");

    private final JdbcTemplate jdbcTemplate;
    private final FotografiaAppointmentNotifier notifier;
    private final ScheduledJobRunRepository jobRunRepository;

    public FotografiaReminderJob(JdbcTemplate jdbcTemplate, FotografiaAppointmentNotifier notifier,
                                 ScheduledJobRunRepository jobRunRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.notifier = notifier;
        this.jobRunRepository = jobRunRepository;
    }

    /** Tick agendado (cron configurável; default diário às 9h50). Delega aos públicos p/ os testes. */
    @Scheduled(cron = "${fotografia.reminder-cron:0 50 9 * * *}")
    public void scheduledRun() {
        var runId = jobRunRepository.start("FotografiaReminderJob");
        try {
            runReminders();
            runAutoComplete();
            runAutoDeliver();
            jobRunRepository.finishSuccess(runId);
        } catch (RuntimeException e) {
            jobRunRepository.finishFailed(runId, e.getMessage());
            throw e;
        }
    }

    private record DueSession(UUID id, UUID companyId, UUID conversationId, String packageName,
                              String professionalName, Instant startAt) {}

    /** Lembretes D-2 e D-1 (#2). Remarcar REARMA (marker = start_at lembrado). Público p/ testes. */
    public int runReminders() {
        LocalDate today = LocalDate.now(TENANT_ZONE);
        int touched = 0;
        touched += sweepWindow(today.plusDays(2), "reminded2_start_at", 2);
        touched += sweepWindow(today.plusDays(1), "reminded1_start_at", 1);
        return touched;
    }

    private int sweepWindow(LocalDate target, String marker, int daysBefore) {
        List<DueSession> due = jdbcTemplate.query(
            "select s.id, s.company_id, s.conversation_id, s.package_name, s.professional_name, s.start_at "
                + "from fotografia_session_appointments s "
                + "join companies co on co.id = s.company_id and co.profile_id = 'fotografia' "
                + "left join fotografia_config cfg on cfg.company_id = s.company_id "
                + "where coalesce(cfg.reminder_enabled, true) "
                + "and s.status in ('agendada','confirmada') "
                + "and (s.start_at at time zone 'America/Sao_Paulo')::date = ? "
                + "and (s." + marker + " is null or s." + marker + " <> s.start_at) "
                + "order by s.company_id",
            (rs, rn) -> new DueSession((UUID) rs.getObject("id"), (UUID) rs.getObject("company_id"),
                (UUID) rs.getObject("conversation_id"), rs.getString("package_name"),
                rs.getString("professional_name"), rs.getTimestamp("start_at").toInstant()),
            java.sql.Date.valueOf(target));
        int touched = 0;
        for (DueSession s : due) {
            try {
                var local = s.startAt().atZone(TENANT_ZONE);
                String quando = daysBefore == 1 ? "AMANHÃ" : DIA.format(local.toLocalDate());
                notifier.notifyStatus(s.companyId(), s.conversationId(),
                    "Lembrete da sua sessão " + s.packageName() + " com " + s.professionalName()
                        + " " + quando + " às " + HORA.format(local.toLocalTime())
                        + ". Confirma sua presença? Responda por aqui. 📸");
                jdbcTemplate.update("update fotografia_session_appointments set " + marker
                    + " = ?, status_updated_at = status_updated_at where id = ?",
                    Timestamp.from(s.startAt()), s.id());
                touched++;
            } catch (Exception e) {
                log.warn("fotografia-reminder: failed {} ({})", s.id(), e.getMessage());
            }
        }
        return touched;
    }

    /** Auto-transição confirmada vencida → realizada, silenciosa (#2). Público p/ testes. */
    public int runAutoComplete() {
        return jdbcTemplate.update(
            "update fotografia_session_appointments s set status = 'realizada', status_updated_at = now() "
                + "from companies co "
                + "left join fotografia_config cfg on cfg.company_id = co.id "
                + "where co.id = s.company_id and co.profile_id = 'fotografia' "
                + "and coalesce(cfg.auto_complete_enabled, true) "
                + "and s.status = 'confirmada' and s.end_at < now()");
    }

    /**
     * Entrega no prazo (#3): no delivery_due_date (ou depois), sessão realizada COM link é
     * entregue automaticamente — link VERBATIM + transição → entregue + convite pós-entrega
     * (extras SEM preço, se o toggle estiver ligado). Público p/ testes.
     */
    public int runAutoDeliver() {
        record Deliverable(UUID id, UUID companyId, UUID conversationId, String link, boolean upsell) {}
        List<Deliverable> due = jdbcTemplate.query(
            "select s.id, s.company_id, s.conversation_id, s.delivery_link, "
                + "coalesce(cfg.post_delivery_upsell_enabled, true) as upsell "
                + "from fotografia_session_appointments s "
                + "join companies co on co.id = s.company_id and co.profile_id = 'fotografia' "
                + "left join fotografia_config cfg on cfg.company_id = s.company_id "
                + "where coalesce(cfg.auto_deliver_enabled, true) "
                + "and s.status = 'realizada' "
                + "and s.delivery_link is not null and s.delivery_link <> '' "
                + "and s.delivery_due_date <= (now() at time zone 'America/Sao_Paulo')::date "
                + "order by s.company_id",
            (rs, rn) -> new Deliverable((UUID) rs.getObject("id"), (UUID) rs.getObject("company_id"),
                (UUID) rs.getObject("conversation_id"), rs.getString("delivery_link"),
                rs.getBoolean("upsell")));
        int touched = 0;
        for (Deliverable d : due) {
            try {
                // O link sai VERBATIM (nunca passa pela IA); só depois a transição é persistida —
                // falha de envio deixa a sessão realizada pra retry no próximo tick.
                boolean sent = notifier.sendText(d.companyId(), d.conversationId(), d.link());
                if (!sent) {
                    log.warn("fotografia-deliver: envio falhou p/ sessão {} — fica pro próximo tick", d.id());
                    continue;
                }
                jdbcTemplate.update(
                    "update fotografia_session_appointments set status = 'entregue', status_updated_at = now() "
                        + "where id = ? and status = 'realizada'", d.id());
                if (d.upsell()) {
                    notifier.notifyStatus(d.companyId(), d.conversationId(),
                        "Esperamos que ame o resultado! ✨ Se quiser fotos extras, álbum impresso "
                            + "ou os arquivos em alta, é só chamar por aqui que a equipe te passa "
                            + "as opções.");
                }
                touched++;
            } catch (Exception e) {
                log.warn("fotografia-deliver: failed {} ({})", d.id(), e.getMessage());
            }
        }
        return touched;
    }
}
