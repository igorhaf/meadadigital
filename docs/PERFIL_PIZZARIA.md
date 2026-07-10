# Perfil Pizzaria (delivery) — camada 8.6

Guia operacional do tenant **pizzaria** (`profile_id='pizzaria'`). Pizzaria delivery: a equipe
gerencia o cardápio (sabores + bordas + bebidas + sobremesas + combos, cada um com modifiers de
Tamanho/Borda), a IA atende clientes pelo WhatsApp e monta o pedido na conversa — **incluindo pizza
meio-a-meio** — e a pizzaria acompanha o fluxo num Kanban com **gate de aceite**.

É o **17º perfil vertical** (sushi · legal · restaurant · dental · salon · pousada · academia · pet ·
oficina · nutri · barbearia · eventos · estetica · comida · floricultura · **pizzaria**). Clona o
chassi do **ComidaBot** (camada 8.4 — cardápio + carrinho-na-conversa + tag de pedido + recálculo de
total no backend + snapshot do pedido + taxa de entrega/pedido mínimo + Kanban + gate de aceite +
modifiers) e adiciona **uma escapada estrutural nova**: a **pizza meio-a-meio** com preço pela
**regra do maior valor**.

## Telas (sidebar "Pizzaria")

| Tela | Rota | O que faz |
|------|------|-----------|
| Cardápio | `/dashboard/pizzaria-menu` | CRUD de sabores/itens (nome, descrição, preço, categoria, disponível) **e** dos modifiers de cada item (Tamanho, Borda — grupos com delta de preço). |
| Pedidos | `/dashboard/pizzaria-orders` | Kanban do pedido + **gate de aceite** (aceitar/recusar) + histórico (entregues/recusados/cancelados). Cada item-pizza mostra os **sabores das frações**. |
| Configurações | `/dashboard/pizzaria-settings` | Taxa de entrega (flat) + pedido mínimo. |

## ESCAPADA — Pizza meio-a-meio (regra do MAIOR VALOR)

A pizza pode ser dividida em **N frações** (1 = inteira, 2 = meio-a-meio; o modelo já suporta N),
cada fração com um **sabor** diferente. Cada sabor é uma linha de `pizzaria_menu_items` (uma pizza de
categoria `pizzas_salgadas`/`pizzas_doces`). As frações são modeladas na sub-entidade
**`pizzaria_order_item_flavors`** (snapshot de `flavor_name` + `flavor_price_cents` + `fraction_index`).

O **preço da pizza meio-a-meio** segue a **REGRA DO MAIOR VALOR** (cravada): cobra-se o preço do
**sabor MAIS CARO** no tamanho escolhido, **+ Σ dos deltas dos modifiers** (Tamanho, Borda). **NÃO é
a soma dos sabores, NÃO é a média.** Tudo **recalculado no backend** (o `total_cents` da tag da IA é
descartado).

```
unit_price = MAX(preço dos sabores) + Σ deltas dos modifiers
```

> **Exemplo (smoke):** meio **Portuguesa** (R$ 52,00) / meio **Quatro Queijos** (R$ 55,00), tamanho
> **Grande** (+R$ 12,00), borda **Recheada** (+R$ 10,00):
> `MAX(5200, 5500) + 1200 + 1000 = 7700` → **R$ 77,00**. Não R$ 129,00 (soma) nem R$ 75,50 (média).

O `menu_item_id` do item do pedido aponta para o **sabor principal** (o de maior preço); os modifiers
(Tamanho/Borda) são resolvidos sobre esse sabor principal. Um `flavor_id` inválido/indisponível/de
outro tenant **aborta a criação** do pedido (simétrico ao `item_id` inválido).

## Herda do chassi comida

- **Gate de aceite (ação humana, não da IA):** o pedido nasce **aguardando** (a IA já confirmou o
  RECEBIMENTO); a pizzaria **ACEITA** (→ `em_preparo`) ou **RECUSA** (→ `recusado`, terminal, com
  motivo opcional) no painel. `aguardando` **NÃO notifica** (evita mensagem duplicada).
- **Modifiers (Tamanho, Borda):** grupos de opção por item em `pizzaria_menu_item_options`, com
  snapshot em `pizzaria_order_item_options` no pedido.

```
aguardando ──aceitar──→ em_preparo ──→ saiu_entrega ──→ entregue
    │                        │                │
    └──recusar──→ recusado   └──→ cancelado ←─┘
```

Transição inválida → 409 `invalid_status_transition`. Terminais: `entregue`, `recusado`, `cancelado`.

### Notificações outbound (texto fixo ao ENTRAR no status)

| Status | Notifica? | Texto |
|--------|-----------|-------|
| `aguardando` | **Não** (silencioso) | — (a IA já confirmou o recebimento na conversa) |
| `em_preparo` | Sim | "Seu pedido foi aceito! Já estamos preparando sua pizza 🍕" |
| `saiu_entrega` | Sim | "Sua pizza saiu pra entrega. Já já chega aí, quentinha!" |
| `entregue` | Sim | "Pizza entregue! Bom apetite e obrigado pela preferência!" |
| `recusado` | Sim | Texto defensivo + motivo opcional. |
| `cancelado` | Sim | "Seu pedido foi cancelado. Se quiser refazer, é só me chamar." |

## O que a IA faz

