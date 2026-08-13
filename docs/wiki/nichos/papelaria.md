# Papelaria (convites personalizados) — regras de negócio (`papelaria`, camada 8.15)

[← Catálogo](../05-nichos.md) · Chassi: **B — pedido order-based com gate de aceite** (clone do padaria) + escapada **prova de arte** · Guia operacional: `docs/PERFIL_PAPELARIA.md` · Migrations: **59** (base), **95** (onda 1)

## O negócio em 3 linhas

Encomenda GRÁFICA personalizada: convites, save the date, cartões, adesivos, embalagens. O cliente
pede pelo WhatsApp; a IA monta o pedido com **tiragem** (50/100/200…), personalização
(papel/acabamento/cor) e texto personalizado, respeitando o lead time. Antes de imprimir, a equipe
produz a ARTE e o **cliente aprova a prova** — gate extra dentro do pedido.

## Jornada no WhatsApp (cenários)

1. A IA apresenta o catálogo (`PapelariaCatalogCache` injeta itens, opções, prazos e a tabela de
   faixas de tiragem — "50+ un = X · 100+ = Y").
2. Coleta tiragem, personalização, `custom_text` por item, retirada × entrega e, para item sob
   encomenda, a data (≥ hoje + maior lead). Emite `<pedido_papelaria>{...}` → pedido `aguardando`.
3. A papelaria **aceita** no painel (`aguardando → aceito`), produz a arte e **sobe o link**
   (PATCH `/art` com `art_url`) → `arte_aprovacao`, cliente notificado ("dê uma olhada e aprove").
4. O cliente declara a aprovação na conversa → a IA emite `<aprovacao_arte>` → `art_approved=true` e
   o pedido move para `em_producao` (ou fica retido aguardando o sinal — onda 1). A papelaria também
   pode aprovar no painel.
5. Segue `pronto → {retirado | saiu_entrega → entregue}`.

**Exceções:** criação aborta em silêncio (item/opção inválidos, lead time, endereço); tentar
produzir sem arte aprovada → 409 `art_not_approved`; sinal pendente → 409 `deposit_required`;
`<aprovacao_arte>` fora de `arte_aprovacao`, de outro contato ou sem pedido resolvido é **no-op**.

## Regras de negócio

### Transacionais (invariantes duras)

- **R1 — Tiragem escala o total.** `line = unit_price × quantity`; `unit_price = base + Σ deltas`.
  Total materializado em Java; o da tag é descartado (`PapelariaOrderRepository.createOrder`).
- **R2 — Preço por faixa de tiragem (onda 1).** A faixa com **MAIOR `min_qty ≤ quantity`** substitui
  o preço-base do item (query `order by min_qty desc limit 1` em `papelaria_item_tiers`); sem faixa
  → preço do item (compat total). Preço sempre do catálogo.
- **R3 — Gate da prova de arte.** `arte_aprovacao → em_producao` SÓ com `art_approved=true` → senão
  409 `art_not_approved` (checado no `PapelariaOrderService.updateStatus`). Subir a arte
  (`setArtUrl`) exige `art_url` não-vazio e só vale de `aceito` (transição p/ `arte_aprovacao`).
  Pedido só pronta-entrega pode pular: `aceito → em_producao` direto.
- **R4 — Aprovação com barreira de contato.** `approveArt` só quando o status é `arte_aprovacao`; a
  tag `<aprovacao_arte>` só vale vinda do **contato dono** do pedido (dono = contato da conversa de
  origem; `order_id` alucinado não aprova arte de outro cliente — `AprovacaoArteHandler`).
- **R5 — Sinal trava a produção (onda 1).** Com `deposit_cents > 0` e não pago: aprovar a arte SETA
  `art_approved` mas o pedido FICA em `arte_aprovacao` (cliente notificado do valor); transição
  manual → 409 `deposit_required`. **Marcar o sinal pago com a arte já aprovada MOVE
  automaticamente para `em_producao`** (`setDeposit` fecha o loop).
- **R6 — Lead time condicional** (herdado do padaria): item `made_to_order` → data obrigatória ≥
  `hoje + MAX(leads, fallback lead_time_days_default=5)`; violação →
  `LeadTimeViolationException` com a 1ª data possível (convenção `lead_time_violation`, 422).
