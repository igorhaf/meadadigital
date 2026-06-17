package com.meada.whatsapp.profiles.legal;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cache do bloco de contexto de processos injetado no prompt do ProcessoBot (camada 7.2).
 *
 * <p>Dada uma conversa, resolve o contato → o cliente jurídico vinculado ({@code legal_clients.
 * contact_id}) → os processos desse cliente + andamentos recentes, e monta o bloco de texto.
 * Cacheado por {@code (companyId, contactId)} com TTL 60s (mesma estratégia do SushiMenuCache).
 * {@link LegalCaseService}/{@link com.meada.whatsapp.profiles.legal.clients.LegalClientService}
 * invalidam por contactId ao mutar.
 *
 * <p>Se a conversa não tem contato, ou o contato não está vinculado a nenhum cliente, o bloco
 * orienta a IA a pedir identificação (a persona segue, mas sem lista de processos).
 */
@Component
public class LegalCaseContextCache {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final JdbcTemplate jdbcTemplate;
    private final Cache<String, String> cache;

    public LegalCaseContextCache(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60))
            .maximumSize(1000)
            .build();
    }

    /** Bloco de contexto p/ a conversa (resolve contato internamente). null-safe. */
    public String contextSegment(UUID companyId, UUID contactId) {
        if (contactId == null) {
            return unidentifiedSegment();
        }
        return cache.get(companyId + ":" + contactId, k -> buildSegment(companyId, contactId));
    }

    /** Invalida o cache de um contato específico (mutação de cliente/processo). */
    public void invalidate(UUID companyId, UUID contactId) {
        if (contactId != null) {
            cache.invalidate(companyId + ":" + contactId);
        }
    }

    /** Invalida tudo (usado quando a mutação não sabe o contactId afetado). */
    public void invalidateAll() {
        cache.invalidateAll();
    }

    private String buildSegment(UUID companyId, UUID contactId) {
        // cliente jurídico vinculado a este contato.
        List<Map<String, Object>> clients = jdbcTemplate.queryForList(
            "select id, name from legal_clients where company_id = ? and contact_id = ? limit 1",
            companyId, contactId);
        if (clients.isEmpty()) {
            return unidentifiedSegment();
        }
        UUID clientId = (UUID) clients.get(0).get("id");
        String clientName = (String) clients.get(0).get("name");

        List<Map<String, Object>> cases = jdbcTemplate.queryForList(
            "select id, cnj_number, title, status, court, forum from legal_cases "
                + "where company_id = ? and legal_client_id = ? order by updated_at desc",
            companyId, clientId);

        StringBuilder sb = new StringBuilder();
        sb.append("DADOS DO CLIENTE (identificado pelo telefone):\n")
            .append("Nome: ").append(clientName).append("\n");
        if (cases.isEmpty()) {
            sb.append("Este cliente não tem processos cadastrados no momento.\n\n");
        } else {
            sb.append("Processos:\n");
            int idx = 1;
            for (Map<String, Object> c : cases) {
                UUID caseId = (UUID) c.get("id");
                sb.append("[Processo ").append(idx++).append("]\n")
                    .append("- Número CNJ: ")
                    .append(LegalCnjValidator.format((String) c.get("cnj_number"))).append("\n")
                    .append("- Título: ").append(c.get("title")).append("\n")
                    .append("- Status: ").append(statusLabel((String) c.get("status"))).append("\n");
                String court = (String) c.get("court");
                String forum = (String) c.get("forum");
                if (court != null || forum != null) {
                    sb.append("- Vara/Fórum: ")
                        .append(court != null ? court : "")
                        .append(court != null && forum != null ? ", " : "")
                        .append(forum != null ? forum : "").append("\n");
                }
                // últimos 3 andamentos.
                List<Map<String, Object>> updates = jdbcTemplate.queryForList(
                    "select title, occurred_at from legal_case_updates where legal_case_id = ? "
                        + "order by occurred_at desc limit 3", caseId);
                if (!updates.isEmpty()) {
                    sb.append("- Últimos andamentos:\n");
                    for (Map<String, Object> u : updates) {
                        java.sql.Timestamp occ = (java.sql.Timestamp) u.get("occurred_at");
                        sb.append("  • ")
                            .append(occ.toInstant().atZone(java.time.ZoneId.of("America/Sao_Paulo"))
                                .toLocalDate().format(DATE))
                            .append(": ").append(u.get("title")).append("\n");
                    }
                }
            }
            sb.append("\n");
        }

        sb.append("INSTRUÇÕES:\n")
            .append("Quando o cliente perguntar sobre o processo dele, RESUMA os andamentos recentes "
                + "acima. NUNCA dê opinião ou aconselhamento jurídico — para dúvidas substantivas, "
                + "oriente a 'consultar o advogado responsável'.\n\n");
        return sb.toString();
    }

    private String unidentifiedSegment() {
        return "CLIENTE NÃO IDENTIFICADO: a mensagem veio de um telefone sem cliente cadastrado. "
            + "Peça educadamente que a pessoa se identifique pelo nome completo e CPF/CNPJ, informe "
            + "que vai encaminhar ao advogado responsável, e NÃO forneça dados de processos.\n\n";
    }

    private static String statusLabel(String id) {
        return LegalCaseStatus.fromId(id).map(LegalCaseStatus::label).orElse(id);
    }
}
