package com.meada.whatsapp.webchat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Endpoint PÚBLICO do widget de chat web (camada 5.25 #73), sob {@code /api/chat/**} — fora
 * do prefixo {@code /admin/}, então o JwtAuthenticationFilter NÃO o filtra (espelha o
 * PublicInvitationController). Sem auth: é o atendimento da IA a um visitante anônimo do site
 * do tenant.
 *
 * <p><b>CORS permissivo ({@code @CrossOrigin("*")}):</b> o widget é embutido em sites
 * ARBITRÁRIOS dos tenants (qualquer origem). É leitura/escrita de um chat público (sem
 * cookie/credencial), então liberar origem é seguro e necessário — o navegador do visitante
 * faz o POST de um domínio que não conhecemos de antemão.
 *
 * <p><b>Defensivo:</b> erro de IA NÃO vira 500 (o WebChatService devolve um fallback educado
 * com 200). Slug desconhecido/inativo → 404. Empresa sem instância (FK NOT NULL) → 409.
 */
@RestController
@CrossOrigin(origins = "*")
public class WebChatController {

    private static final Logger log = LoggerFactory.getLogger(WebChatController.class);

    private final WebChatRepository webChatRepository;
    private final WebChatService webChatService;

    public WebChatController(WebChatRepository webChatRepository, WebChatService webChatService) {
        this.webChatRepository = webChatRepository;
        this.webChatService = webChatService;
    }

    /**
     * Recebe uma mensagem do widget e devolve a resposta da IA.
     *
     * <p>Body {@code {sessionId, message}}. Resolve a empresa pelo slug (404 se desconhecida/
     * inativa), processa via {@link WebChatService} e responde {@code {reply}}. Validação
     * mínima (sessionId/message não-blank) → 400 {@code invalid_request}.
     */
    @PostMapping("/api/chat/{companySlug}")
    public ResponseEntity<Object> chat(
            @PathVariable String companySlug,
            @RequestBody Map<String, String> body) {
        String sessionId = body == null ? null : body.get("sessionId");
        String message = body == null ? null : body.get("message");

        if (isBlank(sessionId) || isBlank(message)) {
            return ResponseEntity.status(400)
                .body(Map.of("error", "Bad Request", "reason", "invalid_request"));
        }

        Optional<UUID> companyId = webChatRepository.findActiveCompanyIdBySlug(companySlug);
        if (companyId.isEmpty()) {
            return ResponseEntity.status(404)
                .body(Map.of("error", "Not Found", "reason", "company_not_found"));
        }

        try {
            String reply = webChatService.handle(companyId.get(), sessionId.strip(), message.strip());
            return ResponseEntity.ok(Map.of("reply", reply));
        } catch (WebChatNoInstanceException e) {
            // Empresa sem whatsapp_instance: não dá para abrir a conversa web (FK NOT NULL).
            log.warn("webchat: company {} sem instância para o canal web", companyId.get());
            return ResponseEntity.status(409)
                .body(Map.of("error", "Conflict", "reason", "company_not_provisioned"));
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
