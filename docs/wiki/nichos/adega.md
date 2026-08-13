# Adega (delivery de bebidas) — regras de negócio (`adega`, camada 8.9)

[← Catálogo](../05-nichos.md) · Chassi: **B — pedido order-based com gate de aceite** (clone do comida) · Guia operacional: `docs/PERFIL_ADEGA.md` · Migrations: **53** (base), **80** (cupons + fidelidade)

## O negócio em 3 linhas

Adega/delivery de bebidas (vinhos, espumantes, cervejas, destilados, sem-álcool, acessórios). O
cliente pede pelo WhatsApp; a IA monta o pedido com modifiers (Volume, Temperatura), **confirma a
maioridade (+18)** antes de fechar, registra cupom quando houver, e a loja acompanha num Kanban com
gate de aceite humano. Só entrega (endereço obrigatório).

## Jornada no WhatsApp (cenários)

1. A IA apresenta o cardápio injetado (`AdegaMenuCache`), pode sugerir harmonização **entre o que já
   está no cardápio** e monta o carrinho na conversa.
2. Antes de fechar, a IA **sempre confirma a maioridade**. Cliente menor/indefinido → recusa gentil,
   pedido não fecha.
3. Na confirmação (com total, endereço e "Beba com moderação") a IA emite
   `<pedido_adega>{"age_confirmed":true,...}`; o `PedidoAdegaConfirmHandler` valida e cria o pedido
   `aguardando`; a tag é removida antes do envio.
4. A loja aceita (→ `em_preparo`) ou recusa (→ `recusado`) no painel; segue `saiu_entrega → entregue`.

**Exceções** (abortam a criação em silêncio — a mensagem segue sem pedido): tag sem
`age_confirmed:true` (trava +18 no handler E no service), endereço ausente, JSON inválido, item
inexistente/indisponível, opção fantasma. **Cupom inválido NÃO aborta** — o pedido sai sem desconto.

## Regras de negócio

### Transacionais (invariantes duras)

- **R1 — Trava +18 ANTES de qualquer cálculo.** `AdegaOrderService.create` lança
  `AgeNotConfirmedException` (convenção `age_not_confirmed`, 422) se `ageConfirmed=false` — nenhum
  pedido "menor de idade" existe no banco. Defesa em 3 camadas: handler aborta sem o flag; service
  lança; `adega_orders.age_confirmed` é **boolean NOT NULL SEM default** (o banco é a defesa final —
  só `true` entra). O flag é persistido para compliance (selo "+18 confirmado" no painel). Vale
  mesmo para carrinho 100% sem-álcool nesta SM.
- **R2 — Total recalculado.** `unit_price = base + Σ deltas`; `total = subtotal − discount +
  delivery_fee`, tudo materializado em Java no INSERT (`AdegaOrderRepository.createOrder`,
  `@Transactional`). O `total_cents` da tag é descartado.
- **R3 — Cupom best-effort, validado no backend.** Válido = `active` + `valid_until` ≥ hoje (fuso
  America/Sao_Paulo) + `subtotal ≥ min_order_cents` + `uses < max_uses`. Inválido → sem desconto,
  pedido segue. `uses` incrementa NA MESMA transação da criação. Código único por adega
  (case-insensitive: UNIQUE `(company_id, lower(code))`).
- **R4 — Fidelidade por contagem.** Com `enabled`, conta os pedidos `entregue` do contato **ANTES**
  de inserir o novo; `count > 0 && count % threshold_orders == 0` → reward (percent do subtotal ou
  fixed) + `loyalty_applied=true`. Sem pontos/saldo.
- **R5 — Desconto clampado.** `discount = min(subtotal, cupom + fidelidade)` — total nunca negativo.
  Cupom e fidelidade SOMAM.
- **R6 — Opção fantasma aborta** (`InvalidOptionException`); snapshots de item/opção imutáveis
  (`menu_item_id ON DELETE RESTRICT` → 409 `menu_item_in_use`; `menu_option_id ON DELETE SET NULL`).
- **R7 — INSERT de pedido só pelo backend** (sem policy de INSERT para `authenticated`; não há POST
  REST de pedido). `delivery_address` NOT NULL — sempre entrega.

### Máquina de status

```
aguardando ──aceitar──→ em_preparo ──→ saiu_entrega ──→ entregue
    │                        │               │
    └──recusar──→ recusado   └──→ cancelado ←┘
```

| Transição | Quem pode | Notifica? |
|---|---|---|
| `aguardando → em_preparo` (gate de aceite) | humano no painel | Sim ("…separando suas bebidas. 🍷") |
| `aguardando → recusado` | humano | Sim (defensivo + motivo opcional) |
| `em_preparo → saiu_entrega` | humano | Sim (inclui "Beba com moderação") |
| `saiu_entrega → entregue` | humano | Sim (idem) |
| `em_preparo/saiu_entrega → cancelado` | humano | Sim ("…é só me chamar") |

Terminais: `entregue`, `recusado`, `cancelado`. **De `aguardando` NÃO se cancela** — só
aceita/recusa (`AdegaOrderStatus.allowedNext`). Transição inválida → 409 `invalid_status_transition`.

### O que a IA PODE × NUNCA faz (travas da persona)

