-- supabase/seed.sql — roda AUTOMATICAMENTE no fim de cada `supabase db reset` (dev local).
-- Torna o ambiente local reproduzível: usuários logáveis + 1 tenant de exemplo (Comida Modelo).
-- NÃO roda em prod (prod nunca executa `supabase db reset`). UUIDs FIXOS p/ determinismo.
--
-- Usuários (senha: bofo-meca-oleo):
--   igorhaf@gmail.com    → super-admin (allowlist ADMIN_SUPER_ADMIN_EMAILS; SEM linha em public.users)
--   igorhaf16@gmail.com  → tenant-admin da Comida Modelo (perfil comida)
--
-- O shape de auth.users + auth.identities replica o que a Admin API do GoTrue gera
-- (instance_id zero-UUID, tokens '', identity provider 'email' com identity_data) — sem
-- isso o login falha invalid_credentials. pgcrypto (crypt/gen_salt) já vem no Supabase.

-- ========================================================================
-- 1) USUÁRIOS DO AUTH (auth.users + auth.identities) — idempotente
-- ========================================================================
do $$
declare
  super_id uuid := 'a0000000-0000-0000-0000-000000000001';
  tenant_id uuid := 'a0000000-0000-0000-0000-000000000016';
begin
  -- super-admin
  insert into auth.users (
    instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
    confirmation_token, email_change, email_change_token_new, recovery_token)
  values (
    '00000000-0000-0000-0000-000000000000', super_id, 'authenticated', 'authenticated',
    'igorhaf@gmail.com', crypt('bofo-meca-oleo', gen_salt('bf')), now(),
    '{"provider":"email","providers":["email"]}', '{"email_verified":true}', now(), now(),
    '', '', '', '')
  on conflict (id) do nothing;

  insert into auth.identities (provider_id, user_id, identity_data, provider, last_sign_in_at, created_at, updated_at)
  values (super_id::text, super_id,
    jsonb_build_object('sub', super_id::text, 'email', 'igorhaf@gmail.com', 'email_verified', true, 'phone_verified', false),
    'email', now(), now(), now())
  on conflict (provider, provider_id) do nothing;

  -- tenant-admin (Comida Modelo)
  insert into auth.users (
    instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
    confirmation_token, email_change, email_change_token_new, recovery_token)
  values (
    '00000000-0000-0000-0000-000000000000', tenant_id, 'authenticated', 'authenticated',
    'igorhaf16@gmail.com', crypt('bofo-meca-oleo', gen_salt('bf')), now(),
    '{"provider":"email","providers":["email"]}', '{"email_verified":true}', now(), now(),
    '', '', '', '')
  on conflict (id) do nothing;

  insert into auth.identities (provider_id, user_id, identity_data, provider, last_sign_in_at, created_at, updated_at)
  values (tenant_id::text, tenant_id,
    jsonb_build_object('sub', tenant_id::text, 'email', 'igorhaf16@gmail.com', 'email_verified', true, 'phone_verified', false),
    'email', now(), now(), now())
  on conflict (provider, provider_id) do nothing;
end $$;

-- ========================================================================
-- 2) TENANT "Comida Modelo" (perfil comida) + cardápio com opções
-- ========================================================================
insert into public.companies (id, name, slug, profile_id)
values ('c8000000-0000-0000-0000-000000000016', 'Comida Modelo', 'comida-modelo', 'comida')
on conflict (id) do update set name = excluded.name, profile_id = excluded.profile_id;

-- tenant-admin ligado à company (id casa com auth.users acima)
insert into public.users (id, company_id, email, role)
values ('a0000000-0000-0000-0000-000000000016', 'c8000000-0000-0000-0000-000000000016', 'igorhaf16@gmail.com', 'admin')
on conflict (id) do update set company_id = excluded.company_id, role = excluded.role;

insert into public.comida_config (company_id, delivery_fee_cents, min_order_cents)
values ('c8000000-0000-0000-0000-000000000016', 700, 2000)
on conflict (company_id) do update set delivery_fee_cents = excluded.delivery_fee_cents, min_order_cents = excluded.min_order_cents;

insert into public.comida_menu_items (id, company_id, name, description, price_cents, category) values
  ('cf000000-0000-0000-0000-000000000071', 'c8000000-0000-0000-0000-000000000016', 'X-Burger',        'Pão, hambúrguer 150g, queijo', 2500, 'lanches'),
  ('cf000000-0000-0000-0000-000000000072', 'c8000000-0000-0000-0000-000000000016', 'Pizza Mussarela', 'Mussarela, molho, orégano',    4500, 'pizzas'),
  ('cf000000-0000-0000-0000-000000000073', 'c8000000-0000-0000-0000-000000000016', 'Batata Frita',    'Porção 300g',                  1500, 'porcoes'),
  ('cf000000-0000-0000-0000-000000000074', 'c8000000-0000-0000-0000-000000000016', 'Coca-Cola Lata',  '350ml',                        600,  'bebidas')
