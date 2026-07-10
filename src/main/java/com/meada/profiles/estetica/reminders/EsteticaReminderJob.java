package com.meada.profiles.estetica.reminders;

import com.meada.admin.health.ScheduledJobRunRepository;
import com.meada.profiles.estetica.appointments.AestheticAppointmentNotifier;
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
 * Automações da onda Estética 1 num tick diário (backlog #1/#2/#3/#4): lembrete de véspera
 * pedindo confirmação SIM/NÃO (a resposta fecha o loop via ConfirmacaoEsteticaHandler — e o NÃO
 * devolve a sessão ao pacote pela mecânica existente), auto-transição confirmado vencido →
 * realizado (silencioso), expiração de pacote ATIVO com valid_until vencida e a RÉGUA DE
 * RENOVAÇÃO (opt-in OFF — pacote esgotado há N dias OU a vencer em N dias, 1 toque por pacote;
 * a resposta cai no fluxo <compra_pacote> existente). Textos operacionais — a IA não recomenda
 * procedimento nem opina (trava intacta). Best-effort.
 */
@Component
public class EsteticaReminderJob {

    private static final Logger log = LoggerFactory.getLogger(EsteticaReminderJob.class);
    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final JdbcTemplate jdbcTemplate;
    private final AestheticAppointmentNotifier notifier;
    private final ScheduledJobRunRepository jobRunRepository;

    public EsteticaReminderJob(JdbcTemplate jdbcTemplate, AestheticAppointmentNotifier notifier,
                               ScheduledJobRunRepository jobRunRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.notifier = notifier;
        this.jobRunRepository = jobRunRepository;
    }

    /** Tick agendado (cron configurável; default diário às 10h50). Delega aos públicos p/ os testes. */
    @Scheduled(cron = "${estetica.reminder-cron:0 50 10 * * *}")
    public void scheduledRun() {
        var runId = jobRunRepository.start("EsteticaReminderJob");
        try {
            runReminders();
            runAutoComplete();
            runAutoExpire();
            runRenewals();
            jobRunRepository.finishSuccess(runId);
        } catch (RuntimeException e) {
            jobRunRepository.finishFailed(runId, e.getMessage());
            throw e;
        }
    }

    /** Lembrete de véspera com pedido de confirmação (#1/#2). Remarcar REARMA. Público p/ testes. */
    public int runReminders() {
        LocalDate tomorrow = LocalDate.now(TENANT_ZONE).plusDays(1);
        record Due(UUID id, UUID companyId, UUID conversationId, String procedureName,
                   String professionalName, Instant startAt) {}
        List<Due> due = jdbcTemplate.query(
            "select a.id, a.company_id, a.conversation_id, a.procedure_name, a.professional_name, a.start_at "
                + "from aesthetic_appointments a "
                + "join companies co on co.id = a.company_id and co.profile_id = 'estetica' "
                + "left join aesthetic_config cfg on cfg.company_id = a.company_id "
                + "where coalesce(cfg.reminder_enabled, true) "
                + "and a.status in ('agendado','confirmado') "
                + "and (a.start_at at time zone 'America/Sao_Paulo')::date = ? "
                + "and (a.reminded_start_at is null or a.reminded_start_at <> a.start_at) "
                + "order by a.company_id",
            (rs, rn) -> new Due((UUID) rs.getObject("id"), (UUID) rs.getObject("company_id"),
                (UUID) rs.getObject("conversation_id"), rs.getString("procedure_name"),
                rs.getString("professional_name"), rs.getTimestamp("start_at").toInstant()),
            java.sql.Date.valueOf(tomorrow));
        int touched = 0;
        for (Due d : due) {
            try {
                var local = d.startAt().atZone(TENANT_ZONE);
                notifier.notifyStatus(d.companyId(), d.conversationId(),
                    "Sua sessão de " + d.procedureName() + " é AMANHÃ às "
                        + HORA.format(local.toLocalTime()) + " com " + d.professionalName()
                        + ". Confirma? Responda SIM ou, se precisar desmarcar, NÃO. 💆");
                jdbcTemplate.update(
                    "update aesthetic_appointments set reminded_start_at = ? where id = ?",
                    Timestamp.from(d.startAt()), d.id());
                touched++;
            } catch (Exception e) {
                log.warn("estetica-reminder: failed {} ({})", d.id(), e.getMessage());
            }
        }
        return touched;
    }

    /** Confirmado vencido → realizado, silencioso (#4). Público p/ testes. */
    public int runAutoComplete() {
        return jdbcTemplate.update(
            "update aesthetic_appointments a set status = 'realizado', status_updated_at = now() "
                + "from companies co "
                + "left join aesthetic_config cfg on cfg.company_id = co.id "
                + "where co.id = a.company_id and co.profile_id = 'estetica' "
                + "and coalesce(cfg.auto_complete_enabled, true) "
                + "and a.status = 'confirmado' and a.end_at < now()");
    }

    /** Pacote ATIVO com validade vencida → EXPIRADO, silencioso (#4). Público p/ testes. */
    public int runAutoExpire() {
        return jdbcTemplate.update(
            "update aesthetic_packages p set status = 'expirado', status_updated_at = now(), "
                + "updated_at = now() "
                + "from companies co "
                + "left join aesthetic_config cfg on cfg.company_id = co.id "
                + "where co.id = p.company_id and co.profile_id = 'estetica' "
                + "and coalesce(cfg.auto_expire_enabled, true) "
                + "and p.status = 'ativo' and p.valid_until is not null "
                + "and p.valid_until < (now() at time zone 'America/Sao_Paulo')::date");
    }

    /**
     * Régua de renovação (#3, opt-in OFF): pacote ESGOTADO há renewal_days sem pacote novo do
     * contato, OU pacote ATIVO a vencer em expiry_warning_days. 1 toque por pacote. Público p/
     * testes.
     */
    public int runRenewals() {
        record Due(UUID pkgId, UUID companyId, UUID conversationId, String procedureName, boolean expiring) {}
        List<Due> due = jdbcTemplate.query(
            "select p.id, p.company_id, p.conversation_id, p.procedure_name, "
                + "(p.status = 'ativo') as expiring "
                + "from aesthetic_packages p "
                + "join companies co on co.id = p.company_id and co.profile_id = 'estetica' "
                + "join aesthetic_config cfg on cfg.company_id = p.company_id and cfg.renewal_enabled "
                + "where p.renewal_reminded_at is null "
                + "and ( "
                + "  (p.status = 'esgotado' "
                + "   and p.status_updated_at < now() - make_interval(days => cfg.renewal_days) "
                + "   and not exists (select 1 from aesthetic_packages n "
                + "     where n.company_id = p.company_id and n.contact_id = p.contact_id "
                + "     and n.status in ('pendente','ativo') and n.purchased_at > p.status_updated_at)) "
                + "  or "
                + "  (p.status = 'ativo' and p.valid_until is not null "
                + "   and p.valid_until <= (now() at time zone 'America/Sao_Paulo')::date "
                + "       + make_interval(days => cfg.expiry_warning_days)) "
                + ") "
                + "order by p.company_id",
            (rs, rn) -> new Due((UUID) rs.getObject("id"), (UUID) rs.getObject("company_id"),
                (UUID) rs.getObject("conversation_id"), rs.getString("procedure_name"),
                rs.getBoolean("expiring")));
        int touched = 0;
        for (Due d : due) {
            try {
                String text = d.expiring()
                    ? "Seu pacote de " + d.procedureName() + " está perto de vencer — ainda dá "
                        + "tempo de usar as sessões que restam. Quer agendar? É só me chamar! 💆"
                    : "Suas sessões de " + d.procedureName() + " terminaram há um tempinho. "
                        + "Quer renovar o pacote ou agendar uma sessão avulsa? É só me chamar! 💆";
                notifier.notifyStatus(d.companyId(), d.conversationId(), text);
                jdbcTemplate.update(
                    "update aesthetic_packages set renewal_reminded_at = now() where id = ?", d.pkgId());
                touched++;
            } catch (Exception e) {
                log.warn("estetica-renewal: failed {} ({})", d.pkgId(), e.getMessage());
            }
        }
        return touched;
    }
}
