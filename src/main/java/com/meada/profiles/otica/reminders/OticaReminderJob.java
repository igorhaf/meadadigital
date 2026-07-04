package com.meada.profiles.otica.reminders;

import com.meada.admin.health.ScheduledJobRunRepository;
import com.meada.profiles.otica.appointments.OticaExamNotifier;
import com.meada.profiles.otica.orders.OticaOrderNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Disparos temporais da ótica (onda 1, backlog #1/#2): lembrete de exame na véspera (a cadeira do
 * optometrista é o funil da venda de óculos) e follow-up do óculos PRONTO parado (capital
 * imobilizado na bancada). Mensagens FIXAS — não passam pela IA; a resposta do lembrete cai no
 * fluxo da IA, que emite {@code <confirmacao_exame>} (clone restaurant/pousada/pet). Trava
 * intacta: nada de grau/conduta. Idempotência: (exame, start_at) — remarcar rearma; follow-up 1x
 * por episódio de 'pronto' (re-armado por status_updated_at). Sem canal → marca sem envio.
 */
@Component
public class OticaReminderJob {

    private static final Logger log = LoggerFactory.getLogger(OticaReminderJob.class);

    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

    private final OticaReminderRepository reminderRepository;
    private final OticaExamNotifier examNotifier;
    private final OticaOrderNotifier orderNotifier;
    private final ScheduledJobRunRepository jobRunRepository;

    public OticaReminderJob(OticaReminderRepository reminderRepository,
                            OticaExamNotifier examNotifier,
                            OticaOrderNotifier orderNotifier,
                            ScheduledJobRunRepository jobRunRepository) {
        this.reminderRepository = reminderRepository;
        this.examNotifier = examNotifier;
        this.orderNotifier = orderNotifier;
        this.jobRunRepository = jobRunRepository;
    }

    /** Tick agendado (cron configurável; default diário às 10h10). Delega aos públicos p/ os testes. */
    @Scheduled(cron = "${otica.reminder-cron:0 10 10 * * *}")
    public void scheduledRun() {
        var runId = jobRunRepository.start("OticaReminderJob");
        try {
            runExamReminders();
            runPickupFollowups();
            jobRunRepository.finishSuccess(runId);
        } catch (RuntimeException e) {
            jobRunRepository.finishFailed(runId, e.getMessage());
            throw e;
        }
    }

    /**
     * Lembra os exames de AMANHÃ em todos os tenants otica. Público para os testes.
     *
     * @return número de exames marcados neste run (com ou sem canal)
     */
    public int runExamReminders() {
        LocalDate tomorrow = LocalDate.now(TENANT_ZONE).plusDays(1);
        List<DueOticaWork> due = reminderRepository.findDueExamReminders(tomorrow);
        int touched = 0;
        for (DueOticaWork e : due) {
            try {
                if (e.conversationId() == null) {
                    log.info("otica-reminder: exame {} sem canal (manual) — marcado sem envio", e.id());
                } else {
                    String hora = HOUR.format(e.startAt().atZone(TENANT_ZONE));
                    examNotifier.notifyStatus(e.companyId(), e.conversationId(),
                        "Olá, " + e.customerName() + "! Seu exame de vista com " + e.professionalName()
                            + " é AMANHÃ às " + hora + ". Confirma sua presença? "
                            + "Se precisar remarcar, é só responder por aqui. 👓");
                }
                reminderRepository.markExamReminded(e.id(), e.startAt());
                touched++;
            } catch (Exception ex) {
                log.warn("otica-reminder: failed exam {} ({})", e.id(), ex.getMessage());
            }
        }
        return touched;
    }

    /**
     * Cutuca os pedidos parados em 'pronto' (janela por tenant). Público para os testes.
     *
     * @return número de pedidos cutucados neste run (com ou sem canal)
     */
    public int runPickupFollowups() {
        List<DueOticaWork> due = reminderRepository.findStalePickups();
        int touched = 0;
        for (DueOticaWork o : due) {
            try {
                if (o.conversationId() == null) {
                    log.info("otica-followup: pedido {} sem canal (manual) — marcado sem envio", o.id());
                } else {
                    orderNotifier.notifyStatus(o.companyId(), o.conversationId(),
                        "Oi, " + o.customerName() + "! Seu óculos está PRONTO e te esperando aqui na "
                            + "loja. 👓 Quando quiser passar pra buscar, estamos à disposição!");
                }
                reminderRepository.markPickupFollowedUp(o.id());
                touched++;
            } catch (Exception ex) {
                log.warn("otica-followup: failed order {} ({})", o.id(), ex.getMessage());
            }
        }
        return touched;
    }
}
