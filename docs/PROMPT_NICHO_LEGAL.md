>>> JÁ IMPLEMENTADO — perfil legal, camada 7.2, migration 31_legal.sql. Prompt de nicho RETROATIVO
>>> (documentação do que já existe), formato T5. Fonte: CLAUDE.md seção Perfil Legal + migration 31
>>> + docs/PERFIL_LEGAL.md.

[TAREFA — PERFIL LEGAL / ProcessoBot (camada 7.2) — RETROATIVO]

Documentação retroativa, no formato T5, do perfil vertical `legal` (ProcessoBot) JÁ implementado no
projeto Meada. NÃO é um pedido de implementação — o nicho existe, está fechado e validado.
Este documento descreve o que REALMENTE está no código/banco (migration 31_legal.sql, pacote
`src/main/java/com/meada/profiles/legal/`, telas em `frontend/app/(protected)/dashboard/`).
Segundo perfil vertical real do projeto (depois do SushiBot, camada 7.1), no mesmo padrão do sushi.

[CONTEXTO]
PROJETO MEADA em /home/igorhaf/meada.
O tenant `legal` (`companies.profile_id='legal'`) vira um produto de ESCRITÓRIO DE ADVOCACIA: gerencia
clientes e processos, registra andamentos manuais, e a IA atende os clientes via WhatsApp consultando
os processos deles. O tenant acessa pelo subdomínio do perfil e vê o produto "ProcessoBot". A IA
reconhece o cliente pelo telefone, resume os andamentos recentes e SEMPRE encaminha dúvida substantiva
ao advogado. Tom formal e respeitoso, terminologia jurídica correta.

>>> TRAVA DE COMPORTAMENTO DA IA (cravada — vive na persona `ProfilePromptContext.LEGAL`) <<<
- A IA NUNCA dá opinião ou aconselhamento jurídico. Para qualquer dúvida substantiva sobre o mérito do
  caso, orienta o cliente a "consultar o advogado responsável".
- Seu papel é confirmar o atendimento, organizar informações e RESUMIR os andamentos — NUNCA interpretar
  o mérito jurídico.
- Telefone não reconhecido: a IA pede que a pessoa se identifique (nome/CPF) e informa que vai encaminhar
  ao advogado, SEM expor dados de processos de ninguém.

EVOLUÇÃO ESTRUTURAL (o que este perfil inaugurou sobre o sushi):
- CLIENTE JURÍDICO DESACOPLADO DE `contacts`: `legal_clients` é catálogo PRÓPRIO (o escritório cadastra
  o cliente ANTES dele mandar WhatsApp). `name` é obrigatório; email/phone/document/`contact_id` são
  opcionais. O `contact_id` (nullable, ON DELETE SET NULL) liga o cliente jurídico ao contato do
  WhatsApp — a IA resolve a cadeia `contact → legal_client → processos`.
- CNJ VALIDADO POR MÓDULO 97 (Resolução CNJ 65/2008) no backend (`LegalCnjValidator`) — regex NÃO basta,
  o dígito verificador depende dos demais campos. Armazenado SEM máscara (20 dígitos), UNIQUE por
  (company_id, cnj_number); o frontend formata para exibição `NNNNNNN-DD.AAAA.J.TR.OOOO`.
- CONTEXTO DE PROCESSOS DO CLIENTE IDENTIFICADO injetado no prompt: `ProfilePromptContext.segmentFor`
  resolve a conversa → contato → cliente → processos+andamentos e injeta no contexto da IA; telefone
  desconhecido → bloco que orienta a IA a pedir identificação.

DECISÕES CRAVADAS (reais, conforme implementado):
1. `legal_clients` é entidade própria DESACOPLADA de `contacts` (não é o contact direto, como nos perfis
   sem catálogo de cliente). O vínculo com o WhatsApp é o `contact_id` nullable.
