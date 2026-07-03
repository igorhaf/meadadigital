>>> SLOT ATRIBUÍDO (ver docs/prompts-nicho/README.md — fonte única de verdade, tem precedência
>>> sobre qualquer "provisório" no corpo): ordem 5 · camada 8.10 · migration 54_lavanderia.sql ·
>>> tenant igorhaf21 (company/user sufixo -021) · ids de seed sufixo -08x. Reconfirmar no arranque
>>> que a fila não avançou; se avançou, deslocar conforme o README.

[TAREFA — SUB-MARATONA: PERFIL LAVANDERIA / Lavanderia (Lavagem com coleta e entrega agendadas) (camada 8.10)]

[CONTEXTO]
PROJETO MEADA em /home/igorhaf/meada.
17+ perfis verticais reais hoje (… comida 8.4, floricultura 8.5, padaria/pizzaria — confirmar o
último fechado no filesystem) + generic. Lê CONTEXT.md e o filesystem no arranque pra cravar
convenções, nº de migration, contagem do Surefire e numeração de tenant ANTES de escrever qualquer
código. NÃO hardcodar a contagem do mvn — relatar a REAL do Surefire ao final. Valores esperados
(CONFIRMAR no filesystem antes; PROVISÓRIOS porque há outros drafts disputando o slot —
50_pizzaria.sql JÁ existe, então a lavanderia tende a ser 51_lavanderia, mas há prompts de padaria/
casamento/outros disputando 50/51; cravar o PRIMEIRO número livre no arranque): migration
51_lavanderia (provisório — confirmar primeiro slot livre), tenant igorhaf17 (confirmar; pode ter
avançado pra 18/19 se padaria/pizzaria foram executadas antes), company c?0000000-...-0NN / user
a?0000000-...-0NN coerentes com o tenant cravado. IDs de namespace compartilhado (contacts/instance/
conversation) NO SEED com sufixo NOVO que NÃO colida com nenhum seed anterior.

Lavanderia é template de nicho pra LAVANDERIA dentro do mesmo dashboard Meada. Tenant acessa
lavanderia.meadadigital.local e vê o produto "Lavanderia". A IA atende clientes via WhatsApp,
conhece o catálogo de SERVIÇOS de lavagem (lavar, lavar+passar, lavagem a seco, passar, edredom/
pesados), MONTA o pedido NA CONVERSA (carrinho relido do histórico a cada turno, igual sushi/comida/
floricultura) — onde cada "item" é uma QUANTIDADE de peças por tipo de serviço (ex.: 5 camisas
lavar+passar, 1 edredom lavagem a seco) — e coleta a DATA de COLETA + período (manhã/tarde) + o
endereço. Confirma SEMPRE com o valor total E com a DATA DE ENTREGA prometida (calculada pelo
prazo), avisa que o pedido vai pra CONFIRMAÇÃO DA LAVANDERIA. Tom limpo, prático e acolhedor.

>>> TRAVA DE COMPORTAMENTO DA IA (cravada) <
- NUNCA inventa serviço, tipo de peça, adicional ou preço fora do catálogo.
- NUNCA aceita NEM recusa o pedido — é AÇÃO HUMANA da lavanderia no painel (gate de aceite). A IA só
  CONFIRMA o RECEBIMENTO ("seu pedido foi enviado pra lavanderia") na própria mensagem.
- O total é SEMPRE recalculado pelo sistema — a IA pode somar pra orientar, mas o backend descarta o
  total da IA e recalcula a partir do catálogo.
- A IA NUNCA promete REMOVER MANCHA, GARANTIR resultado, recuperar peça danificada ou "deixar como
  nova" — não dá laudo de tecido nem garantia de limpeza. Mancha/dano → "a equipe avalia a peça na
  coleta; faço o melhor possível, sem garantia de remoção total".
- A IA NUNCA promete uma DATA DE ENTREGA que não respeite o PRAZO DE TURNAROUND — a entrega prometida
  é SEMPRE data de coleta + MAX(turnaround dos itens). Se o cliente pedir "pra hoje à tarde" e o
  prazo é 2 dias, a IA explica e oferece a primeira data possível; quem valida de fato é o backend
  (rejeita entrega < coleta + turnaround → 422 turnaround_violation com a primeira data possível).
- A IA NUNCA cobra taxa/adicional fora do catálogo nem inventa urgência/expressa com sobretaxa (não
  há express nesta SM — ver NÃO TEM).

