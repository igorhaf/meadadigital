>>> SLOT ATRIBUÍDO (ver docs/prompts-nicho/README.md — fonte única de verdade, tem precedência
>>> sobre qualquer "provisório" no corpo): ordem 9 · camada 8.14 · migration 58_atelie.sql ·
>>> tenant igorhaf25 (company/user sufixo -025) · ids de seed sufixo -12x. Reconfirmar no arranque
>>> que a fila não avançou; se avançou, deslocar conforme o README.

[TAREFA — SUB-MARATONA: PERFIL ATELIE / Ateliê (Costura sob medida · Arte · Design) (camada 8.14)]

[CONTEXTO]
PROJETO MEADA em /home/igorhaf/meada.
Hoje no enum: … comida 8.4, floricultura 8.5, pizzaria 8.6 (último declarado no disco) + generic; a
fila do README acrescenta casamento 8.7 … projetos 8.13. ESTE perfil (Ateliê) é o PRÓXIMO da fila,
camada 8.14 — o 19º perfil vertical real (20º com generic), assumindo que pizzaria…projetos já
fecharam. Lê CONTEXT.md e o filesystem no arranque pra cravar convenções, nº de migration REAL,
contagem do Surefire e numeração de tenant ANTES de escrever qualquer código. NÃO hardcodar a
contagem do mvn — relatar a REAL do Surefire ao final. Valores do slot (CONFIRMAR no filesystem
antes; se a fila avançou, deslocar conforme o README): migration 58_atelie.sql (CONFERIR o maior nº
presente em supabase/migrations/ e usar o PRÓXIMO livre se 57 não estiver lá), tenant igorhaf25
(CONFERIR qual usuário já existe no Supabase e usar o PRÓXIMO livre), company c?0000000-...-025,
user a?0000000-...-025. IDs de namespace compartilhado (contacts/instance/conversation) NO SEED com
sufixo -12x, conferindo que NÃO colida com nenhum seed anterior.

Ateliê é template de nicho pra ATELIÊ DE COSTURA SOB MEDIDA / ESTÚDIO DE ARTE / ESTÚDIO DE DESIGN —
UM perfil ÚNICO (NÃO três perfis). Os três tipos de negócio compartilham o MESMO chassi: peça/obra
SOB ENCOMENDA personalizada (vestido sob medida, arte/quadro, peça de design gráfico) → briefing →
orçamento → aprovação → execução com provas/ajustes. Tenant acessa atelie.meadadigital.local e vê o
produto "Ateliê". A IA atende clientes via WhatsApp, identifica pelo telefone, ABRE uma proposta a
partir do briefing (tipo de peça/obra, ocasião, medidas/dimensões aproximadas, referência descrita
em texto), a equipe monta o ORÇAMENTO no painel, e a IA CAPTURA a aprovação/recusa em 2 fases. Tom
prestativo-consultivo e sensível de quem cria sob encomenda (costureira/alfaiate/artista/designer),
sem prometer o que a equipe não cravou.

>>> TRAVA DE COMPORTAMENTO DA IA (cravada — o que a IA NUNCA faz) <<<
- NUNCA fecha contrato, preço ou desconto por conta própria — quem orça e fecha é a equipe no
  painel.
- NUNCA confirma um PRAZO de entrega/produção nem uma MEDIDA/DIMENSÃO que a equipe não tenha cravado
  — diz "vou confirmar prazo e medidas com a equipe na primeira prova/avaliação".
- NUNCA inventa material, tecido, técnica, acabamento, valor, item de orçamento ou serviço que não
  esteja cadastrado.
- NUNCA promete resultado estético ("seu vestido vai ficar perfeito", "a arte vai te emocionar"),
  durabilidade ou caimento que dependa de medição/prova presencial — acolhe a ideia sem criar
  expectativa fora do controle da equipe.
- NUNCA gerencia as ETAPAS DE PROVA/AJUSTE da peça pela conversa — as provas (1ª prova, 2ª prova,
  ajuste final, entrega) são marcadas e transicionadas pela equipe no painel. A IA SÓ abre a
  proposta e captura a aprovação.

