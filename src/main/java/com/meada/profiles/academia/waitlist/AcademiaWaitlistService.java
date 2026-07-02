package com.meada.profiles.academia.waitlist;

import com.meada.common.audit.AuditLogger;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Regras da lista de espera da academia (migration 74). Enfileira, lista com posição derivada e muta
 * o status. Entrar/sair da fila é INDEPENDENTE de matrícula (a vaga só é criada pelo fluxo existente);
 * a fila só ORDENA o interesse por ordem de chegada.
 */
@Service
public class AcademiaWaitlistService {

    /** status válidos da fila (espelham o CHECK da migration). */
    private static final Set<String> VALID_STATUS = Set.of("aguardando", "chamado", "matriculado", "desistiu");

    private final AcademiaWaitlistRepository repository;
    private final AuditLogger auditLogger;

    public AcademiaWaitlistService(AcademiaWaitlistRepository repository, AuditLogger auditLogger) {
        this.repository = repository;
        this.auditLogger = auditLogger;
    }

    /** Entrada não encontrada / de outro tenant (→ 404). */
    public static class EntryNotFoundException extends RuntimeException {}

    /** status alvo fora do conjunto conhecido (→ 400). */
    public static class InvalidStatusException extends RuntimeException {}

    /** Contato já está 'aguardando' nessa aula (unique parcial) (→ 409). */
    public static class AlreadyWaitingException extends RuntimeException {}

    public List<AcademiaWaitlistEntry> list(UUID companyId, UUID classId, boolean onlyWaiting) {
        return repository.listByClass(companyId, classId, onlyWaiting);
    }

    @Transactional
    public AcademiaWaitlistEntry enqueue(UUID companyId, UUID userId, UUID classId, UUID contactId,
                                         String studentName, String studentPhone) {
        AcademiaWaitlistEntry created;
        try {
            created = repository.insert(companyId, classId, contactId, studentName, studentPhone);
        } catch (DuplicateKeyException e) {
            throw new AlreadyWaitingException();
        }
        auditLogger.log(companyId, userId, "academia_waitlist_enqueued", "academia_class_waitlist",
            created.id(), Map.of("class_id", classId.toString()));
        return created;
    }

    @Transactional
    public AcademiaWaitlistEntry updateStatus(UUID companyId, UUID userId, UUID id, String status) {
        if (status == null || !VALID_STATUS.contains(status)) {
            throw new InvalidStatusException();
        }
        AcademiaWaitlistEntry updated;
        try {
            updated = repository.updateStatus(companyId, id, status).orElseThrow(EntryNotFoundException::new);
        } catch (DuplicateKeyException e) {
            // Voltar para 'aguardando' um contato que já tem outra entrada aguardando na mesma aula.
            throw new AlreadyWaitingException();
        }
        auditLogger.log(companyId, userId, "academia_waitlist_status_changed", "academia_class_waitlist",
            id, Map.of("status", status));
        return updated;
    }
}
