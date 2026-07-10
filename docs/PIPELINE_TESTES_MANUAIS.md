# Pipeline de Testes — Manual do Desenvolvedor (Meada)

Roteiro completo de verificação do Meada: o que rodar, em que ordem, com que
critério de aprovação, e o que só o olho humano pega. Cobre também os
**procedimentos externos** (fora do repositório) que precisam estar de pé antes
de qualquer teste fazer sentido.

> Regra de ouro do projeto: contagem de testes vem do Surefire (`Tests run: N`),
> nunca de `grep @Test`. Resultado de teste é reportado LITERAL — inclusive falha.

---

## 0. Procedimentos externos (fora do repo — fazer 1x por máquina/ambiente)

| # | Procedimento | Comando / onde | Confere com |
|---|--------------|----------------|-------------|
| 0.1 | Java 17 Temurin instalado | `/usr/lib/jvm/temurin-17-jdk-amd64` | `java -version` |
| 0.2 | Maven 3.8+, Node + npm | `mvn -v`, `node -v` | — |
| 0.3 | Docker de pé (Testcontainers + Evolution) | `docker ps` | daemon responde |
| 0.4 | Supabase CLI + projeto local iniciado | `supabase start` na raiz | API `:54321`, Postgres `:54322` |
| 0.5 | Migrations aplicadas no Supabase local | `psql -h 127.0.0.1 -p 54322 -U postgres -d postgres -f supabase/migrations/NN_x.sql` (novas, ao fechar onda) | tabelas do nicho existem |
| 0.6 | `.env` raiz preenchido | `cp .env.example .env` + preencher | backend sobe sem fail-fast |
| 0.7 | `frontend/.env.local` preenchido | `cp frontend/.env.example frontend/.env.local` | **atenção ao IP do WSL** (abaixo) |
| 0.8 | Usuários de teste no Supabase Auth | painel Auth ou seeds | super-admin na allowlist SEM linha em `public.users`; tenant-admin COM linha |
| 0.9 | Google Chrome (para a suíte Selenium) | `google-chrome --version` | Selenium Manager resolve o driver |
| 0.10 | Evolution local (SÓ para fluxo WhatsApp) | `cd evolution-local && docker compose up -d` | API `:8086` |

**⚠️ IP do WSL e o cookie de auth:** o `NEXT_PUBLIC_SUPABASE_URL` do frontend usa
o IP do WSL (ex.: `http://172.27.153.135:54321`) para alinhar o nome do cookie
`sb-<ip>-auth-token` entre browser e SSR. O IP do WSL **muda entre reboots do
Windows**. Sintoma: redirect loop no login. Conferir: `hostname -I` × `.env.local`.

**⚠️ Webhook Evolution permanece OFF** (incidente 2026-06-10, ver RISKS.md).
Religar é decisão consciente: verificar `EVOLUTION_DRY_RUN=true` em dev e o
guard de frescor (`webhook.message-max-age-seconds`) ANTES.

**⚠️ Porta 8000 é do Chroma (claude-mem)** — não subir nada do Meada nela.

---

## 1. Subir o ambiente local (toda sessão de trabalho)

```bash
# 1. Supabase (se não estiver de pé)
supabase start

# 2. Backend Spring (porta 8095)
./scripts/run-local.sh
# sanity: GET /admin/me sem token → 401 {"reason":"missing_auth_header"}
curl -s http://localhost:8095/admin/me   # 404 na raiz é SAUDÁVEL (não há actuator)

# 3. Frontend (dev para trabalhar; build de produção para TESTAR)
cd frontend && npm run dev        # desenvolvimento
# OU, para smoke/Selenium (Turbopack dev é lazy e esconde import quebrado):
cd frontend && npm run build && npm run start
```

Alternativa docker-compose (fase 0.5): `./scripts/meada-up.sh` /
`./scripts/meada-down.sh` (backend+frontend+embeddings+caddy; o banco fica fora).
Os testes de integração rodam **no HOST** mesmo com o compose de pé.

---

## 2. Pipeline automatizado (rodar NESTA ordem — barato → caro)

### Etapa 1 — Lint + formatação do frontend (~30s)
```bash
cd frontend && npm run lint          # critério: 0 erros (3 warnings react-hooks são conhecidos)
npm run format:check                 # ordem de classes/imports é MECÂNICA: se falhar, npm run format
```

### Etapa 2 — Testes de integração do backend (~minutos; exige Docker)
```bash
JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64 mvn -B clean test
```
Critério: verde, contagem do **Surefire** (`Tests run: N, Failures: 0, Errors: 0`).
Usa Testcontainers (Postgres 17-alpine) — o Supabase local NÃO é tocado.
Migration nova que reescreve a CHECK de `companies.profile_id` entra por ÚLTIMO
no `SCRIPTS` do `AbstractIntegrationTest` e lista TODOS os perfis.

### Etapa 3 — Build de produção do frontend (~1-2 min)
```bash
cd frontend && npm run build         # critério: compila limpo; é O gate de frontend
```

### Etapa 4 — Suíte funcional Selenium (~3-6 min; exige ambiente da seção 1 DE PÉ com build de produção)
```bash
pip install -r scripts/selenium_tests/requirements.txt   # 1ª vez
python3 scripts/selenium_tests/seed_users.py             # idempotente (cria selenium.<nicho>@meada.test)
python3 -m pytest scripts/selenium_tests/ -v
```
Cobre: login (inclusive mensagem genérica anti-enumeration), varredura de TODAS
as telas da sidebar em 6 nichos (1 por chassi — salon/sushi/lingerie/oficina/
academia/nutri), CRUD core via UI (FAQ, caminho SDK+RLS), e contratos REST
(guard de perfil 403 `forbidden_wrong_profile`, shape do `/admin/me`, gate do
CMS, endpoints públicos). Screenshots em `scripts/selenium_tests/screenshots/`.

