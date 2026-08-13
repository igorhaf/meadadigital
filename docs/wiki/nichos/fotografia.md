# Fotografia — regras de negócio (fotografia, camada 8.16)

[← Catálogo](../05-nichos.md) · Chassi: A (agenda por profissional) + F (entrega read-only) · Guia operacional: docs/PERFIL_FOTOGRAFIA.md · Migrations: 60, 105

## O negócio em 3 linhas

O tenant é um estúdio de fotografia/audiovisual que vende **pacotes** (ensaio, evento, vídeo — preço, duração e **prazo de entrega em dias** por pacote). O cliente final é o contato do WhatsApp (sem sub-entidade de paciente). A IA agenda a sessão (pacote + fotógrafo + data/hora), informa o prazo de entrega e, depois, **entrega o link da galeria** que o estúdio gravou na sessão — verbatim, nunca reescrito.

## Jornada no WhatsApp (cenários)

1. Cliente pede um ensaio → a IA mostra pacotes e fotógrafos do contexto injetado e slots livres (14 dias) → na confirmação emite `<sessao_foto>` → `SessaoFotoConfirmHandler` cria a sessão `agendada`, snapshotando nome/preço/duração/prazo do pacote e nome do fotógrafo; a tag é removida antes do envio.
2. A equipe **confirma no painel** → notificação com pacote + fotógrafo + data/hora. Em D-2 e D-1 o `FotografiaReminderJob` lembra pedindo confirmação; a resposta do cliente vira `<confirmacao_foto>` (confirmada|cancelada, com barreira de contato).
3. Sessão acontece → `confirmada` vencida vira `realizada` automaticamente (silencioso, toggle). O estúdio cola o `delivery_link` via `PATCH /api/fotografia/appointments/{id}`.
4. No `delivery_due_date`, sessão `realizada` COM link é entregue automaticamente: o link sai **verbatim**, a sessão vira `entregue` e sai um convite de extras SEM preço (toggle). O cliente também pode pedir o material na conversa → `<entrega_material>` entrega o link da **própria** sessão.
- **Exceções:** horário ocupado do fotógrafo → conflito (a IA oferece outro slot do mesmo profissional); fora da janela → sessão não criada; pacote/fotógrafo inativo → não criada; sessão sem link ou de outro contato → material NÃO entregue (silêncio + warn); falha de envio na entrega automática → sessão fica `realizada` para retry no próximo tick.

## Regras de negócio

### Transacionais (invariantes duras)

- **R1 — Conflito por fotógrafo:** duas sessões ativas (`agendada`/`confirmada`) do MESMO `professional_id` não se sobrepõem (janela half-open `NOT (end_at <= :s OR start_at >= :e)`, re-verificada DENTRO da transação do INSERT em `FotografiaAppointmentRepository`) → 409 `conflict_slot`. Mesmo horário com fotógrafo diferente → OK.
- **R2 — Duração/preço/prazo vêm do pacote, nunca da IA:** qualquer preço emitido na tag é DESCARTADO; o backend snapshota `price_cents`/`duration_minutes`/`delivery_days` do catálogo no INSERT.
- **R3 — `end_at` e `delivery_due_date` materializados em Java** no INSERT (`start_at + duration` e `date(start_at) + delivery_days`) — nunca coluna GENERATED (timestamptz+interval não é IMMUTABLE).
- **R4 — Janela de funcionamento:** a sessão INTEIRA cabe em `opens_at..closes_at` no fuso America/Sao_Paulo → senão 400 `outside_hours`.
- **R5 — Entrega só ao dono:** o link só sai se `contact_id` da sessão == contato da conversa (barreira em `EntregaMaterialHandler`); e só se `delivery_link` não for vazio.
- **R6 — Catálogo protegido:** excluir pacote/fotógrafo com sessão → 409 `package_in_use`/`professional_in_use` (FK `on delete restrict`).

### Máquina de status

```
agendada ──→ confirmada ──→ realizada ──→ entregue (terminal)
   │              │
   └─→ cancelada ←┘         confirmada ──→ falta (terminal)
       (terminal)
```

| Transição | Quem pode | Notifica? |
|---|---|---|
| agendada → confirmada | humano no painel OU IA via `<confirmacao_foto>` (decisão do cliente) | Sim (pacote+fotógrafo+data/hora) |
| agendada/confirmada → cancelada | humano OU IA via `<confirmacao_foto>` | Sim (texto defensivo) |
| confirmada → realizada | humano OU sistema (`FotografiaReminderJob`, `end_at` vencido) | Não |
| confirmada → falta | humano | Não |
| realizada → entregue | humano OU sistema (entrega automática no prazo) | Só o próprio link (verbatim) + convite de extras |

### O que a IA PODE × NUNCA faz (travas da persona)

- **PODE:** agendar sessão (pacote+fotógrafo+data/hora do catálogo), avisar o prazo de entrega, comunicar a política de cancelamento, entregar o link já gravado da sessão do próprio cliente, sugerir pacotes `suggestible` (nome+preço do catálogo).
- **NUNCA:** inventa pacote/valor/prazo/fotógrafo/link; negocia preço ou desconto; promete resultado estético ("vai ficar perfeito") ou entrega antes do prazo; transiciona status por conta própria (só reflete a decisão do cliente na confirmação/cancelamento).

