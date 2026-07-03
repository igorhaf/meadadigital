>>> JÁ IMPLEMENTADO — perfil pousada, camada 7.6, migration 35_pousada.sql. Prompt de nicho
>>> RETROATIVO, formato T5. Fonte: CLAUDE.md seção Perfil Pousada + migration 35 +
>>> docs/PERFIL_POUSADA.md.

[TAREFA — PERFIL POUSADA / PousadaBot (camada 7.6) — RETROATIVO]

Este documento descreve, no formato T5, o perfil vertical POUSADA tal como JÁ FOI implementado
(camada 7.6, sexto perfil vertical real). Não é uma sub-maratona a executar — é a reconstrução
retroativa do prompt de nicho que teria gerado o que existe hoje no código. Toda seção reflete o
estado REAL no filesystem/banco; nada aqui é especulação.

[CONTEXTO]
PROJETO MEADA em /home/igorhaf/meada.
Pousada é o template de nicho pra POUSADA / HOSPEDAGEM PEQUENA dentro do mesmo dashboard Meada.
O tenant acessa pousada.meadadigital.local e vê o produto "PousadaBot". A IA atende hóspedes via
WhatsApp com tom acolhedor-turístico: mostra os quartos por número de pessoas e datas, calcula o
total da estadia (diária × noites) e faz a reserva. O tenant acompanha tudo pela tela de reservas.

>>> TRAVA DE COMPORTAMENTO DA IA (cravada) <
- A IA NUNCA promete estrutura, vista ou comodidade que NÃO esteja na descrição do quarto.
- A IA NUNCA faz promessa de "experiência única" nem linguagem publicitária inflada.
- A descrição do quarto é o LIMITE do que a IA pode afirmar — o tom é sereno, sem superlativo.
- O total é SEMPRE calculado pelo sistema (diária × noites) — a IA pode somar pra orientar, mas o
  backend é a fonte de verdade do valor.

EVOLUÇÃO ESTRUTURAL — PRIMEIRA SM QUE ESCAPA DO "SLOT DE HORAS":
Todos os perfis anteriores que tinham agenda (restaurant 7.3, dental 7.4, salon 7.5) ancoravam a
reserva/consulta num INSTANTE (timestamptz) e a janela era de horas. A pousada inaugura o modelo de
INTERVALO DE DIAS:
- A reserva tem check_in_date / check_out_date como DATE (não timestamptz — é DIA, não instante).
  Uma diária = uma noite.
- O conflito é overlap de intervalos HALF-OPEN [check_in, check_out) POR QUARTO:
  NOT (existing.check_out <= new.check_in OR existing.check_in >= new.check_out).
  Consequência natural do half-open: o CHECK-OUT de uma reserva e o CHECK-IN de outra NO MESMO DIA
  NÃO conflitam — o quarto rotaciona no mesmo dia.
- nights e total_cents são MATERIALIZADOS no INSERT (nights = check_out − check_in;
  total_cents = nightly_rate_cents × nights). NÃO são colunas geradas (lição timestamptz+interval /
  end_at das SMs anteriores: o cálculo cruza colunas e o resultado tem de ser congelado no INSERT).
- A confirmação de reserva pela IA emite a tag <reserva_pousada> (NAMESPACE PRÓPRIO, distinto do
  <reserva> do RestaurantBot e de todas as outras tags).

DECISÕES CRAVADAS (reais, refletidas no código):
1. Reserva é INTERVALO DE DIAS (check_in/check_out DATE), não slot de horas. Conflito por overlap
   half-open POR QUARTO; rotação no mesmo dia é permitida.
2. nights e total_cents materializados no INSERT (não generated). Snapshots de room_name +
   nightly_rate_cents + capacity_snapshot congelados no momento — mudar preço/capacidade do quarto
   depois NÃO altera reservas passadas.
3. Cliente NÃO é entidade própria (igual salon 7.5): hóspedes são rotativos. O histórico vem do
   contact + pousada_reservations. guest_name / guest_phone são SNAPSHOTS do contato.
4. Validações no service: check_out > check_in (não aceita 0 noites); check_in >= hoje (fuso
   America/Sao_Paulo, HARDCODED); guests_count <= room.capacity.
5. Status hardcoded com 6 estados: reservado → confirmado → checked_in → checked_out, +cancelado/
   no_show. Só confirmado (com quarto/datas/total) e cancelado notificam o hóspede.
