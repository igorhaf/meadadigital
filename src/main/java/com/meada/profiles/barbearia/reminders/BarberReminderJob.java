package com.meada.profiles.barbearia.reminders;

import com.meada.admin.health.ScheduledJobRunRepository;
import com.meada.profiles.barbearia.appointments.BarberAppointmentNotifier;
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
 * Lembrete de confirmação da barbearia (onda 1 do backlog docs/FEATURES_SUGERIDAS_BARBEARIA.md #1).
 * O no-show é o maior ralo de receita do nicho — cadeira vazia não se revende sozinha.
 *
 * <p>Periodicamente (fixedDelay), varre os agendamentos 'agendado' que começam nas PRÓXIMAS 24h,
 * ainda não lembrados, de tenants com o lembrete LIGADO, e envia pela conversa: "Podemos confirmar
 * seu horário ...? Responda SIM para confirmar ou CANCELAR para desmarcar." A RESPOSTA do cliente é
 * capturada pela IA via a tag {@code <confirmacao_barbearia>} ({@code ConfirmacaoBarbeariaHandler})
 * — a IA só REFLETE a decisão; quem muda o status é a máquina já existente (cancelar libera o slot
 * na hora). Texto FIXO (não passa pela IA).
 *
 * <p>Idempotência: {@code reminded_24h} marca o envio (inclusive sem canal resolúvel — agendamento
 * manual sem conversa não revarre). {@code EVOLUTION_DRY_RUN} honrado pelo EvolutionSender por baixo
 * do notifier (lição Baileys). Molde dos jobs existentes: {@code @Scheduled} fino instrumentado por
 * {@link ScheduledJobRunRepository} + lógica pública testável.
 */
@Component
public class BarberReminderJob {

    private static final Logger log = LoggerFactory.getLogger(BarberReminderJob.class);

    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final Duration WINDOW = Duration.ofHours(24);

    private final BarberReminderRepository reminderRepository;
    private final BarberAppointmentNotifier notifier;
    private final ScheduledJobRunRepository jobRunRepository;

    public BarberReminderJob(BarberReminderRepository reminderRepository,
                             BarberAppointmentNotifier notifier,
                             ScheduledJobRunRepository jobRunRepository) {
        this.reminderRepository = reminderRepository;
        this.notifier = notifier;
        this.jobRunRepository = jobRunRepository;
    }

    /** Tick do job (fixedDelay; default 5min). Delega ao método público para os testes. */
    @Scheduled(fixedDelayString = "${barbearia.reminder-check-ms:300000}")
    public void scheduledRun() {
        var runId = jobRunRepository.start("BarberReminderJob");
        try {
            runReminders();
            jobRunRepository.finishSuccess(runId);
        } catch (RuntimeException e) {
            jobRunRepository.finishFailed(runId, e.getMessage());
            throw e;
        }
    }

    /**
     * Lembra os agendamentos das próximas 24h em todos os tenants barbearia. Público e direto para
     * os testes exercitarem a lógica sem o scheduler.
     *
     * @return número de agendamentos marcados como lembrados neste run (com ou sem canal)
     */
    public int runReminders() {
        Instant now = Instant.now();
        List<DueBarberAppointment> due = reminderRepository.findDueReminders(now, now.plus(WINDOW));
        int touched = 0;
        for (DueBarberAppointment a : due) {
            try {
                if (a.conversationId() == null) {
                    log.info("barbearia-reminder: agendamento {} sem canal (manual) — marcado sem envio",
                        a.appointmentId());
                } else {
                    // best-effort: falha de envio loga no notifier e NÃO impede a marcação.
                    notifier.notifyStatus(a.companyId(), a.conversationId(), buildText(a));
                }
                reminderRepository.markReminded(a.appointmentId());
                touched++;
            } catch (Exception e) {
                log.warn("barbearia-reminder: failed appointment {} ({})", a.appointmentId(), e.getMessage());
            }
        }
        return touched;
    }

    /** Texto fixo e defensivo; a resposta (SIM/CANCELAR) é capturada pela IA via tag. */
    private static String buildText(DueBarberAppointment a) {
        ZonedDateTime z = a.startAt().atZone(TENANT_ZONE);
        return "Oi, " + a.guestName() + "! Podemos confirmar seu horário: " + a.serviceName()
            + " " + DATE_FMT.format(z) + " às " + TIME_FMT.format(z) + " com " + a.barberName()
            + "? Responda SIM para confirmar ou CANCELAR para desmarcar. ✂️";
    }
}
