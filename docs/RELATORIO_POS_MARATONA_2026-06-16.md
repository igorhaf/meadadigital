# Relatório Pós-Maratona — Meada WhatsApp

**Gerado em:** 2026-06-16
**HEAD:** `4150f6a` (sincronizado com `origin/main` — 0/0)
**Maratona:** 8 commits (5.16 → 5.25), 34 features novas + #6 da pré-maratona

---

## 1. Estado Geral do ROADMAP

| Marcador | Total |
|---|---|
| `[x]` cumpridas | **71** |
| `[ ]` pendentes | **9** |
| `[~]` abandonadas | **1** |
| **Total** | **81 linhas de feature** (50 do MVP original + 31 da seção maratona #51-#92) |

> **Nota de honestidade sobre a contagem:** o ROADMAP tem 50 features originais + 31 da seção "maratona pós-MVP" (#51–#92, alguns números pulados). A tarefa esperava "2 pendentes" — mas há **9**, porque 7 delas **nunca fizeram parte desta maratona**: são o bloco WhatsApp adiado e a cobrança, pendentes desde antes. Só **#62 e #72** são os pendentes *da maratona* (bloqueados por segredo). Detalho abaixo.

---

## 2. Features `[x]` Cumpridas

### Da maratona (34 — esta sessão, commits 5.16–5.25)
- **5.16** (`0160786`): #6 convites multi-user.
- **5.17** (`80d6247`): #75 hierarquia de roles (owner/admin/agent), #61 slots de disponibilidade.
- **5.18** (`fd6f382`): #51 intent cancelamento, #52 intent reclamação (força handoff), #53 extracted_data, #54 sugestões de FAQ, #55 memória de longo prazo, #58 tom dinâmico.
- **5.19** (`3841c09`): #59 calendário visual, #60 IA agenda de fato, #63 lembretes automáticos, #64 remarcar/cancelar via WhatsApp.
- **5.20** (`66d832f`): #76 times/departamentos, #77 limites por plano, #78 audit log visível.
- **5.21+5.22** (`6b70cde`): #81 reativação automática, #82 boas-vindas, #83 notificações tempo real, #84 busca global, #88 saved replies.
- **5.23+5.24** (`933b48b`): #65 export PDF, #66 comparação mês a mês, #68 top contatos, #89 LGPD exclusão, #90 LGPD exportação, #92 logs de acesso.
- **5.25** (`4150f6a`): #57 modo treinamento, #73 widget de chat no site, #74 unificação multi-canal.

### Do MVP original (37 — fases ≤ 5.15, já fechadas em sessões anteriores)
#1–#4, #6, #7 (infra/auth) · #8 (Evolution inbound) · #13–#17, #20–#22 (conversas) · #23–#30 (IA) · #31–#37 (conteúdo) · #38–#41 (contatos) · #42–#45 (métricas) · #46–#49 (onboarding/UX). Todas com tag `fase-*`.

---

## 3. Features `[ ]` Pendentes (9)

| # | Descrição | Categoria | Bloqueio |
|---|---|---|---|
| **62** | Integração Google Calendar | **Maratona — bloqueada** | **Segredo novo:** `GOOGLE_OAUTH_CLIENT_ID` + `GOOGLE_OAUTH_CLIENT_SECRET` |
| **72** | Suporte email | **Maratona — bloqueada** | **Segredo novo:** config IMAP + SMTP |
| 9 | Painel scan QR code | WhatsApp adiado (pré-maratona) | Decisão consciente do Igor |
| 10 | Status visual da conexão | WhatsApp adiado | Idem |
| 11 | Reconexão automática | WhatsApp adiado | Idem |
| 12 | Envio outbound via Evolution (parcial) | WhatsApp adiado | Idem |
| 18 | Responder manualmente pelo painel | WhatsApp adiado | Idem |
| 19 | Anexar foto/arquivo na resposta | WhatsApp adiado | Idem |
| 50 | Cobrança recorrente | Modelo de negócio (pré-maratona) | Stripe + segredo + decisão de pricing |

**Da maratona, só 2 ficaram (#62, #72) — exatamente as 2 pausas previstas no prompt, ambas por segredo novo.** As outras 7 nunca estiveram no escopo desta sessão.

---

## 4. Features `[~]` Abandonadas (1)
- **#5** Recuperação de senha por email — abandonada intencionalmente (reset via psql em dev; exigiria SMTP). Documentado no ROADMAP.

---

## 5. Commits da Maratona (`6bb1b97..HEAD`)

```
4150f6a 5.25 — multi-canal (#74 + #73 widget) + #57 treinamento
933b48b 5.23+5.24 — análise (#65 #66 #68) + compliance (#89 #90 #92)
6b70cde 5.21+5.22 — engajamento (#81 #82) + UX painel (#83 #84 #88)
66d832f 5.20 — multi-tenant avançado (#76 #77 #78)
3841c09 5.19 — agendamento real (#59 #60 #63 #64)
fd6f382 5.18 — IA inteligente (#51 #52 #53 #54 #55 #58)
80d6247 5.17 — fundamentos: roles (#75) + slots (#61)
0160786 5.16 — convite multi-user (#6)
```
8 commits, todos pushed para `origin/main`.

---

## 6. Tags

Última tag: **`fase-5.15-fechada`**. **As fases da maratona (5.16–5.25) NÃO foram taggeadas** — isso é intencional e correto: a exceção da maratona cravou "commit sim, tag não — tag fica pro arquiteto fazer na revisão". Há 30 tags `fase-*` no total (todas ≤ 5.15). Criar as tags 5.16–5.25 é uma tarefa de revisão pendente para o Igor/arquiteto.

---

## 7. Estatísticas

| Métrica | Valor |
|---|---|
| **Testes backend (Surefire)** | **256 verde** (0 falhas/erros) — era 183 no início da maratona (**+73**) |
| Rotas frontend (page.tsx) | **23** (+ `widget.js` como asset estático público) |
| Migrations SQL | **25** (eram 18 — +7: migrations 19–25) |
| Arquivos Java produção | **129** |
| Arquivos Java teste | **48** |

Todas as 7 migrations novas (19–25) aplicadas no Supabase real E registradas no `AbstractIntegrationTest` (Testcontainers).

---

## 8. Análise Honesta

### O que está vivo (funcional, testado, pushed)
O painel do tenant cresceu de ~16 para 23 telas. **IA muito mais capaz:** além de responder, agora detecta cancelamento, reclamação (força handoff), intenção de agendar, coleta dados estruturados, mantém memória de longo prazo do contato, adapta o tom, e **agenda de fato** (cria appointments validando slots, com lembretes automáticos 24h/2h e remarcação/cancelamento por conversa). **Multi-tenant maduro:** roles owner/admin/agent, times, limites por plano (enforcement real em convites), audit log visível. **Engajamento:** boas-vindas + reativação automática. **UX:** busca global Cmd+K, notificações em tempo real (Supabase Realtime), saved replies. **Análise:** comparação mês a mês, top contatos, export PDF. **Compliance:** LGPD exclusão (hard delete FK-safe) + exportação + logs de acesso. **Multi-canal:** widget web embeddable + unificação de canais. Tudo com **256 testes verde** e build limpo.

### O que precisa testar UX (olho humano — nada disso foi validado na mão)
A maratona inteira passou pelos gates automatizados (mvn + npm + smoke onde aplicável via subagentes), mas **NENHUMA das 34 features foi validada na UI por humano** — diferente do ritmo normal (uma pausa de teste por fase). Os pontos que merecem olho:
- **Agendamento end-to-end** (#59/#60): criar slots, ver a IA agendar, o calendário renderizar, lembretes (o job dispara só em horário real).
- **Fluxo de convite + roles** (#75): logar como agent e confirmar o comportamento (decisão: agent vê tudo).
- **Widget web** (#73): embutir `widget.js` numa página de teste e conversar — é o único caminho que cria conversa fora do WhatsApp.
- **Busca Cmd+K, realtime toast, saved replies, export PDF** — todos client-side, melhor conferir visualmente.
- **LGPD exclusão** (#89): a confirmação por digitação do nome + o hard delete real (irreversível) — testar com um contato descartável.
- **Reactivation/welcome**: dependem de OutboundService no fluxo real (webhook OFF), então só validáveis quando o webhook religar OU via teste dirigido.

### O que precisa de segredo pra fechar
- **#62 Google Calendar:** `GOOGLE_OAUTH_CLIENT_ID` + `GOOGLE_OAUTH_CLIENT_SECRET` (OAuth 2.0, scope calendar.events). Escopo cravado: sync unidirecional Meada→Google.
- **#72 Email:** IMAP (host/porta/user/senha, poll 1min) + SMTP (envio). Escopo cravado: cada email = mensagem nova, sem threading.
Em ambos, o Igor define as envs no `.env` (território dele) e eu referencio via `${VAR}` — nunca escrevo no `.env`.

### Riscos/dívidas herdados (não introduzidos pela maratona, mas relevantes)
- **Webhook OFF** (incidente Baileys) → várias features novas que dependem do fluxo inbound real (welcome, reativação, intents em produção) só foram exercitadas por teste, não por mensagem real.
- **Schema Evolution não validado** contra payloads reais (RISKS.md) — bloqueante p/ 1º cliente.
- **Senha de banco = senha de login** em dev — desacoplar antes de produção.
- **Fases 5.16–5.25 sem tag** — revisão + tagging pendente.

---

## 9. Recomendação de Próximos Passos

1. **Antes de mais código: revisar e taggear as 8 fases da maratona** (`fase-5.16` … `fase-5.25`). São 34 features que entraram só com gate automatizado — o arquiteto deveria revisar os commits e criar as tags, fechando o ciclo de qualidade que a maratona deliberadamente adiou.
2. **Rodada de validação de UX** das features de maior risco/valor (agendamento, roles, widget, LGPD) — idealmente em 2-3 sessões temáticas, não tudo de uma vez. É a maior lacuna: muito código não-visto-por-humano acumulado.
3. **Fechar #62 e #72** assim que os segredos estiverem no `.env` — 1-2 fases curtas (5.26 Google Calendar, 5.27 email). Isso leva a maratona a 36/36.
4. **Decisão estrutural pendente** (independente da maratona): religar o webhook (com número dedicado + validação do payload Evolution) é o que separa "painel rico" de "SaaS que atende cliente de verdade". Sem isso, todo o fluxo inbound/IA continua só testado, não operante.
5. **#50 cobrança** continua sendo o último marco de produto — depende de decisão de pricing + Stripe.

**Resumo:** a maratona entregou 34/36 com 256 testes verde e tudo pushed. As 2 que faltam são só segredo. O trabalho real pendente agora não é mais *código* — é **revisão/tagging** e **validação humana** do que foi construído em alta velocidade.
