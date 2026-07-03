>>> SLOT ATRIBUÍDO (ver docs/prompts-nicho/README.md — fonte única de verdade, tem precedência
>>> sobre qualquer "provisório" no corpo): ordem 3 · camada 8.8 · migration 52_padaria.sql ·
>>> tenant igorhaf19 (company/user sufixo -019) · ids de seed sufixo -06x. Reconfirmar no arranque
>>> que a fila não avançou; se avançou, deslocar conforme o README.

[TAREFA — SUB-MARATONA: PERFIL PADARIA / Padaria (Padaria & Confeitaria) (camada 8.8)]

[CONTEXTO]
PROJETO MEADA em /home/igorhaf/meada.
17 perfis verticais reais hoje (… comida 8.4, floricultura 8.5 — o último fechado) + generic.
Lê CONTEXT.md e o filesystem no arranque pra cravar convenções, nº de migration, contagem do
Surefire e numeração de tenant ANTES de escrever qualquer código. NÃO hardcodar a contagem do
mvn — relatar a REAL do Surefire ao final. Valores esperados (CONFIRMAR no filesystem antes; podem
ter avançado se Casamento/Pizzaria/outra SM foi executada primeiro): migration 50_padaria, tenant
igorhaf17, company c?0000000-...-017, user a?0000000-...-017. IDs de namespace compartilhado
(contacts/instance/conversation) NO SEED com sufixo NOVO que NÃO colida com nenhum seed anterior.

Padaria é template de nicho pra PADARIA / CONFEITARIA dentro do mesmo dashboard Meada. Tenant
acessa padaria.meadadigital.local e vê o produto "Padaria". A IA atende clientes via WhatsApp,
conhece o cardápio (pães, salgados, doces de balcão de PRONTA-ENTREGA + bolos/doces SOB ENCOMENDA
personalizados), MONTA o pedido NA CONVERSA (carrinho relido do histórico a cada turno, igual
sushi/comida), e — quando há item sob encomenda — coleta a DATA de retirada/entrega + as escolhas
de personalização (sabor/recheio/peso/placa). Confirma SEMPRE com o valor total, avisa que o
pedido vai pra CONFIRMAÇÃO DA PADARIA. Tom caloroso e acolhedor, "de bairro".

>>> TRAVA DE COMPORTAMENTO DA IA (cravada) <
- NUNCA inventa produto, sabor, recheio, tamanho/peso, adicional ou preço fora do cardápio.
- NUNCA aceita NEM recusa o pedido — é AÇÃO HUMANA da padaria no painel (gate de aceite). A IA só
  CONFIRMA o RECEBIMENTO ("seu pedido foi enviado pra padaria") na própria mensagem.
- O total é SEMPRE recalculado pelo sistema — a IA pode somar pra orientar, mas o backend descarta
  o total da IA e recalcula a partir do cardápio.
- ENCOMENDA: a IA NUNCA promete uma data que não respeite a ANTECEDÊNCIA MÍNIMA (lead time) do
  item — se o cliente pedir um bolo "pra amanhã" e o lead time é 2 dias, a IA explica e oferece a
  primeira data possível; quem valida de fato é o backend (rejeita data < hoje+lead_time).
- NUNCA promete decoração/tema/desenho que não esteja no cardápio de personalização; bolo artístico
  complexo → "vou confirmar com a confeitaria". A IA não negocia o preço da personalização.

