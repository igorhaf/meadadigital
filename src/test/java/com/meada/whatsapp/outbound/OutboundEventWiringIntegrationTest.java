package com.meada.whatsapp.outbound;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.webhook.WebhookService;
import com.meada.whatsapp.webhook.dto.EvolutionWebhookPayload;
import com.meada.whatsapp.webhook.dto.EvolutionWebhookPayload.MessageContent;
import com.meada.whatsapp.webhook.dto.EvolutionWebhookPayload.MessageData;
import com.meada.whatsapp.webhook.dto.EvolutionWebhookPayload.MessageKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Testa o WIRING async da Fase 3.4 — publish do evento no WebhookService + a ponte
 * AFTER_COMMIT → OutboundService.process — de forma DETERMINÍSTICA.
 *
 * <p>O que PROVA:
 * <ul>
 *   <li>o WebhookService publica o {@link MessageInboundProcessedEvent} no ramo
 *       PROCESSED, e NÃO publica em ramos IGNORED_*;
 *   <li>o {@link OutboundEventListener} (anotado AFTER_COMMIT) é acionado após o
 *       commit da transação do webhook e chama {@code process} com o evento certo.
 * </ul>
 *
 * <p>O que NÃO prova (trade aceito — mecânica do Spring, não nossa lógica): que
 * {@code @Async} roda em OUTRA thread (aqui um {@link SyncTaskExecutor} roda síncrono,
 * para o teste ser determinístico); que o MDC propaga; que o AsyncUncaughtExceptionHandler
 * dispara em exceção.
 *
 * <p>O {@code OutboundService} é um {@link SpyBean}: o pipeline real não roda (a IA
 * é fake/ausente), só verificamos que {@code process} foi INVOCADO pela ponte.
 */
@RecordApplicationEvents
// o SyncExecutorConfig substitui o bean 'outboundExecutor' do AsyncConfig (mesmo nome)
// por um síncrono; o Spring Boot proíbe override de bean por padrão, liberado aqui.
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
class OutboundEventWiringIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebhookService webhookService;
    @Autowired
    private ApplicationEvents applicationEvents;

    @SpyBean
    private OutboundService outboundService;

    private static final UUID COMPANY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID INSTANCE = UUID.fromString("a1000000-0000-0000-0000-000000000001");
    private static final String INSTANCE_NAME = "inst-a";

    /**
     * Substitui o outboundExecutor real por um síncrono: a ponte AFTER_COMMIT roda na
     * MESMA thread do commit → asserção determinística (sem esperar thread do pool).
     */
    @TestConfiguration
    static class SyncExecutorConfig {
        @Bean("outboundExecutor")
        @Primary
        TaskExecutor outboundExecutor() {
            return new SyncTaskExecutor();
        }
    }

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug) values (?, ?, ?)",
            COMPANY, "Empresa A", "empresa-a");
        jdbcTemplate.update(
            "insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            INSTANCE, COMPANY, INSTANCE_NAME, "tok-a");
    }

    private EvolutionWebhookPayload validInbound(String remoteJid, String evolutionId, String text) {
        return new EvolutionWebhookPayload(
            "messages.upsert", INSTANCE_NAME,
            new MessageData(
                new MessageKey(evolutionId, remoteJid, false),
                "Cliente A", recentTimestamp(),
                new MessageContent(text, null)),
            null, null, null, null, null);
    }

    @Test
    @DisplayName("PROCESSED: publica o evento e a ponte AFTER_COMMIT chama process")
    void processed_publishesEvent_andListenerInvokesProcess() {
        webhookService.process(validInbound("5511999990001@s.whatsapp.net", "evt-1", "Oi"));

        // 1 evento publicado, com os ids do tenant/conversa.
        assertThat(applicationEvents.stream(MessageInboundProcessedEvent.class).count()).isEqualTo(1);
        MessageInboundProcessedEvent event =
            applicationEvents.stream(MessageInboundProcessedEvent.class).findFirst().orElseThrow();
        assertThat(event.companyId()).isEqualTo(COMPANY);
        assertThat(event.whatsappInstanceId()).isEqualTo(INSTANCE);
        assertThat(event.userMessage()).isEqualTo("Oi");

        // a ponte AFTER_COMMIT (executor síncrono) chamou process com esse evento.
        verify(outboundService).process(event);
    }

    @Test
    @DisplayName("IGNORED (instância desconhecida): não publica evento, process não é chamado")
    void ignored_doesNotPublish_norInvokeProcess() {
        EvolutionWebhookPayload unknown = new EvolutionWebhookPayload(
            "messages.upsert", "instancia-fantasma",
            new MessageData(
                new MessageKey("evt-x", "5511999990001@s.whatsapp.net", false),
                "Cliente", recentTimestamp(),
                new MessageContent("Oi", null)),
            null, null, null, null, null);

        webhookService.process(unknown);

        assertThat(applicationEvents.stream(MessageInboundProcessedEvent.class).count()).isZero();
        verify(outboundService, never()).process(any());
    }
}
