# CLAUDE.md — Meada

Instruções para qualquer instância do Claude trabalhando neste projeto. Leia antes de
agir. Estas regras foram cravadas pelo Igor ao longo das sessões e têm precedência.

> Este arquivo é **território do Claude Code** (como DEVELOPMENT.md e RISKS.md): o agente
> lê, cria seções, atualiza convenções e registra lições sem precisar de autorização por
> edição. NÃO confundir com `.env`/`.env.local` (segredos, território do Igor).
>
> **Dieta de contexto (2026-07, autorizada pelo Igor):** este arquivo documenta o INVARIANTE
> (chassis, regras, lições). O detalhe de cada perfil vive em `docs/PERFIL_<NICHO>.md`
> (1 guia por nicho — migration, tenant, paleta, endpoints, telas) e na memória. Ao criar
> um perfil novo, NÃO adicionar seção longa aqui: 1 linha na tabela do catálogo + guia em docs/.

## O que é o projeto

SaaS multi-empresa de atendimento ao cliente via WhatsApp com IA. Cada empresa (tenant)
tem um atendente de IA treinado com seus próprios dados, respondendo clientes pelo
WhatsApp. Dados isolados por tenant via RLS. O monolito se apresenta como N produtos
verticais ("perfis") — ver Multi-perfil abaixo.

- **Backend:** Spring Boot 3.3.13 + Java 17 Temurin, single-module Maven. JdbcTemplate
  (não JPA), sem Lombok, sem webflux. HTTP outbound síncrono via RestClient.
- **Frontend:** Next 16 (app router) + React 19 + TypeScript + Tailwind 4 + shadcn/ui 4
  + @base-ui/react (NÃO Radix) + TanStack Query + react-hook-form + zod + @supabase/ssr.
  Em `frontend/`, isolado do Maven.
- **Banco/Auth:** Supabase (Postgres 17 + Auth + Storage). IA: Gemini Flash.
  WhatsApp: Evolution API self-hosted (evoapicloud/evolution-api:v2.3.1).
- **Detalhes operacionais vivos** (portas, envs, credenciais, estado das camadas):
  ver `CONTEXT.md` na raiz — gitignored e mais detalhado que este arquivo.

## Bootstrap do zero (clone → ambiente rodando)

Pré-requisitos: **Java 17 Temurin** (`/usr/lib/jvm/temurin-17-jdk-amd64`), **Node + npm**,
**Maven 3.8+**, **Docker** (para a Evolution local). Banco/Auth são **Supabase LOCAL via CLI**
desde 2026-07-01 (`supabase start` — API/Auth em `:54321`, Postgres em `:54322`) com o schema
das migrations aplicado. (Antes era Supabase remoto via Session pooler — não vale mais.)

1. **Backend env:** `cp .env.example .env` e preencher (`WEBHOOK_SECRET`, `GEMINI_API_KEY`/
   `GEMINI_MODEL`, `EVOLUTION_BASE_URL`, `SUPABASE_JWKS_URL`, `ADMIN_SUPER_ADMIN_EMAILS`,
   `EVOLUTION_DRY_RUN=true` em dev, `SERVER_PORT=8095`). `.env` é gitignored.
2. **Frontend env:** `cd frontend && cp .env.example .env.local` e preencher
   (`NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_ANON_KEY` — anon é pública por design,
   protegida por RLS; NUNCA service_role aqui —, `NEXT_PUBLIC_API_URL=http://localhost:8095`).
3. **Evolution local (opcional, só p/ fluxo WhatsApp):**
   `cd evolution-local && docker compose up -d` (API :8086, Postgres :5433, Redis :6380).
4. **Backend:** `./scripts/run-local.sh` (porta 8095). Sanity: `GET /admin/me` sem token →
   401 `missing_auth_header` (não há rota raiz/actuator — 404 na raiz é saudável).
5. **Frontend:** `cd frontend && npm install && npm run dev` (porta 3000).
6. **Usuários de teste no Supabase Auth** (painel → Authentication → Users, "Auto Confirm"):
   um **super-admin** (email na allowlist `ADMIN_SUPER_ADMIN_EMAILS`, SEM linha em
   `public.users`) e um **tenant-admin** (linha em `public.users` com `company_id` + `role`).
   Senhas vivem só em comunicação direta — nunca em arquivo.
7. **Logar:** `http://localhost:3000/login`.

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
  escrito — o Write tem que aparecer.
