# DEVELOPMENT — Meada WhatsApp

Notas de setup do ambiente local e de CI. Pendências ambientais (não de produto)
ficam aqui. Riscos de produto vivem em RISKS.md.

---

## docker-java.properties com api.version=1.44

- **Onde:** `src/test/resources/docker-java.properties`
- **Conteúdo:** `api.version=1.44`
- **Por quê:** Docker daemon 29.x elevou a API mínima para 1.44. Testcontainers 1.x
  (todas as versões 1.19.8 a 1.20.6 testadas, e por extensão todo o ramo 1.x)
  inicializa a docker-java com API 1.32 quando a negociação automática não ocorre
  — o daemon 29+ recusa com "client version 1.32 is too old". A property
  `api.version=1.44` força a docker-java a usar a API moderna no startup,
  contornando o bug. Fonte: testcontainers-java issue #11210 e on_failure.html.
- **Critério de remoção:** quando o BOM do Spring Boot atualizar para
  Testcontainers ≥2.x (que negocia API automaticamente). Remover o arquivo e
  re-rodar `mvn test` para confirmar.

---

## Comando padrão de teste

```
JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64 mvn -B clean test
```

Não requer envs adicionais — `docker-java.properties` resolve a negociação,
`@DynamicPropertySource` injeta URL/credenciais do container Testcontainers,
`WEBHOOK_SECRET=test-secret` é setado em `AbstractIntegrationTest`.

Para subir a app localmente contra Supabase descartável: ver `application.yml`
e exports de env vars (SPRING_DATASOURCE_URL/USERNAME/PASSWORD + WEBHOOK_SECRET).
A porta local pode colidir com o Orbit (8080) — usar `SERVER_PORT=8088` em dev.

---

## JDK 17 Temurin (não JDK 21 do sistema)

- Maven deve usar Temurin 17, não o JDK 21 default do Ubuntu.
- `JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64` no `~/.bashrc` ou prefixado em
  cada comando `mvn`. Confirma com `mvn -version` (espera "Eclipse Adoptium").
- Razão: Spring Boot 3.3.13 oficialmente suporta 17 e 21, mas prod roda 17 —
  build sob outra versão introduz divergência de annotation processors e
  features sintáticas do javac.

---

## Pré-requisitos de ambiente

- **Docker** acessível via `/var/run/docker.sock` (daemon 29.x OK). Os integration
  tests sobem PostgreSQL 17-alpine via Testcontainers.
- **Maven** 3.8.7+ no PATH.
- **JDK 17 Temurin** (ver acima).

## Follow-ups técnicos

### Evolution: investigar tag que exponha `remoteJidAlt` em 1:1
A `evoapicloud/evolution-api:v2.3.1` (usada na validação E2E local) **não** expõe `remoteJidAlt` nem `senderPn` (`@s.whatsapp.net`) em mensagens `@lid` 1:1 — validado contra 39 mensagens reais (0 traziam o número). Investigar se há uma tag mais recente de `evoapicloud/evolution-api` que exponha `remoteJidAlt` de forma consistente em 1:1. Se houver, subir essa versão **é frente própria** (operação de infra Docker, exige nova validação E2E — como foi o salto v2.1.1 → v2.3.1 que destravou o pareamento), separada do código. Com o campo disponível, capturar o payload real e adicionar o ramo de recuperação ao `MessagePayloadNormalizer` (ver item "@lid" no [RISKS.md](RISKS.md)).

### Frontend: 4 vulnerabilidades moderate transitivas no npm audit (camada 4.0)
Detectadas no install inicial do `frontend/` (camada 4.0). Todas em deps transitivas
(não declaradas diretamente). Não acionadas no MVP (painel admin interno, dev). Revisar
antes de qualquer deploy público: rodar `npm audit` para listar, avaliar se é possível
resolver com `npm audit fix` (sem `--force`) ou se exige bump de dep direta. NUNCA rodar
`npm audit fix --force` automaticamente — pode quebrar versões cravadas + package-lock.

## Frontend (camada 4)

### Bootstrap de usuário de teste / super-admin
O painel não tem cadastro self-service (MVP). Usuários nascem por seed manual:
- **Admin de tenant:** (1) criar o usuário no Supabase Auth (painel meada-delta-01 →
  Authentication → Users → "Add user", email+senha; ou via Admin API); (2) inserir a
  linha correspondente em `public.users` via SQL (`id` = uid do auth user, `company_id`
  = empresa dele, `role` em owner|admin|agent). Sem a linha em public.users, o
  `app.company_id()` retorna NULL e o RLS bloqueia tudo.
