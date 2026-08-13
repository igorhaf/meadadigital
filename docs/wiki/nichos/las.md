# Lãs — regras de negócio (las, camada 8.23)

[← Catálogo](../05-nichos.md) · Chassi: C — varejo com grade de variantes (clone da lingerie, eixo trocado) · Guia operacional: docs/PERFIL_LAS.md · Migrations: 67, 104

## O negócio em 3 linhas

Loja de lãs e novelos (tricô/crochê) que vende pelo WhatsApp. A escapada do nicho: novelos da mesma
cor mas de **lotes de tingimento diferentes** têm variação visível de tom, então a variante é
**cor × dye_lot** — cada lote é um SKU com SEU estoque. Quem tricota projeto grande exige tudo do
MESMO lote (`same_lot_guaranteed`); a IA monta o pedido pela variante exata e a loja acompanha num
Kanban com gate de aceite humano.

## Jornada no WhatsApp (cenários)

1. **Catálogo:** a IA responde com o bloco do `LasMenuCache` — produtos por categoria, cada variante
   com `variant_id`, `[cor/lote]`, preço e estoque; explica que mesmo lote = mesmo tom.
2. **Calculadora de novelos:** "quantos novelos pra um cachecol?" → a IA usa as referências de
   `las_yield_reference` SEMPRE como ESTIMATIVA ("em média X novelos"); peça não cadastrada → diz
   que não tem a estimativa e sugere confirmar com a loja — nunca dimensiona por conta.
3. **Confirmação:** a última mensagem TERMINA com `<pedido_las>{...}` (com `same_lot_guaranteed`
   quando o cliente exigiu). O `OutboundService.maybeProcessPedidoLas` chama o
   `PedidoLasConfirmHandler`, que cria o pedido `aguardando` e **remove a tag**.
4. **Gate humano:** a loja aceita (`separando`) ou recusa (`recusado` + motivo) no painel; o card
   mostra o selo "mesmo lote garantido".
5. **Exceção — lotes misturados:** com `same_lot_guaranteed=true`, se alguma COR tem itens de 2+
   lotes → `MixedDyeLotsException` (com as cores ofensoras) AINDA na transação → rollback (o estoque
   decrementado volta), pedido não criado, abort silencioso — a IA reorganiza a oferta.
6. **Exceção — esgotado:** `OutOfStockException` → rollback total; a IA oferece a **lista de
   espera**: `<lista_espera_las>{variant_id, any_lot, qty}` — `any_lot:true` = qualquer lote da cor.
7. **Reposição 0→N no painel:** notifica a fila da variante EXATA e a fila `any_lot` da mesma cor.
8. **Reativação (opt-in OFF):** com `reactivation_enabled`, o `LasReactivationJob` (cron 10h40)
   reaborda quem já comprou e sumiu ("chegaram lotes novos", cupom de retorno opcional).

## Regras de negócio

### Transacionais (invariantes duras)

- **R1 — Estoque por UPDATE condicional:** decremento com `stock_qty >= qtd` no WHERE
  (`LasVariantRepository.decrementStock`); 0 linhas → `OutOfStockException` → rollback: o pedido
  inteiro ABORTA (nenhum pedido parcial).
- **R2 — Mesmo lote garantido (⭐ a escapada):** com `same_lot_guaranteed=true`, o repositório
  agrupa os itens por `color_snapshot` e exige um ÚNICO `dye_lot_snapshot` por cor; 2+ lotes numa
  cor → `MixedDyeLotsException` dentro da MESMA transação → rollback devolve o estoque. Nominal:
  422 `mixed_dye_lots`; na prática não vira HTTP (pedido só nasce da IA).
- **R3 — Total recalculado:** `unit_price = variant.price_cents ?? product.base_price_cents`;
  `total = subtotal − desconto + (entrega ? taxa : 0)`, materializado em Java; `total_cents` da tag
  DESCARTADO. Retirada zera taxa e endereço.
- **R4 — Variante = SKU por lote:** `UNIQUE(product_id, color, dye_lot)` — cada (cor, lote) tem
  estoque próprio; `color`/`dye_lot` texto livre 1–40 (sem enum de tamanho). Duplicata → 409
  `duplicate_variant`.
- **R5 — Snapshot por item:** `product_name/color/dye_lot/unit_price_cents` congelados em
  `las_order_items` (a validação R2 usa os snapshots); `variant_id on delete restrict` → 409
  `variant_in_use` / `product_in_use`.
