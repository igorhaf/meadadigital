>>> JÁ IMPLEMENTADO — perfil pet, camada 7.8, migration 37_pet.sql. Prompt de nicho RETROATIVO,
>>> formato T5. Fonte: CLAUDE.md seção Perfil Pet + migration 37 + docs/PERFIL_PET.md.

[TAREFA — PERFIL PET / PetBot (camada 7.8) — RETROATIVO]

Documentação RETROATIVA, no formato T5, do perfil PET (PetBot) JÁ IMPLEMENTADO e fechado no
projeto MEADA (`/home/igorhaf/meada`). Não é uma tarefa a executar —
é o registro fiel do que existe no código, no banco (migration 37_pet.sql) e no guia operacional
(docs/PERFIL_PET.md). Oitavo perfil vertical real e ÚLTIMO da fila planejada de 8 — fecha o
catálogo (sushi 7.1 · legal 7.2 · restaurant 7.3 · dental 7.4 · salon 7.5 · pousada 7.6 ·
academia 7.7 · pet 7.8). `ProfileType.PET` no enum (9º contando generic).

[CONTEXTO]
PetBot é o template de nicho para PET SHOP / CLÍNICA VETERINÁRIA dentro do mesmo dashboard Meada.
O tenant (`profile_id='pet'`) gerencia profissionais (veterinários/banhistas/tosadores), serviços
(banho, tosa, consulta, vacinação) e os ANIMAIS de cada tutor, e a IA atende os tutores via
WhatsApp com tom carinhoso com o animal e atencioso com o tutor — identifica o tutor pelo telefone,
oferece os animais já cadastrados (ou cadastra um pet novo), sugere serviço + profissional com
horário livre, respeita a restrição de espécie e agenda na agenda de cada profissional.

>>> TRAVA DE COMPORTAMENTO DA IA (cravada) <<<
- A IA NUNCA dá diagnóstico veterinário.
- A IA NUNCA prescreve/receita medicação.
- A IA NUNCA recomenda tratamento.
- Tutor descreve um SINTOMA → a IA orienta a agendar uma CONSULTA presencial (não opina).
- A IA respeita a RESTRIÇÃO DE ESPÉCIE do serviço (um serviço "só para gatos" não é oferecido nem
  agendado para um cão); o backend REFORÇA a regra (species match).
- NÃO há prontuário clínico no sistema — `notes` (do animal e do agendamento) é ADMINISTRATIVO
  (LGPD): preferências, observações de contato; NUNCA dado clínico.

EVOLUÇÃO ESTRUTURAL — ANIMAL como SUB-ENTIDADE de um contato (TUTOR). Diferente de todos os perfis
anteriores (cujo "cliente" era o próprio contato), aqui o agendamento é para um ANIMAL, e o animal
pertence a um tutor (o contato do WhatsApp). `pet_animals.contact_id` é NOT NULL (FK restrict) —
primeira SM com sub-entidade de cliente PERSISTENTE entre conversas. Um tutor pode ter N animais;
cada agendamento referencia 1 animal. O conflito de agenda é POR PROFISSIONAL (igual salon): dois
profissionais podem atender no mesmo horário, mas o mesmo profissional não.

>>> ESCAPADAS / DECISÕES CRAVADAS reais <<<
1. ANIMAL é sub-entidade do contact (tutor). `pet_animals.contact_id NOT NULL`. Tutor NÃO é
   entidade própria — continua o contact; o tutor_name/tutor_phone do agendamento é snapshot.
2. CONFLITO POR PROFISSIONAL (clone do salon): `findConflict(professionalId, start, end)` com
   janela materializada (`NOT (end_at <= newStart OR start_at >= newEnd)`), só status bloqueantes
   (`agendado`/`confirmado`). Re-verificado DENTRO da transação no INSERT (defesa de corrida). 409
   `conflict_slot`.
3. SPECIES MATCH (regra cravada): um serviço com `species_restriction` (`cao`|`gato`|`outro`) só
   aceita animal daquela espécie — `svc.speciesRestriction() != null && !equals(animal.species())`
   → `SpeciesMismatchException` (400 `species_mismatch`). A IA respeita; o backend reforça.
   `species_restriction` NULL = qualquer espécie.
