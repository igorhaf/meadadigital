>>> SLOT ATRIBUÍDO (ver docs/prompts-nicho/README.md — tem precedência sobre qualquer "provisório"
>>> no corpo): ordem 17 · camada 8.22 · migration 66_moda_infantil.sql · tenant igorhaf33 (sufixo -033) ·
>>> ids de seed sufixo -20x. Reconfirmar no arranque.

[TAREFA — SUB-MARATONA: PERFIL MODA_INFANTIL / Moda Infantil (camada 8.22)]

[CONTEXTO]
PROJETO MEADA em /home/igorhaf/meada.
Monolito multi-tenant que se apresenta como N produtos verticais ("perfis"). Cada perfil parece um
produto distinto pro cliente final (subdomínio, nome, tom de IA, features). Perfis são HARDCODED em
dois arquivos espelhados — enum Java `ProfileType.java` + const TS `profile-type.ts` —, validados pelo
`ProfileTypeParityTest`; NÃO há tabela de perfis. Cada tenant tem EXATAMENTE 1 perfil
(`companies.profile_id`, CHECK constraint). Adicionar um perfil = editar os 2 arquivos + uma migration
que ESTENDE o CHECK de `companies.profile_id` PRESERVANDO todos os existentes + rodar a paridade.

Lê CONTEXT.md e o filesystem no arranque pra cravar convenções, nº de migration, contagem do Surefire
e numeração de tenant ANTES de escrever qualquer código. NÃO hardcodar a contagem do mvn — relatar a
REAL do Surefire ao final. Valores do SLOT (confirmar no filesystem; podem ter avançado se outra SM da
fila rodou primeiro): migration **66_moda_infantil.sql**, tenant **igorhaf33**, company c?0000000-…-033,
user a?0000000-…-033, ids de namespace compartilhado (contacts/instance/conversation) no SEED com sufixo
**-20x** (conferir que nenhum seed anterior já usou -20x; se usou, deslocar). Camada **8.22**.

⚠️ NUNCA REMOVER UM NICHO AO ADICIONAR OUTRO (cravado pelo Igor). O novo id `'moda_infantil'` ENTRA na
lista do CHECK `companies.profile_id` e no enum PRESERVANDO TODOS os existentes (clonar a migration mais
recente por sed troca a lista inteira — depois de qualquer clonagem, CONFERIR que o CHECK tem TODOS os
perfis anteriores + `moda_infantil`, não só o novo).

Moda Infantil é template de nicho pra LOJA DE VAREJO DE ROUPA INFANTIL dentro do mesmo dashboard Meada.
Tenant acessa modainfantil.meadadigital.local e vê o produto "Moda Infantil". A IA atende clientes
(os PAIS/responsáveis) via WhatsApp, conhece o catálogo (produtos com GRADE DE VARIANTES tamanho×cor,
cada variante com estoque próprio), MONTA o carrinho NA CONVERSA (relido do histórico a cada turno,
igual sushi/comida), confirma SEMPRE com o valor total e avisa que o pedido vai pra CONFIRMAÇÃO DA LOJA.
O diferencial do nicho: o eixo de TAMANHO é por FAIXA ETÁRIA / IDADE da criança (RN, 3 meses, 1 ano,
2 anos, …) — a IA ajuda os pais escolhendo pela IDADE da criança, mapeando idade→tamanho com a tabela
da loja, SEM nunca adivinhar medida exata. Tom acolhedor com os pais.

>>> TRAVA DE COMPORTAMENTO DA IA (cravada) <<<
- NUNCA inventa produto, tamanho, cor, SKU ou preço fora do catálogo. Só oferece o que existe.
- NUNCA vende variante SEM ESTOQUE. Se a variante (tamanho×cor) tem `stock = 0`, a IA NÃO a oferece;
  se o cliente insistir, explica que está esgotada e sugere outra cor/tamanho disponível. Quem garante
  de fato é o backend (decremento transacional → 409 `out_of_stock` se acabou entre o turno e a
  confirmação).
- NUNCA aceita NEM recusa o pedido — é AÇÃO HUMANA da loja no painel (gate de aceite). A IA só CONFIRMA
  o RECEBIMENTO ("seu pedido foi enviado pra loja, já já confirmam pra você") na própria mensagem.
- O total é SEMPRE recalculado pelo sistema — a IA pode somar pra orientar, mas o backend DESCARTA o
  total da IA e recalcula a partir do `price_cents` da variante × quantidade.
- SUGESTÃO DE TAMANHO PELA IDADE é ORIENTAÇÃO, não garantia: ao mapear idade→tamanho, a IA deixa SEMPRE
  claro que "cada marca veste um pouco diferente" e sugere conferir a tabela de medidas / a circunferência
  real da criança. NUNCA afirma medida exata (cm/cintura) — só faixa etária do mapeamento cadastrado.
