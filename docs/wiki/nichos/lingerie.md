# Lingerie — regras de negócio (lingerie, camada 8.21)

[← Catálogo](../05-nichos.md) · Chassi: C — varejo com grade de variantes (**avô** do chassi) · Guia operacional: docs/PERFIL_LINGERIE.md · Migrations: 65, 101

## O negócio em 3 linhas

Loja de moda íntima que vende pelo WhatsApp. A cliente conversa com a IA, escolhe a **variante exata**
(tamanho × cor — o SKU real, com preço e estoque próprios), e confirma o pedido; a loja acompanha num
Kanban com **gate de aceite humano**. A IA tem tom acolhedor e discreto, sem apelo sensual nem
comentário sobre o corpo (persona `ProfilePromptContext.LINGERIE`).

## Jornada no WhatsApp (cenários)

1. **Catálogo:** a IA responde com o catálogo injetado pelo `LingerieMenuCache` — produtos por categoria,
   cada variante com `variant_id`, `[tamanho/cor]`, preço (da variante ou herdado do base) e estoque
   (`N em estoque` / `esgotado`).
2. **Carrinho na conversa:** não existe entidade de carrinho — a IA relê o histórico. Nenhuma tag é
   emitida enquanto a cliente monta o pedido.
3. **Confirmação:** com "pode mandar"/"fechou" + forma de recebimento (e endereço, se entrega), a última
   mensagem da IA TERMINA com `<pedido_lingerie>{...}`. O `OutboundService.maybeProcessPedidoLingerie`
   (só age se `profile_id='lingerie'`) chama o `PedidoLingerieConfirmHandler`, que cria o pedido e
   **remove a tag** antes do envio.
4. **Gate humano:** o pedido nasce `aguardando` (silencioso). A loja aceita (`separando`) ou recusa
   (`recusado` + motivo) no painel — cada transição notifica a cliente com texto fixo.
5. **Exceção — esgotado:** variante sem estoque suficiente → `OutOfStockException` → rollback total
   (nenhum pedido parcial); a mensagem da IA segue normal, **sem pedido criado**. A IA é instruída a
   nunca oferecer variante marcada `esgotado` e, se a cliente quiser uma, oferecer o **avise-me**
   (`<aviso_estoque_lingerie>`).
6. **Exceção — entrega sem endereço / tag malformada:** o handler descarta a tag sem criar pedido
   (log warn); best-effort, a conversa não quebra.
7. **Cupom:** a cliente informa o código; a IA só repassa no campo `cupom` — quem valida e calcula é o
   backend. Cupom inválido NÃO aborta: o pedido sai sem o desconto.

## Regras de negócio

### Transacionais (invariantes duras)

- **R1 — Estoque por UPDATE condicional:** cada linha decrementa via
  `update lingerie_variants set stock_qty = stock_qty - ? where id = ? and company_id = ? and stock_qty >= ?`
  (`LingerieVariantRepository.decrementStock`). 0 linhas afetadas → `OutOfStockException` → rollback do
  `@Transactional`: o pedido inteiro ABORTA. O `WHERE` fecha a corrida da última unidade (duas compras
  concorrentes → só uma vence).
- **R2 — Total recalculado do catálogo:** `unit_price = variant.price_cents ?? product.base_price_cents`;
  `subtotal = Σ unit_price × qtd`; `total = subtotal − desconto + (entrega ? taxa : 0)` — tudo
  MATERIALIZADO em Java no INSERT. O `total_cents` da tag é DESCARTADO.
- **R3 — Variante = SKU:** `UNIQUE(product_id, size, color)` (índice `uniq_lingerie_variant_combo`);
  `size` validado contra o enum `LingerieSize` (PP/P/M/G/GG/XGG, parity com `lingerie-size.ts`), `color`
  texto livre. Duplicata → 409 `duplicate_variant`; tamanho fora do enum → 400 `invalid_size`.
- **R4 — Snapshot por item:** `product_name/size/color/unit_price_cents` congelados em
  `lingerie_order_items`; mudar o catálogo depois NÃO altera pedidos passados. `variant_id` é
  `on delete restrict` → excluir variante/produto com pedido → 409 `variant_in_use`/`product_in_use`.
