# Meada — Wiki do Projeto

> Documentação consolidada do projeto **Meada**: SaaS multi-tenant de atendimento ao cliente
> via WhatsApp com IA. Cada empresa (tenant) tem um atendente de IA treinado com os próprios
> dados (serviços, horários, FAQs, preços), respondendo clientes pelo WhatsApp, com isolamento
> total por tenant via RLS.
>
> Esta wiki cobre o projeto **do começo até o estado atual**: regras de negócio, referências
> técnicas, todos os nichos verticais e o estado de cada subsistema. É a fonte de consulta
> "tipo Wikipédia" do projeto.

## Índice

| Página | Conteúdo |
|--------|----------|
| [01 — Arquitetura e Stack](01-arquitetura.md) | Stack (Spring Boot + JdbcTemplate, Supabase, Gemini, Evolution, Next 16), módulos top-level, convenções de banco, decisões cravadas. |
| [02 — Auth e Multi-tenancy](02-auth-multitenancy.md) | Super-admin × tenant-admin, JWT ES256/JWKS, RLS (`app.company_id()`), FKs compostas, suspensão/LGPD, convites. |
| [03 — IA e Fluxo de Mensagens](03-ia-fluxo.md) | Gemini, PromptBuilder, system-template, RAG/embeddings, webhook inbound, OutboundService, cadeia de handlers de tag, ciclo end-to-end. |
| [04 — Multi-perfil e Chassis](04-multiperfil-chassis.md) | Perfis hardcoded (`ProfileType`), guard por perfil, context cache, e os **9 chassis transversais** que todos os nichos reusam. |
| [05 — Catálogo dos Nichos](05-nichos.md) | Os 34 nichos verticais: regra de negócio, escapada, tag de IA, trava, tabelas e máquina de status de cada um. |
| [06 — Pagamentos](06-pagamentos.md) | Estado atual (registro **manual** de mensalidade) + a pendência do **gateway integrado (#50)**. **Onde paramos.** |
| [07 — Núcleo de Plataforma](07-plataforma.md) | CMS/site por tenant, feature flags por nicho, LGPD, teams, métricas, saved replies, engagement, webchat. |
| [API — Swagger/OpenAPI](../api/openapi.yaml) | Especificação OpenAPI 3.0 de toda a API HTTP (~703 endpoints / 150 controllers). |

## O projeto em uma frase

Um **monolito multi-tenant** que se apresenta como **N produtos verticais** ("perfis"): o mesmo
core (mensageria + IA + outbound) veste-se de Sushi, Dental, Academia, Pousada, etc. — cada perfil
parece um produto distinto para o cliente final (subdomínio, nome, tom de IA, features próprias).

## Números do estado atual

- **34 nichos verticais** no enum `ProfileType` (+ `generic` = produto base do admin).
- **70 migrations** SQL (`supabase/migrations/`).
- **~150 controllers** / **~703 endpoints** HTTP.
- **9 chassis** de negócio reusados pelos nichos (order-based, agenda, assinatura, proposta+aprovação, varejo com variantes, etc.).
- **Pagamento:** registro manual de mensalidade em 3 nichos (academia/escola/cursos). **Gateway integrado (Stripe/Pix): NÃO implementado** — pendência #50. Ver [06 — Pagamentos](06-pagamentos.md).

## Como esta wiki foi montada

Levantamento empírico do código real (`src/main/java/com/meada/`), das migrations
(`supabase/migrations/`), do `CLAUDE.md`, dos guias `docs/PERFIL_*.md` e dos prompts de nicho
(`docs/PROMPT_NICHO_*.md` + `docs/prompts-nicho/`). Onde a doc histórica divergia do código, **o
código real prevaleceu** (ex.: `end_at` do restaurant é materializado no INSERT, não "coluna
gerada" como dizia um comentário antigo de migration).

## Convenção de leitura

- Caminhos de arquivo são relativos à raiz do repo (`/home/igorhaf/meada/`).
- "Tenant" = empresa cliente. "Contato" = cliente final que fala via WhatsApp. "Root/super-admin" = operador da plataforma Meada.
- "Escapada estrutural" = a característica que diferencia um nicho do chassi que ele clona.
- "Tag" = marcação em texto livre que a IA emite (`<pedido>`, `<consulta_nutri>`, ...) e o backend parseia/remove antes de enviar ao cliente.
