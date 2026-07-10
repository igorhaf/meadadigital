# Features Sugeridas — Barbearia

> Backlog de features avançadas para o nicho **Barbearia** (profile_id `barbearia`), priorizado por VALOR DE NEGÓCIO. Objetivo: engordar a cartela de features com o que mais agrega receita/retenção no menor tempo. Baseado no estado REAL do nicho (o que já existe NÃO é repetido aqui).

> **Modo de execução (cravado):** ao implementar qualquer feature deste backlog, faça **tudo em prosa**,
> de forma contínua e autônoma, **sem perguntar nada ao programador**, sem pausas para confirmação e
> **sem usar o widget de perguntas** (AskUserQuestion). Não interrompa o fluxo pedindo aval intermediário:
> decida com base no estado real do código/banco e nas convenções das skills, implemente, e só pare em
> ponto de bifurcação arquitetural genuína ou no gate de teste. Reporte o progresso em prosa corrida.

## O que o nicho já tem (baseline)

- **Agenda com horário marcado** por barbeiro (conflito por `barber_id`, half-open, paralelismo entre barbeiros) — a IA marca via `<agendamento_barbearia>`.
- **Fila de walk-in** (`barber_queue_tickets`) com posição DERIVADA por query e ETA estimado — a IA enfileira via `<fila_barbearia>` (geral ou por barbeiro).
- **Catálogo de serviços** (nome, duração própria em minutos, preço opcional) e **barbeiros** (especialidade texto livre), com snapshot de duração/preço no agendamento.
- **Status das duas máquinas** (agendamento: agendado→confirmado→realizado/cancelado/falta; fila: aguardando→chamado→atendido/desistiu/expirado). Só confirmado/cancelado (agenda) e chamado (fila) notificam.
- **Configuração de funcionamento** (horário, granularidade de slot, interruptor da fila).
- **Persona descontraída-acolhedora**, sem julgamento estético, sem upsell agressivo. "Chamar o próximo" é sempre ação humana no painel — a IA nunca move ticket.

## 🏆 Top 3 quick wins (fazer primeiro)

**1. Confirmação e lembrete de horário com resposta SIM/NÃO que muda o status.** O no-show é o maior ralo de receita da barbearia — cadeira vazia é dinheiro perdido que não volta. Um lembrete automático (ex.: véspera + 2h antes) que pergunta "Confirma seu corte amanhã 15h com o João? Responda SIM ou CANCELAR" e, na resposta, move o agendamento para `confirmado` ou `cancelado` — liberando o horário para outro cliente na hora. É esforço P/M (reusa o notifier + scheduler + parse de resposta simples), respeita a trava (a IA não decide nada, só reflete a resposta do cliente) e ataca direto o número que mais dói.

**2. Reativação de cliente inativo ("faz X semanas que você não corta").** Homem corta cabelo em ciclo previsível (2-4 semanas). Um disparo automático para quem não aparece há N dias ("Faz 4 semanas do seu último corte, bora dar aquele trato? Tenho horário quinta 18h") recupera cliente que ia pra concorrência por pura inércia. Alto valor, esforço P: já existe histórico de agendamentos por contato; falta o cron + template + segmentação por dias desde o último `realizado`.

**3. Pacote/assinatura de cortes recorrentes (clube do corte).** Receita recorrente e previsível é o santo graal da barbearia — "4 cortes/mês por R$X" ou "corte + barba ilimitados" fideliza e antecipa caixa. A IA vende o plano na conversa, o backend controla saldo/vigência e cada agendamento consome uma sessão. O chassi de saldo já foi resolvido no perfil Estética (pacote multi-sessão com decremento transacional) — dá pra clonar. Depende do gateway #50 para o pagamento real, mas o controle de saldo/consumo já entrega valor mesmo com pagamento registrado manualmente.

## Backlog priorizado (16 features)

