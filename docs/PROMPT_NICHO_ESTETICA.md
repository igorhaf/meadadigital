>>> JÁ IMPLEMENTADO — perfil estetica, camada 8.3, migration 46_estetica.sql. Prompt de nicho
>>> RETROATIVO, formato T5. Fonte: CLAUDE.md seção Perfil Estética + migration 46 + docs/PERFIL_ESTETICA.md.

[TAREFA — PERFIL ESTETICA / EsteticaBot (camada 8.3) — RETROATIVO]

[CONTEXTO]
PROJETO MEADA em /home/igorhaf/meada.
Estetica é o 13º perfil vertical real (14º contando o generic) — o mais COMPLETO até a camada 8.3.
O tenant estetica (`profile_id='estetica'`) vira um produto de CLÍNICA DE ESTÉTICA (facial/
corporal, drenagem, limpeza de pele, depilação a laser): a equipe gerencia profissionais e
procedimentos, vende PACOTES de sessões, agenda sessões (consumindo o saldo de um pacote) e
registra a ficha de cada sessão. A IA atende clientes via WhatsApp — agenda sessões e captura a
intenção de compra de pacotes.

>>> TRAVA ESTÉTICA (persona, cravada) — o coração do perfil <
- A IA NUNCA indica nem RECOMENDA procedimento ("para isso a profissional vai te avaliar").
- A IA NUNCA OPINA sobre o corpo, a aparência ou "o que o cliente precisa".
- A IA NUNCA PROMETE resultado ("vai sumir", "fica perfeito").
- A IA NUNCA CONFIRMA PAGAMENTO de pacote — quem confirma é a clínica (o pacote nasce 'pendente').
- A IA NUNCA INVENTA PREÇO — usa o preço de cada procedimento do catálogo.
- A IA NUNCA DISCUTE CONTRAINDICAÇÃO ou condição de saúde — encaminha à avaliação presencial.
Espelho da trava clínica do dental/nutri, adaptada à estética.

EVOLUÇÃO ESTRUTURAL — combina TRÊS eixos + UMA escapada nova:
  (1) AGENDA POR PROFISSIONAL: CLONA o chassi do SALON (7.5). Conflito por `professional_id`
      (`findConflict` transacional, janela half-open). Dois clientes no mesmo horário com
      profissionais DIFERENTES não conflitam; com o MESMO profissional, sim → 409. Duração por
      procedimento. `end_at` materializado no INSERT (timestamptz+interval não é IMMUTABLE — lição
      SM-D/E). Snapshots no agendamento (professional_name/procedure_name/duration_minutes).
      Cliente NÃO é entidade própria (continua o contact; snapshots guest_name/guest_phone).

  (2) ESCAPADA — PACOTE MULTI-SESSÃO COM SALDO QUE DECREMENTA (`aesthetic_packages`): primeiro
      nicho com saldo pré-pago consumível. O cliente compra um pacote de N sessões de um
      procedimento; cada agendamento pode CONSUMIR 1 sessão. `sessions_remaining` é MATERIALIZADO
      (= total − used) e RE-DERIVADO na MESMA TRANSAÇÃO do agendamento. Primeiro nicho em que um
      agendamento mexe num contador pré-pago de OUTRA entidade transacionalmente. Defesa de
      corrida: o UPDATE de consumo é CONDICIONAL `where status='ativo' and sessions_remaining > 0`
      — fecha a janela no banco. ESGOTAR (remaining→0) muta o pacote pra 'esgotado'. CANCELAR um
      agendamento que consumiu DEVOLVE a sessão (used−1, e 'esgotado'→'ativo'). Agendamento AVULSO
      (package_id null) NÃO mexe em saldo.

  (3) FICHA/EVOLUÇÃO TEXTUAL por sessão (`aesthetic_session_notes`): sub-entidade 1:1 do
      agendamento (área tratada / parâmetros do aparelho / observações — texto livre). NÃO editável
      se o agendamento está cancelado. SEM FOTO (bloqueador SERVICE_ROLE_KEY). LGPD: registro
      ADMINISTRATIVO-estético, NÃO prontuário médico (dado clínico sensível é fase futura com
      cripto at-rest).

