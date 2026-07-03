>>> SLOT ATRIBUÍDO (ver docs/prompts-nicho/README.md — fonte única de verdade, tem precedência
>>> sobre qualquer "provisório" no corpo): ordem 2 · camada 8.7 · migration 51_casamento.sql ·
>>> tenant igorhaf18 (company/user sufixo -018) · ids de seed sufixo -05x. Reconfirmar no arranque
>>> que a fila não avançou; se avançou, deslocar conforme o README.

[TAREFA — SUB-MARATONA: PERFIL CASAMENTO / Casamento (camada 8.7)]

[CONTEXTO]
PROJETO MEADA em /home/igorhaf/meada.
17 perfis verticais reais hoje (… comida 8.4, floricultura 8.5 — o último fechado) + generic.
Lê CONTEXT.md e o filesystem no arranque pra cravar convenções, nº de migration, contagem do
Surefire e numeração de tenant ANTES de escrever qualquer código. NÃO hardcodar a contagem do
mvn — relatar a REAL do Surefire ao final. Valores esperados (CONFIRMAR no filesystem antes;
podem ter avançado se Pizzaria/outra SM foi executada primeiro): migration 50_casamento, tenant
igorhaf17, company c?0000000-...-017, user a?0000000-...-017. IDs de namespace compartilhado
(contacts/instance/conversation) NO SEED com sufixo NOVO que NÃO colida com nenhum seed anterior.

Casamento é template de nicho pra ASSESSORIA / CERIMONIAL DE CASAMENTO dentro do mesmo dashboard
Meada. Tenant acessa casamento.meadadigital.local e vê o produto "Casamento". A assessoria
COORDENA casamentos (não é fornecedor isolado de buffet/foto). A IA atende os noivos via WhatsApp,
identifica pelo telefone, ABRE uma proposta a partir do briefing (data prevista, nº de convidados,
estilo, o que sonham pro dia) e, quando a equipe já montou o orçamento no painel, informa o total
e CAPTURA a aprovação/recusa. Tom acolhedor-celebrativo, de quem cuida de um dos dias mais
importantes dos noivos.

>>> TRAVA DE COMPORTAMENTO DA IA (cravada — o que a IA NUNCA faz) <
- NUNCA fecha contrato, preço ou desconto por conta própria — quem orça e fecha é a equipe no
  painel.
- NUNCA confirma disponibilidade de uma data que não esteja confirmada — diz "vou verificar a
  disponibilidade com a equipe".
- NUNCA inventa item de pacote, valor, fornecedor ou serviço que não esteja cadastrado.
- NUNCA promete estrutura/local/comodidade não informada, e NUNCA promete um "casamento perfeito"
  nem garante resultado — acolhe o sonho sem criar expectativa fora do controle.
- NUNCA gerencia o cronograma do dia NEM o checklist pré-casamento pela conversa — ambos são
  montados/acompanhados pela equipe no painel. A IA SÓ abre a proposta e captura a aprovação.

EVOLUÇÃO ESTRUTURAL: CLONA o chassi do EVENTOS (camada 8.2) — proposta order-based + itens de
orçamento (total materializado) + cronograma do dia (ordenado por horário, não entra no total) +
gate de aprovação em 2 fases via tag que muta o estado de um artefato existente. Cliente NÃO é
entidade (continua o contact; snapshots na proposta). UMA escapada nova:

  ESCAPADA — TERCEIRA SUB-ENTIDADE no mesmo artefato: CHECKLIST DE TAREFAS PRÉ-CASAMENTO COM PRAZO
  (wedding_checklist_tasks). Marcos de PREPARAÇÃO que acontecem ANTES do dia, cada um com um PRAZO
  (due_date) e estado PENDENTE/CONCLUÍDA (ex.: "enviar convites", "última prova do vestido",
  "provar o bolo", "fechar buffet"). Ordenados por prazo (sem prazo vão ao fim). Eventos tinha 2
  tipos de sub-item; casamento tem 3, que NÃO se misturam:
    (1) orçamento  = DINHEIRO                  → entra no total
    (2) cronograma = a HORA das coisas no dia  → ordenado por start_time, NÃO entra no total
    (3) checklist  = o que FALTA fazer até lá  → ordenado por due_date, status pendente/concluída,
                     NÃO entra no total

