-- =============================================================================
-- 02_tables.sql
-- Meada — as 11 tabelas do MVP, índices e integridade referencial.
--
-- Escopo deste arquivo:
--   - Tabelas de domínio com company_id onde aplicável.
--   - FKs simples (raiz → companies) e FKs COMPOSTAS (pai → filho, ambos com
--     company_id) que bloqueiam contaminação cross-tenant mesmo sob service_role.
--   - UNIQUE (id, company_id) nos pais — redundante com a PK, mas exigido pelo
--     Postgres como alvo de FK composta.
--   - Índices, incluindo parciais WHERE deleted_at IS NULL.
--
-- NÃO está aqui: ENABLE ROW LEVEL SECURITY + policies (03), grants/coluna
--   blindada do token (04), policies de Storage (05).
--
-- Convenções:
--   - PK uuid default gen_random_uuid().
--   - created_at/updated_at timestamptz not null default now() em todas.
--   - Soft delete (deleted_at) só em services, faqs, documents, contacts.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. companies — a empresa-tenant; raiz de todo o isolamento.
-- -----------------------------------------------------------------------------
create table companies (
  id          uuid primary key default gen_random_uuid(),
  name        text not null,
  slug        text not null unique,
  status      text not null default 'active' check (status in ('active','suspended')),
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);


-- -----------------------------------------------------------------------------
-- 2. users — perfil de aplicação 1:1 com auth.users; carrega company_id e role.
-- -----------------------------------------------------------------------------
-- id = auth.users.id (mesma chave). CASCADE: apagar a identidade apaga o perfil.
-- company_id → companies: RESTRICT. Apagar um operador NUNCA pode apagar a
--   empresa; apagar empresa com usuários vivos é barrado (decisão consciente).
create table users (
  id          uuid primary key references auth.users (id) on delete cascade,
  company_id  uuid not null references companies (id) on delete restrict,
  email       text not null,
  full_name   text,
  role        text not null default 'agent' check (role in ('owner','admin','agent')),
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now(),
  -- alvo da FK composta conversations.assigned_user_id → users(id, company_id)
  unique (id, company_id),
  -- não pode haver dois usuários com o mesmo email na mesma empresa
  unique (company_id, email)
);

create index idx_users_company on users (company_id);


-- -----------------------------------------------------------------------------
-- 3. whatsapp_instances — instância da Evolution API conectada a uma company.
-- -----------------------------------------------------------------------------
-- evolution_token é segredo: fica nesta coluna, mas o GRANT de coluna em 04
--   NÃO o expõe ao role authenticated — só service_role lê. (Critério 3.)
create table whatsapp_instances (
  id              uuid primary key default gen_random_uuid(),
  company_id      uuid not null references companies (id) on delete restrict,
  instance_name   text not null unique,
  phone_number    text,
  status          text not null default 'disconnected'
                    check (status in ('connected','disconnected','connecting')),
  evolution_token text not null,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now(),
  -- alvo da FK composta conversations.whatsapp_instance_id → (id, company_id)
  unique (id, company_id)
);

create index idx_wa_instances_company on whatsapp_instances (company_id);