EVOLUÇÃO ESTRUTURAL: CLONA o chassi de PEDIDO AGENDADO do FLORICULTURA (camada 8.5) — que por sua
vez clonou o COMIDA — catálogo de SERVIÇOS + modifiers (opções) + carrinho-na-conversa + tag de
pedido + recálculo de total (descarta o total da IA) + snapshot de preço/nome + taxa/mínimo + Kanban
de status + GATE DE ACEITE HUMANO (pedido nasce 'aguardando'; lavanderia ACEITA→'coletado' ou
RECUSA→'recusado' com rejection_reason) + DATA agendada + PERÍODO (manhã/tarde). A ESCAPADA NOVA que
justifica ser perfil próprio (nem comida, nem floricultura, nem padaria têm):

  ESCAPADA — DUAS DATAS LIGADAS POR UM PRAZO DE TURNAROUND: o pedido tem DUAS datas, COLETA e
  ENTREGA, e elas NÃO são independentes — estão acopladas por um PRAZO. Cada serviço do catálogo tem
  um turnaround_days (prazo de processamento; ex.: lavar+passar = 1 dia, lavagem a seco = 3 dias,
  edredom = 2 dias). A data de ENTREGA prometida = data de COLETA + MAX(turnaround_days entre TODOS
  os itens do pedido). O backend VALIDA e MATERIALIZA a data de entrega: se a IA/cliente pedir uma
  entrega < coleta + MAX(turnaround), o backend REJEITA → 422 turnaround_violation, devolvendo na
  resposta a PRIMEIRA data de entrega possível (defensivo). Diferente da floricultura (1 data só de
  entrega) e do padrão lead-time da padaria (uma antecedência ANTES de UMA data): aqui são DUAS datas
  reais, ambas persistidas, com a segunda derivada da primeira por um prazo. Esta é a razão de não
  ser "só mais um delivery": o ciclo é coleta-processo-entrega com prazo calculado.

