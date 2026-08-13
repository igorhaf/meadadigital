package com.meada.messaging;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code contacts}. Resolve ou cria o contato (cliente final) de uma
 * mensagem inbound, de forma idempotente sob concorrência.
 */
@Repository
public class ContactRepository {

    private static final RowMapper<Contact> ROW_MAPPER = (rs, rowNum) ->
        new Contact(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("company_id"),
            rs.getString("phone_number"),
            rs.getString("name"),
            rs.getBoolean("blocked"));

    // Caminho quente: contato recorrente já existe (a maioria das mensagens).
    private static final String SELECT_ACTIVE =
        "select id, company_id, phone_number, name, blocked from contacts "
            + "where company_id = ? and phone_number = ? and deleted_at is null";

    // Upsert. ON CONFLICT repete o predicado parcial (WHERE deleted_at IS NULL)
    // para o Postgres reconhecer uq_contacts_company_phone_active como arbiter.
    // DO UPDATE com WHERE name IS NULL: preenche a lacuna de nome sem sobrescrever
    // um nome já conhecido. RETURNING:
    //   - INSERT novo → retorna a linha criada;
    //   - conflito + name era NULL → o UPDATE casa, retorna a linha atualizada;
    //   - conflito + name já preenchido → o WHERE do UPDATE não casa, RETURNING
    //     vem VAZIO (cai no reselect).
    // (Só é alcançado quando o contato NÃO existia no SELECT do passo 1.)
    private static final String UPSERT =
        "insert into contacts (company_id, phone_number, name) values (?, ?, ?) "
            + "on conflict (company_id, phone_number) where deleted_at is null "
            + "do update set name = excluded.name where contacts.name is null "
            + "returning id, company_id, phone_number, name, blocked";

    // Preenche o nome de um contato JÁ EXISTENTE cujo name estava null (lacuna),
    // detectado no caminho quente (passo 1). WHERE name IS NULL é proteção contra
    // race: se outra thread preencheu entre o SELECT e este UPDATE, não casa →
    // RETURNING vazio → reselect.
    private static final String FILL_NAME =
        "update contacts set name = ? where id = ? and name is null "
            + "returning id, company_id, phone_number, name, blocked";

    private final JdbcTemplate jdbcTemplate;

    public ContactRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Resolve o contato ativo de {@code (companyId, phoneNumber)}, criando-o se
     * não existir. Idempotente: chamadas concorrentes convergem para o mesmo
     * contato. O {@code name} (pushName do WhatsApp) só preenche a lacuna quando
     * o contato existente ainda não tem nome — nunca sobrescreve.
     *
     * <p>Padrão select-then-upsert-then-reselect (otimiza o caminho quente:
     * contato recorrente sai no passo 1, sem INSERT):
     * <ol>
     *   <li>SELECT ativo — se achou, retorna (caminho dominante).
     *   <li>UPSERT — se RETURNING traz linha (novo, ou nome preenchido agora), retorna.
     *   <li>Reselect — RETURNING vazio significa que o contato existe com nome já
     *       preenchido OU outra thread o criou entre 1 e 2; em ambos, reselect resolve.
     * </ol>
     *
     * @return o contato persistido (o {@code name} reflete o estado do banco).
     */
    public Contact resolveOrCreate(UUID companyId, String phoneNumber, String name) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(phoneNumber, "phoneNumber must not be null");

        // 1. caminho quente: contato recorrente já existe.
        Optional<Contact> existing = selectActive(companyId, phoneNumber);
        if (existing.isPresent()) {
            Contact contact = existing.get();
            // Preenche a lacuna de nome SEM sobrescrever: contato existe sem name
            // e agora chegou um pushName. Se já tem name, retorna como está (puro
            // caminho quente — só o SELECT). O SELECT-primeiro retornaria o name
            // null sem o upsert nunca rodar, por isso o preenchimento é explícito aqui.
            if (contact.name() == null && name != null) {
                List<Contact> filled = jdbcTemplate.query(FILL_NAME, ROW_MAPPER, name, contact.id());
                if (!filled.isEmpty()) {
                    return filled.get(0);
                }
                // Race: outra thread preencheu name entre o SELECT e o UPDATE. Reselect.
                return selectActive(companyId, phoneNumber)
                    .orElseThrow(() -> new IllegalStateException(
                        "Contact disappeared during fillName for company=" + companyId
                            + " phone=" + phoneNumber));
            }
            return contact;
        }

