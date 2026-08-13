# 03 — IA e Fluxo de Mensagens

[← Home](00-HOME.md)

## Visão geral do ciclo

```
Cliente (WhatsApp) → Evolution API → POST /webhooks/evolution
   → WebhookSecretFilter (secret constant-time) → WebhookService (guards + persiste inbound)
   → publica MessageInboundProcessedEvent DENTRO da transação
       → OutboundEventListener (@Async "outboundExecutor" + AFTER_COMMIT)
       → OutboundService:
            ├─ gate handled_by='ai' (humano assumiu → SKIPPED_NOT_AI, IA não roda)
            ├─ BusinessHoursGate ANTES da IA (fora do horário → resposta padrão, sem custo Gemini)
            ├─ PromptBuilder (persona do perfil + contexto do tenant + RAG + memória do contato)
            ├─ GeminiProvider.generate com retry (responseSchema: reply + needs_human + reason + insights)
            ├─ boas-vindas (1ª mensagem) + persiste scheduling_intent/insights
            ├─ cadeia de handlers de tag por perfil (cria pedido/agendamento/etc., remove a tag)
            ├─ stripLeftoverTags (rede de segurança: tag não interpretada nunca vaza)
            └─ EvolutionSender.sendText (retry) → cliente recebe a resposta
       → persiste a mensagem outbound (idempotente por evolution_message_id, com tokens/modelo)
```

O `200 OK` do webhook é devolvido **imediatamente** (a IA + outbound rodam em listener assíncrono
`@TransactionalEventListener(AFTER_COMMIT)` — só depois que a inbound está durável), para a
Evolution não reenviar.

## Inbound — `webhook/`

`EvolutionWebhookController` (`POST /webhooks/evolution`) → `WebhookService.process` (transacional).
Filtros e guardas, em ordem (cada um pode encerrar com um motivo `IGNORED_*`, sempre devolvendo 200):

1. **Secret:** `WebhookSecretFilter` valida `apikey` (header preferencial; query `?apikey=` como
   fallback) em tempo constante (`MessageDigest.isEqual`). Inválido → **401** `invalid_secret`.
2. `event != messages.upsert` → `IGNORED_NON_MESSAGE_EVENT`.
3. `fromMe = true` **OU null** → `IGNORED_FROM_ME` (defensivo: sem sinal, assume eco da própria
   instância, para a IA não responder a si mesma).
4. Instância desconhecida → `IGNORED_UNKNOWN_INSTANCE` (corta cedo, antes de normalizar o JID).
5. JID de grupo/broadcast/desconhecido → `IGNORED_GROUP` / `IGNORED_BROADCAST` / `IGNORED_UNKNOWN_JID`.
6. Sem texto → `IGNORED_NON_TEXT`.
7. **Guard de frescor:** `messageTimestamp` mais velho que `webhook.message-max-age-seconds`
   (env `WEBHOOK_MESSAGE_MAX_AGE_SECONDS`, default 180s) → `IGNORED_STALE`. Timestamp `null` NÃO
   rejeita (defensivo). Protege contra o re-sync de histórico do Baileys/Evolution
   no boot (incidente registrado em RISKS.md — re-sync disparou respostas a contatos reais).
8. **Persistência (transacional):** `ContactRepository.resolveOrCreate(company, phone, pushName)` →
   `ConversationRepository.resolveOpenOrCreate(company, contact, instance)` →
   `MessageRepository.insertIfNew(direction=inbound, sender=contact, evolution_message_id)`.
   **Reentrega da Evolution** (mesmo `evolution_message_id`) → `IGNORED_DUPLICATE` (idempotente).
   Depois, `touchLastMessageAt` na conversa.
9. **Gate de bloqueio de contato:** se o tenant bloqueou o contato, a mensagem **já foi persistida**
   (histórico íntegro), mas o evento NÃO é publicado → `IGNORED_CONTACT_BLOCKED` (sem resposta
   automática).
10. Publica `MessageInboundProcessedEvent` **dentro da transação** — o `OutboundEventListener`
    (`@Async("outboundExecutor")` + `@TransactionalEventListener(AFTER_COMMIT)`) só processa após o
    commit. Só no ramo `PROCESSED`; nenhum `IGNORED_*` dispara IA.

Heartbeat best-effort em `webhook_heartbeats` (try/catch silencioso, nunca bloqueia).

> **Estado do webhook:** desligado por padrão até religar consciente (mitigação do incidente de
> re-sync). Em dev usa-se `EVOLUTION_DRY_RUN=true` (loga em vez de enviar).

## Montagem do prompt — `ai/`

`PromptBuilder` preenche `src/main/resources/prompts/system-template.txt` com o contexto do tenant
(placeholders em chave dupla `{{...}}`):

