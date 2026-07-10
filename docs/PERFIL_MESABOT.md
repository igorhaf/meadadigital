# MesaBot — guia operacional do restaurante (camada 7.3)

O MesaBot é o produto do Meada para restaurantes que trabalham com reservas. Seus clientes pedem
mesa pelo WhatsApp em linguagem natural; a IA verifica a disponibilidade, confirma dia/hora/mesa/
pessoas e cria a reserva. Você acompanha tudo pela agenda e muda o status conforme o atendimento.

## 1. Cadastrar mesas (`/dashboard/tables`)

- **Nova mesa:** nome/identificador (obrigatório, ex.: "Mesa 1", "Varanda 3"), capacidade (lugares,
  1–50) e observações (opcional).
- **Disponível:** o checkbox inline tira/coloca a mesa na disponibilidade que a IA enxerga. Uma mesa
  indisponível não é oferecida em novas reservas.
- **Excluir:** bloqueado se a mesa já tem reservas (proteção de histórico) — desative-a em vez disso.

## 2. Configurar o restaurante (`/dashboard/restaurant-settings`)

- **Duração da reserva:** quanto tempo uma reserva ocupa a mesa (padrão 2h). Uma reserva às 20h
  ocupa a mesa até 22h.
- **Intervalo entre reservas:** folga extra entre uma reserva e a próxima na mesma mesa (0 por padrão).
- **Horário de funcionamento:** abre/fecha. A reserva inteira (início → início + duração) tem de
  caber nessa janela; fora disso é recusada.
- **Importante:** mudanças afetam apenas reservas **futuras** — reservas já criadas mantêm a duração
  do momento em que foram feitas.

## 3. Agenda de reservas (`/dashboard/reservations`)

- **Lista por dia:** as reservas vêm agrupadas por data, em ordem de horário. Filtre por status
  (pendente/confirmada/realizada/cancelada/não compareceu).
- **Nova reserva (manual):** escolha a mesa, data, hora, nº de pessoas e o nome do cliente (telefone
  e observações são opcionais). Se o horário estiver ocupado naquela mesa, o sistema recusa e mostra
  **quem** ocupa o slot e **de que horas a que horas**.
- **Detalhe + status:** clique numa reserva para ver os dados e mudar o status. Ao confirmar ou
  cancelar, **o cliente é notificado automaticamente** (se a reserva veio do WhatsApp). Marcar como
  realizada ou "não compareceu" é silencioso (não manda mensagem).

## 4. Como a IA atende

- A IA conhece as mesas disponíveis e as reservas já marcadas dos próximos 7 dias. Quando o cliente
  pede uma reserva, ela verifica a disponibilidade, e — se o horário pedido estiver ocupado — oferece
  uma alternativa próxima (30 min antes/depois) ou outra mesa livre.
- Na confirmação (dia, hora, mesa e nº de pessoas definidos), a IA cria a reserva como **pendente**.
  Você a vê na agenda e a confirma quando quiser (o cliente recebe o aviso de confirmação).
- A IA nunca inventa mesa que não existe nem promete horário fora de funcionamento.

## Limitações conhecidas (honestas)

- **Sem lembrete automático** ("sua reserva é em 1h") e **sem auto-transição** de status (confirmada
  → realizada não acontece sozinho) — fases futuras.
- **Sem cobrança de no-show** nem pagamento antecipado.
- **Sem reserva em grupo** (várias mesas combinadas), **sem feriados/dias especiais**, **sem buffer**
  configurável de fato (fixo em 0 nesta versão).
- **Fuso fixo** America/Sao_Paulo (Brasil). Horários são avaliados nesse fuso.
- **Sem cardápio** aqui (se o restaurante também quiser cardápio + pedidos, isso é o perfil SushiBot).
- Os textos de notificação são fixos nesta versão (personalização por restaurante: fase futura).
- **Risco aceito no MVP:** se a IA prometer um horário e, no instante de gravar, alguém tiver acabado
  de ocupar o slot, a reserva não é criada (você não a vê na agenda) — contorne manualmente. É raro.

## Onda 1 do backlog (2026-07 — FEATURES_SUGERIDAS_RESTAURANT #1/#3, migration 91)

- **Lembrete D-1 + confirmação SIM/NÃO (#1):** `RestaurantReminderJob` (cron 9h40) pergunta
  "confirma sua mesa amanhã às {hora}? Responda SIM ou NÃO" pras reservas pendente/confirmada
  de amanhã (idempotência `reminded_24h`; sem canal marca sem envio). A resposta cai no fluxo
  da IA, que emite `<confirmacao_reserva>{reservation_id, decisao}` (confirmada|cancelada) —
  clone do `<confirmacao_barbearia>` com BARREIRA DE CONTATO e máquina de status validando;
  cancelar LIBERA o slot e dispara a notificação padrão. O contexto ganhou um bloco FRESCO
  (não cacheado) com as próximas reservas DO CONTATO + a instrução da tag. Toggle
  `reminder_enabled` (default ligado).
- **Auto-transição (#3):** o mesmo job move confirmada com `end_at` passado (folga 2h) →
  `realizada` via service (silenciosa). `no_show` NÃO é automático (sem check-in no modelo,
  falta é julgamento humano). Toggle `auto_complete_enabled` (default ligado, espelho barbearia).
