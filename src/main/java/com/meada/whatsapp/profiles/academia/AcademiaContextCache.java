package com.meada.whatsapp.profiles.academia;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meada.whatsapp.profiles.academia.classes.AcademiaClass;
import com.meada.whatsapp.profiles.academia.classes.AcademiaClassRepository;
import com.meada.whatsapp.profiles.academia.memberships.AcademiaMembership;
import com.meada.whatsapp.profiles.academia.memberships.AcademiaMembershipRepository;
import com.meada.whatsapp.profiles.academia.memberships.MembershipClassEntry;
import com.meada.whatsapp.profiles.academia.plans.AcademiaPlan;
import com.meada.whatsapp.profiles.academia.plans.AcademiaPlanRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Cache do bloco de contexto dinâmico injetado no prompt do AcademiaBot (camada 7.7).
 *
 * <p>TTL 60s (aulas semanais mudam pouco; vagas mudam lento). Keyed por {@code (companyId,
 * contactId)}. Conteúdo: planos ativos, aulas ativas COM vagas restantes (capacity - matrículas
 * ativas), matrícula atual do contato (se houver — a IA NÃO oferece dupla), persona + instruções +
 * exemplo da tag. Os services chamam {@link #invalidate} ao mutar.
 */
@Component
public class AcademiaContextCache {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final String[] DAYS = {"Domingo", "Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado"};

    private final AcademiaPlanRepository planRepository;
    private final AcademiaClassRepository classRepository;
    private final AcademiaMembershipRepository membershipRepository;
    private final Cache<String, String> cache;

    public AcademiaContextCache(AcademiaPlanRepository planRepository,
                                AcademiaClassRepository classRepository,
                                AcademiaMembershipRepository membershipRepository) {
        this.planRepository = planRepository;
        this.classRepository = classRepository;
        this.membershipRepository = membershipRepository;
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60))
            .maximumSize(1000)
            .build();
    }

    public String contextSegment(UUID companyId, UUID contactId) {
        String key = companyId + ":" + (contactId == null ? "none" : contactId.toString());
        return cache.get(key, k -> buildSegment(companyId, contactId));
    }

    /** Invalida todas as entradas de uma empresa (mutação de plano/aula/matrícula). */
    public void invalidate(UUID companyId) {
        String prefix = companyId + ":";
        cache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
    }

    private String buildSegment(UUID companyId, UUID contactId) {
        List<AcademiaPlan> plans = planRepository.listByCompany(companyId, true);
        List<AcademiaClass> classes = classRepository.listByCompany(companyId, true, null);

        StringBuilder sb = new StringBuilder();

        // --- PLANOS ---
        if (plans.isEmpty()) {
            sb.append("PLANOS ATIVOS: (nenhum plano ativo no momento.)\n\n");
        } else {
            sb.append("PLANOS ATIVOS (use o plan_id EXATO na tag):\n");
            for (AcademiaPlan p : plans) {
                sb.append("- ").append(p.id()).append(" · ").append(p.name())
                    .append(": R$ ").append(formatBrl(p.monthlyCents())).append("/mês");
                if (p.description() != null && !p.description().isBlank()) {
                    sb.append(" — ").append(p.description().strip());
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        // --- AULAS (com vagas restantes) ---
        if (classes.isEmpty()) {
            sb.append("AULAS DA SEMANA: (nenhuma aula ativa no momento.)\n\n");
        } else {
            sb.append("AULAS DA SEMANA (use o class_id EXATO; só ofereça aulas com vaga):\n");
            for (AcademiaClass c : classes) {
                int remaining = Math.max(0, c.capacity() - classRepository.countActiveMembers(c.id()));
                sb.append("- ").append(c.id()).append(" · ").append(c.modality()).append(" \"")
                    .append(c.name()).append("\": ").append(dayLabel(c.dayOfWeek())).append(" às ")
                    .append(TIME_FMT.format(c.startTime())).append(", ").append(c.durationMinutes())
                    .append("min, ").append(remaining).append("/").append(c.capacity()).append(" vagas");
                if (c.instructor() != null && !c.instructor().isBlank()) {
                    sb.append(" com ").append(c.instructor());
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        // --- MATRÍCULA ATUAL DO CLIENTE ---
        if (contactId != null) {
            AcademiaMembership active = membershipRepository.findActiveByContact(companyId, contactId).orElse(null);
            if (active != null) {
                StringBuilder aulas = new StringBuilder();
                for (MembershipClassEntry e : active.classes()) {
                    if (aulas.length() > 0) aulas.append(", ");
                    aulas.append(e.className());
                }
                sb.append("MATRÍCULA ATUAL DO CLIENTE: plano ").append(active.planName())
                    .append(", ativa desde ").append(active.startDate())
                    .append(aulas.length() > 0 ? ", em " + aulas : "")
                    .append(". NÃO ofereça nova matrícula — ele já está matriculado.\n\n");
            }
        }

        // --- PERSONA + INSTRUÇÕES (decisão 9) ---
        sb.append("INSTRUÇÕES DE MATRÍCULA:\n")
            .append("Quando o cliente pedir matrícula, pergunte qual plano interessa e qual(is) aula(s) "
                + "ele quer fazer (mostre dia + horário + vagas restantes). Se ele perguntar sobre "
                + "TREINO específico, prescrição, dieta ou avaliação física, recuse com gentileza e "
                + "explique que isso é com o professor presencialmente (você não é educador físico). "
                + "Sem promessa de resultado corporal, sem julgamento. Confirme plano + aulas + nome "
                + "ANTES de emitir a tag. Sua ÚLTIMA mensagem deve TERMINAR com a tag (em uma linha "
                + "própria, sem markdown):\n")
            .append("<matricula>{\"plan_id\":\"UUID\",\"class_ids\":[\"UUID\",\"UUID\"],"
                + "\"student_name\":\"...\",\"notes\":\"...\"}</matricula>\n")
            .append("Use ids EXATOS das listas. Só ofereça aulas com vaga > 0. Só emita a tag na "
                + "confirmação final.\n\n");

        return sb.toString();
    }

    private static String dayLabel(int dow) {
        return dow >= 0 && dow <= 6 ? DAYS[dow] : String.valueOf(dow);
    }

    private static String formatBrl(int cents) {
        return String.format("%d,%02d", cents / 100, cents % 100);
    }
}
