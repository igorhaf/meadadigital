# AcademiaBot — regras de negócio (academia, camada 7.7)

[← Catálogo](../05-nichos.md) · Chassi: E — assinatura (avô) · Guia operacional: docs/PERFIL_ACADEMIA.md · Migrations: 36, 72, 73, 74, 75, 76, 77, 78, 79

## O negócio em 3 linhas

A academia/studio vende **planos mensais** (assinatura ativa-até-cancelar) com vagas em **aulas semanais
recorrentes** de capacidade limitada. O cliente final é o próprio aluno no WhatsApp. A IA mostra planos e
aulas com vaga em tempo real e **matricula**; pagamento, presença, fila, cupom e fidelidade são do painel.

## Jornada no WhatsApp (cenários)

1. Aluno pede matrícula → IA mostra planos ativos + aulas com vagas restantes (contexto do
   `AcademiaContextCache`, TTL 60s) e confirma plano + aula(s) + nome.
2. Na confirmação a IA emite `<matricula>{plan_id, class_ids[], student_name?, notes?}`; o
   `MatriculaConfirmHandler` cria a matrícula (status `ativa`), o `OutboundService` remove a tag e o
   aluno recebe a mensagem de boas-vindas.
3. **Aula lotada:** a vaga é re-verificada dentro da transação; a tag falha em silêncio (warn no log) —
   no painel o mesmo caso devolve 409 `class_full` com `classId`/`className` no body.
4. **Já matriculado:** o contexto informa a matrícula ativa (a IA não oferece outra); se a tag vier mesmo
   assim, o anti-dupla barra (`already_active` no painel; silêncio best-effort na tag).
5. Automations: lembrete de mensalidade em aberto (1/mês), auto-suspensão opcional, saudação de
   aniversário 1×/ano. Fila de espera, day-pass, cupom, indicação e pontos são operados pelo painel.

## Regras de negócio

### Transacionais (invariantes duras)

- **R1 — Vaga por aula:** `count(matrículas não-canceladas na aula) + 1 <= capacity`, re-verificado
  **dentro da transação** do INSERT (`AcademiaMembershipRepository.insertMembership`) → 409 `class_full`.
  **Suspensa OCUPA vaga** (o count filtra apenas `status <> 'cancelada'`).
- **R2 — Anti-dupla:** 1 matrícula ATIVA por contato (pré-check no service + UNIQUE parcial
  `uniq_active_membership_per_contact (company_id, contact_id) where status='ativa'`) → 409 `already_active`.
- **R3 — Pagamento mensal único:** UNIQUE `(membership_id, reference_month)` → 409 `duplicate_payment`;
  pagamento só em matrícula `ativa` → 400 `membership_not_active`. Registro manual (sem gateway).
- **R4 — Cancelamento materializa:** `end_date = hoje` (America/Sao_Paulo) escrito em Java no UPDATE;
  cancelar LIBERA as vagas (consequência do filtro do R1).
- **R5 — Check-in único:** UNIQUE `(membership_id, class_id, checkin_date)` → 409 `duplicate_checkin`;
  crédito de fidelidade só se `academia_loyalty_config.enabled` (opt-in).
- **R6 — Cupom:** código único por company **case-insensitive** (UNIQUE `(company_id, lower(code))`) →
  409 `duplicate_coupon`; validação = ativo + validade + mínimo + usos, desconto com **clamp ao subtotal**.
- **R7 — Snapshots:** a matrícula congela `plan_name`/`plan_monthly_cents`/`student_name`/`student_phone`;
  a junction congela nome/dia/hora/duração/modalidade da aula. Editar plano/aula NÃO altera matrículas.

### Máquina de status

```
ativa  <──────>  suspensa
  │                 │
  └────> cancelada <┘   (terminal; end_date materializado)
```

| Transição | Quem pode | Notifica o cliente? |
|---|---|---|
| (criação) → ativa | IA (tag) ou humano (POST painel) | Sim (boas-vindas com o plano) |
| ativa → suspensa | humano (painel) ou sistema (auto-suspensão do job) | Não (silenciosa) |
| suspensa → ativa | humano | Sim (retomada = texto de ativa) |
| ativa/suspensa → cancelada | humano | Sim (despedida defensiva) |

Transição fora do `AcademiaMembershipStatus.allowedNext()` → 409 `invalid_status_transition`.

### O que a IA PODE × NUNCA faz (travas da persona)

- **PODE:** apresentar planos/aulas/vagas cadastrados, confirmar e matricular (tag).
- **NUNCA:** prescreve treino, dieta ou avaliação física (não é educador físico); promete resultado
  corporal; julga esteticamente. Nunca muda status de matrícula, registra pagamento, credita ponto,
  aplica cupom ou chama a fila — tudo isso é humano/painel.

### Tags de IA

| Tag | Quando a IA emite | Campos | O que o backend descarta/recalcula |
|---|---|---|---|
| `<matricula>` | ao confirmar plano + aula(s) + nome | `plan_id`, `class_ids[]`, `student_name?` (fallback: nome/telefone do contato), `notes?` | qualquer preço — a mensalidade é snapshotada DO PLANO; vaga e anti-dupla revalidados |

### Validações e erros

