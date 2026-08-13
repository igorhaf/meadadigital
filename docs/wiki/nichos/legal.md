# ProcessoBot — regras de negócio (legal, camada 7.2)

[← Catálogo](../05-nichos.md) · Chassi: singular (consulta read-only — sem tag de IA) · Guia operacional: docs/PERFIL_LEGAL.md · Migrations: 31, 102

## O negócio em 3 linhas

O tenant é um escritório de advocacia. O cliente pergunta pelo WhatsApp "como está meu processo?";
a IA o identifica pelo telefone (contato → cliente jurídico vinculado), resume os andamentos e
informa datas de prazos/audiências — sem JAMAIS opinar sobre o mérito. É o único perfil onde a IA
não cria nada: **não existe tag**.

## Jornada no WhatsApp (cenários)

1. Cliente vinculado manda mensagem. O `LegalCaseContextCache` resolve contato →
   `legal_clients.contact_id` → processos + últimos 3 andamentos + próximos compromissos (até 5
   prazos/audiências pendentes futuros por processo) e injeta o bloco no prompt.
2. A IA RESUME o andamento e informa data/hora/local de audiências quando perguntada — com instrução
   explícita de não comentar estratégia/desfecho; dúvida substantiva → "consulte o advogado
   responsável".
3. **Telefone não reconhecido** (sem contato ou contato sem vínculo): o bloco injetado orienta a IA
   a pedir identificação (nome/CPF) e encaminhar ao advogado, SEM expor dados de processo algum.
4. O advogado trabalha no painel: cadastra clientes, processos (CNJ validado), andamentos (timeline
   manual) e prazos/audiências. Mudar o status do processo para suspensão/arquivamento/encerramento
   notifica o cliente vinculado com texto fixo defensivo.
5. **Onda 1:** o `LegalDeadlineReminderJob` avisa o cliente em D-3 e D-1 do prazo/audiência
   pendente; ao ENCERRAR o processo, uma segunda mensagem agradece + pede avaliação + convida
   indicação.
6. **Exceções:** tudo que falha, falha no PAINEL com reason HTTP (CNJ inválido, duplicado, cliente
   em uso) — o cliente final nunca vê erro, porque a IA não executa ação nenhuma.

## Regras de negócio

### Transacionais (invariantes duras)

- **R1 — Cliente jurídico DESACOPLADO do contact:** `legal_clients.name` é o único obrigatório;
  `contact_id` é nullable (`on delete set null`) — o escritório cadastra ANTES do WhatsApp e o
  vínculo vem depois, pelo telefone. Sem vínculo, o cliente existe mas não recebe notificação
  (skip silencioso) e a IA não o reconhece.
- **R2 — CNJ validado por mód 97 (ISO 7064)** no backend (`LegalCnjValidator`): 20 dígitos
  normalizados sem máscara; rearranjo `NNNNNNN AAAA J TR OOOO DD` tem de dar `mod 97 == 1` — regex
  não basta, o DV depende dos demais campos. Inválido → 400 `invalid_cnj`.
- **R3 — CNJ único por tenant:** UNIQUE `(company_id, cnj_number)` → 409 `duplicate_cnj`.
- **R4 — Cliente com processos não é excluído** (FK `on delete restrict` em
  `legal_cases.legal_client_id`) → 409 `client_in_use`. Processo excluído leva os andamentos e
  prazos juntos (cascade).
- **R5 — Transição de status do processo é LIVRE** (decisão cravada — ≠ máquinas rígidas dos outros
  perfis): qualquer status → qualquer status, sempre por humano no painel.
- **R6 — Andamentos NÃO notificam** (são técnicos); `occurred_at` pode ser retroativo (em branco =
  agora). Só a mudança de STATUS do processo notifica.
- **R7 — Idempotência re-armável dos lembretes de prazo:** `reminded3_due_date`/`reminded1_due_date`
  guardam a `due_date` avisada — **remarcar REARMA os dois avisos** (marker ≠ nova data). Só
  `pendente` entra na varredura (índice parcial `idx_legal_deadlines_due` WHERE status = 'pendente').
- **R8 — `legal_config` e `legal_deadlines` são acessíveis SÓ via Spring** (RLS habilitado sem
  policy nem grant para `authenticated`; grant apenas `service_role`) — endpoints `/api/legal/**`
  atrás do `LegalProfileGuard`.

### Máquina de status

```
       ┌─────────────┐
ativo ⇄ suspenso ⇄ arquivado ⇄ encerrado     (transição LIVRE: qualquer ↔ qualquer)
       └─────────────┘
prazo/audiência: pendente ⇄ cumprido ⇄ perdido  (gestão livre do advogado)
```

| Transição | Quem pode | Notifica o cliente? |
|---|---|---|
| processo → suspenso | humano no painel | **sim** (texto fixo: "entre em contato com nosso escritório") |
| processo → arquivado | humano | **sim** (idem) |
| processo → encerrado | humano | **sim** + segunda mensagem de pós-encerramento (toggle) |
| processo → ativo (reativação) | humano | não |
| prazo: pendente ⇄ cumprido ⇄ perdido | humano (inline na tela Prazos) | não |

Não há transição inválida (livre); status desconhecido → 400 `invalid_status`. A IA não muda status algum.

### O que a IA PODE × NUNCA faz (travas da persona)

- **PODE:** reconhecer o cliente pelo telefone, resumir os andamentos recentes, informar
  data/hora/local dos compromissos listados no contexto, pedir identificação a desconhecidos.
- **NUNCA** (`ProfilePromptContext.LEGAL` + instruções do bloco de contexto): dá opinião ou
  aconselhamento jurídico; interpreta o mérito; comenta estratégia ou desfecho provável; expõe dados
  de processo a telefone não vinculado. Dúvida substantiva → "consultar o advogado responsável".
