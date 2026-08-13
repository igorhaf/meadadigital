# Viagens — regras de negócio (viagens, camada 8.18)

[← Catálogo](../05-nichos.md) · Chassi: D — proposta com aprovação em 2 fases (clone do eventos) · Guia operacional: docs/PERFIL_VIAGENS.md · Migrations: 62, 87

## O negócio em 3 linhas

Agência de viagens que monta COTAÇÕES de pacote (aéreo, hospedagem, traslados, passeios). O cliente
final é o viajante; a IA colhe o briefing (destino, datas, viajantes, estilo), **abre a proposta em
rascunho**, a equipe cota no painel e a IA captura a aprovação. Escapada: o **itinerário MULTI-DIA**
(`travel_itinerary_days`) — uma linha por DIA da viagem, descritivo, sem status, fora do total.

## Jornada no WhatsApp (cenários)

1. Cliente descreve a viagem; a IA emite `<proposta_viagem>` → proposta nasce `rascunho`, total 0,
   sem itens, sem itinerário. `consultant_id` inválido/inativo → **reabre SEM o consultor**;
   datas/`num_travelers` inválidos → ignorados (viram null); sem `briefing` a tag é descartada
   (best-effort, warn, nada acontece).
2. A equipe monta a cotação (itens por categoria aereo/hospedagem/traslado/passeio/outro) e o roteiro
   dia-a-dia, e move para `orcada` → o cliente recebe o total + destino ("Posso seguir com a reserva?
   Responda com sim para aprovar ou não para recusar").
3. A IA emite `<aprovacao_viagem>` → `aprovada`/`recusada`. Única mutação de estado da IA; só com
   proposta `orcada` E do próprio contato (barreira).
4. A equipe registra o sinal (se cobrar) e fecha (`fechada` — a agência emite/reserva com o sinal).
   O `TravelReminderJob` acompanha a viagem fechada: **D-7** checklist de documentos/bagagem, **D0**
   boa viagem, **D+2 da volta** pós-viagem com pedido de avaliação/indicação.
5. **Exceções:** fechar com sinal registrado e não pago → 409 `deposit_required`; orçar sem item →
   400 `empty_budget`; mexer em cotação/itinerário/sinal após `fechada` → 409 `proposal_locked`;
   cotação parada em `orcada` há `quote_followup_days` → 1 cutucada com o total JÁ orçado.

## Regras de negócio

### Transacionais (invariantes duras)

- **R1 — Total recalculado da cotação:** `total_cents` MATERIALIZADO na mesma transação de toda
  mutação de item; `line_total_cents = quantity × unit_price` em Java (nunca GENERATED).
- **R2 — `orcada` exige cotação:** total ≤ 0 → 400 `empty_budget` (`TravelProposalService.updateStatus`).
- **R3 — Gate de sinal:** com `deposit_cents > 0` e `deposit_paid = false`, `aprovada→fechada` → 409
  `deposit_required` (espelho exato do atelie). Sem sinal registrado (NULL/0), fechamento livre.
  Marcar pago sem valor > 0 → 400 `invalid_deposit`. Registro manual (Pix conferido) até o gateway #50.
- **R4 — Trava de estado:** `itemsLocked()` a partir de `fechada` (e nos terminais) congela cotação,
  ITINERÁRIO e sinal → 409 `proposal_locked`.
- **R5 — Itinerário ordenado e validado:** leitura `day_date asc NULLS LAST, day_number asc`;
  `day_number ≥ 1` (CHECK); o reorder re-materializa `day_number` 1..N na mesma transação e id que
  não pertence à proposta → 404 `itinerary_day_not_found` (o reorder falha inteiro — ≠ atelie).
- **R6 — Categoria de item fechada:** CHECK `aereo|hospedagem|traslado|passeio|outro`; `quantity > 0`,
  preços ≥ 0 (CHECK).
- **R7 — `num_travelers ≥ 1`** (CHECK; no painel → 400 `invalid_num_travelers`; via tag o valor
  inválido é descartado e vira default 1).

### Máquina de status

```
rascunho ──→ orcada ──→ aprovada ──→ fechada ──→ realizada
    │           │──→ recusada           │
    └───────────┴──→ cancelada ←────────┘   (realizada/recusada/cancelada terminais; preenchem closed_at)
```

| Transição | Quem pode | Notifica o cliente? |
|---|---|---|
| (criação) → rascunho | IA (`<proposta_viagem>`) ou tenant (POST) | não |
| rascunho → orcada | humano no painel (exige total > 0) | **sim** (total + destino + "sim/não") |
| orcada → aprovada / recusada | IA (`<aprovacao_viagem>`) ou humano | **sim** (ambas, texto fixo) |
| aprovada → fechada | humano (gate do sinal, R3) | **sim** ("sua viagem está confirmada") |
| fechada → realizada | humano apenas (SEM auto-transição) | não |
| qualquer não-terminal → cancelada | humano | não |

Fora do diagrama → 409 `invalid_status_transition` (`TravelProposalStatus.allowedNext`).

### O que a IA PODE × NUNCA faz (travas da persona)

- **PODE:** abrir proposta do briefing (destino, período, nº de viajantes, estilo, orçamento
  aproximado em texto); informar o total já orçado; capturar aprovação/recusa de proposta ORÇADA.
- **NUNCA:** emite passagem/reserva/bilhete/voucher; confirma disponibilidade de VOO, HOTEL, ASSENTO,
  TARIFA ou PREÇO não cravado pela equipe ("vou verificar com a equipe"); inventa destino/roteiro/
  item/valor/hotel/companhia; fecha contrato/preço/desconto; promete "viagem perfeita" ou garante
  clima/câmbio/condição de terceiros; gerencia o itinerário dia-a-dia pela conversa (é do painel;
  o contexto da IA nem o injeta); toca em valor de sinal/pagamento.

### Tags de IA

| Tag | Quando a IA emite | Campos | O que o backend descarta/recalcula |
|---|---|---|---|
| `<proposta_viagem>` | briefing colhido | `destination`, `start_date`, `end_date`, `num_travelers`, `travel_style`, `briefing`, `consultant_id`, `notes` | consultor inválido → abre sem; datas inválidas → null; `num_travelers < 1` → null (default 1); total sempre 0 |
| `<aprovacao_viagem>` | cliente respondeu à cotação | `proposal_id`, `decisao` (aprovada\|recusada) | barreira de contato + só `orcada`; senão no-op silencioso |

Parse por regex (nunca tool calling); tag removida antes do envio; falha → `Optional.empty()` + warn.
Não há tag para itinerário nem sinal — são do painel.

### Validações e erros

| reason | HTTP | Significado de negócio | Cenário que dispara |
|---|---|---|---|
| `forbidden_wrong_profile` | 403 | tenant não é viagens | `/api/viagens/**` com outro perfil |
| `empty_budget` | 400 | não se orça cotação vazia | rascunho → orcada com total 0 |
| `deposit_required` | 409 | fechada = sinal recebido | aprovada → fechada com sinal registrado não pago |
| `invalid_deposit` | 400 | sinal inconsistente | valor negativo; "pago" sem valor > 0 |
| `proposal_locked` | 409 | escopo congelado | mutar cotação/itinerário/sinal a partir de fechada |
| `invalid_status_transition` / `invalid_status` | 409 / 400 | máquina de status | transição proibida; status desconhecido |
| `invalid_num_travelers` | 400 | viajantes < 1 | POST/PATCH no painel |
| `invalid_date` | 400 | data malformada | start/end/day_date não-ISO |
| `invalid_followup_days` | 400 | janela fora de 1–30 | PATCH da config |
| `inactive_consultant` / `consultant_not_found` | 400 / 404 | consultor inválido | atribuir inativo/inexistente (painel; via tag é tolerado) |
| `consultant_in_use` | 409 | consultor com propostas | DELETE — caminho é desativar |
| `proposal_not_found` / `item_not_found` / `itinerary_day_not_found` | 404 | entidade de outro tenant/inexistente | id errado; reorder com id estranho |

### Notificações ao cliente

- **Envia** em `orcada` (total + destino), `aprovada`, `fechada`, `recusada`; lembretes de viagem
  D-7/D0/D+2 (fechadas com data exata; o D+2 vale também para `realizada`); follow-up da cotação
  parada (transmite o total JÁ orçado — a IA não recalcula nada).
- **Silêncio** em `rascunho`, `realizada` (transição) e `cancelada`; proposta manual sem
  `conversation_id` → jobs marcam sem envio (não revarrem).
- Textos fixos e defensivos (sem confirmar voo/hotel emitido, sem "viagem perfeita"); best-effort via
  `TravelProposalNotifier` — falha de envio nunca reverte a transição.

## Dados e snapshots

- **`travel_proposals`** — snapshots `customer_name`/`customer_phone` (cliente segue sendo contact);
  `destination`/`travel_style` texto livre; `start_date`/`end_date` campos LIVRES (é cotação, não
  reserva de recurso — SEM conflito de agenda); `num_travelers ≥ 1`; `total_cents` materializado;
  `deposit_cents/paid/paid_at`; marcadores de idempotência `pretrip_reminded_start_date`/
  `start_reminded_start_date`/`posttrip_reminded_end_date`/`quote_followup_sent_at`; tenant só
  SELECT/UPDATE (INSERT via service_role).
- **`travel_proposal_items`** — categoria CHECK, description 1–200, `line_total_cents` materializado.
- **`travel_itinerary_days`** — `day_number ≥ 1`, `day_date` NULLABLE, title 1–200; descritivo SEM
  status (≠ checklist binário do casamento, ≠ provas do atelie); índice `(proposal_id, day_date,
  day_number)`.
- **`travel_consultants`** (catálogo simples, sem agenda), **`travel_config`** (1:1; ausência =
  defaults; toggles coalesce true nos jobs).
- **Cache:** `ViagensContextCache` (Caffeine, **TTL 20s**, max 1000, keyed `(companyId, contactId)`)
  — consultores ativos + propostas rascunho/orcada do contato (id, destino, datas, viajantes, total).
  **NÃO injeta o itinerário.** Invalidado em toda mutação de consultor/proposta/item/itinerário/config.

## Features de onda (backlog implementado)

Migration 87 (`TravelReminderJob`, cron default `0 10 9 * * *`):

- **Sinal + gate no fechamento (#1):** `deposit_*` na proposta + PATCH
  `/api/viagens/proposals/{id}/deposit`; regras R3/R4. Painel: seção "Sinal / entrada" + selo
  "Sinal pendente" na lista. A IA não toca em valor/pagamento.
- **Lembretes de viagem + pós-venda/NPS (#2):** varre propostas FECHADAS com data EXATA —
  `start_date` = hoje+7 (D-7, checklist), `start_date` = hoje (D0, boa viagem) e `end_date` =
  hoje−2 (D+2, pós-viagem/NPS — também `realizada`). Mensagens fixas, não passam pela IA.
  Idempotência por (proposta, data): remarcar a viagem REARMA. Toggle `trip_reminder_enabled` —
  **default LIGADO** (ausência de config = ligado).
- **Follow-up de orçada parada (#8):** `orcada` com `status_updated_at` ≤ hoje −
  `quote_followup_days` (1–30, default 2) e sem follow-up DESTE episódio
  (`quote_followup_sent_at < status_updated_at` REARMA — re-orçar rearma) → 1 cutucada com o total.
  Toggle `quote_followup_enabled` — **default LIGADO**.

## O que NÃO existe (limites honestos)

- Emissão real de passagem/GDS, integração booking/OTA/cia aérea, seguro-viagem/visto, câmbio.
- Pagamento ONLINE do sinal (gateway #50 — só registro manual); catálogo de pacotes/destinos
  pré-cadastrados (cotação é ad-hoc — ≠ casamento, que tem catálogo); cupom de desconto (a
  notificação de `orcada` usa o total BRUTO — não há desconto neste perfil).
- Contrato e-sign; lista de passageiros; conflito de agenda/data; auto-transição fechada→realizada
  (manual — ≠ casamento); anexo de voucher/PDF (bloqueador SERVICE_ROLE_KEY); multi-consultor com
  agenda/conflito.
- Guard `ViagensProfileGuard` em `/api/viagens/**`; paleta `floresta`; tenant de teste `igorhaf29`.
