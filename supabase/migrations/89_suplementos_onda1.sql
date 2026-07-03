-- =============================================================================
-- 89_suplementos_onda1.sql
-- Meada — Onda Suplementos 1 (backlog docs/FEATURES_SUGERIDAS_SUPLEMENTOS.md #3-frete/#9).
--
--   #3b FRETE GRÁTIS ACIMA DE X: a objeção nº1 do nicho é a taxa de entrega sobre ticket caro.
--       free_shipping_threshold_cents (NULL = desligado): quando o SUBTOTAL bate o piso, o
--       backend ZERA a taxa no cálculo do pedido (materializado, como sempre). A IA pode avisar
--       "faltam R$ X pro frete grátis" (fato do pedido, não conselho de saúde — trava preservada).
--
--   #9 DEVOLUÇÃO DE ESTOQUE AO CANCELAR/RECUSAR (restock idempotente): espelho do moda_infantil —
--      ao entrar em recusado/cancelado o backend devolve o estoque das variantes do pedido
--      (stock_quantity + qtd) e marca stock_returned=true NA MESMA transação; duplo-cancelamento
--      não devolve 2x. Evita ruptura fantasma (estoque bate com a realidade).
--
--   (O cupom de 1ª compra do #3 fica para a Onda 2 — motor unificado com.meada.common.coupons +
--   integração no fluxo do pedido, espelho comida mig 85.)
-- =============================================================================

alter table public.sup_config
  add column if not exists free_shipping_threshold_cents integer
    check (free_shipping_threshold_cents is null or free_shipping_threshold_cents >= 0);

comment on column public.sup_config.free_shipping_threshold_cents is
  'Piso de SUBTOTAL (centavos) para frete grátis. NULL = desligado. Subtotal >= piso → delivery_fee_cents do pedido = 0 (backlog suplementos #3).';

alter table public.sup_orders
  add column if not exists stock_returned boolean not null default false;

comment on column public.sup_orders.stock_returned is
  'Estoque das variantes devolvido ao entrar em recusado/cancelado (idempotência do restock — backlog suplementos #9, espelho moda_infantil).';
