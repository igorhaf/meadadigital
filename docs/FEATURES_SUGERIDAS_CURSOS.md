# Features Sugeridas — Cursos online/escola livre

> Backlog de features avançadas para o nicho **Cursos online/escola livre** (profile_id `cursos`), priorizado por VALOR DE NEGÓCIO. Objetivo: engordar a cartela de features com o que mais agrega receita/retenção no menor tempo. Baseado no estado REAL do nicho (o que já existe NÃO é repetido aqui).

> **Modo de execução (cravado):** ao implementar qualquer feature deste backlog, faça **tudo em prosa**,
> de forma contínua e autônoma, **sem perguntar nada ao programador**, sem pausas para confirmação e
> **sem usar o widget de perguntas** (AskUserQuestion). Não interrompa o fluxo pedindo aval intermediário:
> decida com base no estado real do código/banco e nas convenções das skills, implemente, e só pare em
> ponto de bifurcação arquitetural genuína ou no gate de teste. Reporte o progresso em prosa corrida.

## O que o nicho já tem (baseline)

- **Matrícula = assinatura** no curso (recorrência indefinida, `ativa ⇄ trancada → concluida/cancelada`), com anti-dupla (1 matrícula ativa por aluno+curso) e notificação de boas-vindas/conclusão/despedida.
- **Trilha de módulos ordenados** por curso (`cursos_modules`, position 0..N, título + conteúdo texto), com editor de reordenação no painel.
- **Progresso individual** por matrícula (`cursos_enrollment_progress`) + **entrega read-only do PRÓXIMO módulo** pela IA (`<entrega_modulo>`, VERBATIM, com barreira de contato e avanço automático do progresso).
- **Mensalidade MANUAL** (`cursos_payments`, UNIQUE por mês, sem Stripe/inadimplência automática).
- IA acolhedora-motivadora que apresenta cursos, matricula e entrega módulos — sem inventar curso/preço/bolsa, sem pular ordem, sem reescrever material, sem prometer certificado.
- Base de conhecimento (RAG) disponível como em todo perfil.

## 🏆 Top 3 quick wins (fazer primeiro)

**1. Certificado de conclusão gerado + verificável (P/M, Alto).** No momento a matrícula pode ir a `concluida` mas não gera nada tangível — e "certificado" é justamente o que o aluno quer levar pra LinkedIn/currículo. Um certificado gerado (HTML/print A4 com código único + página pública de verificação `/certificados/{codigo}`) transforma a conclusão num ativo de marketing gratuito (todo aluno formado divulga a escola) e é um argumento de venda direto no funil ("curso COM certificado"). Não depende de gateway nem de upload de imagem — é geração server-side de HTML. É a feature de maior ROI porque vende na entrada E retém até o fim (o aluno persegue o certificado, o que puxa a conclusão dos módulos).

**2. Lembrete/nudge automático de próximo módulo (scheduler) (P, Alto).** Hoje a entrega do módulo é 100% PUXADA — o aluno precisa pedir. Curso online morre por abandono silencioso: quem parou no módulo 3 nunca mais volta e cancela a assinatura. Um cron diário que detecta matrícula ativa parada há N dias no mesmo módulo e dispara um WhatsApp motivador ("Faltam só 2 módulos pra você concluir! Quer o próximo agora?") reduz churn de forma barata e direta. Reaproveita o `notifier` e o cálculo de "próximo módulo" que já existe. É o clássico anti-abandono que sustenta a receita recorrente da assinatura.

**3. Cupom de desconto na matrícula (P, Alto).** A escola vive de campanha ("primeira mensalidade grátis", "black friday -30%") e hoje não há como aplicar desconto — a IA é proibida de inventar preço. Uma tabela `cursos_coupons` (percent/fixo, validade, limite de usos) que a IA lê e aplica na tag `<matricula_curso>` (validação e cálculo no backend, IA nunca inventa o valor) destrava toda a estratégia promocional e mede conversão por campanha. Esforço baixo (espelha o cupom que já existe no perfil sushi) e impacto direto em receita e aquisição.

## Backlog priorizado (16 features)