NÃO TEM nesta SM (registrado pra não inventar): foto da peça / referência de mancha/dano
(bloqueador SERVICE_ROLE_KEY), garantia/laudo de remoção de mancha, serviço EXPRESS / 24h com
sobretaxa (turnaround é fixo por serviço nesta fase — express é fase futura), peso real medido na
coleta com reprecificação (o "item" é por peça/quantidade declarada; pesagem que reajusta preço é
fase futura), rastreio de peça individual com etiqueta/QR, assinatura/plano mensal de lavagem
recorrente (academia cobre recorrência), combo/cupom/fidelidade, pagamento real (Stripe é #50),
integração com app de motoboy/rota, controle de máquinas/produção, slot por horário fino (é dia +
faixa manhã/tarde, igual floricultura), tabela de cuidado por tecido estruturada (instrução de
cuidado fica como texto livre informativo). Fases futuras.

DECISÕES CRAVADAS (revisor decidiu pelo Igor):
1. CLONA o chassi do FLORICULTURA (pedido agendado por dia+período + gate de aceite + catálogo +
   modifiers + carrinho + tag + total recalculado + taxa/mínimo + Kanban). MANTER onde não conflita.
2. Categorias/tipos de serviço hardcoded próprios (CHECK + enum Java + const TS + parity):
   lavar, lavar_passar, lavagem_seco, passar, edredom_pesados. O turnaround é por ITEM/serviço (cada
   linha do catálogo tem turnaround_days), não pela categoria — o flag/valor por item é a verdade.
3. ESCAPADA — DUAS DATAS: collect_date (coleta, obrigatória, >= hoje no fuso America/Sao_Paulo) +
   delivery_date MATERIALIZADA. delivery_date >= collect_date + MAX(turnaround dos itens); abaixo
   disso → 422 turnaround_violation (resposta traz a primeira entrega possível). MAX entre todos os
   itens (não soma): o processamento é paralelo, vale o item mais lento.
4. turnaround_days POR SERVIÇO (catálogo) + default na config (lavanderia_config.
   turnaround_days_default, int >= 0). Item sem override usa o default? NÃO — turnaround_days é NOT
   NULL no item (cada serviço tem prazo); o default da config é o que a UI sugere ao criar item e o
   piso quando nenhum item informar (cravar: item.turnaround_days NOT NULL; config.default só p/ UI/
   fallback de criação — manter simples, espelho do lead_time da padaria mas como NOT NULL no item).
5. period (manha|tarde) é o período da COLETA (hardcoded, parity — LavanderiaPeriod, clone do
   FloriculturaPeriod). A entrega herda o mesmo período (não há um segundo período independente
   nesta SM — simplificação cravada; período de entrega distinto é fase futura).
6. Gate de aceite humano: nasce 'aguardando'; aceite (aguardando→coletado) / recusa
   (aguardando→recusado com rejection_reason) no painel; a IA NUNCA aceita/recusa. 'aguardando' NÃO
   notifica (a IA já confirmou o recebimento).
7. Tag <pedido_lavanderia> (namespace próprio, distinta de <pedido_comida>/<pedido_flor>/
   <encomenda_padaria>/<pedido_pizza> e de TODAS as outras). Carrega itens (qtd por serviço) +
   collect_date + period + endereço; o backend valida, recalcula o total E materializa a delivery_date.
8. delivery_address SEMPRE obrigatório (a lavanderia COLETA e ENTREGA no endereço — não há balcão/
   retirada nesta SM, diferente da padaria; é sempre coleta+entrega). taxa de entrega + pedido mínimo
   espelham floricultura.

[FUNDAÇÃO — migration 51_lavanderia (PROVISÓRIO — confirmar o primeiro slot livre; 50 já é pizzaria)]
- ALTER companies CHECK aceitar 'lavanderia' (espelhar EXATAMENTE o ALTER de 49_floricultura.sql:
  drop constraint companies_profile_id_check + add com a lista INTEIRA atual + 'lavanderia' no fim;
  ler a lista REAL no arranque, NÃO copiar a deste prompt cegamente — pode ter pizzaria/padaria).
- RLS enable+force, policies via app.company_id(), grants authenticated + service_role
  (orders/order_items/order_item_options: INSERT pelo BACKEND via service_role — o pedido é criado
  pela IA via PedidoLavanderiaConfirmHandler; tenant só SELECT/UPDATE — status no Kanban / gate de
  aceite). Espelhar 49_floricultura.sql INTEIRO (cabeçalho, comments, índices, policies, grants).
- subtotal_cents/total_cents/unit_price_cents MATERIALIZADOS no INSERT; NÃO colunas geradas (o
  recálculo cruza linhas/tabelas — generated não serve; lição das migrations anteriores).
- delivery_date MATERIALIZADA no INSERT (collect_date + MAX(turnaround) — timestamptz/date + interval
  não é IMMUTABLE; NÃO coluna gerada; lição end_at do MesaBot/Dental reaplicada às DATAS).
- SNAPSHOTS: preço+nome em order_items; group/option/delta em order_item_options; turnaround_snapshot
  no order_item (o prazo do serviço no momento do pedido). Alterar/excluir serviço/opção no catálogo
  NÃO altera pedidos passados.
- Tabelas:
  * lavanderia_config — taxa de entrega + pedido mínimo + turnaround_days_default (int >= 0). 1:1 com
    company; ausente → taxa/mínimo = 0, turnaround default (ex.: 1). Clone floricultura_config +
    turnaround_days_default.
  * lavanderia_services — catálogo de SERVIÇOS de lavagem (clone floricultura_catalog_items). (id,
    company_id, name CHECK 1..120, description, price_cents = preço base POR PEÇA/UNIDADE, category
    CHECK in ('lavar','lavar_passar','lavagem_seco','passar','edredom_pesados') em sync com enum,
    turnaround_days int NOT NULL CHECK >= 0 (prazo do serviço), care_instructions text nullable
    (texto livre informativo de cuidado), available default true, timestamps). Comment cravando
    turnaround_days + "preço por peça".
  * lavanderia_service_options — modifiers (grupos: ex. "Acabamento": Passar a vapor +R$2; "Cuidado":
    Hipoalergênico +R$3). Cada linha = UMA opção de UM grupo (group_label), price_delta_cents (>= 0).
    Espelho floricultura_catalog_item_options. on delete cascade.
  * lavanderia_orders — pedidos. status CHECK ('aguardando'|'coletado'|'em_processo'|'pronto'|
    'saiu_entrega'|'entregue'|'recusado'|'cancelado') default 'aguardando'; subtotal/delivery_fee/
    total materializados; delivery_address text NOT NULL (sempre coleta+entrega); collect_date date
    NOT NULL (coleta, >= hoje no backend); delivery_date date NOT NULL (MATERIALIZADA = collect +
    MAX(turnaround)); period text CHECK ('manha'|'tarde') NOT NULL (período da coleta);
    rejection_reason nullable (gate de aceite); conversation_id/contact_id NOT NULL; notes; timestamps
    + status_updated_at. Espelho floricultura_orders adaptado (recipient_name/card_message REMOVIDOS
    — não é presente; + collect_date além de delivery_date; delivery_date vira MATERIALIZADA).
    Comment cravando as DUAS datas + turnaround.
  * lavanderia_order_items — itens do pedido com snapshot de nome+preço. unit_price_cents JÁ inclui Σ
    deltas das opções. qty (quantidade de peças). turnaround_snapshot int NOT NULL (prazo do serviço
    no momento). service_name_snapshot. Espelho floricultura_order_items + qty + turnaround_snapshot.
  * lavanderia_order_item_options — opções escolhidas por item (snapshots group/option/delta). Espelho
    floricultura_order_item_options.
- Status do pedido hardcoded (LavanderiaOrderStatus enum Java + const TS + parity test). Funil com o
  ciclo coleta→processo→entrega:
    aguardando   → coletado, recusado, cancelado
    coletado     → em_processo, cancelado
    em_processo  → pronto, cancelado
    pronto       → saiu_entrega, cancelado
    saiu_entrega → entregue, cancelado
    entregue/recusado/cancelado → terminal
  (aceite = aguardando→coletado é o gate humano — aceitar O PEDIDO É RECEBER AS PEÇAS na coleta.)
  Notifica: coletado (aceito — "recebemos suas peças, já vamos cuidar delas"), pronto ("suas peças
  estão prontas"), saiu_entrega, entregue, recusado (com motivo defensivo). aguardando silencioso (a
  IA já confirmou o recebimento); cancelado conforme padrão. notificationText() embutido no enum
  (espelho FloriculturaOrderStatus).
- Categorias/tipos hardcoded (LavanderiaServiceCategory.java + lavanderia-categories.ts +
  LavanderiaServiceCategoryParityTest): lavar, lavar_passar, lavagem_seco, passar, edredom_pesados.
- period hardcoded (LavanderiaPeriod.java + lavanderia-period.ts + parity, clone FloriculturaPeriod):
  manha, tarde.
- TODAS as tabelas novas entram na migration ANTES de tocar o banco (lição os_config) e no
  TRUNCATE/SCRIPTS do AbstractIntegrationTest.

[BACKEND]
- Catálogo (serviços): CRUD de serviços (com turnaround_days + care_instructions) + opções
  (modifiers) — espelho floricultura/comida. Cache do bloco de catálogo injetado no prompt (Caffeine
  TTL 60s), INVALIDADO em toda gravação/edição/exclusão. delete de serviço com pedido → 409
  service_in_use (espelho catalog_item_in_use).
- Config: GET (fallback taxa/mínimo = 0, turnaround default) + PUT.
- Orders: criados pelo BACKEND via PedidoLavanderiaConfirmHandler. Recálculo + materialização das
  datas. Validações cravadas:
    * unit_price = base_do_serviço + Σ deltas (opções); subtotal = Σ (unit_price × qty); total =
      subtotal + delivery_fee (sempre entrega).
    * collect_date OBRIGATÓRIA e >= hoje (fuso America/Sao_Paulo) → senão 422 (invalid_collect_date
      ou collect_date_in_past — cravar a mensagem).
    * delivery_date MATERIALIZADA = collect_date + MAX(turnaround_snapshot entre os itens). Se a tag
      trouxer uma delivery_date e ela for < collect_date + MAX(turnaround) → 422 turnaround_violation
      (resposta inclui a primeira delivery_date possível). Se a tag NÃO trouxer delivery_date, o
      backend CALCULA e materializa (a IA não precisa mandar — mas se mandar, valida).
    * MAX (não soma) dos turnarounds: processamento paralelo, vale o serviço mais lento.
    * delivery_address obrigatório (sempre coleta+entrega) → senão 422 address_required.
    * pedido mínimo: espelhar floricultura/comida (subtotal < min → 422 below_minimum ou o código que
      a floricultura usa — ler e reusar a MESMA mensagem).
    * option_id inválido aborta (sem pedido parcial). Snapshots completos (turnaround_snapshot incluso).
    * total da IA DESCARTADO (recalcula).
- Status: PATCH com validação de transição (inválida → 409 invalid_status_transition) + gate de
  aceite (aguardando→coletado = aceitar; aguardando→recusado = recusar com rejection_reason).
  Notificação outbound por status (texto defensivo, do enum + concat do rejection_reason no recusado).
- IA:
  * Persona limpa-prática-acolhedora com a TRAVA DE COMPORTAMENTO embutida (não inventa serviço/preço,
    não aceita/recusa, total recalculado, respeita turnaround, NUNCA promete remover mancha/garantir
    resultado). Adicionar/ajustar o bloco em ProfilePromptContext (espelho do bloco floricultura —
    LER ProfilePromptContext.java e seguir o MESMO formato de constante/segmento).
  * Contexto injetado = bloco de catálogo (serviços por categoria, marcando o PRAZO (turnaround_days)
    de cada um + opções com deltas) + taxa/mínimo/turnaround default + instruções da tag
    <pedido_lavanderia> (formato com collect_date + period + endereço + itens com qty). Cache TTL 60s
    (IGNORA conversationId — contexto é o catálogo). Invalidação em toda mutação do catálogo.
  * Tag <pedido_lavanderia>{"collect_date":"YYYY-MM-DD","period":"manha|tarde","delivery_address":
    "...","delivery_date":"YYYY-MM-DD|null","items":[{"service_id","options":[{"option_id"}],"qty"}],
    "notes"} → PedidoLavanderiaConfirmHandler (espelho PedidoFlorConfirmHandler + qty + as DUAS datas;
    delivery_date opcional — o backend materializa/valida). Best-effort; o OutboundService REMOVE a
    tag antes de enviar e o backend valida + recalcula + materializa a entrega.
  * JwtFilter autentica /api/lavanderia/** (adicionar à allowlist do JwtAuthenticationFilter, espelho
    de /api/floricultura/**). OutboundService ganha maybeProcessPedidoLavanderia (best-effort,
    encadeado APÓS os outros perfis — perfil é único, só um age; seguir o padrão dos
    maybeProcessPedidoFlor/maybeProcessPedidoComida no encadeamento).

[FRONTEND]
- /dashboard/lavanderia-services (CRUD serviços + editor de opções inline; campo turnaround_days +
  care_instructions; preço "por peça"),
  /dashboard/lavanderia-orders (Kanban por status com o GATE DE ACEITE: Aceitar/Recusar na coluna
  'aguardando', recusa pede motivo; o detalhe mostra as DUAS datas — coleta e entrega prometida —, o
  período, o endereço, e por item a qty + opções escolhidas),
  /dashboard/lavanderia-settings (taxa de entrega + pedido mínimo + turnaround default).
- types + SDKs (services, options, orders) espelhando floricultura + qty/turnaround/duas datas.
- Status TS lavanderia-order-status.ts + LavanderiaServiceCategory const (lavanderia-categories.ts) +
  LavanderiaPeriod const (lavanderia-period.ts) + parity tests (status + categorias + period).
- getNavForProfile('lavanderia') injeta "Lavanderia" (3 itens: Serviços, Pedidos, Configurações), no
  MESMO padrão dos branches existentes (floricultura/comida já têm branch — LER nav-config.tsx e
  seguir o branch da floricultura). Subdomínio lavanderia.meadadigital.local.
  Paleta: 'celeste' (ou 'oceano'/'teal' — limpo/água; agente escolhe entre essas, registrar a
  escolhida). Confirmar como a paleta é declarada (07_palette_id.sql / componente de tema) e seguir.
- npm build limpo (next build de prod — Turbopack dev esconde import quebrado).

[DOCS]
- CLAUDE.md: seção "## Perfil Lavanderia (camada 8.x)" espelhando as seções de perfil + nota de que
  CLONA o FLORICULTURA (pedido agendado por dia+período + gate de aceite) e inaugura: DUAS DATAS
  (coleta + entrega) ligadas por TURNAROUND calculado (MAX dos prazos; 422 turnaround_violation com a
  primeira entrega possível). Documentar EXPLÍCITO: categorias/serviços próprios; turnaround_days por
  serviço; sempre coleta+entrega (sem retirada de balcão); a tag <pedido_lavanderia>; a trava "NUNCA
  promete remover mancha".
- docs/PERFIL_LAVANDERIA.md: guia operacional (catálogo de serviços com prazo; opções; pedidos +
  Kanban + gate de aceite; as duas datas e como a entrega é calculada; como a IA atende; "o que a IA
  NÃO faz" — incluindo a trava de mancha/garantia). Espelhar PERFIL_FLORICULTURA.md.
- NÃO mexer em system-template.txt nem em outros perfis.

[TESTES BACKEND]
Espelhar a suíte do floricultura/comida (service + controller integration por entidade):
- LavanderiaOrderStatusParityTest + LavanderiaServiceCategoryParityTest + LavanderiaPeriodParityTest
  + ProfileTypeParityTest.
- LavanderiaServiceCatalogServiceTest + ControllerIntegrationTest (CRUD serviço+opções+turnaround;
  invalida cache; delete-em-uso 409; wrongProfile 403).
- LavanderiaConfigServiceTest/ControllerIntegrationTest (GET fallback + PUT; turnaround default).
- LavanderiaOrderServiceTest [CHAVE da escapada]:
    * pedido simples (1 serviço, qty) → OK; total = base × qty + taxa; delivery_date materializada =
      collect + turnaround.
    * MAX dos turnarounds: 2 serviços (turnaround 1 e 3) → delivery_date = collect + 3 (não 4, não 1).
    * delivery_date enviada < collect + MAX(turnaround) → 422 turnaround_violation (resposta traz a
      primeira data possível = collect + MAX).
    * delivery_date omitida → backend materializa (collect + MAX); enviada e VÁLIDA (>= collect+MAX) →
      aceita o valor (igual ao materializado).
    * collect_date no passado → 422.
    * personalização/opções: unit_price = base + Σ deltas; snapshots (nome/preço/turnaround/opção)
      preservados.
    * address ausente → 422 address_required.
    * pedido mínimo: subtotal < min → 422 (mesma mensagem da floricultura).
    * total da IA DESCARTADO (recalcula); option_id inválido → aborta; snapshots preservados.
- PedidoLavanderiaConfirmHandlerTest: tag simples; tag com opções + duas datas; tag com delivery_date
  omitida (materializa) vs inválida (422 via service → handler devolve empty); option inválido/data
  inválida → empty; sem tag → empty; total bate.
- Status/gate: aguardando→coletado (aceite) → em_processo → pronto → saiu_entrega → entregue;
  aguardando→recusado(motivo); transição inválida (ex.: aguardando→pronto) → 409; a IA não tem
  endpoint de aceite.
mvn final = relatar contagem REAL do Surefire (NÃO hardcodar; gate empírico).

[CONSTRAINTS DUROS]
- Migration única (51 provisório — confirmar o primeiro slot livre no arranque). Sem foto/anexo
  (cuidado é texto livre, não imagem).
- Cliente NÃO é entidade do core — continua o contact (pedido tem conversation_id/contact_id).
- ESCAPADA: DUAS datas (collect_date obrigatória + delivery_date MATERIALIZADA = collect +
  MAX(turnaround)); 422 turnaround_violation com a primeira entrega possível. turnaround é o MAX (não
  soma) dos itens. delivery_date materializada no INSERT (NÃO coluna gerada — lição end_at).
- turnaround_days NOT NULL por serviço; config.turnaround_days_default só p/ UI/fallback de criação.
- period (manha|tarde) é o da COLETA; entrega herda (sem segundo período nesta SM).
- delivery_address SEMPRE obrigatório (sempre coleta+entrega; sem retirada de balcão — diferença pra
  padaria).
- subtotal/total/unit_price materializados (não generated). Snapshots de item/opção/turnaround.
- Gate de aceite humano: nasce 'aguardando'; aceite/recusa no painel; a IA NUNCA aceita/recusa.
- Categorias/serviços hardcoded (parity) + period hardcoded (parity). Tag <pedido_lavanderia> distinta
  de TODAS as outras.
- A IA NUNCA promete remover mancha / garantir resultado de limpeza.
- NÃO mexer em outros perfis nem em system-template.txt. Webhook OFF (não religar).
- Cache de catálogo TTL 60s + invalidação em toda mutação do catálogo.
- 529 → inline retry. Gate 3× → pausar. Working tree sujo → pausar. git add EXPLÍCITO (nunca
  git add .); .env/CONTEXT.md/secrets NUNCA staged.
- SEED com timestamptz/date: `at time zone 'America/Sao_Paulo'`. IDs de namespace com sufixo NOVO.
- Tabela nova entra na migration ANTES de tocar o banco (lição os_config). Adicionar as tabelas ao
  TRUNCATE/SCRIPTS do AbstractIntegrationTest.
- Decisões menores: agente decide (paleta entre celeste/oceano/teal, layout, mensagens exatas das
  notificações).

[PASSO FINAL — TENANT + SEED + COMMIT + PUSH + SMOKE + RELATÓRIO]
F.1 — TENANT igorhaf17 (Lavanderia Modelo, profile=lavanderia) — CONFIRMAR a numeração real no
      arranque (pode ser 18/19 se padaria/pizzaria já criaram tenants); padrão GoTrue, senha em
      comunicação direta. company/user com IDs coerentes com o nº cravado. Caddy + /etc/hosts pra
      lavanderia.meadadigital.local.
F.2 — Seed /tmp/seed-lavanderia.sql (NÃO COMITAR; `at time zone 'America/Sao_Paulo'`; ids sufixo
      NOVO que não colide com seeds anteriores; lição os_config — tabelas já na migration):
  - config: taxa de entrega R$8, pedido mínimo R$25, turnaround_days_default 1.
  - catálogo (preço POR PEÇA):
    * "Lavar camisa" (lavar, R$8/peça, turnaround 1).
    * "Lavar + passar camisa" (lavar_passar, R$12/peça, turnaround 1) com grupo "Acabamento"
      (Passar a vapor +R$2).
    * "Passar (só)" (passar, R$6/peça, turnaround 1).
    * "Lavagem a seco terno" (lavagem_seco, R$45/peça, turnaround 3).
    * "Edredom casal" (edredom_pesados, R$50/peça, turnaround 2) com grupo "Cuidado"
      (Hipoalergênico +R$3).
  - contact "Daniel Rocha" +5511955554444 (VINCULADO: instance+conversation) + contact "Elaine Souza"
    +5511944443333 (sem vínculo).
  - 4 pedidos cobrindo estados/escapada (TODOS com endereço; datas com `at time zone`):
    * 'aguardando' VINCULADO (Daniel): 1 Lavagem a seco terno (turnaround 3) + 1 Edredom casal
      (Hipoalergênico, turnaround 2), collect_date hoje, period manha → delivery_date materializada =
      hoje+3 (MAX(3,2)); total = (45 + (50+3)) + taxa8 = R$106; pra smoke do MAX + materialização +
      aceite.
    * 'em_processo' (Elaine): 5 "Lavar + passar camisa" (Passar a vapor) → unit (12+2)=14 × 5 = 70 +
      taxa = R$78; collect hoje-1, delivery hoje (turnaround 1); pra smoke do funil
      (em_processo→pronto→saiu_entrega→entregue).
    * 'pronto' (Daniel): 3 "Passar (só)" → 18 + taxa = R$26; collect hoje-1, delivery hoje; pra smoke
      da notificação 'pronto' e do funil de entrega.
    * 'entregue' (Daniel, passado) histórico.
F.3 — JwtFilter /api/lavanderia/** (se ainda não).
F.4-F.6 — git add EXPLÍCITO dos arquivos da SM + sanity (git status -s + diff --staged --stat + grep
      por segredo eyJ.../password/secret=; sem .env/CONTEXT.md/secrets staged) + commit.
      Mensagem padrão (feat(camada-8): perfil lavanderia/Lavanderia (lavagem com coleta e entrega
      agendadas) com FUNDAÇÃO/BACKEND/FRONTEND/DECISÕES/VALIDAÇÃO contagem REAL/NÃO TOCADO/FECHAMENTO
      + Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>). Tag fase-8.x-fechada (nº real
      confirmado no arranque).
F.7 — git push origin main + git push origin --tags (NUNCA --force).
F.8 — docker compose restart backend + aguardar /admin/me → 401 missing_auth_header.
F.9 — Smoke E2E:
  BLOCO A: auth — igorhaf17 → /admin/me → role=tenant_admin, profileId=lavanderia,
    productName=Lavanderia.
  BLOCO B: catálogo + guard — GET services (itens com turnaround + opções); CRUD smoke + invalida
    cache; delete em uso 409; GET config + PUT; tenant floricultura (ou outro) → /api/lavanderia/
    services → 403 forbidden_wrong_profile.
  BLOCO C: PEDIDO SIMPLES — <pedido_lavanderia> 3 "Passar (só)" qty, collect hoje, manha → 'aguardando'
    + total (18 + taxa); delivery_date materializada = hoje + 1; total da IA descartado.
  BLOCO D: TURNAROUND + DUAS DATAS [CHAVE] —
    - <pedido_lavanderia> 1 terno (turnaround 3) + 1 edredom (turnaround 2), collect hoje →
      delivery_date materializada = hoje+3 (prova o MAX, não soma, não 2).
    - mesmo pedido com delivery_date = hoje+1 (< collect + MAX 3) → 422 turnaround_violation (resposta
      traz hoje+3 como primeira data possível).
    - delivery_date omitida → materializa (hoje+3); delivery_date = hoje+3 explícita → aceita.
    - collect_date no passado → 422.
    - address ausente → 422 address_required.
    - option_id inválido → não cria (empty); subtotal < mínimo → 422.
  BLOCO E: gate de aceite + funil — aguardando→coletado (aceite, Daniel vinculado) → 200 + msg;
    coletado→em_processo; em_processo→pronto → msg "suas peças estão prontas"; pronto→saiu_entrega→
    entregue; outro aguardando→recusado(motivo) → msg defensiva; transição inválida (aguardando→pronto)
    → 409; a IA não tem rota de aceite.
  BLOCO F: regressão — perfis anteriores intactos (smoke leve 1 endpoint cada); lavanderia →
    /api/comida/* → 403; lavanderia → /api/floricultura/* → 403.
  BLOCO G: paridade — mvn test -Dtest=LavanderiaOrderStatusParityTest,
    LavanderiaServiceCategoryParityTest,LavanderiaPeriodParityTest,ProfileTypeParityTest → verde.
  Cleanup smoke + restaurar seed pristine. mvn final: contagem REAL.
F.10 — RELATÓRIO + DESTAQUE EXPLÍCITO:
  - "perfil lavanderia — CLONA o FLORICULTURA (pedido agendado por dia+período + gate de aceite)"
  - "ESCAPADA: DUAS datas (coleta + entrega) ligadas por TURNAROUND calculado = collect + MAX(prazos
     dos itens); 422 turnaround_violation com a primeira entrega possível; delivery_date materializada
     no INSERT (lição end_at)"
  - "MAX (não soma) dos turnarounds: processamento paralelo, vale o serviço mais lento"
  - "sempre coleta+entrega (delivery_address obrigatório; sem retirada de balcão — diferença pra padaria)"
  - "BLOCO D prova turnaround_violation, MAX, materialização da entrega e address_required"
  - "BLOCO E prova o gate de aceite humano + o funil coletado→em_processo→pronto→saiu_entrega→entregue"
  - "categorias/serviços próprios (lavar/lavar_passar/lavagem_seco/passar/edredom_pesados)"
  - "trava de IA: NUNCA promete remover mancha/garantir resultado"
  - "Seed: at time zone + sufixo de ids novo; tabelas DENTRO da migration (lição os_config)"
  - PENDÊNCIAS: foto/referência de mancha, garantia/laudo de remoção, express/24h com sobretaxa,
     pesagem real com reprecificação, etiqueta/QR por peça, assinatura recorrente, combo/cupom/
     fidelidade, iFood/motoboy, Stripe + dívida acumulada (webhook, cliente real, olho humano sobre
     os verticais).

[REPORTAR]
Igual SMs anteriores. Incluir EXPLICITAMENTE:
- "ProfileType.LAVANDERIA adicionado (camada 8.x)"
- "Paridade LavanderiaOrderStatus, LavanderiaServiceCategory, LavanderiaPeriod e ProfileType validadas"
- "Tenant igorhaf17 criado (GoTrue + Caddy/etc/hosts) — nº real confirmado no arranque"
- "ESCAPADA: DUAS datas (coleta + entrega) com turnaround calculado (MAX dos prazos); 422
   turnaround_violation com a primeira entrega possível; delivery_date materializada no INSERT"
- "MAX (não soma) dos turnarounds entre os itens"
- "sempre coleta+entrega (delivery_address obrigatório; sem retirada de balcão)"
- "Gate de aceite humano (espelho floricultura): IA confirma recebimento, lavanderia aceita
   (aguardando→coletado) / recusa"
- "Funil coletado→em_processo→pronto→saiu_entrega→entregue"
- "Tag <pedido_lavanderia> distinta de <pedido_flor>/<pedido_comida>/<encomenda_padaria>/<pedido_pizza>
   e das outras"
- "OutboundService ganhou maybeProcessPedidoLavanderia (encadeado após os outros perfis)"
- "getNavForProfile('lavanderia') com branch próprio; paleta celeste/oceano/teal (registrar a escolhida)"
- "Cache de catálogo TTL 60s + invalidação em toda mutação"
- "tabelas criadas DENTRO da migration (lição os_config); seed com at time zone + sufixo novo"
- "Migration nº PROVISÓRIO (51 esperado; 50 já é pizzaria) — confirmado o primeiro slot livre no arranque"
- "Próximas fases: foto/referência de mancha, express/24h, pesagem com reprecificação, etiqueta/QR,
   assinatura recorrente, Stripe + fila de prioridade"
