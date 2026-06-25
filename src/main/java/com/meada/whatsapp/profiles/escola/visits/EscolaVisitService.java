package com.meada.whatsapp.profiles.escola.visits;

import com.meada.whatsapp.profiles.escola.EscolaContextCache;
import com.meada.whatsapp.profiles.escola.EscolaVisitStatus;
import com.meada.whatsapp.profiles.escola.students.EscolaStudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras das visitas agendadas à escola (camada 8.19, ESCAPADA 2). Agenda LEVE: valida data futura +
 * período (manha|tarde); SEM conflito de capacidade. {@link #create} notifica confirmação;
 * {@link #updateStatus} valida a transição e notifica cancelada.
 */
@Service
public class EscolaVisitService {

    private static final Logger log = LoggerFactory.getLogger(EscolaVisitService.class);
    static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");

    private final EscolaVisitRepository repository;
    private final EscolaStudentRepository studentRepository;
    private final EscolaVisitNotifier notifier;
    private final EscolaContextCache contextCache;

    public EscolaVisitService(EscolaVisitRepository repository,
                              EscolaStudentRepository studentRepository,
                              EscolaVisitNotifier notifier,
                              EscolaContextCache contextCache) {
        this.repository = repository;
        this.studentRepository = studentRepository;
        this.notifier = notifier;
        this.contextCache = contextCache;
    }

    public static class VisitNotFoundException extends RuntimeException {}
    public static class PastDateException extends RuntimeException {}
    public static class InvalidPeriodException extends RuntimeException {}
    public static class StudentNotFoundException extends RuntimeException {}
    public static class InvalidStatusException extends RuntimeException {}
    public static class InvalidStatusTransitionException extends RuntimeException {}

    private static void validatePeriod(String p) {
        if (p == null || (!p.equals("manha") && !p.equals("tarde"))) {
            throw new InvalidPeriodException();
        }
    }

    /**
     * Cria a visita. visit_date >= hoje (America/Sao_Paulo) → senão PastDateException; period in
     * (manha|tarde); studentId opcional (se presente, valida que é do company). Status inicial
     * agendada + notificação de confirmação. visitorName/phone são snapshots do responsável.
     */
    @Transactional
    public EscolaVisit create(UUID companyId, UUID conversationId, UUID contactId, UUID studentId,
                              String visitorName, String visitorPhone, LocalDate visitDate, String period,
                              Integer numPeople, String notes) {
        validatePeriod(period);
        if (visitDate.isBefore(LocalDate.now(TENANT_ZONE))) {
            throw new PastDateException();
        }
        if (studentId != null && studentRepository.findById(companyId, studentId).isEmpty()) {
            throw new StudentNotFoundException();
        }
        EscolaVisit created = repository.insert(companyId, conversationId, contactId, studentId, visitorName,
            visitorPhone, visitDate, period, numPeople, notes);
        notifier.notifyStatus(companyId, conversationId,
            EscolaVisitStatus.AGENDADA.notificationText(created.visitDate().toString(), created.period()));
        contextCache.invalidate(companyId);
        log.info("escola: visita {} agendada p/ conversa {} ({} {})", created.id(), conversationId, visitDate, period);
        return created;
    }

    public List<EscolaVisit> list(UUID companyId, String status, int limit, int offset) {
        return repository.listByCompany(companyId, status, limit, offset);
    }

    public long count(UUID companyId, String status) {
        return repository.countByCompany(companyId, status);
    }

    public Optional<EscolaVisit> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }

    @Transactional
    public EscolaVisit updateStatus(UUID companyId, UUID id, String newStatusId) {
        EscolaVisitStatus newStatus = EscolaVisitStatus.fromId(newStatusId)
            .orElseThrow(InvalidStatusException::new);

        EscolaVisit current = repository.findById(companyId, id).orElseThrow(VisitNotFoundException::new);
        EscolaVisitStatus from = EscolaVisitStatus.fromId(current.status())
            .orElseThrow(InvalidStatusException::new);

        if (!from.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException();
        }

        repository.updateStatus(companyId, id, newStatus.id());

        // notifica cancelada; realizada silenciosa.
        String text = newStatus.notificationText(current.visitDate().toString(), current.period());
        notifier.notifyStatus(companyId, current.conversationId(), text);

        contextCache.invalidate(companyId);
        return repository.findById(companyId, id).orElseThrow(VisitNotFoundException::new);
    }
}
