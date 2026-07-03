>>> SLOT ATRIBUÍDO (ver docs/prompts-nicho/README.md — fonte única de verdade, tem precedência
>>> sobre qualquer "provisório" no corpo): ordem 7 · camada 8.12 · migration 56_otica.sql ·
>>> tenant igorhaf23 (company/user sufixo -023) · ids de seed sufixo -10x. Reconfirmar no arranque
>>> que a fila não avançou; se avançou, deslocar conforme o README.

[TAREFA — SUB-MARATONA: PERFIL ÓTICA / Ótica (Loja de Ótica: exame de vista + óculos sob receita) (camada 8.12)]

[CONTEXTO]
PROJETO MEADA em /home/igorhaf/meada.
17 perfis verticais reais hoje (… comida 8.4, floricultura 8.5, pizzaria 8.6 — o último fechado) +
generic. Lê CONTEXT.md e o filesystem no arranque pra cravar convenções, nº de migration, contagem
do Surefire e numeração de tenant ANTES de escrever qualquer código. NÃO hardcodar a contagem do mvn
— relatar a REAL do Surefire ao final. Valores esperados (CONFIRMAR no filesystem antes; são
PROVISÓRIOS porque há OUTROS DRAFTS disputando o slot — Casamento/Padaria/Pizzaria/outra SM podem ter
sido executados primeiro e avançado os números): migration 51_otica (50 já é pizzaria no disco; CONFIRMAR
o primeiro nº LIVRE), tenant igorhaf18 (17 já disputado por padaria/pizzaria drafts — CONFIRMAR o
primeiro LIVRE), company c?0000000-...-018, user a?0000000-...-018. IDs de namespace compartilhado
(contacts/instance/conversation) NO SEED com sufixo NOVO que NÃO colida com nenhum seed anterior.

Ótica é template de nicho pra LOJA DE ÓTICA dentro do mesmo dashboard Meada. Tenant acessa
otica.meadadigital.local e vê o produto "Ótica". A IA atende clientes via WhatsApp e faz AS DUAS
COISAS que uma ótica faz: (1) AGENDA EXAME DE VISTA com um profissional (optometrista) — escolhe
data/hora num horário livre e confirma; e (2) registra ENCOMENDA DE ÓCULOS sob receita — monta o
pedido na conversa (armação do catálogo + par de lentes com tipo escolhido via modifiers), coleta os
DADOS DE RECEITA que o cliente fornecer (grau OD/OE + DP) ou anota "trazer receita", e informa o PRAZO
de entrega (lead time da montagem em laboratório). Tom prestativo, atencioso e claro. A IA NUNCA
interpreta grau nem dá conduta de saúde.

>>> TRAVA DE COMPORTAMENTO DA IA (cravada — o coração desta SM) <<<
- A IA NUNCA prescreve grau, NUNCA diagnostica problema de visão (miopia/astigmatismo/presbiopia/etc.),
  NUNCA recomenda tipo de lente como CONDUTA DE SAÚDE. Para qualquer dúvida de visão/grau/sintoma →
  "para isso o exame/optometrista avalia" e oferece AGENDAR o exame.
- Sobre RECEITA: a IA só REGISTRA os dados de grau que o CLIENTE fornecer (esférico/cilíndrico/eixo
  OD e OE + DP), como CAMPOS ADMINISTRATIVOS do pedido — ela NÃO calcula, NÃO valida, NÃO interpreta o
  grau. Se o cliente não tem os dados, a IA anota "trazer receita" (flag prescription_pending) — a
  montagem só procede depois que a LOJA confirma a receita válida no painel.
- A IA NUNCA inventa armação, lente, tipo de lente, tratamento ou PREÇO fora do catálogo. Tipo de
  lente (monofocal/multifocal/antirreflexo/transitions) é OPÇÃO de catálogo com price_delta — a IA
  oferece o que existe, não cria.
- A IA NUNCA aceita NEM recusa a encomenda — é AÇÃO HUMANA da loja no painel (gate de aceite). A IA só
  CONFIRMA o RECEBIMENTO ("seu pedido foi enviado pra loja") na própria mensagem.
- O total é SEMPRE recalculado pelo sistema — a IA pode somar pra orientar, mas o backend DESCARTA o
  total da IA e recalcula a partir do catálogo (armação + Σ deltas das lentes × qtd).
- ENCOMENDA: a IA NUNCA promete uma data de entrega que não respeite o PRAZO MÍNIMO (lead time da
  montagem) — se o cliente quer "pra amanhã" e o lead time é 7 dias úteis, a IA explica e oferece a
  primeira data possível; quem valida de fato é o backend (rejeita data < hoje+lead_time).
- EXAME: a IA NUNCA marca exame em horário ocupado do profissional nem fora da janela de funcionamento
  — quem valida o conflito é o backend (rejeita choque → 409 conflict_slot).

