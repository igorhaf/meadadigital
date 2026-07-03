>>> SLOT ATRIBUÍDO (ver docs/prompts-nicho/README.md — fonte única de verdade, tem precedência
>>> sobre qualquer "provisório" no corpo): ordem 14 · camada 8.19 · migration 63_escola.sql ·
>>> tenant igorhaf30 (company/user sufixo -030) · ids de seed sufixo -17x. Reconfirmar no arranque
>>> que a fila não avançou; se avançou, deslocar conforme o README (manter UM slot único: migration,
>>> camada e tenant distintos dos demais).

[TAREFA — SUB-MARATONA: PERFIL ESCOLA / Escola (Educação infantil · Escola) (camada 8.19)]

[CONTEXTO]
PROJETO MEADA em /home/igorhaf/meada.
Vários perfis verticais reais hoje (… academia 7.7, pet 7.8 … comida 8.4, floricultura 8.5, pizzaria
8.6 e os demais da fila) + generic. Lê CONTEXT.md e o filesystem no arranque pra cravar convenções,
nº de migration, contagem do Surefire e numeração de tenant ANTES de escrever qualquer código. NÃO
hardcodar a contagem do mvn — relatar a REAL do Surefire ao final. Valores do SLOT (CONFIRMAR no
filesystem antes; podem ter avançado se outra SM foi executada primeiro): migration 63_escola, camada
8.19, tenant igorhaf30, company c?0000000-...-030, user a?0000000-...-030, ids de namespace
compartilhado (contacts/instance/conversation) NO SEED com sufixo -17x — conferir que NENHUM seed
anterior já usou esse sufixo.

Escola é template de nicho pra ESCOLA / EDUCAÇÃO INFANTIL dentro do mesmo dashboard Meada. Tenant
acessa escola.meadadigital.local e vê o produto "Escola". A escola MATRICULA alunos em TURMAS/SÉRIES
(assinatura/mensalidade) e agenda VISITA dos pais à escola. A IA atende os RESPONSÁVEIS (pais) via
WhatsApp, identifica pelo telefone, ACOLHE, mostra as TURMAS COM VAGA (série/ano + turno + valor da
mensalidade já cadastrado), AGENDA uma VISITA da família à escola (dia + período) e REGISTRA o
INTERESSE de matrícula de um ALUNO (filho) numa turma. Tom acolhedor-cuidadoso, de quem fala com pais
sobre a educação dos filhos.

>>> TRAVA DE COMPORTAMENTO DA IA (cravada — o que a IA NUNCA faz) <
- NUNCA promete vaga que não esteja confirmada — a IA mostra as turmas com vaga DISPONÍVEL no momento,
  mas a vaga só é garantida quando a secretaria confirma a matrícula no painel. A IA fala em
  "registrar o interesse" / "deixar pré-reservado pra secretaria confirmar", não em "vaga garantida".
- NUNCA define, negocia ou inventa VALOR de mensalidade, DESCONTO, BOLSA, taxa de matrícula ou
  condição de pagamento — esses valores são da secretaria, cadastrados no painel. A IA só INFORMA o
  valor que JÁ está cadastrado na turma; qualquer negociação → "a secretaria vai falar com você".
- NUNCA dá PARECER PEDAGÓGICO sobre a criança (nível, dificuldade, adequação à série, comportamento,
  necessidade especial) — se o responsável trouxer isso, a IA acolhe e encaminha pra uma conversa com
  a coordenação/secretaria (e agenda a visita). NUNCA decide a série/turma "ideal" pra criança.
- NUNCA inventa turma, série, turno, vaga, valor, professor ou estrutura que não esteja cadastrado.
- A IA SÓ: mostra turmas com vaga, agenda visita e registra interesse de matrícula. Aceitar/confirmar
  a matrícula de fato (ativar a assinatura), definir valor e dar parecer são AÇÕES HUMANAS no painel.

