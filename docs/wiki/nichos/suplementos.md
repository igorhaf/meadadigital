# Suplementos — regras de negócio (suplementos, camada 8.24)

[← Catálogo](../05-nichos.md) · Chassi: B + C — pedido order-based com gate (comida) + grade de variantes com estoque (lingerie) · Guia operacional: docs/PERFIL_SUPLEMENTOS.md · Migrations: 68, 89

## O negócio em 3 linhas

Loja de suplementos (whey, creatina, vitaminas, pré-treino, acessórios) com entrega. A IA é
**balconista, nunca conselheira de saúde**: mostra o catálogo, tira dúvida de PRODUTO (sabor, peso,
preço, estoque) e monta o pedido pela **variante exata** (sabor × peso — o SKU real, com preço e
estoque próprios). O coração do perfil é a **trava de não-prescrição**, inaugurada num vertical de
varejo.

## Jornada no WhatsApp (cenários)

1. **Catálogo:** a IA responde com o bloco do `SuplementosMenuCache` — produtos por categoria, cada
   variante com `variant_id`, sabor/peso, preço e estoque ("esgotado" não pode ser pedido).
2. **Pergunta de saúde (a trava):** "quanto tomo por dia?" / "isso emagrece?" / "posso tomar com
   remédio?" → a IA acolhe SEM responder e orienta consultar nutricionista/médico/educador físico,
   lembrando que o produto não substitui orientação profissional.
3. **Confirmação:** com itens + ENDEREÇO (só entrega), a última mensagem TERMINA com
   `<pedido_suplementos>{...}`. O `OutboundService.maybeProcessPedidoSuplementos` chama o
   `PedidoSuplementosConfirmHandler`, que cria o pedido `aguardando` e **remove a tag**.
4. **Frete grátis:** se o subtotal bate `free_shipping_threshold_cents`, o backend ZERA a taxa; a
   IA pode avisar "faltam R$ X pro frete grátis" (fato do pedido, não conselho de saúde).
5. **Gate humano:** a loja aceita (`em_preparo`) ou recusa (`recusado` + motivo) no Kanban.
6. **Exceções (abort silencioso, sem pedido):** variante esgotada (`OutOfStockException` → rollback
   total), endereço ausente, tag malformada — warn no log, a mensagem da IA segue normal.
7. **Cancelamento:** mover pra `recusado`/`cancelado` devolve o estoque das variantes na mesma
   transação (`stock_returned`, idempotente — onda 1).

## Regras de negócio

### Transacionais (invariantes duras)

- **R1 — Estoque por UPDATE condicional:** decremento com `active = true and stock_quantity >= qtd`
  no WHERE (`SupVariantRepository.decrementStock`); 0 linhas → `OutOfStockException` → rollback do
  `@Transactional`: o pedido inteiro ABORTA (nenhum pedido parcial).
- **R2 — Só entrega:** `delivery_address` NOT NULL no banco; ausente →
  `AddressRequiredException` (nominal 422 `address_required`) — o handler valida antes e aborta em
  silêncio. Não existe coluna `fulfillment`/retirada.
- **R3 — Total recalculado do catálogo:** `unit_price` = preço DA VARIANTE (NOT NULL — não herda
  base, diferente da lingerie); `subtotal = Σ unit_price × qtd`; `total = subtotal + taxa`, com a
  taxa ZERADA quando `subtotal ≥ free_shipping_threshold_cents` (NULL = desligado) — materializado
  em Java. A tag não carrega total; se carregasse, seria descartado.
- **R4 — Restock idempotente ao cancelar (onda 1, espelho moda_infantil):** ao entrar em
  `recusado`/`cancelado`, o repositório devolve `stock_quantity + qtd` por item NA MESMA transação e
  marca `stock_returned = true`; só devolve se ainda era false (duplo-cancelamento não devolve 2×).
- **R5 — Variante = SKU:** `flavor` nullable (acessório sem sabor) × `size_label` 1–60 obrigatório;
  `UNIQUE(company_id, sku) where sku is not null`; combinação duplicada → 409 `duplicate_variant`;
  `expiry_date` é administrativo (a IA NÃO promete validade).
