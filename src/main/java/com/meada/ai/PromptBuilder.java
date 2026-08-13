package com.meada.ai;

import com.meada.messaging.AiSettings;
import com.meada.messaging.AiSettingsRepository;
import com.meada.messaging.BusinessHours;
import com.meada.messaging.BusinessHoursRepository;
import com.meada.messaging.ContactRepository;
import com.meada.messaging.Faq;
import com.meada.messaging.FaqRepository;
import com.meada.messaging.MessageRepository;
import com.meada.messaging.Service;
import com.meada.messaging.ServiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Monta o {@link Prompt} para a IA a partir dos dados do tenant (ai_settings,
 * services, faqs, business_hours) + histórico da conversa. Agnóstico ao provider.
 *
 * <p>Template em {@code resources/prompts/system-template.txt} com placeholders.
 * Seções OPCIONAIS (rules, restrictions, services, faqs, businessHours) carregam
 * o cabeçalho DENTRO do chunk: quando vazias viram "" e somem (sem cabeçalho órfão).
 * Seções com default não-vazio (tone, handoff) têm cabeçalho fixo no template.
 *
 * <p>Defaults neutros (ai_settings ausente ou campo null) são aplicados AQUI, não
 * no repositório (que retorna fielmente o banco).
 */
@Component
public class PromptBuilder {

    private static final Logger log = LoggerFactory.getLogger(PromptBuilder.class);

    private static final int HISTORY_LIMIT = 20;

    // Defaults neutros (produto). tone e handoff nunca ficam vazios.
    private static final String DEFAULT_TONE = "Cordial e profissional.";
    private static final String DEFAULT_HANDOFF =
        "Transfira quando o cliente pedir explicitamente para falar com um atendente humano, "
            + "ou quando você não tiver informação para responder com segurança.";

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private static final String[] WEEKDAY_PT = {
        "Domingo", "Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado"
    };

    private final String template;
    private final AiSettingsRepository aiSettingsRepository;
    private final ServiceRepository serviceRepository;
    private final FaqRepository faqRepository;
    private final BusinessHoursRepository businessHoursRepository;
    private final MessageRepository messageRepository;
    private final ContactRepository contactRepository;
    private final com.meada.knowledge.KnowledgeRetrievalService knowledgeRetrievalService;
    private final com.meada.profiles.CompanyProfileRepository companyProfileRepository;
    private final com.meada.profiles.ProfilePromptContext profilePromptContext;

    public PromptBuilder(AiSettingsRepository aiSettingsRepository,
                         ServiceRepository serviceRepository,
                         FaqRepository faqRepository,
                         BusinessHoursRepository businessHoursRepository,
                         MessageRepository messageRepository,
                         ContactRepository contactRepository,
                         com.meada.knowledge.KnowledgeRetrievalService knowledgeRetrievalService,
                         com.meada.profiles.CompanyProfileRepository companyProfileRepository,
                         com.meada.profiles.ProfilePromptContext profilePromptContext) {
        this.aiSettingsRepository = aiSettingsRepository;
        this.serviceRepository = serviceRepository;
        this.faqRepository = faqRepository;
        this.businessHoursRepository = businessHoursRepository;
        this.messageRepository = messageRepository;
        this.contactRepository = contactRepository;
        this.knowledgeRetrievalService = knowledgeRetrievalService;
        this.companyProfileRepository = companyProfileRepository;
        this.profilePromptContext = profilePromptContext;
        this.template = loadTemplate();
    }

