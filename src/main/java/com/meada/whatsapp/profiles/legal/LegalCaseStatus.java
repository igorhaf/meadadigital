package com.meada.whatsapp.profiles.legal;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Status de um processo (camada 7.2), MATERIALIZADO — espelho 1:1 de
 * {@code frontend/profiles/legal/legal-case-status.ts}. O {@link LegalCaseStatusParityTest}
 * garante a paridade Java↔TS; a CHECK constraint de {@code legal_cases.status} (migration 31)
 * trava os mesmos ids no banco.
 *
 * <p>Diferente do sushi (linear), aqui a transição é LIVRE (qualquer status → qualquer status):
 * o advogado pode reativar um processo arquivado, suspender e reativar, etc.
 */
public enum LegalCaseStatus {
    ATIVO("ativo", "Ativo"),
    SUSPENSO("suspenso", "Suspenso"),
    ARQUIVADO("arquivado", "Arquivado"),
    ENCERRADO("encerrado", "Encerrado");

    private final String id;
    private final String label;

    LegalCaseStatus(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public static Optional<LegalCaseStatus> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(s -> s.id.equals(id)).findFirst();
    }

    public static List<LegalCaseStatus> allActive() {
        return List.of(values());
    }

    /** Transição livre: qualquer status pode ir para qualquer outro (decisão cravada). */
    public boolean canTransitionTo(LegalCaseStatus next) {
        return next != null;
    }

    /**
     * Texto fixo da notificação outbound ao cliente quando o processo entra neste status
     * (decisão 4). null para ATIVO (reativação não notifica). Defensivo juridicamente: sem
     * opinião, sempre orienta a procurar o escritório.
     */
    public String notificationText() {
        return switch (this) {
            case SUSPENSO -> "Informação sobre seu processo: foi colocado em SUSPENSÃO. "
                + "Em caso de dúvida, entre em contato com nosso escritório.";
            case ARQUIVADO -> "Informação sobre seu processo: foi ARQUIVADO. "
                + "Em caso de dúvida, entre em contato com nosso escritório.";
            case ENCERRADO -> "Informação sobre seu processo: foi ENCERRADO. "
                + "Em caso de dúvida, entre em contato com nosso escritório.";
            case ATIVO -> null;
        };
    }
}
