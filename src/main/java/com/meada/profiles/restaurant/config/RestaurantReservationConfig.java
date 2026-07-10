package com.meada.profiles.restaurant.config;

import java.time.LocalTime;
import java.util.UUID;

/**
 * Config de reservas do restaurante (camada 7.3) — espelha restaurant_reservation_config.
 * {@code durationMinutes} é quanto tempo uma reserva ocupa a mesa (2h padrão); {@code bufferMinutes}
 * é o intervalo extra entre reservas (0 nesta SM); {@code opensAt}/{@code closesAt} é a janela de
 * funcionamento (a reserva tem de caber inteira nela). Ausente → {@link #defaultFor(UUID)}.
 */
public record RestaurantReservationConfig(
    UUID companyId,
    int durationMinutes,
    int bufferMinutes,
    LocalTime opensAt,
    LocalTime closesAt,
    boolean reminderEnabled,
    boolean autoCompleteEnabled) {

    /** Defaults cravados (decisão 4): 2h de duração, sem buffer, 11:00–23:00. */
    public static RestaurantReservationConfig defaultFor(UUID companyId) {
        return new RestaurantReservationConfig(
            companyId, 120, 0, LocalTime.of(11, 0), LocalTime.of(23, 0), true, true);
    }
}
