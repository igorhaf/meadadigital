package com.meada.messaging;

/**
 * Configuração de comportamento da IA por empresa — domínio da tabela
 * {@code ai_settings} (1:1 com company, garantido pelo UNIQUE(company_id)).
 *
 * <p>Carrega os 5 campos que o PromptBuilder usa. Fora do record: id, company_id
 * (filtro), timestamps. O "system prompt" NÃO é uma coluna — é MONTADO pelo
 * PromptBuilder a partir de tone+systemRules+restrictions+handoffTriggers + o
 * template base.
 *
 * <p>Todos os campos exceto {@code modelProvider} são nullable (o tenant pode não
 * ter configurado). O repositório retorna FIELMENTE o que está no banco — defaults
 * neutros, quando ai_settings está ausente ou com campos null, são aplicados no
 * PromptBuilder, NÃO aqui.
 *
 * @param tone            tom desejado (ex. "formal", "casual"); nullable
 * @param systemRules     regras livres injetadas no prompt; nullable
 * @param restrictions    o que a IA não pode fazer/dizer; nullable
 * @param handoffTriggers quando transferir para humano; nullable
 * @param modelProvider   "gemini" ou "openai"; NOT NULL (default 'gemini' no schema)
 */
public record AiSettings(
    String tone,
    String systemRules,
    String restrictions,
    String handoffTriggers,
    String modelProvider) {
}
