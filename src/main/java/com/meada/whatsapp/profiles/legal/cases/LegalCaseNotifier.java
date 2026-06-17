package com.meada.whatsapp.profiles.legal.cases;

import com.meada.whatsapp.messaging.EvolutionCredentials;
import com.meada.whatsapp.messaging.MessageDirection;
import com.meada.whatsapp.messaging.MessageRepository;
import com.meada.whatsapp.messaging.MessageSender;
import com.meada.whatsapp.messaging.WhatsappInstanceRepository;
import com.meada.whatsapp.outbound.EvolutionSender;
import com.meada.whatsapp.profiles.legal.clients.LegalClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Notificação outbound ao cliente quando um processo muda de status (camada 7.2, decisão 4).
 * Espelho do SushiOrderNotifier, mas o canal é resolvido a partir do {@code legal_client.
 * contact_id} (o processo não tem conversa própria): contato → conversa mais recente → instância
 * + telefone. Se o cliente NÃO tem contact_id (não vinculado ao WhatsApp), pula em silêncio.
 *
 * <p>Best-effort: falha de envio NUNCA reverte a transição de status (já persistida).
 */
@Component
public class LegalCaseNotifier {

    private static final Logger log = LoggerFactory.getLogger(LegalCaseNotifier.class);

    private final JdbcTemplate jdbcTemplate;
    private final LegalClientRepository clientRepository;
    private final WhatsappInstanceRepository whatsappInstanceRepository;
    private final MessageRepository messageRepository;
    private final EvolutionSender evolutionSender;

    public LegalCaseNotifier(JdbcTemplate jdbcTemplate, LegalClientRepository clientRepository,
                             WhatsappInstanceRepository whatsappInstanceRepository,
                             MessageRepository messageRepository, EvolutionSender evolutionSender) {
        this.jdbcTemplate = jdbcTemplate;
        this.clientRepository = clientRepository;
        this.whatsappInstanceRepository = whatsappInstanceRepository;
        this.messageRepository = messageRepository;
        this.evolutionSender = evolutionSender;
    }

    /** Notifica o cliente do processo (resolvido via legal_client → contact → conversa). */
    public void notifyStatus(UUID companyId, UUID legalClientId, String text) {
        if (text == null) {
            return;   // status 'ativo' não notifica.
        }
        try {
            UUID contactId = clientRepository.findById(companyId, legalClientId)
                .map(c -> c.contactId()).orElse(null);
            if (contactId == null) {
                return;   // cliente não vinculado ao WhatsApp — sem canal, skip silencioso.
            }
            // Conversa mais recente do contato (para resolver instância) + telefone do contato.
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select ct.phone_number, cv.id as conversation_id, cv.whatsapp_instance_id "
                    + "from contacts ct "
                    + "left join conversations cv on cv.contact_id = ct.id "
                    + "where ct.id = ? order by cv.last_message_at desc nulls last limit 1",
                contactId);
            if (rows.isEmpty()) {
                log.warn("legal: contato {} sem dados p/ notificar — pulado", contactId);
                return;
            }
            Map<String, Object> row = rows.get(0);
            String phone = (String) row.get("phone_number");
            UUID conversationId = (UUID) row.get("conversation_id");
            UUID instanceId = (UUID) row.get("whatsapp_instance_id");
            if (phone == null || phone.isBlank() || conversationId == null || instanceId == null) {
                log.warn("legal: canal incompleto p/ contato {} — notificação não enviada", contactId);
                return;
            }
            Optional<EvolutionCredentials> creds =
                whatsappInstanceRepository.findEvolutionCredentials(instanceId);
            if (creds.isEmpty()) {
                log.warn("legal: sem credenciais da instância {} — notificação não enviada", instanceId);
                return;
            }
            String keyId = evolutionSender.sendText(
                creds.get().instanceName(), creds.get().token(), phone, text);
            messageRepository.insertIfNew(companyId, conversationId,
                MessageDirection.OUTBOUND, MessageSender.HUMAN, text, keyId);
        } catch (RuntimeException e) {
            log.warn("legal: falha ao notificar status do processo (cliente {}) ({}) — processo segue",
                legalClientId, e.getMessage());
        }
    }
}
