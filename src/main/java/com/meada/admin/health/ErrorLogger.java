package com.meada.admin.health;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

/**
 * Gravação de erros em pontos CRAVADOS (camada 6.4). NÃO é um catch-all global — só os catches
 * instrumentados explicitamente (envio Evolution fatal, falha fatal da IA) chamam {@link #log}.
 *
 * <p>É DEFENSIVO por contrato: a própria gravação do erro nunca pode lançar (senão mascararia ou
 * agravaria o erro original). Qualquer falha de persistência cai num log.warn e segue. O
 * stack_trace é serializado completo; o context (Map livre) vira jsonb.
 */
@Component
public class ErrorLogger {

    private static final Logger log = LoggerFactory.getLogger(ErrorLogger.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ErrorLogger(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Registra um erro. source identifica o ponto (ex.: "OutboundService", "GeminiProvider");
     * t é a exceção (message + stack_trace); context é metadata livre (ex.: conversationId) ou null.
     *
     * <p>Best-effort: NUNCA lança — o caller já está num catch e não pode quebrar por causa do log.
     */
    public void log(String source, Throwable t, Map<String, Object> context) {
        try {
            String message = t != null ? t.getMessage() : null;
            if (message == null) {
                message = t != null ? t.getClass().getSimpleName() : "unknown";
            }
            String stack = stackTraceOf(t);
            String contextJson = (context == null || context.isEmpty())
                ? null : objectMapper.writeValueAsString(context);
            jdbcTemplate.update(
                "insert into error_log (source, message, stack_trace, context) values (?, ?, ?, ?::jsonb)",
                source, message, stack, contextJson);
        } catch (Exception e) {
            // O log do erro não pode, ele mesmo, derrubar o fluxo de tratamento do erro original.
            log.warn("failed to persist error_log entry from {}: {}", source, e.getMessage());
        }
    }

    private static String stackTraceOf(Throwable t) {
        if (t == null) {
            return null;
        }
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        String s = sw.toString();
        return s.substring(0, Math.min(s.length(), 8000));
    }
}
