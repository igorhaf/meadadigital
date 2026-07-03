>>> JÁ IMPLEMENTADO — perfil dental, camada 7.4, migration 33_dental.sql. Prompt de nicho
>>> RETROATIVO, formato T5. Fonte: CLAUDE.md seção Perfil Dental + migration 33 +
>>> docs/PERFIL_DENTAL.md. Documenta o que JÁ EXISTE no código — não é pedido de
>>> implementação. Serve de molde retroativo e registro do real.

[TAREFA — PERFIL DENTAL / DentalBot (camada 7.4) — RETROATIVO]

Documentar (retroativo) o perfil vertical DENTAL já implementado no projeto MEADA
(/home/igorhaf/meada). Quarto perfil vertical real (sushi 7.1, legal 7.2,
restaurant 7.3, dental 7.4 — 5º contando generic). O tenant dental (`profile_id='dental'`) é um
produto de CLÍNICA ODONTOLÓGICA dentro do mesmo dashboard Meada. NÃO inventar: tudo abaixo reflete
o que está em migration 33_dental.sql, src/main/java/com/meada/profiles/dental/,
frontend/profiles/dental/ + páginas em app/(protected)/dashboard/, e docs/PERFIL_DENTAL.md.

[CONTEXTO]
O tenant dental acessa seu subdomínio e vê o produto "DentalBot". A IA atende PACIENTES via
WhatsApp em linguagem natural, com tom técnico-acolhedor (empatia com quem tem medo de dentista):
identifica o paciente pelo telefone, informa as próximas consultas e AGENDA novas. O tenant gerencia
pacientes e a agenda de consultas pelo painel e muda o status conforme o atendimento.

>>> TRAVA CLÍNICA (o coração do perfil, inegociável) <<<
- A IA NUNCA dá diagnóstico, NUNCA recomenda procedimento, NUNCA discute sintoma. Para qualquer
  dúvida clínica (dor, sintoma, recomendação), encaminha ao dentista:
  "Para isso, vou pedir que o dentista avalie. Posso agendar uma consulta?".
- CANCELAMENTO POR IA É BLOQUEADO: se o paciente pede pra desmarcar, a IA encaminha pro consultório
  ("vou avisar o dentista, ele entra em contato pra confirmar o cancelamento"). A tag só serve para
  AGENDAR — o cancelamento é AÇÃO HUMANA na agenda do painel (espelho do "olho humano" do dental).
- LGPD: `dental_patients.notes` e `dental_appointments.notes` são ADMINISTRATIVOS (preferências de
  horário, contato), NÃO clínicos. Dados clínicos (prontuário, diagnóstico, alergia, odontograma,
  plano de tratamento) ficam para fase futura, com criptografia at-rest e log de acesso por usuário.
  `type` da consulta é texto livre administrativo ("Limpeza", "Avaliação"), nunca recomendação.

EVOLUÇÃO ESTRUTURAL (em relação aos perfis anteriores):
- Modelo análogo a sushi/legal/restaurant: `dental_patients` (catálogo, ~ legal_clients) +
  `dental_appointments` (consultas, ~ table_reservations) + `dental_clinic_config` (duração/horário,
  1:1 com company).
- AGENDA COM CONFLITO POR COMPANY: 1 dentista por tenant nesta SM — o conflito de horário é por
  consultório (company), NÃO por dentista. Não há `dentist_id`; fase futura adiciona dentist_id e
  muda o WHERE da checagem. (Difere do salon, que já checa por profissional.)
- PACIENTE SUB-ENTIDADE DO CONTACT: `dental_patients.contact_id` (nullable) liga o paciente ao
  contato do WhatsApp; a IA resolve contact → patient pelo telefone. O badge "vinculado" no painel
  indica esse vínculo.
- TAG `<consulta>` em texto livre (não tool calling — mesma restrição responseSchema do sushi): a IA
  negocia em linguagem natural e, na confirmação, emite a tag; o backend parseia via regex.
- `DentalContextCache` TTL 30s: contexto dinâmico injetado no prompt (dados do paciente + próximas
  consultas + slots livres + instruções de agendamento), keyed por (companyId, contactId).

