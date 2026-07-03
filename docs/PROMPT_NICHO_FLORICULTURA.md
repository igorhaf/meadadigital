>>> JÁ IMPLEMENTADO — perfil floricultura, camada 8.5, migration 49_floricultura.sql. Prompt de nicho RETROATIVO, formato T5. Fonte: migration 49 (cabeçalho) + código profiles/floricultura/ + docs.

[TAREFA — PERFIL FLORICULTURA / Floricultura (loja de flores, entrega agendada) (camada 8.5) — RETROATIVO]

Este documento reconstrói, no formato T5, o "prompt de nicho" que teria gerado o perfil floricultura JÁ
IMPLEMENTADO no projeto Meada (em /home/igorhaf/meada). NÃO é trabalho a
executar — é a fotografia retroativa do que o código, a migration e os testes realmente contêm. O
perfil floricultura é o DÉCIMO SEXTO perfil vertical real (16º contando generic), fechado no commit
553ce1d, e virou o CHASSI dos perfis agendados que vieram depois (a padaria 8.8 evoluiu dele).

[CONTEXTO]
PROJETO MEADA — SaaS multi-empresa de atendimento via WhatsApp com IA, monolito que se
apresenta como N produtos verticais ("perfis"). Cada perfil é hardcoded (enum Java
`ProfileType` ↔ const TS `profile-type.ts`, com `ProfileTypeParityTest` falhando o build se
divergirem); NÃO existe tabela de perfis. Tenant tem EXATAMENTE 1 perfil (`companies.profile_id`,
CHECK constraint). Todos os perfis coexistem HARMONICAMENTE — feature de um nunca quebra outro.

O tenant floricultura (`profile_id='floricultura'`) vira um produto de LOJA DE FLORES / DELIVERY DE
FLORES: gerencia o catálogo (buquês, arranjos, cestas, plantas, coroas, acessórios), a IA atende
clientes via WhatsApp em linguagem livre, MONTA o carrinho NA CONVERSA (relido do histórico a cada
turno, sem entidade de carrinho), e — porque flor é PRESENTE — coleta a DATA da entrega, a faixa do
dia, o NOME de quem RECEBE e o texto do cartãozinho. Confirma SEMPRE com o valor total e avisa que o
pedido vai para a CONFIRMAÇÃO da floricultura. Subdomínio `floricultura.meadadigital.local`,
productName "Floricultura", paleta `rosa-po`.

>>> TRAVA DE COMPORTAMENTO DA IA (cravada) <<<
- A IA NUNCA inventa item, opção/adicional ou preço fora do catálogo cadastrado.
- A IA NUNCA ACEITA NEM RECUSA o pedido — isso é AÇÃO HUMANA da floricultura no painel (gate de
  aceite). A IA só CONFIRMA o RECEBIMENTO ("seu pedido foi enviado para a floricultura") na própria
  mensagem; por isso o estado inicial 'aguardando' NÃO dispara notificação.
- O total é SEMPRE recalculado pelo backend a partir do catálogo (preço base + Σ deltas das opções);
  o `total_cents` que a IA emite na tag é DESCARTADO (defesa contra a IA chutar valor).
- A IA NUNCA promete uma data de entrega no passado — a `data_entrega` é validada `>= hoje` no fuso
  America/Sao_Paulo; data anterior aborta a criação do pedido (a mensagem segue, sem pedido).

EVOLUÇÃO ESTRUTURAL: o floricultura CLONA o chassi do COMIDA (camada 8.4) — cardápio + OPÇÕES/
adicionais (modifiers) + carrinho-na-conversa + tag de pedido em texto livre + recálculo de total
(descarta o total da IA) + snapshot de preço/nome + taxa de entrega / pedido mínimo + Kanban de
status + GATE DE ACEITE HUMANO (o pedido nasce 'aguardando'; a loja ACEITA → 'em_preparo' ou RECUSA
→ 'recusado', terminal, com `rejection_reason` opcional). Sobre esse chassi, inaugura UMA ESCAPADA
ESTRUTURAL nova que o comida NÃO tem:

  ESCAPADA — ENTREGA AGENDADA (flor é presente para OUTRA pessoa): diferente do comida (entrega
  "para já", para o próprio comprador), o pedido de flor carrega quatro campos do agendamento-presente:
    * `delivery_date` (DATE) — o DIA da entrega, validado `>= hoje` no fuso America/Sao_Paulo.
    * `delivery_period` — a FAIXA do dia (`manha` / `tarde`), hardcoded em `FloriculturaPeriod`
      (enum Java ↔ const TS, `FloriculturaPeriodParityTest`). É faixa, NÃO slot por minuto
      (over-engineering deliberado — a entrega de flor não precisa de horário fechado).
    * `recipient_name` — QUEM RECEBE, que é DIFERENTE do contato/comprador da conversa. Obrigatório.
    * `card_message` — o texto do cartãozinho ("Feliz aniversário, Ana"), nullable (pode haver
      entrega sem cartão).

