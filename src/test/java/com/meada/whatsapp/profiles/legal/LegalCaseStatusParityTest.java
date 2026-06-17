package com.meada.whatsapp.profiles.legal;

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
 * Paridade 1:1 entre {@link LegalCaseStatus} (Java) e {@code frontend/profiles/legal/
 * legal-case-status.ts} (camada 7.2). Mesmo padrão do SushiCategoryParityTest.
 */
class LegalCaseStatusParityTest {

    private static final Path TS_FILE =
        Path.of("frontend", "profiles", "legal", "legal-case-status.ts");

    private static final Pattern ID_PATTERN = Pattern.compile("id:\\s*'([a-z0-9_]+)'");

    @Test
    @DisplayName("ids do enum LegalCaseStatus == ids do legal-case-status.ts")
    void javaTsParity() throws IOException {
        assertThat(Files.exists(TS_FILE))
            .as("arquivo TS de status não encontrado em %s (cwd=%s)",
                TS_FILE, Path.of("").toAbsolutePath())
            .isTrue();

        String ts = Files.readString(TS_FILE);
        Set<String> tsIds = new LinkedHashSet<>();
        Matcher m = ID_PATTERN.matcher(ts);
        while (m.find()) {
            tsIds.add(m.group(1));
        }

        Set<String> javaIds = Arrays.stream(LegalCaseStatus.values())
            .map(LegalCaseStatus::id)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        assertThat(tsIds).as("ids do TS não-vazios").isNotEmpty();

        Set<String> onlyInJava = new LinkedHashSet<>(javaIds);
        onlyInJava.removeAll(tsIds);
        Set<String> onlyInTs = new LinkedHashSet<>(tsIds);
        onlyInTs.removeAll(javaIds);

        assertThat(onlyInJava)
            .as("ids no enum Java mas AUSENTES no TS (adicione em legal-case-status.ts): %s", onlyInJava)
            .isEmpty();
        assertThat(onlyInTs)
            .as("ids no TS mas AUSENTES no enum Java (adicione em LegalCaseStatus.java): %s", onlyInTs)
            .isEmpty();
    }
}
