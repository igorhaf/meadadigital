>>> JÁ IMPLEMENTADO — perfil sushi, camada 7.1, migration 30_sushi.sql. Este é o prompt de nicho
>>> RETROATIVO (documentação do que já existe no código), formato T5. Fonte: CLAUDE.md seção
>>> Perfil Sushi + migration 30 + docs/PERFIL_SUSHI.md.

[TAREFA — PERFIL SUSHI / SushiBot (camada 7.1) — RETROATIVO]

[CONTEXTO]
PROJETO MEADA em /home/igorhaf/meada.
SushiBot é o PRIMEIRO perfil vertical real do projeto, logo depois da fundação multi-perfil
(SM-A). É o ALICERCE que todos os perfis order-based posteriores (comida, floricultura, pizzaria…)
clonaram. Migration 30_sushi.sql; perfil `profile_id='sushi'`; quarto id do enum (5º contando
generic). Documentação retroativa: descreve o que REALMENTE está no código, não um brief a executar.

Sushi é template de nicho pra RESTAURANTE DE SUSHI / DELIVERY dentro do dashboard Meada. O tenant
acessa o produto "Sushi" e vê cardápio + Kanban de pedidos. A IA atende clientes via WhatsApp em
LINGUAGEM LIVRE, conhece o cardápio (entradas, hot rolls, sashimi, combinados, bebidas, sobremesas),
MONTA o pedido NA CONVERSA (carrinho relido do histórico a cada turno — sem entidade de carrinho) e,
na confirmação COM endereço, fecha o pedido com o resumo (itens, total com taxa, endereço). Tom
descontraído mas profissional, ágil e simpático.

>>> TRAVA DE COMPORTAMENTO DA IA (cravada) <
- NUNCA inventa item, combinação ou preço que não esteja no cardápio — usa os item_id EXATOS do
  bloco de cardápio injetado no prompt.
- O total é SEMPRE recalculado pelo sistema — o `total_cents` que a IA manda na tag é DESCARTADO; o
  backend recalcula a partir do cardápio (defesa contra a IA chutar total).
- A IA só emite a tag de pedido na CONFIRMAÇÃO FINAL **E** com o endereço informado — NUNCA enquanto
  o cliente ainda monta o carrinho.
- A IA não mostra códigos internos (item_id, a própria tag) ao cliente: o OutboundService REMOVE a
  tag antes de enviar; o cliente só vê a confirmação humana.
- Pedido mínimo: a IA AVISA se o pedido ficar abaixo do mínimo, mas NÃO recusa — apenas orienta.

EVOLUÇÃO ESTRUTURAL / escapada (o que o SushiBot INAUGUROU no projeto): foi o primeiro perfil
order-based, e cravou o padrão que virou chassi reutilizável:
  - CARRINHO-NA-CONVERSA SEM ENTIDADE PRÓPRIA: o carrinho não é tabela. A IA relê o histórico da
    conversa a cada turno e monta o pedido no texto; só quando o cliente confirma E dá o endereço é
    que vira `sushi_orders` + `sushi_order_items`. Esta é a escapada de design que todos os perfis
    de pedido herdaram.
  - TAG EM TEXTO LIVRE, NÃO TOOL CALLING: a IA emite `<pedido>{...}</pedido>` no fim da mensagem de
    confirmação. NÃO usa tool calling / responseSchema do Gemini — a Gemini API trata tool calling e
    responseSchema como mutuamente exclusivos, e o fluxo de outbound já usa responseSchema
    (needs_human/intent). O `OrderConfirmHandler` parseia a tag via regex.
  - RECÁLCULO DE TOTAL NO BACKEND + SNAPSHOT: o backend descarta o total da IA e recalcula pelo
    cardápio; `sushi_order_items` guarda snapshot de preço + nome do momento — alterar/excluir item
    no cardápio NÃO altera pedidos passados.

DECISÕES CRAVADAS (o que está implementado):
1. Pedido criado pelo BACKEND (service_role) via `OrderConfirmHandler` — NÃO pelo SDK do tenant. O
   tenant só faz SELECT/UPDATE (status no Kanban). Não há POST de criar pedido.
