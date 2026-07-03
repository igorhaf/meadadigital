>>> SLOT ATRIBUÍDO (ver docs/prompts-nicho/README.md — fonte única de verdade, tem precedência
>>> sobre qualquer "provisório" no corpo): ordem 10 · camada 8.15 · migration 59_papelaria.sql ·
>>> tenant igorhaf26 (company/user sufixo -026) · ids de seed sufixo -13x. Reconfirmar no arranque
>>> que a fila não avançou; se avançou, deslocar conforme o README (e a tabela do README NÃO lista os
>>> nichos acima da ordem 8 — esta é a ordem 10, prevista além da fila inicial: reconfirmar no disco o
>>> MAIOR nº de migration em supabase/migrations/ e o MAIOR igorhafN provisionado ANTES de cravar).

[TAREFA — SUB-MARATONA: PERFIL PAPELARIA / Papelaria (Papelaria · Convites personalizados) (camada 8.15)]

[CONTEXTO]
PROJETO MEADA em /home/igorhaf/meada.
Monolito multi-tenant que se apresenta como N produtos verticais ("perfis"). Perfis são HARDCODED em
dois arquivos espelhados — enum Java `ProfileType` (`src/main/java/com/meada/profiles/
ProfileType.java`) + const TS (`frontend/lib/profiles/profile-type.ts`) — com `ProfileTypeParityTest`
falhando o build se divergirem. NÃO há tabela de perfis. Backend Spring Boot 3 + JdbcTemplate (sem JPA,
sem Lombok); frontend Next 16 (app router) + React 19 + Tailwind 4 + shadcn/ui. Migration própria em
`supabase/migrations/`. Lê CONTEXT.md e o filesystem no arranque pra cravar convenções, nº de migration,
contagem do Surefire e numeração de tenant ANTES de escrever qualquer código. NÃO hardcodar a contagem
do mvn — relatar a REAL do Surefire ao final. Valores esperados (CONFIRMAR no filesystem antes; podem ter
avançado se um nicho da fila foi executado primeiro): migration 59_papelaria, tenant igorhaf26, company
c?0000000-...-026, user a?0000000-...-026. IDs de namespace compartilhado (contacts/instance/conversation)
NO SEED com sufixo NOVO (-13x) que NÃO colida com nenhum seed anterior.

Papelaria é template de nicho pra PAPELARIA / CONVITES PERSONALIZADOS dentro do mesmo dashboard Meada.
Tenant acessa papelaria.meadadigital.local e vê o produto "Papelaria". É ENCOMENDA GRÁFICA personalizada:
convites de casamento/aniversário, save the date, cartões, papelaria personalizada, adesivos, embalagens
— produtos sob encomenda com DATA de retirada/entrega, TIRAGEM (quantidade: 50/100/200 convites),
PERSONALIZAÇÃO (papel, acabamento, cor como opções com price_delta) e um TEXTO personalizado por item.
A IA atende clientes via WhatsApp, conhece o catálogo, MONTA o pedido NA CONVERSA (carrinho relido do
histórico a cada turno, igual sushi/comida/padaria), coleta a DATA respeitando a ANTECEDÊNCIA MÍNIMA
(lead time de produção), a TIRAGEM e as escolhas de personalização, e confirma SEMPRE com o valor total,
avisando que o pedido vai pra CONFIRMAÇÃO DA PAPELARIA. Tom prestativo-criativo, de quem ajuda a planejar
um convite especial.

>>> TRAVA DE COMPORTAMENTO DA IA (cravada) <<<
- NUNCA inventa produto, papel, acabamento, cor, adicional ou preço fora do catálogo.
- NUNCA aceita NEM recusa o pedido — é AÇÃO HUMANA da papelaria no painel (gate de aceite). A IA só
  CONFIRMA o RECEBIMENTO ("seu pedido foi enviado pra papelaria") na própria mensagem.
- O total é SEMPRE recalculado pelo sistema — a IA pode somar pra orientar, mas o backend descarta o
  total da IA e recalcula a partir do catálogo (base + Σ deltas) × TIRAGEM.
- LEAD TIME: a IA NUNCA promete uma data que não respeite a ANTECEDÊNCIA MÍNIMA (lead time) do item — se
  o cliente pedir 100 convites "pra depois de amanhã" e o lead time é 7 dias, a IA explica e oferece a
  primeira data possível; quem valida de fato é o backend (rejeita data < hoje+lead_time → 422).
- NUNCA promete arte/tema/desenho que não esteja no catálogo de personalização; layout artístico complexo
  → "vou confirmar com a equipe de criação". A IA não negocia o preço da personalização.
- >>> A IA NUNCA APROVA A ARTE PELO CLIENTE. <<< A aprovação da prova de arte é DECLARAÇÃO do cliente. A
  IA só REGISTRA a aprovação que o cliente DECLARAR (via tag <aprovacao_arte>), e só quando o pedido está
  no estado 'arte_aprovacao'. A IA não sobe arte, não diz que a arte ficou pronta sem a papelaria ter
  subido, não força a aprovação. Se o cliente pedir alteração na arte, a IA registra a observação e
  encaminha — a papelaria revisa e sobe nova versão (a IA não edita arte).