EVOLUÇÃO ESTRUTURAL: CLONA o chassi de MATRÍCULA / ASSINATURA da ACADEMIA (camada 7.7) — planos
viram TURMAS (série/ano + turno + capacity/vagas + mensalidade) + a matrícula é uma ASSINATURA
(status ativa/suspensa/cancelada) + anti-dupla matrícula + capacity por turma validado
TRANSACIONALMENTE no INSERT + pagamento mensal MANUAL (mensalidade, UNIQUE por mês). DUAS escapadas
NOVAS, juntas, que a academia não tem:

  ESCAPADA 1 — O ALUNO É SUB-ENTIDADE DO RESPONSÁVEL (espelho pet_animals/nutri_patients): na
  academia o "aluno" era o PRÓPRIO contato (snapshot, não-entidade). Na escola, quem fala no WhatsApp
  é o RESPONSÁVEL (pai/mãe = contact), e o ALUNO (filho) é uma SUB-ENTIDADE persistente do contato
  (escola_students com contact_id NOT NULL). UM responsável (contact) pode ter N alunos (filhos). A
  matrícula referencia um student_id (o filho), não o contato direto. Anti-dupla passa a ser POR
  ALUNO POR TURMA (1 matrícula ativa do mesmo aluno na mesma turma), não por contato — um irmão pode
  estar em outra turma, e o mesmo aluno não pode ter 2 matrículas ativas na MESMA turma.

  ESCAPADA 2 — AGENDAMENTO DE VISITA À ESCOLA (agenda LEVE por dia + período, espelho floricultura
  period — NÃO slot fino): além da matrícula (a assinatura na turma), a IA AGENDA uma VISITA da
  família à escola — uma data (>= hoje) + período (manha|tarde) com a secretaria. NÃO é slot de
  horário fino nem tem conflito de capacidade (a secretaria recebe a família; várias visitas no mesmo
  período são OK). É uma entidade própria (escola_visits) com status próprio (agendada/realizada/
  cancelada). A visita NÃO depende de matrícula — o responsável pode visitar antes de matricular.

Os DOIS conceitos juntos (matrícula-assinatura com aluno sub-entidade + visita agendada) é a escapada
desta SM. Nenhum perfil anterior combina assinatura-por-aluno-sub-entidade com agenda leve de visita.

NÃO TEM nesta SM (registrado pra não inventar): BOLETIM / NOTAS / avaliação / conceito do aluno;
FREQUÊNCIA / chamada / presença; DIÁRIO DE CLASSE / conteúdo programático / plano de aula; PARECER
PEDAGÓGICO estruturado (a IA nem dá parecer livre); PAGAMENTO REAL (Stripe é #50 — a mensalidade é
registro manual, igual academia); cálculo de inadimplência / juros / multa; CONTRATO / matrícula com
assinatura digital/PDF/e-sign; LISTA DE ESPERA estruturada (vaga cheia → a IA diz que avisa quando
abrir, mas SEM fila persistida — fase futura); transporte escolar / rota; material didático / loja;
cardápio da merenda; foto da criança / documento digitalizado (bloqueador SERVICE_ROLE_KEY); slot de
horário fino na visita (é dia + período, igual floricultura); multi-unidade / multi-campus;
calendário letivo / feriados; comunicado em massa aos pais. Fases futuras.

DECISÕES CRAVADAS (revisor decidiu pelo Igor):
1. CLONA o chassi da ACADEMIA (turmas = planos com capacity; matrícula = assinatura ativa/suspensa/
   cancelada; pagamento mensal manual; capacity transacional; anti-dupla). MANTER onde não conflita.
2. Aluno é SUB-ENTIDADE do contact (escola_students, contact_id NOT NULL, espelho pet_animals/
   nutri_patients). Matrícula referencia student_id. Anti-dupla = 1 matrícula ATIVA por (aluno,
   turma) via índice parcial.
3. Visita é entidade própria (escola_visits) com agenda LEVE: visit_date (>= hoje) + period
   (manha|tarde), SEM conflito de capacidade, SEM slot fino (espelho floricultura date+period). Status
   próprio agendada/realizada/cancelada (parity). A visita NÃO exige matrícula nem aluno (pode ter
   student_id nullable — a família pode visitar antes de escolher o filho/turma).
4. Mensalidade = pagamento MANUAL (escola_payments, UNIQUE (membership, reference_month)). SEM Stripe,
   SEM inadimplência. Espelho academia_payments.
5. Status da matrícula hardcoded (EscolaEnrollmentStatus enum Java + const TS + parity): ativa ⇄
   suspensa; ambas → cancelada (terminal). cancelada materializa end_date e libera a vaga (count
   filtra status <> 'cancelada'); SUSPENSA MANTÉM a vaga ocupada (decisão da academia, cravada).
6. Status da visita hardcoded (EscolaVisitStatus enum Java + const TS + parity): agendada → realizada,
   cancelada; realizada/cancelada terminais. Só confirmação/cancelamento notificam (texto defensivo).
7. DUAS tags namespace próprio, distintas de TODAS as outras (<matricula> da academia, <pedido_*>,
   <consulta_*>, <agendamento_*>, etc.): <matricula_escola> (registra interesse de matrícula; 2 modos:
   student_id existente OU new_student cadastra o aluno como sub-entidade do responsável E matricula no
   mesmo turno — espelho new_animal do pet) e <visita_escola> (agenda a visita). O backend valida,
   resolve o responsável da conversa e cria; o OutboundService REMOVE a tag antes de enviar.

[FUNDAÇÃO — migration 63_escola.sql]
- ALTER companies CHECK aceitar 'escola'. ATENÇÃO (lição cravada): clonar a migration por sed
  s/academia/escola/g TROCA 'academia' por 'escola' na lista do CHECK (remove os demais). Depois de
  qualquer clonagem, CONFERIR que o CHECK tem TODOS os perfis já presentes (ler o último CHECK
  aplicado no banco/migrations) + 'escola' — NENHUM nicho anterior pode sumir. ACRESCENTAR, nunca
  substituir.
