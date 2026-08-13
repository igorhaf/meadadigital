package com.meada.messaging;

import java.util.UUID;

/**
 * Mensagem persistida — domínio da tabela {@code messages} (imutável: só SELECT
 * no RLS; toda escrita via service_role pelo backend).
 *
 * <p>Retorno enxuto: {@code id} (gerado) e {@code companyId}, coerente com os
 * outros domínios. O fluxo do webhook usa o retorno apenas para saber que a
 * mensagem foi de fato inserida (vs. já existia — idempotência).
 *
 * @param id        PK da mensagem
 * @param companyId tenant
 */
public record Message(UUID id, UUID companyId) {
}