- **R5 — Gate de aceite humano:** pedido nasce `aguardando`; só o painel transiciona
  (`PATCH /api/lingerie/orders/{id}/status`). A IA NUNCA aceita/recusa (não há endpoint POST de pedido —
  a criação é exclusiva do handler da tag).
- **R6 — Cupom na mesma transação:** válido = `active` + `valid_until ≥ hoje` (fuso America/Sao_Paulo) +
  `subtotal ≥ min_order_cents` + `uses < max_uses`; desconto `percent`/`fixed` com **clamp ao subtotal**;
  `uses` incrementa na transação do pedido. Código único por tenant (`UNIQUE(company_id, lower(code))`).
  Inválido NÃO aborta. `coupon_code_snapshot` congela o código; `coupon_id on delete set null`.
- **R7 — Estoque nunca negativo:** CHECK `stock_qty >= 0` no banco, defesa em profundidade sob R1.
- **R8 — Linhas inválidas são filtradas:** variante/produto inexistente, de outro tenant ou indisponível
  é IGNORADA (defesa); se nenhuma linha sobrar → `IllegalArgumentException` → sem pedido.
- **R9 — Avise-me sem duplicata:** 1 alerta PENDENTE por contato+variante (UNIQUE parcial
  `where notified_at is null` em `lingerie_stock_alerts`; inserção `on conflict do nothing`).

### Máquina de status

```
aguardando ──aceite──▶ separando ──▶ enviado ──▶ entregue (terminal)
     │                     │             │
     └──recusa──▶ recusado └─▶ cancelado ◀┘   (recusado/cancelado terminais)
```

| Transição | Quem pode | Notifica a cliente? |
|---|---|---|
| (criação) → `aguardando` | IA (tag; único write da IA) | NÃO (a IA já confirmou na mensagem) |
| `aguardando` → `separando` | humano no painel | SIM ("aceito, já estamos separando") |
| `aguardando` → `recusado` | humano (com `rejection_reason`) | SIM (texto defensivo + " Motivo: …") |
| `separando`/`enviado` → `cancelado` | humano | SIM ("cancelado; se quiser refazer…") |
| `separando` → `enviado` | humano | SIM (entrega: "foi enviado" × retirada: "pronto para retirada") |
| `enviado` → `entregue` | humano | SIM ("entregue, obrigada") |

`aguardando` → `cancelado` direto NÃO existe (diferente de suplementos). Transição fora do grafo →
409 `invalid_status_transition`; status desconhecido → 400 `invalid_status`. Cancelar **NÃO devolve
estoque** (a devolução é a adaptação do moda_infantil/suplementos — aqui não existe).

### O que a IA PODE × NUNCA faz (travas da persona)

- **PODE:** apresentar o catálogo; montar o pedido pela variante exata (`variant_id`); confirmar total +
  entrega/retirada; repassar código de cupom; oferecer UMA única vez a peça-par/complementar
  (cross-sell, tom discreto); oferecer o avise-me para variante esgotada; orientar medidas com cuidado.
- **NUNCA:** oferece variante esgotada; inventa produto/tamanho/cor/preço/desconto; aceita ou recusa o
  pedido; promete data de reposição; faz apelo sensual ou comenta o corpo da cliente; constrange ao
  falar de medidas.

### Tags de IA

| Tag | Quando a IA emite | Campos | O backend descarta/recalcula |
|---|---|---|---|
| `<pedido_lingerie>` | confirmação final do pedido | `items[{variant_id,qtd}]`, `fulfillment` (`entrega`/`retirada`), `endereco`, `cupom`, `total_cents` | `total_cents` DESCARTADO (recalcula); `fulfillment` inválido vira `entrega`; desconto calculado pelo backend |
| `<aviso_estoque_lingerie>` | cliente aceita ser avisada de variante esgotada | `variant_id` | valida a variante contra o tenant; duplicata pendente é no-op |

### Validações e erros

