# Meada — Wiki do Projeto

> Documentação consolidada do projeto **Meada**: SaaS multi-tenant de atendimento ao cliente
> via WhatsApp com IA. Cada empresa (tenant) tem um atendente de IA treinado com os próprios
> dados (serviços, horários, FAQs, preços), respondendo clientes pelo WhatsApp, com isolamento
> total por tenant via RLS.
>
> Esta wiki cobre o projeto **do começo até o estado atual**: regras de negócio, referências
> técnicas, todos os nichos verticais e o estado de cada subsistema. É a fonte de consulta
> "tipo Wikipédia" do projeto. Última revisão completa contra o código: **2026-08-12**.

## Como navegar

- **No browser (recomendado):** `./scripts/wiki-serve.sh` na raiz do repo →
  `http://localhost:8098` — wiki navegável com busca (Docsify vendorizado em `assets/`,
  funciona offline).
- **No GitHub/editor:** os arquivos são markdown puro; comece por este índice.

## Índice

| Página | Conteúdo |
|--------|----------|
| [01 — Arquitetura e Stack](01-arquitetura.md) | Stack (Spring Boot + JdbcTemplate, Supabase, Gemini, Evolution, Next 16), módulos top-level, convenções de banco, decisões cravadas. |
| [02 — Auth e Multi-tenancy](02-auth-multitenancy.md) | Super-admin × tenant-admin, JWT ES256/JWKS, RLS (`app.company_id()`), FKs compostas, suspensão/LGPD, convites, feature flags. |
| [03 — IA e Fluxo de Mensagens](03-ia-fluxo.md) | Gemini, PromptBuilder, system-template, RAG/embeddings, webhook inbound, OutboundService, cadeia de handlers de tag, ciclo end-to-end. |
| [04 — Multi-perfil e Chassis](04-multiperfil-chassis.md) | Perfis hardcoded (`ProfileType`), guard por perfil, context cache, os **9 chassis transversais** e as regras de plataforma que valem para todo nicho. |
| [05 — Catálogo dos Nichos](05-nichos.md) | Tabela-mestra dos 33 nichos + link para a **página de regras de negócio de cada um** (`nichos/*.md`). |
| [06 — Pagamentos](06-pagamentos.md) | Registro manual (mensalidade + sinal com gate `deposit_required`), réguas de inadimplência, e o schema da assinatura Mercado Pago (migration 118) ainda sem código. |
| [07 — Núcleo de Plataforma](07-plataforma.md) | CMS/site por tenant, feature flags, vitrine de nichos, LGPD, teams, métricas, saved replies, engagement, webchat, busca, treino da IA, logs de acesso. |
| [Nichos — regras de negócio](05-nichos.md) | 33 páginas individuais em `nichos/<profile_id>.md`: jornada no WhatsApp, invariantes transacionais, máquina de status, travas da IA, tags, erros, notificações, dados e limites de cada nicho. |
| [API — Swagger/OpenAPI](../api/openapi.yaml) | Especificação OpenAPI 3.0 da API HTTP. |

## O projeto em uma frase

Um **monolito multi-tenant** que se apresenta como **N produtos verticais** ("perfis"): o mesmo
core (mensageria + IA + outbound) veste-se de Sushi, Dental, Academia, Pousada, etc. — cada perfil
parece um produto distinto para o cliente final (subdomínio, nome, tom de IA, features próprias).

## Números do estado atual

- **33 nichos verticais** no enum `ProfileType` (+ `generic` = produto base do admin).
- **117 migrations** SQL (`supabase/migrations/`, numeradas até 118 — o slot 57 ficou vago).
- **206 controllers** HTTP no backend.
- **9 chassis** de negócio reusados pelos nichos (order-based, agenda, assinatura, proposta+aprovação, varejo com variantes, etc.).
- **Pagamento:** registro manual (mensalidade em academia/escola/cursos; sinal com gate em casamento/atelie/viagens/papelaria/padaria). **Gateway integrado: NÃO implementado** — a migration 118 traz o schema da assinatura via Mercado Pago Preapproval, sem código ainda (pendência #50). Ver [06 — Pagamentos](06-pagamentos.md).

## Como esta wiki foi montada

Levantamento empírico do código real (`src/main/java/com/meada/`), das migrations
(`supabase/migrations/`), do `CLAUDE.md`, dos guias `docs/PERFIL_*.md` e dos prompts de nicho
(`docs/PROMPT_NICHO_*.md` + `docs/prompts-nicho/`). Onde a doc histórica divergia do código, **o
código real prevaleceu** (ex.: `end_at` do restaurant é materializado no INSERT, não "coluna
gerada" como dizia um comentário antigo de migration; o nicho `projetos` do catálogo antigo nunca
entrou no código e foi removido do índice).

## Convenção de leitura

- Caminhos de arquivo são relativos à raiz do repo (`/home/meada/meadadigital/`).
- "Tenant" = empresa cliente. "Contato" = cliente final que fala via WhatsApp. "Root/super-admin" = operador da plataforma Meada.
- "Escapada estrutural" = a característica que diferencia um nicho do chassi que ele clona.
- "Tag" = marcação em texto livre que a IA emite (`<pedido>`, `<consulta_nutri>`, ...) e o backend parseia/remove antes de enviar ao cliente.
