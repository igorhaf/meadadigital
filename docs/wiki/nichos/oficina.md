# OficinaBot — regras de negócio (oficina, camada 7.9)

[← Catálogo](../05-nichos.md) · Chassi: D — proposta com aprovação em 2 fases (avô) + sub-entidade (G) · Guia operacional: docs/PERFIL_OFICINA.md · Migrations: 38, 98

## O negócio em 3 linhas

Oficina mecânica / auto center. O cliente final é o dono do veículo, que fala pelo WhatsApp; cada
veículo é sub-entidade do contato (`os_vehicles`, placa única por tenant). A IA identifica o
veículo, **abre a OS** a partir da queixa e, quando o mecânico orçou, **captura a aprovação** — quem
diagnostica e orça é sempre o mecânico, no painel.

## Jornada no WhatsApp (cenários)

1. Cliente descreve o problema. A IA oferece os veículos já cadastrados dele ou pede placa/marca/
   modelo/ano e emite `<ordem_servico>` (modo `new_vehicle` cadastra o veículo E abre a OS no mesmo
   turno). A OS nasce `aberta`, total 0. Com catálogo (onda 1), a tag pode levar `servicos:[{id,qtd}]`
   e a OS já nasce com itens tabelados — preço SEMPRE do catálogo do tenant.
2. O mecânico monta o orçamento no painel (peças + mão de obra) e move a OS para `orcada` → o
   cliente recebe o total no WhatsApp ("responda sim para aprovar ou não para recusar").
3. O cliente responde; a IA emite `<aprovacao_os>` → OS vira `aprovada` (notifica) ou `recusada`
   (silencioso). É a ÚNICA mutação de estado que a IA faz.
4. Painel segue o funil: `em_execucao → concluida → entregue` (concluída e entregue notificam).
   A entrega materializa `next_return_date` e arma o lembrete de revisão (onda 1).

Exceções: tag sem `complaint`, veículo/mecânico inválido ou inativo, placa duplicada no
`new_vehicle`, JSON quebrado → handler best-effort loga e **não abre nada** (a mensagem segue).
`<aprovacao_os>` de OS que não está `orcada`, de outro contato ou inexistente → ignorada em silêncio.

## Regras de negócio

### Transacionais (invariantes duras)

- **R1** — Total da OS é sempre recalculado no backend: `line_total_cents = quantity × unit_price`
  e `total_cents` materializados em Java na MESMA transação de cada mutação de item (nunca coluna
  GENERATED; preço vindo de tag é descartado — no modo `servicos` só viajam ids, o preço vem de
  `oficina_catalog_items`).
- **R2** — `orcada` exige `total_cents > 0` (validação no service → 400 `empty_budget`).
- **R3** — Itens travados a partir de `em_execucao` (`OsStatus.itemsLocked()`:
  em_execucao/concluida/entregue/recusada/cancelada → 409 `order_locked`).
- **R4** — Placa única por tenant (`UNIQUE (company_id, plate)` → 409 `plate_taken`).
- **R5** — Excluir veículo com OS → 409 `vehicle_in_use` (preferir arquivar `active=false`);
  excluir mecânico em OS → 409 `mechanic_in_use` (FKs `on delete restrict`/`set null` + check no service).
- **R6** — Snapshots na OS (`customer_name/phone`, `vehicle_plate/model`): mudar cliente/veículo
  depois não altera OS passadas.
- **R7** — Aprovação por tag só do contato DONO da OS (barreira de contato no `AprovacaoOsHandler`)
  e só quando `status='orcada'`; qualquer outro estado é no-op.
- **R8** — `ENTREGUE` materializa `next_return_date = hoje(America/Sao_Paulo) + return_reminder_days`
  (config, CHECK 30–730, default 180) em Java, na transação do updateStatus.

### Máquina de status

```
aberta ──→ orcada ──→ aprovada ──→ em_execucao ──→ concluida ──→ entregue
  │           │  └→ recusada✦        │                              ✦=terminal
  └──────────┴──────────┴────────────┴→ cancelada✦
(concluida NÃO cancela — só → entregue)
```

| Transição | Quem pode | Notifica? |
|---|---|---|
| (abrir) → aberta | IA (`<ordem_servico>`) ou painel | não |
| aberta → orcada | humano no painel (exige item) | **sim** (total + veículo + pedido de sim/não) |
| orcada → aprovada | **IA** (`<aprovacao_os>`) ou painel | **sim** |
| orcada → recusada | IA ou painel | não (silencioso) |
| aprovada → em_execucao | humano | não |
| em_execucao → concluida | humano | **sim** ("veículo pronto") |
| concluida → entregue | humano | **sim** + arma lembrete de retorno |
| não-final → cancelada (exceto concluida) | humano | não |

Hardcoded em `OsStatus` ↔ `os-status.ts` (`OsStatusParityTest`). Transição inválida → 409
`invalid_status_transition`.

### O que a IA PODE × NUNCA faz (travas da persona)

PODE: abrir OS a partir da queixa; cadastrar veículo novo do cliente; pré-preencher serviços
TABELADOS do catálogo (só ids); informar orçamento já lançado; capturar aprovação/recusa.
NUNCA (persona `ProfilePromptContext.OFICINA`): diagnostica o defeito; inventa preço de peça ou
monta orçamento (quem orça é o mecânico); promete prazo que não esteja na OS; dúvida técnica →
orienta avaliação presencial. Queixa que exige diagnóstico abre OS SEM itens.