2. STATUS COM TRANSIÇÃO LIVRE (diferente do sushi, que é linear): qualquer status → qualquer status — o
   advogado pode reativar um processo arquivado, suspender e reativar etc. (`canTransitionTo` aceita
   qualquer destino não-nulo).
3. ANDAMENTOS (`legal_case_updates`) são MANUAIS (registro do advogado: title/body/occurred_at) e NÃO
   disparam notificação (andamento técnico ≠ comunicação ao cliente).
4. Mudança de STATUS notifica o cliente (texto fixo defensivo) apenas para suspenso/arquivado/encerrado;
   'ativo' (reativação) é SILENCIOSO.
5. O `OutboundService` NÃO foi tocado nesta camada — o legal NÃO tem tag equivalente ao `<pedido>` do
   sushi; o cliente só PERGUNTA pelo processo, não confirma um pedido. Não há handler de tag para legal.
6. Excluir cliente é BLOQUEADO se ele tiver processos (proteção de integridade — FK `on delete restrict`
   de `legal_cases.legal_client_id`).

[FUNDAÇÃO — migration 31_legal.sql]
Três tabelas exclusivas do perfil, todas com `RLS enable + force`, policies do tenant via
`app.company_id()`, grants `authenticated` + `service_role`. `updated_at` mantido pelos repositórios.

- `legal_clients` — clientes do escritório (catálogo, desacoplado de contacts).
  Colunas-chave: `id`, `company_id` (FK companies, on delete restrict), `name` (NOT NULL, CHECK
  length(trim) 1..200), `email`, `phone`, `document`, `contact_id` (FK contacts, NULLABLE, ON DELETE SET
  NULL), `notes`, `created_at`, `updated_at`. Índices: (company_id, name) e (company_id, contact_id)
  parcial WHERE contact_id IS NOT NULL.
- `legal_cases` — processos.
  Colunas-chave: `id`, `company_id` (FK companies, on delete restrict), `legal_client_id` (FK
  legal_clients, NOT NULL, on delete restrict), `cnj_number` (NOT NULL, sem máscara — 20 dígitos),
  `title` (NOT NULL, CHECK 1..200), `description`, `court`, `forum`, `subject`, `status` (NOT NULL DEFAULT
  'ativo', CHECK in ('ativo','suspenso','arquivado','encerrado')), `created_at`, `updated_at`,
  `status_updated_at`. UNIQUE (company_id, cnj_number). Índices: (company_id, status, updated_at desc) e
  (legal_client_id).
- `legal_case_updates` — andamentos manuais (timeline).
  Colunas-chave: `id`, `legal_case_id` (FK legal_cases, NOT NULL, ON DELETE CASCADE), `title` (NOT NULL,
  CHECK 1..200), `body`, `occurred_at` (NOT NULL — pode ser passado), `created_at`. Índice (legal_case_id,
  occurred_at desc). RLS via JOIN em legal_cases (tenant só vê andamentos de processos da própria
  empresa). Grants: select/insert/delete a authenticated (sem update — andamento é imutável).

A CHECK de `legal_cases.status` trava os mesmos 4 ids do enum Java/const TS. Validação do CNJ é
APP-LEVEL (`LegalCnjValidator`), não no banco.

[BACKEND]
Pacote `src/main/java/com/meada/profiles/legal/` (subpacotes `clients/` e `cases/`).

- `LegalCnjValidator` (utilitário estático, mód 97 ISO 7064 base 10000):
  * `normalize(raw)` — remove tudo que não é dígito.
  * `format(20 dígitos)` — exibe como `NNNNNNN-DD.AAAA.J.TR.OOOO`.
  * `isValid(raw)` — reordena os campos como `NNNNNNN AAAA J TR OOOO DD` (DV ao final) e verifica
    `BigInteger(rearranjado) mod 97 == 1`.
  * `computeCheckDigits(18 dígitos)` — calcula o DV `98 - (significativos*100 mod 97)` para GERAR números
    válidos (seed/testes). (Nota cravada: CNJs "reais" do prompt original não passavam no mód 97 — os
    testes/seed usam números com DV computado pelo próprio algoritmo.)
