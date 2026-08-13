# Moda Infantil — regras de negócio (moda_infantil, camada 8.22)

[← Catálogo](../05-nichos.md) · Chassi: C — varejo com grade de variantes (clone da lingerie) · Guia operacional: docs/PERFIL_MODA_INFANTIL.md · Migrations: 66, 100

## O negócio em 3 linhas

Loja de roupa de criança que vende pelo WhatsApp. O cliente informa a idade, a IA **sugere a faixa
de tamanho** (`KidsSize.suggestForAgeMonths`), monta o pedido pela **variante exata** (faixa etária ×
cor — o SKU real, com preço e estoque próprios) e a loja acompanha num Kanban com gate de aceite. A
adaptação do nicho: **cancelar/recusar DEVOLVE o estoque** (restock idempotente), porque troca e
desistência são frequentes no varejo infantil.

## Jornada no WhatsApp (cenários)

1. **Catálogo:** a IA responde com o bloco do `ModaInfantilMenuCache` — produtos por categoria, cada
   variante com `variant_id`, `[faixa/cor]`, preço (da variante ou herdado do base) e estoque.
2. **Sugestão idade→tamanho:** "ela tem 6 meses" → a IA sugere `6-9m` (primeira banda cujo teto em
   meses supera a idade), mas CONFIRMA com o cliente — a escolha é dele.
3. **Confirmação:** com fechamento + entrega (com endereço) ou retirada, a última mensagem TERMINA
   com `<pedido_moda_infantil>{...}`. O `OutboundService.maybeProcessPedidoModaInfantil` chama o
   `PedidoModaInfantilConfirmHandler`, que cria o pedido `aguardando` e **remove a tag**.
4. **Gate humano:** a loja aceita (`separando`) ou recusa (`recusado` + motivo) no painel — cada
   transição pós-gate notifica com texto fixo.
5. **Exceção — esgotado:** variante sem estoque → `OutOfStockException` → rollback total (nenhum
   pedido parcial), mensagem segue sem pedido. A IA então oferece o **avise-me**: se o cliente
   aceitar, emite `<aviso_estoque_moda>{variant_id}`.
6. **Reposição:** quando o tenant repõe estoque no painel (0 → N), o backend dispara "VOLTOU ao
   estoque" pra fila pendente e marca `notified_at` (idempotente).
7. **Cancelamento:** mover pra `recusado`/`cancelado` devolve o estoque das variantes NA MESMA
   transação e marca `stock_returned` — duplo-cancelamento não devolve 2×.

## Regras de negócio

### Transacionais (invariantes duras)

- **R1 — Estoque por UPDATE condicional:** cada linha decrementa com `stock_qty >= qtd` no WHERE
  (`ModaInfantilVariantRepository.decrementStock`); 0 linhas → `OutOfStockException` → rollback do
  `@Transactional`: o pedido inteiro ABORTA. Fecha a corrida da última unidade.
- **R2 — Restock idempotente ao cancelar (⭐ a adaptação):** ao entrar em `recusado`/`cancelado`
  (`ModaInfantilOrderStatus.restocksOnEnter`), o repositório devolve `stock_qty + qtd` por item **na
  mesma transação** do status e marca `stock_returned = true`; só devolve se `stock_returned` ainda
  era false — cancelar-depois-de-recusar não devolve duas vezes.
- **R3 — Total recalculado do catálogo:** `unit_price = variant.price_cents ?? product.base_price_cents`;
  `total = subtotal − desconto + (entrega ? taxa : 0)` — materializado em Java. `total_cents` da tag
  é DESCARTADO. Retirada zera a taxa e anula o endereço.
- **R4 — Variante = SKU:** `UNIQUE(product_id, size, color)`; `size` validado contra o enum
  `KidsSize` (13 faixas: RN, 0-3m…9-12m, 1a…12a; parity `kids-size.ts`) → 400 `invalid_size`;
  duplicata → 409 `duplicate_variant`; `color` texto livre.