EVOLUÇÃO ESTRUTURAL: CLONA o chassi do FLORICULTURA (camada 8.5) — que por sua vez clonou o COMIDA
— cardápio + OPÇÕES/adicionais (modifiers) + carrinho-na-conversa + tag de pedido + recálculo de
total (descarta o total da IA) + snapshot de preço/nome + taxa/mínimo + Kanban de status + GATE DE
ACEITE HUMANO (pedido nasce 'aguardando'; padaria ACEITA→'em_preparo' ou RECUSA→'recusado' com
rejection_reason) + PEDIDO AGENDADO com DATA + PERÍODO (a floricultura já agenda a entrega por dia
+ faixa manhã/tarde). DUAS escapadas NOVAS que nem o comida nem a floricultura têm:

  ESCAPADA 1 — PRONTA-ENTREGA × SOB-ENCOMENDA no MESMO cardápio, com ANTECEDÊNCIA MÍNIMA (lead
  time): cada item do cardápio tem um flag made_to_order. Itens de pronta-entrega (pão, salgado,
  doce de balcão) NÃO exigem data/antecedência — saem na hora/no dia. Itens sob encomenda (bolo
  personalizado) exigem uma DATA de retirada/entrega que respeite o lead_time_days do item (ou o
  default da config). O backend REJEITA encomenda com data < hoje+lead_time → 422
  lead_time_violation (com a primeira data possível na resposta, defensivo). Um pedido pode misturar
  os dois; se houver QUALQUER item sob encomenda, a data passa a ser obrigatória e é a MAIOR
  antecedência exigida entre os itens. (A floricultura agenda SEMPRE; aqui a data é CONDICIONAL ao
  tipo de item — é a regra nova.)

  ESCAPADA 2 — PERSONALIZAÇÃO DO ITEM SOB ENCOMENDA (sabor/recheio/peso + texto de placa): além dos
  modifiers planos (espelho comida), um item sob encomenda carrega uma PERSONALIZAÇÃO estruturada —
  sabor da massa + recheio (escolhidos de grupos de opção com price_delta) + peso/tamanho (kg, que
  pode escalar o preço) + um TEXTO LIVRE de placa/topo ("Feliz aniversário, Ana", nullable). O
  texto de placa é snapshot no item do pedido. option_id inválido aborta a criação.

  RETIRADA × ENTREGA: o pedido tem fulfillment ('retirada'|'entrega'). 'retirada' = balcão, sem
  taxa de entrega e sem endereço obrigatório; 'entrega' = exige delivery_address e soma
  delivery_fee. (A floricultura é sempre entrega; a padaria tem balcão — é a diferença.)

