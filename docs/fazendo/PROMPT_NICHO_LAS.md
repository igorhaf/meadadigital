>>> SLOT ATRIBUÍDO (ver docs/prompts-nicho/README.md — tem precedência sobre qualquer "provisório" no
>>> corpo): ordem 18 · camada 8.23 · migration 67_las.sql · tenant igorhaf34 (sufixo -034) · ids de seed
>>> sufixo -21x. Reconfirmar no arranque.

[TAREFA — SUB-MARATONA: PERFIL LAS / Lãs (Loja de lãs · Tricô · Crochê) (camada 8.23)]

[CONTEXTO]
PROJETO MEADA em /home/igorhaf/meada.
Monolito multi-tenant que se apresenta como N produtos verticais ("perfis"). Perfis são HARDCODED em
dois arquivos espelhados — enum Java `ProfileType` (src/main/java/com/meada/profiles/
ProfileType.java) + const TS `profile-type.ts` (frontend/lib/profiles/profile-type.ts) — e o
`ProfileTypeParityTest` falha o build se divergirem. NÃO existe tabela de perfis; cada tenant tem
EXATAMENTE 1 perfil (`companies.profile_id`, CHECK constraint). Backend Spring Boot 3.3.13 + Java 17 +
JdbcTemplate (não JPA, sem Lombok). Frontend Next 16 (app router) + React 19 + Tailwind 4 + shadcn/ui +
@base-ui/react + TanStack Query + @supabase/ssr. Banco/Auth: Supabase (Postgres 17 + RLS via
`app.company_id()`). IA: Gemini Flash. Migration própria em supabase/migrations/.

Lê CONTEXT.md (gitignored, mais detalhado) e o filesystem no arranque pra cravar convenções, nº de
migration REAL, contagem do Surefire e numeração de tenant ANTES de escrever qualquer código. NÃO
hardcodar a contagem do mvn — relatar a REAL do Surefire ao final. Valores do SLOT (acima) têm
precedência; CONFIRMAR no filesystem que a fila não avançou (maior nº em supabase/migrations/ e maior
`igorhafN` já provisionado) — se avançou, deslocar conforme o README.

LAS é template de nicho pra LOJA DE LÃS / TRICÔ / CROCHÊ dentro do mesmo dashboard Meada. Tenant acessa
las.meadadigital.local e vê o produto "Lãs". A IA atende clientes via WhatsApp, conhece o catálogo de
materiais de artesanato (novelos de lã, linhas, agulhas, kits, acessórios), MONTA o pedido NA CONVERSA
(carrinho relido do histórico a cada turno, igual sushi/comida — sem entidade de carrinho), e quando o
cliente confirma emite a tag `<pedido_las>`. Confirma SEMPRE com o valor total, avisa que o pedido vai
pra CONFIRMAÇÃO DA LOJA (gate de aceite humano). Tom acolhedor e prestativo — público de artesanato,
gente que tricota/faz crochê; a IA fala a língua deles (novelo, agulha, ponto, fio).

>>> TRAVA DE COMPORTAMENTO DA IA (cravada) <<<
- NUNCA inventa produto, cor, dye lot (lote de tingimento), preço ou disponibilidade fora do catálogo.
- NUNCA garante "mesmo lote" se não há estoque suficiente NAQUELE lote — avisa que pode haver leve
  variação de tom entre lotes (é a regra de ouro do nicho; ver EVOLUÇÃO ESTRUTURAL).
- NUNCA vende sem estoque — se a quantidade pedida de uma cor não cabe no estoque total da cor (somando
  os lotes), a IA informa o disponível; quem trava de fato é o backend (decremento transacional →
  409 out_of_stock).
- NUNCA aceita NEM recusa o pedido — é AÇÃO HUMANA da loja no painel (gate de aceite). A IA só CONFIRMA
  o RECEBIMENTO ("seu pedido foi enviado pra loja") na própria mensagem.
- O total é SEMPRE recalculado pelo sistema — a IA pode somar pra orientar, mas o backend descarta o
  total da IA e recalcula a partir do catálogo (preço da variante × quantidade).
- Pode orientar quantidade aproximada de novelos por tipo de peça SE a loja cadastrar essa info no
  campo de descrição/rendimento do produto, mas NUNCA inventa rendimento ("um cachecol leva ~3 novelos")
  sem dado cadastrado — encaminha pra loja confirmar.