DECISÕES CRAVADAS (reais):
1. Conflito por COMPANY (1 dentista/tenant), não por dentista. Sem `dentist_id` nesta SM.
2. `end_at` NÃO é coluna gerada — é MATERIALIZADO no INSERT (start_at + duration_minutes). Lição
   SM-D: timestamptz + interval não é IMMUTABLE (depende do timezone da sessão p/ DST), e Postgres
   exige expressão immutable em GENERATED.
3. `duration_minutes` em dental_appointments é SNAPSHOT do config no momento do agendamento —
   alterar o config NÃO altera consultas já criadas.
4. Slot 30min, duração 30min FIXOS (configurável: `dental_clinic_config.duration_minutes` default
   30). Buffer = 0 nesta SM. Janela `opens_at`..`closes_at` (default 08:00–18:00), validada no fuso
   America/Sao_Paulo (HARDCODED — pendência conhecida).
5. Persona DENTAL (`ProfilePromptContext.DENTAL`) — tom técnico-acolhedor, empatia com medo de
   dentista, NUNCA diagnóstico — INTACTA da fundação multi-perfil (SM-A); só ganhou contexto
   dinâmico via DentalContextCache.
6. Cancelamento por IA bloqueado (encaminha pro tenant — risco); a tag só agenda.
7. POST manual pelo tenant cria consulta sem `conversation_id` (sem WhatsApp) — não notifica (sem
   canal). NÃO há DELETE de consulta (histórico; "remover" = status cancelada). Excluir paciente é
   bloqueado se tiver consultas (proteção de histórico).

NÃO TEM nesta SM (registrado pra não inventar): odontograma, plano de tratamento, evolução por
sessão, TUSS, anamnese, alergias estruturadas, histórico médico, receituário, atestado, pagamento,
`dentist_id`. Sem foto/anexo (bloqueador SERVICE_ROLE_KEY). Sem scheduler de auto-transição
(consulta passada não vira "realizada" sozinha). Sem lembrete automático ("sua consulta é amanhã").
Textos de notificação fixos nesta versão. Fases futuras.

[FUNDAÇÃO — migration 33_dental.sql]
Convenções (padrão das migrations 30/31/32): RLS enable + force; policies do tenant via
app.company_id(); grants authenticated + service_role. Três tabelas exclusivas do perfil 'dental'.

- ALTER companies CHECK aceitar 'dental' (preservando os perfis anteriores).
- `dental_patients` — pacientes (catálogo, ~ legal_clients):
  id, company_id (FK companies on delete restrict), name (NOT NULL, length 1..200), email, phone,
  document (CPF sem máscara), birth_date (date, idade client-side), contact_id (FK contacts on
  delete set null — vínculo WhatsApp), notes (ADMINISTRATIVO), created_at, updated_at. Índices por
  (company_id, name) e (company_id, contact_id) where contact_id is not null. RLS com policies
  select/insert/update/delete via app.company_id(); grants select/insert/update/delete a
  authenticated + all a service_role.
- `dental_clinic_config` — duração + horário do consultório (1:1 com company, PK = company_id on
  delete cascade): duration_minutes (NOT NULL default 30, CHECK 15..240), buffer_minutes (NOT NULL
  default 0, CHECK >= 0), opens_at (time default '08:00'), closes_at (time default '18:00'),
  timestamps. Ausente → defaults (30/0/08:00/18:00). RLS select/insert/update via app.company_id();
  grants select/insert/update a authenticated + all a service_role.
- `dental_appointments` — consultas (INSERT pelo BACKEND via service_role — IA ConsultaConfirmHandler
  OU tenant via POST manual; tenant só SELECT/UPDATE, sem policy authenticated de insert):
  id, company_id (FK on delete restrict), patient_id (FK dental_patients on delete restrict),
  conversation_id (FK conversations on delete set null — nullable; consulta manual não tem WhatsApp),
  start_at (timestamptz), duration_minutes (SNAPSHOT do config), end_at (timestamptz NOT NULL,
  MATERIALIZADO no INSERT = start_at + duration_minutes — NÃO coluna gerada), type (NOT NULL, length
  1..100, texto livre administrativo), status (NOT NULL default 'agendada', CHECK in
  ('agendada','confirmada','realizada','cancelada','falta')), notes (ADMINISTRATIVO), created_at,
  status_updated_at. Índices: (company_id, status, start_at); índice CRÍTICO de conflito
  (company_id, start_at) where status in ('agendada','confirmada') — só status bloqueantes por
  company; (patient_id, start_at desc). RLS select/update via app.company_id() (sem insert);
  grants select/update a authenticated + all a service_role.