- **R6 — Gate de aceite humano:** pedido nasce `aguardando`; só o painel transiciona (PATCH). Sem
  POST manual de pedido. Cancelar **NÃO devolve estoque** (o restock é do moda_infantil/suplementos).
- **R7 — Cupom na mesma transação (mig 104):** válido = `active` + `valid_until ≥ hoje` +
  `uses < max_uses` + `subtotal ≥ min_order_cents`; clamp ao subtotal; `uses` incrementa;
  `UNIQUE(company, lower(code))`. Inválido NÃO aborta.
- **R8 — Lista de espera sem duplicata:** UNIQUE parcial pendente por
  `(contact, product, color, coalesce(dye_lot,''))` — o `''` faz o interesse "qualquer lote" também
  ser único; INSERT `on conflict do nothing`.
- **R9 — Reativação com cooldown:** só tenants com `reactivation_enabled` (default OFF — lição
  Baileys); só contatos com pedido `entregue` anterior à janela `reactivation_days` (7–365, default
  45); cooldown = a própria janela via `las_reactivation_log`; cupom de retorno só entra se
  ativo/válido/com usos.

### Máquina de status

```
aguardando ──aceite──▶ separando ──▶ enviado ──▶ entregue (terminal)
     │                     │             │
     └──recusa──▶ recusado └─▶ cancelado ◀┘   (recusado/cancelado terminais; SEM restock)
```

| Transição | Quem pode | Notifica o cliente? |
|---|---|---|
| (criação) → `aguardando` | IA (tag; único write da IA) | NÃO (a IA já confirmou na mensagem) |
| `aguardando` → `separando` | humano no painel | SIM ("aceito, já estamos separando! 🧶") |
| `aguardando` → `recusado` | humano (com `rejection_reason`) | SIM (texto defensivo + " Motivo: …") |
| `separando` → `enviado` | humano | SIM (entrega: "foi enviado" × retirada: "pronto para retirada") |
| `enviado` → `entregue` | humano | SIM ("entregue. Bom trabalho!") |
| `separando`/`enviado` → `cancelado` | humano | SIM ("cancelado; se quiser refazer…") |

Fora do grafo → 409 `invalid_status_transition`; alvo desconhecido → 400 `invalid_status`.

### O que a IA PODE × NUNCA faz (travas da persona)

- **PODE:** apresentar o catálogo; explicar que mesmo lote = mesmo tom; registrar
  `same_lot_guaranteed` quando o cliente EXIGIR (default false, só true explícito); usar a
  calculadora como estimativa; oferecer a lista de espera (lote exato ou qualquer lote); repassar
  cupom; confirmar total + entrega/retirada.
- **NUNCA:** oferece variante esgotada; inventa produto/cor/lote/preço/desconto; aceita ou recusa o
  pedido; **promete data de reposição**; dimensiona projeto sem referência cadastrada. Persona
  `ProfilePromptContext.LAS` (tom de quem entende de trabalho manual).

### Tags de IA

| Tag | Quando a IA emite | Campos | O backend descarta/recalcula |
|---|---|---|---|
| `<pedido_las>` | confirmação final do pedido | `items[{variant_id,qtd}]`, `fulfillment`, `same_lot_guaranteed`, `endereco`, `cupom`, `total_cents` | `total_cents` DESCARTADO; `fulfillment` inválido vira `entrega`; `same_lot_guaranteed` ausente vira false; regra do lote revalidada (R2) |
| `<lista_espera_las>` | cliente aceita ser avisado de variante esgotada | `variant_id`, `any_lot`, `qty` | resolve produto/cor/lote a partir da variante; `any_lot:true` grava `dye_lot` NULL; `qty` é só informativo (entra na mensagem) |

Parse por regex, removidas antes do envio; falha → warn e a mensagem segue.

### Validações e erros