4. TAG `<agendamento_pet>` com 2 MODOS (a novidade do handler): `animal_id` (animal já cadastrado)
   OU `new_animal:{name,species,breed?}` (cadastra o animal como sub-entidade do tutor da conversa
   E agenda, no mesmo turno). Namespace exclusivo, distinta de todas as outras tags de perfil.
5. `end_at` materializado no INSERT (`start_at + duration_minutes`), NÃO coluna gerada
   (`timestamptz + interval` não é IMMUTABLE — lição SM-D/E/F).
6. SNAPSHOTS no agendamento: tutor (name/phone do contact do animal) + animal (name/species) +
   professional_name + service_name/category/price/duration. Arquivar/editar o cadastro depois
   NÃO altera agendamentos passados.
7. EXCLUIR vs ARQUIVAR: animal/profissional/serviço com agendamento → 409
   (`animal_in_use`/`professional_in_use`/`service_in_use`); o caminho preferido é arquivar
   (`active=false`), que tira da lista ativa sem perder histórico.
8. POST manual pelo tenant cria agendamento sem `conversation_id` (sem WhatsApp) — não notifica.
   NÃO há DELETE de agendamento (histórico; "remover" = status cancelado).

NÃO TEM neste perfil (registrado pra não inventar): prontuário/histórico clínico, carteira de
vacinas com agenda, prescrição, internação, pacote/plano de banho recorrente (assinatura — academia
cobre recorrência), foto do pet (bloqueador SERVICE_ROLE_KEY), pagamento real (Stripe é #50),
scheduler de lembrete/auto-transição de status, `dentist_id`-equivalente múltiplo por profissional.
Fases futuras.

[FUNDAÇÃO — migration 37_pet.sql]
- ALTER `companies` CHECK aceitar `'pet'`, PRESERVANDO os perfis anteriores
  (`generic,legal,dental,sushi,restaurant,salon,pousada,academia,pet`).
- RLS enable+force em todas as tabelas; policies via `app.company_id()`; grants `authenticated`
  (SELECT/INSERT/UPDATE/DELETE conforme a tabela) + `service_role` (all).
  `pet_appointments`: INSERT pelo BACKEND (service_role) — o agendamento é criado pela IA via
  AgendamentoPetConfirmHandler; o tenant só SELECT/UPDATE (status no painel). Sem policy de
  INSERT/DELETE de agendamento pro tenant.
- `end_at` MATERIALIZADO no INSERT; NÃO coluna gerada.
- Tabelas:
  * `pet_professionals` — catálogo (id, company_id, name CHECK 1..200, specialty texto livre,
    active default true, notes, timestamps). Conflito de agenda é por profissional;
    `active=false` retira da disponibilidade da IA.
  * `pet_services` — serviços (id, company_id, name CHECK 1..200, category texto livre,
    duration_minutes CHECK 15..240, price_cents nullable, `species_restriction` CHECK
    in('cao','gato','outro') NULLABLE = qualquer espécie, active default true, description,
    timestamps). `species_restriction` valida o fit serviço↔animal no agendamento; `duration_minutes`
    entra como snapshot.
  * `pet_config` — horário de funcionamento 1:1 com company (company_id PK, opens_at default
    '09:00', closes_at default '19:00', buffer_minutes default 0 CHECK >=0). Ausente → defaults.
  * `pet_animals` — A SUB-ENTIDADE (id, company_id, `contact_id NOT NULL` references contacts on
    delete restrict = TUTOR, name CHECK 1..100, species CHECK in('cao','gato','outro') NOT NULL,
    breed texto livre, sex CHECK in('macho','femea','desconhecido') default 'desconhecido',
    birth_year CHECK 1990..2030, notes administrativo, active default true = arquivado quando false,
    timestamps). Persiste entre conversas; `active=false` arquiva sem perder histórico.
  * `pet_appointments` — agendamentos (id, company_id, professional_id/service_id/animal_id refs on
    delete restrict, contact_id refs on delete set null = tutor atalho, conversation_id refs on
    delete set null, snapshots tutor_name NOT NULL/tutor_phone/animal_name NOT NULL/animal_species
    NOT NULL/professional_name NOT NULL/service_name NOT NULL/service_category/price_cents/
    duration_minutes NOT NULL, start_at, `end_at NOT NULL` materializado, status default 'agendado'
    CHECK in('agendado','confirmado','realizado','cancelado','falta'), notes, created_at,
    status_updated_at). Índice CRÍTICO do conflito: `idx_pet_appts_prof_active (professional_id,
    start_at) where status in ('agendado','confirmado')`. + índices company_status_start /
    animal_start / contact_start.
- Status do agendamento hardcoded (`PetAppointmentStatus` enum Java + `pet-appointment-status.ts`
  + `PetAppointmentStatusParityTest`): `agendado → confirmado, cancelado`; `confirmado → realizado,
  cancelado, falta`; `realizado`/`cancelado`/`falta` terminais. Transição inválida → 409
  `invalid_status_transition`. NOTIFICAM o tutor (texto FIXO DEFENSIVO, sem promessa/diagnóstico):
  `confirmado` (com serviço + nome do animal + profissional + data/hora) e `cancelado` (convite a
  remarcar); `agendado`/`realizado`/`falta` são silenciosos (quem furou não recebe sermão).
- TODAS as tabelas entram na migration 37 ANTES de tocar o banco (lição os_config) e no
  TRUNCATE/SCRIPTS do AbstractIntegrationTest.

[BACKEND] (src/main/java/com/meada/profiles/pet/)
- Profissionais: CRUD (`pet_professionals`) — `PetProfessionalService`/Controller/Repository.
  delete com agendamento → 409 `professional_in_use`; preferir arquivar (`active=false`).
- Serviços: CRUD com `species_restriction` (`pet_services`) — `PetService`/Controller/Repository.
  delete com agendamento → 409 `service_in_use`.
- Animais: CRUD da sub-entidade (`pet_animals`) — `PetAnimal`/Controller/Repository/Service.
  Cadastro exige tutor (contact) existente (`ContactNotFoundException`); espécie inválida →
  `InvalidSpeciesException`. delete com agendamento → 409 `animal_in_use`; preferir arquivar.
- Config: GET (fallback defaults 09:00/19:00/buffer 0) + PUT.
- Agendamentos (`PetAppointmentService` + `PetAppointmentController`):
  * `create(companyId, professionalId, serviceId, animalId, conversationId, startAt, notes)`:
    valida prof/serviço/animal existentes e ativos (Inactive*Exception / *NotFoundException),
    aplica SPECIES MATCH (`SpeciesMismatchException` → 400 species_mismatch), valida horário
    (`OutsideHoursException`, fuso America/Sao_Paulo HARDCODED), re-verifica CONFLITO por
    profissional na transação (`ConflictException` → 409 conflict_slot), materializa `end_at` e
    grava os SNAPSHOTS.
  * PATCH de status com validação de transição (inválida → 409 invalid_status_transition) +
    notificação outbound por status via `PetAppointmentNotifier` (resolve o contato do animal;
    pula em silêncio se não houver vínculo WhatsApp).
- Tag `<agendamento_pet>` — `AgendamentoPetConfirmHandler.parseAndCreate(companyId, conversationId,
  contactId, aiResponseText)`:
  * Regex (Pattern.DOTALL), NÃO tool calling / responseSchema (mesma restrição Gemini).
  * `date`+`start_time` → instante America/Sao_Paulo.
  * 2 MODOS via `resolveAnimal`: `animal_id` (UUID existente, a criação revalida que é do tenant)
    OU `new_animal:{name,species,breed?}` (cadastra o animal como sub-entidade do tutor da conversa
    e agenda no mesmo turno; sem contato resolvido / espécie inválida / dados faltando → empty).
  * Best-effort: qualquer falha (JSON inválido, campos faltando, conflito, espécie incompatível,
    fora do horário, inativo) → `Optional.empty()` + warn; a mensagem da IA segue sem agendamento.
  * O OutboundService REMOVE a tag antes de enviar ao cliente; `maybeProcessPetAppointment`
    encadeado após os outros perfis (perfil é único, só um age).
- Contexto da IA: `PetContextCache` (Caffeine TTL 20s, keyed pelo company), injeta profissionais
  ativos, serviços (com restrição de espécie e preço quando informado), ANIMAIS DO TUTOR (com último
  agendamento) e slots livres por profissional (próximos 7 dias). Persona PET carinhosa-atenciosa
  com a TRAVA embutida (nunca diagnóstico/prescrição/tratamento). Invalidação explícita em toda
  mutação.
- Guard: `PetProfileGuard` (403 `forbidden_wrong_profile`). `JwtAuthenticationFilter` autentica
  `/api/pet/**` (além dos 7 perfis anteriores e `/admin/**`).

[FRONTEND] (frontend/profiles/pet/, frontend/lib/api/pet/)
- `getNavForProfile('pet')` injeta o grupo "Pet Shop" com 5 itens: Profissionais / Serviços /
  Animais / Agenda / Configurações.
- Telas: `/dashboard/pet-professionals`, `/dashboard/pet-services`, `/dashboard/pet-animals`,
  `/dashboard/pet-appointments`, `/dashboard/pet-settings`.
  * Serviços: form com restrição de espécie (só cães / só gatos / só outros / qualquer).
  * Animais: escolha do tutor (contato), nome, espécie, raça, sexo, ano de nascimento; filtros por
    espécie/busca/arquivados; arquivar (preferido) vs excluir (bloqueado se tiver agendamento).
  * Agenda: lista por dia com filtro de status/profissional; novo agendamento manual (a lista de
    serviços mostra só os compatíveis com a espécie do animal escolhido); detalhe + transição de
    status com notificação em confirmar/cancelar.
  * Configurações: horário de funcionamento + buffer.
- `pet-types.ts`, `pet-appointment-status.ts` (const espelhando o enum Java) + SDKs em
  `lib/api/pet/` (professionals, services, animals, appointments, config).
- npm build limpo.

[DOCS]
- CLAUDE.md: seção "## Perfil Pet/PetBot (camada 7.8)" — registra a sub-entidade animal/tutor, o
  species match, a tag de 2 modos, o conflito por profissional, excluir-vs-arquivar, a trava
  veterinária e o "NÃO TEM". Nota de fechamento do catálogo dos 8 perfis verticais.
- docs/PERFIL_PET.md: guia operacional do tenant (Profissionais / Serviços com restrição de espécie
  / Animais como sub-entidade do tutor / Agenda + status / Configurações; como a IA atende; conflito
  por profissional; "o que o PetBot NÃO faz").
- NÃO mexe em system-template.txt nem em outros perfis.

[TESTES BACKEND]
- `PetAppointmentStatusParityTest` (Java↔TS) + `ProfileTypeParityTest`.
- Suíte de service + controller integration por entidade (espelho dos perfis-agenda): profissionais,
  serviços (com restrição), animais (sub-entidade, cadastro exige tutor), config (GET fallback +
  PUT), agendamentos (CHAVE: species match → species_mismatch; conflito por profissional →
  conflict_slot; fora do horário; transição inválida → 409; snapshots preservados; excluir-em-uso
  → 409 *_in_use; wrongProfile 403).
- `AgendamentoPetConfirmHandlerTest`: tag modo animal_id; tag modo new_animal (cadastra+agenda);
  sem animal_id nem new_animal → empty; new_animal sem tutor → empty; espécie incompatível → empty;
  sem tag → empty.
- Pool Hikari de teste minúsculo (`application-dev.yml`, min-idle 0/max 2) para não estourar o
  pooler Supabase com ~N ApplicationContexts.
- mvn final: contagem REAL do Surefire (`Tests run: N`), nunca grep textual.

[CONSTRAINTS DUROS]
- Migration única (37). Sem foto/anexo (bloqueador SERVICE_ROLE_KEY).
- Cliente (TUTOR) NÃO é entidade do core — continua o contact; o ANIMAL é a sub-entidade
  (`pet_animals.contact_id NOT NULL`). Snapshots de tutor/animal no agendamento.
- CONFLITO POR PROFISSIONAL, re-verificado na transação. `end_at` materializado (não generated).
- SPECIES MATCH: serviço com `species_restriction` só aceita aquela espécie → 400 species_mismatch.
- TAG `<agendamento_pet>` com 2 MODOS (animal_id OU new_animal), distinta de TODAS as outras tags.
- Status hardcoded com parity test. Transição inválida → 409. Só confirmado/cancelado notificam.
- Excluir-em-uso → 409 (*_in_use); arquivar (`active=false`) é o caminho preferido.
- A IA NUNCA diagnostica / prescreve / recomenda tratamento; sintoma → agenda consulta presencial.
- `notes` administrativo (LGPD), SEM dado clínico.
- NÃO mexer em outros perfis nem em system-template.txt. Webhook OFF.
- Cache de contexto TTL 20s + invalidação em toda mutação.
- git add EXPLÍCITO (nunca `git add .`); .env/CONTEXT.md/secrets NUNCA staged.
- SEED com timestamptz: `at time zone 'America/Sao_Paulo'`; IDs de namespace com sufixo NOVO.

[PASSO FINAL — resumido (RETROATIVO, já executado)]
- TENANT pet criado (padrão GoTrue + Caddy/etc/hosts para o subdomínio do nicho), seed de
  profissionais/serviços (com e sem restrição de espécie)/config/animais (sub-entidade de tutores)/
  agendamentos cobrindo estados e a escapada, contact vinculado (instance+conversation) para smoke
  de notificação + contact sem vínculo.
- `JwtFilter` autentica `/api/pet/**`. git add explícito + sanity (sem .env/secrets/CONTEXT) +
  commit semântico `feat(camada-7.8): perfil pet/PetBot` + tag `fase-7.8-fechada` + push origin
  main + tags. docker compose restart backend + `/admin/me` → 401.
- Smoke E2E: auth (role tenant_admin, profileId=pet); catálogo + guard (outro perfil → /api/pet/**
  → 403); cadastro de animal pela IA (new_animal); agendamento por animal_id; SPECIES MATCH (serviço
  restrito a gato com cão → species_mismatch); conflito por profissional → conflict_slot; transição
  de status com notificação (confirmado/cancelado); regressão dos perfis anteriores intactos;
  paridade `PetAppointmentStatusParityTest`/`ProfileTypeParityTest` verde.

[REPORTAR]
Incluir EXPLICITAMENTE:
- "ProfileType.PET adicionado (camada 7.8) — fecha o catálogo dos 8 perfis verticais"
- "Paridade PetAppointmentStatus e ProfileType validadas"
- "EVOLUÇÃO ESTRUTURAL: ANIMAL como sub-entidade do contato/tutor (pet_animals.contact_id NOT NULL)"
- "Conflito de agenda POR PROFISSIONAL (clone do salon), re-verificado na transação"
- "SPECIES MATCH: serviço com species_restriction só aceita aquela espécie → 400 species_mismatch"
- "Tag <agendamento_pet> com 2 MODOS (animal_id OU new_animal cadastra+agenda no mesmo turno)"
- "TRAVA: IA nunca diagnostica/prescreve/recomenda tratamento; sintoma → consulta presencial"
- "Excluir vs arquivar: *_in_use 409; arquivar (active=false) preserva histórico"
- "PetContextCache TTL 20s: animais do tutor + slots por profissional (7 dias)"
- "OutboundService ganhou maybeProcessPetAppointment"
- "getNavForProfile('pet') com branch próprio (Pet Shop: 5 telas)"
- "Tabelas criadas DENTRO da migration (lição os_config); seed com at time zone + sufixo novo"
- "notes administrativo (LGPD), sem dado clínico"
- "Próximas fases: prontuário, vacinas, prescrição, internação, pacote recorrente, foto, Stripe"
