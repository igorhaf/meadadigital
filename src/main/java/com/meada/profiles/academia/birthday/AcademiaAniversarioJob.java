package com.meada.profiles.academia.birthday;

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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Saudação de aniversário da Academia (backlog docs/FEATURES_SUGERIDAS_ACADEMIA.md #14).
 *
 * <p>Diariamente (cron configurável, default 08h), para cada tenant academia, encontra os contatos
 * cujo dia/mês de nascimento é HOJE e que ainda NÃO foram saudados neste ano, envia uma mensagem
 * calorosa pelo WhatsApp e MARCA o ano — a marcação garante a idempotência (uma saudação por ano de
 * vida, mesmo que o job rode várias vezes no dia).
 *
 * <p>Segue o MOLDE do {@code AcademiaInadimplenciaJob}: método {@code @Scheduled} fino que só
 * instrumenta via {@link ScheduledJobRunRepository}; a lógica real fica em {@link #runBirthdayGreetings()}
 * público, chamado direto pelos testes (sem depender do scheduler). O envio passa pelo
 * {@link EvolutionSender}, que HONRA {@code EVOLUTION_DRY_RUN} em dev (lição do incidente Baileys).
 *
 * <p>TRAVA da academia: a mensagem é um voto de felicidades — NUNCA prescreve treino/dieta/avaliação
 * física e NÃO promete resultado corporal.
 */
@Component
public class AcademiaAniversarioJob {

    private static final Logger log = LoggerFactory.getLogger(AcademiaAniversarioJob.class);

    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");

    private final AcademiaAniversarioRepository aniversarioRepository;
    private final ConversationRepository conversationRepository;
    private final ContactRepository contactRepository;
    private final WhatsappInstanceRepository whatsappInstanceRepository;
    private final EvolutionSender evolutionSender;
    private final ScheduledJobRunRepository jobRunRepository;

    public AcademiaAniversarioJob(AcademiaAniversarioRepository aniversarioRepository,
                                  ConversationRepository conversationRepository,
                                  ContactRepository contactRepository,
                                  WhatsappInstanceRepository whatsappInstanceRepository,
                                  EvolutionSender evolutionSender,
                                  ScheduledJobRunRepository jobRunRepository) {
        this.aniversarioRepository = aniversarioRepository;
        this.conversationRepository = conversationRepository;
        this.contactRepository = contactRepository;
        this.whatsappInstanceRepository = whatsappInstanceRepository;
        this.evolutionSender = evolutionSender;
        this.jobRunRepository = jobRunRepository;
    }

    /** Tick agendado (cron configurável; default diário às 08h). Delega ao método público para os testes. */
    @Scheduled(cron = "${academia.birthday-cron:0 0 8 * * *}")
    public void scheduledRun() {
        UUID runId = jobRunRepository.start("AcademiaAniversarioJob");
        try {
            runBirthdayGreetings();
            jobRunRepository.finishSuccess(runId);
        } catch (RuntimeException e) {
            jobRunRepository.finishFailed(runId, e.getMessage());
            throw e;
        }
    }

    /**
     * Varre todos os tenants academia e saúda os aniversariantes de HOJE ainda não saudados neste ano.
     * Público e direto para os testes exercitarem a lógica sem o scheduler.
     *
     * @return número de contatos saudados neste run
     */
    public int runBirthdayGreetings() {
        LocalDate today = LocalDate.now(TENANT_ZONE);
        List<UUID> companies = aniversarioRepository.findAcademiaCompanies();
        int total = 0;
        for (UUID companyId : companies) {
            try {
                total += processCompany(companyId, today);
            } catch (Exception e) {
                log.warn("academia-birthday: failed for company {} ({})", companyId, e.getMessage());
            }
        }
        return total;
    }

    /** Saúda os aniversariantes de UM tenant. Retorna quantos foram saudados. */
    private int processCompany(UUID companyId, LocalDate today) {
        List<BirthdayContact> contacts = aniversarioRepository.findBirthdayContacts(
            companyId, today.getMonthValue(), today.getDayOfMonth(), today.getYear());
        int greeted = 0;
        for (BirthdayContact c : contacts) {
            try {
                sendAndMark(c, today.getYear());
                greeted++;
            } catch (Exception e) {
                log.warn("academia-birthday: failed contact {} ({})", c.contactId(), e.getMessage());
            }
        }
        return greeted;
    }

    private String buildGreetingText(BirthdayContact c) {
        String nome = (c.name() != null && !c.name().isBlank()) ? c.name() : "tudo de bom";
        return "Feliz aniversário, " + nome + "! 🎉 Toda a equipe da academia deseja um dia muito especial "
            + "para você. Que seja um ano cheio de saúde, alegria e boas energias. Conte com a gente! 🎂";
    }

    /**
     * Resolve o canal (telefone direto + credenciais via a conversa mais recente) e envia a saudação;
     * depois marca o ano. Canal irresolúvel → log + marca mesmo assim (evita revarredura eterna no
     * mesmo dia — igual ao AcademiaInadimplenciaJob). EVOLUTION_DRY_RUN é honrado pelo EvolutionSender.
     */
    private void sendAndMark(BirthdayContact c, int year) {
        String phone = c.phone();
        Optional<EvolutionCredentials> creds = Optional.empty();
        UUID conversationId = c.conversationId();
        if (conversationId != null) {
            // telefone: preferir o do contato (canal direto); fallback pela conversa se vier vazio.
            if (phone == null || phone.isBlank()) {
                phone = contactRepository.findPhoneByConversationId(conversationId).orElse(null);
            }
            Optional<UUID> instanceId = conversationRepository.findInstanceIdByConversation(conversationId);
            creds = instanceId.isPresent()
                ? whatsappInstanceRepository.findEvolutionCredentials(instanceId.get())
                : Optional.empty();
        }
        if (phone == null || phone.isBlank() || creds.isEmpty()) {
            log.info("academia-birthday: contact {} sem canal resolúvel — saudação marcada sem envio",
                c.contactId());
            aniversarioRepository.markGreeted(c.contactId(), year);
            return;
        }
        try {
            evolutionSender.sendText(creds.get().instanceName(), creds.get().token(), phone, buildGreetingText(c));
            log.info("academia-birthday: sent birthday greeting for contact {}", c.contactId());
        } catch (RuntimeException e) {
            log.warn("academia-birthday: send failed for contact {} ({}) — marking anyway",
                c.contactId(), e.getMessage());
        }
        aniversarioRepository.markGreeted(c.contactId(), year);
    }
}
