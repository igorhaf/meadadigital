>>> JÁ IMPLEMENTADO — perfil restaurant, camada 7.3, migration 32_restaurant.sql. Prompt de nicho
>>> RETROATIVO, formato T5. Fonte: CLAUDE.md seção Perfil Restaurante + migration 32 +
>>> docs/PERFIL_MESABOT.md. Documenta o REAL já no código — não é proposta, é registro.

[TAREFA — PERFIL RESTAURANT / MesaBot (camada 7.3) — RETROATIVO]

Documentar (retroativo, formato T5) o perfil vertical RESTAURANT já implementado no Meada:
o tenant restaurant (`profile_id='restaurant'`) é um produto de RESERVAS de restaurante (MesaBot).
Gerencia mesas e reservas; a IA atende clientes via WhatsApp, verifica disponibilidade e confirma a
reserva. É o 3º perfil vertical real (sushi 7.1 · legal 7.2 · restaurant 7.3) e o 4º no enum
`ProfileType` (5º contando generic). Este .md descreve o que EXISTE — não inventa nada além do que
está em CLAUDE.md, na migration 32_restaurant.sql, em docs/PERFIL_MESABOT.md e no código em
`src/main/java/com/meada/profiles/restaurant/`.

[CONTEXTO]
PROJETO MEADA em /home/igorhaf/meada.
Monolito que se apresenta como N produtos verticais ("perfis"). Tenant restaurant acessa o produto
"Restaurante" (subdomínio `mesa`, paleta `tijolo`) e vê o produto de RESERVAS DE MESA. A IA atende
clientes em linguagem natural via WhatsApp: conhece as mesas disponíveis e as reservas já marcadas
dos próximos 7 dias, negocia dia/hora/mesa/nº de pessoas, e na confirmação cria a reserva como
`pendente`. O tenant acompanha pela agenda e muda o status conforme o atendimento.

>>> TRAVA DE COMPORTAMENTO DA IA (cravada) <
- A IA NUNCA inventa mesa que não existe (só usa os ids EXATOS do bloco de contexto).
- A IA NUNCA promete horário fora da janela de funcionamento (opens_at..closes_at).
- A IA cria a reserva como `pendente` — NÃO confirma sozinha. CONFIRMAR é AÇÃO HUMANA do restaurante
  no painel (que é quando o cliente recebe o aviso de confirmação).
- Verifica a disponibilidade no contexto ANTES de confirmar; se o horário pedido está ocupado,
  oferece alternativa próxima (30 min antes/depois) ou outra mesa livre.
- A IA NÃO usa tool calling / responseSchema do Gemini pra emitir a reserva (a API trata os dois como
  mutuamente exclusivos e o outbound já usa responseSchema) — emite a tag `<reserva>` em TEXTO LIVRE;
  o backend parseia via regex.

EVOLUÇÃO ESTRUTURAL (o que este perfil inaugurou):
- LÓGICA DE CONFLITO DE RESERVA EM SQL TRANSACIONAL: `ReservationRepository.findConflict` é um SELECT
  com a janela materializada — sobreposição half-open `NOT (existing.end_at <= newStart OR
  existing.start_at >= newEnd)`, só status bloqueantes (`pendente`/`confirmada`), por mesa. O
  `insertReservation` RE-VERIFICA o conflito DENTRO da transação (`@Transactional`) imediatamente
  antes do INSERT — fecha a janela de race entre o que a IA viu no cache (15s) e a persistência. Se
  conflitar, lança `SlotConflictException` → o service mapeia pra `ConflictException` → 409
  `conflict_slot` (com detalhes de quem ocupa: id/guest_name/start_at/end_at via `ReservationConflict`).
- `end_at` NÃO é coluna gerada — é MATERIALIZADO no INSERT pelo repositório (`startAt +
  durationMinutes`). Lição cravada nesta SM: `timestamptz + interval` não é IMMUTABLE (depende do
  timezone da sessão p/ DST), e Postgres exige expressão immutable em GENERATED. Materializar no
  insert é simples e não muda a lógica de conflito (o SELECT de overlap compara start_at/end_at já
  gravados). (Atenção: o COMENTÁRIO da migration 32 ainda diz "end_at é COLUNA GERADA" — texto
  desatualizado; o comportamento REAL no código é materialização no INSERT.)