- **Honestidade sobre incertezas.** Não improvisar workaround sem consultar. Não
  fabricar resultados de teste — reportar literal sempre, inclusive falhas.
- **Conferir o estado real antes de "consertar".** Não propor fix de algo sem checar
  no código/banco que o problema existe.
- **Nunca assumir "arquivo vazio" por leitura parcial.** `wc -c`/`cat` antes de decidir
  overwrite vs append.
- **Contagem de testes vem do Surefire (`Tests run: N`), nunca de `grep @Test`.**
- **Agente NÃO edita CLAUDE.md por iniciativa própria.** A edição vem por trabalho
  cravado pelo Igor ou pelo arquiteto, NUNCA por dedução do agente sobre "regra
  implícita". Quando em dúvida, perguntar antes de editar.

## Paralelismo dentro de uma sessão

Igor é o arquiteto único; aceleração vem de paralelizar DENTRO da rodada:
leituras/queries/greps independentes disparados juntos na mesma resposta; Task tool
(sub-agentes) só pra trabalho disjunto não-trivial; agrupar fases sem aval intermediário
numa rodada só (pausar só em ponto real de decisão: bifurcação arquitetural, bruto pra
aprovar, smoke pré-commit). NÃO paralelizar quando há dependência sequencial. Ganho
realista: 30-50% por sessão — a revisão do arquiteto continua sendo o limite.

## Padrão de git

- **Commits semânticos**, mensagem em português, prefixo `feat/fix/docs/chore(camada-N):`.
  Multi-linha via `git commit -F <arquivo>` (não `-m`).
- **`git add` arquivo a arquivo, lista explícita. NUNCA `git add .` nem wildcard.**
- **Sanity de staging antes de todo commit:** `git status -s` + `git diff --staged --stat`
  + grep por segredo (`eyJ`, `password`, `secret=`) + confirmar `.env`/`.env.local` FORA.
  Confirmar com o Igor antes do commit (salvo empreitada com autonomia cravada).
- **Segredos nunca são commitados.** `.env`, `frontend/.env.local`, `evolution-local/.env`
  e `CONTEXT.md` são gitignored. Validar presença de env sem expor valor. **Senhas nunca
  vão em arquivo.**
- **Tag anotada por sub-fase fechada:** `git tag -a fase-N.M-fechada -F <msg>`.
- **Trailer obrigatório:** `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.
- **Remote:** `origin` = `https://github.com/igorhaf/meada.git`, branch `main`.
  NUNCA `git push --force`.
- **Critério de "fechado"** — backend: `mvn -B clean test` verde (contagem do Surefire).
  Frontend: `next build` limpo (Turbopack dev é lazy e esconde import quebrado) + smoke.

## Comandos de boot (ambiente local)

- **Backend** (Spring, 8095): `./scripts/run-local.sh`. Sanity: `GET /admin/me` → 401.
- **Frontend** (Next, 3000): `cd frontend && npm run dev`.
- **Testes backend:** `JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64 mvn -B clean test`.
- **Build frontend:** `cd frontend && npm run build`.
- **Banco (psql):** Supabase LOCAL (CLI) — `psql -h 127.0.0.1 -p 54322 -U postgres -d postgres`;
  senha em `SPRING_DATASOURCE_PASSWORD` do `.env`. `SUPABASE_URL=http://127.0.0.1:54321`
  (backend); o frontend usa o IP do WSL (`http://172.27.153.135:54321`) pra alinhar o nome do
  cookie `sb-172-auth-token` entre browser e SSR (lição do redirect loop de 2026-07-01).
  Migration nova: `psql ... -f supabase/migrations/NN_x.sql` ao fechar a onda.
  Smoke E2E real: login via `POST {SUPABASE_URL}/auth/v1/token?grant_type=password` → token →
  backend ou PostgREST.

## Padrões de código (skills em .claude/skills/)

O padrão canônico de cada camada vive em skills — consulte ANTES de criar/editar código:

| Skill | Cobre |
|-------|-------|
| `frontend-components` | App Router, Server×Client, `type`, imports `@/`, FormState, hooks de sync, diálogos, nav por perfil |
| `tailwind-styling` | tokens do tema, cn() × template literal, primitivos de ui/, formulários, tabelas, paleta por nicho |
| `nextjs-data-fetching` | TanStack Query, apiFetch/ApiError, queryKeys, invalidação, erro por `reason`, REST × SDK legado |
| `spring-controllers` | pacote por feature, DI por construtor, records, guard, REST, conflito transacional, snapshots, handlers de tag, testes |
| `spring-error-handling` | contrato `{error, reason}`, mapa HTTP→reason, best-effort, gotchas |
| `docker-infra` | Dockerfiles multi-stage, compose, env (nunca valores), Caddy, portas |

