package com.meada.webchat;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Acessos pontuais do canal web (camada 5.25 #73) que não cabem nos repositórios de
 * domínio existentes: resolver a empresa ATIVA por slug e descobrir uma whatsapp_instance
 * qualquer da empresa (portadora obrigatória da FK NOT NULL conversations.whatsapp_instance_id
 * — ver javadoc do {@link WebChatService}).
 *
 * <p>Opera como service_role (o endpoint web é público, fora do RLS): o WebChatController
 * vive sob {@code /api/}, não {@code /admin/}, então o JwtAuthenticationFilter não o toca.
 */
@Repository
public class WebChatRepository {

    private final JdbcTemplate jdbcTemplate;

    public WebChatRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Resolve o id de uma empresa ATIVA pelo slug. {@link Optional#empty()} se o slug não
     * existe OU a empresa não está ativa (status != 'active') — em ambos o controller
     * devolve 404 (não distingue: não vazar se o slug existe mas está suspenso).
     *
     * @return o companyId da empresa ativa, ou empty.
     */
    public Optional<UUID> findActiveCompanyIdBySlug(String slug) {
        Objects.requireNonNull(slug, "slug must not be null");
        return jdbcTemplate.query(
                "select id from companies where slug = ? and status = 'active'",
                (rs, rowNum) -> (UUID) rs.getObject("id"), slug)
            .stream()
            .findFirst();
    }

    /**
     * Devolve o id de UMA whatsapp_instance da empresa (a mais antiga, determinística) — usada
     * como portadora da FK NOT NULL da conversa web. {@link Optional#empty()} se a empresa não
     * tem nenhuma instância (o serviço lança {@link WebChatNoInstanceException}).
     *
     * @return o id de uma instância da empresa, ou empty se não há nenhuma.
     */
    public Optional<UUID> findAnyInstanceId(UUID companyId) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        return jdbcTemplate.query(
                "select id from whatsapp_instances where company_id = ? order by created_at asc limit 1",
                (rs, rowNum) -> (UUID) rs.getObject("id"), companyId)
            .stream()
            .findFirst();
    }
}