EVOLUÇÃO ESTRUTURAL: CLONA o chassi do PROJETOS (camada 8.13) / EVENTOS (8.2) / CASAMENTO (8.7) —
proposta order-based + itens de orçamento (total materializado) + gate de aprovação em 2 fases via
tag que muta o estado de um artefato existente. Cliente NÃO é entidade (continua o contact;
snapshots na proposta). UMA escapada nova, prima das ETAPAS do projetos mas focada em PROVAS:

  ESCAPADA — SUB-ENTIDADE NOVA: ETAPAS DE PROVA/AJUSTE (atelie_fittings). Uma peça sob medida tem
  PROVAS MARCADAS ao longo da produção, cada uma com DATA PREVISTA, ORDEM e status BINÁRIO
  (pendente/realizada). Ex.: "1ª prova" → "2ª prova" → "ajuste final" → "entrega". É uma SEQUÊNCIA
  ORDENADA DE PROVAS que acompanha a confecção da peça. Espelha a estrutura de milestones do
  PROJETOS (sub-entidade com title + due_date + position + status, ordenada por position, distinta
  dos itens de orçamento, gerenciada SÓ no painel sem tag de IA), com DUAS diferenças cravadas:
    - O status da PROVA é BINÁRIO (pendente/realizada) — NÃO os 3 estados (pendente/em_andamento/
      concluida) das etapas de execução do PROJETOS. Uma prova ou aconteceu ou não.
    - A semântica é PROVA DE ROUPA / AJUSTE / MARCO DE ENTREGA (não "produção/obra"). Para os tipos
      arte/design a mesma estrutura serve como marcos de aprovação visual ("esboço aprovado",
      "arte final", "entrega"), mas o vocabulário-base é de provas de costura.
  Cada proposta também tem um project_type (costura|arte|design) HARDCODED com parity — o MESMO
  perfil serve os três; o tipo é um CAMPO da proposta, NÃO um perfil separado. As provas são
  gerenciadas SÓ no painel (SEM tag de IA, igual o cronograma do eventos / as etapas do projetos).

NÃO TEM nesta SM (registrado pra não inventar): conflito de agenda/data (não há agenda — a peça não
disputa slot; datas das provas são previsões livres), catálogo de tecidos/materiais/técnicas pré-
cadastrados (orçamento ad-hoc, a equipe digita os itens), tabela de medidas estruturada do cliente
(ombro/busto/cintura/quadril como colunas — o briefing é texto livre; medidas estruturadas é fase
futura), contrato com assinatura digital/PDF/e-sign (o "contrato" é o estado 'fechada'), pagamento/
sinal/parcelas (Stripe é #50, fase futura), foto/anexo de referência/croqui/render/arte (bloqueador
SERVICE_ROLE_KEY — foto/anexo off), lembrete automático de data prevista de prova (a data é
informativa; scheduler é fase futura), múltiplos artesãos com agenda/conflito por profissional
(catálogo SIMPLES, atribuição opcional — igual project_planners/event_planners). Fases futuras.

DECISÕES CRAVADAS (revisor decidiu pelo Igor):
1. UM perfil ÚNICO 'atelie' serve costura sob medida + arte + design. O tipo é project_type (CAMPO
   da proposta), NÃO três perfis. project_type é HARDCODED com parity test Java↔TS
   (costura|arte|design).
2. Proposta = artefato central, espelho do project_proposals/event_proposals (order-based, total
   materializado, snapshots de cliente, gate de aprovação em 2 fases). MANTER.
3. ETAPAS DE PROVA/AJUSTE (atelie_fittings) = a escapada da SM. Sub-entidade NOVA, distinta dos
   itens de ORÇAMENTO (que entram no total). Cada prova tem: title, due_date (NULLABLE — previsão),
   status BINÁRIO (pendente/realizada) e position (ORDEM explícita inteira). Ordenada por position
   asc. Gerenciada SÓ no painel — SEM tag de IA.
4. Status da PROVA é BINÁRIO (pendente/realizada) — NÃO 3 estados como o projetos. NÃO tem parity
   test próprio: é um CHECK simples na coluna + validação app-level no service (transição LIVRE entre
   os 2 — a equipe pode reabrir uma prova marcada como realizada). O que TEM parity é o project_type
   e o status da PROPOSTA.
5. Funil da PROPOSTA IDÊNTICO ao EventProposalStatus/ProjectProposalStatus:
   rascunho→orcada→aprovada→fechada→realizada + recusada/cancelada. A trava de itens a partir de
   'fechada' (itemsLocked) congela os itens de ORÇAMENTO E as PROVAS.
6. Tags: <proposta_atelie> (ABRE a proposta em rascunho, UM modo só — resolve o contato da conversa;
   total 0, sem itens, sem provas; carrega project_type+briefing) e <aprovacao_atelie> (MUTA o
   estado, decisao aprovada|recusada, só se a proposta está 'orcada'). Namespaces DISTINTOS de
   <proposta_projeto>/<aprovacao_projeto>, <proposta_evento>/<aprovacao_proposta>,
   <proposta_casamento>/<aprovacao_casamento> e de TODAS as outras tags.