    private static String loadTemplate() {
        try {
            return StreamUtils.copyToString(
                new ClassPathResource("prompts/system-template.txt").getInputStream(),
                StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load system-template.txt", e);
        }
    }

    public Prompt build(UUID companyId, UUID conversationId, String userMessage) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        Objects.requireNonNull(userMessage, "userMessage must not be null");

        Optional<AiSettings> settings = aiSettingsRepository.findByCompany(companyId);

        String tone = settings.map(AiSettings::tone).filter(s -> s != null && !s.isBlank())
            .orElse(DEFAULT_TONE);
        // Ajuste de tom (5.18 #58): se a IA já classificou o tom do contato, anexa uma linha
        // de direção de estilo ao tom configurado pelo tenant (não o substitui).
        tone = appendToneSteering(tone, conversationId);
        String handoff = settings.map(AiSettings::handoffTriggers).filter(s -> s != null && !s.isBlank())
            .orElse(DEFAULT_HANDOFF);
        String rulesValue = settings.map(AiSettings::systemRules).orElse(null);
        String restrictionsValue = settings.map(AiSettings::restrictions).orElse(null);

        // Persona por perfil (camada 7.0) + contexto do tenant (camada 7.1/7.2): prefixo de voz
        // do "produto" — para sushi o cardápio+config; para legal os processos do cliente
        // identificado pela conversa. Concatenado ANTES do prompt base. generic → "". NÃO
        // substitui o template — só antecede.
        String profileSegment = profilePromptContext.segmentFor(
            companyProfileRepository.findProfileId(companyId), companyId, conversationId);

        String systemPrompt = profileSegment + template
            .replace("{{tone}}", tone)
            .replace("{{handoff}}", handoff)
            .replace("{{rules}}", optionalSection("Regras adicionais", rulesValue))
            .replace("{{restrictions}}", optionalSection("Restrições", restrictionsValue))
            .replace("{{services}}", servicesSection(companyId))
            .replace("{{faqs}}", faqsSection(companyId))
            .replace("{{businessHours}}", businessHoursSection(companyId))
            .replace("{{knowledge}}", knowledgeSection(companyId, userMessage))
            .replace("{{contactMemory}}", contactMemorySection(conversationId));

        List<ConversationTurn> history =
            messageRepository.findRecentByConversation(conversationId, HISTORY_LIMIT);

        return new Prompt(systemPrompt, history, userMessage);
    }

    /** Chunk opcional de texto livre (rules/restrictions): cabeçalho + valor, ou "" se vazio. */
    private String optionalSection(String header, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return "\n# " + header + "\n" + value.strip() + "\n";
    }

    private String servicesSection(UUID companyId) {
        List<Service> services = serviceRepository.findActiveByCompany(companyId);
        if (services.isEmpty()) {
            return "";
        }
        String body = services.stream().map(this::formatService).collect(Collectors.joining("\n"));
        return "\n# Serviços oferecidos\n" + body + "\n";
    }

    private String formatService(Service s) {
        // "- <Nome>: <descrição>" ; com preço: "... — R$ XX,XX"
        StringBuilder line = new StringBuilder("- ").append(s.name());
        if (s.description() != null && !s.description().isBlank()) {
            line.append(": ").append(s.description().strip());
        }
        if (s.priceCents() != null) {
            line.append(" — R$ ").append(formatPrice(s.priceCents()));
        }
        return line.toString();
    }

    private static String formatPrice(int cents) {
        return String.format("%d,%02d", cents / 100, cents % 100);
    }

    private String faqsSection(UUID companyId) {
        List<Faq> faqs = faqRepository.findActiveByCompany(companyId);
        if (faqs.isEmpty()) {
            return "";
        }
        // "P: ... / R: ..." separados por linha em branco entre entradas. Sem markdown.
        String body = faqs.stream()
            .map(f -> "P: " + f.question().strip() + "\nR: " + f.answer().strip())
            .collect(Collectors.joining("\n\n"));
        return "\n# Perguntas frequentes\n" + body + "\n";
    }

