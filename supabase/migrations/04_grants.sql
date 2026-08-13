-- =============================================================================
-- 04_grants.sql
-- Meada — privilégios de tabela para os roles `authenticated` e
--   `service_role`.
--
-- POR QUE ESTE ARQUIVO EXISTE (mesmo sendo redundante num Supabase de fábrica):
--   Num projeto Supabase intacto, o role `authenticated` JÁ recebe GRANT amplo
--   nas tabelas do schema public, via um default privilege que a plataforma
--   configura (ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ... TO anon,
--   authenticated, service_role). Então, de fábrica, estes grants são redundantes.
--
--   Concedemos explicitamente mesmo assim, por AUTO-SUFICIÊNCIA da migration. O
--   default privilege da plataforma é frágil e desaparece em cenários reais:
--     - drop schema public + create schema public (reset) NÃO restaura o default;
--     - pg_dump/pg_restore para outro projeto pode não carregar o default;
--     - mudança de política do Supabase no provisionamento;
--     - revisor sem o conhecimento implícito de que "o Supabase concede por baixo".
--   Em todos esses casos, sem GRANT explícito o painel (authenticated) não lê nada
--   e o RLS nem chega a ser avaliado — o Postgres barra no privilégio antes.
--   (Confirmado empiricamente: após reset do schema public, authenticated ficou
--    SEM grant nas tabelas e todo SELECT do painel deu "permission denied".)
--
-- RELAÇÃO COM O RLS (03): grant e RLS são camadas ORTOGONAIS e ambas necessárias.
--   O GRANT abre a PORTA (o role pode tocar a tabela); o RLS decide QUAIS LINHAS.
--   Sem grant, RLS é irrelevante (barra antes). Sem RLS, o grant vazaria tudo.
--   Por isso os grants abaixo ESPELHAM o que o RLS de 03 policia: concedemos a
--   ação só onde há policy correspondente, nada além.
--
-- ORDEM: este arquivo roda ANTES do 05_storage.sql e concede a forma FINAL dos
--   privilégios (sem dança revoke→re-grant). Em whatsapp_instances, o grant já
--   exclui evolution_token de origem — não há revoke depois.
--
-- NOTA sobre service_role:
-- service_role tem BYPASSRLS, mas isso NÃO substitui GRANT. PostgreSQL checa
-- privilégio de tabela ANTES de avaliar RLS — sem GRANT, a query falha em
-- "permission denied" antes do RLS ser consultado. Por isso service_role
-- também recebe grants explícitos aqui (GRANT ALL — é o backend Spring, que
-- escreve mensagens, gerencia instâncias da Evolution, mexe no token).
-- (Confirmado empiricamente: após reset do schema public, service_role ficou
--  SEM grant e SELECT/INSERT como service_role deu "permission denied".)
--
-- Assimetria intencional:
--   authenticated: grants restritos (defesa em profundidade no painel)
--   service_role:  GRANT ALL em todas (é o backend, precisa de acesso total)
-- =============================================================================


-- -----------------------------------------------------------------------------
-- companies — o painel lê e edita a própria empresa (RLS filtra para a própria).
--   Sem INSERT/DELETE: criar/remover tenant é onboarding via service_role.
-- -----------------------------------------------------------------------------
grant select, update on companies to authenticated;


-- -----------------------------------------------------------------------------
-- users — painel lê/edita usuários do tenant (RLS filtra). Sem INSERT/DELETE:
--   criar/remover operador passa pelo backend (cria também o auth.users).
-- -----------------------------------------------------------------------------
grant select, update on users to authenticated;


