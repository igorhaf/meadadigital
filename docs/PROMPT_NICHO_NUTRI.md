>>> JÁ IMPLEMENTADO — perfil nutri, camada 8.0, migration 39_nutri.sql. Prompt de nicho RETROATIVO,
>>> formato T5. Fonte: CLAUDE.md seção Perfil Nutri + migration 39 + docs/PERFIL_NUTRI.md.

[TAREFA — PERFIL NUTRI / NutriBot (camada 8.0) — RETROATIVO]

Documento RETROATIVO: este perfil JÁ ESTÁ IMPLEMENTADO e fechado. O texto abaixo reconstrói, no
formato T5 ("prompt de nicho"), o que de fato foi construído — não é um plano a executar. DÉCIMO perfil
vertical real e PRIMEIRA sub-maratona da camada 8.x. Migration `39_nutri.sql`. Tenant de referência
`igorhaf` (consultório de nutrição). Fonte de verdade: a seção "## Perfil Nutri (NutriBot, camada
8.0)" do CLAUDE.md, a migration 39, o guia `docs/PERFIL_NUTRI.md` e o código em
`src/main/java/com/meada/profiles/nutri/`.

[CONTEXTO]
PROJETO MEADA em /home/igorhaf/meada.
O tenant nutri (`profile_id='nutri'`) vira um produto de CONSULTÓRIO DE NUTRIÇÃO: gerencia
nutricionistas, pacientes e os planos alimentares, e a IA atende pacientes via WhatsApp com tom
acolhedor e profissional — AGENDA consultas e ENTREGA o plano alimentar que o nutricionista gravou.
A IA conhece os profissionais, os pacientes de cada contato (com objetivo e flag de plano ativo) e
os horários livres. Identifica o contato pelo telefone, resolve o paciente, agenda no horário livre
do profissional e, quando solicitado, entrega o texto exato do plano ativo.

>>> ===================================================================================== <
>>> TRAVA DE SEGURANÇA CLÍNICA — O CORAÇÃO DESTA SM (inegociável)                          <
>>> ===================================================================================== <
Plano alimentar individualizado é conduta PRIVATIVA do nutricionista (CFN/CRN). A IA, em NENHUMA
hipótese:
  - cria / calcula / monta / adapta / resume / ajusta plano alimentar;
  - dá caloria, macro, porção ou QUALQUER número nutricional;
  - responde "posso comer X?", "quantas calorias tem Y?", "isso engorda?";
  - opina sobre patologia, suplementação, emagrecimento ou restrição.
Para QUALQUER dúvida nutricional → orienta agendar consulta com o nutricionista. A trava vive em
DOIS lugares: na PERSONA (`ProfilePromptContext.NUTRI` + bloco de INSTRUÇÕES do `NutriContextCache`)
E no SCHEMA (a IA NÃO tem policy de escrita de plano; o `body` só é LIDO na entrega). Defesa em
profundidade: mesmo que a persona falhasse, a IA não tem caminho de INSERT/UPDATE de plano.

>>> GUARDA DE TRANSTORNO ALIMENTAR (permanente, em TODA a conversa) <
Se o paciente sinalizar restrição intensa, compulsão, purga, contagem obsessiva, peso-meta extremo
ou sofrimento com comida/corpo, a IA NÃO dá número, NÃO valida a conduta, ACOLHE sem reforçar e
encaminha ao nutricionista (e, havendo risco, sugere apoio profissional). NUNCA fornece técnica de
restrição/compensação.

EVOLUÇÃO ESTRUTURAL (o que esta SM inaugurou no projeto):
  - COMBINA agenda (espelho dental/salon: conflito POR PROFISSIONAL) + sub-entidade de cliente
    (espelho pet/oficina), com DUAS escapadas novas:
  - ESCAPADA 1 — DOIS NÍVEIS DE SUB-ENTIDADE (aninhamento): o PACIENTE (`nutri_patients`) é
    sub-entidade do `contact` (nível 1); o PLANO (`nutri_plans`) é sub-entidade do PACIENTE (nível
    2). Primeiro perfil do projeto com sub-entidade ANINHADA. Um contato (ex.: um responsável) pode
    ter N pacientes (ele e um filho); cada paciente tem seu histórico de planos.
  - ESCAPADA 2 — ARTEFATO READ-ONLY-PRA-IA: `nutri_plans.body` é escrito SÓ pelo profissional no
    painel. A IA tem um modo de ENTREGA (envia o texto gravado VERBATIM) mas NUNCA o edita, resume
    ou adapta. Primeiro perfil em que a IA serve um artefato textual sem ter participado da geração
    dele.

