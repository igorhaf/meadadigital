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
| Propostas | `/dashboard/casamento-proposals` | Lista por status + alerta "Data ocupada"; detalhe com os TRÊS editores (ORÇAMENTO com autofill do catálogo + cupom, CRONOGRAMA, CHECKLIST) e o PLANO DE PAGAMENTO (sinal + parcelas). |
| Catálogo | `/dashboard/casamento-catalog` | Pacotes (Prata/Ouro/Diamante) e adicionais com preço oficial — autofill do orçamento + vitrine da IA. |
| Cupons | `/dashboard/casamento-coupons` | Cupons percent/fixed que a EQUIPE aplica na proposta (feira de noivas, low-season). |
| Relatórios | `/dashboard/casamento-reports` | Receita realizada (líquida) por mês/assessor + receita prevista dos contratos fechados + funil. |
| Configurações | `/dashboard/casamento-settings` | Nome da assessoria + notas + toggles (lembrete de checklist/parcela, auto-realizada, aniversário). |

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

## Onda 1 do backlog (docs/FEATURES_SUGERIDAS_CASAMENTO.md #1/#2/#3/#4/#10/#14/#15/#16 — migration 84)

### #1 — Sinal + parcelas do contrato (`wedding_payments`, manual até o gateway #50)

O vazamento nº1 do nicho é o intervalo `aprovada→fechada`. A equipe monta o PLANO no detalhe da
proposta (sinal + N parcelas com vencimento) e marca **pago à mão** (Pix conferido). **GATE:** com
'sinal' NÃO pago no plano, `aprovada→fechada` → 409 `deposit_required` ("fechada = sinal recebido");
sem sinal no plano, fechamento livre. DIFERENTE dos sub-itens (itemsLocked em fechada), o plano segue
MUTÁVEL depois de fechada — parcelas vencem até o casamento; só recusada/cancelada travam. A IA
recebe o plano no contexto e **INFORMA** valor/vencimento/status exatamente como estão — nunca
inventa condição, nunca confirma recebimento.

### #2 — Lembretes de checklist e parcela (`WeddingReminderJob`)

Cron diário avisa o casal **D-3** do prazo de tarefa não concluída e do vencimento de parcela não
paga (texto fixo, não passa pela IA; proposta viva). Idempotência por (linha, data):
`reminded_due_date` — remarcar rearma. Toggles por tenant (`checklist_reminder_enabled` /
`payment_reminder_enabled`, default ligados). Sem canal → marca sem enviar.

### #3 — Catálogo de pacotes + adicionais (`wedding_catalog_items`)

Pacotes e adicionais com preço oficial, CRUD em `/api/casamento/catalog` + tela "Catálogo". É o
**autofill** do editor de orçamento (item da proposta continua snapshot texto) e a IA os APRESENTA
com o preço DO CATÁLOGO, podendo sugerir **UMA vez** um adicional compatível (upsell controlado, sem
desconto, sem insistir).

### #4/#16 — Auto-transição + aniversário (`WeddingAutoTransitionJob`)

Diário: proposta FECHADA com `wedding_date` passado vira REALIZADA (silencioso; toggle
`auto_complete_enabled`); no dia/mês do casamento de proposta REALIZADA (1+ ano), parabeniza o casal
1x/ano (`anniversary_notified_year`; toggle `anniversary_enabled`) — pós-venda de longo prazo.

### #10 — Cupom na proposta (`wedding_coupons` — clone do motor atelie)

Aplicado PELO PAINEL (PATCH/DELETE `/api/casamento/proposals/{id}/coupon`); desconto MATERIALIZADO
(`discount_cents` + snapshot) e RE-DERIVADO a cada mutação de item; `uses` devolve ao remover/trocar;
notificação de orcada usa o total LÍQUIDO; `empty_budget` segue sobre o total bruto.

### #14 — Dashboard comercial

