package com.meada.profiles.sushi.reactivation;

import com.meada.admin.health.ScheduledJobRunRepository;
import com.meada.profiles.sushi.orders.SushiOrderNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Reativação automática de cliente inativo do sushi (onda 2, backlog #3 — "saudade do sushi").
 * Cliente que pediu e sumiu é receita perdida que já conhece a casa; o job chama de volta no
 * momento certo, com custo marginal zero.
 *
 * <p>Diariamente varre os contatos dos tenants sushi com o OPT-IN ligado
 * ({@code reactivation_enabled}, default DESLIGADO — lição do incidente Baileys: disparo em massa
 * é decisão consciente) cujo último pedido ENTREGUE é anterior à janela, e envia UMA mensagem de
 * reengajamento pela conversa mais recente — mencionando o cupom de retorno configurado só quando
 * ele existe/está ativo/válido. Cooldown por contato = a própria janela ({@code
 * sushi_reactivation_log}); sem canal → marca sem envio. {@code EVOLUTION_DRY_RUN} honrado por
 * baixo do notifier. Mensagem FIXA (não passa pela IA); a resposta do cliente cai no fluxo
 * inbound normal e a IA monta o pedido como sempre.
 */
@Component
public class SushiReactivationJob {

    private static final Logger log = LoggerFactory.getLogger(SushiReactivationJob.class);

    private final SushiReactivationRepository repository;
    private final SushiOrderNotifier notifier;
    private final ScheduledJobRunRepository jobRunRepository;

    public SushiReactivationJob(SushiReactivationRepository repository,
                                SushiOrderNotifier notifier,
                                ScheduledJobRunRepository jobRunRepository) {
        this.repository = repository;
        this.notifier = notifier;
        this.jobRunRepository = jobRunRepository;
    }

    /** Tick agendado (cron configurável; default diário às 10h20). Delega ao público p/ os testes. */
    @Scheduled(cron = "${sushi.reactivation-cron:0 20 10 * * *}")
    public void scheduledRun() {
        var runId = jobRunRepository.start("SushiReactivationJob");
        try {
            runReactivation();
            jobRunRepository.finishSuccess(runId);
        } catch (RuntimeException e) {
            jobRunRepository.finishFailed(runId, e.getMessage());
            throw e;
        }
    }

    /**
     * Reaborda os inativos due de todos os tenants sushi. Público e direto para os testes.
     *
     * @return número de contatos marcados neste run (com ou sem canal)
     */
    public int runReactivation() {
        List<DueInactiveContact> due = repository.findDueContacts();
        int touched = 0;
        for (DueInactiveContact c : due) {
            try {
                if (c.conversationId() == null) {
                    log.info("sushi-reactivation: contato {} sem conversa — marcado sem envio",
                        c.contactId());
                    repository.markSent(c.companyId(), c.contactId(), false);
                } else {
                    // best-effort: falha de envio loga no notifier e NÃO impede a marcação.
                    notifier.notifyStatus(c.companyId(), c.conversationId(), buildText(c));
                    repository.markSent(c.companyId(), c.contactId(), true);
                }
                touched++;
            } catch (Exception e) {
                log.warn("sushi-reactivation: failed contact {} ({})", c.contactId(), e.getMessage());
            }
        }
        return touched;
    }

    /** Texto fixo e leve; o cupom só entra quando validado na varredura. */
    private static String buildText(DueInactiveContact c) {
        String nome = c.contactName() == null || c.contactName().isBlank() ? "" : ", " + c.contactName();
        StringBuilder sb = new StringBuilder("Oi").append(nome)
            .append("! Sentimos sua falta por aqui 🍣 Que tal matar a saudade hoje?");
        if (c.couponCode() != null) {
            sb.append(" Use o cupom ").append(c.couponCode()).append(" no seu próximo pedido.");
        }
        sb.append(" É só chamar por aqui que a gente monta seu pedido!");
        return sb.toString();
    }
}
