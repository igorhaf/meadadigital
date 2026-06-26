package com.meada.whatsapp.profiles.suplementos;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Acesso a {@code sup_config} (camada 8.24). 1:1 com company. Ausente → {@link SuplementosConfig#ZERO}.
 * Clone de {@link com.meada.whatsapp.profiles.lingerie.LingerieConfigRepository} /
 * {@link com.meada.whatsapp.profiles.adega.AdegaConfigRepository}. Opera via service_role; o escopo
 * por company_id no WHERE é a defesa.
 */
@Repository
public class SuplementosConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public SuplementosConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Config do tenant, ou {@link SuplementosConfig#ZERO} se não houver linha. */
    public SuplementosConfig findByCompany(UUID companyId) {
        return jdbcTemplate.query(
                "select delivery_fee_cents, min_order_cents from sup_config where company_id = ?",
                (rs, rn) -> new SuplementosConfig(
                    rs.getInt("delivery_fee_cents"), rs.getInt("min_order_cents")),
                companyId)
            .stream().findFirst().orElse(SuplementosConfig.ZERO);
    }

    /** Upsert da config (insert ou update por company_id). Mantém updated_at. */
    public SuplementosConfig upsert(UUID companyId, int deliveryFeeCents, int minOrderCents) {
        jdbcTemplate.update(
            "insert into sup_config (company_id, delivery_fee_cents, min_order_cents) "
                + "values (?, ?, ?) "
                + "on conflict (company_id) do update set "
                + "delivery_fee_cents = excluded.delivery_fee_cents, "
                + "min_order_cents = excluded.min_order_cents, updated_at = now()",
            companyId, deliveryFeeCents, minOrderCents);
        return findByCompany(companyId);
    }
}
