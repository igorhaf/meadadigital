>>> JÁ IMPLEMENTADO — perfil oficina, camada 7.9, migration 38_oficina.sql. Prompt de nicho
>>> RETROATIVO, formato T5. Fonte: CLAUDE.md seção Perfil Oficina + migration 38 +
>>> docs/PERFIL_OFICINA.md.

[TAREFA — PERFIL OFICINA / OficinaBot (camada 7.9) — RETROATIVO]

Documentação retroativa, no formato T5, do perfil oficina JÁ IMPLEMENTADO e fechado
(camada 7.9, migration 38_oficina.sql). Este documento descreve o REAL como construído —
não é uma sub-maratona a executar. NONO perfil vertical real do catálogo e PRIMEIRO além
da fila planejada de 8 (sushi/legal/restaurant/dental/salon/pousada/academia/pet/oficina).

[CONTEXTO]
PROJETO MEADA em /home/igorhaf/meada.
Oficina é o template de nicho pra OFICINA MECÂNICA / AUTO CENTER dentro do mesmo dashboard
Meada. O tenant (`profile_id='oficina'`) acessa o produto "OficinaBot": a IA atende clientes
via WhatsApp com tom prestativo-direto, identifica o veículo, ABRE a ordem de serviço (OS) a
partir da queixa, informa o orçamento quando o mecânico já o montou e CAPTURA A APROVAÇÃO do
cliente. O tenant gerencia mecânicos, os veículos dos clientes e as ordens de serviço (com
itens e orçamento) pelo painel. A OS é order-based (itens de peça/mão-de-obra + total).

>>> TRAVA DE COMPORTAMENTO DA IA (cravada) <
- A IA NUNCA diagnostica o defeito (sintoma → orienta avaliação presencial).
- A IA NUNCA inventa preço de peça nem monta orçamento — quem orça é o MECÂNICO, no painel.
- A IA NUNCA promete prazo que não esteja na OS.
- A IA só ABRE a OS (a partir da queixa, sem orçar) e, num turno POSTERIOR, CAPTURA a
  aprovação do cliente (orcada → aprovada/recusada). NÃO move a OS pra fechada/realizada/
  em_execucao nem fecha preço — isso é ação administrativa do painel.

EVOLUÇÃO ESTRUTURAL — combina DOIS padrões anteriores + UMA escapada nova:
1. ORDER-BASED com itens + total materializado (espelho SUSHI: order + line items).
2. SUB-ENTIDADE DE CLIENTE (espelho PET: `os_vehicles` ~ pet_animals — o veículo pertence
   a um contact, que é o dono/cliente; o contact NÃO vira entidade própria).
3. ESCAPADA NOVA — GATE DE APROVAÇÃO EM 2 FASES: a IA (1) ABRE a OS a partir da queixa e,
   num turno POSTERIOR (OS já 'orcada' pelo mecânico), (2) MUTA o estado pra 'aprovada' ou
   'recusada' a partir da resposta do cliente. **PRIMEIRO perfil em que a IA altera o
   estado de um ARTEFATO EXISTENTE via conversa, não só cria.** Materializado por DUAS TAGS
   distintas (uma que abre, outra que aprova/recusa).

DECISÕES CRAVADAS (reais do que foi construído):
1. CLONA o chassi order-based do SUSHI (order + line items + total materializado) e a
   sub-entidade de cliente do PET (veículo do contact). Combina os dois + a escapada nova.
2. Cliente NÃO é entidade do core — continua sendo o `contact`. `os_vehicles.contact_id` é
   a verdade (dono do veículo); a OS guarda snapshots de customer_name/phone.
3. Orçamento é AD-HOC: NÃO há catálogo de peças/serviços pré-cadastrados. O mecânico digita
   os itens (peça/mão-de-obra, qtd, preço unitário) no painel; o total é recalculado.
4. SEM agenda/slot: a oficina trabalha por ordem de serviço, não por horário. `os_config`
   guarda só o horário INFORMATIVO; `expected_delivery` é campo-data livre na OS (não é
   recurso disputado por minuto). NÃO há máquina de conflito 409 nesta SM.
5. Gate de aprovação em 2 fases: a IA abre (tag `<ordem_servico>`) e captura a aprovação
   (tag `<aprovacao_os>`, só se a OS estiver 'orcada'). A IA não fecha preço nem avança o
   resto do funil.
6. Mecânico é catálogo SIMPLES (sem agenda); atribuir mecânico à OS é OPCIONAL.

[FUNDAÇÃO — migration 38_oficina.sql]
- ALTER companies CHECK acrescentando 'oficina' à lista de perfis (preservando todos os
  anteriores: generic/legal/dental/sushi/restaurant/salon/pousada/academia/pet).
