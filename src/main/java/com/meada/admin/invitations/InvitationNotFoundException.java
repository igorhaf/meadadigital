package com.meada.admin.invitations;

/** Token de convite inexistente. Mapeado para 404 invitation_not_found. */
public class InvitationNotFoundException extends InvitationException {
    public InvitationNotFoundException() {
        super("invitation not found");
    }
}
