package com.meada.whatsapp.search;

import com.meada.whatsapp.admin.security.AdminRole;
import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Busca global do painel do TENANT (camada 5.22 #84). TENANT-ADMIN ONLY: pesquisa
 * contatos, conversas e mensagens da PRÓPRIA empresa por similaridade textual (pg_trgm).
 * Sob /admin/** (o JwtAuthenticationFilter autentica e popula authenticatedUser).
 *
 * <p>Autorização por role no método (padrão da camada 4, igual InvitationController):
 * super-admin não tem company (companyId null) → 403 forbidden_not_tenant_admin.
 * Isolamento por empresa vem do companyId do próprio authenticatedUser (nunca de input
 * do cliente) — todo WHERE filtra por company_id (defesa em profundidade; o backend é
 * service_role e não aplica RLS).
 *
 * <p>Busca por similaridade: filtro grosseiro via {@code ilike '%q%'} (usa os índices GIN
 * trgm) + ordenação por {@code similarity()} para trazer os matches mais próximos no topo.
 * Cada grupo limita a 10 resultados. q em branco ou com menos de 2 chars → listas vazias
 * (evita varredura inútil e ruído de 1 caractere).
 */
@RestController
public class SearchController {

    /** Mínimo de caracteres para disparar a busca (1 char traz ruído demais). */
    private static final int MIN_QUERY_LENGTH = 2;
    /** Teto de resultados por grupo. */
    private static final int LIMIT = 10;

    private final JdbcTemplate jdbcTemplate;

    public SearchController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Busca global: {contacts, conversations, messages}, cada um top 10 da empresa. */
    @GetMapping("/admin/search")
    public ResponseEntity<Object> search(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(value = "q", required = false) String q) {
        if (user.role() != AdminRole.TENANT_ADMIN || user.companyId() == null) {
            return forbidden();
        }

        String query = q == null ? "" : q.trim();
        if (query.length() < MIN_QUERY_LENGTH) {
            return ResponseEntity.ok(empty());
        }

        UUID companyId = user.companyId();
        Map<String, Object> body = new HashMap<>();
        body.put("contacts", searchContacts(companyId, query));
        body.put("conversations", searchConversations(companyId, query));
        body.put("messages", searchMessages(companyId, query));
        return ResponseEntity.ok(body);
    }

    /** Contatos por nome OU telefone, ordenados por similaridade do nome. */
    private List<Map<String, Object>> searchContacts(UUID companyId, String query) {
        return jdbcTemplate.query(
            "select id, name, phone_number from contacts "
                + "where company_id = ? and deleted_at is null "
                + "and (name ilike '%' || ? || '%' or phone_number ilike '%' || ? || '%') "
                + "order by similarity(coalesce(name, ''), ?) desc limit " + LIMIT,
            (rs, rowNum) -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", rs.getObject("id").toString());
                m.put("name", rs.getString("name"));
                m.put("phoneNumber", rs.getString("phone_number"));
                return m;
            },
            companyId, query, query, query);
    }

    /** Conversas cujo contato bate por nome ou telefone (distinct por conversa). */
    private List<Map<String, Object>> searchConversations(UUID companyId, String query) {
        return jdbcTemplate.query(
            "select distinct c.id, ct.name as contact_name, ct.phone_number "
                + "from conversations c join contacts ct on ct.id = c.contact_id "
                + "where c.company_id = ? "
                + "and (ct.name ilike '%' || ? || '%' or ct.phone_number ilike '%' || ? || '%') "
                + "limit " + LIMIT,
            (rs, rowNum) -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", rs.getObject("id").toString());
                m.put("contactName", rs.getString("contact_name"));
                m.put("phoneNumber", rs.getString("phone_number"));
                return m;
            },
            companyId, query, query);
    }

    /** Mensagens por conteúdo, ordenadas por similaridade do texto. */
    private List<Map<String, Object>> searchMessages(UUID companyId, String query) {
        return jdbcTemplate.query(
            "select m.id, m.conversation_id, m.content from messages m "
                + "join conversations c on c.id = m.conversation_id "
                + "where c.company_id = ? and m.content ilike '%' || ? || '%' "
                + "order by similarity(m.content, ?) desc limit " + LIMIT,
            (rs, rowNum) -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", rs.getObject("id").toString());
                m.put("conversationId", rs.getObject("conversation_id").toString());
                m.put("content", rs.getString("content"));
                return m;
            },
            companyId, query, query);
    }

    /** Corpo vazio (q em branco/curto): mesma forma do resultado real, listas vazias. */
    private Map<String, Object> empty() {
        Map<String, Object> body = new HashMap<>();
        body.put("contacts", List.of());
        body.put("conversations", List.of());
        body.put("messages", List.of());
        return body;
    }

    private ResponseEntity<Object> forbidden() {
        return ResponseEntity.status(403)
            .body(Map.of("error", "Forbidden", "reason", "forbidden_not_tenant_admin"));
    }
}
