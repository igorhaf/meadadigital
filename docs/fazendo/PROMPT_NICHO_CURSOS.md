>>> SLOT ATRIBUÍDO (ver docs/prompts-nicho/README.md — fonte única de verdade, tem precedência
>>> sobre qualquer "provisório" no corpo): ordem 15 · camada 8.20 · migration 64_cursos.sql ·
>>> tenant igorhaf31 (company/user sufixo -031) · ids de seed sufixo -18x. Reconfirmar no arranque
>>> que a fila não avançou; se avançou, deslocar conforme o README.

[TAREFA — SUB-MARATONA: PERFIL CURSOS / Cursos (Ensino passo a passo) (camada 8.20)]

[CONTEXTO]
PROJETO MEADA em /home/igorhaf/meada.
Catálogo de perfis verticais reais já fechado/declarado no enum (… academia 7.7, nutri 8.0, …
floricultura 8.5, pizzaria 8.6, e os da fila do README: casamento/padaria/adega/lavanderia/
dermatologia/otica/projetos…) + generic. ESTE perfil é mais um vertical real (campo da plataforma).
Lê CLAUDE.md, CONTEXT.md e o filesystem no arranque pra cravar convenções, nº de migration,
contagem do Surefire e numeração de tenant ANTES de escrever qualquer código. NÃO hardcodar a
contagem do mvn — relatar a REAL do Surefire ao final.

VALORES DO SLOT (do README — têm precedência; CONFIRMAR no filesystem que a fila não avançou):
- migration: `64_cursos.sql` — CONFIRME `ls supabase/migrations/` no arranque e use o nº do README;
  se a fila avançou e 64 já existe, deslocar pro PRÓXIMO livre e reportar.
- tenant: igorhaf31 (CONFIRMAR; se já usado, usar o próximo livre e reportar). company
  c?0000000-…-031 / user a?0000000-…-031 seguindo a numeração do tenant.
- IDs de namespace compartilhado (contacts/instance/conversation) NO SEED com sufixo `-18x` (NOVO —
  conferir os usados pra evitar colisão FK).

Cursos é template de nicho pra ESCOLA DE CURSOS (idiomas, profissionalizante, online) dentro do
mesmo dashboard Meada. O tenant acessa cursos.meadadigital.local e vê o produto "Cursos". A IA
atende ALUNOS via WhatsApp, identifica pelo TELEFONE, mostra os cursos disponíveis, INSCREVE o aluno
num curso (assinatura), INFORMA em que ponto da trilha ele está e ENTREGA, read-only, a orientação do
PRÓXIMO MÓDULO que a escola gravou — "ensino passo a passo". Tom acolhedor-incentivador de quem
conduz o aluno pela jornada de aprendizado, sem prometer aprovação, certificado ou resultado que a
escola não cravou.

>>> ========================================================================================
>>> TRAVA DE COMPORTAMENTO DA IA (cravada — o que a IA NUNCA faz)
>>> ========================================================================================
A IA inscreve, mostra cursos, informa o progresso e entrega a descrição do próximo módulo
(READ-ONLY). Em TODA a conversa, sem exceção:
- NUNCA inventa conteúdo de aula/módulo/curso — só entrega o texto EXATO (verbatim) que a escola
  gravou no módulo; se o módulo não tem conteúdo gravado, não improvisa.
- NUNCA dá aula, NUNCA ensina a matéria por conta própria, NUNCA corrige exercício, NUNCA responde
  "a resposta é X" — o conteúdo pedagógico é o que o professor gravou; dúvida de conteúdo →
  encaminha ao professor/escola.
- NUNCA emite/promete CERTIFICADO, NUNCA atribui NOTA, NUNCA declara o aluno "aprovado"/"concluído"
  por conta própria — concluir um módulo/curso é decisão registrada pela escola no painel (ou a
  regra de progresso do backend), não a IA falando.
- NUNCA define PREÇO, DESCONTO, BOLSA, parcelamento ou condição de pagamento — o preço vem do
  catálogo do curso; "vou confirmar valores e condições com a secretaria".
- NUNCA promete prazo de conclusão, garantia de aprendizado ou resultado ("você vai ficar fluente").
- NUNCA pula a trilha: o "próximo passo" é SEMPRE o próximo módulo na ordem (position) ainda não
  concluído — a IA não libera módulo fora de ordem nem antecipa conteúdo de módulo futuro.

EVOLUÇÃO ESTRUTURAL: CLONA o chassi de MATRÍCULA/ASSINATURA da ACADEMIA (camada 7.7) — curso vira
"plano" (curso com preço + carga horária + nº de módulos), a inscrição é uma ASSINATURA com status
(ativa/concluida/cancelada/trancada), anti-dupla (1 inscrição ATIVA por aluno por curso) e pagamento
MANUAL (mensalidade/parcela). Aluno = contact (curso ADULTO → o aluno É o contact; sub-entidade com
responsável fica pra fase futura). UMA escapada nova:

  ESCAPADA — SUB-ENTIDADE NOVA: TRILHA DE MÓDULOS/AULAS COM PROGRESSO SEQUENCIAL
  (course_modules + enrollment_progress). Um curso tem N MÓDULOS ORDENADOS (position); a inscrição
  registra o PROGRESSO do aluno POR MÓDULO (nao_iniciado/em_andamento/concluido). A IA pode INFORMAR
  ao aluno em que módulo ele está e qual é o PRÓXIMO PASSO — "ensino passo a passo" = entregar a
  orientação/descrição do PRÓXIMO MÓDULO ainda não concluído, READ-ONLY/VERBATIM (espelho EXATO do
  EntregaPlanoHandler do nutri): a IA envia o conteúdo gravado pela escola, sem inventar, sem
  resumir, sem adaptar. É a sub-entidade nova: MÓDULOS ORDENADOS + PROGRESSO por aluno.
  DIFERENTE de tudo que veio antes:
    - A AULA da academia (academia_classes) é GRADE SEMANAL FIXA (dia da semana + hora + capacity),
      recorrente, compartilhada por todos os matriculados; o progresso individual NÃO existe lá.
    - Aqui a TRILHA é uma SEQUÊNCIA ORDENADA de módulos (position 0..N) e o progresso é INDIVIDUAL
      por aluno (uma linha enrollment_progress por (inscrição × módulo)). Não há horário/grade
      semanal nem capacity de sala — é o caminho de aprendizado do aluno, no ritmo dele.
    - O conteúdo do módulo (descrição/orientação) é um artefato READ-ONLY-PRA-IA: a escola grava no
      painel; a IA entrega VERBATIM o próximo módulo (espelho do body do plano do nutri).