on conflict (id) do nothing;

insert into public.comida_menu_item_options
  (id, company_id, menu_item_id, group_label, option_label, price_delta_cents, sort_order) values
  ('c0000000-0000-0000-0000-0000000007a1', 'c8000000-0000-0000-0000-000000000016', 'cf000000-0000-0000-0000-000000000071', 'Tamanho',    'Médio',        0,   0),
  ('c0000000-0000-0000-0000-0000000007a2', 'c8000000-0000-0000-0000-000000000016', 'cf000000-0000-0000-0000-000000000071', 'Tamanho',    'Grande',       500, 1),
  ('c0000000-0000-0000-0000-0000000007a3', 'c8000000-0000-0000-0000-000000000016', 'cf000000-0000-0000-0000-000000000071', 'Adicionais', 'Bacon',        300, 0),
  ('c0000000-0000-0000-0000-0000000007a4', 'c8000000-0000-0000-0000-000000000016', 'cf000000-0000-0000-0000-000000000071', 'Adicionais', 'Queijo extra', 200, 1)
on conflict (id) do nothing;

-- whatsapp instance + contatos + conversa (pra smoke de pedido/notificação)
insert into public.whatsapp_instances (id, company_id, instance_name, evolution_token)
values ('c0000000-0000-0000-0000-000000000071', 'c8000000-0000-0000-0000-000000000016', 'comida-modelo-inst', 'tok-comida')
on conflict (id) do nothing;

insert into public.contacts (id, company_id, phone_number, name) values
  ('c0000000-0000-0000-0000-000000000071', 'c8000000-0000-0000-0000-000000000016', '+5511933332222', 'João Cliente'),
  ('c0000000-0000-0000-0000-000000000072', 'c8000000-0000-0000-0000-000000000016', '+5511922221111', 'Ana Cliente')
on conflict (id) do nothing;

insert into public.conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by)
values ('c0000000-0000-0000-0000-000000000071', 'c8000000-0000-0000-0000-000000000016',
        'c0000000-0000-0000-0000-000000000071', 'c0000000-0000-0000-0000-000000000071', 'open', 'ai')
on conflict (id) do nothing;

-- ========================================================================
-- 3) UM TENANT DE EXEMPLO POR NICHO (roteamento de domínios)
-- Cada nicho ganha 1 company com slug navegável ({slug}.meadadigital.local).
-- CMS ALTERNADO: with_cms=true → cms_sites publicado + cms_pages home publicada
-- (cai na página pública /p/{slug}); with_cms=false → sem CMS (cai no login do nicho).
-- Slugs NUNCA colidem com subdomínio de nicho (validação slug_reserved_niche).
-- NÃO cria usuário logável por tenant — serve ao smoke de roteamento (público).
-- ========================================================================
do $$
declare
  t record;
  cid uuid;
  uid uuid;
  uemail text;
