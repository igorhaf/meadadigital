# Suíte Selenium do Meada — testes funcionais de UI e contrato

Testes funcionais end-to-end sobre o ambiente local REAL (Supabase local +
backend 8095 + frontend 3000 em build de produção). Padrão espelhado da suíte
do Orbit (`~/orbit/scripts/selenium_tests`).

## Pré-requisitos

1. Supabase local rodando: `supabase start` (API :54321, Postgres :54322).
2. Backend rodando: `./scripts/run-local.sh` (porta 8095).
3. Frontend em **build de produção** (Turbopack dev é lazy e mascara erro):
   ```bash
   cd frontend && npm run build && npm run start
   ```
4. Python 3 + dependências: `pip install -r scripts/selenium_tests/requirements.txt`
   (Chrome instalado; o Selenium Manager resolve o chromedriver sozinho).

## Usuários de teste

A suíte usa usuários descartáveis `selenium.<nicho>@meada.test` (1 por chassi:
salon, sushi, lingerie, oficina, academia, nutri), amarrados às empresas-modelo
seedadas. **Não** usa os usuários reais do Igor. Seed idempotente:

```bash
python3 scripts/selenium_tests/seed_users.py
```

A senha é sintética e local-only (env `MEADA_SELENIUM_PASSWORD` sobrescreve o
default em `config.py`). Segredos reais (chaves, senha do banco) são lidos dos
envs reais do projeto em runtime — nada de segredo commitado aqui.

## Rodar

```bash
cd /home/igorhaf/meada
python3 -m pytest scripts/selenium_tests/ -v
```

Screenshots (de cada dashboard e de toda falha) ficam em
`scripts/selenium_tests/screenshots/`.

## O que cada arquivo cobre

| Arquivo | Cobre |
|---------|-------|
| `test_00_prerequisites` | ambiente de pé (backend 401 saudável, frontend 200, Supabase health, usuários logáveis) |
| `test_01_login` | senha errada → mensagem genérica anti-enumeration; login válido → dashboard; rota protegida sem sessão → redirect |
| `test_02_nav_smoke` | por nicho: visita TODOS os links da sidebar e falha em 404/500/crash React/erro SEVERE de console |
| `test_03_core_crud` | cria FAQ pela UI (caminho SDK+RLS) e confere invalidação do TanStack; limpeza via SQL |
| `test_04_api_contracts` | guard de perfil (403 `forbidden_wrong_profile`), shape do `/admin/me`, gate do CMS (`feature_disabled`), CMS público 404 |
