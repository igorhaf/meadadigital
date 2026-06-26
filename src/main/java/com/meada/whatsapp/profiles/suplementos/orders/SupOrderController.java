package com.meada.whatsapp.profiles.suplementos.orders;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import com.meada.whatsapp.profiles.suplementos.SuplementosProfileGuard;
import com.meada.whatsapp.profiles.suplementos.SuplementosProfileGuard.WrongProfileException;
import com.meada.whatsapp.profiles.suplementos.orders.SupOrderService.InvalidStatusException;
import com.meada.whatsapp.profiles.suplementos.orders.SupOrderService.InvalidStatusTransitionException;
import com.meada.whatsapp.profiles.suplementos.orders.SupOrderService.OrderNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Pedidos do tenant suplementos (camada 8.24). Clone do
 * {@link com.meada.whatsapp.profiles.lingerie.orders.LingerieOrderController}. TENANT + perfil
 * 'suplementos' only. READ + transição de status (o Kanban / gate de aceite). NÃO há POST de criar
 * pedido — pedidos vêm da IA ({@code PedidoSuplementosConfirmHandler}).
 */
@RestController
public class SupOrderController {

    private static final int MAX_PAGE_SIZE = 100;

    private final SupOrderService service;
    private final SuplementosProfileGuard profileGuard;

    public SupOrderController(SupOrderService service, SuplementosProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    /** Body da transição: newStatus obrigatório; rejectionReason opcional (só usado na recusa). */
    public record StatusRequest(@NotBlank String newStatus, String rejectionReason) {}

    // ---- GET lista (filtro status + paginação) ------------------------------
    @GetMapping("/api/suplementos/orders")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        UUID companyId;
        try {
            companyId = profileGuard.requireSuplementos(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        int size = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        long total = service.count(companyId, status);
        return ResponseEntity.ok(Map.of(
            "items", service.list(companyId, status, size, safePage * size),
            "total", total, "page", safePage, "pageSize", size));
    }

    // ---- GET detalhe --------------------------------------------------------
    @GetMapping("/api/suplementos/orders/{id}")
    public ResponseEntity<Object> detail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireSuplementos(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return service.get(companyId, id)
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .orElseGet(() -> error(404, "Not Found", "order_not_found"));
    }

    // ---- PATCH status (gate de aceite) --------------------------------------
    @PatchMapping("/api/suplementos/orders/{id}/status")
    public ResponseEntity<Object> updateStatus(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody StatusRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireSuplementos(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.updateStatus(companyId, id, req.newStatus(), req.rejectionReason()));
        } catch (InvalidStatusException e) {
            return error(400, "Bad Request", "invalid_status");
        } catch (OrderNotFoundException e) {
            return error(404, "Not Found", "order_not_found");
        } catch (InvalidStatusTransitionException e) {
            return error(409, "Conflict", "invalid_status_transition");
        }
    }
}