NÃO TEM nesta SM (registrado pra não inventar): player de vídeo / LMS real (não há aula em vídeo,
quiz interativo, SCORM, streaming), upload de material/PDF/vídeo (bloqueador SERVICE_ROLE_KEY —
o conteúdo do módulo é TEXTO gravado no painel), certificado automático / emissão de diploma,
prova/quiz corrigido automaticamente / nota / boletim, pré-requisito entre cursos (a trilha é dentro
de UM curso), turma com data de início/fim e calendário de aulas ao vivo (a academia cobre grade
recorrente; aqui é trilha individual no ritmo do aluno), pagamento real / gateway (Stripe é #50 —
pagamento é MANUAL como na academia), cálculo de inadimplência automática, scheduler de lembrete
("você parou no módulo 3"), aluno como sub-entidade com responsável/menor de idade (curso adulto →
aluno = contact; sub-entidade fica pra fase futura). Fases futuras.

DECISÕES CRAVADAS (revisor decidiu pelo Igor):
1. Chassi = MATRÍCULA/ASSINATURA da ACADEMIA: curso (~academia_plans, com preço + carga horária + nº
   de módulos derivado), inscrição = assinatura com status, anti-dupla (1 ATIVA por aluno por curso),
   pagamento manual. MANTER. NÃO inventar grade semanal/capacity (a academia cobre isso).
2. Aluno = contact (curso ADULTO). NÃO é entidade do core; snapshots student_name/phone na inscrição
   (espelho do student_name/phone da matrícula da academia). Sub-entidade com responsável → fase futura.
3. ESCAPADA = TRILHA: course_modules (módulos ordenados por position, com conteúdo TEXTO) +
   enrollment_progress (progresso individual por módulo: nao_iniciado/em_andamento/concluido). Distinta
   da aula-semanal-recorrente da academia. Documentar a escolha no header da migration.
4. Entrega do PRÓXIMO MÓDULO = READ-ONLY pela IA (espelho EntregaPlanoHandler do nutri): texto EXATO
   do conteúdo do módulo, verbatim, com BARREIRA de contato (só entrega da inscrição do PRÓPRIO contato
   da conversa). O "próximo módulo" é o primeiro módulo (menor position) com progresso ≠ concluido.
5. Status da INSCRIÇÃO hardcoded (CursoEnrollmentStatus enum Java + const TS + parity test):
   ativa ⇄ trancada ; ativa/trancada → concluida (terminal) ; ativa/trancada → cancelada (terminal).
   (Espelho do AcademiaMembershipStatus, que tem ativa⇄suspensa→cancelada — aqui "suspensa" vira
   "trancada" (jargão de escola) e GANHA o estado terminal "concluida". Cravar e ser consistente em
   enum/TS/CHECK/textos.) Notificações (texto defensivo): só ATIVA (boas-vindas, com o curso) e
   CANCELADA (despedida) e CONCLUIDA (parabéns SEM promessa de certificado) avisam o aluno;
   trancada silenciosa. (Decisão menor: agente confirma se concluida notifica — recomendo SIM,
   texto defensivo sem prometer certificado.)
6. O status do MÓDULO no progresso (nao_iniciado/em_andamento/concluido) NÃO tem enum/parity — é
   CHECK na coluna + constante/validação no service e no front. Transição LIVRE entre os 3 (o aluno/
   escola pode voltar de em_andamento pra nao_iniciado). Só o status da INSCRIÇÃO tem parity.
7. A IA INSCREVE (tag <inscricao_curso>) e ENTREGA o próximo módulo (tag <proximo_modulo>). A IA
   NUNCA cancela/tranca/conclui a inscrição pela conversa, NUNCA marca módulo como concluído pela
   conversa (mudar progresso é ação humana no painel — espelho do "cancelar é ação humana" do
   dental). A entrega é só LEITURA.
8. Anti-dupla = índice parcial UNIQUE (company_id, contact_id, course_id) WHERE status='ativa' —
   impede 2 inscrições ATIVAS do mesmo contato no MESMO curso. O mesmo aluno PODE ter inscrição ativa
   em cursos DIFERENTES (diferente da academia, que era 1 ativa por contato GLOBAL — aqui é por curso).
   O service também valida via findActiveByContactAndCourse (→ 409 already_active).

[FUNDAÇÃO — migration 64_cursos.sql  (CONFIRMAR o nº livre no disco; README crava 64)]
- ALTER companies CHECK aceitar 'cursos' (estende a constraint atual — drop+add da
  companies_profile_id_check com TODOS os ids ATUAIS no disco + 'cursos'; CONFERIR a lista REAL na
  ÚLTIMA migration de perfil presente — ela cresceu desde a 36; NUNCA remover um id existente; após
  qualquer clonagem por sed, conferir que o CHECK tem TODOS os perfis + 'cursos').
- RLS enable+force, policies via app.company_id(), grants authenticated + service_role. INSERT de
  INSCRIÇÕES e de PROGRESSO pelo BACKEND (service_role — a IA via ConfirmHandler OU o tenant via POST
  manual); tenant SELECT/UPDATE (gerenciar a inscrição e o progresso na tela). Cursos/módulos/config:
  CRUD do tenant (authenticated). Espelhar 36_academia.sql + 39_nutri.sql inteiros (estrutura,
  comentários, índices, policies).