| Placeholder | Origem |
|-------------|--------|
| `{{tone}}` / `{{rules}}` / `{{restrictions}}` / `{{handoff}}` | `ai_settings` (1:1 por company). |
| `{{services}}` | catálogo `services` (soft delete respeitado). |
| `{{faqs}}` | `faqs`. |
| `{{businessHours}}` | `business_hours` (janelas por dia). |
| `{{knowledge}}` | top-K chunks do RAG (ver abaixo); best-effort — falha de retrieval loga warn e o prompt segue sem documentos. |
| `{{contactMemory}}` | memória do contato (#55) — fatos persistidos via `memory_update` das respostas anteriores da IA. |

O **segmento do perfil** (persona + contexto dinâmico — cardápio do sushi, processos do cliente
legal, agenda do dental, etc.) **não é placeholder**: é **PREPENDADO** ao template
(`profileSegment + template`), via `ProfilePromptContext.segmentFor(profileId, companyId,
conversationId)`.

Seção vazia vira `""` (fallback seguro — o prompt genérico cobre o caso). A persona de cada perfil
e o contexto dinâmico estão detalhados em [04 — Multi-perfil](04-multiperfil-chassis.md).

## Chamada ao Gemini — `ai/GeminiProvider`

- `POST` para a API do Gemini com `system_instruction` + `contents` (history alternado + a mensagem
  do usuário) + `responseSchema`.
- **`responseSchema`** em JSON mode — obrigatórios `reply` + `needs_human`; opcionais `reason`
  (motivo do handoff), `scheduling_intent` (#29), `cancellation_intent` (#51), `complaint_intent`
  (#52), `extracted_data` (#53), `memory_update` (#55), `detected_tone` (#58) e
  `appointment_action` (#60/#64 — `book|reschedule|cancel`). (JSON mode é mutuamente exclusivo
  com tool-calling no Gemini — por isso os perfis usam **tags em texto livre**, não function
  calling.)
- `AiResponse` = `reply + needsHuman + reason + tokensIn/tokensOut + latencyMs + schedulingIntent
  + insights (AiInsights)`. As **tags** dentro do `reply` é que disparam a criação de artefatos;
  os insights são persistidos best-effort (intent de agendamento na conversa, memória/tom no
  contato; `appointment_action` é aplicada via `AppointmentService.applyAppointmentAction`, que
  revalida janela/conflito no backend).
- **Retry com backoff:** a chamada roda via `RetryRunner` (`outbound.retry`: max-attempts 3,
  backoff 1s/3s). Só `AiTransientException` é retentável; esgotou/fatal → conversa flipa para
  humano (`FLIPPED_AI_EXHAUSTED`).

## RAG / Base de conhecimento — `knowledge/`

- **Ingestão:** `POST /admin/knowledge/documents` (PDF/TXT) → extrai texto → **chunking**
  (`chunk-size-chars` 800, overlap 100, máx. 500k chars/documento) → **embeddings** (sidecar
  Python, modelo `multilingual-e5-small`) → grava `knowledge_documents` + `knowledge_chunks`
  (com `embedding vector`).
- **Retrieval:** o `PromptBuilder` embeda a pergunta do cliente (kind `query`) e busca os **top-5**
  chunks (`MATCH_COUNT = 5`) por **cosine distance** (`embedding <=> ?` no pgvector; similaridade
  = 1 − distância), filtrando por `company_id` e threshold **0.65** (`MATCH_THRESHOLD`). Os chunks
  entram em `{{knowledge}}`; nada acima do threshold → prompt segue sem contexto de documento.
- Sidecar: `POST /embed {"texts": [...], "kind": "passage"|"query"}` → `{vectors, model, dim}`
  (porta 7080; config `knowledge.embedding-endpoint` / env `KNOWLEDGE_EMBEDDING_ENDPOINT`). O
  código do sidecar vive no repo `shared` (`~/shared/python`), buildado pelo compose.

## Outbound — `outbound/`

`OutboundService` (o maior arquivo do projeto — ~2.600 linhas) orquestra o pipeline. Ordem real:

1. **Gate `handled_by`** — se a conversa não está com a IA (`handled_by != 'ai'`, um humano
   assumiu), nada roda → `SKIPPED_NOT_AI`.
2. **`BusinessHoursGate` ANTES da IA** — decisão pura e determinística (fuso
   `America/Sao_Paulo`): fora do horário, responde a **mensagem padrão de fora-de-horário** sem
   custo de Gemini (`PROCESSED_OUTSIDE_HOURS`; a conversa segue `handled_by='ai'`). **Fallback
   ABERTO:** tenant sem horários configurados = IA responde a qualquer hora. Limitação conhecida:
   janela que atravessa a meia-noite (ex.: 22:00→02:00) NÃO é suportada (`opens < closes`).
3. **Chamada à IA com retry** (ver seção Gemini). Falha esgotada/fatal → flip para humano
   (`FLIPPED_AI_EXHAUSTED`).
4. **Branching por `needsHuman`:** com reply → envia a resposta-ponte (tags são **removidas sem
   agir** — a ação fica com o humano) e flipa (`FLIPPED_AI_HANDOFF`); sem reply → flipa direto.
   `needsHuman=false` sem reply é contrato quebrado → flipa (`FLIPPED_AI_BAD_REPLY`).
5. **Boas-vindas (#82)** — na PRIMEIRA mensagem do contato em todo o histórico, envia
   `ai_settings.welcome_message` ANTES da resposta da IA (best-effort, nunca degrada o
   atendimento). Depois persiste `scheduling_intent` (#29) e insights (5.18).
6. **Cadeia de handlers de tag por perfil** — **64 chamadas `maybeProcessX` encadeadas** cobrindo
   32 dos 33 nichos (legal é a exceção: a IA não emite tag nenhuma e não há `maybeProcess*` para
   ele — toda escrita é do advogado no painel; o perfil do tenant é único, só um age; cada nicho
   pode ter várias tags — pedido, confirmação, aprovação, entrega, aviso de estoque...). Cada handler:
   - detecta a tag por regex (ex.: `<pedido>{...}</pedido>`),
   - parseia o JSON, **recalcula valores no backend** (descarta o que a IA chutou),
   - cria o artefato (pedido/agendamento/proposta/...) best-effort,
   - e o texto segue **sem a tag** para o cliente.
   Exemplos: `maybeProcessSushiOrder` (sushi), `maybeProcessRestaurantReservation` (restaurant),
   `maybeProcessPropostaEvento` + `maybeProcessAprovacaoProposta` (eventos),
   `maybeProcessEntregaPlano` (nutri, entrega read-only). Ver o catálogo em [05 — Nichos](05-nichos.md).
7. **`stripLeftoverTags` (rede de segurança)** — tag que NENHUM handler interpretou (perfil
   errado/alucinada) é removida sem agir; se o reply era SÓ a tag, flipa para humano
   (`FLIPPED_AI_BAD_REPLY`). O cliente nunca vê JSON cru.
8. **Envio (`sendAndPersist`):** valida telefone do contato e credenciais da instância (faltando →
   `EVOLUTION_CONFIG_ERROR`, sem flip); `EvolutionSender.sendText(instance, token, phone, text)` —
   `POST` síncrono à Evolution com o `evolution_token` per-instance — com **retry** (`RetryRunner`,
   max-attempts 3, backoff 1s/3s). Transient (429/5xx/timeout) esgotado → flip
   (`FLIPPED_EVOLUTION_EXHAUSTED`); fatal (4xx/parse) → `EVOLUTION_CONFIG_ERROR` + registro no
   `error_log` do super-admin, SEM flip (humano falharia igual).
9. **Persistência:** insere a mensagem outbound **após** confirmação da Evolution, via
   `insertIfNew` — idempotente por `evolution_message_id` (o `key.id` devolvido pela Evolution).
   Grava `tokens_in/tokens_out/model` quando houve IA real; mensagens sintéticas (boas-vindas,
   fora-de-horário) gravam NULL (≠ custo zero).

> **Dry-run (dev):** `EVOLUTION_DRY_RUN=true` (`evolution.dry-run`) suprime o HTTP no
> `EvolutionClient` — loga e retorna id fake `dry-run-<uuid>`. Proteção contra envio acidental a
> contatos reais (RISKS.md).

### Entrega read-only (padrão importante)

Quatro perfis (nutri, dermatologia, fotografia, cursos) têm um modo de **entrega de conteúdo
gravado pelo profissional** (plano alimentar, instruções de preparo, link de material, conteúdo de
módulo). Nesses casos a tag de entrega faz o handler enviar o texto **VERBATIM** via
`notifier.sendText()` — **fora da geração da IA**, para o conteúdo não ser reescrito — com
**barreira de contato** (só entrega ao contato dono daquele dado). Ver [05 — Nichos](05-nichos.md).

## Canais

- **WhatsApp** (principal) — via Evolution.
- **Webchat** (`webchat/`) — widget web embeddable (`POST /api/chat/{companySlug}`, público, fora
  do filtro JWT): o cliente preenche nome/contato, vira um `contact` web com conversa isolada,
  mesma engine de IA.

## Treino / feedback — `training/`

`POST /admin/message-feedback` registra a avaliação dos agentes humanos sobre respostas da IA
(rating + correção), em `ai_message_feedback`, para análise futura (`GET /admin/message-feedback`
lista).

## Referências de arquivo

- `webhook/EvolutionWebhookController.java`, `webhook/WebhookService.java`, `webhook/WebhookSecretFilter.java`
- `ai/PromptBuilder.java`, `ai/GeminiProvider.java`, `src/main/resources/prompts/system-template.txt`
- `knowledge/KnowledgeRetrievalService.java`, `knowledge/EmbeddingProvider.java`
- `outbound/OutboundService.java`, `outbound/OutboundEventListener.java`, `outbound/EvolutionSender.java`,
  `outbound/EvolutionClient.java`, `outbound/BusinessHoursGate.java`, `outbound/RetryRunner.java`
- `profiles/ProfilePromptContext.java`
