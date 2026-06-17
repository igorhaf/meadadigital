package com.meada.whatsapp.profiles.academia.memberships;

import com.meada.whatsapp.profiles.academia.AcademiaContextCache;
import com.meada.whatsapp.profiles.academia.AcademiaMembershipStatus;
import com.meada.whatsapp.profiles.academia.classes.AcademiaClass;
import com.meada.whatsapp.profiles.academia.classes.AcademiaClassRepository;
import com.meada.whatsapp.profiles.academia.plans.AcademiaPlan;
import com.meada.whatsapp.profiles.academia.plans.AcademiaPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras das matrículas da academia (camada 7.7).
 *
 * <p>{@link #create} valida o plano (ativo), as aulas (não-vazias + ativas), e que o contato NÃO
 * tem matrícula ativa (anti-dupla). Delega ao repo, que valida a VAGA POR AULA dentro da transação.
 * Status inicial = ativa; notifica boas-vindas. {@link #updateStatus} valida a transição; em
 * cancelada materializa end_date e libera vagas; notifica ativa/cancelada.
 */
@Service
public class AcademiaMembershipService {

    static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");

    private final AcademiaMembershipRepository membershipRepository;
    private final AcademiaPlanRepository planRepository;
    private final AcademiaClassRepository classRepository;
    private final AcademiaMembershipNotifier notifier;
    private final AcademiaContextCache contextCache;

    public AcademiaMembershipService(AcademiaMembershipRepository membershipRepository,
                                     AcademiaPlanRepository planRepository,
                                     AcademiaClassRepository classRepository,
                                     AcademiaMembershipNotifier notifier,
                                     AcademiaContextCache contextCache) {
        this.membershipRepository = membershipRepository;
        this.planRepository = planRepository;
        this.classRepository = classRepository;
        this.notifier = notifier;
        this.contextCache = contextCache;
    }

    public static class MembershipNotFoundException extends RuntimeException {}
    public static class PlanNotFoundException extends RuntimeException {}
    public static class PlanInactiveException extends RuntimeException {}
    public static class ClassNotFoundException extends RuntimeException {}
    public static class ClassInactiveException extends RuntimeException {}
    public static class NoClassesException extends RuntimeException {}
    public static class AlreadyActiveException extends RuntimeException {}
    public static class InvalidStatusException extends RuntimeException {}
    public static class InvalidStatusTransitionException extends RuntimeException {}

    /** Alguma aula pedida sem vaga (→ 409 class_full). Carrega classId + className. */
    public static class ClassFullException extends RuntimeException {
        private final UUID classId;
        private final String className;

        public ClassFullException(UUID classId, String className) {
            this.classId = classId;
            this.className = className;
        }

        public UUID classId() {
            return classId;
        }

        public String className() {
            return className;
        }
    }

    /**
     * Cria a matrícula. Valida plano ativo + aulas (não-vazias, existentes, ativas) + anti-dupla;
     * delega ao repo (vaga por aula transacional). Status ativa + notificação de boas-vindas.
     */
    @Transactional
    public AcademiaMembership create(UUID companyId, UUID planId, List<UUID> classIds, UUID contactId,
                                     UUID conversationId, String studentName, String studentPhone, String notes) {
        AcademiaPlan plan = planRepository.findById(companyId, planId).orElseThrow(PlanNotFoundException::new);
        if (!plan.active()) {
            throw new PlanInactiveException();
        }
        if (classIds == null || classIds.isEmpty()) {
            throw new NoClassesException();
        }
        List<AcademiaClass> classes = new ArrayList<>();
        for (UUID classId : classIds) {
            AcademiaClass c = classRepository.findById(companyId, classId).orElseThrow(ClassNotFoundException::new);
            if (!c.active()) {
                throw new ClassInactiveException();
            }
            classes.add(c);
        }
        if (contactId != null && membershipRepository.findActiveByContact(companyId, contactId).isPresent()) {
            throw new AlreadyActiveException();
        }
        AcademiaMembership created;
        try {
            created = membershipRepository.insertMembership(companyId, planId, plan.name(), plan.monthlyCents(),
                conversationId, contactId, studentName, studentPhone, notes, classes);
        } catch (AcademiaMembershipRepository.ClassFullException e) {
            throw new ClassFullException(e.classId(), e.className());
        }
        // boas-vindas (status inicial ativa).
        notifier.notifyStatus(companyId, conversationId,
            AcademiaMembershipStatus.ATIVA.notificationText(created.studentName(), created.planName()));
        contextCache.invalidate(companyId);
        return created;
    }

    public List<AcademiaMembership> list(UUID companyId, String status, UUID planId, UUID classId,
                                         UUID contactId, int limit, int offset) {
        return membershipRepository.listByCompany(companyId, status, planId, classId, contactId, limit, offset);
    }

    public long count(UUID companyId, String status, UUID planId, UUID classId, UUID contactId) {
        return membershipRepository.countByCompany(companyId, status, planId, classId, contactId);
    }

    public Optional<AcademiaMembership> get(UUID companyId, UUID id) {
        return membershipRepository.findById(companyId, id);
    }

    @Transactional
    public AcademiaMembership updateStatus(UUID companyId, UUID id, String newStatusId) {
        AcademiaMembershipStatus newStatus = AcademiaMembershipStatus.fromId(newStatusId)
            .orElseThrow(InvalidStatusException::new);

        AcademiaMembership current = membershipRepository.findById(companyId, id)
            .orElseThrow(MembershipNotFoundException::new);
        AcademiaMembershipStatus from = AcademiaMembershipStatus.fromId(current.status())
            .orElseThrow(InvalidStatusException::new);

        if (!from.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException();
        }

        // cancelada materializa end_date = hoje (e libera vagas, pois count filtra por status).
        LocalDate endDate = newStatus == AcademiaMembershipStatus.CANCELADA ? LocalDate.now(TENANT_ZONE) : null;
        membershipRepository.updateStatus(companyId, id, newStatus.id(), endDate);

        // notifica ativa (retomada) / cancelada; suspensa silenciosa.
        String text = newStatus.notificationText(current.studentName(), current.planName());
        notifier.notifyStatus(companyId, current.conversationId(), text);

        contextCache.invalidate(companyId);
        return membershipRepository.findById(companyId, id).orElseThrow(MembershipNotFoundException::new);
    }
}