begin
  for t in
    select * from (values
      ('sushilegal',   'Sushi Legal',        'sushi',      true),
      ('sorrisolegal', 'Sorriso Legal',      'dental',     false),
      ('juridicopro',  'Jurídico Pro',       'legal',      true),
      ('mesafarta',    'Mesa Farta',         'restaurant', false),
      ('belezapura',   'Beleza Pura',        'salon',      true),
      ('recantoaltos', 'Recanto dos Altos',  'pousada',    false),
      ('corpoemforma', 'Corpo em Forma',     'academia',   true),
      ('patanuvem',    'Pata na Nuvem',      'pet',        false),
      ('motorforte',   'Motor Forte',        'oficina',    true),
      ('nutrevida',    'Nutre Vida',         'nutri',      false),
      ('navalhaouro',  'Navalha de Ouro',    'barbearia',  true),
      ('festamax',     'Festa Max',          'eventos',    false),
      ('glowestetica', 'Glow Estética',      'estetica',   true)
    ) as v(slug, name, profile_id, with_cms)
  loop
    -- UUIDs DETERMINÍSTICOS derivados do slug (md5→uuid) — reproduzíveis e estáveis,
    -- pra ligar o admin (public.users.company_id) à company sem depender de gen_random_uuid.
    cid := md5('company:' || t.slug)::uuid;
    uid := md5('user:'    || t.slug)::uuid;
    uemail := t.slug || '@meadadigital.com';

    insert into public.companies (id, name, slug, profile_id)
    values (cid, t.name, t.slug, t.profile_id)
    on conflict (slug) do update set id = excluded.id, name = excluded.name, profile_id = excluded.profile_id;

    -- admin logável do tenant (auth.users + identity + public.users) — alvo do "Acessar admin"
    -- do super-admin. Senha bofo-meca-oleo. Email: {slug}@meadadigital.com.
    insert into auth.users (
      instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
      raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
      confirmation_token, email_change, email_change_token_new, recovery_token)
    values (
      '00000000-0000-0000-0000-000000000000', uid, 'authenticated', 'authenticated',
      uemail, crypt('bofo-meca-oleo', gen_salt('bf')), now(),
      '{"provider":"email","providers":["email"]}', '{"email_verified":true}', now(), now(),
      '', '', '', '')
    on conflict (id) do nothing;

    insert into auth.identities (provider_id, user_id, identity_data, provider, last_sign_in_at, created_at, updated_at)
    values (uid::text, uid,
      jsonb_build_object('sub', uid::text, 'email', uemail, 'email_verified', true, 'phone_verified', false),
      'email', now(), now(), now())
    on conflict (provider, provider_id) do nothing;

    insert into public.users (id, company_id, email, role)
    values (uid, cid, uemail, 'admin')
    on conflict (id) do update set company_id = excluded.company_id, role = excluded.role;

    if t.with_cms then
      insert into public.cms_sites (company_id, published)
      values (cid, true)
      on conflict (company_id) do update set published = true;

      insert into public.cms_pages (company_id, page_slug, title, blocks, is_home, published)
      values (cid, 'home', t.name,
        jsonb_build_array(jsonb_build_object(
          'id', 'hero-1', 'type', 'hero',
          'props', jsonb_build_object('title', t.name, 'subtitle', 'Bem-vindo!'))),
        true, true)
      on conflict do nothing;
    end if;
  end loop;
end $$;

-- ========================================================================
-- 4) COMPANY-ÂNCORA DA PLATAFORMA (migration 44) — o "Meada" institucional.
-- is_platform=true; o super-admin edita o CMS dela DIRETO no painel (/dashboard/cms),
-- sem ser tenant. A raiz meadadigital.local serve este CMS. id/slug fixos e canônicos.
-- (Recriada aqui pra sobreviver a db reset + deleções acidentais.)
-- ========================================================================
insert into public.companies (id, name, slug, profile_id, status, is_platform)
values ('00000000-0000-0000-0000-000000000000', 'Meada', 'meada', 'generic', 'active', true)
on conflict (id) do update set is_platform = true, slug = excluded.slug, name = excluded.name;

-- site com tema meada-dark (identidade da marca: near-black + gradiente azul→roxo→rosa)
insert into public.cms_sites (company_id, published, theme)
values ('00000000-0000-0000-0000-000000000000', true, '{"preset":"meada-dark"}'::jsonb)
on conflict (company_id) do update set published = true, theme = excluded.theme;

