# Padaria & Confeitaria — regras de negócio (`padaria`, camada 8.8)

[← Catálogo](../05-nichos.md) · Chassi: **B — pedido order-based com gate de aceite** (clone do floricultura) · Guia operacional: `docs/PERFIL_PADARIA.md` · Migrations: **52** (base), **96** (onda 1)

## O negócio em 3 linhas

Padaria/confeitaria de bairro que vende **pronta-entrega** (pães, salgados, doces de balcão) e **sob
encomenda** (bolos, tortas). O cliente final pede pelo WhatsApp; a IA monta o pedido na conversa,
oferece personalização do bolo (sabor/recheio/tamanho + texto da plaquinha), coleta a data quando há
encomenda e a padaria acompanha num Kanban com gate de aceite humano.

## Jornada no WhatsApp (cenários)

1. Cliente pede itens; a IA responde com o cardápio injetado no prompt (`PadariaMenuCache`).
2. O carrinho vive **na conversa** (sem entidade de carrinho — a IA relê o histórico).
3. Se há item sob encomenda, a IA coleta a **data** (≥ hoje + maior lead time) e pergunta
   **retirada × entrega** (entrega exige endereço e soma taxa).
4. Na confirmação a IA emite `<encomenda_padaria>{...}` — o `EncomendaPadariaConfirmHandler` parseia,
   valida cada item contra o cardápio e cria o pedido `aguardando`. A tag é removida antes do envio.
5. A padaria **aceita** (→ `em_preparo`, notifica) ou **recusa** (→ `recusado`, notifica com motivo)
   no painel. A IA nunca aceita/recusa.

**Exceções** (todas abortam a criação — a mensagem da IA segue sem pedido): JSON/fulfillment/período
inválido, data no passado, item inexistente ou indisponível, opção fantasma, data ausente/cedo demais
com item sob encomenda (lead time), entrega sem endereço. **Onda 1:** encomenda com sinal registrado e
não pago não pode ser aceita (409 `deposit_required` no painel).

## Regras de negócio

### Transacionais (invariantes duras)

- **R1 — Total recalculado do cardápio.** `unit_price = preço base + Σ price_delta` das opções;
  `subtotal = Σ unit_price × qtd`; `total = subtotal + taxa (só entrega)`. O `total_cents` da tag é
  DESCARTADO (materializado em Java no INSERT em `PadariaOrderRepository.createOrder`, dentro de
  `@Transactional`).
- **R2 — Lead time condicional.** Se há QUALQUER item `made_to_order`, `pickup_or_delivery_date` é
  obrigatória e ≥ `hoje + MAX(lead_time_days do item, fallback lead_time_days_default da config)`,
  fuso America/Sao_Paulo. Violação → `LeadTimeViolationException` carregando a **1ª data possível**
  (reason convencionado `lead_time_violation`, 422). Pedido só de pronta-entrega não exige data.
- **R3 — Entrega exige endereço.** `fulfillment='entrega'` sem `delivery_address` →
  `AddressRequiredException` (convenção `address_required`, 422); retirada zera taxa e endereço.
- **R4 — Opção fantasma aborta.** Toda opção pedida deve existir, estar disponível e pertencer ao
  item (`findByIdsForItem`; contagem divergente → `InvalidOptionException` — pedido NÃO nasce).
- **R5 — Snapshot imutável.** Nome, preço, `made_to_order`, opções e `cake_message` são copiados no
  INSERT; mudar o cardápio não altera pedidos passados (`menu_item_id ON DELETE RESTRICT` → 409
  `menu_item_in_use`; `menu_option_id ON DELETE SET NULL` preserva o histórico).
- **R6 — Sinal trava o aceite (onda 1).** `deposit_cents > 0` e `deposit_paid=false` bloqueia
  `aguardando → em_preparo` → 409 `deposit_required`; marcar pago libera. Sem sinal, aceite livre.
- **R7 — INSERT de pedido só pelo backend.** Não há policy de INSERT para `authenticated` em
  `padaria_orders` (nem POST REST de pedido); o tenant só SELECT/UPDATE via RLS.

### Máquina de status

```
aguardando ──aceitar──→ em_preparo ──→ pronto ──→ retirado            (retirada)
    │                                     └────→ saiu_entrega ──→ entregue   (entrega)
    ├──recusar──→ recusado
    └──→ cancelado  (também de em_preparo/pronto/saiu_entrega)
```

| Transição | Quem pode | Notifica? |
|---|---|---|
| `aguardando → em_preparo` (gate de aceite) | humano no painel | Sim ("Pedido aceito! 🍞…") |
| `aguardando → recusado` | humano no painel | Sim (defensivo + motivo opcional) |
| `em_preparo → pronto` | humano | Sim |
| `pronto → retirado` | humano | **Não** (cliente está no balcão) |
| `pronto → saiu_entrega → entregue` | humano | Sim (ambas) |
| `* → cancelado` (de não-terminal) | humano | **Não** (silencioso) |

Terminais: `retirado`, `entregue`, `recusado`, `cancelado`. Transição inválida → 409
`invalid_status_transition` (`PadariaOrderStatus.canTransitionTo`, parity com
`padaria-order-status.ts`).

### O que a IA PODE × NUNCA faz (travas da persona)

