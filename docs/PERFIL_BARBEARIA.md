# Perfil Barbearia — BarbeariaBot (camada 8.1)

Guia operacional do tenant **barbearia** (`profile_id='barbearia'`). O BarbeariaBot é o produto de
**barbearia / barber shop**: você gerencia barbeiros e serviços, marca horários na agenda, e gerencia
uma **fila de walk-in** (por ordem de chegada). A IA atende seus clientes pelo WhatsApp e oferece dois
caminhos: **marcar horário** com um barbeiro, ou **entrar na fila** quando o cliente quer ser atendido
"assim que der".

## Telas (menu "Barbearia")

- **Barbeiros** (`/dashboard/barber-barbers`): cadastre quem atende. Especialidade é texto livre e
  opcional ("corte/barba", "degradê"). Barbeiro inativo some da disponibilidade que a IA enxerga.
  Não dá pra excluir um barbeiro com agendamento ou ticket de fila — **desative** em vez de excluir.
- **Serviços** (`/dashboard/barber-services`): nome, duração própria (em minutos) e preço opcional.
  A duração entra como "foto" (snapshot) no agendamento/ticket — mudar o serviço depois não altera os
  passados. Preço em branco = a IA não expõe valor.
- **Agenda** (`/dashboard/barber-appointments`): os horários marcados, agrupados por dia, com filtro de
  status e de barbeiro. Você pode criar manualmente. **O conflito é por barbeiro**: dois clientes no
  mesmo horário com barbeiros diferentes é normal (paralelismo). Botões de transição de status seguem
  as regras (ver abaixo).
- **Fila** (`/dashboard/barber-queue`): a fila de walk-in. Cada pessoa aguardando mostra a **posição**
  (calculada na hora) e a **espera estimada**. Veja como funciona logo abaixo.
