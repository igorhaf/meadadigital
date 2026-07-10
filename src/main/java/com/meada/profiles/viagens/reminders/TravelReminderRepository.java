package com.meada.profiles.viagens.reminders;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Varredura e marcação dos disparos temporais da onda Viagens (backlog #2 lembretes/pós-venda e
 * #8 follow-up de orçada). service_role, cruza TODOS os tenants viagens numa query só (o job roda
 * global). Idempotência: por (proposta, data) nos lembretes de viagem (remarcar rearma) e por
 * episódio de 'orcada' no follow-up (quote_followup_sent_at < status_updated_at rearma).
 * Espelho do AtelieFittingReminderRepository.
 */
@Repository
public class TravelReminderRepository {

    private static final RowMapper<DueTrip> TRIP_MAPPER = (rs, rn) -> new DueTrip(
        (UUID) rs.getObject("proposal_id"),
        (UUID) rs.getObject("company_id"),
        (UUID) rs.getObject("conversation_id"),
        rs.getString("customer_name"),
        rs.getString("destination"),
        rs.getDate("start_date") == null ? null : rs.getDate("start_date").toLocalDate(),
        rs.getDate("end_date") == null ? null : rs.getDate("end_date").toLocalDate());

    private static final String TRIP_SELECT =
        "select p.id as proposal_id, p.company_id, p.conversation_id, p.customer_name, "
            + "p.destination, p.start_date, p.end_date "
            + "from travel_proposals p "
            + "join companies c on c.id = p.company_id "
            + "left join travel_config cfg on cfg.company_id = p.company_id "
            + "where c.profile_id = 'viagens' "
            + "and coalesce(cfg.trip_reminder_enabled, true) ";

    private final JdbcTemplate jdbcTemplate;

    public TravelReminderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Fechadas com ida em D+7 (checklist de documentos/bagagem) ainda não lembradas pra essa ida. */
    public List<DueTrip> findPretripDue(LocalDate startDate) {
        return jdbcTemplate.query(
            TRIP_SELECT
                + "and p.status = 'fechada' and p.start_date = ? "
                + "and (p.pretrip_reminded_start_date is null or p.pretrip_reminded_start_date <> p.start_date) "
                + "order by p.company_id",
            TRIP_MAPPER, Date.valueOf(startDate));
    }

    /** Fechadas com ida HOJE (boa viagem) ainda não saudadas pra essa ida. */
    public List<DueTrip> findStartDue(LocalDate startDate) {
        return jdbcTemplate.query(
            TRIP_SELECT
                + "and p.status = 'fechada' and p.start_date = ? "
                + "and (p.start_reminded_start_date is null or p.start_reminded_start_date <> p.start_date) "
                + "order by p.company_id",
            TRIP_MAPPER, Date.valueOf(startDate));
    }

    /** Fechadas/realizadas com volta em D-2 (pós-viagem/NPS) ainda não abordadas pra essa volta. */
    public List<DueTrip> findPosttripDue(LocalDate endDate) {
        return jdbcTemplate.query(
            TRIP_SELECT
                + "and p.status in ('fechada','realizada') and p.end_date = ? "
                + "and (p.posttrip_reminded_end_date is null or p.posttrip_reminded_end_date <> p.end_date) "
                + "order by p.company_id",
            TRIP_MAPPER, Date.valueOf(endDate));
    }

    public void markPretripReminded(UUID proposalId, LocalDate startDate) {
        jdbcTemplate.update(
            "update travel_proposals set pretrip_reminded_start_date = ?, updated_at = now() where id = ?",
            Date.valueOf(startDate), proposalId);
    }

    public void markStartReminded(UUID proposalId, LocalDate startDate) {
        jdbcTemplate.update(
            "update travel_proposals set start_reminded_start_date = ?, updated_at = now() where id = ?",
            Date.valueOf(startDate), proposalId);
    }

    public void markPosttripReminded(UUID proposalId, LocalDate endDate) {
        jdbcTemplate.update(
            "update travel_proposals set posttrip_reminded_end_date = ?, updated_at = now() where id = ?",
            Date.valueOf(endDate), proposalId);
    }

    /**
     * Orçadas paradas há {@code quote_followup_days} da config (default 2) sem mudança de status e
     * sem follow-up DESTE episódio (re-orçar → status_updated_at avança → rearma).
     */
    public List<DueQuote> findQuoteFollowupsDue() {
        return jdbcTemplate.query(
            "select p.id as proposal_id, p.company_id, p.conversation_id, p.customer_name, "
                + "p.destination, p.total_cents "
                + "from travel_proposals p "
                + "join companies c on c.id = p.company_id "
                + "left join travel_config cfg on cfg.company_id = p.company_id "
                + "where c.profile_id = 'viagens' "
                + "and coalesce(cfg.quote_followup_enabled, true) "
                + "and p.status = 'orcada' "
                + "and p.status_updated_at <= now() - make_interval(days => coalesce(cfg.quote_followup_days, 2)) "
                + "and (p.quote_followup_sent_at is null or p.quote_followup_sent_at < p.status_updated_at) "
                + "order by p.company_id",
            (rs, rn) -> new DueQuote(
                (UUID) rs.getObject("proposal_id"),
                (UUID) rs.getObject("company_id"),
                (UUID) rs.getObject("conversation_id"),
                rs.getString("customer_name"),
                rs.getString("destination"),
                rs.getInt("total_cents")));
    }

    public void markQuoteFollowedUp(UUID proposalId) {
        jdbcTemplate.update(
            "update travel_proposals set quote_followup_sent_at = now(), updated_at = now() where id = ?",
            proposalId);
    }
}
