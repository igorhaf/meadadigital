# Perfil Lavanderia (lavagem com coleta e entrega agendadas) — camada 8.10

Guia operacional do tenant **lavanderia** (`profile_id='lavanderia'`). A IA atende clientes pelo
WhatsApp, conhece o catálogo de SERVIÇOS de lavagem, monta o pedido na conversa (quantidade de peças
por serviço), coleta a DATA de COLETA + período + endereço, confirma com o total E a DATA DE ENTREGA
prometida, e avisa que o pedido vai para confirmação da lavanderia (gate de aceite humano).

É o **22º perfil vertical**. CLONA o chassi do **FloriculturaBot** (pedido agendado por dia+período +
gate de aceite + catálogo + modifiers) e inaugura **DUAS DATAS ligadas por um TURNAROUND**.

## Telas (sidebar "Lavanderia")

| Tela | Rota | O que faz |
|------|------|-----------|
| Serviços | `/dashboard/lavanderia-services` | CRUD de serviços (preço por peça + turnaround_days + cuidado) + opções (modifiers). |
| Pedidos | `/dashboard/lavanderia-orders` | Kanban por status + gate de aceite; detalhe mostra as DUAS datas (coleta + entrega), período, endereço. |
| Configurações | `/dashboard/lavanderia-settings` | Taxa de entrega + pedido mínimo + turnaround default. |

## ESCAPADA — DUAS datas ligadas por um prazo de TURNAROUND

O pedido tem **COLETA** e **ENTREGA**, e elas NÃO são independentes — estão acopladas por um PRAZO:

- Cada serviço tem **`turnaround_days`** (prazo de processamento; ex.: lavar+passar=1, lavagem a seco=3,
  edredom=2).
- `collect_date` é obrigatória e **>= hoje** (fuso America/Sao_Paulo).
- **`delivery_date` é MATERIALIZADA** no INSERT: `collect_date + MAX(turnaround_days entre TODOS os
  itens)`. **MAX, não soma** — o processamento é paralelo, vale o serviço mais lento.
- Se a tag pede uma entrega **< collect + MAX(turnaround)** → **422 `turnaround_violation`**, e a resposta
  traz a **primeira data possível** (= collect + MAX). Se a tag omite delivery_date, o backend calcula e
  materializa.

`delivery_date` é materializada em Java (date + interval não é IMMUTABLE — lição end_at). period
(manhã/tarde) é o da coleta; a entrega herda. **SEMPRE coleta+entrega** (`delivery_address` obrigatório;
sem retirada de balcão — diferença pra padaria).

## Funil de status

`LavanderiaOrderStatus` ↔ TS (parity):

```
aguardando → coletado → em_processo → pronto → saiu_entrega → entregue
    │                                                  │
    └ recusado          cancelado (de qualquer não-terminal) ←┘
```

aceite = **aguardando→coletado** (gate humano — receber as peças na coleta). Notifica coletado
("recebemos suas peças"), pronto ("suas peças estão prontas"), saiu_entrega, entregue, recusado
(defensivo). aguardando silencioso.

## O que a IA NÃO faz

- **NUNCA inventa** serviço, peça, adicional ou preço fora do catálogo.
- **NUNCA aceita/recusa** o pedido — é a lavanderia no painel (gate). A IA só confirma o recebimento.
- **NUNCA promete remover mancha**, garantir resultado ou recuperar peça danificada — "a equipe avalia a
  peça na coleta, sem garantia de remoção total".
- **NUNCA promete entrega antes do prazo** (coleta + MAX(turnaround)). O total é recalculado pelo sistema.

## Tag `<pedido_lavanderia>`

```json
{ "collect_date": "YYYY-MM-DD", "period": "manha|tarde", "delivery_address": "...",
  "delivery_date": "YYYY-MM-DD|null",
  "items": [{ "service_id": "UUID", "options": ["UUID"], "qty": N }], "notes": "...|null" }
```
- `delivery_date` opcional — o backend materializa/valida. `total_cents` (se enviado) é descartado.

## O que NÃO existe nesta fase