6. Tag <reserva_pousada> com namespace exclusivo. O backend valida tudo; falha qualquer → não cria
   (Optional.empty + warn), a mensagem segue ao cliente sem reserva.
7. LGPD: notes é administrativo — sem RG/CPF/documento.

[FUNDAÇÃO — migration 35_pousada.sql]
- ALTER companies: CHECK companies_profile_id_check aceitar 'pousada' PRESERVANDO os anteriores
  ('generic','legal','dental','sushi','restaurant','salon','pousada'). 6º perfil real, 7º contando
  generic.
- RLS enable + force em todas as tabelas; policies do tenant via app.company_id();
  grants authenticated + service_role.
- pousada_rooms — catálogo de quartos:
  (id, company_id refs companies on delete restrict, name CHECK length 1..200, capacity int CHECK
  1..20, nightly_rate_cents int CHECK >= 0, description, active default true, notes, timestamps).
  Índices parciais por (company,active) e (company,capacity) WHERE active. Policies CRUD completas
  (select/insert/update/delete) ao authenticated.
- pousada_config — check-in/check-out + política (1:1 com company):
  (company_id PK refs companies on delete cascade, check_in_time time default '14:00',
  check_out_time time default '11:00', cancellation_policy texto livre nullable, timestamps).
  Ausente → defaults (14:00 / 11:00 / null). Policies select/insert/update.
- pousada_reservations — reservas (intervalo de dias):
  (id, company_id refs companies on delete restrict, room_id refs pousada_rooms on delete restrict,
  conversation_id refs conversations on delete set null, contact_id refs contacts on delete set null,
  guest_name NOT NULL (snapshot), guest_phone nullable (snapshot), guests_count int CHECK >= 1,
  check_in_date date, check_out_date date, nights int CHECK > 0 [materializado no INSERT],
  room_name (snapshot), nightly_rate_cents (snapshot), capacity_snapshot (snapshot do room.capacity),
  total_cents [materializado = nightly_rate_cents × nights], status default 'reservado' CHECK in
  ('reservado','confirmado','checked_in','checked_out','cancelado','no_show'), notes, created_at,
  status_updated_at, constraint pousada_res_dates_check CHECK (check_out_date > check_in_date)).
  INSERT pelo BACKEND (service_role) — IA via handler ou tenant via POST manual; tenant só
  SELECT/UPDATE (status). Índice CRÍTICO do conflito: idx_pousada_res_room_active por
  (room_id, check_in_date) WHERE status in ('reservado','confirmado','checked_in') — só os status
  BLOQUEANTES entram na checagem de overlap.
- Status hardcoded materializado (PousadaReservationStatus.java ↔ pousada-reservation-status.ts,
  PousadaReservationStatusParityTest): reservado → confirmado/cancelado; confirmado →
  checked_in/cancelado/no_show; checked_in → checked_out; checked_out/cancelado/no_show = terminais.
  Transição inválida → 409 invalid_status_transition.
- TODAS as tabelas novas entram na migration 35 ANTES de tocar o banco (lição os_config) e no
  TRUNCATE/SCRIPTS do AbstractIntegrationTest.

