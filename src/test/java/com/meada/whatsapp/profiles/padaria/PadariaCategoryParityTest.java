package com.meada.whatsapp.profiles.padaria;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Paridade 1:1 entre {@link PadariaCategory} (Java) e {@code frontend/profiles/padaria/
 * padaria-categories.ts} (camada 8.8 / perfil padaria). Clone do FloriculturaCategoryParityTest: se os
 * conjuntos de ids divergirem, uma categoria existe num lado e não no outro — a IA/cardápio usaria
 * categoria que o frontend não sabe exibir, ou vice-versa. Falha cedo (build) com mensagem acionável.
 */
class PadariaCategoryParityTest {

    private static final Path TS_FILE =
        Path.of("frontend", "profiles", "padaria", "padaria-categories.ts");

    private static final Pattern ID_PATTERN = Pattern.compile("\\{\\s*id:\\s*'([a-z0-9_]+)'");

    @Test
    @DisplayName("ids do enum PadariaCategory == ids do padaria-categories.ts")
    void javaTsParity() throws IOException {
        assertThat(Files.exists(TS_FILE))
            .as("arquivo TS de categorias não encontrado em %s (cwd=%s)",
                TS_FILE, Path.of("").toAbsolutePath())
            .isTrue();

        String ts = Files.readString(TS_FILE);
        Set<String> tsIds = new LinkedHashSet<>();
        Matcher m = ID_PATTERN.matcher(ts);
        while (m.find()) {
            tsIds.add(m.group(1));
        }

        Set<String> javaIds = Arrays.stream(PadariaCategory.values())
            .map(PadariaCategory::id)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        assertThat(tsIds)
            .as("ids do TS não-vazios — o regex deve ter casado o array PADARIA_CATEGORIES")
            .isNotEmpty();

        Set<String> onlyInJava = new LinkedHashSet<>(javaIds);
        onlyInJava.removeAll(tsIds);
        Set<String> onlyInTs = new LinkedHashSet<>(tsIds);
        onlyInTs.removeAll(javaIds);

        assertThat(onlyInJava)
            .as("ids no enum Java mas AUSENTES no TS (adicione em padaria-categories.ts): %s", onlyInJava)
            .isEmpty();
        assertThat(onlyInTs)
            .as("ids no TS mas AUSENTES no enum Java (adicione em PadariaCategory.java): %s", onlyInTs)
            .isEmpty();
    }
}
