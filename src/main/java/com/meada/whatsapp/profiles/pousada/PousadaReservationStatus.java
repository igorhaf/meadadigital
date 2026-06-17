package com.meada.whatsapp.profiles.pousada;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Status de uma reserva de pousada (camada 7.6) com as transições cravadas (decisão 2):
 * <pre>
 *   reservado   → confirmado, cancelado
 *   confirmado  → checked_in, cancelado, no_show
 *   checked_in  → checked_out
 *   checked_out → (terminal)
 *   cancelado   → (terminal)
 *   no_show     → (terminal)
 * </pre>
 * Transição inválida → 409 invalid_status_transition no controller.
 *
 * <p>Espelhado 1:1 por {@code frontend/profiles/pousada/pousada-reservation-status.ts}
 * (PousadaReservationStatusParityTest garante a paridade Java↔TS).
 */
public enum PousadaReservationStatus {
    RESERVADO("reservado", "Reservado"),
    CONFIRMADO("confirmado", "Confirmado"),
    CHECKED_IN("checked_in", "Check-in feito"),
    CHECKED_OUT("checked_out", "Check-out feito"),
    CANCELADO("cancelado", "Cancelado"),
    NO_SHOW("no_show", "Não compareceu");

    private final String id;
    private final String label;

    PousadaReservationStatus(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public static Optional<PousadaReservationStatus> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(s -> s.id.equals(id)).findFirst();
    }

    /** Transições permitidas a partir deste status (decisão 2). */
    public Set<PousadaReservationStatus> allowedNext() {
        return switch (this) {
            case RESERVADO -> Set.of(CONFIRMADO, CANCELADO);
            case CONFIRMADO -> Set.of(CHECKED_IN, CANCELADO, NO_SHOW);
            case CHECKED_IN -> Set.of(CHECKED_OUT);
            case CHECKED_OUT, CANCELADO, NO_SHOW -> Set.of();
        };
    }

    public boolean canTransitionTo(PousadaReservationStatus next) {
        return allowedNext().contains(next);
    }

    /**
     * Texto fixo da notificação outbound ao ENTRAR neste status (decisão 3). null = não notifica.
     * {@code confirmado} (com quarto/datas/total) e {@code cancelado} avisam o cliente;
     * {@code reservado}/{@code checked_in}/{@code checked_out}/{@code no_show} são silenciosos. O
     * confirmado é parametrizado — ver {@link #notificationText(String, String, String, String)}.
     */
    public String notificationText() {
        return switch (this) {
            case CANCELADO -> "Sua reserva foi cancelada. Pra reagendar, é só me chamar.";
            case CONFIRMADO, RESERVADO, CHECKED_IN, CHECKED_OUT, NO_SHOW -> null;
        };
    }

    /**
     * Texto da notificação de CONFIRMAÇÃO (decisão 3), que depende dos dados da reserva. Para os
     * demais status, devolve {@link #notificationText()}. Texto defensivo (sem promessa de estrutura).
     */
    public String notificationText(String checkInLabel, String checkOutLabel, String roomName, String totalLabel) {
        if (this == CONFIRMADO) {
            return "Sua reserva foi confirmada: " + roomName + " de " + checkInLabel + " a "
                + checkOutLabel + ", total R$ " + totalLabel + ". Aguardamos você!";
        }
        return notificationText();
    }
}