- **Super-admin meada:** (1) criar o usuário no Supabase Auth (SEM linha em
  public.users); (2) adicionar o email à allowlist `admin.super-admin-emails` no
  application.yml do backend (entra na sub-fase 4.1, junto com o filtro JWT). A
  allowlist é o que distingue super-admin — ele opera sempre via Spring (service_role),
  fora do RLS.

### Nota: @supabase/ssr em linha 0.x
`@supabase/ssr` é a lib oficial da Supabase para o Next app router (sessão em cookies,
server+client). É mantida na linha 0.x estável — a versão 1.0 nunca foi lançada; 0.x é
a linha de produção recomendada pela própria Supabase, sem alternativa mais madura. Não
é abandono nem beta — não "migrar para 1.0" achando que 0.x é provisório.

### P1 cravado: validação do JWT Supabase no Spring (input para 4.1)
O Supabase meada-delta-01 assina o JWT com **HS256 + secret compartilhada** (Settings →
API → JWT Settings: Algorithm HS256, JWT Secret estático; SEM JWKS URL, sem chave
assimétrica). Detalhamento técnico completo + onde o filtro mora: ver seção
"Camada 4.1 — decisões de auth/admin" abaixo (fonte canônica).

### Lições de processo (camada 4.0 — frontend)
1. **Aprovar conteúdo de arquivo ≠ arquivo criado.** Após cada "aprovado", o Write
   literal deve aparecer no output antes de seguir para o próximo arquivo. No 4.0 o
   `lib/supabase/server.ts` foi aprovado mas nunca escrito (passou direto ao
   middleware) — só pego no sanity check pré-commit. Disciplina: Write visível por
   aprovação; se não apareceu, cobrar antes de avançar.
2. **`npx next build` no critério de "fechado" de toda sub-fase 4.x que toca frontend.**
   O Turbopack em dev compila lazy — um import quebrado pode passar despercebido se a
   rota afetada não executar o code path no momento certo (foi o caso: smoke test 5/5
   verde em dev com o server.ts faltando). O build de produção resolve todos os imports
   estaticamente e é honesto. Smoke test em dev NÃO substitui build.
3. **Critério de "fechado" do 4.0 (e padrão para 4.x frontend):** `mvn -B clean test`
   verde (backend intacto) + `next build` limpo + smoke test manual sobre o estado
   PÓS-build (não sobre estado intermediário).

## Camada 4.1 — decisões de auth/admin

Estas 12 decisões foram cravadas em prosa antes de qualquer código da 4.1 (padrão das
fases 3.x). Surgem do trade-off "frontend chama backend sempre" (c-revisado da
arquitetura híbrida), do P1 já cravado (HS256 + secret compartilhada), e do escopo da
camada 4 (super-admin via Spring, tenant-admin via SDK+RLS). Estão agrupadas por bloco
temático: A=transporte JWT, B=validação no Spring, C=estrutura de código, D=frontend,
E=bootstrap operacional. Esta seção é a FONTE CANÔNICA do assunto JWT/admin (a nota P1
acima é só um ponteiro).

**A1 — Transporte do JWT:** `Authorization: Bearer <token>` (header), não cookie
HTTP-only. Alinhado ao pattern Supabase; CORS sem credentials simplifica; debug com
curl/Postman trivial.

**A2 — Frontend obtém o token:** `supabase.auth.getSession()` a cada chamada,
encapsulado num wrapper `apiFetch()`. Sem provider próprio (duplicaria o state que o
@supabase/ssr já gerencia).

**B1 — Onde o filtro guarda a identidade:** `request.setAttribute("authenticatedUser", au)`.
Não SecurityContext (não usamos Spring Security), não ThreadLocal próprio (pool reusa
thread → mesma pegadinha do MDC na 3.4).

