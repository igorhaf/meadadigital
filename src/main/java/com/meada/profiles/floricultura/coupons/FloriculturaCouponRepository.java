package com.meada.profiles.floricultura.coupons;

import com.meada.common.coupons.CouponRepositoryBase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.util.UUID;

/**
 * Acesso a {@code floricultura_coupons} — subclasse FINA do motor comum {@link CouponRepositoryBase}
 * (unificação 2026-07 dos 7 clones de cupom; SQL e semântica vivem na base).
 */
@Repository
public class FloriculturaCouponRepository extends CouponRepositoryBase<FloriculturaCoupon> {

    private static final RowMapper<FloriculturaCoupon> MAPPER = (rs, rn) -> {
        Date vu = rs.getDate("valid_until");
        Object maxUses = rs.getObject("max_uses");
        return new FloriculturaCoupon(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("company_id"),
            rs.getString("code"),
            rs.getString("kind"),
            rs.getInt("value"),
            rs.getInt("min_order_cents"),
            maxUses == null ? null : ((Number) maxUses).intValue(),
            rs.getInt("uses"),
            vu == null ? null : vu.toLocalDate(),
            rs.getBoolean("active"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());
    };

    public FloriculturaCouponRepository(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    @Override
    protected String table() {
        return "floricultura_coupons";
    }

    @Override
    protected String minColumn() {
        return "min_order_cents";
    }

    @Override
    protected RowMapper<FloriculturaCoupon> mapper() {
        return MAPPER;
    }
}
