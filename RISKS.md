# RISKS — Meada WhatsApp

Registro de riscos conhecidos e pendências bloqueantes do projeto. Cada item segue
o template fixo abaixo. Auditar este arquivo antes de marcos relevantes (ativação
de cliente, deploy de produção, due diligence).

Template por item:

```
## <Título do risco>

- **Status:** <Aberto | Mitigado | Aceito>
- **Bloqueante para:** <marco que não pode passar sem resolver>
- **Razão:** <por que existe>
- **Plano de mitigação:** <ação concreta + critério de fechamento>
- **Detectado em:** <camada/momento>
```

---

## Schema do payload da Evolution validado contra Baileys, não contra fonte oficial

- **Status:** Aberto.
- **Bloqueante para:** Ativação do primeiro cliente real (não para fechar a camada 2).
- **Razão:** A Evolution API não publica schema canônico do payload de webhook. Os DTOs em `EvolutionWebhookPayload.java` foram montados a partir do código-fonte do envelope (campos `event`, `instance`, `data`, `server_url`, `date_time`, etc. — confirmados no `webhook.controller.ts` do repositório oficial). Inferências concretas que precisam ser validadas contra payload real:
  - Estrutura interna de `data` (`key.remoteJid`, `key.id`, `key.fromMe`, `pushName`, `message.conversation`, `message.extendedTextMessage.text`) — formato Baileys.
  - **Device suffix `:N` no `remoteJid` para sender em Multi-Device** (formato `5511999990000:1@s.whatsapp.net`) — assumido a partir da convenção Baileys/protocolo Multi-Device do WhatsApp; removido pelo `MessagePayloadNormalizer` antes da validação E.164. Sem esse tratamento, mensagem de device > 1 (ex. WhatsApp Web) cairia em `JidType.UNKNOWN` e seria descartada.
- **Plano de mitigação:** Antes da ativação do primeiro cliente, capturar 5+ payloads `messages.upsert` reais de uma instância Evolution + WhatsApp Web rodando, comparar campo a campo contra os DTOs, ajustar divergências, e documentar o resultado da validação neste item (mudando o Status para Mitigado). Critério de fechamento: todos os campos usados pelo `WebhookService` (instance, key.id, key.remoteJid, conteúdo de texto, fromMe) confirmados contra payloads reais.
- **Detectado em:** Camada 2 (webhook), durante o desenho dos DTOs.

---

## Secret de webhook em query param (?apikey=) pode vazar em access-log de proxy

- **Status:** Aceito (trade-off)
- **Bloqueante para:** N/A — mitigado por estratégia documentada
- **Razão:** Evolution self-hosted (vide issues #1933/#2276) não garante suporte a header customizado em webhook outbound. Para robustez, o filter aceita o secret em duas fontes: header `apikey` (preferencial) e query param `apikey` (fallback). Quando o secret cai em query param, proxies podem logar a URL completa no access-log, expondo o secret.
- **Plano de mitigação:** (a) Header tem precedência — filter só lê query param se header ausente; configurar Evolution para usar header quando a versão alvo suportar. (b) Configurar proxy/CDN do meada.app para NÃO logar query string em `/webhooks/*` no access-log (Nginx `log_format` sem `$query_string`, ou exclusão por location). (c) Quando confirmarmos que a versão Evolution alvo suporta header confirmado, remover o fallback de query param desta camada.
- **Detectado em:** Camada 2 (webhook), durante desenho do `WebhookSecretFilter`.

---

## Nome do modelo Gemini vigente a confirmar antes do deploy

- **Status:** Aberto.
- **Bloqueante para:** Validação manual da Fase 3.4 (e deploy de produção da camada 3).
- **Razão:** A doc oficial da Gemini cita variantes de nome diferentes em exemplos diferentes (gemini-1.5-flash, 2.0-flash, 2.5-flash, 3.5-flash). O Google renomeia modelos com frequência, e um nome desatualizado faz a API recusar a chamada. O `GeminiProvider` lê o modelo de `GEMINI_MODEL` (env, sem default no código — fail-fast) justamente para não enterrar um nome que envelhece silenciosamente. Os testes (MockWebServer) usam um valor qualquer e não validam o nome real.
- **Plano de mitigação:** Na validação manual da Fase 3.4, confirmar o nome do modelo flash vigente contra a API real (`GET /v1beta/models` ou doc atual) e preencher `GEMINI_MODEL` com o nome confirmado antes do deploy. Documentar o nome validado aqui (mudando o Status para Mitigado).
- **Detectado em:** Camada 3 (IA), Fase 3.2, ao desenhar o `GeminiProvider`.

---

## Backoff síncrono via Thread.sleep no thread pool @Async

- **Status:** Aceito (trade-off MVP).
- **Bloqueante para:** N/A — mitigado por dimensionamento conservador do pool.
- **Razão:** O retry de IA/Evolution (3 tentativas, backoffs 1s/3s) usa `Thread.sleep` na thread do pool @Async que processa o pipeline outbound. Pior caso ~4s/mensagem de thread PRESA dormindo. Com pool pequeno (2-4 threads na Fase 3.4), sob carga isso vira gargalo (threads bloqueadas em sleep não processam outras mensagens).
- **Plano de mitigação:** Pool dimensionado conservador na 3.4 (suficiente para o volume MVP). Saturação observada (threads do pool todas em sleep, fila de eventos crescendo) → migrar para retry-com-reagendamento (Spring `@Scheduled` + tabela de pendências) ou fila externa (Redis/SQS) — fase 2.
- **Detectado em:** Camada 3 (IA), Fase 3.3, ao desenhar o retry do `OutboundService`.

---

## Tenant com canal Evolution quebrado (token/instância inválidos) fica em backlog até intervenção operacional

- **Status:** Aceito (com alerta).
- **Bloqueante para:** N/A — mitigado por log ERROR alertável.
- **Razão:** Se o `evolution_token` ou o `instance_name` de um tenant estiverem errados, TODO envio outbound falha com erro fatal (401/404). A decisão (matriz do OutboundService, caso 8) é NÃO fazer flip para humano — porque o atendente humano usaria o mesmo canal quebrado, e flip empilharia conversas em `handled_by='human'` sem ninguém poder responder (backlog invisível). Em vez disso: log ERROR, `handled_by` continua 'ai', e cada nova mensagem tenta enviar e falha igual — até um admin corrigir a config. Consequência: o tenant fica efetivamente sem responder (inbound persiste, IA gera resposta, mas não entrega) até a intervenção.
- **Plano de mitigação:** O log ERROR (com `reason` evolution_auth_failed/evolution_instance_not_found, instance, company_id) deve ser ALERTÁVEL no monitoramento — é como o problema é descoberto, não por flip silencioso. Operação corrige token/instance na config do tenant. Pós-MVP: um health-check de instância no onboarding/periódico evitaria o tenant entrar quebrado.
- **Detectado em:** Camada 3 (IA), Fase 3.3, na matriz de fluxo do OutboundService.
