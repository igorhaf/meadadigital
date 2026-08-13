package com.meada.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Verificação de banco no startup — fail-fast do invariante de conexão.
 *
 * Motivo: o pool Hikari inicializa de forma LAZY (confirmado empiricamente: sem
 * isto, nenhuma conexão abre no boot, e o connection-init-sql 'SET ROLE
 * service_role' só rodaria na 1ª query real). Este runner força uma conexão no
 * startup e VALIDA o invariante completo:
 *
 *   1. Conectividade: se o pool não conectar (URL errada, Supabase fora do ar,
 *      credencial inválida), getConnection() falha e a app NÃO sobe.
 *   2. SET ROLE efetivo: SELECT current_user precisa retornar 'service_role'.
 *      Se retornar 'postgres' (ou outro), significa que o connection-init-sql
 *      NÃO rodou ou o grant de service_role está ausente — a app NÃO sobe, com
 *      mensagem explícita. (BYPASSRLS do service_role não supre GRANT; ver
 *      04_grants.sql e o comentário do connection-init-sql no application.yml.)
 *
 * ApplicationRunner roda uma vez, após o contexto pronto, antes de a app aceitar
 * tráfego. Constructor injection (sem @Autowired explícito — single constructor).
 */
@Component
public class StartupDatabaseCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupDatabaseCheck.class);

    private static final String EXPECTED_ROLE = "service_role";

    private final JdbcTemplate jdbcTemplate;

    public StartupDatabaseCheck(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Força a 1ª conexão do pool (dispara o connection-init-sql) e lê o role
        // efetivo da sessão.
        String currentUser = jdbcTemplate.queryForObject("select current_user", String.class);

        if (!EXPECTED_ROLE.equals(currentUser)) {
            throw new IllegalStateException(
                "Expected current_user='" + EXPECTED_ROLE + "' after SET ROLE, got '" + currentUser
                + "'. Connection-init-sql is not running, or service_role grant is missing.");
        }

        log.info("Database connection verified: current_user={}", currentUser);
    }
}
