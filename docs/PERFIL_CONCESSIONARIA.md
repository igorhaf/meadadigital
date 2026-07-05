# Perfil Concessionária (loja de carros) — camada 8.17

Guia operacional do tenant **concessionaria** (`profile_id='concessionaria'`). Loja de carros /
revenda: a IA atende clientes via WhatsApp e faz **AS TRÊS COISAS** de uma concessionária — mostra o
ESTOQUE, agenda TEST-DRIVE e registra LEAD de compra. É um **híbrido triplo** deliberado.

É o **21º perfil vertical** (22º contando generic). Combina três moldes: catálogo de **ESTOQUE** com
ciclo de vida próprio + **agenda** de test-drive (clona dental, conflito por VENDEDOR) + **lead/funil**
(clona oficina/eventos, sem itens).

## Telas (sidebar "Concessionária")

| Tela | Rota | O que faz |
|------|------|-----------|
| Estoque | `/dashboard/concessionaria-vehicles` | CRUD do estoque + ciclo de status (disponível→reservado→vendido); foto via link. |
| Vendedores | `/dashboard/concessionaria-salespeople` | CRUD de vendedores (o conflito de test-drive é por vendedor). |
| Test-drives | `/dashboard/concessionaria-testdrives` | Agenda por status (agendado→confirmado→realizado/cancelado/no_show). |
| Leads | `/dashboard/concessionaria-leads` | Funil (novo→em_negociacao→fechado/perdido); atribuir vendedor. |
| Configurações | `/dashboard/concessionaria-settings` | Nome da loja + janela/duração do test-drive. |

## ESCAPADA — o veículo é ITEM DE ESTOQUE com identidade e status próprios

O **veículo** (`concessionaria_vehicles`) é a entidade central — **estoque da LOJA** (FK company), NÃO
sub-entidade de um cliente (≠ `os_vehicles` da oficina, que é do cliente). É o primeiro perfil em que o
"produto" do nicho é um **item de estoque com identidade única e status próprio** (≠ catálogo
reabastecível de comida/floricultura, onde o item é um TIPO, não uma unidade física).

**Ciclo de estoque** (`VehicleStatus` ↔ `concessionaria-vehicle-status.ts`, parity):

```
disponivel → reservado, vendido
reservado  → disponivel, vendido
vendido    → (terminal)
```

O veículo **VENDIDO sai da disponibilidade** — não entra na vitrine da IA, não aceita test-drive/lead.
Mudança de status é **AÇÃO HUMANA** no painel (a IA não toca o estoque). A IA opera sobre o veículo por
**TRÊS lentes** (vitrine, agenda, lead) sem nunca alterá-lo.

## FLUXO 2 — Test-drive (clona DENTAL, conflito por VENDEDOR)

`concessionaria_test_drives` referencia `vehicle_id` + `salesperson_id`. O conflito é **por
salesperson_id** (não por veículo): 2 clientes podem test-driveiar o MESMO modelo em horários distintos;
o que não pode é o mesmo VENDEDOR em dois test-drives sobrepostos. `findConflict` (janela half-open, só
status bloqueantes agendado/confirmado) é **re-verificado DENTRO da transação** → choque = 409
`conflict_slot`. **MESMO horário com vendedor DIFERENTE → OK** (paralelismo). `end_at` MATERIALIZADO no
INSERT (start_at + duration; não generated). **Só de veículo 'disponivel'** → senão 422
`vehicle_not_available`.

Status `TestDriveStatus` ↔ TS (parity): agendado→confirmado→realizado; cancelado/no_show. Notifica
**confirmado** (com veículo+vendedor+data/hora) e **cancelado**; demais silenciosos.

## FLUXO 3 — Lead (clona OFICINA/EVENTOS, funil sem itens)

`concessionaria_leads` é um registro de **INTERESSE** em UM veículo (NÃO order-com-itens-e-total).
Condição `payment_condition` (avista|financiado, FLAG declarativa). `LeadStatus` ↔ TS (parity):
novo→em_negociacao→fechado/perdido. A IA cria o lead em **'novo'** e NÃO move; a equipe trabalha o funil.
**Preço = SNAPSHOT do catálogo** (`vehicle_price_cents`) — a IA NUNCA carrega preço na tag; o backend
sempre usa o preço do catálogo. **Só de veículo 'disponivel'** → 422 `vehicle_not_available`.

## Onda 1 do backlog (docs/FEATURES_SUGERIDAS_CONCESSIONARIA.md #1/#2/#3/#9/#10 — migration 86)

- **#1 Lista de desejos + alerta de estoque (`concessionaria_wishlists`)**: quando a vitrine não tem
  o carro, a IA registra o interesse via tag `<desejo_carro>` (brand/model — pelo menos um —, teto de
  preço e ano mínimo QUE O CLIENTE declarou). Quando um veículo DISPONÍVEL entra/volta ao estoque e
  casa (ILIKE + teto + ano), o contato é avisado automaticamente (texto fixo) e o desejo desativa
  (ONE-SHOT: `notified_at` + `notified_vehicle_id`). Tela "Desejos"; hooks no create/update/
  updateStatus do veículo.
- **#2 Follow-up de lead parado (`ConcessionariaAutoTransitionJob`)**: lead novo/em_negociacao sem
  movimento há `followup_days` (config, default 3) recebe reengajamento gentil 1x por janela
  (`followup_sent_at` re-arma quando o lead volta a se mover). Sem fechar preço — trava preservada.
