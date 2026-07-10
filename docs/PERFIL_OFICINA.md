# OficinaBot — guia operacional da oficina (camada 7.9)

O OficinaBot é o produto do Meada para oficinas mecânicas e auto centers. Seus clientes falam pelo
WhatsApp; a IA atende com tom prestativo e direto, identifica o veículo, ABRE a ordem de serviço a
partir da queixa, informa o orçamento quando o mecânico já o montou e captura a aprovação do cliente.
Você gerencia mecânicos, veículos e as ordens de serviço (com itens e orçamento) pelo painel.

> **Importante:** o OficinaBot organiza e agiliza — ele **não** diagnostica o defeito, **não** inventa
> preço de peça nem monta orçamento (quem orça é o mecânico, no painel) e **não** promete prazos que
> não estejam na OS. Para qualquer dúvida técnica, a IA orienta a avaliação presencial.

## 1. Mecânicos (`/dashboard/oficina-mechanics`)

- **Novo mecânico:** nome e especialidade (ex.: "motor/suspensão", "elétrica/ar").
- Atribuir um mecânico a uma OS é **opcional**.
- **Ativo/inativo** e **excluir** (bloqueado se o mecânico estiver em alguma OS — 409; desative-o).

## 2. Veículos (`/dashboard/oficina-vehicles`)

- Cada veículo pertence a um **cliente** — que é um contato do WhatsApp. Um cliente pode ter vários
  veículos.
- **Novo veículo:** escolha o cliente (contato), informe a **placa** (obrigatória, única na oficina) e,
  opcionalmente, marca, modelo, ano, cor e quilometragem.
- **Arquivar** (preferido a excluir): tira o veículo da lista ativa sem perder o histórico de OS.
  **Excluir** é bloqueado se houver OS (409).
- A IA também cadastra veículos sozinha: quando um cliente novo descreve um problema, ela pergunta a
  placa/marca/modelo/ano e cadastra junto com a abertura da OS.

## 3. Ordens de serviço (`/dashboard/oficina-orders`)

- **Lista por status** com filtro. Cada OS mostra placa, cliente, queixa, mecânico e total.
- **Nova OS (manual):** escolha o veículo, opcionalmente o mecânico, e descreva a queixa. A OS nasce
  em **aberta**, com total zero.
- **Detalhe — editor de itens:** adicione **peças** e **mão de obra** (descrição, quantidade, preço
  unitário). O total é recalculado automaticamente a cada item. Itens só podem ser alterados enquanto
  a OS não entrou em execução (depois disso o editor é bloqueado).
- **Fluxo de status:**
  - `aberta → orcada` (precisa de **pelo menos um item** — não dá pra orçar OS vazia);
  - `orcada → aprovada / recusada` (normalmente o cliente decide pelo WhatsApp);
  - `aprovada → em execução → concluída → entregue`;
  - `cancelada` a partir de qualquer estado não-final.
- **Notificações automáticas** ao cliente (se veio do WhatsApp): ao **orçar** (com o total), **aprovar**,
  **concluir** e **entregar**.

## 4. Configurações (`/dashboard/oficina-settings`)

- **Horário de funcionamento** — apenas informativo (a oficina trabalha por ordem de serviço, não por
  agendamento de horário).

## 5. Como a IA atende (e o gate de aprovação em 2 fases)

A IA conhece os mecânicos, os veículos de cada cliente e as OS em aberto do cliente. No WhatsApp ela:

1. Identifica o cliente pelo telefone e o veículo (oferece os já cadastrados ou cadastra um novo).
2. **ABRE a OS** a partir da queixa — sem diagnosticar nem orçar.
3. Quando o mecânico já lançou o orçamento e você muda a OS para **orçada**, o cliente recebe o total.
4. O cliente responde pelo WhatsApp; a IA **captura a aprovação** e muda a OS para **aprovada** (ou
   **recusada**) — esse é o único momento em que a IA altera o estado de uma OS já existente.

## 6. O que o OficinaBot NÃO faz (ainda)

Catálogo de peças/serviços com preço pré-cadastrado (o orçamento é digitado pelo mecânico),
agendamento de entrada por horário, integração com tabela FIPE, foto do veículo/avaria, pagamento
online, nota fiscal e controle de estoque. Esses são temas de fases futuras.

## Onda 1 do backlog (2026-07 — FEATURES_SUGERIDAS_OFICINA #1/#2, migration 98)

- **Catálogo de peças/serviços tabelados (#1, `oficina_catalog_items`, clone do catálogo
  atelie):** nome + categoria livre + preço padrão + ativo; CRUD `/api/oficina/catalog` + tela
  `/dashboard/oficina-catalog` + item "Catálogo" no nav. DESTRAVA a IA: o contexto injeta os
  tabelados e a tag `<ordem_servico>` ganha o campo OPCIONAL `servicos:[{id,qtd}]` — o
  `openWithCatalogItems` resolve do catálogo (só ativos, preço do tenant; inválido é ignorado,
  best-effort) e a OS nasce 'aberta' já com os itens (o mecânico revisa e orça). Trava intacta:
  a IA continua sem inventar preço; queixa com diagnóstico abre sem itens.
- **Lembrete de retorno/revisão (#2, `OficinaReminderJob`, cron 10h30):** a ENTREGA materializa
  `next_return_date = hoje + return_reminder_days` (config, default 180); no vencimento o
  cliente recebe "hora da revisão do {modelo/placa}?" — 1x por OS (`return_reminded_at`).
  Toggle + janela em Configurações.
