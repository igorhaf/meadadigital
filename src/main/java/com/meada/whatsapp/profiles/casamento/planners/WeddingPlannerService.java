package com.meada.whatsapp.profiles.casamento.planners;

import com.meada.whatsapp.common.audit.AuditLogger;
import com.meada.whatsapp.profiles.casamento.CasamentoContextCache;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Regras dos assessores do tenant casamento (camada 8.7). Audita + invalida {@link CasamentoContextCache}. */
@Service
public class WeddingPlannerService {

    private final WeddingPlannerRepository repository;
    private final AuditLogger auditLogger;
    private final CasamentoContextCache contextCache;

    public WeddingPlannerService(WeddingPlannerRepository repository, AuditLogger auditLogger,
                                 CasamentoContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    public static class PlannerNotFoundException extends RuntimeException {}
    public static class PlannerInUseException extends RuntimeException {}

    @Transactional
    public WeddingPlanner create(UUID companyId, UUID userId, String name, String specialty, String notes) {
        WeddingPlanner created = repository.insert(companyId, name, specialty, notes);
        auditLogger.log(companyId, userId, "wedding_planner_created", "wedding_planner",
            created.id(), Map.of("name", created.name()));
        contextCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public WeddingPlanner update(UUID companyId, UUID userId, UUID id, String name, String specialty,
                                 String notes, Boolean active) {
        WeddingPlanner updated = repository.update(companyId, id, name, specialty, notes, active)
            .orElseThrow(PlannerNotFoundException::new);
        auditLogger.log(companyId, userId, "wedding_planner_updated", "wedding_planner", id, Map.of());
        contextCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public WeddingPlanner toggle(UUID companyId, UUID userId, UUID id, boolean active) {
        WeddingPlanner p = repository.toggle(companyId, id, active).orElseThrow(PlannerNotFoundException::new);
        auditLogger.log(companyId, userId, "wedding_planner_updated", "wedding_planner", id, Map.of("active", active));
        contextCache.invalidate(companyId);
        return p;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        // planner_id é ON DELETE SET NULL na proposta — checamos uso explicitamente (a FK não barra).
        if (repository.hasProposals(companyId, id)) {
            throw new PlannerInUseException();
        }
        try {
            if (!repository.delete(companyId, id)) {
                throw new PlannerNotFoundException();
            }
        } catch (DataIntegrityViolationException e) {
            throw new PlannerInUseException();
        }
        auditLogger.log(companyId, userId, "wedding_planner_deleted", "wedding_planner", id, Map.of());
        contextCache.invalidate(companyId);
    }

    public List<WeddingPlanner> list(UUID companyId, boolean onlyActive) {
        return repository.listByCompany(companyId, onlyActive);
    }

    public Optional<WeddingPlanner> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }
}
