package com.meada.whatsapp.appointments;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code appointments} (camada 5.19 #59/#60/#63/#64). service_role; isolamento por
 * companyId no WHERE (defesa em profundidade — o RLS também isola). O conflito de horário é
 * barrado pelo unique parcial {@code uq_appointments_no_conflict} (company_id, scheduled_at)
 * entre os 'scheduled' — o insert traduz a violação em {@link Optional#empty()}.
 */
@Repository
public class AppointmentRepository {

    private static final RowMapper<Appointment> ROW_MAPPER = (rs, rowNum) ->
        new Appointment(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("company_id"),
            (UUID) rs.getObject("contact_id"),
            (UUID) rs.getObject("conversation_id"),
            (UUID) rs.getObject("service_id"),
            rs.getTimestamp("scheduled_at").toInstant(),
            rs.getString("status"),
            rs.getString("notes"),
            rs.getBoolean("reminded_24h"),
            rs.getBoolean("reminded_2h"),
            rs.getTimestamp("created_at").toInstant());

    private static final String COLUMNS =
        "id, company_id, contact_id, conversation_id, service_id, scheduled_at, status, notes, "
            + "reminded_24h, reminded_2h, created_at";

    private final JdbcTemplate jdbcTemplate;

    public AppointmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Insere um agendamento 'scheduled' e retorna a linha criada. Se o horário já estiver
     * ocupado por outro 'scheduled' da mesma empresa, o unique parcial dispara
     * {@link DuplicateKeyException} e este método devolve {@link Optional#empty()} (o caller
     * trata como "slot tomado", sem propagar exceção).
     *
     * @return o agendamento criado, ou empty se o horário conflitou.
     */
    public Optional<Appointment> insert(UUID companyId, UUID contactId, UUID conversationId,
                                        UUID serviceId, Instant scheduledAt, String notes) {
        Objects.requireNonNull(companyId, "companyId");
        Objects.requireNonNull(contactId, "contactId");
        Objects.requireNonNull(scheduledAt, "scheduledAt");
        try {
            Appointment created = jdbcTemplate.queryForObject(
                "insert into appointments "
                    + "(company_id, contact_id, conversation_id, service_id, scheduled_at, notes) "
                    + "values (?, ?, ?, ?, ?, ?) returning " + COLUMNS,
                ROW_MAPPER, companyId, contactId, conversationId, serviceId,
                Timestamp.from(scheduledAt), notes);
            return Optional.ofNullable(created);
        } catch (DuplicateKeyException e) {
            // Horário já ocupado por outro 'scheduled' (uq_appointments_no_conflict).
            return Optional.empty();
        }
    }

    /**
     * Agendamentos da empresa cujo scheduled_at cai em [from, to) — para o calendário (#59).
     * Inclui todos os status (o painel mostra cancelados/concluídos também). Ordenado por
     * horário.
     */
    public List<Appointment> findByCompanyBetween(UUID companyId, Instant from, Instant to) {
        Objects.requireNonNull(companyId, "companyId");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        return jdbcTemplate.query(
            "select " + COLUMNS + " from appointments "
                + "where company_id = ? and scheduled_at >= ? and scheduled_at < ? "
                + "order by scheduled_at",
            ROW_MAPPER, companyId, Timestamp.from(from), Timestamp.from(to));
    }

    /**
     * Próximo agendamento ATIVO ('scheduled') e FUTURO do contato — o alvo de reschedule/cancel
     * (#64). Optional vazio se o contato não tem nenhum agendado à frente.
     */
    public Optional<Appointment> findActiveByContact(UUID contactId) {
        Objects.requireNonNull(contactId, "contactId");
        return jdbcTemplate.query(
                "select " + COLUMNS + " from appointments "
                    + "where contact_id = ? and status = 'scheduled' and scheduled_at > now() "
                    + "order by scheduled_at limit 1",
                ROW_MAPPER, contactId)
            .stream()
            .findFirst();
    }

    /** Atualiza o status de um agendamento da empresa. Retorna true se atualizou. */
    public boolean updateStatus(UUID id, UUID companyId, String status) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(companyId, "companyId");
        Objects.requireNonNull(status, "status");
        int n = jdbcTemplate.update(
            "update appointments set status = ?, updated_at = now() where id = ? and company_id = ?",
            status, id, companyId);
        return n > 0;
    }

    /**
     * Remarca um agendamento da empresa para um novo horário. Zera os flags de lembrete (o
     * novo horário precisa de novos lembretes). Retorna true se atualizou. Se o novo horário
     * conflitar com outro 'scheduled', o unique parcial dispara e devolvemos false (slot tomado).
     */
    public boolean reschedule(UUID id, UUID companyId, Instant newScheduledAt) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(companyId, "companyId");
        Objects.requireNonNull(newScheduledAt, "newScheduledAt");
        try {
            int n = jdbcTemplate.update(
                "update appointments set scheduled_at = ?, reminded_24h = false, reminded_2h = false, "
                    + "updated_at = now() where id = ? and company_id = ? and status = 'scheduled'",
                Timestamp.from(newScheduledAt), id, companyId);
            return n > 0;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    /**
     * Agendamentos 'scheduled' futuros próximos que ainda precisam de algum lembrete (#63):
     * scheduled_at em (now, horizon] e (¬reminded_24h ∨ ¬reminded_2h). O job decide, por
     * agendamento, se manda o de 24h ou o de 2h conforme a proximidade. horizon tipicamente
     * now+25h (cobre a janela de 24h com folga).
     */
    public List<Appointment> findDueReminders(Instant now, Instant horizon) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(horizon, "horizon");
        return jdbcTemplate.query(
            "select " + COLUMNS + " from appointments "
                + "where status = 'scheduled' and scheduled_at > ? and scheduled_at <= ? "
                + "and (reminded_24h = false or reminded_2h = false) "
                + "order by scheduled_at",
            ROW_MAPPER, Timestamp.from(now), Timestamp.from(horizon));
    }

    /**
     * Marca um dos flags de lembrete como enviado. {@code which} é "24h" ou "2h" — qualquer
     * outro valor é rejeitado (defesa contra injeção de nome de coluna). Idempotente.
     */
    public boolean markReminded(UUID id, String which) {
        Objects.requireNonNull(id, "id");
        String column = switch (which) {
            case "24h" -> "reminded_24h";
            case "2h" -> "reminded_2h";
            default -> throw new IllegalArgumentException("which deve ser '24h' ou '2h': " + which);
        };
        int n = jdbcTemplate.update(
            "update appointments set " + column + " = true, updated_at = now() where id = ?", id);
        return n > 0;
    }

    /**
     * Casa best-effort um serviceHint (texto livre da IA) a um service_id ATIVO da empresa, por
     * nome (case-insensitive, contém). Optional vazio quando não há match — o agendamento então
     * fica com service_id null (decisão de produto: nem todo agendamento amarra a um serviço).
     */
    public Optional<UUID> findServiceIdByNameHint(UUID companyId, String serviceHint) {
        Objects.requireNonNull(companyId, "companyId");
        if (serviceHint == null || serviceHint.isBlank()) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
                "select id from services "
                    + "where company_id = ? and deleted_at is null and active = true "
                    + "and name ilike ? order by name limit 1",
                (rs, rowNum) -> (UUID) rs.getObject("id"),
                companyId, "%" + serviceHint.trim() + "%")
            .stream()
            .findFirst();
    }

    /**
     * Horários já tomados por agendamentos 'scheduled' da empresa em [from, to) — para o
     * nextFreeSlots filtrar candidatos ocupados. Retorna os instants exatos.
     */
    public List<Instant> findTakenSlots(UUID companyId, Instant from, Instant to) {
        Objects.requireNonNull(companyId, "companyId");
        return jdbcTemplate.query(
            "select scheduled_at from appointments "
                + "where company_id = ? and status = 'scheduled' "
                + "and scheduled_at >= ? and scheduled_at < ?",
            (rs, rowNum) -> rs.getTimestamp("scheduled_at").toInstant(),
            companyId, Timestamp.from(from), Timestamp.from(to));
    }
}