| reason | HTTP | Significado de negócio | Cenário |
|---|---|---|---|
| `forbidden_wrong_profile` | 403 | tenant de outro perfil em `/api/lingerie/**` | guard `LingerieProfileGuard` |
| `invalid_status` / `invalid_status_transition` | 400 / 409 | alvo desconhecido / fora do grafo | PATCH de status no Kanban |
| `order_not_found` / `product_not_found` / `variant_not_found` / `coupon_not_found` | 404 | recurso de outro tenant ou inexistente | GET/PATCH/DELETE |
| `invalid_category` / `invalid_size` | 400 | categoria/tamanho fora do enum | CRUD de produto/variante |
| `duplicate_variant` / `duplicate_coupon` | 409 | combinação size×color / código já existe | POST/PATCH |
| `product_in_use` / `variant_in_use` | 409 | referenciado por item de pedido (FK restrict) | DELETE |
| `invalid_coupon` | 400 | kind/value malformado | CRUD de cupom |
| `out_of_stock` | 409 (nominal) | estoque insuficiente | **não vira HTTP na prática**: só a IA cria pedido e o handler aborta em silêncio |

### Notificações ao cliente

- **Envia** em toda transição pós-gate (`separando`, `enviado`, `entregue`, `recusado`, `cancelado`) —
  texto FIXO e defensivo do enum `LingerieOrderStatus.notificationText(fulfillment)`; a recusa concatena
  o motivo. Avise-me dispara "VOLTOU ao estoque" na reposição 0→N do painel.
- **Silêncio** em `aguardando` (a IA já confirmou o recebimento na própria mensagem) e em falha de envio:
  `LingerieOrderNotifier` é best-effort — nunca reverte a transição já persistida; alerta marca
  `notified_at` mesmo sem canal (não revarre eternamente).

## Dados e snapshots

- `lingerie_config` (1:1): `delivery_fee_cents`/`min_order_cents` ≥ 0; ausente → ZERO.
- `lingerie_products`: `category` CHECK nas 7 categorias (`sutias…acessorios`, sync `LingerieCategory`);
  `base_price_cents ≥ 0`; `available`.
- `lingerie_variants`: `UNIQUE(product_id,size,color)`; `price_cents` nullable (herda o base);
  `stock_qty ≥ 0`; `company_id` denormalizado p/ RLS direta.
- `lingerie_orders`: CHECK de status (6) e fulfillment; totais materializados; `rejection_reason`;
  `discount_cents ≥ 0`, `coupon_id` (set null), `coupon_code_snapshot` (mig 101). INSERT só backend
  (tenant tem só SELECT/UPDATE via RLS).
- `lingerie_order_items`: snapshots + `qtd > 0`; `variant_id on delete restrict`.
- `lingerie_coupons` (mig 101): `kind in (percent,fixed)`, `value ≥ 0`, `UNIQUE(company, lower(code))`.
- `lingerie_stock_alerts` (mig 101): UNIQUE parcial pendente por contato+variante.
- **Cache:** `LingerieMenuCache` — Caffeine TTL **60s** por company (ignora conversationId), invalidado
  EXPLICITAMENTE em toda mutação de produto/variante/config (`LingerieProductService`,
  `LingerieConfigService`).

## Features de onda (backlog implementado — mig 101)

- **Cupom (motor `com.meada.common.coupons`):** regra em R6; caso não-abortivo por design (venda vale
  mais que o desconto). Tela Cupons no painel.
- **Avise-me quando voltar:** a IA registra o interesse (R9); a **reposição 0→N no painel**
  (`LingerieProductService.updateVariant` compara estoque anterior) dispara "voltou!" pra fila pendente
  e marca `notified_at` — idempotente. Demanda reprimida fica visível como insight de reposição.
- **Cross-sell "completa o conjunto" (sem DDL):** instrução no `LingerieMenuCache` — oferta ÚNICA da
  peça-par/complementar ao fechar, sem insistir.

## O que NÃO existe (limites honestos)

- Foto de produto (bloqueador SERVICE_ROLE_KEY); pagamento real (Stripe #50); **devolução de estoque ao
  cancelar** (restock é do moda_infantil/suplementos); reserva de estoque sem pedido; troca/devolução;
  frete por CEP (taxa flat); variante com 3+ eixos; kit/combo; lista de desejos.
- **POST manual de pedido:** não há — pedidos nascem SÓ da tag da IA. Por isso `out_of_stock` nunca
  aparece como resposta HTTP: o abort é silencioso no handler.
- **Pedido mínimo NÃO é validado no backend:** `min_order_cents` só entra no prompt ("avise, mas não
  recuse — apenas oriente"). Não há erro transacional de mínimo.
