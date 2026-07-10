# Features Sugeridas — Delivery de comida

> Backlog de features avançadas para o nicho **Delivery de comida** (profile_id `comida`), priorizado por VALOR DE NEGÓCIO. Objetivo: engordar a cartela de features com o que mais agrega receita/retenção no menor tempo. Baseado no estado REAL do nicho (o que já existe NÃO é repetido aqui).

> **Modo de execução (cravado):** ao implementar qualquer feature deste backlog, faça **tudo em prosa**,
> de forma contínua e autônoma, **sem perguntar nada ao programador**, sem pausas para confirmação e
> **sem usar o widget de perguntas** (AskUserQuestion). Não interrompa o fluxo pedindo aval intermediário:
> decida com base no estado real do código/banco e nas convenções das skills, implemente, e só pare em
> ponto de bifurcação arquitetural genuína ou no gate de teste. Reporte o progresso em prosa corrida.

## O que o nicho já tem (baseline)

- **Cardápio com itens + opções/adicionais (modifiers)** por grupo com `price_delta_cents`; categorias hardcoded (lanches/pizzas/pratos/porções/bebidas/sobremesas/combos).
- **Carrinho-na-conversa via IA:** a IA monta o pedido em linguagem livre, confirma total+endereço e emite a tag `<pedido_comida>`; o backend RECALCULA o total (descarta o da IA).
- **Gate de aceite humano + Kanban:** pedido nasce `aguardando`; a loja aceita (→`em_preparo`) ou recusa; fluxo `em_preparo → saiu_entrega → entregue`; notificação outbound de texto fixo por status.
- **Taxa de entrega flat + pedido mínimo** por company (tela Configurações).
- **Snapshot** de preço/opções no item do pedido (histórico preservado).
- **NÃO tem hoje:** pagamento online, cupom/promoção, fidelidade, avaliação/NPS, foto, rastreio/ETA, endereços salvos, horário próprio do delivery, taxa por bairro, campanha/reativação, retirada no balcão, agendamento.

## 🏆 Top 3 quick wins (fazer primeiro)

**1. Cupom de desconto + pedido mínimo por cupom (chassi já existe no sushi).** O sushi (7.1) JÁ tem `sushi_coupons` com kind percent/fixed, min_order, max_uses, valid_until — é clonar a tabela pra `comida_coupons` e ensinar a IA a receber o código na tag `<pedido_comida>`, validar (ativo+validade+mínimo+usos) e aplicar (cupom inválido não aborta, só sai sem desconto). Vende porque cupom é a alavanca #1 de conversão em delivery: recupera carrinho na hesitação ("usa PRIMEIRAPEDIDO e ganha 15%"), viabiliza campanha de primeira compra e recompra. Esforço P (código pronto no sushi), ROI altíssimo.

**2. Fidelidade por contagem de pedidos (cashback / pedido grátis).** Também já existe no sushi (`sushi_loyalty_config`: threshold_orders + reward): a cada N pedidos entregues do mesmo contato, o (N+1)º ganha desconto automático. Em delivery o cliente pede toda semana — programa de fidelidade é o que trava a migração pro concorrente e aumenta a frequência. A IA anuncia o progresso ("faltam 2 pedidos pro seu desconto") sem inventar valor. Esforço P/M, retenção direta.

**3. Retirada no balcão (fulfillment retirada × entrega) — dispensa taxa e destrava público sem entrega.** O sushi já modela `fulfillment` (entrega exige endereço + taxa; retirada = balcão, sem taxa). Muitos clientes preferem buscar (sem taxa, mais rápido) e muitos estabelecimentos de bairro nem entregam. Adicionar retirada aumenta o ticket convertido (cliente que abandonaria por causa da taxa) e amplia o público. Esforço P, valor imediato.

## Backlog priorizado (16 features)

