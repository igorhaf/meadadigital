package com.meada.admin.instances;

import com.meada.admin.security.AdminRole;
import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.admin.instances.WhatsappConnectionService.AlreadyConnectedException;
import com.meada.admin.instances.WhatsappConnectionService.InstanceNameTakenException;
import com.meada.admin.instances.WhatsappConnectionService.WhatsappUnavailableException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Conexão do WhatsApp do tenant — TENANT-ADMIN only (o super-admin não tem company_id).
 *
 * <p>Endpoints sob {@code /admin/whatsapp/**} (já cobertos pelo {@link JwtAuthenticationFilter},
 * que filtra {@code /admin/**}):
 * <ul>
 *   <li>{@code GET /admin/whatsapp} — estado, sincronizado com a Evolution (fonte da verdade).
 *   <li>{@code POST /admin/whatsapp/connect} — provisiona/retoma e devolve o QR (data-URI base64).
 *   <li>{@code POST /admin/whatsapp/disconnect} — logout; a instância e o histórico permanecem.
 * </ul>
 *
 * <p>Reasons: {@code forbidden_not_tenant} (403), {@code whatsapp_unavailable} (503 — servidor sem
 * API key global da Evolution), {@code already_connected} (409), {@code instance_name_taken} (409),
 * {@code evolution_error} (502).
 */
@RestController
public class WhatsappConnectionController {

    private final WhatsappConnectionService service;

    public WhatsappConnectionController(WhatsappConnectionService service) {
        this.service = service;
    }

    @GetMapping("/admin/whatsapp")
    public ResponseEntity<Object> status(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        ResponseEntity<Object> denied = requireTenant(user);
        if (denied != null) {
            return denied;
        }
        try {
            return ResponseEntity.ok(service.status(user.companyId()));
        } catch (EvolutionInstanceException e) {
            return error(502, "Bad Gateway", "evolution_error");
        }
    }

    @PostMapping("/admin/whatsapp/connect")
    public ResponseEntity<Object> connect(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        ResponseEntity<Object> denied = requireTenant(user);
        if (denied != null) {
            return denied;
        }
        try {
            String qrCode = service.connect(user.companyId(), user.userId());
            return ResponseEntity.ok(Map.of("qrCode", qrCode, "status", WhatsappConnection.CONNECTING));
        } catch (WhatsappUnavailableException e) {
            return error(503, "Service Unavailable", "whatsapp_unavailable");
        } catch (AlreadyConnectedException e) {
            return error(409, "Conflict", "already_connected");
        } catch (InstanceNameTakenException e) {
            return error(409, "Conflict", "instance_name_taken");
        } catch (EvolutionInstanceException e) {
            return error(502, "Bad Gateway", "evolution_error");
        }
    }

    @PostMapping("/admin/whatsapp/disconnect")
    public ResponseEntity<Object> disconnect(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        ResponseEntity<Object> denied = requireTenant(user);
        if (denied != null) {
            return denied;
        }
        try {
            service.disconnect(user.companyId(), user.userId());
            return ResponseEntity.noContent().build();
        } catch (WhatsappUnavailableException e) {
            return error(503, "Service Unavailable", "whatsapp_unavailable");
        } catch (IllegalStateException e) {
            return error(404, "Not Found", "instance_not_found");
        } catch (EvolutionInstanceException e) {
            return error(502, "Bad Gateway", "evolution_error");
        }
    }

    /** 403 se não for tenant-admin (super-admin não tem company_id). null se ok. */
    private ResponseEntity<Object> requireTenant(AuthenticatedUser user) {
        if (user.role() != AdminRole.TENANT_ADMIN || user.companyId() == null) {
            return error(403, "Forbidden", "forbidden_not_tenant");
        }
        return null;
    }

    private ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }
}
