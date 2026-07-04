# Perfil Fotografia (estúdio · cinema · audiovisual) — camada 8.16

Guia operacional do tenant **fotografia** (`profile_id='fotografia'`). A IA atende clientes pelo
WhatsApp, AGENDA sessões/coberturas escolhendo um **pacote** do catálogo + um **fotógrafo**, e
ENTREGA, read-only, o **link do material** que o estúdio gravou na sessão. Tom criativo, atencioso e
organizado — sem prometer resultado estético.

É o **25º perfil vertical** (24 + generic). CLONA o chassi de **agenda por profissional** do
**DentalBot/DermatologiaBot** (conflito por profissional + end_at materializado) + um **catálogo de
pacotes** (espelho leve do `aesthetic_procedures` do EsteticaBot, SEM saldo multi-sessão), e inaugura
a **entrega de LINK read-only com prazo materializado**.

## Telas (sidebar "Fotografia")

| Tela | Rota | O que faz |
|------|------|-----------|
| Fotógrafos | `/dashboard/fotografia-professionals` | CRUD (o conflito de agenda da sessão é por profissional). |
| Pacotes | `/dashboard/fotografia-packages` | Catálogo: nome, categoria, duração, valor, **prazo de entrega** (dias). |
| Agenda | `/dashboard/fotografia-appointments` | Sessões por status + escrever o **link do material** + ver o prazo de entrega. |
| Configurações | `/dashboard/fotografia-settings` | Horário + slot (sem duração — vem do pacote). |

## Cliente = contato (sem sub-entidade)

Diferente do Dermatologia (que tem `dermatologia_patients` sub-entidade do contato), aqui o **cliente
é o próprio contato** do WhatsApp (espelho salon/estetica). A sessão **snapshota**
`customer_name`/`customer_phone` do contato no momento — não há tabela de pacientes nem modo
"new_patient" na tag.

## Catálogo de pacotes (espelho leve estetica)

`fotografia_packages`: nome + categoria (texto livre) + `duration_minutes` + `price_cents` +
`delivery_days` + active. A sessão referencia `package_id` e **snapshota** name+price+duration+
delivery_days — alterar o pacote depois NÃO altera sessões já criadas. A **duração da sessão vem do
pacote** (snapshot), não da config. Excluir pacote com sessão → 409 `package_in_use`.

## Agenda (chassi dental/dermatologia)

Conflito **POR `professional_id`** (half-open `NOT (end_at <= newStart OR start_at >= newEnd)`,
re-verificado dentro da transação) → 409 `conflict_slot`; mesmo horário + fotógrafo DIFERENTE → OK
(paralelismo). `end_at` MATERIALIZADO no INSERT (start_at + duration_minutes, em Java — timestamptz +
interval não é IMMUTABLE). Janela `opens_at`..`closes_at` → 400 `outside_hours`.

**Status** `FotografiaAppointmentStatus` ↔ TS (parity, FEMININO):
`agendada → confirmada → realizada → entregue`; `agendada/confirmada → cancelada`; `confirmada →
falta`. A diferença vs Dermatologia (que para em `realizada`): aqui há um estado **`entregue`** (o
material foi entregue). Notifica **confirmada** (com pacote+fotógrafo+data/hora, defensivo) e
**cancelada**; agendada/realizada/entregue/falta silenciosos.

## ESCAPADA — Entrega de LINK read-only + prazo materializado

A sessão tem duas colunas próprias:
- **`delivery_due_date`** (date) — MATERIALIZADA no INSERT = `date(start_at) + delivery_days`. É o
  prazo prometido de entrega; aparece em cada card da Agenda ("entrega até …").
- **`delivery_link`** (text, nullable) — a URL da galeria/material. O estúdio grava DEPOIS da sessão,
  via PATCH `/api/fotografia/sessions/{id}` (campo Material na tela de Agenda). Vazio = nada a entregar.

`<entrega_material>{session_id}` → o `EntregaMaterialHandler` busca a sessão e envia o `delivery_link`
**VERBATIM** via `notifier.sendText` (NÃO passa pela IA — pra não ser reescrito/encurtado), com
**BARREIRA DE CONTATO**: só entrega se o contato da sessão == contato da conversa (impede vazar o link
de outro cliente). Sem link / contato diferente / sessão inexistente → não entrega. Espelho EXATO da
entrega de plano do NutriBot / preparo do DermatologiaBot, mas o link mora **na própria sessão** (não
numa nota de catálogo).

## Tags

**`<sessao_foto>`** (AGENDA — 1 modo, sem new_patient):
```json
{ "professional_id", "package_id", "date":"YYYY-MM-DD", "start_time":"HH:MM", "notes" }
```
**`<entrega_material>`**: `{ "session_id":"UUID" }`.

