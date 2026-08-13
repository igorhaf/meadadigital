package com.meada.messaging;

import java.util.UUID;

/**
 * Contato (cliente final) que conversa com a empresa pelo WhatsApp — domínio da
 * tabela {@code contacts}.
 *
 * <p>Carrega só o usado no fluxo do webhook: {@code id} (para criar a conversation),
 * o tenant, o telefone e o nome. NÃO carrega created_at/updated_at/deleted_at —
 * {@code deleted_at} é critério de query (no WHERE), não dado que o domínio expõe.
 *
 * <p>O {@code name} reflete o estado PERSISTIDO no banco (não o último pushName
 * recebido) — ver {@link ContactRepository#resolveOrCreate}.
 *
 * @param id          PK do contato em contacts
 * @param companyId   tenant
 * @param phoneNumber telefone em E.164 (+55...)
 * @param name        nome do contato (pode ser null se nunca veio um pushName)
 * @param blocked     true se o tenant bloqueou o contato (camada 5.11): o webhook
 *                    persiste a inbound mas NÃO dispara a IA (não responde)
 */
public record Contact(UUID id, UUID companyId, String phoneNumber, String name, boolean blocked) {
}
