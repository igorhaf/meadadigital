# Suíte de testes funcionais do Meada

Testes que exercitam o produto REAL (Supabase local; as camadas seguintes somam
backend, frontend e browser via Selenium). Cada regra de negócio fechada em
`main` deixa aqui a sua suíte — e TODA a suíte roda de novo a cada fechamento
(regressão acumulada).

## Rodar

```bash
python3 -m pytest scripts/selenium_tests/ -v
```

Pré-requisitos: Supabase local rodando (`supabase start`), `.env` da raiz e
`frontend/.env.local` preenchidos (a suíte lê as chaves de lá — nenhum segredo
vive aqui), `pip install -r scripts/selenium_tests/requirements.txt`.

O laboratório (empresas `selenium-core-*` + usuários `selenium.*@meada.test`)
é semeado automaticamente pelo `conftest.py` — idempotente, senha sintética
local-only (env `MEADA_SELENIUM_PASSWORD` sobrescreve).

## O que cada arquivo cobre

| Arquivo | Regra | Cobre |
|---------|-------|-------|
| `test_01_rls_isolamento` | regra/1 | isolamento multi-tenant (RLS) pela API real: anon não lê, tenant só vê o próprio dado, escrita cross-tenant bloqueada, service_role é plataforma |
