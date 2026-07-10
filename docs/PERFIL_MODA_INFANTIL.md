# Perfil Moda Infantil (roupa de criança · varejo) — camada 8.22

Guia operacional do tenant **moda_infantil** (`profile_id='moda_infantil'`). É uma CLONE quase exata do
perfil Lingerie (8.21, chassi de varejo com variantes), com o eixo de tamanho sendo **faixa etária** e
um acréscimo: **devolução de estoque ao cancelar**.

É o **28º perfil vertical** (27 + generic).

## Telas (sidebar "Moda Infantil")

| Tela | Rota | O que faz |
|------|------|-----------|
| Catálogo | `/dashboard/moda-infantil-catalog` | CRUD de produtos + grade de variantes (faixa etária × cor, preço, estoque). |
| Pedidos | `/dashboard/moda-infantil-orders` | Kanban com gate de aceite + entrega/retirada. |
| Configurações | `/dashboard/moda-infantil-settings` | Taxa de entrega + pedido mínimo. |

## O que herda do Lingerie (chassi de varejo)

- **Grade de variantes com estoque** (`moda_infantil_variants`: size × color, price_cents nullable→herda
  base, stock_qty). O pedido referencia a VARIANTE.
- **Decremento transacional de estoque** na criação do pedido (UPDATE condicional `stock_qty >= qtd`; 0
  linhas → 409 `out_of_stock`, aborta o pedido).
- Categorias hardcoded (`ModaInfantilCategory`, parity): bebe/menino/menina/calcados/acessorios/pijamas/
  kits. Status `ModaInfantilOrderStatus` (parity): aguardando→separando→enviado→entregue + recusado/
  cancelado (gate de aceite humano; aguardando não notifica). Total materializado no backend (descarta o
  da IA). fulfillment entrega(c/ endereço)/retirada. Cliente = contato (snapshots). Tag
  `<pedido_moda_infantil>`.

## ADAPTAÇÃO 1 — size = FAIXA ETÁRIA + sugestão idade→tamanho

O eixo `size` da variante é a **faixa etária** (`KidsSize` ↔ `kids-size.ts`, parity): RN, 0-3m, 3-6m,
6-9m, 9-12m, 1a, 2a, 3a, 4a, 6a, 8a, 10a, 12a. O enum Java tem `suggestForAgeMonths(int months)` — a IA
sugere o tamanho a partir da idade da criança (ex.: 6 meses → 6-9m), mas confirma com o cliente. color é
texto livre.

## ADAPTAÇÃO 2 — Devolução de estoque ao cancelar (restock)

Diferente do Lingerie, ao mover o pedido para `recusado` ou `cancelado` o backend **DEVOLVE o estoque**:
para cada item, `UPDATE moda_infantil_variants SET stock_qty = stock_qty + qtd`, e marca
`orders.stock_returned = true` — tudo na MESMA transação do status. É **idempotente**: só devolve se
`stock_returned` está false, então um duplo-cancelamento (ou cancelar depois de recusar) NÃO devolve duas
vezes. O varejo de roupa infantil tem troca/cancelamento frequente; o estoque volta pra prateleira.

## O que a IA faz / NÃO faz

- **FAZ:** apresenta o catálogo, sugere o tamanho pela idade, monta o pedido pela variante (faixa+cor),
  confirma total + entrega/retirada.
- **NÃO FAZ:** oferecer variante esgotada; inventar produto/tamanho/cor/preço; aceitar/recusar pedido.

## O que NÃO existe nesta fase

- Foto de produto (bloqueador SERVICE_ROLE_KEY); pagamento real (Stripe #50); cupom/promoção; troca com
  fluxo próprio (cancelar+novo pedido); frete por CEP; variante com 3+ eixos (só faixa×cor).

## Notas técnicas

- Migration `66_moda_infantil.sql` (5 tabelas: config, products, variants, orders, order_items). A CHECK
  ACRESCENTA `'moda_infantil'` preservando os 27 perfis. Entra por ÚLTIMO no `SCRIPTS` do
  `AbstractIntegrationTest` (sua CHECK tem os 28).
- **Lição de parity cravada:** `moda_infantil` é o primeiro id de perfil com **underscore**; o regex do
  `ProfileTypeParityTest` (`[a-z0-9-]+`) não casava underscore → ampliado para `[a-z0-9_-]+`.
- Base de conhecimento (RAG): disponível como em todo perfil.
- Guard `/api/moda-infantil/**` → 403 `forbidden_wrong_profile`. Paleta `por-do-sol`. Tenant: `igorhaf33`.

## Onda 1 do backlog (2026-07 — FEATURES_SUGERIDAS_MODA_INFANTIL #1/#3, migration 100)

- **Cupom (#1, motor unificado):** `moda_infantil_coupons` (subclasses finas de
  `com.meada.common.coupons`) + desconto materializado no pedido; a IA passa o código no campo
  `cupom` da tag `<pedido_moda_infantil>`; inválido NÃO aborta; `uses` na transação. Tela
  Cupons + nav.
- **Avise-me quando voltar (#3, `moda_infantil_stock_alerts`):** variante ESGOTADA → a IA
  oferece o aviso e emite `<aviso_estoque_moda>{variant_id}` (1 alerta pendente por
  contato+variante); a REPOSIÇÃO no painel (0→N) dispara "voltou!" pra fila e marca
  `notified_at`. Restock por cancelamento NÃO notifica nesta onda (refinamento futuro).
