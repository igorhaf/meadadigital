# Casamento — regras de negócio (casamento, camada 8.7)

[← Catálogo](../05-nichos.md) · Chassi: D — proposta com aprovação em 2 fases (clone do eventos) · Guia operacional: docs/PERFIL_CASAMENTO.md · Migrations: 51, 84, 113

## O negócio em 3 linhas

Assessoria/cerimonial de casamento — a equipe COORDENA o casamento (não é fornecedor isolado). A IA
atende os noivos, abre a proposta a partir do briefing e captura a aprovação do orçamento. Escapada:
a **TERCEIRA sub-entidade** no mesmo artefato — o **checklist pré-casamento** com prazo
(`wedding_checklist_tasks`, `done` boolean + `due_date` NULLS LAST) — ao lado do orçamento e do
cronograma do dia. O plano de **sinal + parcelas** (`wedding_payments`) gateia o fechamento.

## Jornada no WhatsApp (cenários)

1. Noivos descrevem o sonho; a IA emite `<proposta_casamento>` → proposta nasce `rascunho`, total 0,
   sem sub-itens. `planner_id` inválido/inativo → **reabre SEM o assessor**; sem `briefing` a tag é
   descartada (best-effort). A IA pode apresentar pacotes/adicionais com o preço DO CATÁLOGO e
   sugerir UMA vez um adicional.
2. A equipe orça no painel (autofill do catálogo + cupom) e move para `orcada` → os noivos recebem o
   total LÍQUIDO ("responda sim para aprovar ou não para recusar"). Proposta na mesma `wedding_date`
   de outra aprovada/fechada/realizada ganha badge "Data ocupada" — INFORMATIVO, sem 409.
3. A IA emite `<aprovacao_casamento>` → `aprovada`/`recusada` (só proposta `orcada` do próprio contato).
4. A equipe monta o PLANO DE PAGAMENTO (sinal + N parcelas) e marca pago à mão. Com 'sinal' não pago
   no plano, `aprovada→fechada` → 409 `deposit_required` ("fechada = sinal recebido").
5. Depois de `fechada`: checklist e parcelas seguem a vida — o `WeddingReminderJob` avisa D-3 de
   tarefa não concluída e de parcela não paga. Passada a `wedding_date`, o job move para `realizada`
   (silencioso no funil; pós-casamento agradece se ligado). 1 ano depois, parabéns de aniversário.
6. **Exceções:** orçar sem item → 400 `empty_budget`; mutar sub-item após `fechada` → 409
   `proposal_locked`; orçamento parado em `orcada` há `follow_up_days` → 1 toque gentil por episódio.

## Regras de negócio

### Transacionais (invariantes duras)

- **R1 — Total recalculado do orçamento:** `total_cents` MATERIALIZADO na mesma transação de toda
  mutação de item; `line_total_cents = quantity × unit_price` calculado dos valores FINAIS em Java
  (não na SET clause — Postgres usaria valores antigos da linha).
- **R2 — `orcada` exige orçamento:** total ≤ 0 → 400 `empty_budget` (sobre o total BRUTO).
- **R3 — Gate de sinal por PLANO:** `existsUnpaidSinal` — linha `kind='sinal'` não paga em
  `wedding_payments` bloqueia `aprovada→fechada` → 409 `deposit_required`. Sem sinal no plano,
  fechamento livre. (≠ atelie/viagens, onde o sinal é coluna da proposta.)
- **R4 — Trava assimétrica:** `itemsLocked()` a partir de `fechada` congela os TRÊS sub-itens
  (orçamento, cronograma, checklist) → 409 `proposal_locked`; o PLANO DE PAGAMENTO segue MUTÁVEL
  depois de `fechada` (parcelas vencem até o casamento) — só `recusada`/`cancelada` o travam.
- **R5 — Checklist binário ordenado por prazo:** `done` boolean (sem máquina/parity); toggle seta
  `done_at` e zera ao desfazer; leitura `due_date asc NULLS LAST, created_at asc` (sem prazo vai ao fim).
- **R6 — Parcela válida:** `kind` CHECK sinal|parcela, `amount_cents > 0` (CHECK + service) → 400
  `invalid_payment`; parcela órfã de outra proposta → 404 `payment_not_found`.
- **R7 — Cupom re-derivado:** desconto MATERIALIZADO e recalculado a cada mutação de item na mesma
  transação; `uses` devolve ao remover/trocar; `lower(code)` UNIQUE → 409 `duplicate_coupon`; apply
  inválido → 400 `invalid_coupon`. Notificação de `orcada` usa o total LÍQUIDO.
