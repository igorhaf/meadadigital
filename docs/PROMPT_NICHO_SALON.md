>>> JÁ IMPLEMENTADO — perfil salon, camada 7.5, migration 34_salon.sql. Prompt de nicho RETROATIVO,
>>> formato T5. Fonte: CLAUDE.md seção Perfil Salão + migration 34 + docs/PERFIL_SALAO.md.

[TAREFA — PERFIL SALON / SalãoBot (camada 7.5) — RETROATIVO]

Este documento RECONSTRÓI, no formato T5, o prompt de nicho do perfil SALON tal como ele JÁ EXISTE no
código. Não é trabalho a executar — é a documentação retroativa do que foi entregue (migration 34,
backend em src/main/java/com/meada/profiles/salon/, frontend em frontend/profiles/salon/,
docs/PERFIL_SALAO.md). Serve de molde/registro para auditoria e para clonagens futuras.

[CONTEXTO]
PROJETO MEADA em /home/igorhaf/meada.
SALON é o QUINTO perfil vertical real (sushi 7.1, legal 7.2, restaurant 7.3, dental 7.4, salon 7.5)
— 6º contando generic. O tenant salon (`profile_id='salon'`) vira um produto de SALÃO DE BELEZA:
gerencia profissionais e serviços, e a IA atende clientes via WhatsApp com tom acolhedor, oferece
serviços + profissionais, verifica disponibilidade e marca o horário. Subdomínio
salon.meadadigital.local; produto "Salão".

A IA conhece os serviços ativos (com preço quando informado), os profissionais ativos, o histórico
do cliente e os horários livres de cada profissional nos próximos 7 dias. Quando o cliente pede um
serviço, sugere os profissionais disponíveis pro horário (se houver mais de um) e confirma
serviço + profissional + dia + hora antes de marcar.

>>> TRAVA DE COMPORTAMENTO DA IA (cravada) <
- NUNCA recomenda um serviço que o cliente NÃO pediu.
- NUNCA opina sobre a APARÊNCIA do cliente.
- SEM promessa de resultado estético.
- Tom acolhedor e SEM julgamento. A trava vive na persona (`ProfilePromptContext.SALON`) E nas
  INSTRUÇÕES injetadas pelo `SalonContextCache`.

EVOLUÇÃO ESTRUTURAL (o que o salon inaugura vs. os perfis anteriores):
- CONFLITO DE AGENDA POR PROFISSIONAL (não por company). Dental/restaurant checavam conflito por
  company (1 recurso por tenant). O salon tem MÚLTIPLOS profissionais — o conflito é por
  `professional_id`. `SalonAppointmentRepository.findConflict(professionalId, start, end)` filtra
  por profissional; 2 clientes no MESMO horário com profissionais DIFERENTES NÃO conflitam
  (paralelismo). Fase futura: se um profissional puder atender em salas distintas, refinar.
- CLIENTE NÃO É ENTIDADE PRÓPRIA (decisão cravada): salão tem alta rotatividade; modelar
  `salon_clients` seria over-engineering. O histórico vem do `contact` + `salon_appointments` dele.
  `guest_name`/`guest_phone` são SNAPSHOTS do contato. Fase futura se virar prioridade.
- SLOT 15min (granularidade fina — salão tem serviço curto). DURAÇÃO POR SERVIÇO (não fixa) — cada
  offering carrega `duration_minutes` (15..480), que entra como snapshot no agendamento e define
  quanto tempo o profissional fica ocupado. Buffer = 0 nesta SM.
- TAG `<agendamento>` em texto livre (NÃO tool calling — mesma restrição responseSchema dos demais
  perfis): a IA negocia em linguagem natural e, na confirmação final, emite a tag.

DECISÕES CRAVADAS (reais, como estão no código):
1. Conflito POR `professional_id`, janela half-open, re-verificado DENTRO da transação do INSERT
   (defesa de corrida entre o cache da IA e a persistência).
2. Status hardcoded materializado com parity test Java↔TS (ver TESTES).
3. Notificação: só `confirmado` (com data/hora/profissional) e `cancelado` notificam o cliente;
   `agendado`/`realizado`/`falta` são silenciosos. Texto defensivo (sem promessa estética).
4. Slot 15min (`SLOT_GRANULARITY_MIN`). Janela `opens_at`..`closes_at` (default 09:00–20:00).
   Fuso America/Sao_Paulo HARDCODED.
5. `end_at` materializado no INSERT (`start_at + duration_minutes`) — NÃO coluna gerada
   (`timestamptz + interval` não é IMMUTABLE — lição da SM-D/E).
