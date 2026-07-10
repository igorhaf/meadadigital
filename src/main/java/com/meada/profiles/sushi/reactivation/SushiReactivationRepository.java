package com.meada.profiles.sushi.reactivation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Varredura e log da reativação de inativos do sushi (onda 2, backlog #3). service_role, cruza
 * TODOS os tenants sushi com o opt-in LIGADO numa query só. "Pedido entregue" = status terminal
 * não-cancelado (mesma definição da fidelidade por contagem). O cooldown entre disparos pro mesmo
 * contato é a própria janela de inatividade (reactivation_days).
 */
@Repository
public class SushiReactivationRepository {

    private final JdbcTemplate jdbcTemplate;

    public SushiReactivationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DueInactiveContact> findDueContacts() {
        return jdbcTemplate.query(
            "select ct.company_id, ct.id as contact_id, ct.name as contact_name, "
                + "(select conv.id from conversations conv "
                + "  where conv.company_id = ct.company_id and conv.contact_id = ct.id "
                + "  order by conv.created_at desc limit 1) as conversation_id, "
                + "cp.code as coupon_code "
                + "from contacts ct "
                + "join companies co on co.id = ct.company_id and co.profile_id = 'sushi' "
                + "join sushi_restaurant_config cfg on cfg.company_id = ct.company_id "
                + "  and cfg.reactivation_enabled "
                + "left join sushi_coupons cp on cp.company_id = ct.company_id "
                + "  and cfg.reactivation_coupon_code is not null "
                + "  and lower(cp.code) = lower(cfg.reactivation_coupon_code) "
                + "  and cp.active "
                + "  and (cp.valid_until is null or cp.valid_until >= current_date) "
                + "  and (cp.max_uses is null or cp.uses < cp.max_uses) "
                + "where (select max(o.created_at) from sushi_orders o "
                + "         join sushi_order_statuses st on st.id = o.status "
                + "         where o.company_id = ct.company_id and o.contact_id = ct.id "
                + "         and st.is_terminal = true and st.name not ilike '%cancel%') "
                + "      < now() - make_interval(days => cfg.reactivation_days) "
                + "and not exists (select 1 from sushi_reactivation_log l "
                + "  where l.company_id = ct.company_id and l.contact_id = ct.id "
                + "  and l.sent_at > now() - make_interval(days => cfg.reactivation_days)) "
                + "order by ct.company_id",
            (rs, rn) -> new DueInactiveContact(
                (UUID) rs.getObject("company_id"),
                (UUID) rs.getObject("contact_id"),
                rs.getString("contact_name"),
                (UUID) rs.getObject("conversation_id"),
                rs.getString("coupon_code")));
    }

    /** Registra o disparo (idempotência por contato+janela — inclusive sem canal resolúvel). */
    public void markSent(UUID companyId, UUID contactId, boolean hadChannel) {
        jdbcTemplate.update(
            "insert into sushi_reactivation_log (company_id, contact_id, had_channel) values (?, ?, ?)",
            companyId, contactId, hadChannel);
    }
}
