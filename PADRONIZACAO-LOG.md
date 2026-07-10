# PADRONIZACAO-LOG — padronização retroativa via skills (branch `padronizacao-skills`)

Rollback global: tag `pre-padronizacao`. Cada lote é um commit atômico revertível com `git revert`.

## Fase 1 — Auditoria (2026-07-02)

Cânone extraído da MAIORIA do código existente:

| Eixo | Medição | Cânone |
|------|---------|--------|
| Router (front) | `pages/` inexistente; 192 páginas em `app/` | App Router |
| Server×Client | 189/192 páginas com `'use client'` (públicas do CMS são server) | painel client, público server |
| type × interface | 540 `export type` × 3 `export interface` | `type` |
| Imports | 1869 `@/` × 2 relativos | absolutos `@/` |
| Data fetching | 186 páginas com `useQuery`; 0 fetch-em-useEffect | TanStack Query + `lib/api` |
| Classes condicionais | template literal dominante; `cn()` (clsx+twMerge) em 37 usos (ui/) | template literal; `cn()` p/ merge de className |
| DI (back) | 0 `@Autowired` real (1 menção em comentário) | construtor |
| DTO (back) | records aninhados nos controllers | records |
| Erro (back) | catch local + `{error, reason}` em 186 controllers; `GlobalExceptionHandler` p/ genéricos | dupla camada (domínio local, genérico global) |
| Docker | backend 17-jdk builder→jre runtime→jdk dev; front node:20-alpine dev/builder/prod standalone | multi-stage com stage dev |

Baseline ESLint (referência para "não piorar"): **86 problemas** (74 errors, 12 warnings) —
60× `react-hooks/set-state-in-effect`, 12× `react/no-unescaped-entities`, 4× `no-unused-vars`,
4× `exhaustive-deps`, 3× `incompatible-library`, 2× `no-html-link-for-pages`, 1× `no-unused-expressions`.
Backend não tem lint configurado (gate = `mvn -B clean test`, 1848 verdes na baseline).

## Fase 2 — Skills

- Lote S1: `.claude/skills/{frontend-components,tailwind-styling,nextjs-data-fetching,spring-controllers,spring-error-handling,docker-infra}/SKILL.md` + seção "Padrões de código" no CLAUDE.md.
  Arquivos: 7 (.md apenas — sem impacto em lint/test/build; gates da baseline seguem valendo).
  Commit: a7bc539

## Fase 3 — Lotes aplicados

- **Lote F1** — `type` no lugar de `interface` (cms-block-schemas ×2, data-table ×2 incl. props
  interno) + imports relativos→`@/` (page-templates ×2). Arquivos: 3 (frontend).
  Validação: eslint 86 (= baseline, não piorou) · `next build` ✓ · backend intocado (mvn n/a).
  Commit: 0f44324
- **Lote F2** — achados seguros do ESLint: aspas escapadas em texto JSX (academia-checkins/
  memberships/waitlist), imports/types não usados removidos (cms, concessionaria-reports,
  nutri-plans, cms-render) e ternário-expressão → if/else equivalente (cms). Arquivos: 7 (frontend).
  Validação: eslint 86→75 (nenhum erro novo) · `next build` ✓ · backend intocado (mvn n/a).
  Commit: fe94d52
- **Backend/Infra** — auditoria não encontrou desvios do cânone (0 @Autowired, 0 System.out,
  DTOs records, catch local + GlobalExceptionHandler, Dockerfiles já multi-stage): NENHUM lote
  necessário. mvn -B clean test da baseline: 1848 verdes.