- Tabelas:
  * cursos_config — config 1:1 com company; SEM horário/slot (não há agenda). (company_id PK refs
    companies on delete cascade, business_name nullable (nome da escola), notes, timestamps). Ausente
    → defaults vazios. Espelho academia_config (sem opens_at/closes_at).
  * cursos_courses — o CURSO (espelho academia_plans). (id, company_id refs companies on delete
    restrict, name CHECK 1..200, price_cents int NOT NULL CHECK >=0 (preço/mensalidade do curso),
    workload_hours int nullable CHECK >=0 (carga horária), description text, active default true,
    created_at, updated_at). Índices: (company_id, active) where active, (company_id, name). COMMENT:
    curso = "plano" do nicho; price_cents entra como SNAPSHOT na inscrição; nº de módulos é DERIVADO
    de cursos_modules (não coluna).
  * cursos_modules — A ENTIDADE NOVA (parte 1 da escapada): MÓDULOS ORDENADOS do curso. (id,
    company_id refs companies on delete restrict, course_id refs cursos_courses on delete cascade,
    title CHECK 1..200, content text NULLABLE (conteúdo/orientação do módulo — texto gravado pela
    escola, entregue VERBATIM pela IA; vazio = sem orientação a entregar), position int NOT NULL
    default 0 (ORDEM explícita), active default true, created_at, updated_at). Índice (course_id,
    position). Leitura ordenada por position asc, created_at asc. COMMENT cravando: módulo ordenado da
    trilha; content é orientação READ-ONLY entregue verbatim pela IA (espelho do body do plano do
    nutri), NÃO é gerado pela IA; ≠ aula-semanal-recorrente da academia (sem dia/hora/capacity).
  * cursos_enrollments — a INSCRIÇÃO-ASSINATURA (espelho academia_memberships). (id, company_id refs
    companies on delete restrict, course_id refs cursos_courses on delete restrict, contact_id refs
    contacts on delete restrict, conversation_id nullable refs conversations on delete set null,
    student_name text NOT NULL (snapshot), student_phone text (snapshot), course_name text NOT NULL
    (snapshot do curso), price_cents int NOT NULL (snapshot do preço no momento), status text NOT NULL
    default 'ativa' CHECK in ('ativa','trancada','concluida','cancelada'), start_date date NOT NULL
    default current_date, end_date date nullable (preenchido em concluida/cancelada), notes text
    (ADMINISTRATIVO), created_at, status_updated_at). Índices: (company_id, status), (contact_id,
    status), (course_id, status). ANTI-DUPLA: índice PARCIAL UNIQUE
    uniq_active_enrollment_per_contact_course (company_id, contact_id, course_id) WHERE status='ativa'.
    RLS: SELECT/UPDATE pro tenant; INSERT só backend (service_role).
  * enrollment_progress — A ENTIDADE NOVA (parte 2 da escapada): PROGRESSO INDIVIDUAL por módulo.
    (id, company_id refs companies on delete restrict, enrollment_id refs cursos_enrollments on delete
    cascade, module_id refs cursos_modules on delete cascade, status text NOT NULL default
    'nao_iniciado' CHECK in ('nao_iniciado','em_andamento','concluido'), started_at timestamptz
    nullable, completed_at timestamptz nullable, created_at, updated_at). UNIQUE (enrollment_id,
    module_id). Índice (enrollment_id). COMMENT cravando: progresso INDIVIDUAL do aluno por módulo da
    trilha; 3 estados (CHECK, sem parity); o "próximo passo" = menor position do curso com status ≠
    concluido. RLS: SELECT/UPDATE pro tenant; INSERT só backend.
  * cursos_payments — parcela/mensalidade MANUAL (espelho academia_payments). (id, company_id refs
    companies on delete restrict, enrollment_id refs cursos_enrollments on delete cascade,
    reference_month text NOT NULL (ex.: '2026-06' ou texto livre 'Parcela 1'), amount_cents int NOT
    NULL CHECK >=0, paid_at date NOT NULL default current_date, notes, created_at). UNIQUE
    (enrollment_id, reference_month) → 409 duplicate_payment. Só em inscrição ATIVA (validação no
    service). SEM cobrança automática (Stripe é #50).
- Status da INSCRIÇÃO hardcoded (CursoEnrollmentStatus enum Java + const TS + parity test):
  ativa → trancada, concluida, cancelada ; trancada → ativa, concluida, cancelada ; concluida/
  cancelada → terminal. CANCELADA/CONCLUIDA materializam end_date. Espelho do AcademiaMembershipStatus
  ADICIONANDO o terminal 'concluida' e renomeando 'suspensa'→'trancada'.
- O status do MÓDULO no progresso (nao_iniciado/em_andamento/concluido) NÃO tem enum/parity — CHECK na
  coluna + constante/validação no service e no front.
- TODAS as 6 tabelas novas entram na migration ANTES de tocar o banco (banco se aplica A PARTIR do
  arquivo versionado — lição os_config da SM-J) e na lista de TRUNCATE/SCRIPTS do
  AbstractIntegrationTest.

[BACKEND]
- Estrutura em src/main/java/com/meada/profiles/cursos/ espelhando profiles/academia/:
  raiz (CursoEnrollmentStatus, CursosContextCache, CursosProfileGuard) + subpastas config/, courses/,
  modules/, enrollments/, progress/, payments/.
- Config: GET (fallback default vazio) + PUT. (espelho academia_config; SEM horário)
- Courses (CRUD, espelho academia_plans/AcademiaPlanService): id, name, price_cents, workload_hours,
  description, active. delete em uso (inscrição com course_id) → 409 course_in_use; preferir desativar
  (active=false). O detalhe do curso hidrata a lista de módulos (ordenada por position). Validar
  price_cents >=0.
- Modules (CRUD + reorder; a tela da escapada): add/update/delete + reorder.
  * add: cria com position = (max(position)+1) do curso; content nullable.
  * update: title/content.
  * reorder: recebe a ORDEM nova (lista ordenada de ids) e re-materializa position sequencial 0..N na
    MESMA transação (decisão do agente sobre o payload — recomendo lista ordenada de ids).
  * delete em uso? módulo com progresso registrado → preferir desativar (active=false) ou 409
    module_in_use (decisão do agente; recomendo 409 se houver enrollment_progress apontando, pra não
    perder histórico; on delete cascade do progresso é o caminho destrutivo — preferir bloquear).
  * leitura ordenada por position asc, created_at asc.
  * Exceptions: module_not_found (404), course_not_found (404).
- Enrollments (chassi academia_memberships + os 2 editores de estado: status da inscrição + progresso):
  * create (a partir da tag/IA ou POST manual): resolve course → snapshota course_name+price_cents;
    resolve contact → snapshota student_name/phone; nasce 'ativa'; ANTI-DUPLA via findActiveByContact
    AndCourse (→ 409 already_active) E o índice parcial UNIQUE (defesa de corrida no banco). conversation_id
    quando veio da conversa. Ao criar a inscrição, MATERIALIZA uma linha enrollment_progress
    'nao_iniciado' por módulo ATIVO do curso na MESMA transação (a trilha do aluno nasce inteira em
    nao_iniciado). Decisão menor: o agente pode optar por criar o progresso lazy (na 1ª leitura) — mas
    recomendo materializar no create pra a entrega do "próximo módulo" funcionar sempre.
  * updateStatus (da INSCRIÇÃO): valida transição (inválida → 409 invalid_status_transition);
    concluida/cancelada preenchem end_date + status_updated_at; dispara notificação outbound conforme o
    status (CursoEnrollmentNotifier).
  * PROGRESSO (NOVO): updateModuleProgress(enrollmentId, moduleId, novoStatus) sob a inscrição ser
    mutável (ativa OU trancada; concluida/cancelada → 409 enrollment_locked — não mexe em progresso de
    inscrição terminal). Transição LIVRE entre nao_iniciado/em_andamento/concluido; ao entrar em
    em_andamento seta started_at (se null); ao entrar em concluido seta completed_at (now()); ao SAIR
    de concluido zera completed_at; ao voltar pra nao_iniciado zera started_at/completed_at (decisão do
    agente sobre a regra exata de limpeza — documentar a escolha). status inválido → 400
    invalid_progress_status. progresso inexistente → 404 progress_not_found.
  * GET detalhe da inscrição hidrata a TRILHA (módulos do curso ordenados por position + o status de
    progresso de cada um) e calcula o "próximo módulo" (menor position com status ≠ concluido) — usado
    pela tela e como base da entrega read-only.
  * POST manual pelo tenant: sem conversation_id (sem WhatsApp) — não notifica.
  * NÃO há DELETE de inscrição (histórico; "remover" = status cancelada).
- Payments (manual, espelho academia_payments): registro com UNIQUE (enrollment_id, reference_month) →
  409 duplicate_payment. Só em inscrição ATIVA → 422/409 enrollment_not_active (espelhar o erro da
  academia). amount_cents >=0.
- Notifier (espelho AcademiaMembershipNotifier): best-effort, persiste OUTBOUND/HUMAN, texto DEFENSIVO
  SEM promessa de certificado/aprovação/resultado. Notifica: ativa (boas-vindas, com o nome do curso),
  cancelada (despedida), concluida (parabéns SEM prometer certificado). trancada silenciosa.
- Entrega READ-ONLY do PRÓXIMO MÓDULO (espelho EntregaPlanoHandler do nutri):
  * ProximoModuloHandler parseia <proximo_modulo>{enrollment_id}; resolve a inscrição; BARREIRA DE
    SEGURANÇA: só entrega se o contato da inscrição == contato da conversa (impede vazar a trilha de
    outro aluno). Calcula o PRÓXIMO MÓDULO (menor position do curso com progresso ≠ concluido) e, se o
    content é não-vazio, envia VERBATIM via notifier.sendText (NÃO passa pela IA — pra não ser
    reescrito). Inscrição não-ativa / sem próximo módulo (trilha concluída) / próximo módulo SEM content
    / contato diferente → Optional.empty + warn. Devolve o texto entregue em sucesso.
    (Decisão menor: ao entregar, o agente PODE opcionalmente promover o progresso do módulo entregue de
    nao_iniciado→em_andamento — recomendo NÃO automatizar isso pela IA, pra manter "mudar progresso é
    ação humana"; documentar a escolha. A entrega é LEITURA pura.)
- IA:
  * Persona CURSOS nova em ProfilePromptContext (tom acolhedor-incentivador com a TRAVA DE COMPORTAMENTO
    acima embutida no prompt — espelhar o tom/estrutura de ACADEMIA/NUTRI). Adicionar case CURSOS no
    switch de segmentFor(id) e o branch if ("cursos".equals(profileId)) em segmentFor(id, companyId,
    conversationId).
  * Contexto injetado (CursosContextCache, cache TTL 20s — espelho do academia/nutri, keyed por
    (companyId, contactId)): cursos ATIVOS (nome + preço + carga + nº de módulos) + as inscrições do
    contato (curso + status + módulo atual/próximo passo — SEM despejar o content dos módulos, igual
    nutri não despeja o body do plano; só indica em que módulo o aluno está e que o módulo TEM
    orientação a entregar) + instruções e as 2 tags. Invalidação explícita em toda mutação (curso/
    módulo/inscrição/progresso/config).
  * Tag <inscricao_curso>{"course_id":"UUID","student_name":"texto|null","notes":"texto"} →
    InscricaoCursoConfirmHandler (espelho MatriculaConfirmHandler; resolve o contato da conversa;
    student_name default = contact.name; anti-dupla (já ativa no curso → empty + warn); cria a
    inscrição ATIVA + a trilha de progresso; best-effort; qualquer falha → empty + warn).
  * Tag <proximo_modulo>{"enrollment_id":"UUID"} → ProximoModuloHandler (espelho EntregaPlanoHandler;
    entrega VERBATIM a orientação do próximo módulo da inscrição; barreira de contato; sem próximo /
    sem content / não-ativa / outro contato → empty).
  * JwtFilter autentica /api/cursos/ (adicionar CURSOS_PATH_PREFIX = "/api/cursos/" + o
    !uri.startsWith(...) no shouldNotFilter). OutboundService ganha maybeProcessInscricaoCurso +
    maybeProcessProximoModulo (best-effort, guard "cursos".equals(findProfileId), contactId via
    findContactIdByConversation, encadeados APÓS os outros perfis — perfil é único, só um age; REMOVE a
    tag antes de enviar ao cliente; espelho EXATO de maybeProcessMatricula + maybeProcessEntregaPlano).
- Guard: CursosProfileGuard (403 forbidden_wrong_profile) — espelho AcademiaProfileGuard.

[FRONTEND]
- Telas (App Router, /dashboard/cursos-*):
  * /dashboard/cursos-courses — CRUD cursos (nome + preço + carga horária + descrição + ativo;
    desativar preferido a excluir; delete em uso → 409 course_in_use). NO detalhe do curso, o EDITOR
    DE MÓDULOS ORDENADOS (a tela da escapada): lista por position, form de título + conteúdo (textarea),
    botões ↑↓ ou drag pra reordenar (reorder re-materializa position). Deixar EXPLÍCITO na UI que o
    conteúdo do módulo é entregue ao aluno VERBATIM pela IA (read-only) e que a IA não inventa aula.
  * /dashboard/cursos-modules — (opcional/decisão do agente) se preferir, os módulos vivem dentro do
    detalhe do curso em cursos-courses; mas o README do slot pede a tela cursos-modules — pode ser a
    mesma tela acessada por rota própria OU uma listagem global de módulos por curso. Recomendo: editor
    de módulos no detalhe do curso E uma rota /dashboard/cursos-modules que lista módulos por curso
    selecionado (decisão menor — registrar a escolha).
  * /dashboard/cursos-enrollments — inscrições: lista por status (badge ativa/trancada/concluida/
    cancelada), criar inscrição (escolhe curso + contato/aluno; anti-dupla → 409 already_active),
    transição de status (botões respeitando ALLOWED_NEXT; inválida → 409). NO detalhe da inscrição,
    MOSTRAR A TRILHA DE PROGRESSO: os módulos do curso ordenados por position com o seletor
    nao_iniciado/em_andamento/concluido por módulo + destaque no "próximo passo". Editor de pagamentos
    manuais (registrar parcela/mensalidade; duplicate → 409).
  * /dashboard/cursos-settings — nome da escola + notas (sem horário).
- types + SDKs (config, courses (com módulos: add/update/delete/reorder), enrollments (com status +
  progresso updateModuleProgress + payments)) espelhando academia. CourseModule: { id, courseId,
  title, content: string|null, position: number, active, ... }. EnrollmentProgress: { id, enrollmentId,
  moduleId, status: 'nao_iniciado'|'em_andamento'|'concluido', startedAt: string|null, completedAt:
  string|null, ... }.
- Status TS curso-enrollment-status.ts (4 ids, ALLOWED_NEXT, statusLabel) +
  CursoEnrollmentStatusParityTest Java↔TS. (O status do MÓDULO/progresso SEM parity — constante de 3
  strings no front + CHECK no banco.)
- getNavForProfile('cursos') com BRANCH PRÓPRIO injeta "Cursos" (4 itens: Cursos, Módulos, Inscrições,
  Configurações). ATENÇÃO: floricultura ficou no enum SEM branch em getNavForProfile (fallback) — NÃO
  repetir esse gap; cursos PRECISA do branch (if (profileId === 'cursos') return [CURSOS_GROUP,
  ...NAV_GROUPS]). Subdomínio cursos.meadadigital.local.
  Paleta: sugerir 'indigo' / 'pinheiro' / 'oliva'. ATENÇÃO: 'indigo' já é usada pelo legal e 'pinheiro'
  pela academia (conferir em lib/themes/palettes.ts e no profile-type.ts as já tomadas) — se quiser
  distinção, preferir uma LIVRE; recomendação: 'oliva' (verde-estudo) se livre, ou criar uma paleta
  nova; decisão do agente — só registrar a escolha e por quê.
- frontend/lib/profiles/profile-type.ts: adicionar
  { id: 'cursos', productName: 'Cursos', subdomain: 'cursos', defaultPaletteId: '<paleta>' } no const
  TS E o membro espelho CURSOS("cursos","Cursos","cursos","<paleta>") no enum Java (ProfileTypeParityTest
  valida). NUNCA remover um perfil existente — só ACRESCENTAR.
- npm build limpo (next build — Turbopack dev esconde import quebrado).

[DOCS]
- CLAUDE.md: seção "## Perfil Cursos (CursosBot, camada 8.20)" espelhando as seções de perfil.
  Documentar EXPLÍCITO: clona o chassi de MATRÍCULA/ASSINATURA da ACADEMIA (curso=plano, inscrição=
  assinatura com status, anti-dupla 1 ativa por aluno POR CURSO, pagamento manual); a ESCAPADA (trilha
  de módulos ordenados + progresso individual por aluno + entrega READ-ONLY do próximo módulo, espelho
  do EntregaPlanoHandler do nutri); a diferença entre a trilha sequencial daqui e a aula-semanal-
  recorrente da academia; aluno=contact (curso adulto), sub-entidade com responsável é fase futura; a
  TRAVA de comportamento (a IA não inventa aula, não dá certificado/nota, não define preço/desconto);
  as 2 tags (<inscricao_curso>, <proximo_modulo>).
- docs/PERFIL_CURSOS.md: guia operacional do tenant (cursos + editor de módulos ordenados; inscrições e
  estados + trilha de progresso por módulo; pagamentos manuais; como a IA atende; o bloco "o que a IA
  NUNCA faz"). Espelhar PERFIL_ACADEMIA.md / PERFIL_NUTRI.md.
- NÃO mexer em system-template.txt nem em outros perfis.

[TESTES BACKEND]
Espelhar a suíte do academia/nutri (service + controller integration por entidade):
- CursoEnrollmentStatusParityTest + ProfileTypeParityTest (cursos no enum/const).
- CursosConfigServiceTest/ControllerIntegrationTest (GET fallback + PUT).
- CursoServiceTest + ControllerIntegrationTest (CRUD; price_cents >=0; delete-em-uso 409 course_in_use;
  detalhe hidrata módulos ordenados).
- CursoModuleServiceTest + ControllerIntegrationTest (add cria com position incremental; update
  title/content; REORDER re-materializa position sequencial 0..N; leitura ordenada por position;
  module_not_found 404; delete-em-uso/desativar conforme a regra cravada).
- CursoEnrollmentServiceTest (create snapshota curso+preço+aluno e nasce ATIVA; ANTI-DUPLA: 2ª inscrição
  ATIVA no MESMO curso/contato → already_active; mesmo contato em curso DIFERENTE → OK; create
  materializa a trilha de progresso (1 linha nao_iniciado por módulo ativo); transição válida/inválida;
  concluida/cancelada preenchem end_date; notificação ativa/cancelada/concluida) + ControllerIntegration
  Test (409 already_active; 409 invalid_status_transition; wrongProfile 403).
- EnrollmentProgressServiceTest (a CHAVE da trilha): transição nao_iniciado→em_andamento (started_at
  set) → concluido (completed_at set) → nao_iniciado (timestamps zerados conforme a regra cravada);
  status inválido → 400 invalid_progress_status; progresso inexistente → 404 progress_not_found; mexer em
  progresso de inscrição terminal (concluida/cancelada) → 409 enrollment_locked; o "próximo módulo" =
  menor position com status ≠ concluido.
- CursoPaymentServiceTest + ControllerIntegrationTest (registra parcela; duplicate_payment 409 no UNIQUE;
  só em inscrição ATIVA).
- InscricaoCursoConfirmHandlerTest (inscreve a partir da tag; student_name ausente → contact.name; já
  ativa no curso → empty; sem tag → empty; course_id inválido → empty).
- ProximoModuloHandlerTest (a CHAVE da entrega READ-ONLY): inscrição do PRÓPRIO contato + próximo módulo
  COM content → entrega o texto EXATO (asserção casa VERBATIM, char-a-char); inscrição de OUTRO contato
  na conversa → BLOQUEADO (empty — barreira); trilha toda concluída (sem próximo) → empty; próximo módulo
  SEM content → empty; inscrição não-ativa → empty; sem tag → empty.
- (TRAVA: a trava vive na persona — testar que a persona CURSOS contém os marcadores "NUNCA inventa
  conteúdo"/"NUNCA emite certificado"/"NUNCA define preço" via assert no segmentFor, espelho de como se
  testa o tom em SMs anteriores; e que segmentFor('cursos') é não-vazio e começa com o cabeçalho da
  persona de Cursos.)
mvn final = relatar contagem REAL do Surefire (não estimar).

[CONSTRAINTS DUROS]
- Migration única (nº do README: 64; CONFIRMAR o livre real). Sem foto/anexo/upload (bloqueador
  SERVICE_ROLE_KEY — conteúdo do módulo é TEXTO no painel).
- Chassi = matrícula/assinatura da ACADEMIA: curso=plano, inscrição=assinatura com status, anti-dupla
  (1 ATIVA por aluno POR CURSO), pagamento manual. NÃO inventar grade semanal/capacity (academia cobre).
- Aluno = contact (curso adulto). NÃO é entidade do core; snapshots student_name/phone na inscrição.
  Sub-entidade com responsável = fase futura.
- ESCAPADA = TRILHA: course_modules (ordenados por position, content texto) + enrollment_progress
  (progresso individual por módulo, 3 estados CHECK sem parity). Distinta da aula-semanal-recorrente da
  academia. Documentar a escolha no header.
- Entrega do PRÓXIMO MÓDULO = READ-ONLY (verbatim, barreira de contato) — espelho EntregaPlanoHandler.
  A IA NUNCA gera/interpreta/resume o conteúdo do módulo. "Próximo" = menor position ≠ concluido.
- A IA INSCREVE e ENTREGA o próximo módulo. NUNCA cancela/tranca/conclui a inscrição nem marca módulo
  concluído pela conversa (mudar status/progresso é ação humana no painel).
- TRAVA: IA NUNCA inventa conteúdo de aula/curso, NUNCA dá aula/corrige exercício, NUNCA emite
  certificado/nota/aprovação, NUNCA define preço/desconto/bolsa, NUNCA promete resultado, NUNCA pula a
  ordem da trilha.
- Status da inscrição (ativa/trancada/concluida/cancelada) consistente em enum/TS/CHECK/textos. Status
  do progresso (nao_iniciado/em_andamento/concluido) SÓ CHECK + constante (sem parity).
- Tags <inscricao_curso> e <proximo_modulo> distintas de TODAS as outras (em especial das de academia
  <matricula> e nutri <entrega_plano>/<consulta_nutri>).
- LGPD: notes (inscrição) é ADMINISTRATIVO; content do módulo é material pedagógico gravado pela escola,
  não dado sensível do aluno.
- NÃO mexer em outros perfis nem em system-template.txt. Webhook OFF.
- Cache TTL 20s + invalidação em toda mutação. NÃO despejar o content dos módulos no contexto da IA
  (só indica em que módulo o aluno está e que tem orientação; o texto sai só na entrega — espelho do
  body do plano do nutri).
- 529 → inline. Gate 3× → pausar. Working tree sujo → pausar. git add EXPLÍCITO (nunca git add .);
  .env/CONTEXT.md/secrets NUNCA staged.
- SEED com timestamptz: usar `at time zone 'America/Sao_Paulo'` (lição do fuso).
- IDs de namespace compartilhado no seed com sufixo NOVO `-18x` (conferir os usados; evitar colisão FK).
- Tabela nova entra na migration ANTES de tocar o banco (lição os_config). Adicionar as 6 tabelas ao
  TRUNCATE/SCRIPTS do AbstractIntegrationTest.
- Pool de teste minúsculo já está em src/test/resources/application-dev.yml (lição SM-K Hikari × N
  contextos) — NÃO mexer; só confere que segue lá.
- Decisões menores: agente decide (layout exato, ícones do nav, nome de constante, payload do reorder,
  regra exata de limpeza de timestamps na transição de progresso, se a entrega promove o progresso,
  paleta final, módulos no detalhe do curso vs rota própria).

[PASSO FINAL — TENANT igorhaf31 + SEED + COMMIT + PUSH + SMOKE + RELATÓRIO]
F.1 — TENANT igorhaf31 (Escola Cursos Modelo, profile=cursos), padrão GoTrue (instance_id=zero-UUID +
      colunas de token='' não NULL — lição seed auth.users), senha em comunicação direta. company
      c?0000000-…-031 / user a?0000000-…-031 (numeração do tenant confirmado). Caddy + /etc/hosts pra
      cursos.meadadigital.local. (Se igorhaf31 já existe de outra SM, usar o próximo livre e reportar.)
F.2 — Seed /tmp/seed-cursos.sql (NÃO COMITAR; `at time zone 'America/Sao_Paulo'`; ids sufixo `-18x`;
      lição os_config — só roda DEPOIS que a migration versionada está no disco/aplicada):
  - config: business_name "Escola Cursos Modelo".
  - 2 cursos:
    * "Inglês Básico" (price_cents 19900, workload_hours 40) com 4 MÓDULOS ORDENADOS (position 0..3),
      cada um com content (orientação real, ex.: "Módulo 1 — Saudações: pratique hello/good morning…")
      — pra smoke de entrega read-only do próximo módulo e de ordenação.
    * "Excel Profissional" (price_cents 29900, workload_hours 24) com 3 módulos (position 0..2), um dos
      módulos SEM content (pra smoke de "próximo módulo sem content → empty").
  - contact "Helena Martins" +5511944443333 (VINCULADO: instance+conversation, pra smoke de notificação
    + entrega de próximo módulo) + contact "Bruno Carvalho" +5511955554444 (sem vínculo).
  - 3 inscrições cobrindo estados E a trilha:
    * VINCULADA, status 'ativa' (Helena / "Inglês Básico"), com a TRILHA de progresso materializada:
      módulo 0 'concluido', módulo 1 'em_andamento', módulos 2 e 3 'nao_iniciado' — o "próximo passo" é
      o módulo 1 (em_andamento, menor position ≠ concluido) COM content (pra smoke de entrega verbatim
      via <proximo_modulo>). conversation_id vinculado. + 1 pagamento manual ('2026-06').
    * 'ativa' (Bruno / "Excel Profissional") com a trilha — usada pra smoke de anti-dupla (tentar 2ª
      inscrição ativa no MESMO curso → 409) e de "próximo módulo SEM content → empty".
    * 'trancada' (Helena / "Excel Profissional") — pra smoke de inscrição não-ativa (entrega bloqueada)
      e de transição trancada→ativa. (Atenção anti-dupla: Helena trancada em Excel + Helena pode reativar;
      não criar 2 ATIVAS no mesmo curso pro mesmo contato.)
F.3 — JwtFilter /api/cursos/ (se ainda não).
F.4-F.6 — git add EXPLÍCITO dos arquivos da SM (migration 64_cursos.sql, profiles/cursos/**,
      OutboundService, JwtAuthenticationFilter, ProfilePromptContext, ProfileType.java, profile-type.ts,
      nav-config.tsx, frontend cursos-*, curso-enrollment-status.ts, testes, docs CLAUDE.md +
      PERFIL_CURSOS.md, AbstractIntegrationTest) + sanity (git status -s + diff --staged --stat + grep
      segredo eyJ../password/secret= + confirmar .env/.env.local/CONTEXT.md FORA da staging) + commit.
      Mensagem padrão (feat(camada-8.20): perfil cursos/Cursos (ensino passo a passo) com FUNDAÇÃO/
      BACKEND/FRONTEND/DECISÕES/VALIDAÇÃO contagem REAL/NÃO TOCADO/FECHAMENTO + trailer Co-Authored-By:
      Claude Opus 4.8 <noreply@anthropic.com>). Tag fase-8.20-fechada (nº real confirmado no arranque).
F.7 — git push origin main + git push origin --tags. NUNCA --force.
F.8 — docker compose restart backend (ou ./scripts/run-local.sh) + aguardar /admin/me → 401
      missing_auth_header.
F.9 — Smoke E2E (token ES256 via POST {SUPABASE_URL}/auth/v1/token?grant_type=password):
  BLOCO A: auth — igorhaf31 → /admin/me → role=tenant_admin, profileId=cursos, productName=Cursos.
  BLOCO B: catálogo + guard — GET cursos (2); GET config + PUT; CRUD smoke de um curso + delete-em-uso
    409 course_in_use; tenant de OUTRO perfil (academia/nutri/floricultura) → /api/cursos/courses → 403
    forbidden_wrong_profile.
  BLOCO C: módulos ordenados (parte 1 da escapada) [CHAVE] —
    - GET detalhe do "Inglês Básico" → 4 módulos ordenados por position (0,1,2,3).
    - POST novo módulo → position incremental (4); PATCH reorder (inverter) → GET reflete position
      sequencial nova; DELETE/desativar módulo conforme a regra; module_not_found → 404.
  BLOCO D: inscrição-assinatura + anti-dupla (chassi academia) [CHAVE] —
    - GET enrollments (3) com curso+status corretos.
    - POST inscrição nova (contato sem inscrição ativa no curso) → 200 + trilha de progresso
      materializada (1 linha nao_iniciado por módulo).
    - POST 2ª inscrição ATIVA no MESMO curso/contato → 409 already_active.
    - POST inscrição do MESMO contato em curso DIFERENTE → 200 (prova que anti-dupla é POR CURSO).
    - PATCH transição ativa→trancada→ativa→concluida (preenche end_date); inválida (concluida→ativa) →
      409 invalid_status_transition.
    - POST pagamento manual; duplicate (mesmo reference_month) → 409 duplicate_payment.
  BLOCO E: progresso por módulo (parte 2 da escapada) [CHAVE] —
    - GET trilha da inscrição da Helena (Inglês) → módulo 0 concluido, 1 em_andamento, 2/3 nao_iniciado;
      "próximo passo" = módulo 1.
    - PATCH progresso nao_iniciado→em_andamento (started_at preenchido) → em_andamento→concluido
      (completed_at preenchido) → concluido→nao_iniciado (timestamps zerados conforme a regra cravada).
    - PATCH status de progresso inválido ('xpto') → 400 invalid_progress_status.
    - PATCH progresso numa inscrição CONCLUIDA/CANCELADA → 409 enrollment_locked.
  BLOCO F: entrega READ-ONLY do PRÓXIMO MÓDULO (o coração do "ensino passo a passo") [CHAVE] —
    - <proximo_modulo>{enrollment_id da Helena/Inglês} via handler/teste → o aluno RECEBE o content
      EXATO do PRÓXIMO módulo (módulo 1, em_andamento) — asserção casa VERBATIM, char-a-char.
    - <proximo_modulo> de uma inscrição de OUTRO contato (Bruno) na conversa da Helena → BLOQUEADO
      (empty — barreira de contato).
    - <proximo_modulo> cujo próximo módulo NÃO tem content (Excel) → empty (nada entregue).
    - <proximo_modulo> de inscrição TRANCADA → empty (não-ativa).
  BLOCO G: inscrição via IA + trava [CHAVE da trava] —
    - <inscricao_curso>{course_id, ...} na conversa de um contato SEM inscrição ativa no curso → inscrição
      criada + trilha materializada; tag removida da msg.
    - <inscricao_curso> de quem JÁ tem inscrição ativa no curso → empty (anti-dupla na IA).
    - PROVA DA TRAVA: segmentFor('cursos') contém "NUNCA inventa conteúdo" + "NUNCA emite certificado" +
      "NUNCA define preço" (assert no texto da persona).
  BLOCO H: regressão + paridade — os perfis anteriores intactos (smoke leve 1 endpoint cada);
    cursos → /api/academia/* → 403; cursos → /api/nutri/* → 403; e mvn test
    -Dtest=CursoEnrollmentStatusParityTest,ProfileTypeParityTest → verde.
  Cleanup smoke + restaurar seed pristine. mvn final: contagem REAL do Surefire.
F.10 — RELATÓRIO consolidado + DESTAQUE EXPLÍCITO:
  - "Nº perfil vertical — camada 8.20 (confirmado no arranque)"
  - "CLONA a MATRÍCULA/ASSINATURA da ACADEMIA (curso=plano, inscrição=assinatura com status, anti-dupla
     1 ativa por aluno POR CURSO, pagamento manual) e inaugura a TRILHA de módulos ordenados + progresso
     individual por aluno"
  - "ESCAPADA: trilha de módulos (course_modules, ordenados por position) + progresso individual
     (enrollment_progress, 3 estados) + entrega READ-ONLY do próximo módulo (verbatim, barreira de
     contato — espelho da entrega de plano do nutri); BLOCOS C/E/F provam"
  - "Distinta da aula-semanal-recorrente da academia: aqui é trilha sequencial individual no ritmo do
     aluno, sem grade/capacity"
  - "BLOCO D prova a anti-dupla POR CURSO (2ª ativa no mesmo curso → 409; mesmo aluno em curso diferente
     → OK)"
  - "TRAVA: IA inscreve/mostra/informa progresso/entrega próximo módulo; NUNCA inventa conteúdo, NUNCA
     dá certificado/nota, NUNCA define preço/desconto; BLOCO G prova a persona"
  - "Aluno = contact (curso adulto); snapshots student_name/phone na inscrição"
  - "Seed usou at time zone America/Sao_Paulo + sufixo de ids -18x (sem fuso/colisão)"
  - "as 6 tabelas criadas DENTRO da migration 64 (lição os_config); 6 tabelas no TRUNCATE do
     AbstractIntegrationTest"
  - PENDÊNCIAS: player de vídeo/LMS real, upload de material/PDF/vídeo, certificado automático, prova/
     quiz corrigido com nota, pré-requisito entre cursos, turma com calendário ao vivo, pagamento real
     (Stripe), lembrete automático de progresso, aluno sub-entidade com responsável + a dívida acumulada
     (webhook OFF, cliente real, olho humano sobre os verticais).

[REPORTAR]
Igual SMs anteriores. Incluir EXPLICITAMENTE:
- "ProfileType.CURSOS adicionado (Nº perfil real, camada 8.20) — paridade ProfileType validada"
- "Paridade CursoEnrollmentStatus validada (ativa/trancada/concluida/cancelada)"
- "Tenant igorhaf31 (ou o livre real) criado seguindo padrão GoTrue + Caddy/etc/hosts"
- "Chassi de matrícula/assinatura clonado da ACADEMIA: inscrição-assinatura + anti-dupla 1 ativa por
   aluno POR CURSO + pagamento manual"
- "ESCAPADA: TRILHA de módulos ordenados (course_modules) + progresso individual por aluno
   (enrollment_progress, 3 estados CHECK sem parity) — distinta da grade-semanal da academia"
- "Entrega READ-ONLY do PRÓXIMO MÓDULO (<proximo_modulo>, verbatim, barreira de contato) — espelho do
   EntregaPlanoHandler do nutri; 'próximo' = menor position ≠ concluido"
- "TRAVA na persona: IA inscreve/mostra/informa progresso/entrega; NUNCA inventa conteúdo de aula,
   NUNCA emite certificado/nota, NUNCA define preço/desconto/bolsa, NUNCA promete resultado"
- "Aluno = contact (curso adulto); sub-entidade com responsável é fase futura"
- "Tags: <inscricao_curso> (inscreve) + <proximo_modulo> (entrega read-only) — distintas de TODAS as
   outras (em especial <matricula>/<entrega_plano>)"
- "OutboundService ganhou maybeProcessInscricaoCurso + maybeProcessProximoModulo (encadeados, perfil
   único)"
- "JwtFilter autentica /api/cursos/"
- "getNavForProfile('cursos') com branch próprio (não repetir o gap do floricultura)"
- "Paleta escolhida: <indigo|pinheiro|oliva|nova> (registrar e por que; preferir LIVRE)"
- "as 6 tabelas criadas DENTRO da migration 64 (lição os_config); 6 tabelas no TRUNCATE do
   AbstractIntegrationTest"
- "Seed: at time zone America/Sao_Paulo + sufixo de ids -18x (sem bug de fuso, sem colisão FK)"
- "Próximas fases: player de vídeo/LMS, upload de material, certificado automático, prova/quiz com nota,
   pré-requisito entre cursos, calendário ao vivo, pagamento real, lembrete de progresso, aluno
   sub-entidade com responsável + fila de prioridade (webhook, cliente real, olho humano sobre os
   verticais)"
