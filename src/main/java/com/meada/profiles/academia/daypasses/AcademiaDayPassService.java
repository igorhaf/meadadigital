package com.meada.profiles.academia.daypasses;

import com.meada.common.audit.AuditLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras dos passes de day-use / aula avulsa (camada 7.7). Só REGISTRO — a cobrança real
 * (link Pix/cartão) espera o gateway #50. O passe nasce NÃO pago; marcar pago é ação manual do
 * tenant. Audita as mutações. NÃO invalida o AcademiaContextCache (o day-use não entra no
 * contexto da IA por ora — a IA de academia não vende passe avulso nesta SM).
 */
@Service
public class AcademiaDayPassService {

    static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");

    private final AcademiaDayPassRepository repository;
    private final AuditLogger auditLogger;

    public AcademiaDayPassService(AcademiaDayPassRepository repository, AuditLogger auditLogger) {
        this.repository = repository;
        this.auditLogger = auditLogger;
    }

    /** Passe não encontrado / de outro tenant (→ 404). */
    public static class DayPassNotFoundException extends RuntimeException {}

    @Transactional
    public AcademiaDayPass create(UUID companyId, UUID userId, UUID contactId, String guestName,
                                  String guestPhone, UUID classId, LocalDate passDate, int priceCents) {
        LocalDate effectiveDate = passDate != null ? passDate : LocalDate.now(TENANT_ZONE);
        AcademiaDayPass created = repository.insert(
            companyId, contactId, guestName, guestPhone, classId, effectiveDate, priceCents);
        auditLogger.log(companyId, userId, "academia_day_pass_created", "academia_day_pass",
            created.id(), Map.of("guest_name", created.guestName(), "price_cents", priceCents));
        return created;
    }

    @Transactional
    public AcademiaDayPass markPaid(UUID companyId, UUID userId, UUID id) {
        AcademiaDayPass updated = repository.markPaid(companyId, id)
            .orElseThrow(DayPassNotFoundException::new);
        auditLogger.log(companyId, userId, "academia_day_pass_paid", "academia_day_pass", id, Map.of());
        return updated;
    }

    public List<AcademiaDayPass> list(UUID companyId) {
        return repository.listByCompany(companyId);
    }

    public Optional<AcademiaDayPass> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }
}
