# AcademiaBot — guia operacional da academia (camada 7.7)

O AcademiaBot é o produto do Meada para academias e studios de fitness. Seus clientes falam pelo
WhatsApp; a IA atende com tom acolhedor-motivador, mostra os planos e as aulas com vaga, e matricula.
Você acompanha as matrículas, registra presença e pagamentos, gerencia cupons, fidelidade e
indicações, e enxerga a saúde do negócio nos relatórios.

## 1. Planos (`/dashboard/academia-plans`)

- **Novo plano:** nome (ex.: "Mensal Livre", "Mensal Aulas Coletivas"), valor mensal em R$ e
  descrição.
- **Ativo/inativo:** o checkbox tira/coloca o plano na oferta da IA.
- **Excluir:** bloqueado se houver matrículas — desative-o.

## 2. Aulas (`/dashboard/academia-classes`)

- **Nova aula:** nome, modalidade (texto livre: funcional, pilates, yoga…), dia da semana, hora de
  início, duração, **capacidade** (máximo de alunos) e professor (opcional).
- A lista mostra, por dia da semana, quantas vagas estão **ocupadas/capacidade**.
- **Ativo/inativo** e **excluir** (bloqueado se houver matrícula) como nos planos.

## 3. Matrículas (`/dashboard/academia-memberships`)

- **Lista por status** (ativa/suspensa/cancelada), com filtros.
- **Nova matrícula:** escolha o plano, marque **uma ou mais aulas** (a lista mostra as vagas
  restantes e **desabilita aulas lotadas**), informe o nome do aluno e telefone. Se uma aula encher,
  o sistema avisa qual; se o cliente já tem matrícula ativa, recusa.
- **Detalhe + status:**
  - **Suspender** pausa a matrícula, mas **mantém a vaga ocupada** (pra liberar a vaga, cancele).
  - **Cancelar** encerra a matrícula (registra a data de fim) e **libera as vagas**.
  - Ao **reativar** (suspensa→ativa) ou **cancelar**, o aluno é notificado (se veio do WhatsApp).
- **Aba Pagamentos** (no detalhe): registre o pagamento de um mês (mês de referência + valor + forma),
  veja o histórico, o último mês pago e os meses em aberto. Pagamento só em matrícula ativa; um mês
  não pode ser registrado duas vezes.

## 4. Check-ins / frequência (`/dashboard/academia-checkins`)

- Escolha a aula e marque **"Presente"** nos alunos que apareceram — 1 presença por aluno/aula/dia
  (o sistema barra duplicata).
- **Histórico** com filtro de período: quem veio, em qual aula e quando.
- A frequência é o alicerce da reativação de inativo e da fidelidade por assiduidade. O check-in
  pela conversa ("cheguei" → IA registra) é fase futura — o schema já prevê a origem `ia`.

## 5. Fila de espera (`/dashboard/academia-waitlist`)

- Aula lotada não é venda perdida: adicione o interessado à **fila da aula** (nome + telefone).
- A **posição é derivada por ordem de chegada** — quando alguém sai da frente (chamado/desistiu), as
  posições recomputam sozinhas.
- Ações: **Chamar** (avise o interessado — abriu vaga), **Matriculou** (virou matrícula pelo fluxo
  normal) e **Desistiu**. Entrar na fila NÃO cria matrícula nem reserva vaga: a fila só ordena o
  interesse.

## 6. Avulsos / day-use (`/dashboard/academia-day-passes`)

- Registre a **aula avulsa / diária** de quem ainda não é aluno: nome, telefone, aula (opcional),
  data e valor.
