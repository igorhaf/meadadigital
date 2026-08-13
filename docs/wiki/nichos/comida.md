# ComidaBot — regras de negócio (comida, camada 8.4)

[← Catálogo](../05-nichos.md) · Chassi: B — order-based, consolida o gate de aceite · Guia operacional: docs/PERFIL_COMIDA.md · Migrations: 47, 85, 114

## O negócio em 3 linhas

Delivery de comida genérico, estilo iFood, para um único restaurante (tenant). O cliente final
pede pelo WhatsApp; a IA monta o pedido na conversa — **com opções/adicionais (modifiers)** — e
o restaurante decide no painel: o pedido nasce `aguardando` e o **aceite/recusa é ação humana**
(gate que este perfil consolidou para todo o chassi B).

## Jornada no WhatsApp (cenários)

1. Cliente pede "1 X-Bacon grande com bacon extra". A IA usa o bloco do `ComidaMenuCache`
   (itens + grupos de opção com `option_id` e deltas EXATOS) e monta o pedido na conversa.
2. No fechamento a IA pergunta **entrega ou retirada** (onda 2 #3), pergunta o **bairro** para
   casar uma zona de entrega (onda 1 #8), oferece o **endereço do último pedido** (#10), pode
   fazer **1 upsell** (#4) e registra o **cupom** informado (#1).
3. Confirmação final (com endereço, se entrega) → tag `<pedido_comida>`; o
   `PedidoComidaConfirmHandler` valida itens (existem + `available`), o repositório recalcula
   tudo e o pedido nasce **`aguardando`** — a IA avisa que "vai para confirmação do restaurante".
4. Restaurante ACEITA (→ `em_preparo`) ou RECUSA (→ `recusado`, com motivo opcional) no Kanban;
   depois avança até `entregue`. Cada entrada de status notifica com texto fixo.
- **Exceções:** `option_id` fantasma (inválido/indisponível/de outro item) → o pedido **aborta**
  (`InvalidOptionException`); item inexistente na tag → não cria; **fora do horário**
  (`opens_at`/`closes_at`) a IA avisa e não emite a tag, e o backend reforça
  (`OutsideHoursException` — pedido não criado); cupom inválido → sai sem desconto; zona
  inválida → taxa flat (nunca aborta).

## Regras de negócio

### Transacionais (invariantes duras)

- **R1 — Preço do item = base + Σ deltas das opções**, recalculado no backend; `total_cents` da
  tag é descartado (`ComidaOrderRepository.createOrder`, `@Transactional`).
- **R2 — Opção fantasma aborta o pedido**: as opções resolvidas para o item têm que casar 1:1
  com as pedidas (`resolved.size() != optionIds.size()` → `InvalidOptionException`).
- **R3 — Snapshot em tabela-filha, NÃO JSONB**: `comida_order_item_options` congela
  grupo/opção/delta (`menu_option_id on delete set null` preserva o histórico); item congela
  preço+nome; item com pedido → 409 `menu_item_in_use` (FK restrict).
- **R4 — Gate de aceite é humano**: o pedido nasce `aguardando` (default do INSERT — o status
  nunca é passado); só o painel transiciona; não existe policy de INSERT para `authenticated`.
- **R5 — Cupom** (active + validade + `subtotal ≥ min_order` + usos) aplicado com clamp;
  `uses` incrementa na MESMA transação; inválido NÃO aborta.
- **R6 — Fidelidade**: conta pedidos `status='entregue'` do contato ANTES do INSERT;
  `count > 0 && count % threshold == 0` → reward. Cupom + fidelidade SOMAM,
  `discount = min(subtotal, soma)`; `total = subtotal − discount + fee`.
- **R7 — Taxa por zona com fallback**: zona ativa casada → taxa da zona + `zone_name_snapshot`;
  ausente/inválida/inativa → taxa FLAT da config (nunca aborta). **Retirada zera a taxa**
  (nem zona nem flat) e dispensa endereço (`delivery_address` vira NULL).
- **R8 — Janela do delivery** (`opens_at`/`closes_at`, null = sempre aberto): validada no
  service com hora local America/Sao_Paulo — fora dela o pedido não é criado.
- **R9 — Auto-entrega opt-in** (`auto_deliver_hours` 1–24, NULL desliga): job diário move
  `saiu_entrega` parado há N horas para `entregue` por UPDATE direto, **silencioso**.

### Máquina de status

Enum fixo `ComidaOrderStatus` (paridade TS via `ComidaOrderStatusParityTest`).

```
aguardando ──aceitar──→ em_preparo ──→ saiu_entrega ──→ entregue
    │                        │               │
    └──recusar──→ recusado   └──→ cancelado ←┘
terminais: entregue, recusado, cancelado
```

| Transição | Quem pode | Notifica? |
|---|---|---|
| criação → `aguardando` | IA (tag; INSERT service_role) | **Não** (a IA já confirmou o recebimento) |
| `aguardando` → `em_preparo` / `recusado` | Humano no painel (gate) | Sim ("aceito e em preparo" / texto defensivo + motivo) |
| `em_preparo`/`saiu_entrega` → próximo ou `cancelado` | Humano no painel | Sim (texto fixo do status alvo) |
| `saiu_entrega` → `entregue` (auto, após N h) | Sistema (`ComidaReminderJob`) | **Não** (silencioso) |
| qualquer outra | — | 409 `invalid_status_transition` |

### O que a IA PODE × NUNCA faz (travas da persona)

- **PODE:** montar pedido com opções; oferecer endereço salvo; repassar cupom e `zona_id`;
  anunciar o progresso da fidelidade ("faltam N"); 1 upsell do próprio cardápio; avisar que
  está fora do horário.
- **NUNCA:** aceita/recusa pedido (gate humano); inventa item/opção/preço; define ou promete
  total/desconto (o sistema recalcula e aplica); cria item de cardápio; fecha pedido fora do
  horário (persona `ProfilePromptContext.COMIDA` + instruções do cache).

### Tags de IA

| Tag | Quando a IA emite | Campos | O backend descarta/recalcula |
|---|---|---|---|
| `<pedido_comida>` | Confirmação final (endereço se entrega; "entrega ou retirada?" perguntado) | `items[{item_id,qtd,options[]}]`, `fulfillment`, `endereco`, `cupom`, `zona_id`, `total_cents` | `total_cents` descartado; unit_price = base + Σ deltas relidos; cupom/fidelidade/zona resolvidos pelo sistema |

### Validações e erros

| reason | HTTP | Significado de negócio | Cenário |
|---|---|---|---|
| `forbidden_wrong_profile` | 403 | tenant de outro perfil em `/api/comida/**` | guard `ComidaProfileGuard` |
| `invalid_status` | 400 | status alvo fora do enum | PATCH com valor desconhecido |
| `invalid_status_transition` | 409 | transição fora da matriz | recusar pedido já em preparo |
| `order_not_found` | 404 | pedido inexistente/de outro tenant | GET/PATCH errado |
| `invalid_category` / `menu_item_not_found` / `option_not_found` / `menu_item_in_use` | 400/404/404/409 | CRUD do cardápio e das opções | excluir item com pedido |
| `invalid_zone` / `zone_not_found` / `duplicate_zone` | 400/404/409 | CRUD de zonas | nome duplicado (case-insensitive) |
| `invalid_coupon` / `coupon_not_found` / `duplicate_coupon` | 400/404/409 | CRUD de cupons | código duplicado |
| `invalid_loyalty_config` | 400 | fidelidade fora da faixa | percent > 100 |
| `invalid_time` / `invalid_hours` | 400 | horário malformado / `opens_at` sem `closes_at` (e vice-versa) | PUT da config |

`outside_hours` é documentado como 422, mas na prática só surge no caminho da IA (o handler
engole a exceção e o pedido não é criado) — não há POST REST de pedido nem mapeamento HTTP.

### Notificações ao cliente

- Texto fixo ao ENTRAR no status (enum): `em_preparo`, `saiu_entrega`, `entregue`, `cancelado`;
  `recusado` = texto defensivo ("Infelizmente não conseguimos aceitar…") + " Motivo: X" quando
  informado. **`aguardando` é silencioso** (evita duplicar a confirmação da IA).
- Best-effort: falha de envio nunca reverte a transição; a mensagem é persistida em `messages`
  como outbound/**human**. `EVOLUTION_DRY_RUN` honrado. A auto-entrega do job é silenciosa.

## Dados e snapshots

- `comida_config` (1:1; taxa flat + mínimo + `opens_at`/`closes_at` + `auto_deliver_hours` +
  `reactivation_*`), `comida_menu_items` (categorias hardcoded CHECK: lanches/pizzas/pratos/
  porcoes/bebidas/sobremesas/combos), `comida_menu_item_options` (`price_delta_cents >= 0` —
  sem delta negativo nesta fase), `comida_orders` (CHECK de status; `rejection_reason`;
  `fulfillment` entrega|retirada; INSERT só backend), `comida_order_items`,
  `comida_order_item_options`, `comida_coupons`, `comida_loyalty_config` (seed idempotente),
  `comida_delivery_zones`, `comida_reactivation_log`.
- Snapshots: preço+nome por item; grupo/opção/delta por opção; `coupon_code_snapshot`;
  `zone_name_snapshot` (null = taxa flat). Totais materializados em Java.
- Cache: `ComidaMenuCache` — Caffeine TTL 60s, max 500, **keyed por (companyId, contactId)**
  (progresso de fidelidade e endereço salvo são por contato); `invalidate(companyId)` derruba
  todas as chaves da company ao mutar item/opção/config/zona/cupom/fidelidade.

## Features de onda (backlog implementado)

- **Mig 85 (onda 1):** #1 cupom, #2 fidelidade (default OFF, threshold 10), #8 zonas com
  fallback flat, #10 endereço salvo (sem DDL), #4 upsell na persona (sem DDL), #15 relatórios
  (`GET /api/comida/reports/summary` — faturamento líquido dos entregues, ticket médio, top
  itens, horário de pico).
- **Mig 114 (onda 2):** #3 retirada no balcão (sem taxa, endereço dispensado; badge no Kanban);
  #9 horário próprio do delivery (IA avisa + backend valida); #12 auto-entrega opt-in
  (silenciosa; pedido parado é badge DERIVADA pelo `status_updated_at` — cancelamento segue
  humano); #5 reativação de inativos via `ComidaReminderJob` (cron `0 50 11 * * *`): opt-in
  **OFF** por default, janela 7–365 dias (default 30) = cooldown (`comida_reactivation_log`),
  mensagem fixa com cupom de retorno só se existir/ativo/válido.

## O que NÃO existe (limites honestos)

- Foto de item; pagamento online; rastreio/mapa/ETA; avaliação/NPS; combos/promoção agendada;
  agendamento de pedido (o sushi cobre o padrão); aniversário.
- Regra de min/max de seleção obrigatória por grupo de opção (grupo é livre); edição de pedido
  após criado (só status); entregador como entidade; múltiplos endereços salvos (só o último).
- Auto-cancelamento de `aguardando` antigo — cancelar é sempre humano.
