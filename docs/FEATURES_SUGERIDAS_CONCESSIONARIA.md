# Features Sugeridas — Concessionária de carros

> Backlog de features avançadas para o nicho **Concessionária de carros** (profile_id `concessionaria`), priorizado por VALOR DE NEGÓCIO. Objetivo: engordar a cartela de features com o que mais agrega receita/retenção no menor tempo. Baseado no estado REAL do nicho (o que já existe NÃO é repetido aqui).

> **Modo de execução (cravado):** ao implementar qualquer feature deste backlog, faça **tudo em prosa**,
> de forma contínua e autônoma, **sem perguntar nada ao programador**, sem pausas para confirmação e
> **sem usar o widget de perguntas** (AskUserQuestion). Não interrompa o fluxo pedindo aval intermediário:
> decida com base no estado real do código/banco e nas convenções das skills, implemente, e só pare em
> ponto de bifurcação arquitetural genuína ou no gate de teste. Reporte o progresso em prosa corrida.

## O que o nicho já tem (baseline)

- **Estoque de veículos** (`concessionaria_vehicles`) com identidade e ciclo de status próprio (disponível → reservado → vendido), foto por LINK colado, preço no catálogo. Mudança de status é ação humana.
- **Test-drive agendado** (clona dental): conflito por VENDEDOR (paralelismo entre vendedores), `end_at` materializado, só de veículo disponível, status agendado→confirmado→realizado/cancelado/no_show, notifica confirmado/cancelado.
- **Lead/funil de compra** (clona oficina/eventos, sem itens): interesse em um veículo, `payment_condition` (avista/financiado) como flag declarativa, preço = snapshot do catálogo, funil novo→em_negociacao→fechado/perdido movido pela equipe.
- **IA em 3 lentes** (vitrine + agenda test-drive + registro de lead) via `ConcessionariaContextCache` (TTL 30s); a IA nunca fecha preço, financiamento, crédito, nem toca status do estoque.
- **Painel** com 5 telas (Estoque, Vendedores, Test-drives, Leads, Configurações) e snapshots que preservam histórico ao vender/alterar veículo.

## 🏆 Top 3 quick wins (fazer primeiro)

**1. Alerta de "carro dos sonhos" (lista de interesse + notificação de entrada em estoque).** Hoje um cliente que quis um SUV prata até R$ 90 mil, mas não achou no estoque, é perdido — ninguém volta pra ele. Com uma lista de desejos por critério (marca/modelo/faixa de preço/ano), quando a equipe cadastra um veículo que casa, a IA dispara automaticamente uma mensagem pro contato: "chegou aquele Compass que você procurava". Reaproveita 100% do chassi de outbound + Evolution, é esforço P, e converte lead frio parado em test-drive agendado — venda que não existiria. É o maior ROI porque cria demanda a partir de um estoque que gira o tempo todo.

**2. Reativação automática de lead parado (follow-up com scheduler).** Lead em `em_negociacao` que ficou 3 dias sem resposta é dinheiro esfriando. Um scheduler diário varre leads sem interação recente e a IA dispara um follow-up gentil ("ainda tem interesse no Corolla? Posso segurar um horário de test-drive"). Não viola a trava (não fecha preço, só reengaja), reaproveita o cron + o funil que já existe, esforço P/M. Vendedores esquecem de fazer follow-up; a automação não esquece — recupera negócios que morreriam por inércia.

**3. Confirmação e lembrete automático de test-drive (redução de no-show).** O no-show de test-drive queima uma agenda de vendedor e um veículo bloqueado à toa. Um lembrete D-1 e no dia ("seu test-drive do HB20 é hoje às 15h, confirma?") com botão de confirmar/remarcar pela conversa reduz drasticamente a falta. Reaproveita o notifier + scheduler, esforço P. Menos no-show = mais vendedor produtivo e mais test-drive realizado, que é o passo direto antes da venda.

## Backlog priorizado (16 features)

