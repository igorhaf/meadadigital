package com.meada.whatsapp.admin.companies;

import java.time.Instant;
import java.util.UUID;

/**
 * Empresa (tenant) na listagem do painel super-admin (GET /admin/companies).
 *
 * <p>Campos escolhidos para a listagem: id, name, slug, status (active|suspended),
 * createdAt e paletteId. {@code updated_at} é omitido — metadado de manutenção sem valor
 * na lista do MVP (adicionar se uma tela futura precisar). {@code createdAt} serializa
 * como ISO-8601 (padrão Jackson para Instant).
 *
 * @param id        id da empresa
 * @param name      nome
 * @param slug      slug único
 * @param status    "active" ou "suspended". NÃO é enum Java de propósito: DTO de saída
 *                  serializado direto do banco; o CHECK constraint em companies.status já
 *                  garante valores válidos; nenhuma decisão Java ramifica por ele aqui.
 *                  Frontend tipa como union literal. (Criar enum CompanyStatus só quando
 *                  alguma lógica Java precisar decidir por status.)
 * @param createdAt criação (timestamptz → Instant)
 * @param paletteId id da paleta de tema da empresa (camada 5.1.a). NOT NULL DEFAULT
 *                  'meada-default' no banco → sempre presente, nunca null no JSON.
 */
public record CompanyResponse(
    UUID id, String name, String slug, String status, Instant createdAt, String paletteId) {
}
