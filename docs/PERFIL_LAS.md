# Perfil Lãs (loja de lãs · novelos · tricô/crochê · varejo) — camada 8.23

Guia operacional do tenant **las** (`profile_id='las'`). É uma CLONE do perfil Lingerie (8.21, chassi de
varejo com variantes), com o eixo de variante sendo **cor × lote de tingimento (dye lot)** e a regra
"mesmo lote preferencial".

É o **29º perfil vertical** (28 + generic).

## Telas (sidebar "Lãs")

| Tela | Rota | O que faz |
|------|------|-----------|
| Catálogo | `/dashboard/las-catalog` | CRUD de produtos + grade de variantes (cor × dye_lot, preço, estoque). |
| Pedidos | `/dashboard/las-orders` | Kanban com gate de aceite + selo "mesmo lote garantido" + entrega/retirada. |
| Configurações | `/dashboard/las-settings` | Taxa de entrega + pedido mínimo. |

## ⭐ ESCAPADA — dye lot (lote de tingimento) + regra "mesmo lote preferencial"

Novelos de lã da **mesma cor** mas de **lotes de tingimento diferentes** têm variação visível de tom.
Quem tricota um projeto grande precisa de novelos do MESMO lote. Por isso a variante é
**(color, dye_lot)** — cada lote da mesma cor é um SKU próprio com SEU estoque
(`UNIQUE(product_id, color, dye_lot)`). O pedido referencia a VARIANTE (cor+lote).

**`same_lot_guaranteed`** (boolean no pedido): quando o cliente exige todos os novelos do MESMO lote, o
backend agrupa os itens por `color_snapshot` e valida que cada cor referencia um ÚNICO `dye_lot_snapshot`;
se alguma cor abranger 2+ lotes → **422 `mixed_dye_lots`** (com as cores ofensoras) e o pedido inteiro
ABORTA (rollback, sem pedido parcial). Quando `false`, não há essa checagem. Como o pedido só é criado
pela IA (sem POST manual), o handler trata o `mixed_dye_lots` como abort silencioso (tag descartada, sem
pedido) — a IA reorganiza a oferta.

color e dye_lot são **texto livre** (não há enum de tamanho — o eixo do Lingerie foi trocado).

## O que herda do Lingerie

- Decremento transacional de estoque (UPDATE condicional `stock_qty >= qtd` → 409 `out_of_stock`,
  aborta). Categorias hardcoded (`LasCategory`, parity): las/linhas/kits/agulhas/acessorios/pelucia.
  Status `LasOrderStatus` (parity): aguardando→separando→enviado→entregue + recusado/cancelado (gate de
  aceite humano; aguardando não notifica). Total materializado (descarta o da IA). fulfillment
  entrega(c/ endereço)/retirada. Cliente = contato (snapshots). Tag `<pedido_las>`.

## O que a IA faz / NÃO faz

- **FAZ:** apresenta o catálogo, explica que o mesmo lote = mesmo tom, monta o pedido pela variante
  (cor+lote), registra `same_lot_guaranteed` quando o cliente exige, confirma total + entrega/retirada.
- **NÃO FAZ:** oferecer variante esgotada; inventar produto/cor/lote/preço; aceitar/recusar pedido.

## O que NÃO existe nesta fase

- Foto de produto (bloqueador SERVICE_ROLE_KEY); pagamento real (Stripe #50); cupom/promoção; reserva de
  lote sem pedido; frete por CEP; cálculo de quantidade de novelos por projeto (a IA não dimensiona o
  trabalho); variante com 3+ eixos.

## Notas técnicas

- Migration `67_las.sql` (5 tabelas: config, products, variants, orders, order_items). A CHECK ACRESCENTA
  `'las'` preservando os 28 perfis. Entra por ÚLTIMO no `SCRIPTS` do `AbstractIntegrationTest` (sua CHECK
  tem os 29).
- Base de conhecimento (RAG): disponível como em todo perfil.
- Guard `/api/las/**` → 403 `forbidden_wrong_profile`. Paleta `ferrugem`. Tenant: `igorhaf34`.
