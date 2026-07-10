package com.meada.profiles.fotografia.packages;

import com.meada.common.audit.AuditLogger;
import com.meada.profiles.fotografia.FotografiaContextCache;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras dos pacotes (camada 8.16). Valida a duração (15..1440 → 400 invalid_duration) e o preço/prazo
 * (>= 0 → 400 invalid_price / invalid_delivery_days), audita e invalida o {@link FotografiaContextCache}.
 * DELETE protegido por FK (sessão via restrict) → 409 package_in_use. Espelho do
 * DermatologiaProcedureTypeService.
 */
@Service
public class FotografiaPackageService {

    private static final int MIN_DURATION = 15;
    private static final int MAX_DURATION = 1440;

    private final FotografiaPackageRepository repository;
    private final AuditLogger auditLogger;
    private final FotografiaContextCache contextCache;

    public FotografiaPackageService(FotografiaPackageRepository repository, AuditLogger auditLogger,
                                    FotografiaContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    public static class PackageNotFoundException extends RuntimeException {}
    public static class PackageInUseException extends RuntimeException {}
    public static class InvalidDurationException extends RuntimeException {}
    public static class InvalidPriceException extends RuntimeException {}
    public static class InvalidDeliveryDaysException extends RuntimeException {}

    @Transactional
    public FotografiaPackage create(UUID companyId, UUID userId, String name, String category, int durationMinutes,
                                    int priceCents, int deliveryDays, String notes, boolean suggestible) {
        requireValidDuration(durationMinutes);
        requireValidPrice(priceCents);
        requireValidDeliveryDays(deliveryDays);
        FotografiaPackage created = repository.insert(companyId, name, category, durationMinutes,
            priceCents, deliveryDays, notes, suggestible);
        auditLogger.log(companyId, userId, "fotografia_package_created", "fotografia_package",
            created.id(), Map.of("name", created.name()));
        contextCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public FotografiaPackage update(UUID companyId, UUID userId, UUID id, String name, String category,
                                    Integer durationMinutes, Integer priceCents, Integer deliveryDays,
                                    String notes, Boolean active, Boolean suggestible) {
        if (durationMinutes != null) {
            requireValidDuration(durationMinutes);
        }
        if (priceCents != null) {
            requireValidPrice(priceCents);
        }
        if (deliveryDays != null) {
            requireValidDeliveryDays(deliveryDays);
        }
        FotografiaPackage updated = repository.update(companyId, id, name, category, durationMinutes,
            priceCents, deliveryDays, notes, active, suggestible).orElseThrow(PackageNotFoundException::new);
        auditLogger.log(companyId, userId, "fotografia_package_updated", "fotografia_package", id, Map.of());
        contextCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public FotografiaPackage toggle(UUID companyId, UUID userId, UUID id, boolean active) {
        FotografiaPackage p = repository.toggle(companyId, id, active).orElseThrow(PackageNotFoundException::new);
        auditLogger.log(companyId, userId, "fotografia_package_updated", "fotografia_package", id, Map.of("active", active));
        contextCache.invalidate(companyId);
        return p;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        try {
            if (!repository.delete(companyId, id)) {
                throw new PackageNotFoundException();
            }
        } catch (DataIntegrityViolationException e) {
            throw new PackageInUseException();
        }
        auditLogger.log(companyId, userId, "fotografia_package_deleted", "fotografia_package", id, Map.of());
        contextCache.invalidate(companyId);
    }

    public List<FotografiaPackage> list(UUID companyId, boolean onlyActive) {
        return repository.listByCompany(companyId, onlyActive);
    }

    public Optional<FotografiaPackage> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }

    private static void requireValidDuration(int durationMinutes) {
        if (durationMinutes < MIN_DURATION || durationMinutes > MAX_DURATION) {
            throw new InvalidDurationException();
        }
    }

    private static void requireValidPrice(int priceCents) {
        if (priceCents < 0) {
            throw new InvalidPriceException();
        }
    }

    private static void requireValidDeliveryDays(int deliveryDays) {
        if (deliveryDays < 0) {
            throw new InvalidDeliveryDaysException();
        }
    }
}