GET `/api/casamento/reports/summary` + tela "Relatórios": receita REALIZADA (líquida) por mês
(`closed_at`) e por assessor, receita PREVISTA (fechadas por mês do `wedding_date`) e FUNIL por
status. Sem DDL.

### #15 — Alerta de data ocupada (`dateBusy`, derivado)

Sem coluna nova: `dateBusy` é um EXISTS na leitura — outra proposta aprovada/fechada/realizada na
MESMA `wedding_date` → badge "Data ocupada" na lista e alerta no detalhe. INFORMATIVO (sem 409): a
decisão de aceitar 2 eventos na data continua da equipe.

## O que a IA faz

- Abre uma **proposta** a partir do briefing (data prevista, nº de convidados, estilo, o que sonham).
- **Captura a aprovação/recusa** quando a proposta já está **orçada** (gate de 2 fases).
- **Apresenta pacotes/adicionais com o preço DO CATÁLOGO** e pode sugerir UMA vez um adicional
  (onda 1, backlog #3).
- **Informa o plano de pagamento** (sinal/parcelas, valores e vencimentos) exatamente como está no
  painel (onda 1, backlog #1).

## O que a IA NÃO faz

- **NUNCA fecha contrato, preço ou desconto** — quem orça e fecha é a equipe no painel.
- **NUNCA confirma disponibilidade de uma data não confirmada** — "vou verificar com a equipe".
- **NUNCA inventa** item de pacote, valor, fornecedor ou serviço.
- **NUNCA promete** estrutura/local/comodidade não informada, nem um "casamento perfeito", nem garante
  resultado.
- **NUNCA gerencia o cronograma do dia NEM o checklist pela conversa** — ambos são montados/acompanhados
  pela equipe no painel. A IA só abre a proposta e captura a aprovação.
- **NUNCA confirma pagamento, negocia condição ou informa chave Pix** — informa o plano como está e
  encaminha o acerto à equipe (onda 1, backlog #1).

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

- **Conflito DURO de agenda/data** (o `dateBusy` da onda 1 é alerta INFORMATIVO; 409 de data não
  existe — a assessoria decide).
- **Contrato e-sign** (o "contrato" é o estado `fechada`); **cobrança ONLINE do sinal/parcelas**
  (Stripe #50 — o REGISTRO manual + gate + lembrete já existem, onda 1 #1/#2).
- **Fornecedores externos** como pool com agenda própria; **lista de convidados / RSVP / mesa**.
- **Indicação com recompensa / NPS pós-casamento / reativação de lead frio** (motor de campanha da
  Onda 3).
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
- Migration `84_casamento_onda1.sql` (onda 1 do backlog): `wedding_catalog_items` +
  `wedding_coupons` + `wedding_payments` + desconto/`anniversary_notified_year` na proposta +
  `reminded_due_date` no checklist + 4 toggles na config.
- Guard `/api/casamento/**` → 403 `forbidden_wrong_profile`. Paleta `trigo`.
- Tenant de teste: `igorhaf18` (Casamento Modelo).

## Onda 2 do backlog (migration 113)

`docs/FEATURES_SUGERIDAS_CASAMENTO.md` #6/#8: **#6 pós-casamento** — REALIZADA encadeia
agradecimento emocionado + `review_link` + convite de indicação (o auge da emoção é o melhor
momento de coletar depoimento; toggle `post_event_enabled` ON). **#8 follow-up de orçamento
parado** — ORCADA há `follow_up_days` (default 5) sem resposta → 1 toque por episódio
(`follow_up_sent_at` vs `status_updated_at`; re-orçar REARMA) no `WeddingAutoTransitionJob`.
Settings ganhou "Pós-casamento e follow-up". Teste: `CasamentoOnda2IntegrationTest`. Fica:
#5 indicação c/ cashback (gateway), #7 CMS, #9 qualificação, #11 fornecedores, #12 RSVP (G),
#13 mood board (upload).
