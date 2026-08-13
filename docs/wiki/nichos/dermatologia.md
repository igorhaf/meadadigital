# DermaBot — regras de negócio (dermatologia, camada 8.11)

[← Catálogo](../05-nichos.md) · Chassi: A (por profissional) + F (entrega read-only) + G (sub-entidade do contato) · Guia operacional: docs/PERFIL_DERMATOLOGIA.md · Migrations: 55, 110

## O negócio em 3 linhas

O tenant é uma clínica dermatológica. O paciente chega pelo WhatsApp, é identificado pelo telefone,
e a IA agenda consultas (primeira consulta, retorno, procedimento — cada tipo com SUA duração) e
entrega, verbatim, a orientação de preparo que a dermatologista gravou. A IA **nunca** exerce ato médico.

## Jornada no WhatsApp (cenários)

1. Paciente pede horário. A IA consulta o contexto (dermatologistas, tipos de atendimento com duração,
   agenda, pacientes já cadastrados do contato) e negocia profissional + tipo + dia/hora.
2. Na confirmação, a IA emite `<consulta_derma>` em um de **2 modos**: `patient_id` existente OU
   `new_patient` (cadastra o paciente como sub-entidade do contato E agenda no mesmo turno). O
   `AgendamentoDermaConfirmHandler` valida tudo e grava a consulta **agendada** (silenciosa).
3. Se o tipo tem `prep_instructions`, a IA pode emitir `<entrega_preparo>{appointment_id}` — o
   `EntregaPreparoHandler` envia a nota **VERBATIM** via `notifier.sendText` (não passa pela IA).
4. A clínica confirma/cancela no painel — o paciente é notificado (texto sem conteúdo clínico).
5. **Onda 1:** na véspera o `DermatologiaReminderJob` pergunta "confirma?" e, quando o tipo tem
   preparo, envia a nota junto (verbatim). A resposta fecha o loop via `<confirmacao_derma>`.
6. **Exceções (best-effort, sem mensagem de erro ao cliente):** profissional/tipo inexistente ou
   inativo, horário fora da janela, slot em conflito (re-checado na transação), contato divergente na
   entrega/confirmação → nada é criado/entregue, warn no log, a resposta segue normal.
7. **Foto de lesão / sintoma / "é grave?"** → a IA NÃO avalia: acolhe, explica que avaliação exige
   consulta presencial e oferece agendar. Sinal de alarme (lesão que muda/sangra/cresce) → orienta
   urgência SEM nomear condição nem opinar gravidade.

## Regras de negócio

### Transacionais (invariantes duras)

- **R1 — Conflito de slot POR `professional_id`:** duas consultas ativas (`agendada`/`confirmada`)
  do mesmo profissional não se sobrepõem — janela half-open, RE-VERIFICADA dentro da transação de
  INSERT → 409 `conflict_slot` com o ocupante (`appointmentId`/`patientName`/`startAt`/`endAt`).
  Mesmo horário com profissional DIFERENTE → OK (paralelismo).
- **R2 — Só status bloqueantes contam:** `realizada`/`cancelada`/`falta` liberam o slot (índice
  parcial `idx_derma_appts_company_prof_active` WHERE status IN agendada/confirmada).
- **R3 — Duração vem do TIPO, não da config** (escapada): `dermatologia_procedure_types.duration_minutes`
  (CHECK 5–480), snapshotada na consulta. `end_at` MATERIALIZADO em Java no INSERT (nunca GENERATED).
- **R4 — Consulta inteira dentro da janela** `opens_at`..`closes_at` (America/Sao_Paulo) → 400
  `outside_hours`.
- **R5 — Paciente é sub-entidade do contato** (`contact_id NOT NULL`, FK restrict); a tag em modo
  `new_patient` só cadastra se a conversa tem contato resolvido.
- **R6 — Snapshots congelados:** `patient_name`/`patient_phone`/`professional_name`/
  `procedure_type_name`/`duration_minutes` na consulta — alterar o tipo depois NÃO altera consultas.
- **R7 — Exclusão em uso bloqueada** (FK restrict) → 409 `professional_in_use`/`patient_in_use`/
  `procedure_type_in_use`; o caminho é arquivar (`active = false`).
