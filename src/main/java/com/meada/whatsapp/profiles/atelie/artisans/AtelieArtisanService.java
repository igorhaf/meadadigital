package com.meada.whatsapp.profiles.atelie.artisans;

import com.meada.whatsapp.common.audit.AuditLogger;
import com.meada.whatsapp.profiles.atelie.AtelieContextCache;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Regras dos artesãos do tenant atelie (camada 8.14). Audita + invalida {@link AtelieContextCache}. */
@Service
public class AtelieArtisanService {

    private final AtelieArtisanRepository repository;
    private final AuditLogger auditLogger;
    private final AtelieContextCache contextCache;

    public AtelieArtisanService(AtelieArtisanRepository repository, AuditLogger auditLogger,
                                AtelieContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    public static class ArtisanNotFoundException extends RuntimeException {}
    public static class ArtisanInUseException extends RuntimeException {}

    @Transactional
    public AtelieArtisan create(UUID companyId, UUID userId, String name, String specialty, String notes) {
        AtelieArtisan created = repository.insert(companyId, name, specialty, notes);
        auditLogger.log(companyId, userId, "atelie_artisan_created", "atelie_artisan",
            created.id(), Map.of("name", created.name()));
        contextCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public AtelieArtisan update(UUID companyId, UUID userId, UUID id, String name, String specialty,
                                String notes, Boolean active) {
        AtelieArtisan updated = repository.update(companyId, id, name, specialty, notes, active)
            .orElseThrow(ArtisanNotFoundException::new);
        auditLogger.log(companyId, userId, "atelie_artisan_updated", "atelie_artisan", id, Map.of());
        contextCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public AtelieArtisan toggle(UUID companyId, UUID userId, UUID id, boolean active) {
        AtelieArtisan a = repository.toggle(companyId, id, active).orElseThrow(ArtisanNotFoundException::new);
        auditLogger.log(companyId, userId, "atelie_artisan_updated", "atelie_artisan", id, Map.of("active", active));
        contextCache.invalidate(companyId);
        return a;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        // artisan_id é ON DELETE SET NULL na proposta — checamos uso explicitamente (a FK não barra).
        if (repository.hasProposals(companyId, id)) {
            throw new ArtisanInUseException();
        }
        try {
            if (!repository.delete(companyId, id)) {
                throw new ArtisanNotFoundException();
            }
        } catch (DataIntegrityViolationException e) {
            throw new ArtisanInUseException();
        }
        auditLogger.log(companyId, userId, "atelie_artisan_deleted", "atelie_artisan", id, Map.of());
        contextCache.invalidate(companyId);
    }

    public List<AtelieArtisan> list(UUID companyId, boolean onlyActive) {
        return repository.listByCompany(companyId, onlyActive);
    }

    public Optional<AtelieArtisan> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }
}