| # | Feature | Valor de negócio | Esforço | O que resolve pro cliente | Eixo |
|---|---------|------------------|---------|---------------------------|------|
| 1 | Certificado de conclusão gerado + verificação pública | Alto | M | Aluno leva o diploma; escola ganha divulgação orgânica e argumento de venda | Marketing |
| 2 | Lembrete automático de próximo módulo (scheduler anti-abandono) | Alto | P | Reduz abandono no meio da trilha; sustenta a assinatura recorrente | Operação |
| 3 | Cupom de desconto na matrícula | Alto | P | Destrava campanhas promocionais e mede conversão | Receita |
| 4 | Cobrança/pagamento da mensalidade online (Pix/cartão) | Alto | G | Automatiza a receita recorrente; acaba com cobrança manual e inadimplência silenciosa | Receita |
| 5 | Régua de inadimplência automática (lembrete D-3 / D+1 / D+7) | Alto | M | Recupera mensalidade atrasada sem a escola perseguir aluno na mão | Receita |
| 6 | Quiz/avaliação por módulo com nota mínima pra avançar | Alto | M | Garante aprendizado real; libera certificado só quem passou; aumenta valor percebido | Operação |
| 7 | Reativação de matrícula trancada/cancelada (win-back) | Alto | P | Traz de volta quem pausou/saiu com oferta segmentada | Retenção |
| 8 | Upsell/cross-sell de curso complementar pela IA no fim da trilha | Alto | M | Vende o próximo curso pra quem acabou de concluir (LTV) | Receita |
| 9 | Pacote/combo de cursos (trilha de formação com preço fechado) | Alto | M | Vende jornada completa em vez de curso avulso; ticket maior | Receita |
| 10 | NPS/pesquisa de satisfação pós-conclusão | Médio | P | Mede qualidade, gera depoimento e review pra vender | Marketing |
| 11 | Indicação com recompensa (aluno traz aluno → desconto na mensalidade) | Alto | M | Aquisição barata via boca a boca dos próprios alunos | Marketing |
| 12 | Pré-requisito entre cursos (trilha com dependência) | Médio | P | Organiza a jornada e força a compra do curso base antes do avançado | Operação |
| 13 | Turma com data/coorte + lista de espera | Médio | M | Cria urgência ("turma abre dia X, últimas vagas") e permite curso ao vivo | Operação |
| 14 | Dashboard de engajamento (funil de módulos, alunos travados, churn) | Médio | M | Mostra onde os alunos desistem pra a escola agir | Operação |
| 15 | Página pública/CMS do curso (catálogo + inscrição) | Médio | M | Vitrine própria pra captar lead fora do WhatsApp | Marketing |
| 16 | Entrega de módulo com vídeo/PDF (link/anexo) | Médio | M | Curso deixa de ser só texto; conteúdo rico eleva valor e preço | IA/Operação |

## Detalhamento das prioritárias

### 1. Certificado de conclusão gerado + verificação pública

- **Problema de negócio:** o gancho de venda nº 1 de curso online é "sai com certificado". Hoje a matrícula chega a `concluida` e nada acontece — o aluno não tem o que mostrar e a escola perde o argumento comercial e a divulgação orgânica que um certificado compartilhado gera.
- **Como funciona:** ao mover a matrícula pra `concluida` (transição que já existe), o backend gera um `cursos_certificates` (enrollment_id, código único, emitido_em, snapshot aluno+curso+carga horária). Uma rota **pública sem auth** `/public/cursos/certificados/{codigo}` (fora da allowlist do JwtFilter, espelho do CMS público) renderiza o certificado em HTML/print A4 (nome, curso, data, código) e serve de verificação de autenticidade. A IA, ao entregar o último módulo ou ao concluir, manda o link do certificado via `notifier.sendText` (VERBATIM, sem reescrever). No painel, tela "Certificados" lista os emitidos. Respeita a trava: a IA só ENVIA o link de algo já gerado pelo backend, nunca "promete certificado não descrito".
- **Dependências:** nenhuma de bloqueio (geração server-side de HTML; sem foto, sem Stripe). Opcional: logo do tenant no certificado depende do upload liberado (SERVICE_ROLE_KEY) — sem isso, certificado textual sem logo.
- **Métrica de sucesso:** % de matrículas `concluida` com certificado acessado; nº de compartilhamentos/verificações públicas; aumento na taxa de conclusão da trilha após o certificado virar meta visível.

### 2. Lembrete automático de próximo módulo (scheduler anti-abandono)

- **Problema de negócio:** curso online tem abandono altíssimo no meio da trilha. Como a entrega hoje é puxada (o aluno tem que pedir o módulo), quem esfria simplesmente para — e uma matrícula parada vira cancelamento, matando a receita recorrente.
- **Como funciona:** um cron diário (`cursos:run-nudges`, novo scheduler — hoje o nicho não tem nenhum) varre matrículas `ativa` cuja última entrega de módulo (`enrollment_progress.completed_at` mais recente) é anterior a N dias E que ainda têm próximo módulo. Para cada uma, dispara via `notifier` uma mensagem motivadora com o nome do próximo módulo e o progresso ("Você está em 3/8 — bora continuar? Respondo aqui e te mando o próximo"). Configurável no `cursos_config` (dias de inatividade, on/off). Reaproveita o cálculo de "próximo módulo" existente e o barramento de notificação. IA respeita a persona (motivar, não pressionar).
- **Dependências:** infra de scheduler/cron (transversal — hoje inexistente no nicho; ver Dependências transversais).
- **Métrica de sucesso:** redução do tempo médio entre módulos; queda na taxa de matrículas que ficam >30 dias paradas; queda no churn de assinatura.