- **R7 — Entrega exige endereço** (`AddressRequiredException`, convenção `address_required`);
  retirada zera taxa. Opção fantasma aborta. Snapshots imutáveis (`catalog_item_id ON DELETE
  RESTRICT` → 409 `catalog_item_in_use`); INSERT de pedido só pelo backend (sem POST REST).

### Máquina de status

```
aguardando ──aceitar──→ aceito ──subir arte──→ arte_aprovacao ──aprovar(+sinal pago)──→ em_producao
    │                     └──────── (só pronta-entrega/sem arte) ────────────────────────↗   │
    ├─recusar→ recusado                                                                  pronto
    └→ cancelado (de qualquer não-terminal)                        retirado ←─┘  └─→ saiu_entrega → entregue
```

| Transição | Quem pode | Notifica? |
|---|---|---|
| `aguardando → aceito` (gate de aceite) | humano no painel | Sim ("Vamos preparar a arte…") |
| `aguardando → recusado` | humano | Sim (defensivo + motivo) |
| `aceito → arte_aprovacao` (sobe `art_url`) | humano (PATCH `/art`) | Sim ("Sua arte está pronta! … aprove 🎨") |
| `arte_aprovacao → em_producao` | **cliente via IA** (`<aprovacao_arte>`) OU humano — única mutação de estado que a IA faz | Sim ("Arte aprovada! ✅…") |
| `aceito → em_producao` (sem arte) | humano | Sim |
| `em_producao → pronto` | humano | Sim |
| `pronto → retirado` | humano | **Não** |
| `pronto → saiu_entrega → entregue` | humano | Sim (ambas) |
| `* → cancelado` | humano | **Não** (silencioso) |

Terminais: `retirado`, `entregue`, `recusado`, `cancelado`. Inválida → 409
`invalid_status_transition` (`PapelariaOrderStatus`, 10 estados, parity TS).

### O que a IA PODE × NUNCA faz (travas da persona)

