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
 * @param email     email do usuário
 * @param role      "super_admin" ou "tenant_admin"
 * @param companyId tenant do usuário; null para super-admin
 */
public record MeResponse(String email, String role, UUID companyId) {

    public static MeResponse from(AuthenticatedUser user) {
        String role = user.role() == AdminRole.SUPER_ADMIN ? "super_admin" : "tenant_admin";
        return new MeResponse(user.email(), role, user.companyId());
    }
}
