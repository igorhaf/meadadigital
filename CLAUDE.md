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