---

## 3. Roteiro de testes MANUAIS (o que a máquina não pega)

### 3.1 Auth e multi-perfil (sempre que mexer em auth/middleware/perfis)
- [ ] Login super-admin (email na allowlist): vê `/dashboard/companies` e a grade `/dashboard/profile-features`.
- [ ] Login tenant-admin: vê APENAS o nav do seu perfil, com a paleta do nicho.
- [ ] Subdomínio de produto (ex.: `sushi.meadadigital.local`): login de tenant de OUTRO perfil → mensagem genérica "Email ou senha inválidos." (nunca revelar que a conta existe).
- [ ] `localhost`/domínio-base = login universal (qualquer tenant entra).

### 3.2 Isolamento RLS (sempre que criar tabela/policy nova)
Protocolo da camada 4.3: logar tenant A no browser normal e tenant B numa
janela anônima; conferir pela aba Network que cada um só recebe as suas linhas;
tentativa deliberada de furo (forçar `company_id` do outro no SDK) retorna vazio.

### 3.3 Fluxo IA + WhatsApp (com `EVOLUTION_DRY_RUN=true`)
- [ ] Simular inbound via webhook (POST `/webhooks/evolution` com secret) e conferir resposta da IA no log (dry-run não envia de verdade).
- [ ] Tag de ação do nicho (pedido/agendamento/proposta): a tag é REMOVIDA do texto ao cliente; a entidade nasce no painel; preço/total vem do CATÁLOGO (nunca da tag).
- [ ] "Falar com humano" → conversa flipa para `human` e IA silencia.

### 3.4 Invariantes por chassi (testar o representante ao mexer no motor comum)
| Chassi | Tenant de referência | O que conferir manualmente |
|--------|----------------------|----------------------------|
| A agenda | salon (Beleza Pura) | slot ocupado → 409 `conflict_slot` na UI; fora do horário → `outside_hours` |
| B pedido | sushi (Sushi Legal) | pedido nasce `aguardando`; aceite é AÇÃO HUMANA no Kanban; `aguardando` não notifica |
| C varejo | lingerie (Lingerie Modelo) | estoque 0 → 409 `out_of_stock` e pedido aborta; restock só onde o guia diz |
| D proposta | oficina (Motor Forte) | equipe orça no painel; `orcada` exige total>0; itens travam a partir de `fechada` |
| E assinatura | academia (Corpo em Forma) | capacity transacional; anti-dupla; suspensa MANTÉM vaga, cancelada LIBERA |
| F entrega | nutri (Nutre Vida) | conteúdo entregue VERBATIM; barreira de contato (só ao próprio contato) |
| G sub-entidade | pet (Pata na Nuvem) | tag com 2 modos (`x_id` × `new_x`); excluir em uso → 409 `*_in_use` |

### 3.5 CMS (ao mexer em blocos/tema/publicação)
- [ ] Editor `/dashboard/cms` só aparece para tenant com feature CMS ligada (root liga na grade; toggle propaga em ≤20s — cache Caffeine).
- [ ] Publicar → página pública `/p/{slug}` renderiza TODOS os blocos usados; bloco novo aparece no editor E no renderer (paridade dos 2 registries).
- [ ] Site despublicado → página pública some (404), editor continua.

### 3.6 Smoke pré-commit (sempre)
- [ ] `git status -s` + `git diff --staged --stat` — só os arquivos da empreitada.
- [ ] Grep de segredo no staged: `git diff --staged | grep -E "eyJ|password|secret="` → vazio.
- [ ] `.env`, `frontend/.env.local`, `CONTEXT.md` FORA do staging.
- [ ] `git add` arquivo a arquivo (NUNCA `git add .`).

---

## 4. Critério de "fechado" (resumo)

| Camada | Gate |
|--------|------|
| Backend | `mvn -B clean test` verde (contagem Surefire) |
| Frontend | `npm run lint` 0 erros + `npm run build` limpo |
| Funcional | suíte Selenium verde sobre build de produção |
| Manual | roteiro da seção 3 nas áreas tocadas |
| Git | sanity de staging + commit semântico PT-BR + trailer do Claude |

---

## 5. Troubleshooting rápido

| Sintoma | Causa provável | Ação |
|---------|----------------|------|
| Redirect loop no login | IP do WSL mudou vs `.env.local` | atualizar `NEXT_PUBLIC_SUPABASE_URL` e reiniciar frontend |
| `invalid_credentials` em user seedado via SQL | `instance_id`/colunas de token erradas | usar GoTrue admin API (ou seed com `instance_id` zero-UUID + tokens `''`) |
| Testes estouram conexões | pool Hikari default × N contexts | `application-dev.yml` de teste já limita (min 0/max 2) — não mexer |
| Teste de service que audita falha em silêncio | FK `audit_log.user_id → auth.users` aborta commit | semear `auth.users` + `public.users` no teste |
| Build de teste falha em CHECK de perfil | migration da CHECK fora de ordem no `SCRIPTS` | a reescrita da CHECK entra por ÚLTIMO e lista TODOS os perfis |
| Porta 8000 ocupada | Chroma do claude-mem | não usar 8000; Orbit backend é 8080 |
| Selenium não loga | usuários não seedados / frontend em dev lazy | rodar `seed_users.py`; usar `npm run build && npm run start` |
