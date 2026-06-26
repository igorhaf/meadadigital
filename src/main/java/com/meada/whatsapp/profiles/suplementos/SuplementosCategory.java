package com.meada.whatsapp.profiles.suplementos;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Categorias de catálogo do perfil suplementos (camada 8.24, loja de saúde · nutrição esportiva),
 * MATERIALIZADAS — espelho 1:1 de {@code frontend/profiles/suplementos/suplementos-categories.ts}. O
 * {@code SuplementosCategoryParityTest} garante que os dois nunca divergem (mesmo padrão do
 * {@link com.meada.whatsapp.profiles.lingerie.LingerieCategory} / AdegaCategory). A CHECK constraint
 * de {@code sup_products.category} (migration 68) trava os mesmos ids no banco.
 *
 * <p>{@code id} é a string estável (persistida, ASCII sem acento) e {@code label} é o rótulo pt-BR
 * exibido.
 */
public enum SuplementosCategory {
    PROTEINAS("proteinas", "Proteínas"),
    AMINOACIDOS("aminoacidos", "Aminoácidos"),
    VITAMINAS("vitaminas", "Vitaminas"),
    PRE_TREINO("pre_treino", "Pré-treino"),
    EMAGRECEDORES("emagrecedores", "Emagrecedores"),
    ACESSORIOS("acessorios", "Acessórios");

    private final String id;
    private final String label;

    SuplementosCategory(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    /** Resolve uma categoria pelo id estável. Optional vazio se inválido/null. */
    public static Optional<SuplementosCategory> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(c -> c.id.equals(id)).findFirst();
    }

    /** Todas as categorias, na ordem de declaração (ordem de exibição). */
    public static List<SuplementosCategory> allActive() {
        return List.of(values());
    }
}
