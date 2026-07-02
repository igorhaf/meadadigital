package com.meada.profiles.academia.referrals;

import java.time.Instant;
import java.util.UUID;

/**
 * Indicação ("indique um amigo") da academia (camada 7.7) — espelha academia_referrals.
 * {@code code} é único por company; {@code rewardPercent} é o desconto LOCAL concedido na
 * conversão (sem cashback/gateway). {@code status} = pendente | convertida | expirada.
 */
public record AcademiaReferral(
    UUID id,
    UUID referrerContactId,
    String referredName,
    String referredPhone,
    String code,
    String status,
    Integer rewardPercent,
    Instant createdAt,
    Instant convertedAt) {
}
