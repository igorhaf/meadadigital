>>> JÁ IMPLEMENTADO — perfil eventos, camada 8.2, migration 45_eventos.sql. Prompt de nicho
>>> RETROATIVO, formato T5. Fonte: CLAUDE.md seção Perfil Eventos + migration 45 +
>>> docs/PERFIL_EVENTOS.md.

[TAREFA — PERFIL EVENTOS / EventosBot (camada 8.2) — RETROATIVO]

Documento RETROATIVO no formato T5 de um perfil JÁ IMPLEMENTADO e FECHADO (camada 8.2, tag
fase-8.2-fechada, migration 45_eventos.sql aplicada). NÃO há nada a executar — este .md descreve o
que JÁ existe no código, no banco e no frontend, do jeito real. É o 12º perfil vertical real
(sushi/legal/restaurant/dental/salon/pousada/academia/pet/oficina/nutri/barbearia/eventos) e 13º
contando generic.

[CONTEXTO]
PROJETO MEADA em /home/igorhaf/meada. O tenant eventos
(`profile_id='eventos'`) vira um produto de CASA DE FESTAS / BUFFET / CERIMONIAL / ESPAÇO DE
EVENTOS dentro do mesmo dashboard Meada. O tenant acessa eventos.meadadigital.local e vê o produto
"Eventos". A IA atende clientes via WhatsApp com tom prestativo-consultivo de organizador de festa:
ABRE uma PROPOSTA pela conversa, e num turno POSTERIOR (quando a proposta já está orçada pela equipe
no painel) CAPTURA a APROVAÇÃO/RECUSA do cliente.

>>> TRAVA DE COMPORTAMENTO DA IA (cravada) <
- A IA só ABRE proposta e CAPTURA aprovação — NÃO move a proposta pra 'fechada'/'realizada' nem
  fecha preço/contrato/desconto (ações administrativas do painel).
- NUNCA confirma disponibilidade de data não confirmada ("vou verificar com a equipe").
- NUNCA inventa item/valor de pacote (a proposta nasce sem itens — a equipe monta o orçamento no
  painel).
- NUNCA promete estrutura do espaço não informada.

EVOLUÇÃO ESTRUTURAL: CLONA o chassi do OFICINA (camada 7.9) + UMA escapada nova.
- CLONE DO OFICINA — order-based com itens + total materializado + GATE DE APROVAÇÃO EM 2 FASES via
  tag que MUTA o estado de um artefato existente: `service_orders → event_proposals`;
  `os_items → event_proposal_items`; `<aprovacao_os> → <aprovacao_proposta>`. A IA ABRE a proposta
  e, num turno POSTERIOR (proposta 'orcada'), MUTA o estado pra 'aprovada'/'recusada'.
- ESCAPADA — CRONOGRAMA ORDENADO do dia do evento (`event_timeline_items`): PRIMEIRO perfil com
  DOIS tipos de sub-item no MESMO artefato — (1) itens de ORÇAMENTO (`event_proposal_items`:
  descrição+qtd+preço, `line_total_cents` materializado, ENTRAM no `total_cents`) e (2) marcos de
  CRONOGRAMA (`event_timeline_items`: horário+título+descrição, ORDENADOS por `start_time` na
  leitura, NÃO entram no total). O cronograma é o "dia do evento" organizacional (ex.: 19:00
  recepção / 20:00 cerimônia / 23:00 festa). Os `os_items` do Oficina são linhas de PREÇO sem ordem
  temporal; aqui há linhas de PREÇO **e** linhas de CRONOGRAMA. Não confundir.
- SEM conflito de agenda/data: a casa de festas faz ~1 evento/data; `event_date` é CAMPO LIVRE na
  proposta, não recurso disputado por minuto (igual `expected_delivery` do Oficina). NÃO há máquina
  de conflito 409 de data.

DECISÕES CRAVADAS (reais, materializadas no código):
1. CLONA o chassi do OFICINA (order-based + itens + total materializado + gate de aprovação em 2
   fases). Mantém 1:1 onde não conflita.
2. SEM sub-entidade de cliente — o cliente é o próprio contact da conversa; snapshots
   `customer_name`/`customer_phone` na proposta (igual oficina/salon). A tag de abertura tem UM
   modo só (não os 2 modos vehicle_id/new_vehicle do Oficina — a sub-entidade nova é o cronograma,
   filho da PROPOSTA).
3. ESCAPADA cronograma: `event_timeline_items` (sub-item do MESMO artefato), ordenado por
   `start_time`, NÃO entra no total. Espelho de `os_items` SÓ na forma — semântica de TEMPO, não de
   preço.
4. SEM conflito de agenda/data (`event_date` campo livre). Detecção de "data já tem evento" como
   aviso informativo no painel = fase futura.
