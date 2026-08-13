package com.meada.webchat;

import com.meada.ai.AiResponse;
import com.meada.ai.Prompt;
import com.meada.ai.PromptBuilder;
import com.meada.ai.AiProvider;
import com.meada.messaging.Contact;
import com.meada.messaging.ContactRepository;
import com.meada.messaging.Conversation;
import com.meada.messaging.ConversationRepository;
import com.meada.messaging.MessageDirection;
import com.meada.messaging.MessageRepository;
import com.meada.messaging.MessageSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Orquestra o fluxo do widget de chat web (camada 5.25 #73). Espelho SÍNCRONO e enxuto do
 * {@link com.meada.outbound.OutboundService} para o canal 'web':
 *
 * <ol>
 *   <li>resolve a empresa pelo slug (404 se desconhecida/inativa) — feito no controller;
 *   <li>resolve/cria um contato 'web' para o sessionId (phone_number sintético
 *       {@code web:<sessionId>} — phone_number é NOT NULL no schema);
 *   <li>resolve/cria a conversa ABERTA do canal web (channel='web') desse contato;
 *   <li>persiste a inbound do visitante;
 *   <li>monta o prompt + chama a IA SÍNCRONO;
 *   <li>persiste a outbound da IA e devolve o reply.
 * </ol>
 *
 * <p><b>Decisão whatsapp_instance_id (NOT NULL no schema, sem migration nesta fase):</b>
 * conversations.whatsapp_instance_id é NOT NULL com FK composta para whatsapp_instances.
 * Não há instância no canal web — então o caminho mais limpo e correto é REUTILIZAR uma
 * instância existente da empresa como portador da FK; o {@code channel='web'} é o que
 * distingue a origem. Por isso o controller exige que a empresa tenha ao menos uma
 * whatsapp_instance (resolvida via {@link WebChatRepository#findAnyInstanceId}).
 *
 * <p><b>Defensivo por contrato:</b> falha da IA NÃO devolve 500 — devolve um fallback
 * educado (200), igual ao espírito do OutboundService (o canal web é leitura/escrita
 * pública; um erro de IA não pode quebrar o widget do site do cliente). Diferente do
 * OutboundService, aqui NÃO há retry/handoff/insights: é o caminho mínimo de um chat
 * web anônimo (sem telefone real para transferir, sem Evolution para enviar).
 */
@Service
public class WebChatService {

    private static final Logger log = LoggerFactory.getLogger(WebChatService.class);

    /** Nome fixo do contato web (visitante anônimo do site). */
    private static final String WEB_CONTACT_NAME = "Visitante Web";

    /** Resposta de fallback quando a IA falha — educada, em pt-BR, devolvida com 200. */
    private static final String FALLBACK_REPLY =
        "Desculpe, tive um problema para responder agora. "
            + "Pode tentar novamente em instantes?";

    private final ContactRepository contactRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final WebChatRepository webChatRepository;
    private final PromptBuilder promptBuilder;
    private final AiProvider aiProvider;

    public WebChatService(ContactRepository contactRepository,
                          ConversationRepository conversationRepository,
                          MessageRepository messageRepository,
                          WebChatRepository webChatRepository,
                          PromptBuilder promptBuilder,
                          AiProvider aiProvider) {
        this.contactRepository = contactRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.webChatRepository = webChatRepository;
        this.promptBuilder = promptBuilder;
        this.aiProvider = aiProvider;
    }

    /**
     * Processa uma mensagem do widget web e devolve a resposta da IA.
     *
     * @param companyId empresa resolvida pelo slug (o controller já validou existência/atividade)
     * @param sessionId id de sessão do visitante (gerado no browser, persistido em localStorage)
     * @param message   texto enviado pelo visitante (não-blank; o controller valida)
     * @return o reply da IA, ou o fallback educado se a IA/persistência falhar
     * @throws WebChatNoInstanceException se a empresa não tem nenhuma whatsapp_instance
     *         (portadora obrigatória da FK NOT NULL conversations.whatsapp_instance_id)
     */
    public String handle(UUID companyId, String sessionId, String message) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(message, "message must not be null");

        // Portador da FK NOT NULL: precisa de uma instância da empresa (ver javadoc da classe).
        UUID instanceId = webChatRepository.findAnyInstanceId(companyId)
            .orElseThrow(() -> new WebChatNoInstanceException(companyId));

        // Contato 'web': phone_number sintético (NOT NULL) + nome fixo. channels agrega o canal
        // (#74): {"web": "<sessionId>"}. O resolveOrCreate preenche o nome na 1ª vez.
        String syntheticPhone = "web:" + sessionId;
        Contact contact = contactRepository.resolveOrCreate(companyId, syntheticPhone, WEB_CONTACT_NAME);
        contactRepository.updateChannels(contact.id(),
            "{\"web\": \"" + sessionId.replace("\"", "") + "\"}");

        // Conversa ABERTA do canal web desse contato (channel='web'), reusando a instância como
        // portadora da FK. O índice unique de conversa aberta é por (contact_id, instance) — como
        // o contato web é distinto (phone sintético por sessão), não colide com WhatsApp.
        Conversation conversation =
            conversationRepository.resolveOpenWebOrCreate(companyId, contact.id(), instanceId);

        // Inbound do visitante. evolution_message_id sintético único (web:<uuid>) — o índice
        // de idempotência cobre só NOT NULL, então isto sempre insere (idempotência não se aplica
        // a web; cada POST é uma mensagem distinta).
        messageRepository.insertIfNew(
            companyId, conversation.id(), MessageDirection.INBOUND, MessageSender.CONTACT,
            message, "web:" + UUID.randomUUID());
        conversationRepository.touchLastMessageAt(conversation.id(), Instant.now());

        // Gera a resposta SÍNCRONO. Defensivo: qualquer falha (IA, prompt, persistência) vira o
        // fallback educado — o widget público nunca recebe 500 por erro nosso.
        String reply;
        try {
            Prompt prompt = promptBuilder.build(companyId, conversation.id(), message);
            AiResponse aiResponse = aiProvider.generate(prompt);
            reply = (aiResponse.reply() != null && !aiResponse.reply().isBlank())
                ? aiResponse.reply()
                : FALLBACK_REPLY;
        } catch (RuntimeException e) {
            log.warn("webchat: AI falhou para company {} conversation {} ({}) — fallback",
                companyId, conversation.id(), e.getMessage());
            reply = FALLBACK_REPLY;
        }

        // Persiste a outbound da IA. Best-effort: se a persistência falhar, ainda devolvemos o
        // reply ao visitante (a resposta importa mais que o registro). evolution_message_id
        // sintético único.
        try {
            messageRepository.insertIfNew(
                companyId, conversation.id(), MessageDirection.OUTBOUND, MessageSender.AI,
                reply, "web:" + UUID.randomUUID());
            conversationRepository.touchLastMessageAt(conversation.id(), Instant.now());
        } catch (RuntimeException e) {
            log.warn("webchat: falha ao persistir outbound da conversa {} ({})",
                conversation.id(), e.getMessage());
        }

        return reply;
    }

    /** Atalho de teste/diagnóstico: a empresa tem instância configurada? */
    Optional<UUID> peekInstance(UUID companyId) {
        return webChatRepository.findAnyInstanceId(companyId);
    }
}
