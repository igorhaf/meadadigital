# Floricultura — regras de negócio (floricultura, camada 8.5)

[← Catálogo](../05-nichos.md) · Chassi: B — pedido order-based com gate de aceite (variante "entrega agendada") · Guia operacional: docs/PERFIL_FLORICULTURA.md · Migrations: 49, 106

## O negócio em 3 linhas

Loja de flores (buquês, arranjos, cestas, plantas, coroas) que vende pelo WhatsApp. Flor é **presente
AGENDADO pra OUTRA pessoa**: o comprador conversa com a IA, escolhe itens e opções (cor/tamanho com
`price_delta_cents`), e o pedido nasce com **data + período de entrega, destinatário e cartão** — a
floricultura aceita ou recusa no painel (gate humano). Só entrega, sem retirada.

## Jornada no WhatsApp (cenários)

1. **Catálogo:** a IA responde com o bloco do `FloriculturaCatalogCache` — itens por categoria com
   `item_id`, preço base e as opções por grupo (`[Tamanho] <opt_id> Grande (+R$ 10)`).
2. **Coleta obrigatória:** antes de fechar, a IA PRECISA ter itens, ENDEREÇO, DATA (`YYYY-MM-DD`,
   hoje ou futura), PERÍODO (`manha` 8h–12h / `tarde` 13h–18h) e o NOME de quem recebe; cartão é
   opcional. Sem tudo isso, a tag não sai.
3. **Confirmação:** a última mensagem TERMINA com `<pedido_flor>{...}`. O
   `OutboundService.maybeProcessPedidoFlor` (só age se `profile_id='floricultura'`) chama o
   `PedidoFlorConfirmHandler`, que valida, cria o pedido `aguardando` e **remove a tag**.
4. **Gate humano:** a loja aceita (`em_preparo`) ou recusa (`recusado` + motivo) no Kanban; cada
   transição pós-gate notifica o comprador com texto fixo.
5. **Véspera:** o `FloriculturaReminderJob` (cron default 10h10) avisa o COMPRADOR de pedido aceito
   com entrega amanhã, confirmando destinatário/endereço/período ("se algo mudou, me avisa").
6. **Exceções (abort silencioso, sem pedido):** data no passado, período inválido, destinatário ou
   endereço ausentes, item inexistente/indisponível, opção fantasma (`InvalidOptionException`) —
   warn no log, a mensagem da IA segue normal.
7. **Cupom/surpresa:** código de cupom só viaja no campo `cupom` (o backend valida — inválido NÃO
   aborta); `"anonimo":true` marca presente surpresa (entrega não revela o remetente).

## Regras de negócio

### Transacionais (invariantes duras)

- **R1 — Total recalculado do catálogo:** `unit_price = preço base + Σ price_delta_cents` das opções;
  `subtotal = Σ unit_price × qtd`; `total = subtotal − desconto + delivery_fee` — tudo MATERIALIZADO
  em Java no INSERT (`FloriculturaOrderRepository.createOrder`). O `total_cents` da tag é DESCARTADO.
- **R2 — Entrega agendada ≥ hoje:** `delivery_date` parseada e comparada a hoje no fuso
  America/Sao_Paulo pelo handler; passado → pedido NÃO criado. `delivery_period` restrito por CHECK
  e pelo enum `FloriculturaPeriod` (`manha`/`tarde`, parity TS).
- **R3 — Destinatário e endereço obrigatórios:** `recipient_name` e `delivery_address` são
  NOT NULL no banco e validados no handler; `card_message` é opcional.
- **R4 — Opção fantasma aborta:** se algum `option_id` não existe, está indisponível ou é de OUTRO
  item, `InvalidOptionException` → rollback, nenhum pedido parcial (opções resolvidas por
  `findByIdsForItem`; tamanho divergente da lista = fantasma).
- **R5 — Snapshot por item e por opção:** `item_name_snapshot` + `unit_price_cents` (já com deltas)
  em `floricultura_order_items`; `group_label/option_label/price_delta` congelados em
  `floricultura_order_item_options` (`catalog_option_id on delete set null` preserva o histórico).
  `catalog_item_id on delete restrict` → 409 `catalog_item_in_use`.
- **R6 — Cupom na mesma transação:** válido = `active` + `valid_until ≥ hoje` + `uses < max_uses` +
  `subtotal ≥ min_order_cents`; `percent` 1–100 (CHECK); `uses` incrementa na transação; código
  UNIQUE por `(company_id, lower(code))` → 409 `duplicate_coupon`. Inválido NÃO aborta.
