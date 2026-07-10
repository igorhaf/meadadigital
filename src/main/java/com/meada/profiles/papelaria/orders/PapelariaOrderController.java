package com.meada.profiles.papelaria.orders;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.papelaria.PapelariaProfileGuard;
import com.meada.profiles.papelaria.PapelariaProfileGuard.WrongProfileException;
import com.meada.profiles.papelaria.orders.PapelariaOrderService.ArtNotApprovedException;
import com.meada.profiles.papelaria.orders.PapelariaOrderService.InvalidStatusException;
import com.meada.profiles.papelaria.orders.PapelariaOrderService.InvalidStatusTransitionException;
import com.meada.profiles.papelaria.orders.PapelariaOrderService.OrderNotFoundException;
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
 * Pedidos do tenant papelaria (camada 8.15 / perfil papelaria). Clone de
 * {@link com.meada.profiles.padaria.orders.PadariaOrderController} (camada 8.8) + a ESCAPADA
 * PROVA DE ARTE. TENANT + perfil 'papelaria' only. READ + transição de status (o Kanban / gate de
 * aceite) + PATCH {id}/art (a equipe sobe a arte → arte_aprovacao, OU aprova a arte → em_producao). NÃO
 * há POST de criar pedido — pedidos vêm da IA (PedidoPapelariaConfirmHandler). Sob
 * {@code /api/papelaria/orders}.
 */
@RestController
public class PapelariaOrderController {

    private static final int MAX_PAGE_SIZE = 100;

    private final PapelariaOrderService service;
    private final PapelariaProfileGuard profileGuard;

    public PapelariaOrderController(PapelariaOrderService service, PapelariaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    /** Body da transição: newStatus obrigatório; rejectionReason opcional (só usado na recusa). */
    public record StatusRequest(@NotBlank String newStatus, String rejectionReason) {}

    /**
     * Body do PATCH /art: a equipe ou sobe a arte (artUrl preenchido → aceito→arte_aprovacao), ou
     * aprova a arte (approve=true → arte_aprovacao→em_producao). Os dois caminhos são da ESCAPADA.
     */
    public record ArtRequest(String artUrl, boolean approve) {}

    // ---- GET lista (filtro status + paginação) ------------------------------
    @GetMapping("/api/papelaria/orders")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        UUID companyId;
        try {
            companyId = profileGuard.requirePapelaria(user);
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
    @GetMapping("/api/papelaria/orders/{id}")
    public ResponseEntity<Object> detail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requirePapelaria(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return service.get(companyId, id)
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .orElseGet(() -> error(404, "Not Found", "order_not_found"));
    }

    // ---- PATCH status (gate de aceite + gate da arte) -----------------------
    @PatchMapping("/api/papelaria/orders/{id}/status")
    public ResponseEntity<Object> updateStatus(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody StatusRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requirePapelaria(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.updateStatus(companyId, id, req.newStatus(), req.rejectionReason()));
        } catch (InvalidStatusException e) {
            return error(400, "Bad Request", "invalid_status");
        } catch (OrderNotFoundException e) {
            return error(404, "Not Found", "order_not_found");
        } catch (PapelariaOrderService.DepositRequiredException e) {
            return error(409, "Conflict", "deposit_required");
        } catch (ArtNotApprovedException e) {
            return error(409, "Conflict", "art_not_approved");
        } catch (InvalidStatusTransitionException e) {
            return error(409, "Conflict", "invalid_status_transition");
        }
    }

    // ---- PATCH art (sobe a arte → arte_aprovacao, OU aprova → em_producao) ---
    @PatchMapping("/api/papelaria/orders/{id}/art")
    public ResponseEntity<Object> art(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody ArtRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requirePapelaria(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            if (req.approve()) {
                return ResponseEntity.ok(service.approveArt(companyId, id));
            }
            if (req.artUrl() == null || req.artUrl().isBlank()) {
                return error(400, "Bad Request", "art_url_required");
            }
            return ResponseEntity.ok(service.setArtUrl(companyId, id, req.artUrl()));
        } catch (IllegalArgumentException e) {
            return error(400, "Bad Request", "art_url_required");
        } catch (OrderNotFoundException e) {
            return error(404, "Not Found", "order_not_found");
        } catch (InvalidStatusTransitionException e) {
            return error(409, "Conflict", "invalid_status_transition");
        }
    }

    // ---- PATCH deposit (onda #1 — sinal manual até o gateway #50) -------------

    public record DepositRequest(Integer depositCents, Boolean depositPaid) {}

    @PatchMapping("/api/papelaria/orders/{id}/deposit")
    public ResponseEntity<Object> deposit(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody DepositRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requirePapelaria(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.setDeposit(companyId, id, req.depositCents(),
                Boolean.TRUE.equals(req.depositPaid())));
        } catch (OrderNotFoundException e) {
            return error(404, "Not Found", "order_not_found");
        } catch (PapelariaOrderService.InvalidDepositException e) {
            return error(400, "Bad Request", "invalid_deposit");
        }
    }
}
