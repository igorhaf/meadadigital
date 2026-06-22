package com.meada.whatsapp.profiles.estetica;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Status de um pacote multi-sessão de estética (camada 8.3) — NOVO (a escapada da SM):
 * <pre>
 *   pendente  → ativo, cancelado
 *   ativo     → esgotado, expirado, cancelado
 *   esgotado  → (terminal)   [mas o backend pode reabrir p/ 'ativo' ao DEVOLVER uma sessão]
 *   expirado  → (terminal)
 *   cancelado → (terminal)
 * </pre>
 * <ul>
 *   <li>{@code pendente} = pacote criado, aguardando o tenant confirmar o pagamento.</li>
 *   <li>{@code ativo} = pagamento confirmado; libera os agendamentos que CONSOMEM saldo.</li>
 *   <li>{@code esgotado} = sessions_remaining chegou a 0 (materializado pelo backend ao consumir a
 *       última). NÃO é uma transição "manual" — o controller não permite setar 'esgotado' à mão.</li>
 * </ul>
 * A reabertura esgotado→ativo (ao cancelar um agendamento que consumiu a última sessão) é feita pelo
 * SERVICE, fora da máquina de transição manual ({@link #canTransitionTo} é só pro PATCH do tenant).
 *
 * <p>Espelhado 1:1 por {@code frontend/profiles/estetica/aesthetic-package-status.ts}
 * (AestheticPackageStatusParityTest garante a paridade Java↔TS).
 */
public enum AestheticPackageStatus {
    PENDENTE("pendente", "Pendente"),
    ATIVO("ativo", "Ativo"),
    ESGOTADO("esgotado", "Esgotado"),
    EXPIRADO("expirado", "Expirado"),
    CANCELADO("cancelado", "Cancelado");

    private final String id;
    private final String label;

    AestheticPackageStatus(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public static Optional<AestheticPackageStatus> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(s -> s.id.equals(id)).findFirst();
    }

    /**
     * Transições MANUAIS permitidas pelo PATCH do tenant. 'esgotado' NÃO entra como destino manual
     * (é materializado pelo backend ao consumir a última sessão). 'expirado'/'cancelado' são
     * encerramentos manuais.
     */
    public Set<AestheticPackageStatus> allowedNext() {
        return switch (this) {
            case PENDENTE -> Set.of(ATIVO, CANCELADO);
            case ATIVO -> Set.of(EXPIRADO, CANCELADO);
            case ESGOTADO, EXPIRADO, CANCELADO -> Set.of();
        };
    }

    public boolean canTransitionTo(AestheticPackageStatus next) {
        return allowedNext().contains(next);
    }

    public boolean isTerminal() {
        return switch (this) {
            case ESGOTADO, EXPIRADO, CANCELADO -> true;
            case PENDENTE, ATIVO -> false;
        };
    }

    /**
     * Texto fixo da notificação outbound ao ENTRAR neste status. null = não notifica. Só {@code ativo}
     * (boas-vindas do pacote, com o procedimento) avisa o cliente; pendente/demais são silenciosos.
     */
    public String notificationText(String procedureName, int totalSessions) {
        if (this == ATIVO) {
            return "Seu pacote de " + totalSessions + " sessões de " + procedureName
                + " está ativo! É só me chamar pra agendar cada sessão.";
        }
        return null;
    }
}