- **Lote F3** — hook `lib/use-synced-form.ts` (useSyncedForm — padrão oficial React de "adjusting
  state when props change": setState durante o render condicionado à mudança; sem lib nova) +
  12 telas de settings convertidas de `useEffect(() => setForm(...), [data])`. Arquivos: 13.
  Validação: eslint 0 no lote · `next build` ✓. Commit: cb4247f
- **Lote F4** — mais 14 telas de settings convertidas para useSyncedForm. Arquivos: 14.
  Validação: eslint 0 no lote · `next build` ✓. Commit: 8f818ca
- **Lote F5** — mais 10 telas (settings + loyalty) convertidas. Arquivos: 10.
  Validação: eslint 0 no lote · `next build` ✓. Commit: b954c8e
- **Hooks auxiliares** — useOnSync (multi-setter) e useResetWhen (reset de diálogo) somados ao
  use-synced-form.ts. Arquivos: 1. Commit: efa60a7
- **Lote F6** — 15 casos especiais: useOnSync (ai-settings, business-hours, contacts/[id], cms,
  atelie/casamento-proposals, fotografia-appointments, academia-loyalty), useResetWhen (availability,
  teams, saved-replies, create-invitation, knowledge-upload, create-service, create-faq) e
  ajuste-durante-render na invalidação de seleção do CMS. Arquivos: 15.
  Validação: eslint 0 no lote · `next build` ✓. Commit: 0031950
- **Lote F7** — resto do lint: `categories` memoizado no sushi-menu (exhaustive-deps ×2),
  `<a href="/">`→`<Link>` no meada-chrome (no-html-link-for-pages ×2), disable justificado no
  catch ES5 do widget.js, e 6 disables justificados de set-state-in-effect em efeitos LEGÍTIMOS de
  hidratação SSR/localStorage/modal (login, theme-toggle, theme-mode-provider, sidebar-context,
  global-search ×2). Arquivos: 8. Validação: eslint 0 erros no repo · `next build` ✓. Commit: 8a0514e

## Fase 4 — Formatação mecanizada + skills v2 (2026-07-03, autorização explícita do Igor: "tudo")

O Igor autorizou a exceção à trava "sem dependências novas" para as libs de formatação, e pediu
skills mais ricas. Lotes:

- **P0 `[REVISAR]`** — devDependencies: prettier, prettier-plugin-tailwindcss (ordem canônica de
  classes) e @ianvs/prettier-plugin-sort-imports. Config `.prettierrc.json` espelhando o estilo
  MAJORITÁRIO (sem semi, aspas simples, printWidth 100, blocos de import third-party/`@/`/relativo);
  `.prettierignore` poupa `public/` (widget ES5); scripts `format`/`format:check`.
  Arquivos: 4 (package.json, package-lock.json — regenerado por comando —, 2 configs).
  Commit: 2f107e0
- **P1 `[REVISAR]`** — `prettier --write` em `app/(protected)` (184 arquivos).
  Validação: eslint 0 erros no lote · `next build` ✓. Commit: 793867b
- **P2 `[REVISAR]`** — resto de `app/` (auth, p/, layout) + configs raiz (14 arquivos).
  Validação: eslint 0 erros · `next build` ✓. Commit: dd05b00
- **P3 `[REVISAR]`** — `components/` (30 arquivos). Validação: eslint 0 erros · build ✓.
  Commit: 383f217
- **P4 `[REVISAR]`** — `lib/` + `profiles/` (182 arquivos). Validação: eslint 0 erros · build ✓
  · `npx prettier --check .` limpo no frontend INTEIRO. Commit: 8f33e5e
- **Skills v2** — as 6 skills crescem de ~430 p/ ~707 linhas (cânone completo: esqueleto de tela,
  hooks de sync, taxonomia de queryKey, padrões estruturais dos nichos, handlers de tag, mapa
  HTTP→reason, best-effort, gotchas cravados, portas). CLAUDE.md ganha `npm run format` e o estado
  real do lint (0 erros). Arquivos: 7. Commit: da1e6d0

Total da fase: 410 arquivos reformatados em 4 lotes `[REVISAR]` + 1 lote de deps + 1 de docs.
Reformatação é puramente mecânica (formatação, ordem de classes Tailwind, ordem de imports) —
sem mudança semântica; cada lote validado com eslint (0 erros) + `next build` limpo.

## Fase 5 — Economia de tokens (2026-07-03, "faça tudo de uma vez" do Igor)

Três frentes além da padronização (a estratégia real pedida: reduzir o peso da aplicação e o
custo de tokens das sessões):

- **T1** — dieta do CLAUDE.md: 1708→386 linhas (140KB→26KB, ~25k tokens de contexto fixo a
  menos POR SESSÃO). 30 seções por perfil viram 7 chassis + tabela-catálogo de 34 perfis +
  lições cravadas. Nada de regra cravada se perdeu; detalhe por perfil já vivia em
  docs/PERFIL_*.md. Commit: d236b03
- **T2** — gerador de perfil (`scripts/gerar-perfil.py` + README): clona chassi de exemplar com
  renames com fronteira de palavra e REGENERA a CHECK completa do enum (mata a armadilha do
  sed). Validado com perfil descartável (40 arquivos, zero resíduo, CHECK íntegra, compila) e
  revertido. Commit: ac36113
- **T3 `[REVISAR]`** — unificação fatia 1: motor de cupom (7 clones → `com.meada.common.coupons`
  base + subclasses finas; records/contratos JSON preservados; academia mantém validate/apply).
  −997 linhas líquidas. Gate: mvn -B clean test = **1848 verdes, 0 falhas**, testes por perfil
  inalterados fora imports (prova de preservação). Commit: 4917aca
  Roadmap das fatias 2–7: docs/UNIFICACAO_CHASSIS.md.

## Resumo final

- Lotes executados: **15** (S1 skills+CLAUDE.md · F1 types/imports · F2 eslint seguro ·
  F3/F4/F5 useSyncedForm 36 telas · hooks auxiliares · F6 casos especiais · F7 resto do lint ·
  P0 deps Prettier · P1–P4 reformatação 410 arquivos · skills v2) — **15 passaram, 0 revertidos**.
- ESLint: **86 problemas → 0 erros + 3 warnings** (`react-hooks/incompatible-library` —
  React Compiler pula componentes com react-hook-form; informativo, causado pela lib).
- Prettier: frontend 100% formatado (`npm run format:check` limpo); ordem de classes Tailwind e
  de imports agora são MECÂNICAS (plugin), não convenção manual.
- set-state-in-effect: 60 achados zerados SEM mudança de comportamento observável — o padrão
  useEffect-sync virou sync durante o render (useSyncedForm/useOnSync/useResetWhen em
  `frontend/lib/use-synced-form.ts`, sem dependência nova); os 6 efeitos legítimos de hidratação
  ficaram como efeito com disable justificado em linha.
- Backend: sem desvios; nenhum arquivo Java alterado pela padronização (gate mvn preservado por
  construção).
- Sugestões que exigem lib/decisão: PADRONIZACAO-SUGESTOES.md (Prettier+plugin-tailwind,
  Checkstyle/Spotless, unificação dos motores de cupom).

## Pendências

- 3× `react-hooks/incompatible-library`: informativo (react-hook-form não é compilável pelo React
  Compiler) — não acionável sem trocar a lib.
- Push do branch `padronizacao-skills` + tag `pre-padronizacao`: por conta do Igor (Trava 10):
  `git push -u origin padronizacao-skills --tags`
