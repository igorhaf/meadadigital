# 03 — IA e Fluxo de Mensagens

[← Home](00-HOME.md)

## Visão geral do ciclo

```
Cliente (WhatsApp) → Evolution API → POST /webhooks/evolution
   → WebhookSecretFilter (HMAC) → WebhookService (persiste inbound)
   → publica MessageInboundProcessedEvent (async)
       → PromptBuilder (monta system prompt com contexto do tenant + persona do perfil + RAG)
       → GeminiProvider.chat (responseSchema: text + intent + needs_human)
       → OutboundService:
            ├─ cadeia de handlers de tag por perfil (cria pedido/agendamento/etc., remove a tag)
            ├─ BusinessHoursGate (respeita horário)
            └─ EvolutionClient.sendMessage → cliente recebe a resposta
       → persiste a mensagem outbound (idempotente por evolution_message_id)
```

O `200 OK` do webhook é devolvido **imediatamente** (a IA + outbound rodam em listener assíncrono),
para a Evolution não reenviar.

## Inbound — `webhook/`

`EvolutionWebhookController` (`POST /webhooks/evolution`) → `WebhookService.process` (transacional).
Filtros e guardas, em ordem (cada um pode encerrar com um motivo `IGNORED_*`, sempre devolvendo 200):

1. **Secret:** `WebhookSecretFilter` valida `apikey` (header ou query) em tempo constante.
2. `event != messages.upsert` → `IGNORED_NON_MESSAGE_EVENT`.
3. `fromMe = true/null` → `IGNORED_FROM_ME` (eco da própria instância).
4. Instância desconhecida → `IGNORED_UNKNOWN_INSTANCE`.
5. JID de grupo/broadcast → `IGNORED_*`.
6. Sem texto → `IGNORED_NON_TEXT`.
7. **Guard de frescor:** `messageTimestamp` mais velho que `webhook.message-max-age-seconds`
   (default 180s) → `IGNORED_STALE`. Protege contra o re-sync de histórico do Baileys/Evolution
   no boot (incidente registrado em RISKS.md — re-sync disparou respostas a contatos reais).
8. `ContactRepository.findOrCreate(phone, company)` → `ConversationRepository.findOrCreate(contact, instance)`.
9. `MessageRepository.insert(direction=inbound, sender=contact)` + `UPDATE conversations.last_message_at`.
10. Publica `MessageInboundProcessedEvent` (async).

Heartbeat best-effort em `webhook_heartbeats` (try/catch silencioso, nunca bloqueia).

> **Estado do webhook:** desligado por padrão até religar consciente (mitigação do incidente de
> re-sync). Em dev usa-se `EVOLUTION_DRY_RUN=true` (loga em vez de enviar).

## Montagem do prompt — `ai/`

`PromptBuilder` preenche `src/main/resources/prompts/system-template.txt` com o contexto do tenant:

| Placeholder | Origem |
|-------------|--------|
| `{tone}` / `{rules}` / `{restrictions}` / `{handoff}` | `ai_settings` (1:1 por company). |
| `{services}` | catálogo `services` (soft delete respeitado). |
| `{faqs}` | `faqs`. |
| `{businessHours}` | `business_hours` (janelas por dia, permite wrap pós-meia-noite). |
| `{knowledgeContext}` | top-K chunks do RAG (ver abaixo). |
| `{profileContext}` | **persona do perfil** + contexto dinâmico (cardápio do sushi, processos do cliente legal, agenda do dental, etc.), via `ProfilePromptContext.segmentFor(profileId, companyId, conversationId)`. |

Seção vazia vira `""` (fallback seguro — o prompt genérico cobre o caso). A persona de cada perfil
e o contexto dinâmico estão detalhados em [04 — Multi-perfil](04-multiperfil-chassis.md).

## Chamada ao Gemini — `ai/GeminiProvider`

- `POST` para a API do Gemini com `system` + `history` (turnos da conversa) + a mensagem do usuário.
- **`responseSchema`** em JSON mode: `{ text, intent, needs_human }`. (Isso é mutuamente exclusivo
  com tool-calling no Gemini — por isso os perfis usam **tags em texto livre**, não function calling.)
