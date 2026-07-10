# Perfil Estética — EsteticaBot (camada 8.3)

Guia operacional do tenant **estetica** (`profile_id='estetica'`). Clínica de estética (facial/
corporal, drenagem, limpeza de pele, depilação a laser): a equipe gerencia profissionais e
procedimentos, vende pacotes de sessões, e a IA atende clientes pelo WhatsApp — agenda sessões
(consumindo o saldo de um pacote) e captura a intenção de compra de pacotes.

É o **13º perfil vertical** (14º contando o generic) e o mais completo até agora: clona a agenda por
profissional do SalãoBot e inaugura o **pacote multi-sessão com saldo que decrementa**.

## Telas (sidebar "Estética")

| Tela | Rota | O que faz |
|------|------|-----------|
| Profissionais | `/dashboard/estetica-professionals` | CRUD da equipe. Conflito de agenda é por profissional. |
| Procedimentos | `/dashboard/estetica-procedures` | CRUD com duração + **preço por sessão** (base do total do pacote). |
| Pacotes | `/dashboard/estetica-packages` | A tela da escapada: lista por status com o saldo (restantes/total), cria pacotes e ativa/cancela. |
| Agenda | `/dashboard/estetica-appointments` | Agenda por dia; cria agendamento avulso; abre a ficha de cada sessão; transição de status. |
| Configurações | `/dashboard/estetica-settings` | Horário de funcionamento + granularidade de slot. |

## O pacote (a escapada)

Um pacote é um **saldo pré-pago de sessões** de um procedimento. Funciona assim:

1. **Compra** (no painel ou capturada pela IA): escolhe-se o procedimento e o número de sessões. O
   total é calculado automaticamente (**sessões × preço do procedimento** — a IA nunca inventa
   valor). O pacote nasce **pendente**.
2. **Ativação**: quando a clínica confirma o pagamento, muda o pacote para **ativo**. Só um pacote
   ativo libera o agendamento que consome saldo. O cliente é avisado na ativação.
3. **Consumo**: cada agendamento ligado ao pacote **abate 1 sessão** do saldo. O saldo restante é
   recalculado na hora. Quando chega a zero, o pacote vira **esgotado** automaticamente.
4. **Devolução**: se um agendamento que consumiu uma sessão for **cancelado**, a sessão **volta** ao
   saldo (e um pacote esgotado volta a ficar ativo).

### Estados do pacote

```
pendente  → ativo, cancelado
ativo     → esgotado (automático ao zerar), expirado, cancelado
esgotado / expirado / cancelado → (final)
```

O painel só oferece as transições manuais (ativar, expirar, cancelar). "Esgotado" e a reabertura
"esgotado→ativo" são automáticos do sistema, ligados ao consumo/devolução de sessão.

## O agendamento

- **Conflito por profissional**: dois clientes no mesmo horário com profissionais diferentes não
  conflitam; com o mesmo profissional, sim (409).
- **Com pacote**: a IA agenda consumindo 1 sessão de um pacote ativo do cliente. Se o pacote estiver
  esgotado → recusa (`package_exhausted`); de outro cliente → `package_wrong_contact`; não ativo →
  `package_not_active`.
- **Avulso**: agendamento sem pacote (o POST manual no painel é sempre avulso) — não mexe em saldo.
- **Ficha/evolução**: cada sessão tem uma ficha textual (área tratada, parâmetros do aparelho,
  observações). É registro administrativo — **sem foto** e **sem dado clínico sensível** nesta
  versão. Não editável se o agendamento foi cancelado.

Estados do agendamento: `agendado → confirmado → realizado`; `agendado/confirmado → cancelado`;
`confirmado → falta`. Notificam o cliente: **confirmado** e **cancelado**.

## O que a IA faz pelo WhatsApp

- Identifica o cliente pelo telefone, mostra procedimentos e pacotes, e **agenda sessões** — quando o
  cliente tem um pacote ativo, agenda consumindo o saldo; senão, agenda avulso.
- **Captura a intenção de compra** de um pacote (procedimento + número de sessões). O pacote nasce
  pendente; a clínica confirma o pagamento no painel.

## O que a IA NÃO faz (trava estética, cravado)

- **Não indica nem recomenda** procedimento ("para isso a profissional vai te avaliar").
- **Não opina** sobre o corpo, a aparência ou "o que o cliente precisa".
- **Não promete resultado** ("vai sumir", "fica perfeito").
- **Não confirma pagamento** de pacote (quem confirma é a clínica).
- **Não inventa preço** — usa o preço de cada procedimento do catálogo.
- **Não discute contraindicação** ou condição de saúde — encaminha à avaliação presencial.

## O que NÃO existe nesta fase

Foto antes/depois, prontuário/anamnese estruturada (dado sensível — fase futura com criptografia),
pagamento real do pacote (Stripe), assinatura/recorrência de pacote, comissão de profissional,
estoque de produtos. Tudo fase futura.

## Onda 1 do backlog (migration 108)

Entregue a partir de `docs/FEATURES_SUGERIDAS_ESTETICA.md` (#1, #2, #3 e #4):

- **#1/#2 LEMBRETE DE VÉSPERA + CONFIRMAÇÃO SIM/NÃO:** o `EsteticaReminderJob` (cron
  `${estetica.reminder-cron:0 50 10 * * *}`) lembra a cliente na véspera ("Confirma? SIM ou
  NÃO") — marker `reminded_start_at` (remarcar REARMA). A resposta fecha o loop via
  `ConfirmacaoEsteticaHandler` (`<confirmacao_estetica>{appointment_id, decisao}`, barreira de
  contato); o **NÃO cancela e DEVOLVE a sessão ao pacote** pela mecânica existente do
  updateStatus. Toggle `reminder_enabled` (default ON). Trava intacta (tudo operacional).
- **#4 AUTO-TRANSIÇÕES:** confirmado vencido → realizado (silencioso, `auto_complete_enabled`)
  e pacote ATIVO com `valid_until` vencida → EXPIRADO (`auto_expire_enabled`) — a data que
  faltava pro estado que já existia. `valid_until` é **materializada na ativação** quando
  `package_validity_days` está configurado (em Java) e aparece no card do pacote no painel.
- **#3 RÉGUA DE RENOVAÇÃO** (opt-in `renewal_enabled` **OFF por default** — lição Baileys):
  pacote ESGOTADO há `renewal_days` sem pacote novo do contato (quem já recomprou é suprimido)
  OU pacote ATIVO a vencer em `expiry_warning_days` → 1 toque por pacote
  (`renewal_reminded_at`). A resposta cai no fluxo `<compra_pacote>` existente.

Settings ganhou a seção "Automações" (3 toggles + validade + régua). Teste:
`EsteticaOnda1IntegrationTest` (lembrete+rearm+confirmação com devolução de saldo;
auto-transições; régua opt-in com supressão por recompra).

**Fica pra onda 2** (registrado, não pedido): #5 pagamento/sinal do pacote com ativação
automática (bloqueado pelo gateway #50), #6 assinatura/recorrência, #7 NPS pós-sessão,
#8 fidelidade/cashback, #11 lista de encaixe, #14 cupom, #15 aniversário.
