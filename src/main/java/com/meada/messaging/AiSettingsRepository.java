package com.meada.messaging;

import com.meada.engagement.ReactivationConfig;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import java.util.UUID;

/**
 * Leitura de {@code ai_settings} (1:1 com company). Consumido pelo PromptBuilder.
 *
 * <p>Retorna FIELMENTE o que está no banco: {@link Optional#empty()} se o tenant
 * não configurou. Defaults neutros são responsabilidade do PromptBuilder.
 */
@Repository
public class AiSettingsRepository {

    private static final RowMapper<AiSettings> ROW_MAPPER = (rs, rowNum) ->
        new AiSettings(
            rs.getString("tone"),
            rs.getString("system_rules"),
            rs.getString("restrictions"),
            rs.getString("handoff_triggers"),
            rs.getString("model_provider"));

    private static final String SELECT_BY_COMPANY =
        "select tone, system_rules, restrictions, handoff_triggers, model_provider "
            + "from ai_settings where company_id = ?";

    // Lookup focado da mensagem de boas-vindas (camada 5.21 #82). Fora do ROW_MAPPER de
    // cima para NÃO acoplar o record AiSettings (consumido pelo PromptBuilder) aos campos
    // de engajamento — o OutboundService só precisa do welcome_message na 1ª inbound.
    private static final String SELECT_WELCOME_MESSAGE =
        "select welcome_message from ai_settings where company_id = ?";

    private final JdbcTemplate jdbcTemplate;

    public AiSettingsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Settings do tenant, ou empty se não configurado (UNIQUE garante ≤ 1 linha). */
    public Optional<AiSettings> findByCompany(UUID companyId) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        return jdbcTemplate.query(SELECT_BY_COMPANY, ROW_MAPPER, companyId)
            .stream()
            .findFirst();
    }

    /**
     * Mensagem de boas-vindas configurada pelo tenant (camada 5.21 #82), ou empty se não
     * há linha ai_settings OU se a coluna welcome_message está null/vazia. O OutboundService
     * chama isso na 1ª inbound do contato e, se presente, envia o welcome antes da resposta da IA.
     *
     * <p>O filtro {@code Objects::nonNull} evita o NPE do Stream.findFirst quando a coluna é
     * null (mesmo padrão do findMemoryByConversation). Lookup focado — não carrega o AiSettings.
     *
     * @return o welcome_message não-vazio, ou {@link Optional#empty()} se ausente/null/em-branco.
     */
    public Optional<String> findWelcomeMessage(UUID companyId) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        return jdbcTemplate.query(SELECT_WELCOME_MESSAGE,
                (rs, rowNum) -> rs.getString("welcome_message"), companyId)
            .stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .findFirst();
    }

    // Empresas COM reativação configurada (camada 5.21 #81): reactivation_days E
    // reactivation_message ambos não-null. Varrido pelo ReactivationJob — só essas empresas
    // disparam reativação; as demais nem entram na varredura (job no-op para elas).
    private static final String SELECT_REACTIVATION_CONFIGS =
        "select company_id, reactivation_days, reactivation_message from ai_settings "
            + "where reactivation_days is not null and reactivation_message is not null";

    private static final RowMapper<ReactivationConfig> REACTIVATION_MAPPER = (rs, rowNum) ->
        new ReactivationConfig(
            (UUID) rs.getObject("company_id"),
            rs.getInt("reactivation_days"),
            rs.getString("reactivation_message"));

    /**
     * Empresas com reativação automática configurada (camada 5.21 #81): as que têm
     * {@code reactivation_days} E {@code reactivation_message} preenchidos. O
     * {@link com.meada.engagement.ReactivationJob} varre cada uma em busca de
     * contatos inativos.
     *
     * @return lista de configs de reativação (pode ser vazia se nenhuma empresa configurou)
     */
    public List<ReactivationConfig> findReactivationConfigs() {
        return jdbcTemplate.query(SELECT_REACTIVATION_CONFIGS, REACTIVATION_MAPPER);
    }
}