- RLS enable+force em todas as tabelas novas; policies via `app.company_id()`; grants
  authenticated + service_role. `service_orders`/`os_items`: INSERT pelo BACKEND
  (service_role) — a OS é criada pela IA via AberturaOsConfirmHandler; o tenant tem
  SELECT/UPDATE (status no funil / itens via painel). `os_mechanics`/`os_vehicles`:
  CRUD completo do tenant (catálogos do painel).
- total_cents (na OS) e line_total_cents (no item) MATERIALIZADOS no INSERT/UPDATE; NÃO
  colunas geradas (recálculo cruza linhas — lição end_at das SMs anteriores). O
  total_cents da OS é recalculado na MESMA transação a cada mutação de item.
- SNAPSHOTS na OS: customer_name/customer_phone (do contact) + vehicle_plate/vehicle_model
  (do veículo). Mudar cliente/veículo depois NÃO altera OS passadas.
- Tabelas:
  * os_mechanics — mecânicos/responsáveis (catálogo SIMPLES, SEM agenda). (id, company_id,
    name CHECK 1..200, specialty texto livre, active default true, notes, timestamps).
    Índices por (company,active) e (company,name).
  * os_config — horário INFORMATIVO da oficina (1:1 com company; PK = company_id; opens_at/
    closes_at default 08:00/18:00; sem lógica de slot). Ausente → defaults.
  * os_vehicles — veículos, SUB-ENTIDADE do contact (cliente). (id, company_id, contact_id
    NOT NULL refs contacts on delete restrict, plate NOT NULL CHECK 1..10 + UNIQUE
    (company_id, plate), brand/model/year/color/mileage_km opcionais, notes, active default
    true = arquivado quando false, timestamps). Placa ÚNICA por tenant.
  * service_orders — ordens de serviço (order-based). (id, company_id, contact_id refs
    on delete set null, vehicle_id NOT NULL refs os_vehicles on delete restrict, mechanic_id
    refs on delete set null OPCIONAL, conversation_id refs on delete set null, snapshots
    customer_name/phone + vehicle_plate/model, complaint NOT NULL = queixa, diagnosis
    nullable, total_cents default 0 MATERIALIZADO, status CHECK default 'aberta',
    expected_delivery date, notes, opened_at/closed_at/status_updated_at, timestamps).
  * os_items — itens da OS. (id, company_id, service_order_id refs on delete CASCADE, kind
    CHECK in ('peca','mao_de_obra'), description CHECK 1..200, quantity default 1 CHECK > 0,
    unit_price_cents CHECK >= 0, line_total_cents CHECK >= 0 = quantity*unit_price
    MATERIALIZADO, timestamps).
- Status da OS hardcoded (OsStatus enum Java + os-status.ts + OsStatusParityTest):
    aberta       → orcada, cancelada
    orcada       → aprovada, recusada, cancelada
    aprovada     → em_execucao, cancelada
    em_execucao  → concluida, cancelada
    concluida    → entregue
    recusada / entregue / cancelada → terminal
  - GATE: 'orcada' exige total_cents > 0 (validado no service → 400 empty_budget — não dá
    pra orçar OS vazia).
  - TRAVA DE ITENS (order_locked): itens só são mutáveis em aberta/orcada/aprovada; em
    em_execucao/concluida/entregue/recusada/cancelada → 409 order_locked.
  - Notificam (texto fixo defensivo): orcada (com total + identificação do veículo),
    aprovada, concluida, entregue. aberta/recusada/em_execucao/cancelada são silenciosos.
- Todas as tabelas novas entram DENTRO da migration 38 (lição os_config) e no TRUNCATE/
  SCRIPTS do AbstractIntegrationTest.

[BACKEND]
- Mecânicos: CRUD (OsMechanicService/Controller/Repository). delete de mecânico em uso por
  OS → 409 mechanic_in_use; caminho preferido é desativar (active=false).
- Veículos: CRUD (OsVehicleService/Controller/Repository). placa UNIQUE por tenant; delete
  de veículo em uso por OS → 409 vehicle_in_use; preferir arquivar (active=false).
- Config: GET (fallback horário default 08:00/18:00) + PUT (OficinaConfigService/Controller).
- Ordens (ServiceOrderService/Controller/Repository):
  * INSERT pelo BACKEND (service_role), via AberturaOsConfirmHandler — não pelo SDK do tenant.
  * Itens (os_items): adicionar/editar/remover recalcula total_cents da OS na MESMA
    transação (materializado). Trava order_locked em estados travados → 409.
  * Status: PATCH com validação de transição (inválida → 409 invalid_status_transition);
    'orcada' exige total > 0 → 400 empty_budget. Notificação outbound por status via
    ServiceOrderNotifier (texto defensivo; orcada com total+veículo; aprovada/concluida/
    entregue; demais silenciosos).
