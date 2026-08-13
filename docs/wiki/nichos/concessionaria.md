# AutoBot — regras de negócio (concessionaria, camada 8.17)

[← Catálogo](../05-nichos.md) · Chassi: híbrido TRIPLO (estoque com identidade + agenda A por vendedor + funil D sem itens) · Guia operacional: docs/PERFIL_CONCESSIONARIA.md · Migrations: 61, 86, 115

## O negócio em 3 linhas

O tenant é uma concessionária/revenda de carros. A IA faz as TRÊS coisas do balcão: mostra o ESTOQUE
disponível (veículo é item de estoque com identidade única e ciclo próprio), agenda TEST-DRIVE com
um vendedor e registra LEAD de compra — sem nunca fechar preço, mudar estoque ou mover o funil.

## Jornada no WhatsApp (cenários)

1. Cliente pergunta "tem Corolla?". A IA mostra só veículos `disponivel` + `active` do contexto
   (marca/modelo/ano/km/preço/cor) — reservado/vendido NÃO entram na vitrine.
2. Test-drive: a IA negocia veículo + vendedor + dia/hora e emite `<testdrive_carro>`. O
   `TestDriveConfirmHandler` valida veículo disponível, vendedor ativo, janela e conflito (por
   VENDEDOR, re-checado na transação) e grava **agendado** (silencioso).
3. Interesse de compra: a IA emite `<lead_carro>{vehicle_id, payment_condition}` — o lead nasce
   **novo** com preço SNAPSHOT do catálogo. Pedido de desconto/parcela → "registro seu interesse e o
   vendedor entra em contato" (a IA nunca negocia).
4. Carro não está na vitrine → a IA oferece registrar o DESEJO (`<desejo_carro>`, critérios que o
   CLIENTE declarou). Quando um veículo disponível casar, o alerta sai automático (texto fixo).
5. Troca: a IA COLETA o usado via `<troca_carro>` (marca/modelo obrigatórios + km/estado/valor
   PRETENDIDO declarado) — a avaliação é humana no painel.
6. Véspera do test-drive → "SIM ou CANCELAR"; a resposta muda o status via `<confirmacao_testdrive>`.
7. **Exceções (best-effort):** veículo indisponível, vendedor ocupado, fora de horário, tag
   malformada → nada é criado, warn no log, a resposta segue normal.

## Regras de negócio

### Transacionais (invariantes duras)

- **R1 — Conflito de slot POR `salesperson_id`** (não por veículo): janela half-open, só
  `agendado`/`confirmado` bloqueiam (índice parcial `idx_conc_td_company_sp_active`), RE-VERIFICADO
  dentro da transação → 409 `conflict_slot`. Dois clientes podem test-driveiar o MESMO carro em
  horários distintos; o mesmo VENDEDOR não se sobrepõe.
- **R2 — Test-drive e lead SÓ de veículo `disponivel` E `active`** → 422 `vehicle_not_available`
  (checado no service antes do INSERT).
- **R3 — Preço do lead é SNAPSHOT do catálogo** (`vehicle_price_cents`): a tag NÃO carrega preço; o
  backend sempre lê `vehicles.price_cents` no momento do lead.
- **R4 — `end_at` materializado em Java** no INSERT (`start_at + duration_minutes` do config,
  snapshot); test-drive inteiro dentro de `opens_at`..`closes_at` → 400 `outside_hours`.
- **R5 — Ciclo de estoque é AÇÃO HUMANA:** `disponivel → reservado ⇄ … → vendido` (vendido
  terminal, `VehicleStatus.allowedNext`); a IA nunca toca o status do veículo nem do lead.
- **R6 — Snapshots congelados:** marca/modelo/ano no test-drive; marca/modelo/ano/preço no lead;
  `customer_name`/`customer_phone` — vender/alterar o veículo depois não altera o histórico.
- **R7 — Wishlist exige critério** (CHECK `brand is not null or model is not null`); match =
  brand/model ILIKE + `price_cents ≤ max_price_cents` + `model_year ≥ min_year`, disparado nos hooks
  de create/update/updateStatus do veículo; **ONE-SHOT** (`notified_at` + `notified_vehicle_id`
  desativam o desejo — registrar de novo é permitido).
- **R8 — INSERT de test-drive/lead só pelo backend** (IA ou POST manual do tenant via Spring);
  tenant SELECT/UPDATE via RLS. Trade-in é service_role puro (sem policies).
- **R9 — Exclusão em uso bloqueada** (FK restrict) → 409 `vehicle_in_use`/`salesperson_in_use`;
  arquivar (`active = false`) é o caminho.

### Máquina de status

