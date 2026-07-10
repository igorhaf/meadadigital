package com.meada.profiles.floricultura.reminders;

import com.meada.admin.health.ScheduledJobRunRepository;
import com.meada.profiles.floricultura.orders.FloriculturaOrderNotifier;
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
 * Confirmação D-1 da entrega (onda Floricultura 1, backlog #9): flor é presente perecível com
 * data marcada — entrega furada é arranjo perdido. Na véspera, o COMPRADOR recebe um aviso
 * confirmando endereço/período (a resposta cai na conversa; mudanças são combinadas com a loja).
 * Vale pra pedidos ACEITOS (em_preparo); 1x por data ({@code delivery_reminded_date} — remarcar
 * REARMA). Toggle {@code delivery_reminder_enabled} (default ON). Best-effort.
 */
@Component
public class FloriculturaReminderJob {

    private static final Logger log = LoggerFactory.getLogger(FloriculturaReminderJob.class);
    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");

    private final JdbcTemplate jdbcTemplate;
    private final FloriculturaOrderNotifier notifier;
    private final ScheduledJobRunRepository jobRunRepository;

    public FloriculturaReminderJob(JdbcTemplate jdbcTemplate, FloriculturaOrderNotifier notifier,
                                   ScheduledJobRunRepository jobRunRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.notifier = notifier;
        this.jobRunRepository = jobRunRepository;
    }

    /** Tick agendado (cron configurável; default diário às 10h10). Delega ao público p/ os testes. */
    @Scheduled(cron = "${floricultura.reminder-cron:0 10 10 * * *}")
    public void scheduledRun() {
        var runId = jobRunRepository.start("FloriculturaReminderJob");
        try {
            runDeliveryReminders();
            jobRunRepository.finishSuccess(runId);
        } catch (RuntimeException e) {
            jobRunRepository.finishFailed(runId, e.getMessage());
            throw e;
        }
    }

    /** Aviso D-1 da entrega ao comprador. Público e direto para os testes. */
    public int runDeliveryReminders() {
        LocalDate tomorrow = LocalDate.now(TENANT_ZONE).plusDays(1);
        record Due(UUID orderId, UUID companyId, UUID conversationId, String recipient,
                   String address, String period, LocalDate deliveryDate) {}
        List<Due> due = jdbcTemplate.query(
            "select o.id, o.company_id, o.conversation_id, o.recipient_name, o.delivery_address, "
                + "o.delivery_period, o.delivery_date "
                + "from floricultura_orders o "
                + "join companies co on co.id = o.company_id and co.profile_id = 'floricultura' "
                + "left join floricultura_config cfg on cfg.company_id = o.company_id "
                + "where coalesce(cfg.delivery_reminder_enabled, true) "
                + "and o.status = 'em_preparo' "
                + "and o.delivery_date = ? "
                + "and (o.delivery_reminded_date is null or o.delivery_reminded_date <> o.delivery_date) "
                + "order by o.company_id",
            (rs, rn) -> new Due((UUID) rs.getObject("id"), (UUID) rs.getObject("company_id"),
                (UUID) rs.getObject("conversation_id"), rs.getString("recipient_name"),
                rs.getString("delivery_address"), rs.getString("delivery_period"),
                rs.getDate("delivery_date").toLocalDate()),
            Date.valueOf(tomorrow));
        int touched = 0;
        for (Due d : due) {
            try {
                String periodo = "manha".equals(d.period()) ? "de manhã" : "à tarde";
                notifier.notifyStatus(d.companyId(), d.conversationId(),
                    "Amanhã " + periodo + " entregamos as flores para " + d.recipient()
                        + " em: " + d.address() + ". Se algo mudou (endereço ou horário), "
                        + "me avisa por aqui! 💐");
                jdbcTemplate.update(
                    "update floricultura_orders set delivery_reminded_date = ? where id = ?",
                    Date.valueOf(d.deliveryDate()), d.orderId());
                touched++;
            } catch (Exception e) {
                log.warn("floricultura-reminder: failed {} ({})", d.orderId(), e.getMessage());
            }
        }
        return touched;
    }
}