- IA:
  * Persona OFICINA (tom prestativo-direto) com a TRAVA embutida: não diagnostica, não
    inventa preço/orçamento, não promete prazo fora da OS. Sintoma → avaliação presencial.
  * Contexto injetado via OficinaContextCache (Caffeine, TTL 20s): mecânicos ativos +
    veículos do cliente + as OS abertas/orçadas do cliente (pra a IA capturar a aprovação
    na OS CERTA). Invalidação explícita em toda mutação.
  * DUAS TAGS distintas (namespace próprio, distintas de TODAS as outras):
    - <ordem_servico>{...}</ordem_servico> ABRE a OS — AberturaOsConfirmHandler. DOIS modos
      de veículo: (a) "vehicle_id" de um veículo já cadastrado do cliente; (b) "new_vehicle"
      {plate, brand, model, year} cadastra o veículo (sub-entidade do contact da conversa) E
      abre a OS no mesmo turno (espelho pet). Campo "complaint" obrigatório (queixa). Sem
      tag/JSON inválido/complaint faltando/veículo inválido/sem cliente resolvido →
      Optional.empty() + warn (best-effort, sem efeito colateral).
    - <aprovacao_os>{"decisao":"aprovada"|"recusada","service_order_id":...}</aprovacao_os>
      MUTA o estado — AprovacaoOsHandler. SÓ aplica se a OS estiver 'orcada' (senão ignora
      sem efeito); valida o service_order_id por company; chama updateStatus (que valida
      orcada→aprovada/recusada e dispara a notificação). decisao inválida/id faltando/OS
      inexistente/OS não-orcada → Optional.empty() + warn.
  * O OutboundService chama maybeProcessAberturaOs + maybeProcessAprovacaoOs (best-effort,
    encadeados após os perfis anteriores — perfil é único, só um age) e REMOVE as tags da
    mensagem antes de enviá-la ao cliente.
  * JwtAuthenticationFilter autentica /api/oficina/** (além dos 8 perfis anteriores).
- Guard: OficinaProfileGuard.requireOficina — endpoints /api/oficina/** retornam 403
  forbidden_wrong_profile para tenant de outro perfil.

[FRONTEND]
- /dashboard/oficina-mechanics — CRUD de mecânicos (nome + especialidade; ativo/inativo;
  excluir bloqueado se em uso → 409).
- /dashboard/oficina-vehicles — CRUD de veículos (escolher o cliente/contato, placa
  obrigatória/única, marca/modelo/ano/cor/km opcionais; arquivar preferido a excluir;
  excluir bloqueado se houver OS → 409).
- /dashboard/oficina-orders — lista por status (com filtro). Nova OS manual (escolhe veículo,
  mecânico opcional, descreve a queixa; nasce 'aberta' total zero). DETALHE com EDITOR DE
  ITENS INLINE (adiciona peça/mão-de-obra com descrição/qtd/preço; total recalculado a cada
  item; editor bloqueado depois que a OS sai dos estados editáveis). Botões de fluxo de
  status (aberta→orcada exige ≥1 item; orcada→aprovada/recusada; aprovada→em_execucao→
  concluida→entregue; cancelada de qualquer não-final).
- /dashboard/oficina-settings — horário de funcionamento (informativo).
- types + SDKs (mechanics, vehicles, orders + os_items) seguindo o padrão das telas.
- Status TS os-status.ts + OsStatusParityTest.
- getNavForProfile('oficina') injeta o grupo "Oficina" (Mecânicos / Veículos / Ordens /
  Configurações). Subdomínio oficina.meadadigital.local.

[DOCS]
- CLAUDE.md: seção "## Perfil Oficina (OficinaBot, camada 7.9)" cravando: a combinação dos
  2 padrões + a escapada do gate em 2 fases; as 2 tags; a trava order_locked / empty_budget;
  cliente não é entidade; sem agenda/slot.
- docs/PERFIL_OFICINA.md: guia operacional do tenant (mecânicos; veículos; ordens com editor
  de itens e fluxo de status; como a IA atende e o gate de aprovação em 2 fases; "o que o
  OficinaBot NÃO faz").

[TESTES BACKEND]
- OsStatusParityTest (Java OsStatus ↔ TS os-status.ts) + ProfileTypeParityTest
  (ProfileType.OFICINA presente Java↔TS).
- OsMechanicServiceTest + ControllerIntegrationTest (CRUD; delete-em-uso → 409
  mechanic_in_use; wrongProfile → 403).
- OsVehicleServiceTest + ControllerIntegrationTest (CRUD; placa UNIQUE; delete-em-uso → 409
  vehicle_in_use; arquivar).
- OficinaConfigServiceTest/ControllerIntegrationTest (GET fallback + PUT).
- ServiceOrderServiceTest: itens recalculam total_cents; line_total materializado;
  empty_budget (orcada sem item → 400); order_locked (mutar item em estado travado → 409);
  transição inválida → 409 invalid_status_transition; gate orcada→aprovada/recusada;
  snapshots preservados.
- AberturaOsConfirmHandlerTest: tag modo vehicle_id; tag modo new_vehicle (cadastra+abre);
  complaint faltando → empty; veículo inválido → empty; sem tag → empty.
- AprovacaoOsHandlerTest: aprovada/recusada só se 'orcada'; OS não-orcada → empty;
  service_order_id inválido → empty; decisao inválida → empty.

[CONSTRAINTS DUROS]
- Migration única (38). Sem foto/anexo (bloqueador SERVICE_ROLE_KEY).
- Cliente NÃO é entidade do core — continua o contact; o veículo (os_vehicles) é a
  sub-entidade do contact (contact_id NOT NULL). Snapshots de cliente/veículo na OS.
- Orçamento AD-HOC: SEM catálogo de peças/serviços pré-cadastrados; o mecânico digita os
  itens no painel.
- SEM agenda/slot: expected_delivery é campo-data livre; os_config só informativo.
- total_cents/line_total_cents MATERIALIZADOS (não generated); recálculo na mesma transação.
- Gate em 2 fases: a IA ABRE (<ordem_servico>) e CAPTURA aprovação (<aprovacao_os>, só se
  'orcada'); a IA NUNCA fecha preço, NUNCA avança o resto do funil, NUNCA diagnostica.
- Trava order_locked (itens só em aberta/orcada/aprovada) + empty_budget (orcada exige
  total > 0).
- Status hardcoded (parity). DUAS tags distintas de TODAS as outras.
- NÃO mexer em outros perfis nem em system-template.txt. Webhook OFF.
- Cache de contexto TTL 20s + invalidação em toda mutação.

[PASSO FINAL — resumido (o que foi feito ao fechar a SM)]
- Tenant oficina (profile=oficina) provisionado (GoTrue + Caddy/etc/hosts pra
  oficina.meadadigital.local), seed com `at time zone 'America/Sao_Paulo'` e ids de
  namespace com sufixo novo (mecânicos, veículos de um contact vinculado, OS cobrindo os
  estados — aberta, orcada, aprovada — pra smoke do gate + cálculo de total).
- JwtFilter autentica /api/oficina/**. git add EXPLÍCITO dos arquivos da SM (sem
  .env/CONTEXT/secrets), commit semântico feat(camada-7.9), tag fase-7.9-fechada, push
  origin main + tags. docker compose restart backend → /admin/me 401.
- Smoke E2E: auth (profileId=oficina); CRUD de mecânicos/veículos + guard 403 cross-perfil;
  abertura de OS via <ordem_servico> (modos vehicle_id e new_vehicle); editor de itens +
  total recalculado; gate empty_budget; gate em 2 fases via <aprovacao_os> (orcada→aprovada/
  recusada, notificação); transição inválida → 409; order_locked; regressão dos perfis
  anteriores intactos. Paridade OsStatus/ProfileType verde. mvn final verde.

[REPORTAR]
- "ProfileType.OFICINA adicionado (camada 7.9) — 9º perfil real, primeiro além da fila de 8"
- "Paridade OsStatus e ProfileType validadas"
- "EVOLUÇÃO: combina order-based+total materializado (espelho sushi) + sub-entidade de
  cliente (os_vehicles, espelho pet) + GATE DE APROVAÇÃO EM 2 FASES (a IA muta estado de
  artefato existente — inédito)"
- "DUAS tags: <ordem_servico> (abre, modos vehicle_id/new_vehicle) + <aprovacao_os> (muta
  orcada→aprovada/recusada)"
- "Travas: order_locked (itens só em aberta/orcada/aprovada) + empty_budget (orcada exige
  total > 0)"
- "OutboundService ganhou maybeProcessAberturaOs + maybeProcessAprovacaoOs"
- "getNavForProfile('oficina') com branch próprio (Mecânicos/Veículos/Ordens/Configurações)"
- "Cliente não é entidade — veículo é sub-entidade do contact; snapshots na OS"
- "Orçamento ad-hoc (sem catálogo de peças); sem agenda/slot; foto bloqueada"
- "Próximas fases: catálogo de peças, agendamento de entrada, FIPE, foto, pagamento real
  (Stripe), nota fiscal, controle de estoque"