```
VEÍCULO: disponivel ⇄ reservado          TEST-DRIVE: agendado → confirmado → realizado      LEAD: novo → em_negociacao → fechado
              │          │                              │            │→ no_show                     │            └→ perdido
              └──→ vendido ←┘ (terminal)                └→ cancelado ←┘ (3 terminais)               └──→ perdido    (2 terminais)
```

| Transição | Quem pode | Notifica o cliente? |
|---|---|---|
| test-drive (criação) → agendado | IA (`<testdrive_carro>`) ou tenant | não |
| agendado → confirmado | humano; IA via `<confirmacao_testdrive>` (só de agendado) | **sim** (veículo+vendedor+data/hora) |
| agendado/confirmado → cancelado | humano; IA via `<confirmacao_testdrive>` (libera vendedor e veículo) | **sim** |
| confirmado → realizado | humano; sistema (`auto_complete_enabled`, folga 2h) | não |
| confirmado → no_show | humano apenas | não |
| lead (criação) → novo | IA (`<lead_carro>`) ou tenant | não |
| novo → em_negociacao / perdido; em_negociacao → fechado / perdido | **humano apenas** (funil) | não (fechado dispara o pós-venda da onda 2) |
| veículo: qualquer mudança de status | **humano apenas** | não (mas volta a `disponivel` dispara match de wishlist) |

Transição fora dos diagramas → 409 `invalid_status_transition`. Obs.: `novo → fechado` direto é proibido.

### O que a IA PODE × NUNCA faz (travas da persona)

- **PODE:** mostrar estoque disponível, agendar test-drive, registrar lead (com a condição avista/
  financiado DECLARADA), registrar desejo e trade-in com dados declarados, refletir SIM/CANCELAR do
  lembrete.
