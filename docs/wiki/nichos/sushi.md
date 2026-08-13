# SushiBot — regras de negócio (sushi, camada 7.1)

[← Catálogo](../05-nichos.md) · Chassi: B — order-based (avô) · Guia operacional: docs/PERFIL_SUSHI.md · Migrations: 30, 69, 88

## O negócio em 3 linhas

Restaurante de sushi delivery/balcão. O cliente final pede pelo WhatsApp em linguagem natural;
a IA conhece o cardápio, monta o pedido **na conversa** (sem entidade de carrinho), fecha com a
tag `<pedido>` e o restaurante toca o funil num Kanban. É o perfil **funcionalizado** (mig 69):
categorias, estados do pedido e textos de notificação são **tabelas por tenant**, não enums.

## Jornada no WhatsApp (cenários)

1. Cliente: "quero 2 Filadélfia e 1 California". A IA responde com itens/preços do bloco de
   cardápio injetado no prompt (`SushiMenuCache`) e vai montando o pedido na conversa.
2. Com `upsell_enabled` (default ON), a IA oferece **no máximo 1** complemento do próprio
   cardápio antes de fechar — sem insistir, nunca item fora do cardápio.
3. No fechamento a IA pergunta **entrega ou retirada**; entrega exige endereço. Se
   `scheduling_enabled`, oferece agendar (data + período manhã/tarde/noite); senão é "para agora".
4. Cliente confirma → a IA emite `<pedido>`; o `OrderConfirmHandler` valida cada `item_id`
   (existe no tenant E `available`) e cria o pedido no **status inicial** do tenant (seed:
   "Recebido", silencioso). A tag é removida antes do envio.
5. O restaurante avança o pedido no Kanban; cada estado com `notify_enabled` + `notify_text`
   dispara a mensagem editada pelo tenant.
- **Exceções:** item inexistente/indisponível na tag, JSON inválido, entrega sem endereço ou
  data agendada no passado → o pedido **não é criado** (handler devolve vazio, loga warn; a
  mensagem da IA segue normal). Cupom inválido **não aborta** — o pedido sai sem desconto.

## Regras de negócio

### Transacionais (invariantes duras)

- **R1 — Total recalculado pelo backend.** `total_cents` da tag é descartado; subtotal = Σ
  preço-do-cardápio × qtd, lido no momento do INSERT (`SushiOrderRepository.createOrder`,
  `@Transactional`); `total = subtotal − discount + delivery_fee`, materializados em Java.
- **R2 — Snapshot por item.** `unit_price_cents` + `item_name_snapshot` congelam o cardápio do
  momento; excluir item com pedido → FK `on delete restrict` (409 `menu_item_in_use`).
- **R3 — Cupom válido = active + `valid_until` ≥ hoje + subtotal ≥ `min_order_cents` + `uses`
  < `max_uses`; `uses` incrementa na MESMA transação do pedido; desconto (percent no subtotal
  ou fixed) clampado ao subtotal (CHECKs: percent 1–100, `discount_cents >= 0`).
- **R4 — Fidelidade por contagem** (`sushi_loyalty_config`, default OFF): conta os pedidos
  "entregues" do contato ANTES do INSERT; `count > 0 && count % threshold == 0` → reward.
  Como o status é tabela, "entregue" = **status terminal cujo nome NÃO contém "cancel"**
  (regra pragmática de `countDeliveredForContact`).
- **R5 — Retirada zera a taxa** e dispensa endereço; entrega exige endereço
  (`AddressRequiredException`) e soma `delivery_fee_cents` da config.
- **R6 — Agendamento**: com `scheduling_enabled=false`, data/período da tag são IGNORADOS;
  ligado, data no passado (fuso America/Sao_Paulo) → `InvalidScheduleException`. Período ∈
  `agora|manha|tarde|noite` (CHECK).
- **R7 — Estado inicial único por tenant** (índice parcial UNIQUE `where is_initial`); o pedido
  nasce nele. Nome de categoria/status/cupom único case-insensitive por company (UNIQUE em
  `lower(name)`/`lower(code)`).
- **R8 — Pedido mínimo é consultivo**: a IA avisa abaixo do mínimo mas NÃO recusa; o backend
  não valida (`min_order_cents` só entra na validação de cupom).

### Máquina de status

A máquina é **por tenant** (`sushi_order_statuses` — mig 69). Não há matriz de transições: a
regra é **transição LIVRE entre estados não-terminais**; sair de um terminal → 409.

```
(seed default por company sushi)
Recebido ──→ Em preparo ──→ Saiu pra entrega ──→ Entregue (terminal)
    └────────────┴──────────────────┴──────────→ Cancelado (terminal)
regra real: qualquer não-terminal → qualquer estado; terminal → nada
```

| Transição | Quem pode | Notifica o cliente? |
|---|---|---|
| criação → estado inicial | IA (via tag; INSERT service_role) | Não (seed: Recebido silencioso — a IA já confirmou) |
| não-terminal → qualquer | Humano no painel (Kanban) | Se `notify_enabled` + `notify_text` do estado ALVO |
| terminal → qualquer | Ninguém | 409 `invalid_status_transition` |

### O que a IA PODE × NUNCA faz (travas da persona)

- **PODE:** sugerir combinações/harmonizações; montar o pedido; perguntar entrega×retirada;
  oferecer agendamento (se ligado); registrar o código do cupom; 1 upsell (se ligado).
