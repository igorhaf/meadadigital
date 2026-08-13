package com.meada.admin.companies;

import com.meada.admin.audit.AdminAction;
import com.meada.admin.audit.AdminActionLogger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lógica do drill-down de empresas do super-admin (camada 6.1): detalhe com contadores,
 * edição, suspensão/reativação, hard delete FK-seguro e CRUD de notas internas.
 *
 * <p>Opera como service_role (BYPASSRLS) — visão e poder GLOBAL sobre as empresas, que é a
 * autoridade do super-admin. O escopo por companyId no WHERE de cada query é a defesa
 * (não há RLS para o super-admin).
 *
 * <p><b>Auditoria atômica:</b> toda ação destrutiva/sensível chama
 * {@link AdminActionLogger#log} ANTES da operação, dentro da MESMA @Transactional — se a
 * operação falhar (ex.: FK), o log faz rollback junto (rastro e efeito são atômicos). O
 * superAdminId vem do controller (AuthenticatedUser.userId()).
 */
@Service
public class CompanyAdminService {

    private final JdbcTemplate jdbcTemplate;
    private final AdminActionLogger actionLogger;

    public CompanyAdminService(JdbcTemplate jdbcTemplate, AdminActionLogger actionLogger) {
        this.jdbcTemplate = jdbcTemplate;
        this.actionLogger = actionLogger;
    }

    // ---- listagem com filtros + paginação -----------------------------------

    private static final RowMapper<CompanyResponse> LIST_ROW_MAPPER = (rs, rowNum) ->
        new CompanyResponse(
            (UUID) rs.getObject("id"),
            rs.getString("name"),
            rs.getString("slug"),
            rs.getString("status"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getString("palette_id"),
            rs.getString("profile_id"));

    /**
     * Lista empresas com filtros opcionais (status exato, q ilike em name/slug,
     * createdAfter), paginada (page 0-based, pageSize). O WHERE é montado dinamicamente —
     * cada filtro presente acrescenta um predicado + parâmetro posicional. total é contado
     * com os mesmos predicados (sem LIMIT/OFFSET) para o paginador do frontend.
     */
    public CompanyPage list(String status, String q, java.time.Instant createdAfter,
                            int page, int pageSize) {
        StringBuilder where = new StringBuilder(" where 1=1");
        List<Object> params = new ArrayList<>();

        if (status != null && !status.isBlank()) {
            where.append(" and status = ?");
            params.add(status);
        }
        if (q != null && !q.isBlank()) {
            where.append(" and (name ilike ? or slug ilike ?)");
            String like = "%" + q.trim() + "%";
            params.add(like);
            params.add(like);
        }
        if (createdAfter != null) {
            where.append(" and created_at >= ?");
            params.add(java.sql.Timestamp.from(createdAfter));
        }

        Long total = jdbcTemplate.queryForObject(
            "select count(*) from companies" + where, Long.class, params.toArray());

        // Página: mesmos predicados, ordenado por created_at desc, com LIMIT/OFFSET.
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add((long) page * pageSize);
        List<CompanyResponse> items = jdbcTemplate.query(
            "select id, name, slug, status, created_at, palette_id, profile_id from companies"
                + where + " order by created_at desc limit ? offset ?",
            LIST_ROW_MAPPER, pageParams.toArray());

        return new CompanyPage(items, total == null ? 0L : total, page, pageSize);
    }

    // ---- detalhe com contadores ---------------------------------------------

    /**
     * Detalhe de uma empresa + limites + contadores agregados. 404 se não existir.
     * Os COUNTs são escopados por company_id; usersCount/contactsCount ignoram registros
     * soft-deletados (deleted_at). owner via LEFT JOIN (owner_id nullable).
     */
    public CompanyDetailDto getDetail(UUID companyId) {
        CompanyDetailDto base = jdbcTemplate.query(
                "select c.id, c.name, c.slug, c.status, c.palette_id, c.profile_id, c.created_at, "
                    + "c.max_admins, c.max_faqs, c.max_conversations_month, "
                    + "u.email as owner_email, u.full_name as owner_name "
                    + "from companies c "
                    + "left join users u on u.id = c.owner_id "
                    + "where c.id = ?",
                (rs, rowNum) -> new CompanyDetailDto(
                    (UUID) rs.getObject("id"),
                    rs.getString("name"),
                    rs.getString("slug"),
                    rs.getString("status"),
                    rs.getString("palette_id"),
                    rs.getTimestamp("created_at").toInstant(),
                    (Integer) rs.getObject("max_admins"),
                    (Integer) rs.getObject("max_faqs"),
                    (Integer) rs.getObject("max_conversations_month"),
                    0L, 0L, 0L, 0L, null,
                    rs.getString("owner_email"),
                    rs.getString("owner_name"),
                    rs.getString("profile_id")),
                companyId)
            .stream().findFirst().orElse(null);

        if (base == null) {
            throw new CompanyNotFoundException(companyId);
        }

        long usersCount = countOrZero(
            "select count(*) from users where company_id = ? and deleted_at is null", companyId);
        long contactsCount = countOrZero(
            "select count(*) from contacts where company_id = ? and deleted_at is null", companyId);
        long openConversations = countOrZero(
            "select count(*) from conversations where company_id = ? and status = 'open'",
            companyId);
        long messagesLast30d = countOrZero(
            "select count(*) from messages where company_id = ? "
                + "and created_at >= now() - interval '30 days'",
            companyId);
        java.sql.Timestamp lastActivity = jdbcTemplate.queryForObject(
            "select max(last_message_at) from conversations where company_id = ?",
            java.sql.Timestamp.class, companyId);

        return new CompanyDetailDto(
            base.id(), base.name(), base.slug(), base.status(), base.paletteId(),
            base.createdAt(), base.maxAdmins(), base.maxFaqs(), base.maxConversationsMonth(),
            usersCount, contactsCount, openConversations, messagesLast30d,
            lastActivity == null ? null : lastActivity.toInstant(),
            base.ownerEmail(), base.ownerName(), base.profileId());
    }

    private long countOrZero(String sql, Object... params) {
        Long n = jdbcTemplate.queryForObject(sql, Long.class, params);
        return n == null ? 0L : n;
    }

    // ---- update --------------------------------------------------------------

    /**
     * Edita identidade + limites. @Transactional: registra COMPANY_UPDATED (payload = os
     * campos enviados) ANTES do UPDATE — se o UPDATE falhar (ex.: slug duplicado →
     * DuplicateKeyException), o log faz rollback junto. 404 se a empresa não existir.
     *
     * <p>Colisão de slug (UNIQUE em companies.slug) propaga DuplicateKeyException, que o
     * controller mapeia para 409 slug_already_exists.
     */
    @Transactional
    public void update(UUID companyId, UpdateCompanyRequest req, UUID superAdminId) {
        // 404 cedo (antes de logar) — não auditar tentativa sobre empresa inexistente.
        requireExists(companyId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", req.name());
        payload.put("slug", req.slug());
        payload.put("paletteId", req.paletteId());
        payload.put("maxAdmins", req.maxAdmins());
        payload.put("maxFaqs", req.maxFaqs());
        payload.put("maxConversationsMonth", req.maxConversationsMonth());
        // profileId é OPCIONAL no PATCH (camada 7.0): só entra no payload e no UPDATE quando
        // enviado. O controller já validou contra ProfileType (400 invalid_profile_id) antes.
        boolean changesProfile = req.profileId() != null && !req.profileId().isBlank();
        if (changesProfile) {
            payload.put("profileId", req.profileId());
        }
        actionLogger.log(superAdminId, AdminAction.COMPANY_UPDATED,
            AdminAction.TARGET_COMPANY, companyId, payload);

        if (changesProfile) {
            jdbcTemplate.update(
                "update companies set name = ?, slug = ?, palette_id = ?, max_admins = ?, "
                    + "max_faqs = ?, max_conversations_month = ?, profile_id = ?, updated_at = now() "
                    + "where id = ?",
                req.name(), req.slug(), req.paletteId(), req.maxAdmins(), req.maxFaqs(),
                req.maxConversationsMonth(), req.profileId(), companyId);
        } else {
            jdbcTemplate.update(
                "update companies set name = ?, slug = ?, palette_id = ?, max_admins = ?, "
                    + "max_faqs = ?, max_conversations_month = ?, updated_at = now() where id = ?",
                req.name(), req.slug(), req.paletteId(), req.maxAdmins(), req.maxFaqs(),
                req.maxConversationsMonth(), companyId);
        }
    }

    // ---- suspender / reativar ------------------------------------------------

    /**
     * Suspende a empresa (status='suspended'). 409 already_suspended se já estiver. 404 se
     * não existir. @Transactional: log COMPANY_SUSPENDED (payload = reason) ANTES do UPDATE.
     */
    @Transactional
    public void suspend(UUID companyId, String reason, UUID superAdminId) {
        String current = currentStatus(companyId);
        if ("suspended".equals(current)) {
            throw new CompanyStatusConflictException("already_suspended");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reason", reason);
        actionLogger.log(superAdminId, AdminAction.COMPANY_SUSPENDED,
            AdminAction.TARGET_COMPANY, companyId, payload);

        jdbcTemplate.update(
            "update companies set status = 'suspended', updated_at = now() where id = ?",
            companyId);
    }

    /**
     * Reativa a empresa (status='active'). 409 already_active se já estiver. 404 se não
     * existir. @Transactional: log COMPANY_REACTIVATED ANTES do UPDATE.
     */
    @Transactional
    public void reactivate(UUID companyId, UUID superAdminId) {
        String current = currentStatus(companyId);
        if ("active".equals(current)) {
            throw new CompanyStatusConflictException("already_active");
        }
        actionLogger.log(superAdminId, AdminAction.COMPANY_REACTIVATED,
            AdminAction.TARGET_COMPANY, companyId, Map.of());

        jdbcTemplate.update(
            "update companies set status = 'active', updated_at = now() where id = ?",
            companyId);
    }

    /** Lê o status atual; 404 se a empresa não existir. */
    private String currentStatus(UUID companyId) {
        List<String> rows = jdbcTemplate.query(
            "select status from companies where id = ?",
            (rs, rowNum) -> rs.getString("status"), companyId);
        if (rows.isEmpty()) {
            throw new CompanyNotFoundException(companyId);
        }
        return rows.get(0);
    }

    // ---- hard delete (FK-seguro) ---------------------------------------------

    /**
     * Hard delete REAL de TODA a empresa e de tudo que dela depende (camada 6.1). Apaga em
     * ordem FK-segura: os filhos primeiro, a empresa por último.
     *
     * <p><b>Por que a ordem explícita é obrigatória:</b> várias FKs para companies (e entre
     * as tabelas filhas) são ON DELETE RESTRICT no schema — apagar a empresa direto FALHARIA.
     * As RESTRICT relevantes (migration 02 + 08 + 13):
     * <ul>
     *   <li>users, whatsapp_instances, services, faqs, documents, ai_settings, contacts,
     *       conversations, messages, business_hours, audit_log, knowledge_documents,
     *       knowledge_chunks → companies: RESTRICT;
     *   <li>messages.conversation_id → conversations: RESTRICT;
     *   <li>conversations.contact_id → contacts e .whatsapp_instance_id → whatsapp_instances:
     *       RESTRICT.
     * </ul>
     * As demais (tags, conversation_tags, tenant_invitations, teams, saved_replies,
     * access_logs, ai_message_feedback, appointments, availability_slots, admin_notes,
     * knowledge_chunks→document) são CASCADE/SET NULL, mas apagamos explícito por clareza,
     * idempotência e para respeitar as RESTRICT entre filhas (ex.: messages antes de
     * conversations; conversations antes de contacts/whatsapp_instances).
     *
     * <p>admin_action_log NÃO é apagado: não tem FK para companies (registra ações do
     * super-admin, não dados do tenant) — o rastro COMPANY_DELETED sobrevive à exclusão.
     *
     * <p>@Transactional: log COMPANY_DELETED (payload = nome/slug) ANTES dos deletes — se
     * qualquer delete falhar, o rastro faz rollback junto. 404 se não existir.
     */
    @Transactional
    public void hardDelete(UUID companyId, UUID superAdminId) {
        Map<String, Object> identity = jdbcTemplate.query(
                "select name, slug from companies where id = ?",
                (rs, rowNum) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", rs.getString("name"));
                    m.put("slug", rs.getString("slug"));
                    return m;
                },
                companyId)
            .stream().findFirst().orElse(null);

        if (identity == null) {
            throw new CompanyNotFoundException(companyId);
        }

        // Rastro ANTES dos deletes (atômico com eles via @Transactional).
        actionLogger.log(superAdminId, AdminAction.COMPANY_DELETED,
            AdminAction.TARGET_COMPANY, companyId, identity);

        // Ordem FK-segura — filhos antes dos pais (ver javadoc).
        // 1. feedback de mensagens (FK → messages).
        jdbcTemplate.update("delete from ai_message_feedback where company_id = ?", companyId);
        // 2. mensagens (FK → conversations RESTRICT).
        jdbcTemplate.update("delete from messages where company_id = ?", companyId);
        // 3. tags por conversa (FK → conversations/tags).
        jdbcTemplate.update(
            "delete from conversation_tags where conversation_id in "
                + "(select id from conversations where company_id = ?)",
            companyId);
        // 4. agendamentos (FK → contacts/conversations/services).
        jdbcTemplate.update("delete from appointments where company_id = ?", companyId);
        // 5. conversas (FK → contacts e whatsapp_instances RESTRICT → depois das mensagens).
        jdbcTemplate.update("delete from conversations where company_id = ?", companyId);
        // 6. chunks de conhecimento (FK → knowledge_documents).
        jdbcTemplate.update("delete from knowledge_chunks where company_id = ?", companyId);
        // 7. documentos de conhecimento (FK → companies RESTRICT).
        jdbcTemplate.update("delete from knowledge_documents where company_id = ?", companyId);
        // 8. contatos (FK → companies RESTRICT → depois de conversas/agendamentos).
        jdbcTemplate.update("delete from contacts where company_id = ?", companyId);
        // 9. faqs.
        jdbcTemplate.update("delete from faqs where company_id = ?", companyId);
        // 10. serviços.
        jdbcTemplate.update("delete from services where company_id = ?", companyId);
        // 11. documentos (metadados).
        jdbcTemplate.update("delete from documents where company_id = ?", companyId);
        // 12. ai_settings.
        jdbcTemplate.update("delete from ai_settings where company_id = ?", companyId);
        // 13. horários de atendimento.
        jdbcTemplate.update("delete from business_hours where company_id = ?", companyId);
        // 14. slots de disponibilidade.
        jdbcTemplate.update("delete from availability_slots where company_id = ?", companyId);
        // 15. tags (depois de conversation_tags).
        jdbcTemplate.update("delete from tags where company_id = ?", companyId);
        // 16. respostas salvas.
        jdbcTemplate.update("delete from saved_replies where company_id = ?", companyId);
        // 17. times (users.team_id → SET NULL, mas apagamos antes de users por clareza).
        jdbcTemplate.update("delete from teams where company_id = ?", companyId);
        // 18. logs de acesso.
        jdbcTemplate.update("delete from access_logs where company_id = ?", companyId);
        // 19. audit_log (FK → companies RESTRICT).
        jdbcTemplate.update("delete from audit_log where company_id = ?", companyId);
        // 20. convites.
        jdbcTemplate.update("delete from tenant_invitations where company_id = ?", companyId);
        // 21. notas internas do super-admin.
        jdbcTemplate.update("delete from admin_notes where company_id = ?", companyId);
        // 22. instâncias da Evolution (FK → companies RESTRICT → depois de conversas).
        jdbcTemplate.update("delete from whatsapp_instances where company_id = ?", companyId);
        // 23. usuários da empresa (FK → companies RESTRICT → penúltimo).
        jdbcTemplate.update("delete from users where company_id = ?", companyId);
        // 24. a empresa.
        jdbcTemplate.update("delete from companies where id = ?", companyId);
    }

    // ---- notas internas (CRUD) -----------------------------------------------

    private static final RowMapper<AdminNoteDto> NOTE_ROW_MAPPER = (rs, rowNum) ->
        new AdminNoteDto(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("super_admin_user_id"),
            rs.getString("content"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());

    /** Lista as notas da empresa, mais recentes primeiro. 404 se a empresa não existir. */
    public List<AdminNoteDto> listNotes(UUID companyId) {
        requireExists(companyId);
        return jdbcTemplate.query(
            "select id, super_admin_user_id, content, created_at, updated_at "
                + "from admin_notes where company_id = ? order by created_at desc",
            NOTE_ROW_MAPPER, companyId);
    }

    /**
     * Cria uma nota. @Transactional: log NOTE_CREATED ANTES do INSERT. Retorna a nota
     * persistida (RETURNING). 404 se a empresa não existir.
     */
    @Transactional
    public AdminNoteDto createNote(UUID companyId, String content, UUID superAdminId) {
        requireExists(companyId);

        AdminNoteDto created = jdbcTemplate.queryForObject(
            "insert into admin_notes (company_id, super_admin_user_id, content) "
                + "values (?, ?, ?) "
                + "returning id, super_admin_user_id, content, created_at, updated_at",
            NOTE_ROW_MAPPER, companyId, superAdminId, content);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("noteId", created.id().toString());
        actionLogger.log(superAdminId, AdminAction.NOTE_CREATED,
            AdminAction.TARGET_NOTE, created.id(), payload);
        return created;
    }

    /**
     * Edita o conteúdo de uma nota (escopada por company_id + noteId). @Transactional: log
     * NOTE_UPDATED ANTES do UPDATE. 404 note_not_found se a nota não pertencer à empresa.
     */
    @Transactional
    public AdminNoteDto updateNote(UUID companyId, UUID noteId, String content, UUID superAdminId) {
        actionLogger.log(superAdminId, AdminAction.NOTE_UPDATED,
            AdminAction.TARGET_NOTE, noteId, Map.of());

        List<AdminNoteDto> rows = jdbcTemplate.query(
            "update admin_notes set content = ?, updated_at = now() "
                + "where id = ? and company_id = ? "
                + "returning id, super_admin_user_id, content, created_at, updated_at",
            NOTE_ROW_MAPPER, content, noteId, companyId);
        if (rows.isEmpty()) {
            throw new NoteNotFoundException(noteId);
        }
        return rows.get(0);
    }

    /**
     * Apaga uma nota (escopada por company_id + noteId). @Transactional: log NOTE_DELETED
     * ANTES do DELETE. 404 note_not_found se a nota não pertencer à empresa.
     */
    @Transactional
    public void deleteNote(UUID companyId, UUID noteId, UUID superAdminId) {
        actionLogger.log(superAdminId, AdminAction.NOTE_DELETED,
            AdminAction.TARGET_NOTE, noteId, Map.of());

        int deleted = jdbcTemplate.update(
            "delete from admin_notes where id = ? and company_id = ?", noteId, companyId);
        if (deleted == 0) {
            throw new NoteNotFoundException(noteId);
        }
    }

    // ---- helper --------------------------------------------------------------

    /** 404 se a empresa não existir. */
    private void requireExists(UUID companyId) {
        Long n = jdbcTemplate.queryForObject(
            "select count(*) from companies where id = ?", Long.class, companyId);
        if (n == null || n == 0) {
            throw new CompanyNotFoundException(companyId);
        }
    }
}