- Monta o pedido em **linguagem livre** na conversa, **inclusive pizza meio-a-meio** (2 sabores numa
  mesma pizza).
- **Confirma SEMPRE** com o valor total e o endereço antes de fechar.
- Emite a tag `<pedido_pizza>`; o pedido nasce **aguardando**.
- Avisa o cliente que o pedido vai para **confirmação da pizzaria**.

## O que a IA NÃO faz

- **Não aceita nem recusa** pedido — isso é a pizzaria no painel (gate de aceite).
- **Não inventa** sabor, opção ou preço fora do cardápio.
- **Não define o total** — recalculado no backend; o `total_cents` da tag é **descartado**. Em
  especial, **não calcula o preço da meio-a-meio** (a regra do maior valor é do sistema).
- **Não cria** item de cardápio.

## Tag `<pedido_pizza>`

Formato JSON em texto livre (NÃO é tool calling do Gemini — texto livre + regex). O `OutboundService`
remove a tag antes de enviar a mensagem ao cliente. Cada item tem **dois modos**:

```json
{
  "items": [
    { "flavors": ["UUID_PORTUGUESA", "UUID_QUATRO_QUEIJOS"], "options": ["UUID_TAMANHO_G", "UUID_BORDA_RECHEADA"], "qtd": 1 },
    { "item_id": "UUID_GUARANA", "qtd": 2, "options": [] }
  ],
  "endereco": "Rua X, 10",
  "total_cents": 0
}
```

- Pizza → use **`flavors`** (lista de UUIDs dos sabores das frações: 1 = inteira, 2 = meio-a-meio);
  **sem `item_id`**. O preço é o do sabor mais caro + os modifiers.
- Item simples (bebida/sobremesa/borda/combo) → use **`item_id`** + `options`.
- `options` é **opcional** por item.
- `total_cents` é **ignorado** pelo backend.

## O que NÃO existe nesta fase

- **Foto** de item/cardápio (bloqueador `SERVICE_ROLE_KEY`).
- **Pagamento online** (Stripe).
- **Rastreio** de entregador / mapa / ETA dinâmica.
- **Avaliação/nota** do pedido.
- **Cupom/desconto/promoção**.
- **Frações além de meio-a-meio na UI** (o modelo suporta N; a IA e a tela hoje cobrem 1 e 2).
- **Preço por tamanho POR SABOR** (o tamanho é um modifier de delta único sobre o sabor principal,
  não uma tabela de preço por sabor×tamanho — fase futura se necessário).
- **Horário de funcionamento próprio**, **múltiplos endereços salvos**, **edição de pedido após
  criado**, **entregador como entidade**, **taxa por bairro/distância** (taxa flat, 1 valor por
  company) — iguais ao comida.

## Notas técnicas

- Migration `50_pizzaria.sql` (7 tabelas: config, menu_items, menu_item_options, orders, order_items,
  order_item_options, **order_item_flavors**). A CHECK de `companies.profile_id` ACRESCENTA
  `'pizzaria'` preservando todos os 16 perfis anteriores.
- Categorias **hardcoded** (pizzas_salgadas / pizzas_doces / bordas / bebidas / sobremesas / combos)
  em sync `PizzaCategory.java` ↔ `pizzaria-categories.ts` (`PizzaCategoryParityTest`).
- Status **hardcoded** `PizzaOrderStatus` (Java) ↔ union em `pizzaria-types.ts`
  (`PizzaOrderStatusParityTest`).
- Guard de perfil: `/api/pizzaria/**` → 403 `forbidden_wrong_profile` para tenant de outro perfil.
- `unit_price`/`subtotal`/`total` MATERIALIZADOS no INSERT (não generated) — o recálculo cruza
  linhas/tabelas (lição das migrations anteriores).
- Paleta `carmim`.
- Tenant de teste: `igorhaf17` (Pizzaria Modelo).

## Onda 1 do backlog (2026-07 — FEATURES_SUGERIDAS_PIZZARIA #1/#2/#3, migration 93)

Clone da onda adega (mig 80), sobre o motor unificado `com.meada.common.coupons`:

- **Cupom (#1, `pizzaria_coupons`):** percent/fixed + mínimo + validade + max usos; CRUD
  `/api/pizzaria/coupons` + tela `/dashboard/pizzaria-coupons`. A IA passa SÓ o código no campo
  `cupom` da tag `<pedido_pizza>`; o backend valida (active + validade + mínimo + usos) e aplica
  no total materializado; cupom inválido NÃO aborta (o pedido sai sem desconto); `uses`
  incrementa na MESMA transação.
- **Fidelidade por contagem (#2, `pizzaria_loyalty_config`):** enabled + threshold_orders +
  reward percent/fixed. Conta os pedidos ENTREGUES do contato ANTES do insert; `count>0 &&
  count%threshold==0` → desconto automático + `loyalty_applied=true`. Tela
  `/dashboard/pizzaria-loyalty`. Cupom + fidelidade SOMAM, clampados ao subtotal
  (`total = subtotal − discount + delivery_fee`).
- **Upsell na persona (#3, sem DDL):** o bloco do `PizzariaMenuCache` autoriza UMA sugestão de
  complemento do próprio cardápio (borda recheada/bebida/sobremesa) antes de fechar, sem
  insistir; e ensina o campo `cupom` ("quem valida/calcula é o sistema").