NÃO TEM nesta SM (registrado pra não inventar): foto do bolo/referência visual de decoração
(bloqueador SERVICE_ROLE_KEY), orçamento de bolo artístico ad-hoc com aprovação (a personalização é
por opções cadastradas, não proposta livre — encomenda artística sob orçamento é fase futura, estilo
oficina/eventos), assinatura de pães/cesta recorrente (academia cobre recorrência), combo/cupom/
fidelidade, pagamento real (Stripe é #50), integração com iFood, controle de estoque/produção/forno,
slot por horário fino (é dia + faixa, igual floricultura), tabela nutricional/alérgenos estruturada
(alérgeno fica como texto livre informativo). Fases futuras.

DECISÕES CRAVADAS (revisor decidiu pelo Igor):
1. CLONA o chassi do FLORICULTURA (pedido agendado por dia+período + gate de aceite + cardápio +
   modifiers + carrinho + tag + total recalculado + taxa/mínimo + Kanban). MANTER onde não conflita.
2. Categorias hardcoded próprias (CHECK + enum Java + const TS + parity): paes, salgados,
   doces_balcao, bolos_encomenda, tortas, bebidas. (bolos_encomenda/tortas tendem a ser
   made_to_order; o flag por item é a verdade, não a categoria.)
3. ESCAPADA 1 — made_to_order + lead_time_days (por item; default na config). Data CONDICIONAL:
   obrigatória só se há item sob encomenda; é a maior antecedência exigida. 422 lead_time_violation.
4. ESCAPADA 2 — personalização: grupos de opção (Sabor/Recheio/Tamanho) via price_delta (espelho
   comida) + cake_message texto livre (snapshot no item). Peso/tamanho é um grupo de opção.
5. fulfillment 'retirada'|'entrega' (hardcoded, parity): retirada sem taxa/endereço; entrega com.
6. Gate de aceite humano: nasce 'aguardando'; aceite/recusa no painel; a IA não aceita/recusa.
   'aguardando' NÃO notifica (a IA já confirmou o recebimento).
7. Tag <encomenda_padaria> (namespace próprio, distinta de <pedido_comida>/<pedido_flor>/
   <pedido_pizza> e de todas as outras). Carrega itens (pronta-entrega e/ou encomenda) + fulfillment
   + data (quando aplicável) + personalização; o backend valida e recalcula tudo.

[FUNDAÇÃO — migration 50_padaria]
- ALTER companies CHECK aceitar 'padaria'.
- RLS enable+force, policies via app.company_id(), grants authenticated + service_role
  (orders/order_items/order_item_options: INSERT pelo BACKEND via service_role — o pedido é criado
  pela IA via EncomendaPadariaConfirmHandler; tenant só SELECT/UPDATE — status no Kanban / gate de
  aceite). Espelhar 49_floricultura.sql inteiro.
- subtotal_cents/total_cents/unit_price_cents MATERIALIZADOS no INSERT; NÃO colunas geradas.
- SNAPSHOTS: preço+nome em order_items; group/option/delta em order_item_options; cake_message no
  order_item. Alterar/excluir item/opção no cardápio NÃO altera pedidos passados.
- Tabelas:
  * padaria_config — taxa de entrega + pedido mínimo + lead_time_days_default (default p/ encomenda;
    int >= 0). 1:1 com company; ausente → taxa/mínimo = 0, lead default (ex.: 1). Clone
    floricultura_config + lead_time_days_default.
  * padaria_menu_items — cardápio. (id, company_id, category CHECK in sync com enum, name CHECK
    1..200, description, price_cents = preço base, made_to_order boolean NOT NULL default false,
    lead_time_days int nullable (override do default da config quando made_to_order), allergens text
    nullable (texto livre informativo), active default true, timestamps). Comment cravando
    made_to_order + lead_time.
  * padaria_menu_item_options — modifiers/personalização (grupos: Sabor, Recheio, Tamanho/Peso, …).
    Cada linha = UMA opção de UM grupo (group_label), price_delta_cents. Espelho
    comida/floricultura_*_item_options. on delete cascade.
  * padaria_orders — pedidos. status CHECK ('aguardando'|'em_preparo'|'pronto'|'retirado'|
    'saiu_entrega'|'entregue'|'recusado'|'cancelado') default 'aguardando' (ver nota de status
    abaixo — retirada e entrega divergem no fim do funil); fulfillment text CHECK
    ('retirada'|'entrega'); subtotal/delivery_fee/total materializados; delivery_address text
    NULLABLE (obrigatório só p/ entrega — validado no backend); pickup_or_delivery_date date NULLABLE
    (obrigatória só se há item sob encomenda); delivery_period text CHECK ('manha'|'tarde') NULLABLE;
    rejection_reason nullable (gate de aceite); conversation_id/contact_id NOT NULL; notes;
    timestamps + status_updated_at. Espelho floricultura_orders adaptado (date e period viram
    NULLABLE/condicionais; + fulfillment).
  * padaria_order_items — itens do pedido com snapshot de nome+preço. unit_price_cents JÁ inclui Σ
    deltas (sabor/recheio/peso). made_to_order_snapshot boolean. cake_message text NULLABLE
    (snapshot do texto de placa). quantity. Espelho comida/floricultura_order_items + cake_message.
  * padaria_order_item_options — opções/personalização escolhidas por item (snapshots de group/
    option/delta). Espelho comida_order_item_options.
- Status do pedido hardcoded (PadariaOrderStatus enum Java + const TS + parity test). Funil com os
  DOIS desfechos (retirada × entrega):
    aguardando → em_preparo, recusado, cancelado
    em_preparo → pronto, cancelado
    pronto     → retirado (retirada), saiu_entrega (entrega), cancelado
    saiu_entrega → entregue, cancelado
    retirado/entregue/recusado/cancelado → terminal
  (aceite = aguardando→em_preparo é o gate humano.) Notifica: em_preparo (aceito), pronto ("seu
  pedido está pronto pra retirada" — relevante na padaria), saiu_entrega, entregue, recusado (com
  motivo defensivo). aguardando/cancelado conforme padrão.
- fulfillment hardcoded (PadariaFulfillment enum Java + const TS + parity): retirada, entrega.
  (Decisão: pode ser enum próprio com parity OU CHECK simples — recomendo enum+parity p/ simetria;
  o agente decide se vira parity test separado ou fica só no CHECK.)
- Categorias hardcoded (PadariaCategory.java + padaria-categories.ts + PadariaCategoryParityTest):
  paes, salgados, doces_balcao, bolos_encomenda, tortas, bebidas.
- TODAS as tabelas novas entram na migration 50 ANTES de tocar o banco (lição os_config) e no
  TRUNCATE/SCRIPTS do AbstractIntegrationTest.

[BACKEND]
- Menu (cardápio): CRUD de itens (com made_to_order + lead_time_days + allergens) + opções
  (modifiers/personalização) — espelho floricultura/comida. Cache do bloco de cardápio injetado no
  prompt (Caffeine TTL 60s), INVALIDADO em toda gravação/edição/exclusão. delete de item com pedido
  → 409 menu_item_in_use.
- Config: GET (fallback taxa/mínimo = 0, lead default) + PUT.
- Orders: criados pelo BACKEND via EncomendaPadariaConfirmHandler. Recálculo: unit_price = base + Σ
  deltas (sabor/recheio/peso) × quantity; subtotal = Σ itens; total = subtotal + (entrega ?
  delivery_fee : 0). Validações cravadas:
    * se QUALQUER item made_to_order → pickup_or_delivery_date OBRIGATÓRIA; senão pode ser null.
    * data exigida = hoje + MAX(lead_time dos itens made_to_order) (item override > config default);
      data < isso → 422 lead_time_violation (resposta traz a primeira data possível).
    * fulfillment 'entrega' → delivery_address obrigatório (senão 422 address_required) + soma taxa;
      'retirada' → sem taxa, endereço pode ser null.
    * pedido mínimo: espelhar o que a floricultura/comida fazem.
    * option_id inválido aborta (sem pedido parcial). Snapshots completos (cake_message incluso).
- Status: PATCH com validação de transição (inválida → 409 invalid_status_transition) + gate de
  aceite (aguardando→em_preparo = aceitar; aguardando→recusado = recusar com rejection_reason).
  Notificação outbound por status (texto defensivo).
- IA:
  * Persona calorosa-acolhedora com a TRAVA DE COMPORTAMENTO embutida (não inventa item/preço, não
    aceita/recusa, total recalculado, respeita lead time, não promete decoração não cadastrada).
  * Contexto injetado = bloco de cardápio (itens por categoria, marcando quais são SOB ENCOMENDA com
    o lead time, + opções de personalização com deltas) + taxa/mínimo/lead default + instruções da
    tag <encomenda_padaria> (formato com fulfillment + data condicional + personalização). Cache TTL
    60s (IGNORA conversationId — contexto é o cardápio). Invalidação em toda mutação do cardápio.
  * Tag <encomenda_padaria>{"fulfillment":"retirada|entrega","pickup_or_delivery_date":"YYYY-MM-DD|
    null","delivery_period":"manha|tarde|null","delivery_address":"...|null","items":[{"menu_item_id",
    "options":[{"option_id"}],"cake_message":"...|null","quantity"}],"notes"} →
    EncomendaPadariaConfirmHandler (espelho PedidoFlorConfirmHandler + made_to_order/lead/fulfillment/
    cake_message). Best-effort; o OutboundService REMOVE a tag antes de enviar e o backend valida +
    recalcula.
  * JwtFilter autentica /api/padaria/. OutboundService ganha maybeProcessEncomendaPadaria (best-
    effort, encadeado após os outros perfis — perfil é único, só um age).