[BACKEND]
src/main/java/com/meada/profiles/dental/

- Status hardcoded materializado: `AppointmentStatus` (enum Java) ↔ `appointment-status.ts` (const
  TS), `AppointmentStatusParityTest` garante a paridade. Transições cravadas:
  agendada → confirmada, cancelada ; confirmada → realizada, cancelada, falta ;
  realizada/cancelada/falta → terminal. Transição inválida → 409 invalid_status_transition.
- Conflito transacional (igual MesaBot): `DentalAppointmentRepository.findConflict` é um SELECT com
  a janela materializada (NOT (end_at <= newStart OR start_at >= newEnd)), só status bloqueantes
  (agendada/confirmada), POR COMPANY (não há dentist_id). O `insertAppointment` RE-VERIFICA o
  conflito DENTRO da transação antes do INSERT (fecha a janela de race entre o cache da IA e a
  persistência). Conflito → AppointmentConflict / 409 conflict_slot com detalhes de quem ocupa
  (de que horas a que horas).
- `DentalAppointmentService` / `DentalAppointmentController` (POST manual sem conversation_id; PATCH
  de status validando transição; GET agrupado por dia/status). `DentalPatientService` /
  `DentalPatientController` (CRUD; delete bloqueado se houver consultas — proteção de histórico).
  `DentalClinicConfigService` / `DentalClinicConfigController` (GET com fallback defaults + PUT).
- Tag `<consulta>` por mensagem LIVRE: a persona instrui a IA a terminar a confirmação com
  `<consulta>{"date":"YYYY-MM-DD","start_time":"HH:MM","type":"...","notes":"..."}</consulta>`.
  `ConsultaConfirmHandler` parseia via regex, resolve o paciente pelo contato da conversa
  (dental_patients.contact_id) e cria a consulta. Se o paciente não está identificado → retorna
  empty + warn (a IA não devia emitir a tag sem paciente). O `OutboundService` REMOVE a tag antes de
  enviar a mensagem ao cliente via `maybeProcessDentalAppointment` (encadeado após sushi/restaurant
  — perfil é único, só um age).
- Contexto dinâmico: `DentalContextCache` (Caffeine TTL 30s, keyed por (companyId, contactId)) —
  dados do paciente identificado + próximas consultas + slots livres dos próximos 14 dias +
  instruções de agendamento. Invalidação por company ao mutar paciente/consulta/config.
- Notificações: só **confirmada** (com data/hora) e **cancelada** notificam o paciente
  (`DentalAppointmentNotifier`, texto DEFENSIVO sem promessa clínica). agendada/realizada/falta são
  silenciosos (quem furou não recebe sermão). Notificação só se houver vínculo WhatsApp.
