# SalãoBot — regras de negócio (salon, camada 7.5)

[← Catálogo](../05-nichos.md) · Chassi: A — agenda com conflito POR PROFISSIONAL (inaugura) · Guia operacional: docs/PERFIL_SALAO.md · Migrations: 34, 90

## O negócio em 3 linhas

O tenant é um salão de beleza com N profissionais e um catálogo de serviços, cada um com duração
própria. O cliente pede um serviço pelo WhatsApp; a IA sugere profissionais e horários livres e
marca o agendamento. A escapada estrutural: **cada profissional tem sua própria agenda** — dois
clientes no mesmo horário com profissionais diferentes não conflitam.

## Jornada no WhatsApp (cenários)

1. Cliente pede um serviço ("quero fazer unha sábado"). O contexto injetado traz serviços ativos
   (com preço quando informado), profissionais ativos e os slots livres DE CADA profissional nos
   próximos 7 dias.
2. A IA sugere profissionais disponíveis para o horário (se houver mais de um) e confirma serviço +
   profissional + dia + hora.
3. Na confirmação, emite `<agendamento>{professional_id, service_id, date, start_time, notes}`.
   O `AgendamentoConfirmHandler` parseia; `guest_name` vem do contato (snapshot). O backend valida
   profissional/serviço (existem E ativos), janela com a **duração do serviço**, e o conflito por
   profissional re-checado na transação. Agendamento nasce **agendado**.
4. O salão confirma/cancela no painel — o cliente é notificado (a confirmação inclui o nome do
   profissional).
5. **Exceções best-effort:** conflito na corrida, profissional/serviço inativo ou fora do horário →
   agendamento não criado, warn, mensagem da IA segue (tenant contorna manualmente).