-- A LANDING institucional do Meada, montada com os blocos meada_* (navbar/hero/services/
-- portfolio/cta/footer) — conteúdo idêntico ao defaultProps() de lib/cms/cms-block-type.ts
-- (réplica do meada-page). blocks em formato FLAT (normalizeToTree converte na leitura).
-- Editável pelo super-admin em /dashboard/cms.
insert into public.cms_pages (company_id, page_slug, title, blocks, is_home, published)
values ('00000000-0000-0000-0000-000000000000', 'home', 'Meada',
$json$[
  {"id":"navbar-1","type":"meada_navbar","props":{"brandName":"Meada","brandSuffix":"Digital","links":[{"label":"Serviços","href":"/servicos"},{"label":"Produtos","href":"/produtos"},{"label":"Sobre","href":"/sobre"},{"label":"Contato","href":"/contato"}],"ctaLabel":"Pedir orçamento","ctaHref":"/contato"}},
  {"id":"hero-1","type":"meada_hero","props":{"titlePrefix":"Sites e Sistemas","gradientText":"Sob Medida","titleSuffix":"pra Crescer","subtitle":"Desenvolvimento personalizado do site institucional ao sistema completo. Código limpo, prazo claro e foco no que importa pro seu negócio.","primaryLabel":"Comece Agora →","primaryHref":"/contato","secondaryLabel":"Ver Produtos","secondaryHref":"/produtos","stats":[{"value":"50+","label":"Projetos"},{"value":"20+","label":"Tecnologias"},{"value":"5+","label":"Anos no mercado"}],"showcase":"terminal","terminalTitle":"meada — projeto.sh","terminalLines":[{"kind":"cmd","text":"meada start --tipo=ecommerce"},{"kind":"info","text":"Discovery e arquitetura definidos."},{"kind":"check","text":"Frontend Next.js + Tailwind"},{"kind":"check","text":"Backend escalável + API REST"},{"kind":"check","text":"Banco de dados + migrations"},{"kind":"check","text":"Pagamentos integrados"},{"kind":"check","text":"CI/CD + deploy em produção"},{"kind":"check","text":"Painel admin para gestão"},{"kind":"done","text":"Projeto entregue ✦ pronto pra escalar"}],"terminalCaptionLeft":"do briefing ao ar em produção","terminalCaptionRight":"~ 2-6 sem","chatTitle":"Assistente Meada","chatMessage":"Olá! 👋 Sou o assistente da Meada Digital. Como posso te ajudar hoje?"}},
  {"id":"services-1","type":"meada_services","props":{"eyebrow":"Capacidades","title":"Tudo o Que Você Precisa para Crescer","items":[{"icon":"Code","color":"#60a5fa","title":"Desenvolvimento Personalizado","description":"Sites e sistemas feitos sob medida, do institucional ao mais complexo.","linkLabel":"Saiba mais →","linkHref":"/servicos/desenvolvimento"},{"icon":"Cloud","color":"#a855f7","title":"Infraestrutura em Nuvem","description":"Deploy, CI/CD, monitoramento e escalabilidade sem dores de cabeça.","linkLabel":"Saiba mais →","linkHref":"/servicos/nuvem"},{"icon":"Heart","color":"#ec4899","title":"Manutenção & Suporte","description":"Acompanhamento contínuo, evolução de funcionalidades e correções com prazo previsível.","linkLabel":"Saiba mais →","linkHref":"/contato"},{"icon":"Smartphone","color":"#22d3ee","title":"Design Mobile First","description":"Experiências nativas e fluidas em qualquer dispositivo e tamanho de tela.","linkLabel":"Saiba mais →","linkHref":"/servicos/mobile"},{"icon":"Layers","color":"#34d399","title":"Design & UX","description":"Interfaces bonitas e funcionais. Do wireframe ao Design System completo.","linkLabel":"Saiba mais →","linkHref":"/servicos/design-ux"},{"icon":"BarChart3","color":"#f97316","title":"APIs & Integrações","description":"Pagamentos, CRMs, ERPs e qualquer sistema conectado em uma arquitetura coesa.","linkLabel":"Saiba mais →","linkHref":"/servicos/apis-integracoes"}]}},
  {"id":"niches-1","type":"niches_grid","props":{"eyebrow":"Nossos Produtos","title":"Soluções por nicho","mode":"featured"}},
  {"id":"cta-1","type":"meada_cta","props":{"titlePrefix":"Pronto para","gradientText":"Transformar seu Negócio?","subtitle":"Do site institucional ao sistema completo. Sem enrolação, com prazo claro e resultado.","primaryLabel":"Agendar Consultoria","primaryHref":"/contato","secondaryLabel":"Ver Produtos","secondaryHref":"/produtos"}},
  {"id":"footer-1","type":"meada_footer","props":{"brandName":"Meada","brandSuffix":"Digital","tagline":"Agência digital especializada em sites e sistemas sob medida para pequenos e médios negócios.","instagramUrl":"https://instagram.com/meadadigital","whatsappUrl":"https://wa.me/5581992612292","columns":[{"heading":"Serviços","links":[{"label":"Sites Profissionais","href":"/servicos"},{"label":"Sistemas sob Medida","href":"/servicos"},{"label":"Manutenção & Suporte","href":"/contato"}]},{"heading":"Empresa","links":[{"label":"Sobre Nós","href":"/sobre"},{"label":"Produtos","href":"/produtos"},{"label":"Serviços","href":"/servicos"}]},{"heading":"Contato","links":[{"label":"oi@meadadigital.com","href":"mailto:oi@meadadigital.com"},{"label":"(81) 99261-2292","href":"https://wa.me/5581992612292"},{"label":"@meadadigital","href":"https://instagram.com/meadadigital"}]}],"copyright":"© Meada Agência Digital. Todos os direitos reservados."}}
]$json$::jsonb,
  true, true)
on conflict do nothing;

