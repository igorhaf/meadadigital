package com.meada.whatsapp.profiles.estetica.procedures;

import com.meada.whatsapp.common.audit.AuditLogger;
import com.meada.whatsapp.profiles.estetica.EsteticaContextCache;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Regras dos procedimentos do tenant estetica (camada 8.3). Audita + invalida {@link EsteticaContextCache}. */
@Service
public class AestheticProcedureService {

    private final AestheticProcedureRepository repository;
    private final AuditLogger auditLogger;
    private final EsteticaContextCache contextCache;

    public AestheticProcedureService(AestheticProcedureRepository repository, AuditLogger auditLogger,
                                     EsteticaContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    public static class ProcedureNotFoundException extends RuntimeException {}
    public static class ProcedureInUseException extends RuntimeException {}

    @Transactional
    public AestheticProcedure create(UUID companyId, UUID userId, String name, String category,
                                     int durationMinutes, int unitPriceCents, String notes) {
        AestheticProcedure created = repository.insert(companyId, name, category, durationMinutes, unitPriceCents, notes);
        auditLogger.log(companyId, userId, "aesthetic_procedure_created", "aesthetic_procedure",
            created.id(), Map.of("name", created.name()));
        contextCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public AestheticProcedure update(UUID companyId, UUID userId, UUID id, String name, String category,
                                     Integer durationMinutes, Integer unitPriceCents, String notes, Boolean active) {
        AestheticProcedure updated = repository.update(companyId, id, name, category, durationMinutes,
            unitPriceCents, notes, active).orElseThrow(ProcedureNotFoundException::new);
        auditLogger.log(companyId, userId, "aesthetic_procedure_updated", "aesthetic_procedure", id, Map.of());
        contextCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public AestheticProcedure toggle(UUID companyId, UUID userId, UUID id, boolean active) {
        AestheticProcedure p = repository.toggle(companyId, id, active).orElseThrow(ProcedureNotFoundException::new);
        auditLogger.log(companyId, userId, "aesthetic_procedure_updated", "aesthetic_procedure", id, Map.of("active", active));
        contextCache.invalidate(companyId);
        return p;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        // FK restrict de appointments E packages barra o delete se houver uso (→ 409 procedure_in_use).
        try {
            if (!repository.delete(companyId, id)) {
                throw new ProcedureNotFoundException();
            }
        } catch (DataIntegrityViolationException e) {
            throw new ProcedureInUseException();
        }
        auditLogger.log(companyId, userId, "aesthetic_procedure_deleted", "aesthetic_procedure", id, Map.of());
        contextCache.invalidate(companyId);
    }

    public List<AestheticProcedure> list(UUID companyId, boolean onlyActive) {
        return repository.listByCompany(companyId, onlyActive);
    }

    public Optional<AestheticProcedure> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }
}
