package com.meada.whatsapp.outbound;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Executa uma ação com retry e backoff síncrono, para chamadas externas
 * (IA, Evolution) que podem falhar de forma transiente.
 *
 * <p>Escopo apertado (sem classificador customizável de exceção, sem hierarquia
 * comum de transient): retenta apenas exceções que sejam instância de
 * {@code transientType} (passado por quem chama — Ai* ou Evolution*); qualquer
 * outra (fatal) propaga imediato, sem gastar tentativas. Refatora se um dia
 * precisar de predicado customizável (YAGNI).
 *
 * <p><b>Thread.sleep no backoff</b>: roda na thread do pool @Async (Fase 3.4) —
 * pior caso ~4s (1s+3s) de thread presa. Registrado no RISKS.md ("Backoff síncrono
 * via Thread.sleep"). InterruptedException durante o sleep (pool em shutdown) é
 * ABORT, não retry: restaura a flag de interrupção e propaga RuntimeException — não
 * a engole como transient.
 */
@Component
public class RetryRunner {

    /**
     * @param action       a chamada a executar (e retentar em falha transiente)
     * @param maxAttempts  total de tentativas (inicial + retries); >= 1
     * @param backoffs     esperas ENTRE tentativas; size deve ser maxAttempts - 1
     * @param transientType exceções deste tipo (ou subtipo) são retentadas; demais propagam
     * @return o resultado da primeira tentativa bem-sucedida
     * @throws RuntimeException a última exceção transiente (se esgotar) ou a fatal (imediata)
     */
    public <T> T runWithBackoff(Supplier<T> action,
                                int maxAttempts,
                                List<Duration> backoffs,
                                Class<? extends RuntimeException> transientType) {
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(backoffs, "backoffs must not be null");
        Objects.requireNonNull(transientType, "transientType must not be null");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1, got " + maxAttempts);
        }
        if (backoffs.size() != maxAttempts - 1) {
            throw new IllegalArgumentException(
                "backoffs.size() must be maxAttempts - 1 (esperas entre tentativas): expected "
                    + (maxAttempts - 1) + ", got " + backoffs.size());
        }

        RuntimeException lastTransient = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return action.get();
            } catch (RuntimeException e) {
                if (!transientType.isInstance(e)) {
                    throw e;   // fatal — propaga imediato, sem retry
                }
                lastTransient = e;
                if (attempt < maxAttempts) {
                    sleep(backoffs.get(attempt - 1));   // espera ANTES da próxima tentativa
                }
            }
        }
        throw lastTransient;   // esgotou as tentativas transientes
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            // Abort (pool em shutdown), não retry: restaura a flag e propaga.
            Thread.currentThread().interrupt();
            throw new RuntimeException("Retry backoff interrupted", e);
        }
    }
}
