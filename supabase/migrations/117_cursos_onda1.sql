-- =============================================================================
-- 117_cursos_onda1.sql
-- Meada — Onda Cursos 1 (backlog docs/FEATURES_SUGERIDAS_CURSOS.md #1/#2/#3).
--
--   #1 CERTIFICADO DE CONCLUSÃO: ao entrar em CONCLUIDA, o backend GERA o
--      certificado (código único + snapshots) e envia o link/código; a rota
--      PÚBLICA sem auth /public/cursos/certificados/{code} renderiza o
--      certificado em HTML (verificação de autenticidade). A IA só ENVIA o que
--      o backend gerou (trava intacta).
--   #2 NUDGE ANTI-ABANDONO: matrícula ativa parada há nudge_days no mesmo módulo
--      (com próximo módulo existente) → 1 toque motivador por episódio (marker
--      re-armado quando o progresso avança). Funil ativo → default ON.
--   #3 CUPOM (cursos_coupons, motor comum): a IA repassa o código na tag
--      <matricula_curso>; o backend valida e aplica o desconto sobre a
--      MENSALIDADE snapshotada (discount_cents materializado); inválido NÃO
--      aborta a matrícula.
-- =============================================================================

create table public.cursos_coupons (
  id               uuid        primary key default gen_random_uuid(),
  company_id       uuid        not null references public.companies(id) on delete restrict,
  code             text        not null check (length(trim(code)) between 1 and 40),
  kind             text        not null check (kind in ('percent','fixed')),
  value            integer     not null check (value >= 0),
  min_order_cents  integer     not null default 0 check (min_order_cents >= 0),
  max_uses         integer     check (max_uses is null or max_uses >= 0),
  uses             integer     not null default 0,
  valid_until      date,
  active           boolean     not null default true,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now()
);

create unique index uniq_cursos_coupon_code on public.cursos_coupons (company_id, lower(code));
alter table public.cursos_coupons enable row level security;
grant all on public.cursos_coupons to service_role;

alter table public.cursos_enrollments
  add column discount_cents       integer not null default 0 check (discount_cents >= 0),
  add column coupon_code_snapshot text,
  add column nudge_sent_at        timestamptz;

comment on column public.cursos_enrollments.discount_cents is
  'Desconto do cupom sobre a MENSALIDADE snapshotada (onda 1, backlog #3). Mensalidade líquida = course_monthly_cents − discount_cents.';
comment on column public.cursos_enrollments.nudge_sent_at is
  'Quando o nudge anti-abandono tocou esta matrícula — re-armado quando o progresso avança (onda 1, backlog #2).';

create table public.cursos_certificates (
  id            uuid        primary key default gen_random_uuid(),
  company_id    uuid        not null references public.companies(id) on delete restrict,
  enrollment_id uuid        not null unique references public.cursos_enrollments(id) on delete cascade,
  code          text        not null unique,
  student_name  text        not null,
  course_title  text        not null,
  school_name   text,
  issued_at     timestamptz not null default now()
);

comment on table public.cursos_certificates is
  'Certificados de conclusão (onda 1, backlog #1). Gerado ao CONCLUIR a matrícula; code único verifica autenticidade na rota pública /public/cursos/certificados/{code}. Snapshots preservam o histórico.';

alter table public.cursos_certificates enable row level security;
grant all on public.cursos_certificates to service_role;

alter table public.cursos_config
  add column nudge_enabled  boolean not null default true,
  add column nudge_days     integer not null default 7 check (nudge_days between 1 and 90),
  add column certificate_base_url text;

comment on column public.cursos_config.nudge_enabled is
  'Se true (default), o CursosNudgeJob cutuca matrícula ativa parada há nudge_days no mesmo módulo (funil ativo, não é disparo à base).';
comment on column public.cursos_config.certificate_base_url is
  'Base pública do link do certificado (ex.: https://escola.meadadigital.com). Vazio = a notificação sai só com o código.';
