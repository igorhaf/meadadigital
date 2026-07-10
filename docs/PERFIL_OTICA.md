# Perfil Ótica (loja de ótica: exame de vista + óculos sob receita) — camada 8.12

Guia operacional do tenant **otica** (`profile_id='otica'`). É o **PRIMEIRO HÍBRIDO** do projeto: um perfil
único que expõe DOIS subdomínios funcionais — agenda de exame (FLUXO A, clona o DentalBot) e encomenda de
óculos sob receita (FLUXO B, clona o ComidaBot/FloriculturaBot). A IA atende clientes pelo WhatsApp e faz
as duas coisas. Tom prestativo e claro; NUNCA dá conduta de saúde.

É o **31º perfil vertical** (30 + generic).

## Telas (sidebar "Ótica")

| Tela | Rota | Fluxo |
|------|------|-------|
| Optometristas | `/dashboard/otica-professionals` | A — CRUD (conflito de agenda é por profissional). |
| Exames | `/dashboard/otica-exams` | A — agenda de exames por status. |
| Catálogo | `/dashboard/otica-catalog` | B — armações/lentes/acessórios + opções (tipo de lente/tratamento) + made_to_order/lead. |
| Pedidos | `/dashboard/otica-orders` | B — Kanban com gate de aceite + receita + prazo. |
| Configurações | `/dashboard/otica-settings` | A+B — janela/duração do exame + mínimo + lead default. |

## TRAVA DE COMPORTAMENTO DA IA (o coração da SM)

A IA NUNCA prescreve grau, NUNCA diagnostica problema de visão, NUNCA recomenda tipo de lente como conduta
de saúde — para qualquer dúvida de visão/grau/sintoma, encaminha ao exame e oferece AGENDAR. Sobre receita:
a IA só REGISTRA os dados de grau que o cliente fornecer (esf/cil/eixo OD e OE + DP), como campos
ADMINISTRATIVOS do pedido — NÃO calcula, NÃO valida, NÃO interpreta. Sem os dados → `prescription_pending`
(trazer receita; a loja confirma no painel antes de montar). A IA NUNCA aceita/recusa a encomenda (gate
humano). A IA NUNCA inventa armação/lente/preço fora do catálogo; o total é recalculado pelo sistema.

## FLUXO A — Agenda de exame (clona o DentalBot)

Optometrista (`otica_professionals`) + conflito **POR `professional_id`** (half-open, re-verificado dentro
da transação) → 409 `conflict_slot`; mesmo horário + profissional DIFERENTE → OK (paralelismo). `end_at`
MATERIALIZADO no INSERT (start_at + duration_minutes, em Java). Janela `opens_at`..`closes_at` → 400
`outside_hours`. Cliente = contato (snapshot customer_name). Status `OticaExamStatus` ↔ TS (parity):
agendado→confirmado→realizado; cancelado/falta. Notifica confirmado (com profissional+data/hora, defensivo)
e cancelado. Tag **`<exame_otica>`**.

## FLUXO B — Encomenda de óculos (clona o ComidaBot/FloriculturaBot)

Pedido com itens (armação do catálogo + lentes via modifiers de tipo/tratamento), total RECALCULADO no
backend (descarta o da IA), snapshot, **gate de aceite humano** (nasce 'aguardando'; a loja ACEITA →
'em_montagem' ou RECUSA → 'recusado' com motivo). **Lead time de montagem**: se há item `made_to_order`,
`ready_date` é obrigatória e = `hoje + MAX(lead dos itens)` (override do default da config); antes disso →
422 `lead_time_violation` com a 1ª data possível. Pedido só de acessório (pronta-entrega) → ready_date pode
ser null. **Receita** (`rx_od_*`/`rx_oe_*`/`rx_pd` + `prescription_pending`): campos administrativos
persistidos VERBATIM; o backend NÃO interpreta o grau. Status `OticaOrderStatus` ↔ TS (parity):
aguardando→em_montagem→pronto→retirado; recusado/cancelado. Notifica em_montagem/pronto/recusado;
aguardando não notifica. Óculos pronto = **RETIRADA** na loja (sem taxa de entrega nesta SM). Tag
**`<encomenda_otica>`**. Categorias hardcoded (`OticaCategory` parity): armacoes/lentes/acessorios.

## Tags (namespaces próprios, distintas de TODAS as outras)

- `<exame_otica>{"professional_id","date":"YYYY-MM-DD","start_time":"HH:MM","notes"}`
- `<encomenda_otica>{"items":[{"catalog_item_id","options":[{"option_id"}],"quantity"}],"ready_date":"YYYY-MM-DD|null","rx":{"od":{"spherical","cylindrical","axis"},"oe":{...},"pd"}|null,"prescription_pending":true|false,"notes"}`

## O que NÃO existe nesta fase

Laudo/resultado de exame do optometrista (fase futura), interpretação/cálculo de grau (PROIBIDO),
prontuário oftalmológico estruturado (dado sensível — cripto fase futura), foto da armação (bloqueador
SERVICE_ROLE_KEY), orçamento de armação sob medida ad-hoc, entrega com taxa (nesta SM só retirada),
convênio, laboratório por API, pagamento real (Stripe #50).

## Notas técnicas

- Migration `56_otica.sql` (8 tabelas: config fundida, professionals, exam_appointments, catalog_items,
  catalog_item_options, orders, order_items, order_item_options). A CHECK ACRESCENTA `'otica'` preservando
  os 30 perfis. Entra por ÚLTIMO no `SCRIPTS` do `AbstractIntegrationTest` (sua CHECK tem os 31).
- Os dois fluxos coexistem HARMONICAMENTE (regra do projeto). Cache `OticaContextCache` TTL 30s (per
  contato) injeta os dois fluxos. Base de conhecimento (RAG): disponível como em todo perfil.
- Guard `/api/otica/**` → 403 `forbidden_wrong_profile`. Paleta `ardosia`. Tenant: `igorhaf23`.

## Onda 1 do backlog (2026-07 — FEATURES_SUGERIDAS_OTICA #1/#2, migration 97)

- **Lembrete + confirmação de exame (#1, `OticaReminderJob`, cron 10h10):** véspera dos exames
  agendado/confirmado ("seu exame com {profissional} é amanhã às {hora} — confirma?").
  Idempotência por (exame, start_at) via `reminded_start_at` (remarcar rearma). A resposta cai
  na IA, que emite `<confirmacao_exame>{exam_id, decisao}` (confirmado|cancelado) com BARREIRA
  DE CONTATO — o contexto ganhou o bloco "EXAMES FUTUROS DESTE CLIENTE" + a tag. Trava intacta
  (só horário; nada de grau). Toggle `exam_reminder_enabled` (ON).
- **Follow-up de óculos pronto (#2):** pedido parado em `pronto` há `pickup_followup_days`
  (default 3) recebe UMA cutucada por episódio (re-armada por `status_updated_at`). Toggles e
  janela em Configurações.
