package com.meada.webchat;

import java.util.UUID;

/**
 * A empresa não tem nenhuma whatsapp_instance — portadora obrigatória da FK NOT NULL
 * conversations.whatsapp_instance_id (decisão documentada no {@link WebChatService}). Sem
 * instância, não é possível abrir a conversa web. O WebChatController mapeia para 409
 * {@code company_not_provisioned} (a empresa precisa de uma instância antes do widget operar).
 */
public class WebChatNoInstanceException extends RuntimeException {

    public WebChatNoInstanceException(UUID companyId) {
        super("Company " + companyId + " has no whatsapp_instance to carry the web conversation FK");
    }
}