- **Cupons** (`/dashboard/barber-coupons`): cupons percent/fixed (mínimo, validade, máx. de usos) que o
  CLIENTE informa na conversa — a IA repassa o código na tag e o sistema valida e aplica (onda 1, #12).
- **Fidelidade** (`/dashboard/barber-loyalty`): "a cada N cortes realizados, o próximo é grátis" —
  aplicado automaticamente pelo backend no agendamento (onda 1, #3).
- **Relatórios** (`/dashboard/barber-reports`): faturamento líquido dos realizados, taxa de falta,
  ocupação por barbeiro e ranking de serviços, por janela de meses (onda 1, #15).
- **Configurações** (`/dashboard/barber-settings`): horário de funcionamento, granularidade dos slots
  (ex.: de 15 em 15 min), o **interruptor da fila de walk-in** e os toggles da onda 1: **lembrete de
  confirmação** (24h antes), **auto-transição** e **upsell da IA** (opt-in).

## Como a FILA funciona (a parte nova)

A fila é **por ordem de chegada**, sem hora marcada. O ponto importante: a **posição não é fixa** — ela
é recalculada toda hora.

- Cada ticket pode ser de um **barbeiro específico** ou da **fila geral** ("qualquer barbeiro").
- A **posição** que você vê é derivada na hora: é quantas pessoas estão à frente naquele escopo, + 1.
  Não existe um número gravado que precise ser "arrastado".
- Quando você **atende** (ou alguém **desiste**), todo mundo atrás sobe **automaticamente** — sem você
  reordenar nada.
- Escopo do "qualquer barbeiro": um ticket geral conta contra todos os que estão à frente; um ticket de
  um barbeiro específico conta contra a fila dele + os tickets gerais que chegaram antes (um geral à
  frente pode acabar pegando aquele barbeiro).
- A **espera estimada** é só uma estimativa (soma das durações à frente). A IA sempre fala
  "aproximadamente" — desistências e horários marcados mexem a fila.

### Chamar o próximo

Quando o barbeiro fica livre, **você** clica em **"Chamar"** no ticket que está em 1º na fila daquele
barbeiro. Isso:

1. muda o ticket para **chamado** e
2. **notifica o cliente no WhatsApp**: "Chegou a sua vez! Procure o barbeiro Fulano." — é a notificação
   crítica do walk-in.

Depois que ele for atendido, clique em **"Atendido"**. Se a pessoa sumiu, **"Desistiu"**.

> **A IA não chama ninguém.** Ela só coloca o cliente na fila e informa a posição/espera estimadas.
> Quem chama é sempre o painel (você). Isso é proposital — chamar é decisão de quem está no balcão.

## Status

**Agenda (horários marcados):** `agendado → confirmado → realizado`; `agendado/confirmado → cancelado`;
`confirmado → falta`. Só **confirmado** (com data/hora/barbeiro) e **cancelado** avisam o cliente.

**Fila (tickets):** `aguardando → chamado → atendido`; de aguardando dá pra ir a `desistiu`/`expirado`;
de chamado, a `atendido`/`desistiu`. Só **chamado** notifica ("chegou sua vez").

## Onda 1 do backlog (docs/FEATURES_SUGERIDAS_BARBEARIA.md #1/#3/#4/#7/#12/#15 — migration 83)

- **#1 Lembrete + confirmação SIM/CANCELAR** (`BarberReminderJob` + tag `<confirmacao_barbearia>`):
  o job varre os 'agendado' das PRÓXIMAS 24h (toggle `reminder_enabled`, default ON; idempotência
  `reminded_24h`) e pergunta pela conversa: "Confirma seu horário ...? Responda SIM ou CANCELAR". A
  resposta do cliente vira a tag `<confirmacao_barbearia>{"appointment_id","decisao":"confirmado|
  cancelado"}` — a IA só REFLETE a decisão (nunca decide); o handler valida a BARREIRA DE CONTATO
  (só o dono do horário mexe nele) + a máquina de status. Cancelar libera o slot na hora. Agendamento
  manual sem conversa: marca sem enviar (não revarre).
- **#3 Fidelidade "a cada N cortes, 1 grátis"** (`barber_loyalty_config`, default OFF): o backend
  conta os REALIZADOS do contato antes de criar; ao completar o ciclo (count % N == 0), o novo
  agendamento sai grátis (desconto = preço, `loyalty_applied=true`, badge "grátis · fidelidade"). A
  IA informa o saldo na conversa ("faltam 2 pro seu grátis") — nunca aplica por conta própria.
- **#4 Upsell controlado** (`upsell_enabled`, default OFF — opt-in): ligado, a IA pode sugerir UMA
  única vez um serviço complementar DO CATÁLOGO no fechamento (barba, sobrancelha), sem insistir.
  Desligado, a persona segue "sem upsell agressivo" como sempre.
- **#7 Auto-transição** (`BarberAutoTransitionJob`, toggle `auto_complete_enabled`, default ON):
  'confirmado' com fim há mais de 2h vira 'realizado' (silencioso — alimenta a fidelidade e o
  relatório); tickets 'aguardando' de dias anteriores expiram. 'agendado' nunca é tocado (a máquina
  não permite agendado→falta; fica pro painel).
- **#12 Cupom validável na conversa** (`barber_coupons`, motor sushi/academia/adega/atelie): a IA
  passa o código no campo `"cupom"` da tag `<agendamento_barbearia>`; o backend valida (ativo +
  validade + mínimo sobre o preço + máx. usos) e aplica com clamp; INVÁLIDO NÃO ABORTA (agendamento
  sai sem desconto). Fidelidade tem precedência (grátis não acumula cupom). `uses` incrementa na
  mesma transação.
- **#15 Relatórios**: GET `/api/barbearia/reports/summary?months=N` — faturamento = SÓ realizados,
  líquido (preço − desconto; corte grátis fatura 0), por mês/barbeiro/serviço + taxa de falta.
- **#2 (reativação de inativo) NÃO foi reimplementado**: o `ReactivationJob` genérico do core
  (camada 5.21) já cobre — configure `reactivation_days` + mensagem na config de IA do tenant.

## O que a IA faz (e o que NÃO faz)

A IA **faz**: identifica o cliente pelo telefone, mostra serviços e barbeiros, oferece marcar horário
**ou** entrar na fila, informa a posição/espera estimadas, cria o agendamento/ticket na confirmação,
repassa o código de CUPOM na tag (quem valida é o sistema), informa o saldo de FIDELIDADE do contexto,
e REFLETE a resposta do cliente ao lembrete (SIM confirma / CANCELAR desmarca) pela tag de confirmação.

A IA **nunca**: opina sobre a aparência/estilo do cliente nem promete resultado de corte; recomenda
serviço que o cliente não pediu (única exceção: upsell LIGADO pelo dono → UMA sugestão do catálogo,
sem insistir); inventa posição na fila ou tempo de espera exato ("você é o próximo garantido"); chama
o cliente ou move um ticket por conta própria; promete/calcula desconto (cupom e fidelidade são do
sistema); confirma ou cancela um horário sem o cliente pedir; garante um horário que conflita (o
sistema reforça com erro).

## Limitações conhecidas (fases futuras)

- Não há "chamar o próximo" automático que vire atendimento na agenda — chamar é manual (backlog #8).
- A fila só expira automaticamente na virada do dia (auto-transição); sem timeout por tempo de espera
  nem lembrete "está chegando sua vez".
- Sem painel de TV / display público / check-in por QR.
- Sem pagamento/comanda/gorjeta (gateway #50) e sem clube/assinatura de cortes (backlog #5 — o
  controle de saldo clona o chassi da Estética quando o #50 destravar a cobrança).
- Campanha em massa (#10) e indicação com recompensa (#11) esperam o motor de campanha (Onda 3);
  CMS/vitrine (#13) é a flag por nicho; multi-unidade (#16) é fase própria.
- Sem foto do corte / galeria de referência.
- Um barbeiro = um atendimento por vez (sem múltiplas cadeiras paralelas).
