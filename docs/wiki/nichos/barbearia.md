# BarbeariaBot — regras de negócio (barbearia, camada 8.1)

[← Catálogo](../05-nichos.md) · Chassi: A (agenda por barbeiro) + fila walk-in (escapada) · Guia operacional: docs/PERFIL_BARBEARIA.md · Migrations: 43, 83, 112

## O negócio em 3 linhas

O tenant é uma barbearia: barbeiros + serviços com duração/preço próprios. O cliente final fala com
a IA pelo WhatsApp e tem DOIS caminhos: **marcar horário** com um barbeiro ou **entrar na fila de
walk-in** (ordem de chegada, sem hora). A escapada estrutural: a **posição na fila é DERIVADA por
count** (não existe coluna `position`) e **chamar o próximo é ação humana** no painel.

## Jornada no WhatsApp (cenários)

1. **Horário marcado:** cliente escolhe serviço + barbeiro + dia/hora → a IA confirma e emite
   `<agendamento_barbearia>` (com `cupom` opcional que o cliente informou). Backend valida janela e
   conflito por barbeiro, aplica fidelidade/cupom e cria `agendado` (silencioso).
2. **Fila:** cliente quer "assim que der" → `<fila_barbearia>` com `barber_id` opcional (ausente =
   fila geral). O ticket nasce `aguardando`; a IA informa posição e espera **estimadas**
   ("aproximadamente"). Quando o barbeiro libera, o PAINEL clica "Chamar" → `chamado` + notificação
   "Chegou a sua vez!" (a crítica do walk-in). A IA nunca chama.
3. **Lembrete 24h (onda 1):** job pergunta "Confirma? SIM ou CANCELAR" nos `agendado` das próximas
   24h; a resposta vira `<confirmacao_barbearia>` (a IA só reflete); CANCELAR libera o slot na hora.
