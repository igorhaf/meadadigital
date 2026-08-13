package com.meada.admin.dashboard;

import java.util.List;

/**
 * Payload do hub do super-admin (camada 6.0) — KPIs agregados da plataforma inteira +
 * alertas. Produzido por {@link AdminDashboardService}; consumido por GET
 * /admin/dashboard/overview.
 *
 * @param activeCompanies              empresas com status 'active'
 * @param companiesCreatedThisMonth    criadas no mês corrente
 * @param messagesToday                mensagens (qualquer direção) hoje
 * @param messagesYesterday            mensagens ontem (para o delta no card)
 * @param openConversations            conversas com status 'open' na plataforma
 * @param openConversationsCompanyCount empresas distintas com conversa aberta
 * @param geminiTokensThisMonth        tokens Gemini no mês. LIMITAÇÃO: hoje sempre 0 —
 *                                     os tokens são calculados por chamada (AiResponse)
 *                                     mas NÃO persistidos em nenhuma coluna/tabela.
 *                                     Computar real exige persistir tokens por mensagem
 *                                     (migration + instrumentar o OutboundService) — fora
 *                                     do escopo desta fase. Retornado 0 honesto, não fake.
 * @param alerts                       lista de alertas (vazia em estado saudável)
 */
public record AdminOverviewResponse(
    long activeCompanies,
    long companiesCreatedThisMonth,
    long messagesToday,
    long messagesYesterday,
    long openConversations,
    long openConversationsCompanyCount,
    long geminiTokensThisMonth,
    List<AlertDto> alerts) {
}
