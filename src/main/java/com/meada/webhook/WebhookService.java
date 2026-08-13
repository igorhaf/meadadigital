package com.meada.webhook;

import com.meada.messaging.Contact;
import com.meada.messaging.ContactRepository;
import com.meada.messaging.Conversation;
import com.meada.messaging.ConversationRepository;
import com.meada.messaging.Message;
import com.meada.messaging.MessageDirection;
import com.meada.messaging.MessagePayloadNormalizer;
import com.meada.messaging.MessageRepository;
import com.meada.messaging.MessageSender;
import com.meada.messaging.NormalizedJid;
import com.meada.messaging.WhatsappInstance;
import com.meada.messaging.WhatsappInstanceRepository;
import com.meada.outbound.MessageInboundProcessedEvent;
import com.meada.webhook.dto.EvolutionWebhookPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Orquestra o fluxo de uma mensagem inbound do webhook da Evolution, da
 * classificação à persistência. Termina com a mensagem inbound persistida — a
 * resposta da IA e o envio outbound são de outra camada.
 *
 * <p>Fluxo de curto-circuito (mais barato primeiro; cada ramo retorna um
 * {@link WebhookOutcome} e loga no ponto onde tem o contexto):
 * <ol>
 *   <li>evento != messages.upsert → IGNORED_NON_MESSAGE_EVENT
 *   <li>fromMe true/null → IGNORED_FROM_ME (eco de mensagem nossa)
 *   <li>instância desconhecida → IGNORED_UNKNOWN_INSTANCE
 *   <li>JID group/broadcast/unknown → IGNORED_*
 *   <li>sem texto → IGNORED_NON_TEXT
 *   <li>guard de frescor por messageTimestamp → IGNORED_STALE (rejeita
 *       append-on-reconnect; ver RISKS.md incidente re-sync 2026-06-10)
 *   <li>persistência: contato → conversa → message; duplicata → IGNORED_DUPLICATE,
 *       nova → touchLastMessageAt + PROCESSED
 * </ol>
 *
 * <p>Spring é escritor único de messages (via service_role). @Transactional torna
 * contato+conversa+message+touch atômicos: se a inserção falhar, o contato/conversa
 * criados nesta chamada revertem.
 *
 * <p>No ramo PROCESSED publica um {@link MessageInboundProcessedEvent} (dentro da
 * transação); o OutboundEventListener o consome em AFTER_COMMIT/async e dispara a
 * resposta da IA. O próprio envio outbound continua sendo de outra camada (3.3).
 */
