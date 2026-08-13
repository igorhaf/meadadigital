package com.meada.lgpd;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Operações LGPD sobre um contato (camada 5.24 #89 erase, #90 export). TENANT-scoped: todo
 * select/delete é escopado por (companyId, contactId) — o controller passa o companyId do
 * authenticatedUser, nunca de input do cliente. O backend opera service_role (fora do RLS),
 * então o escopo por companyId no WHERE é a defesa.
 */
@Service
public class LgpdService {

    private static final Logger log = LoggerFactory.getLogger(LgpdService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public LgpdService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Exporta TODOS os dados de um contato (#90): a linha do contato + suas conversas + todas
     * as mensagens dessas conversas + agendamentos + tags. Estrutura em Map (o controller
     * serializa para JSON). Tudo escopado por (companyId, contactId).
     *
     * @throws ContactNotFoundException se o contato não pertence à empresa.
     */
    public Map<String, Object> exportContact(UUID companyId, UUID contactId) {
        Map<String, Object> contact = jdbcTemplate.query(
                "select id, company_id, phone_number, name, blocked, contact_memory::text "
                    + "as contact_memory, detected_tone, deleted_at, created_at, updated_at "
                    + "from contacts where id = ? and company_id = ?",
                (rs, rowNum) -> {
                    java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", rs.getObject("id").toString());
                    m.put("phoneNumber", rs.getString("phone_number"));
                    m.put("name", rs.getString("name"));
                    m.put("blocked", rs.getBoolean("blocked"));
                    m.put("contactMemory", parseJson(rs.getString("contact_memory")));
                    m.put("detectedTone", rs.getString("detected_tone"));
                    m.put("deletedAt", tsOrNull(rs.getTimestamp("deleted_at")));
                    m.put("createdAt", tsOrNull(rs.getTimestamp("created_at")));
                    m.put("updatedAt", tsOrNull(rs.getTimestamp("updated_at")));
                    return m;
                },
                contactId, companyId)
            .stream().findFirst().orElse(null);

        if (contact == null) {
            throw new ContactNotFoundException(
                "contact " + contactId + " not found in company " + companyId);
        }

        List<Map<String, Object>> conversations = jdbcTemplate.query(
            "select id, status, handled_by, assigned_user_id, last_message_at, "
                + "created_at, updated_at from conversations "
                + "where contact_id = ? and company_id = ? order by created_at",
            (rs, rowNum) -> {
                java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("id", rs.getObject("id").toString());
                m.put("status", rs.getString("status"));
                m.put("handledBy", rs.getString("handled_by"));
                m.put("assignedUserId", uuidOrNull(rs.getObject("assigned_user_id")));
                m.put("lastMessageAt", tsOrNull(rs.getTimestamp("last_message_at")));
                m.put("createdAt", tsOrNull(rs.getTimestamp("created_at")));
                m.put("updatedAt", tsOrNull(rs.getTimestamp("updated_at")));
                return m;
            },
            contactId, companyId);

        // Mensagens de TODAS as conversas do contato (subselect pelas conversas do contato).
        List<Map<String, Object>> messages = jdbcTemplate.query(
            "select m.id, m.conversation_id, m.direction, m.sender, m.content, "
                + "m.evolution_message_id, m.created_at from messages m "
                + "where m.conversation_id in "
                + "(select id from conversations where contact_id = ? and company_id = ?) "
                + "and m.company_id = ? order by m.created_at",
            (rs, rowNum) -> {
                java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("id", rs.getObject("id").toString());
                m.put("conversationId", rs.getObject("conversation_id").toString());
                m.put("direction", rs.getString("direction"));
                m.put("sender", rs.getString("sender"));
                m.put("content", rs.getString("content"));
                m.put("evolutionMessageId", rs.getString("evolution_message_id"));
                m.put("createdAt", tsOrNull(rs.getTimestamp("created_at")));
                return m;
            },
            contactId, companyId, companyId);

        List<Map<String, Object>> appointments = jdbcTemplate.query(
            "select id, conversation_id, service_id, scheduled_at, status, notes, "
                + "created_at, updated_at from appointments "
                + "where contact_id = ? and company_id = ? order by scheduled_at",
            (rs, rowNum) -> {
                java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("id", rs.getObject("id").toString());
                m.put("conversationId", uuidOrNull(rs.getObject("conversation_id")));
                m.put("serviceId", uuidOrNull(rs.getObject("service_id")));
                m.put("scheduledAt", tsOrNull(rs.getTimestamp("scheduled_at")));
                m.put("status", rs.getString("status"));
                m.put("notes", rs.getString("notes"));
                m.put("createdAt", tsOrNull(rs.getTimestamp("created_at")));
                m.put("updatedAt", tsOrNull(rs.getTimestamp("updated_at")));
                return m;
            },
            contactId, companyId);

        // Tags vinculadas às conversas do contato (conversation_tags ⋈ tags).
        List<Map<String, Object>> tags = jdbcTemplate.query(
            "select t.id, t.name, t.color, ct.conversation_id from conversation_tags ct "
                + "join tags t on t.id = ct.tag_id "
                + "where ct.conversation_id in "
                + "(select id from conversations where contact_id = ? and company_id = ?)",
            (rs, rowNum) -> {
                java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("id", rs.getObject("id").toString());
                m.put("name", rs.getString("name"));
                m.put("color", rs.getString("color"));
                m.put("conversationId", rs.getObject("conversation_id").toString());
                return m;
            },
            contactId, companyId);

        java.util.LinkedHashMap<String, Object> export = new java.util.LinkedHashMap<>();
        export.put("contact", contact);
        export.put("conversations", conversations);
        export.put("messages", messages);
        export.put("appointments", appointments);
        export.put("tags", tags);
        return export;
    }

    /**
     * Hard delete REAL do contato e de tudo que dele depende (#89). Apaga em ordem FK-segura.
     *
     * <p><b>Por que a ordem explícita é obrigatória:</b> as FKs de conversations.contact_id →
     * contacts e de messages.conversation_id → conversations são ON DELETE RESTRICT (schema
     * 02_tables.sql). Apagar o contato direto FALHARIA enquanto houver conversas; apagar as
     * conversas falharia enquanto houver mensagens. Por isso apagamos os filhos primeiro:
     * messages → conversation_tags → appointments → conversations → contact. (appointments e
     * conversation_tags têm CASCADE, mas apagamos explícito por clareza/idempotência; com o
     * contato e as conversas indo embora, ambos cairiam de qualquer forma.)
     *
     * <p>Antes de apagar, lê nome/telefone (para o registro de auditoria) e valida que o
     * contato existe na empresa (senão 404). Após apagar, grava uma linha em audit_log com
     * action='lgpd_erase' (action enum de access_logs não cobre erase — por isso audit_log).
     *
     * @throws ContactNotFoundException se o contato não pertence à empresa.
     */
    @Transactional
    public void eraseContact(UUID companyId, UUID contactId, UUID actorUserId) {
        // Lê identidade do contato (nome/telefone) E valida existência na empresa.
        Map<String, Object> identity = jdbcTemplate.query(
                "select phone_number, name from contacts where id = ? and company_id = ?",
                (rs, rowNum) -> {
                    java.util.HashMap<String, Object> m = new java.util.HashMap<>();
                    m.put("phone", rs.getString("phone_number"));
                    m.put("name", rs.getString("name"));
                    return m;
                },
                contactId, companyId)
            .stream().findFirst().orElse(null);

        if (identity == null) {
            throw new ContactNotFoundException(
                "contact " + contactId + " not found in company " + companyId);
        }

        // 1. mensagens das conversas do contato (FK RESTRICT → primeiro).
        jdbcTemplate.update(
            "delete from messages where conversation_id in "
                + "(select id from conversations where contact_id = ? and company_id = ?) "
                + "and company_id = ?",
            contactId, companyId, companyId);
        // 2. tags das conversas do contato (CASCADE, explícito por clareza).
        jdbcTemplate.update(
            "delete from conversation_tags where conversation_id in "
                + "(select id from conversations where contact_id = ? and company_id = ?)",
            contactId, companyId);
        // 3. agendamentos do contato (CASCADE, explícito).
        jdbcTemplate.update(
            "delete from appointments where contact_id = ? and company_id = ?",
            contactId, companyId);
        // 4. conversas do contato (FK RESTRICT do contato → depois das mensagens).
        jdbcTemplate.update(
            "delete from conversations where contact_id = ? and company_id = ?",
            contactId, companyId);
        // 5. o contato.
        jdbcTemplate.update(
            "delete from contacts where id = ? and company_id = ?",
            contactId, companyId);

        // Registro de auditoria do apagamento (action fora do enum de access_logs → audit_log).
        writeAuditErase(companyId, actorUserId, contactId,
            (String) identity.get("name"), (String) identity.get("phone"));
    }

    /**
     * Grava a linha de auditoria do erase em audit_log (service_role). metadata leva nome e
     * telefone do contato apagado (para rastrear O QUE foi removido, já que a linha sumiu).
     */
    private void writeAuditErase(UUID companyId, UUID actorUserId, UUID contactId,
                                 String name, String phone) {
        String metadata;
        try {
            metadata = objectMapper.writeValueAsString(Map.of(
                "name", name == null ? "" : name,
                "phone", phone == null ? "" : phone));
        } catch (JsonProcessingException e) {
            // Inesperado (Map de strings sempre serializa); não bloqueia o erase já efetuado.
            log.warn("falha ao serializar metadata do lgpd_erase (usando {{}}): contact={}",
                contactId, e);
            metadata = "{}";
        }
        jdbcTemplate.update(
            "insert into audit_log (company_id, user_id, action, entity, entity_id, metadata) "
                + "values (?, ?, 'lgpd_erase', 'contact', ?, ?::jsonb)",
            companyId, actorUserId, contactId, metadata);
    }

    // --- helpers de serialização -------------------------------------------------

    private String tsOrNull(java.sql.Timestamp ts) {
        return ts == null ? null : ts.toInstant().toString();
    }

    private String uuidOrNull(Object uuid) {
        return uuid == null ? null : uuid.toString();
    }

    /** Reparseia jsonb (vindo como texto) em objeto para não viajar escapado. null→null. */
    private Object parseJson(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            log.warn("jsonb malformado no export (engolido): {}", json, e);
            return null;
        }
    }
}
