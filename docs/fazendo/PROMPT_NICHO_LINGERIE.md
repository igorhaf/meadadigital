>>> SLOT ATRIBUÍDO (ver docs/prompts-nicho/README.md — tem precedência sobre qualquer "provisório"
>>> no corpo): ordem 16 · camada 8.21 · migration 65_lingerie.sql · tenant igorhaf32 (sufixo -032) ·
>>> ids de seed sufixo -19x. Reconfirmar no arranque que a fila não avançou; se avançou, deslocar
>>> conforme o README.

[TAREFA — SUB-MARATONA: PERFIL LINGERIE / Lingerie (Moda íntima) (camada 8.21)]

[CONTEXTO]
PROJETO MEADA em /home/igorhaf/meada.
Vários perfis verticais reais hoje (… comida 8.4, floricultura 8.5, pizzaria 8.6, e a fila de nichos
em sequência) + generic. Lê CONTEXT.md e o filesystem no arranque pra cravar convenções, nº de
migration, contagem do Surefire e numeração de tenant ANTES de escrever qualquer código. NÃO
hardcodar a contagem do mvn — relatar a REAL do Surefire ao final. Valores do SLOT (CONFIRMAR no
filesystem antes; o disco tem GAPS na numeração de migration — o maior nº presente manda): migration
65_lingerie.sql, tenant igorhaf32, company c?0000000-…-032, user a?0000000-…-032. IDs de namespace
compartilhado (contacts/instance/conversation) NO SEED com sufixo NOVO -19x que NÃO colida com
nenhum seed anterior.

Lingerie é template de nicho pra LOJA DE VAREJO DE MODA ÍNTIMA (lingerie) dentro do mesmo dashboard
Meada. Tenant acessa lingerie.meadadigital.local e vê o produto "Lingerie". A IA atende clientes via
WhatsApp, conhece o catálogo (sutiãs, calcinhas, pijamas, conjuntos, acessórios), MONTA o pedido NA
CONVERSA (carrinho relido do histórico a cada turno, igual sushi/comida), e — como produto íntimo
tem GRADE de variantes — escolhe junto com o cliente o TAMANHO e a COR (variante) disponíveis em
ESTOQUE. Confirma SEMPRE com o valor total, avisa que o pedido vai pra CONFIRMAÇÃO DA LOJA. Tom
acolhedor, respeitoso e DISCRETO.

>>> TRAVA DE COMPORTAMENTO DA IA (cravada) <<<
- É acolhedora e RESPEITOSA — produto íntimo. DISCRIÇÃO total: NUNCA comenta o corpo do cliente,
  NUNCA opina sobre aparência/medidas/"ficaria bem em você", NUNCA faz piada ou insinuação. Atende
  como uma vendedora educada de loja física: foca no produto, no tamanho e na cor.
- NUNCA inventa produto, tamanho, cor ou preço fora do catálogo. Só oferece o que está cadastrado.
- NUNCA vende uma variante SEM ESTOQUE. Só mostra/oferece variantes com stock_quantity > 0; se a
  combinação tamanho×cor que o cliente quer está zerada, informa e oferece as disponíveis.
- NUNCA "adivinha" o tamanho do cliente. Se houver dúvida de tamanho, SUGERE consultar a TABELA DE
  MEDIDAS da loja (texto/orientação cadastrado na config) — não recomenda um tamanho com base em
  descrição do corpo.
- NUNCA aceita NEM recusa o pedido — é AÇÃO HUMANA da loja no painel (gate de aceite). A IA só
  CONFIRMA o RECEBIMENTO ("seu pedido foi enviado pra loja, já já confirmam pra você") na própria
  mensagem.
- O total é SEMPRE recalculado pelo sistema — a IA pode somar pra orientar, mas o backend descarta o
  total da IA e recalcula a partir do catálogo (preço da VARIANTE escolhida).

