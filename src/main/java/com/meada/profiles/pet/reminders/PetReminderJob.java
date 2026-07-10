package com.meada.profiles.pet.reminders;

import com.meada.admin.health.ScheduledJobRunRepository;
import com.meada.profiles.pet.appointments.PetAppointmentNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Lembrete de véspera do pet shop (onda Pet 1, backlog #1). Tutor que esquece do banho/consulta é
 * slot perdido que não se revende — o lembrete carinhoso corta o no-show e a resposta cai no fluxo
 * da IA, que emite {@code <confirmacao_pet>} (clone restaurant/pousada) movendo o agendamento pra
 * confirmado/cancelado (cancelar LIBERA o slot do profissional).
 *
 * <p>Mensagem FIXA pela conversa do tutor ({@link PetAppointmentNotifier}) — NÃO passa pela IA
 * (trava clínica intacta: é lembrete administrativo, sem conduta). Idempotência por (agendamento,
 * start_at) — remarcar REARMA. Sem canal (POST manual) → marca sem envio. {@code EVOLUTION_DRY_RUN}
 * honrado por baixo.
 */
@Component
public class PetReminderJob {

    private static final Logger log = LoggerFactory.getLogger(PetReminderJob.class);

    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

    private final PetReminderRepository reminderRepository;
    private final PetAppointmentNotifier notifier;
    private final ScheduledJobRunRepository jobRunRepository;

    public PetReminderJob(PetReminderRepository reminderRepository,
                          PetAppointmentNotifier notifier,
                          ScheduledJobRunRepository jobRunRepository) {
        this.reminderRepository = reminderRepository;
        this.notifier = notifier;
        this.jobRunRepository = jobRunRepository;
    }

    /** Tick agendado (cron configurável; default diário às 10h). Delega ao público p/ os testes. */
    @Scheduled(cron = "${pet.reminder-cron:0 0 10 * * *}")
    public void scheduledRun() {
        var runId = jobRunRepository.start("PetReminderJob");
        try {
            runReminders();
            jobRunRepository.finishSuccess(runId);
        } catch (RuntimeException e) {
            jobRunRepository.finishFailed(runId, e.getMessage());
            throw e;
        }
    }

    /**
     * Lembra os agendamentos de AMANHÃ em todos os tenants pet. Público para os testes.
     *
     * @return número de agendamentos marcados neste run (com ou sem canal)
     */
    public int runReminders() {
        LocalDate tomorrow = LocalDate.now(TENANT_ZONE).plusDays(1);
        List<DuePetAppointment> due = reminderRepository.findDueReminders(tomorrow);
        int touched = 0;
        for (DuePetAppointment a : due) {
            try {
                if (a.conversationId() == null) {
                    log.info("pet-reminder: agendamento {} sem canal (manual) — marcado sem envio",
                        a.appointmentId());
                } else {
                    // best-effort: falha de envio loga no notifier e NÃO impede a marcação.
                    notifier.notifyStatus(a.companyId(), a.conversationId(), buildText(a));
                }
                reminderRepository.markReminded(a.appointmentId(), a.startAt());
                touched++;
            } catch (Exception e) {
                log.warn("pet-reminder: failed appointment {} ({})", a.appointmentId(), e.getMessage());
            }
        }
        return touched;
    }

    /** Texto fixo e carinhoso; a resposta cai no fluxo da IA, que emite <confirmacao_pet>. */
    private static String buildText(DuePetAppointment a) {
        String hora = HOUR.format(a.startAt().atZone(TENANT_ZONE));
        return "Oi, " + a.tutorName() + "! Amanhã o " + a.animalName() + " tem " + a.serviceName()
            + " com " + a.professionalName() + " às " + hora
            + ". Confirma? Se precisar remarcar, é só responder por aqui. 🐾";
    }
}
