package com.meada.profiles.barbearia.appointments;

import com.meada.profiles.barbearia.BarberAppointmentStatus;
import com.meada.profiles.barbearia.BarberContextCache;
import com.meada.profiles.barbearia.barbers.BarberBarber;
import com.meada.profiles.barbearia.barbers.BarberBarberRepository;
import com.meada.profiles.barbearia.config.BarberConfig;
import com.meada.profiles.barbearia.config.BarberConfigRepository;
import com.meada.profiles.barbearia.coupons.BarberCoupon;
import com.meada.profiles.barbearia.coupons.BarberCouponRepository;
import com.meada.profiles.barbearia.loyalty.BarberLoyaltyConfig;
import com.meada.profiles.barbearia.loyalty.BarberLoyaltyConfigRepository;
import com.meada.profiles.barbearia.services.BarberService;
import com.meada.profiles.barbearia.services.BarberServiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras dos agendamentos de barbearia (camada 8.1). Clone de SalonAppointmentService — conflito POR
 * BARBEIRO. {@link #create} valida barbeiro (existe + ativo), serviço (existe + ativo; dá a duração e
 * o preço para snapshot), e a janela de funcionamento. Delega ao repositório, que re-verifica o
 * conflito DENTRO da transação. Status inicial = agendado.
 *
 * <p>{@link #updateStatus} valida a transição e dispara a notificação (confirmado/cancelado) com o
 * nome do barbeiro. Fuso HARDCODED America/Sao_Paulo (pendência, igual salon).
 */
@Service
public class BarberAppointmentService {

    static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private static final Logger log = LoggerFactory.getLogger(BarberAppointmentService.class);

    private final BarberAppointmentRepository appointmentRepository;
    private final BarberBarberRepository barberRepository;
    private final BarberServiceRepository serviceRepository;
    private final BarberConfigRepository configRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final BarberCouponRepository couponRepository;
    private final BarberLoyaltyConfigRepository loyaltyRepository;
    private final BarberAppointmentNotifier notifier;
    private final BarberContextCache contextCache;

    public BarberAppointmentService(BarberAppointmentRepository appointmentRepository,
                                    BarberBarberRepository barberRepository,
                                    BarberServiceRepository serviceRepository,
                                    BarberConfigRepository configRepository,
                                    BarberCouponRepository couponRepository,
                                    BarberLoyaltyConfigRepository loyaltyRepository,
                                    BarberAppointmentNotifier notifier,
                                    BarberContextCache contextCache,
                                    org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.appointmentRepository = appointmentRepository;
        this.barberRepository = barberRepository;
        this.serviceRepository = serviceRepository;
        this.configRepository = configRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.couponRepository = couponRepository;
        this.loyaltyRepository = loyaltyRepository;
        this.notifier = notifier;
        this.contextCache = contextCache;
    }

    public static class AppointmentNotFoundException extends RuntimeException {}
    public static class BarberNotFoundException extends RuntimeException {}
    public static class ServiceNotFoundException extends RuntimeException {}
    public static class InactiveBarberException extends RuntimeException {}
    public static class InactiveServiceException extends RuntimeException {}
    public static class OutsideHoursException extends RuntimeException {}
    public static class InvalidStatusException extends RuntimeException {}
    public static class InvalidStatusTransitionException extends RuntimeException {}

    /** Conflito de slot (→ 409 conflict_slot). Carrega o conflito p/ o controller expor detalhes. */
    public static class ConflictException extends RuntimeException {
        private final transient BarberAppointmentConflict conflict;

        public ConflictException(BarberAppointmentConflict conflict) {
            this.conflict = conflict;
        }

        public BarberAppointmentConflict conflict() {
            return conflict;
        }
    }

    /**
     * Cria um agendamento (status inicial agendado). Valida barbeiro/serviço (existem + ativos),
     * janela de funcionamento (no fuso do tenant), e delega ao repo — que re-verifica conflito por
     * barbeiro na transação. Snapshots de nome/preço/duração vêm de barber+service.
     *
     * <p>Onda 1 do backlog: FIDELIDADE #3 (a cada N cortes realizados do contato, este sai GRÁTIS —
     * desconto = preço, loyalty_applied=true) e CUPOM #12 ({@code couponCode} vem da tag da IA; o
     * backend valida active/validade/mínimo/max_uses e aplica com clamp ao preço; INVÁLIDO NÃO
     * ABORTA — o agendamento sai sem desconto, espelho adega). Fidelidade tem precedência (grátis
     * não acumula cupom). uses do cupom incrementa na MESMA transação.
     */
    @Transactional
    public BarberAppointment create(UUID companyId, UUID barberId, UUID serviceId, UUID contactId,
                                    UUID conversationId, Instant startAt, String guestName,
                                    String guestPhone, String notes, String couponCode) {
        BarberBarber barber = barberRepository.findById(companyId, barberId)
            .orElseThrow(BarberNotFoundException::new);
        if (!barber.active()) {
            throw new InactiveBarberException();
        }
        BarberService service = serviceRepository.findById(companyId, serviceId)
            .orElseThrow(ServiceNotFoundException::new);
        if (!service.active()) {
            throw new InactiveServiceException();
        }
        BarberConfig config = configRepository.findByCompany(companyId);
        requireInsideHours(startAt, service.durationMinutes(), config);

        Integer price = service.priceCents();
        int discount = 0;
        UUID couponId = null;
        String couponSnapshot = null;
        boolean loyaltyApplied = false;

        // FIDELIDADE #3: conta os REALIZADOS do contato ANTES de inserir (espelho sushi/adega).
        if (price != null && price > 0 && contactId != null) {
            BarberLoyaltyConfig loyalty = loyaltyRepository.findByCompany(companyId);
            if (loyalty.enabled()) {
                int realized = appointmentRepository.countRealizedByContact(companyId, contactId);
                if (realized > 0 && realized % loyalty.thresholdCuts() == 0) {
                    discount = price;
                    loyaltyApplied = true;
                }
            }
        }

        // CUPOM #12: só quando não saiu grátis pela fidelidade; inválido é descartado em silêncio.
        if (!loyaltyApplied && price != null && couponCode != null && !couponCode.isBlank()) {
            Optional<BarberCoupon> found = couponRepository.findByCode(companyId, couponCode);
            BarberCoupon c = found.orElse(null);
            boolean valid = c != null && c.active()
                && (c.validUntil() == null || !c.validUntil().isBefore(LocalDate.now(TENANT_ZONE)))
                && (c.maxUses() == null || c.uses() < c.maxUses())
                && price >= c.minOrderCents();
            if (valid) {
                long raw = "percent".equals(c.kind()) ? (long) price * c.value() / 100L : c.value();
                discount = (int) Math.min(raw, price);
                couponId = c.id();
                couponSnapshot = c.code();
            } else {
                log.info("barbearia: cupom '{}' inválido p/ company {} — agendamento segue sem desconto",
                    couponCode, companyId);
            }
        }

        BarberAppointment created;
        try {
            created = appointmentRepository.insertAppointment(companyId, barberId, barber.name(),
                serviceId, service.name(), price, service.durationMinutes(),
                conversationId, contactId, guestName, guestPhone, startAt, notes,
                discount, couponId, couponSnapshot, loyaltyApplied);
        } catch (BarberAppointmentRepository.SlotConflictException e) {
            throw new ConflictException(e.conflict());
        }
        if (couponId != null) {
            couponRepository.incrementUses(companyId, couponId);
        }
        contextCache.invalidate(companyId);
        return created;
    }

    public List<BarberAppointment> list(UUID companyId, String status, Instant dateFrom, Instant dateTo,
                                        UUID barberId, UUID contactId, int limit, int offset) {
        return appointmentRepository.listByCompany(companyId, status, dateFrom, dateTo, barberId, contactId, limit, offset);
    }

    public long count(UUID companyId, String status, Instant dateFrom, Instant dateTo,
                      UUID barberId, UUID contactId) {
        return appointmentRepository.countByCompany(companyId, status, dateFrom, dateTo, barberId, contactId);
    }

    public Optional<BarberAppointment> get(UUID companyId, UUID id) {
        return appointmentRepository.findById(companyId, id);
    }

    @Transactional
    public BarberAppointment updateStatus(UUID companyId, UUID id, String newStatusId) {
        BarberAppointmentStatus newStatus = BarberAppointmentStatus.fromId(newStatusId)
            .orElseThrow(InvalidStatusException::new);

        BarberAppointment current = appointmentRepository.findById(companyId, id)
            .orElseThrow(AppointmentNotFoundException::new);
        BarberAppointmentStatus from = BarberAppointmentStatus.fromId(current.status())
            .orElseThrow(InvalidStatusException::new);

        if (!from.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException();
        }

        appointmentRepository.updateStatus(companyId, id, newStatus.id());

        ZonedDateTime z = current.startAt().atZone(TENANT_ZONE);
        String text = newStatus.notificationText(
            DATE_FMT.format(z), TIME_FMT.format(z), current.barberName());
        notifier.notifyStatus(companyId, current.conversationId(), text);

        // Onda 2 (backlog #9): corte REALIZADO pede avaliação — com COOLDOWN por contato
        // (barber_review_log) pra não spammar o cliente semanal. Toggle OFF por default.
        if (newStatus == BarberAppointmentStatus.REALIZADO && current.contactId() != null) {
            var config = configRepository.findByCompany(companyId);
            if (config.postReviewEnabled()) {
                Long recent = jdbcTemplate.queryForObject(
                    "select count(*) from barber_review_log where company_id = ? and contact_id = ? "
                        + "and sent_at > now() - make_interval(days => ?)",
                    Long.class, companyId, current.contactId(), config.reviewCooldownDays());
                if (recent != null && recent == 0) {
                    StringBuilder pos = new StringBuilder("Valeu pela visita! 💈 ");
                    if (config.reviewLink() != null) {
                        pos.append("Se curtiu o corte, deixa sua avaliação — ajuda demais: ")
                            .append(config.reviewLink());
                    } else {
                        pos.append("Se curtiu o corte, conta pra gente por aqui — e se algo não "
                            + "ficou bom, queremos saber também.");
                    }
                    notifier.notifyStatus(companyId, current.conversationId(), pos.toString());
                    jdbcTemplate.update(
                        "insert into barber_review_log (company_id, contact_id) values (?, ?)",
                        companyId, current.contactId());
                }
            }
        }

        contextCache.invalidate(companyId);
        return appointmentRepository.findById(companyId, id).orElseThrow(AppointmentNotFoundException::new);
    }

    /** Valida que o agendamento inteiro (início → início+duração) cabe na janela, no fuso do tenant. */
    private void requireInsideHours(Instant startAt, int durationMinutes, BarberConfig config) {
        ZonedDateTime start = startAt.atZone(TENANT_ZONE);
        LocalTime startTime = start.toLocalTime();
        LocalTime endTime = start.plusMinutes(durationMinutes).toLocalTime();
        boolean startsOk = !startTime.isBefore(config.opensAt());
        boolean endsOk = !endTime.isAfter(config.closesAt()) && !endTime.isBefore(startTime);
        if (!startsOk || !endsOk) {
            throw new OutsideHoursException();
        }
    }
}
