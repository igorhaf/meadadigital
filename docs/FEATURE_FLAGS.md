# Feature Flags por Nicho (camada 9.0)

Infra de plataforma onde o **root** (super-admin) liga/desliga features por **nicho** (perfil
vertical). A primeira feature é o **CMS** (página pessoal por tenant) — esta camada entrega só a
infra + o gate; o CMS real vem numa entrega seguinte.

## Modelo

- **Feature = hardcoded.** Cada feature é um membro de `ProfileFeature` (enum Java) espelhado em
  `frontend/lib/profiles/profile-feature.ts`, com `ProfileFeatureParityTest` garantindo a paridade.
  Membro atual: `cms`.
- **Nicho = hardcoded** (`ProfileType`). Não há tabela de perfis.
- **Default = OFF.** A tabela `profile_features` guarda só os DESVIOS; a ausência de uma linha
  significa "desligado". Toda feature nasce desligada — é opt-in explícito do root (tem nicho que não
  tem site, então off-pra-todos é o estado natural).
- **A grade é computada:** nichos × features, com as linhas do banco sobrepostas ao default OFF.

## Quem mexe / quem lê

- **Root** liga/desliga na tela `Plataforma → Features` (`/dashboard/profile-features`):
  linhas = nichos, colunas = features, cada célula é um toggle.
  - `GET /admin/profile-features` → a grade.
  - `PUT /admin/profile-features/{profileId}/{featureKey}` `{ "enabled": true|false }` → liga/desliga.
  - Autorização: super-admin only (403 `forbidden_not_super_admin` para tenant; 401 sem token).
  - Validação: `unknown_profile` / `unknown_feature` (400) para ids fora dos catálogos.
  - Toda mudança é auditada (`PROFILE_FEATURE_TOGGLED` em `admin_action_log`).
- **Tenant** NÃO lê a tabela direto. Recebe o estado resolvido do seu nicho em
  `GET /admin/me` no campo `features` (`{ "cms": true, ... }`). No frontend:
  `hasFeature(me, 'cms')`.

## Gate (backend)

Endpoints que pertencem a uma feature chamam, no início:

```java
featureGuard.requireFeature(user, ProfileFeature.CMS); // 403 feature_disabled se OFF para o nicho
```

## Adicionar uma feature nova

1. Adicione o membro em `ProfileFeature.java` **e** em `profile-feature.ts`.
2. Estenda a CHECK de `profile_features.feature_key` (nova migration) para incluir o key.
3. Rode os testes (paridade + service).
4. Quem consome a feature usa `requireFeature(user, ProfileFeature.X)` (backend) e
   `hasFeature(me, 'x')` (frontend).

## Notas

- Sem CHECK de `profile_id` na tabela (não acopla a cada perfil futuro) — a validação é app-level.
- Tabela de plataforma: RLS `enable+force`, só `service_role`. O root opera fora do RLS; o tenant
  recebe o resolvido via backend.
- Resolver com cache (Caffeine, TTL 20s por nicho), invalidado a cada toggle.
