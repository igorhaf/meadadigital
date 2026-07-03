>>> SLOT ATRIBUÍDO (ver docs/prompts-nicho/README.md — fonte única de verdade, tem precedência
>>> sobre qualquer "provisório" no corpo): ordem 1 · camada 8.6 · migration 50_pizzaria.sql ·
>>> tenant igorhaf17 (company/user sufixo -017) · ids de seed sufixo -04x. Reconfirmar no arranque
>>> que a fila não avançou; se avançou, deslocar conforme o README.

[TAREFA — SUB-MARATONA: PERFIL PIZZARIA / Pizzaria (camada 8.6)]

[CONTEXTO]
PROJETO MEADA em /home/igorhaf/meada.
17 perfis verticais reais hoje (… comida 8.4, floricultura 8.5 — o último fechado) + generic.
Lê CONTEXT.md e o filesystem no arranque pra cravar convenções, nº de migration, contagem do
Surefire e numeração de tenant ANTES de escrever qualquer código. NÃO hardcodar a contagem do
mvn — relatar a REAL do Surefire ao final. Valores esperados (CONFIRMAR no filesystem antes;
podem ter avançado se Casamento/outra SM foi executada primeiro): migration 50_pizzaria, tenant
igorhaf17, company c?0000000-...-017, user a?0000000-...-017. IDs de namespace compartilhado
(contacts/instance/conversation) NO SEED com sufixo NOVO que NÃO colida com nenhum seed anterior.

Pizzaria é template de nicho pra PIZZARIA / DELIVERY DE PIZZA dentro do mesmo dashboard Meada.
Tenant acessa pizzaria.meadadigital.local e vê o produto "Pizzaria". A IA atende clientes via
WhatsApp, conhece o cardápio (pizzas por tamanho, bordas, bebidas, sobremesas), MONTA o pedido
NA CONVERSA (carrinho relido do histórico a cada turno, igual sushi/comida), confirma SEMPRE com
o valor total e o endereço de entrega, e avisa que o pedido vai pra CONFIRMAÇÃO DA PIZZARIA. Tom
ágil e simpático.

>>> TRAVA DE COMPORTAMENTO DA IA (cravada) <
- NUNCA inventa pizza, sabor, tamanho, borda, adicional ou preço que não esteja no cardápio.
- NUNCA aceita NEM recusa o pedido — isso é AÇÃO HUMANA da pizzaria no painel (gate de aceite).
  A IA só CONFIRMA o RECEBIMENTO ("seu pedido foi enviado pra pizzaria") na própria mensagem.
- O total é SEMPRE recalculado pelo sistema — a IA pode somar pra orientar, mas o backend
  descarta o total da IA e recalcula a partir do cardápio (defesa contra chute).
- Meio-a-meio: a IA propõe a divisão em sabores, mas o PREÇO da pizza fracionada é o do sistema
  (regra do maior valor) — a IA não negocia preço.

EVOLUÇÃO ESTRUTURAL: CLONA o chassi do COMIDA (camada 8.4) — cardápio + OPÇÕES/adicionais
(modifiers: Tamanho, Borda) + carrinho-na-conversa + tag de pedido + recálculo de total
(descarta o total da IA) + snapshot de preço/nome + taxa de entrega/pedido mínimo + Kanban de
status + GATE DE ACEITE HUMANO (ESCAPADA 1 do comida: pedido nasce 'aguardando'; a pizzaria
ACEITA→'em_preparo' ou RECUSA→'recusado' com rejection_reason; a IA não aceita/recusa). UMA
escapada NOVA que nem o comida nem o sushi têm:

  ESCAPADA — PIZZA MEIO-A-MEIO (SABORES FRACIONADOS): uma MESMA pizza (um item do pedido) pode
  ser dividida em N FRAÇÕES (tipicamente 2; suportar 1=inteira e 2=meio-a-meio nesta SM, com o
  modelo já preparado pra N), cada fração um SABOR diferente escolhido do cardápio de pizzas. O
  PREÇO da pizza fracionada é calculado pela REGRA DO MAIOR VALOR: cobra-se o preço (no tamanho
  escolhido) do sabor MAIS CARO entre as frações — recalculado no backend. As frações são
  modeladas como sub-entidade do item de pedido (pizza_order_item_flavors), cada linha = UM
  sabor de UMA fração, com SNAPSHOT de nome+preço-no-tamanho do sabor no momento do pedido.
  Diferença pro comida: o comida soma deltas de modifiers PLANOS (base + Σ deltas); aqui o preço
  do item NÃO é base+delta — é o MÁXIMO entre os sabores das frações (+ deltas de borda/tamanho
  se aplicável). option_id/flavor_id inválido aborta a criação.

  Regra de precificação cravada (recomendada — MAIOR VALOR; alternativa "média" registrada como
  fase futura): inteira (1 fração) = preço do sabor no tamanho. Meio-a-meio (2 frações) =
  MAX(preço_sabor_A, preço_sabor_B) no tamanho escolhido. Borda recheada e tamanho são modifiers
  (deltas) que somam por cima, como no comida.

