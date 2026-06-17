# AcademiaBot — guia operacional da academia (camada 7.7)

O AcademiaBot é o produto do Meada para academias e studios de fitness. Seus clientes falam pelo
WhatsApp; a IA atende com tom acolhedor-motivador, mostra os planos e as aulas com vaga, e matricula.
Você acompanha as matrículas, registra pagamentos e gerencia planos e aulas pelo painel.

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

## 4. Configurações (`/dashboard/academia-settings`)

- **Horário de funcionamento** (abre/fecha).

## 5. Como a IA atende

- A IA conhece os planos ativos, as aulas com vaga (em tempo real) e se o cliente já tem matrícula
  (nesse caso, não oferece outra).
- Quando o cliente pede matrícula, ela pergunta o plano e as aulas, mostra os horários e vagas, e
  confirma antes de matricular.
- **A IA NÃO prescreve treino, dieta ou avaliação física** — se o cliente pedir, ela recusa com
  gentileza e explica que isso é com o professor presencialmente. Sem promessa de resultado, sem
  julgamento.

## LGPD

- As **observações** da matrícula são administrativas. **Não** registre dados de saúde do aluno.

## Limitações conhecidas (honestas)

- **Matrícula é assinatura** (ativa-até-cancelar), não um agendamento avulso.
- **Vaga é por capacidade da aula** — suspensa mantém a vaga; só cancelar libera.
- **Pagamento é manual** (registro mês a mês). Cobrança automática (cartão/Pix), boleto e cálculo de
  inadimplência são fases futuras.
- **Aluno não é cadastro formal** — o histórico vem do contato do WhatsApp.
- **Sem treino prescrito, ficha de exercícios, avaliação física, balança/wearables, catraca,
  fidelidade, multi-unidade.**
- **Sem lembrete/cobrança automática.**
- **Fuso fixo** America/Sao_Paulo.