DECISÕES CRAVADAS (as reais, lidas do código):
1. CLONA o chassi do COMIDA (cardápio + modifiers + carrinho + tag + total recalculado + taxa/mínimo
   + Kanban + gate de aceite humano). Manter onde não conflita.
2. Categorias hardcoded próprias (CHECK em sync com `FloriculturaCategory.java` + `floricultura-
   categories.ts`, `FloriculturaCategoryParityTest`): buques, arranjos, cestas, plantas, coroas,
   acessorios.
3. ESCAPADA — entrega AGENDADA: `delivery_date` (>= hoje validado, fuso America/Sao_Paulo) +
   `delivery_period` (manha/tarde, `FloriculturaPeriod` hardcoded com parity) + `recipient_name`
   (obrigatório, ≠ comprador) + `card_message` (nullable). Os três primeiros são NOT NULL na tabela;
   o backend valida data e período antes de criar.
4. Gate de aceite humano (ESCAPADA herdada do comida): o pedido nasce 'aguardando'; aceite (→
   'em_preparo') e recusa (→ 'recusado' com `rejection_reason`) são AÇÕES no painel. A IA não
   aceita/recusa. 'aguardando' NÃO notifica (a IA já confirmou o recebimento na mensagem).
5. Opções/adicionais (modifiers) por item via `floricultura_catalog_item_options` (cada linha = UMA
   opção de UM grupo, com `price_delta_cents` >= 0; delta negativo não nesta fase). `unit_price` =
   base + Σ deltas, recalculado no backend; `option_id` inválido aborta a criação.
6. Tag `<pedido_flor>` (namespace próprio, distinta de `<pedido_comida>` e de todas as outras).
   Carrega `endereco`, `data_entrega`, `periodo`, `destinatario`, `cartao` (opcional), `items[]` (cada
   um com `item_id`, `qtd`, `options[]`) e `total_cents` (DESCARTADO).
7. Cliente NÃO é entidade do core — continua o `contact`; o pedido tem `conversation_id`/`contact_id`
   (NOT NULL) e o destinatário é texto livre (não vira entidade).

[FUNDAÇÃO — migration 49_floricultura.sql]
- ALTER `public.companies` CHECK aceitar 'floricultura', PRESERVANDO os 15 perfis anteriores (a lista
  do CHECK fica: generic, legal, dental, sushi, restaurant, salon, pousada, academia, pet, oficina,
  nutri, barbearia, eventos, estetica, comida, floricultura). Lição de clonagem por sed cravada: o
  CHECK ACRESCENTA, nunca substitui.
- RLS enable + force em TODAS as tabelas; policies do tenant via `app.company_id()`; grants
  authenticated + service_role. `floricultura_orders`/`floricultura_order_items`/
  `floricultura_order_item_options`: INSERT vem só do BACKEND (service_role) — o pedido é criado pela
  IA via `PedidoFlorConfirmHandler`, não pelo SDK do tenant; o tenant só SELECT/UPDATE (status pelo
  Kanban / gate de aceite). `floricultura_orders` NÃO tem policy de insert para `authenticated`.
- `subtotal_cents`/`total_cents`/`unit_price_cents` MATERIALIZADOS no INSERT; NÃO colunas geradas (o
  recálculo cruza linhas/tabelas — generated não serve; lição das migrations anteriores).
- SNAPSHOTS: preço + nome em `floricultura_order_items`; group/option/delta em
  `floricultura_order_item_options`. Alterar/excluir um item OU uma opção no catálogo NÃO altera
  pedidos passados.
