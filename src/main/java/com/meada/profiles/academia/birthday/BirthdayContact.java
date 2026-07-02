package com.meada.profiles.academia.birthday;

import java.util.UUID;

/**
 * Contato aniversariante candidato à saudação de aniversário da Academia (backlog #14). Traz só o que
 * o {@link AcademiaAniversarioJob} precisa para montar a mensagem e resolver o canal de envio.
 *
 * @param contactId       id do contato (aluno/tutor)
 * @param companyId       empresa (tenant academia)
 * @param name            nome do contato (usado na saudação; pode vir null/vazio)
 * @param phone           telefone E.164 do contato (canal direto)
 * @param conversationId  conversa mais recente do contato — usada para resolver as credenciais
 *                        Evolution (instância + token); pode ser null se o contato nunca conversou
 */
public record BirthdayContact(
    UUID contactId,
    UUID companyId,
    String name,
    String phone,
    UUID conversationId) {}
