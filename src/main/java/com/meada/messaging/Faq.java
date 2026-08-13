package com.meada.messaging;

/**
 * Pergunta/resposta curada que alimenta o contexto da IA — domínio da tabela
 * {@code faqs}.
 *
 * <p>Fora do record: id, active/deleted_at, company_id, timestamps.
 *
 * @param question pergunta (NOT NULL)
 * @param answer   resposta (NOT NULL)
 */
public record Faq(String question, String answer) {
}