- **R8 — INSERT de consulta só pelo backend:** `dermatologia_appointments` sem policy de INSERT
  para `authenticated` — nasce via IA ou POST manual do tenant na API Spring.

### Máquina de status

```
agendada ──→ confirmada ──→ realizada
   │             │──→ falta
   └──→ cancelada ←──┘        (realizada/cancelada/falta são terminais)
```

| Transição | Quem pode | Notifica o cliente? |
|---|---|---|
| (criação) → agendada | IA (`<consulta_derma>`) ou tenant (POST manual) | não |
| agendada → confirmada | humano no painel; IA via `<confirmacao_derma>` (reflete o SIM do lembrete) | **sim** (tipo+profissional+data/hora) |
| agendada/confirmada → cancelada | humano; IA via `<confirmacao_derma>` | **sim** ("Para remarcar, é só me chamar") |
| confirmada → realizada | humano; sistema (`auto_complete_enabled`, `end_at` vencido) | não |
| confirmada → falta | **humano apenas** (a variante automática foi descartada por segurança) | não |

Transição fora do diagrama → 409 `invalid_status_transition` (`DermatologiaAppointmentStatus.allowedNext`).

### O que a IA PODE × NUNCA faz (travas da persona)

- **PODE:** agendar consulta (inclusive cadastrando paciente novo), entregar a nota de preparo
  gravada (verbatim), informar horários/tipos/profissionais do contexto, refletir o SIM/cancelar do
  lembrete, orientar busca de avaliação **com urgência** em sinal de alarme (sem nomear a condição).
- **NUNCA** (`ProfilePromptContext.DERMATOLOGIA`): dá diagnóstico; avalia/classifica/interpreta
  lesão, mancha, pinta, acne, micose, queda de cabelo, unha ou sintoma; avalia FOTO de lesão;
  recomenda tratamento/medicação/ácido/pomada/protetor/dermocosmético/procedimento; opina "é grave/é
  normal/é câncer/não é nada". O único conteúdo clínico que entrega é o preparo read-only.

### Tags de IA

| Tag | Quando a IA emite | Campos | O que o backend descarta/recalcula |
|---|---|---|---|
| `<consulta_derma>` | paciente confirmou profissional+tipo+dia/hora | `professional_id`, `procedure_type_id`, `date`, `start_time`, `patient_id` OU `new_patient{name, birth_date?}`, `notes` | duração vem do TIPO (snapshot); janela/conflito/atividade revalidados |
| `<entrega_preparo>` | paciente pede a orientação de preparo | `appointment_id` | conteúdo é a `prep_instructions` VERBATIM; BARREIRA DE CONTATO (consulta de outro contato → bloqueado); sem preparo → não entrega |
| `<confirmacao_derma>` | paciente respondeu ao lembrete D-1 (onda 1) | `appointment_id`, `decisao` (confirmada\|cancelada) | BARREIRA DE CONTATO; máquina de status valida |

Todas por regex (nunca tool calling), removidas antes do envio; falha → `Optional.empty()` + warn.

### Validações e erros

| reason | HTTP | Significado de negócio | Cenário que dispara |
|---|---|---|---|
| `forbidden_wrong_profile` | 403 | tenant não é dermatologia | `/api/dermatologia/**` com outro perfil |
| `conflict_slot` | 409 | profissional ocupado no horário (com detalhes) | POST manual sobrepondo consulta ativa |
| `outside_hours` | 400 | consulta não cabe na janela opens/closes | 17h30 com fechamento 18h e tipo de 60min |
| `inactive_professional` / `inactive_patient` / `inactive_procedure_type` | 400 | entidade arquivada | agendar com registro `active = false` |
| `professional_not_found` / `patient_not_found` / `procedure_type_not_found` / `appointment_not_found` / `contact_not_found` | 404 | inexistente ou de outro tenant | id errado |
| `professional_in_use` / `patient_in_use` / `procedure_type_in_use` | 409 | tem consultas (FK restrict) | DELETE com histórico |
| `invalid_status_transition` | 409 | transição proibida | realizada → confirmada |
| `invalid_status` / `invalid_date` / `invalid_duration` / `invalid_hours` / `invalid_time` | 400 | entrada malformada | status desconhecido; duração fora de 5–480; opens ≥ closes |

