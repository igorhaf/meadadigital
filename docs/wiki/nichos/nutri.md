# NutriBot — regras de negócio (nutri, camada 8.0)

[← Catálogo](../05-nichos.md) · Chassi: F — entrega read-only (avô) + G — sub-entidade em 2 níveis (+ agenda A por profissional) · Guia operacional: docs/PERFIL_NUTRI.md · Migrations: 39, 99

## O negócio em 3 linhas

O tenant é um consultório de nutrição. O paciente (ou o responsável — um contato pode ter N
pacientes) fala pelo WhatsApp; a IA **agenda consultas** com os nutricionistas e **entrega o plano
alimentar que o profissional gravou** — texto exato, nunca conteúdo nutricional próprio. Plano é
conduta privativa do nutricionista (CFN/CRN): a linha que a IA não cruza.

## Jornada no WhatsApp (cenários)

1. Contato identificado pelo telefone. O contexto lista os nutricionistas ativos, os pacientes do
   contato (objetivo + **SE** tem plano ativo + última consulta) e os slots livres dos próximos 7
   dias por profissional (granularidade 30 min, máx 6/dia).
2. **Agendar:** na confirmação, a IA emite `<consulta_nutri>` em um de 2 modos — `patient_id`
   existente OU `new_patient{name, goal}` (cadastra o paciente como sub-entidade do contato E agenda
   no mesmo turno). Backend valida tipo/profissional/paciente ativos, janela e conflito; a consulta
   nasce **agendado** (silencioso).
3. **Pedir o plano:** havendo plano ativo, a IA emite `<entrega_plano>{patient_id}`. O
   `EntregaPlanoHandler` aplica a **barreira de contato** e envia o `body` **VERBATIM**
   (`notifier.sendText` — não passa pela IA). Sem plano ativo, a IA oferece agendar consulta.
4. O consultório confirma/cancela no painel — o paciente é notificado.
5. **Exceções (best-effort, sem mensagem de erro ao paciente):** conflito de slot, fora de horário,
   tipo inválido, paciente de outro contato, profissional inativo → a tag vira no-op com warn e a
   resposta segue; o tenant contorna manualmente.
6. **Trava clínica:** "posso comer X?"/"quantas calorias?" → a IA orienta agendar consulta. Sinais de
   transtorno alimentar → guarda: acolhe sem reforçar, sem números, encaminha ao nutricionista.
7. **Onda 1:** na véspera o `NutriReminderJob` pergunta "confirma?". A resposta cai na IA, que emite
   `<confirmacao_nutri>{appointment_id, decisao}` — confirmar ou cancelar (cancelar libera o slot).

## Regras de negócio

### Transacionais (invariantes duras)

- **R1 — Conflito de slot half-open POR PROFISSIONAL:** duas consultas ativas
  (`agendado`/`confirmado`) do mesmo profissional não sobrepõem `[start_at, end_at)` —
  `not (end_at <= :s or start_at >= :e)`, RE-VERIFICADO dentro da transação de INSERT
  (`NutriAppointmentRepository.insertAppointment`) → 409 `conflict_slot` com os dados do ocupante.
- **R2 — Só status bloqueantes contam:** `realizado`/`cancelado`/`falta` liberam o slot (índice
  parcial `idx_nutri_appts_prof_active` WHERE status IN agendado/confirmado).
- **R3 — `end_at` materializado em Java** no INSERT (`start_at + duration`; default 60 min) — nunca
  coluna GENERATED.
- **R4 — Consulta inteira dentro da janela** `opens_at ≤ início` E `fim ≤ closes_at` no fuso
  America/Sao_Paulo → 400 `outside_hours` (`requireInsideHours`).
- **R5 — 1 plano ATIVO por paciente:** índice parcial UNIQUE `uniq_active_plan_per_patient`
  (WHERE status='ativo'); criar plano ativo ou reativar arquivado ARQUIVA o ativo anterior NA MESMA
  transação, ANTES do insert (libera o índice) — `NutriPlanService.create`/`activate`.
- **R6 — Body do plano só é escrito pelo painel:** `nutri_plans` sem policy de INSERT para
  `authenticated` (INSERT via Spring/service_role); a IA não tem NENHUM caminho de escrita; o
  contexto indica QUE existe plano ativo, NUNCA injeta o corpo (`NutriContextCache`).
- **R7 — Barreira de contato:** entrega de plano e confirmação de consulta só agem se a entidade
  pertence ao contato DA CONVERSA (`EntregaPlanoHandler`, `ConfirmacaoNutriHandler`).
