# CLAUDE.md — Meada WhatsApp

Instruções para qualquer instância do Claude trabalhando neste projeto. Leia antes de
agir. Estas regras foram cravadas pelo Igor ao longo das sessões e têm precedência.

> Este arquivo é **território do Claude Code** (como DEVELOPMENT.md e RISKS.md): o agente
> lê, cria seções, atualiza convenções e registra lições sem precisar de autorização por
> edição. NÃO confundir com `.env`/`.env.local` (segredos, território do Igor).

## O que é o projeto

SaaS multi-empresa de atendimento ao cliente via WhatsApp com IA. Cada empresa (tenant)
tem um atendente de IA treinado com seus próprios dados (serviços, horários, FAQs,
preços), respondendo clientes pelo WhatsApp. Dados isolados por tenant via RLS.

- **Backend:** Spring Boot 3.3.13 + Java 17 Temurin, single-module Maven. JdbcTemplate
  (não JPA), sem Lombok, sem webflux. HTTP outbound síncrono via RestClient.
- **Frontend:** Next 16 (app router) + React 19 + TypeScript + Tailwind 4 + shadcn/ui 4
  + @base-ui/react (NÃO Radix) + TanStack Query + react-hook-form + zod + @supabase/ssr.
  Em `frontend/`, isolado do Maven.
- **Banco/Auth:** Supabase (Postgres 17 + Auth + Storage). IA: Gemini Flash.
  WhatsApp: Evolution API self-hosted (evoapicloud/evolution-api:v2.3.1).
- **Detalhes operacionais vivos** (portas, envs, credenciais, estado das camadas):
  ver `CONTEXT.md` na raiz — ele é gitignored e mais detalhado que este arquivo.

## Bootstrap do zero (clone → ambiente rodando)

Pré-requisitos: **Java 17 Temurin** (`/usr/lib/jvm/temurin-17-jdk-amd64`), **Node + npm**,
**Maven 3.8+**, **Docker** (para a Evolution local). Banco/Auth são Supabase remoto (não
sobe local) — é preciso um projeto Supabase com o schema das migrations aplicado.

1. **Backend env:** `cp .env.example .env` e preencher (Supabase datasource via Session
   pooler IPv4, `WEBHOOK_SECRET`, `GEMINI_API_KEY`/`GEMINI_MODEL`, `EVOLUTION_BASE_URL`,
   `SUPABASE_JWKS_URL`, `ADMIN_SUPER_ADMIN_EMAILS`, `EVOLUTION_DRY_RUN=true` em dev,
   `SERVER_PORT=8095`). `.env` é gitignored.
2. **Frontend env:** `cd frontend && cp .env.example .env.local` e preencher
   (`NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_ANON_KEY` — anon é pública por
   design, protegida por RLS; NUNCA service_role aqui —, `NEXT_PUBLIC_API_URL=http://localhost:8095`).
   `.env.local` é gitignored.
3. **Evolution local (opcional, só p/ fluxo WhatsApp):**
   `cd evolution-local && docker compose up -d` (API :8086, Postgres :5433, Redis :6380).
4. **Subir backend:** `./scripts/run-local.sh` (porta 8095). Sanity: `GET /admin/me` sem
   token → 401 `missing_auth_header`.
5. **Subir frontend:** `cd frontend && npm install && npm run dev` (porta 3000).
6. **Provisionar usuários de teste no Supabase Auth** (painel → Authentication → Users,
   "Auto Confirm"): um **super-admin** (email na allowlist `ADMIN_SUPER_ADMIN_EMAILS`,
   SEM linha em `public.users`) e um **tenant-admin** (linha em `public.users` com
   `company_id` + `role`). Senhas vivem só em comunicação direta — nunca em arquivo.
7. **Logar:** `http://localhost:3000/login`. Super-admin → lista global de empresas;
   tenant-admin → dados/serviços/FAQs da própria empresa (isolado por RLS).

## Padrão de trabalho (precedência sobre comportamento default)

- **Decisões em PROSA, nunca em widget.** Não usar o widget de perguntas com abas
  (AskUserQuestion). Igor não interage bem com ele. Apresentar opções como texto,
  com recomendação explícita, e esperar a resposta. (Regra cravada e reforçada.)
- **Proposta em prosa antes de código.** Para qualquer mudança não-trivial: descrever
  a abordagem em prosa, esperar aprovação, só então escrever.
- **Bruto LITERAL antes do Write.** Colar o conteúdo exato do arquivo (em code fence)
  para revisão ANTES de aplicar o Write. Refatoração puramente mecânica pode ser
  aprovada por descrição; lógica nova, não.
- **Write visível após cada aprovação.** Aprovar conteúdo NÃO é o mesmo que arquivo
  escrito — o Write tem que aparecer. (Lição 4.0: um arquivo foi aprovado mas nunca
  escrito; só pego no sanity pré-commit.)
- **Honestidade sobre incertezas.** Não improvisar workaround sem consultar. Não
  fabricar resultados de teste — reportar literal sempre, inclusive falhas.
- **Conferir o estado real antes de "consertar".** Não propor fix de algo sem checar
  no código/banco que o problema existe. (Lição: propus mover um log para WARN que já
  era WARN — trabalho inventado.)
- **Nunca assumir "arquivo vazio" por leitura parcial.** `wc -c`/`cat` antes de decidir
  overwrite vs append.
- **Contagem de testes vem do Surefire (`Tests run: N`), nunca de `grep @Test`.**
  (Lição: grep textual contou 137; o real era 129.)
- **Agente NÃO edita CLAUDE.md por iniciativa própria.** CLAUDE.md é território
  do agente (lê/cria/atualiza convenções E lições) — mas a edição vem por
  trabalho cravado pelo Igor ou pelo arquiteto, NUNCA por dedução do agente
  sobre "regra implícita". Atribuir ao Igor uma regra que ele não cravou é
  violação. Quando em dúvida, perguntar antes de editar.

## Paralelismo dentro de uma sessão

O agente trabalha sozinho na sessão (Igor é o arquiteto único). Aceleração vem de
paralelizar DENTRO da própria rodada, não de abrir sessões extras.

- **Leituras independentes em paralelo numa rodada.** Quando precisa ler N arquivos,
  rodar N queries `psql`, fazer N greps que não dependem um do outro — disparar tudo
  na mesma resposta, em paralelo. Sequencial só quando o passo N depende do resultado
  do passo N-1.
- **Task tool (sub-agentes) pra trabalho disjunto não-trivial.** Quando há fluxos
  que não tocam os mesmos arquivos e cada um exige investigação própria (ex.:
  replicar template em 2 tabelas com schemas distintos, investigar 2 incidentes
  independentes), lançar via Task em paralelo. NÃO usar Task pra coisa trivial —
  overhead de sub-agente só compensa em trabalho não-trivial.
- **Agrupar fases sem aval do Igor intermediário numa rodada só.** Leitura empírica
  + análise + bruto literal podem vir juntos no mesmo cola lá quando o arquiteto já
  decidiu o caminho. Pausar pra revisão acontece só em pontos reais de decisão
  (bifurcação arquitetural, bruto pra aprovar, smoke pra confirmar antes do commit).
- **NÃO paralelizar quando há dependência sequencial óbvia.** Decisão pendente,
  schema desconhecido necessário pro próximo passo, build precisa passar antes do
  smoke — esses são pontos de sincronização que não aceleram com paralelismo.
- **Ganho realista: 30-50% por sessão.** Não 300%. A revisão de bruto pelo arquiteto
  e as decisões arquiteturais continuam sendo o limite real. Honestidade sobre isso
  evita prometer demais.

## Padrão de git

- **Commits semânticos**, mensagem em português, prefixo `feat/fix/docs/chore(camada-N):`.
  Mensagem multi-linha via `git commit -F <arquivo>` (não `-m`), para preservar formatação.
- **`git add` arquivo a arquivo, lista explícita. NUNCA `git add .` nem wildcard.**
  Isso protege contra incluir arquivos não revisados (ex.: `CLAUDE.md` enquanto incompleto,
  ou `.env`).
- **Sanity de staging antes de todo commit:** `git status -s` + `git diff --staged --stat`
  + grep por segredo (`eyJ...`, `password`, `secret=`) + confirmar que `.env`/`.env.local`
  estão FORA da staging. Confirmar com o Igor antes do commit.
- **Segredos nunca são commitados.** `.env`, `frontend/.env.local`, `evolution-local/.env`
  e `CONTEXT.md` são gitignored. Ao validar presença de env, fazer sem expor valor
  (`grep -c`, `wc -c`, mascarar). **Senhas nunca vão em arquivo** — só em comunicação direta.
- **Tag anotada por sub-fase fechada:** `git tag -a fase-N.M-fechada -F <msg>` apontando
  para o commit que a fecha. Tags não se movem depois de criadas.
- **Trailer obrigatório** no fim de cada commit:
  `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`
- **Repo tem remote no GitHub:** `origin` = `https://github.com/igorhaf/whatsapp.git`.
  Push pra `origin/main` + `git push origin --tags` é prática válida (foi feito no
  checkpoint de 2026-06-11). NUNCA `git push --force`. Branch `main`.
- **Critério de "fechado" de sub-fase que toca backend:** `mvn -B clean test` verde
  (gate empírico com a contagem do Surefire). Que toca frontend: `next build` limpo
  (Turbopack dev é lazy e esconde import quebrado — build de prod é a verdade) + smoke.

## Comandos de boot (ambiente local)

- **Backend** (Spring, porta 8095): `./scripts/run-local.sh` (usa Temurin JDK 17). Sobe em
  ~1.5s; conecta ao Supabase como `service_role`. Não tem rota raiz nem actuator → 404 na
  raiz é saudável; sanity real é `GET /admin/me` sem token → 401 `missing_auth_header`.
- **Frontend** (Next, porta 3000): `cd frontend && npm run dev`. `/login` → 200.
- **Testes backend:** `JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64 mvn -B clean test`.
- **Build frontend:** `cd frontend && npm run build`.
- **Banco (psql direto):** Supabase Session pooler IPv4
  (`aws-1-us-west-2.pooler.supabase.com:5432`, user `postgres.<ref>`); senha em
  `SPRING_DATASOURCE_PASSWORD` do `.env`. Smoke E2E real: login via
  `POST {SUPABASE_URL}/auth/v1/token?grant_type=password` → token ES256 → bater no
  backend ou no PostgREST (`/rest/v1/...`).

## DevX local (Docker — fase 0.5)

O dev local roda em **docker-compose** (a porta 80 era interceptada pelo Apache → 403 em
`*.meadadigital.local`). Tudo em container **exceto o banco**, que continua no **Supabase
remoto** (paridade com prod, tenants preservados).

- **Subir:** `./scripts/meada-up.sh` — para o Apache (temporário, sem disable), `docker compose
  up -d --build`, espera o backend. **Parar:** `./scripts/meada-down.sh`.
- **Containers:** `backend` (Spring, `mvn spring-boot:run`, hot-reload via volume em `src/`),
  `frontend` (Next `npm run dev`, hot-reload via volume), `embeddings` (sidecar Python 7080),
  `caddy` (proxy reverso na porta 80, vhosts pros 5 subdomínios + `api.meadadigital.local`).
- **URLs sem porta:** `http://processo.meadadigital.local` etc. (ver
  `docs/MULTI_PROFILE_DEV.md` p/ `/etc/hosts`).
- **Rede:** dentro do compose o backend fala com o sidecar por `embeddings:7080` e o browser
  chama a API por `api.meadadigital.local` — ambos via override de `environment` no compose
  (o `.env` NÃO é alterado). CORS inclui os subdomínios.
- **Testes continuam no HOST** (`mvn -B test`): exigem Supabase real + Testcontainers (Docker),
  fora do container do backend. É o gate de qualidade pré-commit (327 verde).
- **Tenants reais persistem:** Empresa Alpha + igorhaf3/4/5 (legal/dental/sushi) vivem no
  Supabase; nada é recriado ao subir/derrubar containers.

## Usuários de teste (apenas referencial — senhas só em comunicação direta)

