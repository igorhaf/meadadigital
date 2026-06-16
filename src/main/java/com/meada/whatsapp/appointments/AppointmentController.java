package com.meada.whatsapp.appointments;

import com.meada.whatsapp.admin.security.AdminRole;
import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Endpoints de agendamento do painel do TENANT (camada 5.19 #59). TENANT-ADMIN ONLY: o admin vê
 * os agendamentos da PRÓPRIA empresa no calendário e atualiza o status (concluído/cancelado/
 * no-show). Sob /admin/** (o JwtAuthenticationFilter autentica e popula authenticatedUser).
 *
 * <p>Autorização por role no método (padrão da camada 4, igual InvitationController/
 * AvailabilityController): super-admin não tem company (companyId null) → 403
 * forbidden_not_tenant_admin. Isolamento por empresa vem do companyId do próprio
 * authenticatedUser (nunca de input do cliente).
 *
 * <p>Horários trafegam como ISO-8601 (scheduled_at em UTC, Instant.toString()). O range padrão
 * (sem from/to) é o mês corrente no fuso do tenant.
 */
@RestController
public class AppointmentController {

    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final Set<String> ALLOWED_STATUSES =
        Set.of("scheduled", "completed", "cancelled", "no_show");

    private final AppointmentRepository repository;

    public AppointmentController(AppointmentRepository repository) {
        this.repository = repository;
    }

    /**
     * Lista os agendamentos da empresa no range [from, to). Sem from/to → mês corrente (fuso
     * do tenant). from/to em ISO-8601 (ex.: "2026-06-01T00:00:00Z").
     */
    @GetMapping("/admin/appointments")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to) {
        if (user.role() != AdminRole.TENANT_ADMIN || user.companyId() == null) {
            return forbidden();
        }
        Instant fromInstant;
        Instant toInstant;
        if (from != null && to != null) {
            fromInstant = Instant.parse(from);
            toInstant = Instant.parse(to);
        } else {
            // Mês corrente no fuso do tenant → [1º dia 00:00, 1º dia do mês seguinte 00:00).
            YearMonth month = YearMonth.now(TENANT_ZONE);
            fromInstant = month.atDay(1).atStartOfDay(TENANT_ZONE).toInstant();
            LocalDate firstOfNext = month.plusMonths(1).atDay(1);
            toInstant = firstOfNext.atStartOfDay(TENANT_ZONE).toInstant();
        }
        List<Map<String, Object>> body = repository
            .findByCompanyBetween(user.companyId(), fromInstant, toInstant)
            .stream()
            .map(AppointmentController::toJson)
            .toList();
        return ResponseEntity.ok(body);
    }

    /**
     * Atualiza o status de um agendamento. Body {status}. 200 em sucesso; 400 status inválido;
     * 404 se não encontrado (ou não é da empresa).
     */
    @PatchMapping("/admin/appointments/{id}")
    public ResponseEntity<Object> updateStatus(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody Map<String, String> request) {
        if (user.role() != AdminRole.TENANT_ADMIN || user.companyId() == null) {
            return forbidden();
        }
        String status = request.get("status");
        if (status == null || !ALLOWED_STATUSES.contains(status)) {
            return ResponseEntity.status(400)
                .body(Map.of("error", "Bad Request", "reason", "invalid_status"));
        }
        boolean updated = repository.updateStatus(id, user.companyId(), status);
        if (!updated) {
            return ResponseEntity.status(404)
                .body(Map.of("error", "Not Found", "reason", "appointment_not_found"));
        }
        return ResponseEntity.ok(Map.of("id", id.toString(), "status", status));
    }

    private ResponseEntity<Object> forbidden() {
        return ResponseEntity.status(403)
            .body(Map.of("error", "Forbidden", "reason", "forbidden_not_tenant_admin"));
    }

    /** Serializa o agendamento para a resposta JSON (camelCase; scheduled_at em ISO-8601). */
    private static Map<String, Object> toJson(Appointment a) {
        java.util.HashMap<String, Object> m = new java.util.HashMap<>();
        m.put("id", a.id().toString());
        m.put("contactId", a.contactId().toString());
        m.put("conversationId", a.conversationId() != null ? a.conversationId().toString() : null);
        m.put("serviceId", a.serviceId() != null ? a.serviceId().toString() : null);
        m.put("scheduledAt", a.scheduledAt().toString());
        m.put("status", a.status());
        m.put("notes", a.notes());
        return m;
    }
}
