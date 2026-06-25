package com.meada.whatsapp.profiles.atelie;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Tipo de projeto de uma proposta de ateliê (camada 8.14) — um CAMPO da proposta, NÃO um perfil.
 * O MESMO perfil 'atelie' serve os três tipos de negócio: costura sob medida, arte e design.
 *
 * <p>É uma enum LOCAL do perfil atelie (NÃO confundir com a enum de plataforma {@code ProfileType}).
 * Hardcoded + materializado — espelho 1:1 de {@code frontend/profiles/atelie/atelie-project-type.ts}
 * (AtelieProjectTypeParityTest garante a paridade Java↔TS). A CHECK constraint de
 * {@code atelie_proposals.project_type} (migration 58) trava os mesmos ids no banco.
 *
 * <p>{@code id} é a string estável (persistida, ASCII) e {@code label} é o rótulo pt-BR exibido.
 */
public enum AtelieProjectType {
    COSTURA("costura", "Costura sob medida"),
    ARTE("arte", "Arte"),
    DESIGN("design", "Design");

    private final String id;
    private final String label;

    AtelieProjectType(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    /** Resolve um tipo pelo id estável. Optional vazio se inválido/null. */
    public static Optional<AtelieProjectType> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(t -> t.id.equals(id)).findFirst();
    }

    /** Todos os tipos, na ordem de declaração (ordem de exibição). */
    public static List<AtelieProjectType> allActive() {
        return List.of(values());
    }
}