EVOLUÇÃO ESTRUTURAL: a ESCAPADA que justifica perfil próprio é a CONVIVÊNCIA HARMÔNICA DE DOIS CHASSIS
JÁ EXISTENTES no MESMO perfil — agenda-clínica-leve + order-com-receita-e-prazo — com DUAS TAGS
distintas (uma agenda exame, outra encomenda óculos). É o PRIMEIRO perfil que combina agenda por
profissional (estilo dental) com pedido order-based + modifiers + gate de aceite + lead time de
laboratório (estilo comida/floricultura/padaria). Os dois fluxos não se tocam; o perfil é único, mas
expõe DOIS subdomínios funcionais. Detalhe:

  FLUXO A — AGENDA DE EXAME (clona o chassi do DENTAL, camada 7.4): optometrista (profissional) +
  horário + CONFLITO POR PROFISSIONAL (janela half-open, re-verificado DENTRO da transação do INSERT —
  fecha a janela de corrida) + end_at MATERIALIZADO no INSERT (start_at + duration_minutes; NÃO coluna
  gerada — lição timestamptz+interval não é IMMUTABLE) + duração SNAPSHOT do config + status hardcoded
  com parity (agendado→confirmado→realizado; agendado/confirmado→cancelado; confirmado→falta). A IA
  agenda o exame via tag <exame_otica>. Notifica confirmado (com profissional+data/hora) e cancelado;
  agendado/realizado/falta silenciosos (texto defensivo, sem promessa clínica).

  FLUXO B — ENCOMENDA DE ÓCULOS (clona o chassi order-based do COMIDA/FLORICULTURA/PADARIA): pedido
  com itens (cada item = uma armação do catálogo + as lentes via modifiers) + MODIFIERS (tipo de lente:
  monofocal/multifocal/antirreflexo/transitions como OPÇÕES com price_delta_cents) + total RECALCULADO
  no backend (descarta o total da IA) + SNAPSHOT de preço/nome/opção + gate de aceite humano (nasce
  'aguardando'; loja ACEITA→'em_montagem' ou RECUSA→'recusado' com rejection_reason) + PRAZO DE ENTREGA
  (lead_time_days da montagem em laboratório, ex.: 7 dias úteis — espelho do lead_time da padaria) +
  os DADOS DE RECEITA como CAMPOS ADMINISTRATIVOS do pedido (esférico/cilíndrico/eixo OD e OE + DP, +
  flag prescription_pending quando o cliente não traz os dados na hora). A IA registra a encomenda via
  tag <encomenda_otica>. A data de entrega prometida = hoje + lead_time (item override > config default);
  data < isso → 422 lead_time_violation (resposta traz a primeira data possível, defensivo).

NÃO TEM nesta SM (registrado pra não inventar): laudo/diagnóstico de exame (a IA NÃO conduz exame — só
agenda; o resultado do exame é registro do optometrista no painel, fase futura), interpretação/cálculo
de grau (PROIBIDO por trava de comportamento), prontuário oftalmológico estruturado (dado clínico
sensível — fase futura com cripto at-rest + log de acesso), foto da armação/referência visual
(bloqueador SERVICE_ROLE_KEY), orçamento de armação artesanal/sob medida ad-hoc com aprovação (lentes/
armações são por catálogo, não proposta livre — estilo oficina/eventos é fase futura), assinatura/plano
de troca de lente recorrente (academia cobre recorrência), convênio/plano de saúde ocular, integração
com laboratório externo por API (lead time é informativo, não orquestração real), pagamento real
(Stripe é #50), retirada × entrega com taxa (nesta SM o óculos pronto é RETIRADA na loja — sem fluxo de
entrega/taxa; se quiser, é fase futura), combo/cupom/fidelidade, controle de estoque de armações.

DECISÕES CRAVADAS (revisor decidiu pelo Igor):
1. A ótica faz AS DUAS COISAS (exame + encomenda) — é HÍBRIDO deliberado de DOIS chassis. NÃO escolher
   um só. Os dois fluxos coexistem HARMONICAMENTE no perfil 'otica' (regra do projeto: feature de um
   fluxo não pode quebrar o outro).
2. FLUXO A clona o DENTAL: profissional (optometrista) + conflito POR PROFISSIONAL + end_at materializado
   + status com parity. MANTER onde não conflita.
3. FLUXO B clona o COMIDA/FLORICULTURA/PADARIA: order + itens + modifiers + total recalculado + snapshot
   + gate de aceite + lead_time_days (estilo padaria). MANTER onde não conflita.
4. RECEITA = campos ADMINISTRATIVOS do pedido (esf/cil/eixo OD e OE + DP), nunca interpretados pela IA.
   prescription_pending = a IA não tinha os dados; a loja valida a receita no painel antes de montar.
5. Tipo de lente = MODIFIERS de catálogo (price_delta), espelho comida. A IA oferece, não inventa.
6. Gate de aceite humano na ENCOMENDA: nasce 'aguardando'; aceite/recusa no painel; a IA não aceita/
   recusa. 'aguardando' NÃO notifica (a IA já confirmou o recebimento).
7. Status hardcoded com parity para AMBOS: OticaExamStatus (exame) E OticaOrderStatus (encomenda),
   além de OticaCategory (catálogo: armacoes, lentes, acessorios). Cada um com parity test Java↔TS.
