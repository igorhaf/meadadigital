# DentalBot — regras de negócio (dental, camada 7.4)

[← Catálogo](../05-nichos.md) · Chassi: A — agenda por company (+ paciente como sub-entidade) · Guia operacional: docs/PERFIL_DENTAL.md · Migrations: 33, 116

## O negócio em 3 linhas

O tenant é uma clínica odontológica (modelo de 1 dentista por clínica). O paciente fala pelo
WhatsApp; a IA o reconhece pelo telefone (contato → paciente), informa as próximas consultas e
agenda novas — com **trava clínica inegociável**: nunca diagnóstico, nunca conduta, e o
**cancelamento pela IA é bloqueado**.

## Jornada no WhatsApp (cenários)

1. Paciente escreve; a IA resolve `contact_id` → `dental_patients` e injeta no contexto o nome, as
   próximas consultas do paciente e os slots livres dos próximos 14 dias (máx. 8 slots/dia no prompt).
2. Dúvida clínica (dor, sintoma, "que tratamento faço?") → a IA NÃO responde o mérito; oferece
   agendar avaliação ("vou pedir que o dentista avalie").
3. Na confirmação de dia/hora/tipo, a IA emite `<consulta>{date, start_time, type, notes}`.
   O `ConsultaConfirmHandler` resolve o paciente pelo contato — **sem paciente vinculado, NÃO cria**
   (warn) — e o backend valida janela + conflito, gravando a consulta **agendada**.