| # | Feature | Valor de negócio | Esforço | O que resolve pro cliente | Eixo |
|---|---------|------------------|---------|---------------------------|------|
| 1 | Cupom de desconto (percent/fixed, mínimo, validade, max usos) | Alto | P | Converte hesitação e viabiliza campanha de 1ª compra/recompra | Receita |
| 2 | Fidelidade por contagem (Nº pedido → desconto/grátis) | Alto | P | Trava o cliente na marca, aumenta frequência de pedido | Retenção |
| 3 | Retirada no balcão (fulfillment retirada × entrega, sem taxa) | Alto | P | Ganha o cliente que abandona pela taxa; amplia público | Receita |
| 4 | Upsell/cross-sell proativo da IA (adicional, bebida, sobremesa) | Alto | P/M | Sobe o ticket médio a cada pedido sem esforço do dono | IA |
| 5 | Reativação de cliente inativo (campanha "sumido há 30d") | Alto | M | Traz de volta receita que já estava perdida | Retenção |
| 6 | Avaliação/NPS pós-entrega (nota + comentário) | Alto | P/M | Prova social + sinal de qualidade operacional | Marketing |
| 7 | Pagamento online / Pix na conversa (link no fechamento) | Alto | G | Reduz calote e no-show; fecha o ciclo de venda | Receita |
| 8 | Taxa de entrega por bairro/zona (hoje é flat) | Médio | P/M | Precifica entrega corretamente; para de subsidiar longe | Receita |
| 9 | Horário de funcionamento próprio do delivery + fila fora do horário | Médio | P | Evita pedido que a loja não consegue atender; menos recusa | Operação |
| 10 | Endereços salvos por contato (reuso no próximo pedido) | Médio | P | Pedido em 1 toque; menos atrito de digitar endereço | Retenção |
| 11 | Combos/promoções montadas + preço promocional agendado | Médio | M | Escoa item parado, sobe ticket com "combo do dia" | Receita |
| 12 | Scheduler de auto-transição + lembrete de pedido parado | Médio | M | Kanban não trava; loja não esquece pedido em preparo | Operação |
| 13 | Agendamento de pedido (pedir agora pra depois / horário) | Médio | M | Captura pedido de almoço/jantar programado; suaviza pico | Operação |
| 14 | Página pública/cardápio digital (CMS já existe como feature flag) | Médio | M | Link compartilhável do cardápio; vitrine sem app | Marketing |
| 15 | Relatórios de vendas (ticket médio, top itens, horário de pico) | Médio | M | Decisão de cardápio e escala com dado, não achismo | Operação |
| 16 | Aniversário do cliente (cupom automático no mês) | Médio | P | Toque de relacionamento que gera pedido recorrente | Retenção |

## Detalhamento das prioritárias

### 1. Cupom de desconto

- **Problema de negócio:** o delivery hoje não tem NENHUMA alavanca de preço. Não dá pra rodar "primeira compra 15% off", "cupom de recompra", nem promoção de horário morto. Cupom é a ferramenta de conversão mais barata e universal do delivery.
- **Como funciona:** clonar `sushi_coupons` → `comida_coupons` (kind percent 1..100 / fixed centavos, min_order, max_uses, valid_until, active, uses). Tela "Cupons" (CRUD). A IA passa o código `cupom` como campo OPCIONAL na tag `<pedido_comida>`; o backend VALIDA (ativo + validade + mínimo + max_uses) e aplica ao subtotal; **cupom inválido NÃO aborta** o pedido (sai sem desconto, mensagem gentil). `uses` incrementa na criação. `discount_cents` + `coupon_code_snapshot` na order. Respeita a trava: a IA NÃO inventa cupom nem valor — só repassa o código que o cliente digitou.
- **Dependências:** nenhuma (código pronto no sushi; migration aditiva).
- **Métrica de sucesso:** taxa de conversão de pedido com cupom vs sem; % de novos contatos que usam cupom de 1ª compra.

### 2. Fidelidade por contagem

- **Problema de negócio:** em delivery o cliente pede toda semana e troca de loja por qualquer motivo. Sem programa de recompensa, cada pedido é uma disputa nova. Fidelidade cria custo de troca.
- **Como funciona:** clonar `sushi_loyalty_config` (1:1 com company: enabled + threshold_orders + reward percent/fixed). O backend conta os pedidos ENTREGUES (terminal não-cancelado) do contato ANTES de inserir; quando `count>0 && count%threshold==0` → desconto automático + `loyalty_applied=true` na order. A IA anuncia o progresso no contexto ("é seu 4º pedido — no 5º você ganha 20%"), sem inventar número. Cupom + fidelidade SOMAM, clampados ao subtotal (mesma regra do sushi).
- **Dependências:** nenhuma (pronto no sushi).
- **Métrica de sucesso:** frequência média de pedido por contato ativo; % de contatos que atingem o threshold.

