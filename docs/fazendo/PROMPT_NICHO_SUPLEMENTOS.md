>>> SLOT ATRIBUÍDO (ver docs/prompts-nicho/README.md — tem precedência sobre qualquer "provisório" no
>>> corpo): ordem 19 · camada 8.24 · migration 68_suplementos.sql · tenant igorhaf35 (sufixo -035) ·
>>> ids de seed sufixo -22x. Reconfirmar no arranque que a fila não avançou; se avançou, deslocar
>>> conforme o README (a tabela do README é a fonte única — hoje ela vai até a ordem 15/cursos/8.20;
>>> este slot ESTENDE a fila para a ordem 19. Antes de escrever a migration, reconfirmar o MAIOR nº
>>> presente em supabase/migrations/ e o MAIOR igorhafN já provisionado, e a contagem REAL do Surefire).

[TAREFA — SUB-MARATONA: PERFIL SUPLEMENTOS / Suplementos (Loja de saúde) (camada 8.24)]

[CONTEXTO]
PROJETO MEADA em /home/igorhaf/meada.
Monolito multi-tenant que se apresenta como N produtos verticais ("perfis"). Cada tenant tem
EXATAMENTE 1 perfil (companies.profile_id, NOT NULL, CHECK). Perfis são HARDCODED em dois arquivos
espelhados — enum Java `ProfileType.java` + const TS `frontend/lib/profiles/profile-type.ts` — e o
`ProfileTypeParityTest` falha o build se divergirem. NÃO existe tabela de perfis. Backend Spring Boot
3.3.13 + Java 17 Temurin, JdbcTemplate (não JPA), sem Lombok. Frontend Next 16 (app router) + React 19
+ TS + Tailwind 4 + shadcn/ui. Migration própria em `supabase/migrations/`.

Há ~18-19 perfis verticais reais hoje (… comida 8.4, floricultura 8.5, pizzaria 8.6, adega 8.9 — os
order-based mais recentes) + generic. Lê CONTEXT.md e o filesystem no arranque pra cravar convenções,
nº de migration, contagem do Surefire e numeração de tenant ANTES de escrever qualquer código. NÃO
hardcodar a contagem do mvn — relatar a REAL do Surefire ao final. IDs de namespace compartilhado
(contacts/instance/conversation) NO SEED com sufixo -22x (NÃO pode colidir com nenhum seed anterior;
conferir no arranque).

Suplementos é template de nicho pra LOJA DE VAREJO de SUPLEMENTOS ALIMENTARES / loja de saúde /
nutrição esportiva (whey, creatina, vitaminas, pré-treino, termogênicos, acessórios) com ENTREGA.
Tenant acessa `suplementos.meadadigital.local` e vê o produto "Suplementos". A IA atende clientes via
WhatsApp, conhece o catálogo de PRODUTOS e suas VARIANTES (sabor × peso/tamanho da embalagem), MONTA
o pedido NA CONVERSA (carrinho relido do histórico a cada turno, igual sushi/comida), confirma SEMPRE
com o valor total, avisa que o pedido vai pra CONFIRMAÇÃO DA LOJA (gate de aceite). Tom prestativo e
informativo de balconista de loja de suplementos — SEM jamais virar conselheiro de saúde.

>>> TRAVA DE SAÚDE DA IA (O CORAÇÃO DESTA SM — inegociável; espelho LEVE da trava clínica do nutri,
>>> adaptada a VAREJO) <<<
Suplemento alimentar NÃO é medicamento e a IA NÃO é profissional de saúde. A IA, em TODA a conversa:
- NUNCA prescreve DOSAGEM / POSOLOGIA / "quanto tomar" / "como tomar" / horário de uso.
- NUNCA recomenda um suplemento como TRATAMENTO / CONDUTA pra emagrecer, ganhar massa, curar, melhorar
  performance ou resolver um SINTOMA / OBJETIVO de saúde.
- NUNCA responde "isso serve pra [doença/objetivo]?", "isso engorda/emagrece?", "posso tomar com
  [remédio]?", "qual o melhor pra mim?", "isso é seguro pra [condição]?".
- NUNCA opina sobre SAÚDE, PATOLOGIA, INTERAÇÃO MEDICAMENTOSA, contraindicação, alergia ou efeito
  fisiológico. Não monta protocolo, não compara produtos por "eficácia", não valida objetivo corporal.