- **super-admin:** `igorhaf@gmail.com` (na allowlist `ADMIN_SUPER_ADMIN_EMAILS`).
- **tenant-admin:** `igorhaf2@gmail.com` (linha em `public.users`, company_id = Empresa
  Alpha `52e88a0b-...`, role `admin`).
- Empresas seed: Alpha (`52e88a0b-...`), Beta (`38cdac12-...`), Meada Delta 01.

## Arquitetura de auth (camada 4 — painel admin)

Dois perfis, duas vias:
- **super-admin** (meada): allowlist `ADMIN_SUPER_ADMIN_EMAILS`, opera via Spring/
  service_role, FORA do RLS. Lê tudo (ex.: lista global de empresas).
- **tenant-admin**: opera via Supabase SDK + RLS. Só vê/escreve dados da própria empresa.
  CRUD interno do tenant é SDK+RLS; super-admin e chamadas externas são Spring REST.
- JWT do Supabase é ES256 validado por JWKS (filtro próprio `JwtAuthenticationFilter`,
  não Spring Security). RLS no banco via `app.company_id()` (lê `company_id` de
  `public.users` por `auth.uid()`). WRITE via SDK exige `company_id` explícito no payload
  (policy WITH CHECK revalida — defesa em profundidade, provado E2E na 4.4).

## Multi-perfil (camada 7.0)

- **Meada é um monolito que se apresenta como N produtos verticais ("perfis").** Cada perfil
  (generic/Meada, legal/ProcessoBot, dental/DentalBot, sushi/SushiBot) parece um produto
  distinto pro cliente final — subdomínio, nome, tom de IA e (futuramente) features próprias.
- **Perfis são HARDCODED** em dois arquivos espelhados: `src/main/java/com/meada/whatsapp/
  profiles/ProfileType.java` (enum) e `frontend/lib/profiles/profile-type.ts` (const). O
  `ProfileTypeParityTest` falha o build se divergirem. NÃO existe tabela de perfis.
- **Tenant tem EXATAMENTE 1 perfil** (`companies.profile_id`, NOT NULL DEFAULT 'generic',
  CHECK nos 4 ids). Cravado pelo root ao editar a empresa (PATCH /admin/companies); o tenant
  não escolhe.
- **Todos os perfis coexistem HARMONICAMENTE:** feature de um perfil NUNCA pode quebrar ou
  interferir em outro. Quando houver conflito, resolver com CONDICIONAL explícita por
  `profile_id`, NUNCA generalizar à força.
- **Features genéricas (núcleo) ficam disponíveis em TODOS os perfis.** Features específicas
  vivem em `src/main/java/.../profiles/{perfil}/` e `frontend/profiles/{perfil}/`.
- **Adicionar um perfil novo** = editar os 2 arquivos (enum Java + const TS), aplicar uma
  migration de CHECK constraint em `companies.profile_id`, e rodar os testes de paridade.
  NÃO criar tabela de perfis.
- **NUNCA remover um nicho ao adicionar outro (CRAVADO pelo Igor).** Adicionar um perfil é
  sempre ACRESCENTAR — o novo id entra na lista do CHECK `companies.profile_id` e no enum
  PRESERVANDO TODOS os existentes; nenhum nicho anterior pode sumir. Armadilha real: clonar
  uma migration por `sed s/comida/novo/g` troca `'comida'` por `'novo'` na lista do CHECK
  (remove comida + os demais), em vez de adicionar. Depois de qualquer clonagem por sed,
  CONFERIR que o CHECK tem TODOS os perfis (os 16+ atuais + o novo), não só o novo.
- **Subdomínio → perfil:** o middleware Next (`frontend/middleware.ts`) injeta
  `x-meada-subdomain`; `localhost`/domínio-base = 'meada' (universal, login universal). Dev
  local: ver `docs/MULTI_PROFILE_DEV.md` (entradas de `/etc/hosts`).

## Perfil Sushi (SushiBot, camada 7.1)

Primeiro perfil vertical real depois da fundação multi-perfil (SM-A). O tenant sushi
(`profile_id='sushi'`) vira um produto de restaurante: gerencia cardápio, a IA atende clientes
em linguagem livre via WhatsApp, e os pedidos viram um Kanban.

- **Fluxo de pedido por mensagem LIVRE:** o cliente pede em texto natural; a IA interpreta,
  monta o carrinho NA CONVERSA (sem entidade própria — a IA relê o histórico a cada turno) e,
  na confirmação, emite a tag `<pedido>{...}</pedido>`.
- **Tag `<pedido>` em texto livre, NÃO tool calling do Gemini:** a Gemini API trata tool calling
  e `responseSchema` como mutuamente exclusivos, e o fluxo de outbound já usa `responseSchema`
  (needs_human/intent). A persona do SushiBot instrui a IA a terminar a confirmação com a tag;
  o `OrderConfirmHandler` parseia via regex, cria o pedido e o `OutboundService` REMOVE a tag
  antes de enviar a mensagem ao cliente. O `total_cents` da tag é DESCARTADO — o backend
  recalcula a partir do cardápio (defesa contra a IA chutar total).
- **Status e transições (cravadas):** `recebido → preparo → saiu_pra_entrega → entregue`;
  `cancelado` é terminal alternativo (de qualquer não-terminal). Transição inválida → 409
  `invalid_status_transition`. Cada transição dispara notificação outbound automática (texto fixo
  por status; configurável no futuro).
- **Cardápio:** só texto (foto bloqueada por SERVICE_ROLE_KEY ausente — ver RISKS.md). Categorias
  hardcoded e materializadas (`SushiCategory.java` ↔ `sushi-categories.ts`, `SushiCategoryParityTest`).
- **Cache de cardápio:** o bloco de cardápio+config injetado no prompt é cacheado (Caffeine, TTL
  60s) por company em `SushiMenuCache`; o `SushiMenuService` INVALIDA explicitamente ao gravar/
  atualizar/excluir um item — a IA vê a mudança na hora.
- **Snapshot no pedido:** `sushi_order_items` guarda preço+nome do momento do pedido. Alterar/
  excluir um item no cardápio NÃO altera pedidos passados.
- **Guard de perfil:** `SushiProfileGuard.requireSushi` — endpoints `/api/sushi/**` retornam 403
  `forbidden_wrong_profile` para tenant de outro perfil. O `JwtAuthenticationFilter` autentica
  `/api/sushi/**` (tenant), além de `/admin/**`.
- **Sidebar:** `getNavForProfile('sushi')` injeta o grupo "Restaurante" (Cardápio + Pedidos);
  outros perfis não veem.
- Guia operacional do tenant: `docs/PERFIL_SUSHI.md`.

## Perfil Legal (ProcessoBot, camada 7.2)

Segundo perfil vertical real (depois do SushiBot). O tenant legal (`profile_id='legal'`) vira
um produto de escritório de advocacia: gerencia clientes e processos, e a IA atende clientes
consultando os processos deles.

- **Cliente DESACOPLADO de contact:** `legal_clients` é catálogo próprio (name obrigatório;
  email/phone/document/contact_id opcionais). O escritório cadastra o cliente antes do WhatsApp;
  `contact_id` (nullable) liga ao contato — a IA resolve `contact → legal_client → processos`.
- **CNJ validado mód 97** (Resolução CNJ 65/2008) no `LegalCnjValidator` — regex NÃO basta, o DV
  depende dos demais campos. Storage sem máscara (20 dígitos, UNIQUE por company); display
  formatado `NNNNNNN-DD.AAAA.J.TR.OOOO`. (Nota: os CNJs "reais" do prompt da SM-C não passavam
  no mód 97 — os testes/seed usam números com DV computado pelo próprio algoritmo.)
- **Status hardcoded materializado** (`LegalCaseStatus` ↔ `legal-case-status.ts`,
  `LegalCaseStatusParityTest`): ativo/suspenso/arquivado/encerrado. Transição LIVRE (o advogado
  pode reativar arquivado). Mudança de status notifica o cliente (texto fixo defensivo, só
  suspenso/arquivado/encerrado; ativo silencioso) via `LegalCaseNotifier` (resolve contact via
  o legal_client; pula em silêncio se não houver vínculo WhatsApp).
- **Andamentos** (`legal_case_updates`): registro MANUAL pelo advogado (title/body/occurred_at).
  NÃO disparam notificação (andamento técnico ≠ comunicação). Sem cálculo de prazo/scheduler
  nesta SM — fase futura.
- **IA injeta os processos do cliente identificado pelo telefone:**
  `ProfilePromptContext.segmentFor(profileId, companyId, conversationId)` — o legal resolve a
  conversa → contato → cliente → processos+andamentos e injeta no prompt; telefone desconhecido →
  bloco que orienta a IA a pedir identificação. Cache `LegalCaseContextCache` (Caffeine 60s,
  invalidação explícita ao mutar cliente/processo). O sushi ignora o conversationId.
- **Guard:** `LegalProfileGuard` (403 forbidden_wrong_profile). `JwtAuthenticationFilter`
  autentica `/api/legal/**` (além de `/admin/**` e `/api/sushi/**`).
- **OutboundService NÃO foi tocado** (decisão cravada — legal não tem tag equivalente ao
  `<pedido>` do sushi; o cliente só pergunta, não confirma pedido).
- **Sidebar:** `getNavForProfile('legal')` injeta o grupo "Escritório" (Clientes + Processos).
- Guia operacional do tenant: `docs/PERFIL_LEGAL.md`.

## Perfil Restaurante (MesaBot, camada 7.3)

Terceiro perfil vertical real (depois do SushiBot e do ProcessoBot). O tenant restaurant
(`profile_id='restaurant'`) vira um produto de RESERVAS de restaurante: gerencia mesas e reservas,
e a IA atende clientes via WhatsApp, verifica disponibilidade e confirma a reserva. É o 4º perfil
real no enum `ProfileType` (5º contando generic).

- **Modelo análogo a sushi/legal:** `restaurant_tables` (catálogo de mesas, ~ sushi_menu_items) +
  `table_reservations` (reservas, ~ sushi_orders) + `restaurant_reservation_config` (duração/
  horário, 1:1 com company). Migration 32.
- **Fluxo por mensagem LIVRE + tag `<reserva>`:** espelho da tag `<pedido>` do sushi. A IA negocia
  em texto natural; na confirmação emite `<reserva>{"table_id","date":"YYYY-MM-DD","start_time":
  "HH:MM","num_people":N}</reserva>`. O `ReservationConfirmHandler` parseia via regex (NÃO tool
  calling — mesma restrição responseSchema do sushi), valida e cria a reserva; o `OutboundService`
  REMOVE a tag antes de enviar ao cliente (`maybeProcessRestaurantReservation`, encadeado após o
  sushi no caso 6 — perfil é único, só um age).
- **Lógica de conflito em SQL transacional (a parte delicada):** `ReservationRepository.findConflict`
  é um SELECT com a janela materializada (`NOT (end_at <= newStart OR start_at >= newEnd)`), só
  status bloqueantes (`pendente`/`confirmada`). O `insertReservation` RE-VERIFICA o conflito DENTRO
  da transação antes do INSERT (fecha a janela de race entre o cache da IA e a persistência). Se
  conflitar, lança `SlotConflictException` → 409 `conflict_slot` (com detalhes de quem ocupa).
- **end_at NÃO é coluna gerada:** `timestamptz + interval` não é IMMUTABLE (Postgres rejeita em
  GENERATED — depende do timezone da sessão p/ DST). É materializado no INSERT pelo repositório
  (`start_at + duration_minutes`). Lição cravada nesta SM.
- **Slot 30min, duração 2h FIXAS** (configurável por restaurante em `restaurant_reservation_config`:
  `duration_minutes` default 120). Buffer entre reservas = 0 nesta SM. Reserva tem de caber inteira
  na janela `opens_at`..`closes_at` (validado no fuso America/Sao_Paulo, HARDCODED — pendência).
- **Status hardcoded materializado** (`ReservationStatus` ↔ `reservation-status.ts`,
  `ReservationStatusParityTest`): `pendente → confirmada → realizada`; `pendente/confirmada →
  cancelada`; `confirmada → no_show`. Terminais: realizada/cancelada/no_show. Transição inválida →
  409 `invalid_status_transition`. Só **confirmada** (com dados da reserva) e **cancelada** notificam
  o cliente (`ReservationNotifier`); realizada/no_show/pendente são silenciosos (quem furou não
  recebe sermão).
