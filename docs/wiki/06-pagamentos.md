# 06 — Pagamentos (estado atual e onde paramos)

[← Home](00-HOME.md)

> **Resumo honesto do estado atual (re-verificado no código em 2026-08-09):** o Meada **ainda não
> tem gateway de pagamento integrado em código** — nenhuma chamada a PSP (Mercado Pago/Stripe/Pix)
> existe no backend. O que mudou desde a verificação anterior (2026-06-27): (1) a **direção do PSP
> foi cravada — Mercado Pago Preapproval** — e o **schema da assinatura do produto já existe**
> (`118_subscriptions.sql`), mas é **só a tabela** (zero código Spring/Next); (2) o registro
> **manual** de pagamento cresceu: mensalidade em 3 nichos + **sinal/parcelas com gate** em 5
> nichos; (3) nasceram **réguas de inadimplência** (lembrete + auto-suspensão) em academia e
> escola — tudo lembrete/suspensão local, **sem cobrança real**. A integração continua sendo a
> pendência **#50**.

## Verificação (2026-08-09)

Levantamento empírico no repositório:

- `grep preapproval|mercadopago|subscription` em `src/main/java/` → **nenhum arquivo**. A única
  referência de código a `subscriptions` está nos testes de infra (`AbstractIntegrationTest`
  aplica a migration 118 no schema de teste; `MigrationScriptsCompletenessTest` garante que ela
  não fique de fora silenciosamente).
- `supabase/migrations/118_subscriptions.sql` **existe** (commit `9043b39`, 2026-07-12) — ver
  seção abaixo.
- Frontend: **não há** tela/CTA de checkout ou "assinar" (`grep assinar|subscribe|checkout` em
  `frontend/` → nada). O que existe de pricing é **vitrine**: as páginas institucionais por nicho
  (`meadadigital.com/{nicho}`, CMS da company-âncora) exibem um bloco `packages` com **preço por
  nicho** (R$ 147–497/mês conforme o segmento — commit `3b0760f`), sem botão que cobre.
- Os `MercadoPagoService.php` que existiram no monorepo eram dos **projetos externos** (muda,
  pindorama, semente-doce — Laravel), que **saíram do repositório** (commits `414b8cb`/`09c92c5`).
  Se você lembra de "implementando o gateway", era nesses projetos — **não no backend do Meada**.

## Migration 118 — assinaturas do produto (schema pronto, código pendente)

`supabase/migrations/118_subscriptions.sql` cria `public.subscriptions`: a assinatura **do
produto Meada** (alguém assina o plano de um nicho no card da página institucional), desenhada
para **Mercado Pago Preapproval** (recorrência mensal transparente):

```
id                 uuid PK
mp_preapproval_id  text UNIQUE      -- id da assinatura no Mercado Pago
payer_email        text NOT NULL
profile_id         text NOT NULL    -- nicho assinado (HARDCODED em ProfileType; validação app-level)
amount_cents       integer > 0
currency           text default 'BRL'
status             pending | authorized | paused | cancelled   -- espelha o ciclo do MP via webhook
external_reference text             -- referência idempotente nossa
last_charge_at     timestamptz
```

- Tabela de **PLATAFORMA** (não-tenant): RLS enable+force, grant só `service_role` — o público
  interagiria via endpoints `/public/**` do Spring.
- **O que NÃO existe ainda** (o desenho está no comentário da própria migration): client do
  `POST /preapproval` do MP, webhook do MP atualizando `status`, endpoint público do CTA do card
  de plano, tela/fluxo no frontend, envs de credencial do MP, e o vínculo
  assinatura → provisionamento de company.

## O que existe: registro manual

### 1. Mensalidade (academia / escola / cursos)

Padrão replicado nos 3 nichos de **assinatura** (chassi E — ver [04 — Chassis](04-multiperfil-chassis.md)).

Tabela `{nicho}_payments` (`36_academia.sql`, `63_escola.sql`, `64_cursos.sql`):

```
id              uuid PK
company_id      uuid  (FK companies)
{membership_id | enrollment_id}  uuid  (FK; vínculo com a matrícula)
reference_month date  (sempre dia 01 do mês de referência)
paid_at         timestamptz default now()
amount_cents    integer >= 0
method          text   (texto livre: "Pix", "dinheiro", "transferência")
notes           text
UNIQUE ({membership_id | enrollment_id}, reference_month)   -- 1 pagamento por mês
```

| Nicho | Endpoints |
|---|---|
| Academia | `GET/POST/DELETE /api/academia/memberships/{id}/payments` |
| Escola | `GET/POST/DELETE /api/escola/enrollments/{id}/payments` |
| Cursos | `GET/POST/DELETE /api/cursos/enrollments/{id}/payments` |

- **GET** → `{ items, summary: { lastPaidMonth, monthsOpen, totalPayments } }`.
- **POST** → 201 · **400** matrícula não-ativa · **409** `duplicate_payment` (UNIQUE do mês).
- **DELETE** → 204.

### 2. Sinal + parcelas do contrato (casamento) — novo

`wedding_payments` (migration 84): a equipe monta o plano de pagamento (sinal + N parcelas com
vencimento) **no painel**, no detalhe da proposta.