2. Categorias hardcoded e materializadas (CHECK no banco + enum Java + const TS + parity test),
   espelho 1:1: entradas, hot_rolls, sashimi, combinados, bebidas, sobremesas.
3. Status do pedido hardcoded e materializado (enum Java ↔ const TS, parity test):
   recebido → preparo → saiu_pra_entrega → entregue; cancelado é terminal alternativo (de qualquer
   não-terminal). Transição inválida → 409 invalid_status_transition.
4. Cache do bloco de cardápio (Caffeine TTL 60s por company), INVALIDADO explicitamente em toda
   gravação/edição/exclusão de item ou config — a IA vê a mudança na hora.
5. Cardápio é SÓ TEXTO (foto bloqueada por SERVICE_ROLE_KEY ausente — ver RISKS.md).
6. Tag `<pedido>` (o namespace original; os perfis posteriores criaram tags próprias —
   `<pedido_comida>`, `<pedido_pizza>` etc. — distintas desta).

NÃO TEM nesta camada (registrado pra não inventar): tela de configuração de taxa/mínimo (hoje é
semeado no banco; CRUD de config veio depois), tela de carrinho (vive na conversa), gate de aceite
humano (pedido nasce 'recebido', não 'aguardando' — o gate de aceite é escapada do COMIDA, posterior),
modifiers/adicionais/opções (escapada do comida; aqui o item é plano: preço único), endereço com
mapa/CEP (texto livre), foto do cardápio (bloqueador SERVICE_ROLE_KEY), scheduler de auto-transição
de status, personalização dos textos de notificação (fixos), pagamento real (Stripe é #50).

[FUNDAÇÃO — migration 30_sushi]
- ALTER companies CHECK aceitando 'sushi' (feito na fundação multi-perfil SM-A; aqui só as tabelas).
- Convenções: RLS enable+force em todas; policies do tenant via app.company_id(); grants
  authenticated + service_role. updated_at mantido pelos repositórios (não há trigger genérico).
- subtotal_cents/total_cents/unit_price_cents MATERIALIZADOS no INSERT — NÃO colunas geradas (o
  recálculo cruza linhas/tabelas).
- Tabelas:
  * sushi_menu_items — cardápio. (id, company_id refs companies on delete restrict, name CHECK
    1..120, description nullable, price_cents CHECK >=0, category CHECK in
    ('entradas','hot_rolls','sashimi','combinados','bebidas','sobremesas') — em sync com o enum,
    available default true, timestamps). Índice parcial (company_id, category) WHERE available=true.
    Só texto (sem foto).
  * sushi_restaurant_config — taxa de entrega + pedido mínimo (1:1 com company; PK company_id on
    delete cascade). delivery_fee_cents/min_order_cents default 0 CHECK >=0. Ausente → taxa/mínimo
    = 0 (fallback no service).
  * sushi_orders — pedidos. (id, company_id, conversation_id NOT NULL refs conversations,
    contact_id NOT NULL refs contacts, status default 'recebido' CHECK in
    ('recebido','preparo','saiu_pra_entrega','entregue','cancelado'), subtotal_cents NOT NULL,
    delivery_fee_cents default 0, total_cents NOT NULL, delivery_address NOT NULL, notes nullable,
    created_at, status_updated_at). INSERT só pelo backend (NÃO há policy de insert pra
    authenticated — só SELECT/UPDATE). Índices (company,status,created_at desc) e (conversation).
  * sushi_order_items — itens do pedido. (id, order_id refs sushi_orders on delete cascade,
    menu_item_id refs sushi_menu_items on delete restrict, qtd CHECK >0, unit_price_cents,
    item_name_snapshot NOT NULL). unit_price_cents + item_name_snapshot são SNAPSHOTS do momento do
    pedido. Tenant SELECT só via join no order da própria empresa; INSERT só service_role.

[BACKEND]
Pacote: src/main/java/com/meada/profiles/sushi/.
- Menu (cardápio): SushiMenuService + SushiMenuController + SushiMenuItem(Repository). CRUD de itens.
  Cada gravação/edição/exclusão chama SushiMenuCache.invalidate(companyId). delete de item com
  pedido apontando (FK restrict no sushi_order_items) → 409 menu_item_in_use. Audita
  sushi_menu_item_deleted.
- Config: SushiRestaurantConfig(Repository) — taxa de entrega + pedido mínimo (fallback 0 se
  ausente).
- SushiMenuCache: bloco de cardápio+config+instruções de pedido já formatado, cacheado por company
  (Caffeine TTL 60s, maximumSize 500). Conteúdo = só itens available=true agrupados por categoria,
  com item_id (a IA precisa pra emitir a tag) + taxa/mínimo + INSTRUÇÕES DE PEDIDO (formato da tag).
- IA / persona: ProfilePromptContext.SUSHI ("Você é atendente de um restaurante de sushi. Tom
  descontraído mas profissional. Conheça o cardápio, sugira combinações e harmonizações, e confirme
  o pedido sempre com o valor total e o endereço de entrega. Seja ágil e simpático."). O segmentFor
  do sushi anexa `persona + sushiMenuCache.menuSegment(companyId)` — IGNORA o conversationId (o
  contexto é o cardápio, não a conversa).
- Tag/handler: OrderConfirmHandler extrai `<pedido>{...}</pedido>` (regex DOTALL), valida cada item
  contra o cardápio do tenant (existe E available), exige `endereco` não-vazio e `items` não-vazio.
  Formato real da tag: `<pedido>{"items":[{"item_id":"UUID","qtd":N}],"endereco":"...",
  "total_cents":NNN}</pedido>`. O total_cents é DESCARTADO. item_id inexistente/indisponível,
  não-UUID, JSON inválido, sem endereço ou sem items → Optional.empty() (a mensagem da IA segue
  normal, sem pedido — loga warn). hasOrderTag/stripOrderTag pro OutboundService remover a tag antes
  de enviar.
- SushiOrderService.create: lê a taxa do config, delega ao repositório que faz snapshot de
  preço+nome e RECALCULA os totais (ignora o total da IA). updateStatus: valida o alvo (enum,
  InvalidStatusException → 400 invalid_status) e a transição (canTransitionTo →
  InvalidStatusTransitionException → 409), persiste e dispara a notificação outbound do novo status
  (SushiOrderNotifier, best-effort).
- Status (SushiOrderStatus): transições recebido→{preparo,cancelado}; preparo→{saiu_pra_entrega,
  cancelado}; saiu_pra_entrega→{entregue,cancelado}; entregue/cancelado terminais. notificationText()
  com texto fixo por status; preparo/saiu_pra_entrega/entregue/cancelado notificam; recebido NÃO
  notifica (a confirmação do pedido já foi a mensagem da IA).
- Controller (SushiOrderController): TENANT + perfil sushi only. GET /api/sushi/orders (filtro
  status + paginação), GET /api/sushi/orders/{id}, PATCH /api/sushi/orders/{id}/status. NÃO há POST
  de criar pedido (vem da IA).
- Guard: SushiProfileGuard.requireSushi(user) lê company.profile_id via CompanyProfileRepository;
  perfil != 'sushi' ou sem empresa → WrongProfileException → 403 forbidden_wrong_profile. O
  JwtAuthenticationFilter autentica /api/sushi/** (tenant), além de /admin/**.

[FRONTEND]
- /dashboard/sushi-menu — CRUD de itens do cardápio (nome, descrição opcional, preço em R$,
  categoria; checkbox available liga/desliga; excluir bloqueado se em uso). frontend/app/(protected)/
  dashboard/sushi-menu/page.tsx.
- /dashboard/sushi-orders — Kanban de pedidos por status (Recebido → Em preparo → Saiu pra entrega),
  botão "avançar" pro próximo status (notifica o cliente), cancelar disponível em não-finalizado,
  aba de histórico (entregue/cancelado), auto-refresh ~30s. page.tsx.
- types + SDKs: frontend/profiles/sushi/sushi-types.ts, sushi-categories.ts; frontend/lib/api/sushi/
  menu.ts, orders.ts.
- getNavForProfile('sushi') injeta o grupo "Restaurante" (Cardápio + Pedidos); outros perfis não
  veem. Perfil em profile-type.ts: { id:'sushi', productName:'Sushi', subdomain:'sushi',
  defaultPaletteId:'tijolo' }.

[DOCS]
- CLAUDE.md: seção "## Perfil Sushi (SushiBot, camada 7.1)".
- docs/PERFIL_SUSHI.md: guia operacional do restaurante (cadastrar cardápio; taxa/mínimo; como a IA
  atende; Kanban; fluxo de status; limitações honestas — sem foto, carrinho na conversa, endereço
  texto livre).

[TESTES BACKEND]
- SushiCategoryParityTest — garante o enum Java SushiCategory ↔ const TS sushi-categories.ts (mesmo
  padrão do ProfileTypeParityTest). ProfileTypeParityTest cobre a inclusão de 'sushi' no enum.
- SushiMenuServiceTest + SushiMenuControllerIntegrationTest — CRUD de item; invalidação de cache;
  delete-em-uso → 409 menu_item_in_use; guard wrongProfile → 403.
- SushiOrderServiceTest — recálculo de total (descarta a IA); snapshot de preço+nome; transições
  válidas/inválidas (409); notificação por status.
- SushiOrderControllerIntegrationTest — GET lista/detalhe, PATCH status, guard de perfil.
- (A paridade de status foi cravada no enum SushiOrderStatus ↔ TS; checada pela suíte.)
mvn final = contagem REAL do Surefire (não grep @Test).

[CONSTRAINTS DUROS]
- Migration única (30). Sem foto/anexo (bloqueador SERVICE_ROLE_KEY).
- Cliente NÃO é entidade do core — continua o contact (pedido tem conversation_id/contact_id).
- Carrinho NÃO é entidade — vive na conversa (a IA relê o histórico).
- Pedido criado SÓ pelo backend (service_role) via OrderConfirmHandler; tenant só SELECT/UPDATE.
- Total recalculado no backend (descarta o total da IA). Snapshots de preço+nome no order_item.
- subtotal/total/unit_price materializados (não generated).
- Categorias hardcoded (parity). Status hardcoded (parity). Tag <pedido> (texto livre, não tool
  calling).
- Cache de cardápio TTL 60s + invalidação em toda mutação do cardápio/config.
- Guard de perfil em todos os /api/sushi/**. Webhook OFF (incidente registrado).
- NÃO mexer em outros perfis nem em system-template.txt.

[PASSO FINAL — JÁ IMPLEMENTADO]
Já está tudo em produção do código: migration 30 aplicada; backend (menu/config/orders/handler/
cache/guard) em profiles/sushi/; persona SUSHI em ProfilePromptContext; telas sushi-menu/sushi-orders;
nav "Restaurante"; suíte de testes (parity de categoria, service/controller de menu e orders) verde;
docs CLAUDE.md + PERFIL_SUSHI.md escritos. O JwtFilter autentica /api/sushi/**. O OutboundService
remove a tag <pedido> antes de enviar e o OrderConfirmHandler cria o pedido. Tenant de exemplo:
igorhaf5 (sushi) no Supabase. Commit/tag da camada 7.1 fechados.

[REPORTAR] (o que esta camada entregou)
- "ProfileType.SUSHI — primeiro perfil vertical real (camada 7.1)"
- "Paridade SushiCategory e ProfileType validadas"
- "Carrinho-na-conversa SEM entidade própria (a IA relê o histórico a cada turno)"
- "Tag <pedido> em texto livre (NÃO tool calling — restrição responseSchema do Gemini)"
- "Total recalculado no backend (descarta o total da IA); snapshots de preço+nome no order_item"
- "Status hardcoded: recebido→preparo→saiu_pra_entrega→entregue; cancelado terminal; 409 em
  transição inválida; recebido não notifica"
- "Cache de cardápio TTL 60s + invalidação em toda mutação"
- "Pedido criado pelo backend via OrderConfirmHandler; tenant só SELECT/UPDATE (Kanban)"
- "Guard SushiProfileGuard (403 forbidden_wrong_profile); JwtFilter autentica /api/sushi/**"
- "getNavForProfile('sushi') injeta o grupo 'Restaurante' (Cardápio + Pedidos)"
- "É o ALICERCE que os perfis order-based posteriores (comida, pizzaria…) clonaram"
