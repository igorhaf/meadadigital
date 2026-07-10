# Perfil Lingerie (moda íntima · varejo) — camada 8.21

Guia operacional do tenant **lingerie** (`profile_id='lingerie'`). A IA atende clientes pelo WhatsApp,
monta o pedido escolhendo a VARIANTE exata (tamanho × cor), e a loja acompanha num Kanban com gate de
aceite. Tom acolhedor, discreto e respeitoso.

É o **27º perfil vertical** (26 + generic) e **INAUGURA o chassi de varejo com variantes** — reusado
por Moda Infantil (8.22) e Lãs (8.23).

## Telas (sidebar "Lingerie")

| Tela | Rota | O que faz |
|------|------|-----------|
| Catálogo | `/dashboard/lingerie-catalog` | CRUD de produtos + GRADE de variantes (tamanho×cor, preço, ESTOQUE). |
| Pedidos | `/dashboard/lingerie-orders` | Kanban com gate de aceite (Aceitar/Recusar) + entrega/retirada. |
| Configurações | `/dashboard/lingerie-settings` | Taxa de entrega + pedido mínimo. |

## ⭐ ESCAPADA — Grade de variantes (tamanho × cor) com estoque

Um **produto** (`lingerie_products`: nome, categoria, base_price_cents) tem uma **grade de variantes**
(`lingerie_variants`): cada linha é uma combinação (size, color) com SEU `price_cents` (ou herda o base
do produto se null) e SEU `stock_qty`. A variante é o **SKU real**; `UNIQUE(product_id, size, color)`.
O pedido referencia a **VARIANTE**, não o produto.

**Decremento transacional de estoque (o coração da SM):** ao criar o pedido, para cada linha o backend
faz `UPDATE lingerie_variants SET stock_qty = stock_qty - qtd WHERE id = ? AND stock_qty >= qtd`. Se o
UPDATE afeta 0 linhas, a variante está esgotada → `OutOfStockException` → **409 `out_of_stock`** e o
pedido inteiro é ABORTADO (rollback — nada de pedido parcial). O `WHERE stock_qty >= qtd` fecha a janela
de corrida (dois pedidos pela última unidade → só um vence).

`size` é hardcoded com paridade (`LingerieSize` ↔ `lingerie-size.ts`: PP/P/M/G/GG/XGG); `color` é texto
livre.

## Chassi de varejo (clone do comida/adega)

Pedido nasce `aguardando` (**gate de aceite humano**: a loja ACEITA → `separando` ou RECUSA →
`recusado` com motivo; a IA NÃO aceita/recusa). Máquina (`LingerieOrderStatus` ↔ TS, parity):
`aguardando → separando → enviado → entregue`; `recusado`/`cancelado` terminais. Notifica separando/
enviado/entregue/recusado/cancelado; **aguardando NÃO notifica** (a IA já confirmou o recebimento).
Total MATERIALIZADO no backend (descarta o da IA): `subtotal = Σ unit_price×qtd`, `total = subtotal +
(entrega ? taxa : 0)`. SNAPSHOT de produto/variante/preço no item — alterar o catálogo depois NÃO altera
pedidos passados. `fulfillment` = entrega (com endereço) ou retirada.

## Tag

**`<pedido_lingerie>`**: `{ "items":[{"variant_id":"UUID","qtd":2}], "fulfillment":"entrega|retirada",
"endereco":"...", "total_cents":0 }`. Namespace próprio. A IA usa o `variant_id` exato do catálogo,
NUNCA oferece variante esgotada, e o backend descarta o `total_cents`.

## O que a IA faz / NÃO faz

- **FAZ:** apresenta o catálogo, monta o pedido pela variante (tamanho+cor), confirma total +
  entrega/retirada, avisa que vai para confirmação da loja.
- **NÃO FAZ:** oferecer variante esgotada; inventar produto/tamanho/cor/preço; aceitar/recusar pedido;
  qualquer apelo sensual ou comentário sobre o corpo.

## O que NÃO existe nesta fase

- Foto de produto (bloqueador SERVICE_ROLE_KEY); pagamento real (Stripe #50); reserva de estoque sem
  pedido; devolução/troca com reentrada de estoque; cupom/promoção; frete por CEP/distância (taxa flat);
  variante com 3+ eixos (só size×color); kit/combo; lista de desejos.

## Notas técnicas

- Migration `65_lingerie.sql` (5 tabelas: config, products, variants, orders, order_items). A CHECK
  ACRESCENTA `'lingerie'` preservando os 26 perfis. Entra por ÚLTIMO no `SCRIPTS` do
  `AbstractIntegrationTest` (sua CHECK tem os 27).
- Base de conhecimento (RAG): disponível como em todo perfil (item "Conhecimento" do nav +
  `{{knowledge}}` do PromptBuilder, sem gate de perfil).
- Guard `/api/lingerie/**` → 403 `forbidden_wrong_profile`. Paleta `ameixa`. Tenant: `igorhaf32`.

## Onda 1 do backlog (2026-07 — FEATURES_SUGERIDAS_LINGERIE #1/#3/#4, migration 101)

- **Cupom (#4, motor unificado):** `lingerie_coupons` + desconto materializado; campo `cupom` na
  tag `<pedido_lingerie>`; inválido não aborta. Tela Cupons + nav.
- **Avise-me quando voltar (#1, `lingerie_stock_alerts`):** espelho da moda infantil — tag
  `<aviso_estoque_lingerie>{variant_id}` + disparo na reposição 0→N do painel.
- **Cross-sell "completa o conjunto" (#3, sem DDL):** a persona oferece UMA vez a peça-par/
  complementar do catálogo, tom discreto, sem insistir.
