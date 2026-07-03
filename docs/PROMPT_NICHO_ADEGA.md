>>> SLOT ATRIBUÍDO (ver docs/prompts-nicho/README.md — fonte única de verdade, tem precedência
>>> sobre qualquer "provisório" no corpo): ordem 4 · camada 8.9 · migration 53_adega.sql ·
>>> tenant igorhaf20 (company/user sufixo -020) · ids de seed sufixo -07x. Reconfirmar no arranque
>>> que a fila não avançou; se avançou, deslocar conforme o README.

[TAREFA — SUB-MARATONA: PERFIL ADEGA / Adega (camada 8.9)]

[CONTEXTO]
PROJETO MEADA em /home/igorhaf/meada.
18 perfis verticais reais hoje (… comida 8.4, floricultura 8.5, pizzaria 8.6 — confirmar no disco
qual é o último FECHADO) + generic. Lê CONTEXT.md e o filesystem no arranque pra cravar convenções,
nº de migration, contagem do Surefire e numeração de tenant ANTES de escrever qualquer código. NÃO
hardcodar a contagem do mvn — relatar a REAL do Surefire ao final.

>>> NÚMERO DE MIGRATION É PROVISÓRIO (cravado): no disco hoje a última migration é 50_pizzaria.sql,
logo o slot NATURAL seria 51_adega. MAS há OUTROS drafts disputando o slot (PROMPT_NICHO_CASAMENTO.md
e PROMPT_NICHO_PADARIA.md na raiz, além do pizzaria) — qualquer um pode ser executado antes desta SM.
NO ARRANQUE: `ls supabase/migrations/ | sort` e usar o PRÓXIMO número livre REAL; o `profile_id in
(...)` da migration anterior já pode incluir comida/floricultura/pizzaria + outros nichos — ESPELHAR
a lista do CHECK mais recente e APENDAR 'adega' (NÃO copiar a lista do 47_comida, que está
desatualizada). Mesmo raciocínio pro tenant: esperado igorhaf17, MAS pizzaria/casamento/padaria podem
ter consumido — confirmar o próximo nº de tenant/company/user livre no Supabase e usar esse. IDs de
namespace compartilhado (contacts/instance/conversation) NO SEED com sufixo NOVO que NÃO colida com
nenhum seed anterior.

Adega é template de nicho pra ADEGA / DELIVERY DE BEBIDAS (vinhos, destilados, cervejas) dentro do
mesmo dashboard Meada. Tenant acessa adega.meadadigital.local e vê o produto "Adega". A IA atende
clientes via WhatsApp, conhece o cardápio (vinhos, espumantes, cervejas, destilados, sem-álcool,
acessórios — cada item com opções de volume/temperatura), MONTA o pedido NA CONVERSA (carrinho relido
do histórico a cada turno, igual sushi/comida), CONFIRMA A MAIORIDADE do cliente, confirma SEMPRE com
o valor total e o endereço de entrega, e avisa que o pedido vai pra CONFIRMAÇÃO DA LOJA. Tom cordial,
consultivo (pode sugerir harmonização entre o que JÁ está no cardápio), com aviso de "beba com
moderação".

>>> TRAVA DE COMPORTAMENTO DA IA (cravada) <
- TRAVA +18 (a espinha desta SM): a IA SEMPRE pergunta/confirma a MAIORIDADE do cliente ANTES de
  fechar o pedido. NUNCA vende a menor de idade; se o cliente declarar (ou indicar) ser menor de 18,
  RECUSA com gentileza e não emite a tag. Só emite <pedido_adega> com o flag age_confirmed=true.
- NUNCA inventa rótulo, marca, safra, volume, preço ou item que não esteja no cardápio.
- NUNCA incentiva consumo excessivo, NUNCA sugere "beber mais", NUNCA minimiza riscos do álcool;
  inclui o aviso "Beba com moderação" e, quando couber, lembra que a venda é proibida para menores.
- NUNCA aceita NEM recusa o pedido (no sentido de gate de produção) — isso é AÇÃO HUMANA da loja no
  painel (gate de aceite). A IA só CONFIRMA o RECEBIMENTO ("seu pedido foi enviado pra loja") na
  própria mensagem. (Recusar por menoridade é OUTRA coisa: aí a IA nem cria o pedido.)
