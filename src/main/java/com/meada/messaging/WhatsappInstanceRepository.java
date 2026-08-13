package com.meada.messaging;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso de leitura a {@code whatsapp_instances}. Resolve o {@code instance_name}
 * do webhook para o tenant ({@code company_id}) — primeira etapa do fluxo: sem
 * resolver a instância, nenhuma escrita pode acontecer (FK aponta para company_id).
 *
 * <p>Read-only por design: instâncias são provisionadas via service_role fora do
 * webhook (decisão do schema). Este repositório só faz SELECT.
 */
@Repository
public class WhatsappInstanceRepository {

    /** SELECT com colunas explícitas — nunca SELECT *. O domínio WhatsappInstance
     * NÃO carrega evolution_token: instance_name + token só são lidos juntos pelo
     * método dedicado findEvolutionCredentials (defesa em profundidade — ver javadoc lá). */
    private static final String FIND_BY_NAME =
        "select id, company_id from whatsapp_instances where instance_name = ?";

    private static final String FIND_CREDENTIALS_BY_ID =
        "select instance_name, evolution_token from whatsapp_instances where id = ?";

    private static final RowMapper<WhatsappInstance> ROW_MAPPER = (rs, rowNum) ->
        new WhatsappInstance(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("company_id"));

    private final JdbcTemplate jdbcTemplate;

    public WhatsappInstanceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Resolve uma instância pelo nome.
     *
     * @return a instância, ou {@link Optional#empty()} se nenhuma tiver esse nome
     *         (instância desconhecida → o serviço responde 200 + log warning, por
     *         decisão de contrato: reentregar não resolve config ausente).
     */
    public Optional<WhatsappInstance> findByInstanceName(String instanceName) {
        Objects.requireNonNull(instanceName, "instanceName must not be null");
        // query() retorna lista vazia quando não há linha — sem exceção de
        // "não encontrado" (evita EmptyResultDataAccessException do queryForObject).
        return jdbcTemplate.query(FIND_BY_NAME, ROW_MAPPER, instanceName)
            .stream()
            .findFirst();
    }

    /**
     * Lê as credenciais de envio de uma instância — {@code (instance_name, token)} —
     * numa query só, para o envio outbound. A Evolution exige ambos: o nome no path
     * da URL ({@code /message/sendText/{instance}}) e o token no header {@code apikey}.
     *
     * <p>DEFESA EM PROFUNDIDADE: o token (segredo) NÃO circula no domínio
     * {@link WhatsappInstance} (que é enxuto: id, companyId) — só passa por este
     * método dedicado, no único caminho que precisa dele (o OutboundService, ao
     * chamar a Evolution). Assim o segredo não vaza para o fluxo inbound nem para
     * qualquer consumidor de WhatsappInstance que não tenha motivo de vê-lo.
     * service_role pode ler (o column-grant do schema só bloqueia authenticated).
     *
     * @return as credenciais, ou {@link Optional#empty()} se a instância não existe
     *         (ex.: deletada entre o evento e o processamento async).
     */
    public Optional<EvolutionCredentials> findEvolutionCredentials(UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId must not be null");
        return jdbcTemplate.query(
                FIND_CREDENTIALS_BY_ID,
                (rs, rowNum) -> new EvolutionCredentials(
                    rs.getString("instance_name"), rs.getString("evolution_token")),
                instanceId)
            .stream()
            .findFirst();
    }
}
