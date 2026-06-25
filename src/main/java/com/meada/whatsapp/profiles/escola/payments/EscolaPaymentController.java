package com.meada.whatsapp.profiles.escola.payments;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import com.meada.whatsapp.profiles.escola.EscolaProfileGuard;
import com.meada.whatsapp.profiles.escola.EscolaProfileGuard.WrongProfileException;
import com.meada.whatsapp.profiles.escola.enrollments.EscolaEnrollment;
import com.meada.whatsapp.profiles.escola.payments.EscolaPaymentService.DuplicatePaymentException;
import com.meada.whatsapp.profiles.escola.payments.EscolaPaymentService.EnrollmentCancelledException;
import com.meada.whatsapp.profiles.escola.payments.EscolaPaymentService.EnrollmentNotFoundException;
import com.meada.whatsapp.profiles.escola.payments.EscolaPaymentService.PaymentNotFoundException;
import com.meada.whatsapp.profiles.escola.payments.EscolaPaymentService.PaymentSummary;
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
 * Mensalidades de uma matrícula (camada 8.19). TENANT + perfil 'escola' only.
 * Rotas sob /api/escola/enrollments/{id}/payments.
 */
@RestController
public class EscolaPaymentController {

    private final EscolaPaymentService service;
    private final EscolaProfileGuard profileGuard;

    public EscolaPaymentController(EscolaPaymentService service, EscolaProfileGuard profileGuard) {
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

    @GetMapping("/api/escola/enrollments/{id}/payments")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEscola(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        EscolaEnrollment e = service.getEnrollment(companyId, id).orElse(null);
        if (e == null) {
            return error(404, "Not Found", "enrollment_not_found");
        }
        PaymentSummary summary = service.summary(companyId, id, e.startDate());
        return ResponseEntity.ok(Map.of(
            "items", service.listByEnrollment(companyId, id),
            "summary", Map.of(
                "lastPaidMonth", summary.lastPaidMonth() == null ? null : summary.lastPaidMonth().toString(),
                "monthsOpen", summary.monthsOpen(),
                "totalPayments", summary.totalPayments())));
    }

    @PostMapping("/api/escola/enrollments/{id}/payments")
    public ResponseEntity<Object> record(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody RecordPaymentRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEscola(user);
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
            EscolaPayment created = service.record(companyId, user.userId(), id, refMonth,
                req.amountCents(), req.method(), req.notes());
            return ResponseEntity.status(201).body(created);
        } catch (EnrollmentNotFoundException e) {
            return error(404, "Not Found", "enrollment_not_found");
        } catch (EnrollmentCancelledException e) {
            return error(400, "Bad Request", "enrollment_cancelled");
        } catch (DuplicatePaymentException e) {
            return error(409, "Conflict", "duplicate_payment");
        }
    }

    @DeleteMapping("/api/escola/enrollments/{id}/payments/{paymentId}")
    public ResponseEntity<Object> delete(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @PathVariable UUID paymentId) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEscola(user);
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