6. Snapshots no agendamento: `professional_name` + `service_name` + `price_cents` +
   `duration_minutes` congelados no momento — alterar serviço/profissional depois NÃO altera
   agendamentos passados.
7. Nome interno "offering" para o serviço (tabela `salon_offerings`, `SalonOfferingService`) p/ não
   colidir com o Spring `SalonService` — a UI/rota chama "serviços".
8. Tag `<agendamento>` com namespace próprio (distinta de `<pedido>` do sushi, `<reserva>` do
   restaurant, `<consulta>` do dental etc.).

[FUNDAÇÃO — migration 34_salon.sql]
- ALTER companies CHECK aceitar 'salon' PRESERVANDO os anteriores:
  `check (profile_id in ('generic','legal','dental','sushi','restaurant','salon'))`.
- RLS enable+force em todas as tabelas; policies do tenant via `app.company_id()`; grants
  `authenticated` + `service_role`. `salon_appointments`: INSERT vem do BACKEND (service_role) — IA
  (`AgendamentoConfirmHandler`) ou tenant (POST manual). Tenant só SELECT/UPDATE (status na agenda).
- Tabelas:
  * `salon_professionals` — catálogo de profissionais. (id, company_id refs companies on delete
    restrict, name CHECK 1..200, specialty texto livre nullable, active default true, notes,
    timestamps). Índices: (company_id, active) WHERE active; (company_id, name). O conflito de agenda
    é POR profissional → `active=false` retira da disponibilidade que a IA enxerga.
  * `salon_offerings` — catálogo de serviços (nome "offering" no backend; UI = "serviços").
    (id, company_id on delete restrict, name CHECK 1..200, category texto livre nullable,
    duration_minutes NOT NULL CHECK 15..480, price_cents nullable — salão pode não expor preço pela
    IA, active default true, description, timestamps). `duration_minutes` por serviço (varia) entra
    como SNAPSHOT no agendamento.
  * `salon_config` — horário do salão (1:1 com company, PK = company_id on delete cascade).
    (opens_at default '09:00', closes_at default '20:00', buffer_minutes default 0 CHECK >= 0,
    timestamps). Ausente → defaults (09:00/20:00/0).
  * `salon_appointments` — agendamentos. (id, company_id on delete restrict,
    professional_id refs salon_professionals on delete restrict, service_id refs salon_offerings on
    delete restrict, conversation_id refs conversations on delete set null, contact_id refs contacts
    on delete set null, guest_name NOT NULL snapshot, guest_phone snapshot opcional, start_at NOT
    NULL, duration_minutes NOT NULL snapshot, end_at NOT NULL materializado no INSERT, service_name
    NOT NULL snapshot, professional_name NOT NULL snapshot, price_cents snapshot opcional, status
    default 'agendado' CHECK in ('agendado','confirmado','realizado','cancelado','falta'), notes,
    created_at, status_updated_at). Índices: (company_id, status, start_at); ÍNDICE CRÍTICO de
    conflito (professional_id, start_at) WHERE status in ('agendado','confirmado'); (contact_id,
    start_at desc) WHERE contact_id not null.
- Conflito por professional_id em SQL transacional: `findConflict(professional_id, start, end)` é um
  SELECT com a janela half-open (`NOT (existing.end <= newStart OR existing.start >= newEnd)`), só
  status bloqueantes (`agendado`/`confirmado`). NÃO filtra por company no WHERE de conflito — o
  professional_id já é da company. O INSERT re-verifica o conflito DENTRO da transação antes de
  gravar (fecha a janela de corrida) e materializa o end_at + os snapshots.
- Status hardcoded materializado (`SalonAppointmentStatus.java` ↔ `salon-appointment-status.ts`,
  `SalonAppointmentStatusParityTest`):
    agendado   → confirmado, cancelado
    confirmado → realizado, cancelado, falta
    realizado / cancelado / falta → terminal
  Transição inválida → 409 `invalid_status_transition`.
- TODAS as tabelas entram no TRUNCATE/SCRIPTS do AbstractIntegrationTest.

[BACKEND]
- Professionals: CRUD (`SalonProfessionalService`/Controller). Toggle active/inactive. Delete
  bloqueado se houver agendamento (proteção de histórico — preferir desativar).
- Offerings (serviços): CRUD (`SalonOfferingService`/Controller). Toggle active/inactive. Delete
  bloqueado se houver agendamento. `duration_minutes` por serviço; `price_cents` opcional.
