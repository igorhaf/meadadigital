-- =============================================================================
-- 74_academia_waitlist.sql
-- Meada — Onda (piloto Academia): LISTA DE ESPERA por aula (backlog docs/FEATURES_SUGERIDAS_ACADEMIA.md #6).
--
-- Quando uma aula (academia_classes) está lotada (capacity - matrículas ativas = 0), o interessado
-- entra numa FILA por aula, por ordem de chegada. A POSIÇÃO na fila NÃO é coluna persistida — é
-- DERIVADA por query (count de 'aguardando' com enqueued_at menor + 1), espelhando o padrão de
-- fila-com-posição-derivada da BarberQueue (camada 8.1): atender/desistir de quem está à frente
-- recomputa todas as posições sem nenhum UPDATE de reordenação.
--
-- Decisões cravadas:
--   - status: aguardando (na fila) → chamado (o tenant chamou, há vaga) → matriculado (virou
--     matrícula) OU desistiu (saiu). enqueued_at é a âncora de ordem.
--   - unique parcial (class_id, contact_id) where status='aguardando': o mesmo contato não entra
--     2× na fila da MESMA aula enquanto aguardando (walk-in anônimo sem contact_id não é barrado —
--     nulls distintos).
--   - Regra do nicho preservada: entrar/sair da fila é INDEPENDENTE de matrícula; a matrícula (e a
--     vaga) só é criada pelo fluxo existente. A fila só ORDENA o interesse.
--   - RLS enable+force + policies via app.company_id() + grants (authenticated select/insert/update +
--     service_role all), espelhando 36_academia.sql / 72_academia_inadimplencia.sql.
-- =============================================================================

create table public.academia_class_waitlist (
  id            uuid        primary key default gen_random_uuid(),
  company_id    uuid        not null references public.companies(id) on delete restrict,
  class_id      uuid        not null references public.academia_classes(id) on delete cascade,
  contact_id    uuid        references public.contacts(id) on delete set null,
  student_name  text        not null check (length(trim(student_name)) between 1 and 200),
  student_phone text,
  status        text        not null default 'aguardando'
                            check (status in ('aguardando','chamado','matriculado','desistiu')),
  enqueued_at   timestamptz not null default now()
);

comment on table public.academia_class_waitlist is
  'Lista de espera por aula do tenant academia. POSIÇÃO é DERIVADA (count aguardando com enqueued_at menor +1), não persistida — espelha a BarberQueue (8.1). status aguardando→chamado→matriculado/desistiu.';

-- Fila ordenada por chegada, escopo por aula (leitura do painel + cálculo da posição derivada).
create index idx_academia_waitlist_class_status_enq
  on public.academia_class_waitlist (class_id, status, enqueued_at);
create index idx_academia_waitlist_company
  on public.academia_class_waitlist (company_id, status);

-- Impede 2 entradas 'aguardando' do mesmo contato na MESMA aula (walk-in anônimo sem contact não é barrado).
create unique index uniq_waitlist_active_per_contact_class
  on public.academia_class_waitlist (class_id, contact_id)
  where status = 'aguardando' and contact_id is not null;

alter table public.academia_class_waitlist enable row level security;
alter table public.academia_class_waitlist force  row level security;

create policy academia_waitlist_select on public.academia_class_waitlist
  for select to authenticated using (company_id = app.company_id());
create policy academia_waitlist_insert on public.academia_class_waitlist
  for insert to authenticated with check (company_id = app.company_id());
create policy academia_waitlist_update on public.academia_class_waitlist
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());

grant select, insert, update on public.academia_class_waitlist to authenticated;
grant all on public.academia_class_waitlist to service_role;