- `GET/POST/PATCH/DELETE /api/casamento/proposals/{id}/payments[/{paymentId}]` +
  `PATCH .../payments/{paymentId}/paid` (marcar pago à mão).
- `kind ∈ {sinal, parcela}` (default `parcela`).
- **Gate:** `aprovada→fechada` com sinal no plano **não pago** → **409 `deposit_required`**
  ("fechada = sinal recebido"); sem sinal no plano, fechamento livre. O plano segue **mutável**
  após fechada (parcelas vencem até o casamento); só recusada/cancelada travam
  (409 `proposal_locked`).
- `WeddingReminderJob` lembra parcela em aberto (texto fixo, não passa pela IA).

### 3. Sinal/entrada com gate (atelie, viagens, papelaria, padaria) — novo

Colunas `deposit_cents / deposit_paid / deposit_paid_at` direto na proposta/pedido
(migrations 81, 87, 95, 96):

| Nicho | Endpoint de registro | Gate bloqueado com sinal registrado (>0) e não pago |
|---|---|---|
| Atelie | `PATCH /api/atelie/proposals/{id}/deposit` | `aprovada→fechada` → 409 `deposit_required` |
| Viagens | `PATCH /api/viagens/proposals/{id}/deposit` | idem (fechamento da proposta) |
| Papelaria | `PATCH /api/papelaria/orders/{id}/deposit` | avanço do pedido → 409 `deposit_required` |
| Padaria | `PATCH /api/padaria/orders/{id}/deposit` | aceite `aguardando→em_preparo` → 409 `deposit_required` |

- A equipe registra o valor e marca "recebido" ao confirmar o Pix **à mão**; valor inválido →
  400 `invalid_deposit`; o sinal congela junto com os itens a partir de fechada.
- A IA **informa** o valor do sinal (via contexto/cache) mas **nunca** cobra, passa chave Pix
  nem confirma recebimento — trava de persona preservada.

### 4. Réguas de inadimplência (lembrete local, sem cobrança) — novo

- **Academia** (`AcademiaInadimplenciaJob`, migration 72): cron diário varre matrículas ativas,
  reusa `AcademiaPaymentService.summary()` (fonte única de `monthsOpen`) e (a) envia **um**
  lembrete de vencimento por mês de referência (idempotência via `overdue_notified_month`,
  respeitando `grace_days`); (b) se o tenant ligou `auto_suspend_days`, **suspende** a matrícula
  após o atraso-limite (transição validada; suspensa MANTÉM a vaga; o job nunca cancela).
  Política por tenant em `academia_config`, opt-in (default não suspende).
- **Escola** (dentro do `EscolaReminderJob`, migration 109): régua de mensalidade **opt-in OFF**
  (`escola_config.payment_reminder` + `payment_due_day`): matrícula ativa sem pagamento do mês
  após o vencimento → **1 toque por mês** (`payment_reminded_month`), valor snapshot, sem
  multa/juros.
- Ambos honram `EVOLUTION_DRY_RUN` (via `EvolutionSender`) e seguem o molde
  `ReminderJob`/`ReactivationJob`.

**Continua não existindo:** cobrança automática, webhook de PSP, link de pagamento, validação de
cartão/Pix, boleto, juros/multa.

## Pendência #50 — gateway integrado (próximo passo)

Continua **PENDENTE** (`docs/BACKLOG_EXECUCAO.md`: ~65 features, ~12% do backlog, bloqueadas
pelo gateway; "desenho na Onda 1, chamada real na Onda 4"). O que mudou: a direção **deixou de
ser Stripe** — o desenho cravado na migration 118 é **Mercado Pago Preapproval** (assinatura
transparente, cobrança mensal). Bloqueios: credencial do MP + decisão final de pricing.

O que falta para fechar (referência):

- **Backend:** módulo de assinaturas sobre `subscriptions` — client `POST /preapproval` do MP,
  webhook do MP (atualiza `status`/`last_charge_at`; `external_reference` idempotente),
  endpoint `/public/**` para o CTA do card de plano, vínculo assinatura → provisionamento
  de company.
- **Config (.env):** access token do Mercado Pago + secret do webhook (nomes a definir).
- **Frontend:** CTA "assinar" nos cards de preço das páginas de nicho do CMS.
- **Depois:** estender cobrança aos fluxos dos tenants (mensalidades, sinais e pedidos que hoje
  são registro manual) — é transversal.

## Conclusão — onde paramos

| Aspecto | Estado |
|---|---|
| Mensalidade manual (academia/escola/cursos) | ✅ completo e testado |
| Sinal/parcelas manuais com gate `deposit_required` (casamento/atelie/viagens/papelaria/padaria) | ✅ completo e testado |
| Régua de inadimplência local (academia/escola) | ✅ opt-in, lembrete + auto-suspensão, sem cobrança |
| Pricing público por nicho (CMS institucional) | ✅ vitrine (bloco `packages`), sem checkout |
| Schema de assinatura do produto (`subscriptions`, MP Preapproval) | ✅ migration 118 aplicada — **só o schema** |
| Código do gateway (client MP + webhook + CTA) | ❌ não implementado (pendência #50) |
| **Ponto de parada real** | dinheiro é 100% registrado à mão; o desenho do PSP (Mercado Pago) está cravado no schema, a integração ainda não começou |
