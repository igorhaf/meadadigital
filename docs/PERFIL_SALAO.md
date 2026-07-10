# SalãoBot — guia operacional do salão (camada 7.5)

O SalãoBot é o produto do Meada para salões de beleza. Seus clientes falam pelo WhatsApp; a IA
atende com tom acolhedor, oferece os serviços do salão e os profissionais disponíveis, verifica a
agenda e marca o horário. Você acompanha tudo pela agenda e muda o status conforme o atendimento.

## 1. Profissionais (`/dashboard/professionals`)

- **Novo profissional:** nome (obrigatório), especialidade (texto livre: "Cabeleireira",
  "Manicure", "Esteticista") e observações.
- **Ativo/inativo:** o checkbox inline tira/coloca o profissional na disponibilidade que a IA
  enxerga. Um profissional inativo não é oferecido em novos agendamentos.
- **Excluir:** bloqueado se o profissional tiver agendamentos (proteção de histórico) — desative-o.
- **Por que isso importa:** a agenda é **por profissional**. Dois clientes podem marcar o mesmo
  horário com profissionais diferentes — cada um tem sua própria agenda.

## 2. Serviços (`/dashboard/salon-services`)

- **Novo serviço:** nome (obrigatório), categoria (livre: "Cabelo", "Unha", "Pele"), **duração**
  em minutos (15–480) e **preço opcional** (deixe vazio para a IA não expor o preço).
- A duração é importante: ela define quanto tempo o profissional fica ocupado naquele agendamento.
- **Ativo/inativo** e **excluir** (bloqueado se houver agendamento) funcionam como nos profissionais.

## 3. Agenda (`/dashboard/salon-appointments`)

- **Lista por dia**, com filtro por **status** e por **profissional**.
- **Novo agendamento (manual):** escolha o profissional, o serviço (mostra a duração), data, hora,
  o nome do cliente (telefone opcional) e observações. Se aquele profissional já estiver ocupado no
  horário, o sistema recusa e mostra **quem** ocupa e **de que horas a que horas** — você pode
  escolher outro profissional ou outro horário.
- **Detalhe + status:** clique num agendamento para ver os dados e mudar o status. Ao **confirmar**
  ou **cancelar**, o cliente é notificado automaticamente (se veio do WhatsApp); a notificação de
  confirmação inclui o nome do profissional. Marcar como realizado ou falta é silencioso.

## 4. Configurações (`/dashboard/salon-settings`)

- **Horário de funcionamento** (abre/fecha) e **intervalo entre agendamentos**. Mudanças afetam
  agendamentos **futuros**.

## 5. Como a IA atende

- A IA conhece os serviços ativos (com preço quando informado), os profissionais ativos, o histórico
  do cliente e os horários livres de cada profissional nos próximos 7 dias.
- Quando o cliente pede um serviço, ela sugere os profissionais disponíveis para aquele horário (se
  houver mais de um) e confirma serviço + profissional + dia + hora antes de marcar.
- **A IA tem tom acolhedor e SEM julgamento:** nunca recomenda um serviço que o cliente não pediu,
  nunca opina sobre a aparência do cliente, e não promete resultado estético.

## LGPD

- As **observações** (do profissional e do agendamento) são **administrativas**. Não registre dado
  sensível ali.

## Limitações conhecidas (honestas)

- **Cliente não é cadastro formal** nesta versão — o histórico vem do contato do WhatsApp. Salão tem
  alta rotatividade; um cadastro de clientes entra em fase futura se fizer sentido.
- **Sem comissão de profissional, sem pagamento, sem programa de fidelidade/cashback.**
- **Sem foto do trabalho** (bloqueador técnico de Storage), **sem estoque de produtos**, **sem
  multi-loja**.
- **Sem lembrete automático** e **sem auto-transição** de status.
- **Conflito por profissional** considera o salão com 1 sala/cadeira por profissional. Se um
  profissional puder atender em paralelo, é fase futura.
- **Fuso fixo** America/Sao_Paulo.
- **Risco aceito no MVP:** se a IA prometer um horário e o backend detectar conflito ao gravar, o
  agendamento não é criado — contorne manualmente. É raro.

## Onda 1 do backlog (2026-07 — FEATURES_SUGERIDAS_SALON #1/#7, migration 90)

- **Lembrete de véspera (#1, `SalonReminderJob`, cron 9h30):** agendamentos `agendado`/`confirmado`
  com start_at AMANHÃ (America/Sao_Paulo) recebem mensagem FIXA pela conversa ("seu horário com
  {profissional} é amanhã às {hora} — confirma?"); a resposta cai no fluxo inbound normal (a IA
  confirma/remarca pela tag `<agendamento>`). Idempotência por (agendamento, start_at) via
  `reminded_start_at` — REMARCAR rearma. Sem canal (POST manual) → marca sem envio. Toggle
  `reminder_enabled` (default LIGADO) em Configurações.
- **Auto-transição opt-in (#7):** com `auto_complete_enabled` (default DESLIGADO), confirmado com
  `end_at` no passado vira `realizado` automaticamente (transição válida da máquina; realizado é
  SILENCIOSO). `agendado` passado NÃO vira falta (falta só existe a partir de confirmado e é
  julgamento humano). Toggle em Configurações.