DECISÕES CRAVADAS (reais):
1. CLONA a AGENDA POR PROFISSIONAL do SALON (7.5) — conflito transacional half-open, duração por
   procedimento, snapshots, `end_at` materializado no INSERT. Mantém 1:1 onde não conflita.
2. ESCAPADA: pacote multi-sessão com saldo que decrementa. `sessions_remaining` MATERIALIZADO,
   re-derivado na transação. Consumo via UPDATE condicional. Cancelar agendamento devolve a sessão.
3. A IA NÃO INVENTA PREÇO: na compra, `total_cents = total_sessions × unit_price` do procedimento
   no catálogo — a tag NÃO carrega preço. A IA NÃO confirma pagamento (o pacote nasce 'pendente';
   só o tenant ativa, e a ativação libera o agendamento que consome saldo).
4. 'esgotado' NÃO é destino MANUAL — é materializado pelo backend ao consumir a última sessão; a
   reabertura 'esgotado'→'ativo' (ao devolver sessão) também é do backend, fora da máquina manual.
5. Cliente NÃO é entidade do core — continua o contact (snapshots guest_name/phone no agendamento;
   customer_name/phone no pacote).
6. DUAS tags de namespace próprio, distintas de TODAS as outras: `<agendamento_estetica>` (agenda,
   com package_id opcional → consome saldo) e `<compra_pacote>` (registra a intenção de compra;
   pacote 'pendente', preço do catálogo).

[FUNDAÇÃO — migration 46_estetica.sql]
- ALTER companies CHECK aceitar 'estetica' (preservando os 12 perfis anteriores + generic).
- RLS enable+force em todas as tabelas; policies via app.company_id(); grants authenticated +
  service_role. packages/appointments/session_notes: INSERT pelo BACKEND (service_role) — pacote e
  agendamento são criados pela IA via handlers; tenant só SELECT/UPDATE (ativa pacote / muda status
  no painel).
- sessions_remaining + total_cents (pacote) e end_at (agendamento) MATERIALIZADOS no INSERT; NÃO
  colunas geradas (recálculo cruza linhas / timestamptz+interval não-IMMUTABLE — lições anteriores).
- SNAPSHOTS: pacote congela procedure_name/unit_price_cents; agendamento congela professional_name/
  procedure_name/duration_minutes. Alterar procedimento/profissional no catálogo NÃO altera pacotes/
  agendamentos passados.
- Tabelas:
  * aesthetic_professionals — profissionais (catálogo). Espelho salon_professionals. Conflito de
    agenda é POR profissional. active=false retira da disponibilidade.
  * aesthetic_procedures — catálogo de procedimentos. duration_minutes (15..480) + unit_price_cents
    = preço de UMA sessão (base do total do pacote: total_sessions × unit_price). category texto
    livre. Espelho salon_offerings + price obrigatório.
  * aesthetic_config — horário (opens_at default 09:00 / closes_at default 19:00) + slot_minutes
    (default 30, 5..240). 1:1 com company (PK = company_id). Ausente → defaults. Espelho salon_config.
  * aesthetic_packages — A ESCAPADA. contact_id + procedure_id + conversation_id (nullables on
    delete set null/restrict). Snapshots customer_name/customer_phone/procedure_name/
    unit_price_cents. total_sessions (>0) / sessions_used (default 0, ≤ total) / sessions_remaining
    (≥0, MATERIALIZADO = total − used) / total_cents (≥0, MATERIALIZADO = total_sessions ×
    unit_price). status default 'pendente'. purchased_at / activated_at / status_updated_at. INSERT
    pelo backend (service_role); tenant SELECT/UPDATE.
  * aesthetic_appointments — agendamentos (clone salon + package_id + consumed_session). package_id
    nullable on delete set null (null = avulso). guest_name/guest_phone snapshots. start_at /
    duration_minutes (snapshot) / end_at (materializado). procedure_name/professional_name
    snapshots. consumed_session boolean default false (true se abateu 1 sessão do pacote). status
    default 'agendado'. Índice CRÍTICO do conflito: por professional_id, só status bloqueantes
    ('agendado','confirmado').
  * aesthetic_session_notes — ficha/evolução 1:1 com o agendamento. treated_area / device_params /
    observations (texto livre). unique(appointment_id). SEM foto. Registro administrativo-estético.
