# Perfil Eventos — EventosBot (camada 8.2)

Guia operacional do tenant **eventos** (`profile_id='eventos'`). Casa de festas / buffet /
cerimonial / espaço de eventos: a equipe gerencia cerimonialistas, abre propostas (orçamento +
cronograma do dia) e a IA atende clientes pelo WhatsApp — abre a proposta a partir do briefing e
captura a aprovação.

É o **12º perfil vertical** (13º contando o generic). CLONA o chassi do OficinaBot (proposta = OS,
itens = os_items, aprovação em 2 fases) e inaugura o **cronograma ordenado do dia do evento**.

## Telas (sidebar "Eventos")

| Tela | Rota | O que faz |
|------|------|-----------|
| Cerimonialistas | `/dashboard/eventos-planners` | CRUD do catálogo de responsáveis. Atribuir um cerimonialista à proposta é opcional. Excluir um que tem proposta → bloqueado (desative). |
| Propostas | `/dashboard/eventos-proposals` | Lista por status; abrir proposta; detalhe com os DOIS editores inline (orçamento + cronograma) e os botões de transição de status. |
| Configurações | `/dashboard/eventos-settings` | Nome do espaço/buffet + observações. Sem horário (não há agenda). |

## Como funciona a proposta

A proposta é o artefato central (espelho da ordem de serviço da oficina). Tem **dois tipos de
sub-item no mesmo artefato**, que não se misturam:

1. **Itens de ORÇAMENTO** — descrição + quantidade + preço unitário. O `total` da proposta é
   recalculado automaticamente a cada item adicionado/removido (espaço, buffet, decoração, etc.).
   **Entram no total.**
2. **Marcos de CRONOGRAMA** — horário + título (+ descrição opcional) do dia do evento (ex.:
   "19:00 recepção", "20:00 cerimônia", "23:00 festa"). Aparecem **ordenados por horário**,
   independente da ordem em que foram adicionados. **NÃO entram no total** — são o roteiro do dia.

### Estados da proposta

```
rascunho  → orcada, cancelada
orcada    → aprovada, recusada, cancelada
aprovada  → fechada, cancelada
fechada   → realizada, cancelada
realizada → (final)
recusada  → (final)
cancelada → (final)
```

- **rascunho** — proposta aberta, sem orçamento ainda (a IA abre aqui).
- **orcada** — a equipe montou o orçamento (≥1 item) e enviou ao cliente; aguardando a resposta.
  Só é possível ir para "orcada" com **total > 0** (não dá pra orçar sem item).
- **aprovada** — o cliente aceitou (pela conversa ou no painel).
- **fechada** — contrato fechado / sinal combinado fora do app.
- **realizada** — a festa aconteceu.
- **recusada / cancelada** — encerramento alternativo.

**Notificações automáticas ao cliente** (se houver vínculo com o WhatsApp): **orçada** (com o
total), **aprovada**, **fechada**, **recusada**. Os estados rascunho/realizada/cancelada são
silenciosos.

**Trava de edição:** os itens (orçamento E cronograma) só podem ser editados enquanto a proposta
está em **rascunho/orçada/aprovada**. Depois de **fechada** o escopo congela — o editor some no
painel e a API recusa alterações.

## O que a IA faz pelo WhatsApp

- Identifica o cliente pelo telefone, conversa em linguagem natural sobre o evento e **ABRE a
  proposta** a partir do briefing (tipo de evento, data prevista, número de convidados, o que o
  cliente imagina). A proposta nasce em **rascunho** — sem itens; a equipe monta o orçamento no
  painel.
- Quando a equipe coloca a proposta em **orçada**, o cliente recebe o total pelo WhatsApp. Se ele
  responder aprovando ou recusando, a IA **captura a decisão** e move a proposta para
  **aprovada/recusada** (só funciona enquanto a proposta está "orçada").

## O que a IA NÃO faz (cravado)

- **Não fecha contrato, preço ou desconto** — quem orça e fecha é a equipe no painel.
- **Não confirma disponibilidade de data** que não esteja confirmada — diz "vou verificar a
  disponibilidade com a equipe".
- **Não inventa** item de pacote, valor ou serviço, nem promete estrutura/comodidade do espaço que
  não tenha sido informada.
- **Não move** a proposta para "fechada"/"realizada" (transições administrativas do painel). A IA
  só ABRE a proposta e CAPTURA a aprovação/recusa.

## O que NÃO existe nesta fase

Conflito de agenda/data (a data é um campo livre — a casa faz ~1 evento por data), catálogo de
pacotes pré-cadastrados (o orçamento é ad-hoc, a equipe digita os itens), contrato com assinatura
digital/PDF, pagamento/sinal/parcelas, fornecedores externos com agenda própria, fotos/mood board,
lista de convidados/RSVP. Tudo fase futura.

## Onda 1 do backlog (migration 107)

Entregue a partir de `docs/FEATURES_SUGERIDAS_EVENTOS.md` (#2, #3, #6, #7, #8 e #9; o #11 —
qualificação com nº de convidados — já existia no baseline via `guest_count` na tag):

- **#2 CATÁLOGO DE PACOTES/ADICIONAIS** (`event_packages`): Prata/Ouro/Diamante + hora extra/open
  bar. Tela "Pacotes" (CRUD, kind pacote|adicional) + **autofill** no editor de orçamento da
  proposta (select preenche descrição+preço; o item continua snapshot). O contexto da IA lista o
  catálogo — ela DESCREVE pacotes e valores, mas quem monta/fecha o orçamento segue sendo a
  equipe (trava intacta).
- **#9 UPSELL CONSULTIVO:** `suggestible` no pacote — a IA pode mencionar UM adicional marcado
  quando combinar com o briefing (sem insistir, sem desconto).
- **#3 AVISO DE DATA OCUPADA:** `GET /api/eventos/proposals/date-check?date=&excludeId=` (conta
  aprovada/fechada/realizada na data) → aviso NÃO bloqueante no modal de abertura (⚠ âmbar). O
  contexto da IA lista as DATAS RESERVADAS dos próximos 180 dias com a instrução: avisar quando a
  data pedida está ocupada e NUNCA afirmar que uma data está livre (disponibilidade é da equipe).
- **#6 AUTO-REALIZADA:** `EventosReminderJob` (cron 10:30) move `fechada` com `event_date`
  passada → `realizada`. Toggle `auto_complete_enabled` (default ON).
- **#7 PÓS-VENDA:** ao entrar em `realizada` (manual no funil OU pela auto-transição),
  agradecimento + `review_link` (se houver) + convite de indicação. Toggle `post_event_enabled`.
- **#8 FOLLOW-UP DE ORÇAMENTO PARADO:** proposta `orcada` há `follow_up_days` (default 3) sem
  resposta → 1 toque por episódio (`follow_up_sent_at` vs `status_updated_at`; re-orçar REARMA).
  Funil ativo (não é disparo em massa) → default ON.

Settings ganhou a seção "Automações" (2 toggles + review link + follow-up). Teste:
`EventosOnda1IntegrationTest` (date-check; auto-realizada + pós-venda + toggles; follow-up por
episódio).

**Fica pra onda 2** (registrado, não pedido): #1 sinal/depósito e #4/#5 parcelamento+lembrete de
pagamento (bloqueados pelo gateway #50), #10 contrato PDF, #12 campanha em massa, #13 RSVP,
#14 fornecedores/comissão, #15 CMS do espaço (flag cms), #16 mood board (bloqueado por upload).
