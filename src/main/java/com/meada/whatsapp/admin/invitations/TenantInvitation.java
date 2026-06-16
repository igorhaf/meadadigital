package com.meada.whatsapp.admin.invitations;

import java.time.Instant;
import java.util.UUID;

/**
 * Convite de admin extra para um tenant (camada 5.16 #6) — domínio da tabela
 * {@code public.tenant_invitations}.
 *
 * @param id        PK do convite
 * @param companyId empresa para a qual o convidado terá acesso
 * @param email     email para quem o convite foi emitido (validado no accept contra o JWT)
 * @param token     segredo aleatório que viaja na URL /invite/{token}
 * @param invitedBy quem criou o convite (auth.users.id); NULLABLE (FK ON DELETE SET NULL)
 * @param createdAt criação
 * @param expiresAt validade (criação + 7d); cancelar = setar para now()
 * @param usedAt    quando foi aceito; NULL = ainda ativo
 * @param usedBy    quem aceitou (auth.users.id); NULL enquanto não usado
 */
public record TenantInvitation(
    UUID id,
    UUID companyId,
    String email,
    String token,
    UUID invitedBy,
    Instant createdAt,
    Instant expiresAt,
    Instant usedAt,
    UUID usedBy) {
}