@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private static final String EVENT_MESSAGES_UPSERT = "messages.upsert";

    private final WhatsappInstanceRepository instanceRepository;
    private final ContactRepository contactRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final MessagePayloadNormalizer normalizer;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Idade máxima (segundos) de uma mensagem para ser processada. Acima disto, o
     * guard de frescor a rejeita como IGNORED_STALE — defesa contra o
     * append-on-reconnect do Baileys/Evolution (ver RISKS.md incidente 2026-06-10).
     * Default 180s: largo o bastante para latência Evolution→backend + processamento
     * @Async + clock skew NTP; apertado o bastante para barrar histórico (minutos+).
     */
    private final long messageMaxAgeSeconds;

    public WebhookService(WhatsappInstanceRepository instanceRepository,
                          ContactRepository contactRepository,
                          ConversationRepository conversationRepository,
                          MessageRepository messageRepository,
                          MessagePayloadNormalizer normalizer,
                          ApplicationEventPublisher eventPublisher,
                          @Value("${webhook.message-max-age-seconds:180}") long messageMaxAgeSeconds) {
        this.instanceRepository = instanceRepository;
        this.contactRepository = contactRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.normalizer = normalizer;
        this.eventPublisher = eventPublisher;
        this.messageMaxAgeSeconds = messageMaxAgeSeconds;
    }

    @Transactional
    public WebhookOutcome process(EvolutionWebhookPayload payload) {
        // 1. evento
        if (!EVENT_MESSAGES_UPSERT.equals(payload.event())) {
            return logOutcome(WebhookOutcome.IGNORED_NON_MESSAGE_EVENT, "event", payload.event());
        }

        EvolutionWebhookPayload.MessageData data = payload.data();
        EvolutionWebhookPayload.MessageKey key = data.key();

        // 2. fromMe true OU null (defensivo: sem sinal, assume nossa para evitar
        //    loop de IA respondendo a si mesma).
        Boolean fromMe = key.fromMe();
        if (fromMe == null || fromMe) {
            return logOutcome(WebhookOutcome.IGNORED_FROM_ME, "from_me", String.valueOf(fromMe));
        }

        // 3. tenant (instance_name → company_id). Antes de normalizar o JID:
        //    instância desconhecida corta cedo, sem custo de classificação.
        Optional<WhatsappInstance> instance = instanceRepository.findByInstanceName(payload.instance());
        if (instance.isEmpty()) {
            return logOutcome(WebhookOutcome.IGNORED_UNKNOWN_INSTANCE, "instance", payload.instance());
        }
        WhatsappInstance wi = instance.get();

        // 4. classifica o JID
        NormalizedJid jid = normalizer.normalize(key.remoteJid());
        switch (jid.type()) {
            case GROUP -> {
                return logOutcome(WebhookOutcome.IGNORED_GROUP, "instance", payload.instance());
            }
            case BROADCAST -> {
                return logOutcome(WebhookOutcome.IGNORED_BROADCAST, "instance", payload.instance());
            }
            case UNKNOWN -> {
                return logOutcome(WebhookOutcome.IGNORED_UNKNOWN_JID,
                    "instance", payload.instance(), "raw_jid", jid.rawJid());
            }
            case USER -> { /* segue */ }
        }

        // 5. extrai texto
        String content = extractText(data.message());
        if (content == null) {
            return logOutcome(WebhookOutcome.IGNORED_NON_TEXT, "instance", payload.instance());
        }

        // 6. guard de frescor: rejeita mensagens antigas (append-on-reconnect do
        //    Baileys/Evolution despeja histórico como messages.upsert; ver
        //    RISKS.md incidente 2026-06-10). messageTimestamp null não rejeita
        //    (defensivo, mesma semântica do resolveTimestamp). Limitação:
        //    NÃO protege contra mensagens novas chegando ao vivo num número
        //    não-dedicado — esse cenário é coberto pelo dry-run em STAGE=local.
        Long messageTs = data.messageTimestamp();
        if (messageTs != null) {
            long ageSeconds = Instant.now().getEpochSecond() - messageTs;
            if (ageSeconds > messageMaxAgeSeconds) {
                return logOutcome(WebhookOutcome.IGNORED_STALE,
                    "instance", payload.instance(),
                    "evolution_message_id", key.id(),
                    "messageTimestamp", String.valueOf(messageTs),
                    "ageSeconds", String.valueOf(ageSeconds));
            }
        }

        // 7. persistência (transacional)
        Contact contact = contactRepository.resolveOrCreate(
            wi.companyId(), jid.phoneNumber(), data.pushName());
        Conversation conversation = conversationRepository.resolveOpenOrCreate(
            wi.companyId(), contact.id(), wi.id());
        Optional<Message> inserted = messageRepository.insertIfNew(
            wi.companyId(), conversation.id(),
            MessageDirection.INBOUND, MessageSender.CONTACT, content, key.id());

        if (inserted.isEmpty()) {
            // reentrega: a mensagem (e o touch) já foram processados na 1ª vez.
            return logOutcome(WebhookOutcome.IGNORED_DUPLICATE,
                "instance", payload.instance(), "evolution_message_id", key.id());
        }

        conversationRepository.touchLastMessageAt(conversation.id(), resolveTimestamp(data.messageTimestamp()));

        // 8. gate de bloqueio (camada 5.11): se o tenant bloqueou o contato, a mensagem
        //    JÁ foi persistida acima (histórico íntegro — o tenant vê que o bloqueado
        //    tentou contato, e a conversa aparece com atividade recente pelo touch). Mas
        //    NÃO disparamos a IA: sem publishEvent → OutboundService não roda → sem
        //    resposta automática. É a semântica de #41 "não recebe mais resposta automática".
        if (contact.blocked()) {
            return logOutcome(WebhookOutcome.IGNORED_CONTACT_BLOCKED,
                "instance", payload.instance(), "contact_id", contact.id().toString());
        }

        // Dispara o pipeline de resposta da IA. Publicado DENTRO da transação: o
        // OutboundEventListener é @TransactionalEventListener(AFTER_COMMIT), então só
        // processa depois que esta inbound está durável (ele relê handled_by/phone/
        // histórico do banco). Só no ramo PROCESSED (inbound NOVA de cliente) — nunca
        // em IGNORED_* (duplicata/eco/grupo/bloqueado não disparam IA).
        eventPublisher.publishEvent(new MessageInboundProcessedEvent(
            wi.companyId(), conversation.id(), wi.id(), content));
        return logOutcome(WebhookOutcome.PROCESSED, "instance", payload.instance());
    }

    /** conversation (texto simples) ?? extendedTextMessage.text; null se nenhum. */
    private String extractText(EvolutionWebhookPayload.MessageContent message) {
        if (message == null) {
            return null;
        }
        if (message.conversation() != null) {
            return message.conversation();
        }
        if (message.extendedTextMessage() != null) {
            return message.extendedTextMessage().text();
        }
        return null;
    }

    /** epoch SEGUNDOS → Instant; null → agora (a mensagem chegou agora). */
    private Instant resolveTimestamp(Long messageTimestamp) {
        return messageTimestamp != null ? Instant.ofEpochSecond(messageTimestamp) : Instant.now();
    }

    /**
     * Loga o outcome no nível que ele carrega, com pares chave=valor estruturados.
     * Nunca recebe content (PII) nem telefone. Retorna o outcome para o caller.
     */
    private WebhookOutcome logOutcome(WebhookOutcome outcome, String... kv) {
        StringBuilder msg = new StringBuilder("webhook outcome=").append(outcome.name());
        for (int i = 0; i + 1 < kv.length; i += 2) {
            msg.append(' ').append(kv[i]).append('=').append(kv[i + 1]);
        }
        if (outcome.logLevel() == Level.WARN) {
            log.warn(msg.toString());
        } else {
            log.info(msg.toString());
        }
        return outcome;
    }
}
