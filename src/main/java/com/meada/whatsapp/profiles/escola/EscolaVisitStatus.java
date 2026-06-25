package com.meada.whatsapp.profiles.escola;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Status de uma visita agendada à escola (camada 8.19, ESCAPADA 2). Transições:
 * <pre>
 *   agendada  → realizada, cancelada
 *   realizada → (terminal)
 *   cancelada → (terminal)
 * </pre>
 * Transição inválida → 409 invalid_status_transition no controller.
 *
 * <p>{@code agendada} (confirmação, com data+período) e {@code cancelada} (defensiva) notificam;
 * {@code realizada} é silenciosa. A visita é agenda LEVE (dia+período), SEM conflito de capacidade.
 *
 * <p>Espelhado 1:1 por {@code frontend/profiles/escola/escola-visit-status.ts}
 * (EscolaVisitStatusParityTest garante a paridade Java↔TS).
 */
public enum EscolaVisitStatus {
    AGENDADA("agendada", "Agendada"),
    REALIZADA("realizada", "Realizada"),
    CANCELADA("cancelada", "Cancelada");

    private final String id;
    private final String label;

    EscolaVisitStatus(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public static Optional<EscolaVisitStatus> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(s -> s.id.equals(id)).findFirst();
    }

    public Set<EscolaVisitStatus> allowedNext() {
        return switch (this) {
            case AGENDADA -> Set.of(REALIZADA, CANCELADA);
            case REALIZADA, CANCELADA -> Set.of();
        };
    }

    public boolean canTransitionTo(EscolaVisitStatus next) {
        return allowedNext().contains(next);
    }

    /**
     * Texto fixo da notificação outbound ao ENTRAR neste status. null = não notifica.
     * {@code agendada} (confirmação com data+período) e {@code cancelada} (defensiva) avisam;
     * {@code realizada} é silenciosa.
     */
    public String notificationText(String visitDate, String period) {
        return switch (this) {
            case AGENDADA -> "Sua visita à escola está agendada para " + visitDate + " (" + periodLabel(period)
                + "). Vamos te esperar! Qualquer mudança, é só me avisar.";
            case CANCELADA -> "Sua visita à escola foi cancelada. Se quiser reagendar, é só me chamar.";
            case REALIZADA -> null;
        };
    }

    private static String periodLabel(String period) {
        return switch (period) {
            case "manha" -> "manhã";
            case "tarde" -> "tarde";
            default -> period;
        };
    }
}
