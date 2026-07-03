>>> JÁ IMPLEMENTADO — perfil barbearia, camada 8.1, migration 43_barbearia.sql. Prompt de nicho
>>> RETROATIVO, formato T5. Fonte: CLAUDE.md seção Perfil Barbearia + migration 43 +
>>> docs/PERFIL_BARBEARIA.md.

[TAREFA — PERFIL BARBEARIA / BarbeariaBot (camada 8.1) — RETROATIVO]

[CONTEXTO]
PROJETO MEADA em /home/igorhaf/meada.
Barbearia é o 11º perfil vertical real (sushi 7.1, legal 7.2, restaurant 7.3, dental 7.4, salon 7.5,
pousada 7.6, academia 7.7, pet 7.8, oficina 7.9, nutri 8.0, barbearia 8.1) — 12º contando generic.
O tenant barbearia (`profile_id='barbearia'`) vira um produto de BARBEARIA / barber shop dentro do
mesmo dashboard Meada. O tenant acessa `barbearia.meadadigital.local` e vê o produto "Barbearia":
gerencia barbeiros e serviços, marca horários na agenda, E gerencia uma FILA DE WALK-IN (por ordem
de chegada). A IA atende clientes via WhatsApp com tom descontraído-acolhedor e oferece DOIS
caminhos: MARCAR horário com um barbeiro, ou ENTRAR NA FILA quando o cliente quer ser atendido
"assim que der".

>>> TRAVA DE COMPORTAMENTO DA IA (cravada) <
- A IA NUNCA recomenda serviço que o cliente não pediu (sem upsell agressivo).
- A IA NUNCA opina sobre a aparência/estilo do cliente nem promete resultado de corte.
- A FILA é SEMPRE ESTIMATIVA: a IA apresenta posição e tempo de espera como "aproximadamente";
  NUNCA promete tempo exato nem "você é o próximo garantido" (desistências e horários marcados
  mexem a fila).
- A IA NÃO CHAMA o cliente. Quem chama é o BARBEIRO no balcão, via painel (ação humana). A IA só
  ENFILEIRA e INFORMA a posição/espera estimadas — espelho do "cancelamento bloqueado por IA" do
  dental.
- A IA NÃO move ticket de status nem confirma horário que conflita (o sistema reforça com erro).

EVOLUÇÃO ESTRUTURAL:
- CLONA O CHASSI DO SALON (camada 7.5): agenda com conflito POR barbeiro (`barber_id`), duração POR
  serviço, snapshots (barber_name/service_name/price_cents/duration_minutes), `end_at` materializado
  no INSERT (NÃO coluna gerada — `timestamptz + interval` não é IMMUTABLE, lição da SM-D/E). Cliente
  NÃO é entidade própria (continua o contact; snapshots guest_name/guest_phone) — alta rotatividade,
  igual salon/pousada. Conflito por barbeiro: 2 clientes no mesmo horário com barbeiros DIFERENTES é
  OK (paralelismo).
- ESCAPADA NOVA — FILA DE WALK-IN COM POSIÇÃO DERIVADA (`barber_queue_tickets`): PRIMEIRO perfil com
  ORDEM RELATIVA sem âncora temporal absoluta (todos os anteriores ancoravam em coordenada absoluta:
  slot pontual, intervalo de dias, assinatura, order). A POSIÇÃO **não é coluna persistida** — é
  DERIVADA por query (`countAhead*`: count de tickets 'aguardando' com `enqueued_at` menor no mesmo
  escopo, + 1). Atender/desistir de quem está à frente RECOMPUTA todas as posições sem nenhum UPDATE
  de reordenação. `enqueued_at` é a ÂNCORA DE ORDEM; SEM coluna `position`.
  * Regra de escopo do "qualquer barbeiro" (cravada): um ticket GERAL (`barber_id` null) concorre
    com TODOS os 'aguardando' à frente (gerais E de barbeiro específico) — `countAheadGeneral`. Um
    ticket de barbeiro X concorre com os 'aguardando' de X + os GERAIS enfileirados antes dele (um
    geral à frente pode acabar pegando aquele barbeiro) — `countAheadForBarber` (WHERE
    `barber_id = ? or barber_id is null`).
  * ETA estimado = soma das durações (snapshot) à frente no mesmo escopo (`sumDurationAhead`);
    INFORMATIVO. A IA apresenta SEMPRE como estimativa explícita ("aproximadamente").

