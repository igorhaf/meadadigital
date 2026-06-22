package com.meada.whatsapp.profiles.estetica.packages;

import com.meada.whatsapp.profiles.estetica.AestheticPackageStatus;
import com.meada.whatsapp.profiles.estetica.EsteticaContextCache;
import com.meada.whatsapp.profiles.estetica.procedures.AestheticProcedure;
import com.meada.whatsapp.profiles.estetica.procedures.AestheticProcedureRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras dos pacotes multi-sessão do tenant estetica (camada 8.3) — A ESCAPADA.
 *
 * <p>{@link #create} snapshota o procedimento (name + unit_price do CATÁLOGO — a IA/tag não manda
 * preço), materializa total_cents = total_sessions * unit_price e sessions_remaining = total_sessions,
 * e abre em 'pendente'. {@link #updateStatus} valida a transição manual; pendente→ativo materializa
 * activated_at e NOTIFICA o cliente. O consumo/devolução de saldo NÃO mora aqui — é o
 * AestheticAppointmentService que o faz transacionalmente via o repositório.
 */
@Service
public class AestheticPackageService {

    private final AestheticPackageRepository repository;
    private final AestheticProcedureRepository procedureRepository;
    private final AestheticPackageNotifier notifier;
    private final EsteticaContextCache contextCache;

    public AestheticPackageService(AestheticPackageRepository repository,
                                   AestheticProcedureRepository procedureRepository,
                                   AestheticPackageNotifier notifier,
                                   EsteticaContextCache contextCache) {
        this.repository = repository;
        this.procedureRepository = procedureRepository;
        this.notifier = notifier;
        this.contextCache = contextCache;
    }

    public static class PackageNotFoundException extends RuntimeException {}
    public static class ProcedureNotFoundException extends RuntimeException {}
    public static class InvalidSessionsException extends RuntimeException {}
    public static class InvalidStatusException extends RuntimeException {}
    public static class InvalidStatusTransitionException extends RuntimeException {}

    /**
     * Cria um pacote em 'pendente'. Snapshot do procedimento (name + unit_price do catálogo).
     * total_cents/sessions_remaining materializados. customerName: do contact (resolvido pelo caller)
     * ou override.
     */
    @Transactional
    public AestheticPackage create(UUID companyId, UUID contactId, String customerName, String customerPhone,
                                   UUID procedureId, UUID conversationId, int totalSessions, String notes) {
        if (totalSessions <= 0) {
            throw new InvalidSessionsException();
        }
        AestheticProcedure procedure = procedureRepository.findById(companyId, procedureId)
            .orElseThrow(ProcedureNotFoundException::new);
        AestheticPackage created = repository.insert(companyId, contactId, procedureId, conversationId,
            customerName, customerPhone, procedure.name(), procedure.unitPriceCents(), totalSessions, notes);
        contextCache.invalidate(companyId);
        return created;
    }

    public List<AestheticPackage> list(UUID companyId, String status, UUID contactId, UUID procedureId,
                                       int limit, int offset) {
        return repository.listByCompany(companyId, status, contactId, procedureId, limit, offset);
    }

    public long count(UUID companyId, String status, UUID contactId, UUID procedureId) {
        return repository.countByCompany(companyId, status, contactId, procedureId);
    }

    public Optional<AestheticPackage> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }

    @Transactional
    public AestheticPackage updateStatus(UUID companyId, UUID id, String newStatusId) {
        AestheticPackageStatus newStatus = AestheticPackageStatus.fromId(newStatusId).orElseThrow(InvalidStatusException::new);
        AestheticPackage current = repository.findById(companyId, id).orElseThrow(PackageNotFoundException::new);
        AestheticPackageStatus from = AestheticPackageStatus.fromId(current.status()).orElseThrow(InvalidStatusException::new);

        if (!from.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException();
        }

        repository.updateStatus(companyId, id, newStatus.id());

        String text = newStatus.notificationText(current.procedureName(), current.totalSessions());
        notifier.notifyStatus(companyId, current.conversationId(), text);

        contextCache.invalidate(companyId);
        return repository.findById(companyId, id).orElseThrow(PackageNotFoundException::new);
    }
}