| # | Feature | Valor de negócio | Esforço | O que resolve pro cliente | Eixo |
|---|---------|------------------|---------|---------------------------|------|
| 1 | Lista de desejos + alerta de entrada em estoque | Alto | P | Cliente sem carro ideal hoje é avisado quando ele chega — recupera lead perdido | Retenção |
| 2 | Reativação automática de lead parado (follow-up) | Alto | P | Lead em negociação esfriando é reengajado sem depender do vendedor | Retenção |
| 3 | Confirmação/lembrete automático de test-drive | Alto | P | Reduz no-show, libera agenda e veículo, aumenta test-drive realizado | Operação |
| 4 | Reserva de veículo com sinal (pagamento real) | Alto | M | Trava o carro pro cliente com sinal pago — evita perder venda pra concorrência | Receita |
| 5 | Proposta/avaliação de usado na troca (trade-in) | Alto | M | Captura o cliente que só compra dando o carro velho na troca | Receita |
| 6 | Campanha em massa segmentada (estoque parado, faixa de preço) | Alto | M | Empurra veículo encalhado pra base certa — gira estoque parado | Marketing |
| 7 | Pós-venda + NPS + pesquisa de satisfação | Médio | P | Mede satisfação, gera review e reabre canal pra revisão/indicação | Retenção |
| 8 | Indicação com recompensa (member-get-member) | Alto | M | Cliente satisfeito traz outro comprador — CAC baixíssimo | Marketing |
| 9 | Scheduler de auto-transição de test-drive/reserva | Médio | P | Test-drive vencido vira no_show; reserva expirada volta pro estoque sozinha | Operação |
| 10 | Dashboard de vendas/funil/conversão por vendedor | Alto | M | Dono vê taxa de conversão lead→venda e desempenho por vendedor | Operação |
| 11 | Site/vitrine pública por tenant (CMS com estoque) | Alto | M | Loja tem página própria com o estoque ao vivo — capta lead 24h | Marketing |
| 12 | Aniversário de compra + revisão programada | Médio | P | Traz o cliente de volta pra serviço/revisão e futura recompra | Retenção |
| 13 | Simulação de financiamento (via integração/parceiro) | Alto | G | Cliente vê parcela estimada sem a IA violar a trava (link/parceiro) | Integração |
| 14 | Multi-loja / multi-pátio | Médio | M | Rede com várias unidades gerencia estoque e agenda separados | Operação |
| 15 | Qualificação automática de lead (score de intenção) | Médio | M | Vendedor prioriza quem tem intenção real de compra | IA |
| 16 | Galeria de fotos real do veículo (quando liberar upload) | Médio | M | Anúncio com fotos próprias converte muito mais que link externo | Marketing |

## Detalhamento das prioritárias

### 1. Lista de desejos + alerta de entrada em estoque

- **Problema de negócio:** hoje o cliente que procura um modelo/faixa que não está no estoque simplesmente sai da conversa e some. A loja perde uma demanda concreta porque não tem como avisá-lo quando o carro certo chegar. Estoque de concessionária gira o tempo todo — o carro procurado quase sempre entra dias depois.
- **Como funciona:** nova tabela `concessionaria_wishlists` (company_id, contact_id, marca/modelo opcionais, faixa de preço, ano mínimo, câmbio/combustível opcionais, active). A IA, ao perceber que o cliente quer algo fora do estoque disponível, registra a lista via tag `<lista_desejo_carro>` (namespace próprio, respeitando a trava — só registra critério, não promete). No backend, ao inserir/mudar um veículo pra `disponivel`, um matcher casa o veículo contra as wishlists ativas e enfileira um outbound via `ConcessionariaNotifier` ("chegou o {modelo} {ano} que você procurava, por {preço}. Quer agendar um test-drive?"). Painel: aba "Interesses" na tela de Leads mostrando as buscas ativas.
- **Dependências:** nenhuma bloqueante — usa Evolution/outbound que já existe. Ganha muito quando o site público (feature 11) alimentar wishlists também.
- **Métrica de sucesso:** nº de test-drives/leads originados de alerta de estoque; taxa de conversão de alerta → agendamento.

### 2. Reativação automática de lead parado (follow-up)

- **Problema de negócio:** o gargalo real de uma revenda não é gerar lead, é não deixar o lead morrer. Vendedores esquecem follow-up; leads em `em_negociacao` sem interação viram perda silenciosa.
- **Como funciona:** scheduler diário (cron) varre `concessionaria_leads` em status não-terminal cuja última interação passou de N dias (configurável em `concessionaria_config`, ex. 3 dias). Para cada um, dispara um follow-up outbound pela IA — mensagem contextual com o veículo do lead ("ainda pensa naquele {modelo}? Consigo agendar um test-drive essa semana"). A trava é respeitada: não menciona desconto/preço novo, não fecha nada, só reengaja. Registra a tentativa (última_reativacao_at) pra não spammar. Painel: coluna "última interação" + toggle de reativação por lead.
- **Dependências:** scheduler/cron transversal (ver dependências). Sem gateway.
- **Métrica de sucesso:** % de leads reativados que respondem; leads recuperados de `perdido`/inativo pra `em_negociacao`.

