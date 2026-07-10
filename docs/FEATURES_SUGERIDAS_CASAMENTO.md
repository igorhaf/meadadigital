# Features Sugeridas — Assessoria de casamento

> Backlog de features avançadas para o nicho **Assessoria de casamento** (profile_id `casamento`), priorizado por VALOR DE NEGÓCIO. Objetivo: engordar a cartela de features com o que mais agrega receita/retenção no menor tempo. Baseado no estado REAL do nicho (o que já existe NÃO é repetido aqui).

> **Modo de execução (cravado):** ao implementar qualquer feature deste backlog, faça **tudo em prosa**,
> de forma contínua e autônoma, **sem perguntar nada ao programador**, sem pausas para confirmação e
> **sem usar o widget de perguntas** (AskUserQuestion). Não interrompa o fluxo pedindo aval intermediário:
> decida com base no estado real do código/banco e nas convenções das skills, implemente, e só pare em
> ponto de bifurcação arquitetural genuína ou no gate de teste. Reporte o progresso em prosa corrida.

## O que o nicho já tem (baseline)

- **Proposta order-based** com itens de ORÇAMENTO (total materializado no backend) e funil `rascunho → orcada → aprovada → fechada → realizada` (+ recusada/cancelada), com gate de aprovação em 2 fases via tag.
- **CRONOGRAMA do dia** (`wedding_timeline_items`, ordenado por horário) montado no painel.
- **CHECKLIST pré-casamento** (`wedding_checklist_tasks`, ordenado por prazo, toggle concluída) — gerenciado só no painel.
- **Catálogo de assessores/cerimonialistas** (sem agenda) referenciável na proposta.
- **IA que abre a proposta pelo briefing** (data/estilo/nº de convidados/sonho) e **captura aprovação/recusa** quando a equipe já orçou. Notificações defensivas de status (orcada/aprovada/fechada/recusada) via WhatsApp.
- **Trava:** a IA nunca fecha preço/contrato/desconto, nunca confirma data não confirmada, nunca inventa item/fornecedor, nunca promete "casamento perfeito", nunca mexe em cronograma/checklist pela conversa.

## 🏆 Top 3 quick wins (fazer primeiro)