- NUNCA opina sobre o corpo/peso/desenvolvimento da criança; sem julgamento estético; sem promessa de
  caimento. Para dúvida de medida fora da tabela → orienta conferir pessoalmente / a loja confirma.

EVOLUÇÃO ESTRUTURAL: CLONA o chassi order-based do COMIDA (camada 8.4) — catálogo + carrinho-na-conversa
+ tag de pedido + recálculo de total (descarta o total da IA) + snapshot de nome/preço no item +
taxa/mínimo + Kanban de status + GATE DE ACEITE HUMANO (pedido nasce 'aguardando'; loja ACEITA→'separando'
ou RECUSA→'recusado' com rejection_reason) — MAS substitui a "opção/adicional" plana do comida por uma
ESCAPADA ESTRUTURAL nova: GRADE DE VARIANTES DE PRODUTO COM ESTOQUE POR VARIANTE.

  >>> ESCAPADA ESTRUTURAL — VARIANTES tamanho×cor COM ESTOQUE + DECREMENTO TRANSACIONAL + EIXO DE
  TAMANHO POR FAIXA ETÁRIA INFANTIL <<<
  É o PRIMEIRO nicho com "loja de varejo com VARIANTES de produto" — o chassi de irmão estrutural direto
  é a LINGERIE (se PROMPT_NICHO_LINGERIE.md existir no diretório no arranque, LEIA-O e espelhe o modelo
  de variantes/estoque, adaptando o eixo de tamanho). Diferenças do comida:
  - Um PRODUTO (`kids_products`) tem N VARIANTES (`kids_variants`): cada variante é a combinação
    TAMANHO (faixa etária) × COR, com `stock_qty` próprio, `price_cents` próprio (pode variar por
    tamanho — RN custa menos que 8 anos) e `sku` próprio (UNIQUE por company). O preço do PEDIDO vem
    da VARIANTE, não do produto pai (o produto pai não tem preço de venda — é o agrupador).
  - O eixo de tamanho é FAIXA ETÁRIA INFANTIL HARDCODED (`KidsSize` enum Java ↔ const TS ↔ parity).
    Grade-padrão brasileira de moda infantil (você crava a lista final; sugestão abaixo):
    `rn` (Recém-nascido), `m3` (3 meses), `m6` (6 meses), `m9` (9 meses), `a1` (1 ano), `a2` (2 anos),
    `a4` (4 anos), `a6` (6 anos), `a8` (8 anos), `a10` (10 anos), `a12` (12 anos). Cada size tem um
    `label` legível ("3 meses", "1 ano") e uma faixa etária pra exibição. A COR é texto livre por
    variante (não hardcoded — marca tem infinitas cores; é só `color` text + opcional `color_hex`).
  - MAPEAMENTO IDADE→TAMANHO: a tabela é DERIVADA do próprio enum `KidsSize` (cada size carrega a faixa
    etária que cobre — ex.: `a2` cobre ~18-30 meses). A IA recebe a IDADE da criança na conversa e
    sugere o `KidsSize` correspondente (resolvido por uma função utilitária `KidsSize.suggestForAgeMonths
    (int months)` — escolhe o size cuja faixa contém a idade; idade fora da grade → maior/menor
    disponível com aviso). É ORIENTAÇÃO (ver TRAVA). Esse mapeamento entra no bloco de CONTEXTO injetado
    no prompt + na persona, e NÃO grava nada — só sugere.
  - DECREMENTO TRANSACIONAL DE ESTOQUE: ao criar o pedido, cada item DECREMENTA `kids_variants.stock_qty`
    da variante pedida, DENTRO da transação, com `UPDATE ... SET stock_qty = stock_qty - :qty WHERE
    id = :variantId AND stock_qty >= :qty` — se o UPDATE afetar 0 linhas (estoque insuficiente entre o
    turno da IA e a confirmação), a transação ABORTA → 409 `out_of_stock` (com a variante/sku/restante
    na resposta, defensivo). NENHUM pedido parcial. (Decremento de saldo na MESMA transação do pedido é
    o espelho do pacote-saldo da estética 8.3, aqui aplicado a estoque de variante.)
  - DEVOLUÇÃO DE ESTOQUE: cancelar/recusar um pedido DEVOLVE o estoque das variantes do pedido
    (`stock_qty = stock_qty + qty` por item), na transição de status, idempotente (só devolve uma vez —
    flag/estado terminal controla). Aceitar não mexe (o estoque já saiu na criação).