- **NUNCA** (`ProfilePromptContext.CONCESSIONARIA`): fecha preço/desconto, negocia condição, aprova
  crédito, simula parcela/juros/score; inventa veículo/preço/ano/km/opcional fora do catálogo;
  oferece reservado/vendido; promete entrega/documentação/disponibilidade ("isso o vendedor
  confirma"); garante que o carro estará disponível; muda status de veículo/lead; promete quando o
  carro desejado chega; avalia ou promete valor pelo usado do trade-in.

### Tags de IA

| Tag | Quando a IA emite | Campos | O que o backend descarta/recalcula |
|---|---|---|---|
| `<testdrive_carro>` | cliente confirmou veículo+vendedor+dia/hora | `vehicle_id`, `salesperson_id`, `date`, `start_time`, `notes` | duração do config (snapshot); disponibilidade/janela/conflito revalidados |
| `<lead_carro>` | cliente declarou interesse de compra | `vehicle_id`, `payment_condition` (avista\|financiado), `notes` | **sem preço na tag** — snapshot do catálogo; veículo disponível revalidado |
| `<desejo_carro>` | vitrine não tem o carro procurado | `brand`/`model` (≥1), `max_price_cents`, `min_year`, `notes` | critérios são os DECLARADOS; sem marca E modelo → não registra |
| `<confirmacao_testdrive>` | cliente respondeu ao lembrete 24h | `testdrive_id`, `decisao` (confirmado\|cancelado) | BARREIRA DE CONTATO; confirmar só de agendado; máquina valida |
| `<troca_carro>` | cliente ofereceu usado na troca | `used_brand`, `used_model` (obrigatórios), `used_year/km/condition`, `asking_cents`, `interest_vehicle_id` | `asking_cents` é valor DECLARADO (não avaliação); proposta nasce `aberta` |

Todas por regex, removidas pelo `OutboundService` antes do envio; falha → `Optional.empty()` + warn.

### Validações e erros

| reason | HTTP | Significado de negócio | Cenário que dispara |
|---|---|---|---|
| `forbidden_wrong_profile` | 403 | tenant não é concessionaria | `/api/concessionaria/**` com outro perfil |
| `conflict_slot` | 409 | vendedor ocupado no horário | POST manual sobrepondo test-drive ativo |
| `vehicle_not_available` | 422 | veículo não está disponivel+ativo | test-drive/lead de carro reservado/vendido |
| `inactive_salesperson` | 422 | vendedor arquivado | agendar com `active = false` |
| `outside_hours` | 400 | fora da janela opens/closes | 17h45 com fechamento 18h e duração 45min |
| `invalid_status_transition` | 409 | transição proibida (veículo/test-drive/lead) | vendido → disponivel; novo → fechado |
| `invalid_payment_condition` | 400 | condição fora de avista/financiado | POST de lead com "consorcio" |
| `invalid_wishlist` | 400 | desejo sem marca E sem modelo | POST do painel sem critério |
| `vehicle_in_use` / `salesperson_in_use` | 409 | tem test-drives/leads (FK restrict) | DELETE com histórico |
| `*_not_found` (vehicle/salesperson/testdrive/lead/wishlist/tradein/contact) | 404 | inexistente ou de outro tenant | id errado |
| `invalid_status` / `invalid_date` / `invalid_time` / `invalid_hours` | 400 | entrada malformada | status/data/config inválida |

### Notificações ao cliente

- **Envia:** test-drive `confirmado` (veículo+vendedor+data/hora) e `cancelado`; alerta de wishlist
  (texto fixo com carro+preço, NÃO passa pela IA); lembrete 24h ("SIM ou CANCELAR"); follow-up de
  lead parado (reengajamento SEM fechar preço); pós-venda no lead `fechado` (onda 2).
- **Silêncio:** `agendado` (combinado no chat), `realizado`, `no_show`; TODO o funil de lead
  (mudança de status não notifica — a negociação é do vendedor); mudanças de estoque do veículo;
  registros sem `conversation_id` (marcados sem envio).
- Best-effort (`ConcessionariaTestDriveNotifier`): falha de envio nunca reverte status.

## Dados e snapshots

- **`concessionaria_vehicles`** — estoque da LOJA (≠ `os_vehicles` da oficina, que é do cliente):
  brand 1–80, model 1–120, `model_year` 1900–2100, `mileage_km ≥ 0`, `price_cents ≥ 0`, status
  CHECK (disponivel/reservado/vendido), `active`, `photo_url` (LINK colado), `plate` sem UNIQUE.
- **`concessionaria_test_drives`** — snapshots veículo/cliente/duração; `end_at` materializado;
  `reminded_24h` (onda 1). **`concessionaria_leads`** — snapshots + `vehicle_price_cents`;
  `lost_reason`; `salesperson_id` atribuível (set null); `followup_sent_at`; `service_reminded_at`.
- **`concessionaria_wishlists`** — CHECK de critério mínimo; `max_price_cents > 0`; `min_year ≥ 1950`;
  one-shot. **`concessionaria_tradein_offers`** — status CHECK (aberta/avaliada/aceita/recusada),
  gestão LIVRE no painel (sem máquina rígida); `asking_cents` declarado × `offer_cents` humano.
- **`concessionaria_config`** (1:1) — duração 15–240 (45), opens/closes (09:00/18:00) + toggles das
  ondas. Ausência de linha = defaults.
- **Cache:** `ConcessionariaContextCache` (Caffeine, **TTL 30s**, max 1000, keyed por
  company+contato) — vitrine (só disponíveis) + vendedores + slots; invalidado em toda mutação.

## Features de onda (backlog implementado)

**Onda 1 (migration 86)** — `ConcessionariaReminderJob` (fixedDelay 5min) + `ConcessionariaAutoTransitionJob` (cron `0 25 * * * *`):

- **#1 Wishlist + alerta de estoque** (regra R7). **#2 Follow-up de lead parado:** `novo`/
  `em_negociacao` sem movimento há `followup_days` (default 3) → 1 toque por janela
  (`followup_sent_at` re-arma quando `status_updated_at` avança). Toggle `followup_enabled` (ON).
- **#3 Lembrete de test-drive:** `agendado` nas próximas 24h → "SIM ou CANCELAR" (`reminded_24h`,
  1 por test-drive). Toggle `testdrive_reminder_enabled` (ON). **#9 Auto-realizado:** `confirmado`
  com `end_at` passado há 2h+ (`GRACE_HOURS`) → `realizado` silencioso. Toggle
  `auto_complete_enabled` (ON). **#10 Dashboard comercial** (sem DDL): `GET
  /api/concessionaria/reports/summary`.

**Onda 2 (migration 115):**

- **#5 Trade-in** (tag `<troca_carro>` + tela no painel; avaliação humana). **#7 Pós-venda:** lead →
  `fechado` encadeia agradecimento + `review_link` (se configurado) + convite de indicação — toggle
  `post_sale_enabled` (**ON**). **#12 Revisão programada** (opt-in `service_reminder_enabled`
  **OFF** — disparo à base): `service_reminder_months` (default 12, CHECK 1–36) após o fechamento →
  1 convite por lead (`service_reminded_at`).

## O que NÃO existe (limites honestos)

- Financiamento real/simulação/score/aprovação de crédito (a condição é FLAG declarativa), FIPE/
  avaliação automática de usado (trade-in é coleta + avaliação humana), reserva com sinal/pagamento
  (Stripe #50 — `reservado` é status manual).
- Upload de foto (link colado), VIN/chassi formal, documentação/emplacamento, multi-loja/pátio.
- Notificação automática de mudança de status do lead (inclusive `perdido`), campanha/NPS/indicação
  em massa, CMS com vitrine de estoque.
