# EventosBot — regras de negócio (eventos, camada 8.2)

[← Catálogo](../05-nichos.md) · Chassi: D — proposta com aprovação em 2 fases (generaliza o avô oficina) · Guia operacional: docs/PERFIL_EVENTOS.md · Migrations: 45, 107

## O negócio em 3 linhas

Casa de festas / buffet / cerimonial / espaço de eventos. O cliente final é quem quer dar a festa;
a IA colhe o briefing (tipo de evento, data prevista, nº de convidados) e **abre a proposta em
rascunho** — a equipe orça no painel e a IA captura a aprovação. Inaugura os **dois tipos de
sub-item no mesmo artefato**: orçamento (entra no total) e cronograma do dia (não entra).

## Jornada no WhatsApp (cenários)

1. Cliente descreve a festa; a IA emite `<proposta_evento>` → proposta nasce `rascunho`, total 0,
   sem itens. `planner_id`/`event_date` inválidos são **ignorados e a proposta abre mesmo assim**;
   sem `briefing` a tag é descartada (best-effort, nada acontece).
2. A equipe monta o orçamento no painel (com autofill do catálogo de pacotes, onda 1) e move para
   `orcada` → o cliente recebe o total ("responda sim para aprovar ou não para recusar").
3. A IA emite `<aprovacao_proposta>` com a decisão → `aprovada` (notifica) ou `recusada` (notifica).
   Única mutação de estado feita pela IA; só funciona com a proposta `orcada` e do próprio contato.
4. Painel fecha o contrato (`fechada`, notifica) e, passada a `event_date`, o job move para
   `realizada` (silencioso no funil; dispara o pós-venda se o toggle permitir).

Variações: data pedida já reservada → a IA AVISA (contexto lista as datas ocupadas de 180 dias) mas
nunca afirma que uma data está livre; no painel o modal mostra aviso âmbar NÃO bloqueante
(`GET /api/eventos/proposals/date-check`). Orçamento parado há `follow_up_days` → 1 toque gentil.

## Regras de negócio

### Transacionais (invariantes duras)

- **R1** — `total_cents` e `line_total_cents` materializados em Java a cada mutação de item, na
  mesma transação (nunca GENERATED; a tag não carrega valores — o orçamento é 100% da equipe).
- **R2** — `orcada` exige `total_cents > 0` → 400 `empty_budget`.
- **R3** — `itemsLocked()` a partir de `fechada` (fechada/realizada/recusada/cancelada) congela
  orçamento E cronograma → 409 `proposal_locked` (o editor some no painel e a API recusa).
- **R4** — Aprovação por tag: barreira de contato (só o dono da proposta) + estado `orcada`; caso
  contrário no-op silencioso.
- **R5** — Cronograma NÃO entra no total: `event_timeline_items` só tem `start_time`+título, lido
  ordenado por `start_time` independente da ordem de inserção.
- **R6** — Snapshots `customer_name`/`customer_phone` na proposta; item de orçamento é snapshot
  texto+preço (autofill copia do catálogo — mudar `event_packages` não altera propostas passadas).
- **R7** — Data NÃO é recurso disputado: `event_date` é campo livre, sem 409 de conflito — o aviso
  de data ocupada (contagem de aprovada/fechada/realizada na data) é informativo.
- **R8** — Follow-up idempotente por episódio: `follow_up_sent_at < status_updated_at` rearma
  (re-orçar permite novo toque); auto-realizada e follow-up só com toggles ligados.

### Máquina de status

```
rascunho ──→ orcada ──→ aprovada ──→ fechada ──→ realizada✦
   │            │  └→ recusada✦        │            ✦=terminal
   └────────────┴──────────┴───────────┴→ cancelada✦
```

| Transição | Quem pode | Notifica? |
|---|---|---|
| (abrir) → rascunho | IA (`<proposta_evento>`) ou painel | não |
| rascunho → orcada | humano (exige item) | **sim** (total + tipo de evento) |
| orcada → aprovada / recusada | **IA** (`<aprovacao_proposta>`) ou painel | **sim** (ambas) |
| aprovada → fechada | humano | **sim** |
| fechada → realizada | humano OU **sistema** (`EventosReminderJob`, `event_date` passada) | funil não; pós-venda sim (toggle `post_event_enabled`) |
| não-terminal → cancelada | humano | não |

`EventProposalStatus` ↔ `event-proposal-status.ts` (parity). Inválida → 409 `invalid_status_transition`.

### O que a IA PODE × NUNCA faz (travas da persona)

PODE: abrir a proposta do briefing; informar o total orçado; capturar aprovação/recusa; DESCREVER
pacotes/adicionais do catálogo com os valores cadastrados; sugerir UMA vez UM adicional
`suggestible` que combine com o briefing (sem insistir, sem desconto); avisar data ocupada.
NUNCA (persona `ProfilePromptContext.EVENTOS`): fecha contrato/preço/desconto; confirma
disponibilidade de data ("vou verificar com a equipe"); inventa item/valor/serviço/estrutura do
espaço; promete "evento perfeito"; move a proposta para fechada/realizada (administrativo);
gerencia o cronograma pela conversa (sem tag — só painel).

