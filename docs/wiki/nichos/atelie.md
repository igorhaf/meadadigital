# Ateliê — regras de negócio (atelie, camada 8.14)

[← Catálogo](../05-nichos.md) · Chassi: D — proposta com aprovação em 2 fases (clone do eventos) · Guia operacional: docs/PERFIL_ATELIE.md · Migrations: 58, 81, 82, 111

## O negócio em 3 linhas

Ateliê que cria **sob encomenda personalizada** — UM perfil serve três tipos de negócio (costura sob
medida, arte, design): o tipo é um CAMPO da proposta (`project_type`), não três perfis. A IA colhe o
briefing e **abre a proposta em rascunho**; a equipe orça no painel; a IA captura a aprovação.
Escapada: a peça tem **provas/ajustes** (`atelie_fittings`) — sub-itens BINÁRIOS e ordenados.

## Jornada no WhatsApp (cenários)

1. Cliente descreve a peça/obra; a IA emite `<proposta_atelie>` → proposta nasce `rascunho`, total 0,
   sem itens, sem provas. `project_type` ausente/inválido → `'costura'`; `artisan_id`
   inválido/inativo → **reabre SEM o artesão** (a proposta ainda nasce); sem `briefing` a tag é
   descartada (best-effort, warn no log, nada acontece).
2. A equipe monta o orçamento no painel (autofill do catálogo de materiais + cupom) e move para
   `orcada` → o cliente recebe o total LÍQUIDO ("responda sim para aprovar ou não para recusar").
3. A IA emite `<aprovacao_atelie>` com a decisão → `aprovada`/`recusada`. Única mutação de estado da
   IA no funil; só com proposta `orcada` E do próprio contato (barreira).
