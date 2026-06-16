package com.meada.whatsapp.admin.invitations;

/**
 * Base das falhas de negócio do fluxo de convite (camada 5.16 #6). O controller mapeia
 * cada subclasse para um status HTTP + reason ({error, reason}, shape do projeto). Não é
 * erro sistêmico — é regra de negócio do accept/create, fora do GlobalExceptionHandler.
 */
public abstract class InvitationException extends RuntimeException {
    protected InvitationException(String message) {
        super(message);
    }
}
