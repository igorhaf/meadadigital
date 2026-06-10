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

- **Status:** Mitigado (2026-06-04).
- **Bloqueante para:** ~~Validação manual da Fase 3.4~~ — resolvido.
- **Razão:** A doc oficial da Gemini cita variantes de nome diferentes em exemplos diferentes (gemini-1.5-flash, 2.0-flash, 2.5-flash, 3.5-flash). O Google renomeia modelos com frequência, e um nome desatualizado faz a API recusar a chamada. O `GeminiProvider` lê o modelo de `GEMINI_MODEL` (env, sem default no código — fail-fast) justamente para não enterrar um nome que envelhece silenciosamente. Os testes (MockWebServer) usam um valor qualquer e não validam o nome real.
- **Plano de mitigação:** Na validação manual da Fase 3.4, confirmar o nome do modelo flash vigente contra a API real (`GET /v1beta/models` ou doc atual) e preencher `GEMINI_MODEL` com o nome confirmado antes do deploy. Documentar o nome validado aqui (mudando o Status para Mitigado).
- **Mitigação aplicada:** `GET /v1beta/models` (2026-06-04) autenticou (HTTP 200) e listou `models/gemini-3.5-flash` no catálogo. `GEMINI_MODEL=gemini-3.5-flash` confirmado vigente e disponível. A chamada real `generateContent` ainda será exercida no cenário PROCESSED, mas o nome do modelo deixa de ser incógnita.
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

---

## WhatsApp @lid em mensagens reais é silenciosamente ignorado (sem perda de dado, mas sem resposta)

- **Status:** Aceito (com alerta WARN — mitigação já no código).
- **Bloqueante para:** N/A — comportamento defensivo correto; sem solução técnica enquanto o número não vier no payload.
- **Razão:** A Evolution v2.3.1 emite `remoteJid: <opaco>@lid` em mensagens reais (LID = Local Identifier do WhatsApp, privacidade — migração iniciada em 2023, irreversível), SEM trazer `remoteJidAlt` nem `senderPn` em formato `@s.whatsapp.net` (validado contra 39 mensagens reais na sessão de validação E2E: 0 traziam o número). O `MessagePayloadNormalizer` classifica `@lid` como `JidType.UNKNOWN` e o webhook retorna `IGNORED_UNKNOWN_JID`. É o comportamento correto — NÃO há como inventar o número de telefone a partir do LID (o mapeamento LID→número é unidirecional e não exposto no payload) — mas significa que essas mensagens do cliente não são processadas nem respondidas pela IA. Correlação reportada em discussões da comunidade Baileys (NÃO confirmada por testes nossos): ocorreria tipicamente com remetente Android, enquanto iPhone tende a enviar `@s.whatsapp.net`. Útil como hipótese diagnóstica, não como fato cravado — nosso fato validado é apenas "39/39 @lid sem número lateral".
- **Mitigação no código (já existe desde a camada 2):** `IGNORED_UNKNOWN_JID` já é nível `WARN` (não INFO) e o log já inclui `raw_jid=...@lid` literal — operador detecta via filtro de log. Nenhuma mensagem é corrompida ou perdida no banco (a inbound simplesmente não é persistida, pois não há contato resolvível); não há flip nem backlog falso.
- **Plano de mitigação futura:** quando aparecer um payload real de WEBHOOK `@lid` que inclua `remoteJidAlt` ou `senderPn` em formato `@s.whatsapp.net` (versão futura da Evolution, ou conta WhatsApp Business com config diferente), capturar o payload BRUTO, cravar a estrutura real observada, e só então adicionar um ramo "recupera" ao `MessagePayloadNormalizer` (classificar como `USER` com o número do campo lateral). Sem código especulativo — a v2.3.1 atual não traz esses campos, então o ramo não tem caso real para validar hoje.
- **Produto futuro (não codificar agora):** quando o volume de `@lid` ignorado virar dor real, decidir entre (a) painel/dashboard expondo mensagens perdidas para o admin investigar manualmente, ou (b) endpoint de reenvio quando a Evolution expuser o número. Métrica/contador é melhor obtida por agregação de log (`outcome=IGNORED_UNKNOWN_JID` por janela) do que por código novo — YAGNI até haver consumidor.
- **Detectado em:** Validação E2E Camada 3, sessão 2026-06-04, ao inspecionar `/chat/findMessages/meada-delta-01`.

---

## Validação JWT do admin depende de conectividade ao endpoint JWKS do Supabase