7. SEM conflito de data transacional: as datas (proposta e provas) são campos livres. project_type
   nasce na abertura (default 'costura' se a tag não trouxer; a equipe ajusta no painel).

[FUNDAÇÃO — migration 58_atelie (CONFIRMAR o próximo nº livre no disco)]
- ALTER companies CHECK aceitar 'atelie' (estende a constraint atual incluindo TODOS os ids já
  presentes — copiar a lista EXATA da última migration de perfil no disco e ADICIONAR 'atelie' ao
  fim; 19º perfil real). ARMADILHA cravada (lição da floricultura/pizzaria): NÃO clonar por
  `sed s/projetos/atelie/g` cego — isso TROCA 'projetos' por 'atelie' na lista do CHECK em vez de
  ADICIONAR. Depois da clonagem, CONFERIR que o CHECK tem TODOS os perfis anteriores + 'atelie'.
- RLS enable+force, policies via app.company_id(), grants authenticated + service_role (INSERT de
  propostas/itens/provas pelo BACKEND via service_role; tenant SELECT/UPDATE — igual às migrations
  30-57). Espelhar 45_eventos.sql / 57_projetos.sql inteiro (estrutura, comentários, índices,
  policies).
- total_cents (na proposta) e line_total_cents (no item de orçamento) MATERIALIZADOS no INSERT/UPDATE;
  NÃO colunas geradas (lição end_at/total das SMs anteriores).
- Tabelas:
  * atelie_artisans — artesãos/responsáveis (catálogo SIMPLES, sem agenda/conflito; espelho
    project_planners/event_planners). (id, company_id, name CHECK 1..200, specialty texto livre
    (costura/alfaiataria/ilustração/design gráfico…), active default true, notes, timestamps).
    Atribuir à proposta é opcional. delete em uso → 409 artisan_in_use; preferir desativar.
  * atelie_config — config 1:1 com company; SEM horário/slot (não há agenda). (company_id PK,
    business_name nullable (nome do ateliê/estúdio), notes, timestamps). Ausente → defaults vazios.
    Espelho project_config/event_config.
  * atelie_proposals — propostas de peça/obra (order-based, total materializado, snapshots).
    (id, company_id, contact_id refs contacts on delete set null, artisan_id refs atelie_artisans
    on delete set null, conversation_id nullable, customer_name NOT NULL snapshot, customer_phone
    snapshot, project_type text NOT NULL default 'costura' CHECK in ('costura','arte','design'),
    occasion text (ocasião: casamento/formatura/presente/decoração… texto livre), briefing text (o
    que o cliente imagina + medidas/dimensões aproximadas + referência descrita), estimated_date
    date (previsão de entrega — CAMPO LIVRE, sem conflito de agenda), total_cents int NOT NULL
    default 0 (MATERIALIZADO), status text CHECK ('rascunho'|'orcada'|'aprovada'|'fechada'|
    'realizada'|'recusada'|'cancelada') default 'rascunho', notes, opened_at, closed_at,
    status_updated_at, timestamps). Espelho project_proposals (project_type ganha CHECK próprio;
    environment→occasion; estimated_date mantém).
  * atelie_proposal_items — itens de ORÇAMENTO (entram no total; line_total materializado).
    (id, company_id, proposal_id refs atelie_proposals on delete cascade, description CHECK 1..200,
    quantity int default 1 CHECK >0, unit_price_cents int CHECK >=0, line_total_cents int CHECK >=0
    (= quantity*unit_price, materializado), timestamps). Espelho project_proposal_items.
  * atelie_fittings — A ENTIDADE NOVA: etapas de prova/ajuste (ordenadas por position, NÃO entram no
    total). (id, company_id, proposal_id refs atelie_proposals on delete cascade, title CHECK
    1..200, description, due_date date NULLABLE (previsão da prova), status text NOT NULL default
    'pendente' CHECK in ('pendente','realizada'), position int NOT NULL default 0 (ORDEM explícita),
    completed_at timestamptz nullable (preenchido quando vira realizada; zerado quando volta a
    pendente), timestamps). Índice (proposal_id, position). Leitura ordenada por position asc,
    created_at asc. RLS/grants como os sub-itens do eventos/projetos (SELECT/UPDATE pelo tenant;
    INSERT pelo backend/service_role). COMMENT cravando que é a sub-entidade nova: SEQUÊNCIA ORDENADA
    DE PROVAS/AJUSTES da peça sob medida, status BINÁRIO (pendente/realizada) + ordem + data prevista;
    NÃO entra no total; gerenciada no painel sem tag de IA; espelha os milestones do projetos mas com
    status BINÁRIO (não 3 estados) e vocabulário de PROVA DE COSTURA/AJUSTE (não produção/obra).
