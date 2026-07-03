>>> JÁ IMPLEMENTADO — perfil comida, camada 8.4, migration 47_comida.sql. Prompt de nicho RETROATIVO,
>>> formato T5. Fonte: migration 47 (cabeçalho) + docs/PERFIL_COMIDA.md + código profiles/comida/.

[TAREFA — PERFIL COMIDA / Comida (delivery iFood-style) (camada 8.4) — RETROATIVO]

Este documento é a reconstrução RETROATIVA (formato T5) do prompt de nicho que teria originado o perfil
**comida** — o 14º perfil vertical real (15º contando generic), JÁ IMPLEMENTADO na camada 8.4 sob a
migration `47_comida.sql`. Não há nada a executar: o nicho existe, está testado e fechado. O texto
abaixo descreve o REAL (não a intenção), espelhando o estilo de `docs/prompts-nicho/
PROMPT_NICHO_PIZZARIA.md` — que, por sua vez, CLONOU justamente o comida.

[CONTEXTO]
PROJETO MEADA em /home/igorhaf/meada.
Comida é template de nicho pra DELIVERY GENÉRICO DE COMIDA estilo iFood dentro do mesmo dashboard
Meada. O tenant acessa `comida.meadadigital.local` e vê o produto "Comida". A equipe gerencia o
cardápio (itens + opções/adicionais), a IA atende clientes via WhatsApp, conhece o cardápio, MONTA o
pedido NA CONVERSA (carrinho relido do histórico a cada turno, igual sushi), confirma SEMPRE com o
valor total e o endereço de entrega, e avisa que o pedido vai pra CONFIRMAÇÃO DO RESTAURANTE. Tom
ágil e simpático. O restaurante acompanha o fluxo num Kanban com gate de aceite.

>>> TRAVA DE COMPORTAMENTO DA IA (cravada) <<<
- NUNCA inventa item, opção/adicional ou preço que não esteja no cardápio.
- NUNCA aceita NEM recusa o pedido — isso é AÇÃO HUMANA do restaurante no painel (gate de aceite). A
  IA só CONFIRMA o RECEBIMENTO ("seu pedido foi enviado pro restaurante") na própria mensagem e avisa
  que vai pra confirmação do restaurante.
- O total é SEMPRE recalculado pelo sistema — a IA pode somar pra orientar, mas o backend DESCARTA o
  `total_cents` da tag e recalcula a partir do cardápio + deltas das opções (defesa contra chute).
- NÃO cria item de cardápio; NÃO define o total.

EVOLUÇÃO ESTRUTURAL: CLONA o chassi do SUSHI (camada 7.1) — cardápio + carrinho-na-conversa + tag de
pedido + recálculo de total (descarta o total da IA) + snapshot de preço/nome + taxa de entrega/
pedido mínimo + Kanban de status — e adiciona DUAS ESCAPADAS estruturais que o sushi NÃO tem:

  ESCAPADA 1 — GATE DE ACEITE DO RESTAURANTE (ação humana, não da IA): o pedido nasce em
  'aguardando'. O restaurante, no painel, ACEITA (→'em_preparo') ou RECUSA (→'recusado', terminal,
  com motivo opcional em `rejection_reason`). A IA NÃO aceita/recusa — mesmo padrão do "cancelamento
  bloqueado pra IA" do dental e do callNext humano da barbearia. Como a IA já confirmou o RECEBIMENTO
  na própria mensagem ao cliente, o estado 'aguardando' NÃO notifica (evita mensagem duplicada).

  ESCAPADA 2 — ITENS COM OPÇÕES/ADICIONAIS (modifiers): um item do cardápio pode ter grupos de opções
  (Tamanho: M +R$5, G +R$10; Adicionais: bacon +R$3), modelado como sub-entidade
  `comida_menu_item_options` (cada linha = UMA opção de UM grupo, com `price_delta_cents`). No pedido,
  cada item ganha SNAPSHOT das opções escolhidas em TABELA-FILHA `comida_order_item_options` (NÃO
  JSONB — a tabela-filha preserva o histórico mesmo que a opção seja apagada do cardápio depois).
  `unit_price = base + Σ deltas`, RECALCULADO no backend. `option_id` inválido ABORTA a criação do
  pedido (sem pedido parcial).

