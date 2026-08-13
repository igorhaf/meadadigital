package com.meada.admin.plans;

import com.meada.admin.audit.AdminAction;
import com.meada.admin.audit.AdminActionLogger;
import com.meada.admin.plans.PlanDtos.CreatePlanRequest;
import com.meada.admin.plans.PlanDtos.PlanResponse;
import com.meada.admin.plans.PlanDtos.UpdatePlanRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Regras dos planos (camada 6.8). Cada mutação registra no admin_action_log ANTES de mutar,
 * na mesma @Transactional (rastro+efeito atômicos), igual ao UserAdminService.
 */
@Service
public class PlanService {

    private final PlanRepository repository;
    private final AdminActionLogger logger;

    public PlanService(PlanRepository repository, AdminActionLogger logger) {
        this.repository = repository;
        this.logger = logger;
    }

    /** Exceção de não-encontrado (controller → 404). */
    public static class PlanNotFoundException extends RuntimeException {}

    @Transactional
    public PlanResponse create(CreatePlanRequest req, UUID superAdminId) {
        PlanResponse created = repository.insert(req);   // DuplicateKeyException sobe ao controller
        logger.log(superAdminId, AdminAction.PLAN_CREATED, AdminAction.TARGET_PLAN, created.id(),
            Map.of("name", created.name(), "slug", created.slug()));
        return created;
    }

    @Transactional
    public PlanResponse update(UUID id, UpdatePlanRequest req, UUID superAdminId) {
        if (repository.findById(id).isEmpty()) {
            throw new PlanNotFoundException();
        }
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (req.name() != null && !req.name().isBlank()) { sets.add("name = ?"); args.add(req.name().trim()); }
        if (req.slug() != null && !req.slug().isBlank()) { sets.add("slug = ?"); args.add(req.slug().trim()); }
        if (req.monthlyPriceCents() != null) { sets.add("monthly_price_cents = ?"); args.add(req.monthlyPriceCents()); }
        if (req.maxAdmins() != null) { sets.add("max_admins = ?"); args.add(req.maxAdmins()); }
        if (req.maxFaqs() != null) { sets.add("max_faqs = ?"); args.add(req.maxFaqs()); }
        if (req.maxConversationsMonth() != null) { sets.add("max_conversations_month = ?"); args.add(req.maxConversationsMonth()); }
        if (req.maxUsers() != null) { sets.add("max_users = ?"); args.add(req.maxUsers()); }
        if (req.features() != null) { sets.add("features = ?::jsonb"); args.add(repository.featuresJson(req.features())); }
        if (req.active() != null) { sets.add("active = ?"); args.add(req.active()); }

        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(id);
            // DuplicateKeyException (name/slug) sobe ao controller → 409.
            repository.jdbc().update("update plans set " + String.join(", ", sets) + " where id = ?",
                args.toArray());
            logger.log(superAdminId, AdminAction.PLAN_UPDATED, AdminAction.TARGET_PLAN, id,
                Map.of("fields", sets.size() - 1));
        }
        return repository.findById(id).orElseThrow(PlanNotFoundException::new);
    }

    @Transactional
    public void softDelete(UUID id, UUID superAdminId) {
        int n = repository.softDelete(id);
        if (n == 0) {
            throw new PlanNotFoundException();
        }
        logger.log(superAdminId, AdminAction.PLAN_DELETED, AdminAction.TARGET_PLAN, id, Map.of());
    }
}
