# EsteticaBot — regras de negócio (estetica, camada 8.3)

[← Catálogo](../05-nichos.md) · Chassi: A (agenda por profissional) + pacote com saldo (escapada) · Guia operacional: docs/PERFIL_ESTETICA.md · Migrations: 46, 108

## O negócio em 3 linhas

O tenant é uma clínica de estética (facial/corporal, drenagem, laser): profissionais + procedimentos
com duração e **preço por sessão obrigatório**. O cliente compra **pacotes multi-sessão** (saldo
pré-pago) e agenda sessões pelo WhatsApp — a IA agenda consumindo o saldo, captura intenção de
compra de pacote, e opera sob a **trava estética**: nunca opina sobre corpo ou promete resultado.

## Jornada no WhatsApp (cenários)

1. **Compra de pacote:** cliente quer 10 sessões de drenagem → a IA emite
   `<compra_pacote>{procedure_id, total_sessions}`. O pacote nasce **pendente** (silencioso), com
   `total_cents = sessões × unit_price` do catálogo — o preço NUNCA vem da tag. A clínica confirma o
   pagamento no painel (pendente → **ativo**) e o cliente é notificado.
2. **Sessão do pacote:** cliente com pacote ativo agenda → `<agendamento_estetica>` com
   `package_id`. Na MESMA transação do INSERT: conflito por profissional re-verificado + **1 sessão
   consumida** por UPDATE condicional. Zerar o saldo muta o pacote para **esgotado** automaticamente.
3. **Sessão avulsa:** sem `package_id` — não mexe em saldo. (O POST manual do painel passa
   `contactId` nulo, então qualquer `packageId` cai em 403 `package_wrong_contact` — na prática o
   manual é sempre avulso, como diz o guia.)
4. **Lembrete de véspera (onda 1):** "Sua sessão é AMANHÃ... Confirma? SIM ou NÃO". A resposta vira
   `<confirmacao_estetica>`; o **NÃO cancela e DEVOLVE a sessão ao pacote** (esgotado reabre p/ ativo).
5. **Exceções:** pacote esgotado → 409 `package_exhausted` (inclusive na corrida entre pré-validação
   e consumo); pacote de outro cliente → 403 `package_wrong_contact`; pendente/expirado/cancelado →
   400 `package_not_active`; slot ocupado → 409 `conflict_slot`; fora da janela → 400 `outside_hours`.

## Regras de negócio

### Transacionais (invariantes duras)

- **R1 — Consumo de sessão com corrida fechada no banco:** UPDATE condicional
  `where status='ativo' and sessions_remaining > 0` (used+1, remaining re-derivado, zera → status
  'esgotado' na MESMA sentença), executado DENTRO da transação do agendamento; 0 linhas → o INSERT
  aborta com 409 `package_exhausted` (`AestheticPackageRepository.consumeSession`).
- **R2 — Devolução idempotente ao cancelar:** cancelamento de agendamento com
  `consumed_session=true` devolve 1 sessão na mesma transação (used−1; `esgotado` reabre p/ `ativo`)
  e zera `consumed_session` para nunca devolver duas vezes.
- **R3 — Saldo e total MATERIALIZADOS em Java/SQL explícito:** `sessions_remaining = total − used`
  (CHECKs `>= 0` e `used <= total`) e `total_cents = total_sessions × unit_price` — nunca coluna
  gerada; a IA não inventa preço (snapshot `unit_price_cents` do procedimento no pacote).
- **R4 — Conflito por profissional:** half-open re-verificado na transação → 409 `conflict_slot`;
  índice parcial só em `agendado`/`confirmado`. `end_at` materializado no INSERT.
- **R5 — Só pacote ATIVO do MESMO contato consome:** pré-validação amigável no service (wrong
  contact → 403; esgotado → 409; não-ativo → 400) + re-check transacional do R1.
- **R6 — `esgotado` não é transição manual:** o PATCH do tenant só aceita pendente→ativo/cancelado e
  ativo→expirado/cancelado (`AestheticPackageStatus.allowedNext`); esgotar e reabrir são do sistema.
