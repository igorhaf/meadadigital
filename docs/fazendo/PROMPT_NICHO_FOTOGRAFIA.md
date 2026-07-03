>>> SLOT ATRIBUÍDO (ver docs/prompts-nicho/README.md — fonte única de verdade, tem precedência
>>> sobre qualquer "provisório" no corpo): ordem 11 · camada 8.16 · migration 60_fotografia.sql ·
>>> tenant igorhaf27 (company/user sufixo -027) · ids de seed sufixo -14x. Reconfirmar no arranque
>>> que a fila não avançou; se avançou, deslocar conforme o README (próximo nº de migration livre,
>>> próximo igorhafN livre, próximo sufixo de seed livre) e RELATAR o deslocamento.

[TAREFA — SUB-MARATONA: PERFIL FOTOGRAFIA / Fotografia (Fotografia · Cinema · Audiovisual) (camada 8.16)]

[CONTEXTO]
PROJETO MEADA em /home/igorhaf/meada.
17+ perfis verticais reais hoje (… comida 8.4, floricultura 8.5, pizzaria 8.6, e a fila adega/
casamento/padaria/lavanderia/dermatologia/ótica/projetos em execução) + generic. Lê CLAUDE.md,
CONTEXT.md e o filesystem no arranque pra cravar convenções, nº de migration, contagem do Surefire
e numeração de tenant ANTES de escrever qualquer código. NÃO hardcodar a contagem do mvn — relatar
a REAL do Surefire ao final.

VALORES DO SLOT (CONFIRMAR no filesystem antes — a tabela do README tem precedência):
- migration: `60_fotografia.sql` (CONFIRMAR `ls supabase/migrations/` no arranque e usar o PRÓXIMO
  número livre real; hoje o disco tem 50_pizzaria e 53_adega — se a fila avançou, subir o nº).
- tenant: igorhaf27 (company c…-027 / user a…-027 seguindo a numeração do tenant). Se já usado por
  outra SM, usar o próximo livre e RELATAR.
- IDs de namespace compartilhado (contacts/instance/conversation) NO SEED com sufixo `-14x` (conferir
  os usados pra evitar colisão FK; se -14x já apareceu em seed anterior, pular pro próximo livre).

Fotografia é template de nicho pra ESTÚDIO / FOTÓGRAFO / produtora de AUDIOVISUAL dentro do mesmo
dashboard Meada. O tenant acessa fotografia.meadadigital.local e vê o produto "Fotografia". A IA
atende CLIENTES via WhatsApp, identifica pelo TELEFONE, oferece os PACOTES do catálogo, AGENDA a
sessão/cobertura (ensaio, casamento, evento, vídeo) com um FOTÓGRAFO em data/horário, e — depois que
a sessão é realizada e o estúdio registra a entrega — pode ENTREGAR ao cliente o LINK da galeria/
material gravado pelo estúdio (read-only). Tom criativo-acolhedor e organizado, de quem cuida do
registro de um momento importante, SEM prometer resultado artístico.

>>> ========================================================================================
>>> TRAVA DE COMPORTAMENTO (o coração desta SM — espelho leve da trava clínica do dental/nutri)
>>> ========================================================================================
A IA fotográfica, em TODA a conversa, sem exceção:
- NUNCA promete RESULTADO ARTÍSTICO específico ("vai ficar perfeito", "as fotos vão ficar incríveis",
  "garanto o álbum mais bonito"), NUNCA promete EDIÇÃO/RETOQUE/ESTILO específico além do que o pacote
  descreve, NUNCA garante quantidade de fotos/clima/luz/locação não escrita no pacote.
- NUNCA INVENTA pacote, preço, duração ou prazo de entrega fora do CATÁLOGO. O preço/duração/
  delivery_days vêm SEMPRE do package cadastrado; a tag NÃO carrega preço. Se o cliente pede algo que
  não existe no catálogo → a IA encaminha ("vou verificar com o estúdio") e NÃO cria pacote ad-hoc.
- NUNCA aceita/recusa/negocia fora do fluxo: a IA SÓ AGENDA a sessão e ENTREGA o link que o estúdio
  já gravou. NUNCA muda status da sessão pela conversa (confirmar/realizar/cancelar é AÇÃO HUMANA no
  painel — espelho do "cancelamento bloqueado por IA" do dental), NUNCA define/edita o delivery_link
  (o link é gravado pelo estúdio no painel; a IA só o ENTREGA verbatim).
- Para qualquer pedido de orçamento personalizado, exclusividade de data não confirmada, urgência de
  edição → acolhe e encaminha ao estúdio; NÃO promete.
- A trava vive na PERSONA (ProfilePromptContext.FOTOGRAFIA) E no schema (a IA não tem caminho de
  escrita do delivery_link nem de mudança de status; o ÚNICO conteúdo que a IA "entrega" é o
  delivery_link READ-ONLY gravado pelo estúdio — texto/URL VERBATIM, sem interpretar).

EVOLUÇÃO ESTRUTURAL (o que diferencia das outras agendas-por-profissional):
COMBINA DOIS CHASSIS + UMA ESCAPADA:
- (1) AGENDA da sessão com profissional (fotógrafo) + data/horário + conflito POR profissional
  (espelho DENTAL/SALON/NUTRI: half-open re-verificado na transação; end_at MATERIALIZADO no INSERT —
  timestamptz+interval não é IMMUTABLE, lição SM-D; 2 sessões no mesmo horário com fotógrafos
  DIFERENTES NÃO conflitam — paralelismo).
