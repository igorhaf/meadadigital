-- =============================================================================
-- 88_sushi_onda2.sql
-- Meada — Onda Sushi 2 (backlog docs/FEATURES_SUGERIDAS_SUSHI.md #2/#3).
--
--   #2 UPSELL/CROSS-SELL PROATIVO DA IA: a persona ganha (via bloco do SushiMenuCache) a
--      instrução de, ANTES de emitir a tag <pedido>, oferecer 1 item complementar do PRÓPRIO
--      cardápio disponível (categoria sub-representada no carrinho — bebida/sobremesa/combo
--      maior). Uma oferta só, respeita o "não", NUNCA item fora do cardápio (trava preservada).
--      Toggle upsell_enabled por tenant (default LIGADO — é só sugestão consultiva).
--
--   #3 REATIVAÇÃO DE CLIENTE INATIVO (SushiReactivationJob): cron diário varre os contatos
--      com pedidos ENTREGUES (terminal não-cancelado) cujo ÚLTIMO pedido é anterior a
--      reactivation_days (default 21) e dispara UMA mensagem de reengajamento pela conversa
--      mais recente do contato — opcionalmente mencionando um cupom de retorno FIXO cadastrado
--      pelo tenant (reactivation_coupon_code → só entra na mensagem se existir/ativo/válido em
--      sushi_coupons). Idempotência por (contato, janela): sushi_reactivation_log registra o
--      disparo e o contato NÃO é reabordado dentro de reactivation_days (cooldown = a própria
--      janela). DEFAULT DESLIGADO (opt-in explícito — lição do incidente Baileys: nunca disparo
--      em massa sem decisão consciente do tenant; EVOLUTION_DRY_RUN honrado por baixo).
-- =============================================================================

alter table public.sushi_restaurant_config
  add column if not exists upsell_enabled boolean not null default true;
alter table public.sushi_restaurant_config
  add column if not exists reactivation_enabled boolean not null default false;
alter table public.sushi_restaurant_config
  add column if not exists reactivation_days integer not null default 21
    check (reactivation_days between 7 and 180);
alter table public.sushi_restaurant_config
  add column if not exists reactivation_coupon_code text;

comment on column public.sushi_restaurant_config.upsell_enabled is
  'Se true (default), a IA oferece 1 item complementar do cardápio antes de fechar o pedido (backlog sushi #2).';
comment on column public.sushi_restaurant_config.reactivation_enabled is
  'OPT-IN da reativação de inativos (backlog sushi #3). Default DESLIGADO — disparo em massa é decisão consciente do tenant (lição do incidente Baileys).';
comment on column public.sushi_restaurant_config.reactivation_days is
  'Dias sem pedido entregue até o contato ser considerado inativo; também é o cooldown entre disparos pro mesmo contato.';
comment on column public.sushi_restaurant_config.reactivation_coupon_code is
  'Código de cupom de RETORNO (opcional) mencionado na mensagem — só entra se existir/ativo/válido em sushi_coupons.';

-- Log de disparos da reativação (idempotência por contato+janela + auditoria leve).
create table public.sushi_reactivation_log (
  id          uuid        primary key default gen_random_uuid(),
  company_id  uuid        not null references public.companies(id) on delete restrict,
  contact_id  uuid        not null references public.contacts(id) on delete cascade,
  sent_at     timestamptz not null default now(),
  had_channel boolean     not null default true
);

comment on table public.sushi_reactivation_log is
  'Disparos da reativação de inativos do sushi (backlog #3). INSERT pelo job (service_role). had_channel=false quando o contato não tinha conversa resolúvel (marcado sem envio pra não revarrer).';

create index idx_sushi_reactivation_contact
  on public.sushi_reactivation_log (company_id, contact_id, sent_at desc);

alter table public.sushi_reactivation_log enable row level security;
grant all on public.sushi_reactivation_log to service_role;
