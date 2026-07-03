>>> JÁ IMPLEMENTADO — perfil academia, camada 7.7, migration 36_academia.sql. Prompt de nicho
>>> RETROATIVO, formato T5. Fonte: CLAUDE.md seção Perfil Academia + migration 36 +
>>> docs/PERFIL_ACADEMIA.md.

[TAREFA — PERFIL ACADEMIA / AcademiaBot (camada 7.7) — RETROATIVO]

Documentação retroativa, no formato T5, do perfil vertical ACADEMIA já implementado e fechado
(camada 7.7, sétimo perfil real). Este arquivo descreve o que EXISTE no código, na migration e nos
testes — não é um pedido de implementação. Reconstruído a partir das fontes reais (CLAUDE.md seção
"Perfil Academia", migration 36_academia.sql, docs/PERFIL_ACADEMIA.md, código em
src/main/java/com/meada/profiles/academia/).

[CONTEXTO]
PROJETO MEADA em /home/igorhaf/meada.
O tenant academia (`profile_id='academia'`) vira um produto de ACADEMIA / STUDIO de fitness dentro
do mesmo dashboard Meada. O tenant gerencia planos mensais e aulas semanais recorrentes, matricula
alunos (assinatura), registra pagamentos manuais; a IA atende clientes via WhatsApp oferecendo
planos + aulas com vaga, e matricula. Tom acolhedor-motivador. Sétimo perfil real (8º contando
generic).

>>> TRAVA DE COMPORTAMENTO DA IA (cravada) <
- A IA NUNCA prescreve treino, dieta ou avaliação física — não é educadora física; se o cliente
  pedir, recusa com gentileza e explica que isso é com o professor presencialmente.
- A IA NUNCA julga (corpo, condicionamento, peso) e NUNCA promete resultado corporal.
- A IA conhece os planos ativos, as aulas com VAGA em tempo real e se o cliente já tem matrícula —
  nesse caso NÃO oferece outra (anti-dupla matrícula também na IA).
- A IA confirma plano + aulas antes de matricular; o backend revalida tudo (capacidade, dupla
  matrícula, plano/aula ativos) e descarta qualquer chute da IA.
- LGPD: `notes` é administrativo; a IA não registra dado de saúde do aluno.

>>> EVOLUÇÃO ESTRUTURAL — primeira SM com RECORRÊNCIA INDEFINIDA <
A matrícula é uma ASSINATURA (status ativa-até-cancelar), NÃO um evento pontual (slot, como
dental/restaurant/salon) nem um intervalo finito (pousada). É a escapada estrutural mais profunda
até o ponto: uma matrícula ocupa N vagas em N aulas semanais recorrentes, via junction
(`academia_membership_classes`). Consequências cravadas:
- O conflito NÃO é overlap temporal — é por CAPACITY POR AULA: `capacity - count(matrículas não
  canceladas naquela aula) > 0`, validado TRANSACIONALMENTE no INSERT da matrícula (fecha a janela
  de corrida entre o cache da IA e a persistência).
- ANTI-DUPLA MATRÍCULA: índice parcial UNIQUE `uniq_active_membership_per_contact` (company,
  contact) WHERE status='ativa' AND contact_id is not null — impede 2 matrículas ativas para o
  mesmo contato. O service também valida (`findActiveByContact` → 409 `already_active`).
