# Unificação dos chassis — programa de emagrecimento do monolito

Decisão do Igor (2026-07-03): unificar os clones estruturais dos perfis em motores
parametrizados em `com.meada.common.*`, para reduzir o peso do código E o custo de
leitura/tokens das sessões. Este arquivo é o roadmap vivo do programa.

## Regras do programa (invariantes)

1. **Zero mudança de contrato ou comportamento.** Rotas, JSON (nomes de campo por perfil),
   reasons, auditoria e semântica transacional ficam IDÊNTICOS. Records/DTOs por perfil são
   PRESERVADOS (são contrato de API — ex.: academia expõe `minCents`, os demais `minOrderCents`).
2. **Gate: `mvn -B clean test` completo verde**, com os testes por perfil INALTERADOS (fora
   linhas de import) — eles são a prova de preservação. Frontend intocado por construção.
3. **Rotas nunca parametrizadas** (`/api/{nicho}/...` NÃO existe) — controllers continuam por
   perfil (guard + reasons são o contrato HTTP); o que unifica é repo/service/lógica.
4. **Uma fatia por commit `[REVISAR]` isolado**, revertível com `git revert`.
5. **Import de exceção aninhada exige nome CANÔNICO** (lição da fatia 1): exceção movida pra
   base ⇒ ajustar os imports nos controllers/testes (`CouponServiceBase.DuplicateCouponException`);
   referência qualificada via subclasse em código continua válida.

## Fatias

| # | Alvo | Clones | Status |
|---|------|--------|--------|
| 1 | **Motor de cupom** (`common.coupons`: CouponRecord + RepositoryBase + ServiceBase) | 7 (sushi/adega/atelie/barber/wedding/comida/academia) | ✅ FEITA 2026-07-03 (commit 4917aca, −997 linhas, 1848 verdes) |
| 2 | **ContextCache base** (Caffeine TTL parametrizado + invalidação por company) | ~20 caches de contexto da IA | pendente — risco baixo |
| 3 | **Notifier base** (notificação outbound por status, best-effort, dry-run) | ~15 notifiers | pendente — risco baixo |
| 4 | **CRUD de catálogo simples** (professionals/services/mechanics/planners…: list/create/patch/archive/delete-em-uso→409) | ~30 pacotes | pendente — o maior ganho de massa; risco médio |
| 5 | **TagHandler base** (hasTag/stripTag/parse regex + best-effort + barreira de contato) | ~25 handlers | pendente — risco médio (cada tag tem payload próprio) |
| 6 | **Máquina de status** (enum + allowedNext + 409 invalid_status_transition + parity harness) | ~20 enums | pendente — risco médio |
| 7 | **Chassi de pedido order-based** (create/recalc/Kanban/gate de aceite da família comida) | ~9 perfis | pendente — o mais pesado; só como onda própria com smoke por perfil |

Ordem recomendada: 2 → 3 → 4 (massa) → 5 → 6 → 7. Cada fatia começa com o diff estrutural
(`sed exemplar→alvo | diff`) pra mapear a variância REAL antes de desenhar a base — a fatia 1
achou 3 variantes não-óbvias (coluna `min_cents`, `decrementUses`, `validate()` da academia).

## Relação com o resto da economia de tokens

- **CLAUDE.md**: dieta feita 2026-07-03 (1708→386 linhas; chassis documentados uma vez +
  tabela-catálogo). Perfil novo NÃO ganha seção — 1 linha na tabela + guia em docs/.
- **Gerador**: `scripts/gerar-perfil.py` elimina o loop ler-clonar-adaptar de onda nova.
  Conforme as fatias avançam, o gerador clona cada vez MENOS código (as bases já existem) —
  os dois programas convergem.
- **Skills** (`.claude/skills/`): o cânone consultável sem reler código.
