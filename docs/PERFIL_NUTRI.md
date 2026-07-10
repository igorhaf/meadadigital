# NutriBot — guia operacional do consultório de nutrição (camada 8.0)

O NutriBot é o produto do Meada para nutricionistas e consultórios de nutrição. Seus pacientes falam
pelo WhatsApp; a IA atende com tom acolhedor e profissional, agenda consultas e entrega o plano
alimentar que você gravou. Você gerencia nutricionistas, pacientes, planos e a agenda pelo painel.

> **A linha que a IA NÃO cruza (segurança clínica):** plano alimentar é conduta privativa do
> nutricionista (CFN/CRN). A IA **não** cria, calcula, monta, adapta nem resume plano; **não** dá
> caloria, macro ou porção; **não** responde "posso comer X?", "isso engorda?" ou perguntas sobre
> patologia/suplementação. Para qualquer dúvida nutricional, ela orienta agendar consulta. E há uma
> **guarda permanente**: diante de sinais de transtorno alimentar (compulsão, purga, contagem
> obsessiva, peso-meta extremo, sofrimento com comida/corpo) ela acolhe sem reforçar, não dá números
> e encaminha a você. O valor da IA é **agendar** e **entregar o seu plano** — nada além disso.

## 1. Nutricionistas (`/dashboard/nutri-professionals`)

- **Novo profissional:** nome, especialidade (ex.: "nutrição clínica", "nutrição esportiva"), CRN e
  observações.
- **Ativo/inativo** e **excluir** (bloqueado se houver consultas — desative-o).

## 2. Pacientes (`/dashboard/nutri-patients`)

- Cada paciente pertence a um **contato** do WhatsApp. Um contato (ex.: um responsável) pode ter mais
  de um paciente (ex.: ele e um filho).
- **Novo paciente:** escolha o contato, informe nome, objetivo (texto livre — emagrecimento, ganho de
  massa, manutenção…), restrições alimentares (texto livre administrativo), data de nascimento e
  observações.
- **Arquivar** (preferido a excluir): tira o paciente da lista ativa sem perder o histórico. **Excluir**
  é bloqueado se o paciente tiver consultas ou planos.

## 3. Planos alimentares (`/dashboard/nutri-plans`)

- Selecione um paciente para ver e gerenciar os planos dele.
- **Novo plano:** dê um título, escreva o **plano completo no campo de texto (markdown livre)** — é
  exatamente esse texto que a IA entregará ao paciente, sem alterar uma vírgula. Informe a vigência
  (início/fim, opcionais), o profissional responsável (opcional) e marque "ativo".
- **Apenas um plano fica ativo por paciente.** Ao criar um novo plano ativo (ou ao reativar um
  arquivado), o plano ativo anterior é arquivado automaticamente.
- **Arquivar / Ativar:** controle qual plano está vigente. O histórico de planos antigos é preservado.

## 4. Agenda (`/dashboard/nutri-appointments`)

- **Lista por dia** com filtro de status e de profissional.
- **Nova consulta (manual):** escolha o paciente, o profissional, o tipo (primeira consulta / retorno /
  avaliação), a data e a hora. Se o profissional já tiver consulta no horário, o sistema avisa o
  conflito.
- **Status:** `agendado → confirmado/cancelado`; `confirmado → realizado/cancelado/falta`. Em
  **confirmar** e **cancelar**, o paciente é notificado automaticamente (se veio do WhatsApp).

## 5. Configurações (`/dashboard/nutri-settings`)

- **Horário de funcionamento** e intervalo entre consultas.

## 6. Como a IA atende

A IA conhece os nutricionistas, os pacientes de cada contato (com objetivo e se há plano ativo) e os
horários livres. No WhatsApp ela:

1. Identifica o contato pelo telefone e o paciente (oferece os já cadastrados ou cadastra um novo com
   nome + objetivo).
2. Agenda a consulta no horário livre do profissional.
3. Quando o paciente pede o plano e existe um plano **ativo**, ela **entrega o texto exato** que você
   gravou — como uma mensagem própria, sem reescrever nem comentar. Se não há plano ativo, ela oferece
   agendar uma consulta. E ela só entrega o plano de um paciente do **próprio contato** da conversa.

## 7. O que o NutriBot NÃO faz (ainda)

Plano estruturado em refeições/porções, cálculo de calorias/macros/TMB, tabela TACO, antropometria com
gráfico de evolução, prescrição de suplemento, anamnese clínica estruturada, foto, pagamento online e
bioimpedância. Esses são temas de fases futuras — e o cálculo nutricional personalizado permanece, por
segurança, conduta exclusiva do nutricionista.

## Onda 1 do backlog (2026-07 — FEATURES_SUGERIDAS_NUTRI #1/#2/#5, migration 99)

- **Lembrete + confirmação de consulta (#1, `NutriReminderJob`, cron 10h40):** véspera das
  consultas agendado/confirmado; a resposta cai na IA, que emite
  `<confirmacao_nutri>{appointment_id, decisao}` (confirmado|cancelado) com BARREIRA DE CONTATO
  — o contexto ganhou o bloco "CONSULTAS FUTURAS DESTE CONTATO" + a tag (mantendo a instrução
  de NÃO falar de dieta/plano). Idempotência por (consulta, start_at). Toggle (ON).
- **Régua de retomada (#2, OPT-IN default OFF — lição Baileys):** paciente ATIVO sem consulta
  futura e com a última REALIZADA além de `reengagement_days` (default 30) recebe UM convite
  gentil por ciclo (`reengagement_sent_at` no paciente, re-armado por nova realizada).
- **Auto-transição (#5, default ON):** confirmado com end_at vencido (folga 2h) → `realizado`,
  silencioso — destrava régua/relatórios. `agendado` passado não vira falta (julgamento humano).
- Trava clínica intacta em tudo: os disparos são logística de agenda, sem conteúdo nutricional.