EVOLUÇÃO ESTRUTURAL: CLONA o chassi da PADARIA (camada 8.8) — que clonou FLORICULTURA (8.5) → COMIDA
(8.4): catálogo de produtos + OPÇÕES/personalização (modifiers via price_delta) + carrinho-na-conversa +
tag de pedido + recálculo de total (descarta o total da IA) + snapshot de preço/nome + taxa/mínimo +
Kanban de status + GATE DE ACEITE HUMANO (pedido nasce 'aguardando'; papelaria ACEITA→'aceito' ou
RECUSA→'recusado' com rejection_reason) + made_to_order + LEAD TIME (422 lead_time_violation com a
primeira data possível) + DATA de retirada/entrega + PERÍODO (manhã/tarde) + fulfillment
('retirada'|'entrega'). MANTER tudo onde não conflita.

  >>> ESCAPADA ESTRUTURAL — PROVA DE ARTE (aprovação do layout pelo cliente antes da produção): <<<
  A papelaria é encomenda GRÁFICA — antes de imprimir, a equipe faz a ARTE (o layout do convite) e
  precisa do OK do cliente. É um gate de aprovação de ARTE pelo cliente DENTRO de um pedido order-based
  (espelho leve do gate de aprovação em 2 fases dos perfis de proposta — oficina/eventos —, mas aplicado
  a um pedido, não a uma proposta).
    - O pedido ganha um estado EXTRA no funil: 'arte_aprovacao'. Depois do gate de aceite
      ('aguardando'→'aceito'), a papelaria PRODUZ a arte e SOBE (no painel) — o pedido vai pra
      'arte_aprovacao' e fica AGUARDANDO o cliente APROVAR a arte. Só depois de aprovada a arte o pedido
      segue pra produção/impressão ('em_producao').
    - Campo `art_approved boolean NOT NULL default false` no pedido. Vira true quando o cliente aprova.
    - Quem registra a aprovação: o cliente, via tag <aprovacao_arte> na conversa (a IA captura), OU a
      papelaria no painel (botão "Arte aprovada"). A transição 'arte_aprovacao'→'em_producao' SÓ é
      permitida com art_approved=true (senão 409 art_not_approved). Aprovar a arte materializa
      art_approved=true e dispara a notificação "sua arte foi aprovada, vamos pra produção".
    - A IA NÃO sobe arte e NÃO move o pedido pra 'arte_aprovacao' (isso é AÇÃO HUMANA — a papelaria sobe
      a arte). A IA só CAPTURA a aprovação quando o pedido JÁ está em 'arte_aprovacao'; <aprovacao_arte>
      num pedido fora desse estado é no-op (best-effort, sem efeito) + warn.
    - Espelho conceitual: AberturaOs cria a OS / AprovacaoOs muta o estado existente (oficina). Aqui:
      <pedido_papelaria> CRIA o pedido / <aprovacao_arte> MUTA o estado de um pedido existente. Primeiro
      perfil order-based em que a IA APROVA UM ARTEFATO (a arte) de um pedido já criado, não só cria.

  TIRAGEM (quantidade escala o preço): cada item do pedido tem `quantity` que é a TIRAGEM (50/100/200…).
  unit_price_cents (base + Σ deltas de papel/acabamento/cor) é o preço UNITÁRIO; o line total = unit_price
  × quantity. Tiragem alta multiplica o total — é o eixo de escala desta SM (na padaria a quantidade
  existe mas é trivial; aqui é a regra de negócio, convite vende em tiragem).

  RETIRADA × ENTREGA: o pedido tem fulfillment ('retirada'|'entrega'). 'retirada' = balcão, sem taxa e
  sem endereço obrigatório; 'entrega' = exige delivery_address e soma delivery_fee. (Clone padaria.)

NÃO TEM nesta SM (registrado pra não inventar): UPLOAD da arte como ARQUIVO/imagem (bloqueador
SERVICE_ROLE_KEY — a "arte subida" é modelada como um campo de URL/texto colado OU apenas o estado
'arte_aprovacao'; a prova de arte real por imagem é fase futura), múltiplas VERSÕES de arte com histórico
de revisões (é só o gate aprovado/não-aprovado — versionamento de prova é fase futura, estilo
eventos/oficina), orçamento de convite artístico ad-hoc com aprovação de PREÇO (a personalização é por
opções cadastradas, não proposta livre — peça gráfica sob orçamento livre é fase futura, estilo
oficina/eventos), e-sign/contrato, assinatura recorrente, combo/cupom/fidelidade, pagamento real (Stripe
é #50), integração com gráfica externa, controle de estoque/produção/impressão, slot por horário fino (é
dia + faixa, igual padaria/floricultura), tabela de papéis/gramatura estruturada (gramatura/acabamento
ficam como opções cadastradas + texto livre informativo). Fases futuras.

DECISÕES CRAVADAS (revisor decidiu pelo Igor):
1. CLONA o chassi da PADARIA (pedido com lead time + made_to_order + data condicional + gate de aceite +
   catálogo + modifiers + carrinho + tag + total recalculado + taxa/mínimo + Kanban + fulfillment). Onde
   não conflita, MANTER idêntico.
2. Categorias hardcoded próprias (CHECK + enum Java + const TS + parity): convites, save_the_date,
   cartoes, papelaria, adesivos, embalagens. (convites/save_the_date tendem a made_to_order; o flag por
   item é a verdade, não a categoria.)
3. made_to_order + lead_time_days (por item; default na config). Data CONDICIONAL: obrigatória só se há
   item sob encomenda; é a MAIOR antecedência exigida entre os itens. 422 lead_time_violation (resposta
   traz a primeira data possível).