DECISÕES CRAVADAS (reais):
1. CLONA o chassi do SALON (agenda por barbeiro). MANTER 1:1 onde não conflita.
2. ESCAPADA: fila de walk-in com posição DERIVADA (`barber_queue_tickets`), sem coluna `position`;
   `enqueued_at` é a âncora; escopo geral vs barbeiro específico; ETA = soma das durações à frente.
3. DUAS máquinas de status hardcoded, cada uma com parity test Java↔TS (appointments + queue).
4. DUAS tags distintas, namespace próprio: `<agendamento_barbearia>` (marca horário) e
   `<fila_barbearia>` (enfileira; `barber_id` opcional, null = qualquer barbeiro).
5. A IA NÃO move ticket de status — `aguardando→chamado` é AÇÃO HUMANA no painel e dispara a
   notificação crítica "chegou sua vez". NÃO há callNext automático nesta SM.
6. FK do ticket → barber é `on delete set null` (null = qualquer barbeiro); por isso o delete de
   barbeiro checa tickets EXPLICITAMENTE no service (não só a FK restrict do appointment) → 409
   barber_in_use.
7. Cache de contexto da IA TTL 10s — A MAIS CURTA do projeto (abaixo dos 15s do restaurant), porque
   a fila muda a cada cliente.

NÃO TEM nesta SM (registrado pra não inventar): callNext automático (converter ticket em
atendimento materializando start_at=now() e re-checando a agenda); scheduler de timeout/expiração
da fila por tempo; lembrete "está chegando sua vez"; painel de TV / display público / check-in por
QR; pagamento/comanda/gorjeta (Stripe é #50); assinatura de cortes recorrentes (academia cobre
recorrência); foto do corte / galeria (bloqueador SERVICE_ROLE_KEY); barbeiro com múltiplas
cadeiras paralelas (um barbeiro = um atendimento por vez). Fases futuras.

[FUNDAÇÃO — migration 43_barbearia.sql]
- ALTER companies CHECK acrescentando 'barbearia' (PRESERVANDO os 11 perfis anteriores).
- RLS enable + force em todas as tabelas; policies do tenant via `app.company_id()`; grants
  authenticated + service_role. INSERT de appointments/tickets vem do BACKEND (service_role) — IA
  (handlers) ou tenant (POST manual). Tenant só SELECT/UPDATE em appointments/tickets.
- Tabelas:
  * barber_barbers — barbeiros do tenant (catálogo, espelho salon_professionals). name (1..200),
    specialty (texto livre opcional), active, notes, timestamps. on delete restrict no company.
  * barber_services — catálogo de serviços (espelho salon_offerings). name (1..200), category
    (texto livre), duration_minutes (5..480), price_cents nullable (pode não expor preço), active,
    description, timestamps. A duração entra como SNAPSHOT no agendamento/ticket.
  * barber_config — 1:1 com company (espelho salon_config + 2 campos). opens_at (default 09:00),
    closes_at (default 20:00), slot_minutes (default 15, > 0 — granularidade fina do salão de corte)
    e queue_enabled (default true — interruptor da fila de walk-in). Ausente → defaults.
  * barber_appointments — agendamentos (clone 1:1 de salon_appointments). Conflito POR barber_id.
    company_id/barber_id/service_id (on delete restrict); conversation_id/contact_id (set null);
    guest_name (snapshot) + guest_phone; start_at; duration_minutes (snapshot); end_at MATERIALIZADO
    no INSERT (start_at + duration); service_name/barber_name/price_cents (snapshots); status default
    'agendado'; notes; status_updated_at. Índice CRÍTICO de conflito: `(barber_id, start_at) where
    status in ('agendado','confirmado')`.
  * barber_queue_tickets — A ENTIDADE NOVA: fila de walk-in com POSIÇÃO DERIVADA. company_id (on
    delete restrict); barber_id (on delete SET NULL — null = "qualquer barbeiro"/fila geral);
    service_id (restrict); conversation_id/contact_id (set null); guest_name (snapshot) +
    guest_phone; service_name + duration_minutes (snapshots, p/ ETA); barber_name (snapshot nullable
    = qualquer barbeiro); status default 'aguardando'; enqueued_at default now() — a ÂNCORA DE ORDEM;
    called_at; notes; status_updated_at. SEM coluna `position`. Índice parcial do cálculo:
    `(company_id, barber_id, enqueued_at) where status = 'aguardando'`.
