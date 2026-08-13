# PousadaBot — regras de negócio (pousada, camada 7.6)

[← Catálogo](../05-nichos.md) · Chassi: singular — reserva por INTERVALO DE DIAS half-open · Guia operacional: docs/PERFIL_POUSADA.md · Migrations: 35, 92

## O negócio em 3 linhas

O tenant é uma pousada/hospedagem pequena. O hóspede pede reserva pelo WhatsApp; a IA mostra os
quartos por capacidade e datas, calcula o total (diária × noites) e reserva. A escapada estrutural:
a reserva NÃO é um slot de horas — é um **intervalo de DIAS** `[check_in, check_out)` em `DATE`,
com rotação de quarto no mesmo dia permitida.

## Jornada no WhatsApp (cenários)

1. Hóspede pergunta disponibilidade. O contexto injetado traz quartos ativos (nome, capacidade,
   diária, descrição), a política de cancelamento e a disponibilidade de cada quarto nos próximos
   30 dias.
2. A IA pergunta nº de hóspedes + datas, ajuda a escolher quarto que comporte o grupo e calcula o
   total (diária × noites) ANTES de confirmar.
3. Na confirmação, emite `<reserva_pousada>{room_id, check_in, check_out, guests_count, guest_name,
   notes}`. O backend valida quarto ativo, datas (check_out > check_in; check_in não é no passado no
   fuso SP), capacidade, e o conflito de intervalo re-checado na transação. `nights` e `total_cents`
   são RECALCULADOS pelo backend a partir da diária do quarto — o total falado pela IA é conferência,
   não fonte. Reserva nasce **reservado**.
4. A pousada confirma/cancela no painel — o hóspede é notificado (a confirmação inclui quarto, datas
   e total). Chegada/saída viram `checked_in`/`checked_out` manualmente (silenciosos).
5. **Exceções best-effort:** quarto ocupado na corrida, inativo, capacidade estourada ou datas
   inválidas → reserva não criada, warn, mensagem segue (tenant contorna manualmente).
6. **Onda 1:** na véspera do check-in, o `PousadaReminderJob` pergunta se o hóspede confirma a
   chegada. A resposta gera `<confirmacao_pousada>{reservation_id, decisao}` — confirmado ou
   cancelado; cancelar antecipado LIBERA o quarto pra revenda.

## Regras de negócio

### Transacionais (invariantes duras)

- **R1 — Conflito de INTERVALO half-open por quarto:** reservas ativas
  (`reservado`/`confirmado`/`checked_in`) do mesmo quarto não sobrepõem `[check_in, check_out)` —
  `NOT (check_out_date <= :novoCheckIn OR check_in_date >= :novoCheckOut)`, RE-VERIFICADO dentro da
  transação de INSERT (`PousadaReservationRepository`) → 409 **`conflict_dates`** (não
  `conflict_slot`). Check-out de uma e check-in de outra NO MESMO DIA não conflitam (rotação).
- **R2 — `checked_in` também bloqueia:** hóspede dentro do quarto trava o intervalo (índice parcial
  `idx_pousada_res_room_active` WHERE status IN reservado/confirmado/checked_in).
- **R3 — `nights` e `total_cents` materializados em Java** no INSERT: `nights = check_out −
  check_in` (dias), `total_cents = nightly_rate_cents × nights`; CHECKs `nights > 0` e
  `check_out_date > check_in_date` (`pousada_res_dates_check`) no banco.
- **R4 — Snapshots quádruplos:** `room_name`, `nightly_rate_cents`, `capacity_snapshot`,
  `guest_name`/`guest_phone` congelados — mudar preço/capacidade do quarto não altera reservas.
- **R5 — Capacidade validada no CREATE:** `guests_count` entre 1 e `room.capacity` → 400
  `over_capacity` (CHECK do banco garante só `>= 1`; o teto é app-level).
- **R6 — Datas válidas:** `check_out > check_in` e `check_in >= hoje` (America/Sao_Paulo) → 400
  `invalid_dates`. Não há janela de horas — check-in/check-out `time` da config é só informativo.
- **R7 — Quarto tem de existir e estar ativo** → 404 `room_not_found` / 400 `inactive_room`.
- **R8 — INSERT só pelo backend** (sem policy de INSERT para `authenticated`); quarto com reservas
  não é excluído (FK restrict) → 409 `room_in_use`.
- **R9 — Cliente NÃO é entidade própria** (igual salon): histórico via `contact_id` + snapshots;
  `notes` administrativo, sem RG/CPF (LGPD).

### Máquina de status

```
reservado ──→ confirmado ──→ checked_in ──→ checked_out
    │             │──→ no_show
    └──→ cancelado ←──┘         (checked_out/cancelado/no_show são terminais)
```

| Transição | Quem pode | Notifica o cliente? |
|---|---|---|
| (criação) → reservado | IA (`<reserva_pousada>`) ou tenant (POST manual) | não |
| reservado → confirmado | humano no painel; IA via `<confirmacao_pousada>` (onda 1) | **sim** (quarto/datas/total) |
| reservado/confirmado → cancelado | humano; IA via `<confirmacao_pousada>` | **sim** (texto fixo) |
| confirmado → checked_in | humano apenas | não |
| confirmado → no_show | humano; sistema (`auto_transition_enabled`, folga 1 dia) | não |
| checked_in → checked_out | humano; sistema (`auto_transition_enabled`) | não |

Fora do diagrama → 409 `invalid_status_transition` (`PousadaReservationStatus.allowedNext`).

### O que a IA PODE × NUNCA faz (travas da persona)

