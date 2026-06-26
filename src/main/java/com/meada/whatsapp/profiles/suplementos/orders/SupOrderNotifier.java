package com.meada.whatsapp.profiles.suplementos.orders;

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
 * Dispara a notificação outbound ao cliente quando o pedido suplementos muda de status (camada 8.24).
 * Clone literal do {@link com.meada.whatsapp.profiles.lingerie.orders.LingerieOrderNotifier} /
 * {@link com.meada.whatsapp.profiles.adega.orders.AdegaOrderNotifier} (só o prefixo de log muda).
 * Reusa o caminho de envio do core (resolve telefone + credenciais via a conversa, EvolutionSender).
 * EVOLUTION_DRY_RUN (dev) é honrado pela implementação do EvolutionSender.
 *
 * <p>Best-effort por contrato: falha de envio NUNCA reverte a transição de status (já persistida) —
 * loga warn e segue. A mensagem enviada também é persistida em {@code messages} (outbound/human — é a
 * loja avisando, não a IA), best-effort.
 */
@Component
public class SupOrderNotifier {

    private static final Logger log = LoggerFactory.getLogger(SupOrderNotifier.class);

    private final ConversationRepository conversationRepository;
    private final ContactRepository contactRepository;
    private final WhatsappInstanceRepository whatsappInstanceRepository;
    private final MessageRepository messageRepository;
    private final EvolutionSender evolutionSender;

    public SupOrderNotifier(ConversationRepository conversationRepository,
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
            return;   // status 'aguardando'/'cancelado' não notificam.
        }
        try {
            Optional<String> phone = contactRepository.findPhoneByConversationId(conversationId);
            Optional<UUID> instanceId = conversationRepository.findInstanceIdByConversation(conversationId);
            Optional<EvolutionCredentials> creds = instanceId
                .flatMap(whatsappInstanceRepository::findEvolutionCredentials);
            if (phone.isEmpty() || phone.get().isBlank() || creds.isEmpty()) {
                log.warn("suplementos: pedido sem canal resolúvel (phone/creds) p/ conversa {} — notificação não enviada",
                    conversationId);
                return;
            }
            String keyId = evolutionSender.sendText(
                creds.get().instanceName(), creds.get().token(), phone.get(), text);
            // Persiste a notificação como mensagem outbound (sender=human: é a loja, não a IA).
            messageRepository.insertIfNew(companyId, conversationId,
                MessageDirection.OUTBOUND, MessageSender.HUMAN, text, keyId);
        } catch (RuntimeException e) {
            log.warn("suplementos: falha ao notificar status p/ conversa {} ({}) — pedido segue mesmo assim",
                conversationId, e.getMessage());
        }
    }
}