| reason | HTTP | Significado de negócio | Cenário |
|---|---|---|---|
| `forbidden_wrong_profile` | 403 | tenant de outro perfil em `/api/las/**` | guard `LasProfileGuard` |
| `invalid_status` / `invalid_status_transition` | 400 / 409 | alvo desconhecido / fora do grafo | PATCH no Kanban |
| `order_not_found` / `product_not_found` / `variant_not_found` / `coupon_not_found` / `yield_not_found` | 404 | recurso inexistente/de outro tenant | GET/PATCH/DELETE |
| `invalid_category` | 400 | fora das 6 categorias (`las…pelucia`) | CRUD de produto |
| `invalid_skeins` | 400 | rendimento fora de 1–200 novelos | CRUD de referência de rendimento |
| `duplicate_variant` / `duplicate_coupon` | 409 | (cor,lote) / código já existe | POST/PATCH |
| `product_in_use` / `variant_in_use` | 409 | referenciado por pedido (FK restrict) | DELETE |
| `invalid_coupon` | 400 | kind/value malformado | CRUD de cupom |
| `out_of_stock` / `mixed_dye_lots` | 409 / 422 (nominais) | esgotado / cores com 2+ lotes | **não viram HTTP**: abort silencioso no handler |

### Notificações ao cliente

- **Envia** em toda transição pós-gate (texto FIXO de `LasOrderStatus.notificationText(fulfillment)`;
  recusa concatena o motivo); no "chegou!" da reposição 0→N (citando lote quando o interesse era
  exato, e a qty desejada quando informada); e na reativação opt-in (1 mensagem, com cupom se houver).
- **Silêncio** em `aguardando`; a lista de espera marca `notified_at` mesmo sem canal (não revarre);
  reativação sem conversa marca `had_channel=false` sem envio.
- Best-effort (`LasOrderNotifier`): falha de envio nunca reverte status/registro persistido.

## Dados e snapshots

- `las_config` (1:1): taxa/mínimo ≥ 0; mig 104 soma `reactivation_enabled` (default FALSE),
  `reactivation_days` 7–365 (default 45) e `reactivation_coupon_code`.
- `las_products`: `name` 1–200; CHECK de categoria (`las/linhas/kits/agulhas/acessorios/pelucia`,
  sync `LasCategory`); `available`.
- `las_variants`: `UNIQUE(product_id, color, dye_lot)`; `price_cents` nullable (herda o base);
  `stock_qty ≥ 0` (CHECK, defesa sob R1).
- `las_orders`: CHECK de status (6) e fulfillment; **`same_lot_guaranteed`** (default false); totais
  materializados; `rejection_reason`; mig 104 soma `discount_cents ≥ 0`, `coupon_id` (set null),
  `coupon_code_snapshot`. INSERT só backend.
- `las_order_items`: snapshots (inclui `dye_lot_snapshot`) + `qtd > 0`; `variant_id` restrict.
- `las_coupons` / `las_waitlist` / `las_yield_reference` / `las_reactivation_log` (mig 104): RLS
  ligada SEM policy de tenant (só service_role) — gerência via Spring REST (`/api/las/**`).
- **Cache:** `LasMenuCache` — Caffeine TTL **60s** por company, invalidado EXPLICITAMENTE em toda
  mutação de produto/variante/config (`LasProductService`, `LasConfigService`).

## Features de onda (backlog implementado — mig 104)

- **Lista de espera de dye lot (#1):** dye-lot-aware (lote exato × `any_lot`); o hook no
  `LasProductService.updateVariant` (0→N) chama `LasWaitlistService.notifyBackInStock`, que casa a
  variante reposta com a fila exata E a fila `dye_lot` NULL da mesma cor.
- **Calculadora de novelos (#2):** `las_yield_reference` (peça × fio → novelos 1–200), editada pelo
  tenant na tela "Rendimento"; o bloco no cache manda usar SEMPRE como estimativa e fechar a
  quantidade completa no MESMO lote.
- **Cupom (#5, motor `com.meada.common.coupons`):** regra R7; caso típico: queima de lote antigo.
- **Reativação de inativo (#7):** regra R9; `LasReactivationJob`, cron
  `${las.reactivation-cron:0 40 10 * * *}`; opt-in OFF por default.

## O que NÃO existe (limites honestos)

- **Restock ao cancelar** (o estoque NÃO volta — adaptação exclusiva do moda_infantil/suplementos);
  reserva de lote sem pedido; kit de projeto multi-débito (#3); sinal/reserva paga (#4, bloqueado
  por #50); fidelidade por pontos (#6); biblioteca de receitas (#9, chassi F); clube do novelo
  (#12, chassi E).
- Foto de produto (SERVICE_ROLE_KEY); pagamento real; frete por CEP; variante com 3+ eixos.
- **POST manual de pedido:** não há — `out_of_stock` e `mixed_dye_lots` nunca aparecem como HTTP.
- **Pedido mínimo NÃO é validado no backend** (só instrução no prompt); a calculadora NÃO reserva
  nem debita estoque — é referência de conversa.
