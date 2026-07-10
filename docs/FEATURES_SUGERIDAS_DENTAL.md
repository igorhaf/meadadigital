# Features Sugeridas — Dental (odontologia)

> Backlog de features avançadas para o nicho **Dental (odontologia)** (profile_id `dental`), priorizado por VALOR DE NEGÓCIO. Objetivo: engordar a cartela de features com o que mais agrega receita/retenção no menor tempo. Baseado no estado REAL do nicho (o que já existe NÃO é repetido aqui).

> **Modo de execução (cravado):** ao implementar qualquer feature deste backlog, faça **tudo em prosa**,
> de forma contínua e autônoma, **sem perguntar nada ao programador**, sem pausas para confirmação e
> **sem usar o widget de perguntas** (AskUserQuestion). Não interrompa o fluxo pedindo aval intermediário:
> decida com base no estado real do código/banco e nas convenções das skills, implemente, e só pare em
> ponto de bifurcação arquitetural genuína ou no gate de teste. Reporte o progresso em prosa corrida.

## O que o nicho já tem (baseline)

- **Pacientes** (`/dashboard/patients`): catálogo próprio (sub-entidade do contato do WhatsApp), vínculo pra IA reconhecer pelo telefone, exclusão bloqueada se houver consulta.
- **Agenda de consultas** (`/dashboard/appointments`): consulta com tipo texto livre, conflito de horário POR CONSULTÓRIO (1 dentista), janela de funcionamento validada, POST manual e via IA.
- **Configuração** (`/dashboard/dental-settings`): duração da consulta, intervalo, horário abre/fecha.
- **IA agendadora com TRAVA CLÍNICA:** identifica pelo telefone, mostra próximas consultas + slots livres dos próximos 14 dias, agenda via tag `<consulta>`; NUNCA diagnostica, recomenda ou discute sintoma; cancelamento pela IA é bloqueado (encaminha ao consultório).
- **Notificação WhatsApp** automática em confirmar/cancelar (texto fixo defensivo); realizada/falta silenciosos.
- **Status hardcoded** (agendada → confirmada → realizada; agendada/confirmada → cancelada; confirmada → falta).

## 🏆 Top 3 quick wins (fazer primeiro)

**1. Lembrete + confirmação automática 24h antes (scheduler + WhatsApp).** É o maior ROI do nicho: consultório odontológico perde 15–30% da agenda em falta/no-show, e cada falta é uma cadeira ociosa que não volta. Um cron diário que dispara "sua consulta é amanhã às HH:MM, responde SIM pra confirmar / PRECISO REMARCAR" transforma a IA (que já entende linguagem livre) na confirmadora: SIM → move `agendada→confirmada`; pedido de remarcar → oferece novos slots dentro da trava atual. Esforço P/M (reusa notifier + context cache + máquina de status já existentes), retorno imediato em receita recuperada. Vende sozinho na demo.

**2. Sinal/entrada por Pix pra reservar a consulta (anti-no-show financeiro).** O lembrete reduz a falta; o sinal a elimina. Cobrar R$ 30–50 de sinal (abatido no valor do procedimento) faz o paciente ter "pele no jogo" — quem paga, comparece. A IA gera o link Pix na confirmação do agendamento e a consulta só sai de `agendada` quando o sinal cai. É o gancho de receita mais forte da cartela e diferencia de qualquer agenda de papel. Depende do gateway #50 (Pix/Stripe), então entra em segundo — mas é o item que o dono deve priorizar assim que #50 existir.

**3. Reativação de paciente inativo ("faz 6 meses da sua última limpeza").** A recall/manutenção é o motor de receita recorrente da odontologia — limpeza a cada 6 meses é a consulta que mais repete. Um cron que varre pacientes com última consulta `realizada` há X meses e dispara uma campanha WhatsApp ("está na hora da sua revisão, quer que eu já veja um horário?") reenche a agenda com pacientes que já são da base, custo de aquisição zero. Esforço M, retorno altíssimo e contínuo. Respeita a trava (é convite administrativo, não recomendação clínica).

## Backlog priorizado (16 features)

