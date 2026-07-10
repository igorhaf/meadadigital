package com.meada.profiles.lavanderia.coupons;

import com.meada.common.coupons.CouponRecord;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Cupom de desconto do tenant lavanderia (camada 8.10, backlog #1 — clone do chassi sushi). DTO de saída.
 * {@code kind} é 'percent' (value 1..100) ou 'fixed' (value em centavos). {@code maxUses}/
 * {@code validUntil} são nullable (sem limite / sem validade). {@code uses} incrementa quando um
 * pedido aplica o cupom.
 */
public record LavanderiaCoupon(
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