[BACKEND]
src/main/java/com/meada/profiles/pousada/
- PousadaProfileGuard — requirePousada → 403 forbidden_wrong_profile para tenant de outro perfil.
  JwtAuthenticationFilter autentica /api/pousada/** (além de /admin/**, sushi/legal/restaurant/salon).
- PousadaReservationStatus — enum hardcoded com 6 estados, allowedNext()/canTransitionTo() e o texto
  fixo de notificação (notificationText() / notificationText(checkIn,checkOut,room,total)). Só
  confirmado (parametrizado com quarto/datas/total) e cancelado têm texto; os demais retornam null
  (silenciosos). Texto defensivo, sem promessa de estrutura.
- rooms/ — PousadaRoom + Repository + Service + Controller. CRUD de quartos. Excluir quarto com
  reserva → bloqueado (on delete restrict) — desativar (active=false) é o caminho.
- config/ — PousadaConfig + Repository + Service + Controller. GET com fallback aos defaults
  (14:00/11:00/null) + PUT.
- reservations/ — PousadaReservation + Conflict + Repository + Service + Notifier + Controller +
  ReservaPousadaConfirmHandler:
  * PousadaReservationService.create: valida quarto (existe + ativo → RoomNotFoundException /
    InactiveRoomException), datas (check_out > check_in; check_in >= hoje BRT → InvalidDatesException),
    capacidade (1 <= guests_count <= room.capacity → OverCapacityException), computa nights =
    ChronoUnit.DAYS.between(checkIn, checkOut), delega ao repo (que RE-VERIFICA o conflito por quarto
    na transação → DatesConflictException → ConflictException → 409 conflict_dates). Snapshots
    (room_name/nightly_rate/capacity) vêm do room. Status inicial = reservado. Invalida o
    PousadaContextCache ao criar.
  * updateStatus: valida a transição (decisão 5), grava, notifica via PousadaReservationNotifier
    com quarto/datas/total formatados, invalida o cache.
  * ReservaPousadaConfirmHandler: extrai a tag via regex
    Pattern("<reserva_pousada>\\s*(\\{.*?\\})\\s*</reserva_pousada>", DOTALL). Parseia JSON
    {room_id, check_in:"YYYY-MM-DD", check_out:"YYYY-MM-DD", guests_count:N, guest_name?, notes?}.
    guest_name vem do JSON, ou do contact.name, ou do telefone, ou "Hóspede". JSON inválido / campos
    faltando / room_id ou datas malformados / quarto inválido / over-capacity / conflito →
    Optional.empty() + warn (a mensagem segue sem reserva). stripReservaPousadaTag remove a tag antes
    de enviar ao cliente.
  * OutboundService ganha maybeProcessPousadaReservation (best-effort, encadeado após os perfis
    anteriores — perfil é único, só um age; REMOVE a tag antes de enviar).
- PousadaContextCache — bloco de contexto dinâmico injetado no prompt. TTL 30s, keyed por
  (companyId, contactId). Conteúdo: quartos ativos (nome/capacidade/diária/descrição) + política
  (check-in/out + cancelamento) + histórico do contato (últimas 3) + DISPONIBILIDADE por quarto nos
  PRÓXIMOS 30 DIAS (intervalos LIVRES entre as reservas ativas, varrendo os "buracos") + instruções
  de reserva + formato da tag. Fuso America/Sao_Paulo. Invalidação explícita (invalidate(companyId))
  ao mutar quarto/config/reserva.

[FRONTEND]
- Sidebar: getNavForProfile('pousada') injeta o grupo "Pousada" (Quartos / Reservas / Configurações).
- Telas:
  * /dashboard/rooms — CRUD de quartos (nome, capacidade 1–20, diária R$, descrição, ativo/inativo).
    Excluir bloqueado se houver reservas; descrição é o limite do que a IA promete.
  * /dashboard/pousada-reservations — lista por mês com filtro por status e por quarto; nova reserva
    manual (escolhe quarto, check-in/check-out, hóspedes validado contra capacidade, total calculado
    na hora; conflito mostra quem ocupa e de que data a que data); detalhe + mudança de status
    (reservado → confirmado → check-in → check-out; ou cancelado) com notificação ao confirmar/cancelar.
  * /dashboard/pousada-settings — horário de check-in/check-out + política de cancelamento (texto
    livre).
- frontend/profiles/pousada/pousada-reservation-status.ts (espelho do enum Java) +
  pousada-types.ts. frontend/lib/api/pousada/{rooms,reservations,config}.ts.
- npm build limpo.

[DOCS]
- CLAUDE.md: seção "## Perfil Pousada (PousadaBot, camada 7.6)" — modelo análogo aos outros perfis,
  a EVOLUÇÃO ESTRUTURAL (intervalo de dias / overlap half-open), as decisões cravadas, o NÃO TEM.
- docs/PERFIL_POUSADA.md: guia operacional do tenant (quartos, configurações, reservas + status,
  como a IA atende, LGPD, limitações conhecidas).
- NÃO mexer em system-template.txt nem em outros perfis.

[TESTES BACKEND]
src/test/java/com/meada/profiles/pousada/
- PousadaReservationStatusParityTest — garante paridade Java↔TS dos 6 estados e transições.
- ProfileTypeParityTest (global) — pousada presente no enum/const.
- rooms/PousadaRoomServiceTest + PousadaRoomControllerIntegrationTest.
- config/PousadaConfigServiceTest + PousadaConfigControllerIntegrationTest (GET fallback + PUT).
- reservations/PousadaReservationServiceTest — create (quarto inválido/inativo, datas inválidas,
  check_in no passado, over-capacity, conflito por quarto, rotação no mesmo dia OK, nights/total
  materializados, snapshots) + updateStatus (transições válidas/inválidas, notificações).
- reservations/PousadaReservationControllerIntegrationTest — endpoints + guard 403 wrongProfile +
  409 conflict_dates + 409 invalid_status_transition.
- reservations/ReservaPousadaConfirmHandlerTest — tag válida cria; JSON inválido/campos faltando/
  room inválido/conflito → empty; strip da tag.
mvn final = contagem REAL do Surefire (gate empírico).

[CONSTRAINTS DUROS]
- Migration única (35). Sem foto/anexo (bloqueador SERVICE_ROLE_KEY).
- Cliente NÃO é entidade do core — continua o contact (reserva tem conversation_id/contact_id +
  snapshots guest_name/guest_phone).
- Reserva é INTERVALO DE DIAS (check_in/check_out DATE). Conflito por overlap HALF-OPEN
  [check_in, check_out) POR QUARTO; rotação no mesmo dia permitida.
- nights e total_cents MATERIALIZADOS no INSERT (não generated). Snapshots de room_name/
  nightly_rate/capacity_snapshot.
- Validações: check_out > check_in; check_in >= hoje (fuso America/Sao_Paulo HARDCODED);
  guests_count <= room.capacity.
- Status hardcoded (6 estados, parity test). Só confirmado e cancelado notificam.
- Tag <reserva_pousada> distinta de TODAS as outras (namespace próprio, NÃO <reserva> do restaurant).
- Cache de contexto TTL 30s, keyed por (companyId, contactId), invalidação em toda mutação.
- NÃO mexer em outros perfis nem em system-template.txt. Webhook OFF.
- Tabela nova entra na migration ANTES de tocar o banco (lição os_config); adicionar ao
  TRUNCATE/SCRIPTS do AbstractIntegrationTest.
- git add EXPLÍCITO (nunca git add .); .env/CONTEXT.md/secrets NUNCA staged.

[PASSO FINAL — resumido]
- Tenant pousada (profile=pousada), padrão GoTrue; Caddy + /etc/hosts pra pousada.meadadigital.local.
- Seed com `at time zone 'America/Sao_Paulo'`, IDs de namespace com sufixo novo (não comitar).
- JwtFilter autentica /api/pousada/**. OutboundService com maybeProcessPousadaReservation.
- git add explícito + sanity (sem .env/secrets) + commit feat(camada-7.6) + tag fase-7.6-fechada +
  push origin main + tags.
- docker compose restart backend → /admin/me → 401.
- Smoke E2E: auth (profileId=pousada); quartos/config + guard 403; reserva via <reserva_pousada>
  (intervalo de dias, nights/total materializados); conflito por quarto + rotação no mesmo dia OK;
  status (confirmado/cancelado notificam) + transição inválida 409; regressão dos perfis anteriores;
  paridade verde.

[REPORTAR]
- "ProfileType.POUSADA adicionado (camada 7.6) — 6º perfil real"
- "Paridade PousadaReservationStatus e ProfileType validadas"
- "EVOLUÇÃO ESTRUTURAL: reserva é INTERVALO DE DIAS (check_in/check_out DATE), conflito por overlap
  half-open [check_in, check_out) por quarto; rotação no mesmo dia OK"
- "nights e total_cents materializados no INSERT (não generated); snapshots room_name/nightly_rate/
  capacity"
- "Validações: check_out>check_in, check_in>=hoje (BRT), guests<=capacity"
- "Tag <reserva_pousada> distinta de <reserva> (restaurant) e de todas as outras"
- "PousadaContextCache TTL 30s com disponibilidade por quarto nos próximos 30 dias"
- "Cliente NÃO é entidade — contact + snapshots guest_name/phone"
- "TRAVA: IA nunca promete estrutura/vista não cadastrada, sem 'experiência única'"
- "tabelas criadas DENTRO da migration (lição os_config)"
- "Próximas fases: tarifa sazonal, pagamento/sinal/Pix, foto do quarto, Booking/Airbnb, fidelidade,
  café/serviços extras, scheduler de auto-transição"