- O total é SEMPRE recalculado pelo sistema — a IA pode somar pra orientar, mas o backend descarta o
  total da IA e recalcula a partir do cardápio (defesa contra chute).

EVOLUÇÃO ESTRUTURAL: CLONA o chassi do COMIDA (camada 8.4) — cardápio + OPÇÕES/adicionais (modifiers:
Volume, Temperatura) + carrinho-na-conversa + tag de pedido + recálculo de total (descarta o total da
IA) + snapshot de preço/nome + taxa de entrega/pedido mínimo + Kanban de status + GATE DE ACEITE
HUMANO (ESCAPADA 1 do comida: pedido nasce 'aguardando'; a loja ACEITA→'em_preparo'/'separando' ou
RECUSA→'recusado' com rejection_reason; a IA não aceita/recusa). UMA escapada NOVA que nem o comida
nem o pizzaria têm:

  ESCAPADA — TRAVA DE FAIXA ETÁRIA (+18, VENDA DE ÁLCOOL): a venda de bebida alcoólica exige
  confirmação de MAIORIDADE. O pedido carrega um flag boolean age_confirmed; o backend RECUSA criar
  pedido sem ele (422 age_not_confirmed) — é a regra que justifica adega ser perfil próprio, e não um
  preset do comida. A IA confirma a maioridade na conversa e só então emite a tag com
  age_confirmed=true; se a tag chega sem o flag (ou false), o handler aborta SEM criar pedido (não há
  pedido "menor de idade" no banco). O flag é PERSISTIDO no pedido (auditoria/compliance) — fica
  visível no painel. Diferença pro comida: o comida cria o pedido com base no carrinho e pronto; aqui
  há uma PRÉ-CONDIÇÃO DURA de compliance no backend antes de qualquer cálculo de total. Mesmo um
  pedido SÓ de itens sem-álcool nesta SM passa pela trava (decisão cravada — simplicidade; refinar
  "carrinho 100% sem-álcool dispensa +18" é fase futura).

  Modifiers de bebida (cravados): grupo VOLUME (375ml, 750ml, 1L — price_delta sobre o base, igual
  comida/Tamanho) e grupo TEMPERATURA (natural R$0, gelado +R$X — taxa de gelo/serviço, opcional).
  Modelados como pizzaria_/comida_-style options (group_label + price_delta_cents). O preço final do
  item = price_cents base + Σ deltas de modifiers, × quantity, recalculado no backend (descarta IA).

NÃO TEM nesta SM (registrado pra não inventar): clube de assinatura de vinho (recorrência — academia
cobre o padrão de assinatura), curadoria/scoring de safra, integração com sommelier/API de rótulos,
controle de estoque por garrafa, cupom/desconto, fidelidade, integração com iFood/Zé Delivery,
rastreio de entregador em mapa, validação documental de idade (RG/foto/biometria — a confirmação é
DECLARATÓRIA pela IA nesta SM; verificação real na entrega é processo da loja), dispensa da trava +18
pra carrinho 100% sem-álcool, pagamento real (Stripe é #50), foto do rótulo (bloqueador
SERVICE_ROLE_KEY), scheduler de auto-transição de status, ETA de entrega dinâmico. Fases futuras.

DECISÕES CRAVADAS (revisor decidiu pelo Igor):
1. CLONA o chassi do COMIDA (cardápio + modifiers + carrinho + tag de pedido + total recalculado +
   taxa/mínimo + Kanban + gate de aceite humano). MANTER 1:1 onde não conflita.
2. Categorias de adega hardcoded (CHECK + enum Java + const TS + parity test), distintas do comida:
   vinhos, espumantes, cervejas, destilados, sem_alcool, acessorios.
3. ESCAPADA +18: coluna age_confirmed boolean NOT NULL no pedido; backend recusa pedido sem ela (422
   age_not_confirmed). Flag DECLARATÓRIO, persistido pra compliance/auditoria, visível no painel.
4. Modifiers (Volume, Temperatura) seguem o modelo de options/adicionais do comida
   (adega_menu_item_options, price_delta_cents). price_cents é o preço-base do item; Volume/Temperatura
   somam delta. Preço do item = base + Σ deltas (espelho exato do comida — SEM regra do maior valor; a
   complexidade aqui é a trava +18, não o preço).
5. Gate de aceite humano: pedido nasce 'aguardando'; aceite/recusa no painel; a IA não aceita/recusa
   (espelho comida). 'aguardando' NÃO notifica (a IA já confirmou o recebimento).
