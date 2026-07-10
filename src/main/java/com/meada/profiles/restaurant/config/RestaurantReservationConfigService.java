package com.meada.profiles.restaurant.config;

import com.meada.common.audit.AuditLogger;
import com.meada.profiles.restaurant.ReservationContextCache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

/**
 * Regras da config de reservas (camada 7.3). Lê com fallback aos defaults; o PUT faz upsert,
 * audita e invalida o {@link ReservationContextCache} (a duração entra no contexto da IA).
 *
 * <p>Mudar a config afeta apenas reservas FUTURAS — a duração é snapshotada em cada reserva no
 * momento da criação (table_reservations.duration_minutes), então reservas já criadas não mudam.
 */
@Service
public class RestaurantReservationConfigService {

    private final RestaurantReservationConfigRepository repository;
    private final AuditLogger auditLogger;
    private final ReservationContextCache contextCache;

    public RestaurantReservationConfigService(RestaurantReservationConfigRepository repository,
                                              AuditLogger auditLogger,
                                              ReservationContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    /** opens_at >= closes_at (janela inválida) (→ 400 invalid_hours). */
    public static class InvalidHoursException extends RuntimeException {}

    public RestaurantReservationConfig get(UUID companyId) {
        return repository.findByCompany(companyId);
    }

    @Transactional
    public RestaurantReservationConfig update(UUID companyId, UUID userId, int durationMinutes,
                                              int bufferMinutes, LocalTime opensAt, LocalTime closesAt,
                                              boolean reminderEnabled, boolean autoCompleteEnabled) {
        if (!opensAt.isBefore(closesAt)) {
            throw new InvalidHoursException();
        }
        RestaurantReservationConfig saved =
            repository.upsert(companyId, durationMinutes, bufferMinutes, opensAt, closesAt,
                reminderEnabled, autoCompleteEnabled);
        auditLogger.log(companyId, userId, "restaurant_config_updated", "restaurant_reservation_config",
            companyId, Map.of("duration_minutes", durationMinutes, "buffer_minutes", bufferMinutes));
        contextCache.invalidate(companyId);
        return saved;
    }
}