-- ========================================================================
-- 5) SUBPÁGINAS institucionais do Meada (servicos/sobre/contato/portfolio).
-- Réplica do meada-page original; blocos meada_* + genéricos. is_home=false.
-- O dropdown de navegação do CMS lista todas; meadadigital.local/{slug} as serve.
-- ========================================================================
-- ============ SERVIÇOS ============
insert into public.cms_pages (company_id, page_slug, title, blocks, is_home, published) values
('00000000-0000-0000-0000-000000000000','servicos','Serviços',
$json$[
  {"id":"nav","type":"meada_navbar","props":{"brandName":"Meada","brandSuffix":"Digital","links":[{"label":"Serviços","href":"/servicos"},{"label":"Produtos","href":"/produtos"},{"label":"Sobre","href":"/sobre"},{"label":"Contato","href":"/contato"}],"ctaLabel":"Pedir orçamento","ctaHref":"/contato"}},
  {"id":"hero","type":"meada_hero","props":{"titlePrefix":"Tecnologia que","gradientText":"Escala","titleSuffix":"com seu Negócio","subtitle":"Da ideia ao produto final, construímos com excelência técnica em cada etapa. Escolha as soluções que impulsionam seu crescimento.","primaryLabel":"Falar com especialista","primaryHref":"/contato","secondaryLabel":"","secondaryHref":"","stats":[],"showcase":"chat","chatTitle":"Assistente Meada","chatMessage":"Olá! 👋 Qual solução faz sentido pro seu projeto?","terminalTitle":"","terminalLines":[],"terminalCaptionLeft":"","terminalCaptionRight":""}},
  {"id":"svc","type":"meada_services","props":{"eyebrow":"Nossas Soluções","title":"Tudo o Que Você Precisa para Crescer","items":[
    {"icon":"Code","color":"#3b82f6","title":"Desenvolvimento sob Medida","description":"Criamos aplicações web, mobile e APIs altamente performáticas, alinhadas às necessidades do seu negócio. Arquitetura escalável · APIs RESTful & GraphQL · Testes automatizados.","linkLabel":"Saiba mais →","linkHref":"/servicos/desenvolvimento"},
    {"icon":"Cloud","color":"#a855f7","title":"Cloud & DevOps","description":"Infraestrutura robusta e escalável em AWS, GCP e Azure. Kubernetes & Docker · CI/CD automatizado · Monitoramento 24/7.","linkLabel":"Saiba mais →","linkHref":"/servicos/nuvem"},
    {"icon":"Heart","color":"#ec4899","title":"Manutenção & Suporte","description":"Acompanhamento contínuo do sistema em produção, com prazo previsível. Evolução contínua · SLA de resposta acordado · Backups e monitoramento.","linkLabel":"Saiba mais →","linkHref":"/contato"},
    {"icon":"Smartphone","color":"#06b6d4","title":"Apps Mobile","description":"Aplicativos nativos e cross-platform para iOS e Android com React Native. UX/UI nativo · Offline first · Push notifications.","linkLabel":"Saiba mais →","linkHref":"/servicos/mobile"},
    {"icon":"Layers","color":"#34d399","title":"Design & UX","description":"Interfaces que as pessoas adoram usar, do discovery à entrega. Design System completo · Testes de usabilidade · Prototipagem rápida.","linkLabel":"Saiba mais →","linkHref":"/servicos/design-ux"},
    {"icon":"BarChart3","color":"#f97316","title":"APIs & Integrações","description":"Conectamos sistemas legados, plataformas externas e microsserviços numa arquitetura coesa. REST, GraphQL & gRPC · Webhooks & eventos · Documentação OpenAPI.","linkLabel":"Saiba mais →","linkHref":"/servicos/apis-integracoes"}]}},
  {"id":"steps","type":"steps","props":{"eyebrow":"","title":"Como Trabalhamos","items":[
    {"number":"01","title":"Descoberta","description":"Mergulhamos no seu negócio para entender objetivos, desafios e oportunidades. Workshops colaborativos que alinham visão e estratégia."},
    {"number":"02","title":"Arquitetura","description":"Desenhamos a solução técnica ideal — stack, infraestrutura e integrações. Documentação clara antes de qualquer linha de código."},
    {"number":"03","title":"Execução","description":"Desenvolvimento ágil em sprints de duas semanas. Demos regulares, feedback contínuo e adaptação rápida às mudanças."},
    {"number":"04","title":"Lançamento","description":"Deploy seguro, monitoramento proativo e suporte pós-lançamento. Sua solução vai ao ar com confiança e estabilidade."}]}},
  {"id":"cta","type":"meada_cta","props":{"titlePrefix":"Qual solução é certa","gradientText":"para você?","subtitle":"Nossa equipe vai analisar seu caso e recomendar a arquitetura ideal para alcançar seus objetivos.","primaryLabel":"Falar com especialista","primaryHref":"/contato","secondaryLabel":"Ver Produtos","secondaryHref":"/produtos"}},
  {"id":"ft","type":"meada_footer","props":{"brandName":"Meada","brandSuffix":"Digital","tagline":"Agência digital especializada em sites e sistemas sob medida para pequenos e médios negócios.","instagramUrl":"https://instagram.com/meadadigital","whatsappUrl":"https://wa.me/5581992612292","columns":[{"heading":"Serviços","links":[{"label":"Sites Profissionais","href":"/servicos"},{"label":"Sistemas sob Medida","href":"/servicos"},{"label":"Manutenção & Suporte","href":"/contato"}]},{"heading":"Empresa","links":[{"label":"Sobre Nós","href":"/sobre"},{"label":"Produtos","href":"/produtos"},{"label":"Serviços","href":"/servicos"}]},{"heading":"Contato","links":[{"label":"oi@meadadigital.com","href":"mailto:oi@meadadigital.com"},{"label":"(81) 99261-2292","href":"https://wa.me/5581992612292"},{"label":"@meadadigital","href":"https://instagram.com/meadadigital"}]}],"copyright":"© Meada Agência Digital. Todos os direitos reservados."}}
]$json$::jsonb, false, true),

