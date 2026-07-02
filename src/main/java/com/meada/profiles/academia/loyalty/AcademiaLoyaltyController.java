package com.meada.profiles.academia.loyalty;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.academia.AcademiaProfileGuard;
import com.meada.profiles.academia.AcademiaProfileGuard.WrongProfileException;
import com.meada.profiles.academia.loyalty.AcademiaLoyaltyService.ContactNotFoundException;
import com.meada.profiles.academia.loyalty.AcademiaLoyaltyService.InvalidConfigException;
import com.meada.profiles.academia.loyalty.AcademiaLoyaltyService.InvalidPointsException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Fidelidade por assiduidade do tenant academia (camada 7.7, feature #12). TENANT + perfil 'academia'
 * only. Rotas sob /api/academia/loyalty:
 *   - GET  /config           → política (fallback defaults)
 *   - PUT  /config           → grava a política
 *   - GET  /balance?contactId → saldo do contato + se a recompensa foi atingida
 *   - POST /points           → credita pontos a um contato (uso administrativo/painel)
 */
@RestController
public class AcademiaLoyaltyController {

    private final AcademiaLoyaltyService service;
    private final AcademiaProfileGuard profileGuard;

    public AcademiaLoyaltyController(AcademiaLoyaltyService service, AcademiaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record ConfigRequest(
        boolean enabled,
        int pointsPerCheckin,
        Integer rewardThreshold,
        String rewardText) {}

    public record AddPointsRequest(
        @NotNull UUID contactId,
        int points) {}

    @GetMapping("/api/academia/loyalty/config")
    public ResponseEntity<Object> getConfig(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAcademia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(service.getConfig(companyId));
    }

    @PutMapping("/api/academia/loyalty/config")
    public ResponseEntity<Object> putConfig(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody ConfigRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAcademia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.updateConfig(companyId, user.userId(),
                req.enabled(), req.pointsPerCheckin(), req.rewardThreshold(), req.rewardText()));
        } catch (InvalidConfigException e) {
            return error(400, "Bad Request", "invalid_config");
        }
    }

    @GetMapping("/api/academia/loyalty/balance")
    public ResponseEntity<Object> getBalance(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam UUID contactId) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAcademia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        AcademiaLoyaltyConfig config = service.getConfig(companyId);
        AcademiaLoyaltyBalance balance = service.getBalance(companyId, contactId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("companyId", balance.companyId());
        body.put("contactId", balance.contactId());
        body.put("points", balance.points());
        body.put("updatedAt", balance.updatedAt());
        body.put("rewardThreshold", config.rewardThreshold());
        body.put("rewardReached", service.rewardReached(config, balance));
        return ResponseEntity.ok(body);
    }

    @PostMapping("/api/academia/loyalty/points")
    public ResponseEntity<Object> addPoints(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody AddPointsRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAcademia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            AcademiaLoyaltyBalance balance = service.addPoints(companyId, user.userId(),
                req.contactId(), req.points());
            return ResponseEntity.status(201).body(balance);
        } catch (InvalidPointsException e) {
            return error(400, "Bad Request", "invalid_points");
        } catch (ContactNotFoundException e) {
            return error(404, "Not Found", "contact_not_found");
        }
    }
}
