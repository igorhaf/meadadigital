# Perfil Casamento (assessoria / cerimonial) — camada 8.7

Guia operacional do tenant **casamento** (`profile_id='casamento'`). Assessoria / cerimonial de
casamento: a equipe COORDENA casamentos (não é fornecedor isolado de buffet/foto). A IA atende os
noivos via WhatsApp, abre uma proposta a partir do briefing, e — quando a equipe já montou o orçamento
no painel — informa o total e captura a aprovação/recusa.

É o **20º perfil vertical** (21º contando o generic). CLONA o chassi do **EventosBot** (camada 8.2 —
proposta order-based + itens de orçamento com total materializado + cronograma do dia + gate de
aprovação em 2 fases via tag) e inaugura a **TERCEIRA sub-entidade** no mesmo artefato: o **checklist
de tarefas pré-casamento com prazo** (`wedding_checklist_tasks`).

## Telas (sidebar "Casamento")

| Tela | Rota | O que faz |
|------|------|-----------|
| Assessores | `/dashboard/casamento-planners` | CRUD de assessores/cerimonialistas (catálogo simples, sem agenda; desativar preferido a excluir). |
| Propostas | `/dashboard/casamento-proposals` | Lista por status; detalhe com TRÊS editores: ORÇAMENTO (total recalculado) + CRONOGRAMA (ordenado por horário) + CHECKLIST (ordenado por prazo, toggle concluída). |
| Configurações | `/dashboard/casamento-settings` | Nome da assessoria + notas (sem horário). |

## ESCAPADA — TRÊS sub-itens no mesmo artefato

Eventos tinha 2 tipos de sub-item; casamento tem **3**, que NÃO se misturam:

| Sub-item | Tabela | Semântica | Ordem | Entra no total? |
|----------|--------|-----------|-------|-----------------|
| Orçamento | `wedding_proposal_items` | DINHEIRO | — | **Sim** (line_total materializado) |
| Cronograma | `wedding_timeline_items` | a HORA das coisas no dia | `start_time` | Não |
| **Checklist** | `wedding_checklist_tasks` | o que FALTA fazer até lá | `due_date` NULLS LAST | Não |

