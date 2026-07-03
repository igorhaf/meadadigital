package com.meada.profiles.salon.reminders;

import com.meada.admin.health.ScheduledJobRunRepository;
import com.meada.profiles.salon.appointments.SalonAppointmentNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Lembrete de véspera + auto-transição do salão (onda Salon 1, backlog #1/#7). Cadeira vazia por
 * falta não avisada é receita que não volta — o lembrete corta o no-show abrindo janela de
 * remarcação; a resposta do cliente cai no fluxo inbound normal (a IA confirma/remarca pela tag
 * {@code <agendamento>} de sempre).
 *
 * <p>Mensagem FIXA e defensiva pela conversa do agendamento ({@link SalonAppointmentNotifier}) —
 * NÃO passa pela IA (trava do nicho: sem julgamento estético/promessa). Idempotência por
 * (agendamento, start_at) — remarcar REARMA. Sem canal (POST manual) → marca sem envio.
 * {@code EVOLUTION_DRY_RUN} honrado por baixo.
 *
 * <p>Auto-transição (#7, OPT-IN default OFF): confirmado com end_at no passado → realizado
 * (transição válida da máquina; realizado é SILENCIOSO — não notifica). 'agendado' passado não é
 * tocado (falta é julgamento humano). Molde dos jobs: {@code @Scheduled} fino instrumentado via
 * {@link ScheduledJobRunRepository}; lógica em métodos públicos testáveis.
 */
@Component
public class SalonReminderJob {

    private static final Logger log = LoggerFactory.getLogger(SalonReminderJob.class);

    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

    private final SalonReminderRepository reminderRepository;
    private final SalonAppointmentNotifier notifier;
    private final ScheduledJobRunRepository jobRunRepository;

    public SalonReminderJob(SalonReminderRepository reminderRepository,
                            SalonAppointmentNotifier notifier,
                            ScheduledJobRunRepository jobRunRepository) {
        this.reminderRepository = reminderRepository;
        this.notifier = notifier;
        this.jobRunRepository = jobRunRepository;
    }

    /** Tick agendado (cron configurável; default diário às 9h30). Delega aos públicos p/ os testes. */
    @Scheduled(cron = "${salon.reminder-cron:0 30 9 * * *}")
    public void scheduledRun() {
        var runId = jobRunRepository.start("SalonReminderJob");
        try {
            runReminders();
            runAutoComplete();
            jobRunRepository.finishSuccess(runId);
        } catch (RuntimeException e) {
            jobRunRepository.finishFailed(runId, e.getMessage());
            throw e;
        }
    }

    /**
     * Lembra os agendamentos de AMANHÃ em todos os tenants salon. Público para os testes.
     *
     * @return número de agendamentos marcados neste run (com ou sem canal)
     */
    public int runReminders() {
        LocalDate tomorrow = LocalDate.now(TENANT_ZONE).plusDays(1);
        List<DueSalonAppointment> due = reminderRepository.findDueReminders(tomorrow);
        int touched = 0;
        for (DueSalonAppointment a : due) {
            try {
                if (a.conversationId() == null) {
                    log.info("salon-reminder: agendamento {} sem canal (manual) — marcado sem envio",
                        a.appointmentId());
                } else {
                    // best-effort: falha de envio loga no notifier e NÃO impede a marcação.
                    notifier.notifyStatus(a.companyId(), a.conversationId(), buildText(a));
                }
                reminderRepository.markReminded(a.appointmentId(), a.startAt());
                touched++;
            } catch (Exception e) {
                log.warn("salon-reminder: failed appointment {} ({})", a.appointmentId(), e.getMessage());
            }
        }
        return touched;
    }

    /**
     * Auto-transição opt-in: confirmado passado → realizado (silencioso). Público para os testes.
     *
     * @return número de agendamentos concluídos automaticamente
     */
    public int runAutoComplete() {
        int n = reminderRepository.autoCompletePastConfirmed();
        if (n > 0) {
            log.info("salon-auto-complete: {} agendamentos confirmados no passado viraram realizado", n);
        }
        return n;
    }

    /** Texto fixo e defensivo; a resposta cai no fluxo da IA (confirmar/remarcar). */
    private static String buildText(DueSalonAppointment a) {
        String hora = HOUR.format(a.startAt().atZone(TENANT_ZONE));
        return "Oi, " + a.guestName() + "! Lembrete do salão: " + a.serviceName() + " com "
            + a.professionalName() + " amanhã às " + hora
            + ". Podemos confirmar? Se precisar remarcar, é só responder por aqui. 💇";
    }
}