4. Pedido de desmarcação → a IA NÃO cancela; encaminha ao consultório ("ele entra em contato pra
   confirmar o cancelamento"). Cancelar é ação humana no painel.
5. **Exceções best-effort:** conflito na corrida, fora do horário ou paciente não identificado →
   consulta não criada, warn no log, mensagem da IA segue sem tag (tenant contorna manualmente).
6. **Onda 1:** na véspera, o `DentalReminderJob` pede confirmação (SIM). A resposta gera
   `<confirmacao_consulta>{appointment_id}` — que SÓ confirma; a resposta "não" não cancela nada
   pela IA (trava mantida).

## Regras de negócio

### Transacionais (invariantes duras)

- **R1 — Conflito de slot half-open POR COMPANY** (1 dentista/clínica): consultas ativas
  (`agendada`/`confirmada`) não sobrepõem `[start_at, end_at)` — `NOT (end_at <= :ini OR start_at
  >= :fim)`, RE-VERIFICADO dentro da transação de INSERT (`DentalAppointmentRepository`) → 409
  `conflict_slot` com quem ocupa e de que horas a que horas.
- **R2 — Status terminais liberam o slot:** índice parcial `idx_dental_appts_company_active`
  WHERE status IN (agendada, confirmada).
- **R3 — `end_at` materializado em Java** no INSERT (nunca coluna GENERATED).
- **R4 — `duration_minutes` é snapshot do config:** mudanças na config só afetam consultas futuras.
- **R5 — Consulta inteira dentro da janela** opens/closes no fuso America/Sao_Paulo → 400
  `outside_hours`.
- **R6 — Consulta exige paciente:** `patient_id NOT NULL` FK restrict; a IA só cria consulta para
  paciente JÁ vinculado ao contato (`dental_patients.contact_id`) — a tag NÃO tem modo `new_patient`;
  cadastro de paciente é exclusivo do painel.
- **R7 — Paciente com consultas não é excluído** (FK restrict) → 409 `patient_in_use`.
- **R8 — INSERT de consulta só pelo backend** (sem policy de INSERT para `authenticated`).
- **R9 — LGPD:** `notes` (paciente e consulta) e `type` são dados ADMINISTRATIVOS — nada clínico é
  modelado (sem prontuário/diagnóstico/alergia); `type` 1–100 chars, `name` 1–200 chars por CHECK.

### Máquina de status

```
agendada ──→ confirmada ──→ realizada
   │             │──→ falta
   └──→ cancelada ←──┘          (realizada/cancelada/falta são terminais)
```

| Transição | Quem pode | Notifica o cliente? |
|---|---|---|
| (criação) → agendada | IA (`<consulta>`) ou tenant (POST manual) | não (paciente já viu no chat) |
| agendada → confirmada | humano no painel; IA via `<confirmacao_consulta>` (onda 1) | **sim** (data/hora) |
| agendada/confirmada → cancelada | **humano APENAS** (IA bloqueada — trava) | **sim** (texto fixo) |
| confirmada → realizada | humano; sistema (`auto_complete_enabled`) | não |
| confirmada → falta | humano apenas | não |

Fora do diagrama → 409 `invalid_status_transition` (`AppointmentStatus.allowedNext`).

### O que a IA PODE × NUNCA faz (travas da persona)

- **PODE:** reconhecer o paciente, informar as consultas DELE, oferecer slots livres, criar consulta
  `agendada`, confirmar (só confirmar) em resposta ao lembrete D-1.
- **NUNCA:** dá diagnóstico, plano de tratamento ou recomendação de procedimento; discute sintoma
  (encaminha ao dentista — `ProfilePromptContext.DENTAL`); **cancela consulta** (única agenda em que
  a tag de confirmação não aceita `decisao=cancelada`); cria/edita paciente; marca realizada/falta.

### Tags de IA

| Tag | Quando a IA emite | Campos | O que o backend descarta/recalcula |
|---|---|---|---|
| `<consulta>` | paciente confirmou dia/hora/tipo | `date`, `start_time`, `type`, `notes` | paciente resolvido pelo CONTATO (não vem da tag); duração do config (snapshot); janela/conflito revalidados |
| `<confirmacao_consulta>` | paciente respondeu SIM ao lembrete (onda 1) | `appointment_id` | SÓ agendada→confirmada; BARREIRA via `patient.contact_id` = contato da conversa; sem campo de decisão — cancelar não existe |

Regex + strip antes do envio; falha → `Optional.empty()` + warn.

### Validações e erros

| reason | HTTP | Significado de negócio | Cenário que dispara |
|---|---|---|---|
| `forbidden_wrong_profile` | 403 | tenant não é dental | `/api/dental/**` com outro perfil |
| `conflict_slot` | 409 | agenda ocupada (1 dentista) | criar consulta sobrepondo ativa |
| `outside_hours` | 400 | consulta não cabe em opens/closes | 17h45 com fechamento 18h e duração 30min |
| `invalid_status_transition` | 409 | transição proibida | cancelada → confirmada |
| `invalid_status` | 400 | status alvo desconhecido | PATCH com id de status inválido |
| `patient_not_found` / `appointment_not_found` | 404 | entidade inexistente/de outro tenant | id errado |
| `patient_in_use` | 409 | paciente tem consultas (FK restrict) | DELETE de paciente com histórico |
| `invalid_date` / `invalid_time` / `invalid_hours` | 400 | data malformada; opens ≥ closes; birth_date inválida | payload/config inválido |

### Notificações ao cliente

- **Envia** em `confirmada` ("Sua consulta foi confirmada pra {data} às {hora}. Te esperamos!") e
  `cancelada` ("Pra reagendar, é só me chamar") — textos fixos, sem conteúdo clínico.
- **Silêncio** em `agendada` (o paciente acabou de combinar no chat), `realizada` e `falta` (quem
  furou não recebe sermão); consulta manual sem `conversation_id` não notifica (sem canal).
- Best-effort (`DentalAppointmentNotifier`): falha de envio nunca reverte o status.

## Dados e snapshots

- **`dental_patients`** — sub-entidade do contato (`contact_id` nullable, `on delete set null`);
  CPF/`document` sem máscara; `notes` administrativo (LGPD); índice parcial por
  `(company_id, contact_id)` p/ resolução telefone→paciente.
- **`dental_clinic_config`** (1:1) — `duration_minutes` 15–240 (default 30), `buffer_minutes ≥ 0`,
  `opens_at`/`closes_at` (08:00/18:00); onda 1 acrescenta `reminder_enabled` (true),
  `auto_complete_enabled` (true), `recall_enabled` (false), `recall_months` 1–36 (default 6).
- **`dental_appointments`** — snapshot `duration_minutes`; `end_at` materializado;
  `reminded_start_at` (marker do lembrete — remarcar rearma); índice `(patient_id, start_at desc)`.
- **Cache:** `DentalContextCache` (Caffeine, **TTL 30s**, keyed por `(companyId, contactId)` —
  o contexto é personalizado por paciente); invalidado POR COMPANY em toda mutação de
  paciente/consulta/config. Janela de contexto: 14 dias.

## Features de onda (backlog implementado)

Migration 116 (`DentalReminderJob`, cron default `0 10 12 * * *`):

- **Lembrete D-1 + confirmação (#1):** pede SIM na véspera (agendada/confirmada de amanhã);
  idempotência por `reminded_start_at <> start_at` — **remarcar REARMA**. Toggle `reminder_enabled`
  (default LIGADO). O loop fecha só pra confirmar; desmarcar segue com o consultório.
- **Auto-realizada (#5):** `confirmada` vencida → `realizada` (silenciosa). Toggle
  `auto_complete_enabled` (default LIGADO). A variante "→ falta" ficou DE FORA — falta é humana.
- **Recall de manutenção (#3, opt-in OFF — lição Baileys):** paciente sem consulta `realizada` há
  `recall_months` e sem consulta futura recebe 1 convite por episódio; `recall_reminded_at` é
  re-armado por consulta realizada mais nova que o marker. Toggle `recall_enabled` (default DESLIGADO).

## O que NÃO existe (limites honestos)

- Prontuário, odontograma, plano de tratamento, TUSS, anamnese — dados clínicos ficam pra fase
  futura com criptografia at-rest e log de acesso (LGPD pesada).
- **Multi-dentista:** sem `dentist_id` — o conflito é por consultório; mudar isso é onda estrutural
  própria (backlog #4).
- Cancelamento/remarcação pela IA (trava), falta automática, anexo (raio-X/foto — bloqueador de
  Storage), pagamento/sinal Pix (backlog #2), fuso configurável, textos personalizados.
- **Buffer efetivo:** `buffer_minutes` existe na config mas não entra no cálculo de conflito.
