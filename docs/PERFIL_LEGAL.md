# ProcessoBot — guia operacional do escritório (camada 7.2)

O ProcessoBot é o produto do Meada para escritórios de advocacia. Seus clientes perguntam pelo
WhatsApp sobre os processos; a IA responde resumindo os andamentos, sempre orientando a procurar
o advogado para dúvidas substantivas.

## 1. Cadastrar clientes (`/dashboard/clients`)

- **Novo cliente:** nome (obrigatório), email, telefone, CPF/CNPJ e notas (opcionais).
- **Vínculo com o WhatsApp:** quando o cliente já conversou pelo WhatsApp, o vínculo (contato)
  faz a IA reconhecê-lo automaticamente pelo telefone e responder sobre os processos dele. O
  badge **"vinculado"** indica isso. (O vínculo automático acontece quando o telefone do cliente
  bate com um contato; cadastrar antes do WhatsApp é normal — o vínculo vem depois.)
- **Busca:** por nome, email, telefone ou CPF.
- **Excluir:** bloqueado se o cliente tiver processos (proteção de integridade).

## 2. Cadastrar processos (`/dashboard/cases`)

- **Novo processo:** selecione o cliente, informe o **número CNJ** (a máscara é aplicada
  automaticamente; o sistema valida o dígito verificador — número inválido é recusado), o título
  (uma alcunha, ex.: "Ação Trabalhista vs Empresa X"), e opcionalmente vara, fórum e matéria.
- **Filtros:** chips de status (Ativo/Suspenso/Arquivado/Encerrado) + busca por título/CNJ/cliente.

## 3. Detalhe do processo (`/dashboard/cases/{id}`)

- **Status:** o seletor "Mudar status…" altera o status. **O cliente é notificado
  automaticamente** (se vinculado ao WhatsApp) para suspensão, arquivamento e encerramento — com
  texto fixo e defensivo ("…entre em contato com nosso escritório"). Reativar (ativo) não notifica.
- **Andamentos (timeline):** registre manualmente cada movimentação (título obrigatório, descrição
  e data opcionais; data em branco = hoje). Os andamentos **não** notificam o cliente — são
  técnicos. A IA usa os últimos andamentos para responder "como está meu processo?".
- **Detalhes:** vara, fórum, matéria, descrição.

## 4. Como a IA atende

- Quando um cliente vinculado manda mensagem, a IA já tem a lista de processos dele + os andamentos
  recentes. Ela **resume** o andamento, mas **nunca dá opinião jurídica** — para dúvidas
  substantivas, orienta a consultar o advogado responsável.
- Telefone não reconhecido: a IA pede que a pessoa se identifique (nome/CPF) e informa que vai
  encaminhar ao advogado, sem expor dados de processos.

## Limitações conhecidas (honestas)

- **Sem audiências, prazos com cálculo automático ou alertas** — andamentos são manuais.
- **Sem partes formais** (autor/réu/terceiros), recursos, custas ou peças.
- **Sem anexo/documento** (bloqueador técnico de Storage).
- Os textos de notificação são fixos nesta versão (personalização por escritório: fase futura).

## Onda 1 do backlog (migration 102)

Entregue a partir de `docs/FEATURES_SUGERIDAS_LEGAL.md` (#1 e #3):

- **#1 — Agenda de prazos e audiências com lembrete automático** (`legal_deadlines`): o advogado
  cadastra prazos/audiências por processo (kind `prazo|audiencia`, título, data, hora opcional,
  local opcional, status `pendente|cumprido|perdido` — gestão livre, sem máquina rígida). Tela
  "Prazos" no grupo Escritório (CRUD + filtro por status + mudança de status inline). O
  `LegalDeadlineReminderJob` (cron `${legal.deadline-reminder-cron:0 30 8 * * *}`) varre os
  pendentes com vencimento em **D-3 e D-1** e avisa o CLIENTE vinculado via `LegalCaseNotifier`
  (contato → conversa mais recente; sem vínculo WhatsApp marca sem envio). Texto FIXO com
  data/hora/local — **NUNCA mérito** (trava jurídica intacta). Idempotência por
  (`reminded3_due_date`/`reminded1_due_date` = due_date): **remarcar REARMA** os dois avisos.
  Toggle `deadline_reminder_enabled` (default ON, `coalesce(true)` sem linha de config). A IA
  ganhou no contexto do cliente o bloco "Próximos compromissos" (data/hora/local, até 5 futuros)
  com instrução explícita de não comentar estratégia/desfecho.
- **#3 — Pós-encerramento (agradecimento + avaliação + indicação)** (`legal_config`): ao mudar o
  processo para **encerrado**, além da notificação de status, o `LegalCaseService` encadeia uma
  segunda mensagem de agradecimento com o `review_link` (se configurado) e convite de indicação.
  Toggle `post_closure_enabled` (default ON). Tela "Configurações" do grupo Escritório (link de
  avaliação + os 2 toggles), endpoints `GET/PUT /api/legal/config`.

Endpoints novos: `GET/POST /api/legal/deadlines`, `PATCH/DELETE /api/legal/deadlines/{id}`,
`GET/PUT /api/legal/config` (todos atrás do `LegalProfileGuard`). Teste:
`LegalOnda1IntegrationTest` (janelas D-3/D-1 + idempotência + rearm ao remarcar + status
cumprido/toggle silenciam + pós-encerramento ON/OFF com link).

**Fica pra onda 2** (registrado, não pedido): #2 controle de honorários/parcelas, alerta interno
de prazo ao ADVOGADO (hoje o lembrete é ao cliente), integração com tribunais (push de
andamento), documentos do processo (bloqueador SERVICE_ROLE_KEY).