DECISÕES CRAVADAS (reais, refletidas no código):
1. Paciente NÃO é entidade do core — continua o `contact`; `nutri_patients.contact_id` é a verdade.
   Snapshots `patient_name`/`patient_phone` ficam na consulta.
2. `goal` / `dietary_restrictions` / `notes` são texto livre ADMINISTRATIVO, SEM número nutricional
   (LGPD: dado clínico estruturado é fase futura, com cripto at-rest).
3. 1 plano 'ativo' por paciente (índice parcial UNIQUE). Criar/ativar um plano ARQUIVA o anterior
   NA MESMA transação (`NutriPlanService`).
4. Conflito de agenda é POR PROFISSIONAL (`professional_id`), espelho dental/salon. `end_at`
   materializado no INSERT (`start_at + duration_minutes`), NÃO coluna gerada (lição timestamptz +
   interval não é IMMUTABLE).
5. Entrega de plano só sai para o contato DONO do paciente (barreira de contato): o
   `EntregaPlanoHandler` compara `patient.contactId() == conversation.contactId`. Bloqueia
   vazamento de plano de um paciente para o contato errado.
6. Excluir vs arquivar: paciente com consulta OU plano → 409 `patient_in_use`; preferir arquivar
   (`active=false`, não perde histórico).
7. Cancelamento via IA NÃO move plano nem cria conduta; a IA só AGENDA e ENTREGA — nada além.

[FUNDAÇÃO — migration 39_nutri.sql]
- ALTER `companies` CHECK aceitar 'nutri' (10º perfil real; 11º contando generic), PRESERVANDO
  todos os anteriores: ('generic','legal','dental','sushi','restaurant','salon','pousada',
  'academia','pet','oficina','nutri').
- RLS enable + force em todas as tabelas; policies via `app.company_id()`; grants `authenticated` +
  `service_role`. Tabelas que a IA cria/serve (`nutri_appointments`, `nutri_plans`): INSERT pelo
  BACKEND via service_role; tenant tem só SELECT/UPDATE (sem policy de INSERT para `authenticated`).
- Tabelas:
  * `nutri_professionals` — nutricionistas (catálogo). name (1..200), specialty (texto livre), `crn`
    (registro profissional, nullable, texto livre), active, notes, timestamps. Índices por
    (company, active) e (company, name). Conflito de agenda é por profissional. active=false retira
    da disponibilidade da IA.
  * `nutri_config` — horário de funcionamento 1:1 com company (espelho pet_config): opens_at
    (08:00), closes_at (18:00), buffer_minutes (>=0, default 0), timestamps. Ausente → defaults.
  * `nutri_patients` — pacientes, SUB-ENTIDADE do contact (nível 1). `contact_id` NOT NULL
    (references contacts on delete restrict — o cliente), name (1..120), goal (texto livre SEM
    cálculo), dietary_restrictions (texto livre administrativo), birth_date, notes, active
    (false=arquivado). Índices por (company, contact, active) e (company, name).
  * `nutri_plans` — planos alimentares, SUB-ENTIDADE do paciente (nível 2). `patient_id` NOT NULL
    (on delete restrict), `professional_id` nullable (on delete set null), title (1..200), `body`
    text NOT NULL (markdown livre, escrito SÓ pelo profissional — a IA NÃO edita), starts_on/ends_on
    (date, opcionais), status CHECK in ('ativo','arquivado') default 'ativo', notes, timestamps.
    ÍNDICE PARCIAL UNIQUE `uniq_active_plan_per_patient (patient_id) where status='ativo'` — garante
    1 plano ativo por paciente. Grants apenas SELECT/UPDATE a authenticated (sem INSERT: a IA só LÊ
    na entrega; o profissional cria via service_role/painel).
  * `nutri_appointments` — consultas (agenda). `professional_id` NOT NULL (on delete restrict),
    `patient_id` NOT NULL, `contact_id` nullable (snapshot/atalho), `conversation_id` nullable,
    SNAPSHOTS `patient_name`/`patient_phone`/`professional_name`, `appointment_type` CHECK in
    ('primeira','retorno','avaliacao'), duration_minutes (snapshot), start_at, `end_at`
    MATERIALIZADO no INSERT (não generated), status CHECK in
    ('agendado','confirmado','realizado','cancelado','falta') default 'agendado', notes,
    status_updated_at. Índice CRÍTICO do conflito: `(professional_id, start_at) where status in
    ('agendado','confirmado')` — só status bloqueantes, por profissional.
