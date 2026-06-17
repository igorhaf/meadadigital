package com.meada.whatsapp.profiles.legal.clients;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code legal_clients} (camada 7.2). Opera via service_role; escopo por company_id no
 * WHERE é a defesa.
 */
@Repository
public class LegalClientRepository {

    private static final RowMapper<LegalClient> MAPPER = (rs, rn) -> new LegalClient(
        (UUID) rs.getObject("id"),
        rs.getString("name"),
        rs.getString("email"),
        rs.getString("phone"),
        rs.getString("document"),
        (UUID) rs.getObject("contact_id"),
        rs.getString("notes"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private static final String COLS =
        "id, name, email, phone, document, contact_id, notes, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    public LegalClientRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Lista clientes do tenant; search (ilike) em name/email/phone/document. */
    public List<LegalClient> listByCompany(UUID companyId, String search) {
        StringBuilder sql = new StringBuilder(
            "select " + COLS + " from legal_clients where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (search != null && !search.isBlank()) {
            sql.append(" and (name ilike ? or coalesce(email,'') ilike ? "
                + "or coalesce(phone,'') ilike ? or coalesce(document,'') ilike ?)");
            String like = "%" + search.trim() + "%";
            args.add(like); args.add(like); args.add(like); args.add(like);
        }
        sql.append(" order by name asc");
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public Optional<LegalClient> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query(
                "select " + COLS + " from legal_clients where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    /** Cliente vinculado a um contato (usado pela IA para resolver por telefone). */
    public Optional<LegalClient> findByContactId(UUID companyId, UUID contactId) {
        return jdbcTemplate.query(
                "select " + COLS + " from legal_clients where company_id = ? and contact_id = ? limit 1",
                MAPPER, companyId, contactId)
            .stream().findFirst();
    }

    public LegalClient insert(UUID companyId, String name, String email, String phone,
                              String document, UUID contactId, String notes) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into legal_clients (company_id, name, email, phone, document, contact_id, notes) "
                + "values (?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, name.trim(), email, phone, document, contactId, notes);
        return findById(companyId, id).orElseThrow();
    }

    /** Atualiza campos não-null (PATCH parcial). Retorna empty se não existir/pertencer ao tenant. */
    public Optional<LegalClient> update(UUID companyId, UUID id, String name, String email,
                                        String phone, String document, UUID contactId,
                                        boolean contactIdSet, String notes) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (name != null && !name.isBlank()) { sets.add("name = ?"); args.add(name.trim()); }
        if (email != null) { sets.add("email = ?"); args.add(email); }
        if (phone != null) { sets.add("phone = ?"); args.add(phone); }
        if (document != null) { sets.add("document = ?"); args.add(document); }
        // contactId só muda quando explicitamente enviado (pode ser setado para null = desvincular).
        if (contactIdSet) { sets.add("contact_id = ?"); args.add(contactId); }
        if (notes != null) { sets.add("notes = ?"); args.add(notes); }
        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update(
                "update legal_clients set " + String.join(", ", sets) + " where company_id = ? and id = ?",
                args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    /** Hard delete. Lança DataIntegrityViolation se houver processo (FK restrict). */
    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update(
            "delete from legal_clients where company_id = ? and id = ?", companyId, id) > 0;
    }

    /**
     * contact_id de um cliente (para invalidar o cache de contexto da IA). Optional vazio se o
     * cliente não existe OU não tem contato vinculado (contact_id NULL) — em ambos os casos não
     * há contato a invalidar. Não usa stream().findFirst() porque um contact_id NULL faria
     * Stream.findFirst() lançar NPE.
     */
    public Optional<UUID> findContactId(UUID companyId, UUID clientId) {
        List<UUID> rows = jdbcTemplate.query(
            "select contact_id from legal_clients where company_id = ? and id = ?",
            (rs, rn) -> (UUID) rs.getObject("contact_id"), companyId, clientId);
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.get(0));
    }
}
