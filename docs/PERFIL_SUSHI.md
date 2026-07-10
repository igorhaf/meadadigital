# SushiBot — guia operacional do restaurante (camada 7.1)

O SushiBot é o produto do Meada para restaurantes de sushi. Seus clientes pedem pelo WhatsApp em
linguagem natural; a IA atende, monta o pedido e você o acompanha num Kanban.

## 1. Cadastrar o cardápio (`/dashboard/menu`)

- **Novo item:** nome, descrição (opcional), preço (em R$) e categoria (Entradas, Hot rolls,
  Sashimi, Combinados, Bebidas, Sobremesas).
- **Disponível:** o checkbox liga/desliga o item. A IA só oferece itens **disponíveis** — desligue
  um item que acabou sem precisar excluí-lo.
- **Excluir:** remove o item. Se ele já aparece em algum pedido, o sistema bloqueia a exclusão
  (para não corromper o histórico) — nesse caso, apenas desative.
- A IA passa a usar o cardápio novo em até ~1 minuto (cache), ou na hora ao salvar.

## 2. Taxa de entrega e pedido mínimo

Configurados no nível do restaurante (taxa fixa + pedido mínimo). A IA soma a taxa ao total e
avisa o cliente se o pedido ficar abaixo do mínimo (mas não recusa). *(Tela de configuração:
fase futura; hoje é semeado no banco.)*

## 3. Como a IA atende

- O cliente escreve o que quer (“quero 2 Filadélfia e 1 California”). A IA entende, sugere
  combinações e vai montando o pedido **na própria conversa**.
- Quando o cliente confirma **e informa o endereço**, a IA fecha o pedido: confirma o resumo
  (itens, total com taxa, endereço) e o pedido entra no Kanban como **Recebido**.
- A IA nunca mostra códigos internos ao cliente — só a confirmação humana.

## 4. Kanban de pedidos (`/dashboard/orders`)

- **Em andamento:** três colunas — Recebido → Em preparo → Saiu pra entrega.
- **Avançar:** o botão move o pedido para o próximo status. A cada mudança, **o cliente é
  notificado automaticamente** no WhatsApp (ex.: “Seu pedido saiu pra entrega…”).
- **Cancelar:** disponível em qualquer pedido não-finalizado; pede confirmação e notifica o cliente.
- **Histórico:** a aba mostra os pedidos entregues e cancelados.
- A tela atualiza sozinha a cada 30 segundos.

## 5. Fluxo de status

```
recebido → preparo → saiu_pra_entrega → entregue
   └──────────┴──────────────┴────────→ cancelado (de qualquer um acima)
```

Mensagens automáticas por status: preparo, saiu pra entrega, entregue e cancelado. (Os textos
são fixos nesta versão; personalização por restaurante vem em fase futura.)

## Limitações conhecidas (honestas)

- **Sem foto no cardápio** (bloqueador técnico de Storage). Só texto por enquanto.
- **Carrinho não tem tela** — vive na conversa; o cliente confirma por mensagem.
- **Endereço é texto livre** (sem mapa/CEP).

## Onda 2 do backlog (2026-07 — FEATURES_SUGERIDAS_SUSHI #2/#3, migration 88)

- **Upsell/cross-sell proativo (#2):** com `upsell_enabled` (default LIGADO) o bloco do
  `SushiMenuCache` instrui a IA a oferecer NO MÁXIMO 1 item complementar do próprio cardápio
  disponível (categoria ausente no carrinho — bebida/sobremesa/combo maior) antes de emitir a
  tag `<pedido>`; uma oferta só, sem insistir, nunca item fora do cardápio. Toggle em Configurações.
- **Reativação de cliente inativo (#3, `SushiReactivationJob`):** cron diário (10h20) varre os
  contatos cujo último pedido ENTREGUE (terminal não-cancelado) é anterior a `reactivation_days`
  (default 21) e envia UMA mensagem fixa de reengajamento pela conversa mais recente —
  mencionando o cupom de retorno (`reactivation_coupon_code`) só quando ele existe/está
  ativo/válido em `sushi_coupons`. Idempotência por contato+janela via `sushi_reactivation_log`
  (cooldown = a própria janela). **Opt-in: default DESLIGADO** (lição do incidente Baileys —
  disparo em massa é decisão consciente; EVOLUTION_DRY_RUN honrado). Config em Configurações.
