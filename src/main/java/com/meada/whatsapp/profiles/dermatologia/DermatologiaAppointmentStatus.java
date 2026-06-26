package com.meada.whatsapp.profiles.dermatologia;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Status de uma consulta de dermatologia (camada 8.11) com as transições cravadas (FEMININO):
 * <pre>
 *   agendada   → confirmada, cancelada
 *   confirmada → realizada, cancelada, falta
 *   realizada  → (terminal)
 *   cancelada  → (terminal)
 *   falta      → (terminal)
 * </pre>
 * Transição inválida → 409 invalid_status_transition no controller.
 *
 * <p>Espelhado 1:1 por {@code frontend/profiles/dermatologia/dermatologia-appointment-status.ts}
 * (DermatologiaAppointmentStatusParityTest garante a paridade Java↔TS). Espelho do
 * NutriAppointmentStatus, com gênero feminino.
 */
public enum DermatologiaAppointmentStatus {
    AGENDADA("agendada", "Agendada"),
    CONFIRMADA("confirmada", "Confirmada"),
    REALIZADA("realizada", "Realizada"),
    CANCELADA("cancelada", "Cancelada"),
    FALTA("falta", "Falta");

    private final String id;
    private final String label;

    DermatologiaAppointmentStatus(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public static Optional<DermatologiaAppointmentStatus> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(s -> s.id.equals(id)).findFirst();
    }

    /** Transições permitidas a partir deste status. */
    public Set<DermatologiaAppointmentStatus> allowedNext() {
        return switch (this) {
            case AGENDADA -> Set.of(CONFIRMADA, CANCELADA);
            case CONFIRMADA -> Set.of(REALIZADA, CANCELADA, FALTA);
            case REALIZADA, CANCELADA, FALTA -> Set.of();
        };
    }

    public boolean canTransitionTo(DermatologiaAppointmentStatus next) {
        return allowedNext().contains(next);
    }

    /**
     * Texto fixo da notificação outbound ao ENTRAR neste status. null = não notifica.
     * {@code confirmada} (com tipo/profissional/data/hora) e {@code cancelada} avisam o paciente;
     * {@code agendada}/{@code realizada}/{@code falta} são silenciosos. Texto acolhedor, SEM conteúdo
     * clínico — ver {@link #notificationText(String, String, String, String)}.
     */
    public String notificationText() {
        return switch (this) {
            case CANCELADA -> "Sua consulta foi cancelada. Para remarcar, é só me chamar.";
            case CONFIRMADA, AGENDADA, REALIZADA, FALTA -> null;
        };
    }

    /**
     * Texto da notificação de CONFIRMAÇÃO, com o tipo de atendimento + profissional + data/hora.
     * Para os demais status, devolve {@link #notificationText()}.
     */
    public String notificationText(String typeLabel, String professionalName, String dateLabel, String timeLabel) {
        if (this == CONFIRMADA) {
            return "Consulta confirmada: " + typeLabel + " com " + professionalName + " em "
                + dateLabel + " às " + timeLabel + ". Até lá!";
        }
        return notificationText();
    }
}
