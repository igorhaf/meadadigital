package com.meada.messaging;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Leitura de {@code faqs} ativos. Consumido pelo PromptBuilder.
 */
@Repository
public class FaqRepository {

    private static final RowMapper<Faq> ROW_MAPPER = (rs, rowNum) ->
        new Faq(rs.getString("question"), rs.getString("answer"));

    // Só ativos e não-deletados. order by created_at, id: faqs NÃO tem unique de
    // negócio (mesma pergunta pode repetir reformulada), então created_at sozinho
    // pode colidir em inserts rápidos — o id é tiebreaker para ordem determinística.
    private static final String SELECT_ACTIVE =
        "select question, answer from faqs "
            + "where company_id = ? and deleted_at is null and active = true "
            + "order by created_at, id";

    private final JdbcTemplate jdbcTemplate;

    public FaqRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Faq> findActiveByCompany(UUID companyId) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        return jdbcTemplate.query(SELECT_ACTIVE, ROW_MAPPER, companyId);
    }
}
