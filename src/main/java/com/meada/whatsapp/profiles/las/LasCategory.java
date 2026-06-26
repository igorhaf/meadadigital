package com.meada.whatsapp.profiles.las;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Categorias de produto do perfil las (camada 8.23, loja de lãs · novelos · tricô/crochê · varejo),
 * MATERIALIZADAS — espelho 1:1 de {@code frontend/profiles/las/las-categories.ts}. O
 * {@code LasCategoryParityTest} garante que os dois nunca divergem (mesmo padrão do
 * {@link com.meada.whatsapp.profiles.lingerie.LingerieCategory} do chassi de varejo). A CHECK
 * constraint de {@code las_products.category} (migration 67) trava os mesmos ids no banco.
 *
 * <p>{@code id} é a string estável (persistida, ASCII sem acento) e {@code label} é o rótulo pt-BR
 * exibido.
 */
public enum LasCategory {
    LAS("las", "Lãs"),
    LINHAS("linhas", "Linhas"),
    KITS("kits", "Kits"),
    AGULHAS("agulhas", "Agulhas"),
    ACESSORIOS("acessorios", "Acessórios"),
    PELUCIA("pelucia", "Pelúcia");

    private final String id;
    private final String label;

    LasCategory(String id, String label) {
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
    public static Optional<LasCategory> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(c -> c.id.equals(id)).findFirst();
    }

    /** Todas as categorias, na ordem de declaração (ordem de exibição). */
    public static List<LasCategory> allActive() {
        return List.of(values());
    }
}