**B2 — Eager (filtro resolve, controller só lê):** o filtro produz um `AuthenticatedUser`
completo. **Otimização super-admin:** se o email está na allowlist → `role=SUPER_ADMIN`,
`companyId=null`, SEM SELECT em public.users. Tenant-admin → SELECT `company_id, role`
em public.users WHERE id=sub; se NÃO encontrar linha → 403 `user_not_provisioned`
(evita 500 quando o user existe no Auth mas falta provisão em public.users). O controller
lê via `@RequestAttribute("authenticatedUser") AuthenticatedUser user`.

**B3 — Teste do filtro:** helper estático `mintToken(...)` em `AbstractAdminIntegrationTest`
(herda de AbstractIntegrationTest) que gera token HS256 via nimbus-jose-jwt (mesma lib
do prod), claims customizáveis. 6 casos: válido+allowlist → 200 super-admin; válido+linha
em public.users → 200 tenant-admin com companyId; válido+sem provisão → 403
user_not_provisioned; assinatura errada → 401; expirado → 401; sem header → 401.

**C1 — Sub-pacotes:** `admin.{security,companies,me,instances}`. `me` em pacote próprio
(endpoint de identidade, não de segurança). `instances` fica vazio na 4.1, popula na 4.6.

**C2 — DTOs:** records Java (padrão das camadas 2/3).

**D1 — Frontend lib/api:** `apiFetch` genérico em `lib/api/client.ts` (injeta o header
Authorization, trata 401/403) + helpers tipados por recurso (`lib/api/me.ts`,
`lib/api/companies.ts`). Híbrido: wrapper único para concerns transversais, helpers
tipados onde o TypeScript ajuda.

**D2 — Tratamento de erro HTTP:** 401 → `apiFetch` força `supabase.auth.signOut()` +
redirect /login (sessão morreu). 403 → propaga como `ApiError(403, msg)` para o caller
tratar inline (usuário está logado, só não pode ver aquilo; redirect confundiria).

**D3 — Tela companies:** client component com TanStack Query, sem SSR (painel admin, não
landing). Estabelece o pattern para o 4.5 (polling de conversas).

**E1 — Seed para o smoke test:** manual via SQL/painel Supabase, documentado aqui (não
em script versionado). Necessário: user igor.test@meada.dev no Auth (já existe da 4.0);
igor.test em ADMIN_SUPER_ADMIN_EMAILS; 1+ empresa em public.companies; SUPABASE_JWT_SECRET
no .env; para testar 403: tenant.test@meada.dev no Auth + linha em public.users.

**E2 — SUPABASE_JWT_SECRET:** obtido em Settings → API → JWT Settings → "Reveal", colado
no .env raiz. No .env.example raiz com comentário apontando onde pegar.

### Divisão 401 vs 403 (duas fontes de 403, documentadas)
- **401 (filtro):** token ausente, malformado, assinatura inválida, expirado → erro de
  AUTENTICAÇÃO (quem é você?).
- **403 user_not_provisioned (filtro):** token válido, mas tenant-admin sem linha em
  public.users → o filtro não consegue construir o AuthenticatedUser completo.
- **403 forbidden_not_super_admin (controller):** AuthenticatedUser construído, mas role
  insuficiente para o endpoint (tenant-admin em /admin/companies) → erro de AUTORIZAÇÃO
  (você pode fazer isso?).

### JWT do Supabase: HS256 (fonte canônica)
meada-delta-01 assina com HS256 + secret compartilhada. O JwtAuthenticationFilter (em
`com.meada.whatsapp.admin.security`) lê `SUPABASE_JWT_SECRET` e valida via MACVerifier do
nimbus-jose-jwt. Sem JWKS endpoint, sem rotação automática — o secret é estático e
rotacionado manualmente se necessário.

### Versão de nimbus-jose-jwt: 9.48 (linha 9.x)
O `pom.xml` fixa `com.nimbusds:nimbus-jose-jwt:9.48` (versão exata, sem range —
Maven não tem caret). Razão: 10.x evitada por incompatibilidade conhecida com o
ecossistema Spring Security — o `spring-security-oauth2-jose:6.3.8` (linha do
Spring Boot 3.3.13) fixa transitivamente `nimbus-jose-jwt:9.37.3`. Qualquer
dep Spring Security futura traria 9.x transitiva; pinar 10.x conflitaria.
Escolha de 9.48 (em vez de 9.37.3 exata): patches de segurança recentes
(9.38-9.48); descompasso de patch dentro da major é inócuo. MACVerifier/HS256
tem API idêntica em toda a 9.x — sem perda funcional.