- Para QUALQUER dúvida de uso / dosagem / objetivo de saúde → acolhe sem reforçar e ORIENTA consultar
  NUTRICIONISTA / MÉDICO / EDUCADOR FÍSICO. (Espelha o tom de recusa do NUTRI: "Para isso, o ideal é
  conversar com um nutricionista/médico — eu não posso orientar sobre uso ou dosagem.")
- A IA SÓ: mostra o catálogo, tira dúvida de PRODUTO (sabor, peso/tamanho da embalagem, preço,
  disponibilidade/estoque) e monta o pedido.
- AVISO DEFENSIVO embutido na persona: "este produto não substitui orientação de um profissional de
  saúde".
A trava vive na PERSONA (`ProfilePromptContext.SUPLEMENTOS`) E no bloco de INSTRUÇÕES do contexto
injetado (`SuplementosMenuCache`) — em DOIS lugares, igual o nutri.

EVOLUÇÃO ESTRUTURAL: CLONA o chassi order-based do COMIDA (camada 8.4) — catálogo + carrinho-na-
conversa + tag de pedido + recálculo de total (descarta o total da IA) + snapshot de preço/nome + taxa
de entrega/pedido mínimo + Kanban de status + GATE DE ACEITE HUMANO (pedido nasce 'aguardando'; loja
ACEITA→'em_preparo' ou RECUSA→'recusado' com rejection_reason) — e INAUGURA, num perfil de VAREJO,
DUAS coisas que nem o comida nem o pizzaria têm juntas:

  ESCAPADA 1 — CATÁLOGO DE PRODUTOS COM VARIANTES (sabor × peso/tamanho da embalagem) E ESTOQUE POR
  VARIANTE COM DECREMENTO TRANSACIONAL: diferente do comida (cujo item é uma linha só com modifiers
  que somam delta) e da adega (cujas opções são modifiers planos), aqui um PRODUTO ("Whey Protein
  Concentrado") tem N VARIANTES reais e vendáveis (ex.: "Chocolate 900g", "Baunilha 2kg", "Morango
  900g") — cada variante tem seu PRÓPRIO preço, SKU e ESTOQUE (`stock_quantity`). O carrinho referencia
  a VARIANTE (`variant_id`), não o produto. Na criação do pedido o backend DECREMENTA o estoque da
  variante DENTRO da transação, com UPDATE condicional `... where stock_quantity >= :qtd` — se sobrar 0
  linha afetada (estoque insuficiente) → 409 `out_of_stock` e ABORTA o pedido inteiro (sem pedido
  parcial). É o primeiro perfil de LOJA-DE-PRODUTO com estoque por variante e decremento transacional.
  (Espelho do chassi de variantes/estoque que seria do LINGERIE — que NÃO existe no disco; o eixo de
  variante AQUI é SABOR × PESO/TAMANHO da embalagem, não cor×tamanho de roupa.)

  ESCAPADA 2 (o coração) — TRAVA DE NÃO-PRESCRIÇÃO num perfil de VAREJO (não clínico): ver a TRAVA DE
  SAÚDE acima. É a primeira loja-de-produto do projeto com trava explícita de não-prescrição. Justifica
  suplementos ser perfil próprio e não um preset do comida/adega.

  (OPCIONAL, administrativo) — `expiry_date` (data de validade) por VARIANTE/LOTE como campo
  informativo: a LOJA gerencia a validade no painel; a IA NÃO promete validade específica nem usa isso
  pra recomendar. Campo nullable, sem regra de negócio que bloqueie venda nesta SM (só exibição/gestão).

NÃO TEM nesta SM (registrado pra não inventar): pagamento real (Stripe é #50); foto/upload de produto
(bloqueador SERVICE_ROLE_KEY — produto é só texto); RECOMENDAÇÃO PERSONALIZADA / QUIZ de suplemento /
"monte seu combo pro seu objetivo" (PROIBIDO pela trava de saúde — a IA não recomenda por objetivo);
assinatura/recorrência de suplemento (academia cobre recorrência); reserva de estoque com expiração;
controle de lote/FEFO/inventário com movimentações; tabela nutricional/rótulo estruturado (informação
de produto é texto livre descritivo, sem número que a IA possa usar como dosagem); combo/cupom/
fidelidade; integração com marketplace; retirada na loja (nesta SM é só ENTREGA — espelho comida).
Fases futuras.

DECISÕES CRAVADAS (revisor decidiu pelo Igor):
1. CLONA o chassi do COMIDA (cardápio→catálogo + carrinho + tag + total recalculado + taxa/mínimo +
   Kanban + gate de aceite humano). MANTER onde não conflita.
2. ESCAPADA 1 — VARIANTE COM ESTOQUE: produto (`sup_products`) tem N variantes (`sup_variants`); a
   variante carrega sabor + peso/tamanho + price_cents + sku + stock_quantity (+ expiry_date opcional).
   O pedido referencia variant_id. Decremento transacional com UPDATE condicional → 409 out_of_stock.
3. ESCAPADA 2 — TRAVA DE SAÚDE / NÃO-PRESCRIÇÃO: persona + contexto carregam a trava (não prescreve
   dosagem/uso terapêutico, encaminha a profissional). A IA SÓ mostra catálogo, tira dúvida de produto
   e monta pedido.
4. Categorias hardcoded próprias (CHECK + enum Java + const TS + parity): proteinas, aminoacidos,
   vitaminas, pre_treino, emagrecedores, acessorios.
5. Gate de aceite humano: nasce 'aguardando'; aceite/recusa no painel; a IA NUNCA aceita/recusa.
   'aguardando' NÃO notifica (a IA já confirmou o recebimento na própria mensagem).
6. Cliente NÃO é entidade do core — continua o contact (pedido tem conversation_id/contact_id).
7. SÓ ENTREGA nesta SM (sem retirada): pedido exige delivery_address; soma delivery_fee da config.
8. Tag `<pedido_suplementos>` (namespace próprio, distinta de `<pedido_comida>` / `<pedido_flor>` /
   `<pedido_pizza>` / `<pedido_adega>` e de TODAS as outras). Carrega itens por VARIANTE; o backend
   valida estoque, decrementa e recalcula tudo.

[FUNDAÇÃO — migration 68_suplementos.sql]
- ALTER companies CHECK: ESPELHAR a CHECK MAIS RECENTE no disco (ler a última migration de perfil) e
  APENDAR 'suplementos' PRESERVANDO TODOS os perfis existentes — nenhum nicho some. ⚠️ Depois de
  qualquer clonagem por sed, CONFERIR que o CHECK tem TODOS os perfis + 'suplementos' (lição cravada:
  `sed s/comida/suplementos/g` removeria comida e os demais da lista). drop + add constraint.
- RLS enable+force, policies via app.company_id(), grants authenticated + service_role.
  * sup_orders / sup_order_items: INSERT vem do BACKEND (service_role) — o pedido é criado pela IA via
    PedidoSuplementosConfirmHandler, não pelo SDK do tenant. Tenant só SELECT/UPDATE (status no Kanban
    / gate de aceite). Espelhar comida_orders/comida_order_items.
  * sup_products / sup_variants: CRUD do tenant (authenticated SELECT/INSERT/UPDATE/DELETE) +
    service_role.
- subtotal_cents / total_cents / unit_price_cents MATERIALIZADOS no INSERT; NÃO colunas geradas.
- SNAPSHOTS no pedido: preço + nome do PRODUTO e da VARIANTE (sabor/peso) em sup_order_items. Alterar/
  excluir um produto/variante no catálogo NÃO altera pedidos passados.
- Tabelas (TODAS dentro da migration 68 ANTES de tocar o banco — lição os_config):
  * `sup_config` — taxa de entrega + pedido mínimo. 1:1 com company (PK company_id). Ausente →
    taxa/mínimo = 0. Clone EXATO de comida_config (delivery_fee_cents, min_order_cents, timestamps).
  * `sup_products` — CATÁLOGO (o "pai"). (id uuid pk, company_id fk on delete restrict, category text
    CHECK in ('proteinas','aminoacidos','vitaminas','pre_treino','emagrecedores','acessorios') —
    em sync com SuplementosCategory.java, name text CHECK length 1..200, brand text nullable (marca),
    description text nullable (texto livre informativo de produto — SEM dosagem/posologia; comment
    cravando isso), active boolean default true, created_at/updated_at). RLS + grants + index
    (company_id, category) where active. Comment cravando: "description é informativo de produto, NÃO
    dosagem/posologia; a IA não usa pra recomendar".
  * `sup_variants` — A ESCAPADA 1 (variante vendável de um produto). (id uuid pk, company_id uuid
    not null fk on delete restrict — DENORMALIZADO p/ RLS direta, igual comida_menu_item_options;
    product_id uuid not null fk → sup_products on delete cascade; flavor text nullable (sabor, ex.
    'Chocolate'); size_label text not null CHECK length 1..60 (peso/tamanho da embalagem, ex. '900g',
    '2kg', '120 caps'); sku text nullable (UNIQUE por company quando não-null — index parcial);
    price_cents integer not null check >= 0 (preço DA VARIANTE — cada sabor×peso tem o seu);
    stock_quantity integer not null default 0 check >= 0; expiry_date date NULLABLE (administrativo
    informativo); active boolean default true; created_at/updated_at). RLS + grants + index
    (product_id) where active + index parcial UNIQUE (company_id, sku) where sku is not null. Comment
    cravando: variante = sabor × peso; stock_quantity decrementado transacionalmente no pedido;
    expiry_date é informativo (a IA não promete validade).
  * `sup_orders` — pedidos. status text CHECK ('aguardando','em_preparo','saiu_entrega','entregue',
    'recusado','cancelado') default 'aguardando'; subtotal_cents/delivery_fee_cents/total_cents
    materializados; delivery_address text not null; conversation_id/contact_id NOT NULL (fk restrict);
    notes; rejection_reason nullable (gate de aceite); created_at; status_updated_at. Espelho EXATO de
    comida_orders. RLS: tenant SELECT/UPDATE (sem policy authenticated de INSERT — só backend).
  * `sup_order_items` — itens do pedido com snapshot. (id uuid pk, order_id fk → sup_orders on delete
    cascade, product_id uuid fk → sup_products on delete restrict, variant_id uuid fk → sup_variants
    on delete restrict, qtd integer check > 0, unit_price_cents integer not null (snapshot do preço da
    VARIANTE no momento), product_name_snapshot text not null, variant_label_snapshot text not null
    (ex. "Chocolate 900g" — sabor+peso congelados)). RLS SELECT via exists no order. on delete
    restrict no variant_id → variante com pedido não é hard-deletada (409 variant_in_use). Espelho de
    comida_order_items + variant.
    (NÃO há tabela de "options escolhidas" — a variante É a escolha; o modelo de modifiers planos do
    comida é substituído pelo modelo de variante-com-estoque.)
- Status do pedido hardcoded (`SuplementosOrderStatus` enum Java + `suplementos-order-status.ts` const
  TS + `SuplementosOrderStatusParityTest`). Funil (espelho comida):
    aguardando → em_preparo, recusado, cancelado
    em_preparo → saiu_entrega, cancelado
    saiu_entrega → entregue, cancelado
    entregue / recusado / cancelado → terminal
  (aceite = aguardando→em_preparo é o gate humano.) Notifica: em_preparo (aceito), saiu_entrega,
  entregue, recusado (com motivo defensivo). aguardando/cancelado conforme padrão do comida.
- Categorias hardcoded (`SuplementosCategory.java` + `suplementos-categories.ts` +
  `SuplementosCategoryParityTest`): proteinas, aminoacidos, vitaminas, pre_treino, emagrecedores,
  acessorios.
- TODAS as tabelas novas (sup_config, sup_products, sup_variants, sup_orders, sup_order_items) entram
  na migration 68 ANTES de tocar o banco (lição os_config) e no TRUNCATE/SCRIPTS do
  AbstractIntegrationTest.

[BACKEND]
Pacote `src/main/java/com/meada/profiles/suplementos/` (config / products / variants /
orders), espelhando a estrutura de `profiles/comida/`.
- Catálogo: CRUD de PRODUTOS (`sup_products`: name/brand/category/description/active) + CRUD de
  VARIANTES (`sup_variants`: flavor/size_label/sku/price_cents/stock_quantity/expiry_date/active),
  variantes aninhadas ao produto. delete de produto/variante com pedido → 409 (product_in_use /
  variant_in_use, via on delete restrict). Cache do bloco de catálogo injetado no prompt (Caffeine
  TTL 60s), INVALIDADO em toda gravação/edição/exclusão de produto OU variante (`SuplementosMenuCache`,
  espelho ComidaMenuCache; IGNORA conversationId — contexto é o catálogo).
- Config: GET (fallback taxa/mínimo = 0) + PUT. Espelho ComidaConfig.
- Orders: criados pelo BACKEND via `PedidoSuplementosConfirmHandler`. Recálculo + DECREMENTO
  TRANSACIONAL (a parte delicada):
    * cada item referencia variant_id (+ qtd). unit_price = price_cents da VARIANTE (snapshot);
      subtotal = Σ (unit_price × qtd); total = subtotal + delivery_fee (sempre entrega).
    * DENTRO da transação, pra cada item: `UPDATE sup_variants SET stock_quantity = stock_quantity -
      :qtd, updated_at = now() WHERE id = :variantId AND company_id = :cid AND active = true AND
      stock_quantity >= :qtd` — se rowsAffected == 0 → lança `OutOfStockException` → 409 `out_of_stock`
      (com o variant_id/label na resposta, defensivo) e ABORTA tudo (rollback; sem pedido parcial).
    * variant_id inválido / de outro company / inativo aborta (sem pedido). delivery_address ausente
      → 422 address_required (espelho comida). pedido mínimo: espelhar o comida.
    * total da IA DESCARTADO (recalcula sempre). Snapshots completos (product_name + variant_label).
    * Cancelar/recusar pedido NÃO devolve estoque nesta SM (registrar como pendência simples — devolver
      estoque ao cancelar é fase futura; manter o escopo enxuto e cravar a decisão).
- Status: PATCH com validação de transição (inválida → 409 invalid_status_transition) + gate de aceite
  (aguardando→em_preparo = aceitar; aguardando→recusado = recusar com rejection_reason). Notificação
  outbound por status (texto defensivo — SEM conteúdo de saúde/uso).
- Guard: `SuplementosProfileGuard.requireSuplementos` (403 forbidden_wrong_profile) nos `/api/
  suplementos/**`. Espelho ComidaProfileGuard.
- IA:
  * Persona `ProfilePromptContext.SUPLEMENTOS` com a TRAVA DE SAÚDE EMBUTIDA (ver TRAVA acima; espelhar
    o TOM da recusa do NUTRI — acolhe e encaminha a profissional). Tom prestativo-informativo de
    balconista; jamais conselheiro de saúde. Inclui o aviso defensivo "não substitui orientação
    profissional".
  * Contexto injetado (`SuplementosMenuCache`, TTL 60s, ignora conversationId) = bloco de catálogo
    (produtos por categoria, com marca + descrição informativa + as VARIANTES de cada um listando
    sabor + peso + preço + se há ESTOQUE/disponibilidade) + taxa/mínimo + INSTRUÇÕES da tag
    `<pedido_suplementos>` + a TRAVA DE NÃO-PRESCRIÇÃO repetida no bloco de instruções (segundo lugar,
    igual nutri). Invalidação em toda mutação do catálogo.
  * Tag `<pedido_suplementos>{"delivery_address":"...","items":[{"variant_id":"...","qtd":N}],"notes":
    "...|null"}` → `PedidoSuplementosConfirmHandler` (espelho PedidoComidaConfirmHandler, mas o item é
    por variant_id e o handler dispara o decremento transacional). Best-effort; o OutboundService
    REMOVE a tag antes de enviar e o backend valida estoque + recalcula.
  * JwtFilter autentica `/api/suplementos/` (adicionar `SUPLEMENTOS_PATH_PREFIX = "/api/suplementos/"`
    em `admin/security/JwtAuthenticationFilter.java`, junto dos demais prefixos de perfil).
    OutboundService ganha `maybeProcessPedidoSuplementos` (best-effort, encadeado APÓS os outros perfis
    em `OutboundService.java` — perfil é único, só um age; seguir o padrão dos `maybeProcessPedido*`).

[FRONTEND]
- `/dashboard/suplementos-products` (CRUD de produtos + editor de VARIANTES inline: GRADE de variantes
  sabor × peso com colunas sabor / peso-tamanho / SKU / preço / ESTOQUE / validade-opcional / ativo;
  toggle active; estoque editável por variante),
  `/dashboard/suplementos-orders` (Kanban por status com o GATE DE ACEITE: Aceitar/Recusar na coluna
  'aguardando', recusa pede motivo; o detalhe mostra delivery_address, e por item o produto + a
  VARIANTE escolhida [sabor+peso] + qtd + preço),
  `/dashboard/suplementos-settings` (taxa de entrega + pedido mínimo).
- types + SDKs (products, variants, orders, config) espelhando comida + o eixo de variante/estoque.
- Status TS `suplementos-order-status.ts` + `SuplementosCategory` const + parity tests (status +
  categorias).
- `getNavForProfile('suplementos')` em `frontend/components/layout/nav-config.tsx` injeta "Suplementos"
  (3 itens: Produtos, Pedidos, Configurações), no mesmo padrão dos branches existentes (comida/adega já
  têm branch — seguir o modelo deles). Subdomínio `suplementos.meadadigital.local`. Paleta: sugestão
  'pinheiro' (verde-saúde) / 'aco' / 'oceano' — agente escolhe.
- npm build limpo (Turbopack dev esconde import quebrado — `next build` é a verdade).

[DOCS]
- CLAUDE.md: seção "## Perfil Suplementos (camada 8.24)" espelhando as seções de perfil + nota de que
  CLONA o COMIDA e inaugura: (1) variantes sabor×peso com ESTOQUE por variante + decremento
  transacional (409 out_of_stock), (2) a TRAVA DE NÃO-PRESCRIÇÃO num perfil de VAREJO. Documentar
  EXPLÍCITO E DESTACADO a trava: a IA NUNCA prescreve dosagem/uso terapêutico/objetivo de saúde, NUNCA
  recomenda por objetivo/sintoma, NUNCA opina sobre interação medicamentosa — encaminha a
  nutricionista/médico/educador físico; só mostra catálogo, tira dúvida de produto e monta pedido.
  Documentar também: categorias próprias; a tag `<pedido_suplementos>`; só entrega; gate de aceite.
- docs/PERFIL_SUPLEMENTOS.md: guia operacional do tenant (catálogo de produtos + variantes + estoque;
  pedidos + Kanban + gate de aceite; como a IA atende; "o que a IA NÃO faz" com a trava de saúde em
  destaque). Espelhar PERFIL_COMIDA.md / PERFIL_PADARIA.md.
- NÃO mexer em system-template.txt nem em outros perfis.

[TESTES BACKEND]
Espelhar a suíte do comida (service + controller integration por entidade):
- `SuplementosOrderStatusParityTest` + `SuplementosCategoryParityTest` + `ProfileTypeParityTest`.
- `SuplementosProductServiceTest` + `...ControllerIntegrationTest` (CRUD produto + variantes; invalida
  cache; delete-em-uso 409 product_in_use / variant_in_use; wrongProfile 403).
- `SuplementosConfigServiceTest` / `...ControllerIntegrationTest` (GET fallback + PUT).
- `SuplementosOrderServiceTest` [CHAVE das escapadas]:
    * pedido normal (variante com estoque) → OK; estoque DECREMENTA exatamente pela qtd.
    * estoque insuficiente (qtd > stock_quantity) → 409 out_of_stock; pedido NÃO criado; estoque
      INALTERADO (rollback prova a transação).
    * dois itens, o segundo sem estoque → ABORTA tudo (o decremento do primeiro também faz rollback —
      sem pedido parcial).
    * total: unit_price = price da variante; subtotal = Σ(unit×qtd); total = subtotal + delivery_fee;
      total da IA DESCARTADO.
    * snapshot: product_name_snapshot + variant_label_snapshot (sabor+peso) congelados — alterar o
      produto/variante depois NÃO muda o item do pedido.
    * variant_id inválido / de outro company / inativo → aborta (empty/erro), sem pedido.
    * delivery_address ausente → 422 address_required.
- `PedidoSuplementosConfirmHandlerTest`: tag válida cria pedido + decrementa; tag com variante sem
  estoque → não cria (out_of_stock); variant_id inválido → empty; sem tag → empty; total bate.
- `SuplementosPersonaTest` (ou asserção no teste de persona/contexto): a string da persona
  `ProfilePromptContext.SUPLEMENTOS` E o bloco de instruções do `SuplementosMenuCache` CONTÊM a TRAVA
  DE NÃO-PRESCRIÇÃO — asserts que a persona menciona NÃO prescrever dosagem/uso e encaminhar a
  profissional (ex.: contém "dosagem"/"não substitui"/"nutricionista" — escolher tokens estáveis).
  (Espelho do espírito do que o nutri faz com a trava clínica.)
- Status/gate: aguardando→em_preparo (aceite) → saiu_entrega → entregue; aguardando→recusado(motivo);
  transição inválida → 409; a IA não tem endpoint de aceite.
mvn final = relatar contagem REAL do Surefire.

[CONSTRAINTS DUROS]
- Migration única (68_suplementos.sql). Sem foto/anexo (produto é só texto).
- Cliente NÃO é entidade do core — continua o contact (pedido tem conversation_id/contact_id).
- ESCAPADA 1: variante (sabor × peso/tamanho) com price/sku/stock POR VARIANTE; pedido referencia
  variant_id; decremento transacional via UPDATE condicional `stock_quantity >= :qtd` → 409
  out_of_stock; aborta tudo (sem pedido parcial). expiry_date opcional administrativo (a IA não promete
  validade). Cancelar NÃO devolve estoque nesta SM (decisão cravada; fase futura).
- ESCAPADA 2 (O CORAÇÃO): a IA NUNCA prescreve DOSAGEM / USO / OBJETIVO DE SAÚDE, NUNCA recomenda por
  objetivo/sintoma, NUNCA opina sobre interação medicamentosa/patologia — encaminha a profissional. Só
  mostra catálogo, tira dúvida de PRODUTO e monta pedido. Trava na persona E no contexto (2 lugares).
- subtotal/total/unit_price materializados (não generated). Snapshots de produto + variante no item.
- Gate de aceite humano: nasce 'aguardando'; aceite/recusa no painel; a IA NUNCA aceita/recusa.
- Só ENTREGA (sem retirada): delivery_address obrigatório; soma delivery_fee.
- Categorias hardcoded (parity): proteinas, aminoacidos, vitaminas, pre_treino, emagrecedores,
  acessorios. Tag `<pedido_suplementos>` distinta de TODAS as outras.
- companies CHECK: APENDAR 'suplementos' PRESERVANDO todos os perfis (conferir após qualquer sed).
- NÃO mexer em outros perfis nem em system-template.txt. Webhook OFF.
- Cache de catálogo TTL 60s + invalidação em toda mutação de produto/variante.
- 529 → inline. Gate 3× → pausar. Working tree sujo → pausar. git add EXPLÍCITO (nunca git add .);
  .env / CONTEXT.md / secrets NUNCA staged.
- SEED com timestamptz/date: `at time zone 'America/Sao_Paulo'`. IDs de namespace com sufixo -22x.
- Tabela nova entra na migration ANTES de tocar o banco (lição os_config). Adicionar as tabelas ao
  TRUNCATE/SCRIPTS do AbstractIntegrationTest.
- Decisões menores: agente decide (paleta, layout da grade de variantes, exibição da validade).

[PASSO FINAL — TENANT + SEED + COMMIT + PUSH + SMOKE + RELATÓRIO]
F.1 — TENANT igorhaf35 (Suplementos Modelo, profile=suplementos), padrão GoTrue (instance_id=
      zero-UUID + colunas de token='' não-NULL — lição seed auth.users), senha em comunicação direta.
      company c?0000000-...-035 / user a?0000000-...-035. Caddy + /etc/hosts pra
      suplementos.meadadigital.local.
F.2 — Seed /tmp/seed-suplementos.sql (NÃO COMITAR; `at time zone 'America/Sao_Paulo'`; ids sufixo -22x):
  - config: taxa de entrega R$10, pedido mínimo R$50.
  - catálogo (produtos + variantes com estoque):
    * "Whey Protein Concentrado" (proteinas, marca "MaxFit") com variantes:
        Chocolate 900g R$120 stock 10, Baunilha 2kg R$220 stock 5, Morango 900g R$120 stock 0
        (stock 0 pra smoke de out_of_stock).
    * "Creatina Monohidratada" (aminoacidos, marca "PurePower"): 300g R$90 stock 20, 150g R$55 stock 8.
    * "Multivitamínico A-Z" (vitaminas): 60 caps R$45 stock 30 (expiry_date hoje+18 meses, p/ smoke do
      campo administrativo).
    * "Pré-Treino Insano" (pre_treino): Frutas Vermelhas 300g R$130 stock 6.
    * "Termogênico" (emagrecedores): 60 caps R$80 stock 12.
    * "Coqueteleira 600ml" (acessorios): única R$25 stock 50 (flavor null, size_label "600ml").
  - contact "Diego Souza" +5511955554444 (VINCULADO: instance+conversation, sufixo -22x) + contact
    "Elaine Castro" +5511944443333 (sem vínculo).
  - 3-4 pedidos cobrindo estados/escapadas:
    * 'aguardando' VINCULADO (Diego): 2× Whey Chocolate 900g + 1× Creatina 300g → subtotal
      (2×120 + 90) = 330; + taxa 10 = R$340; pra smoke de variante + estoque + aceite.
    * 'em_preparo' (Elaine): 1× Multivitamínico → smoke do funil (→saiu_entrega→entregue).
    * 'entregue' (Diego, passado) histórico.
    (Ajustar o estoque das variantes seedadas considerando que o seed cria pedidos JÁ existentes — se
    o seed não passar pelo handler, decrementar o stock manualmente no seed pra refletir os pedidos, OU
    seedar pedidos sem decremento e cravar a nota; o smoke usa a tag pra provar o decremento.)
F.3 — JwtFilter `/api/suplementos/` (se ainda não).
F.4-F.6 — git add EXPLÍCITO dos arquivos da SM + sanity (git status -s + diff --staged --stat + grep
      por segredo eyJ.../password/secret= + confirmar .env/CONTEXT.md FORA da staging) + commit.
      Mensagem padrão (feat(camada-8.24): perfil suplementos/Suplementos (Loja de saúde) com FUNDAÇÃO/
      BACKEND/FRONTEND/DECISÕES/VALIDAÇÃO contagem REAL/NÃO TOCADO/FECHAMENTO + Co-Authored-By:
      Claude Opus 4.8). Tag fase-8.24-fechada (nº real confirmado no arranque).
F.7 — git push origin main + git push origin --tags.
F.8 — docker compose restart backend + aguardar /admin/me → 401.
F.9 — Smoke E2E (blocos A–G):
  BLOCO A: auth — igorhaf35 → /admin/me → role=tenant_admin, profileId=suplementos,
    productName=Suplementos.
  BLOCO B: catálogo + guard — GET produtos (com variantes: sabor/peso/preço/estoque); CRUD smoke de
    produto+variante + invalida cache; delete em uso 409 (variant_in_use/product_in_use); GET config +
    PUT; tenant comida (ou outro) → /api/suplementos/products → 403.
  BLOCO C: PEDIDO NORMAL + VARIANTE [prova a ESCAPADA 1, parte feliz] — <pedido_suplementos> 2× Whey
    Chocolate 900g + 1× Creatina 300g, endereço → 'aguardando'; total = 330+10 = 340; total da IA
    descartado; ESTOQUE da variante Whey Chocolate DECREMENTA de 10 → 8 e Creatina 300g de 20 → 19;
    snapshots de variant_label "Chocolate 900g" batem.
  BLOCO D: OUT_OF_STOCK [prova o decremento transacional] —
    - <pedido_suplementos> 1× Whey MORANGO 900g (stock 0) → 409 out_of_stock; pedido NÃO criado.
    - pedido com 2 itens onde o 2º excede o estoque → 409 out_of_stock; o estoque do 1º NÃO foi
      decrementado (rollback prova a transação — reler stock antes/depois).
    - delivery_address ausente → 422 address_required.
  BLOCO E: gate de aceite + funil — aguardando→em_preparo (aceite, Diego vinculado) → 200 + msg;
    em_preparo→saiu_entrega → msg; saiu_entrega→entregue → msg; outro aguardando→recusado(motivo) → msg
    defensiva (SEM conteúdo de saúde); transição inválida → 409; a IA não tem rota de aceite.
  BLOCO F: A TRAVA DE SAÚDE [prova a ESCAPADA 2 — o coração] —
    - pedido NORMAL funciona (já provado em C) — a trava NÃO bloqueia compra.
    - asserção de que a PERSONA e o CONTEXTO carregam a NÃO-PRESCRIÇÃO: inspecionar
      ProfilePromptContext.SUPLEMENTOS + o bloco do SuplementosMenuCache (via teste/grep) e confirmar
      que contêm a instrução de não prescrever dosagem/uso/objetivo e encaminhar a profissional + o
      aviso "não substitui orientação profissional". (Se houver smoke de IA real disponível: uma
      pergunta tipo "quanto de whey devo tomar por dia?" deve receber recusa+encaminhamento, NÃO um
      número — mas isso depende do Gemini; se não houver chave, validar via persona/contexto e cravar
      como pendência o teste com IA real.)
  BLOCO F-bis: regressão — perfis anteriores intactos (smoke leve 1 endpoint cada); suplementos →
    /api/comida/* → 403; suplementos → /api/adega/* → 403.
  BLOCO G: paridade — mvn test -Dtest=SuplementosOrderStatusParityTest,SuplementosCategoryParityTest,
    ProfileTypeParityTest → verde.
  Cleanup smoke + restaurar seed pristine. mvn final: contagem REAL.
  LIÇÃO os_config: confirmar no relatório que TODAS as tabelas novas foram criadas DENTRO da migration
  68 ANTES de qualquer toque no banco (não houve ALTER/CREATE fora da migration).
F.10 — RELATÓRIO + DESTAQUE EXPLÍCITO:
  - "perfil suplementos/loja de saúde — CLONA o COMIDA (order-based + gate de aceite)"
  - "ESCAPADA 1: catálogo com VARIANTES sabor×peso + ESTOQUE por variante + DECREMENTO TRANSACIONAL
     (UPDATE condicional stock_quantity >= qtd; 409 out_of_stock; aborta tudo, sem pedido parcial)"
  - "ESCAPADA 2 (o coração): TRAVA DE SAÚDE / NÃO-PRESCRIÇÃO num perfil de VAREJO — a IA não prescreve
     dosagem/uso/objetivo de saúde, encaminha a nutricionista/médico/educador físico; só catálogo +
     dúvida de produto + pedido"
  - "BLOCO C prova variante + decremento; BLOCO D prova out_of_stock + rollback transacional"
  - "BLOCO F prova que o pedido normal funciona E que persona/contexto carregam a não-prescrição"
  - "expiry_date por variante = campo administrativo informativo (a IA não promete validade)"
  - "categorias próprias (proteinas/aminoacidos/vitaminas/pre_treino/emagrecedores/acessorios)"
  - "Seed: at time zone + sufixo de ids -22x; tabelas DENTRO da migration (lição os_config)"
  - PENDÊNCIAS: devolução de estoque ao cancelar/recusar, foto de produto, recomendação personalizada/
     quiz (PROIBIDO pela trava — fase futura só se com guarda forte), assinatura recorrente, lote/FEFO,
     pagamento real (Stripe #50), teste da trava com IA real (Gemini), retirada na loja.

[REPORTAR]
Igual SMs anteriores. Incluir EXPLICITAMENTE:
- "ProfileType.SUPLEMENTOS adicionado (camada 8.24); paridade ProfileType validada"
- "Paridade SuplementosOrderStatus e SuplementosCategory validadas"
- "Tenant igorhaf35 criado (GoTrue + Caddy + /etc/hosts suplementos.meadadigital.local)"
- "ESCAPADA 1: variantes sabor×peso + estoque por variante + decremento transacional (409 out_of_stock)"
- "ESCAPADA 2 (coração): TRAVA DE SAÚDE — IA NUNCA prescreve dosagem/uso/objetivo; encaminha a
   profissional; trava na persona E no contexto + teste/asserção de que a persona a contém"
- "Gate de aceite humano (espelho comida): IA confirma recebimento, loja aceita/recusa"
- "Tag <pedido_suplementos> distinta de <pedido_comida>/<pedido_flor>/<pedido_pizza>/<pedido_adega> e
   das outras"
- "OutboundService ganhou maybeProcessPedidoSuplementos; JwtFilter autentica /api/suplementos/"
- "getNavForProfile('suplementos') com branch próprio (Produtos/Pedidos/Configurações)"
- "Cache de catálogo TTL 60s + invalidação em toda mutação de produto/variante"
- "tabelas criadas DENTRO da migration 68 (lição os_config); seed com at time zone + sufixo -22x"
- "Contagem REAL do Surefire (relatar o número exato; NÃO hardcodar)"
- "Próximas fases: devolução de estoque, foto, recomendação com guarda, assinatura, lote/FEFO, Stripe,
   teste da trava com IA real"
