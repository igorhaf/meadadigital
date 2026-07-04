package com.meada.profiles.fotografia;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meada.profiles.fotografia.appointments.FotografiaAppointmentRepository;
import com.meada.profiles.fotografia.appointments.FotografiaSessionAppointment;
import com.meada.profiles.fotografia.config.FotografiaConfig;
import com.meada.profiles.fotografia.config.FotografiaConfigRepository;
import com.meada.profiles.fotografia.packages.FotografiaPackage;
import com.meada.profiles.fotografia.packages.FotografiaPackageRepository;
import com.meada.profiles.fotografia.professionals.FotografiaProfessional;
import com.meada.profiles.fotografia.professionals.FotografiaProfessionalRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cache do bloco de contexto dinâmico injetado no prompt do FotografiaBot (camada 8.16). TTL 20s.
 * Keyed por {@code (companyId, contactId)}. Conteúdo:
 * <ul>
 *   <li>fotógrafos ativos (id + nome) — pra IA referenciar professional_id na sessão;
 *   <li>pacotes ativos (id + nome + preço + duração + delivery_days) — pra IA referenciar package_id;
 *   <li>sessões do contato (id + pacote + data + status; INDICA se o material já tem link entregável,
 *       sem despejar o link) — pra IA entregar o material;
 *   <li>slots livres por profissional (próximos 14 dias).
 * </ul>
 * + instruções/persona e as 2 tags ({@code <sessao_foto>} e {@code <entrega_material>}).
 *
 * <p>Clone do DermatologiaContextCache (TTL 30s→20s; sem sub-entidade de paciente — o cliente é o
 * contact). NÃO injeta o delivery_link no contexto — só a indicação de que a sessão TEM material
 * entregável; o link só é lido no momento da entrega, pelo EntregaMaterialHandler.
 */
@Component
public class FotografiaContextCache {

    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final int CONTEXT_DAYS = 14;
    private static final int SLOT_GRANULARITY_MIN = 30;
    private static final int MAX_SLOTS_PER_PROF_DAY = 6;
    private static final int MAX_SESSIONS = 10;

    private final FotografiaProfessionalRepository professionalRepository;
    private final FotografiaPackageRepository packageRepository;
    private final FotografiaAppointmentRepository appointmentRepository;
    private final FotografiaConfigRepository configRepository;
    private final Cache<String, String> cache;

