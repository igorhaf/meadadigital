package com.meada.whatsapp.ai;

/**
 * Uma troca do histórico de uma conversa, na forma que a IA consome — projeção de
 * leitura, distinta do domínio {@code messages} (que carrega id/direction/etc.).
 *
 * <p>Mora em {@code ai/} porque é conceito do prompt (papel + texto), não do
 * domínio de mensagens. O {@code MessageRepository.findRecentByConversation} mapeia
 * {@code sender} → {@link Role} (contact→USER, ai/human→ASSISTANT — do ponto de
 * vista do modelo, ai e humano são ambos "nosso lado").
 *
 * @param role papel de quem falou
 * @param text conteúdo da mensagem
 */
public record ConversationTurn(Role role, String text) {

    /** Papel numa conversa, do ponto de vista do modelo de IA. */
    public enum Role {
        USER,        // o contato (cliente)
        ASSISTANT    // nosso lado (resposta da IA ou intervenção humana)
    }
}