- **R6 — Snapshot por item:** `product_name_snapshot` + `variant_label_snapshot` ("Chocolate 900g")
  + `unit_price_cents` congelados em `sup_order_items`; `product_id` E `variant_id`
  `on delete restrict` → 409 `product_in_use` / `variant_in_use`.
- **R7 — Gate de aceite humano:** pedido nasce `aguardando`; só o painel transiciona
  (`PATCH /api/suplementos/orders/{id}/status`). Sem POST manual de pedido (INSERT só service_role).
- **R8 — Linhas inválidas são filtradas** (variante/produto inexistente, de outro tenant ou
  inativo); nenhuma linha válida → `IllegalArgumentException` → sem pedido.
- **R9 — Trava de não-prescrição em DOIS lugares:** persona `ProfilePromptContext.SUPLEMENTOS` E
  bloco de instruções do `SuplementosMenuCache` (padrão nutri) — a IA não tem caminho de dado nem de
  prompt pra prescrever (`sup_products.description` é informativo de produto, sem posologia).

### Máquina de status

```
aguardando ──aceite──▶ em_preparo ──▶ saiu_entrega ──▶ entregue (terminal)
     │   │                  │               │
     │   └──recusa──▶ recusado              │
     └────────────▶ cancelado ◀─────────────┘   (recusado/cancelado terminais; ambos DEVOLVEM estoque)
```

| Transição | Quem pode | Notifica o cliente? |
|---|---|---|
| (criação) → `aguardando` | IA (tag; único write da IA) | NÃO (a IA já confirmou na mensagem) |
| `aguardando` → `em_preparo` | humano no painel | SIM ("aceito! 💪 já estamos separando") |
| `aguardando` → `recusado` | humano (com `rejection_reason`) + restock | SIM (texto defensivo + " Motivo: …") |
| `aguardando` → `cancelado` | humano + restock | **NÃO** (cancelado é silencioso neste perfil) |
| `em_preparo` → `saiu_entrega` | humano | SIM ("saiu pra entrega") |
| `saiu_entrega` → `entregue` | humano | SIM ("entregue, obrigado") |
| `em_preparo`/`saiu_entrega` → `cancelado` | humano + restock | **NÃO** |

Duas particularidades vs. os irmãos de chassi: `aguardando → cancelado` direto EXISTE, e
`cancelado` NÃO notifica (`SuplementosOrderStatus.notificationText` devolve null). Fora do grafo →
409 `invalid_status_transition`; alvo desconhecido → 400 `invalid_status`.

### O que a IA PODE × NUNCA faz (travas da persona)

- **PODE:** mostrar o catálogo; tirar dúvida de PRODUTO (sabor, peso/tamanho da embalagem, preço,
  disponibilidade/estoque); montar o pedido pela variante exata; avisar quanto falta pro frete
  grátis; lembrar que o produto não substitui orientação profissional.
- **NUNCA (a trava, inegociável):** prescreve dosagem/posologia/"quanto/como tomar"/horário;
  recomenda suplemento como tratamento ou conduta por objetivo (emagrecer/ganhar massa/curar) ou
  sintoma; responde "serve pra X?"/"engorda?"/"posso tomar com remédio?"/"qual o melhor pra mim?";
  opina sobre patologia, interação medicamentosa, contraindicação ou efeito fisiológico; monta
  protocolo ou compara por "eficácia". Também NUNCA: oferece variante esgotada, inventa
  produto/sabor/peso/preço, promete validade, aceita/recusa pedido.

### Tags de IA

| Tag | Quando a IA emite | Campos | O backend descarta/recalcula |
|---|---|---|---|
| `<pedido_suplementos>` | confirmação final COM endereço | `delivery_address`, `items[{variant_id,qtd}]`, `notes` | preço/total SEMPRE do catálogo (a tag nem carrega total); taxa e frete grátis calculados pelo backend; endereço obrigatório |

Única tag do perfil (sem cupom nesta fase). Parse por regex, removida antes do envio; falha →
`Optional.empty()` + warn, mensagem segue sem pedido.

### Validações e erros