4. PERSONALIZAÇÃO: grupos de opção (Papel/Acabamento/Cor/Tamanho) via price_delta (espelho comida/padaria)
   + custom_text texto livre por item (snapshot no order_item — ex.: "Casamento Ana & Bruno · 12/12").
5. TIRAGEM: order_item.quantity é a tiragem; line total = unit_price × quantity. Tiragem alta escala o
   total (eixo de negócio).
6. fulfillment 'retirada'|'entrega' (hardcoded, parity OU CHECK simples — recomendo parity p/ simetria):
   retirada sem taxa/endereço; entrega com.
7. >>> ESCAPADA: PROVA DE ARTE — campo art_approved boolean + estado 'arte_aprovacao' no funil entre
   'aceito' e 'em_producao'. Aprovação pela IA via tag <aprovacao_arte> (só registra; nunca aprova pelo
   cliente) OU pelo painel; 'arte_aprovacao'→'em_producao' exige art_approved=true (409 art_not_approved).
8. Gate de aceite humano: nasce 'aguardando'; aceite/recusa no painel; a IA não aceita/recusa.
   'aguardando' NÃO notifica (a IA já confirmou o recebimento).
9. Tag <pedido_papelaria> (cria pedido) e <aprovacao_arte> (cliente aprova a arte, muta estado) — AMBAS
   namespace próprio, distintas de <pedido_comida>/<pedido_flor>/<pedido_pizza>/<encomenda_padaria>/
   <aprovacao_os>/<aprovacao_proposta> e de TODAS as outras tags do projeto.

[FUNDAÇÃO — migration 59_papelaria]
- ALTER companies CHECK aceitar 'papelaria' >>> ACRESCENTANDO, PRESERVANDO TODOS os perfis já presentes.
  Conferir no disco a lista ATUAL do CHECK (último: pizzaria → 'generic','legal','dental','sushi',
  'restaurant','salon','pousada','academia','pet','oficina','nutri','barbearia','eventos','estetica',
  'comida','floricultura','pizzaria', + quaisquer adicionados pela fila intermediária) e adicionar
  'papelaria' AO FIM. NUNCA clonar por `sed s/floricultura/papelaria/g` cego — isso TROCA o perfil em vez
  de acrescentar e remove os demais do CHECK; depois de qualquer clonagem por sed, CONFERIR que o CHECK
  tem TODOS os perfis + 'papelaria'. (Lição de clonagem por sed cravada na floricultura.)
- RLS enable+force, policies via app.company_id(), grants authenticated + service_role (orders/
  order_items/order_item_options: INSERT pelo BACKEND via service_role — o pedido é criado pela IA via
  PedidoPapelariaConfirmHandler; tenant só SELECT/UPDATE — status no Kanban / gate de aceite / aprovação
  de arte no painel). Espelhar 49_floricultura.sql / o que a padaria fez (52_padaria.sql se existir).
- subtotal_cents/total_cents/unit_price_cents MATERIALIZADOS no INSERT; NÃO colunas geradas (o recálculo
  cruza linhas/tabelas — generated não serve; lição das migrations anteriores).
- SNAPSHOTS: preço+nome em order_items; group/option/delta em order_item_options; custom_text no
  order_item. Alterar/excluir item/opção no catálogo NÃO altera pedidos passados.
- Tabelas (TODAS dentro da migration 59 ANTES de tocar o banco — lição os_config):
  * papelaria_config — taxa de entrega + pedido mínimo + lead_time_days_default (default p/ encomenda;
    int >= 0). 1:1 com company; ausente → taxa/mínimo = 0, lead default (ex.: 5). Clone padaria_config /
    floricultura_config + lead_time_days_default.
  * papelaria_catalog_items — catálogo. (id, company_id, name CHECK 1..200, description, price_cents =
    preço base UNITÁRIO, category CHECK in sync com enum, made_to_order boolean NOT NULL default false,
    lead_time_days int nullable (override do default da config quando made_to_order), specs text nullable
    (gramatura/material — texto livre informativo), active/available default true, timestamps). Comment
    cravando made_to_order + lead_time. Clone padaria_menu_items / floricultura_catalog_items.
  * papelaria_catalog_item_options — modifiers/personalização (grupos: Papel, Acabamento, Cor, Tamanho).
    Cada linha = UMA opção de UM grupo (group_label), price_delta_cents (>=0, NÃO negativo nesta fase),
    available, sort_order. Espelho floricultura_catalog_item_options / comida_*_item_options. on delete
    cascade do item.
  * papelaria_orders — pedidos. status CHECK ('aguardando'|'aceito'|'arte_aprovacao'|'em_producao'|
    'pronto'|'retirado'|'saiu_entrega'|'entregue'|'recusado'|'cancelado') default 'aguardando' (ver nota
    de status abaixo — retirada e entrega divergem no fim do funil); fulfillment text CHECK
    ('retirada'|'entrega'); subtotal/delivery_fee/total materializados; delivery_address text NULLABLE
    (obrigatório só p/ entrega — validado no backend); pickup_or_delivery_date date NULLABLE (obrigatória
    só se há item sob encomenda); delivery_period text CHECK ('manha'|'tarde') NULLABLE;
    >>> art_approved boolean NOT NULL default false (ESCAPADA — gate de aprovação de arte); <<<
    art_url text NULLABLE (URL/ref da arte subida pela papelaria — sem upload de arquivo, é link colado);
    rejection_reason nullable (gate de aceite); conversation_id/contact_id NOT NULL; notes; timestamps +
    status_updated_at. Espelho padaria_orders + art_approved + art_url + status 'arte_aprovacao'.
  * papelaria_order_items — itens do pedido com snapshot de nome+preço. unit_price_cents JÁ inclui Σ
    deltas (papel/acabamento/cor/tamanho); quantity = TIRAGEM (>=1); made_to_order_snapshot boolean;
    custom_text text NULLABLE (snapshot do texto personalizado do item). Espelho padaria_order_items +
    custom_text. (line total = unit_price_cents × quantity, materializado/derivado no recálculo.)
  * papelaria_order_item_options — opções/personalização escolhidas por item (snapshots de group/option/
    delta). Espelho comida/floricultura_order_item_options.