4. A equipe registra o sinal (se cobrar), fecha (`fechada`, notifica "em breve combinamos a primeira
   prova") e marca as provas. Na véspera de cada prova o job lembra o cliente; ele confirma presença
   e a IA emite `<confirmacao_prova>` (metadado). Ao entregar (`realizada`), o pós-entrega agradece.
5. **Exceções:** fechar com sinal registrado e não pago → 409 `deposit_required`; orçar sem item →
   400 `empty_budget`; mexer em item/prova/cupom/sinal a partir de `fechada` → 409 `proposal_locked`.

## Regras de negócio

### Transacionais (invariantes duras)

- **R1 — Total recalculado do catálogo:** `total_cents` é MATERIALIZADO na mesma transação de toda
  mutação de item de orçamento; `line_total_cents = quantity × unit_price` em Java (nunca GENERATED).
- **R2 — `orcada` exige orçamento:** transição para `orcada` com `total_cents ≤ 0` → 400
  `empty_budget` (`AtelieProposalService.updateStatus`).
- **R3 — Gate de sinal:** com sinal REGISTRADO (`deposit_cents > 0`) e não pago, `aprovada→fechada`
  → 409 `deposit_required`. Sem sinal registrado (NULL/0), fechamento livre. Marcar pago sem valor
  > 0 → 400 `invalid_deposit` (registro manual do Pix até o gateway #50).
- **R4 — Trava de estado:** `itemsLocked()` a partir de `fechada` (e nos terminais) congela itens de
  orçamento, PROVAS, cupom e sinal → 409 `proposal_locked`.
- **R5 — Prova é binária e ordenada:** status CHECK `pendente|realizada` (sem enum/parity, transição
  LIVRE ⇄); `position` materializado no INSERT (`max+1`) e re-materializado 0..N no reorder, na mesma
  transação. `realizada` grava `completed_at`; voltar a `pendente` zera.
- **R6 — Cupom re-derivado:** `discount_cents` MATERIALIZADO e recalculado a cada mutação de item na
  mesma transação (percent recalcula; fixed clampa ao total; mínimo só valida no APPLY). `uses`
  incrementa ao aplicar e DEVOLVE ao remover/trocar. `lower(code)` UNIQUE por company →
  409 `duplicate_coupon` no CRUD; apply inválido → 400 `invalid_coupon` (erro EXPLÍCITO — ≠ adega).
- **R7 — Medidas com upsert:** UNIQUE `(company_id, contact_id, lower(label))` — regravar a mesma
  medida atualiza o valor. Medidas são do CONTATO (reuso na recompra), não da proposta.
- **R8 — Confirmação de prova invalida ao remarcar:** `confirmed_due_date` guarda a data confirmada;
  o UPDATE do handler só atinge prova `pendente` de proposta do CONTATO da conversa (barreira no SQL).

### Máquina de status

```
rascunho ──→ orcada ──→ aprovada ──→ fechada ──→ realizada
    │           │──→ recusada           │
    └───────────┴──→ cancelada ←────────┘   (realizada/recusada/cancelada terminais; preenchem closed_at)
```

| Transição | Quem pode | Notifica o cliente? |
|---|---|---|
| (criação) → rascunho | IA (`<proposta_atelie>`) ou tenant (POST) | não |
| rascunho → orcada | humano no painel (exige total > 0) | **sim** (total líquido + tipo da peça + "sim/não") |
| orcada → aprovada / recusada | IA (`<aprovacao_atelie>`, refletindo o cliente) ou humano | **sim** (ambas, texto fixo) |
| aprovada → fechada | humano (gate de sinal) | **sim** ("em breve combinamos a primeira prova") |
| fechada → realizada | humano | não (funil); **sim** se pós-entrega ligado (agradecimento + review) |
| qualquer não-terminal → cancelada | humano | não |

Fora do diagrama → 409 `invalid_status_transition` (`AtelieProposalStatus.allowedNext`).

### O que a IA PODE × NUNCA faz (travas da persona)

- **PODE:** abrir proposta do briefing (tipo, ocasião, medidas aproximadas, referência em texto);
  capturar aprovação/recusa de proposta ORÇADA; sugerir **UMA única vez** um complemento do catálogo
  cadastrado (forro, bordado, acabamento) — sem valores; registrar a confirmação de presença na prova.
- **NUNCA:** fecha contrato/preço/desconto; confirma PRAZO ou MEDIDA não cravada pela equipe ("vou
  confirmar na primeira prova"); inventa material/técnica/valor fora do cadastrado; promete resultado
  estético/caimento/durabilidade; gerencia ou remarca provas pela conversa (remarcação vai pra
  equipe); fala de PAGAMENTO/SINAL (valor, Pix, forma, recebimento — "a equipe combina o sinal").
  A IA **não recebe as medidas** nem as provas gerais no contexto (só as pendentes futuras do contato).

### Tags de IA

| Tag | Quando a IA emite | Campos | O que o backend descarta/recalcula |
|---|---|---|---|
| `<proposta_atelie>` | briefing colhido | `project_type`, `occasion`, `estimated_date`, `briefing`, `artisan_id`, `notes` | tipo inválido → costura; artesão inválido → abre sem; data inválida → null; total sempre 0 |
| `<aprovacao_atelie>` | cliente respondeu ao orçamento | `proposal_id`, `decisao` (aprovada\|recusada) | barreira de contato + só `orcada`; senão no-op silencioso |
| `<confirmacao_prova>` | cliente confirmou presença (lembrete D-1) | `fitting_id` | só prova `pendente` do contato; grava metadado, status não muda |

Parse por regex (nunca tool calling); tag removida antes do envio; falha → `Optional.empty()` + warn.

### Validações e erros

| reason | HTTP | Significado de negócio | Cenário que dispara |
|---|---|---|---|
| `forbidden_wrong_profile` | 403 | tenant não é atelie | `/api/atelie/**` com outro perfil |
| `empty_budget` | 400 | não se orça proposta vazia | rascunho → orcada com total 0 |
| `deposit_required` | 409 | fechada = sinal recebido | aprovada → fechada com sinal registrado não pago |
| `invalid_deposit` | 400 | sinal inconsistente | valor negativo; "pago" sem valor > 0 |
| `proposal_locked` | 409 | escopo congelado | mutar item/prova/cupom/sinal a partir de fechada |
| `invalid_status_transition` | 409 | transição proibida | realizada → orcada |
| `invalid_status` / `invalid_fitting_status` | 400 | status desconhecido | PATCH com status fora do CHECK |
| `invalid_coupon` | 400 | cupom inexistente/inativo/vencido/estourado/abaixo do mínimo | apply no painel |
| `duplicate_coupon` | 409 | `lower(code)` UNIQUE | criar código repetido |
| `invalid_measurement` | 400 | label/valor fora de 1–100 chars | PATCH de medida vazio |
| `inactive_artisan` / `artisan_not_found` | 400 / 404 | artesão inválido | atribuir artesão inativo/inexistente (painel; via tag é tolerado) |
| `artisan_in_use` | 409 | artesão com propostas | DELETE — caminho é desativar |
| `proposal_not_found` / `item_not_found` / `fitting_not_found` / `measurement_not_found` / `coupon_not_found` / `contact_not_found` | 404 | entidade de outro tenant/inexistente | id errado |
| `invalid_item` / `invalid_date` | 400 | item/data malformados | quantity ≤ 0; data não-ISO |

### Notificações ao cliente

- **Envia** em `orcada` (total líquido + tipo), `aprovada`, `fechada`, `recusada`; lembrete de prova
  D-1 ("podemos confirmar sua presença?"); pós-entrega em `realizada` (se ligado); reativação (opt-in).
- **Silêncio** em `rascunho`, `cancelada` e na notificação padrão de `realizada`; proposta manual sem
  `conversation_id` → skip (jobs marcam sem envio, não revarrem).
- Textos fixos e defensivos (sem prazo/medida/resultado prometido); best-effort via
  `AtelieProposalNotifier` — falha de envio nunca reverte a transição já persistida.

## Dados e snapshots

- **`atelie_proposals`** — snapshots `customer_name`/`customer_phone` (cliente segue sendo contact);
  `project_type` CHECK costura|arte|design (parity `AtelieProjectType` ↔ TS); `estimated_date` livre
  (SEM agenda/conflito); `total_cents`/`discount_cents` materializados; `coupon_code_snapshot`;
  `deposit_cents/paid/paid_at`; tenant só SELECT/UPDATE (INSERT via service_role).
- **`atelie_proposal_items`** — description 1–200, `quantity > 0`, preços ≥ 0, `line_total_cents`
  materializado; snapshot TEXTO (mudar o catálogo não altera propostas passadas).
- **`atelie_fittings`** — status binário, `position`, `due_date` livre, `completed_at`,
  `reminded_due_date` (idempotência do lembrete), `confirmed_at`+`confirmed_due_date` (onda 3).
- **`atelie_artisans`** (catálogo simples, sem agenda), **`atelie_catalog_items`** (delete livre, sem
  FK; inativo sai do autofill/upsell), **`atelie_coupons`**, **`atelie_measurements`**,
  **`atelie_config`** (1:1, ausência = defaults), **`atelie_reactivation_log`**.
- **Cache:** `AtelieContextCache` (Caffeine, **TTL 20s**, max 1000, keyed `(companyId, contactId)`) —
  artesãos ativos, catálogo (SÓ nomes, sem preço), propostas rascunho/orcada do contato e provas
  pendentes futuras (máx 5). Invalidado explicitamente em TODA mutação do service.

## Features de onda (backlog implementado)

- **Onda 1 (mig 81):** lembrete de prova D-1 (`AtelieFittingReminderJob`, cron default 9h) — só prova
  `pendente` com `due_date` = amanhã, proposta viva, toggle `fitting_reminder_enabled` **default
  LIGADO**; idempotência por (prova, data): remarcar REARMA. Sinal + gate (`deposit_*`, R3).
  Alerta "Entrega atrasada" derivado no painel (`estimated_date` < hoje, sem coluna).
- **Onda 2 (mig 82):** catálogo de materiais (#15), cupom no painel (#13, R6), medidas por contato
  (#9, R7), upsell único da IA (#10), relatório de faturamento (#14 — propostas REALIZADAS, valor
  líquido, por mês do `closed_at`/tipo/artesão, sem DDL).
- **Onda 3 (mig 111):** confirmação de prova (#6, R8); pós-entrega (#7 — agradecimento +
  `review_link` + indicação, toggle `post_delivery_enabled` **default ON**); reativação de inativo
  (#3 — `AtelieReactivationJob` cron 11:20, **opt-in OFF** — lição Baileys; última proposta
  REALIZADA há `reactivation_days` (7–730, default 90) sem proposta viva → 1 convite por ciclo,
  cooldown = a própria janela via `atelie_reactivation_log`).

## O que NÃO existe (limites honestos)

- Conflito de agenda/data (provas e `estimated_date` são previsões LIVRES); multi-artesão com agenda.
- Pagamento ONLINE do sinal (gateway #50 — só registro manual); contrato e-sign (o "contrato" é o
  estado `fechada`); foto/anexo de referência/croqui (bloqueador SERVICE_ROLE_KEY).
- Remarcação de prova pela IA (só a equipe); reorder de provas NÃO valida ids estranhos (ignora em
  silêncio — ≠ viagens, que dá 404).
- Guard `AtelieProfileGuard` em `/api/atelie/**`; paleta `orquidea`; tenant de teste `igorhaf25`.