EVOLUÇÃO ESTRUTURAL — CLONA o esqueleto order-based do COMIDA/ADEGA (catálogo + carrinho-na-conversa +
tag de pedido + total recalculado + taxa/mínimo + Kanban + gate de aceite humano) e adiciona um chassi
de VARIANTES de produto com ESTOQUE por variante e decremento transacional. A escapada REAL é o
DYE LOT (lote de tingimento):

  ESCAPADA — VARIANTE (cor × dye_lot) com ESTOQUE POR LOTE e regra "MESMO LOTE PREFERENCIAL":
  Novelos de lã têm um LOTE DE TINGIMENTO (dye lot / "partida"). Novelos da MESMA cor mas de LOTES
  diferentes têm tonalidade levemente diferente — quem tricota uma peça inteira precisa de novelos do
  MESMO lote, senão a peça fica com manchas de tom. Por isso a variante NÃO é só "cor": é (cor ×
  dye_lot), cada uma com seu próprio `stock_quantity`. Quando o cliente pede N novelos de uma cor, o
  sistema/IA tenta atender do MESMO lote (a partida com estoque suficiente sozinha pra cobrir a qty). Se
  NENHUM lote único cobre a qty (precisa misturar partidas), o sistema marca o item do pedido com
  `same_lot_guaranteed = false` e AVISA o cliente da possível variação de tom. Se um lote cobre →
  `same_lot_guaranteed = true` e o pedido aponta pra ESSE lote. É a regra que diferencia das outras
  lojas de variante (lingerie/roupa só teriam cor/tamanho sem essa restrição de homogeneidade entre
  unidades). Quantidade importa MUITO: vende-se por novelo, o cliente compra 8, 10, 12 novelos.

