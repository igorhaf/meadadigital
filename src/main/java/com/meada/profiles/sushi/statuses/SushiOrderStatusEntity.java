package com.meada.profiles.sushi.statuses;

import java.time.Instant;
import java.util.UUID;

/**
 * Estado de um pedido sushi (camada 7.1 / sushi funcional). DTO de saída — substitui o antigo enum
 * {@code SushiOrderStatus} (DELETADO) + a matriz {@code allowedNext()} + o {@code notificationText()}.
 * Agora é dado gerenciável: {@code isInitial} (≤1 por company, onde o pedido nasce), {@code isTerminal}
 * (sem transições de saída), {@code notifyEnabled}+{@code notifyText} (a mensagem ao ENTRAR no estado),
 * {@code color}. Transição LIVRE entre não-terminais (mirror do perfil legal).
 */
public record SushiOrderStatusEntity(
    UUID id,
    UUID companyId,
    String name,
    int sortOrder,
    boolean isInitial,
    boolean isTerminal,
    boolean notifyEnabled,
    String notifyText,
    String color,
    Instant createdAt,
    Instant updatedAt) {
}
