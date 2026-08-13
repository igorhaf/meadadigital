# PizzariaBot — regras de negócio (pizzaria, camada 8.6)

[← Catálogo](../05-nichos.md) · Chassi: B — order-based (clone do comida) · Guia operacional: docs/PERFIL_PIZZARIA.md · Migrations: 50, 93

## O negócio em 3 linhas

Pizzaria delivery. O cliente pede pelo WhatsApp — **inclusive pizza meio-a-meio** — e a IA monta
o pedido na conversa com modifiers (Tamanho, Borda); a pizzaria decide no painel (gate de aceite
herdado do comida). A escapada estrutural: frações de sabor com preço pela **regra do MAIOR
valor**, calculado sempre pelo backend.

## Jornada no WhatsApp (cenários)

1. Cliente: "uma grande metade Portuguesa metade Quatro Queijos, borda recheada, e um guaraná".
   A IA usa o bloco do `PizzariaMenuCache` (sabores/itens + modifiers com ids exatos).
2. Confirmação final com total e endereço → tag `<pedido_pizza>` com a linha-pizza em modo
   `flavors` (lista de UUIDs de sabores) e o guaraná em modo `item_id`.
3. O `PedidoPizzaConfirmHandler` valida cada sabor e item (existem + `available`); o
   repositório resolve o **sabor principal** (o mais caro), aplica os modifiers sobre ele e
   grava as frações com snapshot. O pedido nasce **`aguardando`**; a IA avisa que vai para
   confirmação da pizzaria.
4. Pizzaria aceita (→ `em_preparo`) ou recusa (→ `recusado`, motivo opcional) no Kanban, que
   mostra os sabores de cada fração; depois avança até `entregue`.
- **Exceções:** sabor fantasma (inexistente/indisponível/de outro tenant) →
  `InvalidFlavorException`, pedido **não criado**; modifier fantasma → `InvalidOptionException`;
  tag sem endereço ou sem itens → não cria (entrega é o único fulfillment); cupom inválido →
  sai sem desconto (onda 1).

## Regras de negócio

### Transacionais (invariantes duras)

- **R1 — Regra do MAIOR valor** (`PizzariaOrderRepository.createOrder`, `@Transactional`):
  `unit_price = MAX(price_cents dos sabores) + Σ deltas dos modifiers` — NÃO soma, NÃO média.
  Ex.: MAX(5200, 5500) + 1200 + 1000 = 7700. `total_cents` da tag é descartado.
- **R2 — Sabor principal**: o `menu_item_id` do order_item aponta para o sabor de MAIOR preço;
  os modifiers (Tamanho/Borda) são resolvidos **sobre o sabor principal** — opção que não
  pertence a ele → `InvalidOptionException` (pedido aborta).
- **R3 — Sabor tem que estar disponível**: a query das frações filtra `available = true`;
  qualquer fração fantasma aborta o pedido inteiro (`InvalidFlavorException`) — simétrico ao
  `item_id` inválido.
- **R4 — Snapshot das frações**: `pizzaria_order_item_flavors` congela
  `flavor_name_snapshot` + `flavor_price_cents_snapshot` por `fraction_index` (1..N;
  CHECK `>= 1`; `menu_item_id on delete set null` preserva o histórico).
- **R5 — Gate de aceite humano**: pedido nasce `aguardando` (default do INSERT); só o painel
  transiciona; sem policy de INSERT para `authenticated` (RLS).
- **R6 — Cupom** (onda 1): active + validade + `subtotal ≥ min_order` + usos; clamp ao
  subtotal; `uses` incrementa na MESMA transação; inválido NÃO aborta.
- **R7 — Fidelidade** (onda 1, default OFF, threshold 10): conta `status='entregue'` do contato
  ANTES do INSERT; cupom + fidelidade SOMAM com clamp;
  `total = subtotal − discount + delivery_fee` (taxa flat da config; ausente → 0).
- **R8 — Item simples com snapshot** (chassi comida): base + Σ deltas; item com pedido → 409
  `menu_item_in_use` (FK restrict).

### Máquina de status

Enum fixo `PizzaOrderStatus` (paridade TS via `PizzaOrderStatusParityTest`).

```
aguardando ──aceitar──→ em_preparo ──→ saiu_entrega ──→ entregue
    │                        │               │
    └──recusar──→ recusado   └──→ cancelado ←┘
terminais: entregue, recusado, cancelado
```

| Transição | Quem pode | Notifica? |
|---|---|---|
| criação → `aguardando` | IA (tag; INSERT service_role) | **Não** (a IA já confirmou o recebimento) |
| `aguardando` → `em_preparo` / `recusado` | Humano no painel (gate) | Sim ("Já estamos preparando sua pizza. 🍕" / defensivo + motivo) |
| `em_preparo`/`saiu_entrega` → próximo ou `cancelado` | Humano no painel | Sim (texto fixo do status alvo) |
| qualquer outra | — | 409 `invalid_status_transition` |

### O que a IA PODE × NUNCA faz (travas da persona)