[FRONTEND]
- /dashboard/padaria-menu (CRUD itens + editor de opções/personalização inline; toggle made_to_order
  + campo lead_time_days quando ligado + allergens),
  /dashboard/padaria-orders (Kanban por status com o GATE DE ACEITE: Aceitar/Recusar na coluna
  'aguardando', recusa pede motivo; o detalhe mostra fulfillment, data/período quando há encomenda,
  e por item a personalização escolhida + o texto da placa),
  /dashboard/padaria-settings (taxa de entrega + pedido mínimo + lead time default).
- types + SDKs (menu, options, orders) espelhando floricultura + made_to_order/lead/fulfillment/
  cake_message.
- Status TS padaria-order-status.ts + PadariaCategory const + PadariaFulfillment const + parity tests
  (status + categorias [+ fulfillment se virar enum]).
- getNavForProfile('padaria') injeta "Padaria" (3 itens: Cardápio, Pedidos, Configurações), no mesmo
  padrão dos branches existentes (comida/floricultura já têm branch — seguir o modelo deles).
  Subdomínio padaria.meadadigital.local. Paleta: agente escolhe (sugestão quente/aconchegante livre,
  ex.: 'mostarda', 'abobora' ou 'por-do-sol').
- npm build limpo.

[DOCS]
- CLAUDE.md: seção "## Perfil Padaria (camada 8.x)" espelhando as seções de perfil + nota de que
  CLONA o FLORICULTURA e inaugura: pronta-entrega × sob-encomenda com LEAD TIME, personalização de
  bolo (sabor/recheio/peso/placa) e retirada × entrega. Documentar EXPLÍCITO: categorias próprias;
  made_to_order + lead_time (422 lead_time_violation); fulfillment; a tag <encomenda_padaria>.