-- ============ SOBRE ============
('00000000-0000-0000-0000-000000000000','sobre','Sobre',
$json$[
  {"id":"nav","type":"meada_navbar","props":{"brandName":"Meada","brandSuffix":"Digital","links":[{"label":"Serviços","href":"/servicos"},{"label":"Produtos","href":"/produtos"},{"label":"Sobre","href":"/sobre"},{"label":"Contato","href":"/contato"}],"ctaLabel":"Pedir orçamento","ctaHref":"/contato"}},
  {"id":"hero","type":"meada_hero","props":{"titlePrefix":"Sites e Sistemas com","gradientText":"Cuidado de Verdade","titleSuffix":"","subtitle":"Sou desenvolvedor especializado em criar sites e sistemas sob medida — sempre com foco em qualidade, prazo e resultado real para o cliente.","primaryLabel":"Falar sobre meu projeto","primaryHref":"/contato","secondaryLabel":"","secondaryHref":"","stats":[],"showcase":"chat","chatTitle":"Assistente Meada","chatMessage":"Oi! 👋 Quer conhecer a Meada? Pergunta o que quiser.","terminalTitle":"","terminalLines":[],"terminalCaptionLeft":"","terminalCaptionRight":""}},
  {"id":"vals","type":"feature_grid","props":{"eyebrow":"","title":"Como Trabalho","items":[
    {"icon":"Heart","title":"Honestidade Acima de Tudo","description":"Só aceito projetos que consigo entregar bem. Prefiro dizer não do que entregar algo que não representa meu trabalho."},
    {"icon":"Sparkles","title":"Qualidade Sem Concessões","description":"Código limpo, interface bem pensada e atenção aos detalhes não são diferenciais — são o mínimo aceitável em cada entrega."},
    {"icon":"Rocket","title":"Tecnologia Que Faz Sentido","description":"Cada projeto recebe a stack que cabe no problema — nada de tecnologia da moda só pra inflar o escopo. Foco no que entrega valor real."},
    {"icon":"Target","title":"Foco no Resultado","description":"Cada linha de código existe para atingir um objetivo real. Métricas de negócio, não só de desenvolvimento, guiam cada decisão."}]}},
  {"id":"stats","type":"stats","props":{"items":[{"value":"50+","label":"Projetos entregues"},{"value":"98%","label":"Clientes satisfeitos"},{"value":"5+","label":"Anos de experiência"},{"value":"20+","label":"Tecnologias dominadas"}]}},
  {"id":"story","type":"steps","props":{"eyebrow":"","title":"A trajetória da Meada","items":[
    {"number":"2019","title":"Primeiros projetos","description":"Início da carreira desenvolvendo sites e sistemas para pequenas empresas, construindo experiência e portfólio."},
    {"number":"2021","title":"Sistemas mais complexos","description":"Expansão para projetos de maior porte: sistemas de gestão, APIs, painéis administrativos e integrações entre plataformas."},
    {"number":"2023","title":"Sistemas em produção","description":"Crescimento da carteira com projetos maiores: e-commerce, plataformas SaaS e sistemas internos em produção robustos."},
    {"number":"2024","title":"Meada","description":"Formalização da Meada como estúdio especializado em sites e sistemas sob medida, com foco em qualidade, prazo e suporte contínuo."}]}},
  {"id":"cta","type":"meada_cta","props":{"titlePrefix":"Tem um projeto","gradientText":"em mente?","subtitle":"Me conta o que você precisa. Site, sistema, integração — vamos descobrir juntos a melhor solução.","primaryLabel":"Falar sobre meu projeto","primaryHref":"/contato","secondaryLabel":"Ver Produtos","secondaryHref":"/produtos"}},
  {"id":"ft","type":"meada_footer","props":{"brandName":"Meada","brandSuffix":"Digital","tagline":"Agência digital especializada em sites e sistemas sob medida para pequenos e médios negócios.","instagramUrl":"https://instagram.com/meadadigital","whatsappUrl":"https://wa.me/5581992612292","columns":[{"heading":"Serviços","links":[{"label":"Sites Profissionais","href":"/servicos"},{"label":"Sistemas sob Medida","href":"/servicos"},{"label":"Manutenção & Suporte","href":"/contato"}]},{"heading":"Empresa","links":[{"label":"Sobre Nós","href":"/sobre"},{"label":"Produtos","href":"/produtos"},{"label":"Serviços","href":"/servicos"}]},{"heading":"Contato","links":[{"label":"oi@meadadigital.com","href":"mailto:oi@meadadigital.com"},{"label":"(81) 99261-2292","href":"https://wa.me/5581992612292"},{"label":"@meadadigital","href":"https://instagram.com/meadadigital"}]}],"copyright":"© Meada Agência Digital. Todos os direitos reservados."}}
]$json$::jsonb, false, true),

