package com.meada.profiles.atelie.coupons;

import com.meada.common.coupons.CouponRecord;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Cupom de desconto do tenant atelie (onda 2, backlog #13 — clone do motor sushi/academia/adega).
 * DTO de saída. {@code kind} é 'percent' (value 1..100) ou 'fixed' (value em centavos).
 * {@code minOrderCents} é o mínimo sobre o TOTAL do orçamento da proposta. {@code uses} incrementa
 * quando uma proposta aplica o cupom e decrementa quando remove.
 */
public record AtelieCoupon(
    UUID id,
    UUID companyId,
    String code,
    String kind,
    int value,
    int minOrderCents,
    Integer maxUses,
    int uses,
    LocalDate validUntil,
    boolean active,
    Instant createdAt,
    Instant updatedAt) implements CouponRecord {
}
