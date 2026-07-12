# Relatório de Inconsistências — Auditoria profunda do Meada (2026-07)

Auditoria multi-agente em 7 dimensões (chassis backend, migrations/paridade, handlers
de IA, navegação frontend, qualidade/UX frontend, lacunas de teste, CMS/feature flags),
com re-verificação por leitura direta dos achados graves. Escopo: **apenas o SaaS Meada**
(`src/`, `frontend/`, `supabase/`, `docs/`, `scripts/`) — nada de `external_projects/`.

**Legenda de status:**
- ✅ **corrigido nesta empreitada** (com teste onde indicado)
- 📋 **backlog** — resolução proposta, aguarda decisão/onda própria
- 🔎 **não re-verificado** individualmente (achado de auditor sem contra-prova minha)

Complementos desta mesma empreitada: suíte Selenium (`scripts/selenium_tests/`, 20
testes verdes sobre o ambiente real) e `docs/PIPELINE_TESTES_MANUAIS.md`.

---

## 1. CRÍTICOS — bugs de dados e segurança

### 1.1 ✅ `line_total_cents` calculado na SET clause lê valores ANTIGOS (3 perfis)
**Onde:** `ServiceOrderRepository.java:234` (oficina), `EventProposalRepository.java:284`
(eventos), `TravelProposalRepository.java:303` (viagens).
**Problema:** o `updateItem` desses 3 repositórios monta
`sets.add("line_total_cents = quantity * unit_price_cents")` no MESMO update que muda
quantity/preço. Em Postgres, expressões da SET clause leem os valores **antigos** da
linha. Editar quantidade de 2→3 num item de R$ 100 grava total de R$ 200 (deveria
R$ 300); o `recalcTotal` propaga o erro pro total da OS/proposta — **valor errado
notificado ao cliente**. Viola a lição cravada no CLAUDE.md ("UPDATEs materializam os
valores FINAIS em Java"). Atelie/casamento fazem certo (`line_total_cents = ?`).
**Resolução:** replicado o padrão do `AtelieProposalRepository.updateItem` (lê o item,
resolve os campos finais em Java, binda o produto como parâmetro) nos 3 repositórios
+ 1 teste de update parcial por perfil (reproduzem o bug no código antigo).

### 1.2 ✅ Tag da IA chega CRUA ao cliente no caminho de handoff
**Onde:** `outbound/OutboundService.java:459` (caso 1 da matriz: needsHuman + reply).
**Problema:** a cadeia de ~40 `maybeProcess*` (que interpreta e REMOVE as tags) só roda
no ramo `needsHuman == false` (linha 486+). Se a IA emite tag + needsHuman, o cliente
recebe o JSON cru no WhatsApp. Agravante (achado irmão): tag **alucinada de outro
perfil** também não é removida — o early-return de perfil devolve o reply intacto.
**Resolução:** sanitizador genérico de tags (`<tag>{...}</tag>` de nomes conhecidos do
formato) aplicado (a) no caminho de handoff **sem agir** — o humano assume, nenhuma
entidade é criada — e (b) no fim da cadeia normal, como rede de segurança para tag de
perfil errado. WARN logado quando algo é removido. + testes.

### 1.3 ✅ XSS armazenado nos blocos `meada_*` do CMS
**Onde:** `frontend/components/cms/blocks/meada-sections.tsx:142,208,238,457,477`
(+ `meada-hero.tsx`, `meada-chrome.tsx`).
**Problema:** href/src renderizados direto das props (`href={s.linkHref || '#'}`), sem
`safeUrl()`. Um tenant com CMS pode gravar `javascript:...` num link e o visitante do
site público executa. Os blocos genéricos e a onda 1 já usam `safeUrl`.
**Resolução:** todos os href/src dos blocos meada_* passados por `safeUrl()`
(`frontend/lib/cms/safe-url.ts`).

### 1.4 ✅ Site público continua servido para empresa SUSPENSA
**Onde:** `cms/CmsSiteRepository.java:60` (`companyIdBySlug`) e `:87`
(`findByVerifiedDomain`).
**Problema:** as queries públicas não filtram suspensão (existente desde a migration
26) — `/p/{slug}`, domínio custom e `tls-allowed` seguem servindo o site de empresa
suspensa pelo root.
**Resolução:** filtro de empresa não-suspensa nas queries públicas +
`CmsPublicControllerIntegrationTest` (suspensa → 404 nos 3 caminhos).

### 1.5 ✅ Aprovações do chassi D sem barreira de contato
**Onde:** `AprovacaoOsHandler` (oficina), `AprovacaoPropostaHandler` (eventos),
`AprovacaoCasamentoHandler`, `AprovacaoViagemHandler`, `AprovacaoAtelieHandler`,
`AprovacaoArteHandler` (papelaria).
**Problema:** a tag de aprovação resolve o artefato só por `companyId` — uma conversa
de contato A pode aprovar/recusar a OS/proposta do contato B (a IA pode alucinar o id,
ou o cliente pode chutar). Os handlers de Confirmação (chassi A) JÁ têm a barreira.
**Resolução:** contactId da conversa passado aos handlers de aprovação; mismatch →
ignora com WARN (best-effort), artefato intocado. + testes.

### 1.6 ✅ Stack docker-compose de dev rejeitava TODO login de tenant (issuer mismatch)
**Onde:** `JwtAuthenticationFilter` (issuer derivado de `SUPABASE_URL`) ×
`docker-compose.override.yml` (SUPABASE_URL = IP do WSL).
**Problema:** a validação de issuer (anti-token-de-outro-projeto) deriva o `iss` esperado
do `SUPABASE_URL`. No compose dev, essa URL é o IP do WSL (alcance container→host), mas o
GoTrue local emite `iss=http://127.0.0.1:54321/auth/v1` → **401 `invalid_claims` para
qualquer tenant** no stack dev. Pego AO VIVO pela suíte Selenium desta empreitada (a 1ª
rodada passou porque um backend host segurava a porta 8095; com o stack compose no ar, 11
dos 20 testes caíram).
**Resolução:** propriedade dedicada `supabase.issuer` (vazia = deriva da URL — produção
intocada) + `SUPABASE_ISSUER=http://127.0.0.1:54321/auth/v1` no override de dev + recreate
do container. Validado: `/admin/me` → 200 `tenant_admin` e suíte Selenium 20/20 de novo.

---

## 2. MÉDIOS — robustez e experiência (todos os nichos)

### 2.1 ✅ Kanban de pedidos engole erro em silêncio (15 telas corrigidas)
**Onde:** `comida-orders/page.tsx:157` e telas irmãs.
**Problema:** `statusMutation` sem `onError` — um 409 `invalid_status_transition` (dois
atendentes mexendo no mesmo pedido) não mostrava NADA; o card voltava sozinho e o tenant
achava que o sistema falhou. A auditoria completa achou **30 telas** com `statusMutation`
sem onError.
**Resolução:** `onError` com mensagem visível + refetch aplicado nas **15 telas de clone
exato** (adega, barber-queue, comida, floricultura, lãs, lavanderia, legal-deadlines,
lingerie, moda infantil, ótica-orders, padaria, papelaria, pizzaria, suplementos, sushi).
As 15 restantes têm `onSuccess` multi-linha (fecham diálogos etc.) e onError em outras
mutações do arquivo — replicar o mesmo padrão nelas ficou no backlog 3.13.

### 2.2 ✅ Chassi F (entrega read-only) sem catch-all best-effort
**Onde:** `EntregaPlanoHandler.java` (nutri) e irmãos (dermatologia/preparo,
fotografia/link, cursos/módulo).
**Problema:** try/catch cobre só o parse; lookup de repositório que lança
RuntimeException derruba o envio da resposta inteira da IA (contrato manda handler ser
best-effort).
**Resolução:** corpo pós-parse envolvido em try/catch(RuntimeException) → WARN +
retorno vazio; resposta ao cliente nunca é derrubada por falha do handler. + testes.

### 2.3 ✅ Barbearia: qualquer erro inesperado vira 409 `conflict_slot`
**Onde:** `BarberQueueController.java:168`.
**Problema:** `catch (RuntimeException) → 409 conflict_slot` — banco fora do ar vira
"horário ocupado" pro atendente (único controller de profiles/ com catch genérico).
**Resolução:** catch da exceção tipada de conflito; RuntimeException inesperada sobe
pro GlobalExceptionHandler (500 honesto). + teste.

### 2.4 ✅ Comida: mudar fidelidade não invalida o cache do prompt da IA
**Onde:** `ComidaLoyaltyConfigService.java:35`.
**Problema:** o `ComidaMenuCache` embute o bloco FIDELIDADE no prompt e seu javadoc
promete invalidação em toda mutação — mas o service de fidelidade não invalida (o
irmão barbearia invalida). A IA anuncia prêmio/threshold velho por até 60s.
**Resolução:** injeção do cache + `invalidate(companyId)` no update. De carona: 2
javadocs com "adega" vazado do clone (`ComidaLoyaltyConfigService.java:11`,
`ComidaCouponController.java:31`). + teste.

### 2.5 ✅ Schema de teste divergente da produção (4 migrations fora do SCRIPTS)
**Onde:** `AbstractIntegrationTest.java` (SCRIPTS pulava 48, 70, 71 e 118).
**Problema:** `companies.admin_token` (71) não existia no banco de teste — o
provisionamento de tenant-admin ficava silenciosamente intestável (o catch engole);
`niche_showcase` (48) idem — vitrine de nichos sem cobertura possível; `subscriptions`
(118, a mais recente) também fora.
**Resolução:** 48/70/71/118 adicionados na posição numérica (verificado: nenhuma
reescreve a CHECK de profile_id; a 70 depende da company-âncora da 44, já presente) +
`MigrationScriptsCompletenessTest` que acusa omissão futura (diretório × array, com
allowlist explícita).

### 2.5b ✅ Teste flaky por horário do relógio (falha real vista no baseline)
**Onde:** `BarbeariaOnda2IntegrationTest.convertQueueTicket`.
**Problema:** a conversão da fila agenda em `Instant.now()` e o teste não semeava
`barber_config` — caía no default 09h–20h e FALHAVA em qualquer run fora do horário
comercial (falha literal observada no baseline desta empreitada, rodado à ~01:30:
`OutsideHoursException`).
**Resolução:** seed de janela cheia (00:00–23:59:59) no teste; o teste novo de
conversão da fila (2.3) já nasce protegido.

### 2.6 ✅ Textos de nicho ERRADO vazados de clone (UX direta)
- `oficina-catalog/page.tsx:111,184,195` — oficina mecânica exibindo "Materiais e
  técnicas", "Bordado à mão, forro de cetim", "tecido, acabamento" (clone do ateliê).
- `floricultura-catalog/page.tsx:146-159` — página chama o catálogo de "Cardápio"
  enquanto a sidebar chama "Catálogo" (vocabulário do comida).
**Resolução:** textos trocados para o domínio correto.

### 2.7 ✅ Editor CMS salva em silêncio
**Onde:** `dashboard/cms/page.tsx:204` (`savePageMut` sem onError).
**Problema:** salvar página que estoura limite do backend (30 linhas/6 colunas/50
blocos → 400 `invalid_blocks`) não mostra nada — o tenant acha que salvou.
**Resolução:** onError com mensagem mapeando `invalid_blocks`. (Espelhar os limites no
editor — desabilitar botões — fica no backlog 3.6.)

### 2.8 ✅(parcial) Toggles/deletes core com erro invisível
**Onde:** `faqs/page.tsx:60`, `services/page.tsx:65` e telas irmãs; detalhe do processo
legal (`cases/[id]/page.tsx`) e wishlists/trade-in da concessionária (mutações sem
NENHUM tratamento).
**Problema:** `onError: console.error` sem render — falha de RLS/rede é invisível.
**Resolução:** erro visível renderizado em **faqs** e **services** (as duas com evidência
direta). Legal (`cases/[id]`) e concessionária (wishlists/trade-in) ficaram no backlog
3.13 com o mesmo padrão descrito.

### 2.9 ✅ Domínio custom do CMS: bloqueio por sufixo sem ponto
**Onde:** `CmsService.java:93`.
**Problema:** `domain.endsWith("meadadigital.com")` rejeita domínios legítimos como
`minhameadadigital.com`.
**Resolução:** igualdade OU `.endsWith(".meadadigital.com")` (idem `.local`).

---

## 3. BACKLOG — reportado, NÃO mexido (decisão de arquiteto / onda própria)

| # | Achado | Onde | Resolução proposta |
|---|--------|------|--------------------|
| 3.1 | **Domain squatting**: tenant grava domínio de terceiro sem verificar e a UNIQUE global bloqueia o dono legítimo | `CmsService.setDomain` + migration 41 (`domain text unique`) | Índice parcial `UNIQUE ... where domain_verified = true` + resolução de conflito no verify (mudança de schema) |
| 3.2 | `safeFrameSrc` aceita QUALQUER https no bloco map (iframe com sandbox permissivo) | `frontend/lib/cms/safe-url.ts:34` | Allowlist de hosts (Google Maps), como o `safeVideoEmbedSrc` — pode quebrar embeds existentes, decidir antes |
| 3.3 | Sem guard de wrong-profile no frontend: tela de nicho alheio renderiza shell com erro genérico após retries | grep `forbidden_wrong_profile` em frontend/ = 0 hits | Tratamento transversal do reason 403 (padrão "Acesso restrito" de profile-features) ou guard central por perfil |
| 3.4 | 5 telas superAdminOnly sem branch 403 "Acesso restrito" (health, jobs, errors, plans, announcements); `/dashboard/palettes` sem gate NENHUM (só catálogo hardcoded, sem vazamento) | ex.: `health/page.tsx:39`, `palettes/page.tsx:47` | Replicar branch 403 de `users/page.tsx:35` ou extrair `<SuperAdminGate>` |
| 3.5 | `isMeadaHost` hardcoded — painel inacessível via IP da LAN/staging (rewrite pro CMS público) | `frontend/lib/profiles/subdomain.ts:106` | Hosts extras via env (`MEADA_PANEL_HOSTS`) |
| 3.6 | Limites do page-builder (30 linhas/6 colunas/50 blocos) não espelhados no editor | `frontend/lib/cms/tree-ops.ts` | Desabilitar botões de adicionar ao atingir o limite |
| 3.7 | 4 pares de nichos com a MESMA paleta default: sushi=restaurant, salon=atelie, pousada=lavanderia, estetica=floricultura | `profile-type.ts` | Confirmar se intencional; se não, 4 paletas novas (+ teste de unicidade com allowlist) |
| 3.8 | CHECKs de profile_id **não-monotônicas** nas migrations antigas (53, 58, 60, 61, 63 têm listas menores que as anteriores em ordem de filename) — replay numérico sobre banco POPULADO falharia | `supabase/migrations/53_adega.sql:47` etc. | NÃO editar migration antiga; documentar a ordem canônica (a do SCRIPTS) e/ou teste de monotonicidade |
| 3.9 | Buraco na numeração (57 nunca existiu) e doc citando migration fantasma | `docs/PROMPT_NICHO_ATELIE.md:109` | Corrigir a referência no doc |
| 3.10 | Controllers sem teste de integração: otica (exams+orders — único híbrido A+B), showcase (`/public/niches`), casamento-catalog, eventos-packages, estetica-procedures, las-yield, barbearia-loyalty, sushi-config; cupons de 6 perfis | vários | Onda de testes própria; priorizar otica (template de híbridos) e showcase (endpoint público) |
| 3.11 | Frontend sem infra de teste unitário (0 frameworks) — propostas RTL da auditoria não executáveis | `frontend/package.json` | Decidir vitest+RTL em onda própria; por ora a suíte Selenium cobre funcional |
| 3.12 | Provisionamento de tenant-admin engole RuntimeException e retorna null (mascarava a coluna ausente do 2.5) | `CompanyAdminController`/`CompanyAdminRepository.java:87` | Com a 71 no schema de teste, cobrir com teste de integração (stub do SupabaseAdmin) |
| 3.13 | onError pendente nas 15 telas de status com `onSuccess` multi-linha (appointments, memberships, proposals etc.) + mutações de legal `cases/[id]` e concessionária wishlists/trade-in | ver 2.1/2.8 | Replicar o padrão aplicado nas 15 telas corrigidas (estado `statusError` + render destructive) |
| 3.14 | 5 handlers de tag SEM teste: AvisoEstoqueModa, AvisoEstoqueLingerie, ListaEsperaLas, DesejoCarroConfirm, ConfirmacaoTestDrive | `OutboundService.java` (injeções) | Clonar o padrão `ConfirmacaoBarbeariaHandlerTest` (age + barreira de contato + strip + best-effort) — 1 arquivo por handler |

---

## 4. Testes novos criados nesta empreitada

| Teste | Invariante travada |
|-------|--------------------|
| `ProfileIdCheckConstraintIntegrationTest` | CHECK de `companies.profile_id` contém TODOS os ids do `ProfileType` (armadilha nº 1 do CLAUDE.md — sed removendo perfis da lista) |
| `MigrationScriptsCompletenessTest` | diretório de migrations × array SCRIPTS (allowlist explícita) — já pegou 48/70/71/118 |
| updateItem parcial em oficina/eventos/viagens (3 cenários) | `line_total_cents` materializado em Java (bug 1.1) |
| 4 cenários novos no `OutboundServiceIntegrationTest` | strip de tag no handoff e no fim da cadeia — cliente nunca vê JSON cru (bug 1.2) |
| barreira de contato em 6 handlers de aprovação (oficina/eventos/casamento/viagens/atelie/papelaria) | conversa de A não aprova artefato de B (bug 1.5) |
| `EntregaPlanoHandlerBestEffortTest` + `EntregaModuloHandlerBestEffortTest` | RuntimeException nos handlers F não derruba a resposta da IA (2.2) |
| conversão da fila da barbearia (feliz + conflito) | caminho tipado do `conflict_slot` (2.3) — fluxo antes sem NENHUM teste |
| invalidação do menu cache na fidelidade comida + validações | prompt da IA reflete config nova (2.4) |
| CMS público de empresa suspensa → 404 (slug/domínio/TLS) + regra de domínio por label | (1.4/2.9) |
| half-open de slot em dental/dermatologia/fotografia/nutri/otica/restaurant + `outside_hours` na estética | janela `NOT (end <= :s OR start >= :e)` não regride nos 6 perfis sem esse assert |
| seed de janela cheia no `convertQueueTicket` | mata o flaky por horário do relógio (2.5b) |
| suíte Selenium `scripts/selenium_tests/` (20 testes) | login, varredura de sidebar em 6 nichos, CRUD core via UI, contratos REST multi-perfil |

## 5. Gates executados (resultados LITERAIS — 2026-07-11)

| Gate | Baseline (pré-fixes) | Final (pós-fixes) |
|------|----------------------|-------------------|
| `mvn -B clean test` (Surefire) | `Tests run: 1951, Failures: 0, Errors: 1` (erro = flaky por horário, item 2.5b) | **`Tests run: 1981, Failures: 0, Errors: 0` — BUILD SUCCESS** (+30 cenários novos) |
| Subconjunto incremental pós-fix do issuer (filtro recompilado) | — | `Tests run: 6, Failures: 0, Errors: 0` |
| `npm run lint` | 0 erros / 3 warnings conhecidos | **0 erros / 3 warnings conhecidos** (gate mantido) |
| `npm run build` | limpo | **limpo** |
| Suíte Selenium (20 testes, 6 nichos, stack compose real) | 20/20 na 1ª rodada (backend host); 9/20 ao trocar pro stack compose (achado 1.6) | **20/20 após o fix do issuer + todos os fixes** |

Working tree entregue SEM commit — revisão do Igor antes de commitar (nada de
`external_projects/` foi tocado).
