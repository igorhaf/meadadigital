# Perfil Padaria (Padaria & Confeitaria) — camada 8.8

Guia operacional do tenant **padaria** (`profile_id='padaria'`). A IA atende clientes pelo WhatsApp,
conhece o cardápio (pronta-entrega + sob encomenda), monta o pedido na conversa, coleta a data quando há
encomenda, e a padaria acompanha num Kanban com gate de aceite. Tom caloroso, "de bairro".

É o **30º perfil vertical** (29 + generic). CLONA o chassi do Floricultura (8.5, pedido agendado + gate
de aceite + modifiers).

## Telas (sidebar "Padaria")

| Tela | Rota | O que faz |
|------|------|-----------|
| Cardápio | `/dashboard/padaria-menu` | CRUD de itens + opções (Sabor/Recheio/Tamanho) + toggle sob encomenda + lead time + alérgenos. |
| Pedidos | `/dashboard/padaria-orders` | Kanban com gate de aceite + retirada/entrega + personalização + placa do bolo. |
| Configurações | `/dashboard/padaria-settings` | Taxa de entrega + pedido mínimo + lead time default. |

## ESCAPADA 1 — Pronta-entrega × sob-encomenda com lead time

Cada item do cardápio tem `made_to_order` + `lead_time_days` (nullable, override do default da config).
Itens de **pronta-entrega** (pão, salgado, doce de balcão) NÃO exigem data. Itens **sob encomenda**
(bolo, torta) exigem `pickup_or_delivery_date` que respeite a antecedência mínima.

A data é **CONDICIONAL**: obrigatória só se há item sob encomenda no pedido; e é a MAIOR antecedência
exigida entre os itens (`hoje + MAX(lead_time dos itens made_to_order)`, em America/Sao_Paulo). Data antes
disso → **422 `lead_time_violation`** com a primeira data possível na resposta (defensivo). Um pedido pode
misturar pronta-entrega e encomenda.

## ESCAPADA 2 — Personalização do bolo

Além dos modifiers planos (Sabor/Recheio/Tamanho via `padaria_menu_item_options` com price_delta), o item
do pedido carrega `cake_message` (texto livre da plaquinha, snapshot — ex.: "Feliz aniversário, Ana").

## Retirada × Entrega

`fulfillment` = `retirada` (balcão, sem taxa nem endereço) ou `entrega` (exige `delivery_address` → senão
422 `address_required`, + soma `delivery_fee`). O funil de status diverge no fim: `pronto → retirado`
(retirada) ou `pronto → saiu_entrega → entregue` (entrega).

## Status (gate de aceite humano)

`PadariaOrderStatus` ↔ TS (parity): `aguardando → em_preparo → pronto → {retirado | saiu_entrega →
entregue}`; `recusado`/`cancelado` terminais. O **gate de aceite** é `aguardando → em_preparo` (humano no
painel; a IA não aceita/recusa). Notifica em_preparo/pronto/saiu_entrega/entregue/recusado; **aguardando
NÃO notifica** (a IA já confirmou o recebimento). Total materializado (descarta o da IA). Snapshots de
item/opção/cake_message.

## Categorias

Hardcoded (`PadariaCategory` ↔ `padaria-categories.ts`, parity): paes, salgados, doces_balcao,
bolos_encomenda, tortas, bebidas. (bolos_encomenda/tortas tendem a ser made_to_order, mas o flag por item
é a verdade, não a categoria.)

## Tag

**`<encomenda_padaria>`**: `{ "fulfillment", "pickup_or_delivery_date", "delivery_period", "delivery_address",
"items":[{"menu_item_id","options":[{"option_id"}],"cake_message","quantity"}], "notes" }`. Namespace
próprio. total_cents descartado; o backend recalcula.

## O que a IA NÃO faz

Inventar produto/sabor/recheio/tamanho/adicional/preço; aceitar/recusar; prometer data antes do lead time;
prometer decoração/tema não cadastrado (bolo artístico → "vou confirmar com a confeitaria").

## O que NÃO existe nesta fase

Foto do bolo (bloqueador SERVICE_ROLE_KEY); orçamento de bolo artístico ad-hoc com aprovação; assinatura
de pães recorrente; combo/cupom/fidelidade; pagamento real (Stripe #50); iFood; estoque/produção; slot por
horário fino (é dia + faixa); tabela nutricional estruturada.

## Notas técnicas

- Migration `52_padaria.sql` (6 tabelas). A CHECK ACRESCENTA `'padaria'` preservando os 29 perfis. Como 52
  < 67 no disco, sua CHECK já lista TODOS os 30; entra por ÚLTIMO no `SCRIPTS` do `AbstractIntegrationTest`
  (a migration que reescreve a CHECK por último precisa ter a lista completa — lição atelie).
- Base de conhecimento (RAG): disponível como em todo perfil.
- Guard `/api/padaria/**` → 403 `forbidden_wrong_profile`. Paleta `abobora`. Tenant: `igorhaf19`.

## Onda 1 do backlog (2026-07 — FEATURES_SUGERIDAS_PADARIA #1/#6, migration 96)

- **Sinal em encomenda (#1):** `deposit_cents/paid/paid_at` no pedido (PATCH
  `/api/padaria/orders/{id}/deposit`, registro manual até o gateway #50). Com sinal REGISTRADO
  e não pago, o GATE DE ACEITE fica bloqueado: aguardando→em_preparo → **409
  `deposit_required`**; pagar libera. Sem sinal, aceite livre. Painel: selo + modal "Sinal" no
  card em aguardando. O cache instrui a IA a informar o valor sem nunca confirmar pagamento.
- **Upsell na persona (#6, sem DDL):** UMA sugestão de complemento do próprio cardápio (vela/
  refrigerante/docinhos/cartão) no fechamento, sem insistir.
- **Nota de flakiness (2ª ocorrência):** `PessimisticLockingFailureException` transitória no
  TRUNCATE do `AbstractIntegrationTest` (contenção entre ApplicationContexts) — não relacionada
  à onda; re-rodar o gate resolve.
