package com.meada.whatsapp.profiles.viagens.proposals;

import com.meada.whatsapp.messaging.ContactRepository;
import com.meada.whatsapp.messaging.ConversationRepository;
import com.meada.whatsapp.messaging.EvolutionCredentials;
import com.meada.whatsapp.messaging.MessageDirection;
import com.meada.whatsapp.messaging.MessageRepository;
import com.meada.whatsapp.messaging.MessageSender;
import com.meada.whatsapp.messaging.WhatsappInstanceRepository;
import com.meada.whatsapp.outbound.EvolutionSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Dispara a notificação outbound ao cliente quando a proposta muda de status (camada 8.18 / perfil
 * viagens): orcada (com total + destino), aprovada, fechada, recusada. Best-effort: falha NUNCA
 * reverte a transição. Persiste em {@code messages} (outbound/human). conversationId null → skip.
 * Texto defensivo, SEM promessa de "viagem perfeita" e SEM confirmar voo/hotel. Espelho EXATO do
 * EventProposalNotifier (chassi eventos 8.2).
 */
@Component
public class TravelProposalNotifier {

    private static final Logger log = LoggerFactory.getLogger(TravelProposalNotifier.class);

    private final ConversationRepository conversationRepository;
    private final ContactRepository contactRepository;
    private final WhatsappInstanceRepository whatsappInstanceRepository;
    private final MessageRepository messageRepository;
    private final EvolutionSender evolutionSender;

    public TravelProposalNotifier(ConversationRepository conversationRepository,
                                  ContactRepository contactRepository,
                                  WhatsappInstanceRepository whatsappInstanceRepository,
                                  MessageRepository messageRepository,
                                  EvolutionSender evolutionSender) {
        this.conversationRepository = conversationRepository;
        this.contactRepository = contactRepository;
        this.whatsappInstanceRepository = whatsappInstanceRepository;
        this.messageRepository = messageRepository;
        this.evolutionSender = evolutionSender;
    }

    public void notifyStatus(UUID companyId, UUID conversationId, String text) {
        if (text == null || conversationId == null) {
            return;
        }
        try {
            Optional<String> phone = contactRepository.findPhoneByConversationId(conversationId);
            Optional<UUID> instanceId = conversationRepository.findInstanceIdByConversation(conversationId);
            Optional<EvolutionCredentials> creds = instanceId.flatMap(whatsappInstanceRepository::findEvolutionCredentials);
            if (phone.isEmpty() || phone.get().isBlank() || creds.isEmpty()) {
                log.warn("viagens: proposta sem canal resolúvel p/ conversa {} — notificação não enviada", conversationId);
                return;
            }
            String keyId = evolutionSender.sendText(creds.get().instanceName(), creds.get().token(), phone.get(), text);
            messageRepository.insertIfNew(companyId, conversationId, MessageDirection.OUTBOUND, MessageSender.HUMAN, text, keyId);
        } catch (RuntimeException e) {
            log.warn("viagens: falha ao notificar status p/ conversa {} ({}) — proposta segue mesmo assim",
                conversationId, e.getMessage());
        }
    }
}