- **Cache de contexto da IA TTL 15s** (`ReservationContextCache`) — UM QUARTO do sushi/legal (60s)
  de propósito: a agenda muda rápido. Injeta mesas disponíveis + reservas ativas dos próximos 7 dias
  + config. Invalidação explícita ao mutar mesa/reserva/config.
- **POST manual pelo tenant:** `/api/restaurant/reservations` cria reserva sem `conversation_id`
  (sem WhatsApp) — não notifica (sem canal). NÃO há DELETE de reserva (histórico; "remover" =
  status cancelada).
- **Guard:** `RestaurantProfileGuard` (403 forbidden_wrong_profile). `JwtAuthenticationFilter`
  autentica `/api/restaurant/**` (além de `/admin/**`, `/api/sushi/**`, `/api/legal/**`).
- **Sidebar:** `getNavForProfile('restaurant')` injeta o grupo "Reservas" (Mesas + Reservas +
  Configurações) — heading "Reservas" (não "Restaurante", pra não colidir com o sushi).
- **NÃO TEM nesta SM:** salão de beleza, pousada, agenda de profissionais, cardápio (sushi cobre),
  pagamento antecipado, no-show com cobrança, scheduler de auto-transição, lembrete "sua reserva é
  em 1h", reserva em grupo, feriados, buffer. Sem foto/anexo (bloqueador SERVICE_ROLE_KEY).
- Guia operacional do tenant: `docs/PERFIL_MESABOT.md`.

## Perfil Dental (DentalBot, camada 7.4)

Quarto perfil vertical real (sushi/legal/restaurant/dental). O tenant dental (`profile_id='dental'`)
vira um produto de CLÍNICA ODONTOLÓGICA: gerencia pacientes e a agenda de consultas, e a IA atende
pacientes via WhatsApp com tom acolhedor — identifica pelo telefone, responde sobre próximas
consultas e agenda novas, SEM nunca dar diagnóstico ou recomendação clínica.

- **Modelo análogo aos outros perfis:** `dental_patients` (catálogo, ~ legal_clients) +
  `dental_appointments` (consultas, ~ table_reservations) + `dental_clinic_config` (duração/horário,
  1:1 com company). Migration 33.
- **Fluxo por mensagem LIVRE + tag `<consulta>`:** espelho das tags `<pedido>`/`<reserva>`. A IA
  negocia em texto natural; na confirmação emite `<consulta>{"date","start_time","type","notes"}
  </consulta>`. O `ConsultaConfirmHandler` parseia via regex, resolve o paciente pelo contato
  (`dental_patients.contact_id`) e cria a consulta; o `OutboundService` REMOVE a tag antes de enviar
  (`maybeProcessDentalAppointment`, encadeado após sushi/restaurant — perfil é único, só um age). Se
  o paciente não está identificado, retorna empty + warn (a IA não devia emitir a tag sem paciente).
- **Lógica de conflito em SQL transacional (igual MesaBot):** `findConflict` é um SELECT com a janela
  materializada (`NOT (end_at <= newStart OR start_at >= newEnd)`), só status bloqueantes
  (`agendada`/`confirmada`), **por company** — NÃO há `dentist_id` nesta SM (1 dentista por tenant);
  fase futura adiciona dentist_id e muda o WHERE. O `insertAppointment` re-verifica o conflito DENTRO
  da transação (defesa race). 409 `conflict_slot` com detalhes.
- **end_at NÃO é coluna gerada** (mesma lição da SM-D: `timestamptz + interval` não é IMMUTABLE) —
  materializado no INSERT (`start_at + duration_minutes`).
- **Slot 30min, duração 30min FIXOS** (configurável: `dental_clinic_config.duration_minutes` default
  30). Buffer = 0 nesta SM. Janela `opens_at`..`closes_at` (default 08:00–18:00), validada no fuso
  America/Sao_Paulo (HARDCODED — pendência).
- **Status hardcoded materializado** (`AppointmentStatus` ↔ `appointment-status.ts`,
  `AppointmentStatusParityTest`): `agendada → confirmada → realizada`; `agendada/confirmada →
  cancelada`; `confirmada → falta`. Terminais: realizada/cancelada/falta. Só **confirmada** (com
  data/hora) e **cancelada** notificam o paciente (`DentalAppointmentNotifier`, texto DEFENSIVO sem
  promessa clínica); agendada/realizada/falta são silenciosos (quem furou não recebe sermão).
- **Persona da SM-A INTACTA:** `ProfilePromptContext.DENTAL` (tom técnico-acolhedor, empatia com medo
  de dentista, NUNCA diagnóstico) NÃO foi tocada — só ganhou contexto DINÂMICO via
  `DentalContextCache` (TTL 30s, keyed por `(companyId, contactId)`): dados do paciente identificado
  + próximas consultas + slots livres dos próximos 14 dias + instruções de agendamento. Invalidação
  por company ao mutar paciente/consulta/config.
