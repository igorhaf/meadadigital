package com.meada.profiles.concessionaria.reminders;

import com.meada.admin.health.ScheduledJobRunRepository;
import com.meada.profiles.concessionaria.testdrives.ConcessionariaTestDriveNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Lembrete + pedido de confirmação de test-drive (onda 1 da concessionária, backlog #3): o no-show
 * queima agenda de vendedor e veículo bloqueado à toa.
 *
 * <p>Periodicamente (fixedDelay), varre os test-drives 'agendado' que começam nas PRÓXIMAS 24h e
 * ainda não foram lembrados, e envia "confirma? Responda SIM ou CANCELAR" pelo canal da conversa
 * (texto fixo — não passa pela IA). A RESPOSTA fecha o loop pela tag {@code <confirmacao_testdrive>}
 * ({@code ConfirmacaoTestDriveHandler}: barreira de contato + máquina de status; cancelar libera o
 * horário e o veículo pra outro cliente na hora).
 *
 * <p>Idempotência simples por test-drive ({@code reminded_24h}). Sem canal (criado no painel) →
 * marca sem enviar. {@code EVOLUTION_DRY_RUN} honrado. Molde dos jobs existentes:
 * {@code @Scheduled} fino instrumentado; lógica pública testável.
 */
@Component
public class ConcessionariaReminderJob {

    private static final Logger log = LoggerFactory.getLogger(ConcessionariaReminderJob.class);

    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final ConcessionariaJobsRepository jobsRepository;
    private final ConcessionariaTestDriveNotifier notifier;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final ScheduledJobRunRepository jobRunRepository;

    public ConcessionariaReminderJob(ConcessionariaJobsRepository jobsRepository,
                                     ConcessionariaTestDriveNotifier notifier,
                                     ScheduledJobRunRepository jobRunRepository,
                                     org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.jobsRepository = jobsRepository;
        this.notifier = notifier;
        this.jdbcTemplate = jdbcTemplate;
        this.jobRunRepository = jobRunRepository;
    }

    /** Tick do job (fixedDelay; default 5min). Delega ao método público para os testes. */
    @Scheduled(fixedDelayString = "${concessionaria.reminder-check-ms:300000}")
    public void scheduledRun() {
        var runId = jobRunRepository.start("ConcessionariaReminderJob");
        try {
            runTestDriveReminders();
            runServiceReminders();
            jobRunRepository.finishSuccess(runId);
        } catch (RuntimeException e) {
            jobRunRepository.finishFailed(runId, e.getMessage());
            throw e;
        }
    }

    /**
     * Lembra os test-drives 'agendado' das próximas 24h. Público e direto para os testes.
     *
     * @return número de test-drives marcados como lembrados neste run (com ou sem canal)
     */
    public int runTestDriveReminders() {
        Instant windowEnd = Instant.now().plus(Duration.ofHours(24));
        List<ConcessionariaJobsRepository.DueTestDrive> due = jobsRepository.findDueTestDrives(windowEnd);
        int touched = 0;
        for (ConcessionariaJobsRepository.DueTestDrive t : due) {
            try {
                if (t.conversationId() == null) {
                    log.info("concessionaria-reminder: test-drive {} sem canal — marcado sem envio",
                        t.testDriveId());
                } else {
                    ZonedDateTime z = t.startAt().atZone(TENANT_ZONE);
                    notifier.notifyStatus(t.companyId(), t.conversationId(),
                        "Oi, " + t.customerName() + "! Seu test-drive do " + t.vehicleBrand() + " "
                            + t.vehicleModel() + " é " + DATE_FMT.format(z) + " às " + TIME_FMT.format(z)
                            + ". Podemos confirmar? Responda SIM para confirmar ou CANCELAR para desmarcar.");
                }
                jobsRepository.markTestDriveReminded(t.testDriveId());
                touched++;
            } catch (Exception e) {
                log.warn("concessionaria-reminder: failed test-drive {} ({})", t.testDriveId(), e.getMessage());
            }
        }
        return touched;
    }

    /**
     * Onda 2 (backlog #12): revisão programada — N meses após o FECHAMENTO do lead, convite de
     * revisão/checape (1 toque por lead; opt-in OFF). Traz o cliente de volta pro serviço e
     * abre caminho pra recompra. Público para os testes.
     */
    public int runServiceReminders() {
        record Due(java.util.UUID leadId, java.util.UUID companyId, java.util.UUID conversationId,
                   String model) {}
        java.util.List<Due> due = jdbcTemplate.query(
            "select l.id, l.company_id, l.conversation_id, l.vehicle_model "
                + "from concessionaria_leads l "
                + "join companies co on co.id = l.company_id and co.profile_id = 'concessionaria' "
                + "join concessionaria_config cfg on cfg.company_id = l.company_id "
                + "  and cfg.service_reminder_enabled "
                + "where l.status = 'fechado' "
                + "and l.conversation_id is not null "
                + "and l.status_updated_at < now() - make_interval(months => cfg.service_reminder_months) "
                + "and l.service_reminded_at is null "
                + "order by l.company_id",
            (rs, rn) -> new Due((java.util.UUID) rs.getObject("id"),
                (java.util.UUID) rs.getObject("company_id"),
                (java.util.UUID) rs.getObject("conversation_id"), rs.getString("vehicle_model")));
        int touched = 0;
        for (Due d : due) {
            try {
                notifier.notifyStatus(d.companyId(), d.conversationId(),
                    "Já faz um tempinho que você saiu daqui com o " + d.model()
                        + " 🚗 Que tal agendar uma revisão/checape? E se estiver pensando em "
                        + "trocar de carro, temos novidades no pátio — é só chamar!");
                jdbcTemplate.update(
                    "update concessionaria_leads set service_reminded_at = now() where id = ?", d.leadId());
                touched++;
            } catch (Exception e) {
                log.warn("concessionaria-service-reminder: failed {} ({})", d.leadId(), e.getMessage());
            }
        }
        return touched;
    }

}
