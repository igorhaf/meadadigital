package com.meada.savedreplies;

import java.time.Instant;
import java.util.UUID;

/**
 * Resposta pronta de uma empresa (camada 5.22 #88). Texto reutilizável que o atendente
 * insere/copia numa conversa. Sem variáveis dinâmicas nesta fase — é só título + corpo.
 *
 * @param id        id da resposta
 * @param companyId tenant dono
 * @param title     título curto (1..80 chars, validado no banco)
 * @param body      corpo (1..2000 chars, validado no banco)
 * @param createdAt criação
 */
public record SavedReply(
    UUID id, UUID companyId, String title, String body, Instant createdAt) {
}
