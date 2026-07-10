package com.meada.profiles.concessionaria.tradein;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Trade-in — proposta de usado na troca (onda Concessionária 2, backlog #5). A IA COLETA os
 * dados do usado (marca/modelo/ano/km/estado + valor DECLARADO pelo cliente) — NUNCA avalia nem
 * promete valor (trava); a avaliação ({@code offer_cents}) é HUMANA no painel. Best-effort.
 */
@Service
public class TradeInService {

    private static final Logger log = LoggerFactory.getLogger(TradeInService.class);

    private final JdbcTemplate jdbcTemplate;

    public TradeInService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UUID open(UUID companyId, UUID contactId, UUID conversationId, String customerName,
                     UUID interestVehicleId, String brand, String model, Integer year, Integer km,
                     String condition, Integer askingCents) {
        return jdbcTemplate.queryForObject(
            "insert into concessionaria_tradein_offers (company_id, contact_id, conversation_id, "
                + "customer_name, interest_vehicle_id, used_brand, used_model, used_year, used_km, "
                + "used_condition, asking_cents) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, contactId, conversationId, customerName, interestVehicleId,
            brand, model, year, km, condition, askingCents);
    }

    public List<Map<String, Object>> list(UUID companyId, String status) {
        StringBuilder sql = new StringBuilder(
            "select t.id, t.customer_name, t.used_brand, t.used_model, t.used_year, t.used_km, "
                + "t.used_condition, t.asking_cents, t.status, t.offer_cents, t.notes, t.created_at, "
                + "v.brand as interest_brand, v.model as interest_model "
                + "from concessionaria_tradein_offers t "
                + "left join concessionaria_vehicles v on v.id = t.interest_vehicle_id "
                + "where t.company_id = ?");
        java.util.List<Object> args = new java.util.ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) {
            sql.append(" and t.status = ?");
            args.add(status);
        }
        sql.append(" order by t.created_at desc limit 200");
        return jdbcTemplate.query(sql.toString(), (rs, rn) -> {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", rs.getObject("id"));
            m.put("customerName", rs.getString("customer_name"));
            m.put("usedBrand", rs.getString("used_brand"));
            m.put("usedModel", rs.getString("used_model"));
            m.put("usedYear", rs.getObject("used_year"));
            m.put("usedKm", rs.getObject("used_km"));
            m.put("usedCondition", rs.getString("used_condition"));
            m.put("askingCents", rs.getObject("asking_cents"));
            m.put("status", rs.getString("status"));
            m.put("offerCents", rs.getObject("offer_cents"));
            m.put("notes", rs.getString("notes"));
            m.put("createdAt", rs.getTimestamp("created_at").toInstant().toString());
            m.put("interestVehicle", rs.getString("interest_brand") == null ? null
                : rs.getString("interest_brand") + " " + rs.getString("interest_model"));
            return m;
        }, args.toArray());
    }

    /** Avaliação/gestão humana: status + offer_cents + notes. */
    public boolean update(UUID companyId, UUID id, String status, Integer offerCents, String notes) {
        return jdbcTemplate.update(
            "update concessionaria_tradein_offers set "
                + "status = coalesce(?, status), offer_cents = coalesce(?, offer_cents), "
                + "notes = coalesce(?, notes), status_updated_at = now() "
                + "where company_id = ? and id = ?",
            status, offerCents, notes, companyId, id) > 0;
    }
}
