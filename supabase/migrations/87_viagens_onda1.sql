-- =============================================================================
-- 87_viagens_onda1.sql
-- Meada — Onda Viagens (backlog docs/FEATURES_SUGERIDAS_VIAGENS.md #1/#2/#8).
--
-- Três features de RECEITA/RETENÇÃO do nicho viagens (camada 8.18), sem bloqueador transversal
-- (o gateway #50 segue pendente — o sinal entra como REGISTRO manual, ponte cravada no backlog):
--
--   #1 SINAL/ENTRADA NA PROPOSTA + GATE NO FECHAMENTO: a agência emite/reserva com o sinal;
--      proposta aprovada sem sinal é desistência na certa. deposit_cents (valor combinado) +
--      deposit_paid (a equipe marca ao confirmar o Pix — manual até o gateway #50). Com sinal
--      REGISTRADO (deposit_cents > 0) e NÃO PAGO, a transição aprovada→fechada é bloqueada no
--      service → 409 deposit_required (espelho EXATO do atelie, mig 81). Sem sinal registrado,
--      o fechamento segue livre. A IA NÃO toca em valor/pagamento (trava preservada).
--
--   #2 LEMBRETES DE VIAGEM + PÓS-VENDA/NPS (TravelReminderJob): cron diário varre as
--      travel_proposals FECHADAS com datas e dispara mensagens FIXAS e defensivas pela conversa
--      (NÃO passam pela IA): D-7 do start_date (checklist documentos/bagagem), D0 (boa viagem)
--      e D+2 do end_date (como foi? avalie/indique — vale também para 'realizada').
--      Idempotência por (proposta, data): *_reminded_* guarda QUAL data já disparou — remarcar
--      a viagem para outra data REARMA o lembrete (espelho reminded_due_date do atelie).
--
--   #8 FOLLOW-UP DE PROPOSTA ORÇADA PARADA: cotação sem resposta esfria. O mesmo job cutuca
--      com gentileza a proposta 'orcada' há N dias (config, default 2) sem mudança de status.
--      Idempotência re-armável: quote_followup_sent_at < status_updated_at ⇒ pode cutucar de
--      novo (re-orçar rearma o follow-up; espelho do followup do concessionaria).
-- =============================================================================

-- ---------------------------------------------------------------------------
-- Toggles por tenant (opt-out; default LIGADO) + janela do follow-up.
-- ---------------------------------------------------------------------------
alter table public.travel_config
  add column if not exists trip_reminder_enabled boolean not null default true;
alter table public.travel_config
  add column if not exists quote_followup_enabled boolean not null default true;
alter table public.travel_config
  add column if not exists quote_followup_days integer not null default 2
    check (quote_followup_days between 1 and 30);

comment on column public.travel_config.trip_reminder_enabled is
  'Se true (default), o TravelReminderJob envia D-7 (checklist), D0 (boa viagem) e D+2 pós-viagem (NPS) das propostas fechadas com datas. Ausência de linha de config = ligado.';
comment on column public.travel_config.quote_followup_enabled is
  'Se true (default), o TravelReminderJob cutuca a proposta orcada parada há quote_followup_days sem resposta.';
comment on column public.travel_config.quote_followup_days is
  'Dias em orcada sem mudança de status até o follow-up automático (default 2).';

-- ---------------------------------------------------------------------------
-- #1 — sinal/entrada na proposta (registro manual até o gateway #50; clone atelie 81).
-- ---------------------------------------------------------------------------
alter table public.travel_proposals
  add column if not exists deposit_cents integer check (deposit_cents is null or deposit_cents >= 0);
alter table public.travel_proposals
  add column if not exists deposit_paid boolean not null default false;
alter table public.travel_proposals
  add column if not exists deposit_paid_at timestamptz;

comment on column public.travel_proposals.deposit_cents is
  'Valor do SINAL/entrada combinado com o cliente (centavos). NULL/0 = sem sinal (fechamento livre). Com sinal registrado e não pago, aprovada→fechada → 409 deposit_required.';
comment on column public.travel_proposals.deposit_paid is
  'Sinal marcado como RECEBIDO pela equipe (confirmação manual do Pix até o gateway #50). Gate do fechamento quando deposit_cents > 0.';
comment on column public.travel_proposals.deposit_paid_at is
  'Quando o sinal foi marcado como recebido (auditoria leve). Preservado enquanto pago.';

-- ---------------------------------------------------------------------------
-- #2/#8 — marcadores de idempotência dos disparos (por proposta+data / por episódio de orcada).
-- ---------------------------------------------------------------------------
alter table public.travel_proposals
  add column if not exists pretrip_reminded_start_date date;
alter table public.travel_proposals
  add column if not exists start_reminded_start_date date;
alter table public.travel_proposals
  add column if not exists posttrip_reminded_end_date date;
alter table public.travel_proposals
  add column if not exists quote_followup_sent_at timestamptz;

comment on column public.travel_proposals.pretrip_reminded_start_date is
  'start_date para o qual o lembrete D-7 (checklist) JÁ foi enviado — remarcar a viagem rearma.';
comment on column public.travel_proposals.start_reminded_start_date is
  'start_date para o qual o "boa viagem" (D0) JÁ foi enviado.';
comment on column public.travel_proposals.posttrip_reminded_end_date is
  'end_date para o qual o pós-viagem/NPS (D+2) JÁ foi enviado.';
comment on column public.travel_proposals.quote_followup_sent_at is
  'Quando o follow-up da orcada parada foi enviado. Re-armado por status_updated_at (re-orçar permite novo follow-up).';

-- Varreduras do job (parciais — só o que interessa ao cron).
create index if not exists idx_travel_reminder_start
  on public.travel_proposals (start_date)
  where status = 'fechada' and start_date is not null;
create index if not exists idx_travel_reminder_end
  on public.travel_proposals (end_date)
  where status in ('fechada','realizada') and end_date is not null;
create index if not exists idx_travel_followup_orcada
  on public.travel_proposals (status_updated_at)
  where status = 'orcada';
