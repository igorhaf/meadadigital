>>> SLOT ATRIBUÍDO (ver docs/prompts-nicho/README.md — fonte única de verdade, tem precedência
>>> sobre qualquer "provisório" no corpo): ordem 13 · camada 8.18 · migration 62_viagens.sql ·
>>> tenant igorhaf29 (company/user sufixo -029) · ids de seed sufixo -16x. Reconfirmar no arranque
>>> que a fila não avançou; se avançou, deslocar conforme o README (conferir o MAIOR nº presente em
>>> supabase/migrations/ e o MAIOR igorhafN já provisionado no Supabase, e usar o PRÓXIMO livre).

[TAREFA — SUB-MARATONA: PERFIL VIAGENS / Viagens (Agência de viagens) (camada 8.18)]

[CONTEXTO]
PROJETO MEADA em /home/igorhaf/meada.
Monolito multi-tenant que se apresenta como N produtos verticais ("perfis"). Os perfis são
HARDCODED em dois arquivos espelhados — `src/main/java/com/meada/profiles/ProfileType.java`
(enum Java) e `frontend/lib/profiles/profile-type.ts` (const TS) — e o `ProfileTypeParityTest` falha
o build se divergirem. NÃO existe tabela de perfis. Cada tenant tem EXATAMENTE 1 perfil
(`companies.profile_id`, CHECK na lista de ids). Adicionar um perfil = editar os 2 arquivos + uma
migration que ESTENDE a CHECK de `companies.profile_id` (acrescentando o novo id e PRESERVANDO TODOS
os existentes — NUNCA remover um nicho ao adicionar outro) + rodar a paridade.

Backend: Spring Boot 3.3.13 + Java 17, single-module Maven, JdbcTemplate (não JPA), sem Lombok. HTTP
outbound síncrono via RestClient. Frontend: Next 16 (app router) + React 19 + TS + Tailwind 4 +
shadcn/ui + @base-ui/react + TanStack Query. Banco/Auth: Supabase (Postgres 17 + Auth). IA: Gemini
Flash. Migrations versionadas em `supabase/migrations/`.

No arranque, ANTES de escrever qualquer código: ler CONTEXT.md e varrer o filesystem pra cravar as
convenções REAIS, o nº de migration livre, a contagem do Surefire e a numeração de tenant. NÃO
hardcodar a contagem do mvn — relatar a REAL do Surefire ao final. Valores ESPERADOS (reconfirmar;
têm precedência o cabeçalho de slot + o README): migration `62_viagens.sql`, tenant igorhaf29,
company `c?0000000-...-029`, user `a?0000000-...-029`. IDs de namespace compartilhado
(contacts/instance/conversation) NO SEED com sufixo NOVO `-16x` que NÃO colida com nenhum seed
anterior (conferir os sufixos já usados pelos seeds anteriores no arranque).

Viagens é template de nicho pra AGÊNCIA DE VIAGENS / operadora de turismo dentro do mesmo dashboard
Meada. Tenant acessa viagens.meadadigital.local e vê o produto "Viagens". A agência MONTA COTAÇÕES /
PROPOSTAS de pacote de viagem (destino, datas, nº de viajantes, hospedagem, voos, traslados,
passeios) e CAPTURA a aprovação do cliente. A IA atende os viajantes via WhatsApp, identifica pelo
telefone, ABRE uma proposta a partir do BRIEFING (destino desejado, período/datas, nº de pessoas,
estilo de viagem, orçamento aproximado), a EQUIPE monta a cotação no painel (itens: aéreo,
hospedagem, traslado, passeios — com total materializado), e a IA informa o total e CAPTURA a
aprovação/recusa em 2 fases. Tom prestativo-consultivo de consultor de viagens, sem prometer o que a
equipe não cravou.

>>> TRAVA DE COMPORTAMENTO DA IA (cravada — o que a IA NUNCA faz) <<<
- NUNCA confirma disponibilidade de VOO, HOTEL, ASSENTO, TARIFA ou PREÇO que a equipe não tenha
  cravado na cotação — diz "vou verificar a disponibilidade e os valores com a equipe".
- NUNCA EMITE passagem, reserva, bilhete ou voucher — não há integração com cia aérea/GDS/booking
  nesta SM; a emissão é operação da equipe fora do app.
- NUNCA inventa destino, roteiro, item de cotação, valor, hotel, companhia aérea ou passeio que não
  esteja cadastrado na proposta pela equipe.
- NUNCA fecha contrato, preço ou desconto por conta própria — quem orça e fecha é a equipe no painel.
- NUNCA promete resultado ("a viagem perfeita") nem garante clima/câmbio/condição que dependa de
  terceiros — acolhe o sonho do viajante sem criar expectativa fora do controle da equipe.