- (2) PACOTE escolhido na hora do agendamento (espelho LEVE do pacote da ESTÉTICA — mas SEM saldo
  multi-sessão consumível: aqui o pacote é só um CATÁLOGO de pacotes pré-definidos com preço +
  duração + prazo de entrega, ex.: "Ensaio 1h / 30 fotos", "Casamento 8h / álbum", "Vídeo
  institucional"). A sessão referencia o package_id e SNAPSHOTA package_name + price_cents +
  duration_minutes + delivery_days no momento — alterar/arquivar o pacote depois NÃO altera sessões
  já criadas. A duração da sessão VEM DO PACOTE (snapshot), NÃO de um config fixo.
- (3) ESCAPADA — ENTREGA DO MATERIAL com PRAZO + LINK (read-only): depois da sessão REALIZADA, o
  estúdio grava na própria sessão o delivery_link (URL da galeria/material) — a data prometida de
  entrega (delivery_due_date) é DERIVADA = data da sessão + delivery_days do pacote (snapshot),
  materializada no INSERT junto com a sessão. A IA pode ENTREGAR o link ao cliente READ-ONLY, VERBATIM,
  com BARREIRA DE CONTATO — espelho EXATO do EntregaPlanoHandler do nutri, MAS com uma diferença
  estrutural cravada: no nutri o artefato ("plano ativo") é uma entidade SEPARADA do appointment; aqui
  o LINK mora NA PRÓPRIA SESSÃO (coluna delivery_link na session_appointment). A tag de entrega
  referencia o session_id; o backend resolve a sessão, checa a barreira de contato e o link
  preenchido, e envia o link EXATO. delivery_link só é entregável quando preenchido pelo estúdio (sem
  link → não entrega). O status cobre o ciclo completo:
  agendada → confirmada → realizada → entregue (+ cancelada / falta).

NÃO TEM nesta SM (registrado pra não inventar):
- SEM UPLOAD de arquivo/foto/vídeo — é LINK COLADO (URL externa: Google Drive, WeTransfer, galeria
  própria). O bloqueador é o SERVICE_ROLE_KEY ausente (mesma trava de foto dos outros perfis); a
  entrega de material é POR LINK, não por anexo.
- SEM pacote multi-sessão com saldo consumível (a ESTÉTICA cobre isso — aqui o pacote é avulso por
  sessão; cada sessão escolhe UM pacote e snapshota).
- SEM contrato/assinatura digital/PDF/e-sign, SEM pagamento/sinal/parcelas (Stripe é #50), SEM
  segundo profissional/assistente na mesma sessão (multi-pool), SEM lista de fotos/shot list/timeline
  do dia como sub-entidade (o EVENTOS cobre cronograma — aqui não), SEM múltiplas locações como
  entidades, SEM galeria de seleção de fotos pelo cliente, SEM scheduler de auto-transição (sessão
  passada não vira "realizada" sozinha) nem lembrete "sua sessão é amanhã", SEM cancelamento/mudança
  de status pela IA (só painel — espelho dental). Fases futuras.

DECISÕES CRAVADAS (revisor decidiu pelo Igor):
1. Chassi de AGENDA = dental/salon/nutri: fotógrafo (profissional) + conflito POR professional_id
   (half-open, re-verificado na transação) + end_at materializado no INSERT. MANTER. NÃO inventar
   conflito por company (há múltiplos fotógrafos).
2. PACOTE = catálogo pré-definido (preço + duração + delivery_days), espelho LEVE do
   aesthetic_procedures (preço + duração), SEM o aesthetic_packages (saldo). A sessão referencia o
   pacote e SNAPSHOTA package_name/price_cents/duration_minutes/delivery_days. A duração da sessão
   vem do pacote (snapshot), NÃO de config fixo.
3. Cliente NÃO é entidade do core — continua o contact; snapshots customer_name/customer_phone na
   sessão (espelho salon/estetica: guest_name/guest_phone — alta rotatividade, sem entidade de
   cliente). A barreira de contato da entrega usa o contact_id da sessão.
4. ESCAPADA = delivery_link + delivery_due_date NA PRÓPRIA SESSÃO. delivery_due_date materializado no
   INSERT (data da sessão + delivery_days, em DATE). delivery_link nullable, gravado pelo estúdio no
   painel DEPOIS da sessão realizada. Entrega READ-ONLY pela IA (verbatim, barreira de contato) —
   espelho EntregaPlanoHandler, mas o artefato mora na sessão (não em tabela separada).
5. Status da sessão hardcoded: agendada → confirmada/cancelada ; confirmada → realizada/cancelada/
   falta ; realizada → entregue/cancelada ; entregue/cancelada/falta terminais. Parity Java↔TS.
   GÊNERO FEMININO ("sessão" → agendada/confirmada/realizada/entregue/cancelada/falta) — cravar e ser
   consistente em enum, const TS, CHECK da migration e textos de notificação.
6. A IA SÓ AGENDA e ENTREGA o link. NUNCA confirma/realiza/cancela, NUNCA muda status, NUNCA grava/
   edita/inventa o link, NUNCA promete resultado artístico ou pacote/preço fora do catálogo.
7. Tags: <sessao_foto> (AGENDA — 2 modos NÃO se aplicam: cliente não é sub-entidade; tem UM modo só,
   resolve o contato da conversa como customer, espelho do <agendamento> do salon) e
   <entrega_material>{session_id} (ENTREGA read-only o delivery_link da sessão — espelho
   <entrega_plano>, mas keyed por session_id). Namespaces distintos de TODAS as outras tags.
   (Se o agente julgar útil distinguir cliente recorrente, pode opcionalmente aceitar um campo livre;
   mas o padrão cravado é UM modo, sem new_client — decisão menor do agente, documentar a escolha.)

[FUNDAÇÃO — migration 60_fotografia.sql  (NN = próximo slot livre; provisoriamente 60 — CONFIRMAR)]
- ALTER companies CHECK aceitar 'fotografia' (drop+add da companies_profile_id_check com TODOS os ids
  atuais + 'fotografia'; CONFERIR a lista REAL no disco antes — ela cresceu desde a 39; a 46_estetica
  já listava generic..estetica, e desde então entraram comida/floricultura/pizzaria/adega/etc. NÃO
  clonar por sed cego — CONFERIR que o CHECK final tem TODOS os perfis existentes + fotografia, nenhum
  some; lição cravada no CLAUDE.md sobre clonagem por sed).
- RLS enable+force, policies via app.company_id(), grants authenticated + service_role. INSERT de
  SESSÕES pelo BACKEND (service_role — a IA via ConfirmHandler OU o tenant via POST manual); tenant
  SELECT/UPDATE da sessão (mudar status na agenda + gravar delivery_link). Profissionais/pacotes/
  config: CRUD do tenant (authenticated). Espelhar 33_dental.sql + 34_salon.sql + 46_estetica.sql.
- end_at MATERIALIZADO no INSERT (start_at + duration_minutes). NÃO coluna gerada (timestamptz +
  interval não é IMMUTABLE — lição da SM-D). duration_minutes na sessão é SNAPSHOT do package no
  momento. delivery_due_date MATERIALIZADO no INSERT (date da sessão + delivery_days do pacote).
- Tabelas (4):
  * fotografia_professionals — fotógrafos/operadores (catálogo; conflito de agenda POR profissional;
    espelho salon_professionals/aesthetic_professionals). (id, company_id refs companies on delete
    restrict, name CHECK 1..200, specialty text livre (ex.: "fotografia social", "vídeo", "ensaio",
    "fotojornalismo"), active default true, notes, timestamps). Índices: (company_id, active) where
    active, (company_id, name).
  * fotografia_packages — A entidade de catálogo (espelho LEVE aesthetic_procedures: preço +
    duração, + prazo de entrega). (id, company_id refs companies on delete restrict, name CHECK
    1..200 (ex.: "Ensaio 1h / 30 fotos", "Casamento 8h / álbum", "Vídeo institucional"), category
    text livre nullable (ex.: "ensaio", "evento", "vídeo"), duration_minutes int NOT NULL CHECK
    between 15 and 1440 (cobertura de casamento é longa), price_cents int NOT NULL CHECK >= 0,
    delivery_days int NOT NULL default 0 CHECK >= 0 (prazo de entrega em dias após a sessão), active
    default true, notes, timestamps). Índices: (company_id, active) where active, (company_id, name).
    COMMENT cravando: duração + preço + delivery_days por pacote; a sessão SNAPSHOTA todos no INSERT.
  * fotografia_config — horário de funcionamento + slot (1:1 com company; espelho aesthetic_config/
    dental_clinic_config). (company_id PK refs companies on delete cascade, opens_at default '08:00',
    closes_at default '20:00', slot_minutes int default 30 CHECK between 5 and 240, timestamps).
    Ausente → defaults. SEM duration_minutes aqui — a duração vem do package (snapshot).
  * fotografia_session_appointments — sessões/coberturas (snapshots; conflito POR profissional;
    delivery_link + delivery_due_date). (id, company_id refs companies on delete restrict,
    professional_id NOT NULL refs fotografia_professionals on delete restrict, package_id NOT NULL
    refs fotografia_packages on delete restrict, conversation_id nullable refs conversations on
    delete set null, contact_id refs contacts on delete set null (atalho/snapshot p/ barreira da
    entrega), customer_name text NOT NULL (snapshot do contato), customer_phone text (snapshot
    opcional), professional_name text NOT NULL (snapshot), package_name text NOT NULL (snapshot),
    price_cents int NOT NULL (snapshot do pacote), duration_minutes int NOT NULL (snapshot do
    pacote), delivery_days int NOT NULL (snapshot do pacote), start_at timestamptz NOT NULL, end_at
    timestamptz NOT NULL (MATERIALIZADO = start_at + duration_minutes), delivery_due_date date NOT
    NULL (MATERIALIZADO = date(start_at @ America/Sao_Paulo) + delivery_days), delivery_link text
    NULLABLE (URL da galeria/material; gravada pelo estúdio DEPOIS da sessão; vazio = nada a
    entregar), status text NOT NULL default 'agendada' CHECK in ('agendada','confirmada','realizada',
    'entregue','cancelada','falta'), notes text (ADMINISTRATIVO), created_at, status_updated_at).
    Índices: (company_id, status, start_at); CRÍTICO do conflito — (professional_id, start_at) where
    status in ('agendada','confirmada','realizada') [ver nota de status bloqueante abaixo];
    (contact_id, start_at desc) where contact_id is not null. RLS: SELECT/UPDATE pro tenant; INSERT só
    backend (sem policy authenticated de insert — igual nutri/dental/estetica).
    >>> NOTA sobre status bloqueante do conflito: o conflito half-open só conta status que ocupam a
    agenda do fotógrafo. Cravar: agendada/confirmada bloqueiam (espelho dos outros). 'realizada' já
    aconteceu (a sessão passada não disputa slot futuro) — o agente decide se inclui no índice/WHERE,
    mas o findConflict de NOVAS sessões deve filtrar apenas status FUTUROS-bloqueantes
    (agendada/confirmada). Documentar a escolha; o padrão dos perfis anteriores é
    in ('agendado','confirmado').
- Status da SESSÃO hardcoded (FotografiaSessionStatus enum Java + const TS + parity test):
  agendada → confirmada, cancelada ; confirmada → realizada, cancelada, falta ; realizada → entregue,
  cancelada ; entregue/cancelada/falta → terminal. Notificações (texto DEFENSIVO, SEM promessa
  artística): CONFIRMADA (com pacote + fotógrafo + data/hora), ENTREGUE (avisa que o material está
  disponível — opcional incluir o link aqui OU deixar a entrega só via <entrega_material>; decisão do
  agente, documentar) e CANCELADA avisam o cliente; agendada/realizada/falta silenciosos. Gênero
  feminino consistente.
- TODAS as 4 tabelas novas entram na migration ANTES de tocar o banco (banco se aplica A PARTIR do
  arquivo versionado — lição os_config da SM-J) e na lista de TRUNCATE/SCRIPTS do
  AbstractIntegrationTest.

[BACKEND]
- Estrutura em src/main/java/com/meada/profiles/fotografia/ espelhando profiles/estetica/ +
  profiles/nutri/: raiz (FotografiaSessionStatus, FotografiaContextCache, FotografiaProfileGuard) +
  subpastas professionals/, packages/, config/, sessions/.
- Professionals: CRUD padrão (espelho salon/aesthetic_professionals). delete em uso (sessão com
  professional_id) → 409 professional_in_use; preferir desativar (active=false).
- Packages: CRUD (id, name, category, duration_minutes, price_cents, delivery_days, active). delete
  em uso (sessão com package_id) → 409 package_in_use; preferir desativar. Validar duration_minutes
  15..1440 → 400 invalid_duration; price_cents >= 0 e delivery_days >= 0 → 400 invalid_package.
- Config: GET (fallback default 08:00/20:00/30) + PUT (espelho aesthetic_config; SEM duration aqui).
- Sessions (chassi aesthetic/nutri_appointments):
  * create (a partir da tag/IA ou POST manual): resolve package → snapshota name+price_cents+
    duration_minutes+delivery_days; resolve professional → snapshota professional_name; resolve o
    contato da conversa → snapshota customer_name/phone; start_at do (date+start_time @ America/
    Sao_Paulo); end_at = start_at + duration_minutes MATERIALIZADO; delivery_due_date = data da sessão
    + delivery_days MATERIALIZADO. Valida: profissional/pacote existem e ativos (ProfessionalNotFound/
    PackageNotFound/Inactive* → empty no handler / 4xx no controller); dentro do horário
    (OutsideHoursException → 400 outside_hours); conflito POR profissional re-verificado DENTRO da
    transação (findConflict half-open só status bloqueantes; SlotConflictException → 409 conflict_slot
    com detalhes de quem ocupa).
  * updateStatus: valida transição (inválida → 409 invalid_status_transition); terminal preenche
    status_updated_at; dispara notificação outbound conforme o status (FotografiaSessionNotifier).
  * setDeliveryLink (NOVO, painel/tenant): grava delivery_link na sessão (validar URL não-vazia →
    400 invalid_link se quiser; o padrão mínimo é só persistir trim). É o caminho de ESCRITA do link
    — a IA NÃO tem esse caminho. Pode exigir status realizada/entregue (decisão do agente).
  * POST manual pelo tenant: sem conversation_id (sem WhatsApp) — não notifica.
  * NÃO há DELETE de sessão (histórico; "remover" = status cancelada).
- Notifier (espelho NutriAppointmentNotifier/AestheticAppointmentNotifier): best-effort, persiste
  OUTBOUND/HUMAN, texto DEFENSIVO SEM promessa artística. Notifica: confirmada (com pacote +
  fotógrafo + data/hora), entregue, cancelada. agendada/realizada/falta silenciosos.
- Entrega READ-ONLY do link (espelho EntregaPlanoHandler — mas keyed por session_id):
  * EntregaMaterialHandler parseia <entrega_material>{session_id}; resolve a sessão; se delivery_link
    é não-vazio, envia VERBATIM via notifier.sendText (NÃO passa pela IA). BARREIRA DE SEGURANÇA: só
    entrega se o contato da SESSÃO (session.contactId) == contato da CONVERSA (impede vazar o link de
    outro cliente). Sem link / sessão inexistente / contato diferente → Optional.empty + warn. Devolve
    o texto/link entregue em sucesso. (Diferença pro nutri: o nutri busca o "plano ativo" numa tabela
    separada; aqui o link está direto na sessão — mais simples, mesma barreira.)
- IA:
  * Persona FOTOGRAFIA nova em ProfilePromptContext (tom criativo-acolhedor e organizado com a TRAVA
    DE COMPORTAMENTO embutida — espelhar o tom/estrutura de ESTETICA/SALON/NUTRI; ver bruto sugerido
    abaixo). Adicionar case FOTOGRAFIA no switch de segmentFor(id) e o branch
    if ("fotografia".equals(profileId)) em segmentFor(id, companyId, conversationId).
  * Contexto injetado (FotografiaContextCache, cache TTL 20s — espelho estetica/salon, keyed por
    (companyId, contactId)): fotógrafos ativos + pacotes ativos (nome + categoria + preço + duração +
    prazo de entrega) + sessões do contato (com status e — quando entregue/realizada — a indicação de
    que TEM link a entregar, SEM despejar o delivery_link no contexto, igual nutri não despeja o body
    do plano; o link só sai na entrega) + slots livres POR profissional (próximos 14 dias) +
    instruções e as 2 tags. Invalidação explícita em toda mutação (profissional/pacote/sessão/config/
    delivery_link).
  * Tag <sessao_foto>{"professional_id","package_id","date":"YYYY-MM-DD","start_time":"HH:MM","notes"}
    → AgendamentoSessaoConfirmHandler (espelho AgendamentoEsteticaConfirmHandler/salon; UM modo —
    resolve o contato da conversa como customer; cria a sessão; best-effort; qualquer falha → empty +
    warn).
  * Tag <entrega_material>{"session_id":"UUID"} → EntregaMaterialHandler (espelho EntregaPlanoHandler;
    entrega VERBATIM o delivery_link da sessão; barreira de contato; sem link → empty).
  * JwtFilter autentica /api/fotografia/ (adicionar FOTOGRAFIA_PATH_PREFIX = "/api/fotografia/" + o
    !uri.startsWith(...) no shouldNotFilter). OutboundService ganha maybeProcessSessaoFoto +
    maybeProcessEntregaMaterial (best-effort, guard "fotografia".equals(findProfileId), contactId via
    findContactIdByConversation, encadeados APÓS os outros perfis — perfil é único, só um age; REMOVE
    a tag antes de enviar; espelho EXATO de maybeProcessAgendamentoEstetica/maybeProcessEntregaPlano).

  BRUTO SUGERIDO da persona (ajustar mecanicamente; concatenar como string Java, espelho ESTETICA/NUTRI):
  "Você é o assistente virtual de um estúdio de fotografia e audiovisual. Tom criativo, acolhedor e "
  "organizado, de quem ajuda a registrar um momento importante. Seu papel é APRESENTAR os pacotes do "
  "catálogo, AGENDAR a sessão ou cobertura (ensaio, casamento, evento, vídeo) com um fotógrafo em data "
  "e horário, e ENTREGAR ao cliente o link do material que o estúdio já preparou — e NADA além disso. "
  "Você NUNCA promete resultado artístico ('vai ficar perfeito', 'as fotos vão ficar incríveis'), "
  "NUNCA promete edição, retoque, estilo, quantidade de fotos, clima, luz ou locação além do que o "
  "pacote descreve; NUNCA inventa pacote, preço, duração ou prazo de entrega fora do catálogo — o "
  "preço e o prazo são SEMPRE os do pacote cadastrado. Se o cliente pedir algo personalizado que não "
  "está no catálogo, você acolhe e explica que vai verificar com o estúdio, sem criar pacote ou valor. "
  "Você NUNCA confirma, realiza ou cancela uma sessão pela conversa, NUNCA muda o status e NUNCA "
  "altera o link de entrega — isso é feito pela equipe do estúdio. Você só AGENDA e ENTREGA o link "
  "quando ele já foi disponibilizado. Identifique o cliente pelo telefone; se for o primeiro contato, "
  "peça o nome."

[FRONTEND]
- Telas (App Router, /dashboard/fotografia-*):
  * /dashboard/fotografia-professionals — CRUD fotógrafos (desativar preferido a excluir; delete em
    uso → 409 professional_in_use).
  * /dashboard/fotografia-packages — CRUD dos PACOTES (a tela de catálogo: nome + categoria + duração
    + preço + prazo de entrega (delivery_days) + ativo). Deixar EXPLÍCITO que o prazo de entrega é o
    que define a data prometida na sessão.
  * /dashboard/fotografia-sessions — agenda: lista por status, criar sessão (escolhe profissional +
    pacote + data/hora; conflito → 409 conflict_slot; fora do horário → 400), transição de status
    (botões respeitando ALLOWED_NEXT; inválida → 409). Mostrar pacote + duração + delivery_due_date.
    CAMPO DE LINK DE ENTREGA: input/textarea pra gravar o delivery_link (visível/editável quando a
    sessão está realizada/entregue), com nota de que ele será entregue ao cliente VERBATIM pela IA.
  * /dashboard/fotografia-settings — horário (opens_at/closes_at/slot; SEM duração — vem do pacote).
- types + SDKs (professionals, packages, config, sessions) espelhando estetica/nutri.
  Package: { id, name, category: string|null, durationMinutes, priceCents, deliveryDays, active, ... }.
  Session inclui deliveryLink: string|null e deliveryDueDate: string.
- Status TS fotografia-session-status.ts (6 ids, ALLOWED_NEXT, statusLabel) +
  FotografiaSessionStatusParityTest Java↔TS. (package/link SEM parity — não é máquina.)
- getNavForProfile('fotografia') com BRANCH PRÓPRIO injeta "Fotografia" (4 itens: Fotógrafos,
  Pacotes, Sessões, Configurações). ATENÇÃO: floricultura ficou no enum SEM branch em
  getNavForProfile (fallback) — NÃO repetir esse gap; fotografia PRECISA do branch
  (if (profileId === 'fotografia') return [FOTOGRAFIA_GROUP, ...NAV_GROUPS]).
  Subdomínio fotografia.meadadigital.local. Paleta: livre — sugestão 'carvao' (cinza-grafite escuro,
  cara de estúdio) ou 'indigo'; ATENÇÃO 'grafite' JÁ é da barbearia e 'indigo' JÁ é do legal — preferir
  'carvao' (se não existir no registro de paletas, criar/registrar OU escolher uma livre e DOCUMENTAR a
  escolha). Recomendação: 'carvao'.
- frontend/lib/profiles/profile-type.ts: adicionar
  { id: 'fotografia', productName: 'Fotografia', subdomain: 'fotografia', defaultPaletteId: '<paleta>' }
  no const TS E o membro espelho no enum Java FOTOGRAFIA("fotografia","Fotografia","fotografia",
  "<paleta>") — ProfileTypeParityTest valida.
- npm build limpo (next build — Turbopack dev esconde import quebrado).

[DOCS]
- CLAUDE.md: seção "## Perfil Fotografia (FotografiaBot, camada 8.16)" espelhando as seções de perfil.
  Documentar EXPLÍCITO: COMBINA o chassi de agenda-por-profissional (dental/salon/nutri: conflito por
  profissional + end_at materializado) + pacote-catálogo (espelho leve estetica, SEM saldo); a
  ESCAPADA (entrega de material por LINK read-only com prazo derivado — delivery_link/delivery_due_date
  NA PRÓPRIA SESSÃO, entrega verbatim com barreira de contato, espelho da entrega de plano do nutri); a
  TRAVA DE COMPORTAMENTO (IA nunca promete resultado artístico/edição, nunca inventa pacote/preço,
  nunca muda status, nunca edita o link — só agenda + entrega); as 2 tags (<sessao_foto>,
  <entrega_material>); cliente NÃO é entidade (snapshots no session); status feminino (agendada/
  confirmada/realizada/entregue/cancelada/falta); SEM upload (LINK colado).
- docs/PERFIL_FOTOGRAFIA.md: guia operacional do tenant (fotógrafos, pacotes — com preço/duração/prazo
  e como o prazo vira a data prometida; agenda — estados/notificações; como gravar o link de entrega e
  como ele chega ao cliente; como a IA atende; o bloco "o que a IA NUNCA faz"). Espelhar
  PERFIL_ESTETICA.md / PERFIL_NUTRI.md.
- NÃO mexer em system-template.txt nem em outros perfis.

[TESTES BACKEND]
Espelhar a suíte do estetica/nutri (service + controller integration por entidade):
- FotografiaSessionStatusParityTest + ProfileTypeParityTest (fotografia no enum/const).
- FotografiaProfessionalServiceTest + ControllerIntegrationTest (CRUD, delete-em-uso 409).
- FotografiaPackageServiceTest + ControllerIntegrationTest (CRUD; invalid_duration 400; delete-em-uso
  409; delivery_days/price persistem).
- FotografiaConfigServiceTest/ControllerIntegrationTest (GET fallback + PUT).
- FotografiaSessionServiceTest (create snapshota pacote+duração+preço+delivery_days+profissional+
  customer; end_at materializado = start + duration; delivery_due_date materializado = data + 
  delivery_days; CONFLITO POR PROFISSIONAL re-verificado na transação (mesmo prof+overlap → conflict;
  profs diferentes mesmo horário → OK — paralelismo); fora do horário → outside_hours; transição
  válida/inválida; setDeliveryLink grava; notificação confirmada/entregue/cancelada) +
  ControllerIntegrationTest (409 conflict_slot; 409 invalid_status_transition; wrongProfile 403).
- AgendamentoSessaoConfirmHandlerTest (agenda a sessão; sem tag → empty; ids inválidos → empty;
  conflito → empty; tag removida da msg).
- EntregaMaterialHandlerTest (a CHAVE da entrega READ-ONLY): sessão COM delivery_link + contato da
  conversa == contato da sessão → entrega o link EXATO (asserção casa o conteúdo VERBATIM, char-a-char);
  sessão de OUTRO contato → BLOQUEADO (empty); sessão SEM delivery_link → empty; sem tag → empty.
- (TRAVA: a trava vive na persona — testar que a persona FOTOGRAFIA contém os marcadores "NUNCA
  promete resultado" / "NUNCA inventa pacote" / "NUNCA muda o status" via assert no segmentFor,
  espelho de como se testa o tom em SMs anteriores; e que segmentFor('fotografia') é não-vazio e
  começa com "# Persona (Fotografia)".)
mvn final = relatar contagem REAL do Surefire (não estimar).

[CONSTRAINTS DUROS]
- Migration única (próximo slot livre; provisoriamente NN=60 — CONFIRMAR). Sem upload de arquivo —
  entrega de material é por LINK colado (URL externa); bloqueador SERVICE_ROLE_KEY.
- Cliente NÃO é entidade do core — continua o contact; snapshots customer_name/phone + professional_
  name + package_name + price_cents + duration_minutes + delivery_days na sessão.
- Conflito POR professional_id (half-open, re-verificado na transação). NÃO por company. end_at
  materializado no INSERT (não generated). duration vem do package (snapshot), NÃO do config.
  delivery_due_date materializado no INSERT (data da sessão + delivery_days).
- PACOTE = catálogo (preço + duração + delivery_days), espelho LEVE estetica SEM saldo consumível. A
  sessão snapshota o pacote.
- Entrega de material READ-ONLY (verbatim, barreira de contato) — espelho EntregaPlanoHandler, mas o
  link mora NA PRÓPRIA SESSÃO (não em tabela separada). A IA NUNCA grava/edita/interpreta o link.
- TRAVA: a IA NUNCA promete resultado artístico/edição/quantidade/locação, NUNCA inventa pacote/
  preço/prazo fora do catálogo, NUNCA confirma/realiza/cancela/muda status pela conversa, NUNCA edita
  o delivery_link. Só AGENDA e ENTREGA o link já gravado.
- Status feminino (agendada/confirmada/realizada/entregue/cancelada/falta) consistente em enum/TS/
  CHECK/textos. delivery_link só entregável quando preenchido (sem link → não entrega).
- Tags <sessao_foto> e <entrega_material> distintas de TODAS as outras.
- LGPD: notes (sessão) é ADMINISTRATIVO. delivery_link é uma URL gravada pelo estúdio — sem dado
  sensível embutido.
- NÃO mexer em outros perfis nem em system-template.txt. Webhook OFF.
- Cache TTL 20s + invalidação em toda mutação. NÃO despejar delivery_link no contexto da IA (só
  indica que a sessão TEM link; o link sai só na entrega — espelho do body do plano do nutri).
- 529 → inline. Gate 3× → pausar. Working tree sujo → pausar. git add EXPLÍCITO (nunca git add .);
  .env/CONTEXT.md/secrets NUNCA staged.
- SEED com timestamptz: usar `at time zone 'America/Sao_Paulo'` (lição do fuso).
- IDs de namespace compartilhado no seed com sufixo -14x (conferir os usados; evitar colisão FK).
- Tabela nova entra na migration ANTES de tocar o banco (lição os_config). Adicionar as 4 tabelas ao
  TRUNCATE/SCRIPTS do AbstractIntegrationTest.
- Pool de teste minúsculo já está em src/test/resources/application-dev.yml (lição SM-K Hikari ×
  N contextos) — NÃO mexer; só confere que segue lá.
- Decisões menores: agente decide (layout exato, ícones do nav, nome de constante, paleta entre as
  sugeridas, se a notificação de entregue inclui o link, se o conflito conta 'realizada', se há campo
  livre de cliente recorrente na tag — documentar cada escolha).

[PASSO FINAL — TENANT igorhaf27 + SEED + COMMIT + PUSH + SMOKE + RELATÓRIO]
F.1 — TENANT igorhaf27 (Estúdio Foco Modelo, profile=fotografia), padrão GoTrue (instance_id=
      zero-UUID + colunas de token='' não NULL — lição seed auth.users), senha em comunicação direta.
      company c…-027 / user a…-027 (numeração do tenant confirmado). Caddy + /etc/hosts pra
      fotografia.meadadigital.local. (Se igorhaf27 já existe de outra SM, usar o próximo livre e
      reportar.)
F.2 — Seed /tmp/seed-fotografia.sql (NÃO COMITAR; `at time zone 'America/Sao_Paulo'`; ids sufixo -14x;
      lição os_config — só roda DEPOIS que a migration versionada está no disco/aplicada):
  - config: opens_at 08:00 / closes_at 20:00 / slot 30.
  - 2 fotógrafos: "Marina Lopes" (specialty "fotografia social"), "Diego Sá" (specialty "vídeo").
  - 3 pacotes: "Ensaio 1h / 30 fotos" (60min, R$, delivery_days 7), "Casamento 8h / álbum" (480min,
    R$, delivery_days 30), "Vídeo institucional" (180min, R$, delivery_days 15).
  - contact "Beatriz Nunes" +5511966665555 (VINCULADO: instance+conversation, pra smoke de
    notificação + entrega do link); + contact "Caio Ramos" +5511977776666 (sem vínculo).
  - 3 sessões cobrindo estados (start_at `at time zone 'America/Sao_Paulo'`; end_at + delivery_due_date
    materializados):
    * VINCULADA (Beatriz / Marina / "Ensaio 1h" → 60min → end_at, delivery_due_date = data+7),
      status 'realizada', delivery_link PREENCHIDO (ex.: "https://galeria.exemplo.com/beatriz-ensaio")
      — pra smoke da ENTREGA read-only via <entrega_material> + transição realizada→entregue.
    * (Caio / Diego / "Vídeo institucional") status 'confirmada', data +5d — pra smoke transição
      confirmada→realizada (silenciosa) e barreira de contato (entrega da sessão do Caio na conversa
      da Beatriz → bloqueada).
    * (Beatriz / Marina / "Ensaio 1h") status 'realizada' SEM delivery_link — pra smoke "sem link →
      não entrega".
    Usar profissional/horário que NÃO conflitem no seed; o conflito é provado via POST no smoke
    (BLOCO C), não no seed.
F.3 — JwtFilter /api/fotografia/ (se ainda não).
F.4-F.6 — git add EXPLÍCITO dos arquivos da SM (migration, profiles/fotografia/**, OutboundService,
      JwtFilter, ProfilePromptContext, ProfileType.java, profile-type.ts, nav-config.tsx, frontend
      fotografia-*, fotografia-session-status.ts, testes, docs) + sanity (git status -s +
      diff --staged --stat + grep segredo eyJ../password/secret= + confirmar .env/.env.local/CONTEXT.md
      FORA da staging) + commit. Mensagem padrão (feat(camada-8.16): perfil fotografia/Fotografia
      (Fotografia · Cinema · Audiovisual) — agenda+pacote+entrega de link read-only) com FUNDAÇÃO/
      BACKEND/FRONTEND/DECISÕES/VALIDAÇÃO contagem REAL/NÃO TOCADO/FECHAMENTO + trailer
      Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>). Tag fase-8.16-fechada (nº real
      confirmado no arranque).
F.7 — git push origin main + git push origin --tags. NUNCA --force.
F.8 — docker compose restart backend (ou ./scripts/run-local.sh) + aguardar /admin/me → 401
      missing_auth_header.
F.9 — Smoke E2E (token ES256 via POST {SUPABASE_URL}/auth/v1/token?grant_type=password):
  BLOCO A: auth — igorhaf27 → /admin/me → role=tenant_admin, profileId=fotografia,
    productName=Fotografia.
  BLOCO B: catálogo + guard — GET fotógrafos (2); GET pacotes (3); GET config + PUT; CRUD smoke de um
    pacote + delete-em-uso 409; tenant de OUTRO perfil (estetica/nutri) → /api/fotografia/professionals
    → 403 forbidden_wrong_profile.
  BLOCO C: agenda + CONFLITO POR PROFISSIONAL [CHAVE do chassi 1] —
    - GET sessions (3); POST sessão nova (Marina, pacote "Ensaio 1h", slot livre) → 200, end_at =
      start_at + 60min materializado; delivery_due_date = data + 7 materializado.
    - POST sessão SOBREPOSTA no MESMO profissional/horário → 409 conflict_slot (com detalhes).
    - POST sessão no MESMO horário com Diego (OUTRO profissional) → 200 (paralelismo prova que o
      conflito é POR profissional, não por company).
    - POST fora do horário (06:00) → 400 outside_hours.
  BLOCO D: PACOTE SNAPSHOT [CHAVE do chassi 2] —
    - a sessão criada no BLOCO C carrega package_name/price_cents/duration_minutes/delivery_days
      SNAPSHOTADOS; alterar o pacote no catálogo (PUT) e reler a sessão → snapshot INALTERADO.
  BLOCO E: ENTREGA READ-ONLY DO LINK (a escapada desta SM) [CHAVE] —
    - <entrega_material>{session_id da sessão da Beatriz com link} via handler/teste → o cliente
      RECEBE a URL EXATA de delivery_link (asserção casa VERBATIM, char-a-char).
    - <entrega_material> de uma sessão de OUTRO contato (Caio) na conversa da Beatriz → BLOQUEADO
      (empty, nada entregue — barreira de contato).
    - <entrega_material> de uma sessão SEM delivery_link → empty (nada entregue).
  BLOCO F: status + AGENDAMENTO via IA [CHAVE da trava] —
    - PATCH agendada→confirmada (sessão vinculada) → 200 + msg outbound (confirmada, com pacote+
      fotógrafo+data/hora; asserção casa o conteúdo EXATO); transição inválida (agendada→entregue) →
      409 invalid_status_transition; realizada→entregue notifica; cancelada notifica.
    - <sessao_foto>{professional_id, package_id, slot livre} na conversa da Beatriz via handler/teste
      → sessão criada; tag removida da msg.
    - PROVA DA TRAVA: segmentFor('fotografia') contém "NUNCA promete resultado" + "NUNCA inventa
      pacote" + "NUNCA muda o status" (assert no texto da persona).
  BLOCO G: regressão — os perfis anteriores intactos (smoke leve 1 endpoint cada); fotografia →
    /api/estetica/* → 403; fotografia → /api/nutri/* → 403.
  BLOCO H: paridade — mvn test -Dtest=FotografiaSessionStatusParityTest,ProfileTypeParityTest → verde.
  Cleanup smoke + restaurar seed pristine. mvn final: contagem REAL do Surefire.
F.10 — RELATÓRIO consolidado + DESTAQUE EXPLÍCITO:
  - "Nº perfil vertical — camada 8.16 (confirmado no arranque)"
  - "COMBINA a AGENDA por profissional (dental/salon/nutri: conflito por profissional + end_at
     materializado) + PACOTE-catálogo (espelho leve estetica, SEM saldo) com snapshot na sessão"
  - "ESCAPADA: entrega de MATERIAL por LINK read-only com prazo derivado (delivery_link/
     delivery_due_date NA SESSÃO; entrega verbatim, barreira de contato — espelho da entrega de plano
     do nutri); BLOCO E prova"
  - "BLOCO C prova o conflito POR profissional (overlap mesmo prof → 409; profs diferentes mesmo
     horário → OK); BLOCO D prova o pacote snapshot"
  - "TRAVA: IA NUNCA promete resultado artístico/inventa pacote-preço/muda status/edita link; só
     agenda + entrega o link; BLOCO F prova a persona"
  - "Seed usou at time zone America/Sao_Paulo + sufixo de ids -14x (sem fuso/colisão)"
  - "as 4 tabelas criadas DENTRO da migration 60 (lição os_config)"
  - PENDÊNCIAS: upload de foto/vídeo (LINK colado por ora; bloqueador SERVICE_ROLE_KEY), contrato/
    e-sign, pagamento/sinal (Stripe), shot list/timeline do dia, segundo profissional na sessão,
    galeria de seleção pelo cliente, scheduler de auto-transição/lembrete + a dívida acumulada
    (webhook OFF, cliente real, olho humano sobre os verticais).

[REPORTAR]
Igual SMs anteriores. Incluir EXPLICITAMENTE:
- "ProfileType.FOTOGRAFIA adicionado (Nº perfil real, camada 8.16) — paridade ProfileType validada"
- "Paridade FotografiaSessionStatus validada (status feminino: agendada/confirmada/realizada/
   entregue/cancelada/falta)"
- "Tenant igorhaf27 (ou o livre real) criado seguindo padrão GoTrue + Caddy/etc/hosts"
- "Chassi de agenda clonado do DENTAL/SALON/NUTRI: conflito POR profissional re-verificado na
   transação + end_at materializado no INSERT"
- "PACOTE = catálogo (preço+duração+delivery_days), espelho leve da estetica SEM saldo; a sessão
   snapshota o pacote"
- "Cliente NÃO é entidade do core — continua o contact; snapshots na sessão"
- "ESCAPADA: entrega de material por LINK read-only (<entrega_material>, verbatim, barreira de
   contato; delivery_link/delivery_due_date na própria sessão) — espelho do EntregaPlanoHandler do
   nutri, mas keyed por session_id"
- "TRAVA na persona: IA NUNCA promete resultado/edição, NUNCA inventa pacote/preço/prazo, NUNCA muda
   status, NUNCA edita o link — só agenda e entrega"
- "Tags: <sessao_foto> (agenda) + <entrega_material> (entrega read-only) — distintas de TODAS as
   outras"
- "OutboundService ganhou maybeProcessSessaoFoto + maybeProcessEntregaMaterial (encadeados, perfil
   único)"
- "JwtFilter autentica /api/fotografia/"
- "getNavForProfile('fotografia') com branch próprio (não repetir o gap do floricultura)"
- "Paleta escolhida: <carvao|indigo|outra> (registrar e por quê; grafite já é da barbearia, indigo já
   é do legal)"
- "as 4 tabelas criadas DENTRO da migration 60 (lição os_config); 4 tabelas no TRUNCATE do
   AbstractIntegrationTest"
- "Seed: at time zone America/Sao_Paulo + sufixo de ids -14x (sem bug de fuso, sem colisão FK)"
- "Próximas fases: upload de mídia/galeria de seleção/contrato-e-sign/pagamento/shot list/scheduler
   + fila de prioridade (webhook, cliente real, olho humano sobre os verticais)"