- **Lint frontend:** `cd frontend && npm run lint` — estado: **0 erros** (só 3 warnings
  `react-hooks/incompatible-library`, informativos). NÃO reintroduzir erro.
- **Formatação frontend:** `cd frontend && npm run format` (Prettier + plugin-tailwindcss +
  sort-imports). Ordem de classes e de imports são MECÂNICAS — rodar o comando, não corrigir
  na mão. `npm run format:check` verifica.
- Backend sem lint — gate é `mvn -B clean test` + convenções das skills.

## DevX local (Docker — fase 0.5)

Dev local em **docker-compose** (porta 80 era interceptada pelo Apache): `backend` (8095,
hot-reload por volume), `frontend` (3000, volume), `embeddings` (7080), `caddy` (proxy 80,
vhosts `*.meadadigital.local` + `api.meadadigital.local`). O BANCO fica fora (Supabase CLI).
Subir/derrubar: `./scripts/meada-up.sh` / `./scripts/meada-down.sh`. Overrides de rede vão
no `environment` do compose (`.env` intocado). **Testes rodam no HOST** (Testcontainers).
Tenants reais persistem no Supabase entre up/down.

## Usuários de teste (senhas só em comunicação direta)

- **super-admin:** `igorhaf@gmail.com` (allowlist). **tenant-admin:** `igorhaf2@gmail.com`
  (Empresa Alpha `52e88a0b-...`, role admin). Seeds: Alpha, Beta (`38cdac12-...`), Meada Delta.
- Tenants de nicho: 1 por perfil (`igorhaf3`..`igorhaf35` — o guia de cada perfil documenta
  o seu).

## Arquitetura de auth (camada 4 — painel admin)

- **super-admin** (meada): allowlist `ADMIN_SUPER_ADMIN_EMAILS`, opera via Spring/
  service_role, FORA do RLS. **tenant-admin**: Supabase SDK + RLS no CRUD interno legado do
  core; nichos e chamadas externas via Spring REST.
- JWT Supabase ES256 validado por JWKS (`JwtAuthenticationFilter` próprio, não Spring
  Security). RLS via `app.company_id()`. WRITE via SDK exige `company_id` explícito (policy
  WITH CHECK revalida).

## Multi-perfil (camada 7.0) — regras invariantes

- **Meada é um monolito que se apresenta como N produtos verticais ("perfis").** Cada perfil
  parece um produto distinto (subdomínio, nome, tom de IA, features próprias).
- **Perfis são HARDCODED** em dois arquivos espelhados: `ProfileType.java` (enum) e
  `frontend/lib/profiles/profile-type.ts` (const). `ProfileTypeParityTest` falha o build se
  divergirem. NÃO existe tabela de perfis.
- **Tenant tem EXATAMENTE 1 perfil** (`companies.profile_id`, CHECK com todos os ids),
  cravado pelo root; o tenant não escolhe.
- **Perfis coexistem HARMONICAMENTE:** feature de um NUNCA quebra outro. Conflito → resolver
  com condicional explícita por `profile_id`, NUNCA generalizar à força.
- **Features genéricas (núcleo) valem em TODOS os perfis.** Específicas vivem em
  `src/main/java/.../profiles/{perfil}/` e `frontend/profiles/{perfil}/`.
- **NUNCA remover um nicho ao adicionar outro (CRAVADO).** Adicionar é sempre ACRESCENTAR —
  a CHECK e o enum preservam TODOS os existentes. Armadilha real: clonar migration por sed
  troca o id NA LISTA da CHECK (removendo os demais). Depois de qualquer clonagem, CONFERIR
  a CHECK completa. **Preferir o gerador: `scripts/gerar-perfil.py` (elimina a armadilha).**
- **Subdomínio → perfil:** middleware Next injeta `x-meada-subdomain`; `localhost`/domínio-base
  = 'meada' (login universal). Dev: `docs/MULTI_PROFILE_DEV.md`.

## Anatomia de um perfil (o que TODO nicho tem)

