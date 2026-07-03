package com.meada.profiles.comida.coupons;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Cupom de desconto do tenant comida (onda 1 do comida, backlog #1 — clone do chassi sushi). DTO de saída.
 * {@code kind} é 'percent' (value 1..100) ou 'fixed' (value em centavos). {@code maxUses}/
 * {@code validUntil} são nullable (sem limite / sem validade). {@code uses} incrementa quando um
 * pedido aplica o cupom.
 */
public record ComidaCoupon(
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
    Instant updatedAt) {
}
