# CursosBot — regras de negócio (cursos, camada 8.20)

[← Catálogo](../05-nichos.md) · Chassi: E — assinatura + F — entrega read-only · Guia operacional: docs/PERFIL_CURSOS.md · Migrations: 64, 117

## O negócio em 3 linhas

O tenant é uma escola livre / curso online / formação que vende cursos por mensalidade. O aluno fala
pelo WhatsApp; a IA apresenta os cursos (mensalidade, categoria, nº de módulos), **matricula** o aluno
(assinatura ativa-até-cancelar) e **entrega o conteúdo do próximo módulo da trilha** — texto do
professor, verbatim, avançando o progresso individual a cada entrega.

## Jornada no WhatsApp (cenários)

1. Aluno pergunta sobre cursos. A IA responde do bloco de contexto (cursos ativos com id/mensalidade/
   nº de módulos + matrículas do próprio contato com progresso "X/N" e título do próximo módulo).
2. Na confirmação final (curso + nome + valor da mensalidade), a IA emite
   `<matricula_curso>{course_id, student_name, notes, cupom}`. O `MatriculaCursoConfirmHandler` cria a
   matrícula **ativa** com snapshots do curso; cupom é validado no backend; boas-vindas na hora.
3. Aluno já matriculado pede o próximo conteúdo → `<entrega_modulo>{enrollment_id}`. O
   `EntregaModuloHandler` aplica a **barreira de contato**, acha o 1º módulo por `position` que não
   está no progresso, envia o `content` **VERBATIM** (`notifier.sendText`, sem passar pela IA) e
   **grava o progresso** — a próxima entrega avança pro módulo seguinte.
4. A escola gerencia pelo painel: trancar/reativar, concluir (parabéns + certificado em mensagem
   separada, com link ou código) e cancelar (despedida).
5. **Exceções (best-effort, sem mensagem de erro ao aluno):** curso inexistente/inativo, contato já
   ativo no mesmo curso, matrícula de outro contato, trilha concluída ou módulo sem `content` → a tag
   vira no-op com warn no log e a resposta da IA segue normal.
6. **Onda 1:** matrícula ativa parada há `nudge_days` no mesmo módulo recebe UM toque motivador do
   `CursosNudgeJob` ("você está em 3/8 — o próximo é X").

## Regras de negócio

### Transacionais (invariantes duras)

- **R1 — Anti-dupla: 1 matrícula ATIVA por (contato, curso)** — índice parcial UNIQUE
  `uniq_active_enrollment_per_contact_course` (WHERE status='ativa'), pré-check no service E RE-CHECK
  dentro da transação de INSERT (`CursosEnrollmentRepository.insertEnrollment`). O mesmo contato pode
  estar em cursos diferentes.
- **R2 — Mensalidade é snapshot do catálogo:** qualquer preço emitido pela IA é DESCARTADO;
  `course_title`/`course_monthly_cents` congelam na matrícula (mudar o curso depois não altera nada).
- **R3 — Cupom recalculado no backend** sobre a mensalidade snapshotada: ativo + `valid_until` +
  `uses < max_uses` + `monthly_cents ≥ min_order_cents`; desconto = min(mensalidade, valor) com
  `percent` = mensalidade×valor/100; `discount_cents` materializado + `coupon_code_snapshot`;
  **cupom inválido NÃO aborta** (matrícula sai sem desconto); `uses` incrementa no uso.
- **R4 — Trilha ordenada:** UNIQUE `(course_id, position)`; "próximo módulo" = 1º por `position` ASC
  ausente de `cursos_enrollment_progress`; o reorder do painel é transacional em 2 fases para não
  bater no UNIQUE durante o swap (`CursosModuleService.reorder`).
- **R5 — Entrega avança progresso:** o progresso (PK `(enrollment_id, module_id)`) é gravado APÓS o
  envio com sucesso; se `recordProgress` falhar depois do envio, o módulo já foi entregue — warn e
  progresso fica pendente (não estoura a resposta).
- **R6 — 1 pagamento por mês por matrícula:** UNIQUE `(enrollment_id, reference_month)` → 409
  `duplicate_payment`; pagamento só em matrícula **ativa** → 400 `enrollment_not_active`.
- **R7 — Terminais materializam `end_date` em Java** (`LocalDate.now` America/Sao_Paulo) —
  `concluida` E `cancelada`.