NÃO TEM nesta SM (registrado pra não inventar): conflito de agenda/data (a data é campo livre — a
assessoria coordena ~1 casamento por data), catálogo de pacotes pré-cadastrados (orçamento ad-hoc,
a equipe digita os itens), contrato com assinatura digital/PDF/e-sign (o "contrato" é o estado
'fechada'), pagamento/sinal/parcelas (Stripe é #50), fornecedores externos como pool com agenda
própria, lista de convidados/RSVP/mesa, lembrete automático de prazo do checklist (o due_date é
informativo; scheduler é fase futura), foto/mood board (bloqueador SERVICE_ROLE_KEY). Fases futuras.

DECISÕES CRAVADAS (revisor decidiu pelo Igor):
1. Proposta = artefato central, espelho do event_proposals (order-based, total materializado,
   snapshots de cliente, gate de aprovação em 2 fases). MANTER.
2. TRÊS sub-itens distintos no mesmo artefato (orçamento/cronograma/checklist). O checklist é a
   escapada da SM.
3. Checklist: estado PENDENTE/CONCLUÍDA é BINÁRIO (done boolean), NÃO máquina de status e NÃO tem
   parity test (diferente do status da proposta). due_date é NULLABLE (tarefa "sem prazo ainda").
   Gerenciado SÓ no painel — SEM tag de IA (igual o cronograma do eventos).
4. Funil da proposta IDÊNTICO ao EventProposalStatus: rascunho→orcada→aprovada→fechada→realizada +
   recusada/cancelada. A trava de itens a partir de 'fechada' congela os TRÊS sub-itens (orçamento
   E cronograma E checklist).
5. Tags: <proposta_casamento> (ABRE a proposta em rascunho, UM modo só — resolve o contato/noivos
   da conversa; total 0, sem itens) e <aprovacao_casamento> (MUTA o estado, decisao aprovada|
   recusada, só se a proposta está 'orcada'). Namespaces distintos de <proposta_evento>/
   <aprovacao_proposta> e de TODAS as outras tags.
6. SEM conflito de data transacional: wedding_date é campo livre na proposta (igual event_date).

[FUNDAÇÃO — migration 50_casamento]
- ALTER companies CHECK aceitar 'casamento' (estende a constraint atual — 17º perfil real).
- RLS enable+force, policies via app.company_id(), grants authenticated + service_role
  (INSERT de propostas/sub-itens pelo BACKEND via service_role; tenant SELECT/UPDATE — igual às
  migrations 30-49). Espelhar 45_eventos.sql inteiro.
- total_cents (na proposta) e line_total_cents (no item de orçamento) MATERIALIZADOS no INSERT/
  UPDATE; NÃO colunas geradas (lição end_at/total das SMs anteriores).
- Tabelas:
  * wedding_planners — assessores/cerimonialistas (catálogo SIMPLES, sem agenda/conflito; espelho
    event_planners). (id, company_id, name CHECK 1..200, specialty texto livre (civil/religioso/
    destination…), active default true, notes, timestamps). Atribuir à proposta é opcional.
  * wedding_config — config 1:1 com company; SEM horário/slot (não há agenda). (company_id PK,
    business_name nullable (nome da assessoria), notes, timestamps). Ausente → defaults vazios.
    Espelho event_config.
  * wedding_proposals — propostas de casamento (order-based, total materializado, snapshots).
    (id, company_id, contact_id refs contacts on delete set null, planner_id refs wedding_planners
    on delete set null, conversation_id nullable, customer_name NOT NULL snapshot (pode ser "Ana &
    João"), customer_phone snapshot, wedding_style text (estilo; livre), wedding_date date (data
    prevista — CAMPO LIVRE, sem conflito de agenda), guest_count int CHECK >=0, briefing text,
    total_cents int NOT NULL default 0 (MATERIALIZADO), status text CHECK ('rascunho'|'orcada'|
    'aprovada'|'fechada'|'realizada'|'recusada'|'cancelada') default 'rascunho', notes, opened_at,
    closed_at, status_updated_at, timestamps). Espelho event_proposals.
  * wedding_proposal_items — itens de ORÇAMENTO (entram no total; line_total materializado).
    (id, company_id, proposal_id refs wedding_proposals on delete cascade, description CHECK
    1..200, quantity int default 1 CHECK >0, unit_price_cents int CHECK >=0, line_total_cents int
    CHECK >=0 (= quantity*unit_price, materializado), timestamps). Espelho event_proposal_items.
  * wedding_timeline_items — marcos de CRONOGRAMA DO DIA (ordenados por horário, NÃO entram no
    total). (id, company_id, proposal_id refs wedding_proposals on delete cascade, start_time time
    NOT NULL, title CHECK 1..200, description, timestamps). Índice (proposal_id, start_time).
    Espelho event_timeline_items.
  * wedding_checklist_tasks — A ENTIDADE NOVA: checklist pré-casamento (ordenado por prazo, NÃO
    entra no total). (id, company_id, proposal_id refs wedding_proposals on delete cascade, title
    CHECK 1..200, description, due_date date NULLABLE (prazo do marco), done boolean NOT NULL
    default false (false=pendente, true=concluída), done_at timestamptz nullable (preenchido quando
    done vira true), timestamps). Índice (proposal_id, due_date). Leitura ordenada por due_date asc
    NULLS LAST, created_at asc. RLS/grants como os sub-itens do eventos (SELECT/UPDATE pelo tenant;
    INSERT pelo backend/service_role). COMMENT cravando que é a 3ª sub-entidade, status binário,
    não entra no total, gerenciada no painel sem tag de IA.
- Status da PROPOSTA hardcoded (WeddingProposalStatus enum Java + const TS + parity test):
  rascunho → orcada, cancelada ; orcada → aprovada, recusada, cancelada ; aprovada → fechada,
  cancelada ; fechada → realizada, cancelada ; realizada/recusada/cancelada → terminal.
  itemsLocked()=true em fechada/realizada/recusada/cancelada (trava os TRÊS sub-itens). Espelho
  EventProposalStatus. (O checklist NÃO tem enum/parity — done é boolean.)
- TODAS as 6 tabelas novas entram na migration 50 ANTES de tocar o banco (banco se aplica A PARTIR
  do arquivo versionado — lição os_config da SM-J) e na lista de TRUNCATE/SCRIPTS do
  AbstractIntegrationTest.

[BACKEND]
- Planners: CRUD padrão (espelho event_planners). delete em uso (proposta com planner_id) → 409
  planner_in_use; preferir desativar (active=false).
- Config: GET (fallback default) + PUT. (espelho event_config; sem horário)
- Proposals (chassi event_proposals + os 3 editores):
  * open (a partir da tag/IA ou manual): nasce 'rascunho', total 0, sem sub-itens. Snapshots de
    cliente (customer_name/phone do contact). conversation_id quando veio da conversa.
  * itens de ORÇAMENTO: add/update/delete sob requireMutableProposal (trava itemsLocked); cada
    mutação RECALCULA e MATERIALIZA total_cents.
  * cronograma: add/update/delete sob a mesma trava; SEM recálculo de total; leitura ordenada por
    start_time.
  * checklist (NOVO): add/update/delete + TOGGLE(done) sob a MESMA trava requireMutableProposal;
    SEM recálculo de total; toggle seta done + done_at (now() quando true, null quando false);
    leitura ordenada por due_date NULLS LAST. Exceptions: checklist_task_not_found (404),
    proposal_locked (409), invalid_date (400) no parse do due_date.
  * updateStatus: valida transição (inválida → 409 invalid_status_transition); ir pra 'orcada'
    exige total_cents > 0 (→ 400 empty_budget); terminal preenche closed_at; dispara notificação
    outbound conforme o status.
  * O detalhe da proposta hidrata as TRÊS listas (items, timeline, checklist).
- Notifier (espelho EventProposalNotifier): best-effort, persiste OUTBOUND/HUMAN, texto defensivo
  SEM "casamento perfeito". Notifica: orcada (com total + estilo), aprovada, fechada, recusada.
  rascunho/realizada/cancelada silenciosos.
- IA:
  * Persona acolhedora-celebrativa com a TRAVA DE COMPORTAMENTO acima embutida no prompt.
  * Contexto injetado (cache TTL 20s, espelho EventosContextCache, keyed por (companyId,
    contactId)): assessores ativos + propostas do contato em ABERTO (rascunho/orcada, com id+
    estilo+data+status+total) + instruções e as 2 tags. NÃO injeta cronograma NEM checklist
    (organizacionais do painel). Invalidação em toda mutação.
  * Tag <proposta_casamento>{"wedding_style","wedding_date":"YYYY-MM-DD|null","guest_count":
    N|null,"briefing","planner_id":"UUID|null","notes"} → PropostaCasamentoConfirmHandler
    (espelho PropostaEventoConfirmHandler; cria proposta em rascunho; best-effort).
  * Tag <aprovacao_casamento>{"proposal_id":"UUID","decisao":"aprovada|recusada"} →
    AprovacaoCasamentoHandler (espelho AprovacaoPropostaHandler; só aplica se a proposta está
    'orcada').
  * JwtFilter autentica /api/casamento/. OutboundService ganha maybeProcessPropostaCasamento +
    maybeProcessAprovacaoCasamento (best-effort, contactId via findContactIdByConversation,
    encadeados APÓS os outros perfis — perfil é único, só um age; REMOVE a tag antes de enviar).

[FRONTEND]
- /dashboard/casamento-planners (CRUD assessores; desativar preferido a excluir),
  /dashboard/casamento-proposals (lista por status; detalhe com TRÊS editores inline: ORÇAMENTO
  com total recalculado + CRONOGRAMA ordenado por horário + CHECKLIST ordenado por prazo com toggle
  concluída/pendente e form de título+data; todos respeitam a trava por status — somem/recusam
  quando a proposta está fechada+),
  /dashboard/casamento-settings (nome da assessoria + notas; sem horário).
- types + SDKs (planners, config, proposals com os endpoints de orçamento/cronograma/checklist)
  espelhando eventos. WeddingChecklistTask: { id, proposalId, title, description, dueDate:
  string|null, done: boolean, doneAt: string|null, ... }.
- Status TS wedding-proposal-status.ts (7 ids, ALLOWED_NEXT, ITEMS_LOCKED, statusLabel) +
  WeddingProposalStatusParityTest Java↔TS. (checklist sem parity.)
- getNavForProfile('casamento') injeta "Casamento" (3 itens: Assessores, Propostas,
  Configurações). ATENÇÃO: floricultura ficou no enum SEM branch em getNavForProfile (fallback) —
  NÃO repetir esse gap; casamento PRECISA do branch. Subdomínio casamento.meadadigital.local.
  Paleta: 'trigo' (champanhe/dourado claro — ainda não usada por nenhum perfil; combina com
  casamento).
- npm build limpo.

[DOCS]
- CLAUDE.md: seção "## Perfil Casamento (camada 8.6)" espelhando as seções de perfil + nota de que
  é o 17º perfil real (18º com generic), CLONA o EVENTOS e inaugura a 3ª sub-entidade (checklist
  pré-casamento com prazo). Documentar EXPLÍCITO: os 3 tipos de sub-item e o que cada um significa;
  o checklist binário sem parity gerenciado só no painel; a trava de comportamento da IA; as 2 tags.
- docs/PERFIL_CASAMENTO.md: guia operacional (assessores, propostas — os 3 editores, estados,
  notificações, trava de edição; como a IA atende; o bloco "o que a IA NÃO faz"). Espelhar
  PERFIL_EVENTOS.md.
- NÃO mexer em system-template.txt nem em outros perfis.

[TESTES BACKEND]
Espelhar a suíte do eventos (service + controller integration por entidade):
- WeddingProposalStatusParityTest + ProfileTypeParityTest (casamento no enum/const).
- WeddingPlannerServiceTest + ControllerIntegrationTest (CRUD, toggle, delete-em-uso 409).
- WeddingConfigServiceTest/ControllerIntegrationTest (GET fallback + PUT).
- WeddingProposalServiceTest (open nasce rascunho/total 0; add item recalcula total; transição
  válida/inválida; orcada sem item → empty_budget; trava itemsLocked em fechada+; cronograma
  ordena por horário; CHECKLIST: add/toggle(done→done_at)/ordena por due_date NULLS LAST/trava
  quando locked) + ControllerIntegrationTest (os endpoints dos 3 sub-itens; 409 proposal_locked;
  404 checklist_task_not_found; wrongProfile 403).
- PropostaCasamentoConfirmHandlerTest (abre proposta em rascunho; sem tag → empty; planner_id
  inválido → ignora planner mas abre).
- AprovacaoCasamentoHandlerTest (proposta orcada + decisao aprovada → aprovada; recusada →
  recusada; proposta NÃO-orcada → empty; sem tag → empty).
mvn final = relatar contagem REAL do Surefire (não estimar).

[CONSTRAINTS DUROS]
- Migration única (50). Sem foto/anexo.
- Cliente NÃO é entidade do core — continua o contact; snapshots customer_name/phone na proposta.
- TRÊS sub-itens distintos no mesmo artefato. Checklist = done boolean (sem enum/parity), due_date
  nullable, gerenciado só no painel (sem tag), trava junto com itemsLocked().
- total_cents/line_total_cents materializados (não generated). wedding_date campo livre (sem
  conflito de agenda).
- Funil idêntico ao EventProposalStatus; orcada exige total>0; trava de itens a partir de fechada.
- IA: NUNCA fecha contrato/preço/desconto, NUNCA confirma data não confirmada, NUNCA inventa
  item/valor/fornecedor, NUNCA promete "casamento perfeito", NUNCA gerencia cronograma/checklist
  pela conversa.
- Tags <proposta_casamento> e <aprovacao_casamento> distintas de TODAS as outras.
- NÃO mexer em outros perfis nem em system-template.txt. Webhook OFF.
- Cache TTL 20s + invalidação em toda mutação. NÃO injetar cronograma/checklist no contexto.
- 529 → inline. Gate 3× → pausar. Working tree sujo → pausar. git add EXPLÍCITO (nunca git add .);
  .env/CONTEXT.md/secrets NUNCA staged.
- SEED com timestamptz: usar `at time zone 'America/Sao_Paulo'` (lição do fuso).
- IDs de namespace compartilhado no seed com sufixo NOVO (conferir os usados; evitar colisão FK).
- Tabela nova entra na migration ANTES de tocar o banco (lição os_config). Adicionar as 6 tabelas
  ao TRUNCATE/SCRIPTS do AbstractIntegrationTest.
- Decisões menores: agente decide (layout exato, ícones do nav, nome de constante).

[PASSO FINAL — TENANT + SEED + COMMIT + PUSH + SMOKE + RELATÓRIO]
F.1 — TENANT igorhaf17 (Casamento Modelo, profile=casamento), padrão GoTrue, senha em comunicação
      direta. company c?0000000-...-017 / user a?0000000-...-017. Caddy + /etc/hosts pra
      casamento.meadadigital.local.
F.2 — Seed /tmp/seed-casamento.sql (NÃO COMITAR; `at time zone 'America/Sao_Paulo'`; ids sufixo
      novo):
  - config: business_name "Ateliê Noivas Modelo".
  - 2 assessores: "Beatriz Lima" (specialty "cerimonial completo"), "Marcos Dias" (specialty
    "destination wedding").
  - contact "Ana & João" +5511933332222 (VINCULADO: instance+conversation, pra smoke de
    notificação) + contact "Clara Souza" +5511922221111 (sem vínculo).
  - 3 propostas cobrindo estados:
    * VINCULADA, status 'orcada' (Ana & João / Beatriz / estilo "clássico" / data +180d / 120
      convidados) COM 3 itens de orçamento (espaço, buffet, decoração → total>0), 4 marcos de
      cronograma (16:00 chegada / 17:00 cerimônia / 19:00 jantar / 22:00 festa) e 5 tarefas de
      checklist com prazos variados + 1 sem prazo (pra smoke de ordenação NULLS LAST e toggle).
    * 'rascunho' (Clara / Marcos / "praia" / data +300d / 80 convidados) sem itens (pra smoke de
      empty_budget na transição pra orcada).
    * 'aprovada' (Ana & João / Beatriz / data futura) com itens (pra smoke de transição
      aprovada→fechada e trava de edição).
F.3 — JwtFilter /api/casamento/ (se ainda não).
F.4-F.6 — git add EXPLÍCITO dos arquivos da SM + sanity (sem .env/secrets/CONTEXT) + commit.
      Mensagem padrão (feat(camada-8): perfil casamento/Casamento (camada 8.6) com FUNDAÇÃO/BACKEND/
      FRONTEND/DECISÕES/VALIDAÇÃO contagem REAL/NÃO TOCADO/FECHAMENTO + Co-Authored-By: Claude
      Opus 4.8). Tag fase-8.6-fechada (ou o nº real confirmado no arranque).
F.7 — git push origin main + tags.
F.8 — docker compose restart backend + aguardar /admin/me → 401.
F.9 — Smoke E2E:
  BLOCO A: auth — igorhaf17 → /admin/me → role=tenant_admin, profileId=casamento,
    productName=Casamento.
  BLOCO B: catalog + guard — GET planners (2), CRUD smoke, delete em uso 409; GET config + PUT;
    tenant floricultura (ou outro) → /api/casamento/planners → 403 forbidden_wrong_profile.
  BLOCO C: proposta + orçamento — GET proposals (3); abrir a 'rascunho' da Clara; POST item de
    orçamento → total recalcula; PATCH rascunho→orcada SEM item (na proposta vazia) → 400
    empty_budget; com item → 200.
  BLOCO D: CRONOGRAMA — POST 2 marcos fora de ordem → GET retorna ordenado por start_time.
  BLOCO E: CHECKLIST (a escapada desta SM) [CHAVE] —
    - POST tarefa com due_date + POST tarefa sem due_date → GET ordena por due_date NULLS LAST
    - PATCH toggle done=true → done + done_at preenchido; toggle done=false → done_at null
    - DELETE tarefa → 204
    - tudo isso na proposta 'orcada'/'rascunho' (mutável); na proposta 'fechada' → 409
      proposal_locked
  BLOCO F: aprovação em 2 fases + notificação —
    - <aprovacao_casamento>{Ana&João orcada, aprovada} via handler/teste → status vira aprovada
    - aprovacao em proposta NÃO-orcada → empty
    - PATCH status orcada→aprovada (Ana&João vinculada) → 200 + msg outbound (com total/estilo);
      asserção casa o conteúdo EXATO (lição do fuso/substring)
    - transição inválida → 409 invalid_status_transition
    - trava: PATCH item numa proposta 'fechada' → 409 proposal_locked (os TRÊS sub-itens)
  BLOCO G: regressão — os perfis anteriores intactos (smoke leve 1 endpoint cada);
    casamento → /api/eventos/* → 403; casamento → /api/floricultura/* → 403.
  BLOCO H: paridade — mvn test -Dtest=WeddingProposalStatusParityTest,ProfileTypeParityTest →
    verde.
  Cleanup smoke + restaurar seed pristine. mvn final: contagem REAL.
F.10 — RELATÓRIO consolidado + DESTAQUE EXPLÍCITO:
  - "17º perfil vertical — camada 8.6"
  - "CLONA o EVENTOS (proposta order-based + aprovação em 2 fases) e inaugura a 3ª sub-entidade"
  - "TRÊS tipos de sub-item: orçamento (entra no total) / cronograma do dia (ordenado por hora) /
     checklist pré-casamento (ordenado por prazo, done boolean)"
  - "BLOCO E prova o checklist: ordenação NULLS LAST, toggle done/done_at, trava proposal_locked"
  - "BLOCO F prova o gate de aprovação em 2 fases + notificação + trava dos 3 sub-itens em fechada"
  - "Checklist gerenciado só no painel (sem tag de IA); IA não promete casamento perfeito, não
     confirma data, não fecha contrato"
  - "Seed usou at time zone + sufixo de ids novo (sem fuso/colisão)"
  - "as 6 tabelas criadas DENTRO da migration 50 (lição os_config)"
  - PENDÊNCIAS: pacotes pré-cadastrados, contrato e-sign, pagamento/sinal (Stripe), RSVP/lista de
     convidados, lembrete de prazo do checklist, foto + a dívida acumulada (webhook, cliente real,
     olho humano sobre os verticais).

[REPORTAR]
Igual SMs anteriores. Incluir EXPLICITAMENTE:
- "ProfileType.CASAMENTO adicionado (17º perfil real, camada 8.6)"
- "Paridade WeddingProposalStatus e ProfileType validadas"
- "Tenant igorhaf17 criado seguindo padrão GoTrue + Caddy/etc/hosts"
- "TRÊS sub-entidades no mesmo artefato (orçamento/cronograma/checklist); checklist é a escapada"
- "Checklist = done boolean, due_date nullable, ordenado NULLS LAST, sem parity, só no painel"
- "Gate de aprovação em 2 fases: <proposta_casamento> abre, <aprovacao_casamento> muta (só orcada)"
- "OutboundService ganhou maybeProcessPropostaCasamento + maybeProcessAprovacaoCasamento"
- "JwtFilter autentica /api/casamento/"
- "getNavForProfile('casamento') com branch próprio (não repetir o gap do floricultura)"
- "Cliente NÃO é entidade do core — continua o contact; snapshots na proposta"
- "as 6 tabelas criadas DENTRO da migration 50 (lição os_config)"
- "Seed: at time zone America/Sao_Paulo + sufixo de ids novo (sem bug de fuso, sem colisão FK)"
- "Próximas fases: pacotes pré-cadastrados/contrato e-sign/pagamento-sinal/RSVP/lembrete de prazo
   + fila de prioridade (webhook, cliente real, olho humano sobre os verticais)"