- TAG `<reserva>` em texto livre: espelho da tag `<pedido>` do sushi, parseada por regex no
  `ReservationConfirmHandler`; o `OutboundService` REMOVE a tag antes de enviar a mensagem ao cliente.
- SLOT 30min, DURAÇÃO 2h FIXAS (duração configurável por restaurante; slot de granularidade 30min é
  a referência que a IA usa pra oferecer alternativa). Buffer entre reservas = 0 nesta SM.

DECISÕES CRAVADAS (reais, registradas no código/CLAUDE.md):
1. Modelo análogo a sushi/legal: `restaurant_tables` (catálogo de mesas, ~ sushi_menu_items) +
   `table_reservations` (reservas, ~ sushi_orders) + `restaurant_reservation_config` (duração/horário,
   1:1 com company). Migration 32.
2. Status hardcoded materializado (`ReservationStatus` ↔ `reservation-status.ts`,
   `ReservationStatusParityTest`): `pendente → confirmada/cancelada`; `confirmada →
   realizada/cancelada/no_show`; terminais `realizada/cancelada/no_show`. Transição inválida → 409
   `invalid_status_transition`.
3. Notificação: só `confirmada` (com dados da reserva — data/hora/mesa/pessoas) e `cancelada`
   notificam o cliente (`ReservationNotifier`); `realizada/no_show/pendente` são silenciosos (quem
   furou não recebe sermão; pendente ainda não confirmou). Best-effort (falha de envio NÃO reverte a
   transição já persistida).
4. (numeração das decisões 4 não destacada separadamente — duração/buffer/horário viram a config 1:1.)
5. Re-verificação transacional do conflito no INSERT (defesa de race). Risco aceito no MVP: se a IA
   prometer e o slot for ocupado no instante de gravar, a reserva NÃO é criada — o `parseAndCreate`
   retorna empty, a mensagem da IA segue normal sem reserva, loga warn; o tenant contorna manualmente.
6. Cache de contexto da IA TTL 15s (`ReservationContextCache`) — UM QUARTO do sushi/legal (60s) de
   propósito: a agenda muda rápido. Invalidação explícita ao mutar mesa/reserva/config.
- Fuso HARDCODED America/Sao_Paulo (pendência conhecida) — janela de funcionamento e rótulos de
  notificação avaliados nesse fuso.
- POST manual pelo tenant cria reserva sem `conversation_id` (sem WhatsApp) → não notifica (sem canal).
- NÃO há DELETE de reserva (histórico; "remover" = status cancelada).

NÃO TEM nesta SM (registrado pra não inventar): salão de beleza, pousada, agenda de profissionais,
cardápio (sushi cobre), pagamento antecipado, no-show com cobrança, scheduler de auto-transição,
lembrete "sua reserva é em 1h", reserva em grupo (várias mesas combinadas), feriados/dias especiais,
buffer configurável de fato (fixo em 0). Sem foto/anexo (bloqueador SERVICE_ROLE_KEY).

[FUNDAÇÃO — migration 32_restaurant.sql]
- ALTER companies CHECK pra aceitar 'restaurant' (drop+add da `companies_profile_id_check`,
  preservando os anteriores: `generic`,`legal`,`dental`,`sushi`,`restaurant`).
- RLS enable+force em todas; policies via `app.company_id()`; grants `authenticated` + `service_role`.
  `table_reservations` NÃO tem policy de INSERT pra `authenticated` (INSERT é só backend/service_role
  — via IA `ReservationConfirmHandler` ou via POST manual do tenant pela API backend); o tenant só
  SELECT/UPDATE (mudar status no Kanban/agenda).
