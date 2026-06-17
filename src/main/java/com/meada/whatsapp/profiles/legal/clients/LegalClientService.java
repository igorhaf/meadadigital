package com.meada.whatsapp.profiles.legal.clients;

import com.meada.whatsapp.common.audit.AuditLogger;
import com.meada.whatsapp.profiles.legal.LegalCaseContextCache;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras de clientes do escritório (camada 7.2). Audita as mutações e invalida o cache de
 * contexto da IA (por contato afetado) a cada gravação.
 */
@Service
public class LegalClientService {

    private final LegalClientRepository repository;
    private final AuditLogger auditLogger;
    private final LegalCaseContextCache contextCache;

    public LegalClientService(LegalClientRepository repository, AuditLogger auditLogger,
                              LegalCaseContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    public static class LegalClientNotFoundException extends RuntimeException {}
    public static class LegalClientInUseException extends RuntimeException {}

    @Transactional
    public LegalClient create(UUID companyId, UUID userId, String name, String email, String phone,
                              String document, UUID contactId, String notes) {
        LegalClient created = repository.insert(companyId, name, email, phone, document, contactId, notes);
        auditLogger.log(companyId, userId, "legal_client_created", "legal_client", created.id(),
            Map.of("name", created.name()));
        contextCache.invalidate(companyId, contactId);
        return created;
    }

    @Transactional
    public LegalClient update(UUID companyId, UUID userId, UUID id, String name, String email,
                              String phone, String document, UUID contactId, boolean contactIdSet,
                              String notes) {
        UUID oldContact = repository.findContactId(companyId, id).orElse(null);
        LegalClient updated = repository.update(companyId, id, name, email, phone, document,
                contactId, contactIdSet, notes)
            .orElseThrow(LegalClientNotFoundException::new);
        auditLogger.log(companyId, userId, "legal_client_updated", "legal_client", id, Map.of());
        // Invalida o contato antigo E o novo (vínculo pode ter mudado).
        contextCache.invalidate(companyId, oldContact);
        if (contactIdSet) {
            contextCache.invalidate(companyId, contactId);
        }
        return updated;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        UUID contactId = repository.findContactId(companyId, id).orElse(null);
        try {
            if (!repository.delete(companyId, id)) {
                throw new LegalClientNotFoundException();
            }
        } catch (DataIntegrityViolationException e) {
            throw new LegalClientInUseException();   // FK restrict: tem processo
        }
        auditLogger.log(companyId, userId, "legal_client_deleted", "legal_client", id, Map.of());
        contextCache.invalidate(companyId, contactId);
    }

    public java.util.List<LegalClient> list(UUID companyId, String search) {
        return repository.listByCompany(companyId, search);
    }

    public Optional<LegalClient> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }
}
