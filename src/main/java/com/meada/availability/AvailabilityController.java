package com.meada.availability;

import com.meada.admin.security.AdminRole;
import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CRUD de janelas de disponibilidade do TENANT (camada 5.17 #61). TENANT-ADMIN ONLY: o
 * admin define janelas agendáveis por dia da semana para a PRÓPRIA empresa. Sob /admin/**
 * (o JwtAuthenticationFilter autentica e popula authenticatedUser).
 *
 * <p>Autorização por role no método (padrão da camada 4, igual InvitationController):
 * super-admin não tem company (companyId null) → não opera janelas de tenant → 403
 * forbidden_not_tenant_admin. Isolamento por empresa vem do companyId do próprio
 * authenticatedUser (nunca de input do cliente).
 *
 * <p>Horários trafegam como strings "HH:MM" no JSON (LocalTime.parse / startsAt.toString()).
 */
@RestController
public class AvailabilityController {

    private final AvailabilitySlotRepository repository;

    public AvailabilityController(AvailabilitySlotRepository repository) {
        this.repository = repository;
    }

    /** Lista todas as janelas da empresa do admin (inclui inativas — tela de gestão). */
    @GetMapping("/admin/availability-slots")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        if (user.role() != AdminRole.TENANT_ADMIN || user.companyId() == null) {
            return forbidden();
        }
        List<Map<String, Object>> body = repository.findAllByCompany(user.companyId()).stream()
            .map(this::toJson)
            .toList();
        return ResponseEntity.ok(body);
    }

    /**
     * Cria uma janela. Body {weekday, startsAt "HH:MM", endsAt "HH:MM", slotMinutes}. 201
     * com a janela serializada.
     */
    @PostMapping("/admin/availability-slots")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestBody Map<String, Object> request) {
        if (user.role() != AdminRole.TENANT_ADMIN || user.companyId() == null) {
            return forbidden();
        }
        int weekday = ((Number) request.get("weekday")).intValue();
        LocalTime startsAt = LocalTime.parse((String) request.get("startsAt"));
        LocalTime endsAt = LocalTime.parse((String) request.get("endsAt"));
        int slotMinutes = ((Number) request.get("slotMinutes")).intValue();
        AvailabilitySlot created =
            repository.insert(user.companyId(), weekday, startsAt, endsAt, slotMinutes);
        return ResponseEntity.status(201).body(toJson(created));
    }

    /**
     * Atualiza uma janela. Body {weekday, startsAt, endsAt, slotMinutes, active}. 200 em
     * sucesso; 404 se não encontrada (ou não é da empresa).
     */
    @PutMapping("/admin/availability-slots/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> request) {
        if (user.role() != AdminRole.TENANT_ADMIN || user.companyId() == null) {
            return forbidden();
        }
        int weekday = ((Number) request.get("weekday")).intValue();
        LocalTime startsAt = LocalTime.parse((String) request.get("startsAt"));
        LocalTime endsAt = LocalTime.parse((String) request.get("endsAt"));
        int slotMinutes = ((Number) request.get("slotMinutes")).intValue();
        boolean active = (Boolean) request.get("active");
        boolean updated = repository.update(
            id, user.companyId(), weekday, startsAt, endsAt, slotMinutes, active);
        if (!updated) {
            return notFound();
        }
        return ResponseEntity.ok(Map.of("id", id.toString()));
    }

    /** Remove uma janela. 204 em sucesso; 404 se não encontrada (ou não é da empresa). */
    @DeleteMapping("/admin/availability-slots/{id}")
    public ResponseEntity<Object> delete(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        if (user.role() != AdminRole.TENANT_ADMIN || user.companyId() == null) {
            return forbidden();
        }
        boolean deleted = repository.delete(id, user.companyId());
        if (!deleted) {
            return notFound();
        }
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<Object> forbidden() {
        return ResponseEntity.status(403)
            .body(Map.of("error", "Forbidden", "reason", "forbidden_not_tenant_admin"));
    }

    private ResponseEntity<Object> notFound() {
        return ResponseEntity.status(404)
            .body(Map.of("error", "Not Found", "reason", "availability_slot_not_found"));
    }

    /** Serializa a janela para a resposta JSON (camelCase; horários como "HH:MM"). */
    private Map<String, Object> toJson(AvailabilitySlot slot) {
        java.util.HashMap<String, Object> m = new java.util.HashMap<>();
        m.put("id", slot.id().toString());
        m.put("weekday", slot.weekday());
        m.put("startsAt", slot.startsAt().toString());
        m.put("endsAt", slot.endsAt().toString());
        m.put("slotMinutes", slot.slotMinutes());
        m.put("active", slot.active());
        return m;
    }
}
