# 🌱 Muda — Marketplace de Brechó

Frente de loja (storefront) de um marketplace de brechó / moda circular, no estilo
Amazon / Submarino / Mercado Livre. Esta fase entrega **toda a lógica de frontend da
loja virtual**. A área administrativa e a arquitetura **multitenant (single DB)** ficam
para a próxima fase (ver [Próximas fases](#próximas-fases)).

## Stack

| Camada | Tecnologia |
|--------|-----------|
| Backend | **Laravel 13** (PHP 8.3) |
| Banco | **PostgreSQL 16** |
| Renderização | **SSR com Blade** (HTML server-side, ótimo para SEO) |
| Interatividade | **Vue 3** montado como _islands_ dentro do Blade |
| Estilo | **Tailwind CSS v4** |
| Build | **Vite 8** |
| Orquestração | **Docker Compose** (postgres + php-fpm + nginx) |

### Por que Blade (SSR) + Vue islands?

Um marketplace vive de SEO — as páginas de produto e categoria precisam ser indexáveis
e carregar rápido. Por isso o HTML é renderizado no servidor pelo **Blade** (SSR puro),
e o **Vue** entra apenas nas ilhas interativas (carrinho, galeria, busca com
autocomplete, carrossel). O melhor dos dois mundos: SEO + UX rica, sem SPA.

As ilhas são declaradas no Blade com `data-island="NomeDoComponente"` e `data-props`,
e montadas por [`resources/js/app.js`](resources/js/app.js).

## Como rodar

Pré-requisitos: Docker + Docker Compose.

```bash
# 1. Configurar o ambiente
cp .env.example .env
docker compose run --rm app php artisan key:generate

# 2. Compilar os assets (Vue + Tailwind)
npm install
npm run build

# 3. Subir a stack
docker compose up -d --build

# 4. Migrar e popular com dados de exemplo
docker compose exec app php artisan migrate:fresh --seed
```

Acesse **http://localhost:8095**.

| Serviço | Porta host |
|---------|-----------|
| Loja (nginx) | http://localhost:8095 |
| PostgreSQL | localhost:5440 |
| Vite dev (opcional) | `docker compose --profile dev up node` → :5173 |

> Durante o desenvolvimento do frontend, rode `npm run dev` (ou o serviço `node`) para
> hot-reload; sem ele, o Blade usa os assets compilados em `public/build`.

### Contas de demonstração (senha: `password`)

| Papel | E-mail | Acesso |
|-------|--------|--------|
| **root** | `root@muda.com.br` | Painel do lojista **e** administração do site |
| **lojista** | `brecho-da-lu@muda.com.br` | Painel do lojista (`/painel`) |
| **cliente** | `cliente@muda.com.br` | Meus pedidos, virar lojista |

Há 8 lojistas semeados (`{slug-da-loja}@muda.com.br`).

## Estrutura do domínio

```
Category (árvore: raiz → subcategorias)
 └─ Product (condição, marca, tamanho, preço, de/por, parcelas, frete, rating…)
     └─ ProductImage (galeria)
Banner       (hero + tiles promocionais da home)
SiteSetting  (linha única: nome, tagline, redes sociais, anúncio — geridos pelo root)
```

Dados de exemplo: **8 categorias-raiz**, ~28 subcategorias, **~140 produtos** de brechó
com marcas, condições (novo/seminovo/usado), tamanhos e descontos realistas.

## Funcionalidades da loja

- **Home**: carrossel hero, atalhos por categoria, "Ofertas do dia", "Novidades",
  "Mais vendidos", vitrines por categoria e tiles promocionais.
- **Listagem** (categoria e busca): filtros por condição, marca, tamanho, faixa de
  preço e frete grátis (com **facets** e contadores), ordenação e paginação — tudo
  via URL (SSR, compartilhável, SEO-friendly).
- **Produto**: galeria com zoom, preço "de/por", parcelamento, estado da peça,
  ficha técnica, vendedor e "você também pode gostar".
- **Busca** com autocomplete (endpoint JSON `GET /api/busca/sugestoes`).
- **Carrinho**: 100% client-side (Vue + `localStorage`), drawer lateral, badge no
  header, página de carrinho com resumo, frete e checkout de demonstração.

### Ilhas Vue (`resources/js/components/`)

`HeroCarousel` · `SearchBar` · `AddToCart` · `CartButton` · `CartDrawer` ·
`ProductGallery` · `CartPage`. O estado do carrinho é um store reativo compartilhado
([`stores/cart.js`](resources/js/stores/cart.js)) persistido em `localStorage` e
sincronizado entre abas.

### Imagens

O lojista faz **upload de fotos do próprio computador** no cadastro do produto — os
arquivos são salvos no disco `public` do Laravel (`storage/app/public/products`) e
servidos via `/storage` (rode `php artisan storage:link`). Quando nenhuma imagem é
enviada, geramos um **placeholder SVG** determinístico pela rota `GET /ph`, mantendo
o projeto autossuficiente para o seed de demonstração.

### Feature flag — marketplace (`MUDA_SELLING_ENABLED`)

Liga/desliga os pontos de entrada de venda por lojistas (links "Vender no Muda" no
header/footer, opção no cadastro e acesso ao painel do lojista / onboarding). Está em
`.env` (padrão `false`) e em [`config/muda.php`](config/muda.php). A funcionalidade
continua no código — apenas fica oculta ao público. **O usuário root sempre mantém
acesso**, para seguir construindo o recurso.

### Páginas institucionais

Central de ajuda, Trocas e devoluções e Privacidade são **páginas editáveis** (tabela
`pages`, editadas em `/admin/paginas`). A página de **Contato** (`/contato`) grava as
mensagens em `contact_messages`, visíveis em `/admin/mensagens`.

## Contas, multitenant e painéis (Fase 2 — concluída)

- **Autenticação** própria (login, cadastro, logout) com _rate limiting_ e papéis
  `customer` / `seller` / `root` (middleware `role:` + `ProductPolicy`).
- **Multitenant single-DB (row-level)**: cada produto pertence a um `seller_id`; o
  lojista só enxerga/edita o próprio catálogo (`Product::forSeller()` + policy). Um
  cliente pode **abrir a loja** (`/vender`) e virar tenant.
- **Painel do lojista** (`/painel`): visão geral (faturamento, estoque baixo, vendas),
  **CRUD de produtos** escopado ao dono, **minhas vendas** e **perfil da loja**
  (vitrine pública em `/loja/{slug}`).
- **Painel do root** (`/admin`): visão geral do marketplace, **configurações do site**
  (identidade, aviso do topo, redes sociais, contato → `SiteSetting`), **CRUD de
  banners** e gestão de **destaques**. O root também é lojista.
- **Pedidos reais**: o checkout (`POST /checkout`) persiste `Order` + `OrderItem` com
  preços **recalculados no servidor** (nunca confiando no cliente), alimentando
  *Meus pedidos* (cliente) e *Minhas vendas* (lojista).

## Próximas fases

1. **Pagamentos** de verdade (o checkout hoje é simulado) e gestão de status do pedido
   pelo lojista (enviado/entregue).
2. **Upload de imagens** (hoje o cadastro aceita URLs / gera placeholder).
3. **Avaliações, favoritos** e cupons.
4. Verificação de e-mail e recuperação de senha.