| # | Feature | Valor de negócio | Esforço | O que resolve pro cliente | Eixo |
|---|---------|------------------|---------|---------------------------|------|
| 1 | Lembrete + confirmação automática 24h/2h antes (cron + IA confirma via WhatsApp) | Alto | P | Corta no-show; reenche cadeira; confirma sem ligação | Operação |
| 2 | Sinal/entrada Pix pra travar a consulta (abatido no procedimento) | Alto | M | Elimina falta com pele no jogo; antecipa caixa | Receita |
| 3 | Recall/reativação de inativo (recall de limpeza/manutenção 6m) | Alto | M | Reenche agenda com a própria base; receita recorrente | Retenção |
| 4 | Multi-dentista (`dentist_id`): conflito por profissional | Alto | M | Clínica com 2+ dentistas atende em paralelo; hoje trava tudo num consultório | Operação |
| 5 | Auto-transição por scheduler (consulta passada → realizada/falta) | Médio | P | Agenda sempre limpa sem mexer na mão; alimenta relatório e recall | Operação |
| 6 | Dashboard de faturamento/ocupação (agenda cheia %, faltas, top procedimentos) | Alto | M | Dono enxerga ocupação, perda por falta e o que mais fatura | Operação |
| 7 | Catálogo de procedimentos com preço + duração própria | Alto | M | IA agenda "Canal" (60min) ≠ "Limpeza" (30min); base pro sinal e orçamento | Receita |
| 8 | Orçamento/plano de tratamento com aprovação (gate 2 fases via IA) | Alto | G | Fecha tratamento caro (implante/ortodontia) com aprovação registrada no WhatsApp | Receita |
| 9 | Pacote/assinatura (ortodontia mensal, plano preventivo anual) | Alto | M | Receita recorrente previsível; fideliza o paciente à clínica | Receita |
| 10 | Cupom + campanha em massa segmentada (ex.: "clareamento -20% este mês") | Médio | M | Ocupa horário vago; capta paciente novo; reativa em lote | Marketing |
| 11 | Indicação com benefício (amigo indicado ganha 1ª avaliação) | Médio | M | Aquisição barata via boca a boca da base atual | Marketing |
| 12 | NPS/avaliação pós-consulta + coleta de review público | Médio | P | Prova social pra captar; detecta paciente insatisfeito antes de perder | Retenção |
| 13 | Aniversário/data especial (mensagem + brinde/desconto) | Médio | P | Toque humano barato que retém e gera reagendamento | Retenção |
| 14 | Página pública/CMS da clínica (habilitar CMS pro nicho dental) | Médio | P | Vitrine com serviços + botão WhatsApp; captação orgânica | Marketing |
| 15 | Anexo de exame/foto (raio-X, antes/depois) quando o Storage liberar | Médio | M | Paciente envia raio-X; clínica guarda evolução; base pro plano de tratamento | Integração |
| 16 | Retorno/proservação automática por procedimento (ex.: revisão do canal em 30d) | Médio | P | Garante o follow-up clínico virar consulta; menos retrabalho e mais receita | Operação |

## Detalhamento das prioritárias

### 1. Lembrete + confirmação automática 24h/2h antes

- **Problema de negócio:** falta/no-show é a maior perda operacional de consultório odontológico — cadeira vazia não fatura e não volta. Hoje não há lembrete (limitação conhecida) e a confirmação é manual.
- **Como funciona:** um scheduler (cron diário/horário) varre `dental_appointments` com status `agendada`/`confirmada` cuja `start_at` cai na janela de lembrete (24h e 2h antes, no fuso America/Sao_Paulo). Para cada uma com paciente vinculado ao WhatsApp, o `DentalAppointmentNotifier` envia "Sua consulta é amanhã às HH:MM. Responde SIM pra confirmar ou PRECISO REMARCAR". A IA (persona dental já entende linguagem livre) interpreta a resposta: SIM → transição `agendada→confirmada` (dispara a notificação de confirmação já existente); "remarcar/desmarcar" → mantém a trava (cancelamento continua bloqueado pela IA) e oferece novos slots livres dentro dos 14 dias. Um flag `reminder_sent_at` na consulta evita reenvio duplicado.
- **Dependências:** scheduler/cron (transversal); reusa notifier, context cache e máquina de status atuais. Nenhum bloqueador de foto/gateway.
- **Métrica de sucesso:** taxa de no-show antes/depois; % de consultas confirmadas via lembrete; horários vagos reagendados no mesmo ciclo.

### 2. Sinal/entrada por Pix pra travar a consulta

- **Problema de negócio:** o lembrete reduz a falta, mas o sinal a elimina — quem paga comparece. Também antecipa caixa e filtra agendamento fantasma.
- **Como funciona:** na config do consultório, o tenant liga "exigir sinal" e define valor (fixo ou % do procedimento). Quando a IA agenda via tag `<consulta>`, o backend cria a consulta em `agendada` + gera cobrança Pix e envia o link. A consulta só passa a valer (ou só é confirmável) quando o webhook do gateway marca o sinal pago; expira e libera o slot se não pagar em N minutos. O valor fica registrado como abatimento no procedimento. A IA NÃO fecha preço de tratamento (trava mantida) — o sinal é um valor de config, não um orçamento inventado pela IA.
- **Dependências:** gateway de pagamento #50 (Pix). Scheduler pra expirar sinal não pago.
- **Métrica de sucesso:** % de consultas com sinal pago vs. no-show desse grupo; receita antecipada/mês; slots liberados por expiração de sinal.

