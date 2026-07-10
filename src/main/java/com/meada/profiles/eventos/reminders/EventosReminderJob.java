package com.meada.profiles.eventos.reminders;

import com.meada.admin.health.ScheduledJobRunRepository;
import com.meada.profiles.eventos.proposals.EventProposalNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Automações da onda Eventos 1 num tick diário: AUTO-REALIZADA (#6 — proposta fechada com
 * event_date passada vira realizada; o pós-venda do updateStatus é disparado pelo próprio funil
 * quando a equipe marca; aqui a transição é direta e a mensagem de pós-venda sai junto quando o
 * toggle permite) e FOLLOW-UP de orçamento parado (#8 — proposta orcada há follow_up_days sem
 * resposta recebe 1 toque por episódio; é funil ativo, não disparo em massa). Textos defensivos,
 * sem pressão nem promessa. Best-effort.
 */
@Component
public class EventosReminderJob {

    private static final Logger log = LoggerFactory.getLogger(EventosReminderJob.class);
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM");

    private final JdbcTemplate jdbcTemplate;
    private final EventProposalNotifier notifier;
    private final ScheduledJobRunRepository jobRunRepository;

    public EventosReminderJob(JdbcTemplate jdbcTemplate, EventProposalNotifier notifier,
                              ScheduledJobRunRepository jobRunRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.notifier = notifier;
        this.jobRunRepository = jobRunRepository;
    }

    /** Tick agendado (cron configurável; default diário às 10h30). Delega aos públicos p/ os testes. */
    @Scheduled(cron = "${eventos.reminder-cron:0 30 10 * * *}")
    public void scheduledRun() {
        var runId = jobRunRepository.start("EventosReminderJob");
        try {
            runAutoComplete();
            runFollowUps();
            jobRunRepository.finishSuccess(runId);
        } catch (RuntimeException e) {
            jobRunRepository.finishFailed(runId, e.getMessage());
            throw e;
        }
    }

    /**
     * Proposta FECHADA com event_date passada → REALIZADA (#6) + pós-venda quando o toggle
     * permite (mesmo texto do funil manual). Público para os testes.
     */
    public int runAutoComplete() {
        record Due(UUID id, UUID companyId, UUID conversationId, boolean postEvent, String reviewLink) {}
        List<Due> due = jdbcTemplate.query(
            "select p.id, p.company_id, p.conversation_id, "
                + "coalesce(cfg.post_event_enabled, true) as post_event, cfg.review_link "
                + "from event_proposals p "
                + "join companies co on co.id = p.company_id and co.profile_id = 'eventos' "
                + "left join event_config cfg on cfg.company_id = p.company_id "
                + "where coalesce(cfg.auto_complete_enabled, true) "
                + "and p.status = 'fechada' "
                + "and p.event_date is not null "
                + "and p.event_date < (now() at time zone 'America/Sao_Paulo')::date "
                + "order by p.company_id",
            (rs, rn) -> new Due((UUID) rs.getObject("id"), (UUID) rs.getObject("company_id"),
                (UUID) rs.getObject("conversation_id"), rs.getBoolean("post_event"),
                rs.getString("review_link")));
        int touched = 0;
        for (Due d : due) {
            try {
                jdbcTemplate.update(
                    "update event_proposals set status = 'realizada', status_updated_at = now(), "
                        + "closed_at = coalesce(closed_at, now()) where id = ? and status = 'fechada'",
                    d.id());
                if (d.postEvent()) {
                    StringBuilder pos = new StringBuilder("Esperamos que a festa tenha sido inesquecível! "
                        + "Obrigado por celebrar com a gente. 🎉 ");
                    if (d.reviewLink() != null && !d.reviewLink().isBlank()) {
                        pos.append("Se puder, deixe sua avaliação — ajuda muito: ")
                            .append(d.reviewLink()).append(" ");
                    }
                    pos.append("E se conhecer alguém planejando um evento, ficaremos felizes com a "
                        + "indicação!");
                    notifier.notifyStatus(d.companyId(), d.conversationId(), pos.toString());
                }
                touched++;
            } catch (Exception e) {
                log.warn("eventos-autocomplete: failed {} ({})", d.id(), e.getMessage());
            }
        }
        return touched;
    }

    /** Follow-up de proposta ORCADA parada (#8) — 1 toque por episódio. Público para os testes. */
    public int runFollowUps() {
        record Due(UUID id, UUID companyId, UUID conversationId, String eventType,
                   java.sql.Date eventDate) {}
        List<Due> due = jdbcTemplate.query(
            "select p.id, p.company_id, p.conversation_id, p.event_type, p.event_date "
                + "from event_proposals p "
                + "join companies co on co.id = p.company_id and co.profile_id = 'eventos' "
                + "left join event_config cfg on cfg.company_id = p.company_id "
                + "where coalesce(cfg.follow_up_enabled, true) "
                + "and p.status = 'orcada' "
                + "and p.status_updated_at < now() - make_interval(days => coalesce(cfg.follow_up_days, 3)) "
                + "and (p.follow_up_sent_at is null or p.follow_up_sent_at < p.status_updated_at) "
                + "order by p.company_id",
            (rs, rn) -> new Due((UUID) rs.getObject("id"), (UUID) rs.getObject("company_id"),
                (UUID) rs.getObject("conversation_id"), rs.getString("event_type"),
                rs.getDate("event_date")));
        int touched = 0;
        for (Due d : due) {
            try {
                StringBuilder sb = new StringBuilder("Oi! Seu orçamento");
                if (d.eventType() != null && !d.eventType().isBlank()) {
                    sb.append(" para ").append(d.eventType());
                }
                if (d.eventDate() != null) {
                    sb.append(" (").append(DIA.format(d.eventDate().toLocalDate())).append(")");
                }
                sb.append(" continua disponível por aqui. Ficou alguma dúvida? "
                    + "É só me chamar que eu ajudo! 🎈");
                notifier.notifyStatus(d.companyId(), d.conversationId(), sb.toString());
                jdbcTemplate.update(
                    "update event_proposals set follow_up_sent_at = now() where id = ?", d.id());
                touched++;
            } catch (Exception e) {
                log.warn("eventos-followup: failed {} ({})", d.id(), e.getMessage());
            }
        }
        return touched;
    }
}