- **IA NUNCA dá diagnóstico, NUNCA recomenda procedimento, NUNCA discute sintoma** — para qualquer
  dúvida clínica, encaminha ao dentista ("Para isso, vou pedir que o dentista avalie. Posso agendar
  uma consulta?"). **Cancelamento por IA é BLOQUEADO** (encaminha pro tenant — risco) — a tag só
  serve para AGENDAR.
- **LGPD (CRAVADO):** `dental_patients.notes` e `dental_appointments.notes` são **ADMINISTRATIVOS**
  (preferências de horário, contato), **NÃO clínicos**. Dados clínicos (prontuário, diagnóstico,
  alergias, plano de tratamento, odontograma) ficam para fase futura, com **criptografia at-rest e
  log de acesso por usuário**. `type` da consulta é texto livre administrativo ("Limpeza",
  "Avaliação"), nunca recomendação.
- **Guard:** `DentalProfileGuard` (403 forbidden_wrong_profile). `JwtAuthenticationFilter` autentica
  `/api/dental/**` (além de `/admin/**`, `/api/sushi/**`, `/api/legal/**`, `/api/restaurant/**`).
- **Sidebar:** `getNavForProfile('dental')` injeta o grupo "Consultório" (Pacientes + Agenda +
  Configurações).
- **POST manual pelo tenant:** sem `conversation_id` (sem WhatsApp) — não notifica. NÃO há DELETE de
  consulta (histórico; "remover" = status cancelada).
- **NÃO TEM nesta SM:** odontograma, plano de tratamento, evolução por sessão, TUSS, anamnese,
  alergias estruturadas, histórico médico, receituário, atestado, pagamento, `dentist_id`. Sem
  foto/anexo (bloqueador SERVICE_ROLE_KEY). Sem scheduler de auto-transição (consulta passada não
  vira "realizada" sozinha).
- Guia operacional do tenant: `docs/PERFIL_DENTAL.md`.

## Perfil Salão (SalãoBot, camada 7.5)

Quinto perfil vertical real (sushi/legal/restaurant/dental/salon). O tenant salon
(`profile_id='salon'`) vira um produto de SALÃO DE BELEZA: gerencia profissionais e serviços, e a
IA atende clientes via WhatsApp com tom acolhedor (sem julgamento estético), oferece serviços +
profissionais, verifica disponibilidade e agenda.

- **EVOLUÇÃO crítica do padrão de conflito:** dental/restaurant checavam conflito por company (1
  recurso por tenant). O salon tem MÚLTIPLOS profissionais — o conflito é por `professional_id`.
  `SalonAppointmentRepository.findConflict(professionalId, start, end)` filtra por profissional;
  **2 clientes no mesmo horário com profissionais DIFERENTES não conflitam** (paralelismo). Fase
  futura: se um profissional puder atender em salas distintas, refinar.
- **Modelo:** `salon_professionals` (catálogo) + `salon_offerings` (serviços; nome "offering" no
  backend p/ não colidir com o Spring `SalonOfferingService` — a UI/rota chama "serviços") +
  `salon_config` (horário) + `salon_appointments`. Migration 34.
- **Cliente NÃO é entidade própria (decisão cravada):** salão tem alta rotatividade; modelar
  `salon_clients` seria over-engineering. O histórico vem do `contact` + `salon_appointments` dele.
  `guest_name`/`guest_phone` são snapshots do contato. Fase futura se virar prioridade.
- **Tag `<agendamento>`:** `<agendamento>{"professional_id","service_id","date","start_time",
  "notes"}</agendamento>`. O `AgendamentoConfirmHandler` resolve o contato da conversa (guest_name =
  contact.name snapshot), lê `offering.duration_minutes` (snapshot) e cria. OutboundService:
  `maybeProcessSalonAppointment` (encadeado após sushi/restaurant/dental).
- **Snapshots no agendamento:** `professional_name` + `service_name` + `price_cents` +
  `duration_minutes` congelados no momento — alterar serviço/profissional depois NÃO altera
  agendamentos passados. `end_at` materializado no INSERT (lição SM-D/E).
- **Slot 15min** (granularidade fina — salão tem serviço curto). **Duração POR SERVIÇO** (não fixa).
  Buffer = 0 nesta SM. Janela `opens_at`..`closes_at` (default 09:00–20:00).
- **Status hardcoded** (`SalonAppointmentStatus` ↔ `salon-appointment-status.ts`, parity test):
  `agendado → confirmado → realizado`; `agendado/confirmado → cancelado`; `confirmado → falta`. Só
  **confirmado** (com data/hora/profissional) e **cancelado** notificam; agendado/realizado/falta
  silenciosos. Texto defensivo (sem promessa estética).
- **Persona SALON nova** (não existia na SM-A, igual restaurant): tom acolhedor, sem julgamento.
  Contexto dinâmico via `SalonContextCache` (TTL 20s — entre restaurant 15s e dental 30s): serviços
  ativos, profissionais ativos, histórico do contato, slots livres POR PROFISSIONAL (próximos 7
  dias). **IA NUNCA recomenda serviço não pedido, NUNCA opina sobre aparência, sem promessa de
  resultado.**
- **LGPD:** `notes` é administrativo, sem dado sensível.
- **Guard:** `SalonProfileGuard`. `JwtAuthenticationFilter` autentica `/api/salon/**` (além de
  sushi/legal/restaurant/dental).
- **Sidebar:** `getNavForProfile('salon')` injeta "Salão" (Profissionais/Serviços/Agenda/
  Configurações). A tela de serviços é `/dashboard/salon-services` (a `/dashboard/services` é a tela
  GENÉRICA de configuração da IA do core — NÃO colidir).
- **NÃO TEM:** comissão, pagamento, fidelidade/cashback, estoque, multi-loja, plano de assinatura,
  scheduler de auto-transição, foto (bloqueador SERVICE_ROLE_KEY).
- Guia operacional do tenant: `docs/PERFIL_SALAO.md`.

## Perfil Pousada (PousadaBot, camada 7.6)

Sexto perfil vertical real (sushi/legal/restaurant/dental/salon/pousada). O tenant pousada
(`profile_id='pousada'`) vira um produto de HOSPEDAGEM: gerencia quartos e reservas, e a IA atende
hóspedes via WhatsApp com tom acolhedor-turístico, mostra quartos por número de pessoas + datas,
calcula o total (diária × noites) e reserva.

- **EVOLUÇÃO ESTRUTURAL — primeira SM que escapa do padrão "slot de horas":** a reserva é um
  INTERVALO DE DIAS (`check_in_date`/`check_out_date` como DATE, não timestamptz — é dia, não
  instante). O conflito é overlap de intervalos HALF-OPEN `[check_in, check_out)` por quarto:
  `NOT (existing.check_out <= new.check_in OR existing.check_in >= new.check_out)`. **Check-out de
  uma reserva e check-in de outra NO MESMO DIA não conflitam** (o quarto rotaciona) — consequência
  natural do intervalo half-open.
- **Modelo:** `pousada_rooms` (catálogo: capacity + nightly_rate_cents) + `pousada_config`
  (check-in/check-out time + cancellation_policy texto livre) + `pousada_reservations`. Migration 35.
- **nights e total_cents materializados no INSERT** (`nights = check_out - check_in`; `total =
  nightly_rate × nights`). Snapshots de `room_name`/`nightly_rate_cents`/`capacity_snapshot` —
  mudar preço/capacidade do quarto depois NÃO altera reservas passadas.
- **Validações no service:** `check_out > check_in` (não aceita 0 noites); `check_in >= hoje` (fuso
  America/Sao_Paulo); `guests_count <= room.capacity`. Conflito re-verificado na transação (defesa
  race).
- **Status hardcoded** (`PousadaReservationStatus` 6 estados ↔ TS, parity test): `reservado →
  confirmado → checked_in → checked_out`; `reservado/confirmado → cancelado`; `confirmado →
  no_show`. Só **confirmado** (com quarto/datas/total) e **cancelado** notificam; demais silenciosos.
- **Tag `<reserva_pousada>`** (NAMESPACE distinto de `<reserva>` do RestaurantBot!): o
  `ReservaPousadaConfirmHandler` parseia, resolve o contato, cria. OutboundService:
  `maybeProcessPousadaReservation`.
- **Persona POUSADA nova:** tom acolhedor-sereno, **NUNCA promete estrutura/vista/comodidade não
  cadastrada na descrição do quarto**, sem "experiência única". Contexto dinâmico via
  `PousadaContextCache` (TTL 30s): quartos ativos, política, histórico do contato, DISPONIBILIDADE
  por quarto nos próximos 30 dias (intervalos LIVRES entre reservas ativas).
- **Cliente NÃO é entidade própria** (igual salon — hóspedes rotativos). Histórico via contact +
  reservations. guest_name/guest_phone snapshots.
- **LGPD:** `notes` é administrativo, sem RG/CPF/documento.
- **Guard:** `PousadaProfileGuard`. `JwtAuthenticationFilter` autentica `/api/pousada/**` (além de
  sushi/legal/restaurant/dental/salon).
- **Sidebar:** `getNavForProfile('pousada')` injeta "Pousada" (Quartos/Reservas/Configurações). Telas
  `/dashboard/rooms`, `/dashboard/pousada-reservations`, `/dashboard/pousada-settings`.
- **NÃO TEM:** tarifa sazonal/promocional, pagamento/sinal, foto, hóspede acompanhante como entidade,
  políticas com aceite de RG/CPF, Booking/Airbnb, fidelidade, café/serviços extras, scheduler de
  auto-transição.
- Guia operacional do tenant: `docs/PERFIL_POUSADA.md`.

## Perfil Academia (AcademiaBot, camada 7.7)

Sétimo perfil vertical real. O tenant academia (`profile_id='academia'`) vira um produto de
ACADEMIA/STUDIO de fitness: gerencia planos mensais e aulas semanais recorrentes, matricula alunos
(assinatura), registra pagamentos manuais, e a IA atende clientes via WhatsApp oferecendo planos +
aulas com vaga.

- **EVOLUÇÃO ESTRUTURAL MAIS PROFUNDA — primeira SM com RECORRÊNCIA INDEFINIDA:** a matrícula é uma
  ASSINATURA (`status` ativa-até-cancelar), não um evento pontual (slot) nem um intervalo finito
  (pousada). Uma matrícula ocupa N vagas em N aulas semanais recorrentes (junction
  `academia_membership_classes`). O conflito é por CAPACITY por aula (`capacity - count(matrículas
  não-canceladas naquela aula) > 0`), validado transacionalmente no INSERT.
- **Modelo:** `academia_plans` + `academia_classes` (dia da semana 0=domingo..6, hora, duração,
  capacity, modalidade) + `academia_config` (horário) + `academia_memberships` (assinatura) +
  `academia_membership_classes` (junction com snapshot da aula) + `academia_payments` (manual).
  Migration 36.
- **Status hardcoded** (`AcademiaMembershipStatus` ↔ TS, parity test): `ativa ⇄ suspensa`; ambas →
  `cancelada` (terminal). Só **ativa** (boas-vindas, com o plano) e **cancelada** (despedida)
  notificam; **suspensa** silenciosa. CANCELADA materializa `end_date` e libera vagas. **SUSPENSA
  MANTÉM ocupando a vaga** (decisão cravada: pausa curta; pra liberar, cancelar) — o count de vaga
  filtra por `status <> 'cancelada'`.
- **Anti-dupla matrícula:** UNIQUE INDEX `uniq_active_membership_per_contact` (company, contact)
  WHERE status='ativa' — impede 2 matrículas ativas pro mesmo contato. O service também valida via
  `findActiveByContact` (→ 409 already_active).
- **Snapshots:** matrícula congela `plan_name` + `plan_monthly_cents` + `student_name`/`phone`;
  junction congela `class_name` + `day_of_week` + `start_time` + `duration` + `modality`. Mudar
  plano/aula depois NÃO altera matrículas existentes.
- **Pagamento manual** (`academia_payments`): registro mensal com UNIQUE (membership,
  reference_month) → 409 duplicate_payment. Só em matrícula ativa. Summary = último mês pago +
  meses em aberto (meses decorridos desde start_date − pagamentos). SEM cobrança automática
  (Stripe é #50, fase futura); SEM cálculo de inadimplência.
- **Tag `<matricula>`** (namespace exclusivo): `<matricula>{"plan_id","class_ids":[...],
  "student_name","notes"}</matricula>`. `MatriculaConfirmHandler` parseia, resolve contato, cria.
  OutboundService: `maybeProcessMatricula`.
- **Persona ACADEMIA nova:** tom acolhedor-motivador, **NUNCA prescreve treino/dieta/avaliação
  física (não é educador físico — recusa com gentileza), NUNCA julga, sem promessa de resultado
  corporal**. Contexto via `AcademiaContextCache` (TTL 60s — aulas mudam pouco): planos ativos,
  aulas ativas com VAGAS RESTANTES em tempo real, matrícula atual do contato (anti-dupla na IA).
- **Aluno NÃO é entidade própria** (igual salon/pousada — rotatividade). Histórico via contact +
  memberships. `student_name`/`student_phone` snapshots.
- **LGPD:** `notes` administrativo, sem dados de saúde do aluno.
- **Guard:** `AcademiaProfileGuard`. `JwtAuthenticationFilter` autentica `/api/academia/**` (além de
  sushi/legal/restaurant/dental/salon/pousada).
- **Sidebar:** `getNavForProfile('academia')` injeta "Academia" (Planos/Aulas/Matrículas/
  Configurações). Telas `/dashboard/academia-{plans,classes,memberships,settings}`.
- **NÃO TEM:** treino prescrito, ficha de exercícios, avaliação física, balança/wearables,
  pagamento real (Stripe #50), foto, catraca biométrica, fidelidade, multi-unidade, scheduler de
  cobrança/lembrete.
- Guia operacional do tenant: `docs/PERFIL_ACADEMIA.md`.

## Perfil Pet/PetBot (camada 7.8)

OITAVO e ÚLTIMO perfil vertical da fila planejada — FECHA o catálogo dos 8 perfis. O tenant pet
(`profile_id='pet'`) vira um produto de PET SHOP / clínica veterinária: gerencia profissionais e
serviços (banho, tosa, consulta), cadastra os ANIMAIS de cada tutor, e a IA atende os tutores via
WhatsApp agendando na agenda de cada profissional.

- **EVOLUÇÃO ESTRUTURAL — ANIMAL como SUB-ENTIDADE de um contato (tutor):** diferente de todos os
  perfis anteriores (cujo "cliente" era o próprio contato), aqui o agendamento é para um ANIMAL, e o
  animal pertence a um tutor (contato do WhatsApp). `pet_animals` tem `contact_id NOT NULL` (FK
  restrict). Um tutor pode ter N animais. O conflito de agenda é **POR PROFISSIONAL** (igual salon).
- **Modelo:** `pet_professionals` + `pet_services` (com `species_restriction` nullable 'cao'|'gato'|
  'outro') + `pet_config` (horário 09:00–19:00 default) + `pet_animals` (sub-entidade: name, species,
  breed, sex, birth_year, active) + `pet_appointments` (snapshots de tutor+animal+prof+service).
  Migration 37. `end_at` materializado no INSERT (não generated).
- **SPECIES MATCH (regra cravada):** um serviço com `species_restriction` só aceita animal daquela
  espécie — `svc.speciesRestriction() != null && !equals(animal.species())` → `SpeciesMismatchException`
  (400 species_mismatch). A IA respeita a restrição; o backend reforça.
- **Status hardcoded** (`PetAppointmentStatus` ↔ `pet-appointment-status.ts`, parity test):
  `agendado → confirmado, cancelado`; `confirmado → realizado, cancelado, falta`; resto terminal.
  Só **confirmado** e **cancelado** notificam o tutor (texto fixo defensivo); demais silenciosos.
- **Tag `<agendamento_pet>` (namespace exclusivo) com 2 MODOS** — a novidade do handler:
  `animal_id` (animal já cadastrado) OU `new_animal:{name,species,breed}` (cadastra o animal como
  sub-entidade do tutor da conversa E agenda, no mesmo turno). `AgendamentoPetConfirmHandler` parseia,
  resolve o tutor pela conversa, cria. OutboundService: `maybeProcessPetAppointment`.
- **Persona PET nova:** tom carinhoso com o animal, atencioso com o tutor, **NUNCA dá diagnóstico
  veterinário, NUNCA prescreve medicação, NUNCA recomenda tratamento** — sintoma → orienta consulta
  presencial. Contexto via `PetContextCache` (TTL 20s): profissionais ativos, serviços (com restrição
  de espécie), ANIMAIS DO TUTOR (com último agendamento), slots livres por profissional (7 dias).
- **Snapshots:** o agendamento congela tutor (name/phone do contact do animal) + animal (name/species)
  + professional_name + service_name/category/price/duration. Arquivar/editar depois NÃO altera
  agendamentos passados.
- **Excluir vs arquivar:** animal/profissional/serviço com agendamento → 409 (`animal_in_use`,
  `professional_in_use`, `service_in_use`); o caminho preferido é arquivar (active=false).
- **LGPD:** `notes` do animal é administrativo, SEM dado clínico/prontuário.
- **Guard:** `PetProfileGuard`. `JwtAuthenticationFilter` autentica `/api/pet/**` (além de
  sushi/legal/restaurant/dental/salon/pousada/academia).
- **Sidebar:** `getNavForProfile('pet')` injeta "Pet Shop" (Profissionais/Serviços/Animais/Agenda/
  Configurações). Telas `/dashboard/pet-{professionals,services,animals,appointments,settings}`.
- **NÃO TEM:** prontuário/histórico clínico, vacinas/vermífugo com agenda, prescrição, internação,
  pacote/plano de banho recorrente (assinatura — academia já fez), foto do pet, pagamento real,
  scheduler de lembrete.
- Guia operacional do tenant: `docs/PERFIL_PET.md`.

> **Catálogo dos 8 perfis verticais COMPLETO** (sushi 7.1 · legal 7.2 · restaurant 7.3 · dental 7.4 ·
> salon 7.5 · pousada 7.6 · academia 7.7 · pet 7.8). Cada um escapou de uma limitação do modelo
> anterior: slot pontual → intervalo de dias → recorrência indefinida (assinatura) → sub-entidade
> (animal do tutor). Próximos perfis, se houver, partem deste alicerce.

## Perfil Oficina (OficinaBot, camada 7.9)

NONO perfil vertical real e PRIMEIRO além da fila planejada de 8. O tenant oficina
(`profile_id='oficina'`) vira um produto de OFICINA MECÂNICA / AUTO CENTER: gerencia mecânicos e os
veículos dos clientes, abre ordens de serviço (OS) order-based com itens (peça/mão-de-obra) e total,
e a IA atende clientes via WhatsApp — abre a OS pela queixa, informa o orçamento quando existe e
CAPTURA A APROVAÇÃO do cliente.

- **EVOLUÇÃO ESTRUTURAL — combina 2 padrões + 1 escapada nova:** (1) order-based com itens + total
  materializado (espelho SUSHI); (2) sub-entidade de cliente (`os_vehicles` ~ pet_animals, o contact
  é o dono); (3) **gate de aprovação em DUAS FASES** — a IA ABRE a OS e, num turno POSTERIOR (OS
  'orcada'), MUTA o estado pra 'aprovada'/'recusada'. **Primeiro perfil em que a IA altera o estado de
  um artefato existente via conversa, não só cria.**
- **Modelo (migration 38):** `os_mechanics` (catálogo simples, SEM agenda) + `os_vehicles` (placa
  UNIQUE por company, sub-entidade do contact) + `os_config` (horário INFORMATIVO, sem slot) +
  `service_orders` (snapshots customer/vehicle + total_cents) + `os_items` (kind peca|mao_de_obra,
  line_total_cents). `total_cents` e `line_total_cents` MATERIALIZADOS a cada mutação (lição end_at).
- **Status hardcoded** (`OsStatus` ↔ `os-status.ts`, parity test): `aberta → orcada/cancelada`;
  `orcada → aprovada/recusada/cancelada`; `aprovada → em_execucao/cancelada`; `em_execucao →
  concluida/cancelada`; `concluida → entregue`; terminais recusada/entregue/cancelada. Notificam
  (texto defensivo): **orcada** (com total), **aprovada**, **concluida**, **entregue**.
- **Trava de itens (`order_locked`):** itens só são mutáveis em aberta/orcada/aprovada; em
  em_execucao/concluida/entregue/recusada/cancelada → 409 order_locked.
- **`empty_budget`:** a OS só vai pra 'orcada' com `total_cents > 0` → 400 empty_budget (não dá pra
  orçar sem item).
- **Duas TAGS distintas** (namespace próprio): `<ordem_servico>` ABRE a OS (2 modos: `vehicle_id`
  existente OU `new_vehicle` cadastra+abre, espelho pet) — `AberturaOsConfirmHandler`; `<aprovacao_os>`
  MUTA o estado (`decisao` aprovada|recusada, só se a OS estiver 'orcada') — `AprovacaoOsHandler`.
  OutboundService: `maybeProcessAberturaOs` + `maybeProcessAprovacaoOs`.
- **Persona OFICINA nova:** tom prestativo-direto, **NUNCA diagnostica defeito, NUNCA inventa preço
  de peça ou monta orçamento (quem orça é o mecânico), NUNCA promete prazo fora da OS** — sintoma →
  avaliação presencial. Contexto via `OficinaContextCache` (TTL 20s): mecânicos ativos, veículos do
  cliente, e as OS abertas/orçadas do cliente (pra capturar a aprovação na OS certa).
- **Snapshots:** a OS congela customer_name/phone (do contact do veículo) + vehicle_plate/model.
  Mudar cliente/veículo depois NÃO altera OS passadas.
- **Cliente NÃO é entidade própria** (continua o contact; snapshots na OS). **Excluir vs arquivar:**
  veículo/mecânico em uso → 409 (vehicle_in_use / mechanic_in_use); preferir arquivar veículo.
- **Guard:** `OficinaProfileGuard`. `JwtAuthenticationFilter` autentica `/api/oficina/**` (além dos
  8 perfis anteriores).
- **Sidebar:** `getNavForProfile('oficina')` injeta "Oficina" (Mecânicos/Veículos/Ordens/
  Configurações). Telas `/dashboard/oficina-{mechanics,vehicles,orders,settings}` — a de ordens tem
  editor de itens inline.
- **NÃO TEM:** catálogo de peças/serviços pré-cadastrados (orçamento ad-hoc), agendamento de drop-off
  por horário/slot, FIPE, foto do veículo, histórico rico, pagamento real (Stripe #50), nota fiscal,
  estoque de peças.
- Guia operacional do tenant: `docs/PERFIL_OFICINA.md`.

## Perfil Nutri (NutriBot, camada 8.0)

DÉCIMO perfil vertical real e PRIMEIRA da camada 8.x. O tenant nutri (`profile_id='nutri'`) vira um
produto de CONSULTÓRIO DE NUTRIÇÃO: gerencia nutricionistas, pacientes e os planos alimentares, e a
IA atende pacientes via WhatsApp — agenda consultas e ENTREGA o plano que o nutricionista gravou.

- **🔒 TRAVA DE SEGURANÇA CLÍNICA (o coração desta SM, inegociável):** plano alimentar
  individualizado é conduta privativa do nutricionista (CFN/CRN). A IA **NUNCA** cria/calcula/monta/
  adapta/resume/ajusta plano; **NUNCA** dá caloria, macro, porção ou número nutricional; **NUNCA**
  responde "posso comer X?"/"quantas calorias tem Y?"/"isso engorda?"; **NUNCA** opina sobre
  patologia/suplementação/emagrecimento/restrição. Para qualquer dúvida nutricional → orienta agendar.
  A trava vive na **persona** (`ProfilePromptContext.NUTRI` + bloco de INSTRUÇÕES do `NutriContextCache`)
  E no **schema** (a IA NÃO tem policy de escrita de plano; só lê na entrega).
- **🔒 GUARDA DE TRANSTORNO ALIMENTAR (em toda a conversa):** se o paciente sinalizar restrição
  intensa, compulsão, purga, contagem obsessiva, peso-meta extremo ou sofrimento com comida/corpo, a
  IA não dá número, não valida a conduta, acolhe sem reforçar e encaminha ao nutricionista (e, havendo
  risco, sugere apoio profissional). NUNCA fornece técnica de restrição/compensação.
- **EVOLUÇÃO ESTRUTURAL — combina agenda (dental/salon: conflito por profissional) + sub-entidade
  (pet/oficina), com 2 escapadas novas:** (1) **DOIS NÍVEIS de sub-entidade** — paciente é
  sub-entidade do contact (nível 1), PLANO é sub-entidade do paciente (nível 2); primeiro perfil com
  sub-entidade aninhada. (2) **Artefato READ-ONLY-PRA-IA** — `nutri_plans.body` é escrito SÓ pelo
  profissional no painel; a IA tem um modo de ENTREGA (envia o texto gravado) mas NUNCA o edita.
- **Modelo (migration 39):** `nutri_professionals` (com `crn`) + `nutri_config` (horário) +
  `nutri_patients` (sub-entidade do contact; goal/dietary_restrictions texto livre SEM número) +
  `nutri_plans` (sub-entidade do paciente; body markdown; status ativo|arquivado) + `nutri_appointments`
  (conflito por profissional, end_at materializado, appointment_type primeira|retorno|avaliacao).
- **1 plano 'ativo' por paciente:** índice parcial `uniq_active_plan_per_patient (patient_id) where
  status='ativo'`. Criar/ativar um plano arquiva o anterior NA MESMA transação (`NutriPlanService`).
- **Status hardcoded** (`NutriAppointmentStatus` ↔ `nutri-appointment-status.ts`, parity test):
  agendado→confirmado/cancelado; confirmado→realizado/cancelado/falta; resto terminal. Notificam
  **confirmado** (tipo+profissional+data/hora) e **cancelado** (texto SEM conteúdo nutricional).
- **Duas TAGS distintas** (namespaces próprios): `<consulta_nutri>` AGENDA (2 modos: `patient_id`
  existente OU `new_patient{name,goal}` cadastra+agenda) via `AgendamentoNutriConfirmHandler`;
  `<entrega_plano>{patient_id}` ENTREGA o plano ATIVO via `EntregaPlanoHandler`. OutboundService:
  `maybeProcessConsultaNutri` + `maybeProcessEntregaPlano`.
- **Entrega de plano = body EXATO, fora da geração da IA + barreira de contato:** o `EntregaPlanoHandler`
  busca o plano ativo e envia o `body` VERBATIM como mensagem outbound separada (`notifier.sendText`)
  — NÃO passa pela IA (pra não ser reescrito). Só entrega plano de paciente do **PRÓPRIO contato** da
  conversa (segurança: `patient.contactId() == conversation.contactId`). Sem plano ativo → não entrega,
  a IA oferece agendar.
- **Contexto da IA NÃO injeta o body do plano** (segurança) — só a indicação de quais pacientes do
  contato têm plano ativo. `NutriContextCache` TTL 20s (profissionais, pacientes do contato com
  objetivo + flag de plano ativo + última consulta, slots livres por profissional). Invalidação em
  toda mutação.
- **Paciente NÃO é entidade do core** (continua o contact; snapshots patient_name/phone nas consultas).
  **Excluir vs arquivar:** paciente com consulta OU plano → 409 patient_in_use; preferir arquivar.
- **Guard:** `NutriProfileGuard`. `JwtAuthenticationFilter` autentica `/api/nutri/**` (além dos 9
  perfis anteriores).
- **Sidebar:** `getNavForProfile('nutri')` injeta "Nutri" (Profissionais/Pacientes/Planos/Agenda/
  Configurações). Telas `/dashboard/nutri-{professionals,patients,plans,appointments,settings}` — a de
  planos tem o editor (title + body markdown + vigência + ativar/arquivar).
- **NÃO TEM:** plano estruturado em refeições/porções (body é texto livre — estruturar é arriscado),
  cálculo de TMB/macro/caloria (PROIBIDO por segurança), TACO/USDA, antropometria com gráfico,
  prescrição de suplemento, anamnese estruturada, foto, pagamento real, bioimpedância.
- **Lição de teste cravada (SM-K):** com 11 perfis, cada `*ServiceTest` com `@Import(TestConfig)` é um
  ApplicationContext distinto; o pool Hikari padrão (min-idle 5/max 10) × ~18 contextos estourava o
  teto de conexões do pooler Supabase (`CannotGetJdbcConnection` no `StartupDatabaseCheck`). Fix:
  `src/test/resources/application-dev.yml` com pool minúsculo (min-idle 0/max 2) — só nos testes, não
  toca dev/prod.
- Guia operacional do tenant: `docs/PERFIL_NUTRI.md`.

## Perfil Barbearia (BarbeariaBot, camada 8.1)

DÉCIMO PRIMEIRO perfil vertical real (sushi/legal/restaurant/dental/salon/pousada/academia/pet/
oficina/nutri/barbearia). O tenant barbearia (`profile_id='barbearia'`) vira um produto de BARBEARIA /
barber shop: gerencia barbeiros e serviços, marca horários na agenda, E gerencia uma FILA DE WALK-IN
(por ordem de chegada). A IA atende clientes via WhatsApp com tom descontraído-acolhedor e oferece
DOIS caminhos: MARCAR horário com um barbeiro, ou ENTRAR NA FILA quando o cliente quer ser atendido
"assim que der".

- **CLONA o chassi do SALON (7.5):** barbeiros (~salon_professionals), serviços (~salon_offerings),
  config + agenda com conflito POR BARBEIRO (overlap half-open, re-verificado na transação),
  duração por serviço, snapshots, `end_at` materializado no INSERT (lição timestamptz+interval).
  Cliente NÃO é entidade (continua contact; snapshots guest_name/phone no appointment e no ticket).
- **ESCAPADA ESTRUTURAL — FILA DE WALK-IN COM POSIÇÃO DERIVADA (`barber_queue_tickets`):** primeiro
  perfil com ORDEM RELATIVA sem âncora temporal absoluta (todos os anteriores ancoravam em coordenada
  absoluta: slot pontual, intervalo de dias, assinatura, order). A POSIÇÃO **não é coluna persistida**
  — é DERIVADA por query (`BarberQueueRepository.countAhead*`: count de tickets 'aguardando' com
  `enqueued_at` menor no mesmo escopo, +1). Atender/desistir de quem está à frente **RECOMPUTA todas
  as posições sem nenhum UPDATE de reordenação**. `enqueued_at` é a âncora de ordem; SEM coluna
  `position`. Regra de escopo do "qualquer barbeiro" (cravada): ticket GERAL (`barber_id` null)
  concorre com TODOS os 'aguardando' à frente; ticket de barbeiro X concorre com os de X + os GERAIS à
  frente (um geral à frente pode pegar o barbeiro X). ETA estimado = soma das durações à frente
  (informativo; a IA apresenta SEMPRE como estimativa explícita "aproximadamente").
- **Modelo (migration 43):** `barber_barbers` + `barber_services` (duration 5..480) + `barber_config`
  (opens/closes + `slot_minutes` + `queue_enabled`) + `barber_appointments` (clone salon_appointments,
  conflito por barber_id) + `barber_queue_tickets` (a entidade nova). FK do ticket→barber é
  `on delete set null` (null = qualquer barbeiro); por isso o delete de barbeiro checa tickets
  EXPLICITAMENTE no service (não só a FK restrict do appointment) → 409 barber_in_use.
- **DUAS máquinas de status hardcoded** (cada uma com parity test Java↔TS):
  `BarberAppointmentStatus` (clone salon: agendado→confirmado/cancelado; confirmado→realizado/cancelado/
  falta) e `BarberQueueStatus` (NOVA: aguardando→chamado/desistiu/expirado; chamado→atendido/desistiu).
- **A IA NÃO move ticket de status (não chama o próximo):** a transição aguardando→chamado é AÇÃO
  HUMANA no painel (espelho do "cancelamento bloqueado por IA" do dental) e dispara a notificação
  "chegou sua vez" (a notificação crítica do walk-in). Só **chamado** (ticket) notifica; os demais são
  silenciosos. NÃO existe callNext automático (fila→agenda) nesta SM — fica no NÃO TEM.
- **DUAS TAGS distintas** (namespace próprio, distintas do `<agendamento>` do salon e de todas as
  outras): `<agendamento_barbearia>` MARCA horário (AgendamentoBarbeariaConfirmHandler, clone salon);
  `<fila_barbearia>` ENFILEIRA (EntrarFilaHandler; `barber_id` opcional null=qualquer; se
  queue_enabled=false → no-op). OutboundService: `maybeProcessAgendamentoBarbearia` +
  `maybeProcessFilaBarbearia` (encadeados após os outros perfis; perfil é único, só um age).
- **Persona BARBER nova** (tom descontraído-acolhedor, sem julgamento estético, sem prometer resultado
  de corte, sem upsell agressivo). Contexto via `BarberContextCache` **TTL 10s — A MAIS CURTA do
  projeto** (abaixo dos 15s do restaurant), porque a fila muda a cada cliente: barbeiros + serviços +
  TAMANHO DA FILA por barbeiro/geral + agenda/slots livres por barbeiro (próximos 3 dias) + histórico
  do contato. Invalidação explícita em toda mutação.
- **Guard:** `BarberProfileGuard` (403 forbidden_wrong_profile). `JwtAuthenticationFilter` autentica
  `/api/barbearia/**` (além dos 10 perfis anteriores).
- **Sidebar:** `getNavForProfile('barbearia')` injeta "Barbearia" (Barbeiros/Serviços/Agenda/Fila/
  Configurações). Telas `/dashboard/barber-{barbers,services,appointments,queue,settings}` — a de fila
  mostra a posição derivada + ETA e o botão "Chamar". Paleta `grafite` (grafite/âmbar).
- **NÃO TEM:** callNext automático (converte ticket em atendimento materializando start_at=now() e
  re-checando a agenda), scheduler de timeout/lembrete da fila, painel de TV/display público/QR,
  pagamento/comanda/gorjeta (Stripe #50), assinatura de cortes recorrentes (academia cobre recorrência),
  foto do corte (bloqueador SERVICE_ROLE_KEY), barbeiro com múltiplas cadeiras paralelas. Fases futuras.
- Guia operacional do tenant: `docs/PERFIL_BARBEARIA.md`.

## Perfil Eventos (EventosBot, camada 8.2)

DÉCIMO SEGUNDO perfil vertical real (sushi/legal/restaurant/dental/salon/pousada/academia/pet/
oficina/nutri/barbearia/eventos) e 13º contando generic. O tenant eventos (`profile_id='eventos'`)
vira um produto de CASA DE FESTAS / BUFFET / CERIMONIAL / ESPAÇO DE EVENTOS: gerencia
cerimonialistas, abre PROPOSTAS (orçamento + cronograma do dia) e captura a aprovação do cliente. A
IA atende clientes via WhatsApp com tom prestativo-consultivo de organizador de festa.

- **CLONA o chassi do OFICINA (camada 7.9):** order-based com itens + total materializado + gate de
  aprovação em 2 fases via tag que muta o estado de um artefato existente. `service_orders →
  event_proposals`; `os_items → event_proposal_items`; `<aprovacao_os> → <aprovacao_proposta>`. A IA
  ABRE a proposta e, num turno POSTERIOR (proposta 'orcada'), MUTA o estado pra 'aprovada'/'recusada'.
- **ESCAPADA ESTRUTURAL — CRONOGRAMA ORDENADO do dia do evento (`event_timeline_items`):** primeiro
  perfil com DOIS tipos de sub-item no MESMO artefato — (1) itens de ORÇAMENTO (`event_proposal_items`:
  descrição+qtd+preço, `line_total_cents` materializado, ENTRAM no `total_cents`) e (2) marcos de
  CRONOGRAMA (`event_timeline_items`: horário+título+descrição, ORDENADOS por `start_time` na leitura,
  NÃO entram no total). O cronograma é o "dia do evento" organizacional (ex.: 19:00 recepção / 20:00
  cerimônia / 23:00 festa). Os `os_items` do Oficina são linhas de PREÇO sem ordem temporal; aqui há
  linhas de PREÇO **e** linhas de CRONOGRAMA. Não confundir.
- **SEM conflito de agenda/data (cravado):** a casa de festas faz ~1 evento/data; `event_date` é
  CAMPO livre na proposta, não recurso disputado por minuto (igual `expected_delivery` do Oficina).
  NÃO há máquina de conflito 409 de data nesta SM. Detecção de "data já tem evento" como aviso
  informativo no painel é fase futura.
- **Cliente NÃO é entidade própria** (continua o contact; snapshots `customer_name`/`customer_phone`
  na proposta — igual oficina/salon). A tag de abertura tem UM modo só (não os 2 modos
  vehicle_id/new_vehicle do Oficina — não há sub-entidade de cliente; a sub-entidade nova é o
  cronograma, filho da PROPOSTA).
- **Modelo (migration 45):** `event_planners` (catálogo simples, ~os_mechanics, SEM agenda) +
  `event_config` (business_name + notes, 1:1, SEM horário/slot — não há agenda) + `event_proposals`
  (snapshots + total materializado) + `event_proposal_items` (preço, entra no total) +
  `event_timeline_items` (cronograma, ordenado por start_time, NÃO entra no total). `total_cents` e
  `line_total_cents` materializados no INSERT/UPDATE de item de orçamento (lição end_at/Oficina).
- **Status hardcoded materializado** (`EventProposalStatus` ↔ `event-proposal-status.ts`,
  `EventProposalStatusParityTest`) — espelho do `OsStatus` adaptado ao funil:
  `rascunho → orcada/cancelada`; `orcada → aprovada/recusada/cancelada`; `aprovada → fechada/cancelada`;
  `fechada → realizada/cancelada`; terminais realizada/recusada/cancelada. Transição inválida → 409
  `invalid_status_transition`. Em 'orcada' exige `total_cents > 0` → 400 `empty_budget`. Notificam
  (texto defensivo): **orcada** (com total), **aprovada**, **fechada**, **recusada**;
  rascunho/realizada/cancelada silenciosos.
  (rascunho = proposta aberta sem orçamento; orcada = aguardando aprovação; aprovada = cliente
  aceitou; fechada = "contrato" fechado/sinal combinado fora do app; realizada = a festa aconteceu.)
- **Trava de itens (`proposal_locked`):** itens (orçamento E cronograma) só mutáveis em
  `rascunho/orcada/aprovada`; em `fechada/realizada/recusada/cancelada` → 409 `proposal_locked`
  (decisão cravada: depois que o contrato fecha o escopo congela; antes disso a equipe ainda ajusta
  orçamento e cronograma).
- **Duas TAGS distintas** (namespace próprio, distintas de TODAS as outras): `<proposta_evento>` ABRE
  a proposta em 'rascunho' (UM modo só — `PropostaEventoConfirmHandler` resolve o contato da conversa;
  total 0, SEM itens — a equipe monta o orçamento no painel, espelho da OS aberta sem itens);
  `<aprovacao_proposta>` MUTA o estado (`decisao` aprovada|recusada, só se a proposta estiver 'orcada')
  — `AprovacaoPropostaHandler`, clone exato do `AprovacaoOsHandler`. OutboundService:
  `maybeProcessPropostaEvento` + `maybeProcessAprovacaoProposta` (encadeados após os outros perfis;
  perfil é único, só um age). Best-effort; o OutboundService REMOVE a tag antes de enviar ao cliente.
- **A IA só ABRE proposta e CAPTURA aprovação** — NÃO move a proposta pra 'fechada'/'realizada' nem
  fecha preço/contrato/desconto (ações administrativas do painel). NUNCA confirma disponibilidade de
  data não confirmada ("vou verificar com a equipe"), NUNCA inventa item/valor de pacote, NUNCA promete
  estrutura do espaço não informada.
- **Persona EVENTOS nova** (`ProfilePromptContext.EVENTOS`, tom prestativo-consultivo). Contexto via
  `EventosContextCache` (TTL 20s — a proposta não muda a cada segundo): cerimonialistas ativos +
  propostas do cliente em aberto (rascunho/orcada) com id+tipo+data+status+total (pra IA capturar a
  aprovação na proposta ORÇADA certa) + instruções e as 2 tags. NÃO injeta o cronograma inteiro
  (organizacional do painel). Invalidação explícita em toda mutação.
- **Guard:** `EventosProfileGuard` (403 forbidden_wrong_profile). `JwtAuthenticationFilter` autentica
  `/api/eventos/**` (além dos 11 perfis anteriores).
- **Sidebar:** `getNavForProfile('eventos')` injeta "Eventos" (Cerimonialistas/Propostas/
  Configurações). Telas `/dashboard/eventos-{planners,proposals,settings}` — a de propostas tem os DOIS
  editores inline (orçamento com total recalculado + cronograma ordenado por horário). Paleta `ambar`
  (dourado/champanhe). POST manual pelo tenant cria proposta sem `conversation_id` (sem WhatsApp) — não
  notifica (sem canal).
- **NÃO TEM nesta SM:** conflito de agenda/data, catálogo de pacotes pré-cadastrados (orçamento ad-hoc,
  igual o Oficina), contrato com assinatura digital/PDF/e-sign (o "contrato" é o estado 'fechada'),
  pagamento/sinal/parcelas (Stripe #50), fornecedores/terceiros como entidades com agenda (multi-pool),
  foto/mood board (bloqueador SERVICE_ROLE_KEY), lista de convidados/RSVP/mesa. Fases futuras.
- Guia operacional do tenant: `docs/PERFIL_EVENTOS.md`.

## Perfil Estética (EsteticaBot, camada 8.3)

DÉCIMO TERCEIRO perfil vertical real (14º contando generic). O tenant estetica
(`profile_id='estetica'`) vira um produto de CLÍNICA DE ESTÉTICA (facial/corporal, drenagem, limpeza
de pele, depilação a laser): gerencia profissionais e procedimentos, vende PACOTES de sessões,
agenda sessões (consumindo o saldo do pacote) e registra a ficha de cada sessão. É o perfil mais
completo até agora — combina três eixos + uma escapada nova.

- **CLONA a AGENDA do SALON (7.5):** conflito POR `professional_id` (`findConflict` transacional,
  janela half-open), duração por procedimento, snapshots (professional_name/procedure_name/
  duration_minutes), `end_at` materializado no INSERT (lição timestamptz+interval). Cliente NÃO é
  entidade (continua o contact; snapshots guest_name/phone).
- **ESCAPADA ESTRUTURAL — PACOTE MULTI-SESSÃO COM SALDO QUE DECREMENTA (`aesthetic_packages`):**
  primeiro nicho com saldo pré-pago consumível. O cliente compra um pacote de N sessões de um
  procedimento; cada agendamento pode CONSUMIR 1 sessão. `sessions_remaining` é MATERIALIZADO (=
  total − used) e re-derivado na MESMA transação do agendamento (defesa de corrida: o UPDATE de
  consumo é condicional `where status='ativo' and sessions_remaining > 0` — fecha a janela no banco).
  Primeiro nicho em que um agendamento mexe num contador pré-pago de OUTRA entidade
  transacionalmente. **Esgotar** (remaining→0) muta o pacote pra `esgotado`; **cancelar** um
  agendamento que consumiu DEVOLVE a sessão (used−1, e `esgotado`→`ativo`). Agendamento AVULSO
  (package_id null) não mexe em saldo.
- **FICHA/EVOLUÇÃO TEXTUAL por sessão (`aesthetic_session_notes`):** 1:1 com o agendamento (área
  tratada / parâmetros do aparelho / observações — texto livre). NÃO editável se o agendamento está
  cancelado. SEM FOTO (bloqueador SERVICE_ROLE_KEY). LGPD: registro administrativo-estético, NÃO
  prontuário médico (dado clínico sensível é fase futura com cripto at-rest).
- **Modelo (migration 46):** `aesthetic_professionals` + `aesthetic_procedures` (duration +
  `unit_price_cents` = preço de UMA sessão) + `aesthetic_config` (horário + slot) +
  `aesthetic_packages` (a escapada) + `aesthetic_appointments` (+ `package_id` nullable +
  `consumed_session`) + `aesthetic_session_notes` (ficha 1:1).
- **DOIS status hardcoded materializados** (cada um com parity test Java↔TS):
  `AestheticAppointmentStatus` (clone salon: agendado→confirmado/cancelado; confirmado→realizado/
  cancelado/falta) e `AestheticPackageStatus` (NOVO: pendente→ativo/cancelado; ativo→esgotado/
  expirado/cancelado — mas `esgotado` NÃO é destino MANUAL, é materializado pelo backend; a
  reabertura `esgotado`→`ativo` ao devolver sessão também é do backend, fora da máquina manual).
  Notificam: appointment **confirmado/cancelado**; package **ativo** (boas-vindas).
- **A IA NÃO inventa preço (regra de ouro):** na compra de pacote, `total_cents = total_sessions ×
  unit_price` do procedimento no catálogo — a tag NÃO carrega preço. A IA NÃO confirma pagamento (o
  pacote nasce `pendente`; só o tenant ativa, e a ativação libera o agendamento que consome saldo).
- **TRAVA ESTÉTICA (persona):** a IA NUNCA indica/recomenda procedimento, NUNCA opina sobre o corpo/
  aparência, NUNCA promete resultado, NUNCA discute contraindicação/condição de saúde (encaminha à
  avaliação). Espelho da trava clínica do dental/nutri, adaptada.
- **Duas TAGS distintas** (namespace próprio): `<agendamento_estetica>` agenda (com `package_id`
  opcional → consome saldo) — `AgendamentoEsteticaConfirmHandler`; `<compra_pacote>` registra a
  intenção de compra (pacote `pendente`, preço do catálogo) — `CompraPacoteConfirmHandler`.
  OutboundService: `maybeProcessAgendamentoEstetica` + `maybeProcessCompraPacote` (encadeados após os
  outros perfis; perfil é único, só um age).
- **Persona ESTETICA + contexto** (`EsteticaContextCache` TTL 20s): procedimentos (com preço de
  sessão), profissionais, PACOTES ATIVOS do cliente com saldo (pra IA agendar consumindo o package_id
  certo), slots por profissional (7 dias), + as 2 tags. Invalidação em toda mutação.
- **Guard:** `EsteticaProfileGuard` (403 forbidden_wrong_profile). `JwtAuthenticationFilter` autentica
  `/api/estetica/**` (além dos 12 perfis anteriores).
- **Sidebar:** `getNavForProfile('estetica')` injeta "Estética" (Profissionais/Procedimentos/Pacotes/
  Agenda/Configurações). Telas `/dashboard/estetica-{professionals,procedures,packages,appointments,
  settings}` — a de Pacotes é a tela da escapada (saldo). Paleta `rosa-po`. POST manual de
  agendamento é avulso (não consome pacote — o consumo é via IA na conversa).
- **NÃO TEM nesta SM:** foto antes/depois, prontuário/anamnese estruturada (dado sensível — fase
  futura com cripto), pagamento real do pacote (Stripe #50), assinatura/recorrência de pacote
  (academia cobre recorrência), comissão de profissional, estoque, multi-unidade. Fases futuras.
- Guia operacional do tenant: `docs/PERFIL_ESTETICA.md`.

## Perfil Adega (AdegaBot, camada 8.9)

DÉCIMO OITAVO perfil vertical real (19º contando generic). O tenant adega (`profile_id='adega'`) vira
um produto de ADEGA / DELIVERY DE BEBIDAS (vinhos, espumantes, cervejas, destilados, sem-álcool,
acessórios): a IA atende clientes via WhatsApp, monta o pedido na conversa (carrinho relido do
histórico, igual sushi/comida), **confirma a maioridade**, e a loja acompanha num Kanban com gate de
aceite. (Os perfis comida 8.4, floricultura 8.5 e pizzaria 8.6 estão documentados em
`docs/PERFIL_*.md` + memória; a fila de nichos vive em `docs/prompts-gordos/README.md`.)

- **CLONA o chassi do COMIDA (camada 8.4):** cardápio + OPÇÕES/modifiers + carrinho-na-conversa + tag
  de pedido + recálculo de total (descarta o da IA) + snapshot de preço/nome + taxa/mínimo + Kanban +
  GATE DE ACEITE HUMANO (nasce 'aguardando'; a loja aceita→'em_preparo' ou recusa→'recusado' com
  rejection_reason; a IA não aceita/recusa; 'aguardando' não notifica).
- **ESCAPADA — TRAVA DE FAIXA ETÁRIA (+18, o coração da SM):** a venda de álcool exige confirmação de
  MAIORIDADE. `adega_orders.age_confirmed` é **boolean NOT NULL sem default** — só `true` entra. A IA
  confirma na conversa e emite a tag com `"age_confirmed":true`; sem o flag (ou false) o
  `PedidoAdegaConfirmHandler` ABORTA sem criar pedido, e `AdegaOrderService.create` lança
  `AgeNotConfirmedException` → **422 `age_not_confirmed`** ANTES de qualquer cálculo. **NÃO há pedido
  "menor de idade" no banco.** Flag DECLARATÓRIO (sem doc/foto nesta SM), persistido pra compliance,
  visível no painel como selo "+18 confirmado". A trava vale **mesmo pra carrinho 100% sem-álcool**
  nesta SM (dispensa é fase futura). É a regra que justifica adega ser perfil próprio, não um preset
  do comida.
- **Modifiers (Volume, Temperatura):** `adega_menu_item_options` (group_label + price_delta_cents,
  espelho exato do comida). `unit_price = base + Σ deltas`, × qtd, recalculado no backend. **SEM regra
  do maior valor** (isso é do pizzaria — adega não tem meio-a-meio).
- **Categorias hardcoded** (`AdegaCategory` ↔ `adega-categories.ts`, parity): vinhos, espumantes,
  cervejas, destilados, sem_alcool, acessorios.
- **Status hardcoded** (`AdegaOrderStatus` ↔ `adega-order-status.ts`, parity): mesma máquina do comida
  (aguardando→em_preparo/recusado/...); textos de notificação tematizados ("separando suas bebidas
  🍷", "Beba com moderação"). Os ids são iguais ao comida (1:1 pra não divergir o enum/parity).
- **Persona ADEGA nova** (`ProfilePromptContext.ADEGA`, tom cordial-consultivo): confirma maioridade
  ANTES de fechar; NUNCA vende a menor; NUNCA incentiva consumo excessivo; inclui "Beba com
  moderação"; pode sugerir harmonização entre o que JÁ está no cardápio. Contexto via
  `AdegaMenuCache` (TTL 60s, invalidado em toda mutação) com a REGRA +18 e o campo `age_confirmed`
  ensinados na tag. IGNORA conversationId (contexto é o cardápio).
- **Tag `<pedido_adega>`** (namespace próprio, distinta de `<pedido_comida>`/`<pedido_flor>`/
  `<pedido_pizza>` e de todas): carrega `age_confirmed` + items + endereco. `OutboundService`
  ganhou `maybeProcessPedidoAdega` (encadeado após comida/floricultura/pizzaria — perfil é único, só
  um age); remove a tag antes de enviar.
- **Guard:** `AdegaProfileGuard` (403 forbidden_wrong_profile). `JwtAuthenticationFilter` autentica
  `/api/adega/**` (além dos 17 perfis anteriores).
- **Sidebar:** `getNavForProfile('adega')` injeta "Adega" (Catálogo/Pedidos/Configurações), branch
  próprio. Telas `/dashboard/adega-{menu,orders,settings}` — a de Pedidos mostra o selo "+18
  confirmado". Paleta `beringela`.
- **NÃO TEM:** clube/assinatura de vinho (academia cobre recorrência), curadoria de safra, estoque,
  cupom/fidelidade, Zé Delivery/iFood, rastreio, validação documental de idade (declaratório nesta
  SM), dispensa +18 p/ carrinho sem-álcool, pagamento (Stripe #50), foto (bloqueador
  SERVICE_ROLE_KEY), scheduler de auto-transição.
- Migration `53_adega.sql` (slot atribuído pelo `docs/prompts-gordos/README.md`; 51/52 reservados a
  casamento/padaria). Tenant `igorhaf20` (Adega Modelo). Guia: `docs/PERFIL_ADEGA.md`.

## Perfil Escola (EscolaBot, camada 8.19)

DÉCIMO NONO perfil vertical real (20º contando generic). O tenant escola (`profile_id='escola'`) vira
um produto de ESCOLA / EDUCAÇÃO INFANTIL: gerencia turmas (série+turno+capacity+mensalidade), cadastra
os alunos de cada responsável, matricula alunos (assinatura) e agenda visitas da família. A IA atende
os RESPONSÁVEIS (pais) via WhatsApp com tom acolhedor-cuidadoso.

- **CLONA o chassi da ACADEMIA (camada 7.7):** planos→TURMAS (com capacity), matrícula = ASSINATURA
  (ativa/suspensa/cancelada), anti-dupla, capacity por turma validado TRANSACIONALMENTE no INSERT
  (defesa race), mensalidade MANUAL (`escola_payments`, UNIQUE por mês → 409 duplicate_payment; sem
  Stripe/inadimplência). suspensa MANTÉM a vaga; cancelada LIBERA + materializa end_date.
- **ESCAPADA 1 — o ALUNO é SUB-ENTIDADE do RESPONSÁVEL (espelho pet/nutri):** na academia o "aluno"
  era o próprio contato; aqui o responsável (pai/mãe) é o `contact` e o aluno (filho) é
  `escola_students` (contact_id NOT NULL). 1 responsável → N alunos. A matrícula referencia
  `student_id`. **Anti-dupla = 1 matrícula ATIVA por (aluno, turma)** (índice parcial
  `uniq_active_enrollment_per_student_class`): um irmão pode estar na mesma turma; o mesmo aluno pode
  estar em turmas diferentes; o mesmo aluno NÃO pode ter 2 ativas na MESMA turma.
- **ESCAPADA 2 — VISITA agendada (agenda LEVE dia+período, espelho floricultura):** `escola_visits`
  (`visit_date >= hoje` + `period` manha|tarde), SEM conflito de capacidade, SEM slot fino. Entidade
  própria com status próprio (agendada/realizada/cancelada). A visita INDEPENDE de matrícula e de aluno
  (`student_id` nullable). Os dois conceitos juntos (assinatura-por-aluno-sub-entidade + agenda leve de
  visita) é a escapada desta SM.
- **DOIS status hardcoded** (cada um com parity Java↔TS): `EscolaEnrollmentStatus` (ativa⇄suspensa;
  ambas→cancelada) ↔ `escola-enrollment-status.ts`; `EscolaVisitStatus` (agendada→realizada/cancelada)
  ↔ `escola-visit-status.ts`. shift (manha/tarde/integral) e period (manha/tarde) por CHECK simples;
  grade/intended_grade texto livre.
- **DUAS tags namespace próprio** (distintas de `<matricula>` da academia e de TODAS): `<matricula_escola>`
  (2 modos — `student_id` existente OU `new_student` cadastra o aluno sub-entidade E matricula, espelho
  `new_animal` do pet) via `MatriculaEscolaConfirmHandler`; `<visita_escola>` via
  `VisitaEscolaConfirmHandler`. `OutboundService` ganhou `maybeProcessMatriculaEscola` +
  `maybeProcessVisitaEscola` (encadeados após os demais; perfil é único, só um age; removem a tag).
- **Persona ESCOLA** (`ProfilePromptContext.ESCOLA`, acolhedora-cuidadosa): NUNCA promete vaga não
  confirmada (fala em "registrar interesse"/"pré-reservar"), NUNCA define/inventa mensalidade/desconto/
  bolsa, NUNCA dá parecer pedagógico (encaminha à coordenação), NUNCA inventa turma/série/turno/
  professor. Contexto via `EscolaContextCache` (TTL 60s, keyed por (companyId, contactId) — traz os
  filhos do responsável + turmas com vagas restantes), invalidado em toda mutação.
- **Guard:** `EscolaProfileGuard` (403 forbidden_wrong_profile). `JwtAuthenticationFilter` autentica
  `/api/escola/**` (além dos 18 perfis anteriores).
- **Sidebar:** `getNavForProfile('escola')` injeta "Escola" (Turmas/Alunos/Matrículas/Visitas/
  Configurações), branch próprio. Paleta `mostarda`.
- **NÃO TEM:** boletim/notas/frequência/diário de classe, parecer pedagógico estruturado, pagamento
  real (Stripe #50)/inadimplência, contrato e-sign, lista de espera persistida, transporte/material/
  merenda, foto (bloqueador SERVICE_ROLE_KEY), slot fino na visita, multi-unidade, calendário letivo.
- Migration `63_escola.sql` (slot do `docs/prompts-gordos/README.md`; 54-62 reservados aos nichos
  intermediários). Tenant `igorhaf30` (Escola Modelo). Guia: `docs/PERFIL_ESCOLA.md`.

## Camada 9.0 — Feature Flags por Nicho (infra de plataforma)

Infra pro ROOT (super-admin) ligar/desligar features por nicho num lugar só. A 1ª feature é o **CMS**
(página pessoal por tenant); a SM-L só entrega a INFRA + o gate — o CMS real vem na SM-M.

- **Feature é HARDCODED** (`ProfileFeature` enum Java ↔ `frontend/lib/profiles/profile-feature.ts`,
  `ProfileFeatureParityTest`) — mesmo padrão dos status. Membro inicial: `CMS` (key `'cms'`, label
  "Página pessoal (CMS)"). Adicionar feature = editar os 2 arquivos + estender a CHECK de
  `profile_features.feature_key` (migration) + rodar a paridade.
- **Perfis seguem HARDCODED** (`ProfileType`); NÃO há tabela de perfis. A grade é COMPUTADA: itera
  os nichos CONFIGURÁVEIS × `ProfileFeature.allActive()` e sobrepõe as linhas do banco.
- **`generic` ("Meada") NÃO entra na grade nem aceita toggle** (cravado): o genérico é o produto do
  próprio admin/root, que não é um tenant-cliente de nicho — features (CMS = página pessoal por
  tenant) só fazem sentido pros nichos VERTICAIS; o root já tem o que precisa embutido, sem flag.
  `ProfileFeatureService.configurable(p)` (= `p != GENERIC`) gateia `grid()` e `setFlag()` (generic
  no PUT → 400 `unknown_profile`). Os 10 nichos verticais são configuráveis.
- **Tabela `profile_features`** (migration 40) guarda SÓ os DESVIOS do default. **Ausência de linha =
  OFF** — o resolver (`ProfileFeatureService`) trata como false. **Default de toda feature = OFF**
  (opt-in explícito do root: tem nicho sem site, então off-pra-todos é o estado natural). PK
  `(profile_id, feature_key)`, `enabled`, `updated_at`, `updated_by`.
- **SEM CHECK de `profile_id`** na tabela (não acopla a cada perfil futuro) — validação APP-LEVEL no
  controller (`profile_id ∈ ProfileType`, `feature_key ∈ ProfileFeature` → 400 `unknown_profile`/
  `unknown_feature`). CHECK de `feature_key` no conjunto conhecido (`in ('cms')`) é OK (features são
  poucas; cada nova é migration+enum+parity deliberados).
- **Tabela de PLATAFORMA** (espelha `public.plans`): `enable+force RLS`, só `grant all ... service_role`
  (sem grant a `authenticated`). O TENANT NÃO lê a tabela direto — recebe o resolvido via
  `GET /admin/me.features` (mapa `{key → enabled}` do seu nicho). O root opera via Spring/service_role.
- **Authz dos endpoints root:** a MESMA dos demais `/admin/*` de plataforma — `user.role() ==
  AdminRole.SUPER_ADMIN`, inline (`notSuperAdmin(user)` → 403 `forbidden_not_super_admin`). Sem role
  nova. `GET /admin/profile-features` (grade) + `PUT /admin/profile-features/{profileId}/{featureKey}`
  `{enabled}` (upsert + audita `PROFILE_FEATURE_TOGGLED` em `admin_action_log` + invalida cache).
- **Gate `ProfileFeatureGuard.requireFeature(user, ProfileFeature)`** → resolve o perfil do company,
  checa `enabledFor`; OFF → `FeatureDisabledException` (→ 403 `feature_disabled`). É onde a SM-M
  pendura o CMS: `requireFeature(user, ProfileFeature.CMS)` no início dos endpoints do CMS.
- **Cache** Caffeine TTL 20s keyed por `profileId`, invalidado em todo `setFlag` (igual aos context
  caches dos perfis). `enabledFor`/`resolvedMap` leem do cache.
- **Frontend:** root tem a tela-grade `/dashboard/profile-features` (grupo "Plataforma",
  `superAdminOnly`) — linhas=nichos, colunas=features, célula=toggle (PUT). Tenant: `me.features` +
  `hasFeature(me, key)` em `lib/api/me.ts`; `getNavForProfile(profileId, features)` recebe `features`
  como **plumbing** (threadado de `/admin/me` via app-shell→sidebar) — a SM-M só adiciona UM item de
  nav atrás de `features?.cms === true`. Esta SM NÃO adiciona item de CMS.
- **Pendência:** SM-M = CMS real (telas + endpoints atrás do `requireFeature(CMS)` + item de nav).
- Doc: `docs/FEATURE_FLAGS.md`.

## Camada 9.x — CMS / Site por tenant (SM-M base + SM-N multi-página, page builder)

PRIMEIRA feature gateada pela infra de feature flags (camada 9.0). O tenant cujo nicho tem o CMS
ligado monta um SITE com N PÁGINAS de BLOCOS (page builder), tema próprio, e pode apontar um
DOMÍNIO próprio com posse verificada.

- **Gateado por `requireFeature(CMS)`:** todos os `/api/cms/**` chamam
  `ProfileFeatureGuard.requireFeature(user, ProfileFeature.CMS)` → 403 `feature_disabled` se o nicho
  não tem a flag. É o consumo real do gate da SM-L.
- **Modelo (migration 41 + 42):** SM-M criou `cms_pages` 1:1; a SM-N REFATOROU em `cms_sites`
  (config 1:1 com company: domain, domain_verified, verify_token, theme jsonb, published) + NOVA
  `cms_pages` (N por company: page_slug, title, blocks, is_home, position, published). 1 home por
  company (índice parcial UNIQUE); UNIQUE (company_id, page_slug).
- **Blocos (8):** `CmsBlockType` (enum Java ↔ `cms-block-type.ts`, `CmsBlockTypeParityTest`):
  `hero`, `text`, `services`, `contact`, `gallery`, `faq`, `testimonials`, `map`. `blocks` é JSONB
  ordenado de `{id, type, props}`; validação app-level no `CmsService` (normaliza, gera id, rejeita
  type inválido → 400 `invalid_blocks`). Galeria é por URL colada (upload bloqueado — SERVICE_ROLE_KEY).
- **Tema:** `cms_sites.theme` jsonb (`{primaryColor, dark}`) aplicado no render (CSS var + claro/escuro).
- **Domínio + posse:** `setDomain` valida hostname/lowercase, rejeita hosts do Meada → 400
  `invalid_domain`, UNIQUE → 409 `domain_taken`; mudar o domínio ZERA a verificação. Posse por TXT:
  `startDomainVerification` gera `verify_token`; o tenant publica `_meada-verify=<token>` no DNS;
  `verifyDomain` consulta via `DnsTxtResolver` (impl `JndiDnsTxtResolver` — JNDI DNS, sem lib;
  mockável nos testes) e marca `domain_verified`. A página por domínio e o cert SÓ valem verificados.
- **Público (sem auth):** `/public/cms/by-slug/{slug}` (home), `/by-slug/{slug}/{pageSlug}`,
  `/by-domain?host=` (+ `/{pageSlug}`), e `/public/cms/tls-allowed?domain=` (ask do Caddy). Fora da
  allowlist do JwtFilter. Só servem PUBLICADO (site + página); a view traz title+blocks+theme+nav,
  nunca campos internos.
- **Roteamento público:** Next `/p/{slug}` + `/p/{slug}/{pageSlug}` (server components, sem shell) +
  `/p/by-domain/{host}[/{pageSlug}]`. `middleware.ts` reescreve host não-Meada (`isMeadaHost`) →
  `/p/by-domain/{host}/{subpath}`. SSR usa `CMS_BACKEND_URL=http://backend:8095` (rede do compose).
- **HTTPS de domínio custom (infra, runbook):** template `on_demand_tls` no `caddy/Caddyfile`
  (comentado — não ativar em dev) com `ask` apontando pro `/public/cms/tls-allowed` (só emite cert
  pra domínio verificado+publicado). Emissão real + DNS apontado é operação de PROD (ver docs/CMS.md);
  não testável no smoke local.
- **Editor (painel tenant):** `/dashboard/cms`, nav "Site → Página" gateado por `features?.cms`.
  Multi-página (criar/excluir/definir-home, publicar por página), blocos com drag-drop NATIVO (HTML5)
  + ↑↓, editor de props por tipo (`components/cms/block-editor.tsx`), tema (cor + dark), domínio +
  verificação (mostra o token TXT + botão Verificar).
- **Caveat (cache de feature flag):** `ProfileFeatureService.enabledFor` cacheia TTL 20s — toggle do
  CMS leva até 20s pra propagar. Nos testes isso causava cache-bleed; resolvido com `invalidateAll()`
  no truncate do `AbstractIntegrationTest`.
- **NÃO TEM:** upload de imagem (URL colada), cert HTTPS testado em dev (só runbook), editor visual
  WYSIWYG (é form por bloco), versionamento/preview de rascunho separado do publicado.
- Doc: `docs/CMS.md`.

## Estado das camadas

- **1 — Schema multi-tenant:** FECHADA. 11 tabelas, RLS, FKs compostas anti-cross-tenant.
- **2 — Webhook Evolution inbound:** FECHADA.
- **3 — IA + outbound:** FECHADA, validada E2E.
- **4 — Painel admin:** em andamento.
  - 4.0 scaffold + login · 4.1 super-admin lista empresas (JWT ES256/JWKS) ·
    4.2 super-admin cria empresa · 4.3 tenant vê sua empresa (SDK+RLS) ·
    4.4 tenant lista+cria services (1º WRITE via SDK+RLS). Todas FECHADAS (tags).
  - Próximo: 4.4.x (update/delete de services; replicar para faqs/business_hours/
    ai_settings) · 4.5 ver conversas · 4.6 parear Evolution pelo painel.

## Incidente registrado (ver RISKS.md)

Re-sync de histórico do Baileys/Evolution disparou respostas automáticas a contatos
reais no boot (2026-06-10). MITIGADO: dry-run em local (`EVOLUTION_DRY_RUN=true`,
`EvolutionClient` loga em vez de enviar) + guard de frescor por `messageTimestamp`
(`webhook.message-max-age-seconds`, rejeita `messages.upsert` antigos). **Webhook
permanece OFF até religar consciente.** Não religar sem verificar dry-run + threshold.
