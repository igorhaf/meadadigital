>>> SLOT ATRIBUÍDO (ver docs/prompts-nicho/README.md — fonte única de verdade, tem precedência
>>> sobre qualquer "provisório" no corpo): ordem 6 · camada 8.11 · migration 55_dermatologia.sql ·
>>> tenant igorhaf22 (company/user sufixo -022) · ids de seed sufixo -09x. Reconfirmar no arranque
>>> que a fila não avançou; se avançou, deslocar conforme o README.

[TAREFA — SUB-MARATONA: PERFIL DERMATOLOGIA / Dermatologia (camada 8.11)]

[CONTEXTO]
PROJETO MEADA em /home/igorhaf/meada.
17+ perfis verticais reais hoje (… comida 8.4, floricultura 8.5, pizzaria em draft) + generic.
Lê CLAUDE.md, CONTEXT.md e o filesystem no arranque pra cravar convenções, nº de migration,
contagem do Surefire e numeração de tenant ANTES de escrever qualquer código. NÃO hardcodar a
contagem do mvn — relatar a REAL do Surefire ao final.

VALORES ESPERADOS (CONFIRMAR no filesystem antes; são PROVISÓRIOS — há drafts disputando o slot):
- migration: o disco hoje tem `49_floricultura.sql` COMMITADO e `50_pizzaria.sql` UNTRACKED (draft).
  A próxima livre provavelmente é `51_dermatologia.sql` — mas CONFIRME `ls supabase/migrations/`
  no arranque e use o PRÓXIMO número livre real; se Pizzaria fechar antes, sobe de novo. NÃO
  assumir 50.
- tenant: igorhaf17 (CONFIRMAR; se já usado por Casamento/Pizzaria, usar o próximo livre —
  igorhaf18/19…). company c?0000000-…-0NN / user a?0000000-…-0NN seguindo a numeração do tenant.
- IDs de namespace compartilhado (contacts/instance/conversation) NO SEED com sufixo NOVO que NÃO
  colida com nenhum seed anterior (conferir os usados pra evitar colisão FK).

Dermatologia é template de nicho pra CONSULTÓRIO / CLÍNICA DERMATOLÓGICA dentro do mesmo dashboard
Meada. O tenant acessa dermatologia.meadadigital.local e vê o produto "Dermatologia". A IA atende
PACIENTES via WhatsApp, identifica pelo TELEFONE, mostra os pacientes vinculados ao contato, e
AGENDA consultas (primeira consulta / retorno / procedimento). Tom técnico-acolhedor, de quem
cuida da pele do paciente com seriedade clínica e sem alarmismo — espelho EXATO do tom DENTAL/NUTRI.

CLONA o chassi de AGENDA do DENTAL (camada 7.4) e/ou NUTRI (8.0): profissional (dermatologista) +
horário + conflito POR profissional (half-open, re-verificado na transação) + end_at MATERIALIZADO
no INSERT + paciente como SUB-ENTIDADE do contact + status hardcoded (agendada→confirmada→realizada
+ cancelada/falta) com parity test Java↔TS. Cliente NÃO é entidade do core — o paciente é
sub-entidade do contact (espelho nutri_patients), snapshots na consulta.

>>> ========================================================================================
>>> TRAVA DE SEGURANÇA CLÍNICA (O CORAÇÃO DESTA SM — INEGOCIÁVEL, espelho do DENTAL/NUTRI)
>>> ========================================================================================
A IA NUNCA exerce ato médico. Em TODA a conversa, sem exceção, a IA dermatológica:
- NUNCA dá diagnóstico dermatológico (não diz o que a pessoa "tem").
- NUNCA avalia/classifica/interpreta lesão, mancha, pinta, sinal, acne, micose, queda de cabelo,
  unha, alergia ou QUALQUER sintoma de pele.
- NUNCA recomenda tratamento, medicação, pomada, ácido, antibiótico, isotretinoína, protocolo,
  laser, peeling, procedimento ou conduta.
- NUNCA opina se algo "é grave", "é normal", "é câncer", "é melanoma", "é benigno" ou "não é nada".
- NUNCA orienta sobre uso de ácido/retinoide/protetor solar/dermocosmético/skincare/dosagem.
- Para QUALQUER dúvida clínica → ACOLHE com gentileza e ENCAMINHA a agendar consulta presencial
  ("para avaliar isso com segurança, o ideal é uma consulta com a dermatologista — posso agendar?").
