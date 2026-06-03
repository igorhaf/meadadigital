package com.meada.whatsapp.ai;

import com.meada.whatsapp.messaging.AiSettings;
import com.meada.whatsapp.messaging.AiSettingsRepository;
import com.meada.whatsapp.messaging.BusinessHours;
import com.meada.whatsapp.messaging.BusinessHoursRepository;
import com.meada.whatsapp.messaging.Faq;
import com.meada.whatsapp.messaging.FaqRepository;
import com.meada.whatsapp.messaging.MessageRepository;
import com.meada.whatsapp.messaging.Service;
import com.meada.whatsapp.messaging.ServiceRepository;
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

    public PromptBuilder(AiSettingsRepository aiSettingsRepository,
                         ServiceRepository serviceRepository,
                         FaqRepository faqRepository,
                         BusinessHoursRepository businessHoursRepository,
                         MessageRepository messageRepository) {
        this.aiSettingsRepository = aiSettingsRepository;
        this.serviceRepository = serviceRepository;
        this.faqRepository = faqRepository;
        this.businessHoursRepository = businessHoursRepository;
        this.messageRepository = messageRepository;
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
        String handoff = settings.map(AiSettings::handoffTriggers).filter(s -> s != null && !s.isBlank())
            .orElse(DEFAULT_HANDOFF);
        String rulesValue = settings.map(AiSettings::systemRules).orElse(null);
        String restrictionsValue = settings.map(AiSettings::restrictions).orElse(null);

        String systemPrompt = template
            .replace("{{tone}}", tone)
            .replace("{{handoff}}", handoff)
            .replace("{{rules}}", optionalSection("Regras adicionais", rulesValue))
            .replace("{{restrictions}}", optionalSection("Restrições", restrictionsValue))
            .replace("{{services}}", servicesSection(companyId))
            .replace("{{faqs}}", faqsSection(companyId))
            .replace("{{businessHours}}", businessHoursSection(companyId));

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
