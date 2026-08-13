package com.meada.admin.companies;

import java.time.Instant;
import java.util.UUID;

/**
 * Nota interna do super-admin sobre uma empresa (admin_notes — camada 6.1). NUNCA visível
 * ao tenant (sem policy authenticated; só service_role).
 *
 * <p>superAdminUserId é o auth.users.id do super-admin que escreveu — NÃO resolvemos para
 * email/nome porque o super-admin não tem linha em public.users (allowlist de email). O
 * frontend exibe um rótulo genérico ("Admin") em vez do id.
 *
 * @param id                  id da nota
 * @param superAdminUserId    auth.users.id de quem escreveu (não resolvível p/ nome)
 * @param content             texto
 * @param createdAt           criação
 * @param updatedAt           última edição
 */
public record AdminNoteDto(
    UUID id,
    UUID superAdminUserId,
    String content,
    Instant createdAt,
    Instant updatedAt) {
}