### Tags de IA

| Tag | Quando emite | Campos | Backend descarta/recalcula |
|---|---|---|---|
| `<ordem_servico>` | cliente confirma a abertura | `complaint`* , `notes`, `mechanic_id`, e **um** de: `vehicle_id` \| `new_vehicle{plate*,brand,model,year}`; opcional `servicos:[{id,qtd}]` | preço/total nunca viajam; `servicos` resolve do catálogo (inativo/inexistente ignorado); mechanic inválido aborta a abertura (best-effort) |
| `<aprovacao_os>` | cliente responde ao orçamento | `service_order_id`, `decisao: aprovada\|recusada` | só aplica se `orcada` + contato dono; o resto é ignorado |

`OutboundService` encadeia `maybeProcessAberturaOs`/`maybeProcessAprovacaoOs` e remove a tag antes do envio.

### Validações e erros

| reason | HTTP | Significado | Dispara quando |
|---|---|---|---|
| `forbidden_wrong_profile` | 403 | tenant de outro perfil | `/api/oficina/**` (`OficinaProfileGuard`) |
| `empty_budget` | 400 | não se orça OS vazia | `aberta→orcada` com total ≤ 0 |
| `order_locked` | 409 | escopo congelado | mutar item com OS em execução ou encerrada |
| `invalid_status_transition` | 409 | fora do funil | transição não permitida |
| `plate_taken` | 409 | placa já cadastrada | criar/editar veículo |
| `vehicle_in_use` / `mechanic_in_use` | 409 | histórico protegido | delete com OS vinculada |
| `inactive_vehicle` / `inactive_mechanic` | 400 | recurso desativado | abrir OS / atribuir mecânico |
| `invalid_kind` | 400 | item só `peca`\|`mao_de_obra` | criar/editar item |
| `invalid_item` | 400 | dados inválidos do catálogo | CRUD `/api/oficina/catalog` |
| `invalid_hours` / `invalid_time` / `invalid_date` | 400 | config/datas malformadas | settings / filtros |
| `*_not_found` (order/vehicle/mechanic/item/contact) | 404 | id inexistente no tenant | qualquer rota |

### Notificações ao cliente

Envia (texto fixo defensivo, best-effort — falha nunca reverte a transição; sem `conversation_id`
= silêncio): **orcada** (total + veículo), **aprovada**, **concluida**, **entregue**. Silenciosos:
aberta, recusada, em_execucao, cancelada — recusada é decisão do próprio cliente e cancelada evita
mensagem fria. O lembrete de retorno (job) também é texto fixo, sem passar pela IA.

## Dados e snapshots

- `os_mechanics` — catálogo simples sem agenda; `active=false` desativa.
- `os_vehicles` — sub-entidade do contact (`contact_id NOT NULL`); `UNIQUE (company_id, plate)`;
  CHECKs `year 1900–2100`, `mileage_km ≥ 0`; `active=false` arquiva sem perder OS.
- `service_orders` — snapshots de cliente/veículo; `total_cents` materializado; CHECK do status com
  os 8 estados; `closed_at` nos terminais; `status_updated_at`; onda 1: `next_return_date`,
  `return_reminded_at`; índice parcial `idx_os_return_due` (entregue + não lembrado).
- `os_items` — `kind peca|mao_de_obra`; `quantity > 0`; `line_total_cents` materializado.
- `oficina_catalog_items` (mig 98) — preço padrão por tenant; delete livre (itens de OS são snapshot).
- `os_config` — horário meramente informativo (defaults 08:00/18:00) + toggles do lembrete.
- Cache: `OficinaContextCache` (Caffeine, **TTL 20s**, key `companyId:contactId`, máx. 1000) — injeta
  mecânicos, até 10 veículos do contato, OS em aberto e os tabelados (ids exatos); `invalidate(companyId)`
  explícito em TODA mutação de mecânico/veículo/OS/item/config.

## Features de onda (backlog implementado — migration 98)

- **Catálogo de peças/serviços (#1):** autofill do editor de itens no painel + campo `servicos` na
  tag. Trava intacta: a IA nunca digita preço; `ServiceOrderService.openWithCatalogItems` resolve
  só itens ativos do próprio tenant, best-effort.
- **Lembrete de retorno/revisão (#2):** `OficinaReminderJob` (cron `oficina.return-reminder-cron`,
  default 10h30) varre OS `entregue` com `next_return_date` vencido e envia "hora da revisão do
  {modelo/placa}?" **1x por OS** (`return_reminded_at`); toggle `return_reminder_enabled` (default
  ON) + janela `return_reminder_days`; sem canal → marca sem envio; `EVOLUTION_DRY_RUN` honrado.

## O que NÃO existe (limites honestos)

Agendamento de entrada por horário (horário da config é informativo), tabela FIPE, foto do veículo/
avaria (bloqueador de upload), pagamento online/sinal (sem gate `deposit_required` — diferente dos
irmãos do chassi D), cupom/desconto, nota fiscal, controle de estoque de peças, follow-up de
orçamento parado e auto-transição de status (não há scheduler além do lembrete de retorno).

**Lacunas doc×código:** o guia diz "cancelada a partir de qualquer estado não-final", mas
`OsStatus.allowedNext()` NÃO permite `concluida→cancelada` (só `→entregue`) — o código manda.