- DUAS máquinas de status hardcoded, cada uma com parity test Java↔TS:
  * BarberAppointmentStatus (clone salon): `agendado → confirmado, cancelado`; `confirmado →
    realizado, cancelado, falta`; realizado/cancelado/falta terminais. Só **confirmado** (com
    data/hora/barbeiro) e **cancelado** notificam o cliente.
  * BarberQueueStatus (NOVA): `aguardando → chamado, desistiu, expirado`; `chamado → atendido,
    desistiu`; atendido/desistiu/expirado terminais. Só **chamado** notifica ("Chegou a sua vez!
    Procure o barbeiro Fulano." — a notificação CRÍTICA do walk-in, parametrizada com o nome do
    barbeiro; null = "qualquer barbeiro"). aguardando/atendido/desistiu/expirado silenciosos.
  Transição inválida → 409 invalid_status_transition.
- TODAS as tabelas novas entram na migration 43 ANTES de tocar o banco (lição os_config) e no
  TRUNCATE/SCRIPTS do AbstractIntegrationTest.

[BACKEND]
(em src/main/java/com/meada/profiles/barbearia/: barbers/ services/ config/ appointments/
queue/ + BarberAppointmentStatus, BarberQueueStatus, BarberContextCache, BarberProfileGuard.)
- Barbeiros / Serviços / Config: CRUD por entidade (Repository + Service + Controller). Config GET
  com fallback de defaults + PUT. Mutação invalida o BarberContextCache.
- Agenda (appointments): conflito POR barber_id, `findConflict` transacional (janela half-open,
  re-verificado DENTRO da transação — defesa de corrida), duração por serviço, snapshots, end_at
  materializado no INSERT. Tag `<agendamento_barbearia>` (clone salon) →
  AgendamentoBarbeariaConfirmHandler: parseia o JSON, resolve o contato da conversa (guest_name =
  contact.name snapshot), cria o agendamento; OutboundService REMOVE a tag antes de enviar.
- Fila (queue): tag `<fila_barbearia>` → EntrarFilaHandler. `barber_id` OPCIONAL (ausente/null/"null"
  = qualquer barbeiro / fila geral). O service lê a duração do serviço (snapshot), valida
  serviço/barbeiro (ativos) e que a fila está ligada (queue_enabled); se desligada é no-op silencioso
  (empty). Qualquer falha → Optional.empty() + warn (a mensagem da IA segue sem efeito colateral). O
  BarberQueueRepository deriva a posição/ETA (countAheadGeneral / countAheadForBarber /
  sumDurationAhead) — NÃO há coluna position. updateStatus grava called_at = now() quando vira
  'chamado'.
- A IA NÃO move ticket de status: a transição `aguardando→chamado` é AÇÃO HUMANA via
  BarberQueueController (painel) e dispara a notificação "chegou sua vez". NÃO existe callNext
  automático.
- BarberContextCache (TTL 10s — A MAIS CURTA do projeto, porque a fila muda a cada cliente), keyed
  por (companyId, contactId), invalidado por company em toda mutação (barbeiro/serviço/agendamento/
  fila/config). Conteúdo injetado no prompt: serviços ativos (duração/preço) + barbeiros ativos +
  TAMANHO ATUAL DA FILA por barbeiro e fila geral + slots livres por barbeiro (próximos 3 dias) +
  histórico do contato (se identificado) + instruções das DUAS tags. Fuso America/Sao_Paulo
  (hardcoded — pendência).
