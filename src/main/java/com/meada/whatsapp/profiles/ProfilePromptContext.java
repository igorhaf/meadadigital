package com.meada.whatsapp.profiles;

import com.meada.whatsapp.messaging.ConversationRepository;
import com.meada.whatsapp.profiles.legal.LegalCaseContextCache;
import com.meada.whatsapp.profiles.sushi.SushiMenuCache;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Segmento de persona do system prompt por perfil (camada 7.0). Cada perfil vertical injeta,
 * ANTES do prompt base do tenant (system-template.txt), uma instrução de persona que dá ao
 * "produto" a sua voz — sem substituir nada do prompt genérico (que continua intacto como
 * fallback).
 *
 * <p>A persona base (TOM) veio na SM-A (camada 7.0). A partir da SM-B (camada 7.1), o perfil
 * sushi também injeta o CARDÁPIO + config + instruções de pedido (via {@link SushiMenuCache},
 * cacheado 60s) após a persona — é o que torna a IA um atendente de restaurante real.
 */
@Component
public class ProfilePromptContext {

    private final SushiMenuCache sushiMenuCache;
    private final LegalCaseContextCache legalCaseContextCache;
    private final ConversationRepository conversationRepository;

    public ProfilePromptContext(SushiMenuCache sushiMenuCache,
                                LegalCaseContextCache legalCaseContextCache,
                                ConversationRepository conversationRepository) {
        this.sushiMenuCache = sushiMenuCache;
        this.legalCaseContextCache = legalCaseContextCache;
        this.conversationRepository = conversationRepository;
    }

    private static final String LEGAL =
        "Você é o assistente virtual de um escritório de advocacia. Tom formal e respeitoso, "
            + "terminologia jurídica correta. NUNCA dê opinião ou aconselhamento jurídico: para "
            + "qualquer dúvida substantiva sobre o caso, oriente o cliente a 'consultar o advogado "
            + "responsável'. Seu papel é confirmar o atendimento, agendar reuniões/consultas e "
            + "organizar informações — não interpretar o mérito jurídico.";

    private static final String DENTAL =
        "Você é o assistente virtual de uma clínica odontológica. Tom técnico mas acolhedor, com "
            + "empatia por quem tem medo de dentista. Esclareça procedimentos de forma didática e "
            + "tranquilizadora e sugira agendamento. NUNCA dê diagnóstico ou plano de tratamento — "
            + "isso é exclusivo do dentista; para dúvidas clínicas, encaminhe ao profissional.";

    private static final String SUSHI =
        "Você é atendente de um restaurante de sushi. Tom descontraído mas profissional. Conheça o "
            + "cardápio, sugira combinações e harmonizações, e confirme o pedido sempre com o valor "
            + "total e o endereço de entrega. Seja ágil e simpático no atendimento.";

    /**
     * Segmento de persona para o perfil, ou "" para generic / perfil desconhecido (fallback
     * seguro: o prompt base já cobre o genérico). Quando não-vazio, vem com um cabeçalho próprio
     * e uma quebra de linha ao final, pronto para concatenar ANTES do prompt base.
     */
    public String segmentFor(String profileId) {
        ProfileType profile = ProfileType.fromId(profileId).orElse(ProfileType.GENERIC);
        String body = switch (profile) {
            case LEGAL -> LEGAL;
            case DENTAL -> DENTAL;
            case SUSHI -> SUSHI;
            case GENERIC -> "";
        };
        if (body.isEmpty()) {
            return "";
        }
        return "# Persona (" + profile.productName() + ")\n" + body + "\n\n";
    }

    /** Compat (sem conversationId): usado por chamadas que não têm a conversa. */
    public String segmentFor(String profileId, UUID companyId) {
        return segmentFor(profileId, companyId, null);
    }

    /**
     * Segmento de perfil COM contexto do tenant (camada 7.1/7.2). Após a persona:
     * <ul>
     *   <li>sushi (7.1): anexa o cardápio + config + instruções de pedido (cache 60s). IGNORA
     *       conversationId.</li>
     *   <li>legal (7.2): resolve a conversa → contato → cliente jurídico → processos, e anexa o
     *       contexto de processos do cliente identificado (cache 60s por contato). Se o telefone
     *       não casa com nenhum cliente, o bloco orienta a IA a pedir identificação.</li>
     *   <li>demais: só a persona.</li>
     * </ul>
     */
    public String segmentFor(String profileId, UUID companyId, UUID conversationId) {
        String persona = segmentFor(profileId);
        if (companyId == null) {
            return persona;
        }
        if ("sushi".equals(profileId)) {
            return persona + sushiMenuCache.menuSegment(companyId);
        }
        if ("legal".equals(profileId)) {
            UUID contactId = conversationId == null ? null
                : conversationRepository.findContactIdByConversation(conversationId).orElse(null);
            return persona + legalCaseContextCache.contextSegment(companyId, contactId);
        }
        return persona;
    }
}
