package com.meada.profiles.sushi.orders;

import com.meada.messaging.ContactRepository;
import com.meada.messaging.ConversationRepository;
import com.meada.messaging.EvolutionCredentials;
import com.meada.messaging.MessageDirection;
import com.meada.messaging.MessageRepository;
import com.meada.messaging.MessageSender;
import com.meada.messaging.WhatsappInstanceRepository;
import com.meada.outbound.EvolutionSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Dispara a notificação outbound ao cliente quando o pedido muda de status (camada 7.1,
 * decisão 6). Reusa o caminho de envio do core (resolve telefone + credenciais via a conversa,
 * EvolutionSender) — mesmo padrão do ReminderJob. EVOLUTION_DRY_RUN (dev) é honrado pela
 * implementação do EvolutionSender (loga em vez de enviar).
 *
 * <p>Best-effort por contrato: falha de envio NUNCA reverte a transição de status (que já foi
 * persistida) — loga warn e segue. A mensagem enviada também é persistida em {@code messages}
 * (outbound/human — é o restaurante avisando, não a IA), best-effort.
 */
@Component
public class SushiOrderNotifier {

    private static final Logger log = LoggerFactory.getLogger(SushiOrderNotifier.class);

    private final ConversationRepository conversationRepository;
    private final ContactRepository contactRepository;
    private final WhatsappInstanceRepository whatsappInstanceRepository;
    private final MessageRepository messageRepository;
    private final EvolutionSender evolutionSender;

    public SushiOrderNotifier(ConversationRepository conversationRepository,
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

    /** Envia o texto fixo do status ao cliente da conversa. Best-effort (nunca lança). */
    public void notifyStatus(UUID companyId, UUID conversationId, String text) {
        if (text == null) {
            return;   // status 'recebido' não notifica.
        }
        try {
            Optional<String> phone = contactRepository.findPhoneByConversationId(conversationId);
            Optional<UUID> instanceId = conversationRepository.findInstanceIdByConversation(conversationId);
            Optional<EvolutionCredentials> creds = instanceId
                .flatMap(whatsappInstanceRepository::findEvolutionCredentials);
            if (phone.isEmpty() || phone.get().isBlank() || creds.isEmpty()) {
                log.warn("sushi: pedido sem canal resolúvel (phone/creds) p/ conversa {} — notificação não enviada",
                    conversationId);
                return;
            }
            String keyId = evolutionSender.sendText(
                creds.get().instanceName(), creds.get().token(), phone.get(), text);
            // Persiste a notificação como mensagem outbound (sender=human: é o restaurante, não a IA).
            messageRepository.insertIfNew(companyId, conversationId,
                MessageDirection.OUTBOUND, MessageSender.HUMAN, text, keyId);
        } catch (RuntimeException e) {
            log.warn("sushi: falha ao notificar status p/ conversa {} ({}) — pedido segue mesmo assim",
                conversationId, e.getMessage());
        }
    }
}