EVOLUÇÃO ESTRUTURAL: CLONA o chassi order-based do COMIDA (camada 8.4) / ADEGA — cardápio→CATÁLOGO +
carrinho-na-conversa + tag de pedido + recálculo de total (descarta o total da IA) + snapshot de
preço/nome + taxa de entrega/pedido mínimo + Kanban de status + GATE DE ACEITE HUMANO (pedido nasce
'aguardando'; loja ACEITA→'em_separacao' ou RECUSA→'recusado' com rejection_reason; a IA não
aceita/recusa). MAS substitui os "modifiers planos" (comida_menu_item_options) por uma GRADE DE
VARIANTES — é o chassi NOVO desta SM:

  ESCAPADA ESTRUTURAL — CHASSI DE LOJA DE VAREJO COM GRADE DE VARIANTES (TAMANHO × COR) E ESTOQUE POR
  VARIANTE: o produto (lingerie_products) é o "guarda-roupa" — categoria, nome, descrição, preço base.
  Cada produto tem N VARIANTES (lingerie_variants), uma por combinação TAMANHO × COR, cada variante
  com seu PRÓPRIO stock_quantity (estoque, int >= 0), um SKU opcional e um price_cents (pode herdar o
  do produto OU ter override por variante — ex.: GG mais caro). O pedido referencia uma VARIANTE
  específica (variant_id), NÃO o produto. A grande diferença para o comida: o backend DECREMENTA o
  ESTOQUE da variante na criação do pedido, TRANSACIONALMENTE e CONDICIONAL (UPDATE … set
  stock_quantity = stock_quantity - qty where id = ? and stock_quantity >= qty; se 0 linhas afetadas →
  409 out_of_stock, aborta o pedido inteiro). A IA só vende variante com estoque > 0 e mostra
  tamanhos/cores disponíveis. SNAPSHOT no item do pedido: nome do produto + tamanho + cor + preço
  unitário do momento (alterar/excluir produto/variante depois NÃO altera pedidos passados).
  Tamanhos são HARDCODED com parity (LingerieSize: pp,p,m,g,gg,unico); a COR é texto livre cadastrado
  por variante (não é enum — cada loja tem sua paleta: "preto", "vinho", "nude", "rosa-chá").

  RETIRADA × ENTREGA: o pedido tem fulfillment ('retirada'|'entrega'). 'retirada' = balcão, sem taxa
  e sem endereço obrigatório; 'entrega' = exige delivery_address e soma delivery_fee. (Espelho da
  padaria/adega — opcional; se preferir simplicidade, manter só 'entrega' como comida. RECOMENDO
  incluir fulfillment com parity, é varejo e a retirada na loja é comum. O agente decide; documentar.)

