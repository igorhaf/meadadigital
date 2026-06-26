package com.meada.whatsapp.profiles.viagens;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Status de uma proposta de viagem (camada 8.18 / perfil viagens) com as transições cravadas —
 * IDÊNTICO ao {@link com.meada.whatsapp.profiles.eventos.EventProposalStatus} (chassi eventos 8.2,
 * clonado): o funil de uma agência de viagens é o mesmo de uma casa de festas:
 * <pre>
 *   rascunho   → orcada, cancelada
 *   orcada     → aprovada, recusada, cancelada
 *   aprovada   → fechada, cancelada
 *   fechada    → realizada, cancelada
 *   realizada  → (terminal)
 *   recusada   → (terminal)
 *   cancelada  → (terminal)
 * </pre>
 * (rascunho = proposta aberta sem cotação; orcada = aguardando aprovação do cliente; aprovada =
 * cliente aceitou; fechada = "contrato"/sinal combinado fora do app; realizada = a viagem aconteceu.)
 *
 * <p>Transição inválida → 409 invalid_status_transition no controller. A passagem para
 * {@code orcada} exige {@code total_cents > 0} (validada no service → 400 empty_budget).
 *
 * <p>{@link #itemsLocked()} trava DOIS tipos de sub-item ao mesmo tempo: os itens de COTAÇÃO E o
 * ITINERÁRIO (a escapada multi-dia). Espelhado 1:1 por
 * {@code frontend/profiles/viagens/travel-proposal-status.ts} (TravelProposalStatusParityTest
 * garante a paridade Java↔TS).
 */
public enum TravelProposalStatus {
    RASCUNHO("rascunho", "Rascunho"),
    ORCADA("orcada", "Orçada"),
    APROVADA("aprovada", "Aprovada"),
    RECUSADA("recusada", "Recusada"),
    FECHADA("fechada", "Fechada"),
    REALIZADA("realizada", "Realizada"),
    CANCELADA("cancelada", "Cancelada");

    private final String id;
    private final String label;

    TravelProposalStatus(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public static Optional<TravelProposalStatus> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(s -> s.id.equals(id)).findFirst();
    }

    /** Transições permitidas a partir deste status. */
    public Set<TravelProposalStatus> allowedNext() {
        return switch (this) {
            case RASCUNHO -> Set.of(ORCADA, CANCELADA);
            case ORCADA -> Set.of(APROVADA, RECUSADA, CANCELADA);
            case APROVADA -> Set.of(FECHADA, CANCELADA);
            case FECHADA -> Set.of(REALIZADA, CANCELADA);
            case REALIZADA, RECUSADA, CANCELADA -> Set.of();
        };
    }

    public boolean canTransitionTo(TravelProposalStatus next) {
        return allowedNext().contains(next);
    }

    /** Estado terminal — proposta encerrada (preenche closed_at). */
    public boolean isTerminal() {
        return allowedNext().isEmpty();
    }

    /**
     * Estados em que os ITENS da proposta (COTAÇÃO E ITINERÁRIO) NÃO podem mais ser mutados
     * (decisão cravada: trava de estado a partir de 'fechada'). Depois que o contrato fechou, o
     * escopo congela; nos terminais a proposta está encerrada. Mutar item/dia nesses estados → 409
     * proposal_locked. Antes disso (rascunho/orcada/aprovada) a equipe ainda ajusta cotação e roteiro.
     */
    public boolean itemsLocked() {
        return switch (this) {
            case FECHADA, REALIZADA, RECUSADA, CANCELADA -> true;
            case RASCUNHO, ORCADA, APROVADA -> false;
        };
    }

    /**
     * Texto fixo da notificação outbound ao ENTRAR neste status. null = não notifica.
     * {@code orcada} (com total), {@code aprovada}, {@code fechada} e {@code recusada} avisam o
     * cliente; {@code rascunho}/{@code realizada}/{@code cancelada} são silenciosos. Texto defensivo,
     * SEM promessa de "viagem perfeita". Ver {@link #notificationText(String, String)} para o caso
     * orcada (com total + destino).
     */
    public String notificationText() {
        return switch (this) {
            case APROVADA -> "Recebemos sua aprovação! Vamos preparar os próximos passos da sua viagem.";
            case FECHADA -> "Tudo certo, sua viagem está confirmada com a gente! Em breve alinhamos os detalhes finais.";
            case RECUSADA -> "Tudo bem, registramos que a proposta não foi adiante. Seguimos à disposição para o que precisar.";
            case ORCADA, RASCUNHO, REALIZADA, CANCELADA -> null;
        };
    }

    /**
     * Texto da notificação de ORÇAMENTO (status orcada), com total formatado + destino. Para os
     * demais status, devolve {@link #notificationText()}.
     */
    public String notificationText(String travelLabel, String totalLabel) {
        if (this == ORCADA) {
            return "Orçamento da sua " + travelLabel + ": " + totalLabel
                + ". Posso seguir com a reserva? Responda com sim para aprovar ou não para recusar.";
        }
        return notificationText();
    }
}
