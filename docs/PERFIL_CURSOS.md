# Perfil Cursos (escola livre · curso online · formação) — camada 8.20

Guia operacional do tenant **cursos** (`profile_id='cursos'`). A IA atende alunos pelo WhatsApp,
apresenta os cursos, MATRICULA (assinatura) e ENTREGA, read-only, o conteúdo do PRÓXIMO módulo da
trilha. Tom acolhedor-motivador.

É o **26º perfil vertical** (25 + generic). CLONA o chassi de **assinatura** do **AcademiaBot**
(matrícula ativa-até-cancelar, anti-dupla, mensalidade manual) e inaugura **trilha de módulos
ordenados + progresso individual + entrega read-only do próximo módulo** (o eixo "Academia + Nutri").

## Telas (sidebar "Cursos")

| Tela | Rota | O que faz |
|------|------|-----------|
| Cursos | `/dashboard/cursos-courses` | CRUD de cursos + editor da TRILHA de módulos ordenados (position/título/conteúdo). |
| Matrículas | `/dashboard/cursos-enrollments` | Matrículas por status + progresso (X/N módulos) + próximo módulo. |
| Pagamentos | `/dashboard/cursos-payments` | Mensalidade manual (sem Stripe). |
| Configurações | `/dashboard/cursos-settings` | Horário de atendimento informativo + notas. |

## Matrícula = assinatura (chassi academia)

A matrícula é uma **assinatura** no curso (recorrência indefinida). Status `CursoEnrollmentStatus` ↔
TS (parity): `ativa ⇄ trancada`; `ativa/trancada → concluida` (terminal, materializa end_date);
`ativa/trancada → cancelada` (terminal, materializa end_date). Notifica **ativa** (boas-vindas, com o
curso), **concluida** (parabéns!) e **cancelada** (despedida); **trancada** silenciosa.

**Anti-dupla = 1 matrícula ATIVA por (aluno, curso)** (índice parcial
`uniq_active_enrollment_per_contact_course`): o mesmo contato pode estar em cursos diferentes, mas não
2× ativo no MESMO curso. Aluno NÃO é entidade própria (igual academia — rotatividade): snapshots
`student_name`/`phone` + `course_title`/`course_monthly_cents` na matrícula. Mensalidade MANUAL
(`cursos_payments`, UNIQUE por mês → 409 `duplicate_payment`; sem Stripe/inadimplência).

## ESCAPADA 1 — Trilha de módulos ordenados

`cursos_modules` (course_id, **position** 0..N, title, content). O curso não é "aula semanal"
(academia) — é uma **sequência ordenada** de módulos. UNIQUE(course_id, position). O `content` é o
material entregue READ-ONLY pela IA. O painel reordena via `PATCH /api/cursos/courses/{courseId}/
modules/reorder` (transacional em 2 fases pra não bater no UNIQUE durante o swap).

## ESCAPADA 2 — Progresso individual + entrega read-only do próximo módulo

`cursos_enrollment_progress` (enrollment_id, module_id, completed_at) — 1 linha = 1 módulo concluído
por aquela matrícula. O **"próximo módulo"** = o 1º por `position` do curso que NÃO está no progresso.

`<entrega_modulo>{enrollment_id}` → o `EntregaModuloHandler`: resolve a matrícula, aplica **BARREIRA
DE CONTATO** (só se a matrícula é do contato da conversa), acha o próximo módulo, envia o `content`
**VERBATIM** via `notifier.sendText` (NÃO passa pela IA — pra não reescrever) e **GRAVA o progresso**
(a próxima entrega avança pro módulo seguinte). Tudo entregue / contato diferente / matrícula
inexistente → não entrega. Espelho da entrega de plano do nutri / material do fotografia, com o
avanço de progresso somado.

## Tags

**`<matricula_curso>`** (1 modo): `{ "course_id", "student_name", "notes" }`.
**`<entrega_modulo>`**: `{ "enrollment_id":"UUID" }`.

Namespace próprio, distinto de `<matricula>` da academia e de TODAS as outras. A IA DESCARTA qualquer
preço — o backend snapshota a mensalidade do curso.

## O que a IA faz / NÃO faz

- **FAZ:** apresenta cursos, matricula no curso escolhido (confirma mensalidade), entrega o próximo
  módulo quando o aluno já está matriculado e é o próprio contato.
- **NÃO FAZ:** inventar curso/módulo/preço/desconto/bolsa; pular a ordem dos módulos; reescrever o
  conteúdo do material; matricular 2× no mesmo curso; prometer certificado/aprovação não descritos.

## O que NÃO existe nesta fase

- Quiz/avaliação/nota por módulo; certificado automático; vídeo hospedado (o conteúdo é texto colado,
  bloqueador SERVICE_ROLE_KEY p/ upload); pré-requisito entre cursos; turma com data/coorte; pagamento
  real (Stripe #50); cobrança/inadimplência automática; trilha com ramificação (a trilha é linear).

## Notas técnicas

- Migration `64_cursos.sql` (6 tabelas: courses, modules, config, enrollments, enrollment_progress,
  payments). A CHECK ACRESCENTA `'cursos'` preservando os 25 perfis. Entra por ÚLTIMO no `SCRIPTS` do
  `AbstractIntegrationTest` (sua CHECK tem os 26).
- Base de conhecimento (RAG): disponível como em todo perfil (item "Conhecimento" do nav +
  `{{knowledge}}` do PromptBuilder, sem gate de perfil).
- Guard `/api/cursos/**` → 403 `forbidden_wrong_profile`. Paleta `oliva`. Tenant: `igorhaf31`.

## Onda 1 do backlog (migration 117)

`docs/FEATURES_SUGERIDAS_CURSOS.md` #1/#2/#3: **#1 certificado de conclusão** — CONCLUIDA emite
`cursos_certificates` (código único ABCD-EFGH-IJKL, snapshots, idempotente por matrícula) e envia
o link (`certificate_base_url` da config; vazio = só o código); verificação PÚBLICA sem auth em
`GET /public/cursos/certificados/{code}` (HTML A4 imprimível; 404 pra código inválido); lista no
painel via `GET /api/cursos/certificates`. **#2 nudge anti-abandono** — matrícula ativa parada há
`nudge_days` (default 7) no mesmo módulo, com próximo módulo existente → 1 toque por episódio
("você está em 3/8 — o próximo é X"; `nudge_sent_at` re-armado quando o progresso avança) via
`CursosNudgeJob` (cron 12:30, default ON — funil ativo). **#3 cupom** (`cursos_coupons`, motor
comum): campo `cupom` na tag `<matricula_curso>`; desconto validado no backend sobre a MENSALIDADE
snapshotada (`discount_cents`); inválido não aborta. Settings ganhou "Automações e certificado".
Teste: `CursosOnda1IntegrationTest`. Fica: #4 cobrança online (gateway), #5 régua de
inadimplência, #6 quiz, #7 win-back, #8 upsell fim de trilha, #9 combos, #15 CMS, #16 vídeo/PDF
(upload).