NÃO TEM nesta SM (registrado pra não inventar): pagamento real (Stripe é #50), foto/upload de produto
(bloqueador SERVICE_ROLE_KEY — catálogo é só texto; cor é palavra, não swatch de imagem), troca/
devolução com fluxo de RMA/status próprio (a troca de moda íntima tem regra sanitária — fica de fora
desta SM, é fase futura), reserva de estoque com EXPIRAÇÃO (o estoque é decrementado direto na criação
do pedido — não há "carrinho que segura a peça por 10 min" nem job de expiração; carrinho vive só na
conversa), grade de medidas estruturada com cálculo de tamanho (a tabela de medidas é texto/orientação
na config — a IA NÃO calcula tamanho do cliente), combo/cupom/fidelidade, lista de desejos, integração
com marketplace (Shopee/ML), curva ABC/relatório de giro de estoque, multi-loja/multi-depósito,
variante com 3+ eixos (só tamanho × cor — modelo/coleção é nome do produto). Fases futuras.

DECISÕES CRAVADAS (revisor decidiu pelo Igor):
1. CLONA o chassi order-based do COMIDA/ADEGA (catálogo + carrinho + tag + total recalculado + taxa/
   mínimo + Kanban + gate de aceite). MANTER onde não conflita. A diferença é a GRADE DE VARIANTES no
   lugar dos modifiers planos.
2. Categorias hardcoded próprias (CHECK + enum Java + const TS + parity): sutia, calcinha, pijama,
   conjunto, acessorios.
3. Tamanhos hardcoded próprios (CHECK + enum Java + const TS + parity LingerieSize): pp, p, m, g, gg,
   unico. COR é TEXTO LIVRE por variante (não enum — cadastrada na grade).
4. ESCAPADA — produto (lingerie_products) com N variantes (lingerie_variants) tamanho×cor; estoque +
   preço POR VARIANTE; pedido referencia variant_id; backend DECREMENTA estoque transacional condicional
   (409 out_of_stock). UNIQUE (product_id, size, color) por variante — não pode duas linhas pra mesma
   combinação. price override por variante (nullable → herda o do produto).
5. Snapshot no item do pedido: product_name + size + color + unit_price (= preço da variante no momento).
6. fulfillment 'retirada'|'entrega' (hardcoded; parity OU CHECK simples — agente decide): retirada sem
   taxa/endereço; entrega com.
7. Gate de aceite humano: nasce 'aguardando'; aceite/recusa no painel; a IA não aceita/recusa.
   'aguardando' NÃO notifica (a IA já confirmou o recebimento).
8. Tag <pedido_lingerie> (namespace próprio, distinta de <pedido_comida>/<pedido_flor>/<pedido_pizza>/
   <pedido_adega> e de TODAS as outras). Carrega itens por VARIANTE (variant_id + quantity) +
   fulfillment + endereço (quando entrega); o backend valida estoque, decrementa e recalcula tudo.

[FUNDAÇÃO — migration 65_lingerie.sql]
- ALTER companies CHECK aceitar 'lingerie' — ACRESCENTAR à lista preservando TODOS os perfis existentes
  (lição CRAVADA: clonar por sed troca a lista inteira; CONFERIR que o CHECK final tem TODOS os perfis
  anteriores + 'lingerie', não só 'lingerie').
- RLS enable+force, policies via app.company_id(), grants authenticated + service_role
  (orders/order_items: INSERT pelo BACKEND via service_role — o pedido é criado pela IA via
  PedidoLingerieConfirmHandler; tenant só SELECT/UPDATE — status no Kanban / gate de aceite). Espelhar
  47_comida.sql inteiro (config, catálogo, orders, order_items) trocando "modifiers" por "variants".
- subtotal_cents/total_cents/unit_price_cents MATERIALIZADOS no INSERT; NÃO colunas geradas.
- SNAPSHOTS: product_name + size + color + unit_price em lingerie_order_items. Alterar/excluir produto
  ou variante no catálogo NÃO altera pedidos passados.
- Tabelas:
  * lingerie_config — taxa de entrega + pedido mínimo + size_guide text nullable (texto/orientação da
    TABELA DE MEDIDAS, exibida pela IA quando o cliente tem dúvida de tamanho). 1:1 com company;
    ausente → taxa/mínimo = 0, size_guide null. Clone comida_config + size_guide.
  * lingerie_products — catálogo (id, company_id, category CHECK in sync com enum
    (sutia,calcinha,pijama,conjunto,acessorios), name CHECK 1..120, description, price_cents = preço
    BASE >= 0, active boolean default true, timestamps). Comment cravando "preço base; variante pode
    sobrescrever". Sem foto (bloqueador SERVICE_ROLE_KEY).
  * lingerie_variants — ESCAPADA: grade de variantes do produto. (id, company_id (denormalizado p/ RLS
    direta), product_id references lingerie_products on delete cascade, size text CHECK in sync com
    LingerieSize (pp,p,m,g,gg,unico), color text NOT NULL check length 1..40 (TEXTO LIVRE),
    price_cents integer NULLABLE (override; null → herda o do produto — o backend resolve no recálculo),
    stock_quantity integer NOT NULL default 0 check (stock_quantity >= 0), sku text nullable,
    active boolean default true, sort_order, timestamps). UNIQUE (product_id, size, color). Index
    (product_id, sort_order) where active. Comment cravando "estoque POR variante; pedido decrementa
    transacional".
  * lingerie_orders — pedidos. status CHECK ('aguardando'|'em_separacao'|'pronto'|'retirado'|
    'saiu_entrega'|'entregue'|'recusado'|'cancelado') default 'aguardando' (funil com os DOIS desfechos
    retirada × entrega — ver nota de status); fulfillment text CHECK ('retirada'|'entrega'); subtotal/
    delivery_fee/total materializados; delivery_address text NULLABLE (obrigatório só p/ entrega —
    validado no backend); rejection_reason nullable (gate de aceite); conversation_id/contact_id NOT
    NULL; notes; timestamps + status_updated_at. Espelho comida_orders + fulfillment + status de varejo.
  * lingerie_order_items — itens do pedido com snapshot. (id, order_id references lingerie_orders on
    delete cascade, variant_id references lingerie_variants on delete restrict, qtd integer check qtd>0,
    unit_price_cents integer NOT NULL (snapshot, = preço resolvido da variante), product_name_snapshot
    text NOT NULL, size_snapshot text NOT NULL, color_snapshot text NOT NULL). Comment: snapshots; o
    item referencia a VARIANTE; variant_id on delete restrict → variante com pedido não pode ser
    hard-deletada (409 variant_in_use). NÃO há tabela-filha de opções (a variante É a escolha).
- Status do pedido hardcoded (LingerieOrderStatus enum Java + const TS + parity test). Funil com os
  DOIS desfechos (retirada × entrega):
    aguardando   → em_separacao, recusado, cancelado
    em_separacao → pronto, cancelado
    pronto       → retirado (retirada), saiu_entrega (entrega), cancelado
    saiu_entrega → entregue, cancelado
    retirado/entregue/recusado/cancelado → terminal
  (aceite = aguardando→em_separacao é o gate humano.) Notifica: em_separacao (aceito), pronto ("seu
  pedido está pronto pra retirada"), saiu_entrega, entregue, recusado (com motivo defensivo).
  aguardando/cancelado conforme padrão (aguardando silencioso — a IA já confirmou).
- fulfillment hardcoded (LingerieFulfillment enum Java + const TS + parity OU só CHECK — agente decide;
  recomendo enum+parity p/ simetria): retirada, entrega.
- Categorias hardcoded (LingerieCategory.java + lingerie-categories.ts + LingerieCategoryParityTest):
  sutia, calcinha, pijama, conjunto, acessorios.
- Tamanhos hardcoded (LingerieSize.java + lingerie-sizes.ts + LingerieSizeParityTest): pp, p, m, g, gg,
  unico.
- TODAS as tabelas novas entram na migration 65 ANTES de tocar o banco (lição os_config) e no TRUNCATE/
  SCRIPTS do AbstractIntegrationTest.

[BACKEND]
- Catálogo: CRUD de PRODUTOS (categoria + nome + descrição + preço base) + CRUD de VARIANTES por produto
  (size ∈ LingerieSize + color texto livre + stock_quantity + price override nullable + sku) — espelho
  comida/comida_menu_item_options adaptado pra grade. Edição de estoque por variante (set/ajuste). Cache
  do bloco de catálogo injetado no prompt (Caffeine TTL 60s, IGNORA conversationId — contexto é o
  catálogo), INVALIDADO em toda gravação/edição/exclusão de produto OU variante OU estoque. delete de
  produto com pedido (via variante) → 409 product_in_use; delete de variante com pedido → 409
  variant_in_use; preferir desativar (active=false).
- Config: GET (fallback taxa/mínimo = 0, size_guide null) + PUT (taxa, mínimo, size_guide).
- Orders: criados pelo BACKEND via PedidoLingerieConfirmHandler. Recálculo + DECREMENTO TRANSACIONAL:
    * resolve cada variant_id → preço efetivo = variant.price_cents ?? product.price_cents; valida
      active=true e stock_quantity >= qty.
    * DENTRO da MESMA transação do INSERT do pedido: pra cada item, UPDATE lingerie_variants set
      stock_quantity = stock_quantity - qty where id = ? and stock_quantity >= qty; se rowsAffected == 0
      → lança OutOfStockException → 409 out_of_stock (com o variant_id/produto/tamanho/cor sem estoque),
      e a transação INTEIRA faz rollback (sem pedido parcial, sem decremento parcial). Defesa de corrida:
      o WHERE condicional fecha a janela entre o cache da IA e a persistência.
    * unit_price = preço efetivo da variante; subtotal = Σ (unit_price × qtd); total = subtotal +
      (entrega ? delivery_fee : 0). O total da IA é DESCARTADO.
    * fulfillment 'entrega' → delivery_address obrigatório (senão 422 address_required) + soma taxa;
      'retirada' → sem taxa, endereço pode ser null.
    * pedido mínimo: espelhar comida (subtotal < min_order → erro padrão do chassi).
    * variant_id inválido/inativo aborta (sem pedido parcial). Snapshots completos (product_name + size
      + color + unit_price).
- Status: PATCH com validação de transição (inválida → 409 invalid_status_transition) + gate de aceite
  (aguardando→em_separacao = aceitar; aguardando→recusado = recusar com rejection_reason). Notificação
  outbound por status (texto defensivo, discreto). NOTA: cancelar/recusar um pedido NÃO devolve o
  estoque nesta SM (decisão cravada — estorno de estoque por cancelamento é fase futura junto com
  troca/devolução; documentar EXPLÍCITO no NÃO TEM e no PERFIL).
- IA:
  * Persona acolhedora-DISCRETA-respeitosa com a TRAVA DE COMPORTAMENTO embutida (não comenta o corpo,
    não inventa produto/tamanho/cor/preço, não vende sem estoque, não adivinha tamanho — sugere a tabela
    de medidas, não aceita/recusa, total recalculado).
  * Contexto injetado = bloco de catálogo (produtos por categoria + por produto a GRADE DE VARIANTES
    DISPONÍVEIS: tamanho × cor com ESTOQUE > 0 e preço efetivo) + taxa/mínimo + size_guide (tabela de
    medidas) + instruções da tag <pedido_lingerie>. Variantes com estoque 0 ou inativas NÃO entram no
    bloco (a IA literalmente não as vê pra oferecer). Cache TTL 60s. Invalidação em toda mutação de
    catálogo/estoque.
  * Tag <pedido_lingerie>{"fulfillment":"retirada|entrega","delivery_address":"…|null","items":
    [{"variant_id","quantity"}],"notes":"…|null"} → PedidoLingerieConfirmHandler (espelho
    PedidoComidaConfirmHandler — mas o item carrega variant_id, NÃO menu_item_id + options). Best-effort;
    o OutboundService REMOVE a tag antes de enviar e o backend valida estoque + decrementa + recalcula.
  * JwtFilter autentica /api/lingerie/** (adicionar LINGERIE_PATH_PREFIX = "/api/lingerie/" + o guard,
    junto dos demais perfis). OutboundService ganha maybeProcessPedidoLingerie (best-effort, encadeado
    após os outros perfis — perfil é único, só um age; somar à cadeia toSend = … após maybeProcessPedido
    Adega).
  * LingerieProfileGuard.requireLingerie (403 forbidden_wrong_profile) — espelho ComidaProfileGuard.

[FRONTEND]
- /dashboard/lingerie-products (CRUD produtos + EDITOR DE GRADE DE VARIANTES inline: por produto, uma
  matriz/lista tamanho × cor onde se adiciona variante (escolhe size do enum + digita color + estoque +
  preço override opcional + sku), edita estoque, ativa/desativa — esta é a tela da escapada),
  /dashboard/lingerie-orders (Kanban por status com o GATE DE ACEITE: Aceitar/Recusar na coluna
  'aguardando', recusa pede motivo; o detalhe mostra fulfillment, endereço quando entrega, e por item o
  produto + tamanho + cor + quantidade + preço),
  /dashboard/lingerie-settings (taxa de entrega + pedido mínimo + tabela de medidas / size_guide).
- types + SDKs (products, variants, orders) espelhando comida + variante (size/color/stock/price/sku).
- Status TS lingerie-order-status.ts + LingerieCategory const + LingerieSize const + LingerieFulfillment
  const + parity tests (status + categorias + tamanhos [+ fulfillment se enum]).
- getNavForProfile('lingerie') injeta "Lingerie" (3 itens: Produtos, Pedidos, Configurações), no mesmo
  padrão dos branches existentes (comida/floricultura/pizzaria já têm branch em nav-config.tsx — seguir
  o modelo: criar LINGERIE_GROUP + `if (profileId === 'lingerie') return [LINGERIE_GROUP, ...NAV_GROUPS]`).
  Rotas /dashboard/lingerie-*. Subdomínio lingerie.meadadigital.local. Paleta: agente escolhe —
  sugestão 'rosa-po', 'orquidea' ou 'ameixa' (tons discretos/elegantes coerentes com o nicho).
- npm build limpo (Turbopack dev esconde import quebrado — o build de prod é a verdade).

[DOCS]
- CLAUDE.md: seção "## Perfil Lingerie (camada 8.21)" espelhando as seções de perfil + nota de que
  CLONA o COMIDA/ADEGA (order-based + gate de aceite) e INAUGURA o chassi de LOJA DE VAREJO COM GRADE DE
  VARIANTES (tamanho × cor, estoque por variante, decremento transacional condicional → 409
  out_of_stock). Documentar EXPLÍCITO: categorias próprias (sutia/calcinha/pijama/conjunto/acessorios);
  tamanhos hardcoded (LingerieSize) com cor texto livre; a tag <pedido_lingerie> (item por variant_id);
  trava de discrição; estoque NÃO estornado em cancelamento (fase futura). Registrar a lição de
  clonagem por sed no CHECK de companies.profile_id (acrescentar, não substituir).
- docs/PERFIL_LINGERIE.md: guia operacional (catálogo de produtos + grade de variantes com estoque;
  pedidos + Kanban + gate de aceite; retirada × entrega; tabela de medidas; como a IA atende com
  discrição; "o que a IA NÃO faz"). Espelhar PERFIL_PADARIA.md / PERFIL_FLORICULTURA.md.
- NÃO mexer em system-template.txt nem em outros perfis.

[TESTES BACKEND]
Espelhar a suíte do comida/adega (service + controller integration por entidade):
- LingerieOrderStatusParityTest + LingerieCategoryParityTest + LingerieSizeParityTest +
  ProfileTypeParityTest (+ fulfillment parity se enum).
- LingerieCatalogServiceTest + ControllerIntegrationTest (CRUD produto + variantes; ajuste de estoque;
  invalida cache; delete-em-uso 409 product_in_use/variant_in_use; UNIQUE (product,size,color);
  wrongProfile 403).
- LingerieConfigServiceTest/ControllerIntegrationTest (GET fallback + PUT; size_guide).
- LingerieOrderServiceTest [CHAVE da escapada]:
    * pedido OK → decrementa o estoque da variante (estoque antes − qtd = estoque depois).
    * variante com estoque insuficiente (qtd > stock) → 409 out_of_stock; pedido NÃO criado e estoque
      INTACTO (rollback total — testar que NENHUM item foi decrementado num pedido multi-item onde o
      2º item estoura).
    * variante inativa/variant_id inválido → aborta (sem pedido parcial).
    * total recalculado: unit_price = variant.price ?? product.price; total da IA DESCARTADO; price
      override por variante aplicado.
    * snapshot de variante preservado (product_name + size + color + unit_price) mesmo após alterar/
      desativar a variante no catálogo.
    * fulfillment entrega sem address → 422 address_required + soma taxa; retirada sem taxa/endereço.
- PedidoLingerieConfirmHandlerTest: tag com variantes válidas (decrementa + total bate); tag com
  variante sem estoque → empty (não cria); variant_id inválido → empty; sem tag → empty.
- Status/gate: aguardando→em_separacao (aceite) → pronto → retirado (retirada) E pronto→saiu_entrega→
  entregue (entrega); recusado(motivo); transição inválida → 409; a IA não tem endpoint de aceite.
mvn final = relatar contagem REAL do Surefire.

[CONSTRAINTS DUROS]
- Migration única (65_lingerie.sql). Sem foto/anexo (catálogo só texto; cor é palavra). Sem Stripe.
- Cliente NÃO é entidade do core — continua o contact (pedido tem conversation_id/contact_id).
- ESCAPADA: grade de variantes tamanho×cor; estoque + preço por variante; pedido por variant_id;
  decremento transacional CONDICIONAL (where stock_quantity >= qty) → 409 out_of_stock; rollback total
  sem pedido/decremento parcial. UNIQUE (product_id, size, color).
- Tamanhos hardcoded (parity LingerieSize); cor texto livre. Categorias hardcoded (parity). fulfillment
  retirada × entrega (retirada sem taxa/endereço).
- subtotal/total/unit_price materializados (não generated). Snapshots de produto+tamanho+cor+preço.
- Gate de aceite humano: nasce 'aguardando'; aceite/recusa no painel; a IA NUNCA aceita/recusa.
- Estoque NÃO é estornado em cancelamento/recusa nesta SM (documentar — fase futura).
- Trava de DISCRIÇÃO na persona (sem comentário sobre o corpo; não adivinha tamanho; sugere tabela de
  medidas). Tag <pedido_lingerie> distinta de TODAS as outras.
- NÃO mexer em outros perfis nem em system-template.txt. Webhook OFF.
- Cache de catálogo TTL 60s + invalidação em toda mutação de catálogo/estoque.
- 529 → inline. Gate 3× → pausar. Working tree sujo → pausar. git add EXPLÍCITO (nunca git add .);
  .env/CONTEXT.md/secrets NUNCA staged.
- SEED com timestamptz/date: `at time zone 'America/Sao_Paulo'`. IDs de namespace com sufixo NOVO -19x.
- Tabela nova entra na migration ANTES de tocar o banco (lição os_config). Adicionar as tabelas ao
  TRUNCATE/SCRIPTS do AbstractIntegrationTest.
- ACRESCENTAR 'lingerie' ao CHECK de companies.profile_id preservando TODOS os perfis (lição sed) —
  conferir a lista final.
- Decisões menores: agente decide (paleta, layout do editor de grade, se fulfillment vira enum+parity
  ou só CHECK).

[PASSO FINAL — TENANT + SEED + COMMIT + PUSH + SMOKE + RELATÓRIO]
F.1 — TENANT igorhaf32 (Lingerie Modelo, profile=lingerie), padrão GoTrue, senha em comunicação direta.
      company c?0000000-…-032 / user a?0000000-…-032. Caddy + /etc/hosts pra lingerie.meadadigital.local.
F.2 — Seed /tmp/seed-lingerie.sql (NÃO COMITAR; `at time zone 'America/Sao_Paulo'`; ids de namespace
      sufixo -19x, ids de seed do catálogo/pedidos com sufixo -19x):
  - config: taxa de entrega R$12, pedido mínimo R$50, size_guide = texto curto de tabela de medidas
    ("PP=busto 76-80cm; P=80-86; M=86-92; G=92-98; GG=98-104 — em dúvida, me diga sua medida que eu te
    oriento.").
  - catálogo (produtos + variantes com estoque):
    * "Sutiã Renda Clássico" (sutia, base R$89,90) — variantes: P/preto estoque 5, M/preto estoque 3,
      M/nude estoque 0 (ESGOTADA, pra smoke out_of_stock / IA não oferece), G/vinho estoque 2 com
      price override R$99,90.
    * "Calcinha Algodão" (calcinha, base R$29,90) — P/branco 10, M/branco 8, G/preto 4.
    * "Pijama Cetim" (pijama, base R$159,90) — M/rosa-chá 2, G/ameixa 1.
    * "Conjunto Renda" (conjunto, base R$149,90) — M/preto 2, P/vinho 3.
    * "Meia-calça" (acessorios, base R$19,90) — unico/preto 20.
  - contact "Beatriz Souza" +5511955554444 (VINCULADO: instance+conversation) + contact "Larissa Pinto"
    +5511944443333 (sem vínculo).
  - pedidos cobrindo estados/escapada:
    * 'aguardando' VINCULADO (Beatriz) ENTREGA: 1× Sutiã P/preto + 2× Calcinha M/branco, endereço →
      subtotal = 89,90 + 2×29,90 = 149,70 + taxa 12 = R$161,70; pra smoke do gate de aceite + decremento
      de estoque + entrega.
    * 'em_separacao' (Larissa) RETIRADA: 1× Pijama M/rosa-chá, sem taxa/endereço; pra smoke do funil
      retirada (→pronto→retirado).
    * 'pronto' (Beatriz) ENTREGA: 1× Conjunto M/preto; pra smoke de notificação 'pronto' + saiu_entrega.
    * 'entregue' (Beatriz, passado) histórico.
F.3 — JwtFilter /api/lingerie/ (se ainda não) + LingerieProfileGuard.
F.4-F.6 — git add EXPLÍCITO dos arquivos da SM + sanity (sem .env/secrets/CONTEXT) + commit.
      Mensagem padrão (feat(camada-8.21): perfil lingerie/Lingerie (Moda íntima) com FUNDAÇÃO/BACKEND/
      FRONTEND/DECISÕES/VALIDAÇÃO contagem REAL/NÃO TOCADO/FECHAMENTO + Co-Authored-By: Claude Opus 4.8).
      Tag fase-8.21-fechada (nº real confirmado no arranque).
F.7 — git push origin main + tags.
F.8 — docker compose restart backend + aguardar /admin/me → 401.
F.9 — Smoke E2E (blocos A–G):
  BLOCO A: auth — igorhaf32 → /admin/me → role=tenant_admin, profileId=lingerie, productName=Lingerie.
  BLOCO B: catálogo + guard — GET produtos (com variantes: tamanho/cor/estoque/preço efetivo); CRUD
    smoke de produto+variante + ajuste de estoque + invalida cache; UNIQUE (product,size,color) ao tentar
    duplicar; delete em uso 409 (product_in_use/variant_in_use); GET config + PUT (size_guide); tenant de
    OUTRO perfil (comida/floricultura) → /api/lingerie/products → 403.
  BLOCO C: CATÁLOGO COM VARIANTES — o bloco de contexto da IA mostra SÓ variantes com estoque > 0 e
    ativas (M/nude esgotada NÃO aparece); preço efetivo aplica o override (G/vinho R$99,90).
  BLOCO D: PEDIDO DECREMENTA ESTOQUE [CHAVE] —
    - <pedido_lingerie> 1× Sutiã P/preto + 2× Calcinha M/branco, entrega, endereço → 'aguardando';
      subtotal recalculado (149,70) + taxa; total da IA descartado; estoque DEPOIS: Sutiã P/preto 5→4,
      Calcinha M/branco 10→8; snapshot de produto+tamanho+cor+preço bate.
  BLOCO E: OUT_OF_STOCK [CHAVE] —
    - <pedido_lingerie> 1× Sutiã M/nude (estoque 0) → 409 out_of_stock; pedido NÃO criado; estoque
      INTACTO.
    - pedido multi-item onde o 2º item estoura (qtd > estoque) → 409 out_of_stock; ROLLBACK total
      (NENHUM item decrementado).
    - variant_id inválido/inativo → não cria (empty).
    - entrega sem endereço → 422 address_required; retirada sem endereço → OK.
  BLOCO F: gate de aceite + funis (retirada × entrega) — aguardando→em_separacao (aceite, Beatriz
    vinculada) → 200 + msg; em_separacao→pronto → msg "pronto pra retirada"; pronto→retirado (retirada)
    OK; outro pedido pronto→saiu_entrega→entregue (entrega); aguardando→recusado(motivo) → msg defensiva;
    transição inválida → 409; a IA não tem rota de aceite. Confirmar que cancelar NÃO devolve estoque
    (decisão cravada).
  BLOCO G: regressão + paridade — perfis anteriores intactos (smoke leve 1 endpoint cada); lingerie →
    /api/comida/* → 403; lingerie → /api/floricultura/* → 403. mvn test -Dtest=LingerieOrderStatusParity
    Test,LingerieCategoryParityTest,LingerieSizeParityTest,ProfileTypeParityTest → verde.
  Cleanup smoke + restaurar seed pristine. mvn final: contagem REAL.
  LIÇÃO os_config: conferir que TODAS as tabelas novas (config/products/variants/orders/order_items)
  estão DENTRO da migration 65 e no TRUNCATE do AbstractIntegrationTest ANTES do smoke.
F.10 — RELATÓRIO + DESTAQUE EXPLÍCITO:
  - "perfil lingerie/moda íntima — CLONA o COMIDA/ADEGA (order-based + gate de aceite)"
  - "ESCAPADA: chassi de LOJA DE VAREJO com GRADE DE VARIANTES (tamanho × cor), estoque + preço por
     variante, pedido por variant_id"
  - "decremento de estoque TRANSACIONAL CONDICIONAL (where stock_quantity >= qty) → 409 out_of_stock;
     rollback total sem pedido/decremento parcial (BLOCO E prova)"
  - "tamanhos hardcoded (LingerieSize pp/p/m/g/gg/unico) com cor TEXTO LIVRE por variante; UNIQUE
     (product,size,color)"
  - "fulfillment retirada × entrega; funil de status diverge no fim (retirado vs entregue)"
  - "gate de aceite humano (IA confirma recebimento; loja aceita→em_separacao / recusa)"
  - "trava de DISCRIÇÃO: a IA não comenta o corpo, não adivinha tamanho (sugere a tabela de medidas),
     não vende variante sem estoque"
  - "categorias próprias (sutia/calcinha/pijama/conjunto/acessorios)"
  - "estoque NÃO estornado em cancelamento/recusa (fase futura, junto com troca/devolução)"
  - "Seed: at time zone + sufixo de ids -19x; tabelas DENTRO da migration (lição os_config)"
  - PENDÊNCIAS: foto/swatch de produto, troca/devolução (RMA), reserva de estoque com expiração, estorno
     de estoque no cancelamento, grade de medidas estruturada com cálculo de tamanho, cupom/fidelidade/
     lista de desejos, marketplace, relatório de giro, multi-loja, pagamento real (Stripe #50), + dívida
     acumulada (webhook, cliente real como entidade, olho humano sobre os verticais).

[REPORTAR]
Igual SMs anteriores. Incluir EXPLICITAMENTE:
- "ProfileType.LINGERIE adicionado (camada 8.21)"
- "Paridade LingerieOrderStatus, LingerieCategory, LingerieSize e ProfileType validadas (+ fulfillment
   se enum)"
- "Tenant igorhaf32 criado (GoTrue + Caddy/etc/hosts)"
- "ESCAPADA: grade de variantes tamanho×cor; estoque + preço por variante; pedido por variant_id"
- "Decremento de estoque transacional condicional → 409 out_of_stock; rollback total (BLOCO E)"
- "Snapshot de produto+tamanho+cor+preço no item; estoque NÃO estornado no cancelamento"
- "fulfillment retirada × entrega; funil de status diverge no fim (retirado vs entregue)"
- "Gate de aceite humano (espelho comida): IA confirma recebimento, loja aceita/recusa"
- "Trava de discrição na persona (sem comentário sobre o corpo; sugere tabela de medidas; não vende sem
   estoque)"
- "Tag <pedido_lingerie> distinta de <pedido_comida>/<pedido_flor>/<pedido_pizza>/<pedido_adega> e das
   outras"
- "OutboundService ganhou maybeProcessPedidoLingerie; JwtFilter autentica /api/lingerie/**;
   LingerieProfileGuard"
- "getNavForProfile('lingerie') com branch próprio (Produtos/Pedidos/Configurações)"
- "Cache de catálogo TTL 60s + invalidação em toda mutação de catálogo/estoque"
- "tabelas criadas DENTRO da migration (lição os_config); seed com at time zone + sufixo -19x novo"
- "Próximas fases: foto/swatch, troca/devolução, reserva com expiração, estorno de estoque, cupom/
   fidelidade, marketplace, Stripe + fila de prioridade"