- Config: GET (fallback defaults 09:00/20:00/buffer 0) + PUT.
- Appointments: criados pelo BACKEND (`SalonAppointmentService`) — via IA
  (`AgendamentoConfirmHandler`) ou tenant (POST manual sem `conversation_id`, não notifica — sem
  canal). PATCH de status com validação de transição (inválida → 409 `invalid_status_transition`).
  Conflito por profissional re-verificado na transação → conflito lança a exceção de slot ocupado
  (mostra quem ocupa e de que hora a que hora). `end_at` materializado no INSERT. NÃO há DELETE de
  agendamento (histórico; "remover" = status cancelado).
- Notificação outbound (`SalonAppointmentNotifier`): só `confirmado` (texto com data/hora/
  profissional) e `cancelado` (texto defensivo) notificam o cliente; demais silenciosos. Resolve o
  contato via o agendamento; pula em silêncio se não houver vínculo WhatsApp.
- IA:
  * Persona SALON nova (não existia na SM-A, igual restaurant): tom acolhedor, SEM julgamento, com
    a TRAVA embutida (não recomenda serviço não pedido, não opina sobre aparência, sem promessa de
    resultado estético).
  * Contexto dinâmico via `SalonContextCache` (TTL 20s — entre restaurant 15s e dental 30s; a agenda
    de múltiplos profissionais atualiza com frequência intermediária). Keyed por
    `(companyId, contactId)`. Conteúdo: serviços ativos (com duração/categoria/preço-se-houver) +
    profissionais ativos + histórico do contato (se identificado, últimos 5) + SLOTS LIVRES POR
    PROFISSIONAL nos próximos 7 dias (granularidade 15min, removendo os ocupados por agendamentos
    ativos daquele profissional) + instruções de agendamento. Invalidação explícita (por company) em
    toda mutação de profissional/serviço/agendamento/config.
  * Tag `<agendamento>{"professional_id":"UUID","service_id":"UUID","date":"YYYY-MM-DD",
    "start_time":"HH:MM","notes":"..."}</agendamento>` → `AgendamentoConfirmHandler` (resolve o
    contato da conversa — guest_name = contact.name snapshot, guest_phone snapshot —, lê
    `offering.duration_minutes` snapshot e cria). Best-effort: JSON inválido / campos faltando /
    profissional ou serviço inválido-ou-inativo / conflito de slot / fora do horário → Optional.empty
    + warn (a mensagem da IA segue sem agendamento). O OutboundService REMOVE a tag antes de enviar
    ao cliente (`maybeProcessSalonAppointment`, encadeado após sushi/restaurant/dental — perfil é
    único, só um age).
  * Guard: `SalonProfileGuard.requireSalon` → endpoints `/api/salon/**` retornam 403
    `forbidden_wrong_profile` para tenant de outro perfil. `JwtAuthenticationFilter` autentica
    `/api/salon/**` (além de sushi/legal/restaurant/dental).
- Rotas REST reais: `/api/salon/professionals` (GET lista, GET {id}, POST, PATCH {id},
  PATCH {id}/toggle, DELETE {id}); `/api/salon/services` (GET lista, GET {id}, POST, PATCH {id},
  PATCH {id}/toggle, DELETE {id}); `/api/salon/appointments` (GET lista, GET {id}, POST,
  PATCH {id}/status); `/api/salon/config` (GET, PUT).

[FRONTEND]
- `getNavForProfile('salon')` injeta o grupo "Salão" (Profissionais / Serviços / Agenda /
  Configurações). Telas:
  * `/dashboard/professionals` — CRUD de profissionais (nome, especialidade livre, notas; toggle
    ativo/inativo inline; delete bloqueado se houver agendamento).
  * `/dashboard/salon-services` — CRUD de serviços (nome, categoria livre, duração 15..480, preço
    opcional; toggle; delete bloqueado se houver agendamento). ATENÇÃO: é DISTINTA da
    `/dashboard/services` (tela GENÉRICA de configuração da IA do core — NÃO colidir).
  * `/dashboard/salon-appointments` — agenda: lista por dia, filtro por status e por profissional;
    novo agendamento manual (escolhe profissional, serviço com duração, data, hora, nome do cliente,
    telefone opcional, notas — conflito do profissional mostra quem ocupa e o intervalo); detalhe +
    mudança de status (confirmar/cancelar notificam; realizado/falta silenciosos).
  * `/dashboard/salon-settings` — horário de funcionamento (abre/fecha) + intervalo entre
    agendamentos. Mudanças afetam agendamentos futuros.
- types + SDKs (professionals, offerings/services, appointments, config). Status TS
  `salon-appointment-status.ts`.
- npm build limpo.

[DOCS]
- CLAUDE.md: seção "## Perfil Salão (SalãoBot, camada 7.5)" — documenta a evolução do conflito (por
  profissional, paralelismo), cliente não é entidade, slot 15min/duração por serviço, snapshots,
  end_at materializado, tag `<agendamento>`, persona/contexto, guard, sidebar e o NÃO TEM.