- **Não executa NENHUMA ação:** sem tag, sem handler — perfil 100% consulta (a IA lê o contexto e
  responde; criar/editar é exclusivo do painel).

### Tags de IA

Não há. É o perfil singular read-only: nenhum handler no pacote `com.meada.profiles.legal`, nenhum
`maybeProcess*` no `OutboundService`. Toda escrita é do advogado no painel.

### Validações e erros

| reason | HTTP | Significado de negócio | Cenário que dispara |
|---|---|---|---|
| `forbidden_wrong_profile` | 403 | tenant não é legal | `/api/legal/**` com outro perfil |
| `invalid_cnj` | 400 | dígito verificador mód 97 não bate (ou ≠ 20 dígitos) | criar processo com CNJ digitado errado |
| `duplicate_cnj` | 409 | CNJ já cadastrado no tenant (UNIQUE) | recadastrar o mesmo processo |
| `client_in_use` | 409 | cliente tem processos (FK restrict) | DELETE de cliente com processo |
| `client_not_found` / `case_not_found` / `deadline_not_found` / `case_or_update_not_found` | 404 | inexistente ou de outro tenant | id errado |
| `invalid_kind` | 400 | kind fora de prazo/audiencia | POST de deadline com "reuniao" |
| `invalid_status` | 400 | status fora do CHECK (processo ou prazo) | PATCH com status inexistente |
| `invalid_date` / `invalid_occurred_at` | 400 | data malformada | due_date/occurred_at ilegível |

### Notificações ao cliente

- **Envia** na mudança de status do processo para `suspenso`/`arquivado`/`encerrado` — texto fixo,
  juridicamente defensivo ("Informação sobre seu processo: … entre em contato com nosso
  escritório"), SEM detalhe de mérito — e nos lembretes D-3/D-1 de prazo/audiência (data/hora/local,
  nunca o conteúdo). Pós-encerramento encadeia agradecimento + `review_link` + indicação.
- **Silêncio** em reativação (`ativo`), andamentos da timeline, criação/edição de prazos, e para
  cliente sem vínculo WhatsApp (skip/marca sem envio — nunca erro).
- Canal: `legal_clients.contact_id` → telefone do contato + conversa mais recente
  (`LegalCaseNotifier`); canal incompleto → warn e não envia. Best-effort: falha nunca reverte o status.

## Dados e snapshots

- **`legal_clients`** — name 1–200 obrigatório; email/phone/document/notes opcionais; `contact_id`
  nullable (vínculo automático por telefone; badge "vinculado" no painel).
- **`legal_cases`** — `cnj_number` SEM máscara (20 dígitos; o frontend formata
  `NNNNNNN-DD.AAAA.J.TR.OOOO`); UNIQUE por tenant; title 1–200; court/forum/subject livres; status
  CHECK; `status_updated_at`. Sem snapshot — o perfil não congela nada (não há transação de venda).
- **`legal_case_updates`** — timeline manual (title 1–200, body, `occurred_at`); cascade; tenant
  SELECT/INSERT/DELETE via RLS (sem UPDATE — andamento não se edita, se apaga e recria).
- **`legal_deadlines`** (onda 1) — kind CHECK prazo/audiencia; `due_date` NOT NULL + `due_time`/
  `location` opcionais; status CHECK; markers `reminded3_due_date`/`reminded1_due_date`.
- **`legal_config`** (onda 1, 1:1) — `review_link`, `post_closure_enabled` (default true),
  `deadline_reminder_enabled` (default true); sem linha = `coalesce(true)`.
- **Cache:** `LegalCaseContextCache` (Caffeine, **TTL 60s** — o dado é pouco volátil, max 1000,
  keyed `companyId:contactId`); invalidado por contato em mutação de cliente/processo (e
  `invalidateAll` quando a mutação não sabe o contato).

## Features de onda (backlog implementado)

Migration 102 (`LegalDeadlineReminderJob`, cron `${legal.deadline-reminder-cron:0 30 8 * * *}`):

- **#1 Agenda de prazos e audiências + lembrete D-3/D-1:** o advogado cadastra na tela "Prazos"
  (CRUD + status inline); o job varre os `pendente` com vencimento em hoje+3 e hoje+1 e avisa o
  CLIENTE vinculado — texto FIXO com data/hora/local, **nunca mérito**. Idempotência R7 (remarcar
  rearma); sem vínculo → marca sem envio. Toggle `deadline_reminder_enabled` (**ON**). A IA ganhou o
  bloco "Próximos compromissos" no contexto (informa a DATA quando perguntada).
- **#3 Pós-encerramento:** processo → `encerrado` dispara, além da notificação de status, a mensagem
  de agradecimento + `review_link` (se configurado; sem link a mensagem sai sem ele) + convite de
  indicação. Toggle `post_closure_enabled` (**ON**). Tela "Configurações" + `GET/PUT /api/legal/config`.

## O que NÃO existe (limites honestos)

- Integração com tribunais (push automático de andamento) — a timeline é 100% manual.
- Cálculo automático de prazo processual, alerta interno ao ADVOGADO (o lembrete é ao cliente),
  partes formais (autor/réu), recursos, custas, peças, honorários/parcelas.
- Anexo/documento do processo (bloqueador de upload/Storage).
- Tag de IA de qualquer tipo — inclusive agendamento: a persona menciona "agendar
  reuniões/consultas", mas não há agenda nem tag no perfil; na prática a IA só pode conversar
  (divergência persona×código registrada).
- Textos de notificação personalizáveis por escritório (fixos nesta versão).
