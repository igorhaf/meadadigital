package com.meada.webhook;

import com.meada.admin.health.WebhookHeartbeatRepository;
import com.meada.webhook.dto.EvolutionWebhookPayload;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint do webhook da Evolution API.
 *
 * <p>Valida a estrutura via Bean Validation (@Valid + @RequestBody) e delega ao
 * {@link WebhookService}. Todos os desfechos do service mapeiam para HTTP 200 —
 * a distinção (processado / duplicata / ignorado / instância desconhecida) vive
 * no LOG estruturado emitido pelo service, não no status. Razão: a Evolution só
 * precisa do 2xx para parar de reentregar; um 4xx faria reentrega inútil de
 * eventos legítimos (presence, grupo, etc.).
 *
 * <p>O único caminho para 400 é payload estruturalmente inválido (viola
 * @NotBlank/@NotNull dos DTOs) — barrado pelo @Valid ANTES de chegar ao service,
 * caindo no handler default do Spring. (Handler customizado é etapa posterior.)
 *
 * <p>Autenticação de origem (secret) é feita ANTES, pelo WebhookSecretFilter —
 * request sem secret válido nem chega aqui (401 no filtro).
 */
@RestController
public class EvolutionWebhookController {

    private static final Logger log = LoggerFactory.getLogger(EvolutionWebhookController.class);

    private final WebhookService webhookService;
    private final WebhookHeartbeatRepository heartbeatRepository;

    public EvolutionWebhookController(WebhookService webhookService,
                                      WebhookHeartbeatRepository heartbeatRepository) {
        this.webhookService = webhookService;
        this.heartbeatRepository = heartbeatRepository;
    }

    @PostMapping("/webhooks/evolution")
    public ResponseEntity<Void> receive(@Valid @RequestBody EvolutionWebhookPayload payload) {
        // Batimento de saúde (camada 6.4): registra que um evento chegou ANTES de processar.
        // Best-effort — try/catch silencioso para NUNCA bloquear o webhook (a Evolution só
        // precisa do 2xx), MAS log.warn no catch para um bug futuro de gravação não passar
        // despercebido. event_type='received' (não dependemos do shape do payload aqui).
        try {
            heartbeatRepository.record(null, "received");
        } catch (RuntimeException e) {
            log.warn("failed to record heartbeat: {}", e.getMessage());
        }
        webhookService.process(payload);
        return ResponseEntity.ok().build();
    }
}
