package com.meada.common.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Registra ações sensíveis em public.audit_log a partir do backend (caminho service_role).
 *
 * <p>Usado para ações que NÃO passam pelo SDK do tenant — tipicamente operações do
 * super-admin via REST (ex.: company.created no CompanyAdminController). As mutations que
 * o tenant faz via SDK (services/faqs/ai_settings/conversations) são auditadas por trigger
 * Postgres (app.audit_trigger), não por esta classe.
 *
 * <p><b>NÃO-BLOQUEANTE por contrato:</b> auditoria nunca pode quebrar o fluxo principal.
 * Se o INSERT de audit falhar (banco indisponível, constraint inesperada), o erro é
 * logado em WARN e engolido — a ação de negócio que já aconteceu (a empresa foi criada)
 * não é revertida por uma falha de log. Por isso o try/catch amplo e o retorno void.
 */
@Component
public class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);

    private static final String INSERT =
        "insert into audit_log (company_id, user_id, action, entity, entity_id, metadata) "
        + "values (?, ?, ?, ?, ?, ?::jsonb)";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AuditLogger(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Insere uma linha de audit. NÃO lança: qualquer falha vira WARN e é engolida
     * (auditoria não quebra fluxo de negócio).
     *
     * @param companyId tenant da ação (NOT NULL no schema)
     * @param userId    autor; null aceito (super-admin não tem linha em users — mas é o
     *                  auth.users.id do JWT, que existe)
     * @param action    verbo curto: "created", "updated", etc.
     * @param entity    entidade afetada: "company", etc.
     * @param entityId  id da entidade; null aceito
     * @param metadata  dados extras serializados como jsonb; {} se vazio
     */
    public void log(UUID companyId, UUID userId, String action, String entity,
                    UUID entityId, Map<String, Object> metadata) {
        try {
            String json = objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
            jdbcTemplate.update(INSERT, companyId, userId, action, entity, entityId, json);
        } catch (JsonProcessingException e) {
            log.warn("audit log failed to serialize metadata: action={} entity={} entityId={}",
                action, entity, entityId, e);
        } catch (RuntimeException e) {
            log.warn("audit log insert failed (engolido — não quebra fluxo): "
                + "action={} entity={} entityId={} companyId={}",
                action, entity, entityId, companyId, e);
        }
    }
}
