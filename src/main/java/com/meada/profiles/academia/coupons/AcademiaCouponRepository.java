package com.meada.profiles.academia.coupons;

import com.meada.common.coupons.CouponRepositoryBase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.util.UUID;

/**
 * Acesso a {@code academia_coupons} — subclasse FINA do motor comum {@link CouponRepositoryBase}
 * (unificação 2026-07 dos 7 clones de cupom; SQL e semântica vivem na base). A academia usa a
 * coluna {@code min_cents} (os demais perfis, {@code min_order_cents}).
 */
@Repository
public class AcademiaCouponRepository extends CouponRepositoryBase<AcademiaCoupon> {

    private static final RowMapper<AcademiaCoupon> MAPPER = (rs, rn) -> {
        Date vu = rs.getDate("valid_until");
        Object maxUses = rs.getObject("max_uses");
        return new AcademiaCoupon(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("company_id"),
            rs.getString("code"),
            rs.getString("kind"),
            rs.getInt("value"),
            rs.getInt("min_cents"),
            maxUses == null ? null : ((Number) maxUses).intValue(),
            rs.getInt("uses"),
            vu == null ? null : vu.toLocalDate(),
            rs.getBoolean("active"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());
    };

    public AcademiaCouponRepository(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    @Override
    protected String table() {
        return "academia_coupons";
    }

    @Override
    protected String minColumn() {
        return "min_cents";
    }

    @Override
    protected RowMapper<AcademiaCoupon> mapper() {
        return MAPPER;
    }
}
