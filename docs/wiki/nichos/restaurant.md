# MesaBot — regras de negócio (restaurant, camada 7.3)

[← Catálogo](../05-nichos.md) · Chassi: A — agenda com conflito de slot (por company) · Guia operacional: docs/PERFIL_MESABOT.md · Migrations: 32, 91

## O negócio em 3 linhas

O tenant é um restaurante que trabalha com reserva de mesa. O cliente final pede mesa pelo WhatsApp
em linguagem natural; a IA conhece as mesas disponíveis e a agenda dos próximos 7 dias, negocia
dia/hora/mesa/nº de pessoas e cria a reserva como **pendente** — confirmar é ação do restaurante.

## Jornada no WhatsApp (cenários)

1. Cliente pede mesa ("sexta às 20h pra 4"). A IA consulta o bloco de contexto (mesas disponíveis +
   reservas ativas dos próximos 7 dias + config) e responde com opções reais.
2. Se o horário pedido está ocupado, a IA oferece alternativa próxima (30 min antes/depois) ou outra
   mesa livre — instrução da persona (`ProfilePromptContext.RESTAURANT`).
3. Na confirmação verbal, a IA emite `<reserva>{table_id, date, start_time, num_people}` no texto.
   O `ReservationConfirmHandler` parseia (regex), o backend valida mesa/janela/conflito e grava a
   reserva **pendente**. A tag é removida antes do envio; a duração vem do config (snapshot).
4. O restaurante vê a reserva na agenda e confirma/cancela — o cliente é notificado.
5. **Exceções (todas best-effort, sem mensagem de erro ao cliente):** se a mesa não existe, o horário
   está fora do funcionamento ou o slot foi ocupado na corrida (conflito re-checado na transação), a
   reserva NÃO é criada — warn no log, a resposta da IA segue normal e o tenant contorna manualmente
   (risco aceito do MVP).
6. **Onda 1:** na véspera, o `RestaurantReminderJob` pergunta "confirma? SIM ou NÃO". A resposta cai
   no fluxo da IA, que emite `<confirmacao_reserva>{reservation_id, decisao}` — confirmada ou
   cancelada. Cancelar libera o slot na hora.

## Regras de negócio

### Transacionais (invariantes duras)

- **R1 — Conflito de slot half-open por mesa:** duas reservas ativas (`pendente`/`confirmada`) na
  mesma mesa não podem sobrepor `[start_at, end_at)` — `NOT (end_at <= :novoInicio OR start_at >=
  :novoFim)`, RE-VERIFICADO dentro da transação de INSERT (`ReservationRepository.insertReservation`)
  → 409 `conflict_slot` com os detalhes de quem ocupa.
- **R2 — Só status bloqueantes contam:** `realizada`/`cancelada`/`no_show` liberam o slot (índice
  parcial `idx_reservations_table_active` WHERE status IN pendente/confirmada).
- **R3 — `end_at` materializado em Java** no INSERT (`start_at + duration_minutes`) — nunca coluna
  GENERATED (`timestamptz + interval` não é IMMUTABLE).
- **R4 — Duração é snapshot do config:** mudar a config NÃO altera reservas já criadas
  (`duration_minutes` congelado na linha).
- **R5 — Reserva inteira dentro da janela de funcionamento** (`opens_at ≤ início` E `fim ≤
  closes_at`) no fuso America/Sao_Paulo → 400 `outside_hours` (`requireInsideHours`).
- **R6 — INSERT só pelo backend:** `table_reservations` não tem policy de INSERT para
  `authenticated` — reserva nasce via IA ou POST manual do tenant na API Spring (service_role).
- **R7 — Limites por CHECK:** `num_people` 1–50; `capacity` da mesa 1–50; `label` 1–60 chars e
  UNIQUE por `(company_id, label)` → 409 `label_in_use`.
- **R8 — Mesa com reserva não é excluída** (FK `on delete restrict`) → 409 `table_in_use`;
  o caminho é desativar (`available = false`, sai da visão da IA).

### Máquina de status

```
pendente ──→ confirmada ──→ realizada
   │             │──→ no_show
   └──→ cancelada ←──┘          (realizada/cancelada/no_show são terminais)
```

| Transição | Quem pode | Notifica o cliente? |
|---|---|---|
| (criação) → pendente | IA (`<reserva>`) ou tenant (POST manual) | não |
| pendente → confirmada | humano no painel; IA via `<confirmacao_reserva>` (onda 1) | **sim** (data/hora/mesa/pessoas) |
| pendente/confirmada → cancelada | humano; IA via `<confirmacao_reserva>` | **sim** (texto fixo) |
| confirmada → realizada | humano; sistema (`auto_complete_enabled`, folga 2h) | não |
| confirmada → no_show | humano apenas (julgamento) | não |

Transição fora do diagrama → 409 `invalid_status_transition` (`ReservationStatus.allowedNext`).

### O que a IA PODE × NUNCA faz (travas da persona)

- **PODE:** informar mesas/horários do contexto, oferecer alternativa próxima, criar reserva
  `pendente`, refletir a decisão SIM/NÃO do lembrete (confirmar/cancelar a PRÓPRIA reserva do contato).