- DOIS status hardcoded materializados (cada um com parity test Java↔TS):
  * AestheticAppointmentStatus (clone salon): agendado → confirmado, cancelado ; confirmado →
    realizado, cancelado, falta ; realizado/cancelado/falta terminais. Notificam o cliente:
    confirmado (com data/hora/profissional) e cancelado. agendado/realizado/falta silenciosos.
  * AestheticPackageStatus (NOVO): pendente → ativo, cancelado ; ativo → esgotado, expirado,
    cancelado ; esgotado/expirado/cancelado terminais. PORÉM 'esgotado' NÃO é destino MANUAL — é
    materializado pelo backend ao consumir a última sessão; e a reabertura 'esgotado'→'ativo' (ao
    devolver sessão) também é do backend, fora da máquina manual. Transições MANUAIS do PATCH do
    tenant: ativar / expirar / cancelar. Notifica: ativo (boas-vindas do pacote, com o procedimento).

[BACKEND]
- Profissionais / Procedimentos / Config: CRUD por entidade (service + controller + repository +
  entity), padrão dos perfis anteriores. Config: GET (fallback 09:00/19:00/30) + PUT.
- Pacotes (AestheticPackageService + Controller + Repository + Notifier): criados pelo backend via
  CompraPacoteConfirmHandler (IA) OU no painel. total_cents = total_sessions × unit_price do
  procedimento (a IA NÃO inventa preço; a tag NÃO carrega preço). Nasce 'pendente'. Ativação
  (tenant) → 'ativo' (notifica o cliente). Saldo (sessions_remaining) re-derivado na transação do
  agendamento; UPDATE condicional `where status='ativo' and sessions_remaining > 0`. Esgotar →
  'esgotado' (backend). Cancelar agendamento consumido → devolve sessão (esgotado→ativo).
- Agendamentos (AestheticAppointmentService + Controller + Repository + Notifier +
  AestheticAppointmentConflict): conflito POR professional_id (findConflict transacional, janela
  half-open). end_at materializado no INSERT. Com pacote: consome 1 sessão de um pacote ATIVO do
  cliente — esgotado → recusa (package_exhausted); de outro cliente → package_wrong_contact; não
  ativo → package_not_active. Avulso (package_id null): não mexe em saldo. POST manual no painel é
  SEMPRE avulso. Status: PATCH com validação de transição (inválida → 409
  invalid_status_transition). Notificação outbound por status (confirmado/cancelado).
- Ficha (AestheticSessionNoteService + Controller + Repository): 1:1 com o agendamento. Não
  editável se o agendamento foi cancelado.
