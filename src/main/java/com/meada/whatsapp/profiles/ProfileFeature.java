package com.meada.whatsapp.profiles;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Catálogo HARDCODED de features de plataforma que o ROOT liga/desliga por nicho (camada 9.0).
 *
 * <p>Mesmo padrão dos status materializados: enum aqui é a fonte de verdade no backend, espelhado
 * 1:1 por {@code frontend/lib/profiles/profile-feature.ts}. O {@code ProfileFeatureParityTest}
 * garante que os dois nunca divergem. Adicionar uma feature = editar os 2 arquivos + estender a
 * CHECK constraint de {@code profile_features.feature_key} (migration) + rodar a paridade.
 *
 * <p>A flag por nicho mora em {@code profile_features} (só os desvios; ausência = OFF). Default de
 * toda feature é OFF — opt-in explícito do root. A primeira feature é {@link #CMS} (página pessoal
 * por tenant); esta SM-L só entrega a INFRA — o CMS real vem na SM-M, atrás do gate
 * {@code ProfileFeatureGuard.requireFeature}.
 */
public enum ProfileFeature {
    CMS("cms", "Página pessoal (CMS)");

    private final String key;
    private final String label;

    ProfileFeature(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    /** Resolve uma feature pelo key estável (profile_features.feature_key). Optional vazio se inválido. */
    public static Optional<ProfileFeature> fromKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(f -> f.key.equals(key)).findFirst();
    }

    /** Todas as features ativas (no MVP, todas as do enum). Ordem de declaração. */
    public static List<ProfileFeature> allActive() {
        return List.of(values());
    }
}