- **R8 — Certificado idempotente por matrícula:** `enrollment_id` UNIQUE + `on conflict do nothing`;
  `code` UNIQUE global no formato `ABCD-EFGH-IJKL` (alfabeto sem 0/O/1/I, `SecureRandom`).
- **R9 — Curso com matrícula não é excluído** (FK `on delete restrict` → 409 `course_in_use`);
  módulos caem em cascata COM o curso; o caminho é desativar (`active=false`, sai da visão da IA).
- **R10 — Matrícula nasce SÓ pela IA:** não existe POST manual de matrícula (o controller só lista,
  detalha e muda status); INSERT é service_role (tenant sem policy de INSERT em `cursos_enrollments`).

### Máquina de status

```
ativa ⇄ trancada
ativa/trancada ──→ concluida   (terminal, end_date, certificado)
ativa/trancada ──→ cancelada   (terminal, end_date)
```

| Transição | Quem pode | Notifica o cliente? |
|---|---|---|
| (criação) → ativa | IA (`<matricula_curso>`) — única origem | **sim** (boas-vindas com o curso) |
| ativa → trancada | humano no painel | não (silenciosa; pausa, mantém o vínculo) |
| trancada → ativa | humano no painel | **sim** (texto de ativa — retomada) |
| ativa/trancada → concluida | humano no painel | **sim** (parabéns) + 2ª mensagem com o certificado |
| ativa/trancada → cancelada | humano no painel | **sim** (despedida) |

Transição fora do diagrama → 409 `invalid_status_transition` (`CursoEnrollmentStatus.allowedNext`).
A IA NÃO muta status de matrícula — nem concluir, nem trancar, nem cancelar.

### O que a IA PODE × NUNCA faz (travas da persona)

- **PODE:** apresentar os cursos do catálogo, matricular confirmando SEMPRE curso + mensalidade,
  entregar o próximo módulo de matrícula do PRÓPRIO contato, repassar código de cupom na tag.
- **NUNCA** (`ProfilePromptContext.CURSOS`): inventa curso/módulo/preço/desconto/bolsa fora do
  catálogo; promete certificado, aprovação ou resultado não descritos no curso; pula a ordem dos
  módulos ou reescreve o material; matricula 2× no mesmo curso; calcula desconto por conta própria
  (o sistema valida o cupom); muta status de matrícula.

### Tags de IA

| Tag | Quando a IA emite | Campos | O que o backend descarta/recalcula |
|---|---|---|---|
| `<matricula_curso>` | confirmação final de curso + nome | `course_id`, `student_name` (opc), `notes` (opc), `cupom` (opc, onda 1) | preço DESCARTADO (snapshot do catálogo); desconto recalculado (R3); `student_name` ausente cai pro nome/telefone do contato |
| `<entrega_modulo>` | aluno matriculado pede o próximo conteúdo | `enrollment_id` | conteúdo NUNCA vem da tag — o backend resolve o próximo módulo e envia o `content` VERBATIM; barreira de contato |

Parseadas por regex (nunca tool calling), removidas antes do envio; falha → no-op + warn.

### Validações e erros

| reason | HTTP | Significado de negócio | Cenário que dispara |
|---|---|---|---|
| `forbidden_wrong_profile` | 403 | tenant não é cursos | qualquer `/api/cursos/**` com outro perfil |
| `course_not_found` / `module_not_found` / `enrollment_not_found` / `payment_not_found` / `coupon_not_found` | 404 | entidade inexistente/de outro tenant | id errado |
| `course_in_use` | 409 | curso tem matrículas (FK restrict) | DELETE de curso com histórico |
| `duplicate_position` | 409 | posição duplicada na trilha (UNIQUE) | criar/mover módulo pra position ocupada |
| `invalid_status` | 400 | status alvo desconhecido | PATCH com status inexistente |
| `invalid_status_transition` | 409 | transição proibida na máquina | concluida → ativa |
| `enrollment_not_active` | 400 | pagamento em matrícula não-ativa | registrar mensalidade de trancada/cancelada |
| `duplicate_payment` | 409 | mês de referência já pago (UNIQUE) | 2º pagamento no mesmo mês |
| `invalid_date` / `invalid_time` / `invalid_hours` | 400 | data/hora malformada; opens ≥ closes | payload/config inválida |
| `invalid_coupon` / `duplicate_coupon` | 400 / 409 | cupom malformado; código repetido (UNIQUE lower) | CRUD de cupom |

