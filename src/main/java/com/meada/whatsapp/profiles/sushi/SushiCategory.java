package com.meada.whatsapp.profiles.sushi;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Categorias de cardápio do perfil sushi (camada 7.1), MATERIALIZADAS — espelho 1:1 de
 * {@code frontend/profiles/sushi/sushi-categories.ts}. O {@code SushiCategoryParityTest}
 * garante que os dois nunca divergem (mesmo padrão do ProfileType da SM-A). A CHECK constraint
 * de {@code sushi_menu_items.category} (migration 30) trava os mesmos ids no banco.
 *
 * <p>{@code id} é a string estável (persistida) e {@code label} é o rótulo pt-BR exibido.
 */
public enum SushiCategory {
    ENTRADAS("entradas", "Entradas"),
    HOT_ROLLS("hot_rolls", "Hot rolls"),
    SASHIMI("sashimi", "Sashimi"),
    COMBINADOS("combinados", "Combinados"),
    BEBIDAS("bebidas", "Bebidas"),
    SOBREMESAS("sobremesas", "Sobremesas");

    private final String id;
    private final String label;

    SushiCategory(String id, String label) {
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
    public static Optional<SushiCategory> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(c -> c.id.equals(id)).findFirst();
    }

    /** Todas as categorias, na ordem de declaração (ordem de exibição). */
    public static List<SushiCategory> allActive() {
        return List.of(values());
    }
}
