package com.meada.whatsapp.outbound;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Config do retry do {@link OutboundService} (IA + envio), ligada ao bloco
 * {@code outbound.retry} do application.yml.
 *
 * <p>{@code @ConfigurationProperties} (não {@code @Value}) porque {@code backoffMs} é
 * uma LISTA: o binder estruturado do Spring agrega {@code backoff-ms[0]}, {@code [1]}
 * (a forma como o yaml expõe uma lista) — coisa que {@code @Value("${...}")} não faz.
 *
 * @param maxAttempts total de tentativas (inicial + retries); o RetryRunner exige >= 1
 * @param backoffMs   esperas em millis ENTRE tentativas; size deve ser maxAttempts - 1
 *                    (validado pelo RetryRunner em cada chamada). Convertido para
 *                    {@code List<Duration>} no construtor do OutboundService.
 */
@ConfigurationProperties(prefix = "outbound.retry")
public record OutboundRetryProperties(int maxAttempts, List<Long> backoffMs) {
}