Anti-dupla e curso inativo NÃO têm reason HTTP: `AlreadyEnrolledException`/`CourseInactiveException`
só existem no fluxo da tag (no-op + warn) — não há endpoint que os exponha.

### Notificações ao cliente

- **Envia:** boas-vindas na matrícula (e na retomada trancada→ativa), parabéns na `concluida` +
  mensagem separada do certificado (link se `certificate_base_url`; senão só o código), despedida na
  `cancelada`, e o nudge anti-abandono (onda 1).
- **Silêncio:** `trancada` (pausa não merece sermão) e qualquer mutação sem `conversation_id`
  (`notifyStatus` pula). Textos fixos e defensivos, SEM promessa de resultado de aprendizado.

## Dados e snapshots

- **`cursos_courses`** — `title` 1–200, `monthly_cents ≥ 0`, `active`; delete restrito (R9).
- **`cursos_modules`** — UNIQUE `(course_id, position)`, `position ≥ 0`; `content` é o material
  entregue verbatim; cascade com o curso.
- **`cursos_config`** (1:1) — `opens_at`/`closes_at` INFORMATIVOS (08:00/22:00 — curso é assíncrono,
  sem agenda/slot); onda 1: `nudge_enabled` (default ON), `nudge_days` 1–90 (default 7),
  `certificate_base_url`.
- **`cursos_enrollments`** — snapshots `student_name`/`student_phone` + `course_title`/
  `course_monthly_cents`; onda 1: `discount_cents ≥ 0` + `coupon_code_snapshot` + `nudge_sent_at`
  (mensalidade líquida = snapshot − desconto); `contact/conversation` `on delete set null`; tenant só
  SELECT/UPDATE; índice parcial anti-dupla (R1).
- **`cursos_enrollment_progress`** — PK `(enrollment_id, module_id)`; cascade com a matrícula,
  restrict no módulo; INSERT só backend; tenant SELECT via JOIN com a matrícula.
- **`cursos_payments`** — UNIQUE `(enrollment_id, reference_month)` (sempre dia 01); tenant só SELECT.
- **`cursos_coupons`** — UNIQUE `(company_id, lower(code))`; `kind` percent|fixed; só service_role.
- **`cursos_certificates`** — `enrollment_id` UNIQUE + `code` UNIQUE; snapshots student/course/school.
- **Cache:** `CursosContextCache` (Caffeine, **TTL 60s** — cursos mudam pouco; max 1000) por
  `(company, contato)`; invalidado explicitamente em toda mutação de curso/módulo/matrícula/config.

## Features de onda (backlog implementado — migration 117)

- **#1 Certificado de conclusão:** `concluida` emite via `CursosCertificateService.issue`
  (idempotente, R8) e envia o link/código na notificação. Verificação PÚBLICA sem auth:
  `GET /public/cursos/certificados/{code}` — HTML A4 landscape imprimível; código inválido → 404 com
  página "não encontrado". Lista no painel: `GET /api/cursos/certificates`.
- **#2 Nudge anti-abandono** (`CursosNudgeJob`, cron default 12h30 `cursos.nudge-cron`): matrícula
  ATIVA com conversa, parada há `nudge_days` (última entrega no progresso, ou `created_at` se nunca
  entregou) E com próximo módulo existente → 1 toque por episódio; `nudge_sent_at` re-armado quando o
  progresso avança; trilha concluída não é cutucada. **Default ON** (funil ativo, não é disparo à base).
- **#3 Cupom** (`cursos_coupons`, motor comum): campo `cupom` na tag; regra R3; CRUD no painel.

## O que NÃO existe (limites honestos)

- Quiz/avaliação/nota por módulo; vídeo/PDF hospedado (conteúdo é texto colado — bloqueador
  SERVICE_ROLE_KEY p/ upload); pré-requisito entre cursos; turma/coorte com data; trilha ramificada
  (a trilha é LINEAR).
- Pagamento online (Stripe é backlog #50), cobrança/régua de inadimplência automática.
- Matrícula manual pelo painel (nasce só pela tag da IA — o painel gerencia status); capacidade/vaga
  por curso (diferente da academia, não há limite de alunos).
- `opens_at`/`closes_at` são informativos: nenhuma validação de horário em matrícula ou entrega.
- Win-back, upsell de fim de trilha, combos e CMS específico (backlog #7/#8/#9/#15, não implementados).