DECISÕES CRAVADAS (registradas no real):
1. CLONA o chassi do SUSHI (cardápio + carrinho + tag de pedido + total recalculado + taxa/mínimo +
   Kanban + snapshot). MANTER 1:1 onde não conflita.
2. Categorias de comida hardcoded (CHECK + enum Java + const TS + parity test):
   lanches, pizzas, pratos, porcoes, bebidas, sobremesas, combos.
3. ESCAPADA 1 (gate de aceite humano): pedido nasce 'aguardando'; aceite→em_preparo / recusa→recusado
   no painel; a IA não aceita/recusa. 'aguardando' NÃO notifica.
4. ESCAPADA 2 (modifiers): `comida_menu_item_options` (group_label/option_label/price_delta_cents);
   escolha snapshot em TABELA-FILHA `comida_order_item_options` (não JSONB); `unit_price = base + Σ
   deltas`, recalculado; option_id inválido aborta.
5. Snapshot de preço+nome em `comida_order_items`; group/option/delta em `comida_order_item_options`.
   Alterar/excluir item ou opção no cardápio NÃO altera pedidos passados.
6. Tag `<pedido_comida>` (namespace próprio, distinta de todas as outras). Carrega itens + opções; o
   backend recalcula tudo e descarta o total da IA.
7. Pedido criado pelo BACKEND via `PedidoComidaConfirmHandler` (service_role), NÃO pelo SDK do tenant.