### 3. Cupom de desconto na matrícula

- **Problema de negócio:** a escola depende de promoção pra converter (primeira mensalidade off, cupom de campanha, parceria com influencer). Hoje não há mecanismo de desconto e a IA é proibida de inventar preço — então toda campanha é impossível de operacionalizar dentro do fluxo.
- **Como funciona:** tabela `cursos_coupons` (código, kind percent(1..100)/fixed(centavos), validade, max_uses, uses, active) — espelho direto do cupom já implementado no sushi. A IA recebe o código do aluno e o repassa na tag `<matricula_curso>` (`cupom`); o backend VALIDA (ativo + validade + limite) e aplica o desconto sobre a mensalidade snapshotada — a IA nunca calcula nem inventa o valor. Cupom inválido não aborta a matrícula (entra sem desconto), preservando o comportamento atual. Tela "Cupons" no painel.
- **Dependências:** nenhuma de bloqueio; combina fortemente com #4 (cobrança online) quando existir, mas funciona já na mensalidade manual.
- **Métrica de sucesso:** nº de matrículas com cupom por campanha; conversão de leads que receberam cupom vs. sem; receita incremental atribuída a campanha.

### 4. Cobrança/pagamento da mensalidade online (Pix/cartão)

- **Problema de negócio:** a receita do nicho é a mensalidade recorrente, mas hoje ela é 100% MANUAL (a escola registra o pagamento na mão, sem link, sem lembrete, sem baixa automática). Isso trava crescimento e esconde inadimplência.
- **Como funciona:** integra o gateway (pendência global #50) para gerar link de cobrança da mensalidade (Pix/cartão). A IA pode enviar o link de pagamento do mês ao aluno via `notifier` (VERBATIM, sem inventar valor — pega da mensalidade snapshotada). O webhook do gateway dá baixa em `cursos_payments` automaticamente (resolvendo o UNIQUE por mês já existente). Painel de Pagamentos mostra pago/pendente por matrícula.
- **Dependências:** **gateway de pagamento (#50)** — bloqueador. Enquanto não existe, #3 e #5 já preparam o terreno (desconto + régua de lembrete manual).
- **Métrica de sucesso:** % de mensalidades pagas via link vs. manual; redução da inadimplência; tempo de baixa (de dias manuais para automático).

### 5. Régua de inadimplência automática (lembrete D-3 / D+1 / D+7)

- **Problema de negócio:** aluno que atrasa a mensalidade e não é cobrado simplesmente para de pagar e a escola só percebe depois. Perseguir cada aluno na mão não escala.
- **Como funciona:** cron diário (`cursos:run-cadence`) calcula, por matrícula ativa, os meses em aberto (meses decorridos desde start_date − pagamentos registrados — cálculo que o summary já faz) e dispara WhatsApp na régua: **D-3** lembrete gentil do vencimento, **D+1** aviso de atraso, **D+7** cobrança + risco de trancamento. Textos configuráveis; on/off no config. Respeita a persona (acolhedor, sem ameaça). Funciona já na mensalidade manual; com #4 vira cobrança com link de pagamento embutido.
- **Dependências:** scheduler/cron (transversal); potencializado por #4 (gateway) mas não bloqueado por ele.
- **Métrica de sucesso:** taxa de recuperação de mensalidade atrasada; redução de dias médios de atraso; redução de cancelamentos por inadimplência.

## Dependências transversais

- **Gateway de pagamento (#50, global):** destrava **#4** (cobrança online da mensalidade) e turbina **#5** (régua com link de pagamento), **#3** (cupom aplicado no checkout online) e **#9** (venda de pacote/combo com pagamento à vista). Sem ele, mensalidade segue manual e essas features rodam em modo "lembrete/registro".
- **Scheduler/cron (hoje inexistente no nicho):** é a dependência mais barata e de maior alavancagem — destrava **#2** (nudge de módulo parado), **#5** (régua de inadimplência), **#8** (upsell no fim da trilha disparado por evento) e o lado automático de **#13** (abertura de coorte / lista de espera).
- **Upload de foto/anexo (bloqueado por SERVICE_ROLE_KEY ausente):** destrava **#16** (entrega de módulo com PDF/vídeo hospedado em vez de só link colado) e o logo do tenant no **#1** (certificado). Enquanto bloqueado, tudo funciona com **link colado** (padrão já usado no projeto).
- **Campanha em massa segmentada (infra de envio em lote):** destrava **#7** (win-back de trancados/cancelados), **#11** (indicação) e o disparo de **#3** (cupom de campanha) e **#15** (captação via página pública) para audiências filtradas (por curso, por progresso, por status de matrícula).
- **CMS/página pública (feature flag camada 9.x já existe):** habilitá-la para o nicho `cursos` destrava **#15** (vitrine de catálogo + inscrição), reaproveitando a infra de CMS já construída, sem código novo de plataforma.
