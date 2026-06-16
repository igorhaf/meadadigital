package com.meada.whatsapp.admin.invitations;

/** Email malformado na criação do convite. Mapeado para 400 invalid_email. */
public class InvalidInvitationEmailException extends InvitationException {
    public InvalidInvitationEmailException() {
        super("invalid email");
    }
}