- **R7 — Validade materializada na ATIVAÇÃO:** com `package_validity_days` configurado (CHECK
  7..1095), `valid_until = hoje + N dias` é gravada em Java ao ativar (editável no painel); job
  expira `ativo` vencido.
- **R8 — Ficha 1:1 e imutável pós-cancelamento:** `aesthetic_session_notes` UNIQUE por
  `appointment_id`; editar ficha de agendamento cancelado → 409 `appointment_cancelled`.

### Máquina de status

```
PACOTE:   pendente ──→ ativo ──→ esgotado ⇄ (reabre ao devolver)      SESSÃO:  agendado ──→ confirmado ──→ realizado
             │           │────→ expirado                                          │              │─────────→ falta
             └───────────┴────→ cancelado                                         └──────────────┴─────────→ cancelado
```

| Transição | Quem pode | Notifica? |
|---|---|---|
| → pendente (compra) | IA (`<compra_pacote>`) ou painel | não |
| pendente → ativo | **só painel** (confirma pagamento) | **sim** ("pacote ativo, é só agendar") |
| ativo → esgotado / esgotado → ativo | **só sistema** (consumo/devolução transacional) | não |
| ativo → expirado | painel ou sistema (job `valid_until`) | não |
| pendente/ativo → cancelado | só painel | não |
| sessão agendado → confirmado | painel · IA via `<confirmacao_estetica>` | **sim** (data/hora/profissional) |
| sessão → cancelado | painel · IA via tag (devolve a sessão) | **sim** |
| confirmado → realizado | painel · sistema (auto-complete vencido) | não |
| confirmado → falta | só painel | não |

### O que a IA PODE × NUNCA faz (travas da persona)

