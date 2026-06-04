package com.meada.whatsapp.outbound;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test puro do {@link RetryRunner} — sem Spring. Usa exceções de teste
 * próprias (TestTransient / TestFatal) para não acoplar às exceções de produção.
 */
class RetryRunnerTest {

    private final RetryRunner runner = new RetryRunner();

    // backoffs curtos para o teste não demorar (1ms entre tentativas).
    private static final List<Duration> FAST_BACKOFFS = List.of(Duration.ofMillis(1), Duration.ofMillis(1));

    static class TestTransient extends RuntimeException {
        TestTransient(String m) { super(m); }
    }

    static class TestFatal extends RuntimeException {
        TestFatal(String m) { super(m); }
    }

    @Test
    @DisplayName("sucesso na 1ª tentativa: não retenta, retorna o valor")
    void successFirstAttempt() {
        AtomicInteger calls = new AtomicInteger();
        String result = runner.runWithBackoff(() -> {
            calls.incrementAndGet();
            return "ok";
        }, 3, FAST_BACKOFFS, TestTransient.class);

        assertThat(result).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(1);   // sem retry
    }

    @Test
    @DisplayName("sucesso após N falhas transientes: retenta até passar")
    void successAfterTransientFailures() {
        AtomicInteger calls = new AtomicInteger();
        String result = runner.runWithBackoff(() -> {
            int n = calls.incrementAndGet();
            if (n < 3) {
                throw new TestTransient("falha " + n);
            }
            return "ok na 3a";
        }, 3, FAST_BACKOFFS, TestTransient.class);

        assertThat(result).isEqualTo("ok na 3a");
        assertThat(calls.get()).isEqualTo(3);   // 1 inicial + 2 retries
    }

    @Test
    @DisplayName("esgota tentativas: propaga a última transiente")
    void exhausted_propagatesLastTransient() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> runner.runWithBackoff(() -> {
            calls.incrementAndGet();
            throw new TestTransient("sempre falha");
        }, 3, FAST_BACKOFFS, TestTransient.class))
            .isInstanceOf(TestTransient.class)
            .hasMessage("sempre falha");

        assertThat(calls.get()).isEqualTo(3);   // tentou as 3
    }

    @Test
    @DisplayName("exceção fatal (não-transiente): propaga IMEDIATO, sem retry")
    void fatal_propagatesImmediately() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> runner.runWithBackoff(() -> {
            calls.incrementAndGet();
            throw new TestFatal("fatal");
        }, 3, FAST_BACKOFFS, TestTransient.class))
            .isInstanceOf(TestFatal.class);

        assertThat(calls.get()).isEqualTo(1);   // não retentou
    }

    @Test
    @DisplayName("backoff respeita as Durations entre tentativas (tolerância)")
    void backoffRespectsDurations() {
        // 3 tentativas, backoffs 50ms + 50ms = ~100ms mínimo de espera total
        // (a ação falha transiente sempre → espera entre 1-2 e 2-3).
        List<Duration> backoffs = List.of(Duration.ofMillis(50), Duration.ofMillis(50));
        long start = System.nanoTime();

        assertThatThrownBy(() -> runner.runWithBackoff(() -> {
            throw new TestTransient("x");
        }, 3, backoffs, TestTransient.class))
            .isInstanceOf(TestTransient.class);

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        // esperou pelo menos os 2 backoffs (100ms); tolerância generosa p/ não flaky.
        assertThat(elapsedMs).isGreaterThanOrEqualTo(90);
    }

    @Test
    @DisplayName("backoffs.size() != maxAttempts-1: IllegalArgumentException")
    void inconsistentBackoffs_throws() {
        assertThatThrownBy(() -> runner.runWithBackoff(
            () -> "x", 3, List.of(Duration.ofMillis(1)), TestTransient.class))   // só 1 backoff p/ 3 tentativas
            .isInstanceOf(IllegalArgumentException.class);
    }
}
