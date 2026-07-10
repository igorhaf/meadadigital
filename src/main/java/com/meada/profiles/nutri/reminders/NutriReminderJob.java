package com.meada.profiles.nutri.reminders;

import com.meada.admin.health.ScheduledJobRunRepository;
import com.meada.profiles.nutri.appointments.NutriAppointmentNotifier;
import com.meada.profiles.nutri.appointments.NutriAppointmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Disparos temporais da nutri (onda 1, backlog #1/#2/#5): lembrete de consulta na véspera (a
 * resposta cai na IA, que emite {@code <confirmacao_nutri>} — clone pet/otica), auto-transição
 * confirmado→realizado (folga 2h; silencioso) e régua de retomada do paciente inativo (OPT-IN,
 * 1 toque por ciclo). TUDO logística — a trava clínica segue intacta (nada de dieta/plano).
 * Mensagens FIXAS; sem canal → marca sem envio; {@code EVOLUTION_DRY_RUN} honrado.
 */
@Component
public class NutriReminderJob {

    private static final Logger log = LoggerFactory.getLogger(NutriReminderJob.class);

    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");
    private static final int GRACE_HOURS = 2;

    private final NutriReminderRepository reminderRepository;
    private final NutriAppointmentNotifier notifier;
    private final NutriAppointmentService appointmentService;
    private final ScheduledJobRunRepository jobRunRepository;

    public NutriReminderJob(NutriReminderRepository reminderRepository,
                            NutriAppointmentNotifier notifier,
                            NutriAppointmentService appointmentService,
                            ScheduledJobRunRepository jobRunRepository) {
        this.reminderRepository = reminderRepository;
        this.notifier = notifier;
        this.appointmentService = appointmentService;
        this.jobRunRepository = jobRunRepository;
    }

    /** Tick agendado (cron configurável; default diário às 10h40). Delega aos públicos p/ os testes. */
    @Scheduled(cron = "${nutri.reminder-cron:0 40 10 * * *}")
    public void scheduledRun() {
        var runId = jobRunRepository.start("NutriReminderJob");
        try {
            runReminders();
            runAutoComplete();
            runReengagements();
            jobRunRepository.finishSuccess(runId);
        } catch (RuntimeException e) {
            jobRunRepository.finishFailed(runId, e.getMessage());
            throw e;
        }
    }

    /** #1 — lembra as consultas de AMANHÃ. Público para os testes. */
    public int runReminders() {
        LocalDate tomorrow = LocalDate.now(TENANT_ZONE).plusDays(1);
        List<DueNutriWork> due = reminderRepository.findDueReminders(tomorrow);
        int touched = 0;
        for (DueNutriWork a : due) {
            try {
                if (a.conversationId() == null) {
                    log.info("nutri-reminder: consulta {} sem canal (manual) — marcada sem envio", a.id());
                } else {
                    String hora = HOUR.format(a.startAt().atZone(TENANT_ZONE));
                    notifier.notifyStatus(a.companyId(), a.conversationId(),
                        "Olá! A consulta de " + a.personName() + " com " + a.professionalName()
                            + " é AMANHÃ às " + hora + ". Confirma? Se precisar remarcar, é só "
                            + "responder por aqui. 🥗");
                }
                reminderRepository.markReminded(a.id(), a.startAt());
                touched++;
            } catch (Exception e) {
                log.warn("nutri-reminder: failed appointment {} ({})", a.id(), e.getMessage());
            }
        }
        return touched;
    }

    /** #5 — confirmado vencido → realizado (silencioso). Público para os testes. */
    public int runAutoComplete() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(GRACE_HOURS));
        int touched = 0;
        for (DueNutriWork a : reminderRepository.findConfirmedPast(cutoff)) {
            try {
                appointmentService.updateStatus(a.companyId(), a.id(), "realizado");
                touched++;
            } catch (RuntimeException e) {
                log.warn("nutri-auto-complete: failed appointment {} ({})", a.id(), e.getMessage());
            }
        }
        return touched;
    }

    /** #2 — régua de retomada (opt-in; 1 toque por ciclo). Público para os testes. */
    public int runReengagements() {
        List<DueNutriWork> due = reminderRepository.findDueReengagements();
        int touched = 0;
        for (DueNutriWork p : due) {
            try {
                if (p.conversationId() == null) {
                    log.info("nutri-reengage: paciente {} sem conversa — marcado sem envio", p.id());
                } else {
                    notifier.notifyStatus(p.companyId(), p.conversationId(),
                        "Oi! Faz um tempinho que não nos vemos por aqui. Que tal retomar o "
                            + "acompanhamento de " + p.personName() + "? Se quiser, já agendo uma "
                            + "consulta pra você. 💚");
                }
                reminderRepository.markReengaged(p.id());
                touched++;
            } catch (Exception e) {
                log.warn("nutri-reengage: failed patient {} ({})", p.id(), e.getMessage());
            }
        }
        return touched;
    }
}
