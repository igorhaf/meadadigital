package com.meada.whatsapp.profiles.pousada.rooms;

import com.meada.whatsapp.common.audit.AuditLogger;
import com.meada.whatsapp.profiles.pousada.PousadaContextCache;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras dos quartos da pousada (camada 7.6). Audita mutações e invalida o
 * {@link PousadaContextCache} — a IA vê a mudança (quartos/preços) na hora.
 */
@Service
public class PousadaRoomService {

    private final PousadaRoomRepository repository;
    private final AuditLogger auditLogger;
    private final PousadaContextCache contextCache;

    public PousadaRoomService(PousadaRoomRepository repository, AuditLogger auditLogger,
                              PousadaContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    /** Quarto não encontrado / de outro tenant (→ 404). */
    public static class RoomNotFoundException extends RuntimeException {}

    /** Quarto referenciado por reserva (FK restrict) — não pode hard-deletar (→ 409). */
    public static class RoomInUseException extends RuntimeException {}

    @Transactional
    public PousadaRoom create(UUID companyId, UUID userId, String name, int capacity,
                              int nightlyRateCents, String description, String notes) {
        PousadaRoom created = repository.insert(companyId, name, capacity, nightlyRateCents, description, notes);
        auditLogger.log(companyId, userId, "pousada_room_created", "pousada_room",
            created.id(), Map.of("name", created.name(), "capacity", created.capacity()));
        contextCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public PousadaRoom update(UUID companyId, UUID userId, UUID id, String name, Integer capacity,
                              Integer nightlyRateCents, String description, String notes, Boolean active) {
        PousadaRoom updated = repository.update(companyId, id, name, capacity, nightlyRateCents, description, notes, active)
            .orElseThrow(RoomNotFoundException::new);
        auditLogger.log(companyId, userId, "pousada_room_updated", "pousada_room", id, Map.of());
        contextCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public PousadaRoom toggle(UUID companyId, UUID userId, UUID id, boolean active) {
        PousadaRoom r = repository.toggle(companyId, id, active)
            .orElseThrow(RoomNotFoundException::new);
        auditLogger.log(companyId, userId, "pousada_room_updated", "pousada_room", id,
            Map.of("active", active));
        contextCache.invalidate(companyId);
        return r;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        try {
            boolean deleted = repository.delete(companyId, id);
            if (!deleted) {
                throw new RoomNotFoundException();
            }
        } catch (DataIntegrityViolationException e) {
            throw new RoomInUseException();
        }
        auditLogger.log(companyId, userId, "pousada_room_deleted", "pousada_room", id, Map.of());
        contextCache.invalidate(companyId);
    }

    public List<PousadaRoom> list(UUID companyId, boolean onlyActive) {
        return repository.listByCompany(companyId, onlyActive);
    }

    public Optional<PousadaRoom> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }
}
