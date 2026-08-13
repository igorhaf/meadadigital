package com.meada.admin.audit;

/**
 * Constantes das ações do super-admin registradas no admin_action_log (camada 6).
 * Strings estáveis (gravadas no banco) — não renomear sem migração de dados.
 */
public final class AdminAction {
    private AdminAction() {}

    public static final String COMPANY_SUSPENDED = "COMPANY_SUSPENDED";
    public static final String COMPANY_REACTIVATED = "COMPANY_REACTIVATED";
    public static final String COMPANY_UPDATED = "COMPANY_UPDATED";
    public static final String COMPANY_DELETED = "COMPANY_DELETED";
    public static final String USER_SUSPENDED = "USER_SUSPENDED";
    public static final String USER_REACTIVATED = "USER_REACTIVATED";
    public static final String USER_PASSWORD_RESET = "USER_PASSWORD_RESET";
    public static final String USER_DELETED = "USER_DELETED";
    public static final String INVITATION_REVOKED = "INVITATION_REVOKED";
    public static final String NOTE_CREATED = "NOTE_CREATED";
    public static final String NOTE_UPDATED = "NOTE_UPDATED";
    public static final String NOTE_DELETED = "NOTE_DELETED";
    public static final String ANNOUNCEMENT_CREATED = "ANNOUNCEMENT_CREATED";
    public static final String ANNOUNCEMENT_UPDATED = "ANNOUNCEMENT_UPDATED";
    public static final String ANNOUNCEMENT_DELETED = "ANNOUNCEMENT_DELETED";
    public static final String PLAN_CREATED = "PLAN_CREATED";
    public static final String PLAN_UPDATED = "PLAN_UPDATED";
    public static final String PLAN_DELETED = "PLAN_DELETED";
    // Camada 9.0 — feature flags por nicho: o root liga/desliga uma feature de um perfil.
    public static final String PROFILE_FEATURE_TOGGLED = "PROFILE_FEATURE_TOGGLED";
    // Vitrine de nichos: o root marca destaque / reordena um nicho na vitrine institucional.
    public static final String NICHE_SHOWCASE_UPDATED = "NICHE_SHOWCASE_UPDATED";

    /** Tipos de alvo (target_type). */
    public static final String TARGET_COMPANY = "company";
    public static final String TARGET_USER = "user";
    public static final String TARGET_INVITATION = "invitation";
    public static final String TARGET_NOTE = "note";
    public static final String TARGET_ANNOUNCEMENT = "announcement";
    public static final String TARGET_PLAN = "plan";
    public static final String TARGET_PROFILE_FEATURE = "profile_feature";
    public static final String TARGET_NICHE_SHOWCASE = "niche_showcase";
}
