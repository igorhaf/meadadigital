# Meada WhatsApp

SaaS multi-empresa de atendimento ao cliente via WhatsApp com IA, **multi-perfil**: um monolito
que se apresenta como N produtos verticais (Meada genérico, ProcessoBot/advocacia,
DentalBot/odonto, SushiBot/sushi). Cada empresa (tenant) tem um atendente de IA treinado com
seus próprios dados; isolamento por tenant via RLS.

- **Backend:** Spring Boot 3.3.13 + Java 17, JdbcTemplate (sem JPA/Lombok). Porta 8095.
- **Frontend:** Next 16 (App Router) + React 19 + Tailwind 4 + shadcn/ui. Porta 3000.
- **Banco/Auth:** Supabase (Postgres 17 + Auth + Storage), **remoto** (cloud).
- **IA:** Gemini Flash · **WhatsApp:** Evolution API · **Embeddings:** sidecar Python (e5-small).

Convenções e regras do projeto: ver [CLAUDE.md](CLAUDE.md).

## Quick start (Docker — caminho cravado)

Pré-requisitos: Docker + Docker Compose v2, `.env` (backend) e `frontend/.env.local`
preenchidos, e as entradas de `/etc/hosts` (ver [docs/MULTI_PROFILE_DEV.md](docs/MULTI_PROFILE_DEV.md)).

```bash
./scripts/meada-up.sh      # sobe backend + frontend + embeddings + caddy (porta 80)
# … desenvolve com hot-reload …
./scripts/meada-down.sh    # derruba os containers
```

Acesse **sem porta**:

| URL                                   | Produto              |
|---------------------------------------|----------------------|
| `http://meada.meadadigital.local`     | Meada (universal)    |
| `http://processo.meadadigital.local`  | ProcessoBot (legal)  |
| `http://dental.meadadigital.local`    | DentalBot (dental)   |
| `http://sushi.meadadigital.local`     | SushiBot (sushi)     |
| `http://api.meadadigital.local`       | Backend / API        |

O **banco continua no Supabase remoto** — os containers são stateless; subir/derrubar não mexe
nos dados.

## Testes (gate de qualidade)

Os testes de integração rodam no **host** (exigem Supabase real + Testcontainers):

```bash
JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64 mvn -B test    # backend
cd frontend && npm run build                                # frontend (type-check + build)
```

## Modo legado (sem Docker, não-recomendado)

`./scripts/run-local.sh` (backend) + `cd frontend && npm run dev`. Esbarra no Apache na porta
80; serve só para debug pontual. Ver [docs/MULTI_PROFILE_DEV.md](docs/MULTI_PROFILE_DEV.md).
