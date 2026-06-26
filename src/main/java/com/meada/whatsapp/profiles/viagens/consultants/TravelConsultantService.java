package com.meada.whatsapp.profiles.viagens.consultants;

import com.meada.whatsapp.common.audit.AuditLogger;
import com.meada.whatsapp.profiles.viagens.ViagensContextCache;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras dos consultores do tenant viagens (camada 8.18 / perfil viagens). Audita + invalida
 * {@link ViagensContextCache}. Espelho EXATO do EventPlannerService (chassi eventos 8.2).
 */
@Service
public class TravelConsultantService {

    private final TravelConsultantRepository repository;
    private final AuditLogger auditLogger;
    private final ViagensContextCache contextCache;

    public TravelConsultantService(TravelConsultantRepository repository, AuditLogger auditLogger,
                                   ViagensContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    public static class ConsultantNotFoundException extends RuntimeException {}
    public static class ConsultantInUseException extends RuntimeException {}

    @Transactional
    public TravelConsultant create(UUID companyId, UUID userId, String name, String specialty, String notes) {
        TravelConsultant created = repository.insert(companyId, name, specialty, notes);
        auditLogger.log(companyId, userId, "travel_consultant_created", "travel_consultant",
            created.id(), Map.of("name", created.name()));
        contextCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public TravelConsultant update(UUID companyId, UUID userId, UUID id, String name, String specialty,
                                   String notes, Boolean active) {
        TravelConsultant updated = repository.update(companyId, id, name, specialty, notes, active)
            .orElseThrow(ConsultantNotFoundException::new);
        auditLogger.log(companyId, userId, "travel_consultant_updated", "travel_consultant", id, Map.of());
        contextCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public TravelConsultant toggle(UUID companyId, UUID userId, UUID id, boolean active) {
        TravelConsultant c = repository.toggle(companyId, id, active).orElseThrow(ConsultantNotFoundException::new);
        auditLogger.log(companyId, userId, "travel_consultant_updated", "travel_consultant", id, Map.of("active", active));
        contextCache.invalidate(companyId);
        return c;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        // consultant_id é ON DELETE SET NULL na proposta — checamos uso explicitamente (a FK não barra).
        if (repository.hasProposals(companyId, id)) {
            throw new ConsultantInUseException();
        }
        try {
            if (!repository.delete(companyId, id)) {
                throw new ConsultantNotFoundException();
            }
        } catch (DataIntegrityViolationException e) {
            throw new ConsultantInUseException();
        }
        auditLogger.log(companyId, userId, "travel_consultant_deleted", "travel_consultant", id, Map.of());
        contextCache.invalidate(companyId);
    }

    public List<TravelConsultant> list(UUID companyId, boolean onlyActive) {
        return repository.listByCompany(companyId, onlyActive);
    }

    public Optional<TravelConsultant> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }
}
