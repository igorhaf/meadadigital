package com.meada.whatsapp.profiles;

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
 * Paridade 1:1 entre o enum Java {@link ProfileFeature} e o catálogo TS
 * {@code frontend/lib/profiles/profile-feature.ts} (camada 9.0). O ROOT liga/desliga features por
 * nicho; se os keys divergirem entre back e front, uma feature existe num lado e não no outro e a
 * grade fica inconsistente. Este teste falha cedo (no build) apontando QUAL lado tem key sobrando.
 *
 * <p>Extrai os keys do TS via regex sobre o array PROFILE_FEATURES. O backend roda com cwd = raiz
 * do módulo Maven (raiz do repo), então o caminho relativo a frontend/ resolve.
 */
class ProfileFeatureParityTest {

    private static final Path TS_FILE =
        Path.of("frontend", "lib", "profiles", "profile-feature.ts");

    // Captura o key de cada entrada do array PROFILE_FEATURES: { key: 'xxx', label: ... }
    private static final Pattern KEY_PATTERN = Pattern.compile("\\{\\s*key:\\s*'([a-z0-9_]+)'");

    @Test
    @DisplayName("o conjunto de keys do enum Java == conjunto de keys do TS")
    void javaTsParity() throws IOException {
        assertThat(Files.exists(TS_FILE))
            .as("arquivo TS de features não encontrado em %s (cwd=%s)",
                TS_FILE, Path.of("").toAbsolutePath())
            .isTrue();

        String ts = Files.readString(TS_FILE);
        Set<String> tsKeys = new LinkedHashSet<>();
        Matcher m = KEY_PATTERN.matcher(ts);
        while (m.find()) {
            tsKeys.add(m.group(1));
        }

        Set<String> javaKeys = Arrays.stream(ProfileFeature.values())
            .map(ProfileFeature::key)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        assertThat(tsKeys)
            .as("keys do TS não-vazios — o regex deve ter casado o array PROFILE_FEATURES")
            .isNotEmpty();

        Set<String> onlyInJava = new LinkedHashSet<>(javaKeys);
        onlyInJava.removeAll(tsKeys);
        Set<String> onlyInTs = new LinkedHashSet<>(tsKeys);
        onlyInTs.removeAll(javaKeys);

        assertThat(onlyInJava).as("keys no enum Java mas AUSENTES no TS: %s", onlyInJava).isEmpty();
        assertThat(onlyInTs).as("keys no TS mas AUSENTES no enum Java: %s", onlyInTs).isEmpty();
    }
}