1. **Enum + parity:** id no `ProfileType` + `profile-type.ts`; enums locais de status/categoria
   com espelho TS e `*ParityTest` cada.
2. **Migration própria** com as tabelas do nicho + reescrita da CHECK de `companies.profile_id`
   (lista COMPLETA).
3. **Guard:** `<Nicho>ProfileGuard.require<Nicho>` → 403 `forbidden_wrong_profile`; prefixo
   `/api/<nicho>/**` autenticado no `JwtAuthenticationFilter`.
4. **Tags de IA com namespace PRÓPRIO** (`<pedido_x>`, `<agendamento_x>`…): parse por REGEX em
   handler best-effort (`hasTag`/`stripTag`/`parseAndCreate`) — NÃO tool calling (conflita com
   o responseSchema do outbound). `OutboundService` ganha `maybeProcessX` encadeado (perfil é
   único → só um age) e REMOVE a tag antes de enviar. Total/preço da tag é DESCARTADO — o
   backend recalcula do catálogo.
5. **Persona** em `ProfilePromptContext.<NICHO>` com as TRAVAS do domínio (clínica: nunca
   diagnóstico/prescrição; estética: nunca opinar sobre corpo/resultado; varejo restrito:
   +18, não-prescrição; serviço: nunca fechar preço/contrato/prazo não cravado). Travas são
   INEGOCIÁVEIS e vivem na persona E no schema (a IA não tem a capacidade perigosa).
6. **Contexto dinâmico** via `<Nicho>ContextCache`/`MenuCache` (Caffeine, TTL 10–60s conforme
   volatilidade), invalidado EXPLICITAMENTE em toda mutação do service.
7. **Sidebar:** branch próprio em `getNavForProfile` + telas `/dashboard/<nicho>-*` + paleta
   própria. POST manual pelo tenant (sem conversation) não notifica.