- Tabelas:
  * `floricultura_config` — `delivery_fee_cents` + `min_order_cents` (ambos >= 0, default 0), 1:1 com
    company (PK = company_id, on delete cascade). Ausente → taxa/mínimo = 0. Clone comida_config.
  * `floricultura_catalog_items` — catálogo (só texto; foto bloqueada por SERVICE_ROLE_KEY ausente).
    `name` CHECK 1..120, `description`, `price_cents` (>= 0, é o preço BASE sem opções), `category`
    CHECK in (buques/arranjos/cestas/plantas/coroas/acessorios) em sync com o enum, `available`
    default true. Índice parcial `(company_id, category) where available`.
  * `floricultura_catalog_item_options` — modifiers (ESCAPADA do comida): sub-entidade do item. Cada
    linha = UMA opção de UM grupo. `catalog_item_id` (FK → floricultura_catalog_items, on delete
    cascade), `company_id` denormalizado para RLS direta, `group_label` (1..60, ex.: "Tamanho",
    "Adicionais"), `option_label` (1..80), `price_delta_cents` (>= 0, pode ser 0), `available`,
    `sort_order`.
  * `floricultura_orders` — pedidos. `status` CHECK in
    (aguardando/em_preparo/saiu_entrega/entregue/recusado/cancelado) default 'aguardando';
    `subtotal_cents`/`delivery_fee_cents`/`total_cents` materializados; `delivery_address` NOT NULL;
    `notes`; `rejection_reason` nullable (gate de aceite). ESCAPADA (NOT NULL salvo cartão):
    `delivery_date` DATE (>= hoje validado no backend), `delivery_period` CHECK in (manha/tarde),
    `recipient_name` (quem recebe ≠ comprador), `card_message` nullable. `conversation_id`/
    `contact_id` NOT NULL. `created_at` + `status_updated_at`.
  * `floricultura_order_items` — itens do pedido com snapshot. `catalog_item_id` (FK on delete
    restrict → item com pedido não pode ser hard-deletado → 409 catalog_item_in_use), `qtd` (> 0),
    `unit_price_cents` (JÁ inclui Σ deltas das opções, snapshot), `item_name_snapshot`.
  * `floricultura_order_item_options` — ESCAPADA modifiers: snapshot das opções escolhidas por item de
    pedido. `order_item_id` (FK on delete cascade), `catalog_option_id` (FK on delete SET NULL — a
    opção pode sumir do catálogo depois; os snapshots permanecem), `group_label_snapshot`,
    `option_label_snapshot`, `price_delta_cents` (snapshot do delta no momento).
- Categorias hardcoded (`FloriculturaCategory.java` ↔ `floricultura-categories.ts`,
  `FloriculturaCategoryParityTest`): buques, arranjos, cestas, plantas, coroas, acessorios.
- Status hardcoded (`FloriculturaOrderStatus` ↔ `floricultura-order-status.ts`,
  `FloriculturaOrderStatusParityTest`). Funil real:
    aguardando   → em_preparo, recusado
    em_preparo   → saiu_entrega, cancelado
    saiu_entrega → entregue, cancelado
    entregue / recusado / cancelado → terminal
  (aceite = aguardando → em_preparo é o gate humano.) Transição inválida → 409
  invalid_status_transition. Notifica (texto fixo defensivo): em_preparo (aceito), saiu_entrega,
  entregue, recusado (o `rejection_reason` é concatenado pelo Service, não pelo enum). 'aguardando'
  NÃO notifica.
- Período hardcoded (`FloriculturaPeriod` ↔ `floricultura-period.ts`, `FloriculturaPeriodParityTest`):
  manha ("Manhã (8h–12h)"), tarde ("Tarde (13h–18h)").
- Lição cravada: colunas FK do snapshot de opção chamam `catalog_item_id` / `catalog_option_id`
  (NÃO `menu_*`) — o floricultura usa o vocabulário "catalog", não "menu".

[BACKEND]
- Catálogo (`floricultura/catalog/`): `FloriculturaCatalogService` + Controller + Repositories
  (`FloriculturaCatalogItemRepository`, `FloriculturaCatalogOptionRepository`). CRUD de itens (com
  categoria + preço base) e de opções (modifiers). Delete de item com pedido → 409
  catalog_item_in_use (FK on delete restrict).
- Config (`FloriculturaConfig` + `FloriculturaConfigRepository`): GET com fallback (taxa/mínimo = 0
  se ausente) + PUT.
- Cache do bloco de catálogo injetado no prompt: `FloriculturaCatalogCache` (Caffeine, TTL 60s,
  keyed por company, IGNORA conversationId — o contexto é o catálogo). Monta o bloco com os UUIDs
  e option_ids EXATOS (a IA precisa deles para emitir a tag `<pedido_flor>`) + as instruções da tag.
  INVALIDADO pelo `FloriculturaCatalogService` a cada gravação/edição/exclusão (a IA vê a mudança na
  hora).
