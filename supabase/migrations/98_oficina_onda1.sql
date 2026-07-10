-- =============================================================================
-- 98_oficina_onda1.sql
-- Meada — Onda Oficina 1 (backlog docs/FEATURES_SUGERIDAS_OFICINA.md #1/#2).
--
--   #1 CATÁLOGO DE PEÇAS/SERVIÇOS TABELADOS (oficina_catalog_items, clone do catálogo atelie
--      mig 82): nome + categoria livre ("peça", "mão de obra"…) + preço padrão + ativo. Vira
--      autofill do editor de itens da OS no painel E destrava a IA a PRÉ-PREENCHER serviços
--      tabelados na abertura da OS (campo opcional `servicos` na tag <ordem_servico> — o preço
--      vem do catálogo do PRÓPRIO tenant, trava intacta: a IA continua sem inventar preço).
--      Serviço que exige diagnóstico segue sem item (mecânico orça).
--
--   #2 LEMBRETE DE RETORNO/REVISÃO (OficinaReminderJob): manutenção é recorrente (óleo/
--      revisão), mas nada traz o cliente de volta. Ao ENTREGAR a OS o backend materializa
--      next_return_date = entrega + return_reminder_days (config, default 180); o cron diário
--      dispara "faz X meses do último serviço no {modelo/placa} — hora da revisão?" 1x por OS
--      (return_reminded_at). Toggle return_reminder_enabled (default ON).
-- =============================================================================

create table public.oficina_catalog_items (
  id               uuid        primary key default gen_random_uuid(),
  company_id       uuid        not null references public.companies(id) on delete restrict,
  name             text        not null check (length(trim(name)) between 1 and 200),
  category         text,        -- "peça", "mão de obra", "revisão"... (texto livre, nullable)
  unit_price_cents integer     not null check (unit_price_cents >= 0),
  active           boolean     not null default true,
  notes            text,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now()
);

comment on table public.oficina_catalog_items is
  'Catálogo de peças/serviços TABELADOS do tenant oficina (onda 1, backlog #1). Autofill do editor de itens da OS + fonte do pré-preenchimento pela IA (campo servicos da tag <ordem_servico> — preço SEMPRE do catálogo). active=false retira do autofill/IA; delete livre (os_items são snapshot).';

create index idx_ofc_catalog_company_active on public.oficina_catalog_items (company_id, active) where active = true;
create index idx_ofc_catalog_company_name on public.oficina_catalog_items (company_id, name);

alter table public.oficina_catalog_items enable row level security;
grant all on public.oficina_catalog_items to service_role;

-- #2 — lembrete de retorno.
alter table public.os_config
  add column if not exists return_reminder_enabled boolean not null default true;
alter table public.os_config
  add column if not exists return_reminder_days integer not null default 180
    check (return_reminder_days between 30 and 730);

comment on column public.os_config.return_reminder_enabled is
  'Se true (default), o OficinaReminderJob lembra o cliente quando vence o retorno sugerido da OS entregue.';
comment on column public.os_config.return_reminder_days is
  'Dias após a ENTREGA da OS até o lembrete de retorno/revisão (default 180).';

alter table public.service_orders
  add column if not exists next_return_date date;
alter table public.service_orders
  add column if not exists return_reminded_at timestamptz;

comment on column public.service_orders.next_return_date is
  'Retorno sugerido, materializado na ENTREGA da OS (= data + return_reminder_days da config).';
comment on column public.service_orders.return_reminded_at is
  'Quando o lembrete de retorno foi enviado (1x por OS).';

create index if not exists idx_os_return_due
  on public.service_orders (next_return_date)
  where status = 'entregue' and return_reminded_at is null;
