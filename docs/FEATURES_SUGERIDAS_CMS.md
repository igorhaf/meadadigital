# Features Sugeridas — CMS (blocos genéricos de site)

> Backlog de **componentes/blocos para o editor de CMS** (camada 9.x), priorizado por VALOR DE
> NEGÓCIO. Diferente dos demais `FEATURES_SUGERIDAS_*` (que são por nicho), este catálogo é
> TRANSVERSAL: cada feature é classificada por **escopo** (global = todo nicho com CMS ligado /
> grupo de nichos / nicho específico) e por **natureza** (estático = tenant digita/cola conteúdo /
> integração interna = puxa dado real do Meada / integração externa = API de terceiro).
> Baseado no estado REAL do CMS (o que já existe NÃO é repetido aqui).

> **Modo de execução (cravado):** ao implementar qualquer feature deste backlog, faça **tudo em prosa**,
> de forma contínua e autônoma, **sem perguntar nada ao programador**, sem pausas para confirmação e
> **sem usar o widget de perguntas** (AskUserQuestion). Não interrompa o fluxo pedindo aval intermediário:
> decida com base no estado real do código/banco e nas convenções das skills, implemente, e só pare em
> ponto de bifurcação arquitetural genuína ou no gate de teste. Reporte o progresso em prosa corrida.

## O que o CMS já tem (baseline)

- **25 tipos de bloco** hardcoded (`CmsBlockType.java` ↔ `cms-block-type.ts`, com
  `CmsBlockTypeParityTest`): 18 genéricos — `hero`, `text`, `services`, `contact`, `gallery`,
  `faq`, `testimonials`, `map`, `banner_strip`, `stats`, `feature_grid`, `image_text_split`,
  `steps`, `columns`, `packages`, `marquee`, `quote`, `cta` — e 7 de marca (`meada_*`,
  `niches_grid`).
- **Árvore rows→columns→blocos** em `cms_pages.blocks` (jsonb, validação app-level em
  `CmsService.normalizeLeaf`; props livres — **estender props de bloco existente = zero backend**).
- **Receita de bloco novo = 5 arquivos** (enum Java + `cms-block-type.ts` + `cms-block-schemas.ts`
  + `cms-render.tsx` com componente + registro no map/switch), sem migration. Paleta do editor é
  data-driven (`allBlockSchemas()`) — bloco novo aparece sozinho.
- **Primitivos reutilizáveis:** `safeFrameSrc` (`frontend/lib/cms/safe-url.ts`, embed https-only,
  já usado pelo `map`); `widget.js` (`frontend/public/widget.js`, chat embutível do webchat);
  `BusinessHours*` no core (`messaging/`); slots de edição (`cms-block-slots.ts`, opcionais).