- **R8 — `dateBusy` derivado:** EXISTS na leitura (outra proposta aprovada/fechada/realizada na mesma
  `wedding_date`) — alerta informativo, a decisão de aceitar 2 casamentos na data é da equipe.

### Máquina de status

```
rascunho ──→ orcada ──→ aprovada ──→ fechada ──→ realizada
    │           │──→ recusada           │
    └───────────┴──→ cancelada ←────────┘   (realizada/recusada/cancelada terminais; preenchem closed_at)
```

| Transição | Quem pode | Notifica o cliente? |
|---|---|---|
| (criação) → rascunho | IA (`<proposta_casamento>`) ou tenant (POST) | não |
| rascunho → orcada | humano no painel (exige total > 0) | **sim** (total líquido + estilo + "sim/não") |
| orcada → aprovada / recusada | IA (`<aprovacao_casamento>`) ou humano | **sim** (ambas, texto fixo) |
| aprovada → fechada | humano (gate do sinal, R3) | **sim** ("seu casamento está confirmado") |
| fechada → realizada | humano; sistema (`auto_complete_enabled`, `wedding_date` passada) | não (funil); **sim** se pós-casamento ligado |
| qualquer não-terminal → cancelada | humano | não |

Fora do diagrama → 409 `invalid_status_transition` (`WeddingProposalStatus.allowedNext`).

### O que a IA PODE × NUNCA faz (travas da persona)

- **PODE:** abrir proposta do briefing (data prevista, convidados, estilo); capturar aprovação/recusa
  de proposta ORÇADA; apresentar pacotes/adicionais com preço OFICIAL do catálogo (+1 sugestão única
  de adicional); **INFORMAR o plano de pagamento** exatamente como está no contexto (valores,
  vencimentos, pago/em aberto).