- Status do pedido hardcoded (PapelariaOrderStatus enum Java + const TS + parity test). Funil com a
  ESCAPADA (arte_aprovacao) e os DOIS desfechos (retirada × entrega):
    aguardando      → aceito, recusado, cancelado            (aceite = gate humano)
    aceito          → arte_aprovacao, cancelado              (papelaria sobe a arte → vai p/ aprovação)
    arte_aprovacao  → em_producao (SÓ se art_approved=true), cancelado
    em_producao     → pronto, cancelado
    pronto          → retirado (retirada), saiu_entrega (entrega), cancelado
    saiu_entrega    → entregue, cancelado
    retirado/entregue/recusado/cancelado → terminal
  (aceite = aguardando→aceito é o gate humano. arte_aprovacao→em_producao exige art_approved=true, senão
  409 art_not_approved.) Notifica: aceito ("recebemos seu pedido / vamos preparar a arte"), arte_aprovacao
  ("sua arte está pronta, dê uma olhada e aprove"), em_producao ("arte aprovada, vamos imprimir"), pronto
  ("seu pedido está pronto pra retirada" — relevante), saiu_entrega, entregue, recusado (com motivo
  defensivo). aguardando/cancelado conforme padrão (aguardando NÃO notifica — a IA já confirmou).
- fulfillment hardcoded (PapelariaFulfillment enum Java + const TS + parity, OU só CHECK — agente decide):
  retirada, entrega.
- Categorias hardcoded (PapelariaCategory.java + papelaria-categories.ts + PapelariaCategoryParityTest):
  convites, save_the_date, cartoes, papelaria, adesivos, embalagens.
- TODAS as tabelas novas entram na migration 59 ANTES de tocar o banco (lição os_config) e no
  TRUNCATE/SCRIPTS do AbstractIntegrationTest.

[BACKEND]
- Menu/Catálogo: CRUD de itens (com made_to_order + lead_time_days + specs) + opções (modifiers/
  personalização) — espelho padaria/floricultura/comida. Cache do bloco de catálogo injetado no prompt
  (Caffeine TTL 60s, IGNORA conversationId — contexto é o catálogo), INVALIDADO em toda gravação/edição/
  exclusão. delete de item com pedido → 409 catalog_item_in_use.
- Config: GET (fallback taxa/mínimo = 0, lead default) + PUT.
- Orders: criados pelo BACKEND via PedidoPapelariaConfirmHandler. Recálculo: unit_price = base + Σ deltas
  (papel/acabamento/cor/tamanho); line = unit_price × quantity(TIRAGEM); subtotal = Σ linhas; total =
  subtotal + (entrega ? delivery_fee : 0). Validações cravadas:
    * se QUALQUER item made_to_order → pickup_or_delivery_date OBRIGATÓRIA; senão pode ser null.
    * data exigida = hoje + MAX(lead_time dos itens made_to_order) (item override > config default); data
      < isso → 422 lead_time_violation (resposta traz a primeira data possível).
    * fulfillment 'entrega' → delivery_address obrigatório (senão 422 address_required) + soma taxa;
      'retirada' → sem taxa, endereço pode ser null.
    * pedido mínimo: espelhar o que padaria/floricultura fazem.
    * quantity (tiragem) >= 1 por item; option_id inválido aborta (sem pedido parcial). Snapshots
      completos (custom_text incluso). art_approved nasce false; art_url null no INSERT.
- >>> Fluxo de aprovação de arte (ESCAPADA): <<<
    * a papelaria, no painel, sobe a arte (PATCH que seta art_url e move aceito→arte_aprovacao) — AÇÃO
      HUMANA; dispara notificação "sua arte está pronta, aprove".
    * aprovação: PATCH (painel) OU a IA via <aprovacao_arte> → seta art_approved=true (só faz sentido em
      'arte_aprovacao'; em outro estado → no-op/warn na IA, 409 no endpoint).
    * 'arte_aprovacao'→'em_producao' VALIDADO: exige art_approved=true → senão 409 art_not_approved.
      (defesa: não dá pra produzir sem o cliente ter aprovado.)
- Status: PATCH com validação de transição (inválida → 409 invalid_status_transition) + gate de aceite
  (aguardando→aceito = aceitar; aguardando→recusado = recusar com rejection_reason) + gate de arte
  (arte_aprovacao→em_producao exige art_approved). Notificação outbound por status (texto defensivo).
