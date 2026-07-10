# Multi-perfil — desenvolvimento local (camada 7.0)

Meada é um monolito que se apresenta como N produtos verticais ("perfis"). Em produção cada
perfil tem seu subdomínio; em dev local você simula isso com `/etc/hosts` + subdomínios do
`meadadigital.local`.

## Perfis vigentes

| profile_id | Produto       | Subdomínio | Paleta padrão  |
|------------|---------------|------------|----------------|
| `generic`  | Meada         | `meada`    | `meada-default`|
| `legal`    | ProcessoBot   | `processo` | `indigo`       |
| `dental`   | DentalBot     | `dental`   | `celeste`      |
| `sushi`    | SushiBot      | `sushi`    | `tijolo`       |
| `restaurant`| MesaBot      | `mesa`     | `tijolo`       |
| `salon`    | SalãoBot      | `salao`    | `orquidea`     |
| `pousada`  | PousadaBot    | `pousada`  | `oceano`       |
| `academia` | AcademiaBot   | `academia` | `pinheiro`     |
| `pet`      | PetBot        | `pet`      | `coral`        |
| `oficina`  | OficinaBot    | `oficina`  | `aco`          |
| `nutri`    | NutriBot      | `nutri`    | `salvia`       |
| `barbearia`| BarbeariaBot  | `barbearia`| `grafite`      |
| `eventos`  | EventosBot    | `eventos`  | `ambar`        |
| `estetica` | EsteticaBot   | `estetica` | `rosa-po`      |
| `comida`   | Comida        | `comida`   | `terracota`    |
| `pizzaria` | Pizzaria      | `pizzaria` | `carmim`       |

Fonte de verdade: `src/main/java/com/meada/profiles/ProfileType.java` +
`frontend/lib/profiles/profile-type.ts` (paridade garantida pelo `ProfileTypeParityTest`).

## `/etc/hosts` (uma vez)

```
127.0.0.1 meadadigital.local
127.0.0.1 meada.meadadigital.local
127.0.0.1 juridico.meadadigital.local
127.0.0.1 dental.meadadigital.local
127.0.0.1 sushi.meadadigital.local
127.0.0.1 mesa.meadadigital.local
127.0.0.1 salao.meadadigital.local
127.0.0.1 pousada.meadadigital.local
127.0.0.1 academia.meadadigital.local
127.0.0.1 pet.meadadigital.local
127.0.0.1 oficina.meadadigital.local
127.0.0.1 nutri.meadadigital.local
127.0.0.1 barbearia.meadadigital.local
127.0.0.1 eventos.meadadigital.local
127.0.0.1 estetica.meadadigital.local
127.0.0.1 comida.meadadigital.local
127.0.0.1 pizzaria.meadadigital.local
127.0.0.1 api.meadadigital.local
127.0.0.1 muda.meadadigital.local
127.0.0.1 conexao-municipio.meadadigital.local
```

> `muda.meadadigital.local` → projeto EXTERNO **muda** (Laravel, em `external_projects/muda`),
> roteado pelo Caddy pro `muda-nginx`. Em produção é `muda.meadadigital.com`.
>
> `conexao-municipio.meadadigital.local` → projeto EXTERNO **conexao-municipio** (Laravel API +
> Next, em `external_projects/conexao-municipio`). Caddy faz path-based: `/api/*` vai pro backend
> Laravel, o resto pro frontend Next (mesma origem, sem CORS). Produção: `conexao-municipio.meadadigital.com`.

(no WSL/Linux: `sudo nano /etc/hosts`.)

## Como rodar (Docker — caminho cravado, fase 0.5)

A stack inteira roda em containers (backend + frontend + sidecar de embeddings + Caddy como
proxy reverso na porta 80). O **banco continua no Supabase remoto** (paridade com prod, tenants
preservados). URLs **sem porta**.

```bash
./scripts/meada-up.sh     # para o Apache, sobe os containers, espera o backend
# … desenvolve (hot-reload de .java/.tsx via volume) …
./scripts/meada-down.sh   # derruba os containers
```

