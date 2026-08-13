# ÓticaBot — regras de negócio (otica, camada 8.12)

[← Catálogo](../05-nichos.md) · Chassi: híbrido A + B (o primeiro do projeto) · Guia operacional: docs/PERFIL_OTICA.md · Migrations: 56, 97

## O negócio em 3 linhas

O tenant é uma loja de ótica que faz DUAS coisas: exame de vista com optometrista (fluxo A, agenda)
e encomenda de óculos sob receita (fluxo B, pedido com gate de aceite e prazo de montagem). A IA
atende os dois fluxos na mesma conversa; a receita é dado **administrativo** que ela registra verbatim.

## Jornada no WhatsApp (cenários)

1. **Fluxo A:** cliente pede exame. A IA consulta o contexto (optometristas + agenda) e, na
   confirmação, emite `<exame_otica>{professional_id, date, start_time, notes}` — o
   `ExameOticaConfirmHandler` cria o exame **agendado** (duração = snapshot do config).
2. **Fluxo B:** cliente monta a encomenda na conversa (armação do catálogo + tipo de lente/tratamento
   como opções). Na confirmação, a IA emite `<encomenda_otica>` com itens, `ready_date`, o bloco `rx`
   (grau que o CLIENTE ditou) ou `prescription_pending`. O `EncomendaOticaConfirmHandler` valida os
   itens no catálogo, recalcula o total e grava o pedido **aguardando**.
3. A loja ACEITA (→ `em_montagem`) ou RECUSA (→ `recusado`, com motivo) no Kanban — gate humano.
   `pronto` avisa que o óculos espera RETIRADA na loja (sem entrega nesta SM).
4. **Receita:** sem os dados de grau, a IA anota "trazer receita" → `prescription_pending = true`;
   a loja confirma no painel antes de montar. A IA nunca calcula/valida/interpreta o grau.
5. **Exceções (best-effort, sem mensagem de erro ao cliente):** item inexistente/indisponível,
   opção fantasma, `ready_date` antes do prazo de montagem, slot do optometrista em conflito ou fora
   da janela → exame/encomenda NÃO é criado, warn no log, a resposta segue normal.
6. **Onda 1:** véspera do exame → lembrete com pedido de confirmação; a resposta fecha o loop via
   `<confirmacao_exame>`. Pedido parado em `pronto` há N dias → cutucada de retirada.

## Regras de negócio

### Transacionais (invariantes duras)

- **R1 — Conflito de slot POR `professional_id`** (fluxo A): janela half-open re-verificada dentro
  da transação de INSERT; só `agendado`/`confirmado` bloqueiam (índice parcial
  `idx_otica_exam_company_prof_active`). Mesmo horário com optometrista diferente → OK.
- **R2 — `end_at` materializado em Java** no INSERT (`start_at + exam_duration_minutes`, snapshot do
  config; CHECK 15–240, default 30). Exame inteiro dentro de `opens_at`..`closes_at`
  (America/Sao_Paulo), senão não é criado.
- **R3 — Total recalculado do catálogo** (fluxo B): o backend snapshota preço base + Σ
  `price_delta_cents` das opções por item (`unit_price_cents` já inclui os deltas) e soma
  `subtotal = total` em Java — o total vindo da tag é DESCARTADO. Deltas são CHECK ≥ 0.
- **R4 — Lead time de montagem:** se ALGUM item é `made_to_order`, `earliest = hoje + MAX(lead dos
  itens sob encomenda`, usando `lead_time_days` do item ou `lead_time_days_default` da config`)`;
  `ready_date` nula ou anterior → `LeadTimeViolationException` e o pedido ABORTA (a exceção carrega
  a primeira data possível). Pedido só de acessório → `ready_date` pode ser null.
- **R5 — Receita persistida AS-IS:** `rx_*` são nullable; grau ilegível vira NULL (nunca é
  "corrigido"); sem bloco `rx` OU `prescription_pending=true` na tag → pedido nasce pendente de
  receita. Únicos CHECKs: eixo 0–180 e precisão numérica (`numeric(4,2)`/`numeric(4,1)`).
- **R6 — Gate de aceite humano:** pedido nasce `aguardando`; a IA NUNCA transiciona pedido
  (`OticaOrderService.updateStatus` só é chamado pelo painel).