        // 2. upsert
        List<Contact> upserted = jdbcTemplate.query(UPSERT, ROW_MAPPER, companyId, phoneNumber, name);
        if (!upserted.isEmpty()) {
            return upserted.get(0);
        }

        // 3. reselect (race genuína, ou conflito com nome já preenchido)
        return selectActive(companyId, phoneNumber)
            .orElseThrow(() -> new IllegalStateException(
                "Contact disappeared after ON CONFLICT for company=" + companyId
                    + " phone=" + phoneNumber));
    }

    private Optional<Contact> selectActive(UUID companyId, String phoneNumber) {
        return jdbcTemplate.query(SELECT_ACTIVE, ROW_MAPPER, companyId, phoneNumber)
            .stream()
            .findFirst();
    }

    // Telefone do contato de uma conversa (JOIN contacts ← conversations pelo
    // contact_id). Lookup por id de conversa — não há defesa em profundidade aqui
    // (phone não é segredo); é só a forma de obter o destinatário do envio outbound.
    private static final String FIND_PHONE_BY_CONVERSATION =
        "select c.phone_number from contacts c "
            + "join conversations cv on cv.contact_id = c.id "
            + "where cv.id = ?";

    /**
     * Telefone (E.164) do contato dono de uma conversa — para o envio outbound da
     * Evolution, que exige o {@code number} do destinatário.
     *
     * <p>Lido do banco (não carregado no evento) porque o phone é PROJEÇÃO ESTÁVEL:
     * não muda entre a publicação do evento e o processamento async, então reler é
     * seguro. (Diferente do userMessage, que é identidade do disparo e vai no evento.)
     *
     * @return o telefone, ou {@link Optional#empty()} se a conversa/contato não existe.
     */
    public Optional<String> findPhoneByConversationId(UUID conversationId) {
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        return jdbcTemplate.query(FIND_PHONE_BY_CONVERSATION,
                (rs, rowNum) -> rs.getString("phone_number"), conversationId)
            .stream()
            .findFirst();
    }

    private static final String FIND_NAME_BY_CONVERSATION =
        "select c.name from contacts c "
            + "join conversations cv on cv.contact_id = c.id "
            + "where cv.id = ?";

    /**
     * Nome do contato dono de uma conversa — usado pelo perfil restaurant (7.3) como guest_name
     * snapshot da reserva criada pela IA. {@link Optional#empty()} se a conversa/contato não existe;
     * o nome pode vir null/vazio (o caller decide o fallback).
     */
    public Optional<String> findNameByConversationId(UUID conversationId) {
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        return jdbcTemplate.query(FIND_NAME_BY_CONVERSATION,
                (rs, rowNum) -> rs.getString("name"), conversationId)
            .stream()
            .findFirst();
    }

    /**
     * Grava a memória de longo prazo do contato em {@code contact_memory} (camada 5.18 #55).
     * Objeto livre (preferências, fatos persistentes). {@code ?::jsonb} casta a String JSON
     * serializada no Java. updated_at = now() por coerência. Chamado pelo OutboundService só
     * quando o AiResponse traz memory_update (a maioria das mensagens não traz).
     *
     * @param contactId  contato a atualizar (resolvido via ConversationRepository)
     * @param memoryJsonb JSON da memória já serializado (não-null; o caller garante)
     */
    public void updateMemory(UUID contactId, String memoryJsonb) {
        Objects.requireNonNull(contactId, "contactId must not be null");
        Objects.requireNonNull(memoryJsonb, "memoryJsonb must not be null");
        jdbcTemplate.update(
            "update contacts set contact_memory = ?::jsonb, updated_at = now() where id = ?",
            memoryJsonb, contactId);
    }

    /**
     * Grava o tom detectado do contato em {@code detected_tone} (camada 5.18 #58):
     * formal|informal|neutro|irritado (CHECK no schema). Chamado pelo OutboundService só
     * quando o AiResponse traz detected_tone (tipicamente só na 1ª interação).
     *
     * @param contactId contato a atualizar
     * @param tone      tom detectado (não-null; o caller garante e o CHECK do banco revalida)
     */
    public void updateDetectedTone(UUID contactId, String tone) {
        Objects.requireNonNull(contactId, "contactId must not be null");
        Objects.requireNonNull(tone, "tone must not be null");
        jdbcTemplate.update(
            "update contacts set detected_tone = ?, updated_at = now() where id = ?",
            tone, contactId);
    }

    /**
     * Mescla/define os canais do contato em {@code channels} (jsonb) — unificação multi-canal
     * (camada 5.25 #74). Um mesmo contato pode existir em vários canais (whatsapp/web/email); a
     * coluna agrega o identificador por canal (ex.: {@code {"whatsapp": "+5511...", "web": "sess-abc"}}).
     *
     * <p>Usa o operador {@code ||} de jsonb (merge raso): o novo objeto sobrescreve só as chaves
     * que traz, preservando os demais canais já gravados. {@code coalesce(channels, '{}')} cobre o
     * caso da 1ª gravação (coluna ainda NULL). {@code ?::jsonb} casta a String JSON do Java. Chamado
     * na criação/resolução do contato web; o caller garante o JSON bem-formado.
     *
     * @param contactId     contato a atualizar
     * @param channelsJsonb objeto JSON do(s) canal(is) a mesclar (não-null; o caller garante)
     */
    public void updateChannels(UUID contactId, String channelsJsonb) {
        Objects.requireNonNull(contactId, "contactId must not be null");
        Objects.requireNonNull(channelsJsonb, "channelsJsonb must not be null");
        jdbcTemplate.update(
            "update contacts set channels = coalesce(channels, '{}'::jsonb) || ?::jsonb, "
                + "updated_at = now() where id = ?",
            channelsJsonb, contactId);
    }

    // contact_memory / detected_tone do contato dono de uma conversa (JOIN contacts ←
    // conversations) — lidos pelo PromptBuilder para injetar a memória e ajustar o tom.
    // contact_memory::text porque o PromptBuilder consome o jsonb como string crua.
    private static final String FIND_MEMORY_BY_CONVERSATION =
        "select c.contact_memory::text as contact_memory from contacts c "
            + "join conversations cv on cv.contact_id = c.id "
            + "where cv.id = ?";

    private static final String FIND_TONE_BY_CONVERSATION =
        "select c.detected_tone from contacts c "
            + "join conversations cv on cv.contact_id = c.id "
            + "where cv.id = ?";

    /**
     * Memória de longo prazo (jsonb como texto) do contato dono de uma conversa — o
     * PromptBuilder injeta no contexto da IA. {@link Optional#empty()} quando a conversa/
     * contato não existe; o valor pode ser null (coluna vazia) quando ainda não há memória.
     *
     * @return o contact_memory como String JSON, ou empty se não há linha.
     */
    public Optional<String> findMemoryByConversation(UUID conversationId) {
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        return jdbcTemplate.query(FIND_MEMORY_BY_CONVERSATION,
                (rs, rowNum) -> rs.getString("contact_memory"), conversationId)
            .stream()
            .filter(Objects::nonNull)   // Stream.findFirst NPEia em elemento null (coluna vazia)
            .findFirst();
    }

    /**
     * Tom detectado do contato dono de uma conversa — o PromptBuilder usa para ajustar o
     * estilo da resposta. {@link Optional#empty()} quando não há linha; o valor pode ser null
     * quando o tom ainda não foi classificado.
     *
     * @return o detected_tone, ou empty se não há linha.
     */
    public Optional<String> findDetectedToneByConversation(UUID conversationId) {
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        return jdbcTemplate.query(FIND_TONE_BY_CONVERSATION,
                (rs, rowNum) -> rs.getString("detected_tone"), conversationId)
            .stream()
            .filter(Objects::nonNull)   // Stream.findFirst NPEia em elemento null (coluna vazia)
            .findFirst();
    }
}
