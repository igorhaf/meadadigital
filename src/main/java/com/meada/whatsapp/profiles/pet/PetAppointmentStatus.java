package com.meada.whatsapp.profiles.pet;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Status de um agendamento de pet (camada 7.8) com as transições cravadas (decisão 2):
 * <pre>
 *   agendado   → confirmado, cancelado
 *   confirmado → realizado, cancelado, falta
 *   realizado  → (terminal)
 *   cancelado  → (terminal)
 *   falta      → (terminal)
 * </pre>
 * Transição inválida → 409 invalid_status_transition no controller.
 *
 * <p>Espelhado 1:1 por {@code frontend/profiles/pet/pet-appointment-status.ts}
 * (PetAppointmentStatusParityTest garante a paridade Java↔TS).
 */
public enum PetAppointmentStatus {
    AGENDADO("agendado", "Agendado"),
    CONFIRMADO("confirmado", "Confirmado"),
    REALIZADO("realizado", "Realizado"),
    CANCELADO("cancelado", "Cancelado"),
    FALTA("falta", "Falta");

    private final String id;
    private final String label;

    PetAppointmentStatus(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public static Optional<PetAppointmentStatus> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(s -> s.id.equals(id)).findFirst();
    }

    /** Transições permitidas a partir deste status (decisão 2). */
    public Set<PetAppointmentStatus> allowedNext() {
        return switch (this) {
            case AGENDADO -> Set.of(CONFIRMADO, CANCELADO);
            case CONFIRMADO -> Set.of(REALIZADO, CANCELADO, FALTA);
            case REALIZADO, CANCELADO, FALTA -> Set.of();
        };
    }

    public boolean canTransitionTo(PetAppointmentStatus next) {
        return allowedNext().contains(next);
    }

    /**
     * Texto fixo da notificação outbound ao ENTRAR neste status (decisão 3). null = não notifica.
     * {@code confirmado} (com serviço/animal/profissional/data/hora) e {@code cancelado} avisam o
     * tutor; {@code agendado}/{@code realizado}/{@code falta} são silenciosos. Texto defensivo, SEM
     * diagnóstico — ver {@link #notificationText(String, String, String, String, String)}.
     */
    public String notificationText() {
        return switch (this) {
            case CANCELADO -> "O agendamento foi cancelado. Pra remarcar, é só me chamar.";
            case CONFIRMADO, AGENDADO, REALIZADO, FALTA -> null;
        };
    }

    /**
     * Texto da notificação de CONFIRMAÇÃO (decisão 3), com serviço + nome do animal + profissional +
     * data/hora. Para os demais status, devolve {@link #notificationText()}.
     */
    public String notificationText(String serviceName, String animalName, String professionalName,
                                   String dateLabel, String timeLabel) {
        if (this == CONFIRMADO) {
            return "Agendamento confirmado: " + serviceName + " do " + animalName + " com "
                + professionalName + " em " + dateLabel + " às " + timeLabel + ". Aguardamos vocês!";
        }
        return notificationText();
    }
}