- **R7 — Fidelidade por contagem:** com `floricultura_loyalty_config.enabled`, o backend conta os
  pedidos `entregue` do contato ANTES do INSERT; `count > 0 && count % threshold == 0` → reward
  (percent/fixed) entra no desconto e marca `loyalty_applied`. Desconto combinado (cupom +
  fidelidade) tem **clamp ao subtotal**.
- **R8 — Gate de aceite humano:** pedido nasce `aguardando`; só o painel transiciona
  (`PATCH /api/floricultura/orders/{id}/status`). Não há POST manual de pedido — a criação é
  exclusiva da tag da IA (INSERT só service_role, sem policy de INSERT para authenticated).
- **R9 — Linhas inválidas são filtradas** no repositório (item de outro tenant é ignorado); se
  nenhuma sobrar → `IllegalArgumentException` → sem pedido.

### Máquina de status

```
aguardando ──aceite──▶ em_preparo ──▶ saiu_entrega ──▶ entregue (terminal)
     │                     │               │
     └──recusa──▶ recusado └──▶ cancelado ◀┘    (recusado/cancelado terminais)
```

| Transição | Quem pode | Notifica o cliente? |
|---|---|---|
| (criação) → `aguardando` | IA (`<pedido_flor>`; único write da IA) | NÃO (a IA já confirmou na mensagem) |
| `aguardando` → `em_preparo` | humano no painel | SIM ("confirmado! preparando com carinho 🌷") |
| `aguardando` → `recusado` | humano (com `rejection_reason`) | SIM ("não conseguimos atender essa data" + " Motivo: …") |
| `em_preparo` → `saiu_entrega` | humano | SIM ("saiu para entrega") |
| `saiu_entrega` → `entregue` | humano | SIM ("entregue! 💐") |
| `em_preparo`/`saiu_entrega` → `cancelado` | humano | SIM ("cancelado; se quiser refazer…") |

Transição fora do grafo → 409 `invalid_status_transition` (`FloriculturaOrderStatus.allowedNext`);
status desconhecido → 400 `invalid_status`.

### O que a IA PODE × NUNCA faz (travas da persona)

- **PODE:** apresentar catálogo e opções com preços; coletar data/período/destinatário/endereço/
  cartão; oferecer REPETIR um pedido entregue do contato (recompra, mesmo destinatário/endereço);
  sugerir UM adicional marcado `suggestible` no fechamento (upsell, sem insistir); repassar cupom;
  confirmar o anonimato do presente surpresa.
- **NUNCA:** inventa item, opção ou preço fora do catálogo; aceita ou recusa o pedido (a
  floricultura confirma a data no painel); promete data no passado; inventa desconto (quem valida
  cupom e calcula fidelidade é o sistema). Persona: `ProfilePromptContext.FLORICULTURA`, tom
  afetivo e sensível à ocasião.

### Tags de IA

| Tag | Quando a IA emite | Campos | O que o backend descarta/recalcula |
|---|---|---|---|
| `<pedido_flor>` | confirmação final COM todos os dados | `items[{item_id,qtd,options[]}]`, `endereco`, `data_entrega`, `periodo`, `destinatario`, `cartao`, `cupom`, `anonimo`, `total_cents` | `total_cents` DESCARTADO (recalcula base + Σ deltas); data/período/opções revalidados; desconto calculado pelo backend |

Única tag do perfil. Parse por regex (`DOTALL`), removida do texto antes do envio; qualquer falha →
`Optional.empty()` + warn, mensagem segue sem pedido.

### Validações e erros

| reason | HTTP | Significado de negócio | Cenário que dispara |
|---|---|---|---|
| `forbidden_wrong_profile` | 403 | tenant de outro perfil em `/api/floricultura/**` | guard `FloriculturaProfileGuard` |
| `invalid_status` / `invalid_status_transition` | 400 / 409 | alvo desconhecido / fora do grafo | PATCH de status no Kanban |
| `order_not_found` / `catalog_item_not_found` / `option_not_found` / `coupon_not_found` | 404 | recurso inexistente/de outro tenant | GET/PATCH/DELETE |
| `invalid_category` | 400 | categoria fora do enum (6 valores) | CRUD de item |
| `catalog_item_in_use` | 409 | item referenciado por pedido (FK restrict) | DELETE de item com histórico |
| `duplicate_coupon` / `invalid_coupon` | 409 / 400 | código já existe / kind-value malformado | CRUD de cupom |
| `invalid_loyalty_config` | 400 | threshold/reward malformado | PUT da fidelidade |

