>>> SLOT ATRIBUÍDO (ver docs/prompts-nicho/README.md — fonte única de verdade, tem precedência
>>> sobre qualquer "provisório" no corpo): ordem 12 · camada 8.17 · migration 61_concessionaria.sql ·
>>> tenant igorhaf28 (company/user sufixo -028) · ids de seed sufixo -15x. Reconfirmar no arranque
>>> que a fila não avançou; se avançou, deslocar conforme o README.

[TAREFA — SUB-MARATONA: PERFIL CONCESSIONARIA / Concessionária (Loja de carros) (camada 8.17)]

[CONTEXTO]
PROJETO MEADA em /home/igorhaf/meada.
Monolito multi-tenant que se apresenta como N produtos verticais ("perfis"). Os perfis são
HARDCODED em DOIS arquivos espelhados (enum Java `ProfileType` + const TS `profile-type.ts`) com um
`ProfileTypeParityTest` que falha o build se divergirem — NÃO existe tabela de perfis. Cada tenant
tem EXATAMENTE 1 perfil (`companies.profile_id`, NOT NULL, CHECK na lista dos ids). Adicionar um
perfil novo = editar os 2 arquivos + uma migration de CHECK + rodar a paridade. NUNCA remover um
nicho ao adicionar outro: a migration ACRESCENTA o id novo PRESERVANDO TODOS os existentes (armadilha
real: clonar por `sed s/x/concessionaria/g` troca o id na lista do CHECK em vez de adicionar — depois
de QUALQUER clonagem por sed, CONFERIR que o CHECK tem TODOS os perfis anteriores + 'concessionaria').

Backend Spring Boot 3.3.x + Java 17 Temurin, single-module Maven, JdbcTemplate (NÃO JPA), sem Lombok.
Frontend Next 16 (app router) + React 19 + TS + Tailwind 4 + shadcn/ui + @base-ui/react + TanStack
Query. Banco/Auth Supabase (Postgres 17 + Auth ES256/JWKS + RLS via `app.company_id()`). Migration
própria em `supabase/migrations/`.

Lê CONTEXT.md e o filesystem no arranque pra cravar convenções, nº de migration, contagem do Surefire
e numeração de tenant ANTES de escrever qualquer código. NÃO hardcodar a contagem do mvn — relatar a
REAL do Surefire ao final. Os valores do slot acima (migration 61, camada 8.17, tenant igorhaf28,
sufixo -15x) têm precedência do README, MAS reconfirmar no arranque: conferir o maior nº presente em
`supabase/migrations/` e o maior `igorhafN` já provisionado; se a fila avançou, deslocar a partir do
README. IDs de namespace compartilhado (contacts/instance/conversation) NO SEED com sufixo `-15x` que
NÃO colida com nenhum seed anterior (conferir os já usados).

Concessionária é template de nicho pra LOJA DE CARROS / revenda dentro do mesmo dashboard Meada. O
tenant acessa concessionaria.meadadigital.local e vê o produto "Concessionária". A IA atende clientes
via WhatsApp e faz AS TRÊS COISAS que uma concessionária faz: (1) MOSTRA O ESTOQUE — tem um CATÁLOGO
DE VEÍCULOS (marca/modelo/ano/km/preço/foto via LINK) com status de estoque, a IA lista os carros
disponíveis e filtra por interesse do cliente (marca, faixa de preço, ano); (2) AGENDA TEST-DRIVE de
um veículo específico com data/horário e um vendedor; (3) registra uma PROPOSTA/LEAD de compra de um
veículo (interesse + condição à vista/financiado) que a equipe trabalha no painel — a IA CAPTURA a
intenção, não fecha negócio. É um HÍBRIDO de catálogo de estoque + agenda + proposta-lead. Tom
prestativo, consultivo e direto. A IA NUNCA fecha preço/desconto/financiamento.

>>> TRAVA DE COMPORTAMENTO DA IA (cravada — o coração desta SM) <<<
- A IA NUNCA fecha preço, NUNCA dá desconto, NUNCA negocia condição de pagamento, NUNCA aprova
  financiamento/crédito, NUNCA simula parcela/juros/score — quem fecha negócio e aprova crédito é o
  VENDEDOR/financeira. Para qualquer pedido de desconto/condição especial → "vou registrar seu
  interesse e o vendedor entra em contato com as condições" e cria o LEAD.
- A IA NUNCA inventa veículo, preço, ano, km, opcional, cor ou condição fora do CATÁLOGO. Só mostra o
  que está cadastrado e DISPONÍVEL. Veículo 'reservado'/'vendido' NÃO é oferecido como disponível.