- Status da PROPOSTA hardcoded (AtelieProposalStatus enum Java + const TS + parity test):
  rascunho → orcada, cancelada ; orcada → aprovada, recusada, cancelada ; aprovada → fechada,
  cancelada ; fechada → realizada, cancelada ; realizada/recusada/cancelada → terminal.
  itemsLocked()=true em fechada/realizada/recusada/cancelada (trava itens de orçamento E provas).
  Espelho EventProposalStatus/ProjectProposalStatus.
- project_type hardcoded (AtelieProjectType enum Java + const TS + AtelieProjectTypeParityTest):
  costura|arte|design (id + label "Costura sob medida"/"Arte"/"Design"). NÃO confundir com a enum de
  plataforma ProfileType — é uma enum LOCAL do perfil atelie (sugestão de nome: AtelieProjectType em
  ...profiles/atelie/, e const atelie-project-type.ts em frontend/profiles/atelie/ ou lib/profiles/,
  decisão do agente — só não colidir com ProfileType nem com a ProjectType do projetos).
- O status da PROVA (pendente/realizada) NÃO tem enum/parity — é CHECK na coluna + constante/
  validação no service e no front. (Só o status da PROPOSTA e o project_type têm parity.)
- TODAS as 5 tabelas novas entram na migration ANTES de tocar o banco (banco se aplica A PARTIR do
  arquivo versionado — lição os_config da SM-J) e na lista de TRUNCATE/SCRIPTS do
  AbstractIntegrationTest.

[BACKEND]
- Artisans: CRUD padrão (espelho EventPlannerService/Controller/Repository). delete em uso (proposta
  com artisan_id) → 409 artisan_in_use; preferir desativar (active=false).
- Config: GET (fallback default) + PUT. (espelho EventConfig*/ProjectConfig*; sem horário)
- Proposals (chassi project_proposals + os 2 editores: ORÇAMENTO + PROVAS):
  * open (a partir da tag/IA ou manual): nasce 'rascunho', total 0, sem sub-itens. project_type
    obrigatório (default 'costura' se ausente, validado contra AtelieProjectType). Snapshots de
    cliente (customer_name/phone do contact). conversation_id quando veio da conversa.
  * itens de ORÇAMENTO: add/update/delete sob requireMutableProposal (trava itemsLocked); cada
    mutação RECALCULA e MATERIALIZA total_cents.
  * provas/ajustes (NOVO): add/update/delete + reorder + transição de estado, TODOS sob a MESMA trava
    requireMutableProposal; SEM recálculo de total.
      - add: cria com status default 'pendente', position = (max(position)+1) da proposta.
      - update: title/description/due_date (parse de data inválida → 400 invalid_date).
      - reorder: recebe a ORDEM nova (lista de ids ou {id,position}) e re-materializa position
        sequencial 0..N na MESMA transação (decisão do agente sobre o formato exato do payload —
        recomendo lista ordenada de ids, espelho do projetos).
      - transição de estado: pendente⇄realizada LIVRE (sem máquina rígida); ao entrar em realizada
        seta completed_at (now()); ao voltar pra pendente zera completed_at. status inválido → 400
        invalid_fitting_status.
      - Exceptions: fitting_not_found (404), proposal_locked (409), invalid_date (400),
        invalid_fitting_status (400).
      - leitura ordenada por position asc, created_at asc.
  * updateStatus (da PROPOSTA): valida transição (inválida → 409 invalid_status_transition); ir pra
    'orcada' exige total_cents > 0 (→ 400 empty_budget); terminal preenche closed_at; dispara
    notificação outbound conforme o status.
  * O detalhe da proposta hidrata as DUAS listas (items de orçamento + fittings de prova).
- Notifier (espelho EventProposalNotifier/ProjectProposalNotifier): best-effort, persiste OUTBOUND/
  HUMAN, texto defensivo SEM promessa de resultado/prazo cravado pela IA. Notifica: orcada (com
  total + tipo de peça), aprovada, fechada, recusada. rascunho/realizada/cancelada silenciosos.
