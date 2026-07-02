package com.meada.profiles.academia.referrals;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code academia_referrals} (camada 7.7). Opera via service_role; escopo por company_id.
 * O código é gerado pelo service; o {@link #insert} propaga DuplicateKeyException em colisão de
 * {@code unique (company_id, code)} para o service tentar outro código.
 */
@Repository
public class AcademiaReferralRepository {

    private static final RowMapper<AcademiaReferral> MAPPER = (rs, rn) -> {
        Timestamp converted = rs.getTimestamp("converted_at");
        Integer reward = (Integer) rs.getObject("reward_percent");
        return new AcademiaReferral(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("referrer_contact_id"),
            rs.getString("referred_name"),
            rs.getString("referred_phone"),
            rs.getString("code"),
            rs.getString("status"),
            reward,
            rs.getTimestamp("created_at").toInstant(),
            converted == null ? null : converted.toInstant());
    };

    private static final String COLS =
        "id, referrer_contact_id, referred_name, referred_phone, code, status, reward_percent, "
            + "created_at, converted_at";

    private final JdbcTemplate jdbcTemplate;

    public AcademiaReferralRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AcademiaReferral> listByCompany(UUID companyId, String statusFilter) {
        StringBuilder sql = new StringBuilder(
            "select " + COLS + " from academia_referrals where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (statusFilter != null && !statusFilter.isBlank()) {
            sql.append(" and status = ?");
            args.add(statusFilter);
        }
        sql.append(" order by created_at desc");
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public Optional<AcademiaReferral> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query(
                "select " + COLS + " from academia_referrals where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    /**
     * Insere a indicação com o código fornecido. Propaga DuplicateKeyException se o
     * {@code (company_id, code)} colidir (o service tenta outro código).
     */
    public AcademiaReferral insert(UUID companyId, UUID referrerContactId, String referredName,
                                   String referredPhone, String code, Integer rewardPercent) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into academia_referrals "
                + "(company_id, referrer_contact_id, referred_name, referred_phone, code, reward_percent) "
                + "values (?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, referrerContactId, referredName.trim(),
            referredPhone == null ? null : referredPhone.trim(), code, rewardPercent);
        return findById(companyId, id).orElseThrow();
    }

    /**
     * Marca a indicação como 'convertida' (converted_at = now()) SÓ se ainda estiver 'pendente'.
     * @return o número de linhas afetadas (0 = não existe / já não estava pendente).
     */
    public int markConverted(UUID companyId, UUID id) {
        return jdbcTemplate.update(
            "update academia_referrals set status = 'convertida', converted_at = now() "
                + "where company_id = ? and id = ? and status = 'pendente'",
            companyId, id);
    }
}
