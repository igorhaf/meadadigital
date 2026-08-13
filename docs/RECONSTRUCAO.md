# Reconstrução por regras de negócio — roadmap

Este repositório é construído **como uma sequência de regras de negócio**: cada regra
nasce numa branch própria, é desenvolvida em passos commitados, fecha com a suíte de
testes funcionais (Selenium) verde e só então é mergeada em `main`. Este documento é
o roteiro completo.

## Convenções

- **Branch:** `regra/<camada>-<slug>` (ex.: `regra/1-schema-multitenant`, `regra/7.1-sushi`).
- **Commits dentro da branch** (semânticos, em português, um passo por commit):
  - `feat(<camada>): <passo>` — ex.: `feat(7.1): migration — tabelas e RLS do cardápio/pedidos`
  - `test(<camada>): suíte selenium — <escopo>`
  - `fix(<camada>): <erro real encontrado pela suíte>`
- **Fechamento:** merge `--no-ff` em `main` com mensagem `merge(<camada>): fecha <regra> — selenium verde`.
- **Sem testes unitários** em nenhuma camada. O gate de qualidade é:
  1. `mvn -B clean compile` limpo (backend);
  2. `cd frontend && npm run build` limpo (quando o frontend existir);
  3. **suíte Selenium ACUMULADA verde** — a regra nova + regressão de todas as já fechadas.
- **Suíte Selenium:** pytest + Selenium em `scripts/selenium_tests/`, contra o ambiente
  local real (Supabase local + backend :8095 + frontend :3000 em build de produção).
  Usuários descartáveis `selenium.<nicho>@meada.test` — 1 por chassi de negócio, seed
  idempotente. Camadas anteriores ao painel (sem UI) são cobertas por testes de contrato
  HTTP na mesma suíte.

## Roadmap de branches (ordem de fechamento em `main`)

### Fundação (core)

| # | Branch | Regra de negócio |
|---|--------|------------------|
| 1 | `regra/1-schema-multitenant` | O banco multi-tenant: tabelas core (companies, users, contacts, conversations, messages, ai_settings, business_hours, services, faqs, whatsapp_instances), RLS por `app.company_id()`, grants, storage. |
| 2 | `regra/2-webhook-inbound` | Nasce o backend Spring: webhook da Evolution API com guards (secret em tempo constante, fromMe, grupos/broadcast, duplicata idempotente, frescor de timestamp) e persistência do inbound. |
| 3 | `regra/3-ia-outbound` | A IA responde: Gemini + PromptBuilder (contexto do tenant), OutboundService (horário de funcionamento ANTES da IA, retry com backoff, idempotência por message id, dry-run). |
| 4 | `regra/4-painel-admin` | Auth JWT ES256/JWKS, super-admin × tenant-admin, painel Next (login, conversas, CRUD de serviços/FAQs/horários/instâncias), convites e roles. |
| 5 | `regra/5-conhecimento-rag` | Base de conhecimento com embeddings (sidecar), retrieval no prompt, busca global. |
| 7 | `regra/6-operacao` | Operação de atendimento: tags, saved replies, teams e limites, métricas, engagement/reativação, insights, treino da IA, webchat, LGPD, access logs, admin de plataforma (planos, announcements, impersonation, auditoria). |
| 6 | `regra/7.0-multiperfil` | O chassi multi-perfil: `ProfileType` (nasce com `generic`), guard por perfil, paleta por perfil, subdomínio → perfil no middleware. |

### Nichos verticais (ordem histórica — 1 branch por nicho)

Cada branch de nicho contém, em passos: entrada no enum + espelho TS · migration própria
(reescrevendo a CHECK de `profile_id` completa daquele momento) · backend do nicho (guard,
services, handlers de tag) · persona com as travas do domínio · contexto/cache · frontend
(telas, sidebar, paleta) · ondas do backlog do nicho · suíte Selenium do nicho.

| # | Branch | # | Branch | # | Branch |
|---|--------|---|--------|---|--------|
| 8 | `regra/7.1-sushi` | 19 | `regra/8.3-estetica` | 30 | `regra/8.16-fotografia` |
| 9 | `regra/7.2-legal` | 20 | `regra/8.4-comida` | 31 | `regra/8.17-concessionaria` |
| 10 | `regra/7.3-restaurant` | 21 | `regra/8.5-floricultura` | 32 | `regra/8.18-viagens` |
| 11 | `regra/7.4-dental` | 22 | `regra/8.6-pizzaria` | 33 | `regra/8.19-escola` |
| 12 | `regra/7.5-salon` | 23 | `regra/8.7-casamento` | 34 | `regra/8.20-cursos` |
| 13 | `regra/7.6-pousada` | 24 | `regra/8.8-padaria` | 35 | `regra/8.21-lingerie` |
| 14 | `regra/7.7-academia` | 25 | `regra/8.9-adega` | 36 | `regra/8.22-moda-infantil` |
| 15 | `regra/7.8-pet` | 26 | `regra/8.10-lavanderia` | 37 | `regra/8.23-las` |
| 16 | `regra/7.9-oficina` | 27 | `regra/8.11-dermatologia` | 38 | `regra/8.24-suplementos` |
| 17 | `regra/8.0-nutri` | 28 | `regra/8.12-otica` | | |
| 18 | `regra/8.1-barbearia` | 29 | `regra/8.14-atelie` | | |

### Plataforma

| # | Branch | Regra de negócio |
|---|--------|------------------|
| 39 | `regra/9.0-feature-flags` | Features por nicho hardcoded, tabela só de desvios (default OFF), gate `feature_disabled`, grade do root. |
| 40 | `regra/9.1-cms` | CMS/site por tenant: páginas com blocos, tema, domínio próprio com verificação TXT, público sem auth, gateado por flag. |
| 41 | `regra/9.2-vitrine` | Vitrine institucional dos nichos: showcase do root, páginas por nicho com pricing. |
| 42 | `regra/9.3-assinaturas` | Assinatura do produto (schema Mercado Pago Preapproval — plataforma-level). |
| 43 | `regra/10-wiki` | A wiki de regras de negócio (docs/wiki + Docsify + serve). |

## Notas de execução

- **Ordem 6 × 7.0:** o chassi multi-perfil (`regra/7.0`) fecha ANTES da operação
  (`regra/6`): os módulos da operação, no estado final, referenciam o `ProfileType` —
  com o enum nascendo primeiro (só `generic` + os declarados), a operação entra
  compilando sem retrabalho. A numeração das camadas segue a história real.


- A referência de conteúdo é a branch `main-legado` (estado congelado na tag
  `wiki-regras-negocio-v1`). Ao final, a convergência é provada por diff de árvores:
  `main` == `main-legado` − `src/test/` − dependências de teste do `pom.xml` − suíte
  Selenium antiga + suíte Selenium reconstruída.
- O Supabase local mantém o schema completo durante a reconstrução (superset): as
  camadas intermediárias rodam sobre ele sem conflito — tabelas de regras futuras
  simplesmente ainda não são usadas.
- Arquivos "de fiação" (enum de perfis, cadeia de handlers do outbound, CHECK de
  `profile_id`, sidebar, prefixos autenticados) **evoluem a cada branch** — cada estado
  intermediário compila e roda, como na história real do projeto.
