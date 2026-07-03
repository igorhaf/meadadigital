package com.meada.profiles.barbearia.reminders;

import com.meada.admin.health.ScheduledJobRunRepository;
import com.meada.profiles.barbearia.appointments.BarberAppointmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/**
 * Auto-transição da barbearia (onda 1 do backlog #7): painel limpo sem trabalho manual e métrica de
 * falta/realizado confiável (a fidelidade #3 e o relatório #15 contam 'realizado').
 *
 * <p>Periodicamente (cron horário): (a) agendamento 'confirmado' cujo {@code end_at} passou há mais
 * de {@value #GRACE_HOURS}h vira 'realizado' — via {@code BarberAppointmentService.updateStatus}
 * (transição validada; 'realizado' é SILENCIOSO, ninguém recebe sermão); (b) tickets de fila
 * 'aguardando' de dias ANTERIORES viram 'expirado' (walk-in não atravessa a noite; silencioso).
 * Ambos respeitam o toggle {@code auto_complete_enabled} (default ligado). Agendamento 'agendado'
 * (nunca confirmado) NÃO é tocado — a máquina não permite agendado→falta; fica pro painel decidir.
 */
@Component
public class BarberAutoTransitionJob {

    private static final Logger log = LoggerFactory.getLogger(BarberAutoTransitionJob.class);

    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final int GRACE_HOURS = 2;

    private final BarberReminderRepository reminderRepository;
    private final BarberAppointmentService appointmentService;
    private final ScheduledJobRunRepository jobRunRepository;

    public BarberAutoTransitionJob(BarberReminderRepository reminderRepository,
                                   BarberAppointmentService appointmentService,
                                   ScheduledJobRunRepository jobRunRepository) {
        this.reminderRepository = reminderRepository;
        this.appointmentService = appointmentService;
        this.jobRunRepository = jobRunRepository;
    }

    /** Tick agendado (cron configurável; default de hora em hora, aos 15min). */
    @Scheduled(cron = "${barbearia.auto-transition-cron:0 15 * * * *}")
    public void scheduledRun() {
        var runId = jobRunRepository.start("BarberAutoTransitionJob");
        try {
            runAutoTransitions();
            jobRunRepository.finishSuccess(runId);
        } catch (RuntimeException e) {
            jobRunRepository.finishFailed(runId, e.getMessage());
            throw e;
        }
    }

    /**
     * Aplica as duas transições automáticas. Público e direto para os testes.
     *
     * @return nº de linhas tocadas (agendamentos realizados + tickets expirados)
     */
    public int runAutoTransitions() {
        int touched = 0;

        // (a) confirmado com end_at passado (+folga) → realizado (silencioso).
        List<DueBarberAppointment> past = reminderRepository.findConfirmedPast(
            Instant.now().minus(Duration.ofHours(GRACE_HOURS)));
        for (DueBarberAppointment a : past) {
            try {
                appointmentService.updateStatus(a.companyId(), a.appointmentId(), "realizado");
                touched++;
            } catch (RuntimeException e) {
                log.warn("barbearia-auto: realizado falhou p/ agendamento {} ({})",
                    a.appointmentId(), e.getMessage());
            }
        }

        // (b) tickets 'aguardando' de dias anteriores → expirado (silencioso).
        Instant startOfToday = Instant.now().atZone(TENANT_ZONE).toLocalDate()
            .atStartOfDay(TENANT_ZONE).toInstant();
        int expired = reminderRepository.expireStaleTickets(startOfToday);
        if (expired > 0) {
            log.info("barbearia-auto: {} tickets de fila expirados (dias anteriores)", expired);
        }
        return touched + expired;
    }
}
