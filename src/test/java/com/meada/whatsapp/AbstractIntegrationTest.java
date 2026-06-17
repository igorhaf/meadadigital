package com.meada.whatsapp;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;

/**
 * Base para integration tests com PostgreSQL real via Testcontainers.
 *
 * <p>Sobe o contexto Spring COMPLETO (DataSource + Hikari + connection-init-sql
 * {@code SET ROLE service_role} + StartupDatabaseCheck + JdbcTemplate + repos),
 * menos o Tomcat ({@code WebEnvironment.NONE}). Testa o repositório no MESMO
 * ambiente que produção usa (incluindo o role service_role).
 *
 * <p><b>Ordem de inicialização (resolve ovo-e-galinha):</b> as migrations rodam
 * num STATIC BLOCK, via conexão JDBC CRUA ({@link DriverManager}, sem Hikari e
 * sem o connection-init-sql), ANTES de o contexto Spring subir. Isto é
 * necessário porque:
 *   - o boot do contexto Spring já abre conexões Hikari que executam
 *     {@code SET ROLE service_role}, e o {@code StartupDatabaseCheck} valida
 *     {@code current_user=service_role} no boot;
 *   - mas o role service_role (e as tabelas) só passam a existir DEPOIS de o
 *     bootstrap + migrations rodarem.
 * Rodando as migrations no static block (antes do contexto), quando o Spring
 * sobe o role e o schema já existem → SET ROLE e StartupDatabaseCheck passam.
 *
 * <p><b>Privilégios — separação intencional:</b> o static block roda como o
 * superuser do container (precisa de CREATE ROLE/SCHEMA). Os testes propriamente
 * ditos rodam como {@code service_role} (via Hikari + connection-init-sql), igual
 * a produção. Só o setup do schema usa superuser; o fluxo normal usa o role
 * limitado.
 *
 * <p>Container singleton (static, start eager): reusado por toda a suíte —
 * migrations rodam uma única vez, economizando segundos por classe de teste.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
public abstract class AbstractIntegrationTest {

    // pgvector/pgvector:pg17 (não o postgres:17-alpine puro) porque a migration 13 usa
    // CREATE EXTENSION vector + vector(384). É a imagem oficial do pgvector sobre PG17;
    // asCompatibleSubstituteFor sinaliza ao Testcontainers que ela substitui "postgres".
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(org.testcontainers.utility.DockerImageName
            .parse("pgvector/pgvector:pg17")
            .asCompatibleSubstituteFor("postgres"));

    /**
     * Scripts em ordem explícita (não confiamos em ordem alfabética de classpath):
     * bootstrap (andaime que simula a superfície Supabase) primeiro, depois as
     * migrations de produção 01..06 (vindas de supabase/migrations/ via testResource).
     */
    private static final String[] SCRIPTS = {
        "db/bootstrap/00_test_bootstrap.sql",
        "db/migrations/01_extensions_and_schema.sql",
        "db/migrations/02_tables.sql",
        "db/migrations/03_rls.sql",
        "db/migrations/04_grants.sql",
        "db/migrations/05_storage.sql",
        "db/migrations/06_unique_open_conversation.sql",
        "db/migrations/07_palette_id.sql",
        "db/migrations/08_audit_log.sql",
        "db/migrations/09_count_unread_conversations.sql",
        "db/migrations/10_contacts_blocked.sql",
        "db/migrations/11_get_tenant_metrics.sql",
        "db/migrations/12_knowledge_storage.sql",
        "db/migrations/13_knowledge_tables.sql",
        "db/migrations/14_search_knowledge_chunks.sql",
        "db/migrations/15_conversations_marked_unread.sql",
        "db/migrations/16_tags.sql",
        "db/migrations/17_conversations_scheduling_intent.sql",
        "db/migrations/18_tenant_invitations.sql",
        "db/migrations/19_roles_and_availability.sql",
        "db/migrations/20_ia_intelligence.sql",
        "db/migrations/21_appointments.sql",
        "db/migrations/22_teams_and_limits.sql",
        "db/migrations/23_engagement_and_ux.sql",
        "db/migrations/24_access_logs.sql",
        "db/migrations/25_multichannel_and_training.sql",
        "db/migrations/26_admin_operacao.sql",
        "db/migrations/27_medicao_e_saude.sql",
        "db/migrations/28_plataforma.sql",
        "db/migrations/29_company_profile.sql",
        "db/migrations/30_sushi.sql",
        "db/migrations/31_legal.sql"
    };

    /**
     * Scripts que DEFINEM FUNÇÃO com corpo entre $$...$$ E têm outro statement depois
     * (um grant/comment terminando em ';'). O splitter padrão do ScriptUtils não trata o
     * dollar-quote corretamente nesse caso e quebra ("Unterminated dollar quote"). Para
     * esses, usamos EOF_STATEMENT_SEPARATOR: o arquivo inteiro vira UM statement enviado
     * ao Postgres, que entende $$ nativamente.
     *
     * <p>Por que não EOF para todos: os demais scripts dependem do split por ';' (várias
     * policies/grants por arquivo) e já rodam verdes assim — não mexemos no que funciona.
     * (Nota: a teoria anterior — "só quebra se o corpo tem ';' interno" — estava errada:
     * a 09 é 'language sql' SEM ';' no corpo e mesmo assim quebrou, porque tem um grant
     * após o $$;. O gatilho real é "função com $$ + statement subsequente no mesmo arquivo".)
     */
    private static final java.util.Set<String> WHOLE_FILE_SCRIPTS = java.util.Set.of(
        "db/migrations/08_audit_log.sql",
        "db/migrations/09_count_unread_conversations.sql",
        "db/migrations/11_get_tenant_metrics.sql",
        "db/migrations/14_search_knowledge_chunks.sql",
        "db/migrations/15_conversations_marked_unread.sql");

    static {
        POSTGRES.start();
        // Conexão CRUA (sem Hikari, sem SET ROLE) como superuser do container.
        // Roda cada script isolado via ScriptUtils. Script por script: se um falhar, a
        // exception aponta qual Resource quebrou.
        try (Connection raw = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            for (String script : SCRIPTS) {
                org.springframework.core.io.support.EncodedResource resource =
                    new org.springframework.core.io.support.EncodedResource(
                        new ClassPathResource(script));
                if (WHOLE_FILE_SCRIPTS.contains(script)) {
                    // Arquivo inteiro como 1 statement — preserva o corpo plpgsql com ';'
                    // interno. continueOnError=false, ignoreFailedDrops=false, comentário e
                    // delimitadores de bloco nos defaults; separator = EOF.
                    ScriptUtils.executeSqlScript(
                        raw, resource, false, false,
                        ScriptUtils.DEFAULT_COMMENT_PREFIX,
                        ScriptUtils.EOF_STATEMENT_SEPARATOR,
                        ScriptUtils.DEFAULT_BLOCK_COMMENT_START_DELIMITER,
                        ScriptUtils.DEFAULT_BLOCK_COMMENT_END_DELIMITER);
                } else {
                    ScriptUtils.executeSqlScript(raw, resource);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to bootstrap test database", e);
        }
    }

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /** Disponível para subclasses que testam a camada HTTP (ex. filtro, controller).
     *  Testes de repositório ignoram. webEnvironment=MOCK sobe um servlet mock
     *  em-memória (não Tomcat) — overhead irrisório para quem não usa. */
    @Autowired
    protected MockMvc mockMvc;

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        // Conecta como o superuser do container. O role efetivo vira service_role
        // via connection-init-sql (SET ROLE), igual produção.
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // WEBHOOK_SECRET precisa existir para o WebhookSecretFilter instanciar no boot.
        registry.add("webhook.secret", () -> "test-secret");
        // gemini.api-key e gemini.model são obrigatórios (fail-fast no GeminiProvider);
        // valores dummy para o contexto subir nos testes de integração. O teste do
        // GeminiProvider em si aponta o base-url para o MockWebServer.
        registry.add("gemini.api-key", () -> "test-gemini-key");
        registry.add("gemini.model", () -> "test-model");
        // evolution.base-url é obrigatório (fail-fast no EvolutionClient @Component);
        // placeholder para o contexto subir nos testes de integração. O teste do
        // EvolutionClient em si aponta o base-url para o MockWebServer.
        registry.add("evolution.base-url", () -> "http://localhost:0");
        // Admin (camada 4.1): supabase.jwks-url é fail-fast no JwksConfig (boot). Valor
        //   DUMMY (porta 0, inalcançável) — nos testes admin o bean JWKSource é sobrescrito
        //   por um local (AdminTestJwksConfig com a chave ECC de teste), e o RemoteJWKSet de
        //   prod é LAZY (só faria HTTP na 1ª verificação), então a URL dummy nunca é resolvida.
        // admin.cors-allowed-origins fixo (o AdminCorsConfig lê no boot).
        // admin.super-admin-emails NÃO é registrado aqui de propósito: @DynamicPropertySource
        //   tem precedência sobre @TestPropertySource, então registrar "" aqui venceria
        //   qualquer override de subclasse. Sem registro → o filtro lê null → requireNonNullElse
        //   → allowlist vazia (correto para os testes não-admin). Os testes admin definem a
        //   allowlist via @DynamicPropertySource próprio (AbstractAdminIntegrationTest).
        registry.add("supabase.jwks-url", () -> "http://localhost:0/jwks");
        registry.add("admin.cors-allowed-origins", () -> "http://localhost:3000");
    }

    /**
     * Limpa dados entre testes, preservando o schema. Roda numa conexão Hikari
     * já com SET ROLE service_role aplicado — service_role tem GRANT ALL nas 11
     * tabelas (04_grants.sql), então o TRUNCATE funciona com o role correto.
     * Não inclui auth.users (não é semeada no escopo atual) nem storage.*.
     */
    @BeforeEach
    void truncate() {
        jdbcTemplate.execute("""
            TRUNCATE TABLE
              companies, users, whatsapp_instances, services, business_hours,
              faqs, documents, ai_settings, contacts, conversations, messages,
              audit_log, tags, conversation_tags, tenant_invitations,
              availability_slots, appointments, teams, saved_replies, access_logs,
              ai_message_feedback, admin_action_log, admin_notes,
              webhook_heartbeats, scheduled_job_runs, error_log,
              announcements, announcement_dismissals, plans,
              sushi_order_items, sushi_orders, sushi_menu_items, sushi_restaurant_config,
              legal_case_updates, legal_cases, legal_clients
            RESTART IDENTITY CASCADE
            """);
    }

    /**
     * Epoch segundos "agora" — para construir payloads de teste com messageTimestamp
     * fresh (passa pelo guard de frescor do WebhookService). Testes que precisam
     * simular mensagem antiga (ex. staleMessageIsIgnored) usam {@code recentTimestamp() - N}.
     */
    protected static long recentTimestamp() {
        return Instant.now().getEpochSecond();
    }
}
