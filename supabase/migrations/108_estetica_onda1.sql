-- =============================================================================
-- 108_estetica_onda1.sql
-- Meada — Onda Estética 1 (backlog docs/FEATURES_SUGERIDAS_ESTETICA.md #1/#2/#3/#4).
--
--   #1/#2 LEMBRETE DE VÉSPERA + CONFIRMAÇÃO SIM/NÃO: cadeira vazia é a maior perda
--      do nicho. O job lembra na véspera pedindo confirmação; a resposta fecha o
--      loop via <confirmacao_estetica> (confirmado OU cancelado — e o CANCELAMENTO
--      já DEVOLVE a sessão ao pacote pela mecânica existente). Remarcar REARMA
--      (marker = start_at lembrado). Toggle reminder_enabled (default ON).
--   #4 AUTO-TRANSIÇÃO: confirmado vencido → realizado (silencioso, toggle) e pacote
--      ATIVO com valid_until vencida → EXPIRADO (a data que faltava pro estado que
--      já existia — materializada na ativação quando package_validity_days está
--      configurado, editável no painel).
--   #3 RÉGUA DE RENOVAÇÃO (opt-in OFF — disparo à base, lição Baileys): pacote
--      ESGOTADO há renewal_days sem pacote novo → convite de renovação; pacote
--      ATIVO a vencer em expiry_warning_days → aviso. 1 toque por pacote
--      (renewal_reminded_at). A resposta cai no fluxo existente de <compra_pacote>.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1) aesthetic_config: toggles + janelas da onda.
-- ---------------------------------------------------------------------------
alter table public.aesthetic_config
  add column reminder_enabled       boolean not null default true,
  add column auto_complete_enabled  boolean not null default true,
  add column auto_expire_enabled    boolean not null default true,
  add column package_validity_days  integer check (package_validity_days is null or package_validity_days between 7 and 1095),
  add column renewal_enabled        boolean not null default false,
  add column renewal_days           integer not null default 30 check (renewal_days between 7 and 365),
  add column expiry_warning_days    integer not null default 7 check (expiry_warning_days between 1 and 60);

comment on column public.aesthetic_config.reminder_enabled is
  'Se true (default), o EsteticaReminderJob lembra a cliente na véspera pedindo confirmação (SIM/NÃO).';
comment on column public.aesthetic_config.package_validity_days is
  'Validade materializada em valid_until na ATIVAÇÃO do pacote (null = pacote sem validade automática).';
comment on column public.aesthetic_config.renewal_enabled is
  'Opt-in da régua de renovação (backlog #3). DESLIGADO por default: ligar dispara pra base (lição Baileys).';

-- ---------------------------------------------------------------------------
-- 2) aesthetic_appointments: marker do lembrete.
-- ---------------------------------------------------------------------------
alter table public.aesthetic_appointments
  add column reminded_start_at timestamptz;

comment on column public.aesthetic_appointments.reminded_start_at is
  'start_at da sessão quando o lembrete de véspera foi enviado — remarcar REARMA (marker <> start_at).';

-- ---------------------------------------------------------------------------
-- 3) aesthetic_packages: validade + marker da régua.
-- ---------------------------------------------------------------------------
alter table public.aesthetic_packages
  add column valid_until          date,
  add column renewal_reminded_at  timestamptz;

comment on column public.aesthetic_packages.valid_until is
  'Validade do pacote (onda 1, backlog #4): ATIVO com valid_until vencida vira EXPIRADO pelo job. Materializada na ativação (package_validity_days) ou editada no painel. NULL = sem validade.';
comment on column public.aesthetic_packages.renewal_reminded_at is
  'Quando a régua de renovação tocou este pacote (esgotado OU a-vencer) — 1 toque por pacote (backlog #3).';

create index idx_aesthetic_packages_expiry
  on public.aesthetic_packages (valid_until)
  where status = 'ativo';
