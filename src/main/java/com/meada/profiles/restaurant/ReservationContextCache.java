package com.meada.profiles.restaurant;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meada.profiles.restaurant.config.RestaurantReservationConfig;
import com.meada.profiles.restaurant.config.RestaurantReservationConfigRepository;
import com.meada.profiles.restaurant.reservations.Reservation;
import com.meada.profiles.restaurant.reservations.ReservationRepository;
import com.meada.profiles.restaurant.tables.RestaurantTable;
import com.meada.profiles.restaurant.tables.RestaurantTableRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Cache do bloco de mesas+reservas injetado no prompt do MesaBot (camada 7.3). Ler a agenda a cada
 * mensagem da IA seria caro; cacheamos o BLOCO DE TEXTO já formatado por company.
 *
 * <p>TTL de 15s — UM QUARTO do sushi/legal (60s) de propósito (decisão 6): a agenda de reservas
 * muda muito mais rápido que um cardápio ou uma lista de processos. Os services de mesas/reservas/
 * config chamam {@link #invalidate} a cada gravação — então a IA vê a mudança na hora, sem esperar
 * o TTL.
 *
 * <p>O conteúdo: mesas DISPONÍVEIS (a IA precisa do id pra emitir a tag &lt;reserva&gt;) + reservas
 * ATIVAS (pendente/confirmada) dos próximos 7 dias + a config (duração/horário) + as instruções de
 * reserva. Fuso America/Sao_Paulo (hardcoded, igual ao resto do perfil).
 */
@Component
public class ReservationContextCache {

    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final RestaurantTableRepository tableRepository;
    private final ReservationRepository reservationRepository;
    private final RestaurantReservationConfigRepository configRepository;
    private final Cache<UUID, String> cache;

    public ReservationContextCache(RestaurantTableRepository tableRepository,
                                   ReservationRepository reservationRepository,
                                   RestaurantReservationConfigRepository configRepository) {
        this.tableRepository = tableRepository;
        this.reservationRepository = reservationRepository;
        this.configRepository = configRepository;
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(15))
            .maximumSize(500)
            .build();
    }

    /** Bloco de mesas+reservas+config+instruções para o prompt, cacheado por company (TTL 15s). */
    public String contextSegment(UUID companyId) {
        return cache.get(companyId, this::buildSegment);
    }

    /**
     * Segmento com o bloco FRESCO do contato ao final (onda 1, backlog #1): as próximas reservas
     * DELE (id exato) + a instrução da tag &lt;confirmacao_reserva&gt; — fecha o loop do lembrete
     * "confirma? SIM/NÃO". O bloco do contato NÃO é cacheado (o cache é por company).
     */
    public String contextSegment(UUID companyId, UUID contactId) {
        String base = contextSegment(companyId);
        if (contactId == null) {
            return base;
        }
        List<Reservation> upcoming = reservationRepository.listUpcomingByContact(companyId, contactId, 5);
        if (upcoming.isEmpty()) {
            return base;
        }
        StringBuilder sb = new StringBuilder(base);
        sb.append("RESERVAS FUTURAS DESTE CLIENTE (use o id EXATO na tag de confirmação):\n");
        for (Reservation r : upcoming) {
            ZonedDateTime s = r.startAt().atZone(TENANT_ZONE);
            sb.append("- ").append(r.id()).append(" · ").append(r.tableLabel())
                .append(", ").append(DATE_FMT.format(s)).append(" às ").append(TIME_FMT.format(s))
                .append(", ").append(r.numPeople()).append(" pessoa(s) · status ")
                .append(r.status()).append("\n");
        }
        sb.append("Quando o cliente CONFIRMAR uma reserva futura (ex.: responder SIM ao lembrete) ou "
            + "pedir para CANCELAR, sua ÚLTIMA mensagem deve TERMINAR com a tag (linha própria):\n"
            + "<confirmacao_reserva>{\"reservation_id\":\"UUID\",\"decisao\":\"confirmada|cancelada\"}"
            + "</confirmacao_reserva>\n"
            + "Você só REFLETE a decisão do cliente — NUNCA confirme ou cancele sem ele pedir.\n\n");
        return sb.toString();
    }

    /** Invalida o cache de uma empresa (chamado pelos services ao mutar mesa/reserva/config). */
    public void invalidate(UUID companyId) {
        cache.invalidate(companyId);
    }

    private String buildSegment(UUID companyId) {
        List<RestaurantTable> tables = tableRepository.listByCompany(companyId, true);
        RestaurantReservationConfig config = configRepository.findByCompany(companyId);

        // Janela das próximas 7 dias (a partir de agora) para listar reservas ativas.
        Instant now = Instant.now();
        Instant in7Days = now.plus(Duration.ofDays(7));
        List<Reservation> upcoming = reservationRepository.listActiveUpcoming(companyId, now, in7Days);

        StringBuilder sb = new StringBuilder();

        if (tables.isEmpty()) {
            sb.append("MESAS DISPONÍVEIS: (nenhuma mesa cadastrada/disponível no momento — informe o "
                + "cliente que não há mesas disponíveis e ofereça anotar o contato.)\n\n");
        } else {
            sb.append("MESAS DISPONÍVEIS (use o id EXATO na tag de reserva):\n");
            for (RestaurantTable t : tables) {
                sb.append("- ").append(t.id()).append(" · ").append(t.label())
                    .append(" (cap. ").append(t.capacity()).append(")\n");
            }
            sb.append("\n");
        }

        if (upcoming.isEmpty()) {
            sb.append("RESERVAS JÁ MARCADAS (próximos 7 dias): nenhuma — toda a agenda está livre.\n\n");
        } else {
            sb.append("RESERVAS JÁ MARCADAS (próximos 7 dias — esses horários estão OCUPADOS na mesa):\n");
            for (Reservation r : upcoming) {
                ZonedDateTime s = r.startAt().atZone(TENANT_ZONE);
                ZonedDateTime e = r.endAt().atZone(TENANT_ZONE);
                sb.append("- ").append(r.tableLabel()).append(", ")
                    .append(DATE_FMT.format(s)).append(" ")
                    .append(TIME_FMT.format(s)).append("-").append(TIME_FMT.format(e)).append("\n");
            }
            sb.append("\n");
        }

        sb.append("CONFIG: cada reserva dura ").append(config.durationMinutes())
            .append(" minutos. Horário de funcionamento: ")
            .append(TIME_FMT.format(config.opensAt())).append(" às ")
            .append(TIME_FMT.format(config.closesAt())).append(".\n\n");

        sb.append("INSTRUÇÕES DE RESERVA:\n")
            .append("Verifique a disponibilidade no contexto acima ANTES de confirmar. NÃO invente "
                + "mesa que não está na lista. Se o horário pedido estiver ocupado naquela mesa, "
                + "ofereça uma alternativa próxima (30 min antes ou depois) ou outra mesa livre. "
                + "Quando o cliente CONFIRMAR a reserva (dia, hora, mesa e nº de pessoas definidos), "
                + "sua ÚLTIMA mensagem deve TERMINAR com a tag (em uma linha própria, sem markdown):\n")
            .append("<reserva>{\"table_id\":\"UUID_EXATO_DA_MESA\",\"date\":\"YYYY-MM-DD\","
                + "\"start_time\":\"HH:MM\",\"num_people\":N}</reserva>\n")
            .append("ANTES da tag, escreva a confirmação humana normal (\"Perfeito! Reservei a Mesa 4 "
                + "pra você, dia 20/06 às 20h, pra 4 pessoas. Te esperamos!\"). NÃO emita a tag "
                + "enquanto faltar algum dado — só na confirmação final.\n\n");

        return sb.toString();
    }
}
