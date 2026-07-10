package com.meada.profiles.oficina.reminders;

import java.time.LocalDate;
import java.util.UUID;

/**
 * OS entregue cujo retorno sugerido venceu (onda Oficina 1, backlog #2). Carrega o mínimo pro
 * texto ("faz X meses do último serviço no {modelo/placa}") e o canal.
 */
public record DueReturn(
    UUID orderId,
    UUID companyId,
    UUID conversationId,
    String customerName,
    String vehiclePlate,
    String vehicleModel,
    LocalDate nextReturnDate) {
}
