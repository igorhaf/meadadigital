-- =============================================================================
-- 96_padaria_onda1.sql
-- Meada — Onda Padaria 1 (backlog docs/FEATURES_SUGERIDAS_PADARIA.md #1/#6).
--
--   #1 SINAL/ENTRADA EM ENCOMENDA: o bolo sob encomenda produzido e não retirado é prejuízo
--      puro (insumo + mão de obra). deposit_cents (valor combinado pela padaria ao ver a
--      encomenda) + deposit_paid (manual até o gateway #50). Com sinal REGISTRADO (> 0) e
--      NÃO pago, o GATE DE ACEITE fica bloqueado: aguardando→em_preparo → 409
--      deposit_required (a "reserva verbal" só vira produção com compromisso financeiro).
--      Sem sinal registrado, o aceite segue livre (nem todo pedido exige sinal).
--
--   #6 UPSELL na persona (sem DDL): o cache autoriza UMA sugestão de complemento do próprio
--      cardápio (vela/refrigerante/docinhos/cartão) no fechamento, sem insistir.
-- =============================================================================

alter table public.padaria_orders
  add column if not exists deposit_cents integer check (deposit_cents is null or deposit_cents >= 0);
alter table public.padaria_orders
  add column if not exists deposit_paid boolean not null default false;
alter table public.padaria_orders
  add column if not exists deposit_paid_at timestamptz;

comment on column public.padaria_orders.deposit_cents is
  'Valor do SINAL/entrada da encomenda (centavos). NULL/0 = sem sinal (aceite livre). Com sinal registrado e não pago, aguardando→em_preparo → 409 deposit_required.';
comment on column public.padaria_orders.deposit_paid is
  'Sinal marcado como RECEBIDO pela padaria (manual até o gateway #50). Libera o aceite da encomenda.';
comment on column public.padaria_orders.deposit_paid_at is
  'Quando o sinal foi marcado como recebido (auditoria leve).';
