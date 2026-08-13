package com.meada.admin.companies;

import java.time.Instant;
import java.util.UUID;

/**
 * Detalhe de uma empresa para o drill-down do super-admin (GET /admin/companies/{id} —
 * camada 6.1). Une os metadados da company, os limites do plano (colunas adicionadas na
 * migration 22) e contadores agregados (usuários, contatos, conversas abertas, mensagens
 * dos últimos 30 dias) calculados num punhado de COUNTs escopados por company_id.
 *
 * <p>ownerEmail/ownerName vêm de companies.owner_id → users (LEFT JOIN — owner_id é
 * nullable, ON DELETE SET NULL; empresa sem owner resolvido traz ambos null).
 *
 * <p>maxAdmins/maxFaqs/maxConversationsMonth são Integer (nullable): as colunas de limite
 * são int SEM default (migration 22) — null significa "sem limite configurado".
 *
 * <p>lastActivityAt = max(conversations.last_message_at) da empresa; null se a empresa
 * nunca teve conversa com mensagem.
 *
 * @param id                     id da empresa
 * @param name                   nome
 * @param slug                   slug único
 * @param status                 "active" | "suspended"
 * @param paletteId              paleta de tema
 * @param createdAt              criação
 * @param maxAdmins              limite de admins (nullable = sem limite)
 * @param maxFaqs                limite de FAQs (nullable = sem limite)
 * @param maxConversationsMonth  limite de conversas/mês (nullable = sem limite)
 * @param usersCount             nº de usuários (não soft-deletados) da empresa
 * @param contactsCount          nº de contatos (não soft-deletados) da empresa
 * @param openConversations      nº de conversas com status 'open'
 * @param messagesLast30d        nº de mensagens criadas nos últimos 30 dias
 * @param lastActivityAt         última atividade (max last_message_at), nullable
 * @param ownerEmail             email do dono (companies.owner_id → users), nullable
 * @param ownerName              nome do dono (users.full_name), nullable
 */
public record CompanyDetailDto(
    UUID id,
    String name,
    String slug,
    String status,
    String paletteId,
    Instant createdAt,
    Integer maxAdmins,
    Integer maxFaqs,
    Integer maxConversationsMonth,
    long usersCount,
    long contactsCount,
    long openConversations,
    long messagesLast30d,
    Instant lastActivityAt,
    String ownerEmail,
    String ownerName,
    String profileId) {
}
