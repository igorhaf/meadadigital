# Lavanderia (coleta e entrega agendadas) — regras de negócio (`lavanderia`, camada 8.10)

[← Catálogo](../05-nichos.md) · Chassi: **B — pedido order-based agendado com gate de aceite** (clone do floricultura) · Guia operacional: `docs/PERFIL_LAVANDERIA.md` · Migrations: **54** (base), **103** (onda 1)

## O negócio em 3 linhas

Lavanderia com coleta e entrega em domicílio. O cliente pede pelo WhatsApp; a IA monta o pedido
(quantidade de peças por serviço do catálogo), coleta a **data de coleta** + período + endereço e
confirma com o total E a **data de entrega prometida** — que o sistema deriva do prazo de
processamento (turnaround). A lavanderia acompanha num Kanban com gate de aceite.

## Jornada no WhatsApp (cenários)

1. A IA apresenta os serviços com preço por peça E prazo (`LavanderiaCatalogCache` injeta
   `turnaround_days` por serviço).
2. Monta o carrinho na conversa; coleta `collect_date` (≥ hoje) + `period` (manhã/tarde) + endereço
   (**sempre coleta+entrega** — não há retirada de balcão).
3. Cliente com pressa? Se `express_enabled`, a IA oferece o EXPRESS informando a sobretaxa DA CONFIG
   e marca `"express":true` na tag (nunca inventa o valor).
4. Na confirmação (total + data de entrega = coleta + prazo do serviço mais lento) emite
   `<pedido_lavanderia>{...}`; o `PedidoLavanderiaConfirmHandler` cria o pedido `aguardando`.
5. Gate de aceite = **receber as peças na coleta**: `aguardando → coletado` (humano). Segue
   `em_processo → pronto → saiu_entrega → entregue`.

**Exceções** (abortam a criação — mensagem segue sem pedido): endereço ausente, `collect_date`
ausente/no passado, período inválido, subtotal abaixo do mínimo, `delivery_date` pedida antes da 1ª
possível, serviço inexistente, opção fantasma. Cupom inválido NÃO aborta.

## Regras de negócio

### Transacionais (invariantes duras)

- **R1 — Duas datas acopladas por turnaround.** `delivery_date` é **MATERIALIZADA** em Java no
  INSERT: `collect_date + MAX(turnaround_days entre os itens)` — **MAX, não soma** (processamento
  paralelo; vale o serviço mais lento). `date + interval` não é IMMUTABLE → nunca coluna GENERATED.
- **R2 — Violação de turnaround.** `delivery_date` pedida pela IA anterior à 1ª possível →
  `TurnaroundViolationException` com a **1ª data possível** (convenção `turnaround_violation`, 422).
  Data pedida IGUAL ou POSTERIOR à 1ª possível é aceita; omitida → o backend materializa a 1ª.
- **R3 — Coleta ≥ hoje** (fuso America/Sao_Paulo) → senão `CollectDateInPastException`
  (`collect_date_in_past`). `period` (manhã/tarde) é o da COLETA; a entrega herda.
- **R4 — Pedido mínimo VALIDADO no backend** (único do quarteto B desta onda): `subtotal <
  min_order_cents` → `BelowMinimumException` (`below_minimum`) — pedido não nasce.
- **R5 — Total recalculado e materializado.** `total = subtotal − discount + delivery_fee +
  express_surcharge`; `unit_price = base + Σ deltas`; `turnaround_snapshot` por item. O
  `total_cents` da tag (se vier) é descartado.
- **R6 — Express controlado pela config.** `express:true` na tag SÓ vale com `express_enabled`
  (toggle off → pedido normal, defensivo). Express substitui o MAX dos itens por
  `express_turnaround_days` e soma `subtotal × express_surcharge_pct / 100`
  (`express_surcharge_cents` materializado).
