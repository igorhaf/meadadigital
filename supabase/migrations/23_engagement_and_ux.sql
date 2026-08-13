-- =============================================================================
-- 23_engagement_and_ux.sql
-- Meada — Camadas 5.21 (engajamento) + 5.22 (UX). Migrations agrupadas.
--
-- #82 BOAS-VINDAS: ai_settings.welcome_message text (até 500 chars, validado no app).
--     OutboundService detecta a 1ª inbound do contato e envia o welcome antes da resposta.
-- #81 REATIVAÇÃO: ai_settings.reactivation_days int + reactivation_message text.
--     contacts.reactivated_at controla o disparo único por janela (#81).
-- #88 SAVED REPLIES: tabela saved_replies (respostas prontas por empresa).
-- #84 BUSCA GLOBAL: índices GIN pg_trgm em contacts.name/phone_number e messages.content
--     para busca por similaridade. Requer extensão pg_trgm.
-- #83 NOTIFICAÇÕES TEMPO REAL: sem schema — Supabase Realtime no frontend (subscribe).
-- =============================================================================

create extension if not exists pg_trgm;

-- ---------------------------------------------------------------------------
-- #82 + #81 — ai_settings + contacts
-- ---------------------------------------------------------------------------
alter table public.ai_settings
  add column welcome_message      text,
  add column reactivation_days    int,
  add column reactivation_message text;

alter table public.contacts
  add column reactivated_at timestamptz;

-- ---------------------------------------------------------------------------
-- #88 — saved_replies
-- ---------------------------------------------------------------------------
create table public.saved_replies (
  id         uuid        primary key default gen_random_uuid(),
  company_id uuid        not null references public.companies(id) on delete cascade,
  title      text        not null check (length(trim(title)) between 1 and 80),
  body       text        not null check (length(trim(body)) between 1 and 2000),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

comment on table public.saved_replies is
  'Respostas prontas por empresa (camada 5.22 #88). Todos do tenant veem. Sem variáveis dinâmicas nesta fase.';

create index idx_saved_replies_company on public.saved_replies (company_id);

alter table public.saved_replies enable row level security;
alter table public.saved_replies force  row level security;

create policy saved_replies_select on public.saved_replies
  for select to authenticated using (company_id = app.company_id());
create policy saved_replies_insert on public.saved_replies
  for insert to authenticated with check (company_id = app.company_id());
create policy saved_replies_update on public.saved_replies
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());
create policy saved_replies_delete on public.saved_replies
  for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.saved_replies to authenticated;
grant all on public.saved_replies to service_role;

create trigger trg_saved_replies_audit after insert or update on public.saved_replies
  for each row execute function app.audit_trigger();

-- ---------------------------------------------------------------------------
-- #84 — índices GIN pg_trgm para busca global
-- ---------------------------------------------------------------------------
create index idx_contacts_name_trgm on public.contacts using gin (name gin_trgm_ops);
create index idx_contacts_phone_trgm on public.contacts using gin (phone_number gin_trgm_ops);
create index idx_messages_content_trgm on public.messages using gin (content gin_trgm_ops);
