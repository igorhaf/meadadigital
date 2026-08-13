package com.meada.admin.users;

import com.meada.admin.audit.AdminAction;
import com.meada.admin.audit.AdminActionLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Operações do super-admin sobre usuários globais (camada 6.2). Toda ação destrutiva
 * registra no admin_action_log ANTES de mutar, dentro da mesma @Transactional (rastro e
 * efeito atômicos — se o log falhar, a mutação faz rollback).
 */
@Service
public class UserAdminService {

    private final UserAdminRepository repository;
    private final AdminActionLogger logger;

    public UserAdminService(UserAdminRepository repository, AdminActionLogger logger) {
        this.repository = repository;
        this.logger = logger;
    }

    public UserPage list(String q, UUID companyId, String role, Boolean suspended,
                         int page, int pageSize) {
        long total = repository.count(q, companyId, role, suspended);
        var items = repository.findPage(q, companyId, role, suspended, page, pageSize);
        return new UserPage(items, total, page, pageSize);
    }

    public Optional<UserAdminRepository.UserDetail> detail(UUID id) {
        return repository.findDetail(id);
    }

    public java.util.List<UserAdminRepository.RecentAction> recentActions(UUID id) {
        return repository.recentActions(id, 20);
    }

    /** @return false se o usuário não existe; lança {@link AlreadySuspendedException} se já suspenso. */
    @Transactional
    public boolean suspend(UUID superAdminId, UUID userId, String reason) {
        if (!repository.exists(userId)) {
            return false;
        }
        if (repository.isSuspended(userId)) {
            throw new AlreadySuspendedException();
        }
        logger.log(superAdminId, AdminAction.USER_SUSPENDED, AdminAction.TARGET_USER, userId,
            reason == null ? Map.of() : Map.of("reason", reason));
        repository.setSuspended(userId, true, reason);
        return true;
    }

    @Transactional
    public boolean reactivate(UUID superAdminId, UUID userId) {
        if (!repository.exists(userId)) {
            return false;
        }
        logger.log(superAdminId, AdminAction.USER_REACTIVATED, AdminAction.TARGET_USER, userId, Map.of());
        repository.setSuspended(userId, false, null);
        return true;
    }

    @Transactional
    public boolean softDelete(UUID superAdminId, UUID userId) {
        if (!repository.exists(userId)) {
            return false;
        }
        logger.log(superAdminId, AdminAction.USER_DELETED, AdminAction.TARGET_USER, userId, Map.of());
        repository.softDelete(userId);
        return true;
    }

    /** Suspensão idempotente: já suspenso → 409. */
    public static class AlreadySuspendedException extends RuntimeException {
        public AlreadySuspendedException() {
            super("user already suspended");
        }
    }

    /** Página de usuários. */
    public record UserPage(java.util.List<UserAdminRepository.UserListItem> items,
                           long total, int page, int pageSize) {
    }
}