- **R7 — Cupom + fidelidade** (motor clone adega): cupom válido = active + validade + mínimo + usos;
  inválido não aborta; `uses` incrementa na mesma transação; fidelidade conta os `entregue` do
  contato ANTES do INSERT (`count % threshold == 0` → reward); `discount = min(subtotal, cupom +
  fidelidade)`.
- **R8 — Endereço obrigatório** (`delivery_address` NOT NULL — `address_required` no service);
  INSERT de pedido só pelo backend (sem policy nem POST REST). Snapshots imutáveis
  (`service_id ON DELETE RESTRICT` → 409 `service_in_use`).

### Máquina de status

```
aguardando ──aceitar(coleta)──→ coletado → em_processo → pronto → saiu_entrega → entregue
    ├──recusar──→ recusado
    └──→ cancelado   (de QUALQUER não-terminal)
```

| Transição | Quem pode | Notifica? |
|---|---|---|
| `aguardando → coletado` (gate = receber as peças) | humano no painel | Sim ("Recebemos suas peças… 🧺") |
| `aguardando → recusado` | humano | Sim (defensivo + motivo) |
| `coletado → em_processo` | humano | **Não** (processamento é interno) |
| `em_processo → pronto` | humano | Sim |
| `pronto → saiu_entrega → entregue` | humano | Sim (ambas) |
| `* → cancelado` (não-terminal) | humano | Sim ("…é só me chamar") |

Terminais: `entregue`, `recusado`, `cancelado`. Inválida → 409 `invalid_status_transition`
(`LavanderiaOrderStatus`, parity TS).

### O que a IA PODE × NUNCA faz (travas da persona)

PODE: montar pedido em linguagem livre; oferecer o express (com sobretaxa da config, só com aceite
explícito do cliente); registrar código de cupom. NUNCA: inventa serviço/peça/adicional/preço;
aceita/recusa o pedido; **promete remover mancha**, garantir resultado ou recuperar peça danificada
("a equipe avalia na coleta, sem garantia de remoção total"); promete entrega antes do prazo
(coleta + prazo do mais lento); inventa desconto (persona `ProfilePromptContext.LAVANDERIA`).

### Tags de IA

| Tag | Quando a IA emite | Campos | O backend descarta/recalcula |
|---|---|---|---|
| `<pedido_lavanderia>` | confirmação com total + data de entrega | `collect_date`, `period` (manha/tarde), `delivery_address`, `delivery_date` (opcional), `items[{service_id, options[], qty}]`, `notes`, `cupom` (opcional), `express` (opcional) | total descartado; `delivery_date` validada/materializada; cupom/fidelidade/sobretaxa calculados no backend |

### Validações e erros

| reason | HTTP | Significado | Cenário |
|---|---|---|---|
| `forbidden_wrong_profile` | 403 | tenant de outro perfil | `/api/lavanderia/**` (`LavanderiaProfileGuard`) |
| `invalid_status` / `invalid_status_transition` | 400 / 409 | Kanban | PATCH `/orders/{id}/status` |
| `order_not_found` / `service_not_found` / `option_not_found` / `coupon_not_found` | 404 | id inexistente | CRUD/consulta |
| `service_in_use` | 409 | serviço com pedido histórico | DELETE de serviço |
| `invalid_category` | 400 | fora de `LavanderiaServiceCategory` (5 hardcoded) | POST/PATCH serviço |
| `invalid_coupon` / `duplicate_coupon` | 400 / 409 | cupom malformado / código repetido | CRUD cupom |
| `invalid_loyalty_config` | 400 | threshold/reward inválidos | PUT `/loyalty` |
| `turnaround_violation` · `below_minimum` · `address_required` · `collect_date_in_past` | (422 convenção) | prazo/mínimo/endereço/data | **só no fluxo da IA** — o handler captura e aborta sem criar (não há POST REST de pedido) |

### Notificações ao cliente

