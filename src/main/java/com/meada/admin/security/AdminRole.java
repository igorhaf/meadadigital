package com.meada.admin.security;

/**
 * Papel de autenticação no PAINEL ADMIN. Determinado pelo JwtAuthenticationFilter:
 * email na allowlist → {@link #SUPER_ADMIN}; senão, com linha em public.users →
 * {@link #TENANT_ADMIN}.
 *
 * <p>NÃO confundir com {@code users.role} do schema (owner|admin|agent), que é o papel
 * do usuário DENTRO do seu tenant. Estes dois valores são sobre acesso ao painel: quem
 * gerencia a plataforma (super-admin meada) vs quem administra um tenant. Por isso não
 * há mapeamento para coluna do banco (super-admin nem tem linha em public.users).
 */
public enum AdminRole {
    SUPER_ADMIN,
    TENANT_ADMIN,

    /**
     * JWT do Supabase Auth válido, mas SEM provisionamento em public.users (camada 5.16
     * #6). Usado SÓ no fluxo de aceite de convite (POST /api/invitations/{token}/accept):
     * o convidado acabou de criar conta no Auth e a linha em users só nasce no accept.
     * companyId é null (ainda não pertence a nenhum tenant). Não tem acesso a /admin/**.
     */
    INVITEE,
}