NÃO TEM nesta SM (registrado pra não inventar): foto de item/cardápio (bloqueador SERVICE_ROLE_KEY),
pagamento online (Stripe é #50), rastreio de entregador / mapa / ETA dinâmica, avaliação/nota do
pedido, cupom/desconto/promoção, horário de funcionamento próprio do delivery (usa o comercial
genérico), múltiplos endereços salvos (endereço vem na tag, texto livre), regra de min/max de seleção
obrigatória por grupo de opção (cada grupo é livre), edição de pedido depois de criado (só transição
de status), entregador como entidade, taxa por bairro/distância (taxa flat, 1 valor por company),
scheduler de auto-transição de status. Fases futuras.

[FUNDAÇÃO — migration 47_comida.sql]
- ALTER companies CHECK aceitar 'comida' (preservando os 13 perfis anteriores + generic).
- RLS enable+force em todas as tabelas; policies do tenant via `app.company_id()`; grants
  authenticated + service_role. `comida_orders`/`comida_order_items`/`comida_order_item_options`:
  INSERT vem do BACKEND (service_role — pedido criado pela IA via PedidoComidaConfirmHandler); o
  tenant só SELECT/UPDATE (status no Kanban / gate de aceite). Sem policy de INSERT authenticated nos
  pedidos.
- subtotal_cents/total_cents/unit_price_cents MATERIALIZADOS no INSERT; NÃO colunas geradas (o
  recálculo cruza linhas/tabelas — generated não serve; lição das migrations anteriores).
- SNAPSHOTS: preço+nome em `comida_order_items`; group/option/delta em `comida_order_item_options`.
- Tabelas:
  * `comida_config` — taxa de entrega + pedido mínimo (1:1 com company; ausente → taxa/mínimo = 0).
    Colunas `delivery_fee_cents` / `min_order_cents` (>= 0). Clone `sushi_restaurant_config`.
  * `comida_menu_items` — cardápio (id, company_id, name CHECK 1..120, description, price_cents = preço
    BASE >= 0, category CHECK in sync com enum, available default true, timestamps). Só texto (foto
    bloqueada por SERVICE_ROLE_KEY). Index parcial (company_id, category) where available.
  * `comida_menu_item_options` — ESCAPADA 2 (modifiers): id, company_id (denormalizado p/ RLS direta),
    menu_item_id refs comida_menu_items on delete CASCADE, group_label ("Tamanho"/"Adicionais"),
    option_label ("Grande"/"Bacon"), price_delta_cents (>= 0; pode ser 0; NÃO negativo nesta fase),
    available, sort_order, timestamps.
  * `comida_orders` — pedidos. status CHECK ('aguardando'|'em_preparo'|'saiu_entrega'|'entregue'|
    'recusado'|'cancelado') default 'aguardando'; subtotal_cents / delivery_fee_cents / total_cents
    materializados; delivery_address NOT NULL; notes nullable; rejection_reason nullable (ESCAPADA 1);
    conversation_id / contact_id NOT NULL (on delete restrict); created_at + status_updated_at. Clone
    sushi_orders + gate.
  * `comida_order_items` — itens do pedido com snapshot. order_id refs comida_orders on delete
    CASCADE, menu_item_id refs comida_menu_items on delete RESTRICT (item com pedido não hard-deleta →
    409 menu_item_in_use), qtd (> 0), unit_price_cents (JÁ inclui Σ deltas das opções),
    item_name_snapshot.
  * `comida_order_item_options` — ESCAPADA 2: snapshot das opções escolhidas por item de pedido.
    order_item_id refs comida_order_items on delete CASCADE, menu_option_id refs
    comida_menu_item_options on delete SET NULL (a opção pode sumir do cardápio — o snapshot fica),
    group_label_snapshot, option_label_snapshot, price_delta_cents (snapshot do delta no momento).
- Status do pedido hardcoded (`ComidaOrderStatus` enum Java + `comida-order-status.ts` const TS +
  ComidaOrderStatusParityTest):
    aguardando   → em_preparo, recusado
    em_preparo   → saiu_entrega, cancelado
    saiu_entrega → entregue, cancelado
    entregue / recusado / cancelado → terminal
  (aceite = aguardando→em_preparo é o gate humano da ESCAPADA 1.) Notifica ao ENTRAR no status:
  em_preparo ("Seu pedido foi aceito e já entrou em preparo! 🍳"), saiu_entrega ("Seu pedido saiu pra
  entrega. Já já chega aí!"), entregue ("Pedido entregue. Bom apetite e obrigado pela preferência!"),
  recusado (texto defensivo + motivo opcional concatenado pelo Service), cancelado ("Seu pedido foi
  cancelado. Se quiser refazer, é só me chamar."). aguardando é SILENCIOSO (null — a IA já confirmou o
  recebimento).
- Categorias hardcoded (`ComidaCategory.java` + `comida-categories.ts` + ComidaCategoryParityTest):
  lanches, pizzas, pratos, porcoes, bebidas, sobremesas, combos.
- TODAS as tabelas novas entram na migration 47 ANTES de tocar o banco (lição os_config) e no
  TRUNCATE/SCRIPTS do AbstractIntegrationTest.

[BACKEND]
- Menu (cardápio): CRUD de itens + opções (modifiers) — espelho sushi. `ComidaMenuService` +
  `ComidaMenuController` (rota /api/comida/menu). delete de item com pedido → 409 menu_item_in_use (on
  delete restrict no order_item).
- Config: `ComidaConfigRepository` — GET (fallback taxa/mínimo = 0) + PUT.
- Cache do bloco de cardápio injetado no prompt: `ComidaMenuCache` (Caffeine TTL 60s, igual sushi),
  INVALIDADO pelo ComidaMenuService em toda gravação/edição/exclusão de item/opção/config — a IA vê a
  mudança na hora. Sob cada item, o bloco lista os grupos de opção e os option_id EXATOS com deltas (a
  IA precisa deles pra emitir a tag).
- Orders: criados pelo BACKEND via `PedidoComidaConfirmHandler` (não pelo SDK do tenant).
  `ComidaOrderService.create` lê a taxa do `ComidaConfig` e delega ao `ComidaOrderRepository`, que faz
  o recálculo: pra cada item, `unit_price = price_base + Σ price_delta_cents das opções escolhidas`;
  `subtotal = Σ (unit_price × qtd)`; `total = subtotal + delivery_fee`. O `total_cents` da IA é
  DESCARTADO. Opção fantasma (option_id que não existe / de outro item / indisponível) → o repo lança
  `InvalidOptionException` e o pedido NÃO é criado (handler devolve Optional.empty; a mensagem da IA
  segue normal, sem pedido).
- Status (gate humano): PATCH com validação de transição via `ComidaOrderStatus.canTransitionTo`
  (inválida → 409 invalid_status_transition; alvo desconhecido → 400 invalid_status). Aceite =
  aguardando→em_preparo; recusa = aguardando→recusado com rejection_reason opcional (persistido SÓ na
  recusa). Notificação outbound por status via `ComidaOrderNotifier` (texto fixo; best-effort, não
  reverte). A IA não tem rota de aceite/recusa.
- IA:
  * Persona ágil-simpática com a TRAVA DE COMPORTAMENTO embutida (não inventa item/opção/preço, não
    aceita/recusa, total recalculado).
  * Contexto injetado = bloco de cardápio (itens por categoria + opções/modifiers com deltas e
    option_id) + taxa/mínimo + instruções da tag `<pedido_comida>`. Cache TTL 60s (IGNORA
    conversationId — o contexto é o cardápio). Invalidação em toda mutação do cardápio.
  * Tag `<pedido_comida>` (texto livre + regex, DOTALL — NÃO tool calling/responseSchema do Gemini,
    que são mutuamente exclusivos com o responseSchema já usado no outbound):
    `{"items":[{"item_id":"UUID","qtd":N,"options":["UUID_OPCAO",...]}],"endereco":"...",
    "total_cents":0}`. `options` é opcional por item; `total_cents` é IGNORADO (recalculado).
    → `PedidoComidaConfirmHandler.parseAndCreate` valida cada item (existe no cardápio do tenant E
    está disponível; senão Optional.empty), repassa os option_id (validados no repo), cria o pedido. O
    `OutboundService` REMOVE a tag antes de enviar (stripOrderTag).
  * JwtFilter autentica /api/comida/. `OutboundService` ganhou `maybeProcessPedidoComida` (best-effort,
    encadeado com os outros perfis — perfil é único, só um age).
- Guard: `ComidaProfileGuard` — /api/comida/** → 403 forbidden_wrong_profile para tenant de outro
  perfil.

[FRONTEND]
- `/dashboard/comida-menu` — CRUD de itens (nome, descrição, preço, categoria, disponível) + editor de
  opções/adicionais inline de cada item (grupos com delta de preço).
- `/dashboard/comida-orders` — Kanban por status com o GATE DE ACEITE: botões Aceitar/Recusar na
  coluna 'aguardando' (recusa pede motivo opcional) + histórico (entregues/recusados/cancelados). O
  detalhe do pedido mostra os itens com as opções escolhidas e o unit_price.
- `/dashboard/comida-settings` — taxa de entrega (flat) + pedido mínimo.
- types + SDKs (menu, options, orders) em `frontend/profiles/comida/comida-types.ts`.
- Status TS `comida-order-status.ts` + `comida-categories.ts` const + os 2 parity tests (status +
  categorias).
- `getNavForProfile('comida')` injeta "Comida" (3 itens: Cardápio, Pedidos, Configurações).
  Subdomínio `comida.meadadigital.local`. Paleta `terracota`.
- npm build limpo.

[DOCS]
- CLAUDE.md: seção "## Perfil Comida (camada 8.4)" espelhando as seções de perfil — clona o SUSHI e
  inaugura as DUAS ESCAPADAS (gate de aceite humano + itens com opções/modifiers). Documentar:
  categorias próprias; o gate de aceite; a soma de deltas no unit_price; a tag `<pedido_comida>`.
- docs/PERFIL_COMIDA.md: guia operacional (telas; ESCAPADA 1 = gate de aceite com diagrama de status e
  tabela de notificações; ESCAPADA 2 = modifiers + tabela-filha de snapshot; o que a IA faz / NÃO faz;
  formato da tag; o que NÃO existe nesta fase; notas técnicas). EXISTE e é a fonte canônica.
- NÃO mexer em system-template.txt nem em outros perfis.

[TESTES BACKEND]
Suíte por entidade (service + controller integration):
- ComidaOrderStatusParityTest + ComidaCategoryParityTest + ProfileTypeParityTest.
- ComidaMenuServiceTest + ComidaMenuControllerIntegrationTest (CRUD item+opções; invalida cache;
  delete-em-uso → 409 menu_item_in_use; wrongProfile → 403).
- ComidaOrderServiceTest + ComidaOrderControllerIntegrationTest: criação com recálculo (base + Σ
  deltas; total da IA DESCARTADO); subtotal/total com taxa; gate de aceite (aguardando→em_preparo +
  saiu_entrega + entregue + recusado com motivo); transição inválida → 409; a IA não tem endpoint de
  aceite.
- PedidoComidaConfirmHandlerTest: tag com item simples; tag com opções/modifiers; option_id inválido →
  empty (pedido não criado); sem tag → empty; total recalculado bate.
mvn final = contagem REAL do Surefire (não hardcodar; reportar `Tests run: N`).

[CONSTRAINTS DUROS]
- Migration única (47). Sem foto/anexo (bloqueador SERVICE_ROLE_KEY).
- Cliente NÃO é entidade do core — continua o contact (pedido tem conversation_id/contact_id).
- ESCAPADA 1: gate de aceite humano; pedido nasce 'aguardando'; aceite/recusa no painel; a IA NUNCA
  aceita/recusa; 'aguardando' não notifica.
- ESCAPADA 2: modifiers = `comida_menu_item_options` com price_delta (>= 0); escolha snapshot em
  TABELA-FILHA `comida_order_item_options` (não JSONB); `unit_price = base + Σ deltas`, recalculado no
  backend (descarta total da IA); option_id inválido aborta a criação.
- subtotal/total/unit_price materializados (não generated). Snapshots de item e opção.
- Categorias hardcoded (parity). Tag `<pedido_comida>` distinta de TODAS as outras.
- NÃO mexer em outros perfis nem em system-template.txt. Webhook OFF.
- Cache de cardápio TTL 60s + invalidação em toda mutação do cardápio.
- git add EXPLÍCITO (nunca git add .); .env/CONTEXT.md/secrets NUNCA staged. SEED com timestamptz `at
  time zone 'America/Sao_Paulo'`; IDs de namespace com sufixo NOVO. Tabela nova entra na migration
  ANTES de tocar o banco (lição os_config); adicionar as tabelas ao TRUNCATE/SCRIPTS do
  AbstractIntegrationTest.

[PASSO FINAL — resumido]
- Tenant igorhaf16 (Comida Modelo, profile=comida), padrão GoTrue, Caddy + /etc/hosts pra
  comida.meadadigital.local; senha em comunicação direta.
- Seed (NÃO COMITAR; `at time zone 'America/Sao_Paulo'`; ids sufixo novo): config (taxa + mínimo),
  cardápio com itens e grupos de opções (Tamanho/Adicionais), contato vinculado (instance+conversation
  pra smoke de notificação) + contato sem vínculo, pedidos cobrindo estados (aguardando vinculado pra
  smoke do gate de aceite; em_preparo; entregue histórico).
- JwtFilter /api/comida/. OutboundService maybeProcessPedidoComida.
- git add explícito + sanity (sem .env/secrets/CONTEXT) + commit semântico
  (feat(camada-8.4): perfil comida ...) + Co-Authored-By: Claude Opus 4.8. Tag fase-8.4-fechada.
- docker compose restart backend + /admin/me → 401.
- Smoke E2E: auth (role tenant_admin, profileId=comida, productName=Comida); cardápio + guard (GET
  menu, CRUD smoke + invalida cache, delete em uso 409, GET/PUT config, outro perfil → /api/comida →
  403); pedido via `<pedido_comida>` (handler) → 'aguardando' + total = base+deltas+taxa, total da IA
  descartado; option_id inválido → não cria; gate de aceite (aguardando→em_preparo + saiu_entrega +
  entregue + recusado com motivo; transição inválida → 409; IA sem rota de aceite); regressão (perfis
  anteriores intactos); paridade (status + categorias + ProfileType verdes). Cleanup smoke; mvn final
  com contagem REAL.

[REPORTAR]
- "ProfileType.COMIDA adicionado (camada 8.4)"
- "Paridade ComidaOrderStatus, ComidaCategory e ProfileType validadas"
- "Tenant igorhaf16 criado (GoTrue + Caddy/etc/hosts)"
- "ESCAPADA 1: gate de aceite humano (pedido nasce aguardando; restaurante aceita→em_preparo ou
  recusa→recusado com rejection_reason; IA não aceita/recusa; aguardando não notifica)"
- "ESCAPADA 2: itens com opções/adicionais (comida_menu_item_options com price_delta; escolhas
  snapshot em comida_order_item_options tabela-filha; unit_price = base + Σ deltas recalculado;
  option_id inválido aborta)"
- "Total da IA DESCARTADO — recalculado no backend a partir do cardápio + deltas"
- "Tag <pedido_comida> distinta de todas as outras (texto livre + regex, não tool calling)"
- "OutboundService ganhou maybeProcessPedidoComida"
- "getNavForProfile('comida') com branch próprio; paleta terracota"
- "Cache de cardápio TTL 60s + invalidação em toda mutação"
- "tabelas criadas DENTRO da migration (lição os_config); seed com at time zone + sufixo novo"
- mvn final: contagem REAL do Surefire.