Data no passado, período inválido, sem destinatário/endereço e opção fantasma NÃO viram HTTP — só a
IA cria pedido e o handler aborta em silêncio.

### Notificações ao cliente

- **Envia** em toda transição pós-gate (texto FIXO e defensivo de
  `FloriculturaOrderStatus.notificationText`; recusa concatena o motivo) e no lembrete D-1 da
  entrega (só pedido `em_preparo`, 1× por data — remarcar REARMA via `delivery_reminded_date`).
- **Silêncio** em `aguardando` (a IA já confirmou o recebimento na própria mensagem).
- Best-effort (`FloriculturaOrderNotifier`): falha de envio nunca reverte status já persistido.

## Dados e snapshots

- **`floricultura_config`** (1:1): `delivery_fee_cents`/`min_order_cents` ≥ 0 (ausente → ZERO);
  `delivery_reminder_enabled` default TRUE (mig 106) — o job lê com `coalesce(…, true)`.
- **`floricultura_catalog_items`**: `name` 1–120; `price_cents ≥ 0` (preço BASE); CHECK de categoria
  (`buques/arranjos/cestas/plantas/coroas/acessorios`, sync `FloriculturaCategory`); `available`;
  `suggestible` (upsell, default false).
- **`floricultura_catalog_item_options`**: `group_label` 1–60, `option_label` 1–80,
  `price_delta_cents ≥ 0` (delta negativo não existe nesta fase); `on delete cascade` do item.
- **`floricultura_orders`**: status CHECK (6); `delivery_address`/`delivery_date`/`delivery_period`/
  `recipient_name` NOT NULL; `card_message`/`rejection_reason`/`notes` nullable; mig 106 soma
  `discount_cents ≥ 0`, `coupon_id` (set null) + `coupon_code_snapshot`, `loyalty_applied`,
  `anonymous`, `delivery_reminded_date`. INSERT só backend; tenant SELECT/UPDATE via RLS.
- **`floricultura_coupons`** / **`floricultura_loyalty_config`** (mig 106): cupom com
  `UNIQUE(company, lower(code))`; fidelidade `threshold_orders ≥ 1` (default 5), reward percent
  0–100, `enabled` default FALSE (seed criado pra tenants floricultura existentes).
- **Cache:** `FloriculturaCatalogCache` — Caffeine TTL **60s**, max 500, chave `company:contact`
  (o contato injeta o histórico de recompra); invalidado por prefixo de company em TODA mutação de
  item/opção/config (`FloriculturaCatalogService`, `FloriculturaConfigService`).

## Features de onda (backlog implementado — mig 106)

- **Recompra de 1 clique (#3):** os últimos 3 pedidos `entregue` do contato (destinatário, itens,
  endereço, data) entram no contexto com instrução de oferecer "repetir o buquê da Ana" (sem DDL).
- **Upsell controlado (#4):** flag `suggestible` por item; a IA pode sugerir UM no fechamento.
- **Cupom (#7)** e **fidelidade por contagem (#8):** regras R6/R7; badges e desconto no Kanban.
- **Confirmação D-1 (#9):** `FloriculturaReminderJob`, cron `${floricultura.reminder-cron:0 10 10 * * *}`;
  toggle `delivery_reminder_enabled` default ON.
- **Presente surpresa (#13):** `anonymous` no pedido; badge no card orienta o entregador; a IA
  confirma o anonimato e orienta cartão sem assinatura.

## O que NÃO existe (limites honestos)

- **Retirada** (pedido é só entrega — `delivery_address` NOT NULL, sem coluna `fulfillment`);
  **estoque** (chassi B puro: nenhum decremento — corte de estoque em data de pico é backlog #15).
- **POST manual de pedido** pelo tenant; por isso nenhum erro de criação vira HTTP.
- **Pedido mínimo NÃO é validado no backend:** `min_order_cents` só instrui a IA ("avise, mas não
  recuse"). Taxa é flat (sem bairro/CEP — backlog #5); sem slots com capacidade por período (#6).
- **Toggle do lembrete D-1 sem tela/endpoint:** a coluna existe e o job respeita, mas o
  `FloriculturaConfigController` só expõe taxa/mínimo — desligar hoje exige UPDATE direto no banco
  (divergência com o guia, que cita "config/settings").
- Pagamento/sinal (Stripe #50), foto de produto (SERVICE_ROLE_KEY), lembrete de datas
  comemorativas (#2), assinatura/clube de flores (#11), edição de pedido após criado.