- **NUNCA:** fecha contrato/preço/desconto; confirma disponibilidade de data não confirmada ("vou
  verificar com a equipe"); inventa item/valor/fornecedor; promete estrutura não informada ou
  "casamento perfeito"; gerencia cronograma do dia ou checklist pela conversa; confirma recebimento
  de pagamento, negocia condição ou informa chave Pix.

### Tags de IA

| Tag | Quando a IA emite | Campos | O que o backend descarta/recalcula |
|---|---|---|---|
| `<proposta_casamento>` | briefing colhido | `wedding_style`, `wedding_date`, `guest_count`, `briefing`, `planner_id`, `notes` | assessor inválido → abre sem; data inválida → null; total sempre 0 |
| `<aprovacao_casamento>` | noivos responderam ao orçamento | `proposal_id`, `decisao` (aprovada\|recusada) | barreira de contato + só `orcada`; senão no-op silencioso |

Parse por regex (nunca tool calling); tag removida antes do envio; falha → `Optional.empty()` + warn.
Não há tag para cronograma, checklist nem pagamento — são do painel.

### Validações e erros

| reason | HTTP | Significado de negócio | Cenário que dispara |
|---|---|---|---|
| `forbidden_wrong_profile` | 403 | tenant não é casamento | `/api/casamento/**` com outro perfil |
| `empty_budget` | 400 | não se orça proposta vazia | rascunho → orcada com total 0 |
| `deposit_required` | 409 | fechada = sinal recebido | fechar com 'sinal' não pago no plano |
| `invalid_payment` | 400 | parcela inconsistente | kind fora de sinal\|parcela; amount ≤ 0; sem due_date |
| `proposal_locked` | 409 | escopo congelado (R4) | sub-item após fechada; parcela após recusada/cancelada |
| `invalid_status_transition` / `invalid_status` | 409 / 400 | máquina de status | transição proibida; status desconhecido |
| `invalid_coupon` / `duplicate_coupon` | 400 / 409 | cupom inválido / código repetido | apply no painel; CRUD |
| `invalid_guest_count` | 400 | convidados negativo | guest_count < 0 |
| `invalid_date` / `invalid_time` | 400 | data/hora malformada | wedding_date/due_date não-ISO; start_time do cronograma |
| `inactive_planner` / `planner_not_found` | 400 / 404 | assessor inválido | atribuir inativo/inexistente (painel) |
| `planner_in_use` | 409 | assessor com propostas | DELETE — caminho é desativar |
| `proposal_not_found` / `item_not_found` / `timeline_item_not_found` / `checklist_task_not_found` / `payment_not_found` / `coupon_not_found` | 404 | entidade de outro tenant/inexistente | id errado |
| `invalid_item` | 400 | item malformado | quantity ≤ 0, preço negativo |

### Notificações ao cliente

- **Envia** em `orcada`/`aprovada`/`fechada`/`recusada`; lembrete D-3 de checklist ("prazo até
  dd/MM") e de parcela ("vence em dd/MM — se já acertou, desconsidere"); pós-casamento em `realizada`
  (se ligado); parabéns de aniversário 1x/ano; follow-up de orçamento parado.
- **Silêncio** em `rascunho`, `cancelada` e na auto-realizada (quem casou não recebe aviso
  burocrático); proposta manual sem `conversation_id` → jobs marcam sem envio (não revarrem).
- Textos fixos e defensivos, sem "casamento perfeito"; best-effort via `WeddingProposalNotifier` —
  falha de envio nunca reverte transição.

## Dados e snapshots

- **`wedding_proposals`** — snapshots `customer_name` (pode ser "Ana & João")/`customer_phone`;
  `wedding_date` campo LIVRE (sem conflito duro); `guest_count ≥ 0` (CHECK); `total_cents`/
  `discount_cents` materializados; `coupon_code_snapshot`; `anniversary_notified_year`;
  `follow_up_sent_at`; tenant só SELECT/UPDATE (INSERT via service_role).
- **3 sub-entidades que NÃO se misturam:** `wedding_proposal_items` (DINHEIRO, entra no total),
  `wedding_timeline_items` (a HORA das coisas no dia, `start_time`, fora do total),
  `wedding_checklist_tasks` (o que FALTA fazer, `due_date`+`done`, fora do total,
  `reminded_due_date` para o lembrete D-3).
- **`wedding_payments`** — `kind` sinal|parcela, `amount_cents > 0`, `paid`/`paid_at`,
  `reminded_due_date`; índice parcial `due_date WHERE paid = false` (varredura do job).
- **`wedding_planners`**, **`wedding_catalog_items`** (kind pacote|adicional, delete livre),
  **`wedding_coupons`**, **`wedding_config`** (1:1; ausência = defaults, toggles coalesce true).
- **Cache:** `CasamentoContextCache` (Caffeine, **TTL 20s**, keyed `(companyId, contactId)`) —
  assessores, catálogo COM preços, propostas rascunho/orcada do contato E o plano de pagamento das
  aprovadas/fechadas. NÃO injeta cronograma nem checklist. Invalidado em toda mutação (inclusive de
  parcela).

## Features de onda (backlog implementado)

- **Onda 1 (mig 84):** plano sinal+parcelas com gate (#1, R3/R4); `WeddingReminderJob` (cron default
  9h30) — janela `due_date ≤ hoje+3` (pega atrasados se o job esteve fora), idempotência por (linha,
  data), remarcar REARMA, toggles `checklist_reminder_enabled`/`payment_reminder_enabled` **default
  ON**; catálogo pacotes/adicionais (#3); `WeddingAutoTransitionJob` (cron default 8h) — auto-realizada
  (#4, toggle `auto_complete_enabled` ON) + aniversário (#16, `anniversary_notified_year`, toggle
  `anniversary_enabled` ON); cupom (#10, R7); relatórios (#14, sem DDL); `dateBusy` (#15, R8).
- **Onda 2 (mig 113):** pós-casamento (#6) — `realizada` encadeia agradecimento + `review_link` +
  convite de indicação (toggle `post_event_enabled` **ON**); follow-up de orçamento parado (#8) —
  `orcada` há `follow_up_days` (1–60, default 5) sem mudança → 1 toque por episódio
  (`follow_up_sent_at` < `status_updated_at` REARMA; re-orçar rearma), toggle `follow_up_enabled` ON.

## O que NÃO existe (limites honestos)

- Conflito DURO de data (o `dateBusy` é alerta; 409 de data não existe — a assessoria decide).
- Cobrança ONLINE do sinal/parcelas (gateway #50 — registro manual + gate + lembrete já existem);
  contrato e-sign; inadimplência automática.
- Fornecedores externos com agenda própria; lista de convidados/RSVP/mesa; mood board/foto
  (bloqueador SERVICE_ROLE_KEY); indicação com recompensa/NPS estruturado (motor de campanha).
- Guard `CasamentoProfileGuard` em `/api/casamento/**`; paleta `trigo`; tenant de teste `igorhaf18`.
