package com.meada.profiles.legal.deadlines;

import com.meada.admin.health.ScheduledJobRunRepository;
import com.meada.profiles.legal.cases.LegalCaseNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Lembrete de prazos/audiências do escritório (onda Legal 1, backlog #1). Prazo perdido é dano ao
 * cliente e risco de responsabilidade — o job avisa o CLIENTE vinculado em D-3 e D-1 (texto FIXO
 * com data/local, SEM mérito — trava jurídica intacta; sem cliente/canal vinculado marca sem
 * envio). Idempotência por (prazo, due_date) em cada janela — remarcar REARMA os dois avisos.
 */
@Component
public class LegalDeadlineReminderJob {

    private static final Logger log = LoggerFactory.getLogger(LegalDeadlineReminderJob.class);

    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");

    private final LegalDeadlineRepository repository;
    private final LegalCaseNotifier notifier;
    private final ScheduledJobRunRepository jobRunRepository;

    public LegalDeadlineReminderJob(LegalDeadlineRepository repository,
                                    LegalCaseNotifier notifier,
                                    ScheduledJobRunRepository jobRunRepository) {
        this.repository = repository;
        this.notifier = notifier;
        this.jobRunRepository = jobRunRepository;
    }

    /** Tick agendado (cron configurável; default diário às 8h30). Delega ao público p/ os testes. */
    @Scheduled(cron = "${legal.deadline-reminder-cron:0 30 8 * * *}")
    public void scheduledRun() {
        var runId = jobRunRepository.start("LegalDeadlineReminderJob");
        try {
            runReminders();
            jobRunRepository.finishSuccess(runId);
        } catch (RuntimeException e) {
            jobRunRepository.finishFailed(runId, e.getMessage());
            throw e;
        }
    }

    /**
     * Dispara os lembretes D-3 e D-1 de todos os tenants legal. Público para os testes.
     *
     * @return número de prazos marcados neste run
     */
    public int runReminders() {
        LocalDate today = LocalDate.now(TENANT_ZONE);
        int touched = 0;
        touched += sweep(repository.findDueForWindow(today.plusDays(3), "reminded3_due_date"),
            "reminded3_due_date", 3);
        touched += sweep(repository.findDueForWindow(today.plusDays(1), "reminded1_due_date"),
            "reminded1_due_date", 1);
        return touched;
    }

    private int sweep(List<DueDeadline> due, String marker, int daysBefore) {
        int touched = 0;
        for (DueDeadline d : due) {
            try {
                // best-effort: o notifier resolve o contato via legal_client e pula em silêncio
                // se não houver vínculo WhatsApp; a marcação evita revarredura eterna.
                notifier.notifyStatus(d.companyId(), d.legalClientId(), buildText(d, daysBefore));
                repository.markReminded(d.deadlineId(), d.dueDate(), marker);
                touched++;
            } catch (Exception e) {
                log.warn("legal-deadline: failed {} ({})", d.deadlineId(), e.getMessage());
            }
        }
        return touched;
    }

    /** Texto fixo e defensivo — data/hora/local, sem qualquer análise de mérito. */
    private static String buildText(DueDeadline d, int daysBefore) {
        String quando = daysBefore == 1 ? "AMANHÃ" : "em " + daysBefore + " dias ("
            + DAY_MONTH.format(d.dueDate()) + ")";
        StringBuilder sb = new StringBuilder("Lembrete do escritório: ")
            .append("audiencia".equals(d.kind()) ? "sua AUDIÊNCIA" : "o prazo")
            .append(" \"").append(d.title()).append("\" (processo ").append(d.caseTitle())
            .append(") é ").append(quando);
        if (d.dueTime() != null) {
            sb.append(" às ").append(d.dueTime().toString().substring(0, 5));
        }
        if (d.location() != null && !d.location().isBlank()) {
            sb.append(", em ").append(d.location());
        }
        sb.append(". Qualquer dúvida, fale com a gente por aqui.");
        return sb.toString();
    }
}