    public FotografiaContextCache(FotografiaProfessionalRepository professionalRepository,
                                  FotografiaPackageRepository packageRepository,
                                  FotografiaAppointmentRepository appointmentRepository,
                                  FotografiaConfigRepository configRepository) {
        this.professionalRepository = professionalRepository;
        this.packageRepository = packageRepository;
        this.appointmentRepository = appointmentRepository;
        this.configRepository = configRepository;
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(20))
            .maximumSize(1000)
            .build();
    }

    public String contextSegment(UUID companyId, UUID contactId) {
        String key = companyId + ":" + (contactId == null ? "none" : contactId.toString());
        return cache.get(key, k -> buildSegment(companyId, contactId));
    }

    /** Invalida todas as entradas de uma empresa (mutação de prof/pacote/sessão/config). */
    public void invalidate(UUID companyId) {
        String prefix = companyId + ":";
        cache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
    }

    private String buildSegment(UUID companyId, UUID contactId) {
        FotografiaConfig config = configRepository.findByCompany(companyId);
        List<FotografiaProfessional> pros = professionalRepository.listByCompany(companyId, true);
        List<FotografiaPackage> packages = packageRepository.listByCompany(companyId, true);

        StringBuilder sb = new StringBuilder();

        // --- FOTÓGRAFOS ---
        if (pros.isEmpty()) {
            sb.append("FOTÓGRAFOS: (nenhum ativo no momento.)\n\n");
        } else {
            sb.append("FOTÓGRAFOS (use o professional_id EXATO):\n");
            for (FotografiaProfessional p : pros) {
                sb.append("- ").append(p.id()).append(" · ").append(p.name());
                if (p.specialty() != null && !p.specialty().isBlank()) {
                    sb.append(" (").append(p.specialty()).append(")");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        // --- PACOTES ---
        if (packages.isEmpty()) {
            sb.append("PACOTES: (nenhum ativo no momento.)\n\n");
        } else {
            sb.append("PACOTES (use o package_id EXATO; a duração e o preço vêm do pacote — NUNCA invente preço):\n");
            for (FotografiaPackage pk : packages) {
                sb.append("- ").append(pk.id()).append(" · ").append(pk.name());
                if (pk.category() != null && !pk.category().isBlank()) {
                    sb.append(" [").append(pk.category()).append("]");
                }
                sb.append(" — R$ ").append(formatCents(pk.priceCents()))
                    .append(" · ").append(pk.durationMinutes()).append(" min")
                    .append(" · entrega em ").append(pk.deliveryDays()).append(" dia(s)")
                    .append("\n");
            }
            sb.append("\n");
        }

        // --- SESSÕES DO CONTATO ---
        if (contactId != null) {
            List<FotografiaSessionAppointment> sessions = appointmentRepository.listByContact(companyId, contactId, MAX_SESSIONS);
            if (!sessions.isEmpty()) {
                sb.append("SESSÕES DESTE CONTATO (use o session_id EXATO p/ entregar o material):\n");
                for (FotografiaSessionAppointment s : sessions) {
                    ZonedDateTime z = s.startAt().atZone(TENANT_ZONE);
                    sb.append("- ").append(s.id()).append(" · ").append(s.packageName())
                        .append(" em ").append(DATE_FMT.format(z)).append(" às ").append(TIME_FMT.format(z))
                        .append(" (").append(s.status()).append(")");
                    boolean hasLink = s.deliveryLink() != null && !s.deliveryLink().isBlank();
                    sb.append(hasLink ? " — material PRONTO para entrega" : " — material ainda não disponível");
                    sb.append("\n");
                }
                sb.append("\n");
            } else {
                sb.append("CONTATO SEM SESSÕES registradas.\n\n");
            }
        } else {
            sb.append("CONTATO NÃO IDENTIFICADO pelo telefone.\n\n");
        }

        // --- SLOTS LIVRES POR PROFISSIONAL ---
        sb.append(buildSlotsSegment(companyId, config, pros));

        // --- INSTRUÇÕES + PERSONA + TAGS ---
        sb.append("INSTRUÇÕES E LIMITES (LEIA COM ATENÇÃO):\n")
            .append("Você AGENDA sessões fotográficas e ENTREGA o material que o estúdio já disponibilizou — nada além "
                + "disso. NUNCA invente preço, pacote, prazo ou disponibilidade fora do que está listado. NUNCA prometa "
                + "estilo/resultado de foto. O preço e a duração vêm SEMPRE do pacote do catálogo.\n")
            .append("Para AGENDAR uma sessão, termine com a tag (linha própria, sem markdown):\n")
            .append("<sessao_foto>{\"professional_id\":\"UUID\",\"package_id\":\"UUID\","
                + "\"date\":\"YYYY-MM-DD\",\"start_time\":\"HH:MM\",\"notes\":\"...\"}</sessao_foto>\n")
            .append("Quando o cliente PEDIR o material de uma sessão dele que JÁ esteja PRONTO, termine com:\n")
            .append("<entrega_material>{\"session_id\":\"UUID\"}</entrega_material>\n")
            .append("O link do material é enviado EXATAMENTE como o estúdio gravou — você não o reescreve nem comenta. "
                + "Se a sessão ainda não tem material disponível, diga que o material ainda está em produção. Use ids EXATOS.\n")
            .append("Quando a cliente RESPONDER a um lembrete de sessão confirmando presença ou pedindo "
                + "pra cancelar, termine com a tag (linha própria, sem markdown):\n")
            .append("<confirmacao_foto>{\"session_id\":\"UUID_DA_SESSAO\",\"decisao\":\"confirmada\"}"
                + "</confirmacao_foto>\n")
            .append("\"decisao\" é \"confirmada\" ou \"cancelada\" — use o session_id EXATO da lista acima. "
                + "Reagendar = cancelar + agendar de novo (confira o horário livre antes).\n\n");

        // Onda 1 (backlog #5): pacotes sugeríveis — oferta consultiva, nome+preço do catálogo.
        List<FotografiaPackage> suggestibles = packages.stream().filter(FotografiaPackage::suggestible).toList();
        if (!suggestibles.isEmpty()) {
            sb.append("UPSELL CONSULTIVO: ao fechar um agendamento, você PODE sugerir UM destes "
                + "pacotes/extras quando fizer sentido pro momento da cliente (sem insistir, sem "
                + "desconto, preço SEMPRE do catálogo):\n");
            for (FotografiaPackage pk : suggestibles) {
                sb.append("- ").append(pk.name()).append(" (R$ ")
                    .append(String.format("%.2f", pk.priceCents() / 100.0).replace('.', ',')).append(")\n");
            }
            sb.append("\n");
        }

        // Onda 1 (backlog #4): política de cancelamento — a IA COMUNICA, nunca decide retenção.
        if (config.cancellationPolicyHours() != null) {
            sb.append("POLÍTICA DE CANCELAMENTO: cancelamento sem custo até ")
                .append(config.cancellationPolicyHours())
                .append(" horas antes da sessão. COMUNIQUE essa política ao fechar um agendamento e "
                    + "quando a cliente pedir cancelamento em cima da hora — mas quem decide qualquer "
                    + "cobrança/exceção é a equipe, nunca você.\n\n");
        }

        return sb.toString();
    }

    private String buildSlotsSegment(UUID companyId, FotografiaConfig config, List<FotografiaProfessional> pros) {
        StringBuilder sb = new StringBuilder("HORÁRIOS LIVRES (próximos ").append(CONTEXT_DAYS).append(" dias, por profissional):\n");
        if (pros.isEmpty()) {
            sb.append("(sem profissionais ativos.)\n\n");
            return sb.toString();
        }
        Instant now = Instant.now();
        Instant until = now.plus(Duration.ofDays(CONTEXT_DAYS));
        ZonedDateTime startDay = now.atZone(TENANT_ZONE).toLocalDate().atStartOfDay(TENANT_ZONE);

        for (FotografiaProfessional p : pros) {
            List<FotografiaSessionAppointment> active = appointmentRepository.listActiveByProfessional(companyId, p.id(), now, until);
            List<String> dayChunks = new ArrayList<>();
            for (int d = 0; d < CONTEXT_DAYS; d++) {
                LocalDate day = startDay.plusDays(d).toLocalDate();
                List<String> free = new ArrayList<>();
                LocalTime t = config.opensAt();
                while (t.plusMinutes(SLOT_GRANULARITY_MIN).compareTo(config.closesAt()) <= 0
                        && free.size() < MAX_SLOTS_PER_PROF_DAY) {
                    ZonedDateTime slotStart = day.atTime(t).atZone(TENANT_ZONE);
                    ZonedDateTime slotEnd = slotStart.plusMinutes(SLOT_GRANULARITY_MIN);
                    boolean inFuture = slotStart.toInstant().isAfter(now);
                    boolean occupied = active.stream().anyMatch(a ->
                        !(a.endAt().compareTo(slotStart.toInstant()) <= 0 || a.startAt().compareTo(slotEnd.toInstant()) >= 0));
                    if (inFuture && !occupied) {
                        free.add(TIME_FMT.format(t));
                    }
                    t = t.plusMinutes(SLOT_GRANULARITY_MIN);
                }
                if (!free.isEmpty()) {
                    dayChunks.add(DATE_FMT.format(day.atStartOfDay(TENANT_ZONE)) + " " + String.join(", ", free));
                }
            }
            if (!dayChunks.isEmpty()) {
                sb.append("- ").append(p.name()).append(": ").append(String.join(" | ", dayChunks)).append("\n");
            }
        }
        sb.append("\n");
        return sb.toString();
    }

    private static String formatCents(int cents) {
        return String.format("%.2f", cents / 100.0);
    }
}
