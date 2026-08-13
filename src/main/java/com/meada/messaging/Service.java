package com.meada.messaging;

/**
 * Serviço/produto do catálogo que a IA pode citar — domínio da tabela
 * {@code services}.
 *
 * <p>Carrega o que o prompt usa. Fora do record: id (o prompt cita por nome, não
 * por FK), active/deleted_at (critérios de query), company_id (filtro), timestamps.
 *
 * @param name        nome do serviço (NOT NULL)
 * @param description descrição; nullable
 * @param priceCents  preço em centavos; nullable (serviço pode não ter preço fixo)
 */
public record Service(String name, String description, Integer priceCents) {
}