### 3. Confirmação/lembrete automático de test-drive

- **Problema de negócio:** no-show de test-drive desperdiça o horário do vendedor e mantém um veículo indisponível pra outro cliente naquele slot. Cada falta é uma oportunidade de venda queimada.
- **Como funciona:** scheduler dispara em D-1 e no dia (X horas antes, configurável) um lembrete pro contato do test-drive `agendado`/`confirmado` ("seu test-drive do {modelo} é amanhã às {hora} com {vendedor}. Confirma?"). O cliente confirma ou pede pra remarcar pela conversa; a IA (dentro da trava — só agenda/reagenda) atualiza. Se não confirmar até X horas antes, marca opcionalmente para acompanhamento manual. Reaproveita `TestDriveStatus` e o notifier existentes.
- **Dependências:** scheduler/cron. Sem gateway.
- **Métrica de sucesso:** queda na taxa de no_show; % de test-drives confirmados via lembrete; test-drives realizados/mês.

### 4. Reserva de veículo com sinal (pagamento real)

- **Problema de negócio:** hoje `reservado` é só uma mudança de status manual, sem compromisso do cliente — não segura ninguém. Um sinal pago cria comprometimento real e tira o carro do mercado pra concorrência, protegendo a venda.
- **Como funciona:** quando o cliente decide reservar, a IA (sem fechar preço — o valor do sinal e o preço vêm do catálogo/config, não da IA) gera um link de pagamento do SINAL. Ao confirmar o pagamento (webhook do gateway), o backend muta o veículo pra `reservado`, materializa `reservation_expires_at` (configurável) e registra o sinal. Se expirar sem fechamento, o scheduler (feature 9) devolve o veículo pra `disponivel`. Painel: veículo reservado mostra sinal pago + validade.
- **Dependências:** **gateway de pagamento #50** (bloqueante). A trava se mantém: a IA não define preço, só encaminha o link do valor já configurado.
- **Métrica de sucesso:** nº de reservas com sinal; taxa de reserva-com-sinal → venda fechada; redução de reservas "furadas".

### 5. Proposta/avaliação de usado na troca (trade-in)

- **Problema de negócio:** uma fatia enorme dos compradores só fecha se der o carro atual na troca. Sem um fluxo de trade-in, esse cliente é atendido de forma amadora ou perdido pra loja que avalia o usado.
- **Como funciona:** nova tabela `concessionaria_tradein_offers` (contact, veículo de interesse, dados do usado do cliente: marca/modelo/ano/km/estado — texto/estruturado, valor pretendido). A IA COLETA os dados do usado e registra a intenção via tag `<troca_carro>` (sem avaliar nem prometer valor — a trava proíbe a IA de precificar; a avaliação é humana). A equipe avalia no painel e responde uma proposta de abatimento. Vira insumo do lead (o lead ganha referência ao trade-in). Opcional: integração FIPE apenas como referência interna pro vendedor (não exposta como promessa ao cliente).
- **Dependências:** nenhuma bloqueante pro fluxo básico; FIPE é integração opcional. Foto do usado depende de upload liberado (transversal).
- **Métrica de sucesso:** nº de propostas de troca abertas; conversão trade-in → venda; ticket médio com troca.

## Dependências transversais

- **Gateway de pagamento (#50):** destrava **#4 reserva com sinal** por completo. Sem ele, a reserva continua sendo mudança de status manual sem compromisso financeiro.
- **Upload de foto/anexo (SERVICE_ROLE_KEY hoje ausente):** destrava **#16 galeria real de fotos** e a foto do usado em **#5 trade-in**. Enquanto bloqueado, tudo segue por link colado (`photo_url`).
- **Scheduler/cron (infra de auto-transição e disparo temporal):** é a espinha de **#2 reativação de lead**, **#3 lembrete de test-drive**, **#9 auto-transição**, **#12 aniversário/revisão** e a expiração de reserva de **#4**. Uma vez existente, todas essas se tornam P.
- **Motor de campanha em massa segmentada:** destrava **#6 campanha por estoque parado/faixa de preço** e potencializa **#1 wishlist** e **#8 indicação**. Precisa de segmentação de contatos + fila de outbound com rate-limit no Evolution.
- **Site público/CMS por tenant (feature flag CMS já existe na plataforma):** ao ligar o CMS pro nicho, **#11 vitrine pública com estoque ao vivo** vira um consumo direto do CMS + endpoint público do estoque disponível, alimentando wishlists e leads 24h.