    /**
     * Trechos de documentos do tenant relevantes para a mensagem do cliente (RAG, 5.13.d).
     * Faz retrieval semântico (embed da query → top-5 chunks acima do threshold). Vazio
     * quando não há documento relevante OU o sidecar de embeddings está indisponível —
     * neste caso a IA responde sem o contexto de documento (degradação graciosa), NÃO
     * quebra o fluxo. Mesma forma de seção opcional das demais (cabeçalho embutido).
     */
    private String knowledgeSection(UUID companyId, String userMessage) {
        List<com.meada.knowledge.RetrievedChunk> chunks;
        try {
            chunks = knowledgeRetrievalService.retrieve(companyId, userMessage);
        } catch (RuntimeException e) {
            // Sidecar fora / erro de retrieval: a IA segue sem documentos. NÃO propaga.
            log.warn("knowledge retrieval failed for company {} — prompt sem documentos ({})",
                companyId, e.getMessage());
            return "";
        }
        if (chunks.isEmpty()) {
            return "";
        }
        String body = chunks.stream()
            .map(c -> "[Documento \"" + c.documentTitle() + "\" — trecho " + (c.chunkIndex() + 1)
                + "]\n" + c.content().strip())
            .collect(Collectors.joining("\n\n"));
        return "\n# Conhecimento de documentos\n" + body + "\n";
    }

    /**
     * Memória de longo prazo do contato (5.18 #55): o jsonb contact_memory injetado no
     * contexto. Vazio ("") quando não há conversa/contato OU a memória ainda está vazia —
     * some sem cabeçalho órfão, como as demais seções opcionais. O jsonb é renderizado cru
     * (a IA lê JSON sem dificuldade); o conteúdo é território do modelo (ele mesmo o gravou
     * via memory_update).
     */
    private String contactMemorySection(UUID conversationId) {
        Optional<String> memory = contactRepository.findMemoryByConversation(conversationId);
        if (memory.isEmpty() || memory.get() == null || memory.get().isBlank()) {
            return "";
        }
        return "\n# Memória do contato\n" + memory.get().strip() + "\n";
    }

    /**
     * Ajuste de tom (5.18 #58): anexa uma linha de direção de estilo ao tom configurado quando
     * a IA já classificou o tom do contato (formal|informal|neutro|irritado). Não substitui o
     * tom do tenant — soma uma instrução de adaptação. Retorna o tom original quando não há
     * tom detectado (caso da 1ª interação).
     */
    private String appendToneSteering(String tone, UUID conversationId) {
        Optional<String> detected = contactRepository.findDetectedToneByConversation(conversationId);
        if (detected.isEmpty() || detected.get() == null || detected.get().isBlank()) {
            return tone;
        }
        return tone + "\nO contato tende a um tom " + detected.get().strip()
            + "; ajuste sua resposta a esse tom.";
    }

    private String businessHoursSection(UUID companyId) {
        List<BusinessHours> hours = businessHoursRepository.findByCompany(companyId);
        if (hours.isEmpty()) {
            return "";
        }
        // Agrupa janelas por weekday na ordem retornada (já vem ordenado por
        // weekday, opens_at). Um dia closed → "fechado"; senão janelas "HH:mm-HH:mm"
        // concatenadas com ", ".
        StringBuilder body = new StringBuilder();
        int currentWeekday = -1;
        for (BusinessHours bh : hours) {
            if (bh.weekday() != currentWeekday) {
                if (currentWeekday != -1) {
                    body.append('\n');
                }
                body.append(WEEKDAY_PT[bh.weekday()]).append(": ");
                currentWeekday = bh.weekday();
            } else {
                body.append(", ");   // janela adicional do mesmo dia (ex. pausa de almoço)
            }
            appendWindow(body, bh);
        }
        return "\n# Horários de atendimento\n" + body + "\n";
    }

    private void appendWindow(StringBuilder body, BusinessHours bh) {
        if (bh.closed()) {
            body.append("fechado");
        } else {
            body.append(format(bh.opensAt())).append('-').append(format(bh.closesAt()));
        }
    }

    private static String format(LocalTime t) {
        return t.format(HM);
    }
}
