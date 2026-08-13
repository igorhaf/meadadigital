package com.meada.admin.invitations;

/**
 * O email do convite não bate com o email da conta que tenta aceitar. Impede que um link
 * vazado seja aceito por outra conta. Mapeado para 403 invitation_email_mismatch.
 */
public class InvitationEmailMismatchException extends InvitationException {
    public InvitationEmailMismatchException() {
        super("invitation email mismatch");
    }
}