8. DUAS TAGS distintas (namespace próprio, distintas de TODAS as outras): <exame_otica> (agenda exame)
   e <encomenda_otica> (óculos sob receita). O backend valida e recalcula tudo; o OutboundService
   REMOVE a tag antes de enviar.
9. Óculos pronto é RETIRADA na loja (sem fluxo de entrega/taxa nesta SM — simplifica vs padaria).
   delivery_fee/min_order da config: manter min_order opcional (default 0); SEM taxa de entrega.

[FUNDAÇÃO — migration 51_otica (CONFIRMAR primeiro nº livre)]
- ALTER companies CHECK aceitar 'otica' (adicionar à lista existente — espelhar como 50_pizzaria fez).
- RLS enable+force, policies via app.company_id(), grants authenticated + service_role. Espelhar
  33_dental.sql (agenda) E 49_floricultura.sql (order/modifiers) lado a lado.
  * EXAME (otica_exam_appointments): INSERT pelo BACKEND via service_role (IA via ExameOticaConfirmHandler
    OU tenant via POST manual). Tenant só SELECT/UPDATE (status na agenda/Kanban) — SEM policy
    authenticated de insert (espelho dental_appointments).
  * ENCOMENDA (otica_orders/otica_order_items/otica_order_item_options): INSERT pelo BACKEND via
    service_role (criado pela IA via EncomendaOticaConfirmHandler). Tenant só SELECT/UPDATE (status no
    Kanban / gate de aceite). Espelho floricultura_orders.
- subtotal_cents/total_cents/unit_price_cents MATERIALIZADOS no INSERT; NÃO colunas geradas.
  end_at do exame MATERIALIZADO no INSERT (start_at + duration_minutes); NÃO coluna gerada (lição
  timestamptz+interval não é IMMUTABLE).
- SNAPSHOTS: preço+nome em otica_order_items; group/option/delta em otica_order_item_options; duração
  snapshot no exame. Alterar/excluir item/opção no catálogo NÃO altera pedidos passados.
- Tabelas:
  * otica_config — config 1:1 com company. min_order_cents (default 0, >= 0) + lead_time_days_default
    (default da montagem p/ encomenda; int >= 0, ex.: 7) + janela/duração do EXAME (duration_minutes
    default 30 check 15..240, buffer_minutes default 0, opens_at default '09:00', closes_at default
    '18:00'). Ausente → defaults. Clone de dental_clinic_config + floricultura_config (lead+mínimo),
    fundidos numa só config (a ótica é um perfil só).
  * otica_professionals — optometristas (catálogo, ~ salon_professionals/dental). (id, company_id,
    name CHECK 1..200, active default true, timestamps). Conflito de agenda é POR professional_id.
  * otica_exam_appointments — exames (clone dental_appointments com professional_id). (id, company_id,
    professional_id NOT NULL references otica_professionals on delete restrict, conversation_id nullable,
    contact_id nullable, start_at, duration_minutes (snapshot), end_at (materializado no INSERT),
    status CHECK ('agendado','confirmado','realizado','cancelado','falta') default 'agendado', notes
    ADMINISTRATIVO, timestamps + status_updated_at). Índice CRÍTICO de conflito: por
    (company_id, professional_id, start_at) WHERE status in ('agendado','confirmado').
  * otica_catalog_items — catálogo (armações, lentes-base, acessórios). (id, company_id, category CHECK
    in sync com enum ('armacoes','lentes','acessorios'), name CHECK 1..120, description, price_cents =
    preço base (>= 0), made_to_order boolean NOT NULL default false (armação/lente sob encomenda;
    acessório de pronta-entrega = false), lead_time_days int nullable (override do default da config
    quando made_to_order), active default true, timestamps). Comment cravando made_to_order + lead_time.
  * otica_catalog_item_options — modifiers (TIPO DE LENTE e tratamentos): grupos (group_label, ex.:
    "Tipo de lente","Tratamento"); cada linha = UMA opção de UM grupo (option_label "Multifocal",
    "Antirreflexo","Transitions"), price_delta_cents (>= 0). Espelho floricultura_catalog_item_options.
    on delete cascade.
  * otica_orders — encomendas de óculos. status CHECK ('aguardando','em_montagem','pronto','retirado',
    'recusado','cancelado') default 'aguardando'; subtotal_cents/total_cents materializados; min_order
    aplicado; rejection_reason nullable (gate de aceite); ready_date date NULLABLE (data prometida de
    entrega = hoje + lead_time, validada no backend); conversation_id/contact_id NOT NULL; notes;
    -- DADOS DE RECEITA (administrativos): OD (olho direito) + OE (olho esquerdo) + DP
    rx_od_spherical numeric(4,2) nullable, rx_od_cylindrical numeric(4,2) nullable, rx_od_axis int
    nullable check (0..180), rx_oe_spherical numeric(4,2) nullable, rx_oe_cylindrical numeric(4,2)
    nullable, rx_oe_axis int nullable check (0..180), rx_pd numeric(4,1) nullable (distância pupilar mm),
    prescription_pending boolean NOT NULL default false (true = cliente vai "trazer receita");
    timestamps + status_updated_at. Espelho floricultura_orders + os campos de receita. Comment cravando
    que os campos de grau são ADMINISTRATIVOS (a IA registra, não interpreta) e prescription_pending.
  * otica_order_items — itens do pedido com snapshot de nome+preço. unit_price_cents JÁ inclui Σ deltas
    (tipo de lente + tratamento). made_to_order_snapshot boolean. quantity. Espelho floricultura_order_items.
  * otica_order_item_options — opções escolhidas por item (snapshots group/option/delta). Espelho
    floricultura_order_item_options.