6. Tag <pedido_adega> (namespace próprio, distinta de <pedido_comida>/<pedido_flor>/<pedido_pizza> e
   de TODAS as outras). Carrega o flag age_confirmed; o backend recalcula tudo e valida a trava.

[FUNDAÇÃO — migration NN_adega (nº confirmado no arranque; ESPELHAR a lista de CHECK mais recente)]
- ALTER companies CHECK: pegar a lista do CHECK da migration MAIS RECENTE no disco (que já inclui
  comida/floricultura/pizzaria + o que tiver entrado), e APENDAR 'adega'. NÃO copiar a lista
  desatualizada do 47_comida.
- RLS enable+force, policies via app.company_id(), grants authenticated + service_role
  (orders/order_items/order_item_options: INSERT pelo BACKEND via service_role — o pedido é criado
  pela IA via PedidoAdegaConfirmHandler; tenant só SELECT/UPDATE — status no Kanban / gate de aceite).
  Espelhar 47_comida.sql inteiro.
- subtotal_cents/total_cents/unit_price_cents MATERIALIZADOS no INSERT; NÃO colunas geradas (recálculo
  cruza linhas/tabelas — lição das migrations anteriores).
- SNAPSHOTS: preço+nome em order_items; group/option/delta em order_item_options. Alterar/excluir
  item/opção no cardápio NÃO altera pedidos passados.
- Tabelas:
  * adega_config — taxa de entrega + pedido mínimo (1:1 com company; ausente → 0). Espelho
    comida_config.
  * adega_menu_items — cardápio. (id, company_id, category CHECK in sync com enum, name CHECK 1..120,
    description, price_cents = preço BASE, available default true, timestamps). Espelho
    comida_menu_items. (Volume/safra/teor vão na description nesta SM — sem colunas extras; estruturar
    é fase futura.)
  * adega_menu_item_options — modifiers (Volume: 375ml/750ml/1L; Temperatura: natural/gelado +R$).
    Cada linha = UMA opção de UM grupo (group_label), price_delta_cents. Espelho
    comida_menu_item_options. on delete cascade.
  * adega_orders — pedidos. status CHECK ('aguardando'|'em_preparo'|'saiu_entrega'|'entregue'|
    'recusado'|'cancelado') default 'aguardando'; subtotal/delivery_fee/total materializados;
    delivery_address NOT NULL; rejection_reason nullable (gate de aceite); **age_confirmed boolean NOT
    NULL** (A ESCAPADA — sem default; o backend só insere com true, nunca persiste pedido não
    confirmado); conversation_id/contact_id NOT NULL; timestamps + status_updated_at. Espelho
    comida_orders + a coluna age_confirmed. Comment cravando: venda de álcool exige maioridade
    confirmada; pedido sem age_confirmed NÃO é criado (422 age_not_confirmed no backend).
  * adega_order_items — itens do pedido com snapshot de nome+preço. unit_price_cents JÁ inclui Σ
    deltas de modifiers. qtd. Espelho comida_order_items.
  * adega_order_item_options — opções/modifiers escolhidos por item (snapshots de group/option/delta).
    Espelho comida_order_item_options.
- Status do pedido hardcoded (AdegaOrderStatus enum Java + const TS + parity test): aguardando →
  em_preparo, recusado, cancelado ; em_preparo → saiu_entrega, cancelado ; saiu_entrega → entregue,
  cancelado ; entregue/recusado/cancelado → terminal. (espelho comida 1:1; aceite= aguardando→
  em_preparo é o gate humano.) Notifica: em_preparo (aceito), saiu_entrega, entregue, recusado (com
  motivo defensivo). aguardando NÃO notifica (a IA já confirmou). cancelado conforme o padrão do
  comida. (Decisão: manter o label 'em_preparo' do comida pra clonar 1:1 — "separando" seria só
  cosmético; o NOME do status fica igual ao comida pra não divergir o enum/parity. O texto da
  notificação pode usar "separando" no notificationText sem mudar o id.)
- Categorias hardcoded (AdegaCategory.java + adega-categories.ts + AdegaCategoryParityTest):
  vinhos, espumantes, cervejas, destilados, sem_alcool, acessorios.