6. **Onda 1:** lembrete de véspera com texto fixo ("seu horário com {profissional} é amanhã às
   {hora} — podemos confirmar?"). A resposta cai no fluxo inbound normal — **não há tag de mutação
   de status no salon** (ver lacuna em "O que NÃO existe").

## Regras de negócio

### Transacionais (invariantes duras)

- **R1 — Conflito de slot half-open POR `professional_id`:** agendamentos ativos
  (`agendado`/`confirmado`) do MESMO profissional não sobrepõem `[start_at, end_at)` — `NOT
  (end_at <= :ini OR start_at >= :fim)`, RE-VERIFICADO dentro da transação de INSERT
  (`SalonAppointmentRepository`) → 409 `conflict_slot`. Profissionais diferentes = paralelismo OK
  (índice parcial `idx_salon_appts_prof_active` por profissional).
- **R2 — Duração vem do SERVIÇO** (`salon_offerings.duration_minutes`, 15–480 por CHECK), não de
  config global — e entra como SNAPSHOT no agendamento.
- **R3 — `end_at` materializado em Java** no INSERT (nunca coluna GENERATED).
- **R4 — Snapshots quádruplos no agendamento:** `professional_name`, `service_name`, `price_cents`
  e `duration_minutes` congelados — renomear/reprecificar depois não altera o histórico.
- **R5 — Profissional e serviço têm de existir E estar ativos** no CREATE → 404
  `professional_not_found`/`service_not_found`, 400 `inactive_professional`/`inactive_service`.
- **R6 — Agendamento inteiro dentro da janela** opens/closes (default 09:00/20:00) no fuso
  America/Sao_Paulo → 400 `outside_hours`.
- **R7 — INSERT só pelo backend** (sem policy de INSERT para `authenticated`).
- **R8 — Profissional/serviço com agendamentos não são excluídos** (FK restrict) → 409
  `professional_in_use`/`service_in_use`; o caminho é inativar.
- **R9 — Cliente NÃO é entidade própria** (decisão cravada — alta rotatividade): o histórico vem do
  `contact_id` + snapshots `guest_name`/`guest_phone`.

### Máquina de status

```
agendado ──→ confirmado ──→ realizado
   │             │──→ falta
   └──→ cancelado ←──┘          (realizado/cancelado/falta são terminais)
```

| Transição | Quem pode | Notifica o cliente? |
|---|---|---|
| (criação) → agendado | IA (`<agendamento>`) ou tenant (POST manual) | não |
| agendado → confirmado | **humano no painel apenas** | **sim** (data/hora/profissional) |
| agendado/confirmado → cancelado | humano apenas | **sim** (texto fixo) |
| confirmado → realizado | humano; sistema (`auto_complete_enabled`, opt-in) | não |
| confirmado → falta | humano apenas | não |

Fora do diagrama → 409 `invalid_status_transition` (`SalonAppointmentStatus.allowedNext`).

### O que a IA PODE × NUNCA faz (travas da persona)

- **PODE:** apresentar serviços (com preço só quando cadastrado), sugerir profissionais disponíveis,
  criar agendamento `agendado`.
- **NUNCA:** recomenda serviço que o cliente não pediu, opina sobre a aparência do cliente, promete
  resultado estético (`ProfilePromptContext.SALON` — tom acolhedor, sem julgamento); não muda status
  de agendamento (nenhuma tag de mutação existe no perfil); não expõe preço de serviço sem
  `price_cents` cadastrado.

### Tags de IA

| Tag | Quando a IA emite | Campos | O que o backend descarta/recalcula |
|---|---|---|---|
| `<agendamento>` | cliente confirmou serviço + profissional + dia/hora | `professional_id`, `service_id`, `date`, `start_time`, `notes` | duração/preço/nomes NÃO vêm da tag (snapshots do offering/professional); `guest_name` do contato; ativo/janela/conflito revalidados |

Única tag do perfil. Regex + strip antes do envio; falha → `Optional.empty()` + warn.

### Validações e erros

| reason | HTTP | Significado de negócio | Cenário que dispara |
|---|---|---|---|
| `forbidden_wrong_profile` | 403 | tenant não é salon | `/api/salon/**` com outro perfil |
| `conflict_slot` | 409 | profissional ocupado no horário | criar agendamento sobrepondo ativa do MESMO profissional |
| `outside_hours` | 400 | não cabe em opens/closes | serviço de 4h começando 1h antes de fechar |
| `inactive_professional` / `inactive_service` | 400 | recurso desativado pelo tenant | agendar com profissional/serviço inativo |
| `professional_not_found` / `service_not_found` / `appointment_not_found` | 404 | entidade inexistente/de outro tenant | id errado |
| `invalid_status_transition` | 409 | transição proibida | realizado → confirmado |
| `invalid_status` | 400 | status alvo desconhecido | PATCH com status inválido |
| `professional_in_use` / `service_in_use` | 409 | referenciado por agendamento (FK restrict) | DELETE com histórico |
| `invalid_date` / `invalid_time` / `invalid_hours` | 400 | data malformada; opens ≥ closes | payload/config inválido |

### Notificações ao cliente

- **Envia** em `confirmado` ("confirmado pra {data} às {hora} com {profissional}. Te esperamos!") e
  `cancelado` ("Pra reagendar, é só me chamar") — textos fixos e defensivos (sem promessa estética).
- **Silêncio** em `agendado`, `realizado` e `falta`; agendamento manual sem `conversation_id` não
  notifica.
- Best-effort (`SalonAppointmentNotifier`): falha de envio nunca reverte o status.

## Dados e snapshots

- **`salon_professionals`** — `name` 1–200 CHECK; `specialty` texto livre; `active` controla a visão
  da IA; índice parcial por company WHERE active.
- **`salon_offerings`** — `name` 1–200; `duration_minutes` 15–480 CHECK; `price_cents` NULLABLE
  (preço não exposto pela IA quando ausente); "offering" no backend pra não colidir com o Spring
  `SalonService`.
- **`salon_config`** (1:1) — `opens_at`/`closes_at` (09:00/20:00), `buffer_minutes ≥ 0`; onda 1:
  `reminder_enabled` (true), `auto_complete_enabled` (**false** — opt-in).
- **`salon_appointments`** — snapshots R4 + `guest_name`/`guest_phone`; `end_at` materializado;
  `reminded_start_at` (marker do lembrete); índices por company/status, por profissional (parcial,
  conflito) e por contato.
- **Cache:** `SalonContextCache` (Caffeine, **TTL 20s**) por company; invalidado em toda mutação de
  profissional/serviço/agendamento/config. Janela de contexto: 7 dias, slots POR profissional.

## Features de onda (backlog implementado)

Migration 90 (`SalonReminderJob`, cron default `0 30 9 * * *`):

- **Lembrete de véspera (#1):** agendamentos `agendado`/`confirmado` com `start_at` amanhã
  (America/Sao_Paulo) recebem mensagem fixa pela conversa. Idempotência por `reminded_start_at`
  (par agendamento+horário) — **remarcar rearma**. Sem canal (POST manual) marca sem envio. Toggle
  `reminder_enabled` (default LIGADO).
- **Auto-transição opt-in (#7):** `confirmado` com `end_at` no passado → `realizado` (silencioso).
  Toggle `auto_complete_enabled` (**default DESLIGADO** — mexer em status sozinho é decisão
  consciente do tenant). `agendado` passado NÃO vira falta (falta só existe a partir de confirmado
  e é julgamento humano).

## O que NÃO existe (limites honestos)

- **Confirmação/remarcação por tag:** o comentário da migration 90 diz que "a IA confirma/remarca
  pela tag `<agendamento>`", mas o handler SÓ CRIA agendamento — não existe tag de mutação de status
  no salon; confirmar segue 100% humano no painel (divergência doc×código registrada).
- Cadastro formal de cliente, comissão de profissional, pagamento, fidelidade/cashback.
- Foto do trabalho (bloqueador de Storage), estoque de produtos, multi-loja.
- Atendimento em paralelo pelo MESMO profissional (modelo: 1 cadeira por profissional).
- **Buffer efetivo:** `buffer_minutes` existe na config mas não entra no cálculo de conflito.
- Fuso configurável, textos de notificação personalizados.
