package com.meada.access;

import com.meada.admin.security.AdminRole;
import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Logs de acesso (camada 5.24 #92). Dois endpoints com regras de auth distintas:
 *
 * <ul>
 *   <li>POST /api/access-logs — PÚBLICO (sem auth), fora do prefixo /admin/. O login acontece
 *       no FRONTEND via Supabase Auth, então o backend nunca o vê; o frontend chama este
 *       endpoint após uma tentativa (sucesso ou falha) para registrar o evento. Um
 *       login_failed não tem sessão — por isso é público (como o lookup de convite). Para
 *       não virar vetor de abuso, só aceita o enum de ações conhecido e deriva ip/user_agent
 *       do request. É auditoria best-effort, não segurança crítica.
 *   <li>GET /admin/access-logs — TENANT-ADMIN only. Lista os logs da PRÓPRIA empresa
 *       (RLS espelhado no WHERE; o backend opera service_role, fora do RLS).
 * </ul>
 *
 * <p>O backend escreve via service_role (JdbcTemplate). company_id/user_id são resolvidos
 * best-effort pelo email (podem ficar null: login_failed de email desconhecido) — nesses
 * casos o registro existe só para forense global (super-admin), nunca para um tenant.
 */
@RestController
public class AccessLogController {

    private static final Logger log = LoggerFactory.getLogger(AccessLogController.class);

    /** Limite duro da listagem do tenant: os 100 acessos mais recentes. */
    private static final int LIST_LIMIT = 100;

    /** As 3 ações válidas (espelha o CHECK do schema). Qualquer outra → 400. */
    private static final Set<String> VALID_ACTIONS =
        Set.of("login_success", "login_failed", "password_changed");

    private final JdbcTemplate jdbcTemplate;

    public AccessLogController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Registra um evento de autenticação (PÚBLICO, sem auth). Body {action, email}. Valida
     * que action é uma das 3 do enum (else 400 invalid_action). Resolve company_id e user_id
     * best-effort pelo email (podem ser null). Captura ip (1º hop de X-Forwarded-For, senão
     * getRemoteAddr) e User-Agent. Insere e devolve 204. Best-effort: além do check de action,
     * nunca lança por input ruim.
     */
    @PostMapping("/api/access-logs")
    public ResponseEntity<Object> record(@RequestBody Map<String, String> body,
                                         HttpServletRequest request) {
        String action = body == null ? null : body.get("action");
        if (action == null || !VALID_ACTIONS.contains(action)) {
            return ResponseEntity.status(400)
                .body(Map.of("error", "Bad Request", "reason", "invalid_action"));
        }
        String email = body.get("email");

        // Resolução best-effort empresa/usuário pelo email (pode não existir → null).
        UUID companyId = resolveSingleUuid(
            "select company_id from users where email = ? limit 1", email);
        UUID userId = resolveSingleUuid(
            "select id from users where email = ? limit 1", email);

        String ip = resolveIp(request);
        String userAgent = request.getHeader("User-Agent");

        jdbcTemplate.update(
            "insert into access_logs (company_id, user_id, email, action, ip, user_agent) "
                + "values (?, ?, ?, ?, ?, ?)",
            companyId, userId, email, action, ip, userAgent);

        return ResponseEntity.noContent().build();
    }

    /**
     * Lista os logs de acesso da empresa do admin, mais recentes primeiro (cap 100).
     * Isola por company_id (defesa em profundidade — a policy access_logs_select_own
     * também filtra por company_id = app.company_id()).
     */
    @GetMapping("/admin/access-logs")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        if (user.role() != AdminRole.TENANT_ADMIN || user.companyId() == null) {
            return ResponseEntity.status(403)
                .body(Map.of("error", "Forbidden", "reason", "forbidden_not_tenant_admin"));
        }

        List<Map<String, Object>> body = jdbcTemplate.query(
            "select id, email, action, ip, user_agent, created_at from access_logs "
                + "where company_id = ? order by created_at desc limit ?",
            (rs, rowNum) -> {
                java.util.HashMap<String, Object> m = new java.util.HashMap<>();
                m.put("id", ((UUID) rs.getObject("id")).toString());
                m.put("email", rs.getString("email"));
                m.put("action", rs.getString("action"));
                m.put("ip", rs.getString("ip"));
                m.put("userAgent", rs.getString("user_agent"));
                m.put("createdAt", rs.getTimestamp("created_at").toInstant().toString());
                return m;
            },
            user.companyId(), LIST_LIMIT);

        return ResponseEntity.ok(body);
    }

    /**
     * Resolve um único UUID pela query dada com o email como parâmetro. email null/blank ou
     * sem linha → null. Tolerante: o registro do acesso não pode falhar por causa disso.
     */
    private UUID resolveSingleUuid(String sql, String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return jdbcTemplate.query(sql, (rs, rowNum) -> (UUID) rs.getObject(1), email)
            .stream()
            .findFirst()
            .orElse(null);
    }

    /**
     * IP do cliente: 1º hop de X-Forwarded-For (o frontend chega via proxy/nginx), senão
     * getRemoteAddr. X-Forwarded-For é "client, proxy1, proxy2" — o 1º é o cliente real.
     */
    private String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