- Guard: `DentalProfileGuard` (403 forbidden_wrong_profile). `JwtAuthenticationFilter` autentica
  `/api/dental/**` (além de /admin/**, /api/sushi/**, /api/legal/**, /api/restaurant/**).

[FRONTEND]
frontend/profiles/dental/ + app/(protected)/dashboard/

- `getNavForProfile('dental')` injeta o grupo "Consultório" (Pacientes + Agenda + Configurações).
- Páginas:
  * `/dashboard/patients` — CRUD de pacientes (nome obrigatório; telefone/email/CPF/nascimento/notes
    opcionais), badge "vinculado" (vínculo WhatsApp), busca por nome/telefone/email/CPF, detalhe com
    a lista de consultas, exclusão bloqueada se houver consultas.
  * `/dashboard/appointments` — agenda agrupada por dia em ordem de horário, filtro por status; nova
    consulta manual (paciente, data, hora, tipo com sugestões, notes); conflito mostra quem ocupa e
    o intervalo; detalhe + mudança de status (confirmar/cancelar notificam; realizada/falta
    silenciosos).
  * `/dashboard/dental-settings` — duração, buffer, horário de funcionamento (mudanças afetam só
    consultas futuras).
- Tipos + status: `dental-types.ts`, `appointment-status.ts` (espelho do enum Java, parity test).

[DOCS]
- CLAUDE.md: seção "## Perfil Dental (DentalBot, camada 7.4)" — trava clínica, evolução estrutural
  (conflito por company, paciente sub-entidade, tag <consulta>, DentalContextCache TTL 30s), LGPD,
  status/transições, notificações, NÃO TEM.
- docs/PERFIL_DENTAL.md: guia operacional do tenant (cadastrar pacientes; configurar consultório;
  agenda de consultas; como a IA atende; LGPD administrativo-não-clínico; limitações honestas).

[TESTES BACKEND]
src/test/java/com/meada/profiles/dental/
- `AppointmentStatusParityTest` — paridade do enum AppointmentStatus (Java) ↔ appointment-status.ts.
- `ProfileTypeParityTest` — ProfileType (Java) ↔ profile-type.ts (inclui 'dental').
- `appointments/DentalAppointmentServiceTest` — criação, conflito transacional, end_at
  materializado, snapshot de duration, transições de status, notificações.
- `appointments/DentalAppointmentControllerIntegrationTest` — POST manual, PATCH status (409
  invalid_status_transition), conflito (409 conflict_slot), guard wrongProfile (403).
- `patients/DentalPatientControllerIntegrationTest` — CRUD de pacientes, delete bloqueado em uso.
- (Contagem REAL do Surefire vem do build `mvn -B clean test`; não hardcodar.)

[CONSTRAINTS DUROS]
- Migration única (33_dental.sql). Sem foto/anexo (bloqueador SERVICE_ROLE_KEY).
- Cliente NÃO é entidade do core de outro tipo — paciente é catálogo próprio sub-entidade do contact
  (contact_id nullable); a IA resolve contact → patient pelo telefone.
- Conflito de horário POR COMPANY (1 dentista/tenant); sem dentist_id nesta SM.
- end_at MATERIALIZADO no INSERT (não generated). duration_minutes é SNAPSHOT do config.
- TRAVA CLÍNICA: IA nunca diagnostica/recomenda/discute sintoma; cancelamento por IA bloqueado.
- LGPD: notes administrativo, não clínico; type é etiqueta administrativa.
- Status hardcoded (parity). Tag <consulta> em texto livre (não tool calling).
- Cache de contexto TTL 30s + invalidação por company em toda mutação.
- Só confirmada/cancelada notificam (texto defensivo sem promessa clínica).
- Fuso fixo America/Sao_Paulo (pendência). Sem scheduler de auto-transição. Sem DELETE de consulta.
- NÃO mexer em outros perfis nem em system-template.txt. Webhook OFF.

[PASSO FINAL — resumido (retroativo)]
Perfil já fechado na camada 7.4. O fechamento original envolveu: migration 33_dental.sql aplicada;
ProfileType.DENTAL no enum + const TS + paridade; JwtFilter autenticando /api/dental/**; tenant de
teste dental (igorhaf4, perfil dental) provisionado via GoTrue + Caddy/etc/hosts; seed de pacientes/
config/consultas; build `mvn -B clean test` verde (gate Surefire); commit feat(camada-7.4) + tag
fase-7.4-fechada; push origin main + tags; smoke E2E (auth → /admin/me profileId=dental; CRUD de
paciente; guard 403 pra outro perfil; tag <consulta> via handler → consulta 'agendada'; conflito →
409; PATCH de status com notificação confirmada/cancelada).

[REPORTAR]
Documento retroativo — sem execução. Pontos a destacar do real:
- "ProfileType.DENTAL (camada 7.4) — 4º perfil vertical real"
- "Paridade AppointmentStatus e ProfileType validadas"
- "TRAVA CLÍNICA: IA nunca diagnostica/recomenda; cancelamento por IA bloqueado (ação humana)"
- "Conflito por COMPANY (1 dentista/tenant); sem dentist_id (fase futura)"
- "end_at materializado no INSERT (lição timestamptz+interval); duration_minutes snapshot do config"
- "Paciente sub-entidade do contact (contact_id) — IA resolve pelo telefone"
- "Tag <consulta> em texto livre (não tool calling); ConsultaConfirmHandler; OutboundService
  maybeProcessDentalAppointment remove a tag antes de enviar"
- "DentalContextCache TTL 30s (keyed por company+contact) + invalidação em toda mutação"
- "Só confirmada/cancelada notificam (texto defensivo sem promessa clínica)"
- "LGPD: notes administrativo, não clínico; sem prontuário/odontograma (fase futura com cripto)"
- "getNavForProfile('dental') = grupo Consultório (Pacientes/Agenda/Configurações)"
