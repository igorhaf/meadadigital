package com.meada.profiles.restaurant.reminders;

import com.meada.admin.health.ScheduledJobRunRepository;
import com.meada.profiles.restaurant.reservations.ReservationNotifier;
import com.meada.profiles.restaurant.reservations.ReservationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Lembrete D-1 + auto-transição do restaurante (onda Restaurant 1, backlog #1/#3). Mesa vazia num
 * sábado à noite é faturamento irrecuperável — o lembrete pergunta "confirma? SIM/NÃO" na véspera
 * e a resposta cai no fluxo da IA, que emite a tag {@code <confirmacao_reserva>} (clone do
 * confirmacao_barbearia) movendo a reserva pra confirmada/cancelada; cancelar LIBERA o slot.
 *
 * <p>Mensagem FIXA pela conversa da reserva ({@link ReservationNotifier}) — NÃO passa pela IA.
 * Idempotência por {@code reminded_24h}. Sem canal (reserva manual) → marca sem envio.
 * {@code EVOLUTION_DRY_RUN} honrado por baixo.
 *
 * <p>Auto-transição (#3): confirmada com end_at passado há {@value #GRACE_HOURS}h+ vira
 * 'realizada' via {@link ReservationService#updateStatus} (transição validada; realizada é
 * SILENCIOSA). no_show NÃO é automático (sem check-in no modelo, falta é julgamento humano).
 */
@Component
public class RestaurantReminderJob {

    private static final Logger log = LoggerFactory.getLogger(RestaurantReminderJob.class);

    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");
    private static final int GRACE_HOURS = 2;

    private final RestaurantReminderRepository reminderRepository;
    private final ReservationNotifier notifier;
    private final ReservationService reservationService;
    private final ScheduledJobRunRepository jobRunRepository;

    public RestaurantReminderJob(RestaurantReminderRepository reminderRepository,
                                 ReservationNotifier notifier,
                                 ReservationService reservationService,
                                 ScheduledJobRunRepository jobRunRepository) {
        this.reminderRepository = reminderRepository;
        this.notifier = notifier;
        this.reservationService = reservationService;
        this.jobRunRepository = jobRunRepository;
    }

    /** Tick agendado (cron configurável; default diário às 9h40). Delega aos públicos p/ os testes. */
    @Scheduled(cron = "${restaurant.reminder-cron:0 40 9 * * *}")
    public void scheduledRun() {
        var runId = jobRunRepository.start("RestaurantReminderJob");
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
     * Lembra as reservas de AMANHÃ em todos os tenants restaurant. Público para os testes.
     *
     * @return número de reservas marcadas neste run (com ou sem canal)
     */
    public int runReminders() {
        LocalDate tomorrow = LocalDate.now(TENANT_ZONE).plusDays(1);
        List<DueReservation> due = reminderRepository.findDueReminders(tomorrow);
        int touched = 0;
        for (DueReservation r : due) {
            try {
                if (r.conversationId() == null) {
                    log.info("restaurant-reminder: reserva {} sem canal (manual) — marcada sem envio",
                        r.reservationId());
                } else {
                    // best-effort: falha de envio loga no notifier e NÃO impede a marcação.
                    notifier.notifyStatus(r.companyId(), r.conversationId(), buildText(r));
                }
                reminderRepository.markReminded(r.reservationId());
                touched++;
            } catch (Exception e) {
                log.warn("restaurant-reminder: failed reservation {} ({})", r.reservationId(), e.getMessage());
            }
        }
        return touched;
    }

    /**
     * Auto-transição: confirmada com end_at passado (folga de 2h) → realizada, silencioso.
     * Público para os testes.
     *
     * @return número de reservas concluídas automaticamente
     */
    public int runAutoComplete() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(GRACE_HOURS));
        List<DueReservation> past = reminderRepository.findConfirmedPast(cutoff);
        int touched = 0;
        for (DueReservation r : past) {
            try {
                reservationService.updateStatus(r.companyId(), r.reservationId(), "realizada");
                touched++;
            } catch (RuntimeException e) {
                log.warn("restaurant-auto-complete: failed reservation {} ({})",
                    r.reservationId(), e.getMessage());
            }
        }
        if (touched > 0) {
            log.info("restaurant-auto-complete: {} reservas confirmadas no passado viraram realizada", touched);
        }
        return touched;
    }

    /** Texto fixo; a resposta (SIM/NÃO) cai no fluxo da IA, que emite <confirmacao_reserva>. */
    private static String buildText(DueReservation r) {
        ZonedDateTime z = r.startAt().atZone(TENANT_ZONE);
        return "Olá, " + r.guestName() + "! Sua mesa (" + r.tableLabel() + ") está reservada para "
            + "amanhã às " + HOUR.format(z) + ", " + r.numPeople() + " pessoa(s). Confirma? "
            + "Responda SIM para confirmar ou NÃO para cancelar. 🍽️";
    }
}