- docs/PERFIL_PADARIA.md: guia operacional (cardápio com pronta-entrega e encomenda; personalização;
  pedidos + Kanban + gate de aceite; retirada × entrega; como a IA atende; "o que a IA NÃO faz").
  Espelhar PERFIL_FLORICULTURA.md.
- NÃO mexer em system-template.txt nem em outros perfis.

[TESTES BACKEND]
Espelhar a suíte do floricultura/comida (service + controller integration por entidade):
- PadariaOrderStatusParityTest + PadariaCategoryParityTest + ProfileTypeParityTest (+ fulfillment
  parity se enum).
- PadariaMenuServiceTest + ControllerIntegrationTest (CRUD item+opções+made_to_order/lead; invalida
  cache; delete-em-uso 409; wrongProfile 403).
- PadariaConfigServiceTest/ControllerIntegrationTest (GET fallback + PUT; lead default).
- PadariaOrderServiceTest [CHAVE das escapadas]:
    * pronta-entrega sem data → OK (data null).
    * item sob encomenda sem data → 422 (data obrigatória).
    * encomenda com data < hoje+lead → 422 lead_time_violation (resposta traz 1ª data possível).
    * encomenda com data válida → OK; data exigida = MAX dos leads quando há vários itens.
    * personalização: unit_price = base + Σ deltas (sabor+recheio+peso); cake_message snapshot.
    * fulfillment entrega sem address → 422 address_required + soma taxa; retirada sem taxa/endereço.
    * total da IA DESCARTADO (recalcula); option_id inválido → aborta; snapshots preservados.
- EncomendaPadariaConfirmHandlerTest: tag pronta-entrega; tag encomenda com personalização+data; tag
  com retirada vs entrega; option inválido/data inválida → empty; sem tag → empty; total bate.
- Status/gate: aguardando→em_preparo (aceite) → pronto → retirado (retirada) E pronto→saiu_entrega→
  entregue (entrega); recusado(motivo); transição inválida → 409; a IA não tem endpoint de aceite.
mvn final = relatar contagem REAL do Surefire.

[CONSTRAINTS DUROS]
- Migration única (50). Sem foto/anexo (decoração é por opção cadastrada + texto, não imagem).
- Cliente NÃO é entidade do core — continua o contact (pedido tem conversation_id/contact_id).
- ESCAPADA 1: made_to_order + lead_time (item override > config default); data CONDICIONAL (só se há
  encomenda); 422 lead_time_violation com a primeira data possível.
- ESCAPADA 2: personalização via options (Sabor/Recheio/Peso) + cake_message snapshot.
- fulfillment retirada × entrega (retirada sem taxa/endereço; entrega com).
- subtotal/total/unit_price materializados (não generated). Snapshots de item/opção/cake_message.
- Gate de aceite humano: nasce 'aguardando'; aceite/recusa no painel; a IA NUNCA aceita/recusa.
- Categorias hardcoded (parity). Tag <encomenda_padaria> distinta de TODAS as outras.
- NÃO mexer em outros perfis nem em system-template.txt. Webhook OFF.
- Cache de cardápio TTL 60s + invalidação em toda mutação do cardápio.
- 529 → inline. Gate 3× → pausar. Working tree sujo → pausar. git add EXPLÍCITO (nunca git add .);
  .env/CONTEXT.md/secrets NUNCA staged.
