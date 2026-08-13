package com.meada.outbound;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Configuração de tudo que é assíncrono na camada outbound (Fase 3.4): o executor
 * dedicado, o {@link AsyncUncaughtExceptionHandler} e o {@code @EnableAsync}.
 *
 * <p>O processamento outbound roda fora da thread do webhook (via
 * {@code @TransactionalEventListener(AFTER_COMMIT)} + {@code @Async("outboundExecutor")}
 * no {@link OutboundEventListener}), para que a resposta HTTP ao Evolution não espere
 * a IA + envio. O executor é dimensionado contra o Thread.sleep do retry (ver
 * RISKS.md "Backoff síncrono via Thread.sleep").
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    /**
     * Pool dedicado do processamento outbound. Dimensionado conservador para o MVP:
     * cada tarefa pode prender a thread até ~4s (pior caso do retry: backoffs 1s+3s
     * de Thread.sleep + chamadas de rede). core=4 dá vazão de regime; max=8 absorve
     * picos; queue=100 enfileira rajadas. {@link ThreadPoolExecutor.CallerRunsPolicy}
     * na saturação: a thread que publicou (AFTER_COMMIT, já commitada) roda a tarefa
     * — degrada com lentidão em vez de DESCARTAR mensagem. waitForTasksToComplete +
     * awaitTermination casam com o {@code server.shutdown: graceful} do yaml (não
     * corta uma resposta no meio no shutdown).
     */
    @Bean("outboundExecutor")
    public TaskExecutor outboundExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("outbound-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * Rede para exceções que escapam do {@code @Async void} (não há Future para
     * propagá-las). A matriz do OutboundService nunca lança (retorna outcome); este
     * handler pega o INESPERADO — PromptBuilder lançando por config ausente (decisão
     * da 3.3: propagar, não virar humano silencioso), bugs.
     *
     * <p>Loga estruturado (key=value) com os ids do evento, extraídos de
     * {@code params[0]} com GUARD: se o handler um dia receber outro tipo de tarefa
     * async, loga método + stack sem campos estruturados em vez de quebrar.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (Throwable ex, Method method, Object... params) -> {
            if (params.length > 0 && params[0] instanceof MessageInboundProcessedEvent event) {
                log.error("outbound async failed company_id={} conversation_id={}",
                    event.companyId(), event.conversationId(), ex);
            } else {
                log.error("async failed method={} (no structured event in params)",
                    method.getName(), ex);
            }
        };
    }
}
