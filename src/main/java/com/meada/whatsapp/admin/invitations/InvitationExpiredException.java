package com.meada.whatsapp.admin.invitations;

/** Convite existe mas já passou de expires_at. Mapeado para 410 invitation_expired. */
public class InvitationExpiredException extends InvitationException {
    public InvitationExpiredException() {
        super("invitation expired");
    }
}
