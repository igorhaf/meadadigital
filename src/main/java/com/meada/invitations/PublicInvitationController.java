package com.meada.invitations;

import com.meada.admin.invitations.InvitationAlreadyUsedException;
import com.meada.admin.invitations.InvitationEmailMismatchException;
import com.meada.admin.invitations.InvitationExpiredException;
import com.meada.admin.invitations.InvitationNotFoundException;
import com.meada.admin.invitations.InvitationService;
import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Endpoints PÚBLICOS do fluxo de convite (camada 5.16 #6), sob {@code /api/invitations/**}
 * — fora do prefixo /admin/. Dois endpoints com regras de auth distintas:
 *
 * <ul>
 *   <li>GET /api/invitations/{token} — SEM auth. A página /invite/{token} precisa mostrar
 *       "você foi convidado pela empresa X" ANTES do convidado logar. Retorna só dados
 *       não-sensíveis (email do convite, nome da empresa, validade). 404 se inválido.
 *   <li>POST /api/invitations/{token}/accept — exige JWT do Supabase (Bearer), mas NÃO
 *       exige linha em public.users (o convidado acabou de criar conta; a linha nasce
 *       AQUI). O JwtAuthenticationFilter autentica este path como INVITEE (companyId null)
 *       e popula authenticatedUser sem tocar users.
 * </ul>
 *
 * <p>O accept roda via service_role (InvitationService) — fora do RLS, porque o convidado
 * ainda não tem company resolvido.
 */
@RestController
public class PublicInvitationController {

    private static final Logger log = LoggerFactory.getLogger(PublicInvitationController.class);

    private final InvitationService invitationService;
    private final JdbcTemplate jdbcTemplate;

    public PublicInvitationController(InvitationService invitationService,
                                      JdbcTemplate jdbcTemplate) {
        this.invitationService = invitationService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Lookup público do convite ativo (não exige auth). Retorna {email, companyName,
     * expiresAt} se ativo (não usado, não expirado); 404 invitation_not_found caso
     * contrário. Não expõe o token nem invited_by — só o necessário para a tela.
     */
    @GetMapping("/api/invitations/{token}")
    public ResponseEntity<Object> lookup(@PathVariable String token) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
            "select i.email, c.name as company_name, i.expires_at "
                + "from tenant_invitations i join companies c on c.id = i.company_id "
                + "where i.token = ? and i.used_at is null and i.expires_at > now()",
            (rs, rowNum) -> {
                java.util.HashMap<String, Object> m = new java.util.HashMap<>();
                m.put("email", rs.getString("email"));
                m.put("companyName", rs.getString("company_name"));
                m.put("expiresAt", rs.getTimestamp("expires_at").toInstant().toString());
                return m;
            },
            token);
        if (rows.isEmpty()) {
            return ResponseEntity.status(404)
                .body(Map.of("error", "Not Found", "reason", "invitation_not_found"));
        }
        return ResponseEntity.ok(rows.get(0));
    }

    /**
     * Aceita o convite. Exige JWT (o filtro autenticou como INVITEE e populou
     * authenticatedUser com userId+email, companyId null). Cria/atualiza a linha em
     * public.users e marca o convite usado. Retorna {companyId, redirectTo}.
     *
     * <p>Cada falha de negócio vira um status + reason próprio (o frontend lê .reason).
     */
    @PostMapping("/api/invitations/{token}/accept")
    public ResponseEntity<Object> accept(
            @PathVariable String token,
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        try {
            UUID companyId = invitationService.acceptInvitation(token, user.userId(), user.email());
            return ResponseEntity.ok(Map.of(
                "companyId", companyId.toString(), "redirectTo", "/dashboard"));
        } catch (InvitationNotFoundException e) {
            return error(404, "invitation_not_found");
        } catch (InvitationExpiredException e) {
            return error(410, "invitation_expired");
        } catch (InvitationAlreadyUsedException e) {
            return error(409, "invitation_already_used");
        } catch (InvitationEmailMismatchException e) {
            return error(403, "invitation_email_mismatch");
        }
    }

    private ResponseEntity<Object> error(int status, String reason) {
        log.warn("invitation accept rejected status={} reason={}", status, reason);
        String errorText = switch (status) {
            case 404 -> "Not Found";
            case 409 -> "Conflict";
            case 410 -> "Gone";
            case 403 -> "Forbidden";
            default -> "Error";
        };
        return ResponseEntity.status(status).body(Map.of("error", errorText, "reason", reason));
    }
}
