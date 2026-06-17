package com.meada.whatsapp.profiles.academia.payments;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import com.meada.whatsapp.profiles.academia.AcademiaProfileGuard;
import com.meada.whatsapp.profiles.academia.AcademiaProfileGuard.WrongProfileException;
import com.meada.whatsapp.profiles.academia.memberships.AcademiaMembership;
import com.meada.whatsapp.profiles.academia.payments.AcademiaPaymentService.DuplicatePaymentException;
import com.meada.whatsapp.profiles.academia.payments.AcademiaPaymentService.MembershipNotActiveException;
import com.meada.whatsapp.profiles.academia.payments.AcademiaPaymentService.MembershipNotFoundException;
import com.meada.whatsapp.profiles.academia.payments.AcademiaPaymentService.PaymentNotFoundException;
import com.meada.whatsapp.profiles.academia.payments.AcademiaPaymentService.PaymentSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Pagamentos de uma matrícula (camada 7.7). TENANT + perfil 'academia' only.
 * Rotas sob /api/academia/memberships/{id}/payments.
 */
@RestController
public class AcademiaPaymentController {

    private final AcademiaPaymentService service;
    private final AcademiaProfileGuard profileGuard;

    public AcademiaPaymentController(AcademiaPaymentService service, AcademiaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    /** Body do registro. referenceMonth em "YYYY-MM-DD" (normalizado pro dia 01). */
    public record RecordPaymentRequest(
        @NotBlank String referenceMonth,
        @PositiveOrZero int amountCents,
        String method,
        String notes) {}

    @GetMapping("/api/academia/memberships/{id}/payments")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAcademia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        AcademiaMembership m = service.getMembership(companyId, id).orElse(null);
        if (m == null) {
            return error(404, "Not Found", "membership_not_found");
        }
        PaymentSummary summary = service.summary(companyId, id, m.startDate());
        return ResponseEntity.ok(Map.of(
            "items", service.listByMembership(companyId, id),
            "summary", Map.of(
                "lastPaidMonth", summary.lastPaidMonth() == null ? null : summary.lastPaidMonth().toString(),
                "monthsOpen", summary.monthsOpen(),
                "totalPayments", summary.totalPayments())));
    }

    @PostMapping("/api/academia/memberships/{id}/payments")
    public ResponseEntity<Object> record(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody RecordPaymentRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAcademia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        LocalDate refMonth;
        try {
            refMonth = LocalDate.parse(req.referenceMonth());
        } catch (DateTimeException e) {
            return error(400, "Bad Request", "invalid_date");
        }
        try {
            AcademiaPayment created = service.record(companyId, user.userId(), id, refMonth,
                req.amountCents(), req.method(), req.notes());
            return ResponseEntity.status(201).body(created);
        } catch (MembershipNotFoundException e) {
            return error(404, "Not Found", "membership_not_found");
        } catch (MembershipNotActiveException e) {
            return error(400, "Bad Request", "membership_not_active");
        } catch (DuplicatePaymentException e) {
            return error(409, "Conflict", "duplicate_payment");
        }
    }

    @DeleteMapping("/api/academia/memberships/{id}/payments/{paymentId}")
    public ResponseEntity<Object> delete(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @PathVariable UUID paymentId) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAcademia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            service.delete(companyId, user.userId(), paymentId);
            return ResponseEntity.noContent().build();
        } catch (PaymentNotFoundException e) {
            return error(404, "Not Found", "payment_not_found");
        }
    }
}
