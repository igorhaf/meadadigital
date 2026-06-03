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

    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17-alpine");

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
        "db/migrations/06_unique_open_conversation.sql"
    };

    static {
        POSTGRES.start();
        // Conexão CRUA (sem Hikari, sem SET ROLE) como superuser do container.
        // Roda cada script isolado via ScriptUtils — que respeita corpo de função
        // em $$, comentários e statements multi-linha. Script por script: se um
        // falhar, a exception aponta qual Resource quebrou.
        try (Connection raw = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            for (String script : SCRIPTS) {
                ScriptUtils.executeSqlScript(raw, new ClassPathResource(script));
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
              faqs, documents, ai_settings, contacts, conversations, messages
            RESTART IDENTITY CASCADE
            """);
    }
}