-- ============ CONTATO ============
('00000000-0000-0000-0000-000000000000','contato','Contato',
$json$[
  {"id":"nav","type":"meada_navbar","props":{"brandName":"Meada","brandSuffix":"Digital","links":[{"label":"Serviços","href":"/servicos"},{"label":"Produtos","href":"/produtos"},{"label":"Sobre","href":"/sobre"},{"label":"Contato","href":"/contato"}],"ctaLabel":"Pedir orçamento","ctaHref":"/contato"}},
  {"id":"hero","type":"meada_hero","props":{"titlePrefix":"Vamos Construir","gradientText":"Algo Incrível","titleSuffix":"","subtitle":"Tem um projeto em mente? Nossa equipe está pronta para transformar sua visão em realidade. Fale conosco.","primaryLabel":"Chamar no WhatsApp","primaryHref":"https://wa.me/5581992612292","secondaryLabel":"","secondaryHref":"","stats":[],"showcase":"chat","chatTitle":"Assistente Meada","chatMessage":"Oi! 👋 Conta seu projeto que a gente te responde rapidinho.","terminalTitle":"","terminalLines":[],"terminalCaptionLeft":"","terminalCaptionRight":""}},
  {"id":"cards","type":"columns","props":{"eyebrow":"","title":"Outras Formas de Contato","items":[
    {"icon":"Mail","title":"Email","body":"oi@meadadigital.com"},
    {"icon":"Phone","title":"WhatsApp","body":"(81) 99261-2292"},
    {"icon":"Globe","title":"Redes","body":"@meadadigital no Instagram"}]}},
  {"id":"faq","type":"faq","props":{"title":"Perguntas Frequentes","items":[
    {"question":"Quanto tempo leva para iniciar um projeto?","answer":"Após o alinhamento inicial, geralmente iniciamos o desenvolvimento em até 5 dias úteis. Para projetos urgentes, o discovery pode começar ainda mais rápido."},
    {"question":"Vocês atendem empresas de que porte?","answer":"De startups em early stage a empresas estabelecidas. A abordagem e o tamanho do trabalho se adaptam à complexidade e ao porte do projeto."},
    {"question":"Como funciona o processo de desenvolvimento?","answer":"Metodologia ágil com sprints de 2 semanas. Cada sprint termina com uma demo do que foi desenvolvido, garantindo alinhamento constante."},
    {"question":"Quais são as formas de pagamento?","answer":"Transferência, PIX e cartão. Para projetos grandes, pagamento por milestone; para serviços contínuos, contratos mensais."}]}},
  {"id":"ft","type":"meada_footer","props":{"brandName":"Meada","brandSuffix":"Digital","tagline":"Agência digital especializada em sites e sistemas sob medida para pequenos e médios negócios.","instagramUrl":"https://instagram.com/meadadigital","whatsappUrl":"https://wa.me/5581992612292","columns":[{"heading":"Serviços","links":[{"label":"Sites Profissionais","href":"/servicos"},{"label":"Sistemas sob Medida","href":"/servicos"},{"label":"Manutenção & Suporte","href":"/contato"}]},{"heading":"Empresa","links":[{"label":"Sobre Nós","href":"/sobre"},{"label":"Produtos","href":"/produtos"},{"label":"Serviços","href":"/servicos"}]},{"heading":"Contato","links":[{"label":"oi@meadadigital.com","href":"mailto:oi@meadadigital.com"},{"label":"(81) 99261-2292","href":"https://wa.me/5581992612292"},{"label":"@meadadigital","href":"https://instagram.com/meadadigital"}]}],"copyright":"© Meada Agência Digital. Todos os direitos reservados."}}
]$json$::jsonb, false, true),