- Tabelas (colunas reais):
  * `restaurant_tables` — catálogo de mesas. (id uuid PK; company_id NOT NULL refs companies on delete
    restrict; label text NOT NULL CHECK length(trim) 1..60; capacity int NOT NULL CHECK 1..50;
    available boolean NOT NULL default true; notes text; created_at/updated_at). UNIQUE (company_id,
    label). Índice parcial `(company_id, available) where available = true`. `available=false` retira a
    mesa da disponibilidade que a IA enxerga.
  * `restaurant_reservation_config` — 1:1 com company. (company_id uuid PK refs companies on delete
    cascade; duration_minutes int NOT NULL default 120 CHECK 30..600; buffer_minutes int NOT NULL
    default 0 CHECK >=0; opens_at time NOT NULL default '11:00'; closes_at time NOT NULL default
    '23:00'; created_at/updated_at). Ausente → defaults (120/0/11:00/23:00).
  * `table_reservations` — reservas. (id uuid PK; company_id NOT NULL refs companies on delete restrict;
    table_id NOT NULL refs restaurant_tables on delete restrict; conversation_id refs conversations on
    delete set null [nullable — reserva manual não tem WhatsApp]; contact_id refs contacts on delete set
    null; guest_name text NOT NULL [SNAPSHOT]; guest_phone text [SNAPSHOT]; start_at timestamptz NOT
    NULL; duration_minutes int NOT NULL [SNAPSHOT do config no momento]; end_at timestamptz NOT NULL
    [MATERIALIZADO no INSERT = start_at + duration_minutes, NÃO gerada]; num_people int NOT NULL CHECK
    1..50; status text NOT NULL default 'pendente' CHECK in
    ('pendente','confirmada','realizada','cancelada','no_show'); notes text; created_at;
    status_updated_at timestamptz NOT NULL default now()).
    Índices: `(company_id, status, start_at)` (agenda/Kanban por dia) + índice CRÍTICO de conflito
    `(table_id, start_at) where status in ('pendente','confirmada')`.
- Conflito `findConflict(tableId, newStart, newEnd)`: SELECT half-open `not (end_at <= ? or start_at >=
  ?)`, status `in ('pendente','confirmada')`, `order by start_at asc limit 1`. Re-checado dentro da
  transação do INSERT.
- Status hardcoded materializado (`ReservationStatus.java` ↔ `reservation-status.ts`): pendente →
  confirmada/cancelada ; confirmada → realizada/cancelada/no_show ; realizada/cancelada/no_show
  terminais.
- Snapshots no momento da reserva: guest_name/guest_phone (o contato pode sumir) + duration_minutes (o
  config pode mudar) ficam congelados — alterar o config NÃO altera reservas já criadas.
- updated_at mantido pelos repositórios (set updated_at = now() no UPDATE) — sem trigger genérico.

[BACKEND]
Pacote `src/main/java/com/meada/profiles/restaurant/`.
- `RestaurantProfileGuard` (`requireRestaurant`) → 403 `forbidden_wrong_profile` pra tenant de outro
  perfil. `JwtAuthenticationFilter` autentica `/api/restaurant/**` (além de `/admin/**`, `/api/sushi/**`,
  `/api/legal/**`).
- tables/: `RestaurantTable` (model) + `RestaurantTableRepository` (JdbcTemplate) +
  `RestaurantTableService` + `RestaurantTableController` — CRUD de mesas. Excluir mesa com reservas é
  bloqueado (on delete restrict — proteção de histórico); o caminho é `available=false`.
- config/: `RestaurantReservationConfig` + Repository + Service + Controller — GET (fallback aos
  defaults se ausente) + PUT (upsert da config 1:1).