- SEED com timestamptz/date: `at time zone 'America/Sao_Paulo'`. IDs de namespace com sufixo NOVO.
- Tabela nova entra na migration ANTES de tocar o banco (lição os_config). Adicionar as tabelas ao
  TRUNCATE/SCRIPTS do AbstractIntegrationTest.
- Decisões menores: agente decide (paleta, layout, se fulfillment vira enum+parity ou só CHECK).

[PASSO FINAL — TENANT + SEED + COMMIT + PUSH + SMOKE + RELATÓRIO]
F.1 — TENANT igorhaf17 (Padaria Modelo, profile=padaria), padrão GoTrue, senha em comunicação
      direta. company c?0000000-...-017 / user a?0000000-...-017. Caddy + /etc/hosts pra
      padaria.meadadigital.local.
F.2 — Seed /tmp/seed-padaria.sql (NÃO COMITAR; `at time zone 'America/Sao_Paulo'`; ids sufixo novo):
  - config: taxa de entrega R$6, pedido mínimo R$20, lead_time_days_default 1.
  - cardápio:
    * PRONTA-ENTREGA: Pão francês (paes, R$1/un), Pão de queijo (salgados, R$5), Coxinha (salgados,
      R$8), Sonho (doces_balcao, R$7), Café (bebidas, R$5).
    * SOB ENCOMENDA: "Bolo Aniversário" (bolos_encomenda, base R$80, made_to_order, lead_time_days
      2) com grupos: Sabor (Chocolate +0, Cenoura +0, Red Velvet +R$20), Recheio (Brigadeiro +0,
      Ninho +R$15, Frutas +R$10), Tamanho (1kg base, 2kg +R$60, 3kg +R$120). "Torta Holandesa"
      (tortas, base R$60, made_to_order, lead_time 1).
  - contact "Bruno Lima" +5511977778888 (VINCULADO: instance+conversation) + contact "Carla Mendes"
    +5511966667777 (sem vínculo).
  - 4 pedidos cobrindo estados/escapadas:
    * 'aguardando' VINCULADO (Bruno) ENCOMENDA: 1 Bolo Aniversário (Red Velvet + Ninho + 2kg, placa
      "Feliz aniversário, Bruno!"), fulfillment 'entrega', data hoje+5d, período tarde, endereço →
      total = (80+20+15+60) + taxa6 = R$181; pra smoke de personalização + lead + aceite.
    * 'em_preparo' (Carla) PRONTA-ENTREGA retirada: 10 pães + 2 cafés, sem data; pra smoke do funil
      retirada (→pronto→retirado).
    * 'pronto' (Bruno) MISTO: 1 Torta Holandesa (encomenda, hoje+2d) + 2 coxinhas, fulfillment
      'retirada'; pra smoke de data condicional + notificação 'pronto'.
    * 'entregue' (Bruno, passado) histórico.
F.3 — JwtFilter /api/padaria/ (se ainda não).
F.4-F.6 — git add EXPLÍCITO dos arquivos da SM + sanity (sem .env/secrets/CONTEXT) + commit.
      Mensagem padrão (feat(camada-8): perfil padaria/Padaria (Padaria & Confeitaria) com FUNDAÇÃO/
      BACKEND/FRONTEND/DECISÕES/VALIDAÇÃO contagem REAL/NÃO TOCADO/FECHAMENTO + Co-Authored-By:
      Claude Opus 4.8). Tag fase-8.x-fechada (nº real confirmado no arranque).