- IA:
  * Persona prestativa-criativa com a TRAVA DE COMPORTAMENTO embutida (não inventa item/preço, não aceita/
    recusa, total recalculado, respeita lead time, não promete arte não cadastrada, NUNCA aprova a arte
    pelo cliente — só registra a aprovação declarada).
  * Contexto injetado = bloco de catálogo (itens por categoria, marcando quais são SOB ENCOMENDA com o
    lead time, + opções de personalização com deltas) + taxa/mínimo/lead default + instruções das tags
    <pedido_papelaria> (formato com fulfillment + data condicional + TIRAGEM(quantity) + personalização +
    custom_text) e <aprovacao_arte>. Cache TTL 60s (IGNORA conversationId). Invalidação em toda mutação
    do catálogo.
  * Tag <pedido_papelaria>{"fulfillment":"retirada|entrega","pickup_or_delivery_date":"YYYY-MM-DD|null",
    "delivery_period":"manha|tarde|null","delivery_address":"...|null","items":[{"catalog_item_id",
    "options":[{"option_id"}],"custom_text":"...|null","quantity":N}],"notes"} →
    PedidoPapelariaConfirmHandler (espelho PedidoFlorConfirmHandler/EncomendaPadariaConfirmHandler +
    made_to_order/lead/fulfillment/custom_text/tiragem). Best-effort; OutboundService REMOVE a tag antes
    de enviar e o backend valida + recalcula (total da IA descartado).
  * Tag <aprovacao_arte>{"order_id":"...|null"} → AprovacaoArteHandler: resolve o pedido (o pedido em
    'arte_aprovacao' do contato/conversa, ou o order_id se dado), seta art_approved=true e move
    arte_aprovacao→em_producao (só se o pedido está em 'arte_aprovacao'; senão no-op + warn). Best-effort;
    a tag é removida antes de enviar. Espelho conceitual de AprovacaoOsHandler/AprovacaoPropostaHandler
    (muta o estado de um artefato existente), mas a aprovação é da ARTE, não de orçamento.
  * JwtFilter autentica /api/papelaria/**. OutboundService ganha maybeProcessPedidoPapelaria +
    maybeProcessAprovacaoArte (best-effort, ENCADEADOS após os outros perfis — perfil é único, só um age).
    Seguir o padrão dos pares maybeProcessPedidoFlor/maybeProcessAprovacaoOs já no OutboundService.

[FRONTEND]
- /dashboard/papelaria-menu (CRUD itens + editor de opções/personalização inline; toggle made_to_order +
  campo lead_time_days quando ligado + specs),
  /dashboard/papelaria-orders (Kanban por status com o GATE DE ACEITE: Aceitar/Recusar na coluna
  'aguardando' (recusa pede motivo); + a COLUNA/ESTADO 'arte_aprovacao' com o fluxo de arte: na coluna
  'aceito' botão "Subir arte" (cola art_url → move p/ arte_aprovacao); na coluna 'arte_aprovacao' mostra
  art_approved e botão "Marcar arte aprovada" + (quando aprovada) "Enviar pra produção" (move
  →em_producao, bloqueado se !art_approved). O detalhe mostra fulfillment, data/período quando há
  encomenda, e por item: TIRAGEM (quantity), a personalização escolhida + o texto personalizado),
  /dashboard/papelaria-settings (taxa de entrega + pedido mínimo + lead time default).
- types + SDKs (menu, options, orders) espelhando padaria/floricultura + made_to_order/lead/fulfillment/
  custom_text/quantity(tiragem)/art_approved/art_url.
- Status TS papelaria-order-status.ts (com 'arte_aprovacao') + PapelariaCategory const + PapelariaFulfillment
  const + parity tests (status + categorias [+ fulfillment se virar enum]).
- getNavForProfile('papelaria') injeta "Papelaria" (3 itens: Catálogo, Pedidos, Configurações), no mesmo
  padrão dos branches existentes (comida/floricultura/pizzaria já têm branch — seguir o modelo deles em
  frontend/components/layout/nav-config.tsx). Subdomínio papelaria.meadadigital.local. Paleta: agente
  escolhe (sugestão suave/papelaria fina: 'rosa-po', 'celeste' ou 'lavanda').
- npm build limpo (next build de prod — Turbopack dev esconde import quebrado).

[DOCS]
- CLAUDE.md: seção "## Perfil Papelaria (camada 8.15)" espelhando as seções de perfil + nota de que CLONA
  a PADARIA e inaugura: PROVA DE ARTE (gate de aprovação de arte pelo cliente dentro de um pedido
  order-based, estado 'arte_aprovacao' + art_approved + tag <aprovacao_arte>) e TIRAGEM (quantity escala o
  preço). Documentar EXPLÍCITO: categorias próprias; made_to_order + lead_time (422 lead_time_violation);
  fulfillment; o funil com arte_aprovacao e art_not_approved; as DUAS tags <pedido_papelaria> e
  <aprovacao_arte>.
- docs/PERFIL_PAPELARIA.md: guia operacional (catálogo com pronta-entrega e encomenda; personalização +
  tiragem; pedidos + Kanban + gate de aceite + fluxo da prova de arte; retirada × entrega; como a IA
  atende; "o que a IA NÃO faz" — em especial: NÃO aprova a arte pelo cliente). Espelhar
  PERFIL_PADARIA.md / PERFIL_FLORICULTURA.md.
- NÃO mexer em system-template.txt nem em outros perfis.

[TESTES BACKEND]
Espelhar a suíte do padaria/floricultura/comida (service + controller integration por entidade):
- PapelariaOrderStatusParityTest + PapelariaCategoryParityTest + ProfileTypeParityTest (+ fulfillment
  parity se enum).
- PapelariaMenuServiceTest + ControllerIntegrationTest (CRUD item+opções+made_to_order/lead; invalida
  cache; delete-em-uso 409; wrongProfile 403).
- PapelariaConfigServiceTest/ControllerIntegrationTest (GET fallback + PUT; lead default).
- PapelariaOrderServiceTest [CHAVE das escapadas]:
    * pronta-entrega sem data → OK (data null).
    * item sob encomenda sem data → 422 (data obrigatória).
    * encomenda com data < hoje+lead → 422 lead_time_violation (resposta traz 1ª data possível).
    * encomenda com data válida → OK; data exigida = MAX dos leads quando há vários itens.
    * personalização: unit_price = base + Σ deltas; line = unit_price × quantity(TIRAGEM); total bate;
      custom_text snapshot.
    * TIRAGEM: 100 convites × unit → line = unit×100; tiragem escala o total.
    * fulfillment entrega sem address → 422 address_required + soma taxa; retirada sem taxa/endereço.
    * total da IA DESCARTADO (recalcula); option_id inválido → aborta; snapshots preservados.
    * >>> FLUXO ARTE_APROVACAO (ESCAPADA): <<<
        - pedido nasce 'aguardando' art_approved=false.
        - aguardando→aceito (gate); aceito→arte_aprovacao (papelaria sobe arte / seta art_url).
        - arte_aprovacao→em_producao com art_approved=FALSE → 409 art_not_approved.
        - aprovar arte (art_approved=true) e arte_aprovacao→em_producao → OK + notifica.
        - <aprovacao_arte> num pedido NÃO em 'arte_aprovacao' → no-op (sem mudar estado).
- PedidoPapelariaConfirmHandlerTest: tag pronta-entrega; tag encomenda com personalização+data+tiragem;
  tag retirada vs entrega; option inválido/data inválida → empty; sem tag → empty; total bate.
- AprovacaoArteHandlerTest: pedido em 'arte_aprovacao' + <aprovacao_arte> → art_approved=true +
  →em_producao; pedido em outro estado → no-op; sem tag → empty.
- Status/gate: aguardando→aceito (aceite)→arte_aprovacao→em_producao→pronto→retirado (retirada) E
  pronto→saiu_entrega→entregue (entrega); recusado(motivo); transição inválida → 409;
  art_not_approved → 409; a IA não tem endpoint de aceite nem de subir arte.
mvn final = relatar contagem REAL do Surefire (Tests run: N), NUNCA grep @Test.

[CONSTRAINTS DUROS]
- Migration única (59). Sem foto/upload de arquivo (a arte é link/ref colada — art_url texto; sem
  imagem por SERVICE_ROLE_KEY).
- Cliente NÃO é entidade do core — continua o contact (pedido tem conversation_id/contact_id).
- made_to_order + lead_time (item override > config default); data CONDICIONAL (só se há encomenda); 422
  lead_time_violation com a primeira data possível.
- PERSONALIZAÇÃO via options (Papel/Acabamento/Cor/Tamanho) + custom_text snapshot. TIRAGEM = quantity
  (escala o line total).
- >>> ESCAPADA: PROVA DE ARTE — art_approved + estado 'arte_aprovacao'; arte_aprovacao→em_producao exige
  art_approved (409 art_not_approved); a IA SÓ registra a aprovação declarada (nunca aprova pelo cliente,
  nunca sobe arte). <<<
- fulfillment retirada × entrega (retirada sem taxa/endereço; funil diverge no fim).
- subtotal/total/unit_price materializados (não generated). Snapshots de item/opção/custom_text.
- Gate de aceite humano: nasce 'aguardando'; aceite/recusa no painel; a IA NUNCA aceita/recusa.
- Categorias hardcoded (parity). Tags <pedido_papelaria> e <aprovacao_arte> distintas de TODAS as outras.
- profile_id 'papelaria', productName 'Papelaria', subdomain 'papelaria'.
- ACRESCENTAR 'papelaria' ao CHECK/enum/const PRESERVANDO TODOS os perfis existentes — NUNCA remover um
  nicho (CRAVADO pelo Igor). Depois de qualquer sed, conferir que o CHECK tem TODOS + papelaria.
- NÃO mexer em outros perfis nem em system-template.txt. Webhook OFF.
- Cache de catálogo TTL 60s + invalidação em toda mutação do catálogo.
- 529 → inline. Gate 3× → pausar. Working tree sujo → pausar. git add EXPLÍCITO (nunca git add .);
  .env/.env.local/CONTEXT.md/evolution-local/.env/secrets NUNCA staged.
- SEED com timestamptz/date: `at time zone 'America/Sao_Paulo'`. IDs de namespace com sufixo NOVO (-13x).
- Tabela nova entra na migration ANTES de tocar o banco (lição os_config). Adicionar as tabelas ao
  TRUNCATE/SCRIPTS do AbstractIntegrationTest. Pool Hikari de teste minúsculo já em application-dev.yml
  (lição SM-K) — não estourar conexões do pooler Supabase com mais um contexto de teste.
- Decisões menores: agente decide (paleta, layout, se fulfillment vira enum+parity ou só CHECK, se art_url
  é link colado ou só o estado).

[PASSO FINAL — TENANT + SEED + COMMIT + PUSH + SMOKE + RELATÓRIO]
F.1 — TENANT igorhaf26 (Papelaria Modelo, profile=papelaria), padrão GoTrue (instance_id=zero-UUID +
      colunas de token='' não NULL — lição seed auth.users), senha em comunicação direta. company
      c?0000000-...-026 / user a?0000000-...-026. Caddy + /etc/hosts pra papelaria.meadadigital.local.
F.2 — Seed /tmp/seed-papelaria.sql (NÃO COMITAR; `at time zone 'America/Sao_Paulo'`; ids sufixo -13x):
  - config: taxa de entrega R$10, pedido mínimo R$50, lead_time_days_default 5.
  - catálogo:
    * PRONTA-ENTREGA: "Cartão Comemorativo" (cartoes, R$8, não made_to_order), "Adesivo Personalizado"
      (adesivos, R$2/un, made_to_order false), "Caixa de Presente" (embalagens, R$15).
    * SOB ENCOMENDA: "Convite de Casamento" (convites, base R$12/un, made_to_order, lead_time_days 10)
      com grupos: Papel (Comum +0, Couché +R$2, Pérola +R$5), Acabamento (Sem +0, Laminado +R$3,
      Hot Stamping +R$8), Cor (Clássico +0, Colorido +R$2). "Save the Date" (save_the_date, base R$6/un,
      made_to_order, lead_time_days 7) com grupo Papel (Comum +0, Couché +R$2).
  - contact "Diego Rocha" +5511955554444 (VINCULADO: instance+conversation, sufixo -13x) + contact
    "Elaine Souza" +5511944443333 (sem vínculo).
  - pedidos cobrindo estados/escapadas:
    * 'aguardando' VINCULADO (Diego) ENCOMENDA: 100 Convites de Casamento (Couché + Laminado + Colorido,
      custom_text "Casamento Ana & Bruno · 12/12/2026"), fulfillment 'entrega', data hoje+15d, período
      tarde, endereço → unit = 12+2+3+2 = 19; line = 19×100 = R$1900; + taxa R$10 → total R$1910; pra
      smoke de personalização + TIRAGEM + lead + aceite.
    * 'arte_aprovacao' (Diego) ENCOMENDA art_url setado, art_approved=false: 50 Save the Date (Couché),
      retirada, data hoje+10d; pra smoke do fluxo de ARTE (aprovar → em_producao).
    * 'em_preparo'/'aceito' (Elaine) PRONTA-ENTREGA retirada: 5 cartões + 1 caixa, sem data; pra smoke do
      funil retirada (após arte n/a — pronta-entrega pode pular arte? cravar: pronta-entrega segue
      aguardando→aceito→...→pronto SEM exigir arte; arte só faz sentido p/ made_to_order — documentar:
      itens só pronta-entrega podem ir aceito→em_producao OU direto p/ pronto, agente decide e crava no
      funil; recomendo: arte_aprovacao é OPCIONAL — só obrigatória quando há made_to_order; a transição
      aceito→arte_aprovacao OU aceito→em_producao ambas válidas, mas em_producao→... segue normal).
    * 'entregue' (Diego, passado) histórico.
  >>> NOTA DE FUNIL (cravar no arranque): a prova de arte é relevante p/ encomenda; pedido SÓ
      pronta-entrega pode pular 'arte_aprovacao'. Modelar a máquina de status pra permitir aceito→
      arte_aprovacao (encomenda) E aceito→em_producao/pronto (pronta-entrega) sem travar. art_not_approved
      só barra arte_aprovacao→em_producao. Decidir e documentar no CLAUDE.md.
F.3 — JwtFilter /api/papelaria/** (se ainda não).
F.4-F.6 — git add EXPLÍCITO dos arquivos da SM + sanity (git status -s; git diff --staged --stat; grep
      por segredo eyJ.../password/secret=; confirmar .env/.env.local/CONTEXT.md FORA da staging) + commit.
      Mensagem padrão multi-linha via `git commit -F` (feat(camada-8.15): perfil papelaria/Papelaria
      (Papelaria · Convites personalizados) com FUNDAÇÃO/BACKEND/FRONTEND/DECISÕES/VALIDAÇÃO contagem
      REAL/NÃO TOCADO/FECHAMENTO + Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>). Tag anotada
      fase-8.15-fechada (nº real confirmado no arranque).
F.7 — git push origin main + git push origin --tags (NUNCA --force).
F.8 — docker compose restart backend (ou ./scripts/run-local.sh) + aguardar GET /admin/me → 401.
F.9 — Smoke E2E (blocos A–G):
  BLOCO A: auth — igorhaf26 → /admin/me → role=tenant_admin, profileId=papelaria, productName=Papelaria.
  BLOCO B: catálogo + guard — GET menu (itens com made_to_order/lead + opções); CRUD smoke + invalida
    cache; delete em uso 409; GET config + PUT; tenant de OUTRO perfil (floricultura/padaria) →
    /api/papelaria/menu → 403.
  BLOCO C: PRONTA-ENTREGA — <pedido_papelaria> 5 cartões + 1 caixa, retirada, SEM data → 'aguardando' +
    total sem taxa; total da IA descartado.
  BLOCO D: ENCOMENDA + LEAD TIME + TIRAGEM + PERSONALIZAÇÃO [CHAVE] —
    - <pedido_papelaria> 100 Convites (Couché+Laminado+Colorido, custom_text), entrega, data hoje+15d →
      unit = 12+2+3+2 = 19; line = 19×100 = 1900; + taxa; custom_text snapshot bate; TIRAGEM escala.
    - mesma encomenda com data hoje+2d (lead=10) → 422 lead_time_violation (resposta traz hoje+10d).
    - pedido só pronta-entrega SEM data → OK; pedido com encomenda SEM data → 422.
    - entrega sem endereço → 422 address_required; retirada sem endereço → OK.
    - option_id inválido → não cria (empty).
  BLOCO E: gate de aceite + PROVA DE ARTE + funis (retirada × entrega) [CHAVE da escapada] —
    - aguardando→aceito (gate, Diego vinculado) → 200 + msg "recebemos / vamos preparar a arte".
    - aceito→arte_aprovacao (papelaria sobe art_url) → msg "sua arte está pronta, aprove".
    - arte_aprovacao→em_producao com art_approved=false → 409 art_not_approved.
    - <aprovacao_arte> do cliente (Diego, pedido em arte_aprovacao) → art_approved=true → arte_aprovacao→
      em_producao → 200 + msg "arte aprovada, vamos imprimir".
    - <aprovacao_arte> num pedido NÃO em arte_aprovacao → no-op (estado inalterado).
    - em_producao→pronto → msg "pronto pra retirada"; pronto→retirado (retirada) OK; outro pedido
      pronto→saiu_entrega→entregue (entrega); aguardando→recusado(motivo) → msg defensiva; transição
      inválida → 409; a IA não tem rota de aceite nem de subir arte.
  BLOCO F: regressão — perfis anteriores intactos (smoke leve 1 endpoint cada); papelaria → /api/comida/*
    → 403; papelaria → /api/padaria/* (se existir) / /api/floricultura/* → 403.
  BLOCO G: paridade — mvn test -Dtest=PapelariaOrderStatusParityTest,PapelariaCategoryParityTest,
    ProfileTypeParityTest → verde.
  Cleanup smoke + restaurar seed pristine. mvn final: contagem REAL (Tests run: N do Surefire).
F.10 — RELATÓRIO + DESTAQUE EXPLÍCITO:
  - "perfil papelaria/convites — CLONA a PADARIA (pedido com lead time + made_to_order + gate de aceite +
     data condicional + fulfillment)"
  - "ESCAPADA: PROVA DE ARTE — estado 'arte_aprovacao' + art_approved; arte_aprovacao→em_producao exige
     art_approved (409 art_not_approved); a IA SÓ registra a aprovação declarada (tag <aprovacao_arte>),
     nunca aprova pelo cliente nem sobe arte"
  - "TIRAGEM (quantity) escala o preço (line = unit × tiragem)"
  - "personalização do convite (papel/acabamento/cor via options + custom_text snapshot)"
  - "fulfillment retirada × entrega (retirada sem taxa/endereço; funil diverge no fim)"
  - "BLOCO D prova lead time, data condicional, TIRAGEM, personalização e address_required"
  - "BLOCO E prova o gate de aceite humano + o FLUXO DA PROVA DE ARTE + os dois funis"
  - "categorias próprias (convites/save_the_date/cartoes/papelaria/adesivos/embalagens)"
  - "CHECK/enum/const ACRESCENTARAM papelaria PRESERVANDO todos os perfis (não removeu nenhum)"
  - "Seed: at time zone + sufixo de ids -13x novo; tabelas DENTRO da migration (lição os_config)"
  - PENDÊNCIAS: upload da arte como arquivo/imagem (SERVICE_ROLE_KEY), versões/revisões da prova de arte,
     convite artístico sob orçamento ad-hoc, e-sign/contrato, assinatura recorrente, combo/cupom/
     fidelidade, gráfica externa, estoque/produção, Stripe + dívida acumulada (webhook OFF, cliente real,
     olho humano sobre os verticais).

[REPORTAR]
Igual SMs anteriores (decisões em prosa; bruto literal antes do Write; honestidade sobre incertezas;
contagem do Surefire, nunca grep @Test). Incluir EXPLICITAMENTE:
- "ProfileType.PAPELARIA adicionado (camada 8.15)"
- "Paridade PapelariaOrderStatus, PapelariaCategory e ProfileType validadas (+ fulfillment se enum)"
- "Tenant igorhaf26 criado (GoTrue + Caddy/etc/hosts)"
- "made_to_order + lead_time, data condicional, 422 lead_time_violation"
- "ESCAPADA: prova de arte (art_approved + estado arte_aprovacao + 409 art_not_approved); IA só registra
   a aprovação declarada via <aprovacao_arte>, nunca aprova pelo cliente"
- "TIRAGEM (quantity) escala o preço; personalização via options + custom_text snapshot"
- "fulfillment retirada × entrega; funil de status diverge no fim (retirado vs entregue)"
- "Gate de aceite humano (espelho padaria/floricultura): IA confirma recebimento, papelaria aceita/recusa"
- "Tags <pedido_papelaria> e <aprovacao_arte> distintas de todas as outras"
- "OutboundService ganhou maybeProcessPedidoPapelaria + maybeProcessAprovacaoArte"
- "getNavForProfile('papelaria') com branch próprio"
- "Cache de catálogo TTL 60s + invalidação em toda mutação"
- "tabelas criadas DENTRO da migration (lição os_config); seed com at time zone + sufixo -13x novo;
   CHECK/enum preservaram todos os perfis"
- "Próximas fases: upload de arte, versões da prova de arte, convite sob orçamento, Stripe + fila de
   prioridade"