- Status hardcoded materializado (`NutriAppointmentStatus` enum Java ↔ `nutri-appointment-status.ts`
  const, `NutriAppointmentStatusParityTest`):
    `agendado → confirmado, cancelado`
    `confirmado → realizado, cancelado, falta`
    `realizado / cancelado / falta → terminal`
  Transição inválida → 409 `invalid_status_transition`. NOTIFICAM o paciente: **confirmado** (texto
  com tipo de consulta + profissional + data/hora) e **cancelado** (texto acolhedor, SEM conteúdo
  nutricional). agendado/realizado/falta são silenciosos (quem furou não recebe sermão).
- TODAS as tabelas novas entram na migration 39 ANTES de tocar o banco (lição os_config) e no
  TRUNCATE/SCRIPTS do `AbstractIntegrationTest`.
- LIÇÃO DE TESTE CRAVADA (SM-K): com ~11 perfis, cada `*ServiceTest` com `@Import(TestConfig)` é um
  ApplicationContext distinto; o pool Hikari padrão (min-idle 5 / max 10) × ~18 contextos estourava
  o teto de conexões do pooler Supabase (`CannotGetJdbcConnection` no `StartupDatabaseCheck`). FIX:
  `src/test/resources/application-dev.yml` com pool minúsculo (min-idle 0 / max 2) — só nos testes,
  não toca dev/prod.

[BACKEND]
Código em `src/main/java/com/meada/profiles/nutri/` (subpacotes professionals/config/
patients/plans/appointments).
- CRUD de profissionais, config (GET com fallback defaults + PUT), pacientes e planos — espelho dos
  outros perfis. `NutriPatientService`: excluir paciente com consulta/plano → 409. `NutriPlanService`:
  criar/ativar plano arquiva o anterior na mesma transação; expõe `getActiveByPatient`.
- Agenda (`NutriAppointmentService`): cria consulta com conflito por profissional re-verificado
  DENTRO da transação; valida horário (fuso America/Sao_Paulo, HARDCODED — pendência), tipo
  (primeira/retorno/avaliacao), profissional/paciente existentes e ativos. PATCH de status com
  validação de transição (409). POST manual pelo tenant não tem `conversation_id` → não notifica.
- TAG `<consulta_nutri>` — `AgendamentoNutriConfirmHandler` com 2 MODOS:
  * `patient_id` existente → agenda direto;
  * `new_patient`{name, goal?} → cadastra o paciente (sub-entidade do contato da conversa) E agenda
    no mesmo turno (cadastro disparado pela IA: actor nulo na auditoria).
  Formato: `<consulta_nutri>{"professional_id","appointment_type","date":"YYYY-MM-DD","start_time":
  "HH:MM","patient_id"|"new_patient":{...},"notes?"}</consulta_nutri>`. NÃO usa tool calling /
  responseSchema (restrição da Gemini — mesma de todos os perfis). Qualquer falha (JSON inválido,
  campos faltando, conflito, fora do horário, tipo inválido, profissional/paciente inativo) →
  `Optional.empty()` + warn; a mensagem da IA segue sem consulta.
- TAG `<entrega_plano>` — `EntregaPlanoHandler` (o padrão NOVO de ENTREGA READ-ONLY):
  `<entrega_plano>{"patient_id"}</entrega_plano>`. Busca o plano ATIVO do paciente e envia o `body`
  VERBATIM como mensagem outbound SEPARADA (`notifier.sendText`) — NÃO passa pela geração da IA (pra
  não ser reescrito/resumido). BARREIRA DE CONTATO: só entrega se `patient.contactId() == contactId`
  da conversa (impede vazar plano de outro paciente). Sem plano ativo / paciente de outro contato /
  patient_id inválido / envio falho → `Optional.empty()` + warn (a IA foi instruída a oferecer
  agendamento).
- `NutriContextCache` — TTL 20s, keyed por (companyId, contactId). Injeta no prompt: profissionais
  ativos, pacientes do contato (com objetivo + FLAG de plano ativo + última consulta), slots livres
  por profissional, e o bloco de INSTRUÇÕES com as 2 tags + a TRAVA. NÃO injeta o `body` do plano
  (segurança) — só a indicação de quais pacientes têm plano ativo. Invalidação explícita em toda
  mutação (profissional/paciente/plano/config/consulta).
- `NutriProfileGuard.requireNutri` — endpoints `/api/nutri/**` retornam 403 `forbidden_wrong_profile`
  para tenant de outro perfil. O `JwtAuthenticationFilter` autentica `/api/nutri/**` (além dos 9
  perfis anteriores).