4. **Fila → agenda (onda 2 #8):** `POST /api/barbearia/queue/{id}/convert` transforma o ticket em
   atendimento IMEDIATO (start=agora, conflito re-verificado, ticket → `atendido`) — o corte entra
   no funil (fidelidade e relatórios contam). Ticket geral exige `barberId` → 400 `barber_required`.
5. **Exceções:** fila desligada → 409 `queue_disabled`; slot ocupado → 409 `conflict_slot`; cupom
   inválido NÃO aborta (agendamento sai sem desconto); fora da janela → 400 `outside_hours`.

## Regras de negócio

### Transacionais (invariantes duras)

- **R1 — Conflito por barbeiro:** overlap half-open (`NOT (end_at <= :s OR start_at >= :e)`) contra
  `agendado`/`confirmado` do mesmo `barber_id`, re-verificado dentro da transação do INSERT → 409
  `conflict_slot`. Índice parcial `idx_barber_appts_barber_active`. Barbeiros diferentes = paralelo.
- **R2 — Posição derivada:** `position = count(aguardando à frente no mesmo escopo) + 1`, calculada
  a cada leitura sobre a âncora `enqueued_at` (índice parcial `idx_barber_queue_waiting`). Atender/
  desistir à frente recomputa todo mundo sem UPDATE. Escopo: ticket geral concorre com TODOS à
  frente; ticket do barbeiro X concorre com a fila de X + os gerais que chegaram antes.
- **R3 — Fidelidade materializada no backend** (#3): com `barber_loyalty_config.enabled` e preço > 0,
  conta os REALIZADOS do contato ANTES do INSERT; `count > 0 && count % N == 0` → grátis
  (`discount_cents = price`, `loyalty_applied = true`). A IA só informa o saldo do contexto.
- **R4 — Cupom validado pelo sistema** (#12): ativo + `valid_until` + `min_order_cents` sobre o
  preço + `max_uses`; desconto clampado ao preço; `uses` incrementa NA MESMA transação; código
  UNIQUE case-insensitive por company (`lower(code)`). **Inválido não aborta.** Fidelidade tem
  precedência (grátis não acumula cupom).
- **R5 — `end_at` materializado em Java**; snapshots de barbeiro/serviço/preço/duração congelados.
- **R6 — Janela de funcionamento** no fuso America/Sao_Paulo → 400 `outside_hours`.
- **R7 — Idempotências:** lembrete por flag `reminded_24h`; reativação por `barber_reactivation_log`
  (cooldown = janela); avaliação por `barber_review_log` (`review_cooldown_days`, CHECK 7..365).
- **R8 — Excluir em uso é bloqueado:** barbeiro/serviço com agendamento ou ticket → 409 `*_in_use`.

### Máquina de status

```
AGENDA:  agendado ──→ confirmado ──→ realizado          FILA:  aguardando ──→ chamado ──→ atendido
            │             │────────→ falta                        │  │            └────→ desistiu
            └─────────────┴────────→ cancelado                    │  └──→ desistiu
                                                                  └─────→ expirado
```

| Transição | Quem pode | Notifica? |
|---|---|---|
| → agendado / → aguardando (criação) | IA (tags) ou painel | não |
| agendado → confirmado | painel · IA via `<confirmacao_barbearia>` | **sim** (data/hora/barbeiro) |
| agendado/confirmado → cancelado | painel · IA via tag (só reflete o cliente) | **sim** |
| confirmado → realizado | painel · sistema (`BarberAutoTransitionJob`, end_at + 2h) | não (mas dispara pedido de avaliação se `post_review_enabled`) |
| confirmado → falta | só painel | não |
| aguardando → chamado | **só painel** (grava `called_at`) | **sim** — "Chegou a sua vez!" |
| chamado → atendido · aguardando/chamado → desistiu | painel (ou convert #8 → atendido) | não |
| aguardando → expirado | sistema (job, tickets de dias anteriores) | não |

### O que a IA PODE × NUNCA faz (travas da persona)

**PODE:** ofertar serviços/barbeiros, marcar OU enfileirar, informar posição/ETA como estimativa,
repassar código de cupom na tag, informar saldo de fidelidade do contexto, refletir SIM/CANCELAR.
**NUNCA** (`ProfilePromptContext.BARBEARIA`): opina sobre aparência/estilo ou promete resultado de
corte; recomenda serviço não pedido (exceção única: `upsell_enabled` ON → UMA sugestão do catálogo,
sem insistir); promete tempo exato ou "próximo garantido"; chama cliente ou move ticket; calcula/
promete desconto (cupom e fidelidade são do sistema); confirma/cancela sem o cliente pedir.

### Tags de IA

| Tag | Quando a IA emite | Campos | Backend descarta/recalcula |
|---|---|---|---|
| `<agendamento_barbearia>` | confirmação do horário | `barber_id`, `service_id`, `date`, `start_time`, `cupom?`, `notes` | preço/duração vêm do catálogo; desconto (cupom/fidelidade) calculado no backend |
| `<fila_barbearia>` | cliente quer walk-in | `service_id`, `barber_id?` (ausente = fila geral) | posição/ETA derivados na hora, nunca da tag |
| `<confirmacao_barbearia>` | resposta ao lembrete / pedido de desmarcar | `appointment_id`, `decisao: confirmado\|cancelado` | BARREIRA DE CONTATO + máquina de status (confirmado só de `agendado`) |

### Validações e erros

| reason | HTTP | Significado | Cenário |
|---|---|---|---|
| `forbidden_wrong_profile` | 403 | guard `/api/barbearia/**` | tenant de outro perfil |
| `conflict_slot` | 409 | barbeiro ocupado | criação e convert #8 |
| `queue_disabled` | 409 | fila desligada na config | `<fila_barbearia>`/POST com `queue_enabled=false` |
| `barber_required` | 400 | convert de ticket geral sem barbeiro | onda 2 #8 |
| `outside_hours` | 400 | fora da janela | agenda e convert |
| `inactive_barber` / `inactive_service` | 400 | entidade desativada | — |
| `barber_not_found` / `service_not_found` / `appointment_not_found` / `ticket_not_found` / `coupon_not_found` | 404 | id inexistente | — |
| `barber_in_use` / `service_in_use` | 409 | DELETE com agendamento/ticket | desativar em vez de excluir |
| `invalid_status` / `invalid_status_transition` | 400 / 409 | máquina de status (agenda E fila) | — |
| `invalid_coupon` / `duplicate_coupon` | 400 / 409 | cupom malformado / código repetido | CRUD de cupom |
| `invalid_loyalty` | 400 | threshold < 1 | config de fidelidade |
| `invalid_slot` / `invalid_hours` / `invalid_time` / `invalid_date` | 400 | config/params malformados | — |

### Notificações ao cliente

Envia: **confirmado**, **cancelado**, **chamado** ("Chegou a sua vez! Procure o barbeiro X. 💈"),
lembrete 24h (SIM/CANCELAR), pós-corte com `review_link` (opt-in + cooldown por contato) e convite
de reativação (opt-in). Silencioso: `agendado`, `aguardando`, `realizado`, `falta`, `atendido`,
`desistiu`, `expirado` — texto sempre fixo e defensivo, nunca gerado pela IA; sem canal (criação
manual) marca sem envio.

## Dados e snapshots

- `barber_appointments` / `barber_queue_tickets` — INSERT só backend (tenant SELECT/UPDATE);
  snapshots `barber_name`, `service_name`, `price_cents`, `duration_minutes`, `guest_name/phone`;
  cliente NÃO é entidade própria (decisão cravada — histórico via contact). Ticket: `enqueued_at`
  âncora de ordem; `barber_name` nullable (= "qualquer barbeiro"). Onda 1 no appointment:
  `discount_cents` (CHECK ≥ 0), `coupon_id`/`coupon_code_snapshot`, `loyalty_applied`, `reminded_24h`.
- `barber_config` — 1:1; defaults 09:00/20:00, `slot_minutes` 15, `queue_enabled` true; toggles
  `reminder_enabled` ON, `auto_complete_enabled` ON, `upsell_enabled` OFF, `reactivation_enabled`
  OFF (`reactivation_days` 45, CHECK 7..365, `reactivation_coupon_code?`), `post_review_enabled` OFF
  (`review_link`, `review_cooldown_days` 90).
- `barber_loyalty_config` — 1:1, `enabled` default false, `threshold_cuts` ≥ 1 (seed idempotente).
- `barber_coupons` — `kind` percent (1..100) | fixed, `min_order_cents`, `max_uses`/`uses`, UNIQUE
  `(company_id, lower(code))`.
- **Cache:** `BarberContextCache` Caffeine TTL **10s** (o mais volátil dos 4 — a fila muda a cada
  minuto), invalidado por company em toda mutação de agenda/fila/catálogo/config.

## Features de onda (backlog implementado)

- **Onda 1 (mig 83):** #1 lembrete 24h (`BarberReminderJob`, fixedDelay
  `${barbearia.reminder-check-ms:300000}`, só `agendado`, flag `reminded_24h`); #3 fidelidade;
  #4 upsell opt-in; #7 `BarberAutoTransitionJob` (cron `0 15 * * * *`): confirmado com `end_at`
  passado há **2h** → realizado, tickets `aguardando` de dias anteriores → expirado ('agendado'
  nunca é tocado — falta é humana); #12 cupom; #15 relatórios (`/api/barbearia/reports/summary`,
  faturamento líquido = SÓ realizados `price − discount`, taxa de falta = faltas/(realizados+faltas)).
- **Onda 2 (mig 112):** #2 reativação (`BarberReactivationJob`, cron `0 40 11 * * *`, opt-in OFF —
  lição Baileys; suprime quem tem agendamento futuro; cupom de retorno opcional); #8 convert
  fila→agenda; #9 pedido de avaliação pós-realizado com cooldown por contato.

## O que NÃO existe (limites honestos)

Chamada automática do próximo (chamar é sempre humano), timeout de fila por tempo de espera,
lembrete "está chegando sua vez", painel de TV/QR check-in, pagamento/comanda (gateway #50),
clube/assinatura de cortes (#5, chassi E), campanha/indicação (#10/#11), multi-unidade (#16), foto
de corte, múltiplas cadeiras por barbeiro. **Divergência doc×código:** o guia diz que o convert #8
parte de ticket `chamado`, mas `BarberQueueService.convertToAppointment` aceita também `aguardando`.