- reservations/:
  * `Reservation` (model, com `tableLabel` resolvido no JOIN) + `ReservationConflict` (record do detalhe
    do conflito).
  * `ReservationRepository` — opera via service_role, escopo por company_id no WHERE; `listByCompany`
    (filtros status/janela, paginado, por start_at asc), `countByCompany`, `findById`,
    `listActiveUpcoming` (pendente/confirmada na janela [from,to) — pro contexto IA), `findConflict`,
    `insertReservation` (`@Transactional`, re-check + materializa end_at), `updateStatus`.
  * `ReservationService` — `create` (valida mesa existe + reserva CABE na janela de funcionamento via
    `requireInsideHours`, lê duração do config como snapshot, delega ao repo que re-checa conflito;
    invalida o cache). `updateStatus` (`@Transactional`: valida alvo + transição → 409 se inválida;
    persiste; notifica o cliente com o texto do novo status; invalida o cache). Exceções:
    `ReservationNotFoundException` (404), `TableNotFoundException` (404 table_not_found),
    `OutsideHoursException` (400 outside_hours), `InvalidStatusException` (400 invalid_status),
    `InvalidStatusTransitionException` (409 invalid_status_transition), `ConflictException` (409
    conflict_slot, carrega o conflito).
  * `ReservationConfirmHandler` — TAG `<reserva>{"table_id","date":"YYYY-MM-DD","start_time":"HH:MM",
    "num_people":N}</reserva>`. `hasReservationTag` / `stripReservationTag` / `parseAndCreate`. Combina
    date+start_time num instante America/Sao_Paulo. Retorna `Optional.empty()` (mensagem segue SEM
    reserva, loga warn) quando: sem tag, JSON inválido, campos faltando/inválidos, mesa inexistente,
    fora do horário, ou conflito de slot.
  * `ReservationNotifier` (`notifyStatus`) — espelho do SushiOrderNotifier: resolve telefone +
    credenciais via a conversa e envia pela Evolution (`EvolutionSender`, honra EVOLUTION_DRY_RUN);
    persiste em `messages` (OUTBOUND/HUMAN). text==null (pendente/realizada/no_show) ou conversationId
    null (reserva manual) → pula em silêncio. Best-effort (nunca lança; nunca reverte a transição).
  * `ReservationController` — `GET /api/restaurant/reservations` (lista/filtro/paginação),
    `GET /api/restaurant/reservations/{id}`, `POST /api/restaurant/reservations` (criação manual pelo
    tenant, sem conversation_id → não notifica), `PATCH /api/restaurant/reservations/{id}/status`.
- `ReservationContextCache` (TTL 15s, Caffeine, maximumSize 500, keyed por company): bloco de texto
  formatado = mesas DISPONÍVEIS (com id pra a tag) + reservas ATIVAS dos próximos 7 dias (slots
  ocupados) + config (duração/horário) + INSTRUÇÕES DE RESERVA (incl. o formato da tag). `invalidate`
  chamado pelos services ao mutar mesa/reserva/config.
- `OutboundService` ganhou `maybeProcessRestaurantReservation` (encadeado após o sushi no fluxo;
  perfil é único, só um age) — best-effort; remove a tag antes de enviar.

[FRONTEND]
- `getNavForProfile('restaurant')` injeta o grupo "Reservas" (heading "Reservas", NÃO "Restaurante"
  pra não colidir com o sushi) com 3 itens: Mesas + Reservas + Configurações.
- Telas:
  * `/dashboard/tables` — CRUD de mesas (nome/label, capacidade 1–50, observações; checkbox inline
    "disponível"; excluir bloqueado se houver reservas).
  * `/dashboard/reservations` — agenda: lista por dia/horário, filtro por status; nova reserva manual
    (mesa/data/hora/nº pessoas/nome; telefone+obs opcionais; se o slot está ocupado mostra QUEM ocupa
    e de que horas a que horas); detalhe + mudança de status (confirmar/cancelar notificam o cliente
    se a reserva veio do WhatsApp; realizada/no_show são silenciosos).
  * `/dashboard/restaurant-settings` — config: duração da reserva (padrão 2h), intervalo entre
    reservas (0 padrão), horário de funcionamento (abre/fecha). Mudanças afetam só reservas FUTURAS.
- `reservation-status.ts` (const espelhada do enum Java; `ReservationStatusParityTest` garante a
  paridade). `profile-type.ts`: `{ id:'restaurant', productName:'Restaurante', subdomain:'mesa',
  defaultPaletteId:'tijolo' }`.

[DOCS]
- CLAUDE.md: seção "## Perfil Restaurante (MesaBot, camada 7.3)" — descreve o modelo, a tag `<reserva>`,
  o conflito SQL transacional, `end_at` materializado, status/transições, notificações, cache TTL 15s,
  guard, sidebar, o que NÃO tem.