- **R5 — Snapshot por item:** `product_name/size/color/unit_price_cents` congelados em
  `moda_infantil_order_items` (o restock usa `variant_id + qtd` desses itens); `variant_id`
  `on delete restrict` → 409 `variant_in_use` / `product_in_use`.
- **R6 — Gate de aceite humano:** pedido nasce `aguardando`; só o painel transiciona (PATCH). A IA
  NUNCA aceita/recusa; não há POST manual de pedido (INSERT só service_role).
- **R7 — Cupom na mesma transação (mig 100):** válido = `active` + `valid_until ≥ hoje`
  (America/Sao_Paulo) + `subtotal ≥ min_order_cents` + `uses < max_uses`; desconto com **clamp ao
  subtotal**; `uses` incrementa na transação; `UNIQUE(company, lower(code))`. Inválido NÃO aborta.
- **R8 — Avise-me sem duplicata:** 1 alerta PENDENTE por contato+variante (UNIQUE parcial
  `where notified_at is null`; INSERT `on conflict do nothing`).
- **R9 — Linhas inválidas são filtradas** (variante/produto inexistente, de outro tenant ou
  indisponível); nenhuma linha válida → `IllegalArgumentException` → sem pedido.

### Máquina de status

```
aguardando ──aceite──▶ separando ──▶ enviado ──▶ entregue (terminal)
     │                     │             │
     └──recusa──▶ recusado └─▶ cancelado ◀┘   (recusado/cancelado terminais; ambos DEVOLVEM estoque)
```

| Transição | Quem pode | Notifica o cliente? |
|---|---|---|
| (criação) → `aguardando` | IA (tag; único write da IA) | NÃO (a IA já confirmou na mensagem) |
| `aguardando` → `separando` | humano no painel | SIM ("aceito, já estamos separando! 🧸") |
| `aguardando` → `recusado` | humano (com `rejection_reason`) + restock | SIM (texto defensivo + " Motivo: …") |
| `separando` → `enviado` | humano | SIM (entrega: "foi enviado" × retirada: "pronto para retirada") |
| `enviado` → `entregue` | humano | SIM ("entregue, obrigada") |
| `separando`/`enviado` → `cancelado` | humano + restock | SIM ("cancelado; se quiser refazer…") |

`aguardando` → `cancelado` direto NÃO existe. Fora do grafo → 409 `invalid_status_transition`;
alvo desconhecido → 400 `invalid_status`.

### O que a IA PODE × NUNCA faz (travas da persona)

- **PODE:** apresentar o catálogo; sugerir a faixa pela idade (confirmando com o cliente); montar o
  pedido pela variante exata; confirmar total + entrega/retirada; repassar cupom; oferecer o
  avise-me para variante esgotada.
- **NUNCA:** oferece variante esgotada (estoque 0); inventa produto/tamanho/cor/preço/desconto;
  aceita ou recusa o pedido; promete data de reposição. Persona `ProfilePromptContext.MODA_INFANTIL`
  (tom acolhedor, gentil e prático).

### Tags de IA

| Tag | Quando a IA emite | Campos | O backend descarta/recalcula |
|---|---|---|---|
| `<pedido_moda_infantil>` | confirmação final do pedido | `items[{variant_id,qtd}]`, `fulfillment` (`entrega`/`retirada`), `endereco`, `cupom`, `total_cents` | `total_cents` DESCARTADO; `fulfillment` inválido vira `entrega` (conservador); desconto calculado pelo backend |
| `<aviso_estoque_moda>` | cliente aceita ser avisado de variante esgotada | `variant_id` | valida a variante contra o tenant; duplicata pendente é no-op |

Parse por regex, removidas antes do envio; falha → `Optional.empty()`/warn, mensagem segue.

### Validações e erros

