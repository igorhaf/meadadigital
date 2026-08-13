package com.meada.messaging;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Leitura de {@code business_hours}. Consumido pelo PromptBuilder para informar
 * os horários de atendimento à IA.
 */
@Repository
public class BusinessHoursRepository {

    // opens_at/closes_at são nullable (null quando closed): getObject(..., LocalTime.class).
    private static final RowMapper<BusinessHours> ROW_MAPPER = (rs, rowNum) ->
        new BusinessHours(
            rs.getInt("weekday"),
            rs.getBoolean("closed"),
            rs.getObject("opens_at", LocalTime.class),
            rs.getObject("closes_at", LocalTime.class));

    // Retorna TODOS os registros (dias fechados inclusos — o prompt precisa saber
    // "fechado aos domingos") e SÓ os configurados (não força os 7 dias).
    // order by weekday, opens_at: a UNIQUE(company_id, weekday, opens_at) garante
    // que (weekday, opens_at) é único por tenant — ordenação determinística sem
    // tiebreaker. Múltiplas janelas do mesmo dia saem ordenadas por horário.
    private static final String SELECT_BY_COMPANY =
        "select weekday, closed, opens_at, closes_at from business_hours "
            + "where company_id = ? "
            + "order by weekday, opens_at";

    private final JdbcTemplate jdbcTemplate;

    public BusinessHoursRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<BusinessHours> findByCompany(UUID companyId) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        return jdbcTemplate.query(SELECT_BY_COMPANY, ROW_MAPPER, companyId);
    }
}