- `OutboundService` ganhou `maybeProcessConsultaNutri` + `maybeProcessEntregaPlano` (encadeados
  após os perfis anteriores; perfil é único, só um age). Best-effort; remove a tag antes de enviar
  ao cliente.

[FRONTEND]
- `/dashboard/nutri-professionals` — CRUD de nutricionistas (nome, especialidade, CRN, observações;
  ativo/inativo; excluir bloqueado se houver consultas).
- `/dashboard/nutri-patients` — CRUD de pacientes vinculados a um contato (nome, objetivo, restrições,
  data de nascimento, observações; arquivar preferido a excluir; excluir bloqueado se houver
  consulta/plano).
- `/dashboard/nutri-plans` — selecionar paciente → gerenciar planos. EDITOR: title + body markdown
  livre (o texto exato que a IA entregará) + vigência (início/fim opcionais) + profissional
  responsável (opcional) + ativar/arquivar. Apenas 1 plano ativo por paciente (criar/reativar
  arquiva o anterior automaticamente; histórico preservado).
- `/dashboard/nutri-appointments` — agenda: lista por dia com filtro de status e profissional; nova
  consulta manual (paciente + profissional + tipo + data/hora; avisa conflito); transições de status
  (confirmar/cancelar notificam o paciente se veio do WhatsApp).
- `/dashboard/nutri-settings` — horário de funcionamento + intervalo (buffer) entre consultas.
- `getNavForProfile('nutri')` injeta o grupo "Nutri" (Profissionais / Pacientes / Planos / Agenda /
  Configurações). SDKs em `frontend/lib/api/nutri`; const de status em `frontend/profiles/nutri/
  nutri-appointment-status.ts`. `npm run build` limpo.

[DOCS]
- CLAUDE.md: seção "## Perfil Nutri (NutriBot, camada 8.0)" com a TRAVA de segurança clínica, a
  guarda de transtorno alimentar, os dois níveis de sub-entidade, o artefato read-only, as duas tags
  e as decisões cravadas.
- `docs/PERFIL_NUTRI.md`: guia operacional do tenant (nutricionistas, pacientes, planos com editor,
  agenda, configurações, como a IA atende, "a linha que a IA NÃO cruza", "o que o NutriBot NÃO faz").
- NÃO mexer em system-template.txt nem em outros perfis.

[TESTES BACKEND]
Em `src/test/java/com/meada/profiles/nutri/`:
- `NutriAppointmentStatusParityTest` + `ProfileTypeParityTest` (paridade Java↔TS).
- `NutriProfessionalServiceTest` / `NutriProfessionalControllerIntegrationTest`.
- `NutriPatientServiceTest` / `NutriPatientControllerIntegrationTest` (excluir-em-uso → 409).
- `NutriPlanServiceTest` / `NutriPlanControllerIntegrationTest` (1 ativo por paciente; ativar arquiva
  o anterior; getActiveByPatient).
- `NutriAppointmentServiceTest` / `NutriAppointmentControllerIntegrationTest` (conflito por
  profissional; transições de status 409; notificação confirmado/cancelado; wrongProfile 403).
- `AgendamentoNutriConfirmHandlerTest` (tag 2 modos: patient_id e new_patient; conflito/horário/tipo
  inválido → empty; sem tag → empty).
- `EntregaPlanoHandlerTest` [CHAVE da escapada read-only]: entrega o body VERBATIM do plano ativo;
  barreira de contato (paciente de outro contato → não entrega); sem plano ativo → empty; patient_id
  inválido/faltando → empty; envio falho → empty; a IA NUNCA gera/edita o body.
mvn final = relatar a contagem REAL do Surefire (não `grep @Test`).

[CONSTRAINTS DUROS]
- Migration única (39). Sem foto/anexo (bloqueador SERVICE_ROLE_KEY).
- Cliente NÃO é entidade do core — paciente é sub-entidade do contact (nível 1); plano é
  sub-entidade do paciente (nível 2). Sub-entidade ANINHADA.
- TRAVA DE SEGURANÇA CLÍNICA: a IA nunca cria/calcula/ajusta plano, nunca dá número nutricional,
  nunca responde "posso comer X", nunca opina patologia/suplementação. Persona + schema.
- GUARDA DE TRANSTORNO ALIMENTAR ativa em toda a conversa.
- `nutri_plans.body` é READ-ONLY-PRA-IA: escrito só pelo profissional; entregue VERBATIM, fora da
  geração da IA. BARREIRA DE CONTATO na entrega.
