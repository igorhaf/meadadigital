package com.meada.training;

import com.meada.admin.security.AdminRole;
import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Feedback de respostas da IA — modo treinamento (camada 5.25 #57). TENANT-ADMIN ONLY: o admin
 * avalia as respostas da PRÓPRIA empresa. Sob {@code /admin/**} (o JwtAuthenticationFilter
 * autentica e popula authenticatedUser).
 *
 * <p>Autorização por role no método (padrão da camada 4, igual SavedReplyController): super-admin
 * não tem company (companyId null) → 403 forbidden_not_tenant_admin. Isolamento por empresa vem do
 * companyId do próprio authenticatedUser — nunca de input do cliente.
 *
 * <ul>
 *   <li>POST /admin/message-feedback {messageId, rating, correction?} → upsert (201 cria, 200 atualiza).
 *   <li>GET  /admin/message-feedback?rating=bad → lista (50 mais recentes) com o conteúdo da mensagem.
 * </ul>
 */
@RestController
public class FeedbackController {

    private static final Set<String> VALID_RATINGS = Set.of("good", "bad");

    private final FeedbackRepository repository;

    public FeedbackController(FeedbackRepository repository) {
        this.repository = repository;
    }

    /**
     * Registra (ou atualiza) o feedback de uma mensagem da IA. Body {messageId, rating, correction?}.
     * 201 se criou o feedback, 200 se atualizou um já existente (UNIQUE por message_id).
     *
     * <p>Validações: rating ∈ {good, bad} e messageId UUID válido → senão 400 invalid_request.
     * messageId inexistente/de outra empresa viola a FK → 404 message_not_found (a FK + o RLS de
     * messages garantem que o tenant só referencia mensagem da própria empresa).
     */
    @PostMapping("/admin/message-feedback")
    public ResponseEntity<Object> submit(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestBody Map<String, String> request) {
        if (user.role() != AdminRole.TENANT_ADMIN || user.companyId() == null) {
            return forbidden();
        }

        String messageIdRaw = request.get("messageId");
        String rating = request.get("rating");
        String correction = request.get("correction");   // nullable

        if (messageIdRaw == null || rating == null || !VALID_RATINGS.contains(rating)) {
            return badRequest();
        }
        UUID messageId;
        try {
            messageId = UUID.fromString(messageIdRaw);
        } catch (IllegalArgumentException e) {
            return badRequest();
        }

        FeedbackRepository.UpsertResult result = repository.upsert(
            user.companyId(), messageId, rating, correction, user.userId());
        return switch (result) {
            // 201 cria, 200 atualiza (UNIQUE por message_id).
            case INSERTED -> ResponseEntity.status(201)
                .body(Map.of("messageId", messageId.toString(), "rating", rating));
            case UPDATED -> ResponseEntity.status(200)
                .body(Map.of("messageId", messageId.toString(), "rating", rating));
            // Mensagem inexistente ou de outra empresa (o repo guarda o INSERT por company_id). Não
            // vaza se a mensagem é de outro tenant — o tenant só conhece as suas pelo painel.
            case MESSAGE_NOT_FOUND -> ResponseEntity.status(404)
                .body(Map.of("error", "Not Found", "reason", "message_not_found"));
        };
    }

    /**
     * Lista o feedback da empresa (50 mais recentes), com o conteúdo da mensagem juntado — view de
     * "revisar respostas". Query param {@code rating} opcional ('good'|'bad') filtra; ausente/
     * inválido → traz todos (não falha: a tela passa rating=bad para revisar as ruins).
     */
    @GetMapping("/admin/message-feedback")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) String rating) {
        if (user.role() != AdminRole.TENANT_ADMIN || user.companyId() == null) {
            return forbidden();
        }
        // rating fora do domínio vira null (sem filtro) — defensivo, não erro.
        String ratingFilter = VALID_RATINGS.contains(rating) ? rating : null;
        List<Map<String, Object>> body = repository.list(user.companyId(), ratingFilter);
        return ResponseEntity.ok(body);
    }

    private ResponseEntity<Object> forbidden() {
        return ResponseEntity.status(403)
            .body(Map.of("error", "Forbidden", "reason", "forbidden_not_tenant_admin"));
    }

    private ResponseEntity<Object> badRequest() {
        return ResponseEntity.status(400)
            .body(Map.of("error", "Bad Request", "reason", "invalid_request"));
    }
}