- TODAS as tabelas novas entram na migration NN ANTES de tocar o banco (lição os_config — a tabela
  os_config foi criada FORA da migration uma vez e quebrou o boot) e no TRUNCATE/SCRIPTS do
  AbstractIntegrationTest.

[BACKEND]
- Menu (cardápio): CRUD de itens + opções (modifiers) — espelho comida (ComidaMenuService → 
  AdegaMenuService). Cache do bloco de cardápio injetado no prompt (Caffeine TTL 60s, igual
  sushi/comida), INVALIDADO em toda gravação/edição/exclusão. delete de item com pedido → 409
  menu_item_in_use (on delete restrict no order_item).
- Config: GET (fallback taxa/mínimo = 0) + PUT.
- Orders: criados pelo BACKEND via PedidoAdegaConfirmHandler (não pelo SDK do tenant).
  * PRÉ-CONDIÇÃO +18 (a escapada, ANTES de qualquer cálculo): se a tag não traz age_confirmed=true →
    aborta com 422 age_not_confirmed (no fluxo da IA = empty, sem criar pedido). O flag é persistido
    em adega_orders.age_confirmed.
  * Recálculo: pra cada item, preço-base do item + Σ deltas de modifiers (Volume/Temperatura) ×
    quantity; subtotal = Σ itens; total = subtotal + delivery_fee (valida pedido mínimo respeitando o
    padrão do comida — registrar o que o comida faz e espelhar). Snapshots completos. option_id
    inválido aborta (sem criar pedido parcial).
