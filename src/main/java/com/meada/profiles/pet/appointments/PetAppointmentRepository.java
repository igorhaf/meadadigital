package com.meada.profiles.pet.appointments;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code pet_appointments} (camada 7.8). Opera via service_role. Conflito por PROFISSIONAL
 * (half-open, espelho salon): {@link #insertAppointment} re-verifica na transação (defesa race),
 * materializa end_at e os snapshots.
 */
@Repository
public class PetAppointmentRepository {

    private static final RowMapper<PetAppointment> MAPPER = (rs, rn) -> new PetAppointment(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("professional_id"),
        rs.getString("professional_name"),
        (UUID) rs.getObject("service_id"),
        rs.getString("service_name"),
        rs.getString("service_category"),
        (UUID) rs.getObject("animal_id"),
        rs.getString("animal_name"),
        rs.getString("animal_species"),
        (UUID) rs.getObject("contact_id"),
        (UUID) rs.getObject("conversation_id"),
        rs.getString("tutor_name"),
        rs.getString("tutor_phone"),
        (Integer) rs.getObject("price_cents"),
        rs.getInt("duration_minutes"),
        rs.getTimestamp("start_at").toInstant(),
        rs.getTimestamp("end_at").toInstant(),
        rs.getString("status"),
        rs.getString("notes"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("status_updated_at").toInstant());

    private static final String COLS =
        "id, professional_id, professional_name, service_id, service_name, service_category, animal_id, "
            + "animal_name, animal_species, contact_id, conversation_id, tutor_name, tutor_phone, price_cents, "
            + "duration_minutes, start_at, end_at, status, notes, created_at, status_updated_at";

    private final JdbcTemplate jdbcTemplate;

    public PetAppointmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<PetAppointment> listByCompany(UUID companyId, String status, Instant dateFrom, Instant dateTo,
                                              UUID professionalId, UUID animalId, UUID contactId, int limit, int offset) {
        StringBuilder sql = new StringBuilder("select " + COLS + " from pet_appointments where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) { sql.append(" and status = ?"); args.add(status); }
        if (dateFrom != null) { sql.append(" and start_at >= ?"); args.add(Timestamp.from(dateFrom)); }
        if (dateTo != null) { sql.append(" and start_at < ?"); args.add(Timestamp.from(dateTo)); }
        if (professionalId != null) { sql.append(" and professional_id = ?"); args.add(professionalId); }
        if (animalId != null) { sql.append(" and animal_id = ?"); args.add(animalId); }
        if (contactId != null) { sql.append(" and contact_id = ?"); args.add(contactId); }
        sql.append(" order by start_at asc limit ? offset ?");
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public long countByCompany(UUID companyId, String status, Instant dateFrom, Instant dateTo,
                               UUID professionalId, UUID animalId, UUID contactId) {
        StringBuilder sql = new StringBuilder("select count(*) from pet_appointments where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) { sql.append(" and status = ?"); args.add(status); }
        if (dateFrom != null) { sql.append(" and start_at >= ?"); args.add(Timestamp.from(dateFrom)); }
        if (dateTo != null) { sql.append(" and start_at < ?"); args.add(Timestamp.from(dateTo)); }
        if (professionalId != null) { sql.append(" and professional_id = ?"); args.add(professionalId); }
        if (animalId != null) { sql.append(" and animal_id = ?"); args.add(animalId); }
        if (contactId != null) { sql.append(" and contact_id = ?"); args.add(contactId); }
        Long n = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0L : n;
    }

    public Optional<PetAppointment> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query("select " + COLS + " from pet_appointments where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    public List<PetAppointment> listByAnimal(UUID companyId, UUID animalId, int limit) {
        return jdbcTemplate.query("select " + COLS + " from pet_appointments where company_id = ? and animal_id = ? "
                + "order by start_at desc limit ?", MAPPER, companyId, animalId, limit);
    }

    public List<PetAppointment> listActiveByProfessional(UUID companyId, UUID professionalId, Instant from, Instant to) {
        return jdbcTemplate.query("select " + COLS + " from pet_appointments where company_id = ? and professional_id = ? "
                + "and status in ('agendado','confirmado') and start_at >= ? and start_at < ? order by start_at asc",
            MAPPER, companyId, professionalId, Timestamp.from(from), Timestamp.from(to));
    }

    /**
     * Conflito de slot por PROFISSIONAL (decisão 9): agendamento ATIVO (agendado/confirmado) do MESMO
     * profissional cuja janela sobrepõe [newStart, newEnd). Half-open: NOT (existing.end <= newStart
     * OR existing.start >= newEnd). Cálculo em SQL (defesa contra race na transação).
     */
    public Optional<PetAppointmentConflict> findConflict(UUID professionalId, Instant newStart, Instant newEnd) {
        return jdbcTemplate.query(
                "select id, animal_name, tutor_name, start_at, end_at from pet_appointments "
                    + "where professional_id = ? and status in ('agendado','confirmado') "
                    + "and not (end_at <= ? or start_at >= ?) order by start_at asc limit 1",
                (rs, rn) -> new PetAppointmentConflict(
                    (UUID) rs.getObject("id"), rs.getString("animal_name"), rs.getString("tutor_name"),
                    rs.getTimestamp("start_at").toInstant(), rs.getTimestamp("end_at").toInstant()),
                professionalId, Timestamp.from(newStart), Timestamp.from(newEnd))
            .stream().findFirst();
    }

    /**
     * Cria o agendamento numa transação que RE-VERIFICA o conflito (por profissional) antes do insert.
     * end_at materializado; snapshots de tutor/animal/profissional/serviço. Lança
     * {@link SlotConflictException} se conflitar.
     */
    @Transactional
    public PetAppointment insertAppointment(UUID companyId, UUID professionalId, String professionalName,
                                            UUID serviceId, String serviceName, String serviceCategory,
                                            Integer priceCents, int durationMinutes, UUID animalId,
                                            String animalName, String animalSpecies, UUID contactId,
                                            UUID conversationId, String tutorName, String tutorPhone,
                                            Instant startAt, String notes) {
        Instant endAt = startAt.plusSeconds(durationMinutes * 60L);
        Optional<PetAppointmentConflict> conflict = findConflict(professionalId, startAt, endAt);
        if (conflict.isPresent()) {
            throw new SlotConflictException(conflict.get());
        }
        UUID id = jdbcTemplate.queryForObject(
            "insert into pet_appointments (company_id, professional_id, service_id, animal_id, contact_id, "
                + "conversation_id, tutor_name, tutor_phone, animal_name, animal_species, professional_name, "
                + "service_name, service_category, price_cents, duration_minutes, start_at, end_at, notes) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, professionalId, serviceId, animalId, contactId, conversationId, tutorName,
            tutorPhone, animalName, animalSpecies, professionalName, serviceName, serviceCategory, priceCents,
            durationMinutes, Timestamp.from(startAt), Timestamp.from(endAt), notes);
        return findById(companyId, id).orElseThrow();
    }

    public void updateStatus(UUID companyId, UUID id, String newStatus) {
        jdbcTemplate.update("update pet_appointments set status = ?, status_updated_at = now() "
            + "where company_id = ? and id = ?", newStatus, companyId, id);
    }

    /** Lançada pelo insert quando o re-check transacional detecta conflito de slot. */
    public static class SlotConflictException extends RuntimeException {
        private final transient PetAppointmentConflict conflict;

        public SlotConflictException(PetAppointmentConflict conflict) {
            this.conflict = conflict;
        }

        public PetAppointmentConflict conflict() {
            return conflict;
        }
    }

    /** Próximos agendamentos ATIVOS do TUTOR — bloco do contexto (onda 1, tag de confirmação). */
    public java.util.List<PetAppointment> listUpcomingByContact(UUID companyId, UUID contactId, int limit) {
        return jdbcTemplate.query(
            "select " + COLS + " from pet_appointments where company_id = ? and contact_id = ? "
                + "and status in ('agendado','confirmado') and start_at >= now() "
                + "order by start_at asc limit " + Math.max(1, limit),
            MAPPER, companyId, contactId);
    }
}
