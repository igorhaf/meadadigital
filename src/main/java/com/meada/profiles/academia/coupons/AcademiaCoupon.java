package com.meada.profiles.academia.coupons;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Cupom de desconto do tenant academia (camada 7.7) — espelha academia_coupons. {@code kind} é
 * 'percent' (value 1..100, aplicado no subtotal) ou 'fixed' (value em centavos). {@code maxUses}/
 * {@code validUntil} são nullable (sem limite / sem validade). {@code uses} incrementa quando um
 * cupom é efetivamente aplicado.
 */
public record AcademiaCoupon(
    UUID id,
    UUID companyId,
    String code,
    String kind,
    int value,
    int minCents,
    Integer maxUses,
    int uses,
    LocalDate validUntil,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {
}
