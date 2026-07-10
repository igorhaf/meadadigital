# Conexão Município

Painel administrativo de denúncias municipais.

- **Backend:** Laravel 13 — API RESTful (sem autenticação, por enquanto)
- **Frontend:** Next.js 16 (App Router) + Tailwind CSS v4
- **Mobile:** Expo / React Native (tela de login; credenciais fixas `admin` / `admin`)
- **Banco:** PostgreSQL 16
- **Orquestração:** Docker Compose (backend, frontend e banco; o mobile roda via Expo)

## Subir o projeto

```bash
docker compose up -d --build
```

Na primeira subida o backend roda `composer install`, as migrations e o seed
(48 denúncias: 12 pendentes, 18 em andamento, 18 resolvidas), e o frontend roda
`npm install`. Aguarde ~1–2 minutos.

| Serviço | URL / Porta |
|---------|-------------|
| Frontend (dashboard) | http://localhost:3015 |
| API (Laravel) | http://localhost:8015/api |
| PostgreSQL | localhost:5433 (user `conexao` / pass `conexao_secret` / db `conexao_municipio`) |

## Endpoints da API

| Método | Rota | Descrição |
|--------|------|-----------|
| GET | `/api/dashboard` | Totais por status + 5 denúncias mais recentes |
| GET | `/api/denuncias` | Lista paginada (`?status=pendente&per_page=15&page=1`) |
| POST | `/api/denuncias` | Cria denúncia (`titulo`, `endereco`, `data` obrigatórios; `descricao`, `status` opcionais) |
| GET | `/api/denuncias/{id}` | Detalhe |
| PUT/PATCH | `/api/denuncias/{id}` | Atualiza (parcial permitido) |
| DELETE | `/api/denuncias/{id}` | Remove (204) |
| GET | `/api/noticias` | Notícias da cidade (`?limit=10`) |
| GET | `/api/agendamentos/horarios` | Grade de horários (`?servico=saude&data=YYYY-MM-DD`) |
| POST | `/api/agendamentos` | Agenda um horário (`servico`, `data`, `horario`) |
| GET/DELETE | `/api/agendamentos[/{id}]` | Lista / cancela agendamentos |

Status possíveis: `pendente`, `em_andamento`, `resolvido`, `arquivado`.
Denúncias têm `categoria` (`buracos`, `iluminacao`, `lixo`, `esgoto`,
`transito`, `vandalismo`, `outros`) e `origem` (`web` = painel admin,
`mobile` = app do cidadão). Serviços agendáveis: `saude`, `documentos`,
`cras`, `licencas`.

## Estrutura

```
├── docker-compose.yml
├── backend/    # Laravel (porta interna 8000 → host 8015)
│   ├── app/Http/Controllers/Api/   # DenunciaController, DashboardController
│   ├── app/Models/Denuncia.php
│   ├── database/migrations/        # create_denuncias_table
│   ├── database/seeders/           # DenunciaSeeder (dados da tela)
│   └── routes/api.php
└── frontend/   # Next.js (porta interna 3000 → host 3015)
    ├── app/page.tsx                # Dashboard (server component)
    ├── components/                 # Sidebar, StatCard, StatusBadge, icons
    └── lib/api.ts                  # Client da API + tipos
```

O código-fonte é montado como volume nos containers (`vendor/` e
`node_modules/` ficam em volumes nomeados), então alterações locais refletem
sem rebuild. O seed é idempotente — restarts não duplicam dados.

## Mobile (Expo / React Native)

```bash
cd mobile
npm install
npx expo start          # use --tunnel se o celular não achar o Metro no WSL2
```

Abra no app **Expo Go** (Android/iOS) escaneando o QR code, ou `npm run web`
para visualizar no navegador. Login com credenciais fixas no código
([mobile/constants/auth.ts](mobile/constants/auth.ts)): usuário `admin`,
senha `admin`.

Telas do app do cidadão: **Home** (notícias da cidade + atalhos), **Denúncias
Urbanas** (categorias, suas denúncias e nova denúncia — vira `origem=mobile`
na API e aparece no painel do administrador) e **Agendar Serviços**
(calendário + horários disponíveis em tempo real). A URL da API é deduzida
automaticamente do host do Metro (funciona no celular físico na mesma rede);
sobrescreva com `EXPO_PUBLIC_API_URL` se necessário.