- RLS enable+force, policies via app.company_id(), grants authenticated + service_role. Padrão das
  migrations 30-62 (academia 36 + pet 37 são as referências diretas):
  * escola_classes / escola_students / escola_config: CRUD pelo tenant (authenticated).
  * escola_enrollments / escola_visits: INSERT pelo BACKEND via service_role (criadas pela IA via
    handler); tenant SELECT/UPDATE (status no painel / gate humano de confirmação e mensalidade).
  * escola_payments: INSERT via service_role (registro pelo painel) ou authenticated conforme o
    padrão da academia_payments — espelhar academia_payments (tenant SELECT; mutação via backend).
- end_date / status materializados; total/valores em centavos. NÃO colunas geradas.
- SNAPSHOTS: a matrícula congela class_name + class_grade + class_shift + class_monthly_cents +
  student_name (snapshot do aluno no momento). Alterar a turma/aluno depois NÃO altera matrículas
  passadas (espelho academia: plan_name/plan_monthly_cents snapshot).
- Tabelas (TODAS dentro da migration 63 ANTES de tocar o banco — lição os_config):
  * escola_config — config 1:1 com company. (company_id PK, business_name nullable (nome da escola),
    opens_at/closes_at time (horário de funcionamento p/ informar nas visitas; defaults ex. 07:00/
    18:00), notes, timestamps). Ausente → defaults. Espelho academia_config + business_name.
  * escola_classes — TURMAS (espelho academia_plans + campos de turma). (id, company_id, name CHECK
    1..200 (ex. "Maternal II - Manhã"), grade text NOT NULL (série/ano — texto livre OU CHECK enum se
    o agente preferir; recomendo texto livre informativo: "Berçário", "Maternal I", "Pré I", "1º ano",
    …), shift text NOT NULL CHECK ('manha'|'tarde'|'integral'), capacity int CHECK between 1 and 200
    (vagas), monthly_cents int CHECK >= 0 (mensalidade), year int nullable (ano letivo, ex. 2026),
    description, active default true, timestamps). COMMENT cravando shift + capacity + monthly_cents.
  * escola_students — ALUNOS (sub-entidade do contact — espelho pet_animals). (id, company_id,
    contact_id NOT NULL references contacts on delete restrict (o responsável/pai), name CHECK 1..200,
    birth_date date nullable (data de nascimento), intended_grade text nullable (série pretendida —
    informativo), notes (administrativo, SEM dado pedagógico/de saúde — LGPD), active default true,
    timestamps). COMMENT cravando: aluno é SUB-ENTIDADE do responsável (contact); um contact tem N
    alunos; notes administrativo (sem parecer/diagnóstico).
  * escola_enrollments — MATRÍCULAS (assinatura — espelho academia_memberships, MAS referencia
    student_id em vez de só contato). (id, company_id, class_id refs escola_classes on delete restrict,
    student_id refs escola_students on delete restrict, conversation_id refs conversations on delete
    set null, contact_id refs contacts on delete set null (o responsável — pra notificar/snapshot),
    student_name text NOT NULL snapshot, responsible_name text nullable snapshot (nome do responsável/
    contato), class_name text NOT NULL snapshot, class_grade text NOT NULL snapshot, class_shift text
    NOT NULL snapshot, class_monthly_cents int NOT NULL snapshot, start_date date NOT NULL default
    current_date, end_date date nullable (materializado em cancelada), status text NOT NULL default
    'ativa' CHECK ('ativa'|'suspensa'|'cancelada'), notes, created_at, status_updated_at). Índices:
    (company_id, status, start_date desc); (contact_id, …) where contact_id not null; (student_id, …).
    ANTI-DUPLA (a escapada): unique index uniq_active_enrollment_per_student_class on
    escola_enrollments (company_id, student_id, class_id) where status = 'ativa' — 1 matrícula ativa
    do MESMO aluno na MESMA turma (um irmão pode estar em outra turma; o mesmo aluno pode estar em
    turmas diferentes — decisão: a unicidade é por (aluno, turma), não por aluno só). COMMENT cravando
    a regra de capacity transacional + anti-dupla por (aluno, turma) + snapshots.
  * escola_payments — MENSALIDADE manual (espelho academia_payments). (id, company_id, enrollment_id
    refs escola_enrollments on delete restrict, reference_month date NOT NULL (dia 01 do mês),
    paid_at timestamptz default now(), amount_cents int CHECK >= 0, method text, notes, created_at,
    unique (enrollment_id, reference_month)). Índice (company_id, reference_month desc).
  * escola_visits — VISITA agendada (a escapada 2; agenda leve dia+período — espelho floricultura
    date+period, NÃO slot fino). (id, company_id, conversation_id refs conversations on delete set
    null, contact_id refs contacts on delete set null (o responsável), student_id refs escola_students
    on delete set null (nullable — pode visitar antes de escolher o filho/turma), visitor_name text
    NOT NULL snapshot (nome do responsável), visitor_phone text nullable snapshot, visit_date date NOT
    NULL, period text NOT NULL CHECK ('manha'|'tarde'), num_people int nullable CHECK >= 1, status
    text NOT NULL default 'agendada' CHECK ('agendada'|'realizada'|'cancelada'), notes, created_at,
    status_updated_at). Índice (company_id, status, visit_date). COMMENT cravando: agenda LEVE (dia +
    período), SEM conflito de capacidade, SEM slot fino; visita independe de matrícula.