- Status do EXAME hardcoded (OticaExamStatus enum Java + const TS + parity test):
    agendado → confirmado, cancelado
    confirmado → realizado, cancelado, falta
    realizado/cancelado/falta → terminal
  Notifica: confirmado (com profissional+data/hora), cancelado (texto defensivo). agendado/realizado/
  falta silenciosos. Texto SEM promessa clínica.
- Status da ENCOMENDA hardcoded (OticaOrderStatus enum Java + const TS + parity test):
    aguardando → em_montagem, recusado, cancelado
    em_montagem → pronto, cancelado
    pronto → retirado, cancelado
    retirado/recusado/cancelado → terminal
  (aceite = aguardando→em_montagem é o gate humano.) Notifica: em_montagem (aceito — "seu óculos entrou
  em montagem"), pronto ("seu óculos está pronto pra retirada"), recusado (com motivo defensivo).
  aguardando/cancelado/retirado conforme padrão (aguardando NÃO notifica — a IA já confirmou).
- Categorias hardcoded (OticaCategory.java + otica-categories.ts + OticaCategoryParityTest):
  armacoes, lentes, acessorios.
- TODAS as tabelas novas entram na migration 51 ANTES de tocar o banco (lição os_config) e no
  TRUNCATE/SCRIPTS do AbstractIntegrationTest.

[BACKEND]
- Profissionais (optometristas): CRUD — espelho salon_professionals/dental. delete de profissional com
  exame → 409 professional_in_use; preferir arquivar (active=false).
- Catálogo: CRUD de itens (com made_to_order + lead_time_days) + opções (modifiers de tipo de lente/
  tratamento) — espelho floricultura/comida. Cache do bloco de catálogo injetado no prompt (Caffeine
  TTL 60s), INVALIDADO em toda gravação/edição/exclusão. delete de item com pedido → 409
  catalog_item_in_use.
- Config: GET (fallback min_order=0, lead default, janela/duração default) + PUT. Config FUNDIDA (exame
  + encomenda numa só).
- EXAME (agenda): criado pelo BACKEND via ExameOticaConfirmHandler OU POST manual do tenant. Conflito
  POR professional_id: findConflict transacional (janela half-open `NOT (end_at <= newStart OR start_at
  >= newEnd)`, só status bloqueantes 'agendado'/'confirmado', por company+professional), RE-VERIFICADO
  DENTRO da transação antes do INSERT (fecha a janela de corrida). Choque → 409 conflict_slot (com
  detalhes). end_at materializado no INSERT. Janela opens_at..closes_at validada no fuso
  America/Sao_Paulo (HARDCODED — pendência, igual dental). duration_minutes snapshot do config.
  Status: PATCH com validação de transição (inválida → 409 invalid_status_transition) + notificação
  outbound por status (texto defensivo). POST manual sem conversation_id → não notifica (sem canal).
- ENCOMENDA (order): criada pelo BACKEND via EncomendaOticaConfirmHandler. Recálculo: unit_price = base
  + Σ deltas (tipo de lente + tratamento) × quantity; subtotal = Σ itens; total = subtotal (SEM taxa de
  entrega nesta SM — retirada na loja). Validações cravadas:
    * se QUALQUER item made_to_order → ready_date OBRIGATÓRIA (e validada contra o lead time); itens só
      de acessório (pronta-entrega) → ready_date pode ser null.
    * data exigida = hoje + MAX(lead_time dos itens made_to_order) (item override > config default);
      ready_date < isso → 422 lead_time_violation (resposta traz a primeira data possível, defensivo).
    * RECEITA: os campos de grau (esf/cil/eixo OD/OE + DP) são gravados COMO VIEREM da tag (a IA passa
      o que o cliente forneceu); se a tag traz prescription_pending=true OU não traz nenhum campo de
      grau → prescription_pending=true (cliente vai trazer a receita). O backend NÃO valida/interpreta
      o grau (só persiste os números administrativamente; checks de range de eixo 0..180 no schema são
      sanidade, não conduta).
    * pedido mínimo: espelhar o que a floricultura/comida fazem (min_order_cents).
    * option_id inválido aborta (sem pedido parcial). Snapshots completos.
    * total da IA DESCARTADO (recalcula).
  Status: PATCH com validação de transição (inválida → 409 invalid_status_transition) + gate de aceite
  (aguardando→em_montagem = aceitar; aguardando→recusado = recusar com rejection_reason). Notificação
  outbound por status (texto defensivo).
- IA:
  * Persona prestativa-clara com a TRAVA DE COMPORTAMENTO embutida em ProfilePromptContext.OTICA (NUNCA
    prescreve grau, NUNCA diagnostica, NUNCA recomenda lente como conduta de saúde, NUNCA inventa
    item/preço, NUNCA aceita/recusa encomenda, total recalculado, respeita lead time, registra receita
    sem interpretar). Branch próprio no switch de ProfilePromptContext.
  * Contexto injetado (via cache Caffeine; segue o padrão dos perfis — recomendo TTL 30s pra parte da
    agenda que muda + 60s seria pro catálogo; cravar UM cache OticaContextCache com TTL 30s cobrindo os
    dois é aceitável): profissionais ativos + SLOTS LIVRES por profissional (próximos 14 dias) (FLUXO A)
    + bloco de catálogo (armações/lentes/acessórios marcando o que é SOB ENCOMENDA com o lead time + as
    opções de tipo de lente/tratamento com deltas) + min_order + lead default + as DUAS tags com seus
    formatos. Invalidação explícita em toda mutação (profissional/exame/catálogo/config).
  * Tag <exame_otica>{"professional_id","date":"YYYY-MM-DD","start_time":"HH:MM","notes":"...|null"} →
    ExameOticaConfirmHandler (espelho ConsultaConfirmHandler do dental: resolve o contato da conversa,
    cria o exame, conflito por profissional). Best-effort.
  * Tag <encomenda_otica>{"items":[{"catalog_item_id","options":[{"option_id"}],"quantity"}],
    "ready_date":"YYYY-MM-DD|null","rx":{"od":{"spherical","cylindrical","axis"},"oe":{"spherical",
    "cylindrical","axis"},"pd"}|null,"prescription_pending":true|false,"notes"} →
    EncomendaOticaConfirmHandler (espelho PedidoFlorConfirmHandler + made_to_order/lead + os campos de
    receita). Best-effort; o backend valida + recalcula.
  * JwtFilter autentica /api/otica/ (adicionar OTICA_PATH_PREFIX = "/api/otica/" + o `!uri.startsWith`
    na cadeia, espelho dos 17 perfis). OutboundService ganha DOIS handlers encadeados na cadeia de
    maybeProcess (após os outros perfis — perfil é único, só um age): maybeProcessExameOtica +
    maybeProcessEncomendaOtica. Best-effort; remove a tag antes de enviar.

[FRONTEND]
- Telas (sidebar grupo "Ótica"):
  * /dashboard/otica-professionals — CRUD optometristas (espelho salon-professionals).
  * /dashboard/otica-exams — agenda de exames (Kanban/lista por status; PATCH de status; mostra
    profissional + data/hora + cliente). Espelho da agenda dental.
  * /dashboard/otica-catalog — CRUD itens (com toggle made_to_order + campo lead_time_days quando
    ligado) + editor de opções/modifiers (tipo de lente/tratamento) inline. Espelho floricultura-menu.
  * /dashboard/otica-orders — Kanban por status com o GATE DE ACEITE: Aceitar/Recusar na coluna
    'aguardando' (recusa pede motivo); o detalhe mostra os itens (armação + tipo de lente escolhido),
    a ready_date (prazo), e os DADOS DE RECEITA (OD/OE esf/cil/eixo + DP) + flag prescription_pending
    ("trazer receita"). Espelho floricultura-orders + bloco de receita.
  * /dashboard/otica-settings — min_order + lead time default + janela/duração do exame.
- types + SDKs (professionals, exams, catalog+options, orders) espelhando dental + floricultura.
- Status TS otica-exam-status.ts + otica-order-status.ts + OticaCategory const + parity tests
  (exam status + order status + categorias).
- getNavForProfile('otica') injeta "Ótica" (5 itens: Optometristas, Exames, Catálogo, Pedidos,
  Configurações), no mesmo padrão dos branches existentes (dental/floricultura já têm branch — seguir
  o modelo deles). Subdomínio otica.meadadigital.local. Paleta: agente escolhe — sugestão 'indigo',
  'celeste' ou 'aco' (tons de azul/cinza casam com ótica; 'celeste' já é do dental, então preferir
  'indigo' ou 'aco' se quiser distinção, mas a decisão é do agente).
- npm build limpo.

[DOCS]
- CLAUDE.md: seção "## Perfil Ótica (camada 8.x)" espelhando as seções de perfil + nota de que é o
  PRIMEIRO HÍBRIDO (agenda-clínica-leve do DENTAL + order-com-receita do COMIDA/FLORICULTURA/PADARIA).
  Documentar EXPLÍCITO: os dois fluxos coexistem; conflito por profissional (exame); made_to_order +
  lead_time (422 lead_time_violation) na encomenda; receita como campos administrativos
  (prescription_pending); trava de comportamento (IA não prescreve grau/não diagnostica); as DUAS tags
  <exame_otica> e <encomenda_otica>; categorias próprias.
- docs/PERFIL_OTICA.md: guia operacional do tenant (optometristas; agenda de exames; catálogo de
  armações/lentes/acessórios com encomenda+lead; pedidos + Kanban + gate de aceite; campos de receita;
  como a IA atende os dois fluxos; "o que a IA NÃO faz" — não prescreve grau, não diagnostica, não
  recomenda lente como conduta). Espelhar PERFIL_DENTAL.md + PERFIL_FLORICULTURA.md.
- NÃO mexer em system-template.txt nem em outros perfis.

[TESTES BACKEND]
Espelhar a suíte do dental (agenda) + floricultura/comida (order):
- OticaExamStatusParityTest + OticaOrderStatusParityTest + OticaCategoryParityTest +
  ProfileTypeParityTest.
- OticaProfessionalServiceTest + ControllerIntegrationTest (CRUD; delete-em-uso 409; wrongProfile 403).
- OticaCatalogServiceTest + ControllerIntegrationTest (CRUD item+opções+made_to_order/lead; invalida
  cache; delete-em-uso 409; wrongProfile 403).
- OticaConfigServiceTest/ControllerIntegrationTest (GET fallback + PUT; lead default + janela/duração).
- OticaExamServiceTest [CHAVE do FLUXO A]:
    * agendar exame OK (end_at materializado = start + duration).
    * conflito POR PROFISSIONAL → 409 conflict_slot; MESMO horário com profissional DIFERENTE → OK
      (paralelismo).
    * fora da janela opens..closes → rejeitado.
    * transição de status (agendado→confirmado→realizado; cancelado/falta); inválida → 409.
- OticaOrderServiceTest [CHAVE do FLUXO B]:
    * acessório pronta-entrega sem ready_date → OK (data null).
    * item made_to_order sem ready_date → 422 (data obrigatória).
    * encomenda com ready_date < hoje+lead → 422 lead_time_violation (resposta traz 1ª data possível).
    * encomenda com data válida → OK; data exigida = MAX dos leads quando há vários itens.
    * modifiers: unit_price = base + Σ deltas (tipo de lente + tratamento); snapshots preservados.
    * RECEITA: campos OD/OE esf/cil/eixo + DP gravados como vieram; prescription_pending=true quando a
      tag não traz grau OU pede explicitamente; a IA/backend NÃO interpreta o grau (só persiste).
    * total da IA DESCARTADO (recalcula); option_id inválido → aborta; pedido mínimo respeitado.
- ExameOticaConfirmHandlerTest: tag de exame válida → cria; conflito → empty; sem tag → empty.
- EncomendaOticaConfirmHandlerTest: tag com receita+lead → cria; tag prescription_pending (sem grau) →
  cria com flag; data inválida/option inválido → empty; sem tag → empty; total bate.
- Status/gate da encomenda: aguardando→em_montagem (aceite) → pronto → retirado; recusado(motivo);
  transição inválida → 409; a IA não tem endpoint de aceite.
- TRAVA (cravado nos testes da persona/handler quando aplicável): a IA não prescreve grau — o
  ExameOticaConfirmHandler/EncomendaOticaConfirmHandler nunca derivam/calculam grau; só registram o
  que a tag traz. (Teste de regressão: tag de encomenda sem campos de grau → prescription_pending=true,
  todos os rx_* null; tag com grau → persiste verbatim, sem cálculo.)
mvn final = relatar contagem REAL do Surefire.

[CONSTRAINTS DUROS]
- Migration única (51 — CONFIRMAR primeiro nº livre). Sem foto/anexo (catálogo é texto + opção).
- Cliente NÃO é entidade do core — continua o contact (exame e pedido têm conversation_id/contact_id).
- HÍBRIDO: FLUXO A (exame) clona o DENTAL — conflito por profissional + end_at materializado + status
  com parity. FLUXO B (encomenda) clona o COMIDA/FLORICULTURA/PADARIA — order + modifiers + total
  recalculado + gate de aceite + lead_time + receita administrativa.
- RECEITA = campos administrativos (esf/cil/eixo OD/OE + DP); prescription_pending quando o cliente vai
  trazer. A IA NUNCA interpreta/calcula/prescreve grau, NUNCA diagnostica.
- lead_time (item override > config default); ready_date CONDICIONAL (só se há item made_to_order); 422
  lead_time_violation com a primeira data possível.
- conflito por professional_id re-verificado na transação; 409 conflict_slot; end_at materializado.
- subtotal/total/unit_price materializados (não generated). Snapshots de item/opção.
- Gate de aceite humano na encomenda: nasce 'aguardando'; aceite/recusa no painel; a IA NUNCA aceita/
  recusa.
- Status hardcoded com parity (exame E pedido) + categorias hardcoded (parity). DUAS tags <exame_otica>
  e <encomenda_otica> distintas de TODAS as outras.
- NÃO mexer em outros perfis nem em system-template.txt. Webhook OFF.
- Cache de contexto TTL (catálogo+agenda) + invalidação em toda mutação relevante.
- 529 → inline. Gate 3× → pausar. Working tree sujo → pausar. git add EXPLÍCITO (nunca git add .);
  .env/CONTEXT.md/secrets NUNCA staged.
- SEED com timestamptz/date: `at time zone 'America/Sao_Paulo'`. IDs de namespace com sufixo NOVO.
- Tabela nova entra na migration ANTES de tocar o banco (lição os_config). Adicionar as tabelas ao
  TRUNCATE/SCRIPTS do AbstractIntegrationTest.
- Decisões menores: agente decide (paleta, layout, se OticaContextCache é 1 cache ou 2).

[PASSO FINAL — TENANT + SEED + COMMIT + PUSH + SMOKE + RELATÓRIO]
F.1 — TENANT igorhaf18 (Ótica Modelo, profile=otica) — CONFIRMAR primeiro nº de tenant LIVRE no
      arranque (17 disputado por padaria/pizzaria drafts). Padrão GoTrue, senha em comunicação direta.
      company c?0000000-...-018 / user a?0000000-...-018. Caddy + /etc/hosts pra otica.meadadigital.local.
F.2 — Seed /tmp/seed-otica.sql (NÃO COMITAR; `at time zone 'America/Sao_Paulo'`; ids sufixo novo):
  - config: min_order R$0, lead_time_days_default 7, duração exame 30min, janela 09:00–18:00.
  - profissionais: "Dra. Helena Souza" (optometrista) + "Dr. Marcos Lima".
  - catálogo:
    * ARMAÇÕES (made_to_order=true, lead default): "Armação Clássica" (armacoes, R$200), "Armação
      Premium" (armacoes, R$450).
    * LENTES com opções (made_to_order=true): "Par de Lentes" (lentes, base R$150) com grupos:
      "Tipo de lente" (Monofocal +0, Multifocal +R$400, Transitions +R$300), "Tratamento" (Antirreflexo
      +R$120, Blue +R$90).
    * ACESSÓRIOS pronta-entrega (made_to_order=false): "Estojo" (acessorios, R$30), "Cordinha"
      (acessorios, R$15).
  - contact "Bruno Lima" +5511977778888 (VINCULADO: instance+conversation) + contact "Carla Mendes"
    +5511966667777 (sem vínculo).
  - exames (FLUXO A):
    * 'agendado' VINCULADO (Bruno) com Dra. Helena, hoje+2d 10:00 — pra smoke de agenda + confirmar.
    * 'confirmado' (Carla) com Dr. Marcos, hoje+3d 14:00 — histórico.
  - pedidos/encomendas (FLUXO B), cobrindo estados/escapadas:
    * 'aguardando' VINCULADO (Bruno) ENCOMENDA: 1 Armação Premium + 1 Par de Lentes (Multifocal +
      Antirreflexo), ready_date hoje+10d, RECEITA preenchida (OD esf -1.50 cil -0.75 eixo 90 / OE esf
      -1.25 cil -0.50 eixo 85 / DP 62) → total = 450 + (150+400+120) = R$1120; pra smoke de modifiers +
      lead + aceite + receita.
    * 'em_montagem' (Carla) ENCOMENDA: 1 Armação Clássica + 1 Par de Lentes (Monofocal), ready_date
      hoje+7d, prescription_pending=true ("trazer receita", rx_* null); pra smoke do funil
      (→pronto→retirado) + prescription_pending.
    * 'pronto' (Bruno) ENCOMENDA com acessório: 1 Estojo (pronta-entrega) + 1 Par de Lentes (Monofocal),
      ready_date hoje+7d; pra smoke de notificação 'pronto'.
    * 'retirado' (Bruno, passado) histórico.
F.3 — JwtFilter /api/otica/ (se ainda não).
F.4-F.6 — git add EXPLÍCITO dos arquivos da SM + sanity (sem .env/secrets/CONTEXT) + commit.
      Mensagem padrão (feat(camada-8): perfil otica/Ótica (exame de vista + óculos sob receita) com
      FUNDAÇÃO/BACKEND/FRONTEND/DECISÕES/VALIDAÇÃO contagem REAL/NÃO TOCADO/FECHAMENTO + Co-Authored-By:
      Claude Opus 4.8). Tag fase-8.x-fechada (nº real confirmado no arranque).
F.7 — git push origin main + tags.
F.8 — docker compose restart backend + aguardar /admin/me → 401.
F.9 — Smoke E2E (blocos A–H cobrindo OS DOIS fluxos):
  BLOCO A: auth — igorhaf18 → /admin/me → role=tenant_admin, profileId=otica, productName=Ótica.
  BLOCO B: catálogo + profissionais + guard — GET catalog (itens com made_to_order/lead + opções) +
    GET professionals; CRUD smoke + invalida cache; delete em uso 409; GET config + PUT; tenant de
    OUTRO perfil → /api/otica/catalog → 403 forbidden_wrong_profile.
  BLOCO C: EXAME [FLUXO A — CHAVE] — <exame_otica> com Dra. Helena, hoje+2d 11:00 → cria exame
    (end_at = start+duration); MESMO horário com Dra. Helena → 409 conflict_slot; MESMO horário com Dr.
    Marcos → OK (paralelismo); horário fora da janela → rejeitado.
  BLOCO D: ENCOMENDA + LEAD + MODIFIERS + RECEITA [FLUXO B — CHAVE] —
    - <encomenda_otica> Armação Premium + Par de Lentes (Multifocal+Antirreflexo), receita preenchida,
      ready_date hoje+10d → unit_price das lentes = 150+400+120 = 670; total = 450+670 = R$1120; total
      da IA descartado; rx_* persiste verbatim (sem cálculo).
    - mesma encomenda com ready_date hoje+1d (lead=7) → 422 lead_time_violation (resposta traz hoje+7d).
    - encomenda made_to_order SEM ready_date → 422; pedido só de acessório SEM ready_date → OK.
    - <encomenda_otica> sem campos de grau / prescription_pending=true → cria com prescription_pending,
      todos rx_* null (a IA não prescreveu grau — prova da TRAVA).
    - option_id inválido → não cria (empty).
  BLOCO E: gate de aceite + funil da encomenda — aguardando→em_montagem (aceite, Bruno vinculado) →
    200 + msg "entrou em montagem"; em_montagem→pronto → msg "pronto pra retirada"; pronto→retirado OK;
    aguardando→recusado(motivo) → msg defensiva; transição inválida → 409; a IA não tem rota de aceite.
  BLOCO F: status do EXAME — agendado→confirmado (msg com profissional+data/hora) → realizado;
    confirmado→cancelado (msg defensiva); confirmado→falta; transição inválida → 409.
  BLOCO G: regressão — perfis anteriores intactos (smoke leve 1 endpoint cada); otica → /api/dental/* →
    403; otica → /api/floricultura/* → 403.
  BLOCO H: paridade — mvn test -Dtest=OticaExamStatusParityTest,OticaOrderStatusParityTest,
    OticaCategoryParityTest,ProfileTypeParityTest → verde.
  Cleanup smoke + restaurar seed pristine. mvn final: contagem REAL.
F.10 — RELATÓRIO + DESTAQUE EXPLÍCITO:
  - "perfil otica/Ótica — PRIMEIRO HÍBRIDO: agenda-clínica-leve (clona DENTAL) + order-com-receita
     (clona COMIDA/FLORICULTURA/PADARIA), os dois fluxos coexistindo no mesmo perfil"
  - "FLUXO A (exame): conflito POR PROFISSIONAL re-verificado na transação; end_at materializado; 409
     conflict_slot; paralelismo entre optometristas"
  - "FLUXO B (encomenda): modifiers de tipo de lente + total recalculado + gate de aceite + lead_time
     (422 lead_time_violation com a primeira data possível)"
  - "RECEITA como campos administrativos (OD/OE esf/cil/eixo + DP) + prescription_pending; a IA NUNCA
     interpreta/calcula/prescreve grau (TRAVA provada no BLOCO D)"
  - "BLOCO C prova o conflito por profissional + paralelismo; BLOCO D prova lead time, modifiers,
     receita e a trava; BLOCO E prova o gate de aceite humano + funil da encomenda"
  - "categorias próprias (armacoes/lentes/acessorios); DUAS tags <exame_otica> e <encomenda_otica>"
  - "Seed: at time zone + sufixo de ids novo; tabelas DENTRO da migration (lição os_config)"
  - PENDÊNCIAS: laudo/resultado de exame do optometrista no painel, prontuário oftalmológico (dado
     sensível com cripto), foto da armação, orçamento de armação sob medida ad-hoc, entrega com taxa,
     convênio, laboratório por API, Stripe + dívida acumulada (webhook, cliente real, olho humano sobre
     os verticais).

[REPORTAR]
Igual SMs anteriores. Incluir EXPLICITAMENTE:
- "ProfileType.OTICA adicionado (camada 8.x)"
- "Paridade OticaExamStatus, OticaOrderStatus, OticaCategory e ProfileType validadas"
- "Tenant igorhaf18 criado (GoTrue + Caddy/etc/hosts) — nº confirmado no arranque"
- "PRIMEIRO HÍBRIDO: FLUXO A exame (clona DENTAL, conflito por profissional + end_at materializado) +
   FLUXO B encomenda (clona COMIDA/FLORICULTURA/PADARIA, order + modifiers + lead time + gate de aceite)"
- "RECEITA: campos administrativos OD/OE esf/cil/eixo + DP + prescription_pending; IA não prescreve grau"
- "lead_time da montagem (item override > config default); 422 lead_time_violation; ready_date condicional"
- "Gate de aceite humano na encomenda: IA confirma recebimento, loja aceita/recusa"
- "DUAS tags <exame_otica> e <encomenda_otica> distintas de TODAS as outras"
- "OutboundService ganhou maybeProcessExameOtica + maybeProcessEncomendaOtica (DOIS handlers)"
- "getNavForProfile('otica') com branch próprio (Optometristas/Exames/Catálogo/Pedidos/Configurações)"
- "Cache de contexto + invalidação em toda mutação"
- "tabelas criadas DENTRO da migration (lição os_config); seed com at time zone + sufixo novo"
- "Próximas fases: laudo de exame, prontuário oftalmológico, foto de armação, armação sob medida,
   entrega com taxa, convênio, Stripe + fila de prioridade"