- O passe nasce **"A receber"**; ao receber (dinheiro/Pix na recepção), marque **"Pago"**.
- Cobrança online do avulso espera o gateway de pagamento (#50) — hoje o registro é manual.

## 7. Cupons (`/dashboard/academia-coupons`)

- Cupom de desconto para fechar matrícula parada: **percentual** (1–100%) ou **valor fixo** (R$),
  com mínimo, limite de usos e validade opcionais.
- O código é **único por academia (sem diferenciar maiúsculas)** e pode ser divulgado em campanha; a
  IA aplica na conversa e o backend valida (ativo + validade + mínimo + usos).

## 8. Fidelidade por assiduidade (`/dashboard/academia-loyalty`)

- **Opt-in:** ligue a fidelidade, defina **pontos por check-in**, o **limiar** de pontos e a
  **recompensa** (texto livre, ex.: "Uma aula grátis").
- Consulte o **saldo do aluno** (por matrícula ativa) e credite pontos manualmente se precisar
  (ajuste/cortesia). Quando o saldo atinge o limiar, a tela sinaliza **"Recompensa atingida"** — a
  concessão é sua (registre e zere/ajuste os pontos como política da casa).

## 9. Indicações (`/dashboard/academia-referrals`)

- **Traga um amigo:** registre a indicação (quem indicou — aluno da casa — e o indicado) e o sistema
  gera um **código único** para o indicador divulgar.
- Quando o amigo matricular, marque **"Converteu"** — o desconto (`%`) é concedido LOCALMENTE por
  você no pagamento (cashback real espera o gateway #50).

## 10. Relatórios (`/dashboard/academia-reports`)

- **MRR** (receita mensal recorrente das matrículas ativas), matrículas por status, e **ocupação por
  aula** (ativos × capacidade) — enxergue aula ociosa e aula lotada de relance. Somente leitura.

## 11. Régua de inadimplência (automática)

- Diariamente o sistema varre as matrículas ativas e, respeitando a **carência (grace_days)** da sua
  configuração, envia **um lembrete de vencimento por mês em aberto** pelo WhatsApp.
- Se você ligar a **auto-suspensão** (`auto_suspend_days`), a matrícula é **suspensa** sozinha após o
  atraso-limite (suspensa mantém a vaga — a régua nunca cancela). A cobrança real com link de
  pagamento espera o gateway #50.

## 12. Aniversário do aluno (automático)

- Cadastre a **data de nascimento** no contato; no dia, a IA envia **uma** mensagem calorosa de
  parabéns por ano (sem repetir). A mensagem é um voto de felicidades — sem oferta clínica, sem
  promessa de resultado.

## 13. Configurações (`/dashboard/academia-settings`)

- **Horário de funcionamento** (abre/fecha) e política de inadimplência (carência e auto-suspensão).

## 14. Como a IA atende

- A IA conhece os planos ativos, as aulas com vaga (em tempo real) e se o cliente já tem matrícula
  (nesse caso, não oferece outra).
- Quando o cliente pede matrícula, ela pergunta o plano e as aulas, mostra os horários e vagas, e
  confirma antes de matricular.
- **A IA NÃO prescreve treino, dieta ou avaliação física** — se o cliente pedir, ela recusa com
  gentileza e explica que isso é com o professor presencialmente. Sem promessa de resultado, sem
  julgamento.

## LGPD

- As **observações** da matrícula e dos check-ins são administrativas. **Não** registre dados de
  saúde do aluno. Data de nascimento do contato é usada só para a saudação de aniversário.

## Limitações conhecidas (honestas)

- **Matrícula é assinatura** (ativa-até-cancelar), não um agendamento avulso.
- **Vaga é por capacidade da aula** — suspensa mantém a vaga; só cancelar libera.
- **Cobrança real (cartão/Pix/boleto) espera o gateway #50** — hoje: registro manual + lembrete de
  vencimento + auto-suspensão opcional.
- **Aluno não é cadastro formal** — o histórico vem do contato do WhatsApp.
- **Check-in é pela recepção (painel).** A via pela conversa ("cheguei" → IA registra) e o
  QR/leitor (espera o desbloqueio de upload, SERVICE_ROLE_KEY) são fases futuras.
- **Sem treino prescrito, ficha de exercícios, avaliação física, balança/wearables, catraca,
  multi-unidade.**
- **Fuso fixo** America/Sao_Paulo.