- 1 plano ativo por paciente (índice parcial UNIQUE); novo ativo arquiva o anterior na transação.
- Conflito de agenda por profissional. `end_at` materializado no INSERT (não generated).
- Status hardcoded (parity). Tags `<consulta_nutri>` e `<entrega_plano>` distintas de TODAS as
  outras. NÃO usa tool calling.
- `goal`/`dietary_restrictions`/`notes` administrativos, SEM número nutricional (LGPD).
- NÃO mexer em outros perfis nem em system-template.txt. Webhook OFF.
- Cache de contexto TTL 20s + invalidação em toda mutação. NÃO injeta o body do plano.
- Fix do pool Hikari de teste em `application-dev.yml` (min-idle 0 / max 2) — só testes.
- Tabela nova entra na migration ANTES de tocar o banco; adicionar ao TRUNCATE do
  `AbstractIntegrationTest`. git add EXPLÍCITO; .env/CONTEXT.md/secrets NUNCA staged.

[PASSO FINAL — resumido]
- TENANT de nutrição (profile=nutri), padrão GoTrue; Caddy + /etc/hosts para o subdomínio do nicho.
- Seed (NÃO comitar; `at time zone 'America/Sao_Paulo'`; ids de namespace com sufixo novo):
  nutricionista(s), config de horário, paciente(s) vinculado(s) a contato com plano ativo (body
  markdown), e consultas cobrindo estados (agendado/confirmado).
- `JwtAuthenticationFilter` autentica `/api/nutri/**`.
- git add EXPLÍCITO + sanity (sem .env/secrets/CONTEXT) + commit semântico (feat(camada-8): perfil
  nutri/NutriBot) com trailer Co-Authored-By: Claude Opus 4.8. Tag `fase-8.0-fechada`. Push origin
  main + tags.
- Restart backend + `/admin/me` → 401 (sanity). Smoke E2E: auth (profileId=nutri); guard
  (/api/nutri → 403 para outro perfil); agendar via `<consulta_nutri>` (2 modos); entregar via
  `<entrega_plano>` (body verbatim + barreira de contato + sem plano → não entrega); transições de
  status + notificação confirmado/cancelado; regressão dos perfis anteriores; paridade
  (NutriAppointmentStatusParityTest, ProfileTypeParityTest). mvn final = contagem REAL.

[REPORTAR]
Incluir EXPLICITAMENTE:
- "ProfileType.NUTRI adicionado (camada 8.0) — 10º perfil real, 1º da camada 8.x"
- "TRAVA DE SEGURANÇA CLÍNICA (CFN/CRN): IA não cria/calcula/ajusta plano, não dá número, não opina
  patologia/suplementação — persona + schema (sem policy de escrita de plano para a IA)"
- "GUARDA DE TRANSTORNO ALIMENTAR ativa em toda a conversa"
- "EVOLUÇÃO ESTRUTURAL: DOIS níveis de sub-entidade (paciente do contact; plano do paciente) —
  sub-entidade aninhada (1ª vez no projeto)"
- "ARTEFATO READ-ONLY-PRA-IA: nutri_plans.body entregue VERBATIM via <entrega_plano>, fora da geração
  da IA + barreira de contato (só o dono do paciente)"
- "1 plano ativo por paciente (índice parcial UNIQUE); novo ativo arquiva o anterior na transação"
- "Conflito de agenda por profissional; end_at materializado no INSERT (não generated)"
- "Tags <consulta_nutri> (2 modos: patient_id / new_patient) e <entrega_plano> distintas de todas as
  outras; OutboundService ganhou maybeProcessConsultaNutri + maybeProcessEntregaPlano"
- "NutriContextCache TTL 20s; NÃO injeta o body do plano; invalidação em toda mutação"
- "Paridade NutriAppointmentStatus + ProfileType validadas; EntregaPlanoHandlerTest cobre a entrega
  read-only + barreira de contato"
- "Lição de teste: pool Hikari minúsculo em application-dev.yml (min-idle 0/max 2) p/ não estourar o
  pooler Supabase com ~18 contextos de teste"
- "tabelas criadas DENTRO da migration 39 (lição os_config); seed com at time zone + sufixo de ids
  novo"
- PENDÊNCIAS: plano estruturado em refeições/porções, cálculo de TMB/macro/caloria (PROIBIDO por
  segurança), TACO/USDA, antropometria com gráfico, prescrição de suplemento, anamnese estruturada,
  foto (bloqueador SERVICE_ROLE_KEY), pagamento real (Stripe #50), bioimpedância, fuso hardcoded.