Ambas têm namespace próprio, distinto de TODAS as outras. A IA DESCARTA qualquer preço que tente
emitir — o backend snapshota o valor do pacote no catálogo.

## O que a IA faz / NÃO faz

- **FAZ:** agenda sessão (pacote + fotógrafo + data/hora), confirma com o prazo de entrega, entrega o
  link quando ele já está na sessão do próprio cliente.
- **NÃO FAZ:** inventar pacote/valor/prazo/fotógrafo fora do catálogo; negociar preço/desconto;
  prometer resultado estético ("vai ficar perfeito"); garantir entrega antes do prazo do pacote;
  inventar link; aceitar/recusar sessão (transição de status é ação humana no painel).

## O que NÃO existe nesta fase

- Upload de foto/material (o material é entregue por **link colado**, não há armazenamento —
  bloqueador SERVICE_ROLE_KEY); saldo multi-sessão / pacote de N sessões (estetica cobre); seleção de
  fotos / galeria com curadoria; contrato/assinatura; sinal/pagamento (Stripe #50); segunda câmera /
  equipe por sessão; orçamento ad-hoc com itens (eventos cobre); scheduler de auto-transição/lembrete
  de prazo de entrega.

## Notas técnicas

- Migration `60_fotografia.sql` (4 tabelas: professionals, packages, config,
  session_appointments). A CHECK ACRESCENTA `'fotografia'` preservando os 24 perfis. Entra por ÚLTIMO
  no `SCRIPTS` do `AbstractIntegrationTest` (sua CHECK tem os 25).
- Base de conhecimento (RAG): disponível como em todo perfil (item "Conhecimento" do nav +
  `{{knowledge}}` do PromptBuilder, sem gate de perfil).
- Guard `/api/fotografia/**` → 403 `forbidden_wrong_profile`. Paleta `carvao`. Tenant: `igorhaf27`.

## Onda 1 do backlog (migration 105)

Entregue a partir de `docs/FEATURES_SUGERIDAS_FOTOGRAFIA.md` (#2, #3, #4-parcial e #5):

- **#2 LEMBRETE D-2/D-1 + CONFIRMAÇÃO:** o `FotografiaReminderJob` (cron
  `${fotografia.reminder-cron:0 50 9 * * *}`) lembra a cliente em D-2 e D-1 (pacote + fotógrafo +
  hora, pedindo confirmação). Markers `reminded2_start_at`/`reminded1_start_at` = start_at
  lembrado — **remarcar REARMA** as duas janelas. A resposta fecha o loop via
  `ConfirmacaoFotografiaHandler` (`<confirmacao_foto>{session_id, decisao:confirmada|cancelada}`,
  barreira de contato, máquina de status validada). Auto-transição: confirmada vencida →
  realizada (silenciosa). Toggles `reminder_enabled`/`auto_complete_enabled` (default ON).
- **#3 ENTREGA NO PRAZO AUTOMATIZADA:** no `delivery_due_date`, sessão `realizada` COM
  `delivery_link` gravado é entregue automaticamente — o link sai **VERBATIM** (mesma garantia do
  `EntregaMaterialHandler`, nunca passa pela IA), a sessão vira `entregue` e um convite
  pós-entrega oferece extras SEM preço (o momento mais quente de compra; toggle
  `post_delivery_upsell_enabled`). Falha de envio deixa a sessão `realizada` pro retry do próximo
  tick. Sem link no prazo, a sessão fica em `realizada` (o estúdio resolve no painel). Toggle
  `auto_deliver_enabled` (default ON).
- **#5 UPSELL CONSULTIVO:** `fotografia_packages.suggestible` (default false) — o contexto da IA
  ganha o bloco "UPSELL CONSULTIVO" com os pacotes marcados (nome + preço DO CATÁLOGO, sem
  pressão/desconto). Checkbox no modal do pacote + badge "sugerível pela IA".
- **#4-parcial POLÍTICA DE CANCELAMENTO comunicada:** `cancellation_policy_hours` (nullable) na
  config — a IA COMUNICA a política ao agendar e em pedidos de cancelamento em cima da hora, mas
  quem decide cobrança/exceção é a equipe. A retenção REAL de sinal fica bloqueada pelo gateway
  (#50), junto com o **#1 sinal/pré-pagamento** inteiro.

Settings ganhou "Automações" (4 toggles) e "Política de cancelamento". Teste:
`FotografiaOnda1IntegrationTest` (lembretes+rearm+confirmação com barreira; auto-complete;
entrega no prazo com link verbatim + convite + toggle).

**Fica pra onda 2** (registrado, não pedido): #1 sinal/pré-pagamento (bloqueado por #50), #6
assinatura de ensaios (chassi E), #7 reativação anual ("faz 1 ano do ensaio"), #8 gift card,
#11 dashboard de ocupação, #14 bloqueio de agenda por fotógrafo, #15 galeria com seleção
(bloqueada por upload).