- **R8 — Snapshots na consulta:** `patient_name`/`patient_phone` + `professional_name` +
  `duration_minutes` congelados — mudar paciente/profissional depois não altera o histórico.
- **R9 — Excluir em uso é bloqueado:** paciente com consulta OU plano (FK restrict dupla) → 409
  `patient_in_use`; profissional com consulta → 409 `professional_in_use`. Caminho preferido:
  arquivar/desativar (sai da visão da IA sem perder histórico).
- **R10 — Inativos não recebem consulta:** 400 `inactive_professional` / `inactive_patient`.

### Máquina de status

```
agendado ──→ confirmado ──→ realizado
   │             │──→ falta
   └──→ cancelado ←──┘        (realizado/cancelado/falta são terminais)
```

| Transição | Quem pode | Notifica o cliente? |
|---|---|---|
| (criação) → agendado | IA (`<consulta_nutri>`) ou tenant (POST manual) | não |
| agendado → confirmado | humano no painel; IA via `<confirmacao_nutri>` (onda 1) | **sim** (tipo/profissional/data/hora) |
| agendado/confirmado → cancelado | humano; IA via `<confirmacao_nutri>` | **sim** ("Para remarcar, é só me chamar") |
| confirmado → realizado | humano; sistema (`auto_complete_enabled`, folga 2h) | não |
| confirmado → falta | humano apenas (julgamento) | não |

Transição fora do diagrama → 409 `invalid_status_transition` (`NutriAppointmentStatus.allowedNext`).

### O que a IA PODE × NUNCA faz (travas da persona)

- **PODE:** agendar consulta (inclusive cadastrando paciente novo com nome + objetivo), informar
  horários livres do contexto, entregar o plano ATIVO de paciente do próprio contato, refletir o
  SIM/desmarcar do lembrete.
- **NUNCA — trava CFN, INEGOCIÁVEL** (`ProfilePromptContext.NUTRI`): cria/calcula/monta/adapta/
  resume plano; dá caloria, macro, porção ou qualquer número nutricional; responde "posso comer X?",
  "quantas calorias tem Y?", "isso engorda?"; opina sobre patologia, suplementação, emagrecimento,
  ganho de massa ou restrição — toda dúvida nutricional vira convite a agendar.
- **GUARDA de transtorno alimentar (permanente):** diante de compulsão, purga, contagem obsessiva,
  peso-meta extremo ou sofrimento com comida/corpo — NÃO dá números, NÃO valida a conduta, acolhe
  sem reforçar, encaminha ao nutricionista (sinal de risco → sugerir apoio profissional de saúde);
  NUNCA fornece técnica de restrição/compensação.
- Também nunca: confirma/cancela consulta sem o cliente pedir; marca `realizado`/`falta`; reescreve
  ou comenta o plano entregue.

### Tags de IA

| Tag | Quando a IA emite | Campos | O que o backend descarta/recalcula |
|---|---|---|---|
| `<consulta_nutri>` | cliente confirmou prof/dia/hora (2 modos) | `professional_id`, `patient_id` OU `new_patient{name, goal}`, `appointment_type` (primeira\|retorno\|avaliacao), `date`, `start_time`, `notes` | duração NÃO vem da tag (default 60, snapshot); ativos/janela/conflito revalidados; `new_patient` só cadastra no contato da conversa |
| `<entrega_plano>` | paciente pediu o plano e há ativo | `patient_id` | corpo NUNCA vem da tag — o backend resolve o plano ativo e envia o `body` VERBATIM; barreira de contato |
| `<confirmacao_nutri>` | resposta ao lembrete D-1 (onda 1) | `appointment_id`, `decisao` (confirmado\|cancelado) | barreira de contato; máquina de status valida |

Parseadas por regex (nunca tool calling), removidas antes do envio; falha → no-op + warn.

### Validações e erros

| reason | HTTP | Significado de negócio | Cenário que dispara |
|---|---|---|---|
| `forbidden_wrong_profile` | 403 | tenant não é nutri | qualquer `/api/nutri/**` com outro perfil |
| `conflict_slot` | 409 | slot do profissional ocupado (com detalhes) | criar consulta sobrepondo ativa |
| `outside_hours` | 400 | consulta não cabe na janela opens/closes | 17h30 com fechamento 18h e duração 60 min |
| `invalid_type` | 400 | tipo fora de primeira\|retorno\|avaliacao | POST com tipo desconhecido |
| `inactive_professional` / `inactive_patient` | 400 | entidade desativada/arquivada | agendar com inativo |
| `professional_not_found` / `patient_not_found` / `appointment_not_found` / `plan_not_found` / `contact_not_found` | 404 | entidade inexistente/de outro tenant | id errado |
| `no_active_plan` | 404 | paciente sem plano vigente | GET `/api/nutri/plans/active` sem ativo |
| `invalid_status` / `invalid_status_transition` | 400 / 409 | status desconhecido; transição proibida | PATCH de status inválido |
| `patient_in_use` / `professional_in_use` | 409 | tem consultas/planos (FK restrict) | DELETE com histórico |
| `invalid_date` / `invalid_time` / `invalid_hours` | 400 | data/hora malformada; opens ≥ closes | payload/config inválida |

