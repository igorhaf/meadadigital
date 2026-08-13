package com.meada.admin.invitations;

/**
 * Limite do plano atingido ao criar um convite (camada 5.20 #77). Levantada pelo
 * {@link InvitationService} quando {@code companies.max_admins} não é null e o total de
 * usuários da empresa + convites ativos já alcançou o teto. Mapeada para 409
 * plan_limit_exceeded no {@link InvitationController}.
 *
 * <p>É regra de negócio (não erro sistêmico) — estende {@link InvitationException} como as
 * demais falhas do fluxo de convite, tratada inline no controller (fora do
 * GlobalExceptionHandler).
 */
public class PlanLimitExceededException extends InvitationException {
    public PlanLimitExceededException() {
        super("plan limit exceeded: max_admins");
    }
}
