package com.meada.whatsapp.teams;

import java.time.Instant;
import java.util.UUID;

/**
 * Time/departamento de um tenant (camada 5.20 #76) — domínio da tabela {@code public.teams}.
 * Uma conversa pode ser atribuída a um time ({@code conversations.team_id}). Sem hierarquia
 * entre times (decisão cravada na migration 22).
 *
 * @param id        PK do time
 * @param companyId empresa dona do time (isolamento por RLS no banco; o backend opera como
 *                  service_role e filtra por companyId no WHERE — defesa em profundidade)
 * @param name      nome do time (1..60 chars — CHECK no banco)
 * @param createdAt criação
 */
public record Team(
    UUID id,
    UUID companyId,
    String name,
    Instant createdAt) {
}