- Status da matrícula hardcoded (EscolaEnrollmentStatus enum Java + escola-enrollment-status.ts +
  EscolaEnrollmentStatusParityTest): ativa ⇄ suspensa; ambas → cancelada; cancelada terminal.
  Notifica: ativa (boas-vindas, com turma+série+turno), cancelada (despedida defensiva). suspensa
  silenciosa. cancelada materializa end_date e libera vaga (count filtra status <> 'cancelada');
  suspensa MANTÉM a vaga.
- Status da visita hardcoded (EscolaVisitStatus enum Java + escola-visit-status.ts +
  EscolaVisitStatusParityTest): agendada → realizada, cancelada; realizada/cancelada terminais.
  Notifica: agendada (confirmação, com data+período — quando criada via IA/painel), cancelada
  (defensiva). realizada silenciosa (ou conforme padrão dos outros perfis).
- shift e period como CHECK simples (decisão do agente: CHECK basta; se quiser enum+parity p/
  simetria, pode — recomendo CHECK pra shift/period e parity SÓ pros 2 status, igual academia/
  floricultura). intended_grade/grade texto livre informativo.
- Adicionar as 6 tabelas novas ao TRUNCATE/SCRIPTS do AbstractIntegrationTest.

[BACKEND]
- Classes (turmas): CRUD padrão (espelho academia_plans + campos grade/shift/capacity/monthly_cents).
  delete de turma com matrícula → 409 class_in_use; preferir desativar (active=false).
- Students (alunos — sub-entidade): CRUD (espelho pet_animals). create exige contact_id válido
  (responsável existente) → ContactNotFoundException se não. list por contato (os filhos de um
  responsável). delete de aluno com matrícula/visita → 409 student_in_use; preferir desativar.
- Config: GET (fallback defaults) + PUT.
- Enrollments (matrícula = assinatura, chassi academia_memberships):
  * create TRANSACIONAL: valida turma (ativa) + aluno (existe, ativo, pertence à company) + anti-dupla
    (sem matrícula ativa do mesmo aluno na mesma turma → AlreadyActiveException → 409 already_active)
    + capacity POR TURMA DENTRO da transação (count(matrículas não-canceladas na turma) + 1 <=
    capacity; estoura → ClassFullException → 409 class_full). Espelho EXATO do
    AcademiaMembershipRepository.insertMembership (checagem de vaga dentro da transação, defesa race).
    Snapshots completos (student_name/responsible_name/class_name/grade/shift/monthly_cents). Status
    inicial ativa + notificação de boas-vindas.
  * list/get/count; updateStatus (transição validada → 409 invalid_status_transition; cancelada
    materializa end_date = hoje no fuso America/Sao_Paulo e libera vaga; notifica ativa/cancelada).
- Payments (mensalidade manual, espelho academia_payments): registrar pagamento de um mês (UNIQUE
  (enrollment, reference_month) → 409 duplicate_payment); só em matrícula não-cancelada; list por
  matrícula. Summary opcional (último mês pago + meses em aberto) se quiser espelhar academia.
- Visits (a escapada 2): create (via IA/handler ou manual no painel): valida visit_date >= hoje (fuso
  America/Sao_Paulo) → PastDateException → 422 past_date; period in (manha|tarde); student_id opcional
  (se vier, valida que pertence à company/contato). SEM conflito de capacidade. Snapshots
  visitor_name/phone. Status inicial agendada + notificação de confirmação. updateStatus (agendada →
  realizada/cancelada; transição inválida → 409; notifica cancelada).
- Status: PATCH com validação de transição (matrícula e visita). Notificação outbound por status
  (texto defensivo — SEM promessa de vaga, SEM valor inventado, SEM parecer).
