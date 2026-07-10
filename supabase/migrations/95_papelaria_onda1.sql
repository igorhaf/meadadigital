-- =============================================================================
-- 95_papelaria_onda1.sql
-- Meada — Onda Papelaria 1 (backlog docs/FEATURES_SUGERIDAS_PAPELARIA.md #1/#2/#5).
--
--   #1 SINAL/ENTRADA PRA LIBERAR A PRODUÇÃO: peça personalizada aprovada e não paga é prejuízo
--      total (não revende "Casamento Ana & Bruno"). deposit_cents (valor combinado) +
--      deposit_paid (manual até o gateway #50). Com sinal REGISTRADO (> 0) e NÃO pago:
--      (a) aprovar a arte seta art_approved MAS NÃO move pra em_producao (fica em
--      arte_aprovacao aguardando o sinal); (b) a transição manual arte_aprovacao→em_producao
--      → 409 deposit_required (espelho do art_not_approved). Marcar o sinal como pago com a
--      arte já aprovada MOVE automaticamente pra em_producao (fecha o loop).
--
--   #2 PREÇO POR FAIXA DE TIRAGEM (papelaria_item_tiers): o line linear (unit × qty) cobra
--      500 convites como 10× 50 — irreal em gráfica (o setup dilui). Faixas por item
--      (min_qty + unit_price decrescente): no cálculo, a faixa com MAIOR min_qty ≤ quantity
--      vira o preço-base (+ Σ deltas dos modifiers). Sem faixa cadastrada → unit_price do
--      item (compat total). O cache injeta a tabela ("50 un = X · 100 un = Y") pra IA
--      estimular a tiragem maior — preço sempre do catálogo, trava intacta.
--
--   #5 UPSELL (sem DDL): o cache instrui UMA sugestão de item complementar de OUTRA categoria
--      (convite → save the date/tags/menu), sem insistir.
-- =============================================================================

alter table public.papelaria_orders
  add column if not exists deposit_cents integer check (deposit_cents is null or deposit_cents >= 0);
alter table public.papelaria_orders
  add column if not exists deposit_paid boolean not null default false;
alter table public.papelaria_orders
  add column if not exists deposit_paid_at timestamptz;

comment on column public.papelaria_orders.deposit_cents is
  'Valor do SINAL/entrada combinado (centavos). NULL/0 = sem sinal (produção livre). Com sinal registrado e não pago, arte aprovada NÃO entra em produção → 409 deposit_required.';
comment on column public.papelaria_orders.deposit_paid is
  'Sinal marcado como RECEBIDO pela equipe (manual até o gateway #50). Pago + arte aprovada → move automaticamente pra em_producao.';
comment on column public.papelaria_orders.deposit_paid_at is
  'Quando o sinal foi marcado como recebido (auditoria leve).';

-- #2 — faixas de tiragem por item do catálogo.
create table public.papelaria_item_tiers (
  id               uuid    primary key default gen_random_uuid(),
  company_id       uuid    not null references public.companies(id) on delete restrict,
  item_id          uuid    not null references public.papelaria_catalog_items(id) on delete cascade,
  min_qty          integer not null check (min_qty >= 1),
  unit_price_cents integer not null check (unit_price_cents >= 0),
  unique (item_id, min_qty)
);

comment on table public.papelaria_item_tiers is
  'Faixas de preço por TIRAGEM (backlog papelaria #2): a faixa com maior min_qty <= quantity define o unit_price-base da linha (+ deltas dos modifiers). Sem faixas → unit_price do item (compat). Gerida pelo painel (service_role).';

create index idx_papelaria_tiers_item on public.papelaria_item_tiers (item_id, min_qty desc);

alter table public.papelaria_item_tiers enable row level security;
grant all on public.papelaria_item_tiers to service_role;
