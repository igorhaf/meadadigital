package com.meada.engagement;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Acesso de leitura/marcação para a reativação automática (camada 5.21 #81).
 *
 * <p>Encapsula a query dos contatos ELEGÍVEIS para reativação de uma empresa e o UPDATE
 * de {@code contacts.reactivated_at} que faz o gate de disparo único por janela. O
 * {@link ReactivationJob} orquestra; aqui mora só o SQL.
 */
@Repository
public class ReactivationRepository {

    private static final RowMapper<DueContact> ROW_MAPPER = (rs, rowNum) ->
        new DueContact(
            (UUID) rs.getObject("contact_id"),
            rs.getString("phone_number"),
            (UUID) rs.getObject("conversation_id"));

    // Contatos elegíveis de UMA empresa: sem mensagem há >= ? dias E não reativados nesta
    // janela. Mecânica:
    //   - last_activity = max(messages.created_at) entre TODAS as conversas do contato. Um
    //     contato sem nenhuma mensagem não aparece (inner join via existência de atividade);
    //   - filtro de inatividade: last_activity < now() - (days || ' days')::interval;
    //   - filtro de disparo único: reactivated_at IS NULL OU reactivated_at < last_activity
    //     (se o contato voltou a falar depois do último reativar, reativa de novo numa nova
    //     janela de silêncio — reactivated_at fica "para trás" da nova atividade);
    //   - exclui blocked e deleted (não reengaja quem foi bloqueado/removido);
    //   - conversation_id = a conversa MAIS RECENTE do contato (por max created_at da conversa),
    //     para o job resolver a instância/credenciais por onde mandar.
    // CTE last_msg agrega a atividade por contato; CTE recent_conv pega a conversa de maior
    // atividade. LEFT JOIN em recent_conv para nunca sumir um contato due por falta de conversa
    // resolúvel (conversation_id pode vir null — o job marca sem enviar nesse caso).
    private static final String FIND_DUE =
        "with last_msg as ("
            + "  select cv.contact_id, max(m.created_at) as last_activity "
            + "  from messages m join conversations cv on cv.id = m.conversation_id "
            + "  where cv.company_id = ? "
            + "  group by cv.contact_id"
            + "), conv_activity as ("
            + "  select cv.contact_id, cv.id as conversation_id, max(m.created_at) as conv_last "
            + "  from conversations cv "
            + "  join messages m on m.conversation_id = cv.id "
            + "  where cv.company_id = ? "
            + "  group by cv.contact_id, cv.id"
            + "), recent_conv as ("
            + "  select distinct on (contact_id) contact_id, conversation_id "
            + "  from conv_activity "
            + "  order by contact_id, conv_last desc, conversation_id desc"
            + ") "
            + "select c.id as contact_id, c.phone_number, rc.conversation_id "
            + "from contacts c "
            + "join last_msg lm on lm.contact_id = c.id "
            + "left join recent_conv rc on rc.contact_id = c.id "
            + "where c.company_id = ? "
            + "  and c.deleted_at is null "
            + "  and c.blocked = false "
            + "  and lm.last_activity < now() - (? || ' days')::interval "
            + "  and (c.reactivated_at is null or c.reactivated_at < lm.last_activity)";

    private static final String MARK_REACTIVATED =
        "update contacts set reactivated_at = now(), updated_at = now() where id = ?";

    private final JdbcTemplate jdbcTemplate;

    public ReactivationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Contatos de {@code companyId} elegíveis para reativação: sem mensagem há
     * {@code days} dias ou mais e ainda não reativados nesta janela de silêncio.
     *
     * @param companyId empresa a varrer
     * @param days      limiar de inatividade (ai_settings.reactivation_days)
     * @return lista de contatos due (pode ser vazia)
     */
    public List<DueContact> findDue(UUID companyId, int days) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        // companyId aparece 3x (2 CTEs + query externa); days 1x. Ordem posicional dos '?'.
        return jdbcTemplate.query(FIND_DUE, ROW_MAPPER, companyId, companyId, companyId, days);
    }

    /**
     * Marca o contato como reativado AGORA — gate de disparo único por janela. Chamado pelo
     * {@link ReactivationJob} após (tentar) enviar a mensagem de reativação.
     *
     * @param contactId contato a marcar
     */
    public void markReactivated(UUID contactId) {
        Objects.requireNonNull(contactId, "contactId must not be null");
        jdbcTemplate.update(MARK_REACTIVATED, contactId);
    }
}
