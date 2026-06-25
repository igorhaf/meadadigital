# Perfil Escola (educação infantil) — camada 8.19

Guia operacional do tenant **escola** (`profile_id='escola'`). Escola / educação infantil: a escola
gerencia TURMAS (série/ano + turno + capacity + mensalidade), cadastra os ALUNOS de cada responsável,
MATRICULA alunos (assinatura mensal) e agenda VISITAS da família. A IA atende os RESPONSÁVEIS (pais)
via WhatsApp com tom acolhedor-cuidadoso.

CLONA o chassi de MATRÍCULA/ASSINATURA da **AcademiaBot** (camada 7.7 — planos→turmas com capacity,
matrícula = assinatura ativa/suspensa/cancelada, anti-dupla, capacity transacional, mensalidade
manual) e inaugura **DOIS conceitos juntos**: o ALUNO como **sub-entidade do responsável** (espelho
pet/nutri) + a **VISITA agendada** (agenda leve dia+período, espelho floricultura).

## Telas (sidebar "Escola")

| Tela | Rota | O que faz |
|------|------|-----------|
| Turmas | `/dashboard/escola-classes` | CRUD de turmas (nome, série/ano, turno manhã/tarde/integral, capacity/vagas, mensalidade, ano letivo). |
| Alunos | `/dashboard/escola-students` | CRUD de alunos POR RESPONSÁVEL (cada aluno é filho de um contato). |
| Matrículas | `/dashboard/escola-enrollments` | Matrículas = assinaturas: lista por status, criar manual (valida vaga/anti-dupla), ativar/suspender/cancelar, registrar mensalidade do mês. |
| Visitas | `/dashboard/escola-visits` | Visitas agendadas: lista por status/data, criar, marcar realizada/cancelada. |
| Configurações | `/dashboard/escola-settings` | Nome da escola + horário de funcionamento + notas. |

## ESCAPADA 1 — O aluno é sub-entidade do responsável

Na academia o "aluno" era o **próprio contato** (snapshot, não-entidade). Aqui quem fala no WhatsApp é
o **RESPONSÁVEL** (pai/mãe = `contact`), e o **ALUNO** (filho) é uma **sub-entidade persistente**
(`escola_students` com `contact_id NOT NULL`). UM responsável tem **N alunos**. A matrícula referencia
`student_id` (o filho), não o contato direto.

**Anti-dupla por (aluno, turma):** índice parcial `uniq_active_enrollment_per_student_class` on
`(company_id, student_id, class_id) where status='ativa'` — 1 matrícula ativa do **mesmo aluno na
mesma turma**. Um **irmão** pode estar na mesma turma; o **mesmo aluno** pode estar em **turmas
diferentes**. A unicidade é por (aluno, turma), não por aluno só.

**Capacity por turma, transacional:** o INSERT da matrícula re-verifica, **dentro da transação**, que
`count(matrículas da turma com status <> 'cancelada') + 1 <= capacity` → senão **409 `class_full`**
(defesa de corrida, espelho exato da academia). **Suspensa MANTÉM a vaga** ocupada (o count filtra só
`cancelada`); **cancelada LIBERA** a vaga e materializa `end_date`.

## ESCAPADA 2 — Visita agendada (agenda leve dia+período)

Além da matrícula, a IA agenda uma **VISITA** da família à escola — `visit_date` (>= hoje) + `period`
(manhã|tarde). É **agenda LEVE**: SEM conflito de capacidade (várias visitas no mesmo período são OK),
SEM slot de horário fino (espelho floricultura date+period). É uma **entidade própria**
(`escola_visits`) com status próprio (`agendada`/`realizada`/`cancelada`). A visita **NÃO depende de
matrícula** nem de aluno (`student_id` nullable — a família pode visitar antes de escolher).

## Status

**Matrícula** (`EscolaEnrollmentStatus` ↔ `escola-enrollment-status.ts`, parity): `ativa ⇄ suspensa`;
ambas → `cancelada` (terminal). Notifica **ativa** (boas-vindas, com turma+série+turno) e **cancelada**
(despedida); **suspensa** silenciosa.

**Visita** (`EscolaVisitStatus` ↔ `escola-visit-status.ts`, parity): `agendada → realizada/cancelada`;
realizada/cancelada terminais. Notifica **agendada** (confirmação com data+período) e **cancelada**
(defensiva); realizada silenciosa.

Transição inválida → 409 `invalid_status_transition`.