- Foto/referência de mancha (bloqueador SERVICE_ROLE_KEY); garantia/laudo de remoção; **serviço EXPRESS/
  24h** com sobretaxa; **pesagem real** com reprecificação; etiqueta/QR por peça; assinatura recorrente;
  combo/cupom/fidelidade; pagamento real (Stripe #50); rastreio/motoboy; slot por horário fino.

## Notas técnicas

- Migration `54_lavanderia.sql` (6 tabelas). A CHECK de `companies.profile_id` ACRESCENTA `'lavanderia'`
  preservando os 22 perfis. **Lição (atelie/casamento):** 54 entra por ÚLTIMO no `SCRIPTS` do
  `AbstractIntegrationTest` (sua CHECK tem os 23 — a que reescreve por último precisa ter a lista
  completa).
- `subtotal`/`total`/`unit_price` materializados; `delivery_date` materializada (MAX em Java).
  `turnaround_snapshot` por item. Snapshots de nome/preço.
- Base de conhecimento (RAG): disponível como em todo perfil — o item "Conhecimento" do nav e a injeção
  `{{knowledge}}` do PromptBuilder valem pra lavanderia automaticamente (sem gate de feature).
- Guard `/api/lavanderia/**` → 403 `forbidden_wrong_profile`. Paleta `oceano`. Tenant: `igorhaf21`.

## Onda 1 do backlog (migration 103)

Entregue a partir de `docs/FEATURES_SUGERIDAS_LAVANDERIA.md` (#2, #3, #5, #6, #7 e #14):

- **#2 EXPRESS/24h com sobretaxa:** a config ganhou `express_enabled` (default ON),
  `express_surcharge_pct` (default 50%) e `express_turnaround_days` (default 1). Quando o cliente
  tem pressa, a IA oferece o express informando a sobretaxa DA CONFIG e emite `"express":true` na
  tag; o backend substitui o MAX(turnaround) pelos dias express na `delivery_date` materializada e
  soma a sobretaxa (`express_surcharge_cents`) ao total. Toggle off → express da tag é ignorado
  (pedido normal, defensivo). Badge EXPRESS no card do Kanban.
- **#6 CUPOM** (`lavanderia_coupons`, motor comum `com.meada.common.coupons` — subclasse fina do
  chassi adega): campo `cupom` na tag; validação (active/validade/mínimo/max_uses) e recálculo no
  backend; inválido NÃO aborta (sai sem desconto); `uses` incrementa na mesma transação. Tela
  "Cupons" + endpoints `/api/lavanderia/coupons`.
- **#5 FIDELIDADE por contagem** (`lavanderia_loyalty_config`, clone adega): a cada
  `threshold_orders` pedidos ENTREGUES do contato, o próximo ganha o reward (percent/fixed),
  clampado ao subtotal junto com o cupom. Tela "Fidelidade" + `GET/PUT /api/lavanderia/loyalty`.
- **#7 LEMBRETE DE COLETA D-1:** pedido `aguardando` com coleta amanhã → "alguém em casa?" 1x por
  data (`collect_reminded_date`; remarcar REARMA). Toggle `collect_reminder_enabled` (default ON).
- **#14 LEMBRETE DE PRONTO PARADO:** pedido em `pronto` há `ready_reminder_days` (default 2) →
  cobra a combinação da entrega, 1 toque por episódio (`ready_reminded_at` vs `status_updated_at`).
  Toggle `ready_reminder_enabled` (default ON).
- **#3 REATIVAÇÃO de inativo** (`lavanderia_reactivation_log`, clone sushi): opt-in
  `reactivation_enabled` **DESLIGADO por default** (lição Baileys — ligar dispara pra base);
  janela `reactivation_days` (default 30) é também o cooldown; cupom de retorno opcional
  (`reactivation_coupon_code`) citado só quando ativo/válido; sem conversa → marca sem envio.

Tudo num único `LavanderiaReminderJob` (cron `${lavanderia.reminder-cron:0 40 9 * * *}`,
instrumentado no `scheduled_job_runs`, métodos públicos testáveis). O total do pedido agora é
`subtotal − desconto + taxa + sobretaxa express`, tudo materializado em Java. Settings ganhou as
seções "Serviço express" e "Automações". Teste: `LavanderiaOnda1IntegrationTest` (5 cenários).

**Fica pra onda 2** (registrado, não pedido): #1 assinatura mensal (chassi academia; cobrança
manual até o gateway #50), #4 pagamento/sinal online (bloqueado por #50), #8 pesagem real com
reprecificação, #9/#10 rastreio e etiqueta QR, #11 campanha em massa, #16 multi-unidade.