- **NUNCA:** inventa item/preço fora do cardápio; inventa desconto/cupom/valor ("quem valida o
  cupom, calcula a fidelidade e recalcula o total é o sistema"); transiciona status; confirma
  total diferente do informado pelo sistema (persona `ProfilePromptContext.SUSHI`).

### Tags de IA

| Tag | Quando a IA emite | Campos | O backend descarta/recalcula |
|---|---|---|---|
| `<pedido>` | Confirmação final do cliente (com endereço, se entrega) | `items[{item_id,qtd}]`, `endereco`, `fulfillment`, `scheduled_date`, `scheduled_period`, `cupom`, `total_cents` | `total_cents` descartado; preços relidos do cardápio; cupom/fidelidade calculados pelo sistema; data/período ignorados se agendamento OFF |

### Validações e erros

| reason | HTTP | Significado de negócio | Cenário |
|---|---|---|---|
| `forbidden_wrong_profile` | 403 | tenant de outro perfil em `/api/sushi/**` | guard `SushiProfileGuard` |
| `invalid_status` | 400 | status alvo desconhecido/de outro tenant | PATCH com UUID inválido |
| `invalid_status_transition` | 409 | pedido já em estado terminal | avançar pedido entregue/cancelado |
| `order_not_found` | 404 | pedido inexistente/de outro tenant | GET/PATCH errado |
| `menu_item_not_found` / `menu_item_in_use` | 404 / 409 | item inexistente / com pedido (FK restrict) | CRUD do cardápio |
| `invalid_category` / `category_not_found` / `duplicate_category` / `category_in_use` / `invalid_category_name` | 400/404/409/409/400 | CRUD de categorias (mig 69) | nome duplicado; excluir categoria com item |
| `status_not_found` / `duplicate_status` / `status_in_use` / `initial_status_undeletable` / `invalid_status_name` | 404/409/409/409/400 | CRUD de estados (mig 69) | excluir estado com pedido ou o inicial |
| `invalid_coupon` / `coupon_not_found` / `duplicate_coupon` | 400/404/409 | CRUD de cupons | código duplicado (case-insensitive) |
| `invalid_loyalty_config` | 400 | config de fidelidade inválida | threshold/reward fora da faixa |

`address_required` e `invalid_schedule_date` existem como exceções do `SushiOrderService`, mas
**não têm mapeamento HTTP** — só surgem no caminho da IA, onde o handler engole e não cria o
pedido (não há POST REST de pedido).

### Notificações ao cliente

- Envia **ao ENTRAR** num estado com `notify_enabled` + `notify_text` (editáveis pelo tenant).
  Seed: Em preparo / Saiu pra entrega / Entregue / Cancelado notificam; **Recebido é
  silencioso** (a IA já confirmou o recebimento — evita mensagem duplicada).
- Best-effort por contrato: falha de envio NUNCA reverte a transição (loga warn). A mensagem é
  persistida em `messages` como outbound/**human** (é o restaurante avisando, não a IA).
  `EVOLUTION_DRY_RUN` honrado.

## Dados e snapshots

- `sushi_menu_items` (nome 1–120, `price_cents >= 0`, `category` FK nullable p/
  `sushi_categories` on delete restrict), `sushi_categories`, `sushi_order_statuses`
  (UNIQUE parcial `is_initial` por company), `sushi_restaurant_config` (1:1; ausente → taxa e
  mínimo 0; flags `scheduling_enabled`, `upsell_enabled`, `reactivation_*`), `sushi_coupons`,
  `sushi_loyalty_config` (1:1, seed idempotente), `sushi_orders` (INSERT só backend; tenant só
  SELECT/UPDATE via RLS), `sushi_order_items`, `sushi_reactivation_log`.
- Snapshots: `unit_price_cents`, `item_name_snapshot`, `coupon_code_snapshot`; `discount_cents`
  e totais materializados em Java (nunca coluna GENERATED).
- Cache: `SushiMenuCache` (Caffeine, TTL 60s, max 500) — bloco de cardápio+config+instruções
  da tag; invalidado explicitamente nas mutações de cardápio/config.

## Features de onda (backlog implementado)

- **Mig 69 (funcional):** categorias/estados/notificações por tenant; cupom; fidelidade;
  `scheduling_enabled` (default OFF); retirada×entrega; desconto no pedido.
- **Mig 88 (onda 2):** `upsell_enabled` (default **ON** — sugestão consultiva) e
  **reativação de inativos** (`SushiReactivationJob`, cron `0 20 10 * * *`): contato cujo último
  pedido entregue (terminal não-cancelado) é anterior a `reactivation_days` (7–180, default 21)
  recebe UMA mensagem fixa (não passa pela IA) pela conversa mais recente, mencionando
  `reactivation_coupon_code` só se existir/ativo/válido. Idempotência por contato+janela em
  `sushi_reactivation_log` (cooldown = a janela; sem conversa → marcado `had_channel=false`).
  **Opt-in: default DESLIGADO** (lição do incidente Baileys).

## O que NÃO existe (limites honestos)

- Foto no cardápio (bloqueador SERVICE_ROLE_KEY); carrinho com tela; endereço estruturado
  (texto livre); pagamento online (backlog #50); rastreio/ETA de entregador.
- **Gate de aceite com recusa** (isso é do chassi comida): sushi nasce no estado inicial e as
  transições são livres — não há `recusado`/`rejection_reason` por default.
- Validação dura de pedido mínimo (só aviso da IA); matriz de transição configurável.
