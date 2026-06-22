package com.meada.whatsapp.profiles.estetica;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Status de um agendamento de estética (camada 8.3) — clone do SalonAppointmentStatus:
 * <pre>
 *   agendado   → confirmado, cancelado
 *   confirmado → realizado, cancelado, falta
 *   realizado  → (terminal)
 *   cancelado  → (terminal)
 *   falta      → (terminal)
 * </pre>
 * Transição inválida → 409 invalid_status_transition no controller.
 *
 * <p>Espelhado 1:1 por {@code frontend/profiles/estetica/aesthetic-appointment-status.ts}
 * (AestheticAppointmentStatusParityTest garante a paridade Java↔TS).
 */
public enum AestheticAppointmentStatus {
    AGENDADO("agendado", "Agendado"),
    CONFIRMADO("confirmado", "Confirmado"),
    REALIZADO("realizado", "Realizado"),
    CANCELADO("cancelado", "Cancelado"),
    FALTA("falta", "Falta");

    private final String id;
    private final String label;

    AestheticAppointmentStatus(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public static Optional<AestheticAppointmentStatus> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(s -> s.id.equals(id)).findFirst();
    }

    public Set<AestheticAppointmentStatus> allowedNext() {
        return switch (this) {
            case AGENDADO -> Set.of(CONFIRMADO, CANCELADO);
            case CONFIRMADO -> Set.of(REALIZADO, CANCELADO, FALTA);
            case REALIZADO, CANCELADO, FALTA -> Set.of();
        };
    }

    public boolean canTransitionTo(AestheticAppointmentStatus next) {
        return allowedNext().contains(next);
    }

    /** Estado terminal de cancelamento — devolve a sessão consumida ao pacote. */
    public boolean isCancelled() {
        return this == CANCELADO;
    }

    /**
     * Texto fixo da notificação outbound ao ENTRAR neste status. null = não notifica. {@code
     * confirmado} (com data/hora/profissional) e {@code cancelado} avisam o cliente; demais
     * silenciosos. Texto defensivo (sem promessa de resultado estético).
     */
    public String notificationText() {
        return switch (this) {
            case CANCELADO -> "Seu agendamento foi cancelado. Pra reagendar, é só me chamar.";
            case CONFIRMADO, AGENDADO, REALIZADO, FALTA -> null;
        };
    }

    /** Texto da CONFIRMAÇÃO (com dados do agendamento). Demais status → {@link #notificationText()}. */
    public String notificationText(String dateLabel, String timeLabel, String professionalName) {
        if (this == CONFIRMADO) {
            return "Seu agendamento foi confirmado pra " + dateLabel + " às " + timeLabel
                + " com " + professionalName + ". Te esperamos!";
        }
        return notificationText();
    }
}
