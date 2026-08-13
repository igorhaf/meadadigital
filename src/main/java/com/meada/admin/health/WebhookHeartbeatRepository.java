package com.meada.admin.health;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Escrita de batimentos do webhook da Evolution (camada 6.4). 1 row por evento recebido —
 * sinal de que o canal inbound está vivo. Webhook está OFF no MVP (dry-run), então a tabela
 * fica vazia até religar; a tela de saúde mostra "sem heartbeat".
 *
 * <p>O insert é best-effort: o {@link com.meada.webhook.EvolutionWebhookController}
 * captura qualquer falha (log.warn, não relança) para NUNCA bloquear o processamento do webhook.
 */
@Repository
public class WebhookHeartbeatRepository {

    private final JdbcTemplate jdbcTemplate;

    public WebhookHeartbeatRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Registra um batimento. instanceId nullable (nem todo payload resolve instância). */
    public void record(UUID instanceId, String eventType) {
        jdbcTemplate.update(
            "insert into webhook_heartbeats (instance_id, event_type) values (?, ?)",
            instanceId, eventType);
    }
}