| # | Feature | Valor de negócio | Esforço | O que resolve pro cliente | Eixo |
|---|---------|------------------|---------|---------------------------|------|
| 1 | Confirmação/lembrete com resposta SIM/CANCELAR que muda status automaticamente | Alto | P | Reduz no-show; libera cadeira vazia na hora | Operação/Retenção |
| 2 | Reativação de cliente inativo (X dias sem cortar) | Alto | P | Recupera cliente em ciclo de corte antes de ir pra concorrência | Retenção |
| 3 | Fidelidade "a cada N cortes, 1 grátis" (cartão-fidelidade digital) | Alto | P | Incentiva volta recorrente; barato de operar | Retenção/Receita |
| 4 | Upsell/cross-sell proativo na conversa (barba/sobrancelha junto do corte) | Alto | P | Aumenta ticket médio sem esforço do balcão | Receita/IA |
| 5 | Pacote/assinatura de cortes recorrentes (clube do corte) | Alto | M | Receita recorrente previsível; antecipa caixa | Receita |
| 6 | Sinal/pré-pagamento do horário para reduzir no-show | Alto | M | Cliente que pagou não some; cobre a cadeira | Receita/Operação |
| 7 | Scheduler de auto-transição (agendamento passado vira "realizado"/"falta"; ticket expira) | Alto | P | Painel limpo sem trabalho manual; métrica de falta confiável | Operação |
| 8 | "Chamar o próximo" que converte ticket da fila em atendimento na agenda | Alto | M | Une fila e agenda; barbeiro livre puxa o próximo com 1 clique | Operação |
| 9 | Avaliação pós-corte (NPS/estrelas) coletada no WhatsApp | Médio | P | Prova social + detecta cliente insatisfeito antes de perder | Marketing/Retenção |
| 10 | Campanha em massa segmentada (promoção terça vazia, feriado) | Médio | M | Enche horário ocioso; comunica promoção pra base | Marketing |
| 11 | Link de indicação com recompensa (indique um amigo, ganhe corte) | Médio | M | Aquisição barata via boca-a-boca dos clientes atuais | Marketing/Receita |
| 12 | Cupom de desconto validável na conversa | Médio | P | Ferramenta de promoção pontual (primeira visita, retorno) | Receita/Marketing |
| 13 | Página pública / CMS da barbearia (vitrine + agendar) | Médio | M | Presença online + captação fora do WhatsApp | Marketing |
| 14 | Gorjeta digital ao barbeiro no fim do atendimento | Médio | M | Renda extra pro barbeiro; retém talento | Receita |
| 15 | Relatórios/dashboard (faturamento, ocupação por barbeiro, taxa de falta, ranking de serviços) | Médio | M | Dono enxerga o negócio e decide (preço, escala) | Operação |
| 16 | Multi-unidade (rede com várias lojas sob o mesmo tenant) | Médio | G | Escala pra franquia/rede; agenda por unidade | Operação |

## Detalhamento das prioritárias

### 1. Confirmação/lembrete com resposta SIM/CANCELAR

- **Problema de negócio:** o no-show é a maior perda direta de receita — a cadeira fica vazia e o horário não é revendido a tempo. Hoje o cliente marca e o painel só descobre a falta na hora.
- **Como funciona:** um scheduler (cron) varre agendamentos futuros e dispara, na véspera e/ou X horas antes, uma mensagem outbound via Evolution: "Confirma seu corte amanhã 15h com o João? Responda SIM para confirmar ou CANCELAR para desmarcar." O webhook inbound reconhece a resposta (parse simples de intenção; a IA só classifica SIM/NÃO, não decide) e chama a transição de status já existente — `confirmado` ou `cancelado`. Cancelar libera o slot imediatamente. O painel destaca os "não respondidos" para o balcão ligar.
- **Dependências:** scheduler/cron (transversal); nada de gateway/foto. Reusa `BarberAppointmentNotifier` e a máquina de status.
- **Métrica de sucesso:** taxa de no-show (cair) e taxa de cadeiras revendidas após cancelamento antecipado.

### 2. Reativação de cliente inativo