- `AiResponse` = `text + intent + needs_human`. O `intent` ajuda o roteamento; as **tags** dentro do
  `text` é que disparam a criação de artefatos.

## RAG / Base de conhecimento — `knowledge/`

- **Ingestão:** `POST /admin/knowledge/documents` (PDF/TXT) → extrai texto → **chunking** (overlap,
  max ~500 chars) → **embeddings** (sidecar Python, `all-MiniLM-L6-v2`) → grava
  `knowledge_documents` + `knowledge_chunks` (com `embedding vector`).
- **Retrieval:** o `PromptBuilder` embeda a pergunta do cliente e busca os top-K chunks por **cosine
  distance** (`embedding <=> ?` no pgvector), filtrando por `company_id` e por um threshold de
  similaridade. Os chunks entram em `{knowledgeContext}`.
- Sidecar: `POST /embed {texts, kind: DOCUMENT|QUERY}` → embeddings (porta 7080).

## Outbound — `outbound/`

`OutboundService` (o maior arquivo do projeto) orquestra o envio. Ordem:

1. **Cadeia de handlers de tag por perfil** — resolve o perfil do tenant e tenta os handlers daquele
   perfil (perfil é único, só um age). Cada handler:
   - detecta a tag por regex (ex.: `<pedido>{...}</pedido>`),
   - parseia o JSON, **recalcula valores no backend** (descarta o que a IA chutou),
   - cria o artefato (pedido/agendamento/proposta/...),
   - e o OutboundService **remove a tag** do texto antes de enviar ao cliente.
   Exemplos: `OrderConfirmHandler` (sushi), `ReservationConfirmHandler` (restaurant),
   `maybeProcessPropostaEvento` + `maybeProcessAprovacaoProposta` (eventos), `EntregaPlanoHandler`
   (nutri, entrega read-only). Ver o catálogo completo em [05 — Nichos](05-nichos.md).
2. **`BusinessHoursGate`** — fora do horário, segura/ignora o envio conforme a config.
3. **`EvolutionClient.sendMessage(phone, text)`** — `POST` síncrono para a Evolution, usando o
   `evolution_token` da instância do tenant.
4. **Persistência:** insere a mensagem outbound (`sender=ai`/`human`) **após** confirmação da
   Evolution. Idempotência via `evolution_message_id` (UNIQUE parcial) — reentrega não duplica.
5. **Retry:** `RetryRunner` re-tenta envios falhados (async, algumas tentativas).

### Entrega read-only (padrão importante)

Quatro perfis (nutri, dermatologia, fotografia, cursos) têm um modo de **entrega de conteúdo
gravado pelo profissional** (plano alimentar, instruções de preparo, link de material, conteúdo de
módulo). Nesses casos a tag de entrega faz o handler enviar o texto **VERBATIM** via
`notifier.sendText()` — **fora da geração da IA**, para o conteúdo não ser reescrito — com
**barreira de contato** (só entrega ao contato dono daquele dado). Ver [05 — Nichos](05-nichos.md).

## Canais

- **WhatsApp** (principal) — via Evolution.
- **Webchat** (`webchat/`) — widget web embeddable: o cliente preenche nome/contato, vira um
  `contact` web com conversa isolada, mesma engine de IA.

## Treino / feedback — `training/`

`POST /api/feedback` registra a avaliação dos agentes humanos sobre respostas da IA (good/bad +
comentário), em `ai_feedback`, para análise futura.

## Referências de arquivo

- `webhook/EvolutionWebhookController.java`, `webhook/WebhookService.java`, `webhook/WebhookSecretFilter.java`
- `ai/PromptBuilder.java`, `ai/GeminiProvider.java`, `src/main/resources/prompts/system-template.txt`
- `knowledge/KnowledgeRetrievalService.java`, `knowledge/EmbeddingProvider.java`
- `outbound/OutboundService.java`, `outbound/EvolutionClient.java`, `outbound/BusinessHoursGate.java`
- `profiles/ProfilePromptContext.java`