## Mensalidade

Pagamento **MANUAL** (`escola_payments`), registro mensal com UNIQUE `(enrollment, reference_month)` →
409 `duplicate_payment`. Só em matrícula não-cancelada (400 `enrollment_cancelled`). SEM cobrança
automática (Stripe é #50), SEM cálculo de inadimplência/juros.

## O que a IA faz

- Identifica o responsável pelo telefone, acolhe.
- Mostra as **turmas com vaga** disponível (série + turno + mensalidade já cadastrada).
- Agenda uma **visita** da família (dia + período).
- Registra o **interesse de matrícula** de um aluno numa turma — via a tag, em 2 modos.

## O que a IA NÃO faz

- **NUNCA promete vaga não confirmada** — fala em "registrar o interesse"/"pré-reservar pra secretaria
  confirmar", nunca "vaga garantida". A vaga só é garantida quando a secretaria confirma no painel.
- **NUNCA define/negocia/inventa** valor de mensalidade, desconto, bolsa, taxa ou condição de
  pagamento — só informa o valor já cadastrado; negociação → "a secretaria vai falar com você".
- **NUNCA dá parecer pedagógico** sobre a criança (nível, dificuldade, adequação à série,
  comportamento, necessidade especial) — acolhe e encaminha à coordenação (e agenda a visita).
- **NUNCA inventa** turma, série, turno, vaga, valor, professor ou estrutura.

## Tags

**`<matricula_escola>`** (registra interesse de matrícula; 2 modos — espelho do `new_animal` do pet):

```json
{ "class_id": "UUID", "student_id": "UUID|null",
  "new_student": { "name": "...", "birth_date": "YYYY-MM-DD|null", "intended_grade": "...|null" }|null,
  "notes": "...|null" }
```
- Modo 1: `student_id` (aluno já cadastrado do responsável).
- Modo 2: `new_student` (cadastra o aluno como sub-entidade do contato da conversa E matricula no
  mesmo turno).

**`<visita_escola>`** (agenda a visita):

```json
{ "visit_date": "YYYY-MM-DD", "period": "manha|tarde", "num_people": N|null,
  "student_id": "UUID|null", "notes": "...|null" }
```

Ambas têm namespace próprio, distinto de `<matricula>` (academia) e de TODAS as outras. O
`OutboundService` remove a tag antes de enviar ao cliente.

## O que NÃO existe nesta fase

- **Boletim / notas / avaliação / conceito**; **frequência / chamada / presença**; **diário de classe
  / conteúdo programático / plano de aula**; **parecer pedagógico estruturado** (a IA nem dá parecer
  livre).
- **Pagamento real** (Stripe #50 — mensalidade é registro manual); **cálculo de inadimplência / juros
  / multa**.
- **Contrato / matrícula com assinatura digital/PDF/e-sign**; **lista de espera** persistida (vaga
  cheia → a IA diz que avisa quando abrir, sem fila).
- **Transporte escolar / material didático / merenda**; **foto/documento da criança** (bloqueador
  SERVICE_ROLE_KEY); **slot de horário fino** na visita (é dia+período); **multi-unidade**; **calendário
  letivo / feriados**; **comunicado em massa**.

## Notas técnicas

- Migration `63_escola.sql` (6 tabelas: config, classes, students, enrollments, payments, visits). A
  CHECK de `companies.profile_id` ACRESCENTA `'escola'` preservando os 18 perfis anteriores.
- `escola_enrollments`/`escola_visits`: INSERT pelo backend (service_role) via handler; tenant
  SELECT/UPDATE. `escola_classes`/`escola_students`/`escola_config`: CRUD pelo tenant.
- Snapshots na matrícula: student_name + responsible_name + class_name + grade + shift +
  monthly_cents. Alterar turma/aluno depois NÃO altera matrículas passadas.
- `end_date`/status materializados (não generated). shift (manha/tarde/integral) e period (manha/tarde)
  por CHECK; parity só nos 2 status. grade/intended_grade texto livre informativo.
- Contexto da IA via `EscolaContextCache` (Caffeine TTL 60s, keyed por (companyId, contactId) — traz
  os filhos do responsável), invalidado em toda mutação.
- Guard `/api/escola/**` → 403 `forbidden_wrong_profile`. LGPD: `notes` administrativo, sem dado
  pedagógico/de saúde.
- Paleta `mostarda`. Tenant de teste: `igorhaf30` (Escola Modelo).
