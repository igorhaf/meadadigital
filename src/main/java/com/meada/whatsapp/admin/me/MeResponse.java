package com.meada.whatsapp.admin.me;

import com.meada.whatsapp.admin.security.AdminRole;
import com.meada.whatsapp.admin.security.AuthenticatedUser;

import java.util.Map;
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
 * <p>{@code features} (camada 9.0): mapa de TODAS as feature flags de plataforma → estado
 * resolvido para o perfil do tenant (ausência de linha = false; default OFF). O frontend usa
 * para gatear UI por feature (ex.: hasFeature('cms')). Para super-admin (sem perfil) vem vazio.
 *
 * @param email     email do usuário
 * @param role      "super_admin" ou "tenant_admin"
 * @param companyId tenant do usuário; null para super-admin
 * @param paletteId id da paleta de tema; nunca null
 * @param features  feature flags resolvidas do nicho ({key → enabled}); {} para super-admin
 */
public record MeResponse(String email, String role, UUID companyId, String paletteId,
                         String tenantRole, String profileId, String productName,
                         Map<String, Boolean> features) {

    public static MeResponse from(AuthenticatedUser user) {
        return from(user, null, Map.of());
    }

    /** Compat (camada 7.0): sem features resolvidas → mapa vazio. */
    public static MeResponse from(AuthenticatedUser user, String profileId) {
        return from(user, profileId, Map.of());
    }

    /**
     * Variante completa (camada 9.0). {@code profileId} é o companies.profile_id do tenant
     * (resolvido pelo controller), {@code productName} o label do produto, {@code features} o mapa
     * de flags resolvidas para o nicho. Para super-admin (sem empresa) o perfil é null, o produto
     * cai para "Meada" e features vem vazio.
     */
    public static MeResponse from(AuthenticatedUser user, String profileId, Map<String, Boolean> features) {
        String role = user.role() == AdminRole.SUPER_ADMIN ? "super_admin" : "tenant_admin";
        String productName = com.meada.whatsapp.profiles.ProfileType.fromId(profileId)
            .map(com.meada.whatsapp.profiles.ProfileType::productName)
            .orElse(com.meada.whatsapp.profiles.ProfileType.GENERIC.productName());
        // tenantRole (owner|admin|agent) só existe para tenant-admin (camada 5.17 #75);
        // null para super-admin. O frontend usa para guards de capacidade.
        return new MeResponse(user.email(), role, user.companyId(), user.paletteId(),
            user.tenantRole(), profileId, productName, features == null ? Map.of() : features);
    }
}
