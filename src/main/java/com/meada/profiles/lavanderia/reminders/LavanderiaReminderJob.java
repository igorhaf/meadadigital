package com.meada.profiles.lavanderia.reminders;

import com.meada.admin.health.ScheduledJobRunRepository;
import com.meada.profiles.lavanderia.orders.LavanderiaOrderNotifier;
import com.meada.profiles.lavanderia.reminders.LavanderiaReminderRepository.DueCollect;
import com.meada.profiles.lavanderia.reminders.LavanderiaReminderRepository.DueInactive;
import com.meada.profiles.lavanderia.reminders.LavanderiaReminderRepository.DueReady;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Automações da onda Lavanderia 1 num tick diário: lembrete de coleta D-1 (#7 — corta coleta
 * furada), lembrete de peça pronta parada (#14 — cobra a combinação da entrega) e reativação de
 * inativo (#3 — opt-in DESLIGADO por default, lição Baileys). Mensagens FIXAS via
 * {@link LavanderiaOrderNotifier} (best-effort, dry-run honrado); a resposta do cliente cai no
 * fluxo inbound normal e a IA segue a conversa.
 */
@Component
public class LavanderiaReminderJob {

    private static final Logger log = LoggerFactory.getLogger(LavanderiaReminderJob.class);
    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");

    private final LavanderiaReminderRepository repository;
    private final LavanderiaOrderNotifier notifier;
    private final ScheduledJobRunRepository jobRunRepository;

    public LavanderiaReminderJob(LavanderiaReminderRepository repository,
                                 LavanderiaOrderNotifier notifier,
                                 ScheduledJobRunRepository jobRunRepository) {
        this.repository = repository;
        this.notifier = notifier;
        this.jobRunRepository = jobRunRepository;
    }

    /** Tick agendado (cron configurável; default diário às 9h40). Delega aos públicos p/ os testes. */
    @Scheduled(cron = "${lavanderia.reminder-cron:0 40 9 * * *}")
    public void scheduledRun() {
        var runId = jobRunRepository.start("LavanderiaReminderJob");
        try {
            runCollectReminders();
            runReadyReminders();
            runReactivations();
            jobRunRepository.finishSuccess(runId);
        } catch (RuntimeException e) {
            jobRunRepository.finishFailed(runId, e.getMessage());
            throw e;
        }
    }

    /** Lembrete D-1 da coleta (#7). Remarcar a coleta REARMA. Público para os testes. */
    public int runCollectReminders() {
        LocalDate tomorrow = LocalDate.now(TENANT_ZONE).plusDays(1);
        int touched = 0;
        for (DueCollect d : repository.findDueCollectReminders(tomorrow)) {
            try {
                String periodo = "manha".equals(d.period()) ? "de manhã" : "à tarde";
                notifier.notifyStatus(d.companyId(), d.conversationId(),
                    "Lembrete: sua coleta está agendada para AMANHÃ " + periodo
                        + ". Combine de ter alguém em casa pra entregar as peças, tá? 🧺");
                repository.markCollectReminded(d.orderId(), d.collectDate());
                touched++;
            } catch (Exception e) {
                log.warn("lavanderia-collect-reminder: failed {} ({})", d.orderId(), e.getMessage());
            }
        }
        return touched;
    }

    /** Peça pronta parada além da janela (#14) — 1 toque por episódio. Público para os testes. */
    public int runReadyReminders() {
        int touched = 0;
        for (DueReady d : repository.findDueReadyReminders()) {
            try {
                notifier.notifyStatus(d.companyId(), d.conversationId(),
                    "Suas peças continuam prontinhas por aqui te esperando! "
                        + "Quer combinar a entrega? É só responder por aqui. ✨");
                repository.markReadyReminded(d.orderId());
                touched++;
            } catch (Exception e) {
                log.warn("lavanderia-ready-reminder: failed {} ({})", d.orderId(), e.getMessage());
            }
        }
        return touched;
    }

    /** Reativação de inativos (#3) — opt-in; cooldown = a própria janela. Público para os testes. */
    public int runReactivations() {
        int touched = 0;
        for (DueInactive c : repository.findDueReactivations()) {
            try {
                if (c.conversationId() == null) {
                    log.info("lavanderia-reactivation: contato {} sem conversa — marcado sem envio",
                        c.contactId());
                    repository.markReactivationSent(c.companyId(), c.contactId(), false);
                } else {
                    notifier.notifyStatus(c.companyId(), c.conversationId(), buildReactivationText(c));
                    repository.markReactivationSent(c.companyId(), c.contactId(), true);
                }
                touched++;
            } catch (Exception e) {
                log.warn("lavanderia-reactivation: failed contact {} ({})", c.contactId(), e.getMessage());
            }
        }
        return touched;
    }

    /** Texto fixo e leve; o cupom só entra quando validado na varredura. */
    private static String buildReactivationText(DueInactive c) {
        String nome = c.contactName() == null || c.contactName().isBlank() ? "" : ", " + c.contactName();
        StringBuilder sb = new StringBuilder("Oi").append(nome)
            .append("! Faz um tempinho que não cuidamos das suas roupas 🧺 Quer agendar uma coleta?");
        if (c.couponCode() != null) {
            sb.append(" Use o cupom ").append(c.couponCode()).append(" no seu próximo pedido.");
        }
        sb.append(" É só chamar por aqui!");
        return sb.toString();
    }
}
