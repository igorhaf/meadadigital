package com.meada.whatsapp.profiles.academia.plans;

import com.meada.whatsapp.common.audit.AuditLogger;
import com.meada.whatsapp.profiles.academia.AcademiaContextCache;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras dos planos da academia (camada 7.7). Audita mutações e invalida o
 * {@link AcademiaContextCache}.
 */
@Service
public class AcademiaPlanService {

    private final AcademiaPlanRepository repository;
    private final AuditLogger auditLogger;
    private final AcademiaContextCache contextCache;

    public AcademiaPlanService(AcademiaPlanRepository repository, AuditLogger auditLogger,
                               AcademiaContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    /** Plano não encontrado / de outro tenant (→ 404). */
    public static class PlanNotFoundException extends RuntimeException {}

    /** Plano referenciado por matrícula (FK restrict) — não pode hard-deletar (→ 409). */
    public static class PlanInUseException extends RuntimeException {}

    @Transactional
    public AcademiaPlan create(UUID companyId, UUID userId, String name, int monthlyCents, String description) {
        AcademiaPlan created = repository.insert(companyId, name, monthlyCents, description);
        auditLogger.log(companyId, userId, "academia_plan_created", "academia_plan",
            created.id(), Map.of("name", created.name()));
        contextCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public AcademiaPlan update(UUID companyId, UUID userId, UUID id, String name, Integer monthlyCents,
                               String description, Boolean active) {
        AcademiaPlan updated = repository.update(companyId, id, name, monthlyCents, description, active)
            .orElseThrow(PlanNotFoundException::new);
        auditLogger.log(companyId, userId, "academia_plan_updated", "academia_plan", id, Map.of());
        contextCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public AcademiaPlan toggle(UUID companyId, UUID userId, UUID id, boolean active) {
        AcademiaPlan p = repository.toggle(companyId, id, active).orElseThrow(PlanNotFoundException::new);
        auditLogger.log(companyId, userId, "academia_plan_updated", "academia_plan", id, Map.of("active", active));
        contextCache.invalidate(companyId);
        return p;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        try {
            if (!repository.delete(companyId, id)) {
                throw new PlanNotFoundException();
            }
        } catch (DataIntegrityViolationException e) {
            throw new PlanInUseException();
        }
        auditLogger.log(companyId, userId, "academia_plan_deleted", "academia_plan", id, Map.of());
        contextCache.invalidate(companyId);
    }

    public List<AcademiaPlan> list(UUID companyId, boolean onlyActive) {
        return repository.listByCompany(companyId, onlyActive);
    }

    public Optional<AcademiaPlan> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }
}