- **R7 — INSERT só pelo backend:** exames e pedidos não têm POST manual nem policy de INSERT —
  nascem exclusivamente pela IA; tenant SELECT/UPDATE (status) via RLS.
- **R8 — Item com pedido não é excluído** (FK restrict) → 409 `catalog_item_in_use`; opções
  removidas preservam o histórico (snapshot + `on delete set null`).

### Máquina de status

```
FLUXO A: agendado ──→ confirmado ──→ realizado        FLUXO B: aguardando ──→ em_montagem ──→ pronto ──→ retirado
            │             │──→ falta                              │               └──→ cancelado ←──┘
            └──→ cancelado ←──┘                                   └──→ recusado
(realizado/cancelado/falta terminais)                 (retirado/recusado/cancelado terminais)
```

| Transição | Quem pode | Notifica o cliente? |
|---|---|---|
| (criação) → agendado / aguardando | IA (tags) | não |
| agendado → confirmado | humano; IA via `<confirmacao_exame>` (reflete o SIM) | **sim** (data/hora) |
| agendado/confirmado → cancelado (exame) | humano; IA via `<confirmacao_exame>` | **sim** |
| confirmado → realizado / falta | humano apenas | não |
| aguardando → em_montagem | **humano** (aceite) | **sim** ("entrou em montagem") |
| aguardando → recusado | **humano** (com motivo) | **sim** (+ motivo concatenado) |
| em_montagem → pronto | humano | **sim** ("pronto pra retirada") |
| pronto → retirado; em_montagem/pronto → cancelado | humano | não |

Transição fora do diagrama → 409 `invalid_status_transition` (`OticaExamStatus`/`OticaOrderStatus.allowedNext`).

### O que a IA PODE × NUNCA faz (travas da persona)

- **PODE:** agendar exame, montar encomenda com itens/opções DO catálogo, REGISTRAR o grau que o
  cliente fornecer (campos administrativos), anotar "trazer receita", informar o prazo de montagem e
  oferecer a primeira data possível, refletir o SIM/cancelar do lembrete de exame.
- **NUNCA** (`ProfilePromptContext.OTICA`): prescreve grau; diagnostica problema de visão
  (miopia/astigmatismo etc.); recomenda tipo de lente como conduta de saúde — dúvida de visão/grau/
  sintoma → oferece AGENDAR o exame; calcula/valida/interpreta receita; inventa armação/lente/
  tratamento/preço; promete data antes do lead time; aceita ou recusa a encomenda.

### Tags de IA

| Tag | Quando a IA emite | Campos | O que o backend descarta/recalcula |
|---|---|---|---|
| `<exame_otica>` | cliente confirmou optometrista+dia/hora | `professional_id`, `date`, `start_time`, `notes` | duração vem do config (snapshot); janela/conflito revalidados |
| `<encomenda_otica>` | cliente fechou a encomenda | `items[{catalog_item_id, options[{option_id}], quantity}]`, `ready_date`, `rx{od/oe{spherical,cylindrical,axis}, pd}`, `prescription_pending`, `notes` | total DESCARTADO e recalculado; itens/opções validados no catálogo; lead time validado; rx persistido AS-IS |
| `<confirmacao_exame>` | cliente respondeu ao lembrete D-1 (onda 1) | `exam_id`, `decisao` (confirmado\|cancelado) | BARREIRA DE CONTATO (exame de outro contato → ignorado); máquina de status valida |

Todas por regex, removidas antes do envio; qualquer falha → `Optional.empty()` + warn.

### Validações e erros

| reason | HTTP | Significado de negócio | Cenário que dispara |
|---|---|---|---|
| `forbidden_wrong_profile` | 403 | tenant não é otica | `/api/otica/**` com outro perfil |
| `invalid_status_transition` | 409 | transição proibida (exame ou pedido) | retirado → pronto |
| `invalid_status` | 400 | status alvo desconhecido | PATCH com status inexistente |
| `exam_not_found` / `order_not_found` / `professional_not_found` / `catalog_item_not_found` / `option_not_found` | 404 | inexistente ou de outro tenant | id errado |
| `professional_in_use` / `catalog_item_in_use` | 409 | tem exames/pedidos (FK restrict) | DELETE com histórico |
| `invalid_category` | 400 | categoria fora de armacoes/lentes/acessorios | criar item com categoria livre |
| `invalid_date` / `invalid_time` / `invalid_hours` | 400 | data/hora malformada; opens ≥ closes | filtro/config inválida |