- IA:
  * Persona acolhedora-cuidadosa com a TRAVA DE COMPORTAMENTO embutida (não promete vaga, não define/
    inventa mensalidade/desconto/bolsa, não dá parecer pedagógico, não inventa turma/série/turno/
    professor/estrutura). Adicionar ESCOLA ao enum/switch de ProfilePromptContext (espelho da persona
    ACADEMIA).
  * Contexto injetado (EscolaContextCache, Caffeine TTL 60s — turmas mudam pouco, igual academia;
    keyed por (companyId, contactId) pra trazer os alunos do responsável): TURMAS ativas com VAGAS
    RESTANTES em tempo real (capacity − count não-canceladas) + série + turno + mensalidade cadastrada
    + os ALUNOS do responsável (filhos já cadastrados, com a turma atual se houver) + horário de
    funcionamento + instruções das 2 tags. Invalidação explícita em toda mutação (turma/aluno/
    matrícula/config/visita).
  * Tag <matricula_escola>{"class_id":"UUID","student_id":"UUID|null","new_student":{"name","birth_
    date":"YYYY-MM-DD|null","intended_grade":"...|null"}|null,"notes":"...|null"} →
    MatriculaEscolaConfirmHandler. DOIS modos (espelho AgendamentoPetConfirmHandler / new_animal):
    student_id (aluno já cadastrado do responsável) OU new_student (cadastra o aluno como sub-entidade
    do contato da conversa E matricula no mesmo turno). Resolve o responsável (contact) da conversa.
    O service valida turma/aluno/capacity/anti-dupla; qualquer falha → empty + warn. Best-effort.
  * Tag <visita_escola>{"visit_date":"YYYY-MM-DD","period":"manha|tarde","num_people":N|null,
    "student_id":"UUID|null","notes":"...|null"} → VisitaEscolaConfirmHandler. Resolve o responsável
    da conversa; valida data futura + período; cria a visita. Best-effort.
  * JwtFilter autentica /api/escola/. OutboundService ganha maybeProcessMatriculaEscola +
    maybeProcessVisitaEscola (best-effort, contactId via findContactIdByConversation, encadeados APÓS
    os outros perfis — perfil é único, só um age; REMOVE a tag antes de enviar). As 2 tags têm
    namespace distinto de <matricula> (academia) e de TODAS as outras.

[FRONTEND]
- /dashboard/escola-classes (CRUD turmas: nome + série/grade + turno (manha/tarde/integral) +
  capacity/vagas + mensalidade + ano letivo + ativar/desativar; mostra vagas ocupadas/total),
  /dashboard/escola-students (CRUD alunos POR RESPONSÁVEL: lista os alunos com o contato/responsável;
  cadastrar filho ligado a um contato; data de nascimento + série pretendida; notes administrativo),
  /dashboard/escola-enrollments (matrículas = assinaturas: lista por status; criar manual (escolhe
  aluno + turma, valida vaga/anti-dupla); ativar/suspender/cancelar; registrar mensalidade do mês
  (pagamento manual); detalhe com snapshots),
  /dashboard/escola-visits (visitas agendadas: lista por status/data; criar manual; marcar realizada/
  cancelada),
  /dashboard/escola-settings (nome da escola + horário de funcionamento + notas).
- types + SDKs (classes, students, enrollments, payments, visits) espelhando academia + pet (aluno
  sub-entidade). EscolaStudent: { id, contactId, name, birthDate, intendedGrade, notes, active, … }.
- Status TS escola-enrollment-status.ts (ativa/suspensa/cancelada, ALLOWED_NEXT, statusLabel) +
  escola-visit-status.ts (agendada/realizada/cancelada) + parity tests dos DOIS status. shift/period
  como const simples (sem parity obrigatório).
- getNavForProfile('escola') injeta "Escola" (5 itens: Turmas, Alunos, Matrículas, Visitas,
  Configurações), no mesmo padrão dos branches existentes (academia/pet já têm branch — seguir o
  modelo deles; NÃO cair no fallback como floricultura fez). Subdomínio escola.meadadigital.local.
  Paleta: agente escolhe — sugestão 'celeste', 'mostarda' ou 'coral' (clara/acolhedora; confirmar que
  o id existe em lib/themes/palettes.ts e não colide de forma confusa com outro perfil).
- npm build limpo.

[DOCS]
- CLAUDE.md: seção "## Perfil Escola (camada 8.19)" espelhando as seções de perfil + nota de que CLONA
  a ACADEMIA (matrícula-assinatura + capacity transacional + anti-dupla + mensalidade manual) e
  inaugura DOIS conceitos juntos: ALUNO como sub-entidade do RESPONSÁVEL (espelho pet/nutri) + VISITA
  agendada (agenda leve dia+período, espelho floricultura). Documentar EXPLÍCITO: turmas (série+turno+
  capacity+mensalidade); aluno sub-entidade (anti-dupla por (aluno, turma)); visita (sem conflito, sem
  slot fino); a trava de comportamento da IA (não promete vaga, não define mensalidade/bolsa, não dá
  parecer pedagógico); as 2 tags <matricula_escola> (2 modos) e <visita_escola>.
- docs/PERFIL_ESCOLA.md: guia operacional (turmas com vaga; cadastro de alunos por responsável;
  matrículas + mensalidade manual + estados; visitas; como a IA atende; o bloco "o que a IA NÃO faz").
  Espelhar PERFIL_ACADEMIA.md.
- NÃO mexer em system-template.txt nem em outros perfis.

[TESTES BACKEND]
Espelhar a suíte da academia + pet (service + controller integration por entidade):
- EscolaEnrollmentStatusParityTest + EscolaVisitStatusParityTest + ProfileTypeParityTest (escola no
  enum/const).
- EscolaClassServiceTest + ControllerIntegrationTest (CRUD turma; delete-em-uso 409 class_in_use;
  wrongProfile 403; invalida cache).
- EscolaStudentServiceTest + ControllerIntegrationTest [aluno sub-entidade] (CRUD aluno ligado a um
  contato; create sem contato válido → ContactNotFound; list por responsável; delete-em-uso 409
  student_in_use).
- EscolaConfigServiceTest/ControllerIntegrationTest (GET fallback + PUT).
- EscolaEnrollmentServiceTest [CHAVE da escapada 1]:
    * matrícula ativa nasce ativa + notifica boas-vindas; snapshots (student/class/grade/shift/
      monthly_cents) corretos.
    * CAPACITY TRANSACIONAL: turma com capacity N; N matrículas → OK; a (N+1)ª → 409 class_full.
    * ANTI-DUPLA por (aluno, turma): 2ª matrícula ATIVA do mesmo aluno na MESMA turma → 409
      already_active; o MESMO aluno em OUTRA turma → OK; OUTRO aluno (irmão) na mesma turma → OK
      (enquanto há vaga).
    * suspensa MANTÉM a vaga (count não libera); cancelada LIBERA (count filtra cancelada) + materializa
      end_date; transição inválida → 409 invalid_status_transition.
    * pagamento mensal: registrar mês → OK; mesmo mês 2× → 409 duplicate_payment.
- EscolaVisitServiceTest [CHAVE da escapada 2]:
    * agendar visita com data futura + período → agendada + notifica confirmação; data passada → 422
      past_date; período inválido → 4xx; student_id nullable → OK sem aluno; SEM conflito de
      capacidade (2 visitas no mesmo dia+período → ambas OK).
    * transição agendada→realizada/cancelada OK; inválida → 409; cancelada notifica.
- MatriculaEscolaConfirmHandlerTest [2 modos]: tag com student_id existente → matricula; tag com
  new_student → cadastra aluno (sub-entidade do contato) E matricula no mesmo turno; sem student_id
  nem new_student → empty; aluno/turma inválido, vaga cheia, anti-dupla → empty + warn; sem tag →
  empty; o OutboundService remove a tag.
- VisitaEscolaConfirmHandlerTest: tag válida → cria visita; data passada/período inválido → empty; sem
  tag → empty.
mvn final = relatar contagem REAL do Surefire (não estimar). Atenção ao pool Hikari de teste
(application-dev.yml min-idle 0/max 2 — lição SM-K): NÃO regredir; com mais um perfil há mais
ApplicationContexts.

[CONSTRAINTS DUROS]
- Migration única (63). Sem foto/anexo (bloqueador SERVICE_ROLE_KEY).
- Responsável (pai) = o contact (não vira entidade do core). O ALUNO é sub-entidade do contact
  (escola_students.contact_id NOT NULL). A matrícula referencia student_id.
- ESCAPADA 1: aluno sub-entidade do responsável; anti-dupla = 1 matrícula ATIVA por (aluno, turma)
  via índice parcial; capacity por turma validado DENTRO da transação (defesa race, espelho academia).
- ESCAPADA 2: visita = agenda LEVE (visit_date >= hoje + period manha|tarde), SEM conflito de
  capacidade, SEM slot fino; entidade própria com status próprio; independe de matrícula.
- Mensalidade = pagamento manual (UNIQUE por mês); SEM Stripe, SEM inadimplência/juros.
- Status materializados; snapshots de student/class na matrícula. suspensa mantém vaga; cancelada
  libera + materializa end_date.
- IA: NUNCA promete vaga não confirmada, NUNCA define/inventa mensalidade/desconto/bolsa, NUNCA dá
  parecer pedagógico, NUNCA inventa turma/série/turno/professor/estrutura. Só mostra turmas com vaga,
  agenda visita, registra interesse de matrícula.
- Tags <matricula_escola> (2 modos: student_id OU new_student) e <visita_escola> distintas de TODAS as
  outras (especialmente de <matricula> da academia).
- Categorias/turno/período hardcoded por CHECK (parity só pros 2 status). grade/intended_grade texto
  livre informativo.
- NÃO mexer em outros perfis nem em system-template.txt. Webhook OFF.
- Cache de contexto TTL 60s + invalidação em toda mutação (turma/aluno/matrícula/config/visita).
- 529 → inline. Gate 3× → pausar. Working tree sujo → pausar. git add EXPLÍCITO (nunca git add .);
  .env/CONTEXT.md/secrets NUNCA staged.
- SEED com timestamptz/date: `at time zone 'America/Sao_Paulo'` (lição do fuso). IDs de namespace com
  sufixo -17x NOVO (conferir os usados; evitar colisão FK).
- Tabela nova entra na migration ANTES de tocar o banco (LIÇÃO os_config: o banco se aplica A PARTIR
  do arquivo versionado — nunca criar tabela só no banco). Adicionar as 6 tabelas ao TRUNCATE/SCRIPTS
  do AbstractIntegrationTest.
- CHECK companies.profile_id: ACRESCENTAR 'escola' PRESERVANDO todos os perfis (NÃO substituir por
  sed). Conferir o último CHECK aplicado e que nenhum nicho sumiu após a clonagem.
- Decisões menores: agente decide (paleta, layout, ícones do nav, nomes de constante, se shift/period
  vira enum+parity ou fica só no CHECK).

[PASSO FINAL — TENANT + SEED + COMMIT + PUSH + SMOKE + RELATÓRIO]
F.1 — TENANT igorhaf30 (Escola Modelo, profile=escola), padrão GoTrue, senha em comunicação direta.
      company c?0000000-...-030 / user a?0000000-...-030. Caddy + /etc/hosts pra
      escola.meadadigital.local.
F.2 — Seed /tmp/seed-escola.sql (NÃO COMITAR; `at time zone 'America/Sao_Paulo'`; ids sufixo -17x):
  - config: business_name "Escola Modelo", horário 07:00–18:00.
  - 3 turmas:
    * "Maternal II - Manhã" (grade "Maternal II", shift 'manha', capacity 2 (pequena, pra smoke de
      class_full), monthly_cents R$650).
    * "Pré I - Tarde" (grade "Pré I", shift 'tarde', capacity 10, monthly R$700).
    * "Integral - Berçário" (grade "Berçário", shift 'integral', capacity 8, monthly R$1200).
  - contact "Marina Souza" +5511955554444 (RESPONSÁVEL, VINCULADO: instance+conversation, pra smoke de
    notificação) + contact "Pedro Alves" +5511944443333 (sem vínculo).
  - alunos (sub-entidade): Marina tem 2 filhos — "Lucas Souza" (birth_date apropriada p/ Maternal) e
    "Helena Souza" (irmã); Pedro tem 1 filho "Théo Alves".
  - matrículas cobrindo estados/escapadas:
    * 'ativa' VINCULADA: Lucas Souza na "Maternal II - Manhã" (pra smoke de boas-vindas + capacity:
      capacity 2, esta ocupa 1).
    * 'ativa': Théo Alves na "Maternal II - Manhã" (ocupa a 2ª vaga → turma CHEIA; smoke de class_full
      ao tentar uma 3ª).
    * 'suspensa': uma matrícula suspensa (pra smoke de que suspensa mantém a vaga).
    * 'cancelada' (histórico, end_date passado).
    * 1 mensalidade registrada (escola_payments) numa matrícula ativa, pra smoke de duplicate_payment.
  - 1 visita 'agendada' VINCULADA (Marina, data hoje+3d, período tarde) — pra smoke do funil de visita.
F.3 — JwtFilter /api/escola/ (se ainda não).
F.4-F.6 — git add EXPLÍCITO dos arquivos da SM + sanity (sem .env/secrets/CONTEXT) + commit.
      Mensagem padrão (feat(camada-8.19): perfil escola/Escola (Educação infantil · Escola) com
      FUNDAÇÃO/BACKEND/FRONTEND/DECISÕES/VALIDAÇÃO contagem REAL/NÃO TOCADO/FECHAMENTO + Co-Authored-By:
      Claude Opus 4.8). Tag fase-8.19-fechada (nº real confirmado no arranque).
F.7 — git push origin main + tags.
F.8 — docker compose restart backend + aguardar /admin/me → 401.
F.9 — Smoke E2E:
  BLOCO A: auth — igorhaf30 → /admin/me → role=tenant_admin, profileId=escola, productName=Escola.
  BLOCO B: catálogo + guard — GET classes (3 turmas com vagas); CRUD turma smoke + invalida cache;
    delete em uso → 409 class_in_use; GET config + PUT; tenant academia (ou outro) → /api/escola/classes
    → 403 forbidden_wrong_profile.
  BLOCO C: ALUNOS sub-entidade — GET students por responsável (filhos de Marina); cadastrar filho
    ligado a um contato; delete em uso → 409 student_in_use.
  BLOCO D: MATRÍCULA + CAPACITY + ANTI-DUPLA [CHAVE escapada 1] —
    - <matricula_escola>{student_id existente, class_id} → matrícula ativa + boas-vindas (Marina
      vinculada recebe msg). snapshots batem.
    - <matricula_escola>{new_student:{...}, class_id} → cadastra o aluno (sub-entidade do responsável)
      E matricula no mesmo turno (2º modo).
    - "Maternal II - Manhã" com capacity 2 já cheia (Lucas + Théo) → nova matrícula → 409 class_full.
    - mesma criança 2ª matrícula ATIVA na MESMA turma → 409 already_active; a MESMA criança em OUTRA
      turma → OK; OUTRO filho (irmão) na mesma turma (se houver vaga) → OK.
    - suspender uma matrícula → a vaga CONTINUA ocupada; cancelar → libera a vaga + end_date; transição
      inválida → 409.
    - mensalidade: registrar mês → OK; mesmo mês 2× → 409 duplicate_payment.
  BLOCO E: VISITA agendada [CHAVE escapada 2] —
    - <visita_escola>{visit_date hoje+3d, period tarde} → visita 'agendada' + msg de confirmação
      (Marina vinculada).
    - visit_date passado → 422 past_date; período inválido → 4xx; 2 visitas no mesmo dia+período →
      ambas OK (SEM conflito).
    - agendada→realizada OK; agendada→cancelada → msg defensiva; transição inválida → 409.
  BLOCO F: regressão — perfis anteriores intactos (smoke leve 1 endpoint cada); escola → /api/academia/*
    → 403; escola → /api/floricultura/* → 403.
  BLOCO G: paridade — mvn test -Dtest=EscolaEnrollmentStatusParityTest,EscolaVisitStatusParityTest,
    ProfileTypeParityTest → verde.
  BLOCO H: lição os_config — confirmar que as 6 tabelas existem PORQUE foram criadas DENTRO da migration
    63 (não só no banco); o seed roda limpo a partir do schema versionado; CHECK companies.profile_id
    tem TODOS os perfis + 'escola' (nenhum sumiu na clonagem).
  Cleanup smoke + restaurar seed pristine. mvn final: contagem REAL.
F.10 — RELATÓRIO + DESTAQUE EXPLÍCITO:
  - "perfil escola/educação infantil (camada 8.19) — CLONA a ACADEMIA (matrícula-assinatura + capacity
     transacional + anti-dupla + mensalidade manual)"
  - "ESCAPADA 1: o ALUNO é sub-entidade do RESPONSÁVEL (contact); 1 responsável → N filhos; anti-dupla
     por (aluno, turma); capacity por turma validado DENTRO da transação"
  - "ESCAPADA 2: VISITA agendada (agenda leve dia+período, espelho floricultura; sem conflito, sem slot
     fino; entidade própria; independe de matrícula)"
  - "BLOCO D prova capacity transacional (class_full), anti-dupla por (aluno, turma) e os 2 modos da
     tag <matricula_escola> (student_id e new_student)"
  - "BLOCO E prova a visita agendada (past_date, sem conflito, funil de status)"
  - "trava: IA não promete vaga, não define mensalidade/bolsa, não dá parecer pedagógico"
  - "tags <matricula_escola> (2 modos) e <visita_escola> distintas de <matricula> da academia e de
     todas as outras"
  - "Seed: at time zone + sufixo de ids -17x novo; as 6 tabelas DENTRO da migration 63 (lição os_config)"
  - PENDÊNCIAS: boletim/notas/frequência/diário de classe, parecer pedagógico estruturado, pagamento
     real (Stripe) + inadimplência, contrato e-sign, lista de espera persistida, transporte/material/
     merenda, foto/documento + a dívida acumulada (webhook, cliente real, olho humano sobre os
     verticais).

[REPORTAR]
Igual SMs anteriores. Incluir EXPLICITAMENTE:
- "ProfileType.ESCOLA adicionado (camada 8.19)"
- "Paridade EscolaEnrollmentStatus, EscolaVisitStatus e ProfileType validadas"
- "Tenant igorhaf30 criado (GoTrue + Caddy/etc/hosts)"
- "ESCAPADA 1: aluno sub-entidade do responsável; anti-dupla por (aluno, turma); capacity transacional"
- "ESCAPADA 2: visita agendada (dia+período, sem conflito, sem slot fino; entidade própria)"
- "Mensalidade = pagamento manual (UNIQUE por mês); sem Stripe"
- "Tags <matricula_escola> (2 modos: student_id OU new_student) e <visita_escola> distintas de TODAS"
- "OutboundService ganhou maybeProcessMatriculaEscola + maybeProcessVisitaEscola"
- "JwtFilter autentica /api/escola/"
- "getNavForProfile('escola') com branch próprio (não repetir o gap do floricultura)"
- "Aluno é sub-entidade do contact; responsável = o contact (não vira entidade do core)"
- "Cache de contexto TTL 60s + invalidação em toda mutação"
- "as 6 tabelas criadas DENTRO da migration 63 (lição os_config); CHECK preservou todos os perfis;
   seed com at time zone + sufixo -17x novo"
- "Próximas fases: boletim/notas/frequência, parecer pedagógico, Stripe + inadimplência, contrato
   e-sign, lista de espera + fila de prioridade (webhook, cliente real, olho humano sobre os verticais)"
