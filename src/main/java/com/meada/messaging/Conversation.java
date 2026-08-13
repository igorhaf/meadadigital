package com.meada.messaging;

import java.util.UUID;

/**
 * Thread de atendimento entre um contato e a empresa — domínio da tabela
 * {@code conversations}.
 *
 * <p>Carrega só o usado no fluxo do webhook: {@code id} (para inserir a message
 * com a FK composta) e {@code companyId} (propagado ao INSERT da message). Os
 * demais campos da tabela (status, handled_by, assigned_user_id, last_message_at)
 * importam para o PAINEL — outra camada, com seu próprio read model. Coerente com
 * {@link WhatsappInstance} e {@link Contact}, que também carregam só o mínimo.
 *
 * @param id        PK da conversation
 * @param companyId tenant
 */
public record Conversation(UUID id, UUID companyId) {
}
