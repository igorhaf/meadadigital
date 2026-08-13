# PetBot — regras de negócio (pet, camada 7.8)

[← Catálogo](../05-nichos.md) · Chassi: A (agenda por profissional) + G (avô da sub-entidade) · Guia operacional: docs/PERFIL_PET.md · Migrations: 37, 94

## O negócio em 3 linhas

O tenant é um pet shop / clínica veterinária que vende banho, tosa, consulta e vacinação. O cliente
final é o **tutor** (um contact do WhatsApp) que pode ter N **animais** — a primeira sub-entidade de
cliente do Meada (`pet_animals.contact_id NOT NULL`). A IA identifica o tutor pelo telefone, oferece
os animais já cadastrados, respeita a **restrição de espécie** de cada serviço e agenda com um
profissional livre — cadastrando o animal na mesma conversa quando é a primeira vez.

## Jornada no WhatsApp (cenários)

1. **Feliz (tutor com animal):** tutor pede banho → a IA oferece os animais cadastrados (contexto tem
   até 10, com o último atendimento de cada), sugere serviço + profissional com slot livre (próximos
   7 dias), confirma animal + serviço + profissional + dia + hora → emite
   `<agendamento_pet>` modo `animal_id`. O `AgendamentoPetConfirmHandler` valida tudo, cria o
   agendamento (`agendado`, silencioso) e remove a tag.
2. **Primeira vez (tutor sem animal):** a IA pede nome + espécie (`cao|gato|outro`) + raça opcional e
   emite a tag no modo `new_animal` — o handler **cadastra o animal E agenda no mesmo turno**. Sem
   contato resolvido na conversa, o modo `new_animal` falha (best-effort: mensagem segue sem agendar).