-- -----------------------------------------------------------------------------
-- 4. services — catálogo de serviços/produtos que a IA pode citar.
-- -----------------------------------------------------------------------------
create table services (
  id          uuid primary key default gen_random_uuid(),
  company_id  uuid not null references companies (id) on delete restrict,
  name        text not null,
  description text,
  price_cents integer check (price_cents is null or price_cents >= 0),
  active      boolean not null default true,
  deleted_at  timestamptz,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

-- unique parcial: respeita soft delete — recriar um "Corte" apagado funciona,
--   mas dois serviços ativos com o mesmo nome falham. (Critério 10.)
create unique index uq_services_company_name_active
  on services (company_id, name) where deleted_at is null;
create index idx_services_company
  on services (company_id) where deleted_at is null;


-- -----------------------------------------------------------------------------
-- 5. business_hours — janelas de atendimento por dia da semana.
-- -----------------------------------------------------------------------------
-- Múltiplas janelas por dia (opção 2): no Brasil fechar 12h-14h pro almoço é
--   regra, não exceção. O painel é uma lista de blocos; a IA soma as janelas
--   para responder "estamos abertos?".
-- Semântica do "fechado":
--   - dia inteiro fechado  = uma linha com closed = true (opens_at/closes_at NULL).
--   - dia com janelas      = N linhas com closed = false e opens_at <> closes_at.
--   Não misturar: um dia é fechado OU tem janelas.
-- Wrap pós-meia-noite: opens_at > closes_at é VÁLIDO e significa que a janela
--   cruza a meia-noite (ex.: 20h–02h fecha às 02h do dia seguinte). Só
--   opens_at = closes_at é rejeitado. O helper Java isOpenNow(now, opens, closes)
--   cobre os dois casos (janela normal e wrap) com um if.
create table business_hours (
  id          uuid primary key default gen_random_uuid(),
  company_id  uuid not null references companies (id) on delete restrict,
  weekday     smallint not null check (weekday between 0 and 6),  -- 0 = domingo
  opens_at    time,
  closes_at   time,
  closed      boolean not null default false,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now(),
  -- coerência: ou é dia fechado (sem horas) ou é janela válida.
  -- opens_at <> closes_at (não <): permite wrap pós-meia-noite (ver nota acima).
  constraint chk_business_hours_shape check (
    (closed = true  and opens_at is null and closes_at is null)
    or
    (closed = false and opens_at is not null and closes_at is not null
                    and opens_at <> closes_at)
  ),
  -- não pode haver duas janelas começando no mesmo horário no mesmo dia
  unique (company_id, weekday, opens_at)
);

create index idx_business_hours_company on business_hours (company_id);


-- -----------------------------------------------------------------------------
-- 6. faqs — perguntas/respostas curadas que alimentam o contexto da IA.
-- -----------------------------------------------------------------------------
create table faqs (
  id          uuid primary key default gen_random_uuid(),
  company_id  uuid not null references companies (id) on delete restrict,
  question    text not null,
  answer      text not null,
  active      boolean not null default true,
  deleted_at  timestamptz,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

-- sem unique de negócio (a mesma pergunta pode existir reformulada);
-- só índice de leitura, respeitando soft delete.
create index idx_faqs_company
  on faqs (company_id) where deleted_at is null;


-- -----------------------------------------------------------------------------
-- 7. documents — metadados de arquivos; o binário vive no Supabase Storage.
-- -----------------------------------------------------------------------------
create table documents (
  id           uuid primary key default gen_random_uuid(),
  company_id   uuid not null references companies (id) on delete restrict,
  filename     text not null,
  storage_path text not null,   -- prefixado por company_id/ (policy de Storage em 05)
  mime_type    text,
  size_bytes   bigint check (size_bytes is null or size_bytes >= 0),
  deleted_at   timestamptz,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now()
);

create index idx_documents_company
  on documents (company_id) where deleted_at is null;

-- CHECK de tenant no path: força storage_path a começar com '<company_id>/'.
--   Isto fecha, no nível do banco, a convenção que o 05_storage.sql depende:
--   a policy de Storage isola por (storage.foldername(name))[1] = company_id, mas
--   essa regra só vale se o path REALMENTE tiver o company_id como 1º segmento.
--   Sem este CHECK, a convenção era só documental — um path mal-formado (vindo de
--   bug no backend) furava o casamento metadado↔binário. O path é construído pelo
--   Spring, NUNCA recebido do cliente; este CHECK é a rede de segurança.
--   Regex: '^<uuid>/.+'  — começa com o uuid do tenant, barra, e ao menos 1 char.
alter table documents
  add constraint chk_documents_path_tenant
  check (storage_path ~ ('^' || company_id::text || '/.+'));


-- -----------------------------------------------------------------------------
-- 8. ai_settings — comportamento da IA por empresa (1:1 com company).
-- -----------------------------------------------------------------------------
create table ai_settings (
  id               uuid primary key default gen_random_uuid(),
  company_id       uuid not null unique references companies (id) on delete restrict,
  tone             text,
  system_rules     text,
  restrictions     text,
  handoff_triggers text,
  model_provider   text not null default 'gemini'
                     check (model_provider in ('gemini','openai')),
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now()
);


-- -----------------------------------------------------------------------------
-- 9. contacts — o cliente final (quem fala com a empresa pelo WhatsApp).
-- -----------------------------------------------------------------------------
create table contacts (
  id           uuid primary key default gen_random_uuid(),
  company_id   uuid not null references companies (id) on delete restrict,
  phone_number text not null,   -- E.164
  name         text,
  deleted_at   timestamptz,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now(),
  -- alvo da FK composta conversations.contact_id → contacts(id, company_id)
  unique (id, company_id)
);

-- mesmo número não se repete na mesma empresa (entre ativos)
create unique index uq_contacts_company_phone_active
  on contacts (company_id, phone_number) where deleted_at is null;
create index idx_contacts_company on contacts (company_id);


-- -----------------------------------------------------------------------------
-- 10. conversations — thread de atendimento; controla IA vs humano.
-- -----------------------------------------------------------------------------
-- TODAS as FKs para pais que carregam company_id são COMPOSTAS: incluem
--   company_id no par. Isso impede, no nível do banco, que uma conversa de A
--   aponte para um contact/instance/user de B — mesmo sob service_role (que
--   ignora RLS). (Critério 8.)
create table conversations (
  id                   uuid primary key default gen_random_uuid(),
  company_id           uuid not null references companies (id) on delete restrict,
  contact_id           uuid not null,
  whatsapp_instance_id uuid not null,
  status               text not null default 'open' check (status in ('open','closed')),
  handled_by           text not null default 'ai' check (handled_by in ('ai','human')),
  assigned_user_id     uuid,
  last_message_at      timestamptz,
  created_at           timestamptz not null default now(),
  updated_at           timestamptz not null default now(),

  -- FKs compostas (pai precisa de UNIQUE (id, company_id), definido acima)
  foreign key (contact_id, company_id)
    references contacts (id, company_id) on delete restrict,
  foreign key (whatsapp_instance_id, company_id)
    references whatsapp_instances (id, company_id) on delete restrict,
  -- assigned_user_id NULL é permitido (conversa sem agente): MATCH SIMPLE,
  --   default, não exige a FK quando qualquer coluna do par é NULL.
  -- ON DELETE SET NULL (assigned_user_id): sintaxe coluna-lista do PG 15+.
  --   SEM a lista, o SET NULL zeraria TODO o par — inclusive company_id, que é
  --   NOT NULL → o DELETE do agente quebraria com violação de not-null. Com a
  --   lista, só assigned_user_id vai a NULL; company_id fica intacto. Agente sai,
  --   conversa volta pra fila. (Supabase roda PG 15.x, então a sintaxe existe.)
  foreign key (assigned_user_id, company_id)
    references users (id, company_id) on delete set null (assigned_user_id),

  -- alvo da FK composta messages.conversation_id → conversations(id, company_id)
  unique (id, company_id)
);

-- inbox: conversas da empresa por status, mais recentes primeiro
create index idx_conversations_inbox
  on conversations (company_id, status, last_message_at desc);
create index idx_conversations_contact on conversations (contact_id);


-- -----------------------------------------------------------------------------
-- 11. messages — cada mensagem trocada na conversa (imutável; sem soft delete).
-- -----------------------------------------------------------------------------
create table messages (
  id                   uuid primary key default gen_random_uuid(),
  company_id           uuid not null references companies (id) on delete restrict,
  conversation_id      uuid not null,
  direction            text not null check (direction in ('inbound','outbound')),
  sender               text not null check (sender in ('contact','ai','human')),
  content              text not null,
  evolution_message_id text,   -- id externo p/ idempotência do webhook
  created_at           timestamptz not null default now(),

  -- coerência direção/remetente: cliente só manda inbound; IA e humano só
  --   mandam outbound. inbound+ai ou inbound+human são impossíveis no fluxo
  --   real — sem este CHECK, código bugado contaminaria a auditoria em silêncio.
  constraint chk_messages_direction_sender check (
    (direction = 'inbound'  and sender = 'contact')
    or
    (direction = 'outbound' and sender in ('ai','human'))
  ),

  -- FK composta: a mensagem e a conversa têm de ser da mesma empresa
  foreign key (conversation_id, company_id)
    references conversations (id, company_id) on delete restrict
);

-- leitura típica: as mensagens de uma conversa em ordem cronológica
create index idx_messages_conversation on messages (conversation_id, created_at);
create index idx_messages_company on messages (company_id);

-- idempotência do webhook: a Evolution reentregar o mesmo evento não duplica.
--   Parcial porque mensagens internas (IA/humano) podem não ter id externo.
create unique index uq_messages_evolution_id
  on messages (evolution_message_id) where evolution_message_id is not null;


-- =============================================================================
-- app.company_id() — peça central do RLS. DEFINIDA AQUI (após public.users
--   existir) porque o corpo faz SELECT em public.users, e CREATE FUNCTION em SQL
--   VALIDA o corpo na criação — se rodasse antes da tabela existir, abortaria com
--   'relation public.users does not exist'. Por isso a função e seus grants vivem
--   no fim do 02, não no 01. O 03 (RLS) roda depois e já encontra a função pronta.
--
-- Toda policy de RLS do schema reduz a:  company_id = app.company_id()
-- Centralizar a regra aqui significa: um único ponto auditável decide o tenant.
--
-- ESCOPO: roteamento de tenant — "quem é você", nada mais. NÃO valida status
--   da empresa (suspended etc). Bloqueio de empresa suspensa é decisão de
--   NEGÓCIO, feita no backend Spring (nega login, nega webhook), nunca no RLS.
--   Misturar lifecycle com identidade tornaria 'suspended' um estado que toda
--   policy precisaria entender — e travaria o próprio admin ao reativar.
--
-- Por que SECURITY DEFINER:
--   A função precisa LER public.users para descobrir o company_id do usuário.
--   Mas public.users tem RLS ligado. Se a função rodasse com os privilégios do
--   chamador (SECURITY INVOKER, default), a própria policy de users dependeria
--   desta função para resolver — recursão infinita / retorno NULL que tranca tudo.
--   SECURITY DEFINER faz a função rodar com o dono, ignorando o RLS de users
--   APENAS dentro desta leitura controlada. O bootstrap não fura porque a função
--   lê exclusivamente a linha do próprio auth.uid().
--
-- STABLE: dentro de uma mesma query o resultado não muda, então o planner pode
--   cachear a chamada (importante para o critério 9 — RLS não vira seq scan).
--
-- search_path travado: blindagem contra search_path hijacking em SECURITY DEFINER.
--   Sem isso, um schema malicioso no path poderia sequestrar "users".
--
-- NOTA service_role (Registro 1): auth.uid() retorna NULL sob service_role,
--   logo esta função também retorna NULL quando o Spring conecta como service_role.
--   Isso é OK — service_role bypassa RLS. O backend NUNCA usa esta função para
--   descobrir tenant; recebe company_id do JWT da sessão do usuário / payload do
--   webhook e o passa explícito nas queries.
--
-- NOTA otimização futura (Registro 2): hoje cada policy faz um SELECT em
--   public.users (desprezível com STABLE + PK). Em escala, dá para eliminar o
--   lookup pondo company_id como custom claim no JWT (auth hook) e lendo via
--   auth.jwt() ->> 'company_id'. Caminho conhecido — não implementar sem problema medido.
-- =============================================================================
create or replace function app.company_id()
returns uuid
language sql
stable
security definer
set search_path = public, pg_temp
as $$
  select company_id
  from public.users
  where id = auth.uid()
$$;

comment on function app.company_id() is
  'Retorna o company_id do usuário autenticado (auth.uid()). Base de todas as policies de RLS. SECURITY DEFINER para não recursar na policy de public.users. Apenas roteamento de tenant — não valida lifecycle da empresa.';

-- Quem pode chamar: usuários autenticados (painel) e o backend (service_role).
-- anon não precisa — não há fluxo público que dependa de tenant.
revoke all on function app.company_id() from public;
grant execute on function app.company_id() to authenticated, service_role;
