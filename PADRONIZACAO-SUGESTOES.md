# PADRONIZACAO-SUGESTOES — melhorias que exigiriam decisão/lib nova (NÃO aplicadas)

Registradas conforme a trava "sem dependências novas"; nada aqui foi instalado ou alterado.

1. **Prettier + prettier-plugin-tailwindcss** — ✅ FEITO em 2026-07-03 com autorização explícita do
   Igor (lotes P0–P4, ver PADRONIZACAO-LOG.md): prettier + plugin-tailwindcss + sort-imports
   instalados, frontend inteiro (410 arquivos) reformatado, `npm run format`/`format:check`.
   Resta a decisão de pôr `format:check` como gate de CI.
2. **ESLint no CI** — ✅ a parte executável foi feita (lotes F3–F7, ver PADRONIZACAO-LOG.md): o hook
   `useSyncedForm`/`useOnSync`/`useResetWhen` (`frontend/lib/use-synced-form.ts`, sem lib nova) zerou
   as 60 ocorrências de `set-state-in-effect` e o lint fechou em 0 erros. Resta a decisão de rodar
   `npm run lint` como gate de CI (hoje o gate é `next build` + mvn).
3. **Checkstyle/Spotless no backend** — não há lint Java; um formatter com config mínima (imports,
   indentação) tornaria o cânone verificável. Exige plugin Maven novo.
4. **Extração dos clones de motor (cupom/fidelidade)** — ✅ INICIADA 2026-07-03 por decisão do
   Igor: motor de cupom unificado (fatia 1, commit 4917aca, −997 linhas, 1848 verdes); roadmap
   das demais fatias em docs/UNIFICACAO_CHASSIS.md. Texto original: — `{Sushi,Adega,Comida,Atelie,Wedding,Barber}Coupon*`
   são clones deliberados por nicho (decisão de arquitetura: isolamento por perfil > DRY). Se um dia
   compensar, um módulo `com.meada.common.coupons` parametrizado por tabela eliminaria ~6× o código —
   decisão do arquiteto, não de padronização.