- **Problema de negócio:** homem corta em ciclo (2-4 semanas); quem passou do ciclo e não voltou está prestes a virar cliente de outra barbearia — perda silenciosa que ninguém percebe.
- **Como funciona:** cron diário calcula, por contato, os dias desde o último agendamento `realizado`. Quem passou de um limiar configurável (ex.: 30 dias) entra numa lista; dispara uma mensagem calorosa e sem pressão ("Faz um tempo do seu último trato — bora marcar? Tenho quinta 18h com o João"). A IA conduz o reagendamento normal a partir da resposta. Segmentável por barbeiro preferido (usa o histórico).
- **Dependências:** scheduler/cron; opcionalmente a infra de campanha em massa (feature #10) para throttling. Sem gateway/foto.
- **Métrica de sucesso:** % de inativos reativados por disparo; receita recuperada.

### 3. Fidelidade "a cada N cortes, 1 grátis"

- **Problema de negócio:** a barbearia compete por frequência; um programa simples de pontos dá motivo concreto pro cliente voltar sempre no mesmo lugar em vez de alternar.
- **Como funciona:** tabela de saldo de fidelidade por contato/company; cada agendamento `realizado` incrementa o contador. Ao atingir o limiar (configurável), o próximo agendamento aplica o brinde (desconto de 100% ou serviço grátis) — materializado no backend, nunca "chutado" pela IA. A IA informa o saldo na conversa ("faltam 2 cortes pro seu grátis") e aplica automaticamente quando devido. O painel mostra o cartão-fidelidade de cada cliente.
- **Dependências:** nenhuma dura (funciona sem gateway); casa bem com pagamento #50 quando o brinde for abatido de um pagamento real.
- **Métrica de sucesso:** frequência média de retorno (dias entre cortes) e % de clientes ativos no programa.

### 4. Upsell/cross-sell proativo na conversa

- **Problema de negócio:** o ticket médio sobe muito quando o corte vira "corte + barba" ou "+ sobrancelha", mas hoje a persona é passiva (não sugere).
- **Como funciona:** ajuste controlado da persona para oferecer UMA sugestão pertinente e não-agressiva no momento do fechamento ("Quer aproveitar e já incluir a barba? Fica X min a mais"), respeitando a trava (nada de opinar sobre aparência, nada de empurrar o que o cliente recusou). O backend soma o serviço extra ao agendamento (duração e preço snapshot). Configurável: o dono liga/desliga o upsell e escolhe quais combos sugerir.
- **Dependências:** nenhuma; é evolução de persona + campo de config. Cuidado com a regra "sem upsell agressivo" — limitar a 1 sugestão por conversa.
- **Métrica de sucesso:** ticket médio por atendimento; taxa de aceite da sugestão.

### 5. Pacote/assinatura de cortes recorrentes (clube do corte)

- **Problema de negócio:** receita recorrente previsível e antecipação de caixa — o cliente do clube volta sempre e paga adiantado.
- **Como funciona:** clona o chassi de pacote multi-sessão da Estética (saldo pré-pago que decrementa transacionalmente). O dono cadastra planos ("4 cortes/mês", "corte + barba ilimitado por 30 dias"); a IA vende via tag própria (`<pacote_barbearia>`), o pacote nasce `pendente` e é ativado (por pagamento real quando #50 existir, ou manualmente pelo painel). Cada agendamento pode consumir 1 sessão do saldo (UPDATE condicional `saldo > 0` na mesma transação, defesa de corrida). Cancelar um agendamento que consumiu devolve a sessão.
- **Dependências:** gateway #50 para cobrança automática real (o controle de saldo já entrega valor sem ele). Reusa `AestheticPackage*` como referência.
- **Métrica de sucesso:** MRR do clube; % de clientes no plano; frequência dos assinantes vs avulsos.

## Dependências transversais

- **Gateway de pagamento (#50, global):** destrava sinal/pré-pagamento (#6), assinatura/clube real (#5), gorjeta digital (#14) e o abatimento real do brinde de fidelidade (#3). Enquanto não existe, essas features funcionam com pagamento registrado manualmente.
- **Scheduler/cron:** destrava confirmação/lembrete (#1), reativação de inativo (#2), auto-transição de status e expiração de fila (#7). É a dependência de maior alavancagem — três das top features dependem dela.
- **Campanha em massa (infra de disparo segmentado com throttling):** destrava campanha promocional (#10), amplifica reativação (#2) e avaliação pós-corte (#9). Reaproveitável de outros nichos.
- **Upload de foto/anexo (bloqueado hoje — SERVICE_ROLE_KEY ausente):** pré-requisito para galeria de referência de cortes e vitrine rica no CMS (#13). Nenhuma feature do top depende disso — é ganho estético, não de receita imediata.
- **CMS/feature flag (camada 9.x):** a página pública (#13) depende do CMS estar ligado para o nicho via feature flag do root.