NÃO TEM nesta SM (registrado pra não inventar): broto/fatia avulsa, combo promocional, cupom/
desconto, programa de fidelidade, integração com iFood, rastreio de entregador em mapa, pizza com
3+ frações na UI (modelo suporta N, UI cobre 1 e 2), pagamento real (Stripe é #50), foto da pizza
(bloqueador SERVICE_ROLE_KEY), scheduler de auto-transição de status, tempo estimado de entrega
dinâmico. Fases futuras.

DECISÕES CRAVADAS (revisor decidiu pelo Igor):
1. CLONA o chassi do COMIDA (cardápio + modifiers + carrinho + tag de pedido + total recalculado +
   taxa/mínimo + Kanban + gate de aceite humano). MANTER 1:1 onde não conflita.
2. Categorias de pizzaria hardcoded (CHECK + enum Java + const TS + parity test), distintas do
   comida: pizzas_salgadas, pizzas_doces, bordas, bebidas, sobremesas, combos. (Sabor de pizza é
   um item das categorias pizzas_salgadas/pizzas_doces.)
3. ESCAPADA meio-a-meio: pizza_order_item_flavors (sub-entidade do item). Regra de preço = MAIOR
   VALOR. Modelo aceita N frações; UI/IA cobrem 1 e 2 nesta SM.
4. Modifiers (Tamanho, Borda) seguem o modelo de options/adicionais do comida
   (pizzaria_menu_item_options, price_delta_cents). Tamanho é o que define o preço-base do sabor
   (decisão: cada sabor tem preço POR TAMANHO — ver FUNDAÇÃO).
5. Gate de aceite humano: pedido nasce 'aguardando'; aceite/recusa no painel; a IA não aceita/
   recusa (espelho comida). 'aguardando' NÃO notifica (a IA já confirmou o recebimento).
6. Tag <pedido_pizza> (namespace próprio, distinta de <pedido_comida>/<pedido_flor> e de todas
   as outras). Carrega itens com frações; o backend recalcula tudo.

[FUNDAÇÃO — migration 50_pizzaria]
- ALTER companies CHECK aceitar 'pizzaria'.
- RLS enable+force, policies via app.company_id(), grants authenticated + service_role
  (orders/order_items/order_item_options/order_item_flavors: INSERT pelo BACKEND via service_role —
  o pedido é criado pela IA via PedidoPizzaConfirmHandler; tenant só SELECT/UPDATE — status no
  Kanban / gate de aceite). Espelhar 47_comida.sql inteiro.
- subtotal_cents/total_cents/unit_price_cents MATERIALIZADOS no INSERT; NÃO colunas geradas
  (recálculo cruza linhas/tabelas — lição das migrations anteriores).
- SNAPSHOTS: preço+nome em order_items; group/option/delta em order_item_options; nome+preço do
  sabor em order_item_flavors. Alterar/excluir item/opção/sabor no cardápio NÃO altera pedidos
  passados.
- Tabelas:
  * pizzaria_config — taxa de entrega + pedido mínimo (1:1 com company; ausente → 0). Espelho
    comida_config.
  * pizzaria_menu_items — cardápio. (id, company_id, category CHECK in sync com enum, name CHECK
    1..200, description, price_cents = preço BASE/de referência, active default true, timestamps).
    DECISÃO de preço por tamanho: como cada sabor tem preço por TAMANHO (M/G/família), modelar o
    preço por tamanho como OPÇÕES do grupo "Tamanho" (price_delta sobre o base) OU — recomendado e
    mais simples pra meio-a-meio — manter price_cents como o preço do sabor no tamanho de
    referência e tratar Tamanho como modifier de delta; a regra do maior valor compara os
    price_cents (+ delta de tamanho aplicado igual às duas frações). O agente escolhe a
    materialização; o INVARIANTE é: o backend calcula o preço da pizza como MAX dos sabores no
    tamanho escolhido + deltas. Documentar a escolha no comment.
  * pizzaria_menu_item_options — modifiers (Tamanho: M/G/Família; Borda: tradicional/recheada
    +R$). Cada linha = UMA opção de UM grupo (group_label), price_delta_cents. Espelho
    comida_menu_item_options. on delete cascade.
  * pizzaria_orders — pedidos. status CHECK ('aguardando'|'em_preparo'|'saiu_entrega'|'entregue'|
    'recusado'|'cancelado') default 'aguardando'; subtotal/delivery_fee/total materializados;
    delivery_address NOT NULL; rejection_reason nullable (gate de aceite); conversation_id/
    contact_id NOT NULL; timestamps + status_updated_at. Espelho comida_orders.
  * pizzaria_order_items — itens do pedido com snapshot de nome+preço. unit_price_cents JÁ inclui
    a regra de preço da pizza (MAX dos sabores) + Σ deltas de modifiers. quantity. Espelho
    comida_order_items + a noção de fração.
  * pizzaria_order_item_options — opções/modifiers escolhidos por item (snapshots de group/option/
    delta). Espelho comida_order_item_options.
  * pizzaria_order_item_flavors — A ESCAPADA: frações/sabores de um item-pizza. (id, company_id,
    order_item_id refs pizzaria_order_items on delete cascade, menu_item_id refs pizzaria_menu_items
    on delete set null (preserva snapshot), fraction_index int (1..N), flavor_name_snapshot,
    flavor_price_cents_snapshot, timestamps). Comment cravando: meio-a-meio, regra do maior valor,
    snapshots preservam histórico.
- Status do pedido hardcoded (PizzaOrderStatus enum Java + const TS + parity test): aguardando →
  em_preparo, recusado, cancelado ; em_preparo → saiu_entrega, cancelado ; saiu_entrega →
  entregue, cancelado ; entregue/recusado/cancelado → terminal. (espelho comida; aceite=
  aguardando→em_preparo é o gate humano.) Notifica: em_preparo (aceito), saiu_entrega, entregue,
  recusado (com motivo defensivo). aguardando/cancelado conforme o padrão do comida.
- Categorias hardcoded (PizzaCategory.java + pizza-categories.ts + PizzaCategoryParityTest):
  pizzas_salgadas, pizzas_doces, bordas, bebidas, sobremesas, combos.
- TODAS as tabelas novas entram na migration 50 ANTES de tocar o banco (lição os_config) e no
  TRUNCATE/SCRIPTS do AbstractIntegrationTest.

[BACKEND]
- Menu (cardápio): CRUD de itens + opções (modifiers) — espelho comida (ComidaMenuService).
  Cache do bloco de cardápio injetado no prompt (Caffeine TTL 60s, igual sushi/comida),
  INVALIDADO em toda gravação/edição/exclusão. delete de item com pedido → 409
  menu_item_in_use (on delete restrict no order_item).
- Config: GET (fallback taxa/mínimo = 0) + PUT.
- Orders: criados pelo BACKEND via PedidoPizzaConfirmHandler (não pelo SDK do tenant). Recálculo:
  pra cada item, se 1 fração → preço do sabor no tamanho; se 2+ frações → MAX dos preços dos
  sabores no tamanho; + Σ deltas de modifiers (borda, etc.); × quantity. subtotal = Σ itens;
  total = subtotal + delivery_fee (valida pedido mínimo → mínimo não atingido = mensagem, não
  cria? decisão: respeita o padrão do comida — registrar o que o comida faz e espelhar). Snapshots
  completos. flavor_id/option_id inválido aborta (sem criar pedido parcial).
- Status: PATCH com validação de transição (inválida → 409 invalid_status_transition) + gate de
  aceite (aguardando→em_preparo = aceitar; aguardando→recusado = recusar com rejection_reason
  opcional). Notificação outbound por status (texto defensivo, espelho comida).
- IA:
  * Persona ágil-simpática com a TRAVA DE COMPORTAMENTO embutida (não inventa item/preço, não
    aceita/recusa, total recalculado, meio-a-meio com preço do sistema).
  * Contexto injetado = bloco de cardápio (itens por categoria + opções/modifiers com deltas +
    quais itens são sabores de pizza pra meio-a-meio) + taxa/mínimo + instruções da tag
    <pedido_pizza> com o formato de frações. Cache TTL 60s (igual comida; IGNORA conversationId —
    o contexto é o cardápio). Invalidação em toda mutação do cardápio.
  * Tag <pedido_pizza>{"items":[{"menu_item_id"|"flavors":[{"menu_item_id","fraction_index"}],
    "size_option_id?","border_option_id?","options":[...],"quantity"}],"delivery_address","notes"}
    → PedidoPizzaConfirmHandler (espelho PedidoComidaConfirmHandler + parse das frações). Best-
    effort; o OutboundService REMOVE a tag antes de enviar e o backend recalcula o total.
  * JwtFilter autentica /api/pizzaria/. OutboundService ganha maybeProcessPedidoPizza (best-
    effort, encadeado após comida/floricultura — perfil é único, só um age).

[FRONTEND]
- /dashboard/pizzaria-menu (CRUD itens + editor de opções/modifiers inline, igual comida; marcar
  itens que são SABORES de pizza),
  /dashboard/pizzaria-orders (Kanban por status com o GATE DE ACEITE: botões Aceitar/Recusar na
  coluna 'aguardando', recusa pede motivo; detalhe do pedido mostra os itens com as FRAÇÕES/
  sabores e o preço pela regra do maior valor),
  /dashboard/pizzaria-settings (taxa de entrega + pedido mínimo).
- types + SDKs (menu, options, orders) espelhando comida + PizzaOrderItemFlavor.
- Status TS pizza-order-status.ts + PizzaCategory const + os 2 parity tests (status + categorias).
- getNavForProfile('pizzaria') injeta "Pizzaria" (3 itens: Cardápio, Pedidos, Configurações),
  seguindo o modelo dos branches existentes (comida/floricultura já têm branch). Subdomínio
  pizzaria.meadadigital.local. Paleta: agente escolhe (sugestão quente livre: 'carmim',
  'ferrugem' ou 'por-do-sol').
- npm build limpo.

[DOCS]
- CLAUDE.md: seção "## Perfil Pizzaria (camada 8.6)" espelhando as seções de perfil + nota de que
  CLONA o COMIDA e inaugura a ESCAPADA meio-a-meio (sabores fracionados, regra do maior valor).
  Documentar EXPLÍCITO: categorias próprias; o gate de aceite humano; a regra de preço da pizza
  fracionada; a tag <pedido_pizza>.
- docs/PERFIL_PIZZARIA.md: guia operacional (cardápio + sabores + modifiers; pedidos + Kanban +
  gate de aceite; meio-a-meio e como o preço é calculado; como a IA atende; "o que a IA NÃO faz").
  Espelhar PERFIL_COMIDA.md.
- NÃO mexer em system-template.txt nem em outros perfis.

[TESTES BACKEND]
Espelhar a suíte do comida (service + controller integration por entidade):
- PizzaOrderStatusParityTest + PizzaCategoryParityTest + ProfileTypeParityTest.
- PizzariaMenuServiceTest + ControllerIntegrationTest (CRUD item+opções; invalida cache; delete-
  em-uso 409 menu_item_in_use; wrongProfile 403).
- PizzariaConfigServiceTest/ControllerIntegrationTest (GET fallback + PUT).
- PizzariaOrderServiceTest [CHAVE da escapada]: pizza INTEIRA (1 sabor) = preço do sabor; MEIO-A-
  MEIO (2 sabores) = MAX dos preços (provar com A mais caro e com B mais caro); + delta de borda;
  × quantity; subtotal/total com taxa; total da IA DESCARTADO (recalcula); flavor_id inválido →
  aborta; snapshots de sabor preservados após alterar o cardápio.
- PedidoPizzaConfirmHandlerTest: tag com item inteiro; tag com meio-a-meio (2 frações); tag com
  modifiers; option/flavor inválido → empty; sem tag → empty; total recalculado bate.
- Status/gate: aguardando→em_preparo (aceite) + saiu_entrega + entregue + recusado(motivo);
  transição inválida → 409; a IA não tem endpoint de aceite (gate é só painel/tenant).
mvn final = relatar contagem REAL do Surefire.

[CONSTRAINTS DUROS]
- Migration única (50). Sem foto/anexo.
- Cliente NÃO é entidade do core — continua o contact (pedido tem conversation_id/contact_id).
- ESCAPADA meio-a-meio: pizza_order_item_flavors; regra MAIOR VALOR; modelo aceita N frações,
  UI/IA cobrem 1 e 2.
- Modifiers planos (Tamanho/Borda) = options com price_delta (espelho comida). Preço final do
  item = MAX(sabores no tamanho) + Σ deltas, × quantity, recalculado no backend (descarta IA).
- subtotal/total/unit_price materializados (não generated). Snapshots de item/opção/sabor.
- Gate de aceite humano: nasce 'aguardando'; aceite/recusa no painel; a IA NUNCA aceita/recusa.
- Categorias hardcoded (parity). Tag <pedido_pizza> distinta de TODAS as outras.
- NÃO mexer em outros perfis nem em system-template.txt. Webhook OFF.
- Cache de cardápio TTL 60s + invalidação em toda mutação do cardápio.
- 529 → inline. Gate 3× → pausar. Working tree sujo → pausar. git add EXPLÍCITO (nunca git add .);
  .env/CONTEXT.md/secrets NUNCA staged.
- SEED com timestamptz: `at time zone 'America/Sao_Paulo'`. IDs de namespace com sufixo NOVO.
- Tabela nova entra na migration ANTES de tocar o banco (lição os_config). Adicionar as tabelas ao
  TRUNCATE/SCRIPTS do AbstractIntegrationTest.
- Decisões menores: agente decide (paleta, layout, materialização do preço por tamanho).

[PASSO FINAL — TENANT + SEED + COMMIT + PUSH + SMOKE + RELATÓRIO]
F.1 — TENANT igorhaf17 (Pizzaria Modelo, profile=pizzaria), padrão GoTrue, senha em comunicação
      direta. company c?0000000-...-017 / user a?0000000-...-017. Caddy + /etc/hosts pra
      pizzaria.meadadigital.local.
F.2 — Seed /tmp/seed-pizzaria.sql (NÃO COMITAR; `at time zone 'America/Sao_Paulo'`; ids sufixo
      novo):
  - config: taxa de entrega R$8, pedido mínimo R$30.
  - cardápio: 4 sabores salgados com preços DIFERENTES (Margherita R$45, Calabresa R$48,
    Portuguesa R$52, Quatro Queijos R$55), 2 sabores doces (Chocolate R$40, Romeu e Julieta R$42),
    grupo Tamanho (M base, G +R$12, Família +R$20), grupo Borda (tradicional R$0, recheada +R$10),
    2 bebidas, 1 sobremesa.
  - contact "Bruno Lima" +5511977778888 (VINCULADO: instance+conversation, pra smoke de
    notificação) + contact "Carla Mendes" +5511966667777 (sem vínculo).
  - 3 pedidos cobrindo estados/escapada:
    * 'aguardando' VINCULADO (Bruno): 1 pizza MEIO-A-MEIO (Portuguesa R$52 + Quatro Queijos R$55)
      tamanho G borda recheada → preço esperado MAX(52,55)=55 +12(G) +10(borda) = R$77; + 1
      bebida; endereço; pra smoke de aceite + cálculo do maior valor.
    * 'em_preparo' (Carla): 1 pizza INTEIRA (Calabresa M) + 1 doce; pra smoke de transição.
    * 'entregue' (Bruno, passado): 1 pizza meio-a-meio doce; histórico.
F.3 — JwtFilter /api/pizzaria/ (se ainda não).
F.4-F.6 — git add EXPLÍCITO dos arquivos da SM + sanity (sem .env/secrets/CONTEXT) + commit.
      Mensagem padrão (feat(camada-8): perfil pizzaria/Pizzaria (camada 8.6) com FUNDAÇÃO/BACKEND/
      FRONTEND/DECISÕES/VALIDAÇÃO contagem REAL/NÃO TOCADO/FECHAMENTO + Co-Authored-By: Claude
      Opus 4.8). Tag fase-8.6-fechada (ou o nº real confirmado no arranque).
F.7 — git push origin main + tags.
F.8 — docker compose restart backend + aguardar /admin/me → 401.
F.9 — Smoke E2E:
  BLOCO A: auth — igorhaf17 → /admin/me → role=tenant_admin, profileId=pizzaria,
    productName=Pizzaria.
  BLOCO B: cardápio + guard — GET menu (itens+opções); CRUD smoke + invalida cache; delete em uso
    409; GET config + PUT; tenant comida (igorhaf16) → /api/pizzaria/menu → 403.
  BLOCO C: pedido INTEIRO — simular <pedido_pizza> com 1 pizza inteira (Calabresa M) via handler →
    pedido 'aguardando' + total = 48 + taxa; total da IA (se enviado errado) descartado.
  BLOCO D: MEIO-A-MEIO [CHAVE — a escapada] —
    - <pedido_pizza> meio-a-meio (Portuguesa+Quatro Queijos, G, borda recheada) → unit_price =
      MAX(52,55)=55 +12 +10 = 77 (provar o MAX, não a soma nem a média)
    - inverter (sabor A mais caro) → ainda pega o MAX
    - flavor_id inválido → não cria pedido (empty)
    - snapshots de sabor preservados após editar o preço do sabor no cardápio
  BLOCO E: gate de aceite + status — PATCH aguardando→em_preparo (Bruno vinculado) = ACEITAR →
    200 + msg outbound; aguardando→recusado com motivo → 200 + msg defensiva; saiu_entrega;
    entregue; transição inválida → 409; a IA não tem rota de aceite.
  BLOCO F: regressão — perfis anteriores intactos (smoke leve 1 endpoint cada); pizzaria →
    /api/comida/* → 403; pizzaria → /api/floricultura/* → 403.
  BLOCO G: paridade — mvn test -Dtest=PizzaOrderStatusParityTest,PizzaCategoryParityTest,
    ProfileTypeParityTest → verde.
  Cleanup smoke + restaurar seed pristine. mvn final: contagem REAL.
F.10 — RELATÓRIO + DESTAQUE EXPLÍCITO:
  - "perfil pizzaria — camada 8.6; CLONA o COMIDA (cardápio+carrinho+tag+gate de aceite)"
  - "ESCAPADA meio-a-meio: sabores fracionados, regra do MAIOR VALOR, recalculado no backend"
  - "BLOCO D prova o MAX (não soma/média) + snapshots de sabor + flavor inválido aborta"
  - "BLOCO E prova o gate de aceite humano (aceitar/recusar no painel; IA não aceita/recusa)"
  - "categorias próprias (pizzas_salgadas/doces/bordas/…), distintas do comida"
  - "Seed: at time zone + sufixo de ids novo"
  - "tabelas criadas DENTRO da migration (lição os_config)"
  - PENDÊNCIAS: combo/cupom/fidelidade, 3+ frações na UI, iFood, rastreio, Stripe, foto + dívida
     acumulada (webhook, cliente real, olho humano sobre os verticais).

[REPORTAR]
Igual SMs anteriores. Incluir EXPLICITAMENTE:
- "ProfileType.PIZZARIA adicionado (camada 8.6)"
- "Paridade PizzaOrderStatus, PizzaCategory e ProfileType validadas"
- "Tenant igorhaf17 criado (GoTrue + Caddy/etc/hosts)"
- "ESCAPADA meio-a-meio: pizza_order_item_flavors, regra do MAIOR VALOR, recalculado no backend"
- "BLOCO D: MAX dos sabores (não soma/média), snapshots preservados, flavor inválido aborta"
- "Gate de aceite humano (espelho comida): IA confirma recebimento, pizzaria aceita/recusa"
- "Tag <pedido_pizza> distinta de <pedido_comida>/<pedido_flor> e de todas as outras"
- "OutboundService ganhou maybeProcessPedidoPizza"
- "getNavForProfile('pizzaria') com branch próprio"
- "Cache de cardápio TTL 60s + invalidação em toda mutação"
- "tabelas criadas DENTRO da migration (lição os_config); seed com at time zone + sufixo novo"
- "Próximas fases: combo/cupom/fidelidade, 3+ frações UI, iFood, Stripe + fila de prioridade"