8. **NÃO TEM global:** foto/upload (bloqueador SERVICE_ROLE_KEY — links colados), pagamento
   real (Stripe é backlog #50), scheduler de auto-transição de status (salvo onde o guia diz).

## Os 7 chassis (padrões estruturais — reusar, não reinventar)

**A — Agenda com conflito de slot** (restaurant/dental por company; salon inaugura POR
`professional_id` — herdam: pet, nutri, dermatologia, fotografia, otica-exame, barbearia,
estetica, concessionaria-testdrive). `findConflict` com janela half-open
(`NOT (end_at <= :s OR start_at >= :e)`), só status bloqueantes, RE-VERIFICADO dentro da
transação → 409 `conflict_slot`; `end_at` materializado no INSERT em Java; duração da config
OU por serviço/pacote/tipo (snapshot); janela opens/closes no fuso America/Sao_Paulo → 400
`outside_hours`; snapshots de nome/preço/duração; notifica só confirmado+cancelado (texto
defensivo), demais silenciosos.

**B — Pedido order-based com gate de aceite humano** (sushi é o avô; comida consolidou o
gate — herdam: floricultura, pizzaria, adega, padaria, lavanderia, papelaria, suplementos,
otica-encomenda). Carrinho NA CONVERSA (a IA relê o histórico; sem entidade de carrinho);
tag de pedido na confirmação; backend RECALCULA o total do catálogo; modifiers/opções com
`price_delta_cents`; snapshots por item; pedido nasce `aguardando` → aceite/recusa é AÇÃO
HUMANA (a IA nunca aceita; `aguardando` não notifica); Kanban no painel; retirada×entrega
(422 `address_required` + taxa); mínimo de pedido. Variantes do chassi: entrega agendada
dia+período ≥ hoje (floricultura/escola-visita), lead time por item `made_to_order` com data
condicional ≥ hoje+MAX (padaria/papelaria/otica → 422 `lead_time_violation`), turnaround
coleta→entrega = collect+MAX (lavanderia → 422 `turnaround_violation`), trava +18
`age_confirmed` NOT NULL (adega → 422 `age_not_confirmed`), meio-a-meio pela regra do MAIOR
valor (pizzaria), prova de ARTE com estado extra + tag de aprovação (papelaria → 409
`art_not_approved`).

**C — Varejo com grade de variantes + estoque transacional** (lingerie inaugura — herdam:
moda_infantil, las, suplementos). Variante = SKU real (eixos por nicho: size×color /
faixa-etária×color / color×dye_lot / flavor×size_label), UNIQUE por combinação, preço próprio
ou herda o base; estoque decrementado por UPDATE condicional (`stock >= :qtd`) na transação —
0 linhas → 409 `out_of_stock` e o pedido ABORTA; restock idempotente ao cancelar SÓ onde o
guia diz (moda_infantil, flag `stock_returned`); lote único garantido (las:
`same_lot_guaranteed` → 422 `mixed_dye_lots`).

**D — Proposta order-based com aprovação em 2 fases** (oficina é o avô com a OS; eventos
generaliza — herdam: atelie, casamento, viagens). A IA ABRE o artefato (rascunho/aberta, sem
itens); a equipe orça NO PAINEL (itens de orçamento com `line_total`/`total_cents`
MATERIALIZADOS em Java); `orcada` exige total>0 (400 `empty_budget`); tag de APROVAÇÃO muta
`orcada`→aprovada/recusada — única mutação de estado que a IA faz; funil segue
…→fechada→realizada; itens TRAVADOS a partir de fechada (409 `proposal_locked`/`order_locked`).
Sub-itens que NÃO entram no total (gerenciados SÓ no painel, SEM tag): cronograma por hora
(eventos/casamento), checklist binário done+due_date NULLS LAST (casamento), provas/ajustes
binárias ordenadas (atelie), itinerário MULTI-DIA (viagens).

**E — Assinatura/recorrência** (academia é o avô — herdam: escola, cursos). Matrícula
ativa-até-cancelar; ativa⇄suspensa/trancada; →cancelada terminal (LIBERA vaga + materializa
`end_date`; suspensa MANTÉM a vaga — cravado); capacity validado TRANSACIONALMENTE no INSERT;
anti-dupla por índice parcial UNIQUE where ativa; pagamento MANUAL mensal (UNIQUE por
`reference_month` → 409 `duplicate_payment`), sem cobrança automática/inadimplência;
snapshots de plano/turma/curso na matrícula.

**F — Entrega read-only pela IA** (nutri é o avô com o plano — herdam: dermatologia/preparo,
fotografia/link, cursos/módulo). Conteúdo escrito SÓ pelo painel; a tag de entrega envia o
conteúdo VERBATIM via `notifier.sendText` (NÃO passa pela IA — nunca é reescrito); BARREIRA
DE CONTATO (só entrega ao próprio contato da conversa); o contexto da IA indica QUE o
conteúdo existe, NUNCA injeta o corpo; cursos soma avanço de progresso na entrega.

**G — Sub-entidade do contato** (pet é o avô com o animal — herdam: oficina/veículo,
nutri/paciente→plano em 2 NÍVEIS, dermatologia/paciente, escola/aluno). A entidade do cliente
pertence ao contact (`contact_id NOT NULL`, 1→N); a tag tem 2 MODOS (`x_id` existente OU
`new_x` cadastra a sub-entidade E age no mesmo turno); matching de escopo onde o guia diz
(species do pet, placa da oficina); excluir em uso → 409 `*_in_use` (preferir arquivar).

**Singulares (escapadas que não viraram chassi):** legal (cliente DESACOPLADO do contact +
CNJ validado mód 97 + contexto por telefone), pousada (reserva por INTERVALO DE DIAS
half-open `[check_in, check_out)` em DATE), barbearia (fila walk-in com posição DERIVADA por
count — sem coluna position; "chamar" é ação humana), estetica (PACOTE multi-sessão com saldo
que decrementa/devolve transacionalmente), concessionaria (híbrido TRIPLO: estoque com
identidade e ciclo próprio + test-drive + lead com preço-snapshot), otica (primeiro híbrido
A+B, receita como dado ADMINISTRATIVO verbatim), sushi funcionalizado (categorias/status/
notificações viraram TABELAS por tenant + cupom/fidelidade/retirada/agendamento — mig 69).

## Catálogo dos perfis (34 verticais + generic)

O guia de cada perfil (`docs/PERFIL_<X>.md`) documenta migration, tenant de teste, paleta,
endpoints, telas e o NÃO-TEM. Aqui só o mapa:

| Camada | id | Chassi | Escapada | Guia |
|--------|----|--------|----------|------|
| 7.1 | sushi | B (avô) | funcionalizado: status/categorias/notif por tenant; cupom+fidelidade | PERFIL_SUSHI.md |
| 7.2 | legal | singular | cliente desacoplado; CNJ mód 97 | PERFIL_LEGAL.md |
| 7.3 | restaurant | A (company) | reservas de mesa | PERFIL_MESABOT.md |
| 7.4 | dental | A (company) | trava clínica; cancelamento por IA bloqueado | PERFIL_DENTAL.md |
| 7.5 | salon | A (prof) | inaugura conflito por profissional | PERFIL_SALAO.md |
| 7.6 | pousada | singular | intervalo de dias half-open | PERFIL_POUSADA.md |
| 7.7 | academia | E (avô) | aulas recorrentes; junction c/ snapshot | PERFIL_ACADEMIA.md |
| 7.8 | pet | G (avô) | animal do tutor; species_restriction | PERFIL_PET.md |
| 7.9 | oficina | D (avô) | OS; IA abre E captura aprovação | PERFIL_OFICINA.md |
| 8.0 | nutri | F (avô)+G | trava CFN; plano read-only; 2 níveis | PERFIL_NUTRI.md |
| 8.1 | barbearia | A+fila | posição derivada sem coluna | PERFIL_BARBEARIA.md |
| 8.2 | eventos | D | cronograma do dia | PERFIL_EVENTOS.md |
| 8.3 | estetica | A+pacote | saldo multi-sessão transacional | PERFIL_ESTETICA.md |
| 8.4 | comida | B | consolida gate de aceite + modifiers | PERFIL_COMIDA.md |
| 8.5 | floricultura | B | entrega agendada dia+período | PERFIL_FLORICULTURA.md |
| 8.6 | pizzaria | B | meio-a-meio regra do maior valor | PERFIL_PIZZARIA.md |
| 8.7 | casamento | D | checklist binário (3ª sub-entidade) | PERFIL_CASAMENTO.md |
| 8.8 | padaria | B | pronta-entrega × encomenda c/ lead time | PERFIL_PADARIA.md |
| 8.9 | adega | B | trava +18 age_confirmed | PERFIL_ADEGA.md |
| 8.10 | lavanderia | B | 2 datas ligadas por turnaround MAX | PERFIL_LAVANDERIA.md |
| 8.11 | dermatologia | A(prof)+F+G | tipos de atendimento em tabela; preparo | PERFIL_DERMATOLOGIA.md |
| 8.12 | otica | híbrido A+B | receita administrativa verbatim | PERFIL_OTICA.md |
| 8.14 | atelie | D | provas/ajustes; project_type | PERFIL_ATELIE.md |
| 8.15 | papelaria | B | prova de ARTE (estado + tag de aprovação) | PERFIL_PAPELARIA.md |
| 8.16 | fotografia | A(prof)+F | pacote c/ delivery_days; link na sessão | PERFIL_FOTOGRAFIA.md |
| 8.17 | concessionaria | híbrido triplo | estoque c/ identidade + test-drive + lead | PERFIL_CONCESSIONARIA.md |
| 8.18 | viagens | D | itinerário multi-dia | PERFIL_VIAGENS.md |
| 8.19 | escola | E+G | aluno sub-entidade; visita dia+período | PERFIL_ESCOLA.md |
| 8.20 | cursos | E+F | trilha de módulos; entrega avança progresso | PERFIL_CURSOS.md |
| 8.21 | lingerie | C (avô) | inaugura grade variantes+estoque | PERFIL_LINGERIE.md |
| 8.22 | moda_infantil | C | tamanho por idade; restock idempotente | PERFIL_MODA_INFANTIL.md |
| 8.23 | las | C | dye lot; same_lot_guaranteed | PERFIL_LAS.md |
| 8.24 | suplementos | B+C | trava de não-prescrição em varejo | PERFIL_SUPLEMENTOS.md |

(A fila/ordem de nichos e os slots de migration vivem em `docs/prompts-gordos/README.md`.)

## Lições cravadas de engenharia

- **`timestamptz + interval` / `date + interval` NÃO é IMMUTABLE** → NUNCA coluna GENERATED;
  materializar em Java no INSERT (`end_at`, `total_cents`, `delivery_date`). UPDATEs
  materializam os valores FINAIS em Java, não na SET clause.
- **SCRIPTS do `AbstractIntegrationTest`:** a migration que REESCREVE a CHECK de
  `companies.profile_id` entra por ÚLTIMO e precisa listar TODOS os perfis (mesmo que seu
  número no disco seja menor).
- **Clonagem por sed é armadilha dupla:** (1) troca o id NA LISTA da CHECK removendo os
  demais; (2) esquece valores case-sensitive (`PRIMEIRA10`/`primeira10`). Usar
  `scripts/gerar-perfil.py`.
- **Parity regex aceita underscore** (`[a-z0-9_-]+`) — lição moda_infantil.
- **Pool Hikari nos testes:** `src/test/resources/application-dev.yml` com min-idle 0/max 2
  (N ApplicationContexts × pool default estourava o pooler).
- **AuditLogger:** FK p/ `auth.users` dentro de `@Transactional` aborta o commit em SILÊNCIO —
  teste de service que audita SEMPRE semeia `auth.users` + `public.users`.
- **Tag em texto livre, NÃO tool calling do Gemini** (tool calling × `responseSchema` são
  mutuamente exclusivos; o outbound já usa responseSchema).
- **Seed de auth.users via SQL:** exige `instance_id` zero-UUID + colunas de token `''`
  (não NULL), senão login falha `invalid_credentials`.

## Feature flags por nicho (camada 9.0) + CMS (9.x)

- **Feature é HARDCODED** (`ProfileFeature` ↔ `profile-feature.ts` + parity). Tabela
  `profile_features` guarda SÓ desvios (ausência = OFF; default OFF, opt-in do root).
  SEM CHECK de profile_id (validação app-level). Tabela de PLATAFORMA (RLS force,
  só service_role); o tenant recebe o resolvido via `GET /admin/me.features`.
- Root: grade `/dashboard/profile-features` (superAdminOnly); `PUT /admin/profile-features/
  {profileId}/{featureKey}` audita + invalida cache (Caffeine TTL 20s — toggle demora ≤20s).
  `generic` NÃO entra na grade (é o produto do root, não tenant de nicho).
- **Gate:** `ProfileFeatureGuard.requireFeature(user, ProfileFeature.X)` → 403
  `feature_disabled`. O CMS inteiro (`/api/cms/**`) é gateado por `requireFeature(CMS)`.
- **CMS/Site por tenant:** `cms_sites` (1:1 — domain+verificação TXT via JNDI, theme jsonb,
  published) + `cms_pages` (N por company, blocos jsonb validados app-level, 1 home).
  Público sem auth: `/public/cms/by-slug|by-domain` + `tls-allowed` (ask do Caddy; template
  `on_demand_tls` COMENTADO em dev). Next: `/p/{slug}[/{pageSlug}]` + rewrite por host no
  middleware. Editor `/dashboard/cms` gateado por `features?.cms`. Docs: `docs/CMS.md` +
  `docs/FEATURE_FLAGS.md`; roteamento de domínios: memória `contrato-roteamento-dominios`.

## Ferramentas de escala

- **Gerador de perfil:** `scripts/gerar-perfil.py --id <novo> --exemplar <id-existente>` —
  clona o chassi do exemplar (backend+frontend+migration) com renames determinísticos e
  REGENERA a CHECK completa a partir do enum. O agente escreve só a escapada + persona.
  Ver `scripts/README-gerar-perfil.md`.
- **Unificação de motores:** engines comuns em `com.meada.common.*` (fase iniciada 2026-07 —
  roadmap em `docs/UNIFICACAO_CHASSIS.md`). Regra: unificar SEM mudar contrato/comportamento,
  gate = suite completa verde.

## Estado das camadas

- **1** Schema multi-tenant · **2** Webhook Evolution inbound · **3** IA + outbound —
  FECHADAS. **4** Painel admin — 4.0–4.4 fechadas (tags); pendências 4.4.x/4.5/4.6.
- **7.x–8.x** — 34 perfis verticais fechados (catálogo acima). **9.0** feature flags +
  **9.x** CMS multi-página — fechadas.
- Empreitada corrente: backlog de features por nicho (`docs/BACKLOG_EXECUCAO.md`), com
  autonomia cravada (memória `feedback_autonomia_execucao_features`).

## Incidente registrado (ver RISKS.md)

Re-sync de histórico do Baileys/Evolution disparou respostas automáticas a contatos reais
no boot (2026-06-10). MITIGADO: dry-run em local (`EVOLUTION_DRY_RUN=true`) + guard de
frescor por `messageTimestamp` (`webhook.message-max-age-seconds`). **Webhook permanece OFF
até religar consciente.** Não religar sem verificar dry-run + threshold.