Texto fixo por status (`LavanderiaOrderStatus.notificationText`, best-effort via
`LavanderiaOrderNotifier`). Silenciosos: `aguardando` (IA já confirmou) e `em_processo` (interno).
Recusa concatena motivo. Além dos status, o `LavanderiaReminderJob` envia as mensagens proativas da
onda 1 (abaixo) — todas com toggle na config.

## Dados e snapshots

| Tabela | Constraints que são regra |
|---|---|
| `lavanderia_config` (1:1) | taxa/mínimo ≥ 0; `turnaround_days_default ≥ 0` (default 1 — só sugestão de prazo no prompt: cada serviço tem o SEU, NOT NULL); onda 1: express (`pct` 0..300 default 50, `days` 0..30 default 1, `enabled` default true), lembretes (`collect/ready` default ON, `ready_reminder_days` 1..30 default 2), reativação (`enabled` default **OFF**, `days` 7..365 default 30, cupom de retorno opcional) |
| `lavanderia_services` | preço POR PEÇA ≥ 0; `turnaround_days` NOT NULL ≥ 0; categoria CHECK (lavar, lavar_passar, lavagem_seco, passar, edredom_pesados) |
| `lavanderia_orders` | `collect_date`/`delivery_date`/`period` NOT NULL; status CHECK (8); onda 1: `discount_cents`, `coupon_id`+`coupon_code_snapshot`, `loyalty_applied`, `express`+`express_surcharge_cents`, markers `collect_reminded_date`/`ready_reminded_at`; índices parciais p/ os lembretes (`status='aguardando'` por collect_date; `status='pronto'` por status_updated_at) |
| `lavanderia_order_items` | `qty > 0`; snapshots nome/preço/**turnaround** |
| `lavanderia_coupons` / `lavanderia_loyalty_config` | clones do adega (UNIQUE lower(code); percent 1..100; threshold ≥ 1; seed idempotente) |
| `lavanderia_reactivation_log` | idempotência do disparo; `had_channel=false` = contato sem conversa (marcado sem envio) |

Cache: `LavanderiaCatalogCache` (Caffeine, **TTL 60s**), invalidado em toda mutação de
serviço/opção/config.

## Features de onda (backlog implementado — migration 103)

Tudo num único `LavanderiaReminderJob` (cron `${lavanderia.reminder-cron:0 40 9 * * *}`,
instrumentado em `scheduled_job_runs`):

- **#2 EXPRESS/24h** — regra R6; badge no Kanban.
- **#6 Cupom** e **#5 Fidelidade** — regra R7; motor comum `com.meada.common.coupons`.
- **#7 Lembrete de coleta D-1:** pedido `aguardando` com coleta amanhã → "alguém em casa?" 1x por
  data (`collect_reminded_date`; **remarcar a coleta REARMA** — marker ≠ collect_date).
- **#14 Lembrete de pronto parado:** pedido `pronto` há ≥ `ready_reminder_days` → cobra a combinação
  da entrega, 1 toque por episódio (`ready_reminded_at < status_updated_at` rearma).
- **#3 Reativação de inativo:** opt-in **DESLIGADO por default** (lição do incidente Baileys —
  ligar dispara pra base). Janela `reactivation_days` = também o cooldown (via log). Cupom de
  retorno só é citado quando resolvido E válido (active + validade + usos, no SQL da varredura).
  Contato sem conversa → marcado sem envio.

## O que NÃO existe (limites honestos)

- Retirada de balcão (sempre coleta+entrega); foto/referência de mancha (`SERVICE_ROLE_KEY`);
  garantia/laudo de remoção; pesagem real com reprecificação; etiqueta/QR por peça; assinatura
  recorrente (onda 2, chassi academia); pagamento/sinal online (#50); rastreio/motoboy; slot por
  horário fino (dia + manhã/tarde); scheduler de auto-transição de status (o job só LEMBRA, não
  transiciona).
- Divergência doc×código registrada: o guia não lista `cancelado` como notificável, mas o código
  notifica ("Seu pedido foi cancelado…").