3. **Lembrete de véspera (onda 1):** `PetReminderJob` (10h) manda texto fixo carinhoso ("Amanhã o
   Thor tem banho às 14h... Confirma?"). A resposta do tutor vira `<confirmacao_pet>` — a IA só
   REFLETE a decisão; cancelar libera o slot do profissional na hora.
4. **Exceções:** slot do profissional ocupado → 409 `conflict_slot` (o conflito volta no corpo);
   serviço "só para gatos" com um cão → 400 `species_mismatch`; fora da janela → 400
   `outside_hours`; sintoma descrito pelo tutor → a IA NÃO opina e orienta consulta presencial.

## Regras de negócio

### Transacionais (invariantes duras)

- **R1 — Conflito por profissional:** agendamento ativo (`agendado`/`confirmado`) do MESMO
  `professional_id` com janela sobreposta bloqueia a criação (query half-open
  `NOT (end_at <= :s OR start_at >= :e)`, re-verificada dentro da transação do INSERT em
  `PetAppointmentRepository`; índice parcial `idx_pet_appts_prof_active` só nos status bloqueantes).
  Profissionais diferentes no mesmo horário são paralelismo normal.
- **R2 — Species match:** `pet_services.species_restriction` não-nula exige `animal.species` igual
  (CHECK `cao|gato|outro` no schema + comparação no `PetAppointmentService.create`).
- **R3 — Janela de funcionamento:** início E fim (start + duração) dentro de
  `opens_at..closes_at` no fuso America/Sao_Paulo (hardcoded) → senão 400 `outside_hours`.
- **R4 — `end_at` materializado em Java no INSERT** (start + duração snapshot) — nunca coluna
  gerada (`timestamptz + interval` não é IMMUTABLE).
- **R5 — Sub-entidade protegida:** excluir animal/serviço/profissional com agendamento → 409
  `*_in_use` (FK `on delete restrict`); o caminho preferido é arquivar (`active=false`).
- **R6 — Só entidades ativas agendam:** profissional/serviço/animal inativo → 400 `inactive_*`.
- **R7 — Idempotência do lembrete por (agendamento, start_at):** `reminded_start_at` — remarcar
  REARMA o lembrete (marker ≠ novo start_at).

### Máquina de status

```
agendado ──→ confirmado ──→ realizado
   │             │────────→ falta
   └─────────────┴────────→ cancelado        (realizado/cancelado/falta = terminais)
```

| Transição | Quem pode | Notifica o tutor? |
|---|---|---|
| (criação) → agendado | IA (tag) ou painel (POST manual) | não |
| agendado → confirmado | painel · IA via `<confirmacao_pet>` (refletindo o tutor) | **sim** (serviço + animal + profissional + data/hora) |
| agendado/confirmado → cancelado | painel · IA via `<confirmacao_pet>` | **sim** (texto defensivo, convite a remarcar) |
| confirmado → realizado / falta | só painel | não |

Transição fora do grafo → 409 `invalid_status_transition` (`PetAppointmentStatus`).

### O que a IA PODE × NUNCA faz (travas da persona)

**PODE:** identificar o tutor, ofertar animais/serviços/slots, cadastrar animal (`new_animal`),
criar agendamento na confirmação final, refletir confirmação/cancelamento do tutor ao lembrete.
**NUNCA** (persona `ProfilePromptContext.PET` + instruções do `PetContextCache`): dá diagnóstico
veterinário, prescreve medicação ou recomenda tratamento (sintoma → consulta presencial); ignora a
restrição de espécie; confirma/cancela sem o tutor pedir; não há prontuário clínico (`notes` é
administrativo, LGPD).

### Tags de IA

| Tag | Quando a IA emite | Campos | Backend descarta/recalcula |
|---|---|---|---|
| `<agendamento_pet>` (modo 1) | confirmação final, animal já cadastrado | `professional_id`, `service_id`, `animal_id`, `date`, `start_time`, `notes` | snapshots (nome/preço/duração/tutor) vêm do catálogo e do contact, nunca da tag |
| `<agendamento_pet>` (modo 2) | confirmação final, primeiro animal do tutor | idem + `new_animal{name, species, breed?}` no lugar de `animal_id` | tutor = contact da conversa; espécie validada (`invalid_species` aborta o cadastro) |
| `<confirmacao_pet>` | tutor responde ao lembrete ou pede desmarcar | `appointment_id`, `decisao: confirmado\|cancelado` | BARREIRA DE CONTATO (só o dono do agendamento); máquina de status valida |

Handlers best-effort: qualquer falha → `Optional.empty()` + warn, a mensagem segue sem efeito; o
`OutboundService` remove a tag antes de enviar.

### Validações e erros

| reason | HTTP | Significado de negócio | Cenário |
|---|---|---|---|
| `forbidden_wrong_profile` | 403 | tenant de outro perfil em `/api/pet/**` | `PetProfileGuard.requirePet` |
| `conflict_slot` | 409 | profissional ocupado no horário | overlap re-checado na transação (traz o conflito) |
| `species_mismatch` | 400 | serviço restrito a outra espécie | banho "só gatos" para um cão |
| `outside_hours` | 400 | fora da janela do tenant | início ou fim fora de opens/closes |
| `inactive_professional` / `inactive_service` / `inactive_animal` | 400 | entidade arquivada | agendar com animal arquivado |
| `professional_not_found` / `service_not_found` / `animal_not_found` / `appointment_not_found` | 404 | id inexistente no tenant | — |
| `animal_in_use` / `service_in_use` / `professional_in_use` | 409 | DELETE com agendamentos | preferir arquivar |
| `contact_not_found` | 404 | tutor inexistente ao criar animal | painel com contact inválido |
| `invalid_species` | 400 | espécie fora de `cao\|gato\|outro` | CRUD de animal/serviço |
| `invalid_status` / `invalid_status_transition` | 400 / 409 | status desconhecido / transição proibida | PATCH de status |
| `invalid_date` / `invalid_time` / `invalid_hours` | 400 | datas/horas malformadas, opens ≥ closes | query params e config |

### Notificações ao cliente

Envia (via `PetAppointmentNotifier`, texto fixo defensivo, nunca gerado pela IA): **confirmado**,
**cancelado** e o **lembrete de véspera**. Silencioso: `agendado` (evita ruído antes do aceite),
`realizado`, `falta` (ninguém recebe "sermão") e agendamento manual sem conversa (job marca sem
envio — não revarre). O lembrete NÃO passa pela IA (trava clínica intacta, é administrativo).

## Dados e snapshots

- `pet_professionals` / `pet_services` — catálogo; `duration_minutes` CHECK 15..240;
  `price_cents` nullable (sem preço = a IA não expõe valor); `species_restriction` CHECK.
- `pet_animals` — `contact_id NOT NULL` (tutor), `species`/`sex` CHECK hardcoded, `birth_year`
  CHECK 1990..2030, `active=false` = arquivado sem perder histórico.
- `pet_appointments` — INSERT só pelo backend (RLS: tenant SELECT/UPDATE); **snapshots**
  `tutor_name/tutor_phone`, `animal_name/animal_species`, `professional_name`,
  `service_name/service_category`, `price_cents`, `duration_minutes` — mudar catálogo/animal depois
  não altera o passado; `reminded_start_at` (onda 1).
- `pet_config` — 1:1, defaults 09:00/19:00/buffer 0; `reminder_enabled` default true (mig 94).
- **Cache:** `PetContextCache` (Caffeine, TTL **20s**, key `companyId:contactId`), invalidado por
  company em TODA mutação de profissional/serviço/animal/config/agendamento. Slots ofertados em
  granularidade fixa de 30min, máx. 6 por profissional/dia, 7 dias.

## Features de onda (backlog implementado — migration 94)

- **#1 Lembrete de véspera + confirmação:** `PetReminderJob` cron `${pet.reminder-cron:0 0 10 * * *}`
  varre `agendado`/`confirmado` com start AMANHÃ (America/Sao_Paulo); toggle `reminder_enabled`
  (default ON; ausência de linha de config = ligado); idempotência `reminded_start_at` (remarcar
  rearma); sem canal → marca sem envio; fecha o loop via `<confirmacao_pet>` com barreira de
  contato. O contexto ganhou o bloco "AGENDAMENTOS FUTUROS DO TUTOR" (até 5, com instrução da tag).

## O que NÃO existe (limites honestos)

Prontuário/histórico clínico, carteira de vacinas, prescrição, internação, pacote/assinatura de
banho, foto do pet (bloqueador SERVICE_ROLE_KEY), pagamento online (gateway #50), auto-transição de
status (realizado/falta são sempre ação humana — não há job além do lembrete). `buffer_minutes`
existe no schema, mas não foi verificado uso na validação de slot (a granularidade do contexto é
fixa em 30min).
