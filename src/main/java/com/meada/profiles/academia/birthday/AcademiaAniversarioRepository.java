package com.meada.profiles.academia.birthday;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Acesso de leitura/marcação para a saudação de aniversário da Academia (backlog #14). Opera via
 * service_role (o job roda fora de request de tenant) — espelha o {@code AcademiaBillingRepository}
 * da régua de inadimplência.
 *
 * <p>A data de nascimento vive no CORE ({@code public.contacts.birth_date}); o filtro por dia/mês é
 * feito com {@code extract(...)} para casar o aniversário independentemente do ANO. O canal de envio é
 * resolvido a partir da conversa mais recente do contato (subquery correlacionada).
 */
@Repository
public class AcademiaAniversarioRepository {

    private static final RowMapper<BirthdayContact> BIRTHDAY_MAPPER = (rs, rn) -> new BirthdayContact(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("company_id"),
        rs.getString("name"),
        rs.getString("phone_number"),
        (UUID) rs.getObject("conversation_id"));

    private final JdbcTemplate jdbcTemplate;

    public AcademiaAniversarioRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Todas as empresas do perfil academia — o job varre uma a uma. */
    public List<UUID> findAcademiaCompanies() {
        return jdbcTemplate.query(
            "select id from companies where profile_id = 'academia'",
            (rs, rn) -> (UUID) rs.getObject("id"));
    }

    /**
     * Contatos de uma empresa cujo aniversário (dia/mês de {@code birth_date}) é o informado e que
     * AINDA NÃO foram saudados neste ano ({@code academia_birthday_greeted_year} nulo ou diferente).
     * Traz o telefone direto do contato + a conversa mais recente (para as credenciais Evolution).
     *
     * @param companyId   empresa
     * @param month       mês do aniversário (1..12), no fuso do tenant
     * @param day         dia do aniversário (1..31), no fuso do tenant
     * @param currentYear ano corrente — quem já tem esse ano marcado é excluído (idempotência)
     */
    public List<BirthdayContact> findBirthdayContacts(UUID companyId, int month, int day, int currentYear) {
        return jdbcTemplate.query(
            "select ct.id, ct.company_id, ct.name, ct.phone_number, "
                + "  (select cv.id from conversations cv "
                + "   where cv.contact_id = ct.id and cv.company_id = ct.company_id "
                + "   order by cv.last_message_at desc nulls last, cv.created_at desc limit 1) as conversation_id "
                + "from contacts ct "
                + "where ct.company_id = ? "
                + "  and ct.deleted_at is null "
                + "  and ct.birth_date is not null "
                + "  and extract(month from ct.birth_date) = ? "
                + "  and extract(day from ct.birth_date) = ? "
                + "  and (ct.academia_birthday_greeted_year is null or ct.academia_birthday_greeted_year <> ?)",
            BIRTHDAY_MAPPER, companyId, month, day, currentYear);
    }

    /** Marca o ano em que o contato foi saudado (idempotência: 1 saudação por ano). */
    public void markGreeted(UUID contactId, int year) {
        jdbcTemplate.update(
            "update contacts set academia_birthday_greeted_year = ? where id = ?",
            year, contactId);
    }
}