### Tags de IA

| Tag | Quando emite | Campos | Backend descarta/recalcula |
|---|---|---|---|
| `<proposta_evento>` | cliente confirma que quer a proposta | `briefing`*, `event_type`, `event_date`, `guest_count`, `planner_id`, `notes` | valores nunca viajam; planner/data inválidos ignorados (abre sem); guest_count não numérico ignorado |
| `<aprovacao_proposta>` | cliente responde ao orçamento | `proposal_id`, `decisao: aprovada\|recusada` | só aplica se `orcada` + contato dono |

`OutboundService`: `maybeProcessPropostaEvento`/`maybeProcessAprovacaoProposta`; tag removida antes do envio.

### Validações e erros

| reason | HTTP | Significado | Dispara quando |
|---|---|---|---|
| `forbidden_wrong_profile` | 403 | tenant de outro perfil | `/api/eventos/**` (`EventosProfileGuard`) |
| `empty_budget` | 400 | não se orça proposta vazia | `rascunho→orcada` com total ≤ 0 |
| `proposal_locked` | 409 | escopo congelado | mutar orçamento/cronograma após `fechada` |
| `invalid_status_transition` | 409 | fora do funil | transição não permitida |
| `planner_in_use` | 409 | histórico protegido | excluir cerimonialista com proposta |
| `inactive_planner` | 400 | cerimonialista desativado | abrir/editar proposta |
| `invalid_guest_count` / `invalid_date` / `invalid_time` | 400 | dados malformados | proposta / cronograma / date-check |
| `invalid_kind` / `invalid_price` | 400 | pacote `pacote\|adicional`, preço ≥ 0 | CRUD `/api/eventos/packages` |
| `*_not_found` (proposal/item/timeline_item/planner/package) | 404 | id inexistente no tenant | qualquer rota |

### Notificações ao cliente

Best-effort (`EventProposalNotifier` — falha nunca reverte; sem conversa = silêncio; persiste em
`messages`): **orcada** (total), **aprovada**, **fechada**, **recusada**. Silenciosos: rascunho
(a IA acabou de conversar), realizada e cancelada. Pós-venda (#7): ao entrar em `realizada` (manual
ou automático) envia agradecimento + `review_link` (se houver) + convite de indicação, toggle
`post_event_enabled`. Follow-up (#8) e pós-venda são textos fixos — não passam pela IA.

## Dados e snapshots

- `event_planners` — catálogo simples sem agenda; atribuição opcional.
- `event_config` — `business_name`/`notes`; onda 1: `auto_complete_enabled` (ON), `post_event_enabled`
  (ON), `review_link`, `follow_up_enabled` (ON), `follow_up_days` (default 3, CHECK 1–60).
- `event_proposals` — snapshots de cliente; `guest_count ≥ 0` (CHECK); CHECK de status com os 7
  estados; `closed_at` nos terminais; onda 1: `follow_up_sent_at` + índices parciais
  `idx_event_proposals_orcada_stale` e `idx_event_proposals_event_date` (aprovada/fechada/realizada).
- `event_proposal_items` — `quantity > 0`, `unit_price_cents ≥ 0`, `line_total_cents` materializado.
- `event_timeline_items` — `start_time time NOT NULL` + título; índice `(proposal_id, start_time)`.
- `event_packages` (mig 107) — `kind pacote|adicional`, `suggestible` (default false), `active`.
- Cache: `EventosContextCache` (Caffeine, **TTL 20s**, key `companyId:contactId`) — cerimonialistas,
  propostas do contato em aberto, catálogo de pacotes (com valores) e datas reservadas dos próximos
  180 dias; NÃO injeta o cronograma. `invalidate(companyId)` em toda mutação.

## Features de onda (backlog implementado — migration 107)

- **#2 Catálogo de pacotes/adicionais** (`event_packages`): autofill do orçamento (item continua
  snapshot) + vitrine da IA. **#9 Upsell consultivo:** só itens `suggestible`, uma sugestão.
- **#3 Aviso de data ocupada:** `date-check` (conta aprovada/fechada/realizada; `excludeId`) →
  aviso não bloqueante; contexto da IA lista as datas e proíbe afirmar data livre.
- **#6 Auto-realizada** + **#7 Pós-venda** + **#8 Follow-up de orçamento parado:**
  `EventosReminderJob` (cron `eventos.reminder-cron`, default 10h30), toggles acima, 1 toque por
  episódio, tudo defensivo e sem passar pela IA.

## O que NÃO existe (limites honestos)

Conflito DURO de agenda/data (só aviso), sinal/depósito e gate `deposit_required` (diferente de
atelie/casamento/viagens — bloqueado pelo gateway #50), cupom de desconto, contrato PDF/e-sign,
pagamento/parcelas, fornecedores externos com agenda, lista de convidados/RSVP, mood board/upload,
campanha em massa. Registrados para ondas futuras no guia.
