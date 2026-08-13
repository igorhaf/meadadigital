package com.meada.appointments;

import com.meada.admin.health.ScheduledJobRunRepository;
import com.meada.messaging.ContactRepository;
import com.meada.messaging.ConversationRepository;
import com.meada.messaging.EvolutionCredentials;
import com.meada.messaging.WhatsappInstanceRepository;
import com.meada.outbound.EvolutionSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Job de lembretes de agendamento (camada 5.19 #63). Varre periodicamente os agendamentos
 * 'scheduled' futuros próximos que ainda precisam de algum lembrete e, conforme a proximidade,
 * envia o lembrete de 24h ou de 2h pela Evolution, marcando o flag correspondente para não
 * reenviar.
 *
 * <p>Decisão de proximidade (por agendamento):
 * <ul>
 *   <li>faltam ≤ ~2h15m E ¬reminded_2h → manda o lembrete de 2h.
 *   <li>senão, faltam ≤ ~24h15m E ¬reminded_24h → manda o lembrete de 24h.
 * </ul>
 * A folga de 15min absorve a granularidade do tick (o job não roda no minuto exato). O de 2h tem
 * prioridade sobre o de 24h quando ambos estariam pendentes num tick tardio.
 *
 * <p>Envio: resolve telefone (do contato dono da conversa) + credenciais (da instância da
 * conversa) e usa o {@link EvolutionSender}. Quando o agendamento NÃO tem conversa associada
 * (conversation_id null — SET NULL on delete) ou a resolução falha, caímos para LOG + marca o
 * flag mesmo assim (não dá pra enviar sem canal; marcar evita varredura infinita da mesma linha).
 *
 * <p>{@code @Scheduled(fixedDelay)} com intervalo grande por padrão (5min) e atuação só sobre
 * linhas DUE — testes não semeiam linhas due, então o job não dispara efeitos lá. EVOLUTION_DRY_RUN
 * (em dev) é honrado pela própria implementação do EvolutionSender (loga em vez de enviar).
 */
@Component
public class ReminderJob {

    private static final Logger log = LoggerFactory.getLogger(ReminderJob.class);

    // Limiares de proximidade com folga de 15min para o tick não perder a janela.
    private static final Duration WINDOW_2H = Duration.ofHours(2).plusMinutes(15);
    private static final Duration WINDOW_24H = Duration.ofHours(24).plusMinutes(15);
    // Horizonte da varredura: 25h cobre a janela de 24h com folga.
    private static final Duration HORIZON = Duration.ofHours(25);

    private final AppointmentRepository appointmentRepository;
    private final ConversationRepository conversationRepository;
    private final ContactRepository contactRepository;
    private final WhatsappInstanceRepository whatsappInstanceRepository;
    private final EvolutionSender evolutionSender;
    private final ScheduledJobRunRepository jobRunRepository;

    public ReminderJob(AppointmentRepository appointmentRepository,
                       ConversationRepository conversationRepository,
                       ContactRepository contactRepository,
                       WhatsappInstanceRepository whatsappInstanceRepository,
                       EvolutionSender evolutionSender,
                       ScheduledJobRunRepository jobRunRepository) {
        this.appointmentRepository = appointmentRepository;
        this.conversationRepository = conversationRepository;
        this.contactRepository = contactRepository;
        this.whatsappInstanceRepository = whatsappInstanceRepository;
        this.evolutionSender = evolutionSender;
        this.jobRunRepository = jobRunRepository;
    }

    /**
     * Tick do job: busca os agendamentos due e processa cada um. fixedDelay (não fixedRate) para
     * não acumular ticks se um processamento demorar. Intervalo configurável; default 5min.
     */
    @Scheduled(fixedDelayString = "${appointments.reminder-check-ms:300000}")
    public void sendDueReminders() {
        // Registro de execução (camada 6.4): start ao iniciar, finish (success/failed) no try/finally.
        // Instrumentamos AQUI (o método @Scheduled), não no loop interno — nenhum teste chama este
        // método direto, então não polui scheduled_job_runs nos testes.
        UUID runId = jobRunRepository.start("ReminderJob");
        try {
            runDueReminders();
            jobRunRepository.finishSuccess(runId);
        } catch (RuntimeException e) {
            jobRunRepository.finishFailed(runId, e.getMessage());
            throw e;
        }
    }

    /** Lógica do tick (separada do registro de execução). */
    private void runDueReminders() {
        Instant now = Instant.now();
        List<Appointment> due = appointmentRepository.findDueReminders(now, now.plus(HORIZON));
        if (due.isEmpty()) {
            return;
        }
        for (Appointment a : due) {
            try {
                processOne(a, now);
            } catch (Exception e) {
                // Um agendamento problemático não pode derrubar o tick inteiro.
                log.warn("reminder: failed to process appointment {} ({})", a.id(), e.getMessage());
            }
        }
    }

    /** Decide qual lembrete (2h tem prioridade) e dispara, marcando o flag. */
    private void processOne(Appointment a, Instant now) {
        Duration timeUntil = Duration.between(now, a.scheduledAt());
        if (timeUntil.isNegative()) {
            return;   // já passou (a query filtra > now, mas defensivo contra clock skew)
        }
        if (!a.reminded2h() && timeUntil.compareTo(WINDOW_2H) <= 0) {
            sendAndMark(a, "2h", "Lembrete: seu agendamento é daqui a aproximadamente 2 horas.");
        } else if (!a.reminded24h() && timeUntil.compareTo(WINDOW_24H) <= 0) {
            sendAndMark(a, "24h", "Lembrete: você tem um agendamento amanhã. Até lá!");
        }
        // senão: nada due ainda neste tick (ex.: falta mais de 24h15m).
    }

    /**
     * Resolve o canal (telefone + credenciais via a conversa) e envia o lembrete; depois marca o
     * flag. Sem conversa associada ou canal irresolúvel → log + marca o flag mesmo assim (evita
     * revarredura eterna; o lembrete fica registrado como "tentado").
     */
    private void sendAndMark(Appointment a, String which, String text) {
        UUID conversationId = a.conversationId();
        if (conversationId == null) {
            log.info("reminder: appointment {} sem conversa associada — lembrete {} marcado sem envio",
                a.id(), which);
            appointmentRepository.markReminded(a.id(), which);
            return;
        }
        Optional<String> phone = contactRepository.findPhoneByConversationId(conversationId);
        Optional<UUID> instanceId = conversationRepository.findInstanceIdByConversation(conversationId);
        Optional<EvolutionCredentials> creds = instanceId.isPresent()
            ? whatsappInstanceRepository.findEvolutionCredentials(instanceId.get())
            : Optional.empty();
        if (phone.isEmpty() || phone.get().isBlank() || creds.isEmpty()) {
            log.info("reminder: appointment {} sem canal resolúvel (phone/creds) — lembrete {} "
                + "marcado sem envio", a.id(), which);
            appointmentRepository.markReminded(a.id(), which);
            return;
        }
        try {
            evolutionSender.sendText(
                creds.get().instanceName(), creds.get().token(), phone.get(), text);
            log.info("reminder: sent {} reminder for appointment {}", which, a.id());
        } catch (RuntimeException e) {
            // Falha de envio (transient/fatal): logamos e marcamos assim mesmo — o job é
            // best-effort de lembrete, não vale retentar indefinidamente o mesmo agendamento.
            log.warn("reminder: send failed for appointment {} ({}) — marking anyway",
                a.id(), e.getMessage());
        }
        appointmentRepository.markReminded(a.id(), which);
    }
}
