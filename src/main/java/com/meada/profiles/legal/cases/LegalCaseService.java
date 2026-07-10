package com.meada.profiles.legal.cases;

import com.meada.common.audit.AuditLogger;
import com.meada.profiles.legal.LegalCaseContextCache;
import com.meada.profiles.legal.LegalCaseStatus;
import com.meada.profiles.legal.LegalCnjValidator;
import com.meada.profiles.legal.clients.LegalClientRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras dos processos (camada 7.2). Valida CNJ (mód 97), audita, dispara notificação outbound
 * na mudança de status e invalida o cache de contexto da IA (pelo contato do cliente).
 */
@Service
public class LegalCaseService {

    private final LegalCaseRepository repository;
    private final LegalClientRepository clientRepository;
    private final AuditLogger auditLogger;
    private final LegalCaseNotifier notifier;
    private final com.meada.profiles.legal.config.LegalConfigRepository legalConfigRepository;
    private final LegalCaseContextCache contextCache;

    public LegalCaseService(LegalCaseRepository repository, LegalClientRepository clientRepository,
                            AuditLogger auditLogger, LegalCaseNotifier notifier,
                            LegalCaseContextCache contextCache,
                            com.meada.profiles.legal.config.LegalConfigRepository legalConfigRepository) {
        this.repository = repository;
        this.clientRepository = clientRepository;
        this.auditLogger = auditLogger;
        this.notifier = notifier;
        this.legalConfigRepository = legalConfigRepository;
        this.contextCache = contextCache;
    }

    public static class LegalCaseNotFoundException extends RuntimeException {}
    public static class InvalidCnjException extends RuntimeException {}
    public static class DuplicateCnjException extends RuntimeException {}
    public static class InvalidStatusException extends RuntimeException {}
    public static class LegalClientNotFoundException extends RuntimeException {}

    @Transactional
    public LegalCase create(UUID companyId, UUID userId, UUID legalClientId, String rawCnj,
                            String title, String description, String court, String forum, String subject) {
        if (!LegalCnjValidator.isValid(rawCnj)) {
            throw new InvalidCnjException();
        }
        if (clientRepository.findById(companyId, legalClientId).isEmpty()) {
            throw new LegalClientNotFoundException();
        }
        String cnj = LegalCnjValidator.normalize(rawCnj);
        try {
            LegalCase created = repository.insert(companyId, legalClientId, cnj, title, description,
                court, forum, subject);
            auditLogger.log(companyId, userId, "legal_case_created", "legal_case", created.id(),
                Map.of("cnj", cnj));
            invalidateClientContext(companyId, legalClientId);
            return created;
        } catch (DuplicateKeyException e) {
            throw new DuplicateCnjException();
        }
    }

    @Transactional
    public LegalCase update(UUID companyId, UUID userId, UUID id, String title, String description,
                            String court, String forum, String subject) {
        LegalCase updated = repository.update(companyId, id, title, description, court, forum, subject)
            .orElseThrow(LegalCaseNotFoundException::new);
        auditLogger.log(companyId, userId, "legal_case_updated", "legal_case", id, Map.of());
        invalidateClientContext(companyId, updated.legalClientId());
        return updated;
    }

    @Transactional
    public LegalCase updateStatus(UUID companyId, UUID userId, UUID id, String newStatusId) {
        LegalCaseStatus newStatus = LegalCaseStatus.fromId(newStatusId)
            .orElseThrow(InvalidStatusException::new);
        LegalCase current = repository.findById(companyId, id)
            .orElseThrow(LegalCaseNotFoundException::new);

        repository.updateStatus(companyId, id, newStatus.id());
        auditLogger.log(companyId, userId, "legal_case_status_changed", "legal_case", id,
            Map.of("from", current.status(), "to", newStatus.id()));

        // Notificação outbound (best-effort; 'ativo' não notifica).
        notifier.notifyStatus(companyId, current.legalClientId(), newStatus.notificationText());

        // Onda 1 (backlog #3): o ENCERRAMENTO encadeia agradecimento + avaliação + indicação
        // (comunicação de relacionamento — trava jurídica intacta).
        if (newStatus == LegalCaseStatus.ENCERRADO) {
            var config = legalConfigRepository.findByCompany(companyId);
            if (config.postClosureEnabled()) {
                StringBuilder pos = new StringBuilder("Foi uma honra cuidar do seu caso até aqui. "
                    + "Obrigado pela confiança! ");
                if (config.reviewLink() != null) {
                    pos.append("Se puder, deixe sua avaliação — ajuda muito o escritório: ")
                        .append(config.reviewLink()).append(" ");
                }
                pos.append("E se conhecer alguém precisando de orientação jurídica, "
                    + "ficaremos felizes com a indicação. 🙏");
                notifier.notifyStatus(companyId, current.legalClientId(), pos.toString());
            }
        }
        invalidateClientContext(companyId, current.legalClientId());
        return repository.findById(companyId, id).orElseThrow(LegalCaseNotFoundException::new);
    }

    @Transactional
    public LegalCaseUpdate addUpdate(UUID companyId, UUID userId, UUID caseId, String title,
                                     String body, Instant occurredAt) {
        UUID clientId = repository.findClientId(companyId, caseId)
            .orElseThrow(LegalCaseNotFoundException::new);
        LegalCaseUpdate created = repository.insertUpdate(caseId, title, body,
            occurredAt != null ? occurredAt : Instant.now());
        auditLogger.log(companyId, userId, "legal_case_update_added", "legal_case", caseId,
            Map.of("updateId", created.id().toString()));
        invalidateClientContext(companyId, clientId);
        return created;
    }

    @Transactional
    public void removeUpdate(UUID companyId, UUID userId, UUID caseId, UUID updateId) {
        UUID clientId = repository.findClientId(companyId, caseId)
            .orElseThrow(LegalCaseNotFoundException::new);
        if (!repository.deleteUpdate(caseId, updateId)) {
            throw new LegalCaseNotFoundException();
        }
        auditLogger.log(companyId, userId, "legal_case_update_removed", "legal_case", caseId, Map.of());
        invalidateClientContext(companyId, clientId);
    }

    public List<LegalCase> list(UUID companyId, String status, String search, int limit, int offset) {
        return repository.listByCompany(companyId, status, search, limit, offset);
    }

    public long count(UUID companyId, String status, String search) {
        return repository.countByCompany(companyId, status, search);
    }

    public Optional<LegalCase> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }

    /** Invalida o contexto da IA do contato vinculado ao cliente do processo. */
    private void invalidateClientContext(UUID companyId, UUID legalClientId) {
        UUID contactId = clientRepository.findContactId(companyId, legalClientId).orElse(null);
        contextCache.invalidate(companyId, contactId);
    }
}
