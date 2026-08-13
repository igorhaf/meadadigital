package com.meada.messaging;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Leitura de {@code services} ativos. Consumido pelo PromptBuilder para listar o
 * catálogo no prompt da IA.
 */
@Repository
public class ServiceRepository {

    // price_cents é nullable: getObject(..., Integer.class) preserva o null
    // (getInt devolveria 0, mascarando "sem preço").
    private static final RowMapper<Service> ROW_MAPPER = (rs, rowNum) ->
        new Service(
            rs.getString("name"),
            rs.getString("description"),
            rs.getObject("price_cents", Integer.class));

    // Só ativos e não-deletados. order by name: o unique parcial
    // uq_services_company_name_active garante name único entre ativos do tenant,
    // então a ordenação já é determinística — sem tiebreaker.
    private static final String SELECT_ACTIVE =
        "select name, description, price_cents from services "
            + "where company_id = ? and deleted_at is null and active = true "
            + "order by name";

    private final JdbcTemplate jdbcTemplate;

    public ServiceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Service> findActiveByCompany(UUID companyId) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        return jdbcTemplate.query(SELECT_ACTIVE, ROW_MAPPER, companyId);
    }
}
