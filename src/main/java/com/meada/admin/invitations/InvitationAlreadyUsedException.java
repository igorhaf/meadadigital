package com.meada.admin.invitations;

/** Convite já foi aceito (used_at set). Mapeado para 409 invitation_already_used. */
public class InvitationAlreadyUsedException extends InvitationException {
    public InvitationAlreadyUsedException() {
        super("invitation already used");
    }
}
