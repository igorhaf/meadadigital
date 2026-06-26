package com.meada.whatsapp.profiles.padaria;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Categorias de cardápio do perfil padaria (camada 8.8 / perfil padaria), MATERIALIZADAS — espelho 1:1
 * de {@code frontend/profiles/padaria/padaria-categories.ts}. Clone de
 * {@link com.meada.whatsapp.profiles.floricultura.FloriculturaCategory}. O
 * {@code PadariaCategoryParityTest} garante que os dois nunca divergem (mesmo padrão do ProfileType da
 * SM-A). A CHECK constraint de {@code padaria_menu_items.category} (migration 52) trava os mesmos ids
 * no banco.
 *
 * <p>{@code id} é a string estável (persistida, ASCII sem acento) e {@code label} é o rótulo pt-BR
 * exibido. {@code bolos_encomenda} costuma reunir os itens sob encomenda (made_to_order).
 */
public enum PadariaCategory {
    PAES("paes", "Pães"),
    SALGADOS("salgados", "Salgados"),
    DOCES_BALCAO("doces_balcao", "Doces de Balcão"),
    BOLOS_ENCOMENDA("bolos_encomenda", "Bolos sob Encomenda"),
    TORTAS("tortas", "Tortas"),
    BEBIDAS("bebidas", "Bebidas");

    private final String id;
    private final String label;

    PadariaCategory(String id, String label) {
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
    public static Optional<PadariaCategory> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(c -> c.id.equals(id)).findFirst();
    }

    /** Todas as categorias, na ordem de declaração (ordem de exibição). */
    public static List<PadariaCategory> allActive() {
        return List.of(values());
    }
}