- Guard: BarberProfileGuard (403 forbidden_wrong_profile para tenant de outro perfil).
  JwtAuthenticationFilter autentica /api/barbearia/** (além dos 10 perfis anteriores).
- OutboundService ganha maybeProcessAgendamentoBarbearia + maybeProcessFilaBarbearia (best-effort,
  encadeados após os outros perfis — perfil é único, só um age).

[FRONTEND]
(em frontend/profiles/barbearia/: barber-appointment-status.ts, barber-queue-status.ts,
barber-types.ts.)
- getNavForProfile('barbearia') injeta "Barbearia": Barbeiros, Serviços, Agenda, Fila,
  Configurações. Subdomínio barbearia.meadadigital.local. Paleta `grafite` (grafite/âmbar).
- /dashboard/barber-barbers — CRUD de barbeiros (especialidade texto livre; inativo some da
  disponibilidade; delete bloqueado se há agendamento/ticket → desativar).
- /dashboard/barber-services — CRUD de serviços (nome, duração própria em min, preço opcional;
  duração entra como snapshot).
- /dashboard/barber-appointments — agenda agrupada por dia, filtro de status e de barbeiro; criação
  manual; botões de transição seguindo as regras; conflito POR barbeiro.
- /dashboard/barber-queue — a fila de walk-in: mostra a POSIÇÃO DERIVADA (calculada na hora) + a
  ESPERA ESTIMADA (ETA) e o botão "Chamar" no 1º da fila daquele barbeiro (→ ticket 'chamado' +
  notifica o cliente); depois "Atendido" / "Desistiu".
- /dashboard/barber-settings — horário de funcionamento, granularidade dos slots (slot_minutes) e o
  interruptor da fila de walk-in (queue_enabled).
- Os 2 status TS (barber-appointment-status.ts + barber-queue-status.ts) espelham os enums Java; os 2
  parity tests garantem a paridade. npm build limpo.

[DOCS]
- CLAUDE.md: seção "## Perfil Barbearia (BarbeariaBot, camada 8.1)" documentando: clona o chassi do
  salon; a ESCAPADA da fila com posição DERIVADA (countAhead, enqueued_at âncora, escopo geral vs
  específico, ETA informativo); as 2 máquinas de status; as 2 tags; a IA não chama (ação humana); o
  cache TTL 10s (a mais curta); o NÃO TEM.
- docs/PERFIL_BARBEARIA.md: guia operacional do tenant (telas Barbeiros/Serviços/Agenda/Fila/
  Configurações; como a FILA funciona — posição recalculada, escopo do "qualquer barbeiro", ETA
  estimado, "Chamar"; status das duas máquinas; "o que a IA faz e o que NÃO faz"; limitações
  conhecidas).
- NÃO mexer em system-template.txt nem em outros perfis.

[TESTES BACKEND]
- BarberAppointmentStatusParityTest + BarberQueueStatusParityTest (as 2 máquinas de status,
  paridade Java↔TS) + ProfileTypeParityTest (com BARBEARIA).
- BarberBarberServiceTest + BarberBarberControllerIntegrationTest (CRUD; delete-em-uso →
  barber_in_use; wrongProfile 403).
- BarberServiceServiceTest + BarberServiceControllerIntegrationTest.
- BarberAppointmentServiceTest + BarberAppointmentControllerIntegrationTest (conflito POR barbeiro;
  barbeiros diferentes no mesmo horário NÃO conflitam; transições; 409 invalid_status_transition).
- BarberQueueServiceTest + BarberQueueControllerIntegrationTest [CHAVE da escapada]: posição derivada
  (countAhead); escopo geral vs barbeiro específico (geral à frente conta contra o específico); ETA =
  soma das durações à frente; atender/desistir RECOMPUTA posições sem UPDATE de reordenação;
  aguardando→chamado é ação humana e notifica; fila desligada = no-op; transição inválida → 409.
- mvn final = relatar a contagem REAL do Surefire (`Tests run: N`), nunca grep @Test.

[CONSTRAINTS DUROS]
- Migration única (43). Sem foto/anexo (bloqueador SERVICE_ROLE_KEY).
- Cliente NÃO é entidade do core — continua o contact (appointment/ticket têm
  conversation_id/contact_id + snapshots guest_name/guest_phone).
- ESCAPADA: posição da fila DERIVADA por query (countAhead), SEM coluna position; enqueued_at é a
  âncora; escopo geral vs barbeiro específico (geral à frente conta contra o específico); ETA = soma
  das durações à frente (informativo, sempre "aproximadamente").
- end_at materializado no INSERT (NÃO generated — timestamptz+interval não é IMMUTABLE).
- Conflito de agenda POR barber_id, re-verificado DENTRO da transação. Barbeiros diferentes no mesmo
  horário NÃO conflitam.
- DUAS máquinas de status hardcoded (parity Java↔TS cada). DUAS tags distintas de TODAS as outras.
- A IA NÃO chama o cliente nem move ticket de status (aguardando→chamado é ação humana no painel,
  notifica "chegou sua vez"). NÃO há callNext automático.
- FK ticket→barber on delete set null; delete de barbeiro checa tickets explicitamente → 409
  barber_in_use.
- Cache de contexto TTL 10s + invalidação em toda mutação.
- NÃO mexer em outros perfis nem em system-template.txt. Webhook OFF.
- 529 → inline. Gate 3× → pausar. Working tree sujo → pausar. git add EXPLÍCITO (nunca git add .);
  .env/CONTEXT.md/secrets NUNCA staged. SEED com timestamptz: `at time zone 'America/Sao_Paulo'`;
  IDs de namespace com sufixo NOVO. Tabela nova entra na migration ANTES de tocar o banco (lição
  os_config) + no TRUNCATE/SCRIPTS do AbstractIntegrationTest.

[PASSO FINAL — resumido]
- ProfileType.BARBEARIA (enum Java) + const TS adicionados, preservando os 10 anteriores; CHECK
  companies.profile_id acrescenta 'barbearia'; ProfileTypeParityTest verde.
- TENANT barbearia (padrão GoTrue, senha só em comunicação direta) + Caddy/etc/hosts pra
  barbearia.meadadigital.local; seed (NÃO comitar; `at time zone 'America/Sao_Paulo'`; ids sufixo
  novo): config + barbeiros + serviços + alguns agendamentos e tickets de fila cobrindo a escapada
  (geral vs específico, posição/ETA, chamar).
- git add EXPLÍCITO dos arquivos da SM + sanity (sem .env/secrets/CONTEXT) + commit semântico
  (feat(camada-8.1): perfil barbearia/BarbeariaBot) + Co-Authored-By: Claude Opus 4.8. Tag
  fase-8.1-fechada. Push origin main + tags.
- Smoke E2E: auth → /admin/me (profileId=barbearia); guard (outro perfil → /api/barbearia/** = 403);
  agendamento via tag; fila via tag (geral e específica) provando a posição/ETA derivados;
  aguardando→chamado pelo painel notifica; transição inválida → 409. mvn final = contagem REAL.

[REPORTAR]
Igual SMs anteriores. Incluir EXPLICITAMENTE:
- "ProfileType.BARBEARIA adicionado (camada 8.1)"
- "Paridade BarberAppointmentStatus, BarberQueueStatus e ProfileType validadas"
- "CLONA o chassi do SALON (agenda por barbeiro, conflito por barber_id)"
- "ESCAPADA: fila de walk-in com POSIÇÃO DERIVADA (barber_queue_tickets) — sem coluna position,
  enqueued_at é a âncora, posição por countAhead, escopo geral vs barbeiro específico, ETA = soma das
  durações à frente (informativo)"
- "DUAS máquinas de status (BarberAppointmentStatus + BarberQueueStatus: aguardando→chamado→
  atendido/desistiu/expirado)"
- "DUAS tags distintas: <agendamento_barbearia> + <fila_barbearia> (barber_id opcional = qualquer
  barbeiro)"
- "A IA NÃO chama o cliente nem move ticket de status (aguardando→chamado é ação humana no painel;
  notifica 'chegou sua vez'); NÃO há callNext automático"
- "BarberContextCache TTL 10s — A MAIS CURTA do projeto"
- "FK ticket→barber on delete set null; delete de barbeiro → 409 barber_in_use"
- "OutboundService ganhou maybeProcessAgendamentoBarbearia + maybeProcessFilaBarbearia"
- "getNavForProfile('barbearia') com branch próprio (Barbeiros/Serviços/Agenda/Fila/Configurações),
  paleta grafite"
- "tabelas criadas DENTRO da migration 43 (lição os_config); seed com at time zone + sufixo novo"
- "Próximas fases: callNext automático, expiração/lembrete da fila, painel de TV/QR, pagamento/
  comanda, assinatura de cortes, foto/galeria, múltiplas cadeiras paralelas"
