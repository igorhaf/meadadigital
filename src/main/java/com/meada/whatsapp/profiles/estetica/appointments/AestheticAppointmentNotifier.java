package com.meada.whatsapp.profiles.estetica.appointments;

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
 * Notificação outbound ao cliente quando o agendamento de estética muda de status (camada 8.3):
 * confirmado/cancelado. Best-effort. conversationId null → skip. Espelho do SalonAppointmentNotifier.
 */
@Component
public class AestheticAppointmentNotifier {

    private static final Logger log = LoggerFactory.getLogger(AestheticAppointmentNotifier.class);

    private final ConversationRepository conversationRepository;
    private final ContactRepository contactRepository;
    private final WhatsappInstanceRepository whatsappInstanceRepository;
    private final MessageRepository messageRepository;
    private final EvolutionSender evolutionSender;

    public AestheticAppointmentNotifier(ConversationRepository conversationRepository,
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
                log.warn("estetica: agendamento sem canal resolúvel p/ conversa {} — notificação não enviada", conversationId);
                return;
            }
            String keyId = evolutionSender.sendText(creds.get().instanceName(), creds.get().token(), phone.get(), text);
            messageRepository.insertIfNew(companyId, conversationId, MessageDirection.OUTBOUND, MessageSender.HUMAN, text, keyId);
        } catch (RuntimeException e) {
            log.warn("estetica: falha ao notificar status p/ conversa {} ({}) — agendamento segue mesmo assim",
                conversationId, e.getMessage());
        }
    }
}
