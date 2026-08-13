package com.meada.admin.instances;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Escrita/leitura administrativa de {@code whatsapp_instances} — o provisionamento que o schema
 * sempre previu mas nunca foi implementado (o comentário da RLS em {@code 03_rls.sql:110} diz:
 * "a instância nasce quando o Spring conecta na Evolution e recebe o evolution_token").
 *
 * <p>Separado do {@link com.meada.messaging.WhatsappInstanceRepository} de propósito: aquele é o
 * caminho QUENTE (webhook/outbound), read-only por design e com o token blindado. Este é o caminho
 * ADMINISTRATIVO (1 request humano por vez). Opera via service_role.
 *
 * <p><b>Não existe DELETE aqui:</b> {@code conversations.whatsapp_instance_id} tem FK
 * {@code on delete restrict} ({@code 02_tables.sql:254}) — apagar a instância de um tenant com
 * histórico falharia. "Desconectar" = logout na Evolution + {@code status='disconnected'}, a linha
 * permanece (o histórico de conversas continua íntegro). O purge de empresa é o único DELETE, e vive
 * no {@code CompanyAdminService}.
 */
@Repository
public class WhatsappInstanceAdminRepository {

    /** Linha administrativa: sem o evolution_token (segredo não circula onde não é preciso). */
    public record InstanceRow(UUID id, String instanceName, String phoneNumber, String status) {}

    private final JdbcTemplate jdbcTemplate;

    public WhatsappInstanceAdminRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Slug da empresa — base do {@code instance_name} na Evolution (legível no manager). */
    public Optional<String> findCompanySlug(UUID companyId) {
        return jdbcTemplate.query("select slug from companies where id = ?",
                (rs, rn) -> rs.getString("slug"), companyId)
            .stream().findFirst();
    }

    /** A instância do tenant (1 por empresa nesta fase). */
    public Optional<InstanceRow> findByCompany(UUID companyId) {
        return jdbcTemplate.query(
                "select id, instance_name, phone_number, status from whatsapp_instances "
                    + "where company_id = ? order by created_at asc limit 1",
                (rs, rn) -> new InstanceRow(
                    (UUID) rs.getObject("id"),
                    rs.getString("instance_name"),
                    rs.getString("phone_number"),
                    rs.getString("status")),
                companyId)
            .stream().findFirst();
    }

    /** Cria a linha da instância recém-provisionada na Evolution. Nasce 'connecting' (aguardando QR). */
    public UUID insert(UUID companyId, String instanceName, String evolutionToken) {
        return jdbcTemplate.queryForObject(
            "insert into whatsapp_instances (company_id, instance_name, evolution_token, status) "
                + "values (?, ?, ?, 'connecting') returning id",
            UUID.class, companyId, instanceName, evolutionToken);
    }

    /** Atualiza o token (re-criação da instância na Evolution devolve um hash novo). */
    public void updateToken(UUID id, String evolutionToken) {
        jdbcTemplate.update(
            "update whatsapp_instances set evolution_token = ?, updated_at = now() where id = ?",
            evolutionToken, id);
    }

    /**
     * Materializa o estado vindo da Evolution: status + número conectado.
     * O {@code phoneNumber} é a FONTE DA VERDADE do pareamento (vem do {@code ownerJid}) — nunca
     * um input livre do tenant.
     */
    public void updateStatusAndNumber(UUID id, String status, String phoneNumber) {
        jdbcTemplate.update(
            "update whatsapp_instances set status = ?, phone_number = coalesce(?, phone_number), "
                + "updated_at = now() where id = ?",
            status, phoneNumber, id);
    }
}