- **NUNCA:** inventa mesa que não existe, promete horário fora do funcionamento, confirma reserva
  por iniciativa própria (o gate de confirmação original é humano; a tag de confirmação só REFLETE
  a resposta do cliente ao lembrete), marca `realizada`/`no_show`.

### Tags de IA

| Tag | Quando a IA emite | Campos | O que o backend descarta/recalcula |
|---|---|---|---|
| `<reserva>` | cliente confirmou dia/hora/mesa/pessoas | `table_id`, `date`, `start_time`, `num_people` | duração NÃO vem da tag (snapshot do config); mesa/janela/conflito revalidados |
| `<confirmacao_reserva>` | cliente respondeu ao lembrete D-1 (onda 1) | `reservation_id`, `decisao` (confirmada\|cancelada) | BARREIRA DE CONTATO (reserva tem de ser do contato da conversa); máquina de status valida |

Ambas parseadas por regex (nunca tool calling), removidas do texto antes do envio; qualquer falha →
`Optional.empty()` + warn, mensagem segue sem efeito.

### Validações e erros

| reason | HTTP | Significado de negócio | Cenário que dispara |
|---|---|---|---|
| `forbidden_wrong_profile` | 403 | tenant não é restaurant | qualquer `/api/restaurant/**` com outro perfil |
| `conflict_slot` | 409 | slot ocupado na mesa (com detalhes do ocupante) | criar reserva sobrepondo ativa |
| `outside_hours` | 400 | reserva não cabe na janela opens/closes | 22h30 com fechamento 23h e duração 2h |
| `invalid_status_transition` | 409 | transição proibida na máquina | realizada → confirmada |
| `invalid_status` | 400 | status alvo desconhecido | PATCH com status inexistente |
| `table_not_found` / `reservation_not_found` | 404 | entidade inexistente/de outro tenant | id errado |
| `label_in_use` | 409 | nome de mesa duplicado (UNIQUE) | criar "Mesa 1" duas vezes |
| `table_in_use` | 409 | mesa tem reservas (FK restrict) | DELETE de mesa com histórico |
| `invalid_date` / `invalid_time` / `invalid_hours` | 400 | data/hora malformada; opens ≥ closes | query/config inválida |

### Notificações ao cliente

- **Envia** só em `confirmada` (texto parametrizado com data/hora/mesa/pessoas) e `cancelada`
  ("Pra remarcar, é só chamar") — textos fixos e defensivos, sem promessa.
- **Silêncio** em `pendente` (ainda não confirmou), `realizada` e `no_show` (quem furou não recebe
  sermão) e em reserva manual sem `conversation_id` (não há canal).
- Best-effort por contrato (`ReservationNotifier`): falha de envio nunca reverte o status já persistido.

## Dados e snapshots

- **`restaurant_tables`** — UNIQUE `(company_id, label)`; `capacity` 1–50; `available` controla a
  visão da IA; RLS force por `app.company_id()`.
- **`restaurant_reservation_config`** (1:1) — `duration_minutes` 30–600 (default 120),
  `buffer_minutes ≥ 0` (default 0), `opens_at`/`closes_at` (11:00/23:00). Ausência de linha = defaults.
- **`table_reservations`** — snapshots `guest_name`/`guest_phone`/`duration_minutes`; `end_at`
  materializado; `conversation_id`/`contact_id` nullable (`on delete set null` — a reserva sobrevive
  ao contato); tenant só SELECT/UPDATE via RLS.
- **Cache:** `ReservationContextCache` (Caffeine, **TTL 15s** — o mais curto dos perfis, agenda muito
  volátil; max 500) por company; invalidado explicitamente em TODA mutação de mesa/reserva/config.

## Features de onda (backlog implementado)

Migration 91 (`RestaurantReminderJob`, cron default `0 40 9 * * *`):

- **Lembrete D-1 + confirmação (#1):** varre `pendente`/`confirmada` de amanhã (America/Sao_Paulo) e
  pergunta "confirma? SIM ou NÃO" pela conversa. Idempotência por `reminded_24h` (boolean); sem canal
  (reserva manual) marca sem envio. Toggle `reminder_enabled` — **default LIGADO**. A resposta fecha
  o loop via `<confirmacao_reserva>`; cancelar LIBERA o slot e dispara a notificação padrão.
- **Auto-transição (#3):** `confirmada` com `end_at` passado há 2h+ (folga `GRACE_HOURS`) →
  `realizada` via service (silenciosa). Toggle `auto_complete_enabled` — **default LIGADO**.
  `no_show` NUNCA é automático (sem check-in no modelo, falta é julgamento humano).

## O que NÃO existe (limites honestos)

- Lembrete "sua reserva é em 1h", cobrança de no-show, pagamento/sinal antecipado.
- Reserva em grupo (várias mesas), feriados/dias especiais, tarifas.
- **Buffer efetivo:** `buffer_minutes` existe na config mas NÃO entra no cálculo de conflito
  (fixo em 0 na prática — o guia admite).
- Cardápio/pedidos (isso é o perfil sushi), fuso configurável (America/Sao_Paulo hardcoded),
  textos de notificação personalizáveis, foto/upload.