Acesse (sem porta — o Caddy resolve por subdomínio):
- `http://meada.meadadigital.local` (ou `http://meadadigital.local`) → **genérico** (login universal).
- `http://juridico.meadadigital.local` → **Legal** (login valida perfil `legal`).
- `http://dental.meadadigital.local` → **DentalBot** (perfil `dental`).
- `http://sushi.meadadigital.local` → **SushiBot** (perfil `sushi`).
- `http://mesa.meadadigital.local` → **MesaBot** (perfil `restaurant`).
- `http://salao.meadadigital.local` → **SalãoBot** (perfil `salon`).
- `http://pousada.meadadigital.local` → **PousadaBot** (perfil `pousada`).
- `http://academia.meadadigital.local` → **AcademiaBot** (perfil `academia`).
- `http://pet.meadadigital.local` → **PetBot** (perfil `pet`).
- `http://oficina.meadadigital.local` → **OficinaBot** (perfil `oficina`).
- `http://nutri.meadadigital.local` → **NutriBot** (perfil `nutri`).
- `http://barbearia.meadadigital.local` → **BarbeariaBot** (perfil `barbearia`).
- `http://eventos.meadadigital.local` → **EventosBot** (perfil `eventos`).
- `http://estetica.meadadigital.local` → **EsteticaBot** (perfil `estetica`).
- `http://comida.meadadigital.local` → **Comida** (perfil `comida`).
- `http://pizzaria.meadadigital.local` → **Pizzaria** (perfil `pizzaria`).
- `http://api.meadadigital.local` → **backend / API**.

O backend é o mesmo para todos os subdomínios — o perfil viaja no header `X-Meada-Subdomain`
(apiFetch) e é resolvido por `companies.profile_id`.

> **Modo legado (sem Docker, não-recomendado):** `./scripts/run-local.sh` (backend) +
> `cd frontend && npm run dev` (porta 3000). Esbarra no Apache na porta 80; serve só p/ debug
> pontual.

## Troubleshooting

- **403 em qualquer subdomínio?** Algo (provavelmente o Apache) ainda está na porta 80. Rode
  `./scripts/meada-down.sh`, confira `sudo ss -tlnp | grep ':80 '` (deve estar livre ou ser o
  Caddy), depois `./scripts/meada-up.sh`.
- **Subdomínio não resolve?** Falta a entrada em `/etc/hosts` (ver acima).
- **Backend não sobe?** `docker compose logs backend` — a 1ª subida compila o jar (~1-2min).

## Comportamento de match (login)

- **Subdomínio universal** (`meada`/`localhost`): qualquer usuário entra.
- **Subdomínio de produto** (`processo`/`dental`/`sushi`): após autenticar, o frontend chama
  `GET /admin/me/profile-match?subdomain=...`. Se o perfil da empresa do usuário **não** bate,
  faz `signOut` e mostra "Você é cliente do <Produto>. Acesse <subdomínio>.meadadigital.local".
- **Super-admin** sempre casa (acessa qualquer subdomínio).

## Tenants de teste (perfis)

| Email                | Senha            | Empresa                       | Perfil  |
|----------------------|------------------|-------------------------------|---------|
| `igorhaf3@gmail.com` | `bofo-meca-oleo` | Escritório Modelo Advocacia   | legal   |
| `igorhaf4@gmail.com` | `bofo-meca-oleo` | Clínica Modelo Odonto         | dental  |
| `igorhaf5@gmail.com` | `bofo-meca-oleo` | Sushi Modelo                  | sushi      |
| `igorhaf6@gmail.com` | `bofo-meca-oleo` | Restaurante Modelo            | restaurant |
| `igorhaf7@gmail.com` | `bofo-meca-oleo` | Salão Modelo                  | salon      |
| `igorhaf8@gmail.com` | `bofo-meca-oleo` | Pousada Modelo                | pousada    |
| `igorhaf9@gmail.com` | `bofo-meca-oleo` | Academia Modelo               | academia   |
| `igorhaf10@gmail.com`| `bofo-meca-oleo` | Pet Shop Modelo               | pet        |
| `igorhaf11@gmail.com`| `bofo-meca-oleo` | Oficina Modelo                | oficina    |
| `igorhaf12@gmail.com`| `bofo-meca-oleo` | Nutri Modelo                  | nutri      |
| `igorhaf14@gmail.com`| `bofo-meca-oleo` | Eventos Modelo                | eventos    |
| `igorhaf15@gmail.com`| `bofo-meca-oleo` | Estética Modelo               | estetica   |
| `igorhaf16@gmail.com`| `bofo-meca-oleo` | Comida Modelo                 | comida     |
| `igorhaf17@gmail.com`| _(comunicação direta)_ | Pizzaria Modelo         | pizzaria   |

(Seed em `/tmp/seed-multi-profile.sql` — não versionado; roda via psql. O seed do tenant
`pizzaria` é `/tmp/seed-pizzaria.sql`, também não versionado; senha só em comunicação direta.)

## Adicionar um perfil novo

1. Adicione a entrada no enum `ProfileType.java` **e** no const `profile-type.ts`.
2. Crie uma migration que altere a CHECK constraint de `companies.profile_id`.
3. Adicione a entrada de `/etc/hosts` do novo subdomínio.
4. `mvn -B test` (paridade Java↔TS) + `npm run build`.