NÃO TEM nesta SM (registrado pra não inventar): pagamento real (Stripe é #50), foto/upload de produto
(bloqueador SERVICE_ROLE_KEY — produto e variante são só texto/cor; "imagem" fica como URL colada se
necessário em fase futura), troca/devolução de mercadoria com fluxo de RMA, reserva de estoque com
EXPIRAÇÃO (a variante é decrementada na confirmação, não reservada com TTL), tabela de medidas em cm
estruturada (a sugestão é faixa etária + aviso, não antropometria), carrinho persistente entre sessões
(é relido da conversa), cupom/combo/fidelidade, frete por CEP/transportadora (taxa é fixa por config,
igual comida), multi-loja/estoque por filial, código de barras/leitor, NF-e. Fases futuras.

DECISÕES CRAVADAS (revisor decidiu pelo Igor):
1. CLONA o chassi order-based do COMIDA (catálogo + carrinho + tag + total recalculado + snapshot +
   taxa/mínimo + Kanban + gate de aceite humano). MANTER onde não conflita.
2. Categorias hardcoded próprias (CHECK + enum Java `KidsCategory` + const TS + parity): `bebe`,
   `menino`, `menina`, `calcados`, `acessorios`.
3. ESCAPADA — VARIANTES: `kids_variants` (tamanho×cor) com `stock_qty` + `price_cents` + `sku` por
   variante. O preço de venda é da VARIANTE. Decremento transacional → 409 `out_of_stock`; devolução
   ao cancelar/recusar.
4. Eixo de tamanho hardcoded por FAIXA ETÁRIA: `KidsSize` enum Java + const TS + parity. Cor é texto
   livre por variante. Mapeamento idade→tamanho derivado do enum (`suggestForAgeMonths`).
5. Gate de aceite humano: nasce 'aguardando'; aceite/recusa no painel; a IA NUNCA aceita/recusa.
   'aguardando' NÃO notifica (a IA já confirmou o recebimento).
6. Tag `<pedido_moda_infantil>` (namespace próprio, DISTINTA de `<pedido_comida>`/`<pedido_flor>`/
   `<pedido_pizza>` e de TODAS as outras). Carrega itens por `variant_id` + quantidade + endereço/
   notes; o backend valida, decrementa estoque e recalcula tudo.
7. profile_id `'moda_infantil'`, productName "Moda Infantil", subdomain `'modainfantil'`. Paleta:
   sugestão `'celeste'`, `'coral'` ou `'mostarda'` (agente escolhe — livre).

[FUNDAÇÃO — migration 66_moda_infantil.sql]
- ALTER companies CHECK aceitar 'moda_infantil' PRESERVANDO todos os perfis anteriores (conferir a lista
  completa após a edição — NÃO substituir a lista). Espelhar a estrutura de 47_comida.sql inteira (RLS
  enable+force, policies via app.company_id(), grants authenticated + service_role).
- INSERT de pedidos/itens é do BACKEND via service_role (a IA cria via handler). Tenant só SELECT/UPDATE
  em orders (status no Kanban / gate). Tenant tem CRUD via SDK+RLS em products/variants/config.
- subtotal_cents/total_cents/unit_price_cents MATERIALIZADOS no INSERT; NÃO colunas geradas.
- SNAPSHOTS no item do pedido: nome do produto + tamanho(label) + cor + preço da variante + sku no
  momento. Alterar/excluir produto/variante no catálogo NÃO altera pedidos passados.
- Tabelas:
  * `moda_infantil_config` — taxa de entrega + pedido mínimo (1:1 com company; ausente → 0/0). Clone
    comida_config. (Nome de tabela `moda_infantil_*` — underscore, sem hífen.)
  * `kids_products` — produto pai (agrupador), SEM preço de venda (o preço é da variante). (id,
    company_id, name CHECK 1..120, description, category CHECK in sync com `KidsCategory`
    (bebe/menino/menina/calcados/acessorios), gender_hint nullable (informativo: menino/menina/unissex),
    brand text nullable, active default true, timestamps). Comment cravando "preço é da variante".
  * `kids_variants` — a ESCAPADA. (id, company_id (denormalizado p/ RLS direta), product_id references
    kids_products on delete cascade, size text NOT NULL CHECK in sync com `KidsSize`
    (rn/m3/m6/m9/a1/a2/a4/a6/a8/a10/a12 — crave a grade final), color text NOT NULL (texto livre),
    color_hex text nullable, sku text NOT NULL, price_cents integer NOT NULL check >= 0,
    stock_qty integer NOT NULL default 0 check >= 0, active default true, timestamps).
    UNIQUE (company_id, sku). UNIQUE (product_id, size, color) — uma variante por combinação. Índice
    parcial por (product_id) where stock_qty > 0 and active. Comment cravando estoque por variante +
    decremento transacional + eixo faixa etária.
  * `moda_infantil_orders` — pedidos. status CHECK ('aguardando'|'separando'|'pronto'|'saiu_entrega'|
    'entregue'|'recusado'|'cancelado') default 'aguardando' (ver máquina de status abaixo);
    subtotal/delivery_fee/total materializados; delivery_address text NOT NULL; rejection_reason
    nullable (gate); stock_returned boolean NOT NULL default false (idempotência da devolução);
    conversation_id/contact_id NOT NULL; notes; timestamps + status_updated_at. Clone comida_orders +
    stock_returned.
  * `moda_infantil_order_items` — itens com snapshot. (id, order_id on delete cascade, variant_id
    references kids_variants on delete restrict, product_name_snapshot, size_snapshot, color_snapshot,
    sku_snapshot, unit_price_cents (snapshot do price da variante), quantity int >= 1, line_total_cents
    materializado). Clone comida_order_items adaptado a variante (sem tabela-filha de opções — a
    variante JÁ é a especificação).
- Status do pedido hardcoded (`ModaInfantilOrderStatus` enum Java + const TS + parity). Funil de varejo:
    aguardando   → separando, recusado, cancelado
    separando    → pronto, cancelado
    pronto       → saiu_entrega, entregue, cancelado   (entregue p/ retirada no balcão; saiu_entrega p/ envio)
    saiu_entrega → entregue, cancelado
    entregue/recusado/cancelado → terminal
  (aceite = aguardando→separando é o gate humano.) Notifica: separando (aceito), pronto, saiu_entrega,
  entregue, recusado (motivo defensivo). aguardando silencioso; cancelado conforme padrão.
  DEVOLUÇÃO DE ESTOQUE dispara ao entrar em recusado OU cancelado (guarda por stock_returned).
- Categorias hardcoded (`KidsCategory.java` + `kids-categories.ts` + `KidsCategoryParityTest`):
  bebe, menino, menina, calcados, acessorios.
- Tamanhos hardcoded (`KidsSize.java` + `kids-size.ts` + `KidsSizeParityTest`): rn, m3, m6, m9, a1, a2,
  a4, a6, a8, a10, a12 (crave a grade final; cada um com label + faixa etária em meses pro mapeamento).
- TODAS as tabelas novas entram na migration 66 ANTES de tocar o banco (lição os_config) e no
  TRUNCATE/SCRIPTS do AbstractIntegrationTest.

[BACKEND]
Estrutura em `src/main/java/com/meada/profiles/modainfantil/` (espelho de `comida/`):
- `ModaInfantilProfileGuard.requireModaInfantil` → 403 `forbidden_wrong_profile` p/ tenant de outro
  perfil. `JwtAuthenticationFilter` autentica `/api/moda-infantil/**` (além dos perfis anteriores).
- Catálogo: CRUD de `kids_products` (com category/gender_hint/brand) + CRUD de `kids_variants`
  (size/color/color_hex/sku/price_cents/stock_qty) — espelho do CRUD menu+options do comida, mas a
  variante é a sub-entidade. SKU UNIQUE por company → 409 `sku_taken`. delete de produto com pedido →
  409 `product_in_use`; delete de variante com pedido → 409 `variant_in_use` (preferir desativar
  active=false). Cache do bloco de catálogo injetado no prompt (Caffeine TTL 60s), INVALIDADO em toda
  gravação/edição/exclusão de produto OU variante (IGNORA conversationId — contexto é o catálogo).
- Config: GET (fallback taxa/mínimo = 0) + PUT.
- Orders: criados pelo BACKEND via `PedidoModaInfantilConfirmHandler`. Recálculo:
    * resolve cada `variant_id` → variante ativa do company; variante inexistente/inativa/de outro
      company → aborta (sem pedido parcial).
    * unit_price = `kids_variants.price_cents` (NÃO o que a IA mandar); line_total = unit_price × qty;
      subtotal = Σ line_totals; total = subtotal + delivery_fee.
    * DECREMENTO TRANSACIONAL por item: `UPDATE kids_variants SET stock_qty = stock_qty - :qty WHERE
      id = :variantId AND company_id = :company AND stock_qty >= :qty` — 0 linhas afetadas → lança
      `OutOfStockException` (→ 409 `out_of_stock`, com sku/size/color/disponível). TODA a operação em
      UMA transação: se qualquer item falha, ROLLBACK (nada decrementa, nenhum pedido).
    * pedido mínimo: espelhar comida (subtotal < min → 422 `below_min_order`).
    * delivery_address obrigatório (422 `address_required` se faltar — varejo sempre tem entrega/retirada
      com endereço; manter simples como comida).
    * snapshots completos no item (product_name/size/color/sku/unit_price).
- Status: PATCH com validação de transição (inválida → 409 `invalid_status_transition`) + gate de aceite
  (aguardando→separando = aceitar; aguardando→recusado = recusar com rejection_reason). DEVOLUÇÃO DE
  ESTOQUE ao entrar em recusado/cancelado (guarda por stock_returned, idempotente, na mesma transação
  do UPDATE de status). Notificação outbound por status (texto defensivo).
- IA:
  * Persona acolhedora-com-os-pais com a TRAVA DE COMPORTAMENTO embutida (não inventa produto/tamanho/
    cor/preço, não vende sem estoque, não aceita/recusa, total recalculado, sugestão de tamanho por
    idade é ORIENTAÇÃO com aviso de variação por marca, sem opinar sobre o corpo da criança).
  * Contexto injetado = bloco de catálogo (produtos por categoria, e POR PRODUTO as variantes
    DISPONÍVEIS — tamanho(label) + cor + preço, marcando estoque baixo/esgotado; variante com stock=0
    NÃO é oferecida) + taxa/mínimo + a TABELA DE MAPEAMENTO idade→tamanho (derivada do KidsSize) +
    instruções da tag `<pedido_moda_infantil>`. Cache TTL 60s (IGNORA conversationId). Invalidação em
    toda mutação do catálogo.
  * Tag `<pedido_moda_infantil>{"delivery_address":"…","items":[{"variant_id","quantity"}],"notes":
    "…|null"}` → `PedidoModaInfantilConfirmHandler` (espelho `PedidoComidaConfirmHandler` adaptado a
    variante + decremento). Best-effort; o `OutboundService` REMOVE a tag antes de enviar e o backend
    valida + decrementa + recalcula. A IA referencia a variante pelo `variant_id` que o bloco de
    contexto expõe (a IA NÃO inventa id).
  * `OutboundService` ganha `maybeProcessPedidoModaInfantil` (best-effort, encadeado APÓS os outros
    perfis — perfil é único, só um age; seguir o padrão dos `maybeProcess*` existentes).
  * `ProfilePromptContext` ganha o ramo MODA_INFANTIL (persona + segmento de contexto com o bloco de
    catálogo+variantes+mapeamento idade→tamanho).

[FRONTEND]
Telas em `frontend/app/dashboard/moda-infantil-*` e SDK em `frontend/profiles/moda-infantil/`:
- `/dashboard/moda-infantil-products` — CRUD de produtos COM editor de GRADE DE VARIANTES inline (por
  produto, a lista de variantes tamanho×cor com sku/preço/estoque/ativo; adicionar/editar/desativar
  variante; combinação (tamanho,cor) duplicada bloqueada; sku duplicado mostra 409 sku_taken).
- `/dashboard/moda-infantil-orders` — Kanban por status com o GATE DE ACEITE: Aceitar/Recusar na coluna
  'aguardando' (recusa pede motivo); o detalhe mostra por item produto + tamanho(label) + cor + sku +
  preço + qtd, endereço e total. Mostra o aviso de estoque devolvido em recusado/cancelado.
- `/dashboard/moda-infantil-settings` — taxa de entrega + pedido mínimo.
- types + SDKs (products, variants, orders, config) espelhando comida + a grade de variantes.
- Status TS `moda-infantil-order-status.ts` + `KidsCategory` const (`kids-categories.ts`) + `KidsSize`
  const (`kids-size.ts`) + parity tests (status + categorias + tamanhos).
- `getNavForProfile('moda_infantil')` injeta "Moda Infantil" (3 itens: Produtos, Pedidos,
  Configurações), no mesmo padrão dos branches existentes (comida/floricultura têm branch — seguir o
  modelo deles). Subdomínio modainfantil.meadadigital.local. Paleta: agente escolhe ('celeste'/'coral'/
  'mostarda' sugeridas).
- npm build limpo (Turbopack dev esconde import quebrado — `next build` é a verdade).

[DOCS]
- CLAUDE.md: seção "## Perfil Moda Infantil (camada 8.22)" espelhando as seções de perfil + nota de que
  CLONA o COMIDA e inaugura o CHASSI DE VARIANTES (tamanho×cor com estoque por variante + decremento
  transacional 409 out_of_stock + devolução ao cancelar/recusar) com o eixo de tamanho por FAIXA ETÁRIA
  INFANTIL e o mapeamento idade→tamanho (orientação, não medida). Documentar EXPLÍCITO: categorias
  próprias (bebe/menino/menina/calcados/acessorios); KidsSize hardcoded; preço é da variante; a tag
  `<pedido_moda_infantil>`; gate de aceite humano. (Lembrar a regra: agente NÃO edita CLAUDE.md por
  iniciativa própria — esta SM é trabalho cravado, então a edição é autorizada por este prompt.)
- docs/PERFIL_MODA_INFANTIL.md: guia operacional (catálogo com produtos e grade de variantes; estoque
  por variante; pedidos + Kanban + gate de aceite; como a IA atende e sugere tamanho pela idade; "o que
  a IA NÃO faz"). Espelhar um PERFIL_*.md order-based (PERFIL_COMIDA ou PERFIL_FLORICULTURA) como molde.
- NÃO mexer em system-template.txt nem em outros perfis.

[TESTES BACKEND]
Espelhar a suíte do comida (service + controller integration por entidade):
- `ModaInfantilOrderStatusParityTest` + `KidsCategoryParityTest` + `KidsSizeParityTest` +
  `ProfileTypeParityTest` → verdes.
- `KidsCatalogServiceTest` + `ControllerIntegrationTest`: CRUD produto + variante; sku duplicado → 409
  sku_taken; (size,color) duplicada bloqueada; invalida cache; delete produto/variante em uso → 409;
  wrongProfile → 403.
- `ModaInfantilConfigServiceTest`/`ControllerIntegrationTest`: GET fallback + PUT.
- `ModaInfantilOrderServiceTest` [CHAVE da escapada]:
    * pedido OK com variantes de tamanhos/cores distintos → unit_price = price da VARIANTE; subtotal/
      total batem; estoque decrementa exatamente a qty de cada variante.
    * estoque insuficiente (qty > stock) → 409 `out_of_stock`; NENHUM decremento parcial (rollback total).
    * variant_id de outro company / inativo / inexistente → aborta (sem pedido parcial).
    * total da IA DESCARTADO (recalcula a partir da variante); snapshots (product_name/size/color/sku/
      unit_price) preservados mesmo após alterar a variante depois.
    * pedido mínimo (subtotal < min) → 422 below_min_order; sem address → 422 address_required.
    * devolução de estoque: aguardando→recusado devolve qty às variantes; aguardando→…→cancelado
      devolve; segunda transição terminal NÃO devolve de novo (idempotência via stock_returned).
- `KidsSizeSuggestionTest`: `KidsSize.suggestForAgeMonths` mapeia idade→tamanho (ex.: 2 meses→rn ou m3
  conforme a grade; 24 meses→a2; idade acima da grade→a12 com flag; abaixo→rn).
- `PedidoModaInfantilConfirmHandlerTest`: tag válida com 2 itens → cria pedido + decrementa; tag com
  variant esgotada → 409/empty (sem pedido); variant_id inválido → empty; sem tag → empty; total bate.
- Status/gate: aguardando→separando (aceite) → pronto → saiu_entrega → entregue; aguardando→recusado
  (motivo, devolve estoque); transição inválida → 409; a IA não tem endpoint de aceite.
mvn final = relatar contagem REAL do Surefire (NÃO chutar; lição: grep textual ≠ Surefire).

[CONSTRAINTS DUROS]
- Migration única (66). Sem foto/anexo (produto/variante são texto+cor; lição SERVICE_ROLE_KEY).
- Cliente NÃO é entidade do core — continua o contact (pedido tem conversation_id/contact_id).
- ESCAPADA: variantes tamanho×cor com estoque por variante; eixo de tamanho por FAIXA ETÁRIA infantil
  (KidsSize hardcoded + parity); mapeamento idade→tamanho derivado do enum (orientação, não medida).
- DECREMENTO TRANSACIONAL de estoque na criação do pedido (UPDATE condicional `stock_qty >= qty`,
  0 linhas → 409 out_of_stock); rollback total se qualquer item falhar; DEVOLUÇÃO ao recusar/cancelar
  (idempotente via stock_returned).
- preço de venda é da VARIANTE (produto pai não tem preço). subtotal/total/unit_price/line_total
  materializados (não generated). Snapshots de product_name/size/color/sku/unit_price no item.
- Gate de aceite humano: nasce 'aguardando'; aceite/recusa no painel; a IA NUNCA aceita/recusa.
- Categorias hardcoded (parity) + KidsSize hardcoded (parity). Tag `<pedido_moda_infantil>` distinta de
  TODAS as outras.
- NUNCA remover nicho ao estender o CHECK de companies.profile_id (conferir lista completa pós-edição).
- NÃO mexer em outros perfis nem em system-template.txt. Webhook OFF (incidente re-sync — não religar).
- Cache de catálogo TTL 60s + invalidação em toda mutação de produto OU variante.
- Erro 529 → inline. Gate 3× → pausar e reportar. Working tree sujo no arranque → pausar. git add
  EXPLÍCITO arquivo a arquivo (NUNCA `git add .` nem wildcard); .env/.env.local/CONTEXT.md/secrets
  NUNCA staged (sanity: grep eyJ.../password/secret= + confirmar .env fora da staging).
- SEED com timestamptz/date: `at time zone 'America/Sao_Paulo'`. IDs de namespace com sufixo -20x NOVO
  (conferir que nenhum seed anterior usou).
- Tabela nova entra na migration ANTES de tocar o banco (lição os_config). Adicionar todas as tabelas
  novas ao TRUNCATE/SCRIPTS do AbstractIntegrationTest.
- Pool Hikari de teste já está enxuto (`src/test/resources/application-dev.yml`, min-idle 0/max 2) —
  cada novo *ServiceTest é um ApplicationContext; NÃO inflar o pool (lição SM-K do Supabase pooler).
- Decisões menores: agente decide (paleta, layout do editor de variantes, grade final do KidsSize, se
  os snapshots ficam em colunas ou compõem).

[PASSO FINAL — TENANT igorhaf33 + SEED + COMMIT + PUSH + SMOKE + RELATÓRIO]
F.1 — TENANT igorhaf33 (Moda Infantil Modelo, profile=moda_infantil), padrão GoTrue (instance_id=
      zero-UUID + colunas de token = '' não NULL — lição seed auth.users), senha em comunicação direta.
      company c?0000000-…-033 / user a?0000000-…-033. Caddy + /etc/hosts pra
      modainfantil.meadadigital.local. JwtFilter autentica /api/moda-infantil/ (se ainda não).
F.2 — Seed /tmp/seed-moda-infantil.sql (NÃO COMITAR; `at time zone 'America/Sao_Paulo'`; ids sufixo
      -20x NOVO):
  - config: taxa de entrega R$12, pedido mínimo R$50.
  - catálogo (produtos + variantes COM estoque e preço por variante):
    * "Body Manga Curta" (bebe, brand "Tip Top") — variantes:
        rn/Branco sku BODY-RN-BR R$30 stock 5; rn/Azul sku BODY-RN-AZ R$30 stock 0 (ESGOTADO p/ smoke
        out_of_stock); m3/Branco sku BODY-M3-BR R$32 stock 8; m6/Rosa sku BODY-M6-RS R$32 stock 3.
    * "Camiseta Dino" (menino) — a4/Verde sku DINO-A4-VD R$45 stock 6; a6/Verde sku DINO-A6-VD R$48
        stock 4; a8/Azul sku DINO-A8-AZ R$50 stock 2.
    * "Vestido Floral" (menina) — a2/Lilás sku VEST-A2-LI R$70 stock 4; a4/Lilás sku VEST-A4-LI R$75
        stock 5.
    * "Tênis Velcro" (calcados) — a4/Branco sku TEN-A4-BR R$90 stock 3.
    * "Meia Kit 3" (acessorios) — m6/Sortido sku MEIA-M6-ST R$25 stock 10.
  - contact "Paula Souza" +5511955554444 (VINCULADO: instance+conversation, sufixo -20x) + contact
    "Renato Dias" +5511944443333 (sem vínculo).
  - 4 pedidos cobrindo estados/escapada (line_total e total recalculados; estoque já decrementado):
    * 'aguardando' VINCULADO (Paula): 2× Body Manga Curta m3/Branco (R$32) + 1× Camiseta Dino a6/Verde
      (R$48) → subtotal 32×2+48 = 112; +taxa12 = R$124; pra smoke de gate de aceite + variante.
    * 'separando' (Renato): 1× Vestido Floral a4/Lilás (R$75) → 75 +12 = R$87; pra smoke do funil.
    * 'pronto' (Paula): 1× Tênis Velcro a4/Branco (R$90) → 90+12 = R$102; pra notificação 'pronto'.
    * 'entregue' (Paula, passado) histórico.
  (Ajustar os stocks pós-seed pra refletir o que os pedidos 'aguardando/separando/pronto' já
  decrementaram, OU criar os pedidos via o caminho de decremento — o agente decide manter coerência.)
F.3 — git add EXPLÍCITO dos arquivos da SM (migration, .java novos, .ts novos, telas, docs) + sanity
      (git status -s + diff --staged --stat + grep segredo + .env/CONTEXT.md FORA) + commit.
      Mensagem padrão multi-linha via `git commit -F` (feat(camada-8.22): perfil moda_infantil/Moda
      Infantil (loja de roupa infantil) com FUNDAÇÃO/BACKEND/FRONTEND/DECISÕES/VALIDAÇÃO contagem REAL/
      NÃO TOCADO/FECHAMENTO). Trailer obrigatório: Co-Authored-By: Claude Opus 4.8
      <noreply@anthropic.com>. Tag anotada `fase-8.22-fechada` (nº real confirmado no arranque).
F.4 — git push origin main + `git push origin --tags` (NUNCA --force).
F.5 — docker compose restart backend + aguardar /admin/me → 401 missing_auth_header.
F.6 — Smoke E2E (blocos A–G):
  BLOCO A: auth — igorhaf33 → /admin/me → role=tenant_admin, profileId=moda_infantil, productName=
    "Moda Infantil".
  BLOCO B: catálogo + guard — GET produtos (com variantes: tamanho/cor/sku/preço/estoque); CRUD smoke
    de produto+variante + invalida cache; sku duplicado → 409 sku_taken; (size,color) duplicada
    bloqueada; delete em uso → 409; GET config + PUT; tenant de OUTRO perfil → /api/moda-infantil/* →
    403 forbidden_wrong_profile.
  BLOCO C: VARIANTES POR FAIXA ETÁRIA [escapada] — provar que produtos têm grade tamanho×cor; cada
    variante com price/stock próprios; o bloco de contexto da IA lista as DISPONÍVEIS e omite a
    esgotada (Body rn/Azul stock 0 não aparece pra venda).
  BLOCO D: DECREMENTO + OUT_OF_STOCK [CHAVE] —
    - `<pedido_moda_infantil>` com 2× Body m3/Branco + 1× Camiseta a6/Verde → cria 'aguardando';
      unit_price = price da variante; total = 124 (32×2+48+12); estoque das variantes decrementou
      exatamente (m3/Branco -2, a6/Verde -1); total da IA descartado.
    - pedido pedindo qty > estoque (ex.: 99× Camiseta a8/Azul, stock 2) → 409 out_of_stock; estoque
      INALTERADO (sem decremento parcial).
    - pedido com Body rn/Azul (stock 0) → 409 out_of_stock.
    - variant_id inválido / de outro company → não cria.
    - subtotal < mínimo → 422 below_min_order; sem endereço → 422 address_required.
  BLOCO E: SUGESTÃO DE TAMANHO POR IDADE — provar KidsSize.suggestForAgeMonths (ex.: criança de 2 anos
    → a2; 6 meses → m6) e que o contexto/persona trazem o mapeamento + o aviso "cada marca veste
    diferente, confira a tabela de medidas".
  BLOCO F: gate de aceite + funil + devolução — aguardando→separando (aceite, Paula vinculada) → 200 +
    msg; separando→pronto → msg "pronto"; pronto→saiu_entrega→entregue; OUTRO pedido aguardando→recusado
    (motivo) → msg defensiva + ESTOQUE DEVOLVIDO às variantes (stock_returned true; segunda terminal não
    devolve de novo); transição inválida → 409 invalid_status_transition; a IA não tem rota de aceite.
  BLOCO G: regressão + paridade — perfis anteriores intactos (smoke leve 1 endpoint cada);
    moda_infantil → /api/comida/* → 403; outro perfil → /api/moda-infantil/* → 403; mvn test
    -Dtest=ModaInfantilOrderStatusParityTest,KidsCategoryParityTest,KidsSizeParityTest,
    ProfileTypeParityTest → verde.
  Cleanup smoke + restaurar seed pristine (estoque/estados originais). mvn final: contagem REAL do
  Surefire.

[REPORTAR]
Igual SMs anteriores. Incluir EXPLICITAMENTE:
- "ProfileType.MODA_INFANTIL adicionado (camada 8.22); CHECK companies.profile_id estendido PRESERVANDO
   todos os perfis anteriores"
- "Paridade ModaInfantilOrderStatus, KidsCategory, KidsSize e ProfileType validadas"
- "Tenant igorhaf33 criado (GoTrue + Caddy + /etc/hosts modainfantil.meadadigital.local)"
- "ESCAPADA: chassi de VARIANTES tamanho×cor com estoque por variante; eixo de tamanho por FAIXA ETÁRIA
   INFANTIL (KidsSize); preço de venda é da variante"
- "Decremento TRANSACIONAL de estoque na criação (UPDATE condicional stock_qty>=qty; 0 linhas → 409
   out_of_stock; rollback total); devolução ao recusar/cancelar (idempotente via stock_returned)"
- "Mapeamento idade→tamanho (KidsSize.suggestForAgeMonths) é ORIENTAÇÃO — a IA avisa que varia por marca"
- "BLOCO D prova decremento + out_of_stock + total recalculado; BLOCO F prova gate de aceite + devolução"
- "categorias próprias (bebe/menino/menina/calcados/acessorios)"
- "Tag <pedido_moda_infantil> distinta de <pedido_comida>/<pedido_flor>/<pedido_pizza> e das outras"
- "OutboundService ganhou maybeProcessPedidoModaInfantil; ProfilePromptContext ganhou ramo
   MODA_INFANTIL; getNavForProfile('moda_infantil') com branch próprio"
- "Cache de catálogo TTL 60s + invalidação em toda mutação de produto OU variante"
- "tabelas criadas DENTRO da migration 66 (lição os_config); seed com at time zone + sufixo -20x novo;
   pool Hikari de teste mantido enxuto (lição SM-K)"
- "Próximas fases: pagamento real (Stripe #50), foto de produto, troca/devolução com RMA, reserva de
   estoque com expiração, tabela de medidas em cm, frete por CEP, multi-loja + dívida acumulada
   (webhook OFF, cliente real, olho humano sobre os verticais)"
