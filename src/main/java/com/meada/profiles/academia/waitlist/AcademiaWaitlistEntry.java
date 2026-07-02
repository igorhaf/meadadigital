package com.meada.profiles.academia.waitlist;

import java.time.Instant;
import java.util.UUID;

/**
 * Entrada na lista de espera de uma aula da academia (migration 74). {@code position} NÃO é coluna
 * do banco — é DERIVADA por query (count de 'aguardando' com {@code enqueuedAt} menor + 1), no
 * espírito da fila-com-posição-derivada da BarberQueue (camada 8.1). Só é significativa para
 * entradas com status {@code aguardando}.
 */
public record AcademiaWaitlistEntry(
    UUID id,
    UUID classId,
    UUID contactId,
    String studentName,
    String studentPhone,
    String status,
    Instant enqueuedAt,
    int position) {
}
