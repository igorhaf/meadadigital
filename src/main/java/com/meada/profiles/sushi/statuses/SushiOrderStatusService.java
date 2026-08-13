package com.meada.profiles.sushi.statuses;

import com.meada.common.audit.AuditLogger;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras dos estados do pedido sushi (camada 7.1 / sushi funcional). CRUD + a regra do ÚNICO
 * inicial: ao criar/editar com {@code is_initial=true}, zera o inicial anterior na MESMA transação
 * (respeitando o índice parcial UNIQUE). Nome duplicado → 409 duplicate_status; delete com pedidos →
 * 409 status_in_use; delete do inicial → 409 initial_status_undeletable. notify_enabled/notify_text
 * são apenas campos editáveis no PATCH (essa É a feature de "notificações").
 */
@Service
public class SushiOrderStatusService {

    private final SushiOrderStatusRepository repository;
    private final AuditLogger auditLogger;

    public SushiOrderStatusService(SushiOrderStatusRepository repository, AuditLogger auditLogger) {
        this.repository = repository;
        this.auditLogger = auditLogger;
    }

    /** Estado não encontrado / de outro tenant (→ 404). */
    public static class StatusNotFoundException extends RuntimeException {}

    /** Nome duplicado (UNIQUE company_id+lower(name)) → 409 duplicate_status. */
    public static class DuplicateStatusException extends RuntimeException {}

    /** Nome inválido (vazio/só espaços) → 400 invalid_status_name. */
    public static class InvalidStatusNameException extends RuntimeException {}

    /** Estado com pedidos (FK restrict) → 409 status_in_use. */
    public static class StatusInUseException extends RuntimeException {}

    /** Não se pode deletar o estado inicial → 409 initial_status_undeletable. */
    public static class InitialStatusUndeletableException extends RuntimeException {}

    @Transactional
    public SushiOrderStatusEntity create(UUID companyId, UUID userId, String name, Integer sortOrder,
                                         Boolean isInitial, Boolean isTerminal, Boolean notifyEnabled,
                                         String notifyText, String color) {
        requireValidName(name);
        boolean initial = Boolean.TRUE.equals(isInitial);
        if (initial) {
            repository.clearInitial(companyId);
        }
        SushiOrderStatusEntity created;
        try {
            created = repository.insert(companyId, name, sortOrder == null ? 0 : sortOrder,
                initial, Boolean.TRUE.equals(isTerminal), Boolean.TRUE.equals(notifyEnabled),
                notifyText, color);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateStatusException();
        }
        auditLogger.log(companyId, userId, "sushi_order_status_created", "sushi_order_status",
            created.id(), Map.of("name", created.name()));
        return created;
    }

    @Transactional
    public SushiOrderStatusEntity update(UUID companyId, UUID userId, UUID id, String name,
                                         Integer sortOrder, Boolean isInitial, Boolean isTerminal,
                                         Boolean notifyEnabled, String notifyText,
                                         boolean notifyTextProvided, String color, boolean colorProvided) {
        if (name != null && !name.isBlank()) {
            requireValidName(name);
        } else if (name != null) {
            throw new InvalidStatusNameException();
        }
        // is_initial=true → zera o inicial anterior antes de gravar este (única transação).
        if (Boolean.TRUE.equals(isInitial)) {
            repository.clearInitial(companyId);
        }
        SushiOrderStatusEntity updated;
        try {
            updated = repository.update(companyId, id, name, sortOrder, isInitial, isTerminal,
                    notifyEnabled, notifyText, notifyTextProvided, color, colorProvided)
                .orElseThrow(StatusNotFoundException::new);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateStatusException();
        }
        auditLogger.log(companyId, userId, "sushi_order_status_updated", "sushi_order_status", id, Map.of());
        return updated;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        SushiOrderStatusEntity status = repository.findById(companyId, id)
            .orElseThrow(StatusNotFoundException::new);
        if (status.isInitial()) {
            throw new InitialStatusUndeletableException();
        }
        if (repository.hasOrders(companyId, id)) {
            throw new StatusInUseException();
        }
        try {
            if (!repository.delete(companyId, id)) {
                throw new StatusNotFoundException();
            }
        } catch (DataIntegrityViolationException e) {
            throw new StatusInUseException();
        }
        auditLogger.log(companyId, userId, "sushi_order_status_deleted", "sushi_order_status", id, Map.of());
    }

    public List<SushiOrderStatusEntity> list(UUID companyId) {
        return repository.listByCompany(companyId);
    }

    public Optional<SushiOrderStatusEntity> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }

    private static void requireValidName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidStatusNameException();
        }
    }
}