- `LegalCaseStatus` (enum Java, espelho 1:1 de `frontend/profiles/legal/legal-case-status.ts`,
  `LegalCaseStatusParityTest`): ATIVO/SUSPENSO/ARQUIVADO/ENCERRADO. `canTransitionTo` = transição LIVRE
  (qualquer destino não-nulo). `notificationText()` devolve texto fixo defensivo para
  suspenso/arquivado/encerrado e `null` para ativo (não notifica).
- `LegalClientService` / `LegalClientController` (`/api/legal/clients`): CRUD de clientes; exclusão
  bloqueada se houver processos. `findContactId`/`findById` resolvem o vínculo WhatsApp.
- `LegalCaseService` / `LegalCaseController` (`/api/legal/cases`): create valida CNJ (mód 97 → 400/`InvalidCnjException`),
  exige cliente existente (`LegalClientNotFoundException`), trata duplicidade de CNJ (`DuplicateCnjException`
  via DuplicateKeyException). `updateStatus` valida o id do status, persiste, audita
  (`legal_case_status_changed`), DISPARA notificação outbound (`notifier.notifyStatus`, best-effort,
  'ativo' não notifica) e invalida o cache de contexto da IA pelo contato do cliente. `addUpdate`/
  `removeUpdate` gerenciam andamentos (sem notificação). Tudo audita via `AuditLogger`.
- `LegalCaseNotifier`: resolve o canal a partir de `legal_client.contact_id` (o processo não tem conversa
  própria): contato → conversa mais recente → instância + telefone → `EvolutionSender.sendText`. Se o
  cliente NÃO tem `contact_id` (não vinculado), pula em SILÊNCIO. Best-effort: falha de envio NUNCA
  reverte a transição de status já persistida. Espelho do SushiOrderNotifier.
- `ProfilePromptContext.segmentFor(profileId, companyId, conversationId)`: para 'legal', resolve
  `conversationId → contactId` (via `conversationRepository.findContactIdByConversation`) e concatena a
  persona LEGAL com `legalCaseContextCache.contextSegment(companyId, contactId)` — o bloco com os
  processos+andamentos do cliente identificado, ou a orientação de pedir identificação se o telefone é
  desconhecido.
- `LegalCaseContextCache`: Caffeine `expireAfterWrite` TTL 60s (igual sushi), keyed por (company,
  contact). INVALIDADO explicitamente em toda mutação de cliente/processo/andamento (via
  `invalidateClientContext`, que resolve o contato do cliente).
- `LegalProfileGuard.requireLegal(user)`: endpoints `/api/legal/**` retornam 403
  `forbidden_wrong_profile` para tenant de outro perfil ou sem empresa. O `JwtAuthenticationFilter`
  autentica `/api/legal/**` (além de `/admin/**` e dos demais perfis).

[FRONTEND]
- `getNavForProfile('legal')` injeta o grupo "Escritório" (Clientes + Processos).
- `/dashboard/clients` (`app/(protected)/dashboard/clients/page.tsx`): CRUD de clientes — nome
  obrigatório; email/telefone/CPF-CNPJ/notas opcionais; badge "vinculado" quando há contact_id; busca por
  nome/email/telefone/CPF; exclusão bloqueada se houver processos.
- `/dashboard/cases` (`app/(protected)/dashboard/cases/page.tsx`): lista de processos com chips de status
  (Ativo/Suspenso/Arquivado/Encerrado) + busca por título/CNJ/cliente; novo processo (cliente + CNJ com
  máscara automática e validação de DV + título + vara/fórum/matéria opcionais).
- `/dashboard/cases/[id]` (`app/(protected)/dashboard/cases/[id]/`): detalhe do processo — seletor de
  status (notifica o cliente vinculado em suspensão/arquivamento/encerramento; reativar não notifica) +
  timeline de andamentos manuais (título obrigatório, descrição/data opcionais; data em branco = hoje).
