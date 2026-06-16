package com.meada.whatsapp.admin.security;

import java.util.UUID;

/**
 * Identidade resolvida do usuário autenticado num request /admin/**. Produzida pelo
 * JwtAuthenticationFilter (decisão B2 eager) e lida pelos controllers via
 * {@code @RequestAttribute("authenticatedUser") AuthenticatedUser user}.
 *
 * <p><b>Invariante</b> (garantida pelo filtro na construção, NÃO validada aqui):
 * <ul>
 *   <li>{@code role == SUPER_ADMIN} ⟹ {@code companyId == null} (super-admin não
 *       pertence a tenant; o filtro nem consulta public.users para ele) E
 *       {@code paletteId == "meada-default"} (constante: super-admin não tem linha em
 *       public.users de onde ler paleta; preferência pessoal de super-admin é fase
 *       futura — decisão Opção A da camada 5.0).
 *   <li>{@code role == TENANT_ADMIN} ⟹ {@code companyId != null} (resolvido do SELECT
 *       em public.users) E {@code paletteId} lido de {@code users.palette_id} na MESMA
 *       query (NOT NULL DEFAULT 'meada-default' no banco → nunca null).
 *   <li>{@code role == INVITEE} ⟹ {@code companyId == null} (camada 5.16 #6: JWT válido
 *       sem linha em public.users ainda; usado só no aceite de convite) E
 *       {@code paletteId == "meada-default"} (constante; sem linha de onde ler).
 * </ul>
 * Não há validação dessa invariante no compact constructor de propósito: o filtro é o
 * único produtor e já a garante; uma assertion aqui seria custo recorrente no hot path
 * de cada request. A invariante é coberta pelos testes do filtro.
 *
 * @param email     email do usuário (claim do JWT)
 * @param userId    id do usuário (claim {@code sub} do JWT = auth.users.id)
 * @param role      papel no painel (ver {@link AdminRole})
 * @param companyId tenant do usuário; null para SUPER_ADMIN
 * @param paletteId id da paleta de tema; "meada-default" para SUPER_ADMIN, lido de
 *                  users.palette_id para TENANT_ADMIN (nunca null)
 */
public record AuthenticatedUser(
    String email, UUID userId, AdminRole role, UUID companyId, String paletteId) {
}