- NUNCA gerencia o ROTEIRO/ITINERÁRIO dia-a-dia pela conversa — o itinerário é montado e ordenado
  pela equipe no painel. A IA SÓ abre a proposta e captura a aprovação.

EVOLUÇÃO ESTRUTURAL: CLONA o chassi do EVENTOS (camada 8.2) / PROJETOS (8.13) / CASAMENTO (8.7) —
proposta order-based + itens de cotação (total materializado) + gate de aprovação em 2 fases via tag
que muta o estado de um artefato existente. Cliente NÃO é entidade (continua o contact; snapshots na
proposta). UMA escapada nova:

  ESCAPADA — SUB-ENTIDADE NOVA: ROTEIRO / ITINERÁRIO MULTI-DIA (travel_itinerary_days). A proposta de
  viagem tem um ITINERÁRIO dia-a-dia (dia 1: chegada + city tour; dia 2: passeio X; dia 3: praia;
  dia N: retorno), cada DIA com uma DATA (day_date), um título e uma descrição, ORDENADO por data /
  sequência. É a sub-entidade nova, distinta dos itens de COTAÇÃO (que entram no total). DIFERENTE de
  tudo que veio antes:
    - O CRONOGRAMA do eventos/casamento é o roteiro de UM DIA SÓ (ordenado por HORA, horas de um
      mesmo dia — `start_time time`).
    - O ITINERÁRIO de viagens é MULTI-DIA: UMA linha por DIA da viagem, ordenado por DATA (`day_date
      date`) + uma sequência (`day_number`) — pode cobrir 7, 10, 15 dias. Não são horas de um dia,
      são os DIAS da viagem inteira.
    - O CHECKLIST do casamento é PRÉ-evento BINÁRIO; as ETAPAS de projetos são PÓS-aprovação com 3
      estados de progresso. O itinerário NÃO tem status/progresso — é descritivo (o que se faz em
      cada dia), ordenado por data. Não entra no total.
  O itinerário é gerenciado SÓ no painel (SEM tag de IA, igual o cronograma do eventos/casamento). A
  IA não injeta o itinerário no contexto (organizacional do painel).