- Const de status em `frontend/profiles/legal/legal-case-status.ts` (espelho do enum Java, guardado pelo
  parity test).

[DOCS]
- `docs/PERFIL_LEGAL.md`: guia operacional do escritório (cadastrar clientes; cadastrar processos com
  CNJ; detalhe do processo com status + andamentos; como a IA atende; limitações honestas).
- Seção "## Perfil Legal (ProcessoBot, camada 7.2)" no CLAUDE.md.

[TESTES BACKEND]
Suíte em `src/test/java/com/meada/profiles/legal/`:
- `LegalCnjValidatorTest` — validação mód 97 + format + computeCheckDigits.
- `LegalCaseStatusParityTest` — paridade Java↔TS dos 4 status.
- `LegalCaseServiceTest` — create (CNJ inválido, cliente inexistente, CNJ duplicado), updateStatus
  (transição livre + notificação), andamentos, invalidação de cache.
- `LegalCaseControllerIntegrationTest` — endpoints `/api/legal/cases` (CRUD + status + guard 403).
- `LegalClientServiceTest` / `LegalClientControllerIntegrationTest` — CRUD de clientes + delete-em-uso.
- `LegalCaseContextCacheTest` — contexto da IA (cache + invalidação).
- `PromptBuilderLegalContextTest` — injeção dos processos do cliente identificado no prompt.
(Contagem real vem do Surefire `Tests run: N`, nunca de grep textual.)

[CONSTRAINTS DUROS]
- Migration única (31_legal.sql). Sem foto/anexo (bloqueador SERVICE_ROLE_KEY / Storage).
- Cliente jurídico DESACOPLADO de contacts (`legal_clients`, catálogo próprio); vínculo via `contact_id`
  nullable. Excluir cliente com processo → bloqueado (FK restrict).
- CNJ validado por mód 97 no backend (não só regex), armazenado sem máscara (20 dígitos), UNIQUE por
  company.
- Status hardcoded materializado (enum Java ↔ const TS, parity test ↔ CHECK no banco). Transição LIVRE.
- Andamentos manuais, NÃO notificam. Mudança de status notifica só suspenso/arquivado/encerrado (texto
  fixo defensivo); 'ativo' silencioso.
- `OutboundService` NÃO tocado (legal não tem tag de pedido). Guard `LegalProfileGuard` → 403
  forbidden_wrong_profile. JwtFilter autentica `/api/legal/**`.
- Cache de contexto da IA TTL 60s + invalidação em toda mutação de cliente/processo/andamento.
- NÃO mexer em outros perfis nem em system-template.txt. Webhook OFF.

[PASSO FINAL — RESUMIDO]
Tenant legal de teste (`igorhaf3`, perfil legal) provisionado no Supabase Auth + linha em
`public.users` com company_id do tenant legal. Smoke E2E: login → `/admin/me` (profileId=legal) →
CRUD de cliente/processo → mudança de status notifica cliente vinculado → IA injeta processos do
cliente identificado pelo telefone. Gate de fechamento: `mvn -B clean test` verde (contagem real do
Surefire) + `next build` limpo. Camada FECHADA (tag de sub-fase).

[REPORTAR]
- "perfil legal — camada 7.2 (ProcessoBot); segundo perfil vertical, padrão do sushi"
- "EVOLUÇÃO: cliente jurídico DESACOPLADO de contacts (legal_clients) + CNJ validado mód 97 + contexto de
  processos do cliente identificado injetado no prompt"
- "status com transição LIVRE (não linear como o sushi); só suspenso/arquivado/encerrado notificam"
- "andamentos manuais não notificam; OutboundService NÃO tocado (sem tag de pedido)"
- "Guard LegalProfileGuard 403 forbidden_wrong_profile; JwtFilter autentica /api/legal/**"
- "Cache de contexto TTL 60s + invalidação em toda mutação"
- "Paridade LegalCaseStatus (Java↔TS) validada"