| reason | HTTP | Significado de negócio | Cenário |
|---|---|---|---|
| `forbidden_wrong_profile` | 403 | tenant de outro perfil | bater `/api/academia/**` sem `profile_id='academia'` |
| `class_full` | 409 | aula sem vaga (body traz `classId`/`className`) | R1 estourou na transação |
| `already_active` | 409 | contato já tem matrícula ativa | R2 |
| `invalid_status_transition` | 409 | transição fora da máquina | ex.: reativar cancelada |
| `duplicate_payment` | 409 | mês de referência já pago | R3 |
| `duplicate_checkin` | 409 | presença já registrada no dia | R5 |
| `plan_in_use` / `class_in_use` | 409 | excluir plano/aula com matrícula | preferir desativar |
| `already_waiting` | 409 | contato já está na fila daquela aula | UNIQUE parcial da waitlist |
| `duplicate_coupon` | 409 | código repetido (case-insensitive) | R6 |
| `referral_not_pending` | 409 | converter/expirar indicação não-pendente | máquina da indicação |
| `membership_not_active` | 400 | pagamento em matrícula não-ativa | R3 |
| `plan_inactive` / `class_inactive` / `no_classes` | 400 | plano/aula inativos ou lista vazia | criação de matrícula |
| `invalid_hours` / `invalid_config` / `invalid_points` / `invalid_coupon` / `invalid_date` / `invalid_time` / `invalid_status` | 400 | payload inválido | config/loyalty/cupom/day-pass |
| `*_not_found` (plan, class, membership, payment, contact, coupon, day_pass, referral, waitlist_entry) | 404 | id inexistente no tenant | lookups |

### Notificações ao cliente

- **Envia:** boas-vindas (→ativa, na criação e na retomada), despedida (→cancelada), lembrete de
  mensalidade em aberto (1×/mês por matrícula), parabéns de aniversário (1×/ano). Textos fixos e
  defensivos, sem promessa de resultado (`AcademiaMembershipStatus.notificationText`).
- **Silêncio:** suspensa (inclusive a auto-suspensão do job — ver lacuna abaixo), POST manual do painel
  sem `conversation_id` (notifier faz skip), fila de espera ("Chamar" NÃO envia WhatsApp — é marcação de
  status no painel), day-pass, pontos/recompensa. Notifier é best-effort: falha de envio nunca reverte.

## Dados e snapshots

- `academia_plans` (mensalidade ≥ 0), `academia_classes` (day_of_week 0–6, duração 15–240, capacity
  1–100), `academia_config` (opens/closes, defaults 06:00/22:00 + política de cobrança),
  `academia_memberships` (CHECK de status; índices por status/contato; R2), `academia_membership_classes`
  (PK composta + 5 snapshots; INSERT só backend), `academia_payments` (R3; INSERT só backend).
- Onda: `academia_checkins` (R5), `academia_class_waitlist` (posição DERIVADA por count de `aguardando`
  mais antigos — sem coluna position), `academia_day_passes` (nasce `paid=false`), `academia_referrals`
  (código UNIQUE por company; pendente→convertida/expirada), `academia_coupons` (R6),
  `academia_loyalty_config` + `academia_loyalty` (saldo ≥ 0; crédito só backend), `contacts.birth_date` +
  `academia_birthday_greeted_year` (core).
- **Cache:** `AcademiaContextCache` (Caffeine, TTL 60s, key `(companyId, contactId)`), invalidado
  explicitamente em toda mutação de plano/aula/matrícula.

## Features de onda (backlog implementado)

- **Inadimplência (mig. 72, `AcademiaInadimplenciaJob`, cron default 10h):** `billing_reminder_enabled`
  default ON, `grace_days` default 5, `auto_suspend_days` NULL = nunca suspende. 1 lembrete por mês de
  referência (`overdue_notified_month` marca até sem canal resolúvel, evitando revarredura); atraso ≥
  limite → suspende via máquina de status (mantém a vaga; a régua **nunca cancela**).
- **Check-ins (73):** presença por (matrícula, aula, dia); origem `painel` (a via `ia` é prevista no
  schema, fluxo futuro). Alicerce da fidelidade.
- **Fila de espera (74):** só ORDENA interesse — não reserva vaga nem cria matrícula; anti-dupla na fila
  por UNIQUE parcial `(class_id, contact_id) where status='aguardando'` (anônimo sem contato passa).
- **Day-pass (75):** registro de avulso; `paid` é marcação manual. **Indicação (76):** código único,
  desconto `reward_percent` concedido LOCALMENTE pelo tenant. **Cupom (77):** validação via
  `POST /api/academia/coupons/validate`; `uses` incrementa só quando aplicado. **Fidelidade (78):**
  opt-in (`enabled` default false), `points_per_checkin` ≥ 1; recompensa é concessão manual.
  **Aniversário (79, cron default 08h):** 1 saudação/ano, idempotente por `greeted_year`.

## O que NÃO existe (limites honestos)

Cobrança real (gateway #50 — hoje registro manual + lembrete + auto-suspensão), treino/ficha/avaliação
física, catraca/QR/leitor (bloqueador SERVICE_ROLE_KEY de upload), aluno como entidade própria (histórico
via contato + snapshots), check-in pela conversa, multi-unidade, fuso configurável (fixo
America/Sao_Paulo).

**Lacunas verificadas:** (1) o guia diz que a IA "aplica o cupom na conversa", mas a tag `<matricula>`
não tem campo de cupom e o contexto da IA não menciona cupons — no código o cupom da academia é validado
só pelo painel; (2) o comentário do `AcademiaInadimplenciaJob` afirma que a auto-suspensão "já notifica
pelo nicho", mas `SUSPENSA.notificationText` é `null` → a auto-suspensão é SILENCIOSA (código manda).
