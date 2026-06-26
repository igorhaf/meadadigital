package com.meada.whatsapp.profiles.padaria;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Acesso a {@code padaria_config} (camada 8.8 / perfil padaria). 1:1 com company. Ausente →
 * {@link PadariaConfig#DEFAULT}. Clone de
 * {@link com.meada.whatsapp.profiles.floricultura.FloriculturaConfigRepository} + a coluna
 * {@code lead_time_days_default} (ESCAPADA 1). Opera via service_role; o escopo por company_id no
 * WHERE é a defesa.
 */
@Repository
public class PadariaConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public PadariaConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Config do tenant, ou {@link PadariaConfig#DEFAULT} se não houver linha. */
    public PadariaConfig findByCompany(UUID companyId) {
        return jdbcTemplate.query(
                "select delivery_fee_cents, min_order_cents, lead_time_days_default "
                    + "from padaria_config where company_id = ?",
                (rs, rn) -> new PadariaConfig(
                    rs.getInt("delivery_fee_cents"), rs.getInt("min_order_cents"),
                    rs.getInt("lead_time_days_default")),
                companyId)
            .stream().findFirst().orElse(PadariaConfig.DEFAULT);
    }

    /** Upsert da config (insert ou update por company_id). Mantém updated_at. */
    public PadariaConfig upsert(UUID companyId, int deliveryFeeCents, int minOrderCents,
                                int leadTimeDaysDefault) {
        jdbcTemplate.update(
            "insert into padaria_config (company_id, delivery_fee_cents, min_order_cents, lead_time_days_default) "
                + "values (?, ?, ?, ?) "
                + "on conflict (company_id) do update set "
                + "delivery_fee_cents = excluded.delivery_fee_cents, "
                + "min_order_cents = excluded.min_order_cents, "
                + "lead_time_days_default = excluded.lead_time_days_default, updated_at = now()",
            companyId, deliveryFeeCents, minOrderCents, leadTimeDaysDefault);
        return findByCompany(companyId);
    }
}
