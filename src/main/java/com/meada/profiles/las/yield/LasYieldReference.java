package com.meada.profiles.las.yield;

import java.time.Instant;
import java.util.UUID;

/**
 * Referência de rendimento (onda Lãs 1, backlog #2): peça × fio → novelos ESTIMADOS, editada pelo
 * tenant. A IA usa SEMPRE como estimativa explícita; sem referência, diz que não tem.
 */
public record LasYieldReference(
    UUID id,
    String pieceType,
    String yarnSpec,
    int skeins,
    String notes,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {
}
