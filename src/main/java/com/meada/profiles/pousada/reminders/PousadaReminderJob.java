package com.meada.profiles.pousada.reminders;

import com.meada.admin.health.ScheduledJobRunRepository;
import com.meada.profiles.pousada.config.PousadaConfig;
import com.meada.profiles.pousada.config.PousadaConfigRepository;
import com.meada.profiles.pousada.reservations.PousadaReservationNotifier;
import com.meada.profiles.pousada.reservations.PousadaReservationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Lembrete de check-in D-1 + auto-transição da pousada (onda Pousada 1, backlog #2/#4). Hóspede
 * que desiste sem avisar deixa o quarto bloqueado sem receita — o lembrete antecipa o cancelamento
 * (liberando o quarto pra revenda) e a resposta cai no fluxo da IA, que emite a tag
 * {@code <confirmacao_pousada>} (clone do restaurant) movendo pra confirmado/cancelado.
 *
 * <p>Mensagem FIXA pela conversa da reserva ({@link PousadaReservationNotifier}) — NÃO passa pela
 * IA. Idempotência por (reserva, check_in_date) — remarcar REARMA. Sem canal (reserva manual) →
 * marca sem envio. {@code EVOLUTION_DRY_RUN} honrado por baixo.
 *
 * <p>Auto-transição (#4, OPT-IN default OFF — marcar no_show sozinho pune o hóspede se a equipe
 * esqueceu de registrar o check-in): confirmado com check-in vencido há 1 dia+ → no_show;
 * checked_in com check-out vencido → checked_out. Ambas silenciosas (máquina + notificação do
 * baseline preservadas).
 */
@Component
public class PousadaReminderJob {

    private static final Logger log = LoggerFactory.getLogger(PousadaReminderJob.class);

    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

    private final PousadaReminderRepository reminderRepository;
    private final PousadaReservationNotifier notifier;
    private final PousadaReservationService reservationService;
    private final PousadaConfigRepository configRepository;
    private final ScheduledJobRunRepository jobRunRepository;

    public PousadaReminderJob(PousadaReminderRepository reminderRepository,
                              PousadaReservationNotifier notifier,
                              PousadaReservationService reservationService,
                              PousadaConfigRepository configRepository,
                              ScheduledJobRunRepository jobRunRepository) {
        this.reminderRepository = reminderRepository;
        this.notifier = notifier;
        this.reservationService = reservationService;
        this.configRepository = configRepository;
        this.jobRunRepository = jobRunRepository;
    }

    /** Tick agendado (cron configurável; default diário às 9h50). Delega aos públicos p/ os testes. */
    @Scheduled(cron = "${pousada.reminder-cron:0 50 9 * * *}")
    public void scheduledRun() {
        var runId = jobRunRepository.start("PousadaReminderJob");
        try {
            runReminders();
            runAutoTransitions();
            jobRunRepository.finishSuccess(runId);
        } catch (RuntimeException e) {
            jobRunRepository.finishFailed(runId, e.getMessage());
            throw e;
        }
    }

    /**
     * Lembra os check-ins de AMANHÃ em todos os tenants pousada. Público para os testes.
     *
     * @return número de reservas marcadas neste run (com ou sem canal)
     */
    public int runReminders() {
        LocalDate tomorrow = LocalDate.now(TENANT_ZONE).plusDays(1);
        List<DuePousadaReservation> due = reminderRepository.findDueReminders(tomorrow);
        int touched = 0;
        for (DuePousadaReservation r : due) {
            try {
                if (r.conversationId() == null) {
                    log.info("pousada-reminder: reserva {} sem canal (manual) — marcada sem envio",
                        r.reservationId());
                } else {
                    PousadaConfig config = configRepository.findByCompany(r.companyId());
                    notifier.notifyStatus(r.companyId(), r.conversationId(), buildText(r, config));
                }
                reminderRepository.markReminded(r.reservationId(), r.checkInDate());
                touched++;
            } catch (Exception e) {
                log.warn("pousada-reminder: failed reservation {} ({})", r.reservationId(), e.getMessage());
            }
        }
        return touched;
    }

    /**
     * Auto-transições opt-in: confirmado→no_show (check-in vencido 1 dia+) e checked_in→checked_out
     * (check-out vencido), ambas silenciosas. Público para os testes.
     *
     * @return número de reservas transicionadas
     */
    public int runAutoTransitions() {
        LocalDate today = LocalDate.now(TENANT_ZONE);
        int touched = 0;
        for (DuePousadaReservation r : reminderRepository.findConfirmedPastCheckin(today)) {
            touched += apply(r, "no_show");
        }
        for (DuePousadaReservation r : reminderRepository.findCheckedInPastCheckout(today)) {
            touched += apply(r, "checked_out");
        }
        if (touched > 0) {
            log.info("pousada-auto-transition: {} reservas atualizadas", touched);
        }
        return touched;
    }

    private int apply(DuePousadaReservation r, String status) {
        try {
            reservationService.updateStatus(r.companyId(), r.reservationId(), status);
            return 1;
        } catch (RuntimeException e) {
            log.warn("pousada-auto-transition: failed reservation {} → {} ({})",
                r.reservationId(), status, e.getMessage());
            return 0;
        }
    }

    /** Texto fixo; a resposta cai no fluxo da IA, que emite <confirmacao_pousada>. */
    private static String buildText(DuePousadaReservation r, PousadaConfig config) {
        return "Olá, " + r.guestName() + "! Sua estadia no quarto " + r.roomName()
            + " começa AMANHÃ — check-in a partir das " + HOUR.format(config.checkInTime())
            + ". Confirma sua chegada? Se precisar cancelar ou ajustar, é só avisar por aqui. 🏡";
    }
}