-- ============ PORTFÓLIO ============
('00000000-0000-0000-0000-000000000000','portfolio','Portfólio',
$json$[
  {"id":"nav","type":"meada_navbar","props":{"brandName":"Meada","brandSuffix":"Digital","links":[{"label":"Serviços","href":"/servicos"},{"label":"Produtos","href":"/produtos"},{"label":"Sobre","href":"/sobre"},{"label":"Contato","href":"/contato"}],"ctaLabel":"Pedir orçamento","ctaHref":"/contato"}},
  {"id":"port","type":"meada_portfolio","props":{"eyebrow":"Portfolio","title":"Todos os Projetos","linkLabel":"Ver detalhes →","linkHref":"/portfolio","items":[]}},
  {"id":"cta","type":"meada_cta","props":{"titlePrefix":"Quer um projeto","gradientText":"como esses?","subtitle":"Do site institucional ao sistema completo. Cada projeto é construído sob medida para o negócio do cliente.","primaryLabel":"Começar meu projeto","primaryHref":"/contato","secondaryLabel":"Ver Serviços","secondaryHref":"/servicos"}},
  {"id":"ft","type":"meada_footer","props":{"brandName":"Meada","brandSuffix":"Digital","tagline":"Agência digital especializada em sites e sistemas sob medida para pequenos e médios negócios.","instagramUrl":"https://instagram.com/meadadigital","whatsappUrl":"https://wa.me/5581992612292","columns":[{"heading":"Serviços","links":[{"label":"Sites Profissionais","href":"/servicos"},{"label":"Sistemas sob Medida","href":"/servicos"},{"label":"Manutenção & Suporte","href":"/contato"}]},{"heading":"Empresa","links":[{"label":"Sobre Nós","href":"/sobre"},{"label":"Produtos","href":"/produtos"},{"label":"Serviços","href":"/servicos"}]},{"heading":"Contato","links":[{"label":"oi@meadadigital.com","href":"mailto:oi@meadadigital.com"},{"label":"(81) 99261-2292","href":"https://wa.me/5581992612292"},{"label":"@meadadigital","href":"https://instagram.com/meadadigital"}]}],"copyright":"© Meada Agência Digital. Todos os direitos reservados."}}
]$json$::jsonb, false, true);

-- ============ PRODUTOS (vitrine: TODOS os nichos na ordem) ============
insert into public.cms_pages (company_id, page_slug, title, blocks, is_home, published) values
('00000000-0000-0000-0000-000000000000','produtos','Produtos',
$json$[
  {"id":"nav","type":"meada_navbar","props":{"brandName":"Meada","brandSuffix":"Digital","links":[{"label":"Serviços","href":"/servicos"},{"label":"Produtos","href":"/produtos"},{"label":"Sobre","href":"/sobre"},{"label":"Contato","href":"/contato"}],"ctaLabel":"Pedir orçamento","ctaHref":"/contato"}},
  {"id":"niches","type":"niches_grid","props":{"eyebrow":"Produtos","title":"Um produto pra cada nicho","mode":"all"}},
  {"id":"cta","type":"meada_cta","props":{"titlePrefix":"Não achou o seu","gradientText":"nicho?","subtitle":"A Meada cria sistemas sob medida — fale com a gente e montamos a solução do seu jeito.","primaryLabel":"Falar com especialista","primaryHref":"/contato","secondaryLabel":"Ver Serviços","secondaryHref":"/servicos"}},
  {"id":"ft","type":"meada_footer","props":{"brandName":"Meada","brandSuffix":"Digital","tagline":"Agência digital especializada em sites e sistemas sob medida para pequenos e médios negócios.","instagramUrl":"https://instagram.com/meadadigital","whatsappUrl":"https://wa.me/5581992612292","columns":[{"heading":"Serviços","links":[{"label":"Sites Profissionais","href":"/servicos"},{"label":"Sistemas sob Medida","href":"/servicos"},{"label":"Manutenção & Suporte","href":"/contato"}]},{"heading":"Empresa","links":[{"label":"Sobre Nós","href":"/sobre"},{"label":"Produtos","href":"/produtos"},{"label":"Serviços","href":"/servicos"}]},{"heading":"Contato","links":[{"label":"oi@meadadigital.com","href":"mailto:oi@meadadigital.com"},{"label":"(81) 99261-2292","href":"https://wa.me/5581992612292"},{"label":"@meadadigital","href":"https://instagram.com/meadadigital"}]}],"copyright":"© Meada Agência Digital. Todos os direitos reservados."}}
]$json$::jsonb, false, true);

-- Vitrine inicial: marca 6 nichos como destaque + ordem (o root ajusta depois no painel).
insert into public.niche_showcase (profile_id, featured, display_order) values
  ('sushi', true, 0), ('comida', true, 1), ('dental', true, 2),
  ('salon', true, 3), ('barbearia', true, 4), ('estetica', true, 5),
  ('legal', false, 6), ('restaurant', false, 7), ('pousada', false, 8),
  ('academia', false, 9), ('pet', false, 10), ('oficina', false, 11),
  ('nutri', false, 12), ('eventos', false, 13)
on conflict (profile_id) do update set featured = excluded.featured, display_order = excluded.display_order;