### 3. Recall/reativação de paciente inativo

- **Problema de negócio:** manutenção/limpeza semestral é a consulta que mais repete e a mais lucrativa por esforço; sem recall, o paciente esquece e some (ou vai pro concorrente). A base parada é dinheiro na mesa.
- **Como funciona:** cron varre pacientes cuja última consulta `realizada` foi há ≥ X meses (config, default 6) e que não têm consulta futura. Dispara campanha WhatsApp: "Oi [nome], faz 6 meses da sua última revisão — quer que eu já veja um horário pra você?". A resposta cai na IA agendadora normal (respeitando a trava: é convite administrativo, não recomendação clínica). Segmenta por período desde a última consulta e por tipo (ex.: só quem fez ortodontia). Marca `last_recall_at` pra não repetir toda semana.
- **Dependências:** scheduler/cron; motor de campanha em massa (transversal). Ideal ter a auto-transição (#5) alimentando `realizada` corretamente pra o recall disparar na hora certa.
- **Métrica de sucesso:** % de inativos reativados que agendaram; receita de consultas originadas de recall; queda no intervalo médio entre consultas por paciente.

### 4. Multi-dentista (`dentist_id`)

- **Problema de negócio:** hoje o conflito é por consultório (1 dentista) — uma clínica com 2+ dentistas não consegue agendar dois pacientes no mesmo horário mesmo com profissionais diferentes, travando a receita de metade da equipe.
- **Como funciona:** tabela `dental_dentists` (catálogo por company) + `dentist_id` em `dental_appointments`. O `findConflict` passa a filtrar por `dentist_id` (espelho exato do que salon/pet/estetica já fazem com `professional_id`: half-open, re-verificado na transação) — dois pacientes no mesmo horário com dentistas diferentes NÃO conflitam. A IA passa a oferecer slots por dentista; o context cache injeta os livres por profissional. Snapshot de `dentist_name` na consulta.
- **Dependências:** nenhuma externa — é replicar o chassi de agenda por profissional já provado em 5 outros nichos. Migration + guard já existem.
- **Métrica de sucesso:** nº de consultas paralelas/dia; ocupação por dentista; receita adicional destravada da equipe.

### 5. Auto-transição por scheduler

- **Problema de negócio:** consulta passada não vira "realizada"/"falta" sozinha (limitação conhecida), então relatórios e o recall (#3) ficam furados e o dono perde visão de faltas.
- **Como funciona:** cron horário move consultas cuja `end_at` já passou: `confirmada → realizada` (compareceu) fica manual ou por regra opt-in; `agendada` passada sem confirmação → `falta` (configurável). Respeita as transições da máquina de status atual. Silencioso (realizada/falta já não notificam). Um flag de config liga/desliga o comportamento por clínica.
- **Dependências:** scheduler/cron. Nenhum bloqueador de foto/gateway.
- **Métrica de sucesso:** consistência da agenda (nada "agendado" no passado); precisão do relatório de faltas; recall disparando no momento certo.

## Dependências transversais

- **Gateway de pagamento #50 (Pix/Stripe):** destrava #2 (sinal anti-no-show), #8 (aprovação de orçamento com pagamento), #9 (pacote/assinatura recorrente) e a cobrança de campanhas (#10). É o desbloqueio de maior impacto em RECEITA.
- **Scheduler/cron (infra de tarefas agendadas):** destrava #1 (lembrete), #3 (recall), #5 (auto-transição), #16 (retorno/proservação), aniversário (#13) e a expiração de sinal (#2). É o desbloqueio de maior impacto em OPERAÇÃO/RETENÇÃO — vários quick wins de alto ROI dependem só dele.
- **Motor de campanha em massa segmentada:** destrava #3 (recall em lote), #10 (cupom/promoção), #11 (indicação), #13 (aniversário). Reusável por todos os nichos.
- **Upload de foto/anexo (SERVICE_ROLE_KEY hoje ausente):** destrava #15 (raio-X/antes-depois) e enriquece #8 (plano de tratamento com imagem). Enquanto bloqueado, essas features ficam com o campo de imagem por link colado ou pendentes.
- **CMS/feature flags (camada 9.x já existente):** basta habilitar a flag `cms` pro nicho dental pra destravar #14 (página pública) sem código novo de plataforma.
