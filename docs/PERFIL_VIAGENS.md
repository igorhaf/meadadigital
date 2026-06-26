# Perfil Viagens (Agência de viagens) — camada 8.18

Guia operacional do tenant **viagens** (`profile_id='viagens'`). A agência monta COTAÇÕES/PROPOSTAS de
pacote de viagem (destino, datas, viajantes, hospedagem, voos, traslados, passeios) e captura a aprovação
do cliente. A IA atende os viajantes pelo WhatsApp, abre a proposta a partir do briefing, e captura a
aprovação/recusa em 2 fases. Tom prestativo-consultivo de consultor de viagens.

É o **33º perfil vertical** (32 + generic). CLONA o chassi do Eventos (8.2, proposta order-based +
aprovação em 2 fases) e inaugura a sub-entidade de **roteiro/itinerário multi-dia**.

## Telas (sidebar "Viagens")

| Tela | Rota | O que faz |
|------|------|-----------|
| Consultores | `/dashboard/viagens-consultants` | CRUD (desativar preferido a excluir). |
| Propostas | `/dashboard/viagens-proposals` | Lista por status + detalhe com 2 editores (cotação + itinerário). |
| Configurações | `/dashboard/viagens-settings` | Nome da agência + notas (sem horário). |

## ESCAPADA — Roteiro / itinerário multi-dia

A proposta tem um ITINERÁRIO dia-a-dia (`travel_itinerary_days`): UMA linha por DIA da viagem
(`day_number` int + `day_date` date NULLABLE + title + description), ordenado por **`day_date asc NULLS
LAST, day_number asc`**. DIFERENTE de tudo que veio antes:

- O **cronograma** do eventos/casamento é o roteiro de UM DIA (ordenado por HORA, `start_time time`).
- O **itinerário** de viagens é MULTI-DIA: cobre 7/10/15 dias. Não são horas de um dia, são os DIAS da
  viagem inteira.
- O **checklist** do casamento é binário; as **etapas** de projetos têm 3 estados. O itinerário NÃO tem
  status/progresso — é descritivo.
- NÃO entra no total. Gerenciado SÓ no painel (SEM tag de IA). Trava junto com `itemsLocked()` (a partir
  de 'fechada').
- reorder re-materializa `day_number` 1..N na transação.

## Proposta (chassi eventos)

Order-based: itens de COTAÇÃO (`travel_proposal_items`: category aereo/hospedagem/traslado/passeio/outro,
description, quantity, unit_price, line_total materializado) ENTRAM no total. Total `total_cents`
materializado a cada mutação. Cliente NÃO é entidade (snapshots `customer_name`/`phone`). SEM conflito de
agenda/data (`start_date`/`end_date`/`day_date` são campos LIVRES — é cotação, não reserva de recurso).

Status `TravelProposalStatus` ↔ TS (parity, idêntico ao EventProposalStatus): `rascunho → orcada →
aprovada → fechada → realizada` + recusada/cancelada. Ir pra 'orcada' exige `total_cents > 0` → 400
`empty_budget`. `itemsLocked()` em fechada/realizada/recusada/cancelada (trava cotação E itinerário).
Notifica orcada (com total+destino), aprovada, fechada, recusada; texto defensivo (sem "viagem perfeita",
sem confirmar voo/hotel emitido).

## Tags (namespaces próprios, distintas de TODAS as outras)

- `<proposta_viagem>{"destination","start_date","end_date","num_travelers","travel_style","briefing","consultant_id","notes"}`
  — ABRE a proposta em rascunho (1 modo; consultant/datas inválidos são ignorados mas a proposta abre).
- `<aprovacao_viagem>{"proposal_id","decisao":"aprovada|recusada"}` — MUTA o estado (só se a proposta está
  'orcada'; senão no-op).

## O que a IA NÃO faz

Emitir passagem/reserva/voucher; confirmar voo/hotel/preço/disponibilidade não cravada pela equipe;
inventar destino/item/valor; fechar contrato/preço/desconto; prometer "viagem perfeita"; gerenciar o
itinerário pela conversa.

## O que NÃO existe nesta fase

Emissão real de passagem/GDS, integração booking/OTA/cia aérea, pagamento/sinal/câmbio (Stripe #50),
catálogo de pacotes/destinos pré-cadastrados (cotação ad-hoc), contrato e-sign, seguro-viagem/visto, lista
de passageiros, conflito de agenda/data, lembrete automático de data de viagem, anexo de voucher/PDF,
multi-consultor com agenda/conflito.

## Notas técnicas

- Migration `62_viagens.sql` (5 tabelas: consultants, config, proposals, proposal_items, itinerary_days).
  A CHECK ACRESCENTA `'viagens'` preservando os 32 perfis. Entra por ÚLTIMO no `SCRIPTS` do
  `AbstractIntegrationTest` (sua CHECK tem os 33).
- Contexto via `ViagensContextCache` (TTL 20s, per (companyId, contactId)) — NÃO injeta o itinerário.
  Base de conhecimento (RAG): disponível como em todo perfil.
- Guard `/api/viagens/**` → 403 `forbidden_wrong_profile`. Paleta `floresta`. Tenant: `igorhaf29`.
