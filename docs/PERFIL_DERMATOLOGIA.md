# Perfil Dermatologia (clínica dermatológica) — camada 8.11

Guia operacional do tenant **dermatologia** (`profile_id='dermatologia'`). A IA atende pacientes pelo
WhatsApp, identifica pelo telefone, AGENDA consultas (primeira consulta / retorno / procedimento) e
ENTREGA, read-only, a orientação de preparo que a dermatologista gravou. Tom técnico-acolhedor, sem
alarmismo — espelho do tom DENTAL/NUTRI.

É o **23º perfil vertical**. CLONA o chassi de agenda do **DentalBot/NutriBot** (profissional + conflito
por profissional + end_at materializado + paciente sub-entidade do contact) e inaugura **TIPOS DE
ATENDIMENTO como tabela** (duração + preparo próprios).

## TRAVA DE SEGURANÇA CLÍNICA (o coração da SM)

A IA **NUNCA** exerce ato médico:
- **NUNCA** dá diagnóstico; **NUNCA** avalia/classifica/interpreta lesão, mancha, pinta, acne, micose,
  queda de cabelo, unha ou qualquer sintoma; **NUNCA** recomenda tratamento/medicação/ácido/pomada/
  protetor/procedimento; **NUNCA** opina "é grave/é câncer/não é nada".
- Se o paciente mandar **FOTO** de lesão pedindo avaliação → a IA **NÃO avalia a foto**; acolhe e
  encaminha à consulta presencial.
- **GUARDA de sinais de alarme:** lesão que muda/sangra/cresce/coça-dói persistente/não cicatriza → a IA
  orienta buscar avaliação **com urgência** e oferece a primeira consulta, **SEM dar nome à condição** e
  **SEM dizer se é grave** (nem minimizar, nem alarmar).
- O único conteúdo clínico que a IA "entrega" é a **nota de preparo READ-ONLY** gravada pelo médico.

LGPD: `notes` (paciente/consulta) e `prep_instructions` são **administrativos**, não prontuário.

## Telas (sidebar "Dermatologia")

| Tela | Rota | O que faz |
|------|------|-----------|
| Dermatologistas | `/dashboard/dermatologia-professionals` | CRUD (conflito de agenda é por profissional). |
| Pacientes | `/dashboard/dermatologia-patients` | CRUD (sub-entidade do contact). |
| Tipos de Atendimento | `/dashboard/dermatologia-procedures` | A tela da escapada: nome + duração + nota de preparo. |
| Agenda | `/dashboard/dermatologia-appointments` | Consultas por status. |
| Configurações | `/dashboard/dermatologia-settings` | Horário (sem duração — vem do tipo). |

## ESCAPADA — Tipos de atendimento como TABELA (duração + preparo)

Dental/nutri tinham um `appointment_type` enum com **duração fixa** do config. Aqui o tenant **CADASTRA**
os tipos (`dermatologia_procedure_types`): cada um com SUA `duration_minutes` (5..480) e, opcionalmente,
uma **nota de preparo** (`prep_instructions` — orientação pré-procedimento, texto livre gravado pelo
médico). Decisão cravada: **tabela, não enum** (a duração varia por tipo; o preparo é por tipo,
cadastrável). A consulta referencia `procedure_type_id` e **snapshota** name + duration_minutes — alterar
o tipo depois NÃO altera consultas já criadas.

## Agenda (chassi dental/nutri)

Conflito **POR `professional_id`** (half-open, re-verificado dentro da transação) → 409 `conflict_slot`;
mesmo horário + profissional DIFERENTE → OK (paralelismo). `end_at` MATERIALIZADO no INSERT (start_at +
duration_minutes, em Java — date+interval não é IMMUTABLE). Janela `opens_at`..`closes_at` → 400
`outside_hours`. Paciente é **sub-entidade do contact** (snapshots patient_name/phone + professional_name
+ procedure_type_name + duration na consulta).

**Status** `DermatologiaAppointmentStatus` ↔ TS (parity, FEMININO): agendada→confirmada→realizada;
cancelada/falta. Notifica **confirmada** (com tipo+profissional+data/hora, defensivo, SEM conteúdo
clínico) e **cancelada**; agendada/realizada/falta silenciosos.

## Entrega READ-ONLY da nota de preparo

`<entrega_preparo>{appointment_id}` → o `EntregaPreparoHandler` busca o tipo da consulta e envia o
`prep_instructions` **VERBATIM** via `notifier.sendText` (NÃO passa pela IA — pra não ser reescrito), com
**BARREIRA DE CONTATO**: só entrega se o contato da consulta == contato da conversa (impede vazar preparo
de outro paciente). Sem preparo / contato diferente / consulta inexistente → não entrega. Espelho EXATO
da entrega de plano do nutri.

## Tags

**`<consulta_derma>`** (AGENDA — 2 modos, espelho `<consulta_nutri>`):
```json
{ "professional_id", "procedure_type_id", "date":"YYYY-MM-DD", "start_time":"HH:MM",
  "patient_id":"UUID|null", "new_patient":{"name","birth_date?"}|null, "notes" }
```
**`<entrega_preparo>`**: `{ "appointment_id":"UUID" }`.

Ambas têm namespace próprio, distinto de `<consulta_nutri>`/`<entrega_plano>` e de TODAS as outras.

## O que NÃO existe nesta fase

- Prontuário/laudo/dermatoscopia estruturada (dado sensível — fase futura com cripto); foto da lesão/
  antes-depois (bloqueador SERVICE_ROLE_KEY); receituário/prescrição; biópsia com resultado; pacote
  multi-sessão (estetica cobre); pagamento (Stripe #50); scheduler de auto-transição/lembrete;
  cancelamento pela IA (só painel).

## Notas técnicas

- Migration `55_dermatologia.sql` (5 tabelas: professionals, config, procedure_types, patients,
  appointments). A CHECK ACRESCENTA `'dermatologia'` preservando os 23 perfis. Entra por ÚLTIMO no
  `SCRIPTS` do `AbstractIntegrationTest` (sua CHECK tem os 24).
- Base de conhecimento (RAG): disponível como em todo perfil (item "Conhecimento" do nav + `{{knowledge}}`
  do PromptBuilder, sem gate).
- Guard `/api/dermatologia/**` → 403 `forbidden_wrong_profile`. Paleta `teal`. Tenant: `igorhaf22`.