- docs/PERFIL_MESABOT.md: guia operacional do tenant (cadastrar mesas; configurar o restaurante; agenda
  de reservas; como a IA atende; limitações honestas).
- NÃO mexer em system-template.txt nem em outros perfis.

[TESTES BACKEND]
- `ReservationStatusParityTest` — garante a paridade Java (`ReservationStatus`) ↔ TS
  (`reservation-status.ts`) das transições/labels.
- `ProfileTypeParityTest` — enum Java `ProfileType` ↔ const TS `profile-type.ts` (restaurant presente).
- Suíte de service/controller integration por entidade (tables/config/reservations): CRUD, conflito de
  slot (409 conflict_slot com detalhes), re-check transacional, outside_hours (400), transição inválida
  (409), guard wrongProfile (403), notificação só em confirmada/cancelada.
- Contagem de testes vem do Surefire (`Tests run: N`), nunca de grep — relatar a REAL.

[CONSTRAINTS DUROS]
- Migration única (32). Sem foto/anexo (bloqueador SERVICE_ROLE_KEY).
- Cliente NÃO é entidade do core — continua o contact (reserva tem conversation_id/contact_id nullable;
  guest_name/guest_phone snapshots).
- `end_at` MATERIALIZADO no INSERT (NÃO coluna gerada — `timestamptz + interval` não é IMMUTABLE).
- Conflito de slot half-open re-checado DENTRO da transação (defesa de race). 409 conflict_slot.
- Status hardcoded com parity test. Tag `<reserva>` em texto livre (não tool calling), removida antes
  de enviar ao cliente.
- INSERT de reserva é só backend/service_role (sem policy authenticated insert); tenant só SELECT/UPDATE.
- Cache de contexto TTL 15s + invalidação em toda mutação de mesa/reserva/config.
- Notificação só confirmada/cancelada; best-effort (não reverte a transição). Reserva manual (sem
  conversation_id) não notifica.
- Fuso America/Sao_Paulo HARDCODED (pendência). NÃO há DELETE de reserva.
- NÃO mexer em outros perfis nem em system-template.txt. Webhook OFF.

[PASSO FINAL — resumido]
Migration 32 aplicada ao Supabase; `ProfileType.RESTAURANT` + const TS + parity verde; tenant
restaurant provisionado (subdomínio mesa); guard + JwtFilter `/api/restaurant/**`;
`maybeProcessRestaurantReservation` no OutboundService; sidebar "Reservas" (Mesas/Reservas/
Configurações); telas /dashboard/{tables,reservations,restaurant-settings}; suíte verde no Surefire;
docs/PERFIL_MESABOT.md + seção CLAUDE.md. git add explícito, sem .env/CONTEXT.md/secrets. mvn final =
contagem REAL do Surefire.

[REPORTAR]
- "Perfil restaurant — camada 7.3; modelo análogo a sushi/legal (tables + reservations + config 1:1)"
- "EVOLUÇÃO: conflito de slot half-open em SQL re-checado na transação do INSERT (409 conflict_slot)"
- "end_at MATERIALIZADO no INSERT (não coluna gerada — lição timestamptz+interval não IMMUTABLE)"
- "Tag <reserva> em texto livre (não tool calling), removida antes de enviar; ReservationConfirmHandler"
- "Status pendente→confirmada→realizada (+cancelada/no_show); ReservationStatusParityTest verde"
- "Notifica só confirmada (com dados) e cancelada; best-effort; reserva manual não notifica"
- "ReservationContextCache TTL 15s (um quarto do sushi/legal) + invalidação em toda mutação"
- "Guard RestaurantProfileGuard (403 forbidden_wrong_profile); JwtFilter autentica /api/restaurant/**"
- "Sidebar 'Reservas' (Mesas/Reservas/Configurações); subdomínio mesa, paleta tijolo"
- "Fuso America/Sao_Paulo hardcoded (pendência); sem DELETE de reserva; INSERT só backend/service_role"