### Notificações ao cliente

- **Envia** em `confirmada` ("Consulta confirmada: {tipo} com {profissional} em {data} às {hora}") e
  `cancelada` — textos fixos, acolhedores, SEM conteúdo clínico.
- **Silêncio** em `agendada` (o paciente acabou de combinar no chat), `realizada`, `falta` (quem
  furou não recebe sermão) e em consulta manual sem `conversation_id`.
- Best-effort (`DermatologiaAppointmentNotifier`): falha de envio nunca reverte o status.

## Dados e snapshots

- **`dermatologia_professionals`** — nome 1–200; `active` controla vitrine; specialty/crm_rqe livres.
- **`dermatologia_procedure_types`** (a escapada) — nome 1–120; `duration_minutes` CHECK 5–480;
  `prep_instructions` nullable (vazio = sem preparo); tabela, NÃO enum.
- **`dermatologia_patients`** — `contact_id NOT NULL` (sub-entidade); `notes` ADMINISTRATIVO (LGPD:
  não é prontuário); `recall_reminded_at` (onda 1).
- **`dermatologia_appointments`** — snapshots (R6); `end_at` materializado; `reminded_start_at`
  (onda 1); tenant só SELECT/UPDATE via RLS; `contact_id`/`conversation_id` `on delete set null`.
- **`dermatologia_config`** (1:1) — opens/closes (08:00/18:00), `buffer_minutes ≥ 0` + toggles da
  onda 1. Ausência de linha = defaults (`coalesce` nos jobs).
- **Cache:** `DermatologiaContextCache` (Caffeine, **TTL 30s**, max 1000), invalidado explicitamente
  em toda mutação de profissional/tipo/paciente/consulta/config.

## Features de onda (backlog implementado)

Migration 110 (`DermatologiaReminderJob`, cron `${dermatologia.reminder-cron:0 10 11 * * *}`):

- **#1 Lembrete D-1 + confirmação + PREPARO:** véspera de `agendada`/`confirmada` → pede SIM e,
  quando o tipo tem `prep_instructions`, envia a nota JUNTO, **verbatim** (preparo mal feito queima
  dois slots). Idempotência por `reminded_start_at` (= start_at; **remarcar REARMA**). Sem canal →
  marca sem envio. Toggle `reminder_enabled` — **default LIGADO**. Resposta via `<confirmacao_derma>`.
- **#5 Auto-realizada:** `confirmada` com `end_at` vencido → `realizada` silenciosa (UPDATE em massa;
  toggle `auto_complete_enabled`, **default LIGADO**). A variante "→ falta" ficou FORA por segurança —
  falta é sempre julgamento humano.
- **#2 Recall de retorno (opt-in `recall_enabled` — default DESLIGADO,** lição Baileys): paciente
  ativo cuja última consulta REALIZADA é mais antiga que `recall_months` (default 6, CHECK 1–36) e
  sem consulta futura ativa → 1 convite administrativo por episódio (`recall_reminded_at` re-armado
  por consulta realizada mais nova). Sem conversa → marca sem envio.

## O que NÃO existe (limites honestos)

- Prontuário/laudo/dermatoscopia, foto de lesão ou antes/depois (bloqueador de upload), receituário,
  biópsia com resultado — a IA e o schema são administrativos por design (LGPD).
- Pacote multi-sessão (é o perfil estetica), pagamento/sinal (Stripe #50), fila de encaixe, NPS.
- Cancelamento espontâneo pela IA: só existe o cancelamento que REFLETE a resposta do paciente ao
  lembrete (`<confirmacao_derma>`); fora disso, cancelar é ação do painel.
- **Buffer efetivo:** `buffer_minutes` existe na config mas NÃO entra no cálculo de conflito.
- **Folga no auto-realizada:** ao contrário de restaurant/concessionaria (2h de graça), aqui a
  transição ocorre assim que `end_at` passa (sem grace no código).