- Se o paciente mandar FOTO de lesão/mancha/pinta pedindo avaliação ("isso é grave?", "o que é
  isso?", "preciso me preocupar?") → a IA NÃO AVALIA a foto. Acolhe ("entendo a preocupação") e
  encaminha pra consulta presencial. A avaliação dermatológica exige exame presencial/dermatoscopia.
- A trava vive na PERSONA (ProfilePromptContext.DERMATOLOGIA) E no schema (a IA não tem caminho de
  escrita de prontuário/laudo; o ÚNICO conteúdo clínico que a IA "entrega" é a NOTA DE PREPARO
  READ-ONLY gravada pelo médico — texto VERBATIM, sem interpretar).

>>> GUARDA — SINAIS DE ALARME (sem diagnosticar):
Se o paciente relatar lesão que MUDA de cor/forma/tamanho, que SANGRA, que CRESCE rápido, que COÇA/
DÓI persistentemente, ferida que NÃO CICATRIZA, ou pinta nova/assimétrica → a IA orienta buscar
AVALIAÇÃO COM URGÊNCIA, oferece a primeira consulta disponível, e NÃO dá nome à condição, NÃO diz
se é grave nem tranquiliza com promessa ("não deve ser nada"). Acolhe a preocupação e prioriza o
agendamento. NUNCA minimiza ("é só uma espinha") nem alarma ("isso parece câncer").

LGPD (cravado): `notes` (em pacientes e consultas) é ADMINISTRATIVO (preferência de horário,
contato, observação operacional), NÃO clínico. SEM prontuário, diagnóstico, dermatoscopia, histórico
de lesões, biópsia estruturada nesta SM — dados clínicos sensíveis ficam pra fase futura com
criptografia at-rest e log de acesso por usuário. A NOTA DE PREPARO do procedure_type é orientação
PRÉ-procedimento administrativa (ex.: "suspender ácido 5 dias antes", "vir sem maquiagem"), gravada
pelo médico no painel — não é prontuário.

EVOLUÇÃO ESTRUTURAL (o que diferencia das outras agendas clínicas dental/nutri):
TIPOS DE ATENDIMENTO COM DURAÇÃO E PREPARO PRÓPRIOS (`dermatologia_procedure_types`). Dental/nutri
tinham um `appointment_type` enum cravado com duração FIXA do config. Aqui o tenant CADASTRA os
tipos de atendimento, cada um com SUA duração e, opcionalmente, uma NOTA DE PREPARO (orientação
pré-procedimento, texto livre gravado pelo médico):
  - consulta (ex.: 30min, sem preparo) — primeira consulta ou retorno;
  - procedimento estético/cirúrgico (ex.: 60min+: cauterização, biópsia, botox, laser,
    crioterapia) que pode exigir uma NOTA DE PREPARO entregue READ-ONLY pela IA na confirmação.
A nota de preparo é o espelho EXATO da ENTREGA READ-ONLY de plano do nutri (EntregaPlanoHandler):
a IA entrega o texto EXATO gravado pelo médico, VERBATIM, sem interpretar, resumir ou adaptar. É a
escapada estrutural desta SM: a agenda clínica deixa de ter type+duração fixos e ganha um catálogo
de tipos com duração própria + um artefato de texto clínico read-only-pra-IA acoplado ao tipo.

  DECISÃO DE MODELAGEM (cravada, documentar a escolha no header da migration): modelar como TABELA
  `dermatologia_procedure_types` (id, company_id, name, duration_minutes, prep_instructions
  nullable, active, …) — NÃO como enum+campo. Motivo: a duração varia por tipo (consulta 30 ≠ botox
  60) e a nota de preparo é por tipo, cadastrável pelo tenant; um enum com prep_instructions hardcoded
  não escala e exigiria migration a cada novo procedimento. A consulta referencia o tipo
  (`procedure_type_id`) e SNAPSHOTA name+duration_minutes (+ tem um `appointment_kind` derivado/
  livre se quiser distinguir primeira/retorno textualmente — opcional, decisão menor do agente).

NÃO TEM nesta SM (registrado pra não inventar): prontuário/laudo/dermatoscopia estruturada
(dado clínico sensível — fase futura com cripto), histórico de lesões com mapa corporal, biópsia
com resultado anexado, receituário/prescrição digital, atestado, foto da lesão / antes-depois
(bloqueador SERVICE_ROLE_KEY), múltiplos consultórios/salas paralelas (1 agenda por profissional),
pacote multi-sessão com saldo (estetica cobre isso — aqui cada sessão é uma consulta avulsa),
pagamento/sinal (Stripe é #50), scheduler de auto-transição (consulta passada não vira "realizada"
sozinha) nem lembrete "sua consulta é amanhã", cancelamento de consulta pela IA (a IA SÓ agenda;
cancelar é ação humana no painel — espelho dental). Fases futuras.

DECISÕES CRAVADAS (revisor decidiu pelo Igor):
1. Chassi = agenda clínica do DENTAL/NUTRI: profissional + conflito POR professional_id (half-open,
   re-verificado na transação) + end_at materializado no INSERT + paciente sub-entidade do contact.
   MANTER. NÃO inventar conflito por company (há múltiplos dermatologistas).
2. Paciente é sub-entidade do contact (espelho nutri_patients, contact_id NOT NULL refs contacts).
   Snapshots patient_name/phone + professional_name na consulta. Cliente NÃO é entidade do core.
3. ESCAPADA = procedure_types como TABELA (duração por tipo + nota de preparo read-only), NÃO enum.
   A consulta referencia o tipo e snapshota name+duration. Documentar a escolha no header.
4. Nota de preparo = entrega READ-ONLY pela IA (espelho EntregaPlanoHandler do nutri): texto EXATO,
   verbatim, com BARREIRA de contato (só entrega preparo de consulta do PRÓPRIO contato da conversa).
5. Status da consulta hardcoded IDÊNTICO ao NutriAppointmentStatus: agendada → confirmada/cancelada;
   confirmada → realizada/cancelada/falta; realizada/cancelada/falta terminais. Parity Java↔TS.
   (ATENÇÃO ao gênero: dental usa "agendada" feminino; nutri usa "agendado" masculino. Dermatologia
   = CONSULTA, feminino → "agendada/confirmada/realizada/cancelada/falta". Cravar e ser consistente
   no enum, const TS, CHECK da migration e textos de notificação.)
6. A IA SÓ AGENDA e ENTREGA o preparo. NUNCA cancela, NUNCA muda status clínico, NUNCA diagnostica.
7. Tags: <consulta_derma> (AGENDA, 2 modos: patient_id existente OU new_patient cadastra+agenda —
   espelho <consulta_nutri>) e <entrega_preparo>{appointment_id} (ENTREGA read-only a nota de
   preparo do tipo da consulta — espelho <entrega_plano>). Namespaces distintos de <consulta_nutri>/
   <entrega_plano> e de TODAS as outras tags.

[FUNDAÇÃO — migration NN_dermatologia.sql  (NN = próximo slot livre; provisoriamente 51)]
- ALTER companies CHECK aceitar 'dermatologia' (estende a constraint atual — drop+add da
  companies_profile_id_check com TODOS os ids atuais + 'dermatologia'; CONFERIR a lista REAL no
  disco antes — ela cresceu desde a 39).
- RLS enable+force, policies via app.company_id(), grants authenticated + service_role. INSERT de
  CONSULTAS pelo BACKEND (service_role — a IA via ConfirmHandler OU o tenant via POST manual);
  tenant SELECT/UPDATE da consulta (mudar status na agenda). Profissionais/pacientes/tipos/config:
  CRUD do tenant (authenticated). Espelhar 33_dental.sql + 39_nutri.sql inteiros.
- end_at MATERIALIZADO no INSERT (start_at + duration_minutes). NÃO coluna gerada (timestamptz +
  interval não é IMMUTABLE — lição da SM-D). duration_minutes na consulta é SNAPSHOT do procedure_type
  no momento — alterar o tipo NÃO altera consultas já criadas.
- Tabelas:
  * dermatologia_professionals — dermatologistas (catálogo; conflito de agenda POR profissional;
    espelho nutri_professionals). (id, company_id refs companies on delete restrict, name CHECK
    1..200, specialty text livre (ex.: "dermatologia clínica", "dermatologia estética",
    "tricologia"), crm/rqe text nullable (registro profissional), active default true, notes,
    timestamps). Índices: (company_id, active) where active, (company_id, name).
  * dermatologia_config — horário de funcionamento (1:1 com company; espelho nutri_config/
    dental_clinic_config). (company_id PK refs companies on delete cascade, opens_at default '08:00',
    closes_at default '18:00', buffer_minutes default 0 CHECK >=0, timestamps). Ausente → defaults.
    SEM duration_minutes aqui — a duração vem do procedure_type (escapada).
  * dermatologia_procedure_types — A ENTIDADE NOVA (escapada): tipos de atendimento com duração +
    preparo. (id, company_id refs companies on delete restrict, name CHECK 1..120 (ex.: "Consulta",
    "Retorno", "Cauterização", "Botox"), duration_minutes int NOT NULL CHECK between 5 and 480,
    prep_instructions text NULLABLE (nota de preparo read-only, gravada pelo médico; vazio = sem
    preparo), active default true, notes, timestamps). Índices: (company_id, active) where active,
    (company_id, name). COMMENT cravando: duração por tipo (≠ config fixo do dental); prep_instructions
    é orientação PRÉ-procedimento entregue VERBATIM pela IA, NÃO é prontuário (LGPD administrativo).
  * dermatologia_patients — pacientes (SUB-ENTIDADE do contact; espelho nutri_patients). (id,
    company_id refs companies on delete restrict, contact_id uuid NOT NULL refs contacts on delete
    restrict, name CHECK 1..120, birth_date date nullable, notes text (ADMINISTRATIVO, não clínico),
    active default true (false=arquivado, não perde histórico), timestamps). Índices: (company_id,
    contact_id, active) where active, (company_id, name).
  * dermatologia_appointments — consultas (snapshots; conflito POR profissional; espelho
    nutri_appointments). (id, company_id refs companies on delete restrict, professional_id NOT NULL
    refs dermatologia_professionals on delete restrict, patient_id NOT NULL refs dermatologia_patients
    on delete restrict, procedure_type_id NOT NULL refs dermatologia_procedure_types on delete
    restrict, contact_id refs contacts on delete set null (atalho/snapshot), conversation_id nullable
    refs conversations on delete set null, patient_name text NOT NULL (snapshot), patient_phone text
    (snapshot opcional), professional_name text NOT NULL (snapshot), procedure_type_name text NOT
    NULL (snapshot), duration_minutes int NOT NULL (snapshot do tipo), start_at timestamptz NOT NULL,
    end_at timestamptz NOT NULL (MATERIALIZADO no INSERT), status text NOT NULL default 'agendada'
    CHECK in ('agendada','confirmada','realizada','cancelada','falta'), notes text (ADMINISTRATIVO),
    created_at, status_updated_at). Índices: (company_id, status, start_at); CRÍTICO do conflito —
    (professional_id, start_at) where status in ('agendada','confirmada'); (patient_id, start_at
    desc); (contact_id, start_at desc). RLS: SELECT/UPDATE pro tenant; INSERT só backend (sem policy
    authenticated de insert — igual nutri/dental).
- Status da CONSULTA hardcoded (DermatologiaAppointmentStatus enum Java + const TS + parity test):
  agendada → confirmada, cancelada ; confirmada → realizada, cancelada, falta ; realizada/cancelada/
  falta → terminal. Notificações (texto DEFENSIVO, SEM conteúdo clínico): só CONFIRMADA (com tipo +
  profissional + data/hora) e CANCELADA avisam o paciente; agendada/realizada/falta silenciosos.
  Espelho EXATO de NutriAppointmentStatus com gênero feminino.
- TODAS as 5 tabelas novas entram na migration ANTES de tocar o banco (banco se aplica A PARTIR do
  arquivo versionado — lição os_config da SM-J) e na lista de TRUNCATE/SCRIPTS do
  AbstractIntegrationTest.

[BACKEND]
- Estrutura em src/main/java/com/meada/profiles/dermatologia/ espelhando profiles/nutri/:
  raiz (DermatologiaAppointmentStatus, DermatologiaContextCache, DermatologiaProfileGuard) +
  subpastas professionals/, config/, patients/, proceduretypes/, appointments/.
- Professionals: CRUD padrão (espelho nutri_professionals). delete em uso (consulta com
  professional_id) → 409 professional_in_use; preferir desativar (active=false).
- ProcedureTypes (NOVO): CRUD (id, name, duration_minutes, prep_instructions nullable, active).
  delete em uso (consulta com procedure_type_id) → 409 procedure_type_in_use; preferir desativar.
  Validar duration_minutes 5..480 → 400 invalid_duration.
- Patients: CRUD (espelho nutri_patients). sub-entidade do contact (contact_id obrigatório). delete
  em uso (consulta com patient_id) → 409 patient_in_use; preferir arquivar (active=false).
- Config: GET (fallback default 08:00/18:00/0) + PUT. (espelho nutri_config; SEM duration aqui)
- Appointments (chassi nutri_appointments):
  * create (a partir da tag/IA ou POST manual): resolve procedure_type → snapshota
    name+duration_minutes; resolve patient → snapshota patient_name/phone; resolve professional →
    snapshota professional_name; start_at do (date+start_time @ America/Sao_Paulo); end_at = start_at
    + duration_minutes MATERIALIZADO. Valida: profissional/paciente/tipo existem e ativos
    (ProfessionalNotFound/PatientNotFound/ProcedureTypeNotFound/Inactive* → empty no handler / 4xx
    no controller); dentro do horário (OutsideHoursException → 400 outside_hours); conflito POR
    profissional re-verificado DENTRO da transação (findConflict half-open só status bloqueantes;
    ConflictException → 409 conflict_slot com detalhes de quem ocupa).
  * updateStatus: valida transição (inválida → 409 invalid_status_transition); terminal preenche
    status_updated_at; dispara notificação outbound conforme o status (DermatologiaAppointmentNotifier).
  * POST manual pelo tenant: sem conversation_id (sem WhatsApp) — não notifica.
  * NÃO há DELETE de consulta (histórico; "remover" = status cancelada).
- Notifier (espelho NutriAppointmentNotifier): best-effort, persiste OUTBOUND/HUMAN, texto DEFENSIVO
  SEM conteúdo clínico. Notifica: confirmada (com tipo + profissional + data/hora) e cancelada.
  agendada/realizada/falta silenciosos.
- Entrega READ-ONLY da nota de preparo (espelho EntregaPlanoHandler):
  * EntregaPreparoHandler parseia <entrega_preparo>{appointment_id}; resolve a consulta; pega o
    procedure_type da consulta; se prep_instructions é não-vazio, envia VERBATIM via notifier.sendText
    (NÃO passa pela IA). BARREIRA DE SEGURANÇA: só entrega se o contato da consulta == contato da
    conversa (impede vazar preparo de outro paciente). Sem preparo / consulta inexistente / contato
    diferente → Optional.empty + warn. Devolve o texto entregue em sucesso.
- IA:
  * Persona DERMATOLOGIA nova em ProfilePromptContext (tom técnico-acolhedor com a TRAVA DE SEGURANÇA
    CLÍNICA + GUARDA de sinais de alarme embutidos no prompt — espelhar o tom e a estrutura de
    NUTRI/DENTAL; ver bruto sugerido abaixo). Adicionar case DERMATOLOGIA no switch de segmentFor(id)
    e o branch if ("dermatologia".equals(profileId)) em segmentFor(id, companyId, conversationId).
  * Contexto injetado (DermatologiaContextCache, cache TTL 30s — espelho do dental, keyed por
    (companyId, contactId)): dermatologistas ativos + tipos de atendimento (nome + duração; SEM
    despejar prep_instructions no contexto, igual nutri não despeja o body do plano — só indica que
    o tipo TEM preparo) + pacientes do contato (com próxima consulta) + slots livres POR profissional
    (próximos 14 dias) + instruções e as 2 tags. Invalidação explícita em toda mutação (profissional/
    tipo/paciente/consulta/config).
  * Tag <consulta_derma>{"professional_id","procedure_type_id","date":"YYYY-MM-DD","start_time":
    "HH:MM","patient_id":"UUID|null","new_patient":{"name","birth_date?"}|null,"notes"} →
    AgendamentoDermaConfirmHandler (espelho AgendamentoNutriConfirmHandler; 2 modos patient_id/
    new_patient; cria consulta; best-effort; qualquer falha → empty + warn).
  * Tag <entrega_preparo>{"appointment_id":"UUID"} → EntregaPreparoHandler (espelho EntregaPlanoHandler;
    entrega VERBATIM a nota de preparo do tipo da consulta; barreira de contato; sem preparo → empty).
  * JwtFilter autentica /api/dermatologia/ (adicionar DERMATOLOGIA_PATH_PREFIX = "/api/dermatologia/"
    + o !uri.startsWith(...) no shouldNotFilter). OutboundService ganha maybeProcessConsultaDerma +
    maybeProcessEntregaPreparo (best-effort, guard "dermatologia".equals(findProfileId), contactId
    via findContactIdByConversation, encadeados APÓS os outros perfis — perfil é único, só um age;
    REMOVE a tag antes de enviar; espelho EXATO de maybeProcessConsultaNutri/maybeProcessEntregaPlano).

  BRUTO SUGERIDO da persona (ajustar mecanicamente; concatenar como string Java, espelho NUTRI):
  "Você é o assistente virtual de um consultório de dermatologia. Tom técnico mas acolhedor, sério "
  "e sem alarmismo. Seu papel é AGENDAR consultas (primeira consulta, retorno ou procedimento) e "
  "ENTREGAR a orientação de preparo que a dermatologista já gravou — e NADA além disso. Avaliar a "
  "pele é ato médico exclusivo: você NUNCA dá diagnóstico; NUNCA avalia, classifica ou interpreta "
  "lesão, mancha, pinta, sinal, acne, micose, queda de cabelo, unha ou qualquer sintoma de pele; "
  "NUNCA recomenda tratamento, medicação, ácido, pomada, protetor solar, dermocosmético, procedimento "
  "ou protocolo; NUNCA opina se algo 'é grave', 'é normal', 'é câncer' ou 'não é nada'. Se o paciente "
  "enviar FOTO de uma lesão pedindo avaliação, você NÃO avalia a foto — acolhe a preocupação e "
  "explica que a avaliação exige consulta presencial; ofereça agendar. Para QUALQUER dúvida clínica, "
  "oriente a agendar consulta. Se o paciente relatar uma lesão que MUDA, SANGRA, CRESCE, COÇA/DÓI de "
  "forma persistente ou não cicatriza, oriente a buscar avaliação COM URGÊNCIA e ofereça a primeira "
  "consulta disponível, SEM dar nome à condição e SEM dizer se é grave (nem minimizar, nem alarmar). "
  "Identifique o paciente pelo telefone; se for o primeiro atendimento, peça o nome."

[FRONTEND]
- Telas (App Router, /dashboard/dermatologia-*):
  * /dashboard/dermatologia-professionals — CRUD dermatologistas (desativar preferido a excluir;
    delete em uso → 409).
  * /dashboard/dermatologia-patients — CRUD pacientes (sub-entidade do contact; arquivar preferido;
    delete em uso → 409). notes administrativo.
  * /dashboard/dermatologia-procedures — CRUD dos TIPOS DE ATENDIMENTO (a tela da escapada: nome +
    duração + nota de preparo (textarea) + ativo). Deixar EXPLÍCITO na UI que o preparo é entregue ao
    paciente VERBATIM e NÃO é prontuário.
  * /dashboard/dermatologia-appointments — agenda: lista por status, criar consulta (escolhe
    profissional + tipo + paciente + data/hora; conflito → 409 conflict_slot; fora do horário → 400),
    transição de status (botões respeitando ALLOWED_NEXT; inválida → 409). Mostrar o tipo + duração.
  * /dashboard/dermatologia-settings — horário (opens_at/closes_at/buffer; SEM duração — vem do tipo).
- types + SDKs (professionals, procedure-types, patients, config, appointments) espelhando nutri.
  ProcedureType: { id, name, durationMinutes, prepInstructions: string|null, active, ... }.
- Status TS dermatologia-appointment-status.ts (5 ids, ALLOWED_NEXT, statusLabel) +
  DermatologiaAppointmentStatusParityTest Java↔TS. (procedure_type/preparo SEM parity — não é máquina.)
- getNavForProfile('dermatologia') com BRANCH PRÓPRIO injeta "Dermatologia" (5 itens:
  Dermatologistas, Pacientes, Tipos de Atendimento, Agenda, Configurações). ATENÇÃO: floricultura
  ficou no enum SEM branch em getNavForProfile (fallback) — NÃO repetir esse gap; dermatologia
  PRECISA do branch (if (profileId === 'dermatologia') return [DERMATOLOGIA_GROUP, ...NAV_GROUPS]).
  Subdomínio dermatologia.meadadigital.local. Paleta: 'celeste' (azul-clínico — JÁ USADA pelo dental;
  se quiser distinção visual entre os dois nichos clínicos, sugerir 'salvia' (verde-sálvia, usada
  pelo nutri) ou 'rosa-po' — decisão do agente, mas registrar a escolha; recomendação: 'celeste' por
  ser a cor clínica natural, OU 'salvia' pra não colidir com o dental no enum/paleta).
- frontend/lib/profiles/profile-type.ts: adicionar
  DERMATOLOGIA('dermatologia','Dermatologia','dermatologia','<paleta>') no enum Java E o objeto
  espelho no const TS (ProfileTypeParityTest valida).
- npm build limpo (next build — Turbopack dev esconde import quebrado).

[DOCS]
- CLAUDE.md: seção "## Perfil Dermatologia (camada 8.x — confirmar nº)" espelhando as seções de
  perfil. Documentar EXPLÍCITO: clona o chassi de agenda do DENTAL/NUTRI (conflito por profissional +
  end_at materializado + paciente sub-entidade); a ESCAPADA (procedure_types como tabela com duração
  própria + nota de preparo entregue READ-ONLY pela IA, espelho da entrega de plano do nutri); a TRAVA
  DE SEGURANÇA CLÍNICA (NUNCA diagnostica/avalia lesão/foto/recomenda) + GUARDA de sinais de alarme; as
  2 tags (<consulta_derma>, <entrega_preparo>); LGPD (notes/preparo administrativos, sem prontuário).
- docs/PERFIL_DERMATOLOGIA.md: guia operacional do tenant (dermatologistas, tipos de atendimento — com
  a nota de preparo e como ela chega ao paciente, pacientes, agenda — estados/notificações; como a IA
  atende; o bloco "o que a IA NUNCA faz" + sinais de alarme). Espelhar PERFIL_NUTRI.md / PERFIL_DENTAL.md.
- NÃO mexer em system-template.txt nem em outros perfis.

[TESTES BACKEND]
Espelhar a suíte do nutri (service + controller integration por entidade):
- DermatologiaAppointmentStatusParityTest + ProfileTypeParityTest (dermatologia no enum/const).
- DermatologiaProfessionalServiceTest + ControllerIntegrationTest (CRUD, delete-em-uso 409).
- DermatologiaProcedureTypeServiceTest + ControllerIntegrationTest (CRUD; invalid_duration 400;
  delete-em-uso 409; prep_instructions nullable persiste).
- DermatologiaPatientServiceTest + ControllerIntegrationTest (CRUD sub-entidade; delete-em-uso 409).
- DermatologiaConfigServiceTest/ControllerIntegrationTest (GET fallback + PUT).
- DermatologiaAppointmentServiceTest (create snapshota tipo+duração+paciente+profissional; end_at
  materializado = start_at + duration; CONFLITO POR PROFISSIONAL re-verificado na transação
  (mesmo prof+overlap → conflict; profs diferentes mesmo horário → OK — paralelismo); fora do
  horário → outside_hours; transição válida/inválida; notificação confirmada/cancelada) +
  ControllerIntegrationTest (409 conflict_slot; 409 invalid_status_transition; wrongProfile 403).
- AgendamentoDermaConfirmHandlerTest (2 modos: patient_id existente agenda; new_patient cadastra+
  agenda; sem tag → empty; ids inválidos → empty; conflito → empty).
- EntregaPreparoHandlerTest (a CHAVE da entrega READ-ONLY): consulta com tipo COM preparo + contato
  da conversa == contato da consulta → entrega o texto EXATO (asserção casa o conteúdo VERBATIM);
  consulta de OUTRO contato → BLOQUEADO (empty); tipo SEM preparo → empty; sem tag → empty.
- (TRAVA CLÍNICA: a trava vive na persona — testar que a persona DERMATOLOGIA contém os marcadores
  "NUNCA dá diagnóstico"/"NÃO avalia a foto"/"sinal de alarme/urgência" via assert no segmentFor,
  espelho de como se testa o tom em SMs anteriores; e que segmentFor('dermatologia') é não-vazio e
  começa com "# Persona (Dermatologia)".)
mvn final = relatar contagem REAL do Surefire (não estimar).

[CONSTRAINTS DUROS]
- Migration única (próximo slot livre; provisoriamente NN=51 — CONFIRMAR). Sem foto/anexo.
- Cliente NÃO é entidade do core — paciente é sub-entidade do contact (contact_id NOT NULL); snapshots
  patient_name/phone + professional_name + procedure_type_name + duration_minutes na consulta.
- Conflito POR professional_id (half-open, re-verificado na transação). NÃO por company. end_at
  materializado no INSERT (não generated). duration vem do procedure_type (snapshot), NÃO do config.
- procedure_types = TABELA (duração + preparo por tipo), NÃO enum. Documentar a escolha no header.
- Nota de preparo entregue READ-ONLY (verbatim, barreira de contato) — espelho EntregaPlanoHandler.
  A IA NUNCA gera/interpreta/resume o preparo.
- TRAVA CLÍNICA: a IA NUNCA diagnostica, NUNCA avalia lesão/mancha/pinta/foto, NUNCA recomenda
  tratamento/medicação/procedimento, NUNCA opina "é grave/é câncer/é nada", NUNCA orienta ácido/
  protetor/skincare. Dúvida clínica → encaminha a agendar. Sinal de alarme → urgência sem diagnosticar.
- A IA SÓ AGENDA e ENTREGA o preparo. NUNCA cancela/muda status clínico pela conversa.
- Status feminino (agendada/confirmada/realizada/cancelada/falta) consistente em enum/TS/CHECK/textos.
- Tags <consulta_derma> e <entrega_preparo> distintas de TODAS as outras.
- LGPD: notes (paciente+consulta) e prep_instructions são ADMINISTRATIVOS, sem prontuário/diagnóstico.
- NÃO mexer em outros perfis nem em system-template.txt. Webhook OFF.
- Cache TTL 30s + invalidação em toda mutação. NÃO despejar prep_instructions no contexto da IA
  (só indica que o tipo tem preparo; o texto sai só na entrega — espelho do body do plano do nutri).
- 529 → inline. Gate 3× → pausar. Working tree sujo → pausar. git add EXPLÍCITO (nunca git add .);
  .env/CONTEXT.md/secrets NUNCA staged.
- SEED com timestamptz: usar `at time zone 'America/Sao_Paulo'` (lição do fuso).
- IDs de namespace compartilhado no seed com sufixo NOVO (conferir os usados; evitar colisão FK).
- Tabela nova entra na migration ANTES de tocar o banco (lição os_config). Adicionar as 5 tabelas
  ao TRUNCATE/SCRIPTS do AbstractIntegrationTest.
- Pool de teste minúsculo já está em src/test/resources/application-dev.yml (lição SM-K Hikari ×
  N contextos) — NÃO mexer; só confere que segue lá.
- Decisões menores: agente decide (layout exato, ícones do nav, nome de constante, paleta entre as
  sugeridas, se cria appointment_kind textual primeira/retorno).

[PASSO FINAL — TENANT + SEED + COMMIT + PUSH + SMOKE + RELATÓRIO]
F.1 — TENANT igorhaf17 (Clínica Derma Modelo, profile=dermatologia), padrão GoTrue (instance_id=
      zero-UUID + colunas de token='' não NULL — lição seed auth.users), senha em comunicação direta.
      company c?0000000-…-0NN / user a?0000000-…-0NN (numeração do tenant confirmado). Caddy +
      /etc/hosts pra dermatologia.meadadigital.local. (Se igorhaf17 já existe de outra SM, usar o
      próximo livre e reportar.)
F.2 — Seed /tmp/seed-dermatologia.sql (NÃO COMITAR; `at time zone 'America/Sao_Paulo'`; ids sufixo
      NOVO; lição os_config — só roda DEPOIS que a migration versionada está no disco/aplicada):
  - config: opens_at 08:00 / closes_at 18:00.
  - 2 dermatologistas: "Dra. Helena Castro" (specialty "dermatologia clínica"), "Dr. Paulo Renno"
    (specialty "dermatologia estética").
  - 3 tipos de atendimento: "Consulta" (30min, SEM preparo), "Retorno" (20min, SEM preparo),
    "Cauterização" (60min, COM prep_instructions = orientação real ex.: "Compareça sem maquiagem na
    região. Suspenda ácido/retinoide 5 dias antes. Traga acompanhante." — pra smoke da entrega read-only).
  - contact "Mariana Alves" +5511944443333 (VINCULADO: instance+conversation, pra smoke de
    notificação + entrega de preparo) com 1 paciente dermatologia_patient vinculado (Mariana Alves);
    + contact "Rafael Pinto" +5511955554444 (sem vínculo) com 1 paciente.
  - 3 consultas cobrindo estados (start_at `at time zone 'America/Sao_Paulo'`):
    * VINCULADA (Mariana / Dra. Helena / tipo "Cauterização" → duração 60 → end_at materializado),
      status 'agendada', data futura (+7d 10:00), conversation_id vinculado — pra smoke de transição
      agendada→confirmada (notifica) + entrega de preparo via <entrega_preparo>.
    * (Rafael / Dr. Paulo / "Consulta") status 'confirmada', data +3d — pra smoke transição
      confirmada→realizada (silenciosa).
    * (Mariana / Dra. Helena / "Consulta") status 'agendada', MESMO profissional e horário sobreposto
      à 1ª? NÃO — usar profissional/horário que NÃO conflite no seed; o conflito é provado via POST no
      smoke (BLOCO C), não no seed.
F.3 — JwtFilter /api/dermatologia/ (se ainda não).
F.4-F.6 — git add EXPLÍCITO dos arquivos da SM (migration, profiles/dermatologia/**, OutboundService,
      JwtFilter, ProfilePromptContext, ProfileType.java, profile-type.ts, nav-config.tsx, frontend
      dermatologia-*, dermatologia-appointment-status.ts, testes, docs) + sanity (git status -s +
      diff --staged --stat + grep segredo eyJ../password/secret= + confirmar .env/.env.local/CONTEXT.md
      FORA da staging) + commit. Mensagem padrão (feat(camada-8): perfil dermatologia/Dermatologia
      (camada 8.x) com FUNDAÇÃO/BACKEND/FRONTEND/DECISÕES/VALIDAÇÃO contagem REAL/NÃO TOCADO/FECHAMENTO
      + trailer Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>). Tag fase-8.x-fechada (nº real
      confirmado no arranque).
F.7 — git push origin main + git push origin --tags. NUNCA --force.
F.8 — docker compose restart backend (ou ./scripts/run-local.sh) + aguardar /admin/me → 401
      missing_auth_header.
F.9 — Smoke E2E (token ES256 via POST {SUPABASE_URL}/auth/v1/token?grant_type=password):
  BLOCO A: auth — igorhaf17 → /admin/me → role=tenant_admin, profileId=dermatologia,
    productName=Dermatologia.
  BLOCO B: catálogo + guard — GET dermatologistas (2); GET tipos (3); GET pacientes; GET config + PUT;
    CRUD smoke de um tipo (com prep) + delete-em-uso 409; tenant de OUTRO perfil (nutri/floricultura)
    → /api/dermatologia/professionals → 403 forbidden_wrong_profile.
  BLOCO C: agenda + CONFLITO POR PROFISSIONAL [CHAVE do chassi] —
    - GET appointments (3); POST consulta nova (Dra. Helena, tipo Consulta, paciente da Mariana,
      slot livre) → 200, end_at = start_at + 30min materializado.
    - POST consulta SOBREPOSTA no MESMO profissional/horário → 409 conflict_slot (com detalhes).
    - POST consulta no MESMO horário com Dr. Paulo (OUTRO profissional) → 200 (paralelismo prova que
      o conflito é POR profissional, não por company).
    - POST fora do horário (06:00) → 400 outside_hours.
  BLOCO D: status — PATCH agendada→confirmada (consulta da Mariana vinculada) → 200 + msg outbound
    (confirmada, com tipo+profissional+data/hora; asserção casa o conteúdo EXATO — lição do fuso/
    substring); transição inválida (agendada→realizada) → 409 invalid_status_transition; cancelada
    notifica.
  BLOCO E: ENTREGA READ-ONLY DA NOTA DE PREPARO (a escapada desta SM) [CHAVE] —
    - <entrega_preparo>{appointment_id da consulta de Cauterização da Mariana} via handler/teste →
      o paciente RECEBE o texto EXATO de prep_instructions (asserção casa VERBATIM, char-a-char).
    - <entrega_preparo> de uma consulta de OUTRO contato (Rafael) na conversa da Mariana → BLOQUEADO
      (empty, nada entregue — barreira de contato).
    - <entrega_preparo> de uma consulta cujo tipo NÃO tem preparo (Consulta) → empty (nada entregue).
  BLOCO F: AGENDAMENTO via IA (2 modos) [CHAVE da trava clínica] —
    - <consulta_derma>{patient_id existente, Dra. Helena, Consulta, slot livre} via handler/teste →
      consulta criada; tag removida da msg.
    - <consulta_derma>{new_patient:{name}, ...} na conversa da Mariana → paciente cadastrado +
      consulta criada no mesmo turno.
    - PROVA DA TRAVA: segmentFor('dermatologia') contém "NUNCA dá diagnóstico" + "NÃO avalia a foto" +
      o bloco de sinal de alarme/urgência (assert no texto da persona).
  BLOCO G: regressão — os perfis anteriores intactos (smoke leve 1 endpoint cada);
    dermatologia → /api/nutri/* → 403; dermatologia → /api/dental/* → 403.
  BLOCO H: paridade — mvn test -Dtest=DermatologiaAppointmentStatusParityTest,ProfileTypeParityTest
    → verde.
  Cleanup smoke + restaurar seed pristine. mvn final: contagem REAL do Surefire.
F.10 — RELATÓRIO consolidado + DESTAQUE EXPLÍCITO:
  - "Nº perfil vertical — camada 8.x (confirmado no arranque)"
  - "CLONA a AGENDA do DENTAL/NUTRI (conflito por profissional + end_at materializado + paciente
     sub-entidade do contact) e inaugura procedure_types (tipos de atendimento com duração + preparo)"
  - "ESCAPADA: nota de preparo entregue READ-ONLY pela IA (texto verbatim, barreira de contato —
     espelho da entrega de plano do nutri); BLOCO E prova"
  - "BLOCO C prova o conflito POR profissional (overlap mesmo prof → 409; profs diferentes mesmo
     horário → OK)"
  - "TRAVA CLÍNICA: IA NUNCA diagnostica/avalia lesão/foto/recomenda; sinal de alarme → urgência sem
     diagnosticar; BLOCO F prova a persona"
  - "Seed usou at time zone America/Sao_Paulo + sufixo de ids novo (sem fuso/colisão)"
  - "as 5 tabelas criadas DENTRO da migration NN (lição os_config)"
  - PENDÊNCIAS: prontuário/dermatoscopia (dado sensível, cripto), foto da lesão/antes-depois,
    receituário, biópsia com resultado, pacote multi-sessão (estetica cobre), pagamento (Stripe),
    scheduler de auto-transição/lembrete + a dívida acumulada (webhook OFF, cliente real, olho humano
    sobre os verticais).

[REPORTAR]
Igual SMs anteriores. Incluir EXPLICITAMENTE:
- "ProfileType.DERMATOLOGIA adicionado (Nº perfil real, camada 8.x) — paridade ProfileType validada"
- "Paridade DermatologiaAppointmentStatus validada (status feminino: agendada/confirmada/realizada/
   cancelada/falta)"
- "Tenant igorhaf17 (ou o livre real) criado seguindo padrão GoTrue + Caddy/etc/hosts"
- "Chassi de agenda clínica clonado do DENTAL/NUTRI: conflito POR profissional re-verificado na
   transação + end_at materializado no INSERT"
- "Paciente é sub-entidade do contact (contact_id NOT NULL); snapshots na consulta"
- "ESCAPADA: dermatologia_procedure_types (tabela, duração por tipo + nota de preparo) — NÃO enum;
   escolha documentada no header"
- "Entrega READ-ONLY da nota de preparo (<entrega_preparo>, verbatim, barreira de contato) — espelho
   do EntregaPlanoHandler do nutri"
- "TRAVA DE SEGURANÇA CLÍNICA na persona + guarda de sinais de alarme: IA NUNCA diagnostica, NUNCA
   avalia lesão/mancha/pinta/foto, NUNCA recomenda tratamento, NUNCA opina 'é grave/é câncer'"
- "Tags: <consulta_derma> (agenda, 2 modos patient_id/new_patient) + <entrega_preparo> (entrega
   read-only) — distintas de TODAS as outras"
- "OutboundService ganhou maybeProcessConsultaDerma + maybeProcessEntregaPreparo (encadeados, perfil
   único)"
- "JwtFilter autentica /api/dermatologia/"
- "getNavForProfile('dermatologia') com branch próprio (não repetir o gap do floricultura)"
- "Paleta escolhida: <celeste|salvia|rosa-po> (registrar e por que)"
- "as 5 tabelas criadas DENTRO da migration NN (lição os_config); 5 tabelas no TRUNCATE do
   AbstractIntegrationTest"
- "Seed: at time zone America/Sao_Paulo + sufixo de ids novo (sem bug de fuso, sem colisão FK)"
- "Próximas fases: prontuário/dermatoscopia/foto da lesão/receituário/biópsia/pagamento/scheduler
   + fila de prioridade (webhook, cliente real, olho humano sobre os verticais)"