- Pedidos (`floricultura/orders/`): `FloriculturaOrderService` cria o pedido (INSERT via
  service_role), recalcula `unit_price = base + Σ deltas` × qtd, `subtotal = Σ itens`, `total =
  subtotal + delivery_fee`. Aplica pedido mínimo (espelho comida). `option_id` fantasma →
  `InvalidOptionException` (aborta, sem pedido parcial). PATCH de status valida transição (409
  invalid_status_transition) e implementa o gate de aceite (aguardando→em_preparo = aceitar;
  aguardando→recusado = recusar com `rejection_reason`). `FloriculturaOrderNotifier` dispara a
  notificação outbound por status (texto defensivo de `FloriculturaOrderStatus.notificationText()`).
- `PedidoFlorConfirmHandler` (clone do `OrderConfirmHandler` do sushi/comida + a ESCAPADA): regex
  `<pedido_flor>\s*(\{.*?\})\s*</pedido_flor>` (DOTALL). NÃO usa tool calling / responseSchema do
  Gemini (a API trata os dois como mutuamente exclusivos e o fluxo outbound já usa responseSchema). O
  handler:
    * exige `endereco` (não-branco) — senão empty;
    * parseia `data_entrega` (LocalDate), valida `>= hoje` no fuso America/Sao_Paulo — passado/inválida
      → empty;
    * valida `periodo` ∈ FloriculturaPeriod — inválido → empty;
    * exige `destinatario` (não-branco) — senão empty; `cartao` é opcional;
    * valida cada item contra o catálogo (existe + disponível), parseia `options[]` (UUIDs);
    * chama `orderService.create(...)`; o `total_cents` da tag é DESCARTADO (o repo recalcula);
    * `stripOrderTag` remove a tag do texto antes de enviar ao cliente.
  Qualquer falha (JSON inválido, item fantasma, opção fantasma) → `Optional.empty()` e a mensagem da
  IA segue normal, sem pedido.
- `OutboundService` ganhou `maybeProcessPedidoFlor` (best-effort, encadeado após os outros perfis —
  perfil é único, só um age; remove a tag antes de enviar).
- `FloriculturaProfileGuard` (403 forbidden_wrong_profile). O `JwtAuthenticationFilter` autentica
  `/api/floricultura/**` (além dos perfis anteriores).
- Persona FLORICULTURA: tom acolhedor/afetivo de floricultura, com a TRAVA embutida (não inventa
  item/opção/preço, não aceita/recusa, total recalculado, sempre coleta data + período + destinatário
  + cartão para presente).

[FRONTEND]
- `/dashboard/floricultura-catalog` — CRUD de itens (com categoria + preço base) e editor de opções/
  modifiers inline.
- `/dashboard/floricultura-orders` — Kanban por status com o GATE DE ACEITE: Aceitar / Recusar na
  coluna 'aguardando' (recusa pede motivo → `rejection_reason`); o detalhe do pedido mostra a entrega
  agendada (data + período), o destinatário e o texto do cartão, além das opções escolhidas por item.
- `/dashboard/floricultura-settings` — taxa de entrega + pedido mínimo.
- SDKs em `frontend/lib/api/floricultura/` (catalog.ts, config.ts, orders.ts) + types em
  `frontend/profiles/floricultura/` (floricultura-types.ts).
- Consts/parity TS: `floricultura-categories.ts`, `floricultura-order-status.ts`,
  `floricultura-period.ts`.
- `getNavForProfile('floricultura')` injeta o grupo "Floricultura" (Catálogo / Pedidos /
  Configurações), no mesmo padrão dos demais perfis. Subdomínio `floricultura.meadadigital.local`,
  paleta `rosa-po`.

[DOCS]
- CLAUDE.md: o perfil floricultura aparece na seção de catálogo de perfis (16º perfil, camada 8.5),
  registrando a entrega agendada + cartão + destinatário e a regra de clonagem por sed (o CHECK
  acrescenta, preservando todos os perfis).
- (Nota retroativa: `docs/PERFIL_FLORICULTURA.md` — guia operacional do tenant — NÃO existe no
  filesystem hoje; se for produzido, deve espelhar os guias dos outros perfis: catálogo, pedidos +
  Kanban + gate de aceite, entrega agendada com data/período/destinatário/cartão, como a IA atende e
  "o que a IA NÃO faz".)