- **Status:** Aceito (mitigado por cache).
- **Bloqueante para:** N/A.
- **Razão:** O `JwtAuthenticationFilter` (camada 4.1.1) valida os tokens ES256 contra as chaves públicas servidas no endpoint JWKS do Supabase (`SUPABASE_JWKS_URL`), via `RemoteJWKSet`. Isso introduz uma dependência de rede: na 1ª verificação após o boot (e quando o cache expira), o backend faz um GET ao endpoint JWKS. Se o endpoint estiver indisponível nesse exato momento, a 1ª request de auth pode falhar (e re-tentar). Diferente do HS256 antigo (secret local, zero rede), mas inevitável com chaves assimétricas.
- **Plano de mitigação:** O `RemoteJWKSet` do nimbus **cacheia** as chaves (lifespan ~15min, refresh ~5min — defaults), então o GET ao JWKS é raro (não por request). A migração para ES256/JWKS foi forçada pela rotação do Supabase para chaves assimétricas (não há HS256 reversível pelo painel). O ganho: rotação de chave futura é absorvida automaticamente (o RemoteJWKSet re-busca quando a `kid` muda) — sem intervenção manual. Monitorar erros de auth correlacionados a indisponibilidade do endpoint Supabase; se virar dor, avaliar um cache persistente/maior TTL.
- **Detectado em:** Camada 4.1.1 (migração HS256→ES256/JWKS), 2026-06-06.

---

## Re-sync de histórico (Baileys/Evolution) dispara respostas automáticas a conversas reais antigas

- **Status:** Aberto — CONTIDO (webhook desligado em 2026-06-10). Bug de produto, não só do ambiente de teste.
- **Bloqueante para:** Religar o webhook de qualquer instância; ativação de qualquer cliente real. NÃO religar o webhook até o fix.
- **Razão:** Quando uma instância Evolution **reconecta**, a Evolution/Baileys **sincroniza o histórico** do WhatsApp pareado e reentrega mensagens ANTIGAS como eventos `messages.upsert`. O `WebhookService` não distingue mensagem nova (ao vivo) de histórico sincronizado — trata todo `messages.upsert` com `fromMe:false` + texto como inbound nova → publica evento → o `OutboundService` responde com a IA → `EvolutionClient.sendText` envia DE VERDADE. Resultado: ao subir o backend (com webhook ligado), uma RAJADA de respostas automáticas sai para conversas pessoais reais e antigas.
- **Evidência empírica (sessão 2026-06-10):** a instância estava pareada ao WhatsApp pessoal do Igor (`558195489984`, "Igor Herson"). Foram gravadas **26 mensagens outbound** para ~10 contatos reais. As inbounds foram ingeridas numa rajada de ~20s (**06-07 22:48:12 → 22:48:30**) — um contato sozinho despejou ~20 mensagens em 11s (conteúdo pessoal: organização de aniversário). Uma das mensagens respondidas tinha `messageTimestamp: 1780853793` (= 2026-06-04), gravada no banco 3 dias depois — prova de reprocessamento de histórico, não mensagem ao vivo. As mensagens reais vinham `@s.whatsapp.net` + `fromMe:false` (passavam o normalizer e o filtro fromMe); notado também `previousRemoteJid` resolvendo `@lid`→`@s.whatsapp.net` no sync (a Evolution v2.3.1 resolve o LID ao sincronizar, por isso passaram o normalizer que ignora `@lid`).
- **Por que os filtros atuais não barraram:** o `fromMe` funcionou (ignorou os envios do próprio Igor, que vêm `@lid fromMe:true`); o `@lid` cru é ignorado (UNKNOWN). O que vazou foi inbound de usuário legítimo (`@s.whatsapp.net fromMe:false`) — indistinguível de "cliente" no design atual, que pressupõe número de atendimento dedicado, não número pessoal com tráfego humano.
- **Plano de mitigação (3 camadas, ordem de prioridade):**
  1. **Dry-run em `STAGE=local`** — o `EvolutionClient.sendText` LOGA em vez de enviar quando `STAGE != production`. É a salvaguarda que teria evitado TODO o episódio: testar localmente nunca envia mensagem real. Prioridade máxima.
  2. **Filtrar por `type` do `messages.upsert`** — processar só `type=notify` (mensagem nova ao vivo); ignorar `append`/sync de histórico. O campo NÃO é capturado pelo DTO atual (`EvolutionWebhookPayload`) — precisa adicionar + checar no `WebhookService`. Fix central.
  3. **Guard de frescor por `messageTimestamp`** — ignorar mensagens cujo `messageTimestamp` seja muito antigo (ex. > alguns minutos). Defesa em profundidade caso o `type` falhe.
- **Próxima sessão (ordem cravada):** registrar (feito aqui) → investigação empírica do campo `type` no payload real do webhook (capturar um `messages.upsert` real e confirmar onde o `notify`/`append` aparece) → proposta em prosa do fix (3 mitigações na ordem acima) → só então código. Webhook permanece OFF até o fix.
- **Detectado em:** Camada 4.1, sessão 2026-06-10, ao subir o backend repetidamente durante tentativas de smoke do painel admin.
