package com.meada.whatsapp.profiles.academia.payments;

import com.meada.whatsapp.common.audit.AuditLogger;
import com.meada.whatsapp.profiles.academia.memberships.AcademiaMembership;
import com.meada.whatsapp.profiles.academia.memberships.AcademiaMembershipRepository;
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
 * Regras dos pagamentos manuais (camada 7.7). Registra pagamento mensal só em matrícula ATIVA,
 * impede duplicidade no mês (UNIQUE → 409), e calcula um resumo (último mês pago + meses em aberto).
 * NÃO invalida o AcademiaContextCache (pagamento não entra no contexto da IA por ora).
 */
@Service
public class AcademiaPaymentService {

    static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");

    private final AcademiaPaymentRepository repository;
    private final AcademiaMembershipRepository membershipRepository;
    private final AuditLogger auditLogger;

    public AcademiaPaymentService(AcademiaPaymentRepository repository,
                                  AcademiaMembershipRepository membershipRepository,
                                  AuditLogger auditLogger) {
        this.repository = repository;
        this.membershipRepository = membershipRepository;
        this.auditLogger = auditLogger;
    }

    public static class MembershipNotFoundException extends RuntimeException {}
    public static class MembershipNotActiveException extends RuntimeException {}
    public static class DuplicatePaymentException extends RuntimeException {}
    public static class PaymentNotFoundException extends RuntimeException {}

    /** Resumo de pagamentos: último mês pago + meses em aberto (decorridos desde start_date - pagos). */
    public record PaymentSummary(LocalDate lastPaidMonth, int monthsOpen, int totalPayments) {}

    @Transactional
    public AcademiaPayment record(UUID companyId, UUID userId, UUID membershipId, LocalDate referenceMonth,
                                  int amountCents, String method, String notes) {
        AcademiaMembership m = membershipRepository.findById(companyId, membershipId)
            .orElseThrow(MembershipNotFoundException::new);
        if (!"ativa".equals(m.status())) {
            throw new MembershipNotActiveException();
        }
        AcademiaPayment created;
        try {
            // normaliza pro dia 01 do mês de referência.
            LocalDate firstOfMonth = referenceMonth.withDayOfMonth(1);
            created = repository.insert(companyId, membershipId, firstOfMonth, amountCents, method, notes);
        } catch (DuplicateKeyException e) {
            throw new DuplicatePaymentException();
        }
        auditLogger.log(companyId, userId, "academia_payment_recorded", "academia_payment",
            created.id(), Map.of("membership_id", membershipId.toString(), "amount_cents", amountCents));
        return created;
    }

    public List<AcademiaPayment> listByMembership(UUID companyId, UUID membershipId) {
        return repository.listByMembership(companyId, membershipId);
    }

    public Optional<AcademiaMembership> getMembership(UUID companyId, UUID membershipId) {
        return membershipRepository.findById(companyId, membershipId);
    }

    /**
     * Resumo: último mês pago + meses em aberto. monthsOpen = meses decorridos desde o start_date
     * (inclusivo, +1) menos os pagamentos lançados, nunca negativo. Cálculo simples (sem juros/multa).
     */
    public PaymentSummary summary(UUID companyId, UUID membershipId, LocalDate startDate) {
        Optional<LocalDate> last = repository.lastPaidMonth(companyId, membershipId);
        int paid = repository.countByMembership(companyId, membershipId);
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
        auditLogger.log(companyId, userId, "academia_payment_deleted", "academia_payment", paymentId, Map.of());
    }
}