PODE: montar o pedido com tiragem/personalização/texto; usar as faixas de tiragem do catálogo para
estimular tiragem maior; oferecer **UMA** sugestão de item de OUTRA categoria (convite → save the
date/tags/menu — upsell onda 1); **REGISTRAR** a aprovação que o cliente declarar (só em
`arte_aprovacao`); informar o valor do sinal registrado. NUNCA: **aprova a arte pelo cliente** nem
força a aprovação; sobe arte ou diz que ficou pronta sem a papelaria ter subido; inventa
produto/papel/acabamento/cor/preço; promete layout artístico fora do catálogo ("vou confirmar com a
equipe de criação"); promete data antes do lead; aceita/recusa o pedido; confirma pagamento
(persona `ProfilePromptContext.PAPELARIA` + instruções do `PapelariaCatalogCache`).

### Tags de IA

| Tag | Quando a IA emite | Campos | O backend descarta/recalcula |
|---|---|---|---|
| `<pedido_papelaria>` | confirmação do pedido | `fulfillment`, `pickup_or_delivery_date`, `delivery_period`, `delivery_address`, `items[{catalog_item_id, options[{option_id}], custom_text, quantity}]`, `notes` | total ignorado; preço via tiers + deltas; lead/endereço revalidados |
| `<aprovacao_arte>` | quando o cliente DECLARA aprovar a prova | `order_id` (opcional — sem ele, resolve o pedido em `arte_aprovacao` da conversa) | muta um pedido EXISTENTE; fora de `arte_aprovacao`/outro contato → no-op silencioso |

### Validações e erros

| reason | HTTP | Significado | Cenário |
|---|---|---|---|
| `forbidden_wrong_profile` | 403 | tenant de outro perfil | `/api/papelaria/**` (`PapelariaProfileGuard`) |
| `art_not_approved` | 409 | produzir sem OK do cliente | PATCH status `arte_aprovacao → em_producao` com `art_approved=false` |
| `art_url_required` | 400 | subir arte sem link | PATCH `/orders/{id}/art` sem `artUrl` (e `approve=false`) |
| `deposit_required` | 409 | sinal registrado e não pago | transição manual p/ `em_producao` com sinal pendente |
| `invalid_deposit` | 400 | sinal negativo ou "pago" sem valor > 0 | PATCH `/orders/{id}/deposit` |
| `invalid_status` / `invalid_status_transition` | 400 / 409 | Kanban / subir arte fora de `aceito` / aprovar fora de `arte_aprovacao` | PATCH status / `/art` |
| `order_not_found` / `catalog_item_not_found` / `option_not_found` | 404 | id inexistente | consulta/CRUD |
| `catalog_item_in_use` | 409 | item com pedido histórico | DELETE de item |
| `invalid_category` | 400 | fora de `PapelariaCategory` (6 hardcoded) | POST/PATCH item |
| `invalid_tier` | 400 | faixa malformada (`min_qty < 1`, preço < 0) | PUT `/catalog/{id}/tiers` |
| `lead_time_violation` · `address_required` | (422 convenção) | data antes do prazo · entrega sem endereço | **só no fluxo da IA** — handler aborta sem criar (não há POST REST de pedido) |

### Notificações ao cliente

Texto fixo por status (`PapelariaOrderStatus.notificationText`, best-effort via
`PapelariaOrderNotifier`). Silenciosos: `aguardando`, `retirado`, `cancelado`. Mensagens extras da
escapada: ao subir a arte ("aprove pra gente imprimir") e, com sinal pendente na aprovação, o texto
ad-hoc com o valor do sinal ("falta a confirmação do sinal de R$ X"). Recusa concatena o motivo.

## Dados e snapshots

| Tabela | Constraints que são regra |
|---|---|
| `papelaria_config` (1:1) | taxa/mínimo ≥ 0; `lead_time_days_default ≥ 0` (**default 5** — gráfica demora mais que padaria) |
| `papelaria_catalog_items` | preço BASE UNITÁRIO (× tiragem); categoria CHECK (convites, save_the_date, cartoes, papelaria, adesivos, embalagens); `made_to_order` + `lead_time_days` nullable; `specs` texto livre |
| `papelaria_catalog_item_options` | `price_delta_cents ≥ 0`; CASCADE do item |
| `papelaria_orders` | status CHECK (**10 estados**, incl. `arte_aprovacao`); `art_approved` NOT NULL default false + `art_url`; fulfillment/period CHECK; mig 95: `deposit_cents ≥ 0`, `deposit_paid` default false, `deposit_paid_at` |
| `papelaria_order_items` | `quantity > 0` (= TIRAGEM); snapshots `unit_price_cents`, `item_name_snapshot`, `made_to_order_snapshot`, `custom_text` |
| `papelaria_item_tiers` | **UNIQUE `(item_id, min_qty)`**; `min_qty ≥ 1`; tabela SÓ `service_role` (RLS sem policies — gerida pelo painel via Spring) |

Cache: `PapelariaCatalogCache` (Caffeine, **TTL 60s**), invalidado em toda mutação de
item/opção/tier/config; injeta as faixas de tiragem e ensina as DUAS tags.

## Features de onda (backlog implementado — migration 95)

- **#1 Sinal pra liberar a produção:** regra R5; registro MANUAL (PATCH
  `/api/papelaria/orders/{id}/deposit`) até o gateway #50; selo + modal no card do Kanban.
- **#2 Preço por faixa de tiragem** (`papelaria_item_tiers`): regra R2; GET/PUT
  `/api/papelaria/catalog/{id}/tiers`; o cache injeta a tabela pra IA estimular tiragem maior.
- **#5 Upsell na persona (sem DDL):** UMA sugestão de item de outra categoria, sem insistir; IA
  informa o sinal registrado sem nunca confirmar pagamento.

## O que NÃO existe (limites honestos)

- **Pedido mínimo NÃO é validado no backend** (`min_order_cents` só no prompt) — trava
  conversacional.
- Upload da arte como arquivo/imagem — a "arte subida" é **link colado** (`art_url`; bloqueador
  `SERVICE_ROLE_KEY`); versões/revisões da prova (aprovação é binária, 1 rodada); orçamento ad-hoc
  de convite artístico; e-sign/contrato; assinatura recorrente; combo/cupom/fidelidade; pagamento
  online real (Stripe #50); integração com gráfica externa; estoque; scheduler de auto-transição.
