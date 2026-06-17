package com.meada.whatsapp.profiles.academia;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Status de uma matrícula de academia (camada 7.7) com as transições cravadas (decisão 2):
 * <pre>
 *   ativa     → suspensa, cancelada
 *   suspensa  → ativa, cancelada
 *   cancelada → (terminal)
 * </pre>
 * Transição inválida → 409 invalid_status_transition no controller.
 *
 * <p>Espelhado 1:1 por {@code frontend/profiles/academia/academia-membership-status.ts}
 * (AcademiaMembershipStatusParityTest garante a paridade Java↔TS).
 */
public enum AcademiaMembershipStatus {
    ATIVA("ativa", "Ativa"),
    SUSPENSA("suspensa", "Suspensa"),
    CANCELADA("cancelada", "Cancelada");

    private final String id;
    private final String label;

    AcademiaMembershipStatus(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public static Optional<AcademiaMembershipStatus> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(s -> s.id.equals(id)).findFirst();
    }

    /** Transições permitidas a partir deste status (decisão 2). */
    public Set<AcademiaMembershipStatus> allowedNext() {
        return switch (this) {
            case ATIVA -> Set.of(SUSPENSA, CANCELADA);
            case SUSPENSA -> Set.of(ATIVA, CANCELADA);
            case CANCELADA -> Set.of();
        };
    }

    public boolean canTransitionTo(AcademiaMembershipStatus next) {
        return allowedNext().contains(next);
    }

    /**
     * Texto fixo da notificação outbound ao ENTRAR neste status (decisão 3). null = não notifica.
     * {@code ativa} (boas-vindas, com o plano) e {@code cancelada} (despedida) avisam; {@code
     * suspensa} é silenciosa. Texto defensivo, SEM promessa de resultado corporal.
     */
    public String notificationText(String studentName, String planName) {
        return switch (this) {
            case ATIVA -> "Sua matrícula foi confirmada no plano " + planName
                + ". Bom treino e qualquer dúvida é só me chamar!";
            case CANCELADA -> "Sua matrícula foi cancelada. Pra voltar a treinar com a gente, é só me chamar.";
            case SUSPENSA -> null;
        };
    }
}
