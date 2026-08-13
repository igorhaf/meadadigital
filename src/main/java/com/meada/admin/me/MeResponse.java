package com.meada.admin.me;

import com.meada.admin.security.AdminRole;
import com.meada.admin.security.AuthenticatedUser;

import java.util.UUID;

/**
 * Identidade do usuário logado, consumida pelo frontend para decidir a UI por papel
 * (super-admin vê gestão de empresas; tenant-admin vê área restrita / futura tela do
 * tenant). É a fonte de verdade do papel no frontend (decisão: GET /admin/me).
 *
 * <p>{@code role} serializado como String LOWERCASE ("super_admin" | "tenant_admin"),
 * NÃO o enum {@link AdminRole} (que viraria "SUPER_ADMIN" no JSON). O frontend tipa como
 * union literal {@code "super_admin" | "tenant_admin"}; a conversão fica no factory
 * {@link #from(AuthenticatedUser)}, num lugar só — o controller não conhece o detalhe.
 *
 * <p>{@code paletteId} é SEMPRE presente e não-null (camada 5.0): "meada-default" para
 * super-admin (constante), valor de users.palette_id para tenant-admin. O frontend faz
 * lookup no catálogo de paletas e cai para 'meada-default' se o id não existir.
 *
 * @param email     email do usuário
 * @param role      "super_admin" ou "tenant_admin"
 * @param companyId tenant do usuário; null para super-admin
 * @param paletteId id da paleta de tema; nunca null
 */
public record MeResponse(String email, String role, UUID companyId, String paletteId,
                         String tenantRole, String profileId, String productName) {

    public static MeResponse from(AuthenticatedUser user) {
        return from(user, null);
    }

    /**
     * Variante com perfil (camada 7.0): {@code profileId} é o companies.profile_id do tenant
     * (resolvido pelo controller), {@code productName} é o label do produto correspondente.
     * Para super-admin (sem empresa) o perfil é null e o produto cai para "Meada" (identidade
     * da plataforma). O frontend usa productName no topo do sidebar e profileId para a sidebar
     * dinâmica (estrutura aberta às SM-B/C/D).
     */
    public static MeResponse from(AuthenticatedUser user, String profileId) {
        String role = user.role() == AdminRole.SUPER_ADMIN ? "super_admin" : "tenant_admin";
        String productName = com.meada.profiles.ProfileType.fromId(profileId)
            .map(com.meada.profiles.ProfileType::productName)
            .orElse(com.meada.profiles.ProfileType.GENERIC.productName());
        // tenantRole (owner|admin|agent) só existe para tenant-admin (camada 5.17 #75);
        // null para super-admin. O frontend usa para guards de capacidade.
        return new MeResponse(user.email(), role, user.companyId(), user.paletteId(),
            user.tenantRole(), profileId, productName);
    }
}
