package com.meada.admin.dashboard;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Agrega os KPIs da plataforma inteira para o hub do super-admin (camada 6.0). Roda como
 * service_role (BYPASSRLS) — diferente das telas do tenant, aqui as contagens são GLOBAIS
 * (todas as empresas), o que é correto para o super-admin. NÃO aplica app.company_id().
 *
 * <p>Queries diretas via JdbcTemplate (padrão do projeto — sem JPA). Cada KPI é uma query
 * agregada simples; nenhuma faz scan caro (counts com índice por status/created_at).
 */
@Service
public class AdminDashboardService {

    private final JdbcTemplate jdbcTemplate;
    private final boolean evolutionDryRun;

    public AdminDashboardService(JdbcTemplate jdbcTemplate,
                                 @Value("${evolution.dry-run:false}") boolean evolutionDryRun) {
        this.jdbcTemplate = jdbcTemplate;
        this.evolutionDryRun = evolutionDryRun;
    }

    public AdminOverviewResponse getOverview() {
        long activeCompanies = count(
            "select count(*) from companies where status = 'active'");
        long companiesThisMonth = count(
            "select count(*) from companies where created_at >= date_trunc('month', now())");
        long messagesToday = count(
            "select count(*) from messages where created_at >= date_trunc('day', now())");
        long messagesYesterday = count(
            "select count(*) from messages "
                + "where created_at >= date_trunc('day', now()) - interval '1 day' "
                + "and created_at < date_trunc('day', now())");
        long openConversations = count(
            "select count(*) from conversations where status = 'open'");
        long openConvCompanies = count(
            "select count(distinct company_id) from conversations where status = 'open'");

        // geminiTokensThisMonth: 0 — tokens não são persistidos (ver javadoc do DTO).
        long geminiTokens = 0L;

        List<AlertDto> alerts = buildAlerts();

        return new AdminOverviewResponse(
            activeCompanies, companiesThisMonth, messagesToday, messagesYesterday,
            openConversations, openConvCompanies, geminiTokens, alerts);
    }

    /**
     * Alertas de plataforma. Baseados SÓ no que é mensurável hoje (sem fabricar dados):
     *  - warning: há instâncias whatsapp marcadas 'disconnected' (Evolution pode ter caído
     *    para esses tenants). Conta as desconectadas; alerta se >= 1.
     *  - error: webhook em modo dry-run (evolution.dry-run=true) — o envio outbound está
     *    suprimido (incidente Baileys, RISKS.md). NÃO há timestamp de "desde quando" no
     *    schema, então o alerta não inventa contagem de dias.
     */
    private List<AlertDto> buildAlerts() {
        List<AlertDto> alerts = new ArrayList<>();

        long disconnected = count(
            "select count(*) from whatsapp_instances where status = 'disconnected'");
        if (disconnected > 0) {
            alerts.add(new AlertDto(
                "warning",
                disconnected + " instância(s) WhatsApp desconectada(s) — Evolution pode estar offline",
                "/dashboard/health"));
        }

        if (evolutionDryRun) {
            alerts.add(new AlertDto(
                "error",
                "Webhook em modo dry-run: o envio de mensagens está desligado",
                "/dashboard/health"));
        }

        return alerts;
    }

    private long count(String sql) {
        Long n = jdbcTemplate.queryForObject(sql, Long.class);
        return n == null ? 0L : n;
    }
}
