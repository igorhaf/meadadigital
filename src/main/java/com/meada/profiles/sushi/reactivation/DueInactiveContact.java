package com.meada.profiles.sushi.reactivation;

import java.util.UUID;

/**
 * Contato INATIVO due para a reativação (onda Sushi 2, backlog #3): tem pedido entregue, o último
 * é anterior à janela do tenant e não foi reabordado dentro do cooldown. {@code couponCode} só vem
 * preenchido se o código configurado existe/está ativo/válido em sushi_coupons (senão a mensagem
 * sai sem cupom — nunca prometemos cupom inválido). {@code conversationId} é a conversa mais
 * recente do contato (null = sem canal; marca sem envio).
 */
public record DueInactiveContact(
    UUID companyId,
    UUID contactId,
    String contactName,
    UUID conversationId,
    String couponCode) {
}
