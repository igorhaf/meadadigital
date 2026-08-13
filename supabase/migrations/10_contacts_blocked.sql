-- =============================================================================
-- 10_contacts_blocked.sql
-- Meada — Camada 5.11: bloqueio de contato (#41).
--
-- Adiciona contacts.blocked: quando true, o WebhookService persiste a mensagem
-- inbound (histórico íntegro — o tenant vê que o bloqueado tentou contato) mas NÃO
-- dispara a IA (não publica MessageInboundProcessedEvent). Outcome IGNORED_CONTACT_BLOCKED.
--
-- NOT NULL DEFAULT false: todo contato existente nasce desbloqueado (graceful).
--
-- Trigger de audit (trg_contacts_audit): block/unblock e edição de nome são ações
-- sensíveis (afetam se a empresa responde a alguém) — auditadas pelo app.audit_trigger
-- genérico da fase-5.3 (já existente). contacts entra no conjunto de tabelas auditadas.
-- =============================================================================

alter table public.contacts
  add column blocked boolean not null default false;

create trigger trg_contacts_audit after insert or update on public.contacts
  for each row execute function app.audit_trigger();