NÃO TEM nesta SM (registrado pra não inventar): emissão real de passagem/bilhete/voucher (sem GDS,
sem Amadeus/Sabre/Travelport, sem integração com cia aérea), integração com booking/hotel/OTA
(Booking/Expedia/Decolar), pagamento/sinal/parcelas/câmbio (Stripe é #50, fase futura), catálogo de
pacotes/destinos pré-cadastrados (cotação ad-hoc — a equipe digita os itens), conflito de
agenda/data (não há recurso disputado por slot — datas são campos livres; a agência monta N propostas
sobreposto, é cotação, não reserva de recurso), contrato com assinatura digital/PDF/e-sign (o
"contrato" é o estado 'fechada'), seguro-viagem/visto/documentação como entidades, lista de
passageiros/manifesto, múltiplos consultores com agenda/conflito por profissional (catálogo SIMPLES,
atribuição opcional — igual event_planners), anexo de roteiro/voucher/PDF em arquivo (bloqueador
SERVICE_ROLE_KEY — foto/anexo off), lembrete automático de data de viagem (a data é informativa;
scheduler é fase futura). Fases futuras.

DECISÕES CRAVADAS (revisor decidiu pelo Igor):
1. Proposta = artefato central, espelho do event_proposals (order-based, total materializado,
   snapshots de cliente, gate de aprovação em 2 fases). MANTER.
2. DOIS sub-itens distintos no mesmo artefato: itens de COTAÇÃO (entram no total) + ROTEIRO/
   ITINERÁRIO multi-dia (NÃO entra no total). O itinerário é a escapada da SM.
3. Itinerário: cada DIA tem day_number (sequência inteira do dia da viagem, ex. 1, 2, 3…), day_date
   (date NULLABLE — pode haver dia ainda sem data cravada), title e description. SEM status/progresso
   (não é checklist binário nem etapa de 3 estados — é descritivo). Ordenado por day_date asc NULLS
   LAST, day_number asc. Gerenciado SÓ no painel — SEM tag de IA. SEM parity test (é uma sub-entidade
   simples; o que tem parity é o status da PROPOSTA).
4. Funil da PROPOSTA IDÊNTICO ao EventProposalStatus: rascunho→orcada→aprovada→fechada→realizada +
   recusada/cancelada. A trava de itens a partir de 'fechada' (itemsLocked) congela os itens de
   COTAÇÃO E o ROTEIRO.
5. Tags: <proposta_viagem> (ABRE a proposta em rascunho, UM modo só — resolve o contato da conversa;
   total 0, sem itens, sem itinerário; carrega destination/travel_dates/pax/style/briefing) e
   <aprovacao_viagem> (MUTA o estado, decisao aprovada|recusada, só se a proposta está 'orcada').
   Namespaces DISTINTOS de <proposta_evento>/<aprovacao_proposta>, <proposta_casamento>/
   <aprovacao_casamento>, <proposta_projeto>/<aprovacao_projeto> e de TODAS as outras tags.
6. SEM conflito de data transacional: as datas (start_date/end_date da proposta e day_date do
   itinerário) são campos livres (igual event_date). É COTAÇÃO, não reserva de recurso.
7. Cliente NÃO é entidade do core — continua o contact; snapshots customer_name/phone na proposta.

[FUNDAÇÃO — migration 62_viagens.sql (CONFIRMAR o próximo nº livre no disco)]
- ALTER companies CHECK aceitar 'viagens' — COPIAR a lista EXATA de ids da última migration de perfil
  presente no disco e ADICIONAR 'viagens' ao fim, PRESERVANDO TODOS os existentes. Armadilha cravada:
  clonar por `sed s/<perfil>/viagens/g` troca o id antigo na lista do CHECK (remove os demais) em vez
  de acrescentar — depois de qualquer clonagem por sed, CONFERIR que o CHECK tem TODOS os perfis
  (todos os atuais + 'viagens'), não só o novo.
- RLS enable+force, policies via app.company_id(), grants authenticated + service_role (INSERT de
  propostas/itens/itinerário pelo BACKEND via service_role; tenant SELECT/UPDATE — igual às migrations
  30-53). Espelhar 45_eventos.sql inteiro (estrutura, comentários, índices, policies).
- total_cents (na proposta) e line_total_cents (no item de cotação) MATERIALIZADOS no INSERT/UPDATE;
  NÃO colunas geradas (lição end_at/total das SMs anteriores).
- Tabelas (5 novas):
  * travel_consultants — consultores/agentes de viagem (catálogo SIMPLES, sem agenda/conflito;
    espelho event_planners). (id, company_id, name CHECK 1..200, specialty texto livre (nacional/
    internacional/cruzeiro/lua-de-mel/corporativo…), active default true, notes, timestamps).
    Atribuir à proposta é opcional. delete em uso (proposta com consultant_id) → 409 consultant_in_use;
    preferir desativar (active=false).
  * travel_config — config 1:1 com company; SEM horário/slot (não há agenda). (company_id PK,
    business_name nullable (nome da agência), notes, timestamps). Ausente → defaults vazios. Espelho
    event_config.
  * travel_proposals — propostas de viagem (order-based, total materializado, snapshots).
    (id, company_id, contact_id refs contacts on delete set null, consultant_id refs
    travel_consultants on delete set null, conversation_id nullable, customer_name NOT NULL snapshot,
    customer_phone snapshot, destination text (destino desejado; livre), start_date date (ida —
    CAMPO LIVRE), end_date date (volta — CAMPO LIVRE), num_travelers int CHECK >=1 default 1,
    travel_style text (estilo: econômico/conforto/luxo/aventura/lua-de-mel… livre), briefing text
    (o que o cliente sonha + orçamento aproximado em texto), total_cents int NOT NULL default 0
    (MATERIALIZADO), status text CHECK ('rascunho'|'orcada'|'aprovada'|'fechada'|'realizada'|
    'recusada'|'cancelada') default 'rascunho', notes, opened_at, closed_at, status_updated_at,
    timestamps). Espelho event_proposals (event_type→destination/travel_style; event_date→
    start_date/end_date; + num_travelers).
  * travel_proposal_items — itens de COTAÇÃO (entram no total; line_total materializado). (id,
    company_id, proposal_id refs travel_proposals on delete cascade, category text (categoria livre
    ou CHECK leve: 'aereo'|'hospedagem'|'traslado'|'passeio'|'outro' — decisão do agente; recomendo
    CHECK leve com 'outro' de escape), description CHECK 1..200, quantity int default 1 CHECK >0,
    unit_price_cents int CHECK >=0, line_total_cents int CHECK >=0 (= quantity*unit_price,
    materializado), timestamps). Espelho event_proposal_items + a coluna category.
  * travel_itinerary_days — A ENTIDADE NOVA: roteiro multi-dia (ordenado por data/sequência, NÃO entra
    no total). (id, company_id, proposal_id refs travel_proposals on delete cascade, day_number int
    NOT NULL default 1 CHECK >=1 (sequência do dia da viagem), day_date date NULLABLE (data do dia —
    pode estar em aberto), title CHECK 1..200, description, timestamps). Índice (proposal_id, day_date)
    e/ou (proposal_id, day_number). Leitura ordenada por day_date asc NULLS LAST, day_number asc,
    created_at asc. RLS/grants como os sub-itens do eventos (SELECT/UPDATE pelo tenant; INSERT pelo
    backend/service_role). COMMENT cravando que é a sub-entidade nova: ROTEIRO MULTI-DIA (uma linha por
    DIA da viagem, ordenado por data), descritivo SEM status, NÃO entra no total, gerenciado no painel
    sem tag de IA; ≠ cronograma de UM dia ordenado por hora (eventos/casamento), ≠ checklist binário
    (casamento), ≠ etapas de 3 estados (projetos).
- Status da PROPOSTA hardcoded (TravelProposalStatus enum Java + const TS + parity test):
  rascunho → orcada, cancelada ; orcada → aprovada, recusada, cancelada ; aprovada → fechada,
  cancelada ; fechada → realizada, cancelada ; realizada/recusada/cancelada → terminal.
  itemsLocked()=true em fechada/realizada/recusada/cancelada (trava itens de cotação E o itinerário).
  Espelho EventProposalStatus.
- O itinerário NÃO tem enum/parity (sub-entidade descritiva). Só o status da PROPOSTA tem parity.
- TODAS as 5 tabelas novas entram na migration ANTES de tocar o banco (banco se aplica A PARTIR do
  arquivo versionado — lição os_config da SM-J) e na lista de TRUNCATE/SCRIPTS do
  AbstractIntegrationTest.

[BACKEND]
- Consultants: CRUD padrão (espelho EventPlannerService/Controller/Repository). delete em uso
  (proposta com consultant_id) → 409 consultant_in_use; preferir desativar (active=false).
- Config: GET (fallback default) + PUT. (espelho EventConfig*; sem horário)
- Proposals (chassi event_proposals + os 2 editores: COTAÇÃO + ITINERÁRIO):
  * open (a partir da tag/IA ou manual): nasce 'rascunho', total 0, sem sub-itens. Snapshots de
    cliente (customer_name/phone do contact). conversation_id quando veio da conversa.
  * itens de COTAÇÃO: add/update/delete sob requireMutableProposal (trava itemsLocked); cada mutação
    RECALCULA e MATERIALIZA total_cents.
  * itinerário (NOVO): add/update/delete + reorder, TODOS sob a MESMA trava requireMutableProposal;
    SEM recálculo de total.
      - add: cria um dia com day_number = (max(day_number)+1) da proposta; day_date opcional (parse de
        data inválida → 400 invalid_date).
      - update: title/description/day_date/day_number (parse de data inválida → 400 invalid_date).
      - reorder: recebe a ORDEM nova (lista de ids ou {id,day_number}) e re-materializa day_number
        sequencial 1..N na MESMA transação (decisão do agente sobre o formato exato do payload —
        recomendo lista ordenada de ids).
      - Exceptions: itinerary_day_not_found (404), proposal_locked (409), invalid_date (400).
      - leitura ordenada por day_date asc NULLS LAST, day_number asc, created_at asc.
  * updateStatus (da PROPOSTA): valida transição (inválida → 409 invalid_status_transition); ir pra
    'orcada' exige total_cents > 0 (→ 400 empty_budget); terminal preenche closed_at; dispara
    notificação outbound conforme o status.
  * O detalhe da proposta hidrata as DUAS listas (items de cotação + itinerary days).
- Notifier (espelho EventProposalNotifier): best-effort, persiste OUTBOUND/HUMAN, texto defensivo
  SEM promessa de "viagem perfeita" e SEM confirmar voo/hotel emitido. Notifica: orcada (com total +
  destino), aprovada, fechada, recusada. rascunho/realizada/cancelada silenciosos.
- IA:
  * Persona prestativa-consultiva (consultor de viagens) com a TRAVA DE COMPORTAMENTO acima embutida
    no prompt. Adicionar a persona VIAGENS em ProfilePromptContext (espelho do bloco EVENTOS) + branch
    em segmentFor pra 'viagens'.
  * Contexto injetado (cache TTL 20s, espelho EventosContextCache, keyed por (companyId, contactId)):
    consultores ativos + propostas do contato em ABERTO (rascunho/orcada, com id+destino+datas+
    num_travelers+status+total) + instruções e as 2 tags. NÃO injeta o ROTEIRO/ITINERÁRIO
    (organizacional do painel). Invalidação em toda mutação.
  * Tag <proposta_viagem>{"destination":"texto|null","start_date":"YYYY-MM-DD|null","end_date":
    "YYYY-MM-DD|null","num_travelers":N|null,"travel_style":"texto|null","briefing":"texto",
    "consultant_id":"UUID|null","notes":"texto"} → PropostaViagemConfirmHandler (espelho
    PropostaEventoConfirmHandler; cria proposta em rascunho; consultant_id inválido → ignora consultor
    mas abre; datas inválidas → ignora a data mas abre; best-effort).
  * Tag <aprovacao_viagem>{"proposal_id":"UUID","decisao":"aprovada|recusada"} →
    AprovacaoViagemHandler (espelho AprovacaoPropostaHandler; só aplica se a proposta está 'orcada';
    proposta NÃO-orcada → empty).
  * JwtFilter autentica /api/viagens/**. OutboundService ganha maybeProcessPropostaViagem +
    maybeProcessAprovacaoViagem (best-effort, contactId via findContactIdByConversation, encadeados
    APÓS os outros perfis — perfil é único, só um age; REMOVE a tag antes de enviar ao cliente).
- Guard: ViagensProfileGuard (403 forbidden_wrong_profile) — espelho EventosProfileGuard.

[FRONTEND]
- /dashboard/viagens-consultants (CRUD consultores; desativar preferido a excluir),
  /dashboard/viagens-proposals (lista por status com badge de destino/datas; detalhe com DOIS
  editores inline: COTAÇÃO com total recalculado (itens com categoria aéreo/hospedagem/traslado/
  passeio) + ROTEIRO/ITINERÁRIO dia-a-dia ordenado por data — cada linha = um DIA (day_number +
  day_date + título + descrição), botões ↑↓ ou drag pra reordenar (recomputa day_number), form de
  dia+data+título; ambos respeitam a trava por status — somem/recusam quando a proposta está
  fechada+),
  /dashboard/viagens-settings (nome da agência + notas; sem horário).
- types + SDKs (consultants, config, proposals com os endpoints de cotação + itinerário: add/update/
  delete/reorder) espelhando eventos. TravelItineraryDay: { id, proposalId, dayNumber: number,
  dayDate: string|null, title, description, ... }.
- Status TS travel-proposal-status.ts (7 ids, ALLOWED_NEXT, ITEMS_LOCKED, statusLabel) +
  TravelProposalStatusParityTest Java↔TS. (O itinerário NÃO tem parity — é sub-entidade descritiva.)
- getNavForProfile('viagens') injeta "Viagens" (3 itens: Consultores, Propostas, Configurações).
  ATENÇÃO: floricultura ficou no enum SEM branch em getNavForProfile (fallback) — NÃO repetir esse
  gap; viagens PRECISA do branch próprio. Subdomínio viagens.meadadigital.local.
  Paleta: sugerir 'oceano' / 'celeste' / 'teal' (azul/turquesa — combina com viagem/mar/céu).
  ATENÇÃO: as três já EXISTEM em frontend/lib/themes/palettes.ts — então REUTILIZAR a livre, NÃO
  recriar. Conferir no arranque qual delas ainda não está atribuída a outro perfil e usar essa
  (decisão do agente — só registrar a escolha; se as três já estiverem em uso, escolher outra livre
  azul/turquesa ou criar uma nova).
- npm build limpo (Turbopack dev esconde import quebrado — `next build` de prod é a verdade).

[DOCS]
- CLAUDE.md: seção "## Perfil Viagens (camada 8.18)" espelhando as seções de perfil + nota de que é o
  Nº-ésimo perfil real (conferir a contagem real no arranque), CLONA o EVENTOS (proposta order-based
  + aprovação em 2 fases) e inaugura a sub-entidade de ROTEIRO/ITINERÁRIO MULTI-DIA. Documentar
  EXPLÍCITO: a diferença entre itinerário multi-dia (uma linha por DIA, ordenado por data),
  cronograma de UM dia (eventos/casamento, ordenado por hora), checklist binário (casamento) e etapas
  de 3 estados (projetos); itinerário gerenciado só no painel sem tag; a trava de comportamento da IA
  (NUNCA emite passagem, NUNCA confirma voo/hotel/preço não cravado, NUNCA inventa destino/valor); as
  2 tags.
- docs/PERFIL_VIAGENS.md: guia operacional (consultores, propostas — os 2 editores, estados,
  notificações, trava de edição; o editor de ITINERÁRIO multi-dia; como a IA atende; o bloco "o que a
  IA NÃO faz"). Espelhar PERFIL_EVENTOS.md.
- NÃO mexer em system-template.txt nem em outros perfis.

[TESTES BACKEND]
Espelhar a suíte do eventos (service + controller integration por entidade):
- TravelProposalStatusParityTest + ProfileTypeParityTest (viagens no enum/const).
- TravelConsultantServiceTest + ControllerIntegrationTest (CRUD, delete-em-uso 409 consultant_in_use).
- TravelConfigServiceTest/ControllerIntegrationTest (GET fallback + PUT).
- TravelProposalServiceTest (open nasce rascunho/total 0; add item recalcula total; transição válida/
  inválida; orcada sem item → empty_budget; trava itemsLocked em fechada+; ITINERÁRIO: add cria com
  day_number incremental; update; reorder re-materializa day_number sequencial; ORDENA por day_date
  NULLS LAST + day_number; trava proposal_locked quando a proposta está fechada+) +
  ControllerIntegrationTest (os endpoints de cotação E de itinerário: add/update/delete/reorder;
  409 proposal_locked; 404 itinerary_day_not_found; 400 invalid_date; wrongProfile 403).
- PropostaViagemConfirmHandlerTest (abre proposta em rascunho; sem tag → empty; consultant_id inválido
  → ignora consultor mas abre; data inválida → ignora a data mas abre).
- AprovacaoViagemHandlerTest (proposta orcada + decisao aprovada → aprovada; recusada → recusada;
  proposta NÃO-orcada → empty; sem tag → empty).
mvn final = relatar contagem REAL do Surefire (não estimar — vem do `Tests run: N`, nunca de
`grep @Test`).

[CONSTRAINTS DUROS]
- Migration única (próximo nº livre — esperado 62; CONFERIR o maior no disco e usar o PRÓXIMO). Sem
  foto/anexo (bloqueador SERVICE_ROLE_KEY).
- Cliente NÃO é entidade do core — continua o contact; snapshots customer_name/phone na proposta.
- DOIS sub-itens distintos no mesmo artefato: itens de COTAÇÃO (entram no total) + ROTEIRO/ITINERÁRIO
  multi-dia (NÃO entra no total). O itinerário tem day_number + day_date (nullable) + title +
  description, SEM status (descritivo), ordenado por day_date NULLS LAST + day_number, gerenciado só
  no painel (sem tag de IA), trava junto com itemsLocked().
- total_cents/line_total_cents materializados (não generated). start_date/end_date/day_date campos
  livres (sem conflito de agenda — é cotação, não reserva de recurso).
- Funil idêntico ao EventProposalStatus; orcada exige total>0 (empty_budget); trava de itens (E
  itinerário) a partir de fechada.
- IA: NUNCA emite passagem/reserva/voucher, NUNCA confirma voo/hotel/preço/disponibilidade não
  cravada pela equipe, NUNCA inventa destino/item/valor, NUNCA fecha contrato/preço/desconto, NUNCA
  promete "viagem perfeita", NUNCA gerencia o itinerário pela conversa.
- Tags <proposta_viagem> e <aprovacao_viagem> distintas de TODAS as outras (em especial das de
  casamento/eventos/projetos).
- ProfileType: ADICIONAR 'viagens' PRESERVANDO TODOS os ids existentes (enum Java + const TS); o CHECK
  de companies.profile_id na migration ADICIONA 'viagens' sem remover nenhum perfil anterior. Rodar
  ProfileTypeParityTest.
- NÃO mexer em outros perfis nem em system-template.txt. Webhook OFF.
- Cache TTL 20s + invalidação em toda mutação. NÃO injetar o ITINERÁRIO no contexto da IA.
- 529 → inline (retry inline, não abrir sessão extra). Gate de build/teste 3× falho → PAUSAR e
  reportar. Working tree sujo no arranque → PAUSAR. git add EXPLÍCITO arquivo a arquivo (NUNCA
  `git add .` nem wildcard); .env/.env.local/CONTEXT.md/secrets NUNCA staged.
- SEED com timestamptz: usar `at time zone 'America/Sao_Paulo'` (lição do fuso).
- IDs de namespace compartilhado no seed com sufixo NOVO `-16x` (conferir os usados; evitar colisão FK).
- Tabela nova entra na migration ANTES de tocar o banco (lição os_config). Adicionar as 5 tabelas ao
  TRUNCATE/SCRIPTS do AbstractIntegrationTest.
- Decisões menores: agente decide (layout exato, ícones do nav, nome de constante, payload do reorder,
  CHECK de category, paleta final).

[PASSO FINAL — TENANT + SEED + COMMIT + PUSH + SMOKE + RELATÓRIO]
F.1 — TENANT igorhaf29 (Viagens Modelo, profile=viagens) — se igorhaf29 já existe, usar o PRÓXIMO
      livre. Padrão GoTrue (instance_id zero-UUID + colunas de token='' não NULL — lição seed
      auth.users). Senha SÓ em comunicação direta (nunca em arquivo). company c?0000000-...-029 /
      user a?0000000-...-029 (ajustar sufixo ao tenant real). Caddy + /etc/hosts pra
      viagens.meadadigital.local.
F.2 — Seed /tmp/seed-viagens.sql (NÃO COMITAR; `at time zone 'America/Sao_Paulo'`; ids sufixo NOVO
      -16x; lição os_config: as 5 tabelas já existem na migration versionada antes do seed):
  - config: business_name "Agência Voa Modelo".
  - 2 consultores: "Camila Rocha" (specialty "internacional / lua-de-mel"), "Diego Nunes" (specialty
    "nacional / cruzeiros").
  - contact "Patrícia Gomes" +5511966665555 (VINCULADO: instance+conversation, pra smoke de
    notificação) + contact "Rodrigo Alves" +5511977776666 (sem vínculo).
  - 3 propostas cobrindo estados:
    * VINCULADA, status 'orcada' (Patrícia / Camila / destination "Cancún" / start +90d / end +97d /
      num_travelers 2 / style "lua-de-mel") COM 3 itens de cotação (aéreo, hospedagem all-inclusive,
      traslado → total>0) e 5 DIAS de itinerário com day_number 1..5 e day_date variadas (incluindo
      1 dia SEM day_date — pra smoke de ordenação NULLS LAST e reorder).
    * 'rascunho' (Rodrigo / Diego / destination "Gramado" / start +150d / num_travelers 4 /
      style "família") sem itens (pra smoke de empty_budget na transição pra orcada).
    * 'aprovada' (Patrícia / Camila / destination "Buenos Aires") com itens E itinerário (pra smoke de
      transição aprovada→fechada e trava de edição dos itens E do itinerário).
F.3 — JwtFilter /api/viagens/** (se ainda não).
F.4-F.6 — git add EXPLÍCITO arquivo a arquivo dos arquivos da SM + sanity de staging (`git status -s`
      + `git diff --staged --stat` + grep por segredo `eyJ...`/password/secret= + confirmar
      .env/.env.local/CONTEXT.md FORA da staging) + commit. Mensagem padrão multi-linha via
      `git commit -F <arquivo>` (feat(camada-8.18): perfil viagens/Viagens (Agência de viagens) com
      FUNDAÇÃO/BACKEND/FRONTEND/DECISÕES/VALIDAÇÃO contagem REAL/NÃO TOCADO/FECHAMENTO). Trailer
      obrigatório: Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>. Tag fase-8.18-fechada
      (ou o nº real confirmado no arranque).
F.7 — git push origin main + git push origin --tags. NUNCA --force.
F.8 — docker compose restart backend + aguardar /admin/me → 401 missing_auth_header.
F.9 — Smoke E2E (login via POST {SUPABASE_URL}/auth/v1/token?grant_type=password → token ES256 →
      bater no backend):
  BLOCO A: auth — igorhaf29 → /admin/me → role=tenant_admin, profileId=viagens, productName=Viagens.
  BLOCO B: catalog + guard — GET consultants (2), CRUD smoke, delete em uso 409 consultant_in_use;
    GET config + PUT; tenant de OUTRO perfil → /api/viagens/consultants → 403 forbidden_wrong_profile.
  BLOCO C: proposta + cotação — GET proposals (3) com destino correto; abrir a 'rascunho' do Rodrigo;
    POST item de cotação → total recalcula; PATCH rascunho→orcada SEM item (na proposta vazia) → 400
    empty_budget; com item → 200.
  BLOCO D: ITINERÁRIO MULTI-DIA (a escapada desta SM) [CHAVE] —
    - POST dias fora de ordem, incluindo 1 dia SEM day_date → GET retorna ordenado por day_date NULLS
      LAST + day_number (o dia sem data vai ao FIM)
    - PATCH reorder (inverter a ordem) → GET reflete a nova day_number sequencial 1..N
    - PATCH update de um dia (title/day_date) → reflete e reordena
    - DELETE dia → 204
    - tudo isso na proposta 'orcada'/'rascunho' (mutável); na proposta 'fechada' → 409 proposal_locked
  BLOCO E: aprovação em 2 fases + notificação —
    - <aprovacao_viagem>{Patrícia orcada, aprovada} via handler/teste → status vira aprovada
    - aprovacao em proposta NÃO-orcada → empty
    - PATCH status orcada→aprovada (Patrícia vinculada) → 200 + msg outbound (com total/destino);
      asserção casa o conteúdo EXATO (lição do fuso/substring, não só "contém")
    - transição inválida → 409 invalid_status_transition
  BLOCO F: trava em 'fechada' — numa proposta 'fechada', PATCH item de cotação → 409 proposal_locked
    E PATCH/POST de dia de itinerário → 409 proposal_locked (os DOIS sub-itens travam juntos).
  BLOCO G: regressão — os perfis anteriores intactos (smoke leve 1 endpoint cada);
    viagens → /api/eventos/* → 403; viagens → /api/projetos/* → 403 (se existir);
    viagens → /api/floricultura/* → 403.
  BLOCO H: paridade + lição os_config — mvn test -Dtest=TravelProposalStatusParityTest,
    ProfileTypeParityTest → verde; CONFIRMAR que as 5 tabelas existem na migration versionada 62 ANTES
    do seed (a aplicação do banco parte do arquivo versionado, não de DDL solto — lição os_config) e
    que estão no TRUNCATE do AbstractIntegrationTest.
  Cleanup smoke + restaurar seed pristine. mvn final: contagem REAL do Surefire.
F.10 — RELATÓRIO consolidado + DESTAQUE EXPLÍCITO:
  - "Perfil VIAGENS — camada 8.18 (Agência de viagens)"
  - "CLONA o EVENTOS (proposta order-based + aprovação em 2 fases) e inaugura a sub-entidade de
     ROTEIRO/ITINERÁRIO MULTI-DIA"
  - "DOIS tipos de sub-item: cotação (aéreo/hospedagem/traslado/passeio — entra no total) /
     ITINERÁRIO multi-dia (uma linha por DIA, ordenado por data, NÃO entra no total)"
  - "Itinerário multi-dia ≠ cronograma de um dia (hora) ≠ checklist binário ≠ etapas de 3 estados"
  - "BLOCO D prova o itinerário: ordenação por day_date NULLS LAST + day_number, reorder, trava
     proposal_locked"
  - "BLOCO E prova o gate de aprovação em 2 fases + notificação; BLOCO F prova a trava dos DOIS
     sub-itens em fechada"
  - "Itinerário gerenciado só no painel (sem tag de IA); IA não emite passagem, não confirma voo/
     hotel/preço, não inventa destino/valor, não fecha contrato, não promete viagem perfeita"
  - "Seed usou at time zone + sufixo de ids novo -16x (sem fuso/colisão FK)"
  - "as 5 tabelas criadas DENTRO da migration 62 (lição os_config)"
  - PENDÊNCIAS: emissão real de passagem/GDS, integração booking/OTA/cia aérea, pagamento/sinal/câmbio
     (Stripe), catálogo de pacotes/destinos pré-cadastrados, contrato e-sign, seguro-viagem/visto,
     lista de passageiros, lembrete automático de data de viagem, anexo de voucher/PDF, multi-consultor
     com agenda + a dívida acumulada (webhook, cliente real, olho humano sobre os verticais).

[REPORTAR]
Igual SMs anteriores. Incluir EXPLICITAMENTE:
- "ProfileType.VIAGENS adicionado (camada 8.18) — TODOS os perfis anteriores preservados no enum/CHECK"
- "Paridade TravelProposalStatus e ProfileType validadas"
- "Tenant igorhaf29 criado seguindo padrão GoTrue + Caddy/etc/hosts"
- "DOIS sub-itens no mesmo artefato (cotação/itinerário multi-dia); o ITINERÁRIO é a escapada"
- "Itinerário = day_number + day_date (nullable) + title + description, descritivo SEM status,
   ordenado por day_date NULLS LAST + day_number, só no painel, trava junto com itemsLocked"
- "Gate de aprovação em 2 fases: <proposta_viagem> abre, <aprovacao_viagem> muta (só orcada)"
- "OutboundService ganhou maybeProcessPropostaViagem + maybeProcessAprovacaoViagem"
- "JwtFilter autentica /api/viagens/**"
- "getNavForProfile('viagens') com branch próprio (não repetir o gap do floricultura)"
- "Cliente NÃO é entidade do core — continua o contact; snapshots na proposta"
- "as 5 tabelas criadas DENTRO da migration 62 (lição os_config)"
- "Seed: at time zone America/Sao_Paulo + sufixo de ids novo -16x (sem bug de fuso, sem colisão FK)"
- "Paleta escolhida (oceano/celeste/teal — a livre): registrar qual; reutilizada de palettes.ts, não
   recriada"
- "Próximas fases: emissão real de passagem/GDS, booking/OTA, pagamento-sinal/câmbio (Stripe),
   catálogo de pacotes, contrato e-sign, seguro/visto, lista de passageiros, lembrete de data, anexo
   de voucher, multi-consultor com agenda + fila de prioridade (webhook, cliente real, olho humano
   sobre os verticais)"