| reason | HTTP | Significado de negócio | Cenário |
|---|---|---|---|
| `forbidden_wrong_profile` | 403 | tenant de outro perfil em `/api/suplementos/**` | guard `SuplementosProfileGuard` |
| `invalid_status` / `invalid_status_transition` | 400 / 409 | alvo desconhecido / fora do grafo | PATCH no Kanban |
| `order_not_found` / `product_not_found` / `variant_not_found` | 404 | recurso inexistente/de outro tenant | GET/PATCH/DELETE |
| `invalid_category` | 400 | fora das 6 categorias (`proteinas…acessorios`) | CRUD de produto |
| `duplicate_variant` | 409 | sabor×peso (ou SKU) já existe | POST/PATCH de variante |
| `product_in_use` / `variant_in_use` | 409 | referenciado por item de pedido (FK restrict) | DELETE |
| `out_of_stock` / `address_required` | 409 / 422 (nominais) | esgotado / entrega sem endereço | **não viram HTTP**: abort silencioso no handler |

### Notificações ao cliente

- **Envia** em `em_preparo`, `saiu_entrega`, `entregue` e `recusado` (+ motivo) — textos FIXOS e
  defensivos de `SuplementosOrderStatus.notificationText`, SEM qualquer conteúdo de saúde.
- **Silêncio** em `aguardando` (a IA já confirmou o recebimento) e em `cancelado` (particularidade
  do perfil). Best-effort (`SupOrderNotifier`): falha de envio nunca reverte o status persistido.

## Dados e snapshots

- `sup_config` (1:1): `delivery_fee_cents`/`min_order_cents` ≥ 0 (ausente → ZERO); mig 89 soma
  `free_shipping_threshold_cents` (NULL = desligado, CHECK ≥ 0).
- `sup_products`: `name` 1–200; `brand` livre; CHECK de categoria
  (`proteinas/aminoacidos/vitaminas/pre_treino/emagrecedores/acessorios`, sync
  `SuplementosCategory`); `description` sem dosagem (por convenção — a IA não usa pra recomendar).
- `sup_variants`: `flavor` nullable, `size_label` 1–60, `price_cents ≥ 0` NOT NULL (preço da
  variante, sem herança), `stock_quantity ≥ 0` (CHECK, defesa sob R1), `expiry_date` informativo,
  `UNIQUE(company, sku)` parcial.
- `sup_orders`: CHECK de status (6); `delivery_address` NOT NULL; totais materializados;
  `rejection_reason`; mig 89 soma **`stock_returned`** (marcador do restock). INSERT só backend;
  tenant SELECT/UPDATE via RLS.
- `sup_order_items`: snapshots + `qtd > 0`; `product_id` e `variant_id` restrict.
- **Cache:** `SuplementosMenuCache` — Caffeine TTL **60s** por company (ignora conversationId),
  carrega catálogo + a TRAVA; invalidado EXPLICITAMENTE em toda mutação de produto/variante/config
  (`SupProductService`, `SuplementosConfigService`).

## Features de onda (backlog implementado — mig 89)

- **Frete grátis acima de X (#3b):** `free_shipping_threshold_cents` na config (campo em
  Configurações); subtotal ≥ piso → taxa zerada no cálculo materializado (R3). O bloco do cache
  autoriza a IA a avisar quanto falta — fato do pedido, trava preservada.
- **Devolução de estoque ao cancelar/recusar (#9):** regra R4 — evita ruptura fantasma (o estoque
  do sistema volta a bater com a prateleira).

## O que NÃO existe (limites honestos)

- **Retirada na loja** (só entrega); **cupom/combo** (motor unificado fica pra onda 2);
  recomendação personalizada / quiz de suplemento (**PROIBIDO pela trava**, não é backlog);
  assinatura/recorrência (chassi E — perfil academia cobre); reativação por ciclo de reposição
  (onda 2).
- Foto de produto (SERVICE_ROLE_KEY); pagamento real (Stripe #50); lote/FEFO/inventário — a
  `expiry_date` é um campo informativo por variante, sem controle de validade; tabela nutricional
  estruturada.
- **POST manual de pedido:** não há — `out_of_stock`/`address_required` nunca aparecem como HTTP.
- **Pedido mínimo NÃO é validado no backend:** `min_order_cents` só instrui a IA ("avise, mas não
  recuse — apenas oriente").