- IA:
  * Persona prestativa-consultiva e sensível (ateliê sob encomenda) com a TRAVA DE COMPORTAMENTO
    acima embutida no prompt. Adicionar a persona ATELIE em ProfilePromptContext (espelho do bloco
    EVENTOS/PROJETOS) + branch em segmentFor pra 'atelie'.
  * Contexto injetado (cache TTL 20s, espelho EventosContextCache/ProjetosContextCache, keyed por
    (companyId, contactId)): artesãos ativos + propostas do contato em ABERTO (rascunho/orcada, com
    id+project_type+occasion+estimated_date+status+total) + instruções e as 2 tags. NÃO injeta as
    PROVAS (organizacionais do painel). Invalidação em toda mutação.
  * Tag <proposta_atelie>{"project_type":"costura|arte|design","occasion":"texto|null",
    "estimated_date":"YYYY-MM-DD|null","briefing":"texto","artisan_id":"UUID|null","notes":"texto"}
    → PropostaAtelieConfirmHandler (espelho PropostaProjetoConfirmHandler; cria proposta em rascunho;
    project_type inválido/ausente → default 'costura'; artisan_id inválido → ignora artesão mas abre;
    best-effort).
  * Tag <aprovacao_atelie>{"proposal_id":"UUID","decisao":"aprovada|recusada"} →
    AprovacaoAtelieHandler (espelho AprovacaoProjetoHandler; só aplica se a proposta está 'orcada';
    proposta NÃO-orcada → empty).
  * JwtFilter autentica /api/atelie/**. OutboundService ganha maybeProcessPropostaAtelie +
    maybeProcessAprovacaoAtelie (best-effort, contactId via findContactIdByConversation, encadeados
    APÓS os outros perfis — perfil é único, só um age; REMOVE a tag antes de enviar ao cliente).
- Guard: AtelieProfileGuard (403 forbidden_wrong_profile) — espelho EventosProfileGuard/
  ProjetosProfileGuard.

[FRONTEND]
- /dashboard/atelie-artisans (CRUD artesãos; desativar preferido a excluir),
  /dashboard/atelie-proposals (lista por status com badge de project_type; detalhe com DOIS editores
  inline: ORÇAMENTO com total recalculado + PROVAS/AJUSTES ordenadas por position com os 2 estados —
  seletor/checkbox pendente↔realizada por prova, botões ↑↓ ou drag pra reordenar, form de título+data
  prevista; ambos respeitam a trava por status — somem/recusam quando a proposta está fechada+),
  /dashboard/atelie-settings (nome do ateliê + notas; sem horário).
- types + SDKs (artisans, config, proposals com os endpoints de orçamento + provas: add/update/
  delete/reorder/transição) espelhando eventos/projetos. AtelieFitting: { id, proposalId, title,
  description, dueDate: string|null, status: 'pendente'|'realizada', position: number,
  completedAt: string|null, ... }.
- Status TS atelie-proposal-status.ts (7 ids, ALLOWED_NEXT, ITEMS_LOCKED, statusLabel) +
  AtelieProposalStatusParityTest Java↔TS. atelie-project-type.ts (3 ids + labels) +
  AtelieProjectTypeParityTest Java↔TS. (O status da PROVA NÃO tem parity — é uma constante de 2
  strings no front + CHECK no banco.)
- getNavForProfile('atelie') injeta "Ateliê" (3 itens: Artesãos, Propostas, Configurações).
  ATENÇÃO: floricultura ficou no enum SEM branch em getNavForProfile (fallback) — NÃO repetir esse
  gap; atelie PRECISA do branch próprio. Subdomínio atelie.meadadigital.local.
  Paleta: sugerir 'orquidea' / 'ameixa' / 'lavanda' (tons artesanais/criativos). Conferir em
  lib/themes/palettes.ts qual já existe; se nenhuma das 3 existir, criar uma nova paleta roxa/lilás
  ou reaproveitar uma livre (decisão do agente — só registrar a escolha; preferir 'orquidea'/
  'ameixa'/'lavanda' nessa ordem se livres).
- npm build limpo.

[DOCS]
- CLAUDE.md: seção "## Perfil Ateliê (camada 8.14)" espelhando as seções de perfil + nota de que é o
  19º perfil real (20º com generic), CLONA o PROJETOS/EVENTOS (proposta order-based + aprovação em 2
  fases) e inaugura a sub-entidade de ETAPAS DE PROVA/AJUSTE (status BINÁRIO pendente/realizada +
  ordem + data prevista). Documentar EXPLÍCITO: UM perfil serve os três tipos (project_type costura/
  arte/design, hardcoded+parity, campo da proposta); a diferença entre prova (status BINÁRIO, ordem,
  vocabulário de costura/ajuste) e a etapa de execução do projetos (3 estados pendente/em_andamento/
  concluida) e o cronograma/checklist do eventos/casamento; provas gerenciadas só no painel sem tag;
  a trava de comportamento da IA; as 2 tags.
- docs/PERFIL_ATELIE.md: guia operacional (artesãos, propostas — os 2 editores, estados,
  notificações, trava de edição; o editor de PROVAS com os 2 estados e a ordem; como a IA atende; o
  bloco "o que a IA NÃO faz"). Espelhar PERFIL_PROJETOS.md/PERFIL_EVENTOS.md.
- NÃO mexer em system-template.txt nem em outros perfis.

[TESTES BACKEND]
Espelhar a suíte do eventos/projetos (service + controller integration por entidade):
- AtelieProposalStatusParityTest + AtelieProjectTypeParityTest + ProfileTypeParityTest (atelie no
  enum/const).
- AtelieArtisanServiceTest + ControllerIntegrationTest (CRUD, delete-em-uso 409 artisan_in_use).
- AtelieConfigServiceTest/ControllerIntegrationTest (GET fallback + PUT).
- AtelieProposalServiceTest (open nasce rascunho/total 0/project_type validado; add item recalcula
  total; transição válida/inválida; orcada sem item → empty_budget; trava itemsLocked em fechada+;
  PROVAS: add cria pendente com position incremental; transição pendente→realizada (completed_at set)
  →pendente (completed_at zerado); reorder re-materializa position sequencial; ordena por position;
  trava proposal_locked quando a proposta está fechada+) + ControllerIntegrationTest (os endpoints de
  orçamento E de provas: add/update/delete/reorder/transição; 409 proposal_locked; 404
  fitting_not_found; 400 invalid_fitting_status; wrongProfile 403).
- PropostaAtelieConfirmHandlerTest (abre proposta em rascunho; project_type ausente → 'costura';
  sem tag → empty; artisan_id inválido → ignora artesão mas abre).
- AprovacaoAtelieHandlerTest (proposta orcada + decisao aprovada → aprovada; recusada → recusada;
  proposta NÃO-orcada → empty; sem tag → empty).
mvn final = relatar contagem REAL do Surefire (não estimar).

[CONSTRAINTS DUROS]
- Migration única (próximo nº livre — provavelmente 58; CONFERIR). Sem foto/anexo (bloqueador
  SERVICE_ROLE_KEY).
- UM perfil 'atelie' serve costura sob medida + arte + design. project_type é CAMPO da proposta
  (hardcoded + parity), NÃO três perfis.
- Cliente NÃO é entidade do core — continua o contact; snapshots customer_name/phone na proposta.
- DOIS sub-itens distintos no mesmo artefato: itens de ORÇAMENTO (entram no total) + PROVAS/AJUSTES
  (NÃO entram no total). A prova tem 2 estados (pendente/realizada — CHECK, sem parity), due_date
  nullable, position (ordem), gerenciada só no painel (sem tag), trava junto com itemsLocked().
- total_cents/line_total_cents materializados (não generated). estimated_date / due_date campos
  livres (sem conflito de agenda).
- Funil idêntico ao EventProposalStatus/ProjectProposalStatus; orcada exige total>0 (empty_budget);
  trava de itens (E provas) a partir de fechada.
- IA: NUNCA fecha contrato/preço/desconto, NUNCA confirma prazo/medida não cravado pela equipe,
  NUNCA inventa material/tecido/técnica/item/valor, NUNCA promete resultado estético, NUNCA gerencia
  provas pela conversa.
- Tags <proposta_atelie> e <aprovacao_atelie> distintas de TODAS as outras (em especial das de
  projetos/casamento/eventos).
- NÃO mexer em outros perfis nem em system-template.txt. Webhook OFF.
- Cache TTL 20s + invalidação em toda mutação. NÃO injetar as PROVAS no contexto da IA.
- 529 → inline. Gate 3× → pausar. Working tree sujo → pausar. git add EXPLÍCITO (nunca git add .);
  .env/CONTEXT.md/secrets NUNCA staged.
- SEED com timestamptz: usar `at time zone 'America/Sao_Paulo'` (lição do fuso).
- IDs de namespace compartilhado no seed com sufixo -12x (conferir os usados; evitar colisão FK).
- Tabela nova entra na migration ANTES de tocar o banco (lição os_config). Adicionar as 5 tabelas ao
  TRUNCATE/SCRIPTS do AbstractIntegrationTest.
- Decisões menores: agente decide (layout exato, ícones do nav, nome de constante, payload do
  reorder, regra exata de limpeza de completed_at na transição de prova, paleta final).

[PASSO FINAL — TENANT + SEED + COMMIT + PUSH + SMOKE + RELATÓRIO]
F.1 — TENANT igorhaf25 (Ateliê Modelo, profile=atelie) — PROVISÓRIO: se igorhaf25 já existe, usar o
      PRÓXIMO livre. Padrão GoTrue, senha em comunicação direta. company c?0000000-...-025 / user
      a?0000000-...-025 (ajustar sufixo ao tenant real). Caddy + /etc/hosts pra
      atelie.meadadigital.local.
F.2 — Seed /tmp/seed-atelie.sql (NÃO COMITAR; `at time zone 'America/Sao_Paulo'`; ids sufixo -12x;
      lição os_config: as 5 tabelas já existem na migration versionada antes do seed):
  - config: business_name "Ateliê Modelo".
  - 2 artesãos: "Clara Monteiro" (specialty "costura sob medida / alfaiataria"), "Téo Albuquerque"
    (specialty "arte / ilustração / design gráfico").
  - contact "Helena Martins" +5511944443333 (VINCULADO: instance+conversation, pra smoke de
    notificação) + contact "Bruno Carvalho" +5511955554444 (sem vínculo).
  - 3 propostas cobrindo estados E os project_type:
    * VINCULADA, status 'orcada' (Helena / Clara / project_type 'costura' / occasion "vestido de
      formatura" / estimated_date +45d) COM 3 itens de orçamento (tecido, mão de obra, aviamentos →
      total>0) e 4 PROVAS com position 0..3 e estados variados ("1ª prova" realizada, "2ª prova"
      pendente, "ajuste final" pendente, "entrega" pendente — pra smoke de ordenação e transição).
    * 'rascunho' (Bruno / Téo / project_type 'arte' / occasion "quadro de presente" / estimated_date
      +90d) sem itens (pra smoke de empty_budget na transição pra orcada).
    * 'aprovada' (Helena / Clara / project_type 'design') com itens E provas (pra smoke de transição
      aprovada→fechada e trava de edição dos itens E das provas).
F.3 — JwtFilter /api/atelie/** (se ainda não).
F.4-F.6 — git add EXPLÍCITO dos arquivos da SM + sanity (sem .env/secrets/CONTEXT) + commit.
      Mensagem padrão (feat(camada-8.14): perfil atelie/Ateliê (costura sob medida · arte · design)
      com FUNDAÇÃO/BACKEND/FRONTEND/DECISÕES/VALIDAÇÃO contagem REAL/NÃO TOCADO/FECHAMENTO +
      Co-Authored-By: Claude Opus 4.8). Tag fase-8.14-fechada (ou o nº real confirmado no arranque).
F.7 — git push origin main + tags.
F.8 — docker compose restart backend + aguardar /admin/me → 401.
F.9 — Smoke E2E:
  BLOCO A: auth — igorhaf25 → /admin/me → role=tenant_admin, profileId=atelie, productName=Ateliê.
  BLOCO B: catalog + guard — GET artisans (2), CRUD smoke, delete em uso 409 artisan_in_use; GET
    config + PUT; tenant de OUTRO perfil (floricultura/projetos) → /api/atelie/artisans → 403
    forbidden_wrong_profile.
  BLOCO C: proposta + orçamento — GET proposals (3) com project_type correto; abrir a 'rascunho' do
    Bruno; POST item de orçamento → total recalcula; PATCH rascunho→orcada SEM item (na proposta
    vazia) → 400 empty_budget; com item → 200.
  BLOCO D: ETAPAS DE PROVA/AJUSTE (a escapada desta SM) [CHAVE] —
    - POST 3 provas → GET retorna ordenado por position (0,1,2)
    - PATCH reorder (inverter ordem) → GET reflete a nova position sequencial
    - PATCH transição pendente→realizada (completed_at preenchido) → realizada→pendente (completed_at
      zerado conforme a regra cravada)
    - PATCH status inválido (ex.: 'xpto') → 400 invalid_fitting_status
    - DELETE prova → 204
    - tudo isso na proposta 'orcada'/'rascunho' (mutável); na proposta 'fechada' → 409
      proposal_locked
  BLOCO E: aprovação em 2 fases + notificação —
    - <aprovacao_atelie>{Helena orcada, aprovada} via handler/teste → status vira aprovada
    - aprovacao em proposta NÃO-orcada → empty
    - PATCH status orcada→aprovada (Helena vinculada) → 200 + msg outbound (com total/tipo de peça);
      asserção casa o conteúdo EXATO (lição do fuso/substring)
    - transição inválida → 409 invalid_status_transition
    - trava: numa proposta 'fechada', PATCH item de orçamento → 409 proposal_locked E PATCH/POST de
      prova → 409 proposal_locked (os DOIS sub-itens travam juntos)
  BLOCO F: project_type — GET de uma proposta de cada tipo (costura/arte/design) confirma o campo;
    POST de proposta manual com project_type inválido → rejeitado/normalizado conforme a regra
    cravada.
  BLOCO G: regressão — os perfis anteriores intactos (smoke leve 1 endpoint cada);
    atelie → /api/eventos/* → 403; atelie → /api/projetos/* → 403 (se projetos existir);
    atelie → /api/floricultura/* → 403.
  BLOCO H: paridade — mvn test -Dtest=AtelieProposalStatusParityTest,AtelieProjectTypeParityTest,
    ProfileTypeParityTest → verde.
  Cleanup smoke + restaurar seed pristine. mvn final: contagem REAL.
F.10 — RELATÓRIO consolidado + DESTAQUE EXPLÍCITO:
  - "19º perfil vertical — camada 8.14 (UM perfil serve costura sob medida / arte / design)"
  - "CLONA o PROJETOS/EVENTOS (proposta order-based + aprovação em 2 fases) e inaugura a sub-entidade
     de ETAPAS DE PROVA/AJUSTE (atelie_fittings)"
  - "DOIS tipos de sub-item: orçamento (entra no total) / PROVAS de costura/ajuste (status BINÁRIO
     pendente/realizada + ordem + data prevista, NÃO entra no total) — diferença pro projetos é o
     status BINÁRIO (lá são 3 estados) e o vocabulário de prova de roupa/ajuste"
  - "project_type costura/arte/design hardcoded com parity — campo da proposta, NÃO três perfis"
  - "BLOCO D prova as PROVAS: ordenação por position, reorder, transição com completed_at, trava
     proposal_locked"
  - "BLOCO E prova o gate de aprovação em 2 fases + notificação + trava dos DOIS sub-itens em fechada"
  - "Provas gerenciadas só no painel (sem tag de IA); IA não fecha contrato, não confirma prazo/
     medida, não inventa material/valor, não promete resultado estético"
  - "Seed usou at time zone + sufixo de ids -12x (sem fuso/colisão)"
  - "as 5 tabelas criadas DENTRO da migration (lição os_config)"
  - PENDÊNCIAS: catálogo de tecidos/materiais/técnicas pré-cadastrados, tabela de medidas estruturada
     do cliente, contrato e-sign, pagamento/sinal (Stripe), anexo de referência/croqui/arte, lembrete
     automático de data de prova, multi-artesão com agenda + a dívida acumulada (webhook, cliente
     real, olho humano sobre os verticais).

[REPORTAR]
Igual SMs anteriores. Incluir EXPLICITAMENTE:
- "ProfileType.ATELIE adicionado (19º perfil real, camada 8.14)"
- "UM perfil 'atelie' serve costura sob medida / arte / design (project_type, não três perfis)"
- "Paridade AtelieProposalStatus, AtelieProjectType e ProfileType validadas"
- "Tenant igorhaf25 criado seguindo padrão GoTrue + Caddy/etc/hosts"
- "DOIS sub-itens no mesmo artefato (orçamento/provas de ajuste); as PROVAS são a escapada"
- "Prova = 2 estados (pendente/realizada, CHECK sem parity) + position (ordem) + due_date nullable,
   só no painel, trava junto com itemsLocked; espelha milestones do projetos mas BINÁRIO"
- "Gate de aprovação em 2 fases: <proposta_atelie> abre, <aprovacao_atelie> muta (só orcada)"
- "OutboundService ganhou maybeProcessPropostaAtelie + maybeProcessAprovacaoAtelie"
- "JwtFilter autentica /api/atelie/**"
- "getNavForProfile('atelie') com branch próprio (não repetir o gap do floricultura)"
- "Cliente NÃO é entidade do core — continua o contact; snapshots na proposta"
- "as 5 tabelas criadas DENTRO da migration (lição os_config)"
- "Seed: at time zone America/Sao_Paulo + sufixo de ids -12x (sem bug de fuso, sem colisão FK)"
- "Próximas fases: catálogo de tecidos/materiais/medidas estruturadas/contrato e-sign/pagamento-sinal/
   anexo de referência/lembrete de prova/multi-artesão com agenda (webhook, cliente real, olho humano
   sobre os verticais)"