- A IA NUNCA promete entrega, prazo de documentação/emplacamento, ou disponibilidade não confirmada
  ("isso o vendedor confirma com você"). NUNCA garante que o carro ainda estará disponível ("posso
  agendar um test-drive / registrar seu interesse e o vendedor confirma a disponibilidade").
- A IA só faz TRÊS coisas: MOSTRA o estoque disponível, AGENDA test-drive de um veículo, e REGISTRA o
  lead/interesse de compra com a condição que o cliente declarar (à vista / financiado). A IA NÃO
  muda o status de ESTOQUE do veículo (disponível→reservado→vendido é AÇÃO HUMANA do painel) e NÃO
  muda o status do LEAD (novo→em_negociacao→fechado/perdido é da equipe).
- O preço é SEMPRE o do catálogo — a IA repete o preço cadastrado, nunca arredonda nem "fecha em".
- TEST-DRIVE: a IA NUNCA marca em horário ocupado do vendedor nem fora da janela de funcionamento —
  quem valida o conflito é o backend (choque → 409 conflict_slot). A IA também NÃO agenda test-drive
  de veículo não-disponível (o backend rejeita).

EVOLUÇÃO ESTRUTURAL: a ESCAPADA que justifica perfil próprio é a CONVIVÊNCIA, num só perfil, de TRÊS
artefatos com naturezas distintas — um CATÁLOGO DE ESTOQUE com CICLO DE VIDA PRÓPRIO (não é pedido
nem proposta tradicional: é STATUS DE ESTOQUE `disponivel → reservado → vendido`, do qual o veículo
VENDIDO SAI DA DISPONIBILIDADE), uma AGENDA leve de test-drive (conflito por VENDEDOR, espelho
dental/salon) e um LEAD/proposta de compra (espelho do funil de oficina/eventos). O VEÍCULO é a
ENTIDADE CENTRAL: tanto o test-drive quanto o lead REFERENCIAM um veículo do catálogo (FK), e o ciclo
de estoque do veículo é independente dos dois. É o primeiro perfil em que o "produto" do nicho é um
ITEM DE ESTOQUE com identidade única e status próprio (≠ catálogo reabastecível de comida/floricultura
onde o item é um TIPO, não uma unidade física), e em que a IA opera sobre ESSE item por TRÊS lentes
distintas (vitrine, agenda, lead) sem nunca alterar o estoque.

CONTRASTE com os moldes (cravar pra não confundir):
- vs OFICINA: lá o `os_vehicles` é veículo DO CLIENTE (sub-entidade do contact). AQUI o `vehicles` é
  ESTOQUE DA LOJA (pertence à company, não a um contact) — molde de TABELA de veículo, mas semântica
  invertida. O contact NÃO é dono do veículo; é o interessado.
- vs OFICINA/EVENTOS (order-based): o LEAD aqui NÃO é order-com-itens-e-total. É um registro de
  INTERESSE em UM veículo, com condição de pagamento, que a equipe trabalha — mais perto do funil de
  status do que de um pedido com line items. Preço de referência = preço do veículo no catálogo
  (snapshot). SEM itens, SEM modifiers, SEM total recalculado por linhas.
- vs DENTAL/SALON (agenda): o test-drive clona o conflito-por-recurso, MAS o recurso de conflito é o
  VENDEDOR (escolha cravada), não o veículo. Dois clientes podem test-driveiar o MESMO modelo em
  horários distintos; o que não pode é o mesmo VENDEDOR em dois test-drives sobrepostos.

NÃO TEM nesta SM (registrado pra não inventar): upload de FOTO (a foto do veículo é LINK colado —
`photo_url`, bloqueador SERVICE_ROLE_KEY pra storage), financiamento real / simulação de parcela /
consulta de score / aprovação de crédito (PROIBIDO por trava; condição à vista/financiado é só uma
FLAG declarativa no lead), tabela FIPE / avaliação de usado / carro na troca (trade-in é fase futura),
reserva com sinal/pagamento (Stripe é #50; 'reservado' é mudança de status MANUAL no painel, sem
cobrança), documentação/emplacamento/transferência (fluxo de pós-venda é fase futura), múltiplas
unidades físicas do mesmo modelo como linhas separadas com VIN/chassi (cada `vehicle` JÁ é uma unidade
única — placa/VIN opcional, mas sem controle de chassi formal), test-drive com termo/CNH digitalizada
(o test-drive é só agenda; documento é processo de loja fora do app), histórico de propostas
competindo pelo mesmo carro com leilão/prioridade (lead é simples; vários leads no mesmo veículo
coexistem — a equipe decide), combo/cupom/garantia estendida como produto, multi-loja/multi-pátio.

DECISÕES CRAVADAS (revisor decidiu pelo Igor):
1. A concessionária faz AS TRÊS COISAS (estoque + test-drive + lead) — é HÍBRIDO deliberado. NÃO
   reduzir a um só fluxo. Os três coexistem HARMONICAMENTE no perfil 'concessionaria' (regra do
   projeto: feature de um fluxo não pode quebrar outro).
2. O VEÍCULO é ESTOQUE DA LOJA (FK pra company, não pra contact). Ciclo de estoque hardcoded com
   parity: `disponivel → reservado, vendido`; `reservado → disponivel, vendido`; `vendido → terminal`.
   Veículo NÃO-disponível NÃO entra na vitrine da IA e NÃO aceita test-drive/lead que o exija
   disponível (decisão: test-drive e lead SÓ de veículo 'disponivel'; reservado/vendido → 409/422).
3. TEST-DRIVE clona a AGENDA do DENTAL: vendedor (profissional) + conflito POR VENDEDOR (janela
   half-open, re-verificado DENTRO da transação do INSERT — fecha a janela de corrida) + end_at
   MATERIALIZADO no INSERT (start_at + duration_minutes; NÃO coluna gerada — lição timestamptz+interval
   não é IMMUTABLE) + duração SNAPSHOT do config + status hardcoded com parity. O test-drive referencia
   `vehicle_id` (FK) E `salesperson_id` (FK). Conflito é por salesperson_id (paralelismo entre
   vendedores). Notifica confirmado (com vendedor+veículo+data/hora) e cancelado; agendado/realizado/
   no_show silenciosos (texto defensivo).
4. LEAD clona o FUNIL de status do OFICINA/EVENTOS (sem itens/total): registro de interesse em UM
   `vehicle_id`, condição `avista|financiado`, status `novo → em_negociacao → fechado, perdido` (com
   transições cravadas), SNAPSHOTS de veículo (marca/modelo/ano/preço do momento) + cliente (nome/
   telefone do contact). A IA cria o lead (status nasce 'novo'); a equipe trabalha no painel. A IA
   NÃO move o lead de status.
5. SNAPSHOTS: o test-drive e o lead congelam marca/modelo/ano (+ preço no lead) do veículo no momento.
   Editar/vender o veículo depois NÃO altera test-drives/leads passados.
6. Cliente NÃO é entidade do core — continua o contact (test-drive e lead têm conversation_id/
   contact_id; snapshots de nome/telefone). Igual oficina/eventos.
7. Status hardcoded com parity para os TRÊS: `VehicleStatus` (estoque), `TestDriveStatus` (agenda) e
   `LeadStatus` (funil). Cada um com parity test Java↔TS.
8. DUAS TAGS distintas (namespace próprio, distintas de TODAS as outras): <testdrive_carro> (agenda
   test-drive) e <lead_carro> (registra interesse de compra). O backend valida tudo; o OutboundService
   REMOVE a tag antes de enviar ao cliente.
9. Foto do veículo é LINK (photo_url) — sem upload. Preço sempre do catálogo.

[FUNDAÇÃO — migration 61_concessionaria.sql (CONFIRMAR primeiro nº livre)]
- ALTER companies CHECK aceitar 'concessionaria' ACRESCENTANDO à lista existente (espelhar como
  45_eventos/50_pizzaria fizeram: `drop constraint companies_profile_id_check; add constraint ...
  check (profile_id in ('generic',...,'concessionaria'))`). CONFERIR após escrever que TODOS os
  perfis anteriores (16+) continuam na lista — não removidos por clonagem de sed.
- RLS enable+force, policies via app.company_id(), grants authenticated + service_role. Espelhar
  33_dental.sql (agenda) E 38_oficina.sql/45_eventos.sql (lead/funil) lado a lado.
  * VEÍCULOS (vehicles): catálogo de ESTOQUE. Tenant CRUD completo (authenticated insert/update/delete
    + service_role). O veículo é gerido no painel pela loja — espelho de um catálogo (sushi_menu_items/
    floricultura_catalog_items), MAS com status de estoque.
  * VENDEDORES (salespeople): catálogo simples (~os_mechanics/salon_professionals). Tenant CRUD
    completo.
  * TEST-DRIVES (test_drives): INSERT pelo BACKEND via service_role (IA via TestDriveConfirmHandler OU
    tenant via POST manual). Tenant só SELECT/UPDATE (status na agenda/Kanban) — SEM policy
    authenticated de insert (espelho dental_appointments).
  * LEADS (leads): INSERT pelo BACKEND via service_role (criado pela IA via LeadCarroConfirmHandler OU
    tenant via POST manual). Tenant só SELECT/UPDATE (trabalhar o funil/status no painel). Espelho
    service_orders (insert backend, update tenant).
- end_at do test-drive MATERIALIZADO no INSERT (start_at + duration_minutes); NÃO coluna gerada (lição
  timestamptz+interval não é IMMUTABLE).
- SNAPSHOTS: marca/modelo/ano no test-drive; marca/modelo/ano/preço no lead. Alterar/vender o veículo
  no catálogo NÃO altera test-drives/leads passados.
- Tabelas:
  * concessionaria_config — config 1:1 com company. Janela/duração do TEST-DRIVE (duration_minutes
    default 45 check 15..240, buffer_minutes default 0, opens_at default '09:00', closes_at default
    '18:00') + business_name nullable (nome da loja, texto livre) + notes. Ausente → defaults. Clone
    de dental_clinic_config + um nome de loja (estilo event_config.business_name).
  * concessionaria_salespeople — vendedores (catálogo, ~ salon_professionals/os_mechanics). (id,
    company_id, name CHECK 1..200, phone nullable, active default true, notes, timestamps). Conflito de
    agenda do test-drive é POR salesperson_id.
  * concessionaria_vehicles — ESTOQUE de veículos da loja (catálogo com ciclo de estoque). (id,
    company_id references companies on delete restrict, brand text NOT NULL CHECK 1..80 ("Toyota"),
    model text NOT NULL CHECK 1..120 ("Corolla XEi"), model_year int CHECK (1900..2100), mileage_km int
    CHECK (>= 0) (0 = zero-km), price_cents int NOT NULL CHECK (>= 0), color text nullable, fuel text
    nullable ("flex","diesel","eletrico" — texto livre, SEM enum), transmission text nullable ("manual",
    "automatico"), plate text nullable (placa/identificador, SEM unique forte — usado é opcional),
    photo_url text nullable (LINK colado — sem upload), description text nullable, status text NOT NULL
    default 'disponivel' CHECK in sync com enum ('disponivel','reservado','vendido'), active boolean NOT
    NULL default true (false = oculto do painel/vitrine sem perder histórico), timestamps +
    status_updated_at). Índices: (company_id, status, active) WHERE active=true (vitrine);
    (company_id, brand, model). Comment cravando: status é CICLO DE ESTOQUE; 'vendido' sai da
    disponibilidade; foto é LINK; preço é o de referência (snapshot no lead).
  * concessionaria_test_drives — test-drives (clone dental_appointments com salesperson_id + vehicle_id).
    (id, company_id, vehicle_id NOT NULL references concessionaria_vehicles on delete restrict,
    salesperson_id NOT NULL references concessionaria_salespeople on delete restrict, conversation_id
    nullable references conversations on delete set null, contact_id nullable references contacts on
    delete set null, customer_name text (snapshot do contact, nullable p/ POST manual), vehicle_brand
    text + vehicle_model text + vehicle_year int (SNAPSHOTS do veículo), start_at timestamptz NOT NULL,
    duration_minutes int NOT NULL (snapshot do config), end_at timestamptz NOT NULL (materializado no
    INSERT), status text NOT NULL CHECK ('agendado','confirmado','realizado','cancelado','no_show')
    default 'agendado', notes ADMINISTRATIVO, timestamps + status_updated_at). Índice CRÍTICO de
    conflito: (company_id, salesperson_id, start_at) WHERE status in ('agendado','confirmado').
  * concessionaria_leads — interesse de compra de UM veículo (funil, SEM itens). (id, company_id,
    vehicle_id NOT NULL references concessionaria_vehicles on delete restrict, conversation_id nullable
    references conversations on delete set null, contact_id nullable references contacts on delete set
    null, customer_name text (snapshot, nullable p/ POST manual), customer_phone text nullable
    (snapshot), vehicle_brand text + vehicle_model text + vehicle_year int + vehicle_price_cents int
    (SNAPSHOTS do veículo no momento do lead), payment_condition text NOT NULL CHECK
    ('avista','financiado') default 'avista', status text NOT NULL CHECK
    ('novo','em_negociacao','fechado','perdido') default 'novo', salesperson_id uuid nullable references
    concessionaria_salespeople on delete set null (vendedor atribuído no painel — opcional), notes,
    lost_reason text nullable (motivo de 'perdido', defensivo), timestamps + status_updated_at). Índices:
    (company_id, status, created_at desc); (company_id, vehicle_id); (company_id, contact_id). Comment
    cravando: lead é INTERESSE, não pedido; preço é SNAPSHOT; a IA cria 'novo', a equipe trabalha o funil.
- Status do VEÍCULO/ESTOQUE hardcoded (VehicleStatus enum Java + const TS + parity test):
    disponivel → reservado, vendido
    reservado  → disponivel, vendido
    vendido    → terminal
  Mudança de status do veículo é AÇÃO HUMANA no painel (a IA não toca). Vendido NÃO notifica via
  WhatsApp por padrão (sem canal garantido; é estoque interno). Sem notificação automática de estoque
  nesta SM (decisão: o veículo não tem um "cliente dono" pra notificar — diferente do test-drive/lead).
- Status do TEST-DRIVE hardcoded (TestDriveStatus enum Java + const TS + parity test):
    agendado   → confirmado, cancelado
    confirmado → realizado, cancelado, no_show
    realizado/cancelado/no_show → terminal
  Notifica: confirmado (com vendedor + veículo + data/hora), cancelado (texto defensivo). agendado/
  realizado/no_show silenciosos (quem furou não recebe sermão). Texto SEM promessa de venda/preço.
- Status do LEAD hardcoded (LeadStatus enum Java + const TS + parity test):
    novo          → em_negociacao, perdido
    em_negociacao → fechado, perdido
    fechado/perdido → terminal
  A IA cria o lead em 'novo' e NÃO move. A equipe move no painel. Notifica: nenhum por padrão (o lead é
  trabalho interno; o cliente já recebeu a confirmação de recebimento da IA). 'fechado'/'perdido' são
  registro interno — NÃO notificam automaticamente nesta SM (decisão cravada: evitar "você perdeu o
  carro" automático; comunicação de fechamento é o vendedor). (Se quiser, notificação opcional de
  'fechado' como "parabéns" é fase futura.)
- TODAS as tabelas novas entram na migration 61 ANTES de tocar o banco (lição os_config) e no
  TRUNCATE/SCRIPTS do AbstractIntegrationTest.

[BACKEND]
- Vendedores: CRUD — espelho salon_professionals/os_mechanics. delete de vendedor com test-drive OU
  lead atribuído → 409 salesperson_in_use; preferir arquivar (active=false).
- Veículos (ESTOQUE): CRUD completo (brand/model/year/km/price/color/fuel/transmission/plate/photo_url/
  description) + transição de status de estoque (PATCH status com validação de transição VehicleStatus;
  inválida → 409 invalid_status_transition). delete de veículo com test-drive OU lead → 409
  vehicle_in_use; preferir marcar vendido/inativo. Cache do bloco de catálogo/vitrine injetado no prompt
  (Caffeine), INVALIDADO em toda gravação/edição/exclusão/mudança de status de veículo.
- Config: GET (fallback janela/duração default + business_name null) + PUT. Config FUNDIDA (test-drive
  + dados da loja numa só).
- TEST-DRIVE (agenda): criado pelo BACKEND via TestDriveConfirmHandler OU POST manual do tenant.
    * Pré-condição: o veículo precisa estar 'disponivel' (status='disponivel' E active=true) → senão
      422 vehicle_not_available (a IA não devia oferecer test-drive de carro indisponível; o backend
      reforça).
    * Conflito POR salesperson_id: findConflict transacional (janela half-open
      `NOT (end_at <= newStart OR start_at >= newEnd)`, só status bloqueantes 'agendado'/'confirmado',
      por company+salesperson), RE-VERIFICADO DENTRO da transação antes do INSERT (fecha a janela de
      corrida). Choque → 409 conflict_slot (com detalhes de quem ocupa). MESMO horário com vendedor
      DIFERENTE → OK (paralelismo). end_at materializado no INSERT.
    * Janela opens_at..closes_at validada no fuso America/Sao_Paulo (HARDCODED — pendência, igual
      dental). duration_minutes snapshot do config. SNAPSHOTS de vehicle_brand/model/year + customer_name.
    * Status: PATCH com validação de transição (inválida → 409 invalid_status_transition) + notificação
      outbound por status (texto defensivo). POST manual sem conversation_id → não notifica (sem canal).
- LEAD (funil): criado pelo BACKEND via LeadCarroConfirmHandler OU POST manual do tenant.
    * Pré-condição: o veículo precisa estar 'disponivel' → senão 422 vehicle_not_available (não se
      registra lead de carro já vendido; reservado é decisão — cravar: lead SÓ de 'disponivel').
    * payment_condition ∈ {avista, financiado}; default avista. SNAPSHOTS de marca/modelo/ano + PREÇO do
      veículo no momento (vehicle_price_cents) + cliente (nome/telefone). status nasce 'novo'.
    * Status: PATCH com validação de transição LeadStatus (inválida → 409 invalid_status_transition);
      atribuir vendedor (salesperson_id) é UPDATE do painel; lost_reason ao mover pra 'perdido'.
    * A IA NUNCA fecha/negocia preço — o lead não carrega preço da IA; o backend usa o preço do catálogo
      (snapshot). A IA NÃO move status.
- IA:
  * Persona prestativa-consultiva-direta com a TRAVA DE COMPORTAMENTO embutida em
    ProfilePromptContext.CONCESSIONARIA (NUNCA fecha preço/desconto/financiamento, NUNCA aprova crédito,
    NUNCA simula parcela, NUNCA inventa veículo/preço/condição fora do catálogo, NUNCA promete entrega/
    disponibilidade não confirmada; só mostra estoque DISPONÍVEL, agenda test-drive, registra lead).
    Branch próprio no switch de ProfilePromptContext (NÃO mexer nos branches dos outros perfis).
  * Contexto injetado (via cache Caffeine — cravar UM ConcessionariaContextCache TTL 30s cobrindo os
    três; o estoque muda quando vende/reserva, a agenda muda quando agenda — 30s é o meio-termo): a
    VITRINE = veículos 'disponivel'+active (marca/modelo/ano/km/preço/cor/combustível/câmbio/descrição/
    link da foto) + vendedores ativos + SLOTS LIVRES por vendedor (próximos 14 dias, FLUXO test-drive)
    + business_name + as DUAS tags com seus formatos. NÃO injeta veículos reservado/vendido na vitrine.
    Invalidação explícita em toda mutação (veículo/estoque/vendedor/test-drive/config/lead).
  * Tag <testdrive_carro>{"vehicle_id","salesperson_id","date":"YYYY-MM-DD","start_time":"HH:MM",
    "notes":"...|null"} → TestDriveConfirmHandler (espelho ConsultaConfirmHandler do dental: resolve o
    contato da conversa, valida veículo disponível, conflito por vendedor, cria o test-drive, snapshots).
    Best-effort.
  * Tag <lead_carro>{"vehicle_id","payment_condition":"avista|financiado","notes":"...|null"} →
    LeadCarroConfirmHandler (resolve o contato da conversa, valida veículo disponível, snapshot de
    marca/modelo/ano/preço + cliente, cria lead em 'novo'). Best-effort.
  * JwtAuthenticationFilter autentica /api/concessionaria/ (adicionar
    CONCESSIONARIA_PATH_PREFIX = "/api/concessionaria/" + o `!uri.startsWith` na cadeia, espelho dos
    perfis existentes). OutboundService ganha DOIS handlers encadeados na cadeia de maybeProcess (após
    os outros perfis — perfil é único, só um age): maybeProcessTestDrive + maybeProcessLeadCarro.
    Best-effort; remove a tag antes de enviar. ConcessionariaProfileGuard.requireConcessionaria nos
    endpoints /api/concessionaria/** (403 forbidden_wrong_profile para tenant de outro perfil).

[FRONTEND]
- Telas (sidebar grupo "Concessionária"):
  * /dashboard/concessionaria-vehicles — CRUD do ESTOQUE (marca/modelo/ano/km/preço/cor/combustível/
    câmbio/placa/link-da-foto/descrição) + toggle de status de estoque (disponível/reservado/vendido)
    com a transição validada; mostra preview da foto pelo link. Espelho de um catálogo (floricultura-
    menu/sushi-menu) + bloco de status de estoque.
  * /dashboard/concessionaria-salespeople — CRUD vendedores (espelho salon-professionals).
  * /dashboard/concessionaria-testdrives — agenda de test-drives (Kanban/lista por status; PATCH de
    status; mostra veículo + vendedor + data/hora + cliente). Espelho da agenda dental.
  * /dashboard/concessionaria-leads — Kanban/lista do funil de leads (novo/em_negociacao/fechado/
    perdido); PATCH de status com validação; atribuir vendedor; o detalhe mostra o veículo (snapshot),
    a condição (à vista/financiado), o cliente, e o motivo ao marcar 'perdido'. Espelho do funil de
    service_orders/event_proposals (sem editor de itens — lead é simples).
  * /dashboard/concessionaria-settings — business_name + janela/duração do test-drive.
- types + SDKs (vehicles, salespeople, testdrives, leads, config) espelhando dental + oficina/eventos.
- Status TS concessionaria-vehicle-status.ts + concessionaria-test-drive-status.ts +
  concessionaria-lead-status.ts + parity tests (vehicle status + test-drive status + lead status).
- getNavForProfile('concessionaria') injeta "Concessionária" (5 itens: Estoque, Vendedores,
  Test-drives, Leads, Configurações), no mesmo padrão dos branches existentes em
  frontend/components/layout/nav-config.tsx (dental/oficina/eventos já têm branch — seguir o modelo).
  Subdomínio concessionaria.meadadigital.local. Paleta: agente escolhe — sugestão 'aco', 'grafite' ou
  'meia-noite' (tons de cinza/azul-escuro casam com carros/automotivo); CONFERIR que a paleta escolhida
  não colide com outro perfil já existente, senão preferir a próxima da sugestão.
- npm build limpo (Turbopack dev esconde import quebrado — `next build` de prod é a verdade).

[DOCS]
- CLAUDE.md: seção "## Perfil Concessionária (ConcessionariaBot, camada 8.17)" espelhando as seções de
  perfil + nota de que é HÍBRIDO TRIPLO (catálogo de ESTOQUE com ciclo próprio + agenda de test-drive
  clonando DENTAL/SALON com conflito por VENDEDOR + lead/funil clonando OFICINA/EVENTOS sem itens).
  Documentar EXPLÍCITO: o veículo é estoque da LOJA (≠ os_vehicles do cliente na oficina); ciclo de
  estoque disponivel→reservado→vendido (vendido sai da disponibilidade); test-drive e lead SÓ de
  veículo disponível (422 vehicle_not_available); conflito por salesperson_id re-verificado na
  transação (409 conflict_slot); end_at materializado; snapshots; trava de comportamento (IA não fecha
  preço/desconto/financiamento, não aprova crédito, não inventa veículo/preço); as DUAS tags
  <testdrive_carro> e <lead_carro>; os TRÊS status hardcoded com parity.
- docs/PERFIL_CONCESSIONARIA.md: guia operacional do tenant (cadastro do estoque com foto via link e
  ciclo de status; vendedores; agenda de test-drives + Kanban; funil de leads + atribuir vendedor +
  motivo de perdido; condição à vista/financiado; como a IA atende os três fluxos; "o que a IA NÃO
  faz" — não fecha preço, não dá desconto, não aprova financiamento, não inventa carro/preço, não
  promete entrega). Espelhar PERFIL_DENTAL.md + PERFIL_OFICINA.md.
- NÃO mexer em system-template.txt nem em outros perfis.

[TESTES BACKEND]
Espelhar a suíte do dental (agenda) + oficina/eventos (funil/status):
- VehicleStatusParityTest + TestDriveStatusParityTest + LeadStatusParityTest + ProfileTypeParityTest.
- ConcessionariaSalespersonServiceTest + ControllerIntegrationTest (CRUD; delete-em-uso 409;
  wrongProfile 403).
- ConcessionariaVehicleServiceTest + ControllerIntegrationTest (CRUD; transição de status de estoque
  disponivel→reservado→vendido OK, transição inválida → 409 invalid_status_transition; veículo
  'vendido' NÃO aparece na vitrine/disponibilidade — CHAVE da escapada; invalida cache; delete-em-uso
  409; wrongProfile 403).
- ConcessionariaConfigServiceTest/ControllerIntegrationTest (GET fallback + PUT; janela/duração default
  + business_name).
- ConcessionariaTestDriveServiceTest [CHAVE do FLUXO AGENDA]:
    * agendar test-drive OK (end_at materializado = start + duration; snapshots de veículo gravados).
    * conflito POR VENDEDOR → 409 conflict_slot; MESMO horário com vendedor DIFERENTE → OK (paralelismo).
    * fora da janela opens..closes → rejeitado.
    * test-drive de veículo NÃO-disponível (reservado/vendido) → 422 vehicle_not_available.
    * transição de status (agendado→confirmado→realizado; cancelado/no_show); inválida → 409.
- ConcessionariaLeadServiceTest [CHAVE do FLUXO LEAD]:
    * criar lead OK (status 'novo'; snapshot de marca/modelo/ano/PREÇO do veículo no momento; cliente).
    * lead de veículo NÃO-disponível → 422 vehicle_not_available.
    * payment_condition avista/financiado; default avista.
    * transição de funil (novo→em_negociacao→fechado; novo/em_negociacao→perdido com lost_reason);
      inválida → 409 invalid_status_transition.
    * atribuir vendedor (salesperson_id) no painel — UPDATE OK.
    * a IA NÃO fecha preço: o lead usa o preço do CATÁLOGO (snapshot), nunca um preço vindo da tag.
- TestDriveConfirmHandlerTest: tag de test-drive válida → cria; veículo indisponível → empty; conflito
  → empty; sem tag → empty.
- LeadCarroConfirmHandlerTest: tag de lead válida → cria com snapshots + status 'novo'; veículo
  indisponível → empty; payment_condition inválido → empty; sem tag → empty.
- Status/funil do lead + transições + atribuição de vendedor (cobertos acima).
- TRAVA (cravado nos testes da persona/handler quando aplicável): a IA não fecha preço/financiamento —
  o LeadCarroConfirmHandler NUNCA deriva preço da tag (usa o snapshot do catálogo); o
  TestDriveConfirmHandler/LeadCarroConfirmHandler NUNCA mudam o status de ESTOQUE do veículo. (Teste de
  regressão: criar lead/test-drive NÃO altera vehicles.status; o preço do lead = preço do catálogo,
  independente do que a tag dissesse.)
mvn final = relatar contagem REAL do Surefire (Tests run: N), nunca grep @Test.

[CONSTRAINTS DUROS]
- Migration única (61 — CONFIRMAR primeiro nº livre). Sem foto/anexo de upload (foto do veículo é LINK
  photo_url; catálogo é texto + link).
- O VEÍCULO é ESTOQUE DA LOJA (FK company), NÃO sub-entidade do cliente (≠ os_vehicles da oficina).
  Cliente NÃO é entidade do core — continua o contact (test-drive e lead têm conversation_id/contact_id
  + snapshots de nome/telefone).
- HÍBRIDO TRIPLO: ESTOQUE (catálogo com ciclo disponivel→reservado→vendido, vendido sai da
  disponibilidade) + TEST-DRIVE (clona DENTAL: conflito por VENDEDOR + end_at materializado + status
  com parity) + LEAD (clona OFICINA/EVENTOS: funil de status, SEM itens/total, snapshot de preço do
  catálogo).
- TRAVA: a IA NUNCA fecha preço/desconto/financiamento, NUNCA aprova crédito, NUNCA simula parcela,
  NUNCA inventa veículo/preço/condição fora do catálogo, NUNCA promete entrega/disponibilidade. A IA
  NÃO muda status de estoque do veículo nem status do lead.
- test-drive e lead SÓ de veículo 'disponivel' → 422 vehicle_not_available caso contrário.
- conflito por salesperson_id re-verificado na transação; 409 conflict_slot; end_at materializado.
- Snapshots de veículo (marca/modelo/ano [+ preço no lead]) e cliente. Editar/vender o veículo depois
  NÃO altera test-drives/leads passados.
- Status hardcoded com parity para os TRÊS (vehicle/test-drive/lead). DUAS tags <testdrive_carro> e
  <lead_carro> distintas de TODAS as outras.
- NÃO mexer em outros perfis nem em system-template.txt. Webhook OFF.
- Cache de contexto (vitrine+agenda) TTL + invalidação em toda mutação relevante (veículo/estoque/
  vendedor/test-drive/lead/config).
- 529 → inline. Gate 3× → pausar. Working tree sujo → pausar. git add EXPLÍCITO (nunca git add .);
  .env/CONTEXT.md/secrets NUNCA staged.
- SEED com timestamptz/date: `at time zone 'America/Sao_Paulo'`. IDs de namespace com sufixo -15x NOVO.
- Tabela nova entra na migration ANTES de tocar o banco (lição os_config). Adicionar as tabelas ao
  TRUNCATE/SCRIPTS do AbstractIntegrationTest.
- ProfileTypeParityTest e os 3 parity tests verdes ANTES de fechar.
- Decisões menores: agente decide (paleta, layout, se ConcessionariaContextCache é 1 cache ou mais).

[PASSO FINAL — TENANT igorhaf28 + SEED + COMMIT + PUSH + SMOKE + RELATÓRIO]
F.1 — TENANT igorhaf28 (Concessionária Modelo, profile=concessionaria) — CONFIRMAR primeiro nº de
      tenant LIVRE no arranque. Padrão GoTrue (instance_id=zero-UUID + colunas de token=''), senha em
      comunicação direta (NUNCA em arquivo). company c?0000000-...-028 / user a?0000000-...-028. Caddy +
      /etc/hosts pra concessionaria.meadadigital.local.
F.2 — Seed /tmp/seed-concessionaria.sql (NÃO COMITAR; todo timestamptz/date com
      `at time zone 'America/Sao_Paulo'`; ids de namespace sufixo -15x novo):
  - config: business_name "Concessionária Modelo", duração test-drive 45min, janela 09:00–18:00.
  - vendedores: "Rafael Torres" + "Juliana Prado".
  - ESTOQUE (concessionaria_vehicles):
    * 'disponivel': "Toyota Corolla XEi" 2022, 35.000 km, R$135.000, prata, flex, automático,
      photo_url link de exemplo — pra smoke de vitrine + test-drive + lead.
    * 'disponivel': "Honda HR-V EXL" 2023, 12.000 km, R$170.000, preto, flex, automático.
    * 'disponivel': "Volkswagen Gol" 2019, 78.000 km, R$52.000, branco, flex, manual.
    * 'reservado': "Jeep Compass Longitude" 2021, 40.000 km, R$155.000 — pra smoke de "indisponível"
      (test-drive/lead → 422).
    * 'vendido': "Fiat Argo Drive" 2020, R$68.000 — histórico (não aparece na vitrine).
  - contact "Bruno Lima" +5511977778888 (VINCULADO: instance+conversation, sufixo -15x) + contact
    "Carla Mendes" +5511966667777 (sem vínculo).
  - test-drives (FLUXO AGENDA):
    * 'agendado' VINCULADO (Bruno) — Corolla com Rafael Torres, hoje+2d 10:00 — pra smoke de agenda +
      confirmar (snapshots de veículo gravados).
    * 'confirmado' (Carla) — HR-V com Juliana Prado, hoje+3d 14:00 — histórico.
  - leads (FLUXO LEAD):
    * 'novo' VINCULADO (Bruno) — interesse no HR-V, financiado, snapshot de preço R$170.000 — pra smoke
      de funil + atribuir vendedor.
    * 'em_negociacao' (Carla) — interesse no Corolla, à vista, snapshot R$135.000 — histórico do funil.
    * 'fechado' (Bruno, passado) — Gol, à vista — histórico.
F.3 — JwtAuthenticationFilter /api/concessionaria/ (se ainda não).
F.4-F.6 — git add EXPLÍCITO dos arquivos da SM (lista arquivo a arquivo, NUNCA git add .) + sanity de
      staging (git status -s + git diff --staged --stat + grep por segredo eyJ.../password/secret= +
      confirmar .env/.env.local/CONTEXT.md FORA da staging) + commit com `git commit -F <arquivo>`.
      Mensagem padrão (feat(camada-8.17): perfil concessionaria/Concessionária (estoque + test-drive +
      lead) com FUNDAÇÃO/BACKEND/FRONTEND/DECISÕES/VALIDAÇÃO contagem REAL/NÃO TOCADO/FECHAMENTO +
      trailer Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>). Tag anotada fase-8.17-fechada
      (nº real confirmado no arranque) apontando pro commit que a fecha.
F.7 — git push origin main + git push origin --tags (NUNCA --force).
F.8 — docker compose restart backend + aguardar /admin/me → 401 missing_auth_header.
F.9 — Smoke E2E (blocos A–H cobrindo OS TRÊS fluxos):
  BLOCO A: auth — igorhaf28 → /admin/me → role=tenant_admin, profileId=concessionaria,
    productName=Concessionária.
  BLOCO B: estoque + vendedores + guard — GET vehicles (só 'disponivel' na vitrine; reservado/vendido
    fora) + GET salespeople; CRUD smoke de veículo + invalida cache; transição de estoque
    disponivel→reservado→vendido OK + inválida → 409; delete em uso 409; GET config + PUT; tenant de
    OUTRO perfil → /api/concessionaria/vehicles → 403 forbidden_wrong_profile.
  BLOCO C: ESTOQUE — listar estoque [FLUXO 1 — CHAVE] — vitrine retorna Corolla/HR-V/Gol (disponíveis),
    NÃO retorna Compass (reservado) nem Argo (vendido); marcar Corolla 'vendido' → some da vitrine.
  BLOCO D: TEST-DRIVE [FLUXO 2 — CHAVE] — <testdrive_carro> Corolla com Rafael Torres, hoje+2d 11:00 →
    cria test-drive (end_at = start+45min; snapshots de veículo); MESMO horário com Rafael → 409
    conflict_slot; MESMO horário com Juliana → OK (paralelismo entre vendedores); horário fora da
    janela → rejeitado; <testdrive_carro> de veículo 'reservado' (Compass) → 422 vehicle_not_available.
  BLOCO E: LEAD [FLUXO 3 — CHAVE] — <lead_carro> HR-V financiado → cria lead 'novo' (snapshot de preço
    R$170.000 do catálogo, NÃO da tag); lead de veículo 'vendido' (Argo) → 422 vehicle_not_available;
    payment_condition inválido → não cria (empty); o preço do lead = preço do catálogo (prova da trava).
  BLOCO F: funil do LEAD + status do test-drive — lead novo→em_negociacao→fechado; novo→perdido(motivo);
    transição inválida → 409; atribuir vendedor OK; test-drive agendado→confirmado (msg com vendedor +
    veículo + data/hora) → realizado; confirmado→cancelado (msg defensiva); confirmado→no_show;
    transição inválida → 409; a IA não tem rota de mudar status de estoque/lead.
  BLOCO G: regressão — perfis anteriores intactos (smoke leve 1 endpoint cada); concessionaria →
    /api/dental/* → 403; concessionaria → /api/oficina/* → 403.
  BLOCO H: paridade — mvn test -Dtest=VehicleStatusParityTest,TestDriveStatusParityTest,
    LeadStatusParityTest,ProfileTypeParityTest → verde.
  Cleanup smoke + restaurar seed pristine. mvn final: contagem REAL do Surefire.
  LIÇÃO os_config: as tabelas novas DEVEM estar DENTRO da migration 61 ANTES de qualquer toque no
  banco/smoke (o smoke roda contra o schema da migration aplicada; tabela "criada à mão" fora da
  migration é dívida que quebra o próximo clone/AbstractIntegrationTest).
F.10 — RELATÓRIO + DESTAQUE EXPLÍCITO:
  - "perfil concessionaria/Concessionária — HÍBRIDO TRIPLO: catálogo de ESTOQUE com ciclo próprio
     (disponivel→reservado→vendido) + agenda de test-drive (clona DENTAL, conflito por VENDEDOR) +
     lead/funil (clona OFICINA/EVENTOS, sem itens), os três coexistindo no mesmo perfil"
  - "ESCAPADA: o veículo é ITEM DE ESTOQUE com identidade única e status próprio (≠ catálogo
     reabastecível); vendido SAI da disponibilidade; a IA opera por TRÊS lentes (vitrine/agenda/lead)
     sem nunca alterar o estoque"
  - "FLUXO TEST-DRIVE: conflito POR VENDEDOR re-verificado na transação; end_at materializado; 409
     conflict_slot; paralelismo entre vendedores; 422 vehicle_not_available pra carro indisponível"
  - "FLUXO LEAD: funil novo→em_negociacao→fechado/perdido; snapshot do PREÇO do catálogo; a IA cria
     'novo' e NÃO move; condição à vista/financiado declarativa"
  - "TRAVA: a IA NUNCA fecha preço/desconto/financiamento, NÃO aprova crédito, NÃO inventa veículo/
     preço, NÃO muda status de estoque/lead (provado no BLOCO E: preço do lead = catálogo, não a tag)"
  - "BLOCO C prova o ciclo de estoque (vendido some da vitrine); BLOCO D prova conflito por vendedor +
     paralelismo + 422 indisponível; BLOCO E prova o lead + a trava de preço"
  - "TRÊS status hardcoded com parity (VehicleStatus/TestDriveStatus/LeadStatus); DUAS tags
     <testdrive_carro> e <lead_carro>"
  - "OutboundService ganhou maybeProcessTestDrive + maybeProcessLeadCarro (DOIS handlers encadeados)"
  - "Seed: at time zone + sufixo de ids -15x novo; tabelas DENTRO da migration (lição os_config)"
  - PENDÊNCIAS: foto via upload (hoje é link/SERVICE_ROLE_KEY), financiamento real/simulação/score,
     FIPE/avaliação de usado/trade-in, reserva com sinal (Stripe #50), documentação/emplacamento,
     VIN/chassi formal, notificação de fechamento do lead, multi-loja/pátio, Stripe + dívida acumulada
     (webhook, cliente real, olho humano sobre os verticais).

[REPORTAR]
Igual SMs anteriores. Incluir EXPLICITAMENTE:
- "ProfileType.CONCESSIONARIA adicionado (camada 8.17)"
- "Paridade VehicleStatus, TestDriveStatus, LeadStatus e ProfileType validadas"
- "Tenant igorhaf28 criado (GoTrue + Caddy/etc/hosts) — nº confirmado no arranque"
- "HÍBRIDO TRIPLO: ESTOQUE (ciclo disponivel→reservado→vendido, vendido sai da disponibilidade) +
   TEST-DRIVE (clona DENTAL, conflito por VENDEDOR + end_at materializado) + LEAD (clona OFICINA/
   EVENTOS, funil sem itens, snapshot do preço do catálogo)"
- "ESCAPADA: veículo é estoque da LOJA (≠ os_vehicles do cliente na oficina), entidade central
   referenciada por test-drive E lead; a IA opera por três lentes sem alterar o estoque"
- "TRAVA: a IA não fecha preço/desconto/financiamento, não aprova crédito, não simula parcela, não
   inventa veículo/preço/condição, não promete entrega/disponibilidade"
- "test-drive e lead SÓ de veículo disponível (422 vehicle_not_available)"
- "conflito por salesperson_id re-verificado na transação (409 conflict_slot); end_at materializado"
- "DUAS tags <testdrive_carro> e <lead_carro> distintas de TODAS as outras"
- "OutboundService ganhou maybeProcessTestDrive + maybeProcessLeadCarro (DOIS handlers)"
- "getNavForProfile('concessionaria') com branch próprio (Estoque/Vendedores/Test-drives/Leads/
   Configurações)"
- "Cache de contexto + invalidação em toda mutação"
- "tabelas criadas DENTRO da migration 61 (lição os_config); seed com at time zone + sufixo -15x novo"
- "Próximas fases: foto via upload, financiamento real/simulação, FIPE/trade-in, reserva com sinal,
   documentação/emplacamento, VIN/chassi, notificação de fechamento, multi-loja, Stripe + fila"
