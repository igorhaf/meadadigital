package com.meada.profiles.oficina.reminders;

import com.meada.admin.health.ScheduledJobRunRepository;
import com.meada.profiles.oficina.orders.ServiceOrderNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Lembrete de retorno/revisão da oficina (onda Oficina 1, backlog #2). Manutenção é recorrente por
 * natureza (óleo/revisão/alinhamento), mas sem gatilho o cliente só volta quando quebra — em outra
 * oficina. Na ENTREGA a OS materializa o retorno sugerido; este cron dispara UMA mensagem por OS
 * quando vence ("faz X meses do último serviço no {modelo/placa} — hora da revisão?").
 *
 * <p>Mensagem FIXA pela conversa da OS ({@link ServiceOrderNotifier}) — NÃO passa pela IA (trava
 * intacta: sem diagnóstico/preço; a resposta cai no fluxo normal, que abre nova OS). Sem canal →
 * marca sem envio. {@code EVOLUTION_DRY_RUN} honrado por baixo.
 */
@Component
public class OficinaReminderJob {

    private static final Logger log = LoggerFactory.getLogger(OficinaReminderJob.class);

    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");

    private final OficinaReminderRepository reminderRepository;
    private final ServiceOrderNotifier notifier;
    private final ScheduledJobRunRepository jobRunRepository;

    public OficinaReminderJob(OficinaReminderRepository reminderRepository,
                              ServiceOrderNotifier notifier,
                              ScheduledJobRunRepository jobRunRepository) {
        this.reminderRepository = reminderRepository;
        this.notifier = notifier;
        this.jobRunRepository = jobRunRepository;
    }

    /** Tick agendado (cron configurável; default diário às 10h30). Delega ao público p/ os testes. */
    @Scheduled(cron = "${oficina.return-reminder-cron:0 30 10 * * *}")
    public void scheduledRun() {
        var runId = jobRunRepository.start("OficinaReminderJob");
        try {
            runReturnReminders();
            jobRunRepository.finishSuccess(runId);
        } catch (RuntimeException e) {
            jobRunRepository.finishFailed(runId, e.getMessage());
            throw e;
        }
    }

    /**
     * Lembra os retornos vencidos de todos os tenants oficina. Público para os testes.
     *
     * @return número de OS marcadas neste run (com ou sem canal)
     */
    public int runReturnReminders() {
        LocalDate today = LocalDate.now(TENANT_ZONE);
        List<DueReturn> due = reminderRepository.findDueReturns(today);
        int touched = 0;
        for (DueReturn r : due) {
            try {
                if (r.conversationId() == null) {
                    log.info("oficina-reminder: OS {} sem canal (manual) — marcada sem envio", r.orderId());
                } else {
                    notifier.notifyStatus(r.companyId(), r.conversationId(), buildText(r));
                }
                reminderRepository.markReminded(r.orderId());
                touched++;
            } catch (Exception e) {
                log.warn("oficina-reminder: failed OS {} ({})", r.orderId(), e.getMessage());
            }
        }
        return touched;
    }

    /** Texto fixo; a resposta cai no fluxo da IA (que abre nova OS pela queixa, como sempre). */
    private static String buildText(DueReturn r) {
        String veiculo = r.vehicleModel() != null && !r.vehicleModel().isBlank()
            ? r.vehicleModel() + " (" + r.vehiclePlate() + ")" : r.vehiclePlate();
        return "Olá, " + r.customerName() + "! Já faz um tempo desde o último serviço no seu "
            + veiculo + ". Que tal agendar uma revisão preventiva? É só responder por aqui que a "
            + "gente cuida do resto. 🔧";
    }
}