- **PODE:** apresentar quartos ativos com capacidade/diária/descrição, calcular e informar o total,
  repassar a política de cancelamento, criar reserva `reservado`, refletir a decisão do hóspede ao
  lembrete D-1 (confirmar/cancelar a PRÓPRIA reserva do contato).
- **NUNCA:** promete estrutura, vista ou comodidade que não esteja na DESCRIÇÃO do quarto; sem
  promessa de "experiência única" (`ProfilePromptContext.POUSADA` — tom sereno, sem exagero);
  não registra check-in/check-out nem marca no_show.

### Tags de IA

| Tag | Quando a IA emite | Campos | O que o backend descarta/recalcula |
|---|---|---|---|
| `<reserva_pousada>` | hóspede confirmou quarto + datas + grupo | `room_id`, `check_in`, `check_out`, `guests_count`, `guest_name`, `notes` | `nights`/`total_cents` RECALCULADOS da diária do quarto (qualquer total da conversa é descartado); ativo/datas/capacidade/conflito revalidados |
| `<confirmacao_pousada>` | hóspede respondeu ao lembrete D-1 (onda 1) | `reservation_id`, `decisao` (confirmado\|cancelado) | BARREIRA DE CONTATO (`reservation.contact_id` = contato da conversa); máquina de status valida |

Regex + strip antes do envio; falha → `Optional.empty()` + warn.

### Validações e erros

| reason | HTTP | Significado de negócio | Cenário que dispara |
|---|---|---|---|
| `forbidden_wrong_profile` | 403 | tenant não é pousada | `/api/pousada/**` com outro perfil |
| `conflict_dates` | 409 | quarto ocupado no período (com quem e de que data a que data) | intervalo sobrepondo reserva ativa |
| `invalid_dates` | 400 | check_out ≤ check_in, ou check_in no passado | reservar pra ontem |
| `over_capacity` | 400 | grupo maior que a capacidade do quarto | 5 hóspedes em quarto de 4 |
| `inactive_room` | 400 | quarto desativado pelo tenant | reservar quarto inativo |
| `room_not_found` / `reservation_not_found` | 404 | entidade inexistente/de outro tenant | id errado |
| `invalid_status_transition` | 409 | transição proibida | checked_out → confirmado |
| `invalid_status` | 400 | status alvo desconhecido | PATCH com status inválido |
| `room_in_use` | 409 | quarto tem reservas (FK restrict) | DELETE com histórico |
| `invalid_date` / `invalid_time` | 400 | filtro de data/hora malformado | query/config inválida |

### Notificações ao cliente

- **Envia** em `confirmado` ("Sua reserva foi confirmada: {quarto} de {data} a {data}, total R$
  {total}. Aguardamos você!") e `cancelado` ("Pra reagendar, é só me chamar").
- **Silêncio** em `reservado` (acabou de combinar no chat), `checked_in`/`checked_out` (o hóspede
  está presente) e `no_show` (quem furou não recebe sermão); reserva manual sem `conversation_id`
  não notifica.
- Best-effort (`PousadaReservationNotifier`): falha de envio nunca reverte o status.

## Dados e snapshots

- **`pousada_rooms`** — `name` 1–200, `capacity` 1–20, `nightly_rate_cents ≥ 0` por CHECK;
  `description` é o LIMITE do que a IA promete; índices parciais por company WHERE active (um por
  capacidade, p/ busca "quarto que comporta N").
- **`pousada_config`** (1:1) — `check_in_time` (14:00) / `check_out_time` (11:00) informativos +
  `cancellation_policy` texto livre; onda 1: `reminder_enabled` (true), `auto_transition_enabled`
  (**false** — opt-in).
- **`pousada_reservations`** — `check_in_date`/`check_out_date` em `DATE`; materializados
  `nights`/`total_cents`; snapshots R4; `reminded_checkin_date` (marker do lembrete); índices por
  company/status, por quarto (parcial, conflito) e por contato.
- **Cache:** `PousadaContextCache` (Caffeine, **TTL 30s**) por company; invalidado em toda mutação
  de quarto/reserva/config. Janela de contexto: 30 dias.

## Features de onda (backlog implementado)

Migration 92 (`PousadaReminderJob`, cron default `0 50 9 * * *`):

- **Lembrete de check-in D-1 + confirmação (#2):** reservas `reservado`/`confirmado` com check-in
  amanhã recebem "check-in a partir das {hora} — confirma sua chegada?". Idempotência por
  `reminded_checkin_date` (par reserva+data) — **remarcar as datas rearma**; sem canal marca sem
  envio. Toggle `reminder_enabled` (default LIGADO). A resposta fecha o loop via
  `<confirmacao_pousada>`; cancelar antecipado libera o quarto pra revenda.
- **Auto-transição opt-in (#4):** `confirmado` com check-in vencido há 1 dia+ (folga) → `no_show`;
  `checked_in` com check-out vencido → `checked_out`. Ambas silenciosas. Toggle
  `auto_transition_enabled` (**default DESLIGADO** — marcar no_show sozinho pune o hóspede se a
  equipe esqueceu de registrar o check-in).

## O que NÃO existe (limites honestos)

- Reserva por horas — a unidade é a DIÁRIA (1 diária = 1 noite); os horários de check-in/check-out
  são informativos, não validados.
- Tarifa sazonal/promocional (diária fixa por quarto), pagamento/sinal/Pix, fidelidade.
- Foto do quarto (bloqueador de Storage), integração Booking/Airbnb, café da manhã/serviços extras.
- Cadastro formal do hóspede (histórico vem do contato WhatsApp); documento/RG não é modelado.
- Fuso configurável (America/Sao_Paulo hardcoded), textos de notificação personalizados.