### Notificações ao cliente

- **Envia:** `confirmado` (tipo + profissional + data/hora), `cancelado` (texto fixo de remarque),
  lembrete de véspera (#1) e convite de retomada (#2, opt-in). Textos fixos, acolhedores e SEM
  conteúdo nutricional — os disparos são logística de agenda.
- **Silêncio:** `agendado` (criação), `realizado` (inclusive a auto-transição) e `falta` (quem faltou
  não recebe sermão); consulta manual sem `conversation_id` é marcada sem envio.
- Best-effort (`NutriAppointmentNotifier`): falha de envio nunca reverte o status persistido.

## Dados e snapshots

- **`nutri_professionals`** — `name` 1–200; `crn`/`specialty` texto livre; `active=false` sai da
  visão da IA; delete restrito por consultas (R9).
- **`nutri_config`** (1:1) — `opens_at`/`closes_at` (08:00/18:00), `buffer_minutes ≥ 0`; onda 1:
  `reminder_enabled` (ON), `auto_complete_enabled` (ON), `reengagement_enabled` (**OFF**),
  `reengagement_days` 7–365 (30). Ausência de linha = defaults.
- **`nutri_patients`** — sub-entidade do contact (nível 1, `contact_id NOT NULL` restrict);
  `goal`/`dietary_restrictions`/`notes` texto livre administrativo SEM número nutricional;
  `active=false` arquiva sem perder histórico; `reengagement_sent_at` (onda 1).
- **`nutri_plans`** — sub-entidade do PACIENTE (nível 2 — primeiro perfil com aninhamento duplo);
  `body` markdown do profissional; `status` ativo|arquivado + índice parcial UNIQUE (R5); tenant só
  SELECT/UPDATE via RLS (INSERT backend).
- **`nutri_appointments`** — snapshots (R8); `end_at` materializado; `appointment_type` CHECK;
  `reminded_start_at` (onda 1); `contact/conversation` `on delete set null`; tenant só SELECT/UPDATE.
- **Cache:** `NutriContextCache` (Caffeine, **TTL 20s**, max 1000) por `(company, contato)`;
  invalidado explicitamente em TODA mutação de profissional/paciente/plano/consulta/config; NUNCA
  injeta o body do plano — só a indicação de que existe.

## Features de onda (backlog implementado — migration 99)

`NutriReminderJob`, cron default 10h40 (`nutri.reminder-cron`):

- **#1 Lembrete D-1 + confirmação:** varre `agendado`/`confirmado` de amanhã (America/Sao_Paulo);
  idempotência por `reminded_start_at <> start_at` (remarcar REARMA); sem canal marca sem envio.
  Toggle `reminder_enabled` — **default ON**. A resposta fecha o loop via `<confirmacao_nutri>`;
  cancelar LIBERA o slot e dispara a notificação padrão.
- **#2 Régua de retomada (OPT-IN, default OFF — lição Baileys):** paciente ATIVO sem consulta futura
  ativa e com a última `realizado` além de `reengagement_days` → UM convite por ciclo
  (`reengagement_sent_at`, re-armado por realizada posterior); canal = conversa mais recente do contato.
- **#5 Auto-transição (default ON):** `confirmado` com `end_at` vencido há 2h+ (`GRACE_HOURS`) →
  `realizado`, silencioso. `agendado` passado NUNCA vira `falta` (julgamento humano).

## O que NÃO existe (limites honestos)

- Plano estruturado em refeições/porções, cálculo de calorias/macros/TMB, tabela TACO, antropometria
  com evolução, prescrição de suplemento, anamnese clínica estruturada, bioimpedância — cálculo
  nutricional personalizado permanece conduta exclusiva do nutricionista, por segurança.
- Foto/upload (bloqueador SERVICE_ROLE_KEY), pagamento online.
- **Buffer efetivo:** `buffer_minutes` existe na config mas NÃO entra no conflito nem nos slots do
  contexto (fixo em 0 na prática — só é gravado e auditado).
- Lembrete "sua consulta é em 1h", cobrança de falta, fuso configurável (America/Sao_Paulo
  hardcoded), textos de notificação personalizáveis.
