-- =============================================================================
-- 25_multichannel_and_training.sql
-- Meada — Camada 5.25 (Fase I parcial) + #57 modo treinamento.
--
-- #74 UNIFICAÇÃO MULTI-CANAL: contacts.channels jsonb agrega os canais do mesmo contato
--     (ex.: {"whatsapp": "+5511...", "web": "sess-abc"}). conversations.channel marca a
--     origem ('whatsapp' default | 'web' | futuro 'email').
-- #73 WIDGET WEB: conversas do widget entram com channel='web'. O endpoint público
--     /api/chat/{slug} (backend) cria contato/conversa web e roda a IA. Sem schema novo
--     além de channel.
-- #72 EMAIL: PAUSA (segredo IMAP/SMTP) — não entra aqui.
-- #57 MODO TREINAMENTO: tabela ai_message_feedback — o tenant marca respostas da IA como
--     boa/ruim e opcionalmente fornece uma correção, que vira material de melhoria do
--     prompt (curadoria manual nesta fase; sem fine-tune automático).
-- =============================================================================

-- ---------------------------------------------------------------------------
-- #74 + #73 — canais
-- ---------------------------------------------------------------------------
alter table public.contacts
  add column channels jsonb;

alter table public.conversations
  add column channel text not null default 'whatsapp'
    check (channel in ('whatsapp', 'web', 'email'));


-- ---------------------------------------------------------------------------
-- #57 — feedback de mensagens da IA (modo treinamento)
-- ---------------------------------------------------------------------------
create table public.ai_message_feedback (
  id           uuid        primary key default gen_random_uuid(),
  company_id   uuid        not null references public.companies(id) on delete cascade,
  message_id   uuid        not null references public.messages(id) on delete cascade,
  rating       text        not null check (rating in ('good', 'bad')),
  correction   text,
  created_by   uuid        references auth.users(id) on delete set null,
  created_at   timestamptz not null default now(),
  unique (message_id)
);

comment on table public.ai_message_feedback is
  'Feedback do tenant sobre respostas da IA (camada 5.25 #57). rating good|bad + correção opcional. Curadoria manual; material para melhorar o prompt.';

create index idx_ai_message_feedback_company on public.ai_message_feedback (company_id, created_at desc);

alter table public.ai_message_feedback enable row level security;
alter table public.ai_message_feedback force  row level security;

create policy ai_message_feedback_select on public.ai_message_feedback
  for select to authenticated using (company_id = app.company_id());
create policy ai_message_feedback_insert on public.ai_message_feedback
  for insert to authenticated with check (company_id = app.company_id());
create policy ai_message_feedback_update on public.ai_message_feedback
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());
create policy ai_message_feedback_delete on public.ai_message_feedback
  for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.ai_message_feedback to authenticated;
grant all on public.ai_message_feedback to service_role;