**PODE:** agendar sessões (com ou sem pacote, usando o `package_id` do contexto), capturar a
intenção de compra, informar procedimentos/preços do catálogo e o saldo dos pacotes do cliente.
**NUNCA** (`ProfilePromptContext.ESTETICA` — trava cravada): indica/recomenda procedimento ("a
profissional vai te avaliar"); opina sobre corpo/aparência/"o que o cliente precisa"; promete
resultado ("vai sumir", "fica perfeito"); **confirma pagamento de pacote** (só a clínica ativa);
inventa preço/condição; discute contraindicação ou condição de saúde (→ avaliação presencial).

### Tags de IA

| Tag | Quando a IA emite | Campos | Backend descarta/recalcula |
|---|---|---|---|
| `<agendamento_estetica>` | confirmação da sessão | `professional_id`, `procedure_id`, `package_id?`, `date`, `start_time`, `notes` | duração/nome via snapshot do catálogo; consumo de saldo é transacional, nunca "da tag" |
| `<compra_pacote>` | cliente fecha a intenção de compra | `procedure_id`, `total_sessions`, `notes` | preço TOTAL recalculado do catálogo (`unit_price` snapshot); nasce `pendente` |
| `<confirmacao_estetica>` | resposta ao lembrete / pedido de desmarcar | `appointment_id`, `decisao: confirmado\|cancelado` | BARREIRA DE CONTATO; cancelar devolve a sessão pela mecânica do updateStatus |

### Validações e erros

| reason | HTTP | Significado | Cenário |
|---|---|---|---|
| `forbidden_wrong_profile` | 403 | guard `/api/estetica/**` | tenant de outro perfil |
| `conflict_slot` | 409 | profissional ocupado | overlap na transação |
| `package_exhausted` | 409 | saldo zero (ou corrida perdida) | agendar num pacote esgotado |
| `package_wrong_contact` | 403 | pacote de outro cliente (ou POST manual com pacote) | barreira de titularidade |
| `package_not_active` | 400 | pacote pendente/expirado/cancelado | agendar antes da ativação |
| `package_not_found` / `procedure_not_found` / `professional_not_found` / `appointment_not_found` / `note_not_found` | 404 | id inexistente | — |
| `invalid_sessions` | 400 | total_sessions inválido (≤ 0) | compra de pacote |
| `inactive_professional` / `inactive_procedure` | 400 | entidade desativada | — |
| `outside_hours` | 400 | fora da janela | início/fim fora de opens/closes |
| `appointment_cancelled` | 409 | ficha de sessão cancelada | editar `session_note` |
| `professional_in_use` / `procedure_in_use` | 409 | DELETE com vínculos | preferir desativar |
| `invalid_status` / `invalid_status_transition` | 400 / 409 | máquina de status (sessão E pacote) | ex.: setar `esgotado` à mão |
| `invalid_date` / `invalid_hours` / `invalid_time` | 400 | params/config malformados | — |

### Notificações ao cliente

Envia (texto fixo, defensivo): **pacote ativo** (boas-vindas com procedimento e nº de sessões),
sessão **confirmada** e **cancelada**, lembrete de véspera (SIM/NÃO) e a régua de renovação
(opt-in). Silencioso: pacote pendente/esgotado/expirado/cancelado, sessão agendada, realizada
(auto-complete idem) e falta — nada de cobrança ou "sermão" automático.

## Dados e snapshots

- `aesthetic_procedures` — `unit_price_cents NOT NULL` (≥ 0; base do total do pacote — diferencial
  vs. salon, onde preço é opcional), `duration_minutes` CHECK 15..480.
- `aesthetic_packages` — snapshots `customer_name/phone`, `procedure_name`, `unit_price_cents`;
  `total_sessions > 0`, `sessions_used ≥ 0`, `sessions_used <= total_sessions`,
  `sessions_remaining ≥ 0`; `activated_at` preenchido uma vez (coalesce); onda 1: `valid_until`,
  `renewal_reminded_at`. INSERT só backend; tenant SELECT/UPDATE.
- `aesthetic_appointments` — `package_id` nullable (null = avulso), `consumed_session` boolean,
  snapshots `procedure_name`/`professional_name`/`duration_minutes`, `end_at` materializado; onda 1:
  `reminded_start_at`.
- `aesthetic_session_notes` — 1:1 com o agendamento (UNIQUE), texto administrativo (área tratada,
  parâmetros de aparelho, observações), SEM foto, NÃO é prontuário.
- `aesthetic_config` — defaults 09:00/19:00/slot 30 (CHECK 5..240) + toggles da onda.
- **Cache:** `EsteticaContextCache` Caffeine TTL **20s**, key por contato (inclui os PACOTES ATIVOS
  com saldo do cliente), invalidado por company em toda mutação (agenda, pacote, catálogo, config).

## Features de onda (backlog implementado — migration 108)

- **#1/#2 Lembrete + confirmação:** `EsteticaReminderJob` cron `${estetica.reminder-cron:0 50 10 * * *}`,
  varre `agendado`/`confirmado` de amanhã; marker `reminded_start_at` (remarcar REARMA); toggle
  `reminder_enabled` ON. O NÃO cancela e devolve saldo (R2).
- **#4 Auto-transições:** confirmado com `end_at` vencido → realizado (silencioso,
  `auto_complete_enabled` ON) e pacote ATIVO com `valid_until` < hoje → EXPIRADO
  (`auto_expire_enabled` ON).
- **#3 Régua de renovação** (`renewal_enabled` **OFF por default** — lição Baileys): pacote ESGOTADO
  há `renewal_days` (7..365, default 30) sem pacote novo do contato, OU ATIVO a vencer em
  `expiry_warning_days` (1..60, default 7) → 1 toque por pacote (`renewal_reminded_at`); a resposta
  cai no fluxo `<compra_pacote>` existente.

## O que NÃO existe (limites honestos)

Foto antes/depois (bloqueador SERVICE_ROLE_KEY), anamnese/prontuário estruturado (dado sensível —
fase futura com cripto), pagamento real do pacote (a ativação é confirmação MANUAL; Stripe é #50),
assinatura/recorrência, comissão de profissional, estoque de produtos, NPS/fidelidade/cupom/encaixe
(onda 2 registrada, não pedida). A IA não ativa pacote nem confirma pagamento em hipótese alguma.