PODE: sugerir harmonização do próprio cardápio; oferecer **UMA** sugestão de conveniência no
fechamento (harmonização, acessório ou completar o mínimo) — nunca como incentivo a beber mais;
registrar o código do cupom na tag. NUNCA: vende a menor de idade (sem maioridade confirmada não
fecha, e o backend recusa criar); incentiva consumo excessivo ou minimiza riscos do álcool; inventa
rótulo/marca/safra/volume/opção/preço; inventa desconto ou cupom (quem valida é o sistema); aceita
ou recusa o pedido; define o total (persona `ProfilePromptContext.ADEGA` + regras do
`AdegaMenuCache`).

### Tags de IA

| Tag | Quando a IA emite | Campos | O backend descarta/recalcula |
|---|---|---|---|
| `<pedido_adega>` | fechamento, após confirmar maioridade + endereço + total | `age_confirmed` (obrigatório `true`), `items[{item_id, qtd, options:[UUID...]}]`, `endereco`, `cupom` (opcional), `total_cents` | `total_cents` ignorado; preços re-lidos do cardápio; cupom/fidelidade calculados no backend; sem `age_confirmed:true` a tag é abortada |

### Validações e erros

| reason | HTTP | Significado | Cenário |
|---|---|---|---|
| `forbidden_wrong_profile` | 403 | tenant de outro perfil | qualquer `/api/adega/**` (`AdegaProfileGuard`) |
| `invalid_status` / `invalid_status_transition` | 400 / 409 | alvo desconhecido / transição proibida | PATCH `/orders/{id}/status` |
| `order_not_found` | 404 | pedido de outro tenant | GET/PATCH por id |
| `menu_item_in_use` | 409 | item com pedido histórico | DELETE de item |
| `menu_item_not_found` / `option_not_found` / `coupon_not_found` | 404 | CRUD | id inexistente |
| `invalid_category` | 400 | fora de `AdegaCategory` (6 hardcoded) | POST/PATCH item |
| `invalid_coupon` | 400 | kind/value inválidos | POST/PATCH cupom |
| `duplicate_coupon` | 409 | código repetido (case-insensitive) | POST/PATCH cupom |
| `invalid_loyalty_config` | 400 | threshold/reward inválidos | PUT `/loyalty` |
| `age_not_confirmed` | (422 convenção) | pedido sem maioridade confirmada | **só no fluxo da IA**: handler/service abortam sem criar — o reason vive na exceção, não há POST REST de pedido |

### Notificações ao cliente

Texto fixo ao ENTRAR no status (`AdegaOrderStatus.notificationText`, best-effort via
`AdegaOrderNotifier`). `aguardando` é silencioso (a IA já confirmou o recebimento). `saiu_entrega` e
`entregue` sempre incluem "Beba com moderação" (compliance no texto). Recusa concatena o motivo,
defensivamente. Diferente da padaria/papelaria, **`cancelado` NOTIFICA** aqui.

## Dados e snapshots

| Tabela | Constraints que são regra |
|---|---|
| `adega_config` (1:1) | taxa/mínimo ≥ 0; ausente → 0/0 |
| `adega_menu_items` | categoria CHECK nas 6 hardcoded (vinhos, espumantes, cervejas, destilados, sem_alcool, acessorios); volume/safra/teor vão na `description` |
| `adega_menu_item_options` | `price_delta_cents ≥ 0`; CASCADE do item |
| `adega_orders` | **`age_confirmed` NOT NULL sem default**; status CHECK (6); `delivery_address` NOT NULL; mig 80: `discount_cents ≥ 0`, `coupon_id ON DELETE SET NULL` + `coupon_code_snapshot`, `loyalty_applied` |
| `adega_order_items` / `_options` | `qtd > 0`; snapshots de nome/preço/group/option/delta |
| `adega_coupons` | UNIQUE `(company_id, lower(code))`; `percent` CHECK 1..100; `uses ≥ 0` |
| `adega_loyalty_config` (1:1) | `threshold_orders ≥ 1` (default 10); percent CHECK 0..100; `enabled` default false; seed idempotente por company adega |

RLS enable+force via `app.company_id()`. Cache: `AdegaMenuCache` (Caffeine, **TTL 60s**), invalidado
pelo `AdegaMenuService` em toda mutação de item/opção/config; o bloco injeta a REGRA +18 e o formato
da tag.

## Features de onda (backlog implementado — migration 80)

- **#1 Cupom** (`adega_coupons` + telas `/dashboard/adega-coupons`): regras em R3. A IA só passa o
  código; desconto aplicado sobre o subtotal.
- **#2 Fidelidade** (`adega_loyalty_config`): regras em R4; default **desligada** (`enabled=false`,
  opt-in do tenant). Reward configurável percent/fixed.
- A trava +18 NÃO muda: desconto só é calculado DEPOIS do `age_confirmed`.

## O que NÃO existe (limites honestos)

- **Pedido mínimo NÃO é validado no backend** (`min_order_cents` só entra no prompt e na validação
  do cupom) — a trava é conversacional.
- Validação documental de idade (RG/foto/biometria) — a confirmação é **declaratória**; verificação
  real na entrega é processo da loja. Dispensa da trava para carrinho sem-álcool (todo pedido passa).
- Clube de assinatura de vinho; curadoria/scoring de safra; estoque por garrafa; iFood/Zé Delivery;
  rastreio em mapa/ETA; pagamento online (Stripe #50); foto de rótulo (`SERVICE_ROLE_KEY`);
  scheduler de auto-transição; meio-a-meio/regra do maior valor (isso é do pizzaria).