F.7 — git push origin main + tags.
F.8 — docker compose restart backend + aguardar /admin/me → 401.
F.9 — Smoke E2E:
  BLOCO A: auth — igorhaf17 → /admin/me → role=tenant_admin, profileId=padaria, productName=Padaria.
  BLOCO B: cardápio + guard — GET menu (itens com made_to_order/lead + opções); CRUD smoke + invalida
    cache; delete em uso 409; GET config + PUT; tenant floricultura (ou outro) → /api/padaria/menu →
    403.
  BLOCO C: PRONTA-ENTREGA — <encomenda_padaria> 10 pães + café, retirada, SEM data → 'aguardando' +
    total sem taxa; total da IA descartado.
  BLOCO D: ENCOMENDA + LEAD TIME + PERSONALIZAÇÃO [CHAVE] —
    - <encomenda_padaria> Bolo (Red Velvet+Ninho+2kg, placa), entrega, data hoje+5d → unit_price =
      80+20+15+60 = 175; + taxa; cake_message snapshot bate.
    - mesma encomenda com data hoje+1d (lead=2) → 422 lead_time_violation (resposta traz hoje+2d).
    - pedido só pronta-entrega SEM data → OK; pedido com encomenda SEM data → 422.
    - entrega sem endereço → 422 address_required; retirada sem endereço → OK.
    - option_id inválido → não cria (empty).
  BLOCO E: gate de aceite + funis (retirada × entrega) — aguardando→em_preparo (aceite, Bruno
    vinculado) → 200 + msg; em_preparo→pronto → msg "pronto pra retirada"; pronto→retirado (retirada)
    OK; outro pedido pronto→saiu_entrega→entregue (entrega); aguardando→recusado(motivo) → msg
    defensiva; transição inválida → 409; a IA não tem rota de aceite.
  BLOCO F: regressão — perfis anteriores intactos (smoke leve 1 endpoint cada); padaria →
    /api/comida/* → 403; padaria → /api/floricultura/* → 403.
  BLOCO G: paridade — mvn test -Dtest=PadariaOrderStatusParityTest,PadariaCategoryParityTest,
    ProfileTypeParityTest → verde.
  Cleanup smoke + restaurar seed pristine. mvn final: contagem REAL.
F.10 — RELATÓRIO + DESTAQUE EXPLÍCITO:
  - "perfil padaria/confeitaria — CLONA o FLORICULTURA (pedido agendado + gate de aceite)"
  - "ESCAPADA 1: pronta-entrega × sob-encomenda com LEAD TIME (data condicional; 422
     lead_time_violation com a primeira data possível)"
  - "ESCAPADA 2: personalização do bolo (sabor/recheio/peso via options + texto de placa snapshot)"
  - "fulfillment retirada × entrega (retirada sem taxa/endereço; funil diverge no fim)"
  - "BLOCO D prova lead time, data condicional, personalização e address_required"
  - "BLOCO E prova o gate de aceite humano + os dois funis"
  - "categorias próprias (paes/salgados/doces_balcao/bolos_encomenda/tortas/bebidas)"
  - "Seed: at time zone + sufixo de ids novo; tabelas DENTRO da migration (lição os_config)"
  - PENDÊNCIAS: foto/referência de decoração, bolo artístico sob orçamento ad-hoc, assinatura de
     pães recorrente, combo/cupom/fidelidade, iFood, estoque/produção, Stripe + dívida acumulada
     (webhook, cliente real, olho humano sobre os verticais).

[REPORTAR]
Igual SMs anteriores. Incluir EXPLICITAMENTE:
- "ProfileType.PADARIA adicionado (camada 8.x)"
- "Paridade PadariaOrderStatus, PadariaCategory e ProfileType validadas (+ fulfillment se enum)"
- "Tenant igorhaf17 criado (GoTrue + Caddy/etc/hosts)"
- "ESCAPADA 1: made_to_order + lead_time, data condicional, 422 lead_time_violation"
- "ESCAPADA 2: personalização via options (sabor/recheio/peso) + cake_message snapshot"
- "fulfillment retirada × entrega; funil de status diverge no fim (retirado vs entregue)"
- "Gate de aceite humano (espelho floricultura): IA confirma recebimento, padaria aceita/recusa"
- "Tag <encomenda_padaria> distinta de <pedido_comida>/<pedido_flor>/<pedido_pizza> e das outras"
- "OutboundService ganhou maybeProcessEncomendaPadaria"
- "getNavForProfile('padaria') com branch próprio"
- "Cache de cardápio TTL 60s + invalidação em toda mutação"
- "tabelas criadas DENTRO da migration (lição os_config); seed com at time zone + sufixo novo"
- "Próximas fases: foto de decoração, bolo artístico sob orçamento, assinatura de pães, Stripe +
   fila de prioridade"
