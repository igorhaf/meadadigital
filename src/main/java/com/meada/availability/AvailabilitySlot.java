package com.meada.availability;

import java.time.LocalTime;
import java.util.UUID;

/**
 * Janela agendável de um dia da semana (camada 5.17 #61) — domínio de
 * {@code availability_slots}. weekday 0=domingo..6=sábado. Os slots concretos
 * (ex.: 09:00, 09:30, ...) são gerados pelo backend a partir de [startsAt, endsAt)
 * em passos de slotMinutes.
 *
 * @param id           PK
 * @param companyId    tenant
 * @param weekday      0..6 (0=domingo)
 * @param startsAt     início da janela (HH:MM)
 * @param endsAt       fim da janela (exclusivo)
 * @param slotMinutes  duração de cada slot em minutos
 * @param active       janela ativa
 */
public record AvailabilitySlot(
    UUID id,
    UUID companyId,
    int weekday,
    LocalTime startsAt,
    LocalTime endsAt,
    int slotMinutes,
    boolean active) {
}