- Status: PATCH com validação de transição (inválida → 409 invalid_status_transition) + gate de aceite
  (aguardando→em_preparo = aceitar; aguardando→recusado = recusar com rejection_reason opcional).
  Notificação outbound por status (texto defensivo, espelho comida; em_preparo pode dizer "estamos
  separando seu pedido"). A IA NÃO tem endpoint de aceite (gate é só painel/tenant).
- IA:
  * Persona cordial-consultiva com a TRAVA DE COMPORTAMENTO embutida: confirma maioridade ANTES de
    fechar; NUNCA vende a menor; aviso "Beba com moderação"; não inventa item/preço; não aceita/recusa
    (gate); total recalculado. Persona AdegaContextCache/segmento próprio em ProfilePromptContext
    (constante ADEGA + branch `if ("adega".equals(profileId))` em segmentFor, espelho do COMIDA;
    IGNORA conversationId — o contexto é o cardápio).
  * Contexto injetado = bloco de cardápio (itens por categoria + opções/modifiers Volume/Temperatura
    com deltas) + taxa/mínimo + instruções da tag <pedido_adega> com o formato e o campo
    age_confirmed + a regra +18. Cache TTL 60s (igual comida). Invalidação em toda mutação do cardápio.
  * Tag <pedido_adega>{"age_confirmed":true,"items":[{"menu_item_id","options":[{"option_id"}],
    "quantity"}],"delivery_address","notes"} → PedidoAdegaConfirmHandler (espelho
    PedidoComidaConfirmHandler + o guard de age_confirmed). Best-effort; o OutboundService REMOVE a tag
    antes de enviar e o backend recalcula o total e valida a trava.
  * JwtFilter autentica /api/adega/ (ADEGA_PATH_PREFIX = "/api/adega/", espelho dos demais prefixes —
    pizzaria já está lá; adicionar adega na mesma lista e no isProfilePath/allowlist). OutboundService
    ganha maybeProcessPedidoAdega (best-effort, encadeado APÓS comida/floricultura/pizzaria na cadeia
    de toSend = … — perfil é único, só um age).

[FRONTEND]
- /dashboard/adega-menu (CRUD itens + editor de opções/modifiers inline, igual comida; grupos
  Volume/Temperatura),
  /dashboard/adega-orders (Kanban por status com o GATE DE ACEITE: botões Aceitar/Recusar na coluna
  'aguardando', recusa pede motivo; detalhe do pedido mostra itens+opções, o total recalculado E o
  selo "+18 confirmado" (age_confirmed) pra compliance),
  /dashboard/adega-settings (taxa de entrega + pedido mínimo).
- types + SDKs (menu, options, orders) espelhando comida + o campo ageConfirmed no pedido.
- Status TS adega-order-status.ts + AdegaCategory const + os 2 parity tests (status + categorias).
- getNavForProfile('adega') injeta "Adega" (3 itens: Cardápio/Catálogo, Pedidos, Configurações) com
  BRANCH PRÓPRIO em frontend/components/layout/nav-config.tsx (`if (profileId === 'adega') return
  [ADEGA_GROUP, ...NAV_GROUPS]`, espelho dos branches comida/floricultura já existentes). NÃO deixar no
  fallback. Subdomínio adega.meadadigital.local. Paleta: agente escolhe (sugestão de vinho: 'ameixa',
  'beringela' ou 'carmim' — tons de vinho tinto).
- npm build limpo (Turbopack dev esconde import quebrado — `next build` é a verdade).

[DOCS]
- CLAUDE.md: seção "## Perfil Adega (camada 8.x)" espelhando as seções de perfil + nota de que CLONA o
  COMIDA e inaugura a ESCAPADA +18 (trava de faixa etária, age_confirmed, 422 age_not_confirmed).
  Documentar EXPLÍCITO: categorias próprias (vinhos/espumantes/cervejas/destilados/sem_alcool/
  acessorios); o gate de aceite humano; a TRAVA +18 (flag declaratório persistido, backend recusa sem
  ele); os modifiers Volume/Temperatura; a tag <pedido_adega>; a persona com "beba com moderação".
- docs/PERFIL_ADEGA.md: guia operacional (cardápio + modifiers Volume/Temperatura; pedidos + Kanban +
  gate de aceite; a TRAVA +18 e como a IA confirma maioridade; como a IA atende; "o que a IA NÃO faz"
  com ênfase em NUNCA vender a menor / NUNCA incentivar consumo). Espelhar PERFIL_COMIDA.md.
- NÃO mexer em system-template.txt nem em outros perfis.

[TESTES BACKEND]
Espelhar a suíte do comida (service + controller integration por entidade):
- AdegaOrderStatusParityTest + AdegaCategoryParityTest + ProfileTypeParityTest.
- AdegaMenuServiceTest + ControllerIntegrationTest (CRUD item+opções; invalida cache; delete-em-uso
  409 menu_item_in_use; wrongProfile 403).
- AdegaConfigServiceTest/ControllerIntegrationTest (GET fallback + PUT).
- AdegaOrderServiceTest [CHAVE da escapada]:
  * TRAVA +18: criar pedido SEM age_confirmed (ou false) → 422 age_not_confirmed, NENHUM pedido no
    banco (provar que não cria parcial). Com age_confirmed=true → cria e persiste o flag.
  * preço do item = base + Σ deltas (Volume +R$, gelado +R$); × quantity; subtotal/total com taxa;
    total da IA DESCARTADO (recalcula); option_id inválido → aborta; snapshots de item/opção
    preservados após alterar o cardápio.
- PedidoAdegaConfirmHandlerTest: tag com age_confirmed=true cria; tag SEM age_confirmed → empty (não
  cria); tag com modifiers; option inválido → empty; sem tag → empty; total recalculado bate.
- Status/gate: aguardando→em_preparo (aceite) + saiu_entrega + entregue + recusado(motivo); transição
  inválida → 409; a IA não tem endpoint de aceite (gate é só painel/tenant).
mvn final = relatar contagem REAL do Surefire.

[CONSTRAINTS DUROS]
- Migration única (nº confirmado no arranque). Sem foto/anexo.
- Cliente NÃO é entidade do core — continua o contact (pedido tem conversation_id/contact_id).
- ESCAPADA +18: adega_orders.age_confirmed boolean NOT NULL; backend recusa pedido sem ele (422
  age_not_confirmed); flag persistido pra compliance, visível no painel; declaratório (sem doc/foto
  nesta SM). A trava vale mesmo pra carrinho 100% sem-álcool nesta SM.
- Modifiers planos (Volume/Temperatura) = options com price_delta (espelho comida). Preço final do
  item = base + Σ deltas, × quantity, recalculado no backend (descarta IA). SEM regra do maior valor
  (isso é do pizzaria — adega não tem meio-a-meio).
- subtotal/total/unit_price materializados (não generated). Snapshots de item/opção.
- Gate de aceite humano: nasce 'aguardando'; aceite/recusa no painel; a IA NUNCA aceita/recusa.
- Categorias hardcoded (parity). Tag <pedido_adega> distinta de TODAS as outras.
- Persona NUNCA vende a menor, NUNCA incentiva consumo excessivo, inclui "beba com moderação".
- NÃO mexer em outros perfis nem em system-template.txt. Webhook OFF.
- Cache de cardápio TTL 60s + invalidação em toda mutação do cardápio.
- 529 → inline. Gate 3× → pausar. Working tree sujo → pausar. git add EXPLÍCITO (nunca git add .);
  .env/CONTEXT.md/secrets NUNCA staged.
- SEED com timestamptz: `at time zone 'America/Sao_Paulo'`. IDs de namespace com sufixo NOVO.
- Tabela nova entra na migration ANTES de tocar o banco (lição os_config). Adicionar as tabelas ao
  TRUNCATE/SCRIPTS do AbstractIntegrationTest.
- Decisões menores: agente decide (paleta, layout, label do notificationText).

[PASSO FINAL — TENANT + SEED + COMMIT + PUSH + SMOKE + RELATÓRIO]
F.1 — TENANT igorhaf17 (Adega Modelo, profile=adega), padrão GoTrue, senha em comunicação direta.
      company c?0000000-...-0NN / user a?0000000-...-0NN — CONFIRMAR o próximo nº livre no arranque
      (pizzaria/casamento/padaria podem ter consumido o 017). Caddy + /etc/hosts pra
      adega.meadadigital.local.
F.2 — Seed /tmp/seed-adega.sql (NÃO COMITAR; `at time zone 'America/Sao_Paulo'`; ids com sufixo NOVO
      que não colide com nenhum seed anterior):
  - config: taxa de entrega R$10, pedido mínimo R$50.
  - cardápio: 2 vinhos (Tinto Reserva R$89, Branco Seco R$72), 1 espumante (Brut R$98), 2 cervejas
    (IPA R$18, Pilsen R$12), 1 destilado (Gin R$120), 1 sem_alcool (Suco de Uva R$24), 1 acessorio
    (Saca-rolha R$35); grupo Volume nos que fizerem sentido (375ml -R$ não — usar 750ml base, 1L +R$;
    cerveja 350ml base, 600ml +R$), grupo Temperatura (natural R$0, gelado +R$3) nas cervejas/
    espumante.
  - contact "Bruno Lima" +5511977778888 (VINCULADO: instance+conversation, pra smoke de notificação) +
    contact "Carla Mendes" +5511966667777 (sem vínculo).
  - 3 pedidos cobrindo estados/escapada (TODOS com age_confirmed=true — pedido sem o flag não existe no
    banco):
    * 'aguardando' VINCULADO (Bruno): 1 Tinto Reserva R$89 (1L +R$X) + 1 IPA gelada (+R$3); endereço;
      pra smoke de aceite + cálculo de delta + selo +18.
    * 'em_preparo' (Carla): 1 Brut R$98 gelado + 1 Pilsen; pra smoke de transição.
    * 'entregue' (Bruno, passado): 1 Gin R$120; histórico.
F.3 — JwtFilter /api/adega/ (se ainda não — adicionar ADEGA_PATH_PREFIX e na allowlist, espelho do
      PIZZARIA_PATH_PREFIX já presente).
F.4-F.6 — git add EXPLÍCITO dos arquivos da SM + sanity (sem .env/secrets/CONTEXT) + commit. Mensagem
      padrão (feat(camada-8): perfil adega/Adega (camada 8.x) com FUNDAÇÃO/BACKEND/FRONTEND/DECISÕES/
      VALIDAÇÃO contagem REAL/NÃO TOCADO/FECHAMENTO + Co-Authored-By: Claude Opus 4.8). Tag
      fase-8.x-fechada (nº real confirmado no arranque).
F.7 — git push origin main + tags.
F.8 — docker compose restart backend + aguardar /admin/me → 401.
F.9 — Smoke E2E:
  BLOCO A: auth — igorhaf17 (ou o tenant confirmado) → /admin/me → role=tenant_admin,
    profileId=adega, productName=Adega.
  BLOCO B: cardápio + guard — GET menu (itens+opções); CRUD smoke + invalida cache; delete em uso 409;
    GET config + PUT; tenant comida (igorhaf16 ou outro) → /api/adega/menu → 403.
  BLOCO C: pedido NORMAL (com +18) — simular <pedido_adega> com age_confirmed=true, 1 IPA gelada via
    handler → pedido 'aguardando' + total = 18 +3(gelado) + taxa; total da IA (se enviado errado)
    descartado; age_confirmed=true persistido.
  BLOCO D: TRAVA +18 [CHAVE — a escapada] —
    - <pedido_adega> SEM age_confirmed (ou age_confirmed=false) → 422 age_not_confirmed, NENHUM pedido
      criado (provar contagem de adega_orders inalterada — não cria parcial)
    - <pedido_adega> com age_confirmed=true → cria normal e persiste o flag (consultar a coluna)
    - option_id inválido (com age_confirmed=true) → não cria pedido (empty)
    - snapshots de item/opção preservados após editar o preço no cardápio
  BLOCO E: gate de aceite + status — PATCH aguardando→em_preparo (Bruno vinculado) = ACEITAR → 200 +
    msg outbound; aguardando→recusado com motivo → 200 + msg defensiva; saiu_entrega; entregue;
    transição inválida → 409; a IA não tem rota de aceite.
  BLOCO F: regressão — perfis anteriores intactos (smoke leve 1 endpoint cada); adega → /api/comida/*
    → 403; adega → /api/pizzaria/* → 403.
  BLOCO G: paridade — mvn test -Dtest=AdegaOrderStatusParityTest,AdegaCategoryParityTest,
    ProfileTypeParityTest → verde.
  Cleanup smoke + restaurar seed pristine. mvn final: contagem REAL.
F.10 — RELATÓRIO + DESTAQUE EXPLÍCITO:
  - "perfil adega — camada 8.x; CLONA o COMIDA (cardápio+carrinho+tag+gate de aceite)"
  - "ESCAPADA +18: flag age_confirmed (boolean NOT NULL), backend recusa pedido sem ele (422
    age_not_confirmed); declaratório, persistido pra compliance"
  - "BLOCO D prova a TRAVA: pedido sem age_confirmed → 422, nenhum pedido criado; com o flag → cria e
    persiste"
  - "BLOCO E prova o gate de aceite humano (aceitar/recusar no painel; IA não aceita/recusa)"
  - "categorias próprias (vinhos/espumantes/cervejas/destilados/sem_alcool/acessorios), distintas do
    comida"
  - "persona: NUNCA vende a menor, NUNCA incentiva consumo, 'beba com moderação'"
  - "Seed: at time zone + sufixo de ids novo"
  - "tabelas criadas DENTRO da migration (lição os_config)"
  - PENDÊNCIAS: clube/assinatura de vinho, validação documental de idade, dispensa +18 p/ carrinho
    sem-álcool, cupom/fidelidade, Zé Delivery/iFood, rastreio, Stripe, foto + dívida acumulada
    (webhook, cliente real, olho humano sobre os verticais).

[REPORTAR]
Igual SMs anteriores. Incluir EXPLICITAMENTE:
- "ProfileType.ADEGA adicionado (camada 8.x)"
- "Paridade AdegaOrderStatus, AdegaCategory e ProfileType validadas"
- "Tenant criado (GoTrue + Caddy/etc/hosts) — nº confirmado no arranque"
- "ESCAPADA +18: adega_orders.age_confirmed, backend recusa sem ele (422 age_not_confirmed),
  persistido pra compliance"
- "BLOCO D: pedido sem age_confirmed → 422 e nenhum pedido criado; com o flag → cria/persiste;
  snapshots preservados; option inválido aborta"
- "Gate de aceite humano (espelho comida): IA confirma recebimento, loja aceita/recusa"
- "Persona com trava de venda a menor + 'beba com moderação'"
- "Tag <pedido_adega> distinta de <pedido_comida>/<pedido_flor>/<pedido_pizza> e de todas as outras"
- "OutboundService ganhou maybeProcessPedidoAdega (encadeado após os demais)"
- "getNavForProfile('adega') com branch próprio (não cair no fallback)"
- "Cache de cardápio TTL 60s + invalidação em toda mutação"
- "Migration: nº PROVISÓRIO (50 já é pizzaria; casamento/padaria disputam o slot) — confirmado no
  arranque; CHECK apendado à lista mais recente"
- "tabelas criadas DENTRO da migration (lição os_config); seed com at time zone + sufixo novo"
- "Próximas fases: clube de vinho/assinatura, validação documental de idade, dispensa +18 p/
  carrinho sem-álcool, cupom/fidelidade, Zé Delivery, Stripe + fila de prioridade"
