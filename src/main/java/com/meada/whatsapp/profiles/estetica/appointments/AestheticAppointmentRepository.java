package com.meada.whatsapp.profiles.estetica.appointments;

import com.meada.whatsapp.profiles.estetica.packages.AestheticPackageRepository;
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
 * Acesso a {@code aesthetic_appointments} (camada 8.3). Opera via service_role.
 *
 * <p>Conflito POR PROFISSIONAL (clone salon). {@link #insertAppointment} re-verifica o conflito DENTRO
 * da transação (defesa race) e materializa end_at + snapshots. A ESCAPADA: quando {@code packageId}
 * não é null, o insert CONSOME 1 sessão do pacote na MESMA transação (via
 * {@link AestheticPackageRepository#consumeSession}) — se o consumo falhar (pacote não-ativo/esgotado),
 * lança {@link PackageConsumeException} e a transação inteira reverte (nada é criado).
 */
@Repository
public class AestheticAppointmentRepository {

    private static final RowMapper<AestheticAppointment> MAPPER = (rs, rn) -> new AestheticAppointment(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("professional_id"),
        rs.getString("professional_name"),
        (UUID) rs.getObject("procedure_id"),
        rs.getString("procedure_name"),
        (UUID) rs.getObject("package_id"),
        rs.getBoolean("consumed_session"),
        (UUID) rs.getObject("conversation_id"),
        (UUID) rs.getObject("contact_id"),
        rs.getString("guest_name"),
        rs.getString("guest_phone"),
        rs.getTimestamp("start_at").toInstant(),
        rs.getTimestamp("end_at").toInstant(),
        rs.getInt("duration_minutes"),
        rs.getString("status"),
        rs.getString("notes"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("status_updated_at").toInstant());

    private static final String COLS =
        "id, professional_id, professional_name, procedure_id, procedure_name, package_id, consumed_session, "
            + "conversation_id, contact_id, guest_name, guest_phone, start_at, end_at, duration_minutes, "
            + "status, notes, created_at, status_updated_at";

    private final JdbcTemplate jdbcTemplate;
    private final AestheticPackageRepository packageRepository;

    public AestheticAppointmentRepository(JdbcTemplate jdbcTemplate, AestheticPackageRepository packageRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.packageRepository = packageRepository;
    }

    public List<AestheticAppointment> listByCompany(UUID companyId, String status, Instant dateFrom,
                                                    Instant dateTo, UUID professionalId, UUID contactId,
                                                    int limit, int offset) {
        StringBuilder sql = new StringBuilder("select " + COLS + " from aesthetic_appointments where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) { sql.append(" and status = ?"); args.add(status); }
        if (dateFrom != null) { sql.append(" and start_at >= ?"); args.add(Timestamp.from(dateFrom)); }
        if (dateTo != null) { sql.append(" and start_at < ?"); args.add(Timestamp.from(dateTo)); }
        if (professionalId != null) { sql.append(" and professional_id = ?"); args.add(professionalId); }
        if (contactId != null) { sql.append(" and contact_id = ?"); args.add(contactId); }
        sql.append(" order by start_at asc limit ? offset ?");
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public long countByCompany(UUID companyId, String status, Instant dateFrom, Instant dateTo,
                               UUID professionalId, UUID contactId) {
        StringBuilder sql = new StringBuilder("select count(*) from aesthetic_appointments where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) { sql.append(" and status = ?"); args.add(status); }
        if (dateFrom != null) { sql.append(" and start_at >= ?"); args.add(Timestamp.from(dateFrom)); }
        if (dateTo != null) { sql.append(" and start_at < ?"); args.add(Timestamp.from(dateTo)); }
        if (professionalId != null) { sql.append(" and professional_id = ?"); args.add(professionalId); }
        if (contactId != null) { sql.append(" and contact_id = ?"); args.add(contactId); }
        Long n = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0L : n;
    }

    public Optional<AestheticAppointment> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query("select " + COLS + " from aesthetic_appointments where company_id = ? and id = ?",
                MAPPER, companyId, id).stream().findFirst();
    }

    public List<AestheticAppointment> listByContact(UUID companyId, UUID contactId, int limit) {
        return jdbcTemplate.query(
            "select " + COLS + " from aesthetic_appointments where company_id = ? and contact_id = ? "
                + "order by start_at desc limit ?",
            MAPPER, companyId, contactId, limit);
    }

    public List<AestheticAppointment> listActiveByProfessional(UUID companyId, UUID professionalId,
                                                               Instant from, Instant to) {
        return jdbcTemplate.query(
            "select " + COLS + " from aesthetic_appointments where company_id = ? and professional_id = ? "
                + "and status in ('agendado','confirmado') and start_at >= ? and start_at < ? order by start_at asc",
            MAPPER, companyId, professionalId, Timestamp.from(from), Timestamp.from(to));
    }

    /** Conflito de slot por PROFISSIONAL (clone salon). Janela materializada. */
    public Optional<AestheticAppointmentConflict> findConflict(UUID professionalId, Instant newStart, Instant newEnd) {
        return jdbcTemplate.query(
                "select id, guest_name, start_at, end_at from aesthetic_appointments "
                    + "where professional_id = ? and status in ('agendado','confirmado') "
                    + "and not (end_at <= ? or start_at >= ?) order by start_at asc limit 1",
                (rs, rn) -> new AestheticAppointmentConflict(
                    (UUID) rs.getObject("id"),
                    rs.getString("guest_name"),
                    rs.getTimestamp("start_at").toInstant(),
                    rs.getTimestamp("end_at").toInstant()),
                professionalId, Timestamp.from(newStart), Timestamp.from(newEnd))
            .stream().findFirst();
    }

    /**
     * Cria o agendamento numa transação que: (1) re-verifica o conflito por profissional; (2) se
     * packageId != null, CONSOME 1 sessão do pacote (UPDATE condicional status='ativo' and
     * remaining>0); se o consumo não afetar linha → {@link PackageConsumeException} e tudo reverte.
     * end_at materializado; snapshots gravados. consumed_session reflete se houve consumo.
     */
    @Transactional
    public AestheticAppointment insertAppointment(UUID companyId, UUID professionalId, String professionalName,
                                                  UUID procedureId, String procedureName, int durationMinutes,
                                                  UUID packageId, UUID conversationId, UUID contactId,
                                                  String guestName, String guestPhone, Instant startAt, String notes) {
        Instant endAt = startAt.plusSeconds(durationMinutes * 60L);
        Optional<AestheticAppointmentConflict> conflict = findConflict(professionalId, startAt, endAt);
        if (conflict.isPresent()) {
            throw new SlotConflictException(conflict.get());
        }
        boolean consumed = false;
        if (packageId != null) {
            // consumo transacional: se não afetar linha, o pacote não estava elegível → reverte tudo.
            boolean ok = packageRepository.consumeSession(companyId, packageId);
            if (!ok) {
                throw new PackageConsumeException();
            }
            consumed = true;
        }
        UUID id = jdbcTemplate.queryForObject(
            "insert into aesthetic_appointments (company_id, professional_id, procedure_id, package_id, "
                + "conversation_id, contact_id, guest_name, guest_phone, start_at, duration_minutes, end_at, "
                + "procedure_name, professional_name, consumed_session, notes) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, professionalId, procedureId, packageId, conversationId, contactId,
            guestName, guestPhone, Timestamp.from(startAt), durationMinutes, Timestamp.from(endAt),
            procedureName, professionalName, consumed, notes);
        return findById(companyId, id).orElseThrow();
    }

    /**
     * Persiste a transição de status. Se o novo status é 'cancelado' e o agendamento havia consumido
     * sessão, DEVOLVE a sessão ao pacote na MESMA transação (e marca consumed_session=false pra não
     * devolver duas vezes). Tudo transacional.
     */
    @Transactional
    public void updateStatus(UUID companyId, UUID id, String newStatus, boolean cancelling) {
        if (cancelling) {
            AestheticAppointment current = findById(companyId, id).orElseThrow();
            if (current.consumedSession() && current.packageId() != null) {
                packageRepository.returnSession(companyId, current.packageId());
                jdbcTemplate.update("update aesthetic_appointments set consumed_session = false "
                    + "where company_id = ? and id = ?", companyId, id);
            }
        }
        jdbcTemplate.update(
            "update aesthetic_appointments set status = ?, status_updated_at = now() where company_id = ? and id = ?",
            newStatus, companyId, id);
    }

    /** Lançada pelo insert quando o re-check transacional detecta conflito de slot. */
    public static class SlotConflictException extends RuntimeException {
        private final transient AestheticAppointmentConflict conflict;

        public SlotConflictException(AestheticAppointmentConflict conflict) {
            this.conflict = conflict;
        }

        public AestheticAppointmentConflict conflict() {
            return conflict;
        }
    }

    /** Lançada quando o consumo de sessão do pacote não afeta linha (não-ativo ou esgotado). */
    public static class PackageConsumeException extends RuntimeException {}
}