O **checklist** (a escapada) são marcos de PREPARAÇÃO que acontecem ANTES do dia (ex.: "enviar
convites", "última prova do vestido", "provar o bolo", "fechar buffet"), cada um com:

- **due_date** NULLABLE (prazo — tarefa "sem prazo ainda" vai ao fim).
- **done** BOOLEAN (estado binário pendente/concluída — NÃO máquina de status, NÃO tem parity). O
  toggle seta `done_at` quando `done=true`; zera quando `done=false`.
- Lido ordenado por `due_date asc NULLS LAST, created_at asc`.

O checklist **NÃO entra no total** e é gerenciado **SÓ no painel** — não há tag de IA pra ele (igual o
cronograma do eventos). Trava junto com os outros 2 sub-itens a partir de `fechada` (`itemsLocked` →
409 `proposal_locked`).

## Funil da proposta (idêntico ao EventosBot)

`WeddingProposalStatus` ↔ `wedding-proposal-status.ts` (`WeddingProposalStatusParityTest`):

```
rascunho → orcada → aprovada → fechada → realizada
   │         │         │          │
   └ cancelada (de qualquer não-terminal) · orcada → recusada
```

- Ir pra `orcada` exige `total_cents > 0` → 400 `empty_budget`.
- Transição inválida → 409 `invalid_status_transition`.
- **`itemsLocked` a partir de `fechada`** congela os TRÊS sub-itens.
- Notificam (texto defensivo, SEM "casamento perfeito"): **orcada** (com total + estilo),
  **aprovada**, **fechada**, **recusada**. rascunho/realizada/cancelada silenciosos.

## O que a IA faz

- Abre uma **proposta** a partir do briefing (data prevista, nº de convidados, estilo, o que sonham).
- **Captura a aprovação/recusa** quando a proposta já está **orçada** (gate de 2 fases).

## O que a IA NÃO faz

- **NUNCA fecha contrato, preço ou desconto** — quem orça e fecha é a equipe no painel.
- **NUNCA confirma disponibilidade de uma data não confirmada** — "vou verificar com a equipe".
- **NUNCA inventa** item de pacote, valor, fornecedor ou serviço.
- **NUNCA promete** estrutura/local/comodidade não informada, nem um "casamento perfeito", nem garante
  resultado.
- **NUNCA gerencia o cronograma do dia NEM o checklist pela conversa** — ambos são montados/acompanhados
  pela equipe no painel. A IA só abre a proposta e captura a aprovação.

## Tags

**`<proposta_casamento>`** (ABRE a proposta em rascunho — UM modo só; resolve o contato/noivos da
conversa; total 0, sem sub-itens):

```json
{ "wedding_style": "texto", "wedding_date": "YYYY-MM-DD|null", "guest_count": N|null,
  "briefing": "texto", "planner_id": "UUID|null", "notes": "texto" }
```
- `planner_id` inválido → ignora o assessor mas abre.

**`<aprovacao_casamento>`** (MUTA o estado — só se a proposta está `'orcada'`):

```json
{ "proposal_id": "UUID", "decisao": "aprovada|recusada" }
```

Ambas têm namespace próprio, distinto de `<proposta_evento>`/`<aprovacao_proposta>` e de TODAS as
outras. O `OutboundService` remove a tag antes de enviar ao cliente.

## O que NÃO existe nesta fase

- **Conflito de agenda/data** (a data é campo livre — a assessoria coordena ~1 casamento por data).
- **Catálogo de pacotes pré-cadastrados** (orçamento ad-hoc — a equipe digita os itens).
- **Contrato e-sign** (o "contrato" é o estado `fechada`); **pagamento/sinal/parcelas** (Stripe #50).
- **Fornecedores externos** como pool com agenda própria; **lista de convidados / RSVP / mesa**.
- **Lembrete automático** de prazo do checklist (o due_date é informativo; scheduler é fase futura).
- **Foto/mood board** (bloqueador SERVICE_ROLE_KEY).

## Notas técnicas

- Migration `51_casamento.sql` (6 tabelas: planners, config, proposals, proposal_items, timeline_items,
  checklist_tasks). A CHECK de `companies.profile_id` ACRESCENTA `'casamento'` preservando os 20
  perfis anteriores. **Lição de ordenação:** como 51 é numericamente anterior às migrations de perfis
  posteriores (53/58/63) mas tem a lista de CHECK completa, ela é aplicada por ÚLTIMO no `SCRIPTS` do
  `AbstractIntegrationTest` (a migration que reescreve a CHECK por último precisa ter a lista completa).
- `wedding_proposals`/`*_items`/`*_timeline_items`/`*_checklist_tasks`: INSERT pelo backend
  (service_role); tenant SELECT/UPDATE.
- `total_cents`/`line_total_cents` MATERIALIZADOS no INSERT/UPDATE (não generated; o UPDATE de item
  calcula o `line_total` dos valores FINAIS em Java — não na SET clause, que em Postgres usa valores
  antigos da linha). `wedding_date` campo livre (sem conflito de agenda).
- Cliente NÃO é entidade do core — continua o contact; snapshots `customer_name`/`phone` (pode ser
  "Ana & João") na proposta.
- Contexto da IA via `CasamentoContextCache` (Caffeine TTL 20s, keyed por (companyId, contactId) —
  assessores + propostas do contato em aberto; **NÃO injeta cronograma nem checklist**), invalidado em
  toda mutação.
- Guard `/api/casamento/**` → 403 `forbidden_wrong_profile`. Paleta `trigo`.
- Tenant de teste: `igorhaf18` (Casamento Modelo).
