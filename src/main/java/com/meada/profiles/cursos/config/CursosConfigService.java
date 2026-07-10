package com.meada.profiles.cursos.config;

import com.meada.common.audit.AuditLogger;
import com.meada.profiles.cursos.CursosContextCache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

/**
 * Regras da config do tenant cursos (camada 8.20 / perfil cursos). Lê com fallback aos defaults; o
 * PUT faz upsert, audita e invalida o {@link CursosContextCache}. Análogo ao AcademiaConfigService
 * (camada 7.7) com o campo extra {@code notes}.
 */
@Service
public class CursosConfigService {

    private final CursosConfigRepository repository;
    private final AuditLogger auditLogger;
    private final CursosContextCache contextCache;

    public CursosConfigService(CursosConfigRepository repository, AuditLogger auditLogger,
                               CursosContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    /** opens_at >= closes_at (janela inválida) (→ 400 invalid_hours). */
    public static class InvalidHoursException extends RuntimeException {}

    public CursosConfig get(UUID companyId) {
        return repository.findByCompany(companyId);
    }

    @Transactional
    public CursosConfig update(UUID companyId, UUID userId, LocalTime opensAt, LocalTime closesAt,
                               String notes, boolean nudgeEnabled, int nudgeDays,
                               String certificateBaseUrl) {
        if (!opensAt.isBefore(closesAt)) {
            throw new InvalidHoursException();
        }
        CursosConfig saved = repository.upsert(companyId, opensAt, closesAt, notes,
            nudgeEnabled, Math.min(90, Math.max(1, nudgeDays)),
            certificateBaseUrl == null || certificateBaseUrl.isBlank() ? null : certificateBaseUrl.strip());
        auditLogger.log(companyId, userId, "cursos_config_updated", "cursos_config", companyId, Map.of());
        contextCache.invalidate(companyId);
        return saved;
    }
}
