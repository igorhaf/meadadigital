package com.meada.profiles.barbearia.appointments;

import java.time.Instant;
import java.util.UUID;

/**
 * Agendamento de barbearia (camada 8.1) — espelha barber_appointments. {@code barberName}/
 * {@code serviceName}/{@code priceCents}/{@code durationMinutes} são SNAPSHOTS do momento.
 * {@code conversationId}/{@code contactId} nullable (agendamento manual sem WhatsApp). {@code notes}
 * é administrativo (LGPD). Onda 1 do backlog: {@code discountCents} (cupom #12 e/ou fidelidade #3,
 * clampado ao preço; a cobrar = priceCents − discountCents), {@code couponCodeSnapshot} e
 * {@code loyaltyApplied} (true = corte GRÁTIS da fidelidade). Clone de SalonAppointment.
 */
public record BarberAppointment(
    UUID id,
    UUID barberId,
    String barberName,
    UUID serviceId,
    String serviceName,
    UUID conversationId,
    UUID contactId,
    String guestName,
    String guestPhone,
    Instant startAt,
    Instant endAt,
    int durationMinutes,
    Integer priceCents,
    int discountCents,
    String couponCodeSnapshot,
    boolean loyaltyApplied,
    String status,
    String notes,
    Instant createdAt,
    Instant statusUpdatedAt) {
}