- **#3 Lembrete + confirmação de test-drive (`ConcessionariaReminderJob` + tag
  `<confirmacao_testdrive>`)**: test-drive 'agendado' nas próximas 24h recebe "confirma? SIM ou
  CANCELAR" (`reminded_24h`); a resposta muda o status pela tag (BARREIRA DE CONTATO; confirmar só
  de agendado, cancelar de agendado/confirmado — cancelar libera vendedor e veículo na hora).
- **#9 Auto-realizado**: test-drive confirmado com `end_at` passado (2h de graça) vira realizado
  (silencioso; toggle `auto_complete_enabled`).
- **#10 Dashboard comercial (sem DDL)**: GET `/api/concessionaria/reports/summary` + tela
  "Relatórios" — funil de leads (snapshot), conversão na janela, desempenho por vendedor (test-drives
  realizados + leads fechados) e vendas por mês (veículos 'vendido').
- Config ganhou 4 campos (`followup_enabled`/`followup_days`/`testdrive_reminder_enabled`/
  `auto_complete_enabled`) editáveis em Configurações. ADIADOS: reserva com sinal (gateway #50),
  trade-in, campanha/NPS/indicação (motor Onda 3), financiamento (integração), fotos reais (upload),
  multi-loja (fase própria). As tags novas têm namespace próprio; o OutboundService as remove antes
  de enviar (maybeProcessDesejoCarro + maybeProcessConfirmacaoTestDrive).

## A TRAVA (o coração da SM)

A IA SÓ: mostra estoque disponível, agenda test-drive, registra lead. **NUNCA** fecha preço/desconto/
financiamento, **NUNCA** aprova crédito, **NUNCA** simula parcela/juros/score, **NUNCA** inventa
veículo/preço/condição fora do catálogo, **NUNCA** promete entrega/disponibilidade não confirmada,
**NUNCA** muda o status de estoque do veículo nem o status do lead (ações humanas do painel). O preço é
SEMPRE o do catálogo.

## Tags

**`<testdrive_carro>`** (agenda): `{"vehicle_id","salesperson_id","date":"YYYY-MM-DD","start_time":"HH:MM","notes"}`.

**`<lead_carro>`** (registra interesse): `{"vehicle_id","payment_condition":"avista|financiado","notes"}` —
**sem preço** (o backend usa o do catálogo).

Ambas têm namespace próprio, distinto de TODAS as outras. O `OutboundService` remove a tag antes de
enviar.

## O que NÃO existe nesta fase

- **Upload de foto** (a foto do veículo é LINK colado — `photo_url`; bloqueador SERVICE_ROLE_KEY).
- **Financiamento real / simulação de parcela / score / aprovação de crédito** (proibido por trava;
  condição é só uma flag declarativa).
- **FIPE / avaliação de usado / trade-in**; **reserva com sinal/pagamento** (Stripe #50; 'reservado' é
  mudança de status manual); **documentação/emplacamento/transferência**; **VIN/chassi formal**;
  **notificação automática de fechamento do lead**; **multi-loja/pátio**.

## Notas técnicas

- Migration `61_concessionaria.sql` (5 tabelas: config, salespeople, vehicles, test_drives, leads). A
  CHECK de `companies.profile_id` ACRESCENTA `'concessionaria'` preservando os 21 perfis anteriores.
  **Lição de ordenação (atelie/casamento):** 61 entra por ÚLTIMO no `SCRIPTS` do
  `AbstractIntegrationTest` (sua CHECK tem os 22 perfis — a que reescreve a CHECK por último precisa ter
  a lista completa).
- `test_drives`/`leads`: INSERT pelo backend (service_role) — IA via handler OU POST manual; tenant
  SELECT/UPDATE. `vehicles`/`salespeople`/`config`: CRUD do tenant.
- Snapshots: marca/modelo/ano no test-drive; marca/modelo/ano/preço no lead. Alterar/vender o veículo
  depois NÃO altera test-drives/leads passados.
- Contexto da IA via `ConcessionariaContextCache` (Caffeine TTL 30s, keyed por (companyId, contactId) —
  vitrine + vendedores + slots livres por vendedor; NÃO injeta reservado/vendido), invalidado em toda
  mutação.
- Guard `/api/concessionaria/**` → 403 `forbidden_wrong_profile`. Paleta `meia-noite`.
- Tenant de teste: `igorhaf28` (Concessionária Modelo).

## Onda 2 do backlog (migration 115)

`docs/FEATURES_SUGERIDAS_CONCESSIONARIA.md` #5/#7/#12: **#5 trade-in**
(`concessionaria_tradein_offers`): a IA COLETA o usado via `<troca_carro>` (marca/modelo/ano/km/
estado + valor DECLARADO — `asking_cents` não é avaliação; a IA nunca precifica); a equipe avalia
no painel (tela "Trade-in": propor abatimento `offer_cents`, aceitar/recusar). **#7 pós-venda:**
lead FECHADO → parabéns + `review_link` + convite de indicação (toggle ON). **#12 revisão
programada** (opt-in OFF): N meses após o fechamento (`service_reminder_months` default 12) →
convite de revisão/checape, 1 toque por lead (`service_reminded_at`), no
`ConcessionariaReminderJob`. Teste: `ConcessionariaOnda2IntegrationTest`. Fica: #4 reserva com
sinal (gateway), #6 campanha, #8 indicação, #10 dashboard, #11 CMS c/ estoque, #13 financiamento,
#16 fotos (upload).