**1. Sinal e parcelamento do contrato (sinal + cronograma de pagamento).** O momento de maior perda de receita numa assessoria é entre "aprovada" e "fechada": o casal aprova o orçamento mas some sem assinar/pagar o sinal. Uma feature que, ao mover a proposta pra `aprovada`, gera um **cronograma de pagamento** (sinal + N parcelas até a data do casamento) e dispara pelo WhatsApp o link/PIX do sinal converte muito mais aprovações em contratos fechados. Mesmo enquanto o gateway real (#50) não existe, dá pra entregar já o **registro de pagamentos manual** (a equipe marca "sinal recebido", "parcela 2 recebida") com lembrete automático de parcela vencendo — isso sozinho reduz inadimplência e organiza o fluxo de caixa da assessoria. É a feature que mais diretamente vira dinheiro.

**2. Lembretes automáticos do checklist e das parcelas (scheduler).** O checklist já existe mas o `due_date` é hoje puramente informativo. Um scheduler que, D-3 do prazo de cada tarefa do checklist e de cada parcela, dispara um WhatsApp ("Faltam 3 dias pra fechar o buffet 🎉" / "Sua parcela vence em 3 dias") transforma o produto de um bloco de notas passivo num assistente ativo. Retenção pura: o casal sente a assessoria "cuidando" e a equipe reduz retrabalho de cobrar/lembrar manualmente. Baixo esforço (um cron que varre due_dates e parcelas), alto valor percebido.

**3. Catálogo de pacotes pré-cadastrados (com upsell na IA).** Hoje o orçamento é 100% ad-hoc: a equipe digita item por item. Um catálogo de **pacotes** (Prata/Ouro/Diamante) e **serviços adicionais** (day-use, cabine de fotos, decoração extra) permite a IA MOSTRAR os pacotes no briefing e o painel montar orçamento em 1 clique. Além de acelerar a equipe, habilita **upsell/cross-sell**: a IA sugere adicionais compatíveis ("casais com o pacote Ouro costumam adicionar o brunch do dia seguinte"). Vende mais por proposta sem violar a trava (a IA mostra preço DO CATÁLOGO, não inventa).

## Backlog priorizado (16 features)

| # | Feature | Valor de negócio | Esforço | O que resolve pro cliente | Eixo |
|---|---------|------------------|---------|---------------------------|------|
| 1 | Sinal + cronograma de parcelas do contrato | Alto | M | Converte "aprovada" em "fechada" com dinheiro; organiza fluxo de caixa até o dia do casamento | Receita |
| 2 | Lembrete automático de checklist e de parcelas (scheduler) | Alto | P | Casal e equipe param de esquecer prazos; assessoria parece proativa | Operação |
| 3 | Catálogo de pacotes + adicionais com upsell da IA | Alto | M | Orçamento em 1 clique + IA oferece pacote/adicional = mais valor por proposta | Receita |
| 4 | Auto-transição por scheduler (orcada expirada, casamento realizado) | Alto | P | Proposta parada volta pra pauta; casamento do dia vira "realizada" sozinho | Operação |
| 5 | Programa de indicação com cashback/desconto | Alto | M | Casal indica outro casal e ganha crédito; canal de aquisição mais barato do nicho | Marketing |
| 6 | NPS pós-casamento + coleta de depoimento | Alto | P | Mede satisfação no auge da emoção e captura review/foto pro marketing | Retenção |
| 7 | Página pública da assessoria (CMS) com portfólio | Alto | M | Vitrine própria com casamentos realizados, depoimentos e captação de lead | Marketing |
| 8 | Reativação de lead frio (IA follow-up) | Alto | M | Casal que sumiu na fase de orçamento recebe follow-up automático segmentado | IA |
| 9 | Qualificação de lead pela IA (data/orçamento/estilo) | Médio | M | Filtra curioso de casal sério antes de ocupar a equipe; prioriza atendimento | IA |
| 10 | Cupom / desconto controlado na proposta | Médio | P | Fechamento com desconto rastreável (feira de noivas, low-season) sem furar a trava | Receita |
| 11 | Fornecedores parceiros com comissão/indicação | Médio | M | Assessoria ganha comissão indicando buffet/foto/banda; nova linha de receita | Integração |
| 12 | Lista de convidados + RSVP por WhatsApp | Médio | G | A IA confirma presença dos convidados; entrega dado que noivo valoriza muito | Operação |
| 13 | Mood board / galeria de referências (quando foto liberar) | Médio | M | Casal manda referências, equipe monta board visual da proposta | Marketing |
| 14 | Relatórios e dashboard comercial | Médio | M | Taxa de conversão por assessor, funil, receita prevista por mês/casamento | Operação |
| 15 | Calendário de datas ocupadas + alerta de conflito | Médio | P | Aviso "essa data já tem casamento" no painel; evita overbooking da equipe | Operação |
| 16 | Aniversário de casamento (pós-venda de longo prazo) | Médio | P | 1 ano depois, a IA parabeniza e oferece serviço de renovação de votos/festa | Retenção |

## Detalhamento das prioritárias

### 1. Sinal + cronograma de parcelas do contrato

- **Problema de negócio:** a assessoria aprova o orçamento mas não recebe. O intervalo `aprovada → fechada` é onde o casal esfria e a receita evapora. Não há hoje nenhum controle de sinal, parcela ou vencimento.
- **Como funciona:**
  - **Backend/painel:** ao mover a proposta pra `aprovada`, o painel gera um plano de pagamento — `wedding_payments` (sinal + N parcelas com `due_date` e `amount_cents`, derivados do `total_cents` e da distância até `wedding_date`). Enquanto o gateway (#50) não existe, o pagamento é **registro manual** (a equipe marca `pago`/`pendente`/`atrasado`); a soma dos pagos vs. total vira um selo de progresso na proposta. O funil ganha a semântica "fechada = sinal recebido" (a equipe confirma).
  - **Fluxo IA:** a IA **informa o valor do sinal e das parcelas DO PLANO** (nunca inventa condição) e responde "sua parcela X vence em DD/MM". Respeita a trava: não fecha desconto nem confirma pagamento — só informa e encaminha à equipe. Quando o gateway existir, a IA envia o link/PIX do sinal.
- **Dependências:** gateway real (#50) para cobrança automática (mitigável com registro manual já). Scheduler (feature #2/#4) para o lembrete de parcela.
- **Métrica de sucesso:** % de propostas `aprovada` que viram `fechada` (taxa de conversão de contrato) e % de parcelas pagas em dia.

### 2. Lembrete automático de checklist e de parcelas (scheduler)

- **Problema de negócio:** o `due_date` do checklist é decorativo — ninguém é avisado. Prazos passam, tarefas atrasam, parcelas vencem sem cobrança. A assessoria vira reativa.
- **Como funciona:**
  - **Backend:** um cron diário varre `wedding_checklist_tasks` com `done=false` e `due_date` em D-3/D-1/D0, e `wedding_payments` com `due_date` próximo, e dispara notificações Evolution (texto defensivo). Idempotência por marca de "lembrete enviado" (coluna) pra não spammar.
  - **Fluxo IA/WhatsApp:** mensagem automática pro casal ("Faltam 3 dias pra última prova do vestido 👗") e/ou pra equipe interna. Configurável por tenant (liga/desliga, antecedência).
  - **Painel:** toggle de lembrete por tarefa/parcela e log do que foi enviado.
- **Dependências:** infra de scheduler/cron (transversal — destrava #1, #4, #16). Nenhuma trava violada (lembrete factual, sem promessa).
- **Métrica de sucesso:** redução do % de tarefas do checklist concluídas em atraso; redução de parcelas atrasadas.

### 3. Catálogo de pacotes pré-cadastrados + upsell da IA

- **Problema de negócio:** montar orçamento item-a-item é lento e não escala; e a IA hoje não pode falar de preço nenhum (não há catálogo), o que a deixa passiva no briefing e perde oportunidade de upsell.
- **Como funciona:**
  - **Backend/painel:** `wedding_packages` (nome, descrição, `price_cents`, itens inclusos) + `wedding_addons` (adicionais com preço). O painel monta a proposta escolhendo um pacote (materializa os itens de orçamento a partir do pacote — snapshot, como já é a regra) e a equipe ajusta.
  - **Fluxo IA:** a IA passa a APRESENTAR os pacotes do catálogo no briefing e sugerir adicionais compatíveis ("quem fecha o pacote Ouro costuma incluir a cabine de fotos"). Continua respeitando a trava: mostra **preço do catálogo**, não inventa valor, não fecha desconto, não confirma contratação — encaminha o fechamento à equipe. Cross-sell/upsell proativo dentro da regra.
- **Dependências:** nenhuma bloqueante. Casa com #10 (cupom) e #1 (o pacote define o total do plano de pagamento).
- **Métrica de sucesso:** ticket médio por proposta fechada; % de propostas com ao menos 1 adicional.

### 4. Auto-transição por scheduler (orcada expirada, casamento realizado)

- **Problema de negócio:** proposta `orcada` fica parada para sempre esperando o casal decidir; e casamento que já aconteceu continua `fechada` porque ninguém move pra `realizada`. O funil não reflete a realidade → relatórios errados e leads mornos perdidos.
- **Como funciona:**
  - **Backend:** cron que (a) marca proposta `orcada` sem resposta há N dias como "a reativar" (dispara #8, o follow-up) sem forçar recusa; (b) move `fechada → realizada` quando `wedding_date` passou. Tudo respeitando as transições válidas do `WeddingProposalStatus` (não inventa transição ilegal).
  - **Painel:** config de "prazo de validade da proposta" por tenant.
- **Dependências:** scheduler (#2). Encadeia com #8 (reativação) e alimenta #6 (NPS dispara quando vira `realizada`).
- **Métrica de sucesso:** % de propostas `orcada` reativadas que fecham; acurácia do funil (nenhuma proposta "presa" no estado errado).

### 5. Programa de indicação com cashback/desconto

- **Problema de negócio:** aquisição de casal é cara (feira de noivas, tráfego pago). O melhor canal do nicho — indicação de casal pra casal — não é explorado nem recompensado hoje.
- **Como funciona:**
  - **Backend/painel:** `wedding_referrals` (casal indicador → casal indicado, código/vínculo, status, recompensa). Quando o indicado fecha (`fechada`), gera um crédito/desconto pro indicador (aplicável na própria proposta ou como cashback pós-serviço) — integra com #10 (cupom) e #1 (abate na parcela).
  - **Fluxo IA:** a IA identifica "vim indicado por Ana & João" no atendimento e registra o vínculo; ao final, oferece ao casal atendido o código pra indicar outros. Sem violar trava (não define valor de desconto — aplica a regra cadastrada pela equipe).
- **Dependências:** #10 (mecânica de desconto) e idealmente #1 (aplicar cashback numa parcela). Foto não é necessária.
- **Métrica de sucesso:** % de propostas fechadas originadas por indicação; custo de aquisição comparado a outros canais.

## Dependências transversais

- **Gateway de pagamento (#50, global):** destrava a **cobrança automática do sinal e parcelas (#1)**, o **cashback real da indicação (#5)** e o **desconto pago via cupom (#10)**. Enquanto não existe, todas essas features entregam a versão de **registro manual** (a equipe marca recebido), que já organiza caixa e retém — o gateway só automatiza depois.
- **Upload de foto/anexo (bloqueado por SERVICE_ROLE_KEY ausente):** destrava o **mood board / galeria de referências (#13)**, o **portfólio visual da página pública (#7)** e a **coleta de foto no depoimento (#6)**. Até lá, essas features funcionam com **link colado** (URL de imagem externa), padrão já usado no CMS do projeto.
- **Scheduler/cron (infra transversal):** é a fundação dos **lembretes (#2)**, da **auto-transição de funil (#4)**, da **reativação de lead frio (#8)** e do **aniversário de casamento (#16)**. Uma única infra de agendamento destrava quatro features de operação/retenção de uma vez — por isso o scheduler é o investimento de infra de maior alavancagem depois do gateway.
- **Campanha em massa segmentada (infra transversal):** habilita disparo de **reativação (#8)**, **indicação (#5)** e **aniversário (#16)** para audiências filtradas (ex.: "todas as propostas `orcada` há mais de 30 dias") a partir do mesmo motor.