- **PODE:** montar pizza inteira ou meio-a-meio (2 sabores); aplicar modifiers do cardápio;
  repassar o código do cupom; 1 upsell (borda recheada/bebida/sobremesa — onda 1 #3, no bloco
  do cache); explicar a regra da casa ("o preço é o do sabor mais caro — o sistema calcula").
- **NUNCA:** aceita/recusa pedido (gate humano); inventa sabor/opção/preço; **calcula o preço
  da meio-a-meio** (regra do maior valor é do sistema); define total; promete desconto; cria
  item de cardápio (persona `ProfilePromptContext.PIZZARIA`).

### Tags de IA

| Tag | Quando a IA emite | Campos | O backend descarta/recalcula |
|---|---|---|---|
| `<pedido_pizza>` | Confirmação final com total + endereço | por item: modo pizza `flavors[]` + `options[]` + `qtd` (sem `item_id`) OU modo simples `item_id` + `options[]` + `qtd`; `endereco`, `cupom`, `total_cents` | `total_cents` descartado; preço da pizza = MAX dos sabores + deltas, relido do cardápio; cupom/fidelidade aplicados pelo sistema |

### Validações e erros

| reason | HTTP | Significado de negócio | Cenário |
|---|---|---|---|
| `forbidden_wrong_profile` | 403 | tenant de outro perfil em `/api/pizzaria/**` | guard `PizzariaProfileGuard` |
| `invalid_status` | 400 | status alvo fora do enum | PATCH desconhecido |
| `invalid_status_transition` | 409 | transição fora da matriz | entregar pedido `aguardando` |
| `order_not_found` | 404 | pedido inexistente/de outro tenant | GET/PATCH errado |
| `invalid_category` / `menu_item_not_found` / `option_not_found` / `menu_item_in_use` | 400/404/404/409 | CRUD do cardápio/modifiers | excluir sabor com pedido |
| `invalid_coupon` / `coupon_not_found` / `duplicate_coupon` | 400/404/409 | CRUD de cupons (onda 1) | código duplicado (case-insensitive) |
| `invalid_loyalty_config` | 400 | fidelidade fora da faixa | percent > 100 |

Sabor/opção fantasma na tag não vira HTTP: o handler devolve vazio e o pedido simplesmente não
é criado (best-effort do fluxo de IA).

### Notificações ao cliente

- Texto fixo ao ENTRAR no status, com voz de pizzaria ("Sua pizza saiu pra entrega. Já já chega
  quentinha!"); `recusado` = texto defensivo + " Motivo: X" quando informado. **`aguardando` é
  silencioso** (a IA já confirmou na conversa).
- Best-effort: falha de envio não reverte a transição; mensagem persistida em `messages` como
  outbound/**human**; `EVOLUTION_DRY_RUN` honrado (`PizzariaOrderNotifier`).

## Dados e snapshots

- 7 tabelas base (mig 50): `pizzaria_config` (1:1, taxa flat + mínimo), `pizzaria_menu_items`
  (categorias hardcoded CHECK: pizzas_salgadas/pizzas_doces/bordas/bebidas/sobremesas/combos —
  `PizzaCategory` ↔ TS), `pizzaria_menu_item_options` (`price_delta_cents >= 0`),
  `pizzaria_orders` (CHECK de status; `rejection_reason`; `delivery_address` NOT NULL; INSERT
  só backend), `pizzaria_order_items`, `pizzaria_order_item_options`,
  **`pizzaria_order_item_flavors`** (a escapada). Onda 1: `pizzaria_coupons`,
  `pizzaria_loyalty_config` (seed idempotente) + colunas de desconto no pedido.
- Snapshots: preço+nome por item; grupo/opção/delta por modifier; nome+preço por fração;
  `coupon_code_snapshot`. `unit_price`/`subtotal`/`total` materializados em Java no INSERT.
- Cache: `PizzariaMenuCache` (Caffeine TTL 60s, max 500), invalidado nas mutações de
  cardápio/config.

## Features de onda (backlog implementado)

- **Mig 93 (onda 1, clone da onda adega/sushi):** #1 cupom percent/fixed com mínimo, validade e
  max usos (CRUD `/api/pizzaria/coupons` + tela); #2 fidelidade por contagem de entregues
  (default OFF, threshold 10 — reward automático no pedido, `loyalty_applied=true`); #3 upsell
  na persona (sem DDL) — UMA sugestão do próprio cardápio, sem insistir, e o ensino do campo
  `cupom` ("quem valida/calcula é o sistema").

## O que NÃO existe (limites honestos)

- Foto de cardápio; pagamento online; rastreio/ETA; avaliação de pedido.
- **Retirada no balcão / fulfillment** (endereço é sempre obrigatório — diferente do comida
  onda 2), **zonas de entrega** (taxa flat única), **horário próprio** e **reativação de
  inativos** — nada disso chegou à pizzaria.
- Frações além de meio-a-meio na UI/IA (o modelo aceita N por `fraction_index`, mas IA e tela
  cobrem 1 e 2); preço por tamanho POR SABOR (Tamanho é delta único sobre o sabor principal);
  edição de pedido após criado; entregador como entidade.
