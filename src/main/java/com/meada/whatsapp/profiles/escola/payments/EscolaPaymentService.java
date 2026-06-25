package com.meada.whatsapp.profiles.escola.payments;

import com.meada.whatsapp.common.audit.AuditLogger;
import com.meada.whatsapp.profiles.escola.enrollments.EscolaEnrollment;
import com.meada.whatsapp.profiles.escola.enrollments.EscolaEnrollmentRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras das mensalidades manuais (camada 8.19). Registra pagamento mensal só em matrícula
 * NÃO-CANCELADA, impede duplicidade no mês (UNIQUE → 409), e calcula um resumo (último mês pago +
 * meses em aberto). NÃO invalida o EscolaContextCache (pagamento não entra no contexto da IA).
 */
@Service
public class EscolaPaymentService {

    static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");

    private final EscolaPaymentRepository repository;
    private final EscolaEnrollmentRepository enrollmentRepository;
    private final AuditLogger auditLogger;

    public EscolaPaymentService(EscolaPaymentRepository repository,
                                EscolaEnrollmentRepository enrollmentRepository,
                                AuditLogger auditLogger) {
        this.repository = repository;
        this.enrollmentRepository = enrollmentRepository;
        this.auditLogger = auditLogger;
    }

    public static class EnrollmentNotFoundException extends RuntimeException {}
    public static class EnrollmentCancelledException extends RuntimeException {}
    public static class DuplicatePaymentException extends RuntimeException {}
    public static class PaymentNotFoundException extends RuntimeException {}

    /** Resumo de pagamentos: último mês pago + meses em aberto (decorridos desde start_date - pagos). */
    public record PaymentSummary(LocalDate lastPaidMonth, int monthsOpen, int totalPayments) {}

    @Transactional
    public EscolaPayment record(UUID companyId, UUID userId, UUID enrollmentId, LocalDate referenceMonth,
                                int amountCents, String method, String notes) {
        EscolaEnrollment e = enrollmentRepository.findById(companyId, enrollmentId)
            .orElseThrow(EnrollmentNotFoundException::new);
        if ("cancelada".equals(e.status())) {
            throw new EnrollmentCancelledException();
        }
        EscolaPayment created;
        try {
            // normaliza pro dia 01 do mês de referência.
            LocalDate firstOfMonth = referenceMonth.withDayOfMonth(1);
            created = repository.insert(companyId, enrollmentId, firstOfMonth, amountCents, method, notes);
        } catch (DuplicateKeyException ex) {
            throw new DuplicatePaymentException();
        }
        auditLogger.log(companyId, userId, "escola_payment_recorded", "escola_payment",
            created.id(), Map.of("enrollment_id", enrollmentId.toString(), "amount_cents", amountCents));
        return created;
    }

    public List<EscolaPayment> listByEnrollment(UUID companyId, UUID enrollmentId) {
        return repository.listByEnrollment(companyId, enrollmentId);
    }

    public Optional<EscolaEnrollment> getEnrollment(UUID companyId, UUID enrollmentId) {
        return enrollmentRepository.findById(companyId, enrollmentId);
    }

    /**
     * Resumo: último mês pago + meses em aberto. monthsOpen = meses decorridos desde o start_date
     * (inclusivo, +1) menos os pagamentos lançados, nunca negativo. Cálculo simples (sem juros/multa).
     */
    public PaymentSummary summary(UUID companyId, UUID enrollmentId, LocalDate startDate) {
        Optional<LocalDate> last = repository.lastPaidMonth(companyId, enrollmentId);
        int paid = repository.countByEnrollment(companyId, enrollmentId);
        LocalDate today = LocalDate.now(TENANT_ZONE);
        long monthsElapsed = ChronoUnit.MONTHS.between(startDate.withDayOfMonth(1), today.withDayOfMonth(1)) + 1;
        int monthsOpen = (int) Math.max(0, monthsElapsed - paid);
        return new PaymentSummary(last.orElse(null), monthsOpen, paid);
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID paymentId) {
        if (!repository.delete(companyId, paymentId)) {
            throw new PaymentNotFoundException();
        }
        auditLogger.log(companyId, userId, "escola_payment_deleted", "escola_payment", paymentId, Map.of());
    }
}
