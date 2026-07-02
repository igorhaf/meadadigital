-- =============================================================================
-- 79_academia_aniversario.sql
-- Meada — Academia (backlog docs/FEATURES_SUGERIDAS_ACADEMIA.md #14): saudação de aniversário.
--
-- Feature de RELACIONAMENTO/RETENÇÃO: a academia perde a chance de estreitar o vínculo com o aluno
-- no aniversário dele. Este job (AcademiaAniversarioJob) roda uma vez por dia e, para cada tenant
-- academia, encontra os contatos cujo dia/mês de nascimento é HOJE e ainda não foram saudados NESTE
-- ANO, envia uma mensagem calorosa pelo WhatsApp e marca o ano — para não repetir a saudação.
--
-- Decisões cravadas:
--   - birth_date fica no CORE (public.contacts) — o contato é do core, não do nicho; a coluna é
--     genérica (um aniversário é um aniversário), o USO é do perfil academia nesta SM.
--   - academia_birthday_greeted_year: idempotência POR ANO (1 saudação por ano de vida). Espelha o
--     padrão overdue_notified_month/reminded_24h dos jobs de scheduler existentes.
--   - contacts já tem RLS enable+force + policies via app.company_id() + grants (migrations 02/03);
--     esta migration só ACRESCENTA colunas (idempotente), sem re-declarar RLS/policy/grant.
-- =============================================================================

-- Data de nascimento do contato (core; genérica). Nullable — a maioria dos contatos não terá.
alter table public.contacts
  add column if not exists birth_date date;

-- Idempotência da saudação de aniversário da academia: ano em que já parabenizamos.
alter table public.contacts
  add column if not exists academia_birthday_greeted_year integer;

comment on column public.contacts.birth_date is
  'Data de nascimento do contato (opcional). Usada pela saudação de aniversário do perfil academia (migration 79).';
comment on column public.contacts.academia_birthday_greeted_year is
  'Ano da última saudação de aniversário enviada pelo AcademiaAniversarioJob — evita repetir no mesmo ano. Espelha overdue_notified_month/reminded_24h dos jobs de scheduler.';
