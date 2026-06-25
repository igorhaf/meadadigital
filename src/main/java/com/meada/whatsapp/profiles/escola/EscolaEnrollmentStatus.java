package com.meada.whatsapp.profiles.escola;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Status de uma matrícula de escola (camada 8.19) — clone de
 * {@link com.meada.whatsapp.profiles.academia.AcademiaMembershipStatus} (assinatura). Transições:
 * <pre>
 *   ativa     → suspensa, cancelada
 *   suspensa  → ativa, cancelada
 *   cancelada → (terminal)
 * </pre>
 * Transição inválida → 409 invalid_status_transition no controller.
 *
 * <p>{@code cancelada} materializa end_date e LIBERA a vaga (o count de capacity filtra status
 * &lt;&gt; 'cancelada'); {@code suspensa} MANTÉM a vaga ocupada (decisão cravada da academia).
 *
 * <p>Espelhado 1:1 por {@code frontend/profiles/escola/escola-enrollment-status.ts}
 * (EscolaEnrollmentStatusParityTest garante a paridade Java↔TS).
 */
public enum EscolaEnrollmentStatus {
    ATIVA("ativa", "Ativa"),
    SUSPENSA("suspensa", "Suspensa"),
    CANCELADA("cancelada", "Cancelada");

    private final String id;
    private final String label;

    EscolaEnrollmentStatus(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public static Optional<EscolaEnrollmentStatus> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(s -> s.id.equals(id)).findFirst();
    }

    /** Transições permitidas a partir deste status. */
    public Set<EscolaEnrollmentStatus> allowedNext() {
        return switch (this) {
            case ATIVA -> Set.of(SUSPENSA, CANCELADA);
            case SUSPENSA -> Set.of(ATIVA, CANCELADA);
            case CANCELADA -> Set.of();
        };
    }

    public boolean canTransitionTo(EscolaEnrollmentStatus next) {
        return allowedNext().contains(next);
    }

    /**
     * Texto fixo da notificação outbound ao ENTRAR neste status. null = não notifica.
     * {@code ativa} (boas-vindas, com turma+série+turno) e {@code cancelada} (despedida) avisam;
     * {@code suspensa} é silenciosa. Texto defensivo — SEM promessa de vaga não confirmada, SEM
     * valor inventado, SEM parecer pedagógico.
     */
    public String notificationText(String studentName, String className, String grade, String shift) {
        return switch (this) {
            case ATIVA -> "A matrícula de " + studentName + " foi confirmada na turma " + className
                + " (" + grade + ", " + shiftLabel(shift) + "). Seja muito bem-vindo(a)! Qualquer dúvida, é só chamar.";
            case CANCELADA -> "A matrícula de " + studentName + " foi cancelada. Se quiser retomar, é só me chamar.";
            case SUSPENSA -> null;
        };
    }

    private static String shiftLabel(String shift) {
        return switch (shift) {
            case "manha" -> "manhã";
            case "tarde" -> "tarde";
            case "integral" -> "integral";
            default -> shift;
        };
    }
}
