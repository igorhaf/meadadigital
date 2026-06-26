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
 * Paridade 1:1 entre {@link PadariaPeriod} (Java) e {@code frontend/profiles/padaria/padaria-period.ts}
 * (camada 8.8 / perfil padaria, ESCAPADA 1). Se os ids divergirem, a IA emitiria um período que o
 * frontend não exibe, ou a CHECK do banco recusaria. Clone do FloriculturaPeriodParityTest.
 */
class PadariaPeriodParityTest {

    private static final Path TS_FILE =
        Path.of("frontend", "profiles", "padaria", "padaria-period.ts");

    private static final Pattern ID_PATTERN = Pattern.compile("\\{\\s*id:\\s*'([a-z0-9_]+)'");

    @Test
    @DisplayName("ids do enum PadariaPeriod == ids do padaria-period.ts")
    void javaTsParity() throws IOException {
        assertThat(Files.exists(TS_FILE))
            .as("arquivo TS de períodos não encontrado em %s (cwd=%s)",
                TS_FILE, Path.of("").toAbsolutePath())
            .isTrue();

        String ts = Files.readString(TS_FILE);
        Set<String> tsIds = new LinkedHashSet<>();
        Matcher m = ID_PATTERN.matcher(ts);
        while (m.find()) {
            tsIds.add(m.group(1));
        }

        Set<String> javaIds = Arrays.stream(PadariaPeriod.values())
            .map(PadariaPeriod::id)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        assertThat(tsIds)
            .as("ids do TS não-vazios — o regex deve ter casado o array PADARIA_PERIODS")
            .isNotEmpty();

        Set<String> onlyInJava = new LinkedHashSet<>(javaIds);
        onlyInJava.removeAll(tsIds);
        Set<String> onlyInTs = new LinkedHashSet<>(tsIds);
        onlyInTs.removeAll(javaIds);

        assertThat(onlyInJava)
            .as("ids no enum Java mas AUSENTES no TS (adicione em padaria-period.ts): %s", onlyInJava)
            .isEmpty();
        assertThat(onlyInTs)
            .as("ids no TS mas AUSENTES no enum Java (adicione em PadariaPeriod.java): %s", onlyInTs)
            .isEmpty();
    }
}