| reason | HTTP | Significado de negócio | Cenário |
|---|---|---|---|
| `forbidden_wrong_profile` | 403 | tenant de outro perfil em `/api/moda-infantil/**` | guard `ModaInfantilProfileGuard` |
| `invalid_status` / `invalid_status_transition` | 400 / 409 | alvo desconhecido / fora do grafo | PATCH no Kanban |
| `order_not_found` / `product_not_found` / `variant_not_found` / `coupon_not_found` | 404 | recurso inexistente/de outro tenant | GET/PATCH/DELETE |
| `invalid_category` / `invalid_size` | 400 | categoria fora das 7 / faixa fora do `KidsSize` | CRUD de produto/variante |
| `duplicate_variant` / `duplicate_coupon` | 409 | combinação size×color / código já existe | POST/PATCH |
| `product_in_use` / `variant_in_use` | 409 | referenciado por item de pedido (FK restrict) | DELETE |
| `invalid_coupon` | 400 | kind/value malformado | CRUD de cupom |
| `out_of_stock` | 409 (nominal) | estoque insuficiente | **não vira HTTP**: só a IA cria pedido; abort silencioso |

### Notificações ao cliente

- **Envia** em toda transição pós-gate (texto FIXO de `ModaInfantilOrderStatus.notificationText(fulfillment)`;
  recusa concatena o motivo) e no "voltou ao estoque" da reposição 0→N.
- **Silêncio** em `aguardando` e no **restock por cancelamento** (a devolução de estoque NÃO
  notifica a fila do avise-me nesta onda — só a reposição manual no painel notifica).
- Best-effort (`ModaInfantilOrderNotifier`): falha de envio nunca reverte o status; o alerta marca
  `notified_at` mesmo sem canal (não revarre eternamente).

## Dados e snapshots

- `moda_infantil_config` (1:1): `delivery_fee_cents`/`min_order_cents` ≥ 0; ausente → ZERO.
- `moda_infantil_products`: `name` 1–200; CHECK de categoria
  (`bebe/menino/menina/calcados/acessorios/pijamas/kits`, sync `ModaInfantilCategory`); `available`.
- `moda_infantil_variants`: `UNIQUE(product_id,size,color)`; `size` 1–20 (sync `KidsSize`);
  `price_cents` nullable (herda o base); `stock_qty ≥ 0` (CHECK, defesa sob R1).
- `moda_infantil_orders`: CHECK de status (6) e fulfillment; totais materializados;
  `rejection_reason`; **`stock_returned`** (marcador do restock); mig 100 soma `discount_cents ≥ 0`,
  `coupon_id` (set null), `coupon_code_snapshot`. INSERT só backend.
- `moda_infantil_order_items`: snapshots + `qtd > 0`; `variant_id on delete restrict`.
- `moda_infantil_coupons` / `moda_infantil_stock_alerts` (mig 100): RLS ligada SEM policy de tenant
  (só service_role) — gerência exclusivamente via Spring REST; alerta com UNIQUE parcial pendente.
- **Cache:** `ModaInfantilMenuCache` — Caffeine TTL **60s** por company (ignora conversationId),
  invalidado EXPLICITAMENTE em toda mutação de produto/variante/config (`ModaInfantilProductService`).

## Features de onda (backlog implementado — mig 100)

- **Cupom (motor `com.meada.common.coupons`, #1):** regra R7; a IA só repassa o código; tela Cupons.
- **Avise-me quando voltar (#3):** a IA registra o interesse (R8); a reposição 0→N no painel
  (`ModaInfantilProductService` → `ModaInfantilStockAlertService.notifyBackInStock`) dispara
  "voltou!" pra fila pendente. Demanda reprimida visível é insight de reposição. O restock por
  cancelamento NÃO dispara o aviso (refinamento futuro registrado no guia).

## O que NÃO existe (limites honestos)

- Foto de produto (SERVICE_ROLE_KEY); pagamento real (Stripe #50); troca com fluxo próprio (o
  caminho é cancelar — que devolve o estoque — e fazer novo pedido); frete por CEP (taxa flat);
  variante com 3+ eixos (só faixa×cor); kit/combo; lista de desejos.
- **POST manual de pedido:** não há — `out_of_stock` nunca aparece como HTTP.
- **Pedido mínimo NÃO é validado no backend:** `min_order_cents` só instrui a IA ("avise, mas não
  recuse — apenas oriente").
