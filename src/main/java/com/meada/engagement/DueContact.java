package com.meada.engagement;

import java.util.UUID;

/**
 * Contato ELEGÍVEL para reativação (camada 5.21 #81): ficou sem mensagem por
 * {@code reactivation_days} dias ou mais e ainda não foi reativado nesta janela.
 *
 * <p>Carrega o necessário para o {@link ReactivationJob} enviar a mensagem de reativação
 * e marcar o disparo:
 * <ul>
 *   <li>{@code contactId} — para o UPDATE de {@code reactivated_at} (gate de disparo único);
 *   <li>{@code phone} — destinatário do envio Evolution;
 *   <li>{@code conversationId} — a conversa MAIS RECENTE do contato, usada para resolver a
 *       instância (e daí as credenciais) por onde mandar; pode ser null (defensivo) se a
 *       resolução não achar conversa — nesse caso o job marca sem enviar.
 * </ul>
 *
 * @param contactId      contato a reativar
 * @param phone          telefone E.164 do contato (destinatário)
 * @param conversationId conversa mais recente do contato (para resolver a instância); nullable
 */
public record DueContact(UUID contactId, String phone, UUID conversationId) {
}