[TESTES BACKEND]
Suíte real do floricultura (em `src/test/java/com/meada/profiles/floricultura/`):
- `FloriculturaCategoryParityTest` — categorias Java ↔ TS.
- `FloriculturaOrderStatusParityTest` — status Java ↔ TS (funil + notificationText).
- `FloriculturaPeriodParityTest` — período (manha/tarde) Java ↔ TS.
- `FloriculturaCatalogServiceTest` + `FloriculturaCatalogControllerIntegrationTest` — CRUD item +
  opções, invalidação de cache, delete-em-uso, wrongProfile.
- `FloriculturaOrderServiceTest` + `FloriculturaOrderControllerIntegrationTest` — criação com
  recálculo de total (descarta total da IA), opções, snapshots, gate de aceite, transições de status.
- `ProfileTypeParityTest` cobre o enum global com 'floricultura'.
(A contagem do Surefire na sessão de fechamento foi mvn 831 verde — valor histórico; a verdade é
sempre o `Tests run: N` do Surefire na execução corrente.)

[CONSTRAINTS DUROS]
- Migration única (49). Sem foto/anexo (bloqueador SERVICE_ROLE_KEY ausente).
- Cliente NÃO é entidade do core — continua o `contact` (pedido tem conversation_id/contact_id); o
  destinatário é texto livre, não entidade.
- ESCAPADA: entrega AGENDADA — `delivery_date` (>= hoje, fuso America/Sao_Paulo) + `delivery_period`
  (manha/tarde hardcoded) + `recipient_name` (obrigatório) + `card_message` (nullable).
- Gate de aceite humano: o pedido nasce 'aguardando'; aceite/recusa no painel; a IA NUNCA aceita/
  recusa. 'aguardando' não notifica.
- `subtotal`/`total`/`unit_price` materializados (NÃO generated). Snapshots de item e de opção; FK do
  snapshot de opção é `on delete set null` (preserva histórico).
- Total da IA SEMPRE descartado (backend recalcula a partir do catálogo). `option_id` inválido aborta.
- Categorias, status e período hardcoded com parity test Java↔TS. Tag `<pedido_flor>` distinta de
  TODAS as outras (namespace próprio).
- Cache de catálogo TTL 60s + invalidação em toda mutação do catálogo.
- O CHECK de `companies.profile_id` ACRESCENTA 'floricultura' preservando os 15 perfis anteriores
  (nunca substituir — lição de clonagem por sed). Colunas FK chamam `catalog_item_id`/
  `catalog_option_id` (NÃO `menu_*`).
- NÃO mexer em outros perfis nem em system-template.txt. Webhook permanece OFF.

[PASSO FINAL — resumido (histórico)]
Tenant de floricultura provisionado no Supabase (GoTrue) + Caddy/etc/hosts para
`floricultura.meadadigital.local`; seed com `at time zone 'America/Sao_Paulo'`; tabelas DENTRO da
migration (lição os_config) e no TRUNCATE do `AbstractIntegrationTest`; `git add` explícito (sem
.env/CONTEXT.md/secrets); commit `feat(camada-8.5): perfil floricultura (delivery de flores) …`
(553ce1d) + push; smoke E2E provando: catálogo + guard 403, montagem do pedido com data/período/
destinatário/cartão, recálculo de total (total da IA descartado), gate de aceite humano (aguardando→
em_preparo / →recusado com motivo) e funil até entregue.

[REPORTAR]
- "ProfileType.FLORICULTURA adicionado (camada 8.5) — 16º perfil, CHECK preserva os anteriores".
- "Paridade FloriculturaCategory, FloriculturaOrderStatus, FloriculturaPeriod e ProfileType
  validadas".
- "ESCAPADA: entrega AGENDADA — data (>= hoje) + período (manha/tarde) + destinatário + cartão".
- "Gate de aceite humano: a IA confirma recebimento; a floricultura aceita/recusa no painel".
- "Tag `<pedido_flor>` distinta de `<pedido_comida>` e das demais; total da IA descartado".
- "OutboundService ganhou maybeProcessPedidoFlor; JwtFilter autentica /api/floricultura/**".
- "getNavForProfile('floricultura') com branch próprio; subdomínio floricultura.meadadigital.local,
  paleta rosa-po".
- "Cache de catálogo TTL 60s + invalidação em toda mutação".
- "Colunas FK catalog_item_id/catalog_option_id (não menu_*); tabelas dentro da migration 49".
- PENDÊNCIAS/NÃO TEM: foto/imagem do arranjo, escolha de slot por horário fino (é dia + faixa),
  pagamento real (Stripe #50), destinatário como entidade, fidelidade/cupom, integração externa
  (iFood etc.).
