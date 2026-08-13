# Escola (educação infantil) — regras de negócio (escola, camada 8.19)

[← Catálogo](../05-nichos.md) · Chassi: E — assinatura (academia) + G — sub-entidade do contato · Guia operacional: docs/PERFIL_ESCOLA.md · Migrations: 63, 109

## O negócio em 3 linhas

A escola vende **matrículas mensais** (assinatura) em **turmas** com capacidade e mensalidade fixas. Quem
fala no WhatsApp é o **responsável** (pai/mãe = contato); o **aluno é sub-entidade** dele
(`escola_students`, 1→N). A IA mostra turmas com vaga, agenda **visitas** da família (dia + período) e
registra o **interesse de matrícula** — quem confirma vaga é a secretaria.

## Jornada no WhatsApp (cenários)

1. Responsável chega → IA o identifica pelo telefone e vê os filhos já cadastrados
   (`EscolaContextCache`).
2. **Visita:** IA emite `<visita_escola>{visit_date, period, num_people?, student_id?, notes?}` →
   visita `agendada` + confirmação por WhatsApp. Data passada → recusada (422 `past_date` no painel;
   silêncio best-effort na tag). Lembrete D-1 e no dia; a resposta cai na IA (remarcar = cancelar +
   agendar de novo).
3. **Matrícula:** IA emite `<matricula_escola>` em 2 modos — `student_id` (filho já cadastrado) OU
   `new_student{name, birth_date?, intended_grade?}` (cadastra o aluno E matricula no mesmo turno).
   Nasce `ativa` + boas-vindas com turma/série/turno.
4. **Turma cheia:** no caminho da tag o handler NÃO descarta o lead — **enfileira** na
   `escola_waitlist`; avisar quando abrir vaga é AÇÃO HUMANA (botão no painel). No painel o mesmo caso
   é 409 `class_full` (com `classId`/`className`).
5. Mensalidade é registro manual no painel; régua de cobrança é opt-in (OFF por default).

## Regras de negócio

### Transacionais (invariantes duras)

- **R1 — Capacity por turma:** `count(matrículas com status <> 'cancelada') + 1 <= capacity`,
  re-verificado **dentro da transação** do INSERT (`EscolaEnrollmentRepository`) → 409 `class_full`.
  **Suspensa MANTÉM a vaga; só cancelada libera.**
- **R2 — Anti-dupla por (aluno, turma):** UNIQUE parcial `uniq_active_enrollment_per_student_class
  (company_id, student_id, class_id) where status='ativa'` + re-check na transação → 409
  `already_active`. Irmão pode estar na mesma turma; o mesmo aluno pode estar em turmas DIFERENTES.
- **R3 — Mensalidade única no mês:** UNIQUE `(enrollment_id, reference_month)` → 409
  `duplicate_payment`; matrícula `cancelada` não recebe pagamento → 400 `enrollment_cancelled`
  (ativa E suspensa podem pagar).
- **R4 — Visita no futuro:** `visit_date >= hoje` (America/Sao_Paulo) → 422 `past_date`; `period`
  só `manha|tarde` (CHECK + 400 `invalid_period`). Agenda LEVE: **sem** conflito de capacidade, sem
  slot fino.
- **R5 — Cancelamento materializa:** `end_date = hoje` em Java; libera a vaga (consequência do R1).
- **R6 — Fila sem promessa:** posição DERIVADA por count de `aguardando` mais antigos (+1), sem coluna
  position; anti-dupla na fila por UNIQUE parcial `(class_id, contact_id, student_name) where
  status='aguardando'`. Entrar na fila não reserva vaga.
- **R7 — Snapshots:** a matrícula congela `student_name`, `responsible_name`, `class_name`,
  `class_grade`, `class_shift`, `class_monthly_cents`; a waitlist congela `student_name`.

### Máquina de status

```
MATRÍCULA                          VISITA
ativa <──────> suspensa            agendada ──> realizada (terminal)
  │               │                    │
  └─> cancelada <─┘  (terminal)        └──────> cancelada (terminal)
```

| Transição | Quem pode | Notifica? |
|---|---|---|
| (criação) → ativa | IA (tag) ou humano (POST painel) | Sim (boas-vindas com turma+série+turno) |
| ativa → suspensa | humano | Não |
| suspensa → ativa | humano | Sim (texto de ativa) |
| ativa/suspensa → cancelada | humano | Sim (despedida) |
| (criação) → visita agendada | IA (tag) ou humano | Sim (data + período) |
| agendada → realizada | humano ou sistema (auto-transição diária) | Não |
| agendada → cancelada | humano (ou IA num remarcar: cancela + agenda) | Sim (defensiva) |

Fora de `EscolaEnrollmentStatus`/`EscolaVisitStatus.allowedNext()` → 409 `invalid_status_transition`.

### O que a IA PODE × NUNCA faz (travas da persona)

- **PODE:** mostrar turmas COM vaga (com a mensalidade JÁ cadastrada), agendar visita, registrar
  interesse de matrícula (2 modos), oferecer a fila quando a turma está cheia.