-- -----------------------------------------------------------------------------
-- whatsapp_instances — BLINDAGEM DO TOKEN consolidada aqui (column-grant direto).
--   O segredo evolution_token NUNCA é concedido a authenticated — nem leitura,
--   nem escrita. Concedemos a forma FINAL, coluna a coluna, sem revoke posterior:
--     SELECT: tudo MENOS evolution_token.
--     UPDATE: só phone_number, status (painel ajusta conexão; token é do backend).
--   Sem INSERT/DELETE (Patch 2 / RLS 03): criar/conectar/remover instância é
--   operação de backend (service_role) — a instância nasce quando o Spring
--   conecta na Evolution e recebe o token. service_role mantém acesso total
--   (BYPASSRLS + grants default da plataforma para service_role).
--
--   Lista de SELECT explícita = fail-closed: coluna nova futura nasce invisível
--   a authenticated até ser adicionada aqui.
-- -----------------------------------------------------------------------------
grant select (
  id,
  company_id,
  instance_name,
  phone_number,
  status,
  created_at,
  updated_at
) on whatsapp_instances to authenticated;

grant update (
  phone_number,
  status
) on whatsapp_instances to authenticated;

-- HARDENING PENDENTE (pós-primeiro-cliente) — cifrar evolution_token em repouso.
--   Estado atual (MVP): token em TEXTO PURO na coluna, protegido pelo column-grant
--     acima (authenticated não lê). Defesa de nível de aplicação, suficiente p/ MVP.
--   Limite: se service_role vazar OU um backup/dump escapar, os tokens estão
--     legíveis. O column-grant não protege contra isso.
--   Caminho quando houver cliente real (escolher 1):
--     (a) pgcrypto — pgp_sym_encrypt(token, key); chave no env do Spring, NUNCA no
--         banco. Backend cifra ao gravar, decifra ao usar. Dump traz só ciphertext.
--     (b) Supabase Vault — segredos cifrados gerenciados; a coluna referencia um
--         secret id em vez do valor.
--   Não implementar agora — sem problema medido e adiciona gestão de chave.


-- -----------------------------------------------------------------------------
-- Tabelas de configuração/catálogo — CRUD completo por tenant (RLS filtra linhas).
-- -----------------------------------------------------------------------------
grant select, insert, update, delete on services       to authenticated;
grant select, insert, update, delete on business_hours to authenticated;
grant select, insert, update, delete on faqs           to authenticated;
grant select, insert, update, delete on documents       to authenticated;
grant select, insert, update, delete on ai_settings     to authenticated;
grant select, insert, update, delete on contacts        to authenticated;


-- -----------------------------------------------------------------------------
-- conversations — painel cria/atualiza conversa, mas NÃO deleta (histórico).
--   Fechar = UPDATE status='closed'. Espelha o RLS de 03 (sem policy de DELETE).
-- -----------------------------------------------------------------------------
grant select, insert, update on conversations to authenticated;


-- -----------------------------------------------------------------------------
-- messages — SOMENTE SELECT. Toda escrita é via backend (service_role): contact
--   (webhook), ai, e intervenção humana — no mesmo fluxo que envia pela Evolution.
--   Espelha o RLS de 03 (só policy de SELECT). Gravar sem enviar = inconsistência.
-- -----------------------------------------------------------------------------
grant select on messages to authenticated;


-- =============================================================================
-- service_role — o backend Spring. GRANT ALL em todas as 11 tabelas.
--   service_role tem BYPASSRLS (ignora as policies de 03), mas BYPASSRLS não
--   supre GRANT (ver NOTA no cabeçalho) — daí os grants explícitos abaixo.
--   Acesso total porque o backend escreve mensagens (escritor único), gerencia
--   instâncias da Evolution e manipula evolution_token (todas as colunas).
-- =============================================================================
grant all on companies          to service_role;
grant all on users              to service_role;
grant all on whatsapp_instances to service_role;
grant all on services           to service_role;
grant all on business_hours     to service_role;
grant all on faqs               to service_role;
grant all on documents          to service_role;
grant all on ai_settings        to service_role;
grant all on contacts           to service_role;
grant all on conversations      to service_role;
grant all on messages           to service_role;