5. Gate de aprovação em 2 fases: a IA ABRE em 'rascunho' e, num turno posterior com a proposta em
   'orcada', MUTA pra 'aprovada'/'recusada' via tag distinta. A IA NÃO fecha preço/contrato.
6. Duas TAGS distintas, namespace próprio, distintas de TODAS as outras: `<proposta_evento>` (abre)
   e `<aprovacao_proposta>` (muta o estado).

NÃO TEM nesta SM (registrado pra não inventar): conflito de agenda/data; catálogo de pacotes
pré-cadastrados (orçamento ad-hoc, igual o Oficina); contrato com assinatura digital/PDF/e-sign (o
"contrato" é o estado 'fechada'); pagamento/sinal/parcelas (Stripe é #50); fornecedores/terceiros
como entidades com agenda (multi-pool); foto/mood board (bloqueador SERVICE_ROLE_KEY); lista de
convidados/RSVP/mesa. Fases futuras.

[FUNDAÇÃO — migration 45_eventos.sql]
- ALTER companies CHECK aceitar 'eventos' (acrescenta preservando os 11 perfis anteriores +
  generic; lista final: generic/legal/dental/sushi/restaurant/salon/pousada/academia/pet/oficina/
  nutri/barbearia/eventos).
- RLS enable+force em todas as tabelas novas; policies via app.company_id(); grants authenticated +
  service_role. event_proposals + itens (orçamento E cronograma): INSERT pelo BACKEND via
  service_role; o tenant tem só SELECT/UPDATE (gerencia status/itens no painel). event_planners e
  event_config têm CRUD do tenant (authenticated).
- total_cents (na proposta) e line_total_cents (no item de orçamento) MATERIALIZADOS no INSERT/UPDATE;
  NÃO colunas geradas (recálculo cruza linhas — lição end_at das SMs anteriores).
- SNAPSHOTS na proposta: customer_name/customer_phone. Mudar o contato depois NÃO altera propostas
  passadas. Cliente NÃO é entidade própria — continua o contact. LGPD: notes é administrativo.
- Tabelas:
  * event_planners — cerimonialistas/responsáveis (catálogo SIMPLES, SEM agenda/conflito; espelho
    os_mechanics). (id, company_id, name CHECK 1..200, specialty texto livre nullable, active
    default true, notes, timestamps). active=false retira da disponibilidade.
  * event_config — config simples/informativa, 1:1 com company; SEM horário/slot (não há agenda).
    (company_id PK, business_name nullable, notes, timestamps). Ausente → defaults vazios. Espelho
    leve do os_config.
  * event_proposals — propostas (order-based, total materializado, snapshots; espelho
    service_orders). (id, company_id, contact_id on delete set null, planner_id on delete set null
    opcional, conversation_id on delete set null, customer_name NOT NULL snapshot, customer_phone
    snapshot opcional, event_type texto livre SEM enum, event_date date campo LIVRE sem slot,
    guest_count >=0, briefing, total_cents default 0 MATERIALIZADO, status default 'rascunho' CHECK,
    notes, opened_at, closed_at nullable preenchido em terminais, status_updated_at, timestamps).
  * event_proposal_items — itens de ORÇAMENTO (entram no total; espelho os_items linha de PREÇO).
    (id, company_id, proposal_id on delete cascade, description CHECK 1..200, quantity >0 default 1,
    unit_price_cents >=0, line_total_cents >=0 = quantity*unit_price MATERIALIZADO, timestamps).
  * event_timeline_items — A ESCAPADA: marcos de CRONOGRAMA do dia. NÃO entra no total. (id,
    company_id, proposal_id on delete cascade, start_time TIME NOT NULL, title CHECK 1..200,
    description, timestamps). Ordenado por start_time na leitura (idx_evt_timeline_proposal_time).
- Status do pedido/proposta hardcoded (EventProposalStatus enum Java + event-proposal-status.ts +
  EventProposalStatusParityTest), espelho do OsStatus adaptado ao funil:
    rascunho → orcada, cancelada
    orcada   → aprovada, recusada, cancelada
    aprovada → fechada, cancelada
    fechada  → realizada, cancelada
    realizada / recusada / cancelada → terminais (preenche closed_at)
  (rascunho = aberta sem orçamento; orcada = aguardando aprovação; aprovada = cliente aceitou;
  fechada = contrato fechado/sinal combinado fora do app; realizada = a festa aconteceu.)
  Transição inválida → 409 invalid_status_transition. A passagem para 'orcada' exige
  total_cents > 0 → 400 empty_budget (não dá pra orçar sem item).
- Trava de itens (proposal_locked / itemsLocked): itens (orçamento E cronograma) só mutáveis em
  rascunho/orcada/aprovada; em fechada/realizada/recusada/cancelada → 409 proposal_locked (decisão
  cravada: depois que o contrato fecha o escopo congela; antes disso a equipe ainda ajusta).
- Notificações (texto defensivo, SEM promessa de "evento perfeito"): orcada (com total + tipo de
  evento + pedido de sim/não), aprovada, fechada, recusada notificam; rascunho/realizada/cancelada
  silenciosos.
- TODAS as tabelas novas vivem DENTRO da migration 45 (lição os_config) e no TRUNCATE/SCRIPTS do
  AbstractIntegrationTest.

[BACKEND]
Código em src/main/java/com/meada/profiles/eventos/.
- Planners: EventPlanner + Controller/Repository/Service — CRUD de cerimonialistas (catálogo
  simples).
- Config: EventConfig + Controller/Repository/Service — GET (fallback defaults vazios) + PUT.
- Proposals: EventProposal + EventProposalItem + EventTimelineItem +
  Controller/Repository/Service/Notifier. CRUD/gestão das propostas, itens de orçamento (line_total
  materializado; total_cents da proposta recalculado na MESMA transação) e itens de cronograma
  (ordenados por start_time, fora do total). POST manual pelo tenant cria proposta sem
  conversation_id (sem WhatsApp) — não notifica (sem canal). PATCH de status com validação de
  transição (inválida → 409 invalid_status_transition; orcada exige total_cents>0 → 400
  empty_budget). Mutação de item em estado travado → 409 proposal_locked. EventProposalNotifier
  dispara o outbound por status.
- IA — duas TAGS distintas, namespace próprio (distintas de TODAS as outras):
  * <proposta_evento>{...}</proposta_evento> → PropostaEventoConfirmHandler. ABRE a proposta em
    'rascunho' (total 0, SEM itens — a equipe monta o orçamento no painel; espelho da OS aberta sem
    itens). UM modo só (não há sub-entidade tipo veículo). NÃO usa tool calling / responseSchema;
    parse via regex (DOTALL). Resolve o contato pela conversa (contactId → customer_name/phone
    snapshot). Falha (sem tag, JSON inválido, briefing faltando, abertura falha) → Optional.empty()
    + warn, best-effort.
  * <aprovacao_proposta>{...}</aprovacao_proposta> → AprovacaoPropostaHandler. MUTA o estado (clone
    exato do AprovacaoOsHandler do Oficina): `decisao` 'aprovada' ou 'recusada', SÓ aplicada se a
    proposta estiver em 'orcada' (senão ignora em silêncio + log). A transição passa por
    EventProposalService.updateStatus (valida orcada→aprovada/recusada e dispara a notificação). O
    proposal_id é resolvido e validado por company.
- OutboundService: maybeProcessPropostaEvento + maybeProcessAprovacaoProposta, encadeados após os
  outros perfis (perfil é único, só um age). Best-effort; o OutboundService REMOVE a tag (stripTag)
  antes de enviar a mensagem ao cliente.
- EventosContextCache — Caffeine TTL 20s (a proposta não muda a cada segundo): injeta no prompt os
  cerimonialistas ativos + as propostas do cliente em aberto (rascunho/orcada) com
  id+tipo+data+status+total (pra IA capturar a aprovação na proposta ORÇADA certa) + instruções e as
  2 tags. NÃO injeta o cronograma inteiro (organizacional do painel). Invalidação explícita em toda
  mutação.
- Guard: EventosProfileGuard.requireEventos → 403 forbidden_wrong_profile para tenant de outro
  perfil. JwtAuthenticationFilter autentica /api/eventos/** (além dos 11 perfis anteriores).

[FRONTEND]
Telas em frontend/app/(protected)/dashboard/eventos-{planners,proposals,settings}; SDKs em
frontend/lib/api/eventos/; tipos em frontend/profiles/eventos/.
- /dashboard/eventos-planners — CRUD de cerimonialistas.
- /dashboard/eventos-proposals — propostas, com os DOIS editores inline: orçamento (itens com total
  recalculado) + cronograma (marcos ordenados por horário). Botões de transição de status (gate de
  aprovação) + a trava proposal_locked refletida na UI.
- /dashboard/eventos-settings — config (nome do espaço + notas; SEM horário/slot).
- getNavForProfile('eventos') injeta "Eventos" (3 itens: Cerimonialistas / Propostas /
  Configurações). Subdomínio eventos.meadadigital.local. Paleta `ambar` (dourado/champanhe).
- event-proposal-status.ts (espelho do enum Java) + eventos-types.ts. npm build limpo.

[DOCS]
- CLAUDE.md: seção "## Perfil Eventos (EventosBot, camada 8.2)" (já presente) documentando o clone
  do Oficina + a escapada do cronograma ordenado (2 tipos de sub-item), o gate de aprovação em 2
  fases, as 2 tags, e o NÃO TEM.
- docs/PERFIL_EVENTOS.md: guia operacional do tenant (cerimonialistas; propostas + os 2 editores;
  status/gate de aprovação; como a IA atende; "o que a IA NÃO faz").
- NÃO mexer em system-template.txt nem em outros perfis.

[TESTES BACKEND]
Suíte real em src/test/java/com/meada/profiles/eventos/ (+ outbound):
- EventProposalStatusParityTest (paridade Java↔TS do enum de status) + ProfileTypeParityTest.
- EventPlannerServiceTest + EventPlannerControllerIntegrationTest (CRUD; wrongProfile 403).
- EventProposalServiceTest + EventProposalControllerIntegrationTest (criação/itens de orçamento com
  line_total + total_cents recalculado na transação; transições de status; empty_budget na 'orcada';
  proposal_locked em estado travado; wrongProfile 403).
- EventTimelineServiceTest (marcos de cronograma ordenados por start_time; fora do total).
- PropostaEventoConfirmHandlerTest (tag abre proposta em rascunho; sem tag/JSON inválido → empty).
- OutboundEventWiringIntegrationTest (maybeProcessPropostaEvento + maybeProcessAprovacaoProposta;
  tag removida antes do envio; aprovação só aplica em 'orcada').
mvn final = contagem REAL do Surefire (referência da época: mvn 738 verde, tag fase-8.2-fechada).

[CONSTRAINTS DUROS]
- Migration única (45). Sem foto/anexo (bloqueador SERVICE_ROLE_KEY).
- Cliente NÃO é entidade do core — continua o contact (proposta com snapshots customer_name/phone +
  conversation_id/contact_id).
- ESCAPADA cronograma: event_timeline_items, ordenado por start_time, NÃO entra no total. event_date
  é campo livre (SEM conflito de agenda/data 409).
- total_cents (proposta) e line_total_cents (item de orçamento) materializados (NÃO generated).
  Snapshots de cliente na proposta.
- Gate de aprovação em 2 fases: a IA ABRE ('rascunho') e CAPTURA a decisão (orcada→aprovada/recusada)
  via tag distinta; a IA NUNCA fecha preço/contrato/desconto nem move pra fechada/realizada.
- 'orcada' exige total_cents>0 (400 empty_budget). Itens só mutáveis em rascunho/orcada/aprovada
  (409 proposal_locked nos demais).
- Status hardcoded (parity Java↔TS). Tags <proposta_evento> e <aprovacao_proposta> distintas de
  TODAS as outras.
- NÃO mexer em outros perfis nem em system-template.txt. Webhook OFF.
- Cache de contexto TTL 20s + invalidação em toda mutação. NÃO injeta o cronograma inteiro no prompt.

[PASSO FINAL — RESUMIDO (retroativo)]
Perfil JÁ FECHADO: migration 45_eventos.sql aplicada; tenant igorhaf14 (Eventos Modelo,
profile=eventos) + Caddy/etc/hosts pra eventos.meadadigital.local; commit + tag fase-8.2-fechada;
push origin main + tags. Smoke E2E real (HTTP) + provisionamento do tenant igorhaf14 no Supabase
ficaram registrados como PENDENTES (provisionamento manual / sem service_role key na época). Não há
nada a re-executar — este documento é a reconstrução retroativa do que foi entregue.

[REPORTAR]
Itens a destacar (retroativo, já entregues):
- "ProfileType.EVENTOS adicionado (camada 8.2)"
- "Paridade EventProposalStatus e ProfileType validadas"
- "CLONA o OFICINA (order-based + itens + total materializado + gate de aprovação em 2 fases)"
- "ESCAPADA cronograma ordenado: DOIS tipos de sub-item — orçamento entra no total,
  event_timeline_items (cronograma por start_time) NÃO entra"
- "SEM conflito de agenda/data (event_date campo livre)"
- "Cliente NÃO é entidade — continua o contact (snapshots na proposta); tag de abertura UM modo só"
- "Duas tags: <proposta_evento> abre (rascunho); <aprovacao_proposta> muta o estado só se 'orcada'"
- "empty_budget na 'orcada'; proposal_locked nos estados travados (a partir de fechada)"
- "OutboundService ganhou maybeProcessPropostaEvento + maybeProcessAprovacaoProposta"
- "getNavForProfile('eventos') com branch próprio (Cerimonialistas/Propostas/Configurações); paleta ambar"
- "Cache de contexto TTL 20s + invalidação em toda mutação; NÃO injeta o cronograma no prompt"
- "tabelas criadas DENTRO da migration (lição os_config)"
- PENDÊNCIAS da época: smoke E2E HTTP + provisionamento do tenant igorhaf14 (manual/sem service_role).
- Próximas fases: conflito/aviso de data, catálogo de pacotes, contrato e-sign, pagamento/sinal
  (Stripe #50), fornecedores multi-pool, foto/mood board, lista de convidados/RSVP.