- docs/PERFIL_SALAO.md: guia operacional do tenant (Profissionais / Serviços / Agenda /
  Configurações; como a IA atende; LGPD; limitações conhecidas).
- system-template.txt e outros perfis NÃO foram tocados.

[TESTES BACKEND]
- `SalonAppointmentStatusParityTest` — garante a paridade Java (`SalonAppointmentStatus`) ↔ TS
  (`salon-appointment-status.ts`).
- `ProfileTypeParityTest` — enum Java ↔ const TS dos perfis (salon presente).
- Suíte de service + controller integration por entidade (professionals, offerings, appointments,
  config): CRUD; toggle; delete-em-uso bloqueado; PATCH de status com transição válida/inválida (409
  `invalid_status_transition`); CONFLITO POR PROFISSIONAL (2 clientes mesmo horário com profissionais
  diferentes NÃO conflitam; mesmo profissional conflita); `AgendamentoConfirmHandler` (tag válida
  cria; JSON inválido/campos faltando/ids inválidos/conflito/fora do horário → empty); guard
  wrongProfile → 403 `forbidden_wrong_profile`.
- mvn final = relatar contagem REAL do Surefire (`Tests run: N`), nunca grep de @Test.

[CONSTRAINTS DUROS]
- Migration única (34). Sem foto/anexo (bloqueador SERVICE_ROLE_KEY).
- CLIENTE NÃO é entidade do core — continua o contact (agendamento tem conversation_id/contact_id;
  guest_name/guest_phone são snapshots).
- CONFLITO POR `professional_id` (não company), janela half-open, re-verificado na transação.
  Paralelismo: profissionais diferentes no mesmo horário não conflitam.
- SLOT 15min, DURAÇÃO POR SERVIÇO (snapshot no agendamento). Buffer = 0 nesta SM. Janela
  opens_at..closes_at default 09:00–20:00. Fuso America/Sao_Paulo HARDCODED.
- `end_at` materializado no INSERT (NÃO generated). Snapshots de professional_name/service_name/
  price_cents/duration_minutes — alterar o cadastro depois NÃO altera agendamentos passados.
- Status hardcoded com parity test. Tag `<agendamento>` distinta de TODAS as outras.
- A IA NUNCA recomenda serviço não pedido, NUNCA opina sobre aparência, sem promessa estética.
- Cache de contexto TTL 20s + invalidação em toda mutação. NÃO mexer em outros perfis nem em
  system-template.txt. Webhook OFF.

[PASSO FINAL — resumido (já executado retroativamente)]
- Tenant salon provisionado (GoTrue + Caddy/etc/hosts pra salon.meadadigital.local).
- Seed com cardápio de serviços + profissionais + config (`at time zone 'America/Sao_Paulo'`; ids de
  namespace com sufixo próprio).
- `JwtAuthenticationFilter` autentica `/api/salon/**`. OutboundService com
  `maybeProcessSalonAppointment` (best-effort, encadeado após sushi/restaurant/dental).
- git add EXPLÍCITO dos arquivos da SM + sanity (sem .env/secrets/CONTEXT) + commit semântico
  `feat(camada-7.5): ...` + Co-Authored-By + tag `fase-7.5-fechada`. Push origin main + tags.
- Smoke E2E: auth (profileId=salon, productName=Salão); CRUD profissional/serviço + guard 403 de
  outro perfil; conflito por profissional (paralelismo OK, mesmo profissional 409); tag
  `<agendamento>` cria agendamento; status confirmar/cancelar notifica; paridade verde. mvn final
  com contagem REAL do Surefire.

[REPORTAR]
Incluir EXPLICITAMENTE:
- "ProfileType.SALON (camada 7.5, 5º perfil real)"
- "Paridade SalonAppointmentStatus e ProfileType validadas"
- "EVOLUÇÃO: conflito de agenda POR profissional (paralelismo); cliente NÃO é entidade (contact +
  snapshots); slot 15min + duração por serviço"
- "end_at materializado no INSERT (lição timestamptz+interval); snapshots preservam histórico"
- "Tag <agendamento> distinta de todas as outras; OutboundService ganhou maybeProcessSalonAppointment"
- "getNavForProfile('salon') com grupo Salão; /dashboard/salon-services (NÃO colidir com
  /dashboard/services do core)"
- "Cache de contexto TTL 20s + invalidação em toda mutação"
- "Trava da IA: não recomenda serviço não pedido, não opina sobre aparência, sem promessa estética"
- "NÃO TEM: comissão, pagamento, fidelidade/cashback, estoque, multi-loja, plano de assinatura,
  scheduler de auto-transição, foto"
