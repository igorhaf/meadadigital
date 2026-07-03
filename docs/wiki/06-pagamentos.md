# 06 — Pagamentos (estado atual e onde paramos)

[← Home](00-HOME.md)

> **Resumo honesto do estado atual (verificado no código em 2026-06-27):** o Meada **não tem
> gateway de pagamento integrado** (Stripe/Pix/MercadoPago/etc.). O que existe é **registro
> manual de mensalidade** em 3 nichos de assinatura (academia, escola, cursos). A integração com
> um PSP real é a pendência **#50 "Cobrança recorrente"**, ainda **não implementada**.

## Verificação

Levantamento empírico no repositório:

- Busca por `stripe|pix|gateway|payment_intent|checkout|billing|mercadopago|asaas` no backend →
  **nenhuma integração de PSP**; só os módulos de registro manual abaixo.
- `git status` limpo, sem branch de gateway, sem arquivo de pagamento modificado nas últimas 48h,
  sem módulo top-level `payments/`/`billing/`.
- Migrations só têm `*_payments` de mensalidade (academia/escola/cursos), todas com o comentário
  explícito *"SEM cobrança automática (Stripe é #50, futuro)"*.

> Se você lembra de "a última interação foi implementando o gateway de pagamento", esse trabalho
> **não está neste repositório** (pode ter sido em outra janela/sessão cujo resultado não foi
> commitado aqui, ou planejamento). O estado atual versionado é o descrito nesta página.

## O que existe: registro manual de mensalidade

Padrão replicado em **academia (7.7)**, **escola (8.19)** e **cursos (8.20)** — os 3 nichos de
**assinatura** (ver chassi #4 em [04 — Chassis](04-multiperfil-chassis.md)).

### Modelo de dados (idêntico nos 3)

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
created_at      timestamptz
UNIQUE ({membership_id | enrollment_id}, reference_month)   -- 1 pagamento por mês
```

### Endpoints (por nicho)

| Nicho | Endpoints |
|---|---|
| Academia | `GET/POST/DELETE /api/academia/memberships/{id}/payments` |
| Escola | `GET/POST/DELETE /api/escola/enrollments/{id}/payments` |
| Cursos | `GET/POST/DELETE /api/cursos/enrollments/{id}/payments` |

- **GET** → `{ items: Payment[], summary: { lastPaidMonth, monthsOpen, totalPayments } }`.
- **POST** `{ referenceMonth, amountCents, method, notes }` → 201 · **400** se a matrícula não está
  ativa · **409** `duplicate_payment` (UNIQUE do mês).
- **DELETE** → 204.

### Fluxo (100% manual)

1. Operador abre a matrícula/inscrição ativa no painel.
2. Clica "Registrar pagamento": mês + valor + forma (texto) + notas.
3. Backend valida (matrícula ativa, sem duplicata no mês, valor ≥ 0) e persiste.
4. A listagem mostra o histórico + um **resumo** (último mês pago + meses em aberto = meses
   decorridos desde o início − pagamentos registrados).

**Não há:** cobrança automática, webhook de PSP, validação de cartão/Pix, boleto, juros/multa por
inadimplência, nem notificação automática "sua mensalidade venceu".

### Arquivos

```
supabase/migrations/36_academia.sql   (academia_payments)
supabase/migrations/63_escola.sql     (escola_payments)
supabase/migrations/64_cursos.sql     (cursos_payments)

src/main/java/com/meada/profiles/{academia|escola|cursos}/payments/
    {Nicho}Payment.java | {Nicho}PaymentService.java
    {Nicho}PaymentRepository.java | {Nicho}PaymentController.java

frontend/lib/api/{academia|escola|cursos}/payments.ts
frontend/app/(protected)/dashboard/cursos-payments/page.tsx   (tela de exemplo)
```

Testado: `{Nicho}PaymentServiceTest` + integration tests (cobrem record + summary, 409 de duplicata,
matrícula cancelada → 400).

## Pendência #50 — gateway integrado (próximo passo)

Marcado **PENDENTE** no roadmap (`docs/RELATORIO_POS_MARATONA_2026-06-16.md`, seção 3) e em todas as
migrations de pagamento. Bloqueios declarados: **escolha do PSP** + **segredo (chave de API)** +
**decisão de pricing**.

Esboço do que falta (referência para quando #50 for atacada):

- **Schema novo:** `payment_integrations` (PSP + chave por company), `payment_methods` (cartão
  salvo), `payment_intents` (status da cobrança), `payment_webhooks` (eventos do PSP). Colunas
  novas nos `*_payments` (`provider_intent_id`, `payment_status`, `retry_count`, `next_retry_at`).
- **Backend:** módulo `payments/` com `PaymentGatewayProvider` (interface) + `StripeGateway` (impl),
  `StripeWebhookController` (inbound do PSP), `RecurringPaymentJob` (scheduler mensal),
  `PaymentRetryService` (backoff).
- **Config (.env):** `STRIPE_API_KEY_SECRET`, `STRIPE_WEBHOOK_SECRET`, `STRIPE_PUBLISHABLE_KEY`.
- **Frontend:** Stripe Elements para método de pagamento; tela de config do PSP por empresa.
- **Lógica:** matrícula ativa gera intent mensal → job diário cria/envia → webhook confirma/falha →
  retry com backoff → notifica o cliente (WhatsApp) com link de pagamento.

> Os outros nichos com valor monetário (todos os order-based e propostas) hoje **registram o total**
> mas **não cobram** — pagamento é combinado fora do app. A integração de PSP, quando vier, é
> transversal e habilita cobrança nesses fluxos também.

## Conclusão — onde paramos

| Aspecto | Estado |
|---|---|
| Registro manual de mensalidade (academia/escola/cursos) | ✅ completo e testado |
| Gateway integrado (Stripe/Pix) | ❌ não implementado (pendência #50) |
| Cobrança automática / webhook PSP / inadimplência | ❌ não existe |
| **Ponto de parada real** | registro manual mês a mês; integração de PSP é o próximo passo, ainda não iniciado neste repo |
