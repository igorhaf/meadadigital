package com.meada.messaging;

import java.util.UUID;

/**
 * Instância da Evolution API resolvida a partir do {@code instance_name} do
 * webhook — domínio (o que a tabela {@code whatsapp_instances} representa),
 * distinto do DTO de transporte {@code EvolutionWebhookPayload}.
 *
 * <p>Carrega só o MÍNIMO usado no fluxo do webhook: o {@code id} da instância e
 * o {@code companyId} (tenant) que ela resolve. NÃO carrega {@code evolution_token}
 * — o webhook não precisa, e omiti-lo do mapeamento é defesa-em-profundidade que
 * espelha o column-grant do schema (o token nunca entra num repositório de leitura
 * do webhook). Demais colunas (status, phone_number) entram só se o fluxo passar
 * a usá-las.
 *
 * @param id        PK da instância em whatsapp_instances
 * @param companyId tenant ao qual a instância pertence
 */
public record WhatsappInstance(UUID id, UUID companyId) {
}