- **Gate:** o CMS inteiro é uma `ProfileFeature` (`cms`) ligada por nicho pelo root. **NÃO existe
  gating por-bloco** — nicho com CMS ligado enxerga todos os blocos da paleta (ver feature #20).
- **NÃO-TEM (bloqueadores conhecidos):** upload de imagem (SERVICE_ROLE_KEY — tudo é URL colada),
  WYSIWYG, versionamento draft/published, pagamento real (backlog #50).

## 🏆 Top 3 quick wins (fazer primeiro)

**1. Carrossel de avaliações (`reviews_carousel`) — versão estática.** Prova social é o
componente que mais converte em site de serviço local, e hoje o `testimonials` é uma lista
estática sem cara de "avaliação real". Um bloco novo com visual de review do Google (estrelas,
nome, avatar por inicial, data, selo "via Google"), rolando em carrossel com autoplay, onde o
tenant cola as avaliações que já tem. Esforço P (bloco puramente frontend + enum), zero
dependência externa, e prepara o terreno para a versão viva via Places API (#17) — o mesmo bloco
ganha depois um modo `source: google` sem quebrar os sites existentes.

**2. Funil WhatsApp no site: botão flutuante (`whatsapp_float`) + atendente IA embutido
(`webchat_embed`).** O produto do Meada É o atendimento por WhatsApp com IA — mas o site CMS de
tenant hoje não puxa o visitante pra dentro do funil. Dois tiros no mesmo alvo: (a) botão
flutuante de WhatsApp (deep link `wa.me` com mensagem pré-preenchida) como configuração do site
(theme), presente em toda página; (b) embutir o `widget.js` já existente nas páginas `/p/` —
o visitante conversa com o MESMO atendente IA sem sair do site. É a feature mais Meada-nativa
de todas: transforma o site vitrine em canal de captação real. Esforço P (float) + M (widget).

**3. Horário de funcionamento vivo (`opening_hours`).** Primeira ponte dado-core→site: um bloco
que renderiza os horários já configurados em `BusinessHours` do tenant, com indicador
"Aberto agora" / "Fecha às 18h" calculado no fuso America/Sao_Paulo. Todo nicho local precisa
disso na página, ninguém quer manter dois cadastros. Esforço M (endpoint público read-only dos
horários + bloco), e inaugura o padrão de bloco-com-dado-interno que os blocos de grupo (#13,
#14, #15) vão reusar.

## Backlog priorizado (20 features)

| # | Feature (bloco/infra) | Escopo | Natureza | Valor | Esforço | O que resolve pro tenant |
|---|-----------------------|--------|----------|-------|---------|--------------------------|
| 1 | `reviews_carousel` — carrossel de avaliações estilo Google (estático) | Global | Estático | Alto | P | Prova social que converte; visual de review real, não de "depoimento de site" |
| 2 | `whatsapp_float` — botão flutuante de WhatsApp com mensagem pré-preenchida (config do site) | Global | Interna | Alto | P | Todo visitante do site vira conversa no WhatsApp — o funil do produto |
| 3 | `webchat_embed` — atendente IA embutido nas páginas `/p/` (reusa `widget.js`) | Global | Interna | Alto | M | Visitante fala com a MESMA IA do WhatsApp sem sair do site |
| 4 | `opening_hours` — horário de funcionamento + "aberto agora" (puxa `BusinessHours`) | Global | Interna | Alto | M | Um cadastro só; a pergunta nº 1 do cliente local respondida na página |
| 5 | `video` — vídeo embutido YouTube/Vimeo (via `safeFrameSrc`) | Global | Estático | Alto | P | Vídeo institucional/tour/demo sem gambiarra de iframe colado |
| 6 | `before_after` — comparador antes/depois com slider | Grupo (estética, barbearia, salon, dermatologia, atelie, oficina, moda) | Estático | Alto | M | O argumento de venda visual dos nichos de transformação |
| 7 | `lead_form` — formulário público que cria contato + conversa no inbox | Global | Interna | Alto | M/G | Captação fora do WhatsApp cai direto no atendimento; nada de e-mail perdido |
| 8 | `team` — equipe/profissionais (foto, nome, especialidade) | Global (mais forte no chassi A) | Estático | Médio | P | Rosto humano no site; cliente escolhe com quem quer ser atendido |
| 9 | `rating_badge` — selo compacto de nota agregada ("4,9 ★ · 320 avaliações no Google") | Global | Estático | Médio | P | Trust badge pro hero/rodapé; par do #1 |
| 10 | `logo_strip` — faixa de logos (parceiros, certificações, selos, bandeiras de pagamento) | Global | Estático | Médio | P | Credibilidade institucional barata |
| 11 | `countdown` — banner de promoção com contagem regressiva | Global | Estático | Médio | P | Urgência para campanha/data (Black Friday, matrícula, feriado) |
| 12 | `instagram_grid` — grade de fotos estática + link do perfil (sem API) | Global | Estático | Médio | P | Presença visual do IG no site sem depender de API do Meta |
| 13 | `services_live` — tabela de serviços/preços puxada do catálogo real do nicho | Grupo (chassi A: salon, barbearia, estetica, dermatologia, fotografia…) | Interna | Alto | M | Preço no site sempre igual ao do atendimento; um cadastro só |
| 14 | `catalog_showcase` — vitrine do catálogo/cardápio real com CTA pro WhatsApp | Grupo (chassi B/C: sushi, pizzaria, comida, padaria, adega, lingerie, las…) | Interna | Alto | G | Cardápio/vitrine vivos; produto novo aparece no site sozinho |
| 15 | `plans_showcase` — planos/turmas/cursos reais com vagas | Grupo (chassi E: academia, escola, cursos) | Interna | Médio | M | Página de matrícula sempre atual, com CTA pro WhatsApp |
| 16 | `portfolio` — portfólio de trabalhos com categorias/filtro | Grupo (chassi D + fotografia, atelie, eventos, casamento) | Estático | Médio | P | Vitrine de trabalho autoral organizada (gallery é crua demais) |
| 17 | `reviews_carousel` modo vivo — avaliações reais via Google Places API | Global | Externa | Alto | G | Reviews sempre atuais sem colar nada (chave de API + cache + job) |
| 18 | `vehicle_showcase` — vitrine do estoque da concessionária (ciclo `disponivel`) | Nicho (concessionaria) | Interna | Alto | M | Estoque vivo no site; vendido some sozinho da vitrine |
| 19 | `room_showcase` — quartos/acomodações da pousada com faixa de preço | Nicho (pousada) | Estático→Interna | Médio | M | Página de acomodações padrão do setor, com CTA de reserva |
| 20 | **Infra: gating de bloco por nicho** (`scopes` no BlockSchema + filtro da paleta) | Infra transversal | — | Médio | P/M | Torna o "parcialmente global" regra de sistema, não convenção |

## Detalhamento das prioritárias

### 1. `reviews_carousel` — carrossel de avaliações (estático primeiro, vivo depois)

- **Problema de negócio:** prova social é o maior conversor de site local, e o bloco
  `testimonials` atual parece depoimento genérico de landing page — não tem a "assinatura visual"
  de avaliação do Google que o visitante reconhece e confia.
- **Como funciona (fase estática):** bloco novo com props `title`, `source`
  (`google|manual`), `autoplay`, `reviews[]` (repeater: `name`, `rating` 1–5, `text`, `date`,
  `avatarUrl?`). Renderiza cards no estilo review do Google (estrelas amarelas, avatar por
  inicial colorida quando sem foto, selo "via Google" quando `source=google`), em carrossel com
  autoplay pausável e setas — carrossel em CSS scroll-snap + um pouco de estado client-side
  (o `cms-render.tsx` é client-safe, então pode ter interatividade).
- **Fase viva (#17):** o mesmo bloco ganha `placeId` e passa a hidratar de um endpoint público
  cacheado (`GET /public/cms/reviews/{companyId}`) alimentado por job que consulta a Places API
  (chave do TENANT ou da plataforma, decidir na hora; cache generoso — a API do Google devolve
  só ~5 reviews e tem cota). Sites com reviews coladas continuam funcionando — o modo vivo é
  aditivo.
- **Dependências:** nenhuma na fase estática. Fase viva: chave Google, cache backend, job.
- **Métrica de sucesso:** % de sites publicados usando o bloco; conversão (clique no CTA) em
  páginas com vs. sem o bloco.

### 2. `whatsapp_float` + 3. `webchat_embed` — o funil no site

- **Problema de negócio:** o site CMS hoje é vitrine morta — não existe caminho de 1 clique do
  visitante para o atendente IA, que é exatamente o produto vendido pelo Meada.
- **Como funciona:** (a) `whatsapp_float` como **configuração do site** (campos novos no
  `cms_sites.theme` jsonb — zero migration): número, mensagem pré-preenchida, posição. O layout
  `/p/` renderiza o botão flutuante em toda página quando configurado. (b) `webchat_embed` como
  toggle do site que injeta o `widget.js` existente nas páginas públicas, apontando para o
  webchat do tenant — o visitante conversa com a mesma IA (mesma persona, mesmo contexto de
  nicho) sem sair do site. Os dois coexistem; o tenant escolhe um, outro ou ambos.
- **Por que theme e não bloco:** elemento flutuante é da PÁGINA INTEIRA, não de uma seção — pôr
  na árvore de blocos geraria duplicata e posição errada. Editor ganha os campos na aba de tema.
- **Dependências:** nenhuma dura; o `widget.js` e o webchat já existem (camada 5.25).
- **Métrica de sucesso:** conversas iniciadas com origem "site" por tenant.

### 4. `opening_hours` — horário vivo (inaugura bloco-com-dado-interno)

- **Problema de negócio:** horário é a informação mais buscada de negócio local; mantê-la em
  dois lugares (config do atendimento + texto no site) garante que uma delas está errada.
- **Como funciona:** endpoint público read-only (`GET /public/cms/business-hours/{companyId}`,
  cacheado) expondo os horários já cadastrados em `BusinessHours`; o bloco renderiza a semana
  com destaque do dia atual e badge "Aberto agora" / "Fecha às 18h" / "Fechado — abre seg 9h",
  calculado em America/Sao_Paulo. Fallback: se o tenant não configurou horários, o bloco aceita
  linhas manuais (props), para não renderizar vazio.
- **Padrão inaugurado:** bloco cujo dado vem de endpoint público cacheado do próprio Meada — é
  o mesmo esqueleto de `services_live` (#13), `catalog_showcase` (#14), `plans_showcase` (#15)
  e `vehicle_showcase` (#18). Fazer este primeiro e documentar o chassi.
- **Dependências:** nenhuma. **Métrica:** adoção do bloco; queda de perguntas de horário no
  WhatsApp (visível nas conversas).

### 6. `before_after` — antes/depois com slider

- **Problema de negócio:** nos nichos de transformação (estética, dermatologia, barbearia,
  salão, ateliê, oficina, moda), o antes/depois É o argumento de venda — hoje o tenant improvisa
  com duas fotos soltas na `gallery`, sem impacto.
- **Como funciona:** repeater de pares (`beforeUrl`, `afterUrl`, `caption`), renderizado como
  comparador com alça deslizante (clip-path + pointer events, client-side) e carrossel entre
  pares. URLs coladas (limitação de upload vale aqui como em todo o CMS).
- **Escopo:** primeiro caso de bloco "de grupo" — nasce global (nada impede uma padaria de usar),
  mas quando a infra #20 existir, é marcado com `scopes` dos nichos de transformação para não
  poluir a paleta dos demais.
- **Dependências:** nenhuma. **Métrica:** adoção nos nichos-alvo.

### 7. `lead_form` — formulário que cai no inbox

- **Problema de negócio:** visitante fora do horário (ou que não quer abrir WhatsApp) hoje não
  deixa rastro. Formulário que dispara e-mail seria corpo estranho — no Meada, lead tem que
  virar CONVERSA.
- **Como funciona:** bloco com campos configuráveis (nome, telefone obrigatório, mensagem) que
  posta num endpoint público novo (`POST /public/cms/leads/{companyId}`) com rate-limit e
  honeypot anti-spam. O backend cria/acha o `Contact` pelo telefone e abre `Conversation` com a
  mensagem como primeiro turno, marcada com origem "site" — cai no painel como qualquer conversa.
  Decisão de rota: a IA responde na hora via WhatsApp (se o telefone confere) ou fica aguardando
  humano — começar SEM disparo automático (respeitar a lição do incidente de re-sync: outbound
  não solicitado é zona de risco) e evoluir depois.
- **Dependências:** endpoint público novo com proteção; nenhuma externa.
- **Métrica:** leads/mês por tenant; % de leads respondidos.

### 13/14/15. Blocos de grupo com dado real (`services_live`, `catalog_showcase`, `plans_showcase`)

- **Problema de negócio:** o tenant já cadastra serviços/produtos/planos no painel para a IA
  atender — recadastrar tudo no site (blocos `services`/`packages` estáticos) é retrabalho e
  dessincroniza preço na primeira alteração.
- **Como funciona:** mesmo chassi do #4 — endpoint público read-only cacheado + bloco que
  renderiza. A diferença é que o dado é POR NICHO (tabelas diferentes por perfil), então cada
  bloco precisa de um resolver por `profile_id` no backend (switch explícito por perfil, como
  manda a regra de coexistência harmônica — NUNCA generalizar à força as tabelas). Ordem
  recomendada: `services_live` (chassi A é o mais uniforme) → `plans_showcase` (E, três nichos)
  → `catalog_showcase` (B/C, mais variantes: modifiers, grade de variantes, made_to_order — por
  isso G). Todo card termina em CTA "Pedir/Agendar pelo WhatsApp" (deep link) — o site alimenta
  o funil, nunca fecha pedido sozinho (isso continua sendo trabalho da IA na conversa).
- **Dependências:** #4 fechado (chassi); infra #20 ajuda a paleta a mostrar cada bloco só no
  grupo certo. **Métrica:** % de blocos estáticos substituídos; cliques no CTA.

### 20. Infra — gating de bloco por nicho (o "parcialmente global" de verdade)

- **Problema:** hoje a paleta é tudo-ou-nada — nicho com CMS vê os 25 blocos. Com blocos de
  grupo (#6, #13–#15) e de nicho (#18, #19) entrando, a paleta da padaria não pode oferecer
  `vehicle_showcase`.
- **Como funciona (leve, app-level):** campo opcional `scopes?: ProfileTypeId[]` no
  `BlockSchema` (`cms-block-schemas.ts`); ausência = global (todos os blocos atuais ficam como
  estão). O editor filtra `allBlockSchemas()` pelo perfil do tenant logado (o `/admin/me` já
  entrega o perfil). Backend: `CmsService.normalizeLeaf` ganha a MESMA checagem (bloco fora de
  escopo → 400 `invalid_blocks`) com um espelho Java do mapa de escopos — coberto por um parity
  test novo no molde dos existentes, para os dois lados nunca divergirem. SEM tabela nova, SEM
  feature flag por bloco (se um dia o root precisar ligar/desligar bloco por nicho em runtime,
  aí sim vira `profile_features`-like — não agora).
- **Quando fazer:** junto do primeiro bloco de escopo restrito que for implementado (#6 ou #13).

## Ordem de ondas sugerida

1. **Onda 1 (estáticos globais, esforço P):** #1 `reviews_carousel` + #5 `video` + #9
   `rating_badge` + #10 `logo_strip` — quatro blocos frontend-only + enum, um gate só.
2. **Onda 2 (funil):** #2 `whatsapp_float` + #3 `webchat_embed` — o site passa a captar.
3. **Onda 3 (primeiro dado interno):** #4 `opening_hours` — inaugura o chassi público cacheado.
4. **Onda 4 (grupo + gating):** #20 infra de escopo + #6 `before_after` + #8 `team` + #16
   `portfolio`.
5. **Onda 5 (dado interno por grupo):** #13 `services_live` → #15 `plans_showcase` → #14
   `catalog_showcase`; nicho específico (#18, #19) entra a reboque do chassi.
6. **Backlog frio:** #17 (Places API — decidir modelo de chave), #12/#11/#7 conforme demanda.
