package com.meada;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Espelho SCRIPTS × diretório: TODA migration de supabase/migrations/ precisa estar no array
 * {@link AbstractIntegrationTest#SCRIPTS} (ou na allowlist EXPLÍCITA abaixo). Sem este gate,
 * uma migration esquecida deixa o schema de teste divergir da produção em silêncio — foi o
 * caso real das 48 (niche_showcase), 70/71 (admin_token: provisionamento intestável, o catch
 * do controller mascarava a coluna ausente) e 118 (subscriptions).
 *
 * <p>Teste PURO de filesystem (sem container): o Surefire roda com working dir na raiz do
 * projeto — mesma fonte da verdade que o pom copia pro classpath de teste.
 */
class MigrationScriptsCompletenessTest {

    /** Migrations deliberadamente FORA do schema de teste — vazia hoje; toda entrada exige justificativa. */
    private static final Set<String> ALLOWLIST = Set.of();

    @Test
    @DisplayName("todo .sql de supabase/migrations está no SCRIPTS (ou na allowlist justificada)")
    void scriptsMirrorMigrationsDirectory() throws IOException {
        Path dir = Path.of("supabase", "migrations");
        assertThat(dir).as("diretório de migrations (Surefire roda na raiz do projeto)").exists();

        List<String> onDisk;
        try (Stream<Path> files = Files.list(dir)) {
            onDisk = files.map(p -> p.getFileName().toString())
                .filter(n -> n.endsWith(".sql"))
                .sorted()
                .collect(Collectors.toList());
        }

        Set<String> inScripts = Arrays.stream(AbstractIntegrationTest.SCRIPTS)
            .filter(s -> s.startsWith("db/migrations/"))
            .map(s -> s.substring("db/migrations/".length()))
            .collect(Collectors.toSet());

        List<String> missing = onDisk.stream()
            .filter(n -> !inScripts.contains(n) && !ALLOWLIST.contains(n))
            .toList();
        assertThat(missing)
            .as("migrations no diretório mas FORA do SCRIPTS do AbstractIntegrationTest (adicione lá "
                + "— reescrita da CHECK de profile_id entra por ÚLTIMO e superset — ou justifique na allowlist)")
            .isEmpty();

        List<String> phantom = inScripts.stream()
            .filter(n -> !onDisk.contains(n))
            .sorted()
            .toList();
        assertThat(phantom)
            .as("entradas do SCRIPTS sem arquivo correspondente no diretório")
            .isEmpty();
    }
}