- PAGAMENTO MANUAL (`academia_payments`): registro mês a mês, UNIQUE (membership, reference_month)
  → 409 `duplicate_payment`. SEM cobrança automática (Stripe é #50, fase futura).
- Tag `<matricula>` (namespace exclusivo) carrega plan_id + class_ids[]; o backend cria a matrícula
  + as linhas da junction com snapshots.

DECISÕES CRAVADAS (reais, do que foi implementado):
1. SUSPENSA MANTÉM A VAGA (decisão cravada): pausa curta; pra liberar a vaga, cancelar. O count de
   vaga filtra por `status <> 'cancelada'` — só CANCELADA libera vaga.
2. Transições de status: `ativa ⇄ suspensa`; ambas → `cancelada` (terminal). Transição inválida →
   409 `invalid_status_transition`.
3. CANCELADA materializa `end_date` (data de fim) e libera as vagas.
4. Notificam: **ativa** (boas-vindas, com o plano) e **cancelada** (despedida); **suspensa** é
   silenciosa. Texto defensivo, SEM promessa de resultado corporal.
5. Aluno NÃO é entidade própria (igual salon/pousada — alta rotatividade): histórico vem do
   contact + memberships. `student_name`/`student_phone` são snapshots.
6. Snapshots: a matrícula congela `plan_name` + `plan_monthly_cents` + `student_name`/`phone`; a
   junction congela `class_name` + `day_of_week` + `start_time` + `duration` + `modality`. Mudar
   plano/aula depois NÃO altera matrículas existentes.

[FUNDAÇÃO — migration 36_academia.sql]
- ALTER companies CHECK aceitar 'academia' (preservando os perfis anteriores:
  generic/legal/dental/sushi/restaurant/salon/pousada + academia).
- RLS enable+force em TODAS as tabelas, policies via `app.company_id()`, grants `authenticated` +
  `service_role`. `academia_memberships`: INSERT só pelo BACKEND (service_role) via o handler da
  tag; o tenant tem SELECT/UPDATE (status no painel / pagamentos). A junction só tem SELECT pra
  `authenticated` (via EXISTS na matrícula do tenant); INSERT só service_role. `academia_payments`
  só SELECT pra authenticated; mutação via service_role.
- `day_of_week`: 0=domingo .. 6=sábado.
- Tabelas:
  * `academia_plans` — planos mensais. (id, company_id, name CHECK 1..200, monthly_cents >= 0,
    description, active default true, timestamps). Entra como SNAPSHOT na matrícula.
  * `academia_classes` — aulas semanais recorrentes. (id, company_id, name, modality CHECK 1..100,
    day_of_week 0..6, start_time, duration_minutes 15..240, capacity 1..100, instructor opc,
    active default true). `capacity` = máx. de matrículas ativas. Entra como SNAPSHOT na junction.
  * `academia_config` — horário de funcionamento, 1:1 com company (PK = company_id). opens_at
    default '06:00', closes_at default '22:00'. Ausente → defaults.
  * `academia_memberships` — matrículas (assinaturas). plan_id (restrict), conversation_id/
    contact_id (set null), snapshots student_name/student_phone/plan_name/plan_monthly_cents,
    start_date default current_date, end_date (materializado só em cancelada), status CHECK
    ('ativa'|'suspensa'|'cancelada') default 'ativa', notes, status_updated_at. Índice parcial
    UNIQUE anti-dupla por contato ativo.
  * `academia_membership_classes` — JUNCTION matrícula↔aula com snapshot. PK (membership_id,
    class_id). membership_id (cascade), class_id (restrict), snapshots
    class_name/day_of_week/start_time/duration_minutes/modality. A vaga é contada por class_id
    sobre matrículas ATIVAS.
  * `academia_payments` — pagamentos manuais mensais. membership_id (restrict), reference_month
    (dia 01 do mês), paid_at, amount_cents >= 0, method (texto: dinheiro/Pix/transferência), notes.
    UNIQUE (membership_id, reference_month) impede duplicidade no mês.
- Status da matrícula hardcoded (`AcademiaMembershipStatus.java` enum ↔
  `frontend/profiles/academia/academia-membership-status.ts` const, garantido pelo
  `AcademiaMembershipStatusParityTest`): ativa ⇄ suspensa; ambas → cancelada (terminal).
- end_date materializado no cancelamento (não coluna gerada).

[BACKEND]
- Planos: CRUD (`AcademiaPlanService`/Controller). Excluir bloqueado se houver matrícula → 409
  (desative o plano). Ativo/inativo controla a oferta da IA.
- Aulas: CRUD (`AcademiaClassService`/Controller). Lista mostra vagas ocupadas/capacidade por
  aula. Excluir bloqueado se houver matrícula. Ativo/inativo controla a oferta.
- Config: GET (fallback defaults 06:00/22:00) + PUT.
- Matrículas (`AcademiaMembershipService` + `MatriculaConfirmHandler`):
  * Criadas pelo BACKEND (service_role) — não pelo SDK do tenant.
  * Tag `<matricula>{"plan_id":"UUID","class_ids":["UUID","UUID"],"student_name":"...",
    "notes":"..."}</matricula>` (namespace exclusivo) → `MatriculaConfirmHandler` parseia via regex,
    resolve o contato da conversa, valida plano/aulas ativos, capacidade por aula e dupla
    matrícula, e cria a matrícula + as linhas da junction com snapshots, tudo na MESMA transação.
    Falhas: aula lotada, contato já tem matrícula ativa (`already_active`), plano/aula inválido ou
    inativo → Optional.empty() + warn (a matrícula não é criada; sem parcial).
  * Status: PATCH com validação de transição (inválida → 409 `invalid_status_transition`).
    CANCELADA materializa end_date e libera vagas; SUSPENSA mantém a vaga. Notificação outbound por
    status (ativa/cancelada notificam; suspensa silenciosa).
  * OutboundService ganhou `maybeProcessMatricula` (best-effort, encadeado após os outros perfis;
    perfil é único, só um age; a tag é REMOVIDA antes de enviar a mensagem ao cliente).
- Pagamentos (`AcademiaPaymentService`): registro mensal manual. UNIQUE (membership,
  reference_month) → 409 `duplicate_payment`. Só em matrícula ativa. Summary = último mês pago +
  meses em aberto (meses decorridos desde start_date − pagamentos). SEM cobrança automática nem
  cálculo de inadimplência.
- IA:
  * Persona ACADEMIA (acolhedor-motivador) com a TRAVA embutida (não prescreve treino/dieta/
    avaliação física, não julga, sem promessa de resultado corporal).
  * Contexto injetado via `AcademiaContextCache` (Caffeine TTL 60s — aulas semanais mudam pouco,
    vagas mudam lento; keyed por (companyId, ...)): planos ativos (com plan_id exato), aulas ativas
    com VAGAS RESTANTES em tempo real, matrícula atual do contato (anti-dupla na IA: "NÃO ofereça
    nova matrícula — ele já está matriculado"), e o formato da tag `<matricula>`. Invalidação
    explícita em toda mutação de plano/aula/matrícula.
  * JwtAuthenticationFilter autentica `/api/academia/**` (além dos 6 perfis anteriores).
- Guard: `AcademiaProfileGuard` → endpoints `/api/academia/**` retornam 403
  `forbidden_wrong_profile` para tenant de outro perfil.

[FRONTEND]
- `getNavForProfile('academia')` injeta o grupo "Academia" (Planos / Aulas / Matrículas /
  Configurações).
- Telas:
  * `/dashboard/academia-plans` — CRUD de planos (nome, valor mensal, descrição, ativo/inativo;
    excluir bloqueado se houver matrícula).
  * `/dashboard/academia-classes` — CRUD de aulas (nome, modalidade texto livre, dia da semana,
    hora, duração, capacidade, professor opc; lista mostra ocupadas/capacidade por dia).
  * `/dashboard/academia-memberships` — lista por status com filtros; nova matrícula (escolhe
    plano, marca uma ou mais aulas — lista mostra vagas restantes e DESABILITA aulas lotadas —,
    nome + telefone do aluno); detalhe + transição de status (suspender mantém vaga / cancelar
    libera); aba Pagamentos no detalhe (registrar mês + valor + forma; histórico; último mês pago +
    meses em aberto).
  * `/dashboard/academia-settings` — horário de funcionamento.
- Status TS `academia-membership-status.ts` espelhando o enum Java (parity test).
- npm build limpo.

[DOCS]
- CLAUDE.md: seção "## Perfil Academia (AcademiaBot, camada 7.7)" descreve o perfil, a evolução
  estrutural (recorrência indefinida), o modelo, o status, o pagamento manual, a tag, a persona com
  a trava, o guard, o sidebar e o NÃO TEM.
- docs/PERFIL_ACADEMIA.md: guia operacional do tenant (Planos / Aulas / Matrículas + Pagamentos /
  Configurações; como a IA atende; o que a IA NÃO faz; LGPD; limitações honestas).
- system-template.txt e os outros perfis NÃO foram tocados.

[TESTES BACKEND]
- `AcademiaMembershipStatusParityTest` — paridade Java↔TS do status da matrícula.
- `ProfileTypeParityTest` — ProfileType.ACADEMIA presente no enum Java ↔ const TS.
- `AcademiaPlanServiceTest` + `AcademiaPlanControllerIntegrationTest` (CRUD; delete-em-uso; guard).
- `AcademiaClassServiceTest` + `AcademiaClassControllerIntegrationTest` (CRUD; vagas; delete-em-uso).
- `AcademiaConfigServiceTest` + `AcademiaConfigControllerIntegrationTest` (GET fallback + PUT).
- `AcademiaMembershipServiceTest` + `AcademiaMembershipControllerIntegrationTest` (criação;
  capacidade por aula; anti-dupla `already_active`; suspensa mantém vaga; cancelada libera +
  end_date; transições + 409 inválida).
- `AcademiaPaymentServiceTest` + `AcademiaPaymentControllerIntegrationTest` (registro; UNIQUE por
  reference_month → `duplicate_payment`; só em ativa; summary).
- Lição cravada da SM (pool Hikari de teste): com muitos perfis, cada `*ServiceTest` com
  `@Import(TestConfig)` é um ApplicationContext distinto; o pool padrão estourava o teto do pooler
  Supabase. Fix: `src/test/resources/application-dev.yml` com pool minúsculo (min-idle 0/max 2).
- Contagem do Surefire: relatar a REAL (`Tests run: N`), nunca grep textual.

[CONSTRAINTS DUROS]
- Migration única (36). Sem foto/anexo (bloqueador SERVICE_ROLE_KEY).
- RECORRÊNCIA INDEFINIDA: matrícula é assinatura ativa-até-cancelar; ocupa N vagas em N aulas via
  junction; conflito por CAPACITY por aula, transacional.
- SUSPENSA mantém a vaga; só CANCELADA libera (count filtra status <> 'cancelada').
- Anti-dupla matrícula: índice parcial UNIQUE por contato ativo + validação no service
  (`already_active`).
- Pagamento manual: UNIQUE (membership, reference_month) → `duplicate_payment`; só em ativa; SEM
  cobrança automática.
- Snapshots de plano (na matrícula) e de aula (na junction) — alterar o cardápio NÃO altera
  matrículas existentes. end_date materializado no cancelamento (não generated).
- Status hardcoded (parity). Tag `<matricula>` distinta de TODAS as outras.
- Aluno NÃO é entidade do core — continua o contact (matrícula tem conversation_id/contact_id +
  snapshots).
- Cache de contexto TTL 60s + invalidação em toda mutação de plano/aula/matrícula.
- A IA NUNCA prescreve treino/dieta/avaliação física, não julga, sem promessa de resultado.
- NÃO mexer em outros perfis nem em system-template.txt. Webhook OFF.
- NÃO TEM: treino prescrito, ficha de exercícios, avaliação física, balança/wearables, pagamento
  real (Stripe #50), foto, catraca biométrica, fidelidade, multi-unidade, scheduler de
  cobrança/lembrete. Fuso fixo America/Sao_Paulo.

[PASSO FINAL — resumido]
Perfil entregue e fechado: ProfileType.ACADEMIA no enum + const TS (paridade verde), migration 36
aplicada com as 6 tabelas + RLS/grants, backend (CRUD planos/aulas/config + matrículas via
MatriculaConfirmHandler + pagamentos manuais + AcademiaContextCache + AcademiaProfileGuard +
maybeProcessMatricula no OutboundService), frontend (grupo "Academia" com 4 telas + status TS),
docs (CLAUDE.md + PERFIL_ACADEMIA.md). Tenant de teste, seed, commit, push, smoke E2E e tag de
fechamento da sub-fase conforme o padrão das SMs anteriores.

[REPORTAR]
Igual SMs anteriores. Incluir EXPLICITAMENTE:
- "ProfileType.ACADEMIA adicionado (camada 7.7)"
- "Paridade AcademiaMembershipStatus e ProfileType validadas"
- "EVOLUÇÃO ESTRUTURAL: primeira SM com RECORRÊNCIA INDEFINIDA — matrícula é assinatura
  (ativa-até-cancelar), N vagas em N aulas via junction, conflito por capacity por aula transacional"
- "SUSPENSA mantém a vaga; só CANCELADA libera (count filtra status <> 'cancelada')"
- "Anti-dupla matrícula: índice parcial UNIQUE + validação no service (already_active)"
- "Pagamento manual: UNIQUE por reference_month (duplicate_payment); SEM cobrança automática"
- "Tag <matricula> distinta de todas as outras; OutboundService ganhou maybeProcessMatricula"
- "AcademiaContextCache TTL 60s (vagas em tempo real) + invalidação em toda mutação"
- "getNavForProfile('academia') com branch próprio (Planos/Aulas/Matrículas/Configurações)"
- "TRAVA: IA não prescreve treino/dieta/avaliação física, não julga, sem promessa de resultado"
- "Lição do pool Hikari de teste (application-dev.yml min-idle 0/max 2)"
- "Próximas fases: treino prescrito, avaliação física, Stripe (#50), foto, fidelidade, multi-unidade"
