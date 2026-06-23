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