- **NUNCA:** promete vaga não confirmada (fala "registrar interesse"/"pré-reservar"); define/negocia
  mensalidade, desconto, bolsa ou condição (negociação → "a secretaria vai falar com você"); dá parecer
  pedagógico sobre a criança (nível, dificuldade, série "ideal", comportamento); inventa turma, série,
  turno, vaga, valor, professor ou estrutura. Aceitar/confirmar de fato é da secretaria.

### Tags de IA

| Tag | Quando a IA emite | Campos | O que o backend descarta/recalcula |
|---|---|---|---|
| `<matricula_escola>` | responsável confirma interesse numa turma | `class_id`, `student_id` OU `new_student{name, birth_date?, intended_grade?}`, `notes?` | qualquer valor — mensalidade snapshotada DA TURMA; vaga (R1) e anti-dupla (R2) revalidados; `class_full` → enfileira na waitlist |
| `<visita_escola>` | família combina a visita | `visit_date`, `period`, `num_people?`, `student_id?`, `notes?` | data validada (R4); nome/telefone do visitante vêm do contato |

### Validações e erros

| reason | HTTP | Significado de negócio | Cenário |
|---|---|---|---|
| `forbidden_wrong_profile` | 403 | tenant de outro perfil | `/api/escola/**` sem `profile_id='escola'` |
| `class_full` | 409 | turma sem vaga (body traz `classId`/`className`) | R1 |
| `already_active` | 409 | aluno já ativo NAQUELA turma | R2 |
| `invalid_status_transition` | 409 | transição fora da máquina | matrícula ou visita |
| `duplicate_payment` | 409 | mês já registrado | R3 |
| `class_in_use` / `student_in_use` | 409 | excluir turma/aluno referenciado | preferir desativar/arquivar |
| `past_date` | 422 | visita no passado | R4 |
| `enrollment_cancelled` | 400 | pagamento em matrícula cancelada | R3 |
| `class_inactive` / `student_inactive` | 400 | turma/aluno inativos | criação de matrícula |
| `invalid_period` / `invalid_shift` / `invalid_date` / `invalid_time` / `invalid_hours` / `invalid_status` | 400 | payload inválido | visita/turma/config |
| `*_not_found` (class, student, enrollment, visit, payment, contact, waitlist_entry) | 404 | id inexistente no tenant | lookups |

### Notificações ao cliente

- **Envia:** matrícula ativa (boas-vindas com turma+série+turno) e cancelada; visita agendada
  (confirmação com data+período) e cancelada (defensiva); aviso de vaga aberta (SÓ pelo botão humano
  `POST /api/escola/waitlist/{id}/notify` → status `avisada`); lembrete de visita D-1 e D0; lembrete de
  mensalidade em aberto (opt-in). Textos fixos, sem promessa de vaga.
- **Silêncio:** suspensa, visita realizada (inclusive a auto-transição), enfileiramento na waitlist (a
  IA apenas explica que é fila SEM promessa), POST manual sem conversa. Best-effort: falha nunca reverte.

## Dados e snapshots

- `escola_config` (nome + horários, defaults 07:00/18:00 + toggles da onda), `escola_classes` (shift
  CHECK `manha|tarde|integral`, capacity 1–200, mensalidade ≥ 0), `escola_students` (sub-entidade:
  `contact_id NOT NULL`; `active=false` arquiva), `escola_enrollments` (CHECK de status, R2, snapshots),
  `escola_payments` (R3; INSERT só backend), `escola_visits` (period CHECK, status CHECK, markers de
  lembrete), `escola_waitlist` (status `aguardando|avisada|convertida|desistiu`, R6).
- **Cache:** `EscolaContextCache` (Caffeine, TTL 60s, key `(companyId, contactId)` — inclui os filhos do
  responsável), invalidado em toda mutação.

## Features de onda (migration 109)

- **#1 Lista de espera por turma:** `class_full` na tag → enfileira com snapshot do nome (até de
  `new_student` que nem foi criado); posição derivada; avisar/converter/desistir é gestão humana.
- **#2 Lembrete de visita D-1 e D0:** `EscolaReminderJob` (cron `${escola.reminder-cron:0 10 8 * * *}`);
  markers `reminded1/0_visit_date` — remarcar REARMA (marker ≠ visit_date). Toggle
  `visit_reminder_enabled` default ON.
- **#10 Auto-transição de visita:** agendada com data passada → realizada, silenciosa (falta = a
  secretaria marca cancelada). Toggle `visit_auto_complete_enabled` default ON.
- **#4 Régua de mensalidade em aberto:** OPT-IN `payment_reminder_enabled` default **OFF** (cobrança em
  massa é decisão consciente); a partir de `payment_due_day` (default 10, CHECK 1–28), matrícula ativa
  sem pagamento do mês → 1 lembrete gentil/mês (`payment_reminded_month`), com o valor snapshot, sem
  multa/juros.

## O que NÃO existe (limites honestos)

Boletim/notas/frequência/diário de classe, parecer pedagógico (nem livre), contrato/assinatura digital,
cobrança real (gateway #50) e inadimplência com juros/multa, taxa de matrícula, transporte/material/
merenda, foto/documento da criança (bloqueador SERVICE_ROLE_KEY), slot de horário fino na visita (é
dia+período), multi-unidade, calendário letivo, comunicado em massa. Não há campo de cupom nem
fidelidade neste perfil.