### Tags de IA

| Tag | Quando emite | Campos | Backend descarta/recalcula |
|---|---|---|---|
| `<sessao_foto>` | cliente confirma o agendamento | `professional_id`, `package_id`, `date`, `start_time`, `notes` | preço/duração/prazo → snapshot do catálogo |
| `<entrega_material>` | cliente pede o material E a sessão tem link | `session_id` | conteúdo: o link sai VERBATIM da sessão (a IA nunca gera) |
| `<confirmacao_foto>` | cliente responde ao lembrete D-2/D-1 | `session_id`, `decisao: confirmada\|cancelada` | máquina de status valida; barreira de contato |

Handlers best-effort: falha → `Optional.empty()`/`false` + warn; a mensagem segue sem artefato. `OutboundService` remove a tag antes de enviar.

### Validações e erros

| reason | HTTP | Significado | Dispara em |
|---|---|---|---|
| `forbidden_wrong_profile` | 403 | tenant de outro perfil | qualquer `/api/fotografia/**` |
| `conflict_slot` | 409 | fotógrafo ocupado (payload traz o conflito) | POST manual; via tag → sessão não criada |
| `outside_hours` | 400 | sessão fora da janela | POST manual; via tag → não criada |
| `inactive_professional` / `inactive_package` | 400 | recurso arquivado | criação |
| `professional_not_found` / `package_not_found` / `session_not_found` | 404 | id inexistente no tenant | criação / detail / PATCH |
| `invalid_status` / `invalid_status_transition` | 400 / 409 | status desconhecido / transição proibida | PATCH status |
| `package_in_use` / `professional_in_use` | 409 | exclusão com sessão vinculada | DELETE |
| `invalid_date` | 400 | ISO-8601 inválido | filtros e create |

### Notificações ao cliente

- **Envia:** `confirmada` (com pacote, fotógrafo, data/hora), `cancelada` (defensivo, "para remarcar é só me chamar"), lembretes D-2/D-1, entrega automática (link verbatim + convite de extras sem preço).
- **Silêncio:** `agendada` (a IA já confirmou na conversa), `realizada`, `entregue` manual, `falta`; POST manual do painel (sem `conversation_id`) nunca notifica — não há canal.

## Dados e snapshots

| Tabela | Constraints-regra | Snapshots |
|---|---|---|
| `fotografia_professionals` | name 1–200; delete restrito em uso | — |
| `fotografia_packages` | duration 15–1440; price ≥ 0; `delivery_days` ≥ 0; `suggestible` default false | — |
| `fotografia_config` | slot 5–240; `cancellation_policy_hours` null ou 1–720; 4 toggles default ON | — |
| `fotografia_session_appointments` | CHECK de 6 status; `end_at`/`delivery_due_date` NOT NULL materializados; `delivery_link` nullable; markers `reminded2/1_start_at` | `customer_name/phone`, `professional_name`, `package_name`, `price_cents`, `duration_minutes`, `delivery_days` |

Cache: `FotografiaContextCache` (Caffeine, TTL **20s**, keyed `(companyId, contactId)`, max 1000) — fotógrafos, pacotes, sessões do contato (indica que HÁ material, **sem** despejar o link) e slots livres de 14 dias. Invalidado por company em toda mutação de profissional/pacote/sessão/config.

## Features de onda (migration 105)

- **Lembrete D-2/D-1 + confirmação** (`reminder_enabled`, ON): `FotografiaReminderJob` (cron `${fotografia.reminder-cron:0 50 9 * * *}`); idempotência por marker = `start_at` lembrado → **remarcar REARMA** as duas janelas.
- **Auto-complete** (`auto_complete_enabled`, ON): `confirmada` com `end_at < now()` → `realizada`, silencioso.
- **Entrega no prazo** (`auto_deliver_enabled`, ON): no `delivery_due_date`, `realizada` COM link → envia o link verbatim e SÓ DEPOIS persiste `entregue` (falha de envio deixa `realizada` pro retry). Sem link, nada acontece (o painel destaca o atraso).
- **Convite pós-entrega** (`post_delivery_upsell_enabled`, ON): extras SEM preço no momento da entrega.
- **Upsell consultivo:** `fotografia_packages.suggestible` (default OFF) libera o bloco "UPSELL CONSULTIVO" no contexto — nome+preço do catálogo, sem pressão.
- **Política de cancelamento comunicada:** `cancellation_policy_hours` (nullable) — a IA COMUNICA; cobrança/exceção é decisão humana (retenção real bloqueada pelo gateway #50).

## O que NÃO existe (limites honestos)

Upload de foto/material (link colado — bloqueador SERVICE_ROLE_KEY); galeria com seleção/curadoria; saldo multi-sessão (estetica cobre); contrato/assinatura; sinal/pagamento e retenção de sinal (Stripe #50); segunda câmera/equipe por sessão; orçamento ad-hoc com itens (eventos cobre); bloqueio de agenda por fotógrafo; lembrete de prazo de entrega ao ESTÚDIO (o job entrega, não cobra).