PODE: montar o pedido em linguagem livre, oferecer personalização do bolo e o texto da plaquinha,
oferecer **UMA** sugestão de complemento do próprio cardápio no fechamento (upsell onda 1, sem
insistir), informar o valor do sinal registrado. NUNCA: inventa produto/sabor/recheio/tamanho/
adicional/preço fora do cardápio; promete data antes do lead time (oferece a 1ª possível); promete
decoração/tema não cadastrado (bolo artístico → "vou confirmar com a confeitaria"); aceita/recusa o
pedido; define o total; **confirma pagamento de sinal** (persona `ProfilePromptContext.PADARIA` +
instruções do `PadariaMenuCache`).

### Tags de IA

| Tag | Quando a IA emite | Campos | O backend descarta/recalcula |
|---|---|---|---|
| `<encomenda_padaria>` | na confirmação do pedido | `fulfillment`, `pickup_or_delivery_date`, `delivery_period`, `delivery_address`, `items[{menu_item_id, options[{option_id}], cake_message, quantity}]`, `notes` | `total_cents` ignorado; preços/nomes re-lidos do cardápio; lead time e endereço revalidados |

### Validações e erros

| reason | HTTP | Significado | Cenário |
|---|---|---|---|
| `forbidden_wrong_profile` | 403 | tenant de outro perfil | qualquer `/api/padaria/**` (`PadariaProfileGuard`) |
| `invalid_status` | 400 | status alvo desconhecido | PATCH status com id fora do enum |
| `invalid_status_transition` | 409 | transição não permitida | ex.: `aguardando → pronto` |
| `deposit_required` | 409 | sinal registrado e não pago | aceitar encomenda com sinal pendente |
| `invalid_deposit` | 400 | sinal negativo, ou "pago" sem valor > 0 | PATCH `/orders/{id}/deposit` |
| `order_not_found` | 404 | pedido de outro tenant/inexistente | GET/PATCH por id |
| `menu_item_in_use` | 409 | item com pedido histórico | DELETE de item do cardápio |
| `menu_item_not_found` / `option_not_found` | 404 | CRUD do cardápio | id inexistente |
| `invalid_category` | 400 | categoria fora de `PadariaCategory` | POST/PATCH item |
| `lead_time_violation` · `address_required` | (422 convenção) | data antes do prazo · entrega sem endereço | **só no fluxo da IA**: o handler captura e aborta sem criar (não existe POST REST de pedido — o reason vive na exceção, nunca chega como HTTP) |

### Notificações ao cliente

Texto FIXO e defensivo por status (`PadariaOrderStatus.notificationText`, best-effort via
`PadariaOrderNotifier` — falha não reverte a transação). `aguardando` é silencioso (a IA já confirmou
o recebimento na própria mensagem); `retirado` e `cancelado` também ("quem cancela não recebe
sermão"). Na recusa, o `rejection_reason` é concatenado ao texto pelo service.

## Dados e snapshots

| Tabela | Constraints que são regra |
|---|---|
| `padaria_config` (1:1) | `delivery_fee_cents/min_order_cents ≥ 0`; `lead_time_days_default ≥ 0` (default 1); ausente → 0/0/1 |
| `padaria_menu_items` | categoria CHECK nas 6 hardcoded (paes, salgados, doces_balcao, bolos_encomenda, tortas, bebidas — o flag `made_to_order` por item é a verdade, não a categoria); `lead_time_days` NULL = usa default; `price_cents ≥ 0` |
| `padaria_menu_item_options` | `price_delta_cents ≥ 0` (não negativo nesta fase); `ON DELETE CASCADE` do item |
| `padaria_orders` | status CHECK (8), fulfillment CHECK, `delivery_period` CHECK manha/tarde; sinal (mig 96): `deposit_cents ≥ 0`, `deposit_paid` default false |
| `padaria_order_items` | `qtd > 0`; snapshots `unit_price_cents` (já com Σ deltas), `item_name_snapshot`, `made_to_order_snapshot`, `cake_message` |
| `padaria_order_item_options` | snapshots de group/option/delta |

RLS enable+force em tudo, via `app.company_id()`. Cache: `PadariaMenuCache` (Caffeine, **TTL 60s**),
invalidado explicitamente pelo `PadariaMenuService`/config em toda mutação.

## Features de onda (backlog implementado — migration 96)

- **#1 Sinal em encomenda:** `deposit_cents/deposit_paid/deposit_paid_at`; registro MANUAL
  (PATCH `/api/padaria/orders/{id}/deposit`) até o gateway Stripe (#50). Regra: sinal registrado e
  não pago bloqueia o gate de aceite (R6); `deposit_paid_at` preservado enquanto pago
  (`coalesce(deposit_paid_at, now())`). O prompt instrui a IA a informar o valor sem nunca confirmar
  pagamento.
- **#6 Upsell na persona (sem DDL):** UMA sugestão de complemento do próprio cardápio no fechamento.

## O que NÃO existe (limites honestos)

- **Pedido mínimo NÃO é validado no backend**: `min_order_cents` só é injetado no prompt — a trava é
  conversacional (diferente da lavanderia, que aborta por `below_minimum`).
- Foto do bolo (bloqueador `SERVICE_ROLE_KEY`); orçamento de bolo artístico ad-hoc; assinatura de
  pães; combo/cupom/fidelidade; pagamento online real (Stripe #50); iFood; estoque/produção;
  slot por horário fino (é dia + faixa manhã/tarde); tabela nutricional estruturada; scheduler de
  auto-transição de status.