### 3. Retirada no balcão (fulfillment)

- **Problema de negócio:** hoje TODO pedido é entrega com taxa flat. O cliente que mora perto (ou a loja que não entrega) perde a venda por causa da taxa/logística. Retirada é receita adicional de baixo custo.
- **Como funciona:** adicionar `fulfillment` ('entrega'|'retirada') na order (espelho do sushi). Entrega exige `delivery_address` (422 `address_required`) + soma taxa; retirada = sem taxa, endereço opcional. A IA pergunta "entrega ou retirada?" no fechamento e inclui o campo na tag. `total = subtotal − desconto + taxa` (entrega) ou `subtotal − desconto` (retirada). O Kanban mostra um selo "Retirada" no card (a loja separa, não despacha).
- **Dependências:** nenhuma.
- **Métrica de sucesso:** % de pedidos em retirada; ticket recuperado de carrinhos que seriam abandonados pela taxa.

### 4. Upsell/cross-sell proativo da IA

- **Problema de negócio:** o adicional (bacon, borda, bebida, sobremesa) é a margem mais gorda e o cliente esquece de pedir. Um garçom bom sempre oferece; a IA hoje é passiva.
- **Como funciona:** na persona/contexto do comida, instruir a IA a oferecer UMA sugestão relevante ANTES de fechar — bebida se o carrinho não tem, sobremesa depois do prato, adicional do próprio item — **sempre a partir do cardápio real** (nunca inventa item/preço). Marcar itens do cardápio como "sugerível" (flag `suggest_upsell` opcional) pra a loja controlar o que a IA empurra. Uma oferta só, sem insistência (respeita o cliente).
- **Dependências:** nenhuma (é persona + 1 flag opcional no item).
- **Métrica de sucesso:** ticket médio antes/depois; taxa de aceite da sugestão.

### 5. Reativação de cliente inativo (campanha segmentada)

- **Problema de negócio:** a base de contatos que já pediu e sumiu é o ativo mais barato de reconquistar — mas hoje não há nenhum jeito de falar com ela em massa. Cada inativo é receita recorrente perdida.
- **Como funciona:** feature de plataforma (campanha em massa segmentada por outbound Evolution): segmentar contatos por "último pedido há > N dias" e disparar mensagem com cupom de volta ("sentimos sua falta — 20% no próximo pedido"). Depende da infra de campanha em massa (transversal) e do cupom (#1). A trava: opt-out respeitado, sem spam; disparo com throttle pra não queimar o número no WhatsApp.
- **Dependências:** cupom (#1) + infra de campanha em massa (transversal) + scheduler.
- **Métrica de sucesso:** taxa de reativação (inativo que volta a pedir em 14d após a campanha); receita atribuída.

## Dependências transversais

- **Gateway de pagamento (#50, global):** destrava **#7 (pagamento/Pix na conversa)** e reforça **#13 (agendamento com pré-pagamento)** e **#3/#11** (sinal/antecipado). Enquanto #50 não existe, tudo segue "pagar na entrega/retirada".
- **Upload de foto/anexo (SERVICE_ROLE_KEY):** destrava **foto de item no cardápio** e **foto no cardápio digital/CMS (#14)**. Alto valor de conversão (comida vende pela imagem), mas bloqueado hoje — marcar como "aguarda SERVICE_ROLE_KEY".
- **Scheduler/cron:** destrava **#12 (auto-transição + lembrete de pedido parado)**, **#5 (reativação agendada)**, **#13 (agendamento)** e **#16 (cupom de aniversário)**. É a peça que transforma features reativas em proativas.
- **Infra de campanha em massa segmentada:** destrava **#5 (reativação)** e **#16 (aniversário)** e qualquer marketing outbound futuro (promoção do dia, novo item). É o multiplicador de todas as features de retenção.
