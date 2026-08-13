package com.meada.admin.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.UUID;

/**
 * Registra ações do super-admin no admin_action_log (camada 6). Compartilhado pelas fases
 * 6.1/6.2/6.5: TODA ação destrutiva/sensível chama log() ANTES de efetuar a operação,
 * dentro da mesma @Transactional do serviço — se o log falhar, a operação faz rollback
 * (rastro e efeito são atômicos).
 *
 * <p>Grava via service_role (BYPASSRLS). O payload é serializado para jsonb (?::jsonb).
 */
@Component
public class AdminActionLogger {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AdminActionLogger(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Registra uma ação. payload pode ser null/vazio (vira '{}').
     *
     * @param superAdminId auth.users.id do super-admin (do AuthenticatedUser.userId())
     * @param action       constante de {@link AdminAction}
     * @param targetType   tipo do alvo ("company" | "user" | "invitation" | "note")
     * @param targetId     id do alvo (nullable)
     * @param payload      detalhes da ação (campos alterados, motivo, etc.)
     */
    public void log(UUID superAdminId, String action, String targetType, UUID targetId,
                    Map<String, Object> payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (Exception e) {
            // payload não-serializável não pode mascarar a ação — grava objeto vazio + nota.
            json = "{\"_serialization_error\":true}";
        }
        jdbcTemplate.update(
            "insert into admin_action_log (super_admin_user_id, action, target_type, target_id, payload) "
                + "values (?, ?, ?, ?, ?::jsonb)",
            superAdminId, action, targetType, targetId, json);
    }
}
