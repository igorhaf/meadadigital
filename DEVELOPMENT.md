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
assimétrica). O filtro JWT do Spring (sub-fase 4.1, em `com.meada.whatsapp.admin.security`)
deve ler `SUPABASE_JWT_SECRET` do env e validar os tokens via `MACVerifier` do
nimbus-jose-jwt. Sem JWKS endpoint, sem rotação automática — o secret é estático e
rotacionado manualmente se necessário.

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
