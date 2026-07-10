package com.meada.profiles.barbearia.coupons;

import com.meada.common.coupons.CouponRecord;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Cupom de desconto do tenant barbearia (onda 1, backlog #12 — clone do motor adega/atelie). DTO de
 * saída. {@code kind} é 'percent' (value 1..100) ou 'fixed' (value em centavos). {@code minOrderCents}
 * é o mínimo sobre o PREÇO do serviço agendado. {@code uses} incrementa quando um agendamento aplica
 * o cupom (a IA passa o code na tag {@code <agendamento_barbearia>}; inválido não aborta).
 */
public record BarberCoupon(
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
