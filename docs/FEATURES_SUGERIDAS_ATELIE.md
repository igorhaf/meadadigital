# Features Sugeridas — Ateliê (costura/arte/design)

> Backlog de features avançadas para o nicho **Ateliê (costura/arte/design)** (profile_id `atelie`), priorizado por VALOR DE NEGÓCIO. Objetivo: engordar a cartela de features com o que mais agrega receita/retenção no menor tempo. Baseado no estado REAL do nicho (o que já existe NÃO é repetido aqui).

> **Modo de execução (cravado):** ao implementar qualquer feature deste backlog, faça **tudo em prosa**,
> de forma contínua e autônoma, **sem perguntar nada ao programador**, sem pausas para confirmação e
> **sem usar o widget de perguntas** (AskUserQuestion). Não interrompa o fluxo pedindo aval intermediário:
> decida com base no estado real do código/banco e nas convenções das skills, implemente, e só pare em
> ponto de bifurcação arquitetural genuína ou no gate de teste. Reporte o progresso em prosa corrida.

## O que o nicho já tem (baseline)

- **Proposta order-based** com briefing → itens de orçamento (total materializado) → aprovação → fechada → realizada; funil hardcoded `AtelieProposalStatus`.
- **IA abre a proposta** a partir do briefing (`<proposta_atelie>`) e **captura a aprovação/recusa** quando já orçada (`<aprovacao_atelie>`) — gate de 2 fases; nunca fecha preço/prazo/medida.
- **Etapas de prova/ajuste** (`atelie_fittings`): sub-entidade ordenada (1ª prova → ajuste → entrega), status binário pendente/realizada, gerenciada só no painel.
- **UM perfil, três tipos** (`project_type` costura/arte/design) — mesmo chassi serve os três.
- **Catálogo de artesãos** (sem agenda), config simples (nome + notas), cliente = contato com snapshots.
- **NÃO tem hoje:** pagamento/sinal (Stripe #50), foto/anexo de referência (SERVICE_ROLE_KEY), lembrete automático de prova (scheduler), tabela de medidas, contrato e-sign, agenda com conflito.

## 🏆 Top 3 quick wins (fazer primeiro)

**1. Lembrete automático de prova/ajuste (scheduler).** O ateliê vive de provas presenciais e cada prova furada empurra o prazo de entrega inteiro — é o maior vazamento operacional do nicho. Como as `atelie_fittings` já têm `due_date`, um scheduler diário que dispara mensagem no WhatsApp na véspera ("Sua 2ª prova é amanhã às 15h — confirma?") reduz drasticamente prova perdida sem custo de gateway nem foto. É esforço P (o chassi de notificação outbound já existe) com retorno operacional imediato e alto valor de retenção percebida.

**2. Registro de sinal/entrada com aprovação (integrado ao gate).** Ateliê trabalha sob encomenda e quase sempre cobra sinal antes de comprar tecido/material — hoje isso acontece fora do sistema. Registrar o sinal na proposta (valor + status pago/pendente) e condicionar a transição `aprovada → fechada` ao sinal marcado como pago protege o caixa do ateliê contra encomenda abandonada. Enquanto o Stripe (#50) não chega, o registro é manual (a equipe confirma o Pix recebido no painel) — mesmo assim já agrega valor de negócio real e prepara o terreno pro pagamento online.

**3. Reativação de cliente inativo por ocasião.** O ateliê tem recompra sazonal previsível (formatura, casamento, festa, coleção nova) mas nenhum mecanismo de trazer o cliente de volta. Uma campanha segmentada por contatos com proposta `realizada` há X meses ("Faz tempo que não costuramos nada pra você — temos novidades pra próxima temporada") reativa receita adormecida a custo quase zero. É marketing de alto ROI que aproveita a base já existente de propostas.

## Backlog priorizado (16 features)

| # | Feature | Valor de negócio | Esforço | O que resolve pro cliente | Eixo |
|---|---------|------------------|---------|---------------------------|------|
| 1 | Lembrete automático de prova/ajuste (scheduler na véspera do `due_date`) | Alto | P | Prova furada empurra a entrega toda; lembrete reduz no-show da prova | Operação |
| 2 | Registro de sinal/entrada + gate no `fechada` (manual até #50) | Alto | M | Protege o caixa contra encomenda abandonada; formaliza o sinal | Receita |
| 3 | Reativação de cliente inativo por ocasião (campanha segmentada) | Alto | M | Traz de volta cliente que sumiu; receita adormecida da base | Marketing |
| 4 | Pagamento online do sinal/parcela (Pix/cartão) | Alto | G | Cobra à distância sem depender de Pix manual; converte na hora | Receita |
| 5 | Foto de referência/croqui/render anexada à proposta | Alto | M | Briefing de peça sob medida é visual; texto perde detalhe | Operação |
| 6 | Confirmação automática de prova pelo cliente (responde SIM/remarcar) | Alto | M | Fecha o loop do lembrete; equipe sabe quem confirmou | Operação |
| 7 | Pós-entrega + avaliação (NPS + foto do resultado) | Médio | M | Prova social do trabalho autoral; feedback que retém | Retenção |
| 8 | Página pública/CMS do ateliê (portfólio + orçamento) | Alto | M | Vitrine que capta lead novo e recebe briefing 24h | Marketing |
| 9 | Tabela de medidas estruturada por cliente (reuso na recompra) | Médio | M | Não remede a cada peça; acelera a próxima encomenda | Retenção |
| 10 | Upsell proativo da IA no briefing (itens/serviços complementares) | Médio | M | Aumenta ticket sugerindo forro/bordado/acabamento | IA |
| 11 | Indicação com recompensa (cliente traz cliente) | Médio | M | Ateliê cresce por boca a boca; formaliza e premia | Marketing |
| 12 | Prazo de entrega prometido + alerta de atraso no painel | Médio | P | Evita entregar atrasado e perder o cliente | Operação |
| 13 | Cupom/desconto na proposta (primeira peça, coleção) | Médio | P | Fecha proposta parada; incentiva a coleção nova | Receita |
| 14 | Relatório de faturamento por tipo/artesão/período | Médio | M | Dono vê o que dá dinheiro (costura×arte×design) | Operação |
| 15 | Catálogo de materiais/técnicas pré-cadastrados (autofill do orçamento) | Médio | M | Orçamento mais rápido e consistente; menos erro de preço | Operação |
| 16 | Aniversário/data comemorativa do cliente (gatilho de venda) | Médio | P | Toca o cliente em momento de compra natural | Retenção |

## Detalhamento das prioritárias

### 1. Lembrete automático de prova/ajuste (scheduler)

- **Problema de negócio:** provas presenciais são o coração da execução de uma peça sob medida; uma prova esquecida atrasa a entrega inteira e gera retrabalho de reagendamento. Hoje o `due_date` das `atelie_fittings` é só previsão passiva — ninguém é avisado.
- **Como funciona:** um cron diário (novo scheduler Spring) varre `atelie_fittings` com `status='pendente'` e `due_date = amanhã` (fuso America/Sao_Paulo), resolve o contato via a proposta e dispara uma mensagem outbound fixa e defensiva pela Evolution ("Olá! Sua prova está prevista para amanhã. Podemos confirmar?"). NÃO passa pela IA (texto fixo, sem risco de a IA prometer prazo/medida — respeita a trava do nicho). Painel ganha um toggle por proposta/ateliê pra ligar/desligar o lembrete.
- **Dependências:** scheduler/cron (transversal, ainda inexistente no nicho). Não depende de #50 nem de foto.
- **Métrica de sucesso:** taxa de provas realizadas na data prevista; queda no nº de reagendamentos por prova esquecida.

### 2. Registro de sinal/entrada com gate no fechamento

- **Problema de negócio:** ateliê compra tecido/material com o sinal do cliente; encomenda aprovada mas sem sinal é prejuízo se o cliente some. Não há onde registrar nem travar o avanço.
- **Como funciona:** a proposta ganha campos `deposit_cents` (valor do sinal) e `deposit_paid` (bool). A transição `aprovada → fechada` passa a exigir `deposit_paid=true` → senão 409 `deposit_required` (espelho do `empty_budget`). Enquanto o Stripe #50 não existe, a equipe marca "sinal recebido" manualmente no painel após confirmar o Pix. A IA continua sem tocar em preço/pagamento (só informa que "a equipe vai combinar o sinal") — respeita a trava.
- **Dependências:** nenhuma pra versão manual; o pagamento online real destrava com o gateway #50 (feature #4).
- **Métrica de sucesso:** % de propostas fechadas com sinal registrado; redução de encomendas abandonadas pós-aprovação.

### 3. Reativação de cliente inativo por ocasião

- **Problema de negócio:** a recompra no ateliê é sazonal e previsível, mas o negócio não tem gatilho pra reengajar quem já comprou — receita fica na mesa.
- **Como funciona:** segmentação sobre os contatos com proposta `realizada` há mais de X meses (parâmetro do painel). O tenant dispara uma campanha em massa (texto fixo, opt-out respeitado) via Evolution: "Faz um tempo que não criamos nada juntos — chegou coleção/tecido novo, quer conversar?". A IA que responder ao retorno segue o fluxo normal de briefing → `<proposta_atelie>`. Sem foto obrigatória; foto de teaser destrava depois.
- **Dependências:** motor de campanha em massa (transversal). Não depende de #50.
- **Métrica de sucesso:** taxa de resposta da campanha; nº de novas propostas abertas por reativados; receita recuperada.

### 4. Pagamento online do sinal/parcela (Pix/cartão)

- **Problema de negócio:** cobrar sinal e parcelas por Pix manual atrasa a compra de material e depende da boa vontade do cliente. Um link de pagamento converte na hora.
- **Como funciona:** integrado à feature #2, a proposta gera um link de pagamento (gateway #50) do sinal e das parcelas do saldo. Pagamento confirmado via webhook seta `deposit_paid=true` automaticamente e libera o gate de `fechada`. A IA pode ENVIAR o link (texto), mas NUNCA negocia valor/desconto (a trava permanece — o valor vem do painel).
- **Dependências:** gateway de pagamento #50 (bloqueador global). É a materialização online da feature #2.
- **Métrica de sucesso:** tempo médio entre aprovação e sinal pago; % de sinais pagos online vs. manual.

### 5. Foto de referência/croqui/render anexada à proposta

- **Problema de negócio:** peça sob medida (e mais ainda arte/design) é intrinsecamente visual — o cliente manda uma foto de inspiração, a equipe manda o croqui/render pra aprovar. Hoje o briefing é só texto, o que gera retrabalho e mal-entendido de escopo.
- **Como funciona:** anexos por proposta (referência do cliente + croqui/arte da equipe) exibidos no detalhe e enviáveis pelo WhatsApp. A aprovação visual pode ser amarrada às `atelie_fittings` (ex.: "arte final" como marco). A IA, quando o cliente envia uma foto, não interpreta esteticamente (respeita a trava de "não prometer resultado") — só anexa ao briefing e sinaliza pra equipe.
- **Dependências:** upload de foto/anexo (bloqueador SERVICE_ROLE_KEY, transversal). Sem ele, fica em link colado como paliativo.
- **Métrica de sucesso:** redução de idas-e-vindas por escopo mal entendido; propostas com anexo aprovadas mais rápido.

## Dependências transversais

- **Gateway de pagamento #50 (global):** destrava #4 (pagamento online do sinal/parcela) e a versão online de #13 (cupom aplicado no checkout). Enquanto não existe, #2 roda em modo manual e já entrega valor.
- **Upload de foto/anexo (SERVICE_ROLE_KEY):** destrava #5 (referência/croqui), a foto do resultado em #7 (pós-venda/NPS) e a vitrine visual de #8 (CMS). Paliativo até lá: link colado.
- **Scheduler/cron (inexistente no nicho):** destrava #1 (lembrete de prova), #6 (confirmação automática), #12 (alerta de atraso), #16 (aniversário). É a dependência de maior alavancagem — um cron habilita quatro features de operação/retenção.
- **Motor de campanha em massa segmentada:** destrava #3 (reativação de inativo), #11 (indicação) e #16 (aniversário). Reaproveita a base de contatos/propostas já existente.