- IA:
  * AgendamentoEsteticaConfirmHandler — tag `<agendamento_estetica>` (com `package_id` opcional →
    consome saldo de um pacote ativo do cliente). Resolve o contato da conversa, valida o pacote,
    cria o agendamento + abate a sessão na MESMA transação.
  * CompraPacoteConfirmHandler — tag `<compra_pacote>` (procedimento + número de sessões). Registra
    a intenção de compra: pacote nasce 'pendente', preço do CATÁLOGO (total_sessions × unit_price).
    A clínica confirma o pagamento depois (ativação no painel).
  * Persona ESTETICA (ProfilePromptContext.ESTETICA) com a TRAVA ESTÉTICA embutida (não indica/
    recomenda procedimento, não opina sobre o corpo, não promete resultado, não confirma pagamento,
    não inventa preço, não discute contraindicação).
  * EsteticaContextCache TTL 20s: procedimentos (com preço de sessão), profissionais, PACOTES
    ATIVOS do cliente COM SALDO (pra IA agendar consumindo o package_id certo), slots por
    profissional (próximos 7 dias), + as 2 tags. Invalidação explícita em toda mutação.
  * EsteticaProfileGuard (403 forbidden_wrong_profile). JwtFilter autentica /api/estetica/** (além
    dos 12 perfis anteriores). OutboundService: maybeProcessAgendamentoEstetica +
    maybeProcessCompraPacote (encadeados após os outros perfis; perfil é único, só um age; a tag é
    REMOVIDA antes de enviar ao cliente).

[FRONTEND]
- getNavForProfile('estetica') injeta "Estética" (5 itens): Profissionais / Procedimentos /
  Pacotes / Agenda / Configurações. Subdomínio estetica.meadadigital.local. Paleta: rosa-po.
- Telas:
  * /dashboard/estetica-professionals — CRUD da equipe (conflito de agenda é por profissional).
  * /dashboard/estetica-procedures — CRUD com duração + preço POR SESSÃO (base do total do pacote).
  * /dashboard/estetica-packages — A TELA DA ESCAPADA: lista por status com o SALDO (restantes/
    total); cria pacotes e ativa/cancela.
  * /dashboard/estetica-appointments — agenda por dia; cria agendamento AVULSO (POST manual nunca
    consome pacote — o consumo é via IA na conversa); abre a ficha de cada sessão; transição de
    status.
  * /dashboard/estetica-settings — horário de funcionamento + granularidade de slot.
- types + SDKs por entidade (professionals, procedures, config, packages, appointments) em
  frontend/lib/api/estetica/ + frontend/profiles/estetica/estetica-types.ts.
- Status TS: aesthetic-appointment-status.ts + aesthetic-package-status.ts (os 2 parity tests).

[DOCS]
- CLAUDE.md: seção "## Perfil Estética (EsteticaBot, camada 8.3)" — clona a agenda por profissional
  do salon + inaugura a escapada do pacote multi-sessão com saldo, ficha 1:1, trava estética, as 2
  tags, os 2 status, o NÃO TEM.
- docs/PERFIL_ESTETICA.md: guia operacional do tenant (telas; o pacote — compra/ativação/consumo/
  devolução + estados; o agendamento — conflito por profissional / com pacote / avulso / ficha; o
  que a IA faz; o que a IA NÃO faz — trava estética; o que NÃO existe nesta fase).
- NÃO mexer em system-template.txt nem em outros perfis.

[TESTES BACKEND]
- AestheticAppointmentStatusParityTest (Java↔TS) — verde.
- AestheticPackageStatusParityTest (Java↔TS) — verde.
- ProfileTypeParityTest (ProfileType.ESTETICA presente em Java + TS).
- Suíte por entidade (service + controller integration): professionals, procedures, config,
  packages, appointments, session-notes.
- CHAVE da escapada (AestheticPackage/Appointment): compra → pacote 'pendente', total =
  total_sessions × unit_price (a IA não inventa preço); ativação → 'ativo'; agendamento com pacote
  consome 1 sessão (sessions_remaining decrementa na transação); esgotar → 'esgotado'; cancelar
  agendamento consumido devolve a sessão ('esgotado'→'ativo'); pacote de outro cliente →
  package_wrong_contact; não ativo → package_not_active; esgotado → package_exhausted; avulso não
  mexe em saldo. Conflito por profissional → 409. Snapshots preservados após alterar o catálogo.

[CONSTRAINTS DUROS]
- Migration única (46). Sem foto/anexo (bloqueador SERVICE_ROLE_KEY).
- Cliente NÃO é entidade do core — continua o contact (snapshots guest_name/customer_name no
  agendamento/pacote).
- ESCAPADA pacote: aesthetic_packages; sessions_remaining/total_cents MATERIALIZADOS; consumo via
  UPDATE condicional `where status='ativo' and sessions_remaining > 0` na MESMA transação do
  agendamento; cancelar devolve a sessão; 'esgotado' e a reabertura 'esgotado'→'ativo' são do
  backend (não destino manual).
- A IA NÃO inventa preço (total = total_sessions × unit_price do catálogo). A IA NÃO confirma
  pagamento (pacote nasce 'pendente'; só o tenant ativa).
- end_at + snapshots materializados no INSERT (não generated). Conflito de agenda POR professional_id.
- TRAVA ESTÉTICA na persona: não indica/recomenda, não opina sobre o corpo, não promete resultado,
  não discute contraindicação.
- DOIS status hardcoded com parity (AestheticAppointmentStatus + AestheticPackageStatus). DUAS tags
  (`<agendamento_estetica>` + `<compra_pacote>`) distintas de TODAS as outras.
- Ficha 1:1 com o agendamento (unique appointment_id); não editável se cancelado; SEM dado clínico
  sensível (LGPD — administrativo-estético).
- Cache de contexto da IA TTL 20s + invalidação em toda mutação. JwtFilter autentica /api/estetica/**.
- NÃO mexer em outros perfis nem em system-template.txt. Webhook OFF.
- Tabela nova entra na migration ANTES de tocar o banco (lição os_config) + no TRUNCATE/SCRIPTS do
  AbstractIntegrationTest.

[PASSO FINAL — resumido (estado real ao fechar a camada 8.3)]
- Migration 46_estetica.sql APLICADA no Supabase real (migration + company + seed + smoke SQL).
- Tenant igorhaf15 (clínica de estética, profile=estetica) provisionado (GoTrue), logável.
- ProfileType.ESTETICA adicionado; paridades AestheticAppointmentStatus, AestheticPackageStatus e
  ProfileType validadas (mvn verde — contagem do Surefire à época: 762).
- Commit + tag fase-8.3-fechada.
- PENDENTES à época: smoke E2E HTTP completo (sem service_role key) + olho humano sobre o vertical.

[REPORTAR]
- "perfil estetica — camada 8.3; CLONA a agenda por profissional do SALON (7.5)"
- "ESCAPADA: pacote multi-sessão com saldo que decrementa (aesthetic_packages); consumo
  transacional via UPDATE condicional; cancelar devolve a sessão; esgotado materializado pelo backend"
- "ficha/evolução 1:1 por sessão (aesthetic_session_notes), administrativa, sem foto, sem dado clínico"
- "TRAVA ESTÉTICA: IA não indica/recomenda, não opina sobre o corpo, não promete resultado, não
  confirma pagamento, não inventa preço, não discute contraindicação"
- "DOIS status com parity: AestheticAppointmentStatus + AestheticPackageStatus (esgotado/reabertura
  são do backend, não destino manual)"
- "DUAS tags: <agendamento_estetica> (consome saldo) + <compra_pacote> (pacote pendente, preço do
  catálogo), distintas de todas as outras"
- "OutboundService ganhou maybeProcessAgendamentoEstetica + maybeProcessCompraPacote"
- "getNavForProfile('estetica') com branch próprio; paleta rosa-po; telas estetica-{professionals,
  procedures,packages,appointments,settings}"
- "EsteticaContextCache TTL 20s + invalidação em toda mutação; JwtFilter autentica /api/estetica/**"
- "Próximas fases: foto antes/depois, prontuário/anamnese (dado sensível com cripto), pagamento real
  do pacote (Stripe #50), assinatura/recorrência de pacote, comissão de profissional, estoque,
  multi-unidade"