NÃO TEM nesta SM (registrado pra não inventar):
- Pagamento real (Stripe é #50, fase futura).
- Foto/upload de produto ou de carta de cores (bloqueador SERVICE_ROLE_KEY — cor é texto + nome do
  lote; amostra de cor é fase futura).
- Calculadora de rendimento AUTOMÁTICA (quantos novelos pra um suéter tamanho M) — a IA só repassa
  rendimento se a loja cadastrou texto no produto; cálculo por gramatura/metragem é fase futura.
- Reserva de estoque com EXPIRAÇÃO (segurar 6 novelos por 30 min) — o decremento acontece na criação do
  pedido (gate de aceite humano depois); TTL de reserva pré-pedido é fase futura.
- Combo/cupom/fidelidade, multi-loja, controle de fornecedor/compra, integração com marketplace.

DECISÕES CRAVADAS (revisor decidiu pelo Igor):
1. CLONA o esqueleto order-based do COMIDA/ADEGA (config taxa+mínimo, catálogo, carrinho-na-conversa,
   tag de pedido, total recalculado, snapshot de nome+preço, Kanban, gate de aceite humano). MANTER
   onde não conflita.
2. VARIANTES novas (cor × dye_lot) com `stock_quantity` por lote — NÃO existem no comida/adega. O item
   do pedido aponta pra UMA variante (não pro produto direto). Snapshot de cor+lote+preço no item.
3. ESTOQUE com DECREMENTO TRANSACIONAL: criar o pedido decrementa `stock_quantity` da variante DENTRO da
   transação, com UPDATE condicional `where stock_quantity >= :qty` — 0 linhas afetadas → estoque
   esgotou na corrida → 409 out_of_stock (sem pedido parcial). Espelho do padrão de saldo do EsteticaBot
   (decremento condicional na mesma transação), mas aqui sobre estoque de variante.
4. REGRA MESMO LOTE: ao montar o item, o backend escolhe o lote da cor que cobre a qty sozinho (preferir
   o de menor estoque restante que ainda cobre, pra consolidar partidas — ou o primeiro que cobre; o
   agente decide e documenta). Cobre → `same_lot_guaranteed = true`, decrementa SÓ esse lote. Nenhum
   lote único cobre mas o TOTAL da cor cobre → `same_lot_guaranteed = false`, consome de >1 lote
   (decrementa em ordem até completar) e o item carrega o aviso. Total da cor não cobre → 409
   out_of_stock.
5. Categorias hardcoded próprias (CHECK + enum Java + const TS + parity): novelos, linhas, agulhas,
   kits, acessorios.
6. Gate de aceite humano: pedido nasce 'aguardando'; aceite/recusa no painel; a IA NUNCA aceita/recusa.
   'aguardando' NÃO notifica (a IA já confirmou o recebimento).
7. Tag `<pedido_las>` (namespace próprio, distinta de `<pedido_comida>`/`<pedido_adega>`/`<pedido_flor>`/
   `<pedido_pizza>` e de TODAS as outras). Carrega itens (produto + cor + quantidade) + endereço; o
   backend resolve a variante/lote, valida estoque e recalcula tudo.
8. profile_id = 'las'; productName = 'Lãs'; subdomain = 'las'. Paleta: agente escolhe (sugestão
   acolhedora: 'lavanda', 'salvia' ou 'terracota').

[FUNDAÇÃO — migration 67_las.sql]
Espelhar 47_comida.sql / 53_adega.sql inteiro (RLS enable+force, policies via app.company_id(),
grants authenticated + service_role; orders/order_items são INSERT pelo BACKEND via service_role — o
pedido é criado pela IA via PedidoLasConfirmHandler; tenant só SELECT/UPDATE no Kanban / gate de aceite).
- ALTER companies CHECK aceitar 'las' — ⚠ ACRESCENTAR 'las' à lista PRESERVANDO TODOS os perfis
  existentes (generic, legal, dental, sushi, restaurant, salon, pousada, academia, pet, oficina, nutri,
  barbearia, eventos, estetica, comida, floricultura, pizzaria, adega, … + os da fila já fechados antes
  desta SM). NÃO clonar por `sed s/x/las/g` (apaga os outros). DEPOIS de escrever, CONFERIR que o CHECK
  tem TODOS os perfis + 'las', não só 'las' (lição cravada no CLAUDE.md).
- subtotal_cents/total_cents/unit_price_cents MATERIALIZADOS no INSERT; NÃO colunas geradas (o recálculo
  cruza linhas/tabelas — generated não serve; lição os_config / migrations anteriores).
- SNAPSHOTS: nome do produto + cor + dye_lot + preço em las_order_items. Alterar/excluir produto ou
  variante no catálogo NÃO altera pedidos passados.
- Tabelas:
  * las_config — taxa de entrega + pedido mínimo (1:1 com company). Clone comida_config. Ausente →
    taxa/mínimo = 0.
  * las_products — CATÁLOGO (o "produto base", sem cor/lote). (id, company_id, category text CHECK in
    sync com enum, name CHECK 1..200, description text nullable, base_price_cents int >= 0 (preço de
    referência; a variante pode ter price próprio — ver nota), composition text nullable (composição/
    metragem/gramatura/rendimento — texto livre informativo, ex.: "100% lã merino, 100g, ~200m, rende
    ~1 cachecol"), active default true, timestamps). Comment cravando que o estoque NÃO fica aqui — fica
    nas variantes.
  * las_variants — VARIANTE (cor × dye_lot), a escapada. (id, company_id denormalizado p/ RLS direta,
    product_id references las_products on delete cascade, color text NOT NULL CHECK 1..80, dye_lot text
    NOT NULL CHECK 1..60 (a "partida"; ex.: "L2403-A"), stock_quantity int NOT NULL default 0 CHECK
    >= 0, price_cents int NOT NULL CHECK >= 0 (preço POR NOVELO/unidade desta variante — pode divergir
    do base se a cor for premium; a IA/total usa este), sku text nullable, active default true,
    timestamps). UNIQUE (product_id, color, dye_lot) — não duplica a mesma partida da mesma cor. Index
    por (product_id, color) where active. Comment cravando dye_lot + regra "mesmo lote preferencial".
  * las_orders — pedidos. status CHECK ('aguardando'|'em_preparo'|'saiu_entrega'|'entregue'|'recusado'|
    'cancelado') default 'aguardando' (espelho comida); subtotal/delivery_fee/total materializados;
    delivery_address text NOT NULL; rejection_reason nullable (gate de aceite); conversation_id/
    contact_id NOT NULL references; notes; created_at + status_updated_at. Clone comida_orders.
  * las_order_items — itens do pedido com snapshot. (id, order_id on delete cascade, product_id
    references las_products on delete restrict, variant_id references las_variants on delete restrict,
    quantity int CHECK > 0, unit_price_cents int (snapshot do price da variante), product_name_snapshot
    text NOT NULL, color_snapshot text NOT NULL, dye_lot_snapshot text NOT NULL (snapshot do lote
    atendido), same_lot_guaranteed boolean NOT NULL (a flag da escapada — true = atendido de 1 lote só;
    false = misturou lotes, há aviso de variação de tom), lot_note text nullable (aviso defensivo
    materializado, ex.: "Atendido de 2 partidas — pode haver leve variação de tom")). on delete restrict
    em variant_id/product_id → variante/produto com pedido não pode ser hard-deletado (409
    variant_in_use / product_in_use).
- Status do pedido hardcoded (LasOrderStatus enum Java + const TS + parity test). Mesmo funil do comida:
    aguardando → em_preparo, recusado
    em_preparo → saiu_entrega, cancelado
    saiu_entrega → entregue, cancelado
    entregue/recusado/cancelado → terminal
  (aceite = aguardando→em_preparo é o gate humano.) Notifica: em_preparo (aceito), saiu_entrega,
  entregue, recusado (com motivo defensivo). aguardando NÃO notifica (a IA já confirmou o recebimento);
  cancelado conforme padrão comida.
- Categorias hardcoded (LasCategory.java + las-categories.ts + LasCategoryParityTest): novelos, linhas,
  agulhas, kits, acessorios.
- TODAS as tabelas novas entram na migration 67 ANTES de tocar o banco (lição os_config) e no TRUNCATE/
  SCRIPTS do AbstractIntegrationTest.

[BACKEND]
- Produtos + variantes: CRUD de las_products (com composition) + CRUD de las_variants (cor + dye_lot +
  stock_quantity + price + sku) aninhado ao produto. Cache do bloco de catálogo injetado no prompt
  (Caffeine TTL 60s), INVALIDADO em toda gravação/edição/exclusão de produto OU variante (a IA vê
  estoque/lote na hora). delete de produto/variante com pedido → 409 product_in_use / variant_in_use
  (on delete restrict).
- Config: GET (fallback taxa/mínimo = 0) + PUT. Espelho comida.
- Orders: criados pelo BACKEND via PedidoLasConfirmHandler. Algoritmo de criação (a parte delicada),
  TUDO numa transação:
    1. Pra cada linha {product_id, color, quantity}: resolver as VARIANTES ativas daquele produto+cor.
    2. Regra MESMO LOTE: procurar UM lote (variante) cuja stock_quantity >= quantity →
       same_lot_guaranteed = true; decrementa SÓ esse lote (UPDATE condicional
       `set stock_quantity = stock_quantity - :qty where id = :variantId and stock_quantity >= :qty`;
       0 linhas → 409 out_of_stock, corrida).
    3. Se NENHUM lote único cobre, somar o estoque dos lotes da cor: total >= quantity →
       same_lot_guaranteed = false; consumir de vários lotes em ordem (cada decremento condicional;
       qualquer 0-linhas no meio → rollback + 409 out_of_stock); o item aponta o lote PRINCIPAL (o de
       maior consumo) no snapshot e materializa lot_note de aviso. total < quantity → 409 out_of_stock.
    4. unit_price = price_cents da variante atendida (o lote escolhido); subtotal = Σ (unit_price ×
       quantity); total = subtotal + (delivery_fee se aplicável). DESCARTA o total da IA.
    5. pedido mínimo: espelhar comida/adega.
    6. Snapshots completos (product_name, color, dye_lot, same_lot_guaranteed, lot_note). product/color/
       variante inválida ou estoque insuficiente aborta SEM pedido parcial (transação inteira).
- Status: PATCH com validação de transição (inválida → 409 invalid_status_transition) + gate de aceite
  (aguardando→em_preparo = aceitar; aguardando→recusado = recusar com rejection_reason). Notificação
  outbound por status (texto defensivo). Cancelamento DEVOLVE estoque? NÃO nesta SM (decisão: o cancelado
  pós-decremento não repõe automaticamente — repor é ajuste manual de estoque; documentar no NÃO TEM /
  fase futura). [O agente confirma essa decisão no arranque; se o revisor preferir devolução, espelhar
  o "devolver sessão" do EsteticaBot — mas o default cravado aqui é NÃO devolver auto.]
- IA:
  * Persona acolhedora-prestativa (público de artesanato) com a TRAVA DE COMPORTAMENTO embutida (não
    inventa produto/cor/lote/preço, não garante mesmo lote sem estoque, não vende sem estoque, não
    aceita/recusa, total recalculado, rendimento só se cadastrado).
  * Contexto injetado = bloco de catálogo (produtos por categoria, com composition/rendimento + as
    VARIANTES de cada um: cor, dye_lot, estoque disponível e preço por novelo) + taxa/mínimo +
    instruções da tag <pedido_las>. A IA enxerga estoque por lote pra orientar honestamente ("temos 8
    novelos azul-marinho na partida L2403, dá pra fazer sua peça no mesmo tom"). Cache TTL 60s (IGNORA
    conversationId — contexto é o catálogo). Invalidação em toda mutação de produto/variante.
  * Tag <pedido_las>{"endereco":"...","items":[{"product_id","color","quantity"}],"notes":"...|null"} →
    PedidoLasConfirmHandler (espelho PedidoComidaConfirmHandler — a IA passa product_id + cor + qtd; o
    backend resolve o lote pela regra de mesmo-lote, NÃO a IA). Best-effort; o OutboundService REMOVE a
    tag antes de enviar e o backend valida + recalcula + decrementa.
  * JwtFilter autentica /api/las/ (espelho do bloco COMIDA_PATH_PREFIX em
    admin/security/JwtAuthenticationFilter.java). OutboundService ganha maybeProcessPedidoLas
    (best-effort, encadeado após os outros perfis — perfil é único, só um age).
- Guard: LasProfileGuard.requireLas (403 forbidden_wrong_profile) — espelho ComidaProfileGuard.

[FRONTEND]
- /dashboard/las-products (CRUD produtos + EDITOR DE VARIANTES inline: por produto, uma lista de
  variantes cor × dye_lot com estoque e preço por novelo + sku; adicionar/editar/remover variante;
  campo composition/rendimento no produto),
  /dashboard/las-orders (Kanban por status com o GATE DE ACEITE: Aceitar/Recusar na coluna 'aguardando',
  recusa pede motivo; o detalhe mostra, por item, produto + cor + LOTE atendido + quantidade + o AVISO
  de variação de tom quando same_lot_guaranteed = false),
  /dashboard/las-settings (taxa de entrega + pedido mínimo).
- types + SDKs (products, variants, orders) espelhando comida/adega + os campos de variante/lote/
  same_lot_guaranteed.
- Status TS las-order-status.ts + LasCategory const + parity tests (status + categorias).
- getNavForProfile('las') injeta "Lãs" (3 itens: Produtos, Pedidos, Configurações), no mesmo padrão dos
  branches existentes (comida/floricultura/pizzaria já têm branch em nav-config.tsx — seguir o modelo:
  `if (profileId === 'las') return [LAS_GROUP, ...NAV_GROUPS]` + const LAS_GROUP). Ícone de novelo/tesoura
  do lucide (ex.: Scissors / Package). Subdomínio las.meadadigital.local. Paleta: 'lavanda', 'salvia' ou
  'terracota' (agente escolhe).
- npm build limpo (next build de prod é a verdade — Turbopack dev esconde import quebrado).

[DOCS]
- CLAUDE.md: seção "## Perfil Lãs (camada 8.23)" espelhando as seções de perfil existentes + nota de que
  CLONA o COMIDA/ADEGA (order-based + gate de aceite) e inaugura: VARIANTE (cor × dye_lot), estoque por
  lote com decremento transacional (409 out_of_stock), e a regra "mesmo lote preferencial"
  (same_lot_guaranteed). Documentar EXPLÍCITO: categorias próprias; a tag <pedido_las>; que a IA NÃO
  escolhe o lote (o backend resolve). Editar somente ACRESCENTANDO — não tocar nas seções de outros
  perfis.
- docs/PERFIL_LAS.md: guia operacional (catálogo com variantes cor × lote; o que é dye lot e por que
  importa; estoque por partida; pedidos + Kanban + gate de aceite; o aviso de variação de tom; como a IA
  atende; "o que a IA NÃO faz"). Espelhar docs/PERFIL_PADARIA.md / PERFIL_FLORICULTURA.md no formato.
- NÃO mexer em system-template.txt nem em outros perfis.

[TESTES BACKEND]
Espelhar a suíte do comida/adega (service + controller integration por entidade):
- LasOrderStatusParityTest + LasCategoryParityTest + ProfileTypeParityTest → verdes.
- LasProductServiceTest + ControllerIntegrationTest (CRUD produto + variantes; invalida cache;
  delete-em-uso 409 product_in_use / variant_in_use; wrongProfile 403).
- LasConfigServiceTest/ControllerIntegrationTest (GET fallback + PUT).
- LasOrderServiceTest [CHAVE da escapada]:
    * DECREMENTO transacional: criar pedido reduz stock_quantity da variante; ler de volta confere.
    * out_of_stock: qty pedida > estoque total da cor → 409 out_of_stock, SEM pedido parcial (estoque
      intacto após o erro).
    * REGRA MESMO LOTE — cabe: existe 1 lote com estoque >= qty → same_lot_guaranteed = true; decrementa
      SÓ esse lote; dye_lot_snapshot = o lote escolhido; lot_note null.
    * REGRA MESMO LOTE — NÃO cabe mas total cobre: nenhum lote único cobre, soma dos lotes cobre →
      same_lot_guaranteed = false; consome de >1 lote; lot_note de aviso materializado; estoques dos
      lotes envolvidos batem.
    * TOTAL recalculado: unit_price = price da variante; subtotal/total batem; total da IA DESCARTADO.
    * SNAPSHOT de lote PRESERVADO: alterar/excluir a variante depois NÃO muda o dye_lot_snapshot/
      color_snapshot/unit_price do item de pedido.
    * variant/product/cor inválida → aborta (sem pedido).
- PedidoLasConfirmHandlerTest: tag com 1 cor que cabe em 1 lote (same_lot true); tag com qty que mistura
  lotes (same_lot false + aviso); tag com qty > estoque → empty; product/cor inexistente → empty; sem
  endereço → empty; sem tag → empty; total bate.
- Status/gate: aguardando→em_preparo (aceite) → saiu_entrega → entregue; aguardando→recusado(motivo);
  transição inválida → 409; a IA não tem endpoint de aceite.
mvn final = relatar contagem REAL do Surefire.

[CONSTRAINTS DUROS]
- Migration única (67_las.sql). Sem foto/anexo (cor é texto + nome do lote; amostra de cor é fase
  futura — bloqueador SERVICE_ROLE_KEY).
- Cliente NÃO é entidade do core — continua o contact (pedido tem conversation_id/contact_id).
- ESCAPADA: variante (cor × dye_lot) com stock_quantity por lote; decremento TRANSACIONAL condicional →
  409 out_of_stock; regra mesmo-lote-preferencial (cabe → same_lot_guaranteed=true; não cabe → false +
  aviso lot_note); a IA NÃO escolhe o lote, o backend resolve.
- subtotal/total/unit_price materializados (não generated). Snapshots de produto/cor/lote/preço +
  same_lot_guaranteed + lot_note no item.
- Gate de aceite humano: nasce 'aguardando'; aceite/recusa no painel; a IA NUNCA aceita/recusa.
- Categorias hardcoded (parity): novelos, linhas, agulhas, kits, acessorios. Tag <pedido_las> distinta de
  TODAS as outras.
- CHECK de companies.profile_id ACRESCENTA 'las' PRESERVANDO todos os perfis (conferir após escrever —
  lição cravada). ProfileType (Java) + profile-type.ts (TS) + ProfileTypeParityTest.
- NÃO mexer em outros perfis nem em system-template.txt. Webhook OFF (não religar).
- Cache de catálogo TTL 60s + invalidação em toda mutação de produto/variante.
- 529 → inline. Gate 3× → pausar. Working tree sujo → pausar. git add EXPLÍCITO (nunca git add .);
  .env/CONTEXT.md/secrets NUNCA staged.
- SEED com timestamptz/date: `at time zone 'America/Sao_Paulo'`. IDs de namespace com sufixo -21x
  (NOVO — conferir no arranque que não colide com seeds anteriores).
- Tabela nova entra na migration ANTES de tocar o banco (lição os_config). Adicionar as tabelas ao
  TRUNCATE/SCRIPTS do AbstractIntegrationTest. Pool de teste mínimo já está em
  src/test/resources/application-dev.yml (lição SM-K) — não remexer.
- Decisões menores: agente decide (paleta, layout do editor de variantes, ícone do nav, ordem de
  consumo de lotes quando mistura, se devolve estoque no cancelamento — default cravado: NÃO devolve).

[PASSO FINAL — TENANT igorhaf34 + SEED + COMMIT + PUSH + SMOKE + RELATÓRIO]
F.1 — TENANT igorhaf34 (Loja de Lãs Modelo, profile=las), padrão GoTrue, senha em comunicação direta.
      company c?0000000-...-034 / user a?0000000-...-034. Caddy + /etc/hosts pra las.meadadigital.local.
F.2 — Seed /tmp/seed-las.sql (NÃO COMITAR; `at time zone 'America/Sao_Paulo'`; ids de namespace sufixo
      -21x):
  - config: taxa de entrega R$10, pedido mínimo R$30.
  - catálogo:
    * "Lã Merino 100g" (novelos, base R$28, composition "100% lã merino, 100g, ~200m") com VARIANTES:
        - Azul-marinho, lote "L2403-A", estoque 10, R$28/novelo, sku LM-AZ-2403A
        - Azul-marinho, lote "L2407-B", estoque 4,  R$28/novelo, sku LM-AZ-2407B
        - Vinho,        lote "L2405-C", estoque 6,  R$30/novelo, sku LM-VN-2405C
    * "Linha de Crochê Anne" (linhas, base R$9, composition "100% algodão, 500m") com VARIANTES:
        - Cru,  lote "A101", estoque 20, R$9/novelo
        - Rosa, lote "A102", estoque 3,  R$9/novelo
    * "Agulha de Tricô nº8" (agulhas, base R$22, sem variante de cor → 1 variante "Único"/lote "-"
      estoque 15, R$22) — mostra que produto sem cor real ainda passa pelo chassi de variante.
    * "Kit Iniciante Crochê" (kits, base R$75) — 1 variante "Único"/lote "-" estoque 5.
  - contact "Marta Tricô" +5511955554444 (VINCULADO: instance+conversation, sufixo -21x) + contact
    "Helena Crochê" +5511944443333 (sem vínculo).
  - pedidos cobrindo estados/escapada:
    * 'aguardando' VINCULADO (Marta): 8 novelos Lã Merino Azul-marinho → CABE no lote L2403-A (estoque
      10) → same_lot_guaranteed = true, dye_lot_snapshot "L2403-A". Total = 8×28 + taxa10 = R$234. Pra
      smoke de mesmo-lote-cabe + aceite.
    * 'em_preparo' (Helena): 12 novelos Lã Merino Azul-marinho → NÃO cabe num lote (10+4=14 total,
      nenhum lote único cobre 12) → same_lot_guaranteed = false, lot_note de aviso, consome L2403-A(10)
      + L2407-B(2). Pra smoke de mistura-de-lotes (após o seed, estoque azul = 0 + 2).
      [NOTA: pra esse seed bater com o decremento, ou (a) inserir o pedido com estoques JÁ decrementados
      coerentes, ou (b) seedar via a mesma rota de criação. O agente decide; o importante é que o smoke
      C/D prove a regra criando pedidos NOVOS, não só lendo o seed.]
    * 'saiu_entrega' (Marta): 2 novelos Vinho lote L2405-C, same_lot true; pra notificação de saiu.
    * 'entregue' (Marta, passado): histórico.
F.3 — JwtFilter /api/las/ (se ainda não).
F.4-F.6 — git add EXPLÍCITO dos arquivos da SM + sanity (git status -s + diff --staged --stat + grep por
      segredo eyJ.../password/secret + confirmar .env/.env.local/CONTEXT.md FORA da staging) + commit.
      Mensagem padrão (feat(camada-8.23): perfil las/Lãs (loja de lãs · tricô · crochê) — variante
      cor×dye_lot + estoque por lote + mesmo-lote-preferencial; com FUNDAÇÃO/BACKEND/FRONTEND/DECISÕES/
      VALIDAÇÃO contagem REAL/NÃO TOCADO/FECHAMENTO + Co-Authored-By: Claude Opus 4.8). Tag
      fase-8.23-fechada (nº real confirmado no arranque), anotada, apontando pro commit que fecha.
F.7 — git push origin main + git push origin --tags. NUNCA --force.
F.8 — docker compose restart backend + aguardar /admin/me → 401 missing_auth_header.
F.9 — Smoke E2E (blocos A–G):
  BLOCO A: auth — igorhaf34 → /admin/me → role=tenant_admin, profileId=las, productName=Lãs.
  BLOCO B: catálogo + guard — GET produtos (com variantes cor×lote+estoque+preço); CRUD smoke de produto
    e variante + invalida cache; delete em uso → 409 product_in_use/variant_in_use; GET config + PUT;
    tenant de outro perfil (ex.: comida) → /api/las/products → 403 forbidden_wrong_profile.
  BLOCO C: VARIANTES cor×lote + MESMO LOTE CABE [CHAVE] — <pedido_las> 8 novelos Azul-marinho (estoque
    L2403-A=10) → pedido 'aguardando', same_lot_guaranteed = true, dye_lot_snapshot "L2403-A",
    estoque do lote vira 2; total = 8×28 + taxa = 234; total da IA descartado.
  BLOCO D: MESMO LOTE NÃO CABE + DECREMENTO MULTI-LOTE [CHAVE] — repor estoque; <pedido_las> 12 novelos
    Azul-marinho (L2403-A=10, L2407-B=4; nenhum único cobre 12) → same_lot_guaranteed = false, lot_note
    de aviso presente, consome 2 lotes; estoques batem.
  BLOCO E: OUT_OF_STOCK — <pedido_las> 100 novelos Vinho (estoque total 6) → 409 out_of_stock, SEM pedido
    criado, estoque intacto.
  BLOCO F: gate de aceite + funil — aguardando→em_preparo (aceite, Marta vinculada) → 200 + msg;
    em_preparo→saiu_entrega → msg; saiu_entrega→entregue → msg; outro pedido aguardando→recusado(motivo)
    → msg defensiva; transição inválida → 409 invalid_status_transition; a IA não tem rota de aceite.
  BLOCO G: regressão + paridade — perfis anteriores intactos (smoke leve 1 endpoint cada); las →
    /api/comida/* → 403; mvn test -Dtest=LasOrderStatusParityTest,LasCategoryParityTest,
    ProfileTypeParityTest → verde. + lição os_config: confirmar que TODAS as tabelas las_* existem no
    banco (foram criadas DENTRO da migration, não ad-hoc).
  Cleanup smoke + restaurar seed pristine. mvn final: contagem REAL do Surefire.
F.10 — RELATÓRIO + DESTAQUE EXPLÍCITO:
  - "perfil las/Lãs (loja de lãs · tricô · crochê) — CLONA o COMIDA/ADEGA (order-based + gate de aceite)"
  - "ESCAPADA: variante (cor × dye_lot) com estoque por lote + decremento TRANSACIONAL (409 out_of_stock)"
  - "regra MESMO LOTE PREFERENCIAL: cabe num lote → same_lot_guaranteed=true; não cabe → false + aviso
     de variação de tom (lot_note), consome múltiplos lotes"
  - "BLOCO C prova mesmo-lote-cabe; BLOCO D prova mistura de lotes; BLOCO E prova out_of_stock"
  - "BLOCO F prova o gate de aceite humano + o funil"
  - "categorias próprias (novelos/linhas/agulhas/kits/acessorios)"
  - "Seed: at time zone + sufixo de ids -21x; tabelas DENTRO da migration (lição os_config)"
  - PENDÊNCIAS: foto/amostra de cor, calculadora de rendimento automática, reserva com expiração,
     devolução de estoque no cancelamento, combo/cupom/fidelidade, Stripe + dívida acumulada (webhook,
     cliente real como entidade, olho humano sobre os verticais).

[REPORTAR]
Igual SMs anteriores. Incluir EXPLICITAMENTE:
- "ProfileType.LAS adicionado (camada 8.23) — CHECK de companies.profile_id ACRESCENTA 'las'
  preservando todos os perfis (conferido)"
- "Paridade LasOrderStatus, LasCategory e ProfileType validadas"
- "Tenant igorhaf34 criado (GoTrue + Caddy + /etc/hosts las.meadadigital.local)"
- "ESCAPADA: variante cor × dye_lot com estoque por lote; decremento transacional condicional → 409
  out_of_stock; regra mesmo-lote-preferencial (same_lot_guaranteed + lot_note)"
- "subtotal/total/unit_price materializados; snapshots de produto/cor/lote/preço + same_lot_guaranteed"
- "Gate de aceite humano (espelho comida): IA confirma recebimento, loja aceita/recusa"
- "Tag <pedido_las> distinta de <pedido_comida>/<pedido_adega>/<pedido_flor>/<pedido_pizza> e das outras"
- "OutboundService ganhou maybeProcessPedidoLas; JwtFilter autentica /api/las/"
- "getNavForProfile('las') com branch próprio (Produtos/Pedidos/Configurações)"
- "Cache de catálogo TTL 60s + invalidação em toda mutação de produto/variante"
- "tabelas criadas DENTRO da migration (lição os_config); seed com at time zone + sufixo -21x novo"
- "contagem REAL do Surefire (mvn -B clean test verde)"
- "Próximas fases: foto/amostra de cor, rendimento automático, reserva com expiração, devolução de
   estoque no cancelamento, Stripe"