Conflito de slot, fora de horário e violação de lead time NÃO têm reason HTTP neste perfil: só
acontecem no caminho da IA (não há POST manual) e resultam em criação abortada + warn (best-effort).

### Notificações ao cliente

- **Exame:** envia em `confirmado` (data/hora, "Te esperamos!") e `cancelado`; `agendado`/
  `realizado`/`falta` silenciosos (quem furou não recebe sermão). Textos SEM promessa clínica.
- **Pedido:** envia em `em_montagem` (aceite), `pronto` (retirada) e `recusado` (desculpa + motivo);
  `aguardando` (a IA já confirmou o recebimento no chat), `retirado` e `cancelado` silenciosos.
- Best-effort (`OticaExamNotifier`/`OticaOrderNotifier`): falha de envio nunca reverte o status.

## Dados e snapshots

- **`otica_config`** (1:1, FUNDIDA A+B) — opens/closes (09:00/18:00), `exam_duration_minutes` 15–240
  (30), `min_order_cents ≥ 0` (0), `lead_time_days_default ≥ 0` (7) + toggles da onda 1.
- **`otica_professionals`** — nome 1–200, `active`; **`otica_exam_appointments`** — snapshots
  `customer_name`/`professional_name`/`duration_minutes`; `reminded_start_at` (onda 1).
- **`otica_catalog_items`** — categoria CHECK (`OticaCategory` com parity TS); `made_to_order` +
  `lead_time_days` (override do default); `price_cents ≥ 0`. **`otica_catalog_item_options`** —
  grupo/opção ("Tipo de lente"/"Multifocal"), `price_delta_cents ≥ 0`, cascade.
- **`otica_orders`** — `subtotal_cents = total_cents` (retirada, sem taxa); `ready_date`;
  `rejection_reason`; bloco `rx_*` + `prescription_pending`; `pickup_followup_sent_at` (onda 1).
  **`otica_order_items`** — `qtd > 0`, `unit_price_cents` (com Σ deltas), `item_name_snapshot`,
  `made_to_order_snapshot`. **`otica_order_item_options`** — snapshots de grupo/opção/delta.
- **Cache:** `OticaContextCache` (Caffeine, **TTL 30s**, max 1000, keyed por company+contato — os
  DOIS fluxos no mesmo bloco), invalidado por company em toda mutação.

## Features de onda (backlog implementado)

Migration 97 (`OticaReminderJob`, cron `${otica.reminder-cron:0 10 10 * * *}`):

- **#1 Lembrete + confirmação de exame:** exames `agendado`/`confirmado` de AMANHÃ recebem "seu
  exame com {profissional} é AMANHÃ às {hora} — confirma?". Idempotência por `reminded_start_at`
  (**remarcar REARMA**); sem canal → marca sem envio. Toggle `exam_reminder_enabled` — **default
  LIGADO**. A resposta fecha o loop via `<confirmacao_exame>` (barreira de contato).
- **#2 Follow-up de óculos pronto:** pedido parado em `pronto` há `pickup_followup_days` (default 3,
  CHECK 1–30) recebe UMA cutucada por episódio (`pickup_followup_sent_at`, re-armado por
  `status_updated_at` — voltar a `pronto` permite novo follow-up). Toggle `pickup_followup_enabled`
  — **default LIGADO**.

## O que NÃO existe (limites honestos)

- Laudo/resultado do exame, interpretação/cálculo de grau (PROIBIDO), prontuário oftalmológico.
- Entrega com taxa (só RETIRADA), convênio, integração com laboratório, pagamento real (Stripe #50),
  foto de armação (links não; bloqueador de upload).
- POST manual de exame/encomenda pelo painel — os dois nascem só pela IA.
- **Mínimo de pedido efetivo:** `min_order_cents` existe na config (e na tela) mas NÃO é validado
  na criação da encomenda — nenhum caminho do código o aplica.
- Reason HTTP para `conflict_slot`/`outside_hours`/`lead_time_violation` (ver acima — o guia cita
  os códigos 409/400/422, mas sem endpoint de criação eles nunca chegam ao HTTP).
