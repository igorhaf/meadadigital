# PetBot — guia operacional do pet shop (camada 7.8)

O PetBot é o produto do Meada para pet shops e clínicas veterinárias. Seus clientes (os tutores)
falam pelo WhatsApp; a IA atende com tom carinhoso, identifica o animal, mostra os serviços e a
agenda dos profissionais e agenda. Você gerencia profissionais, serviços, animais e a agenda pelo
painel.

> **Importante:** o PetBot agenda e organiza — ele **não** dá diagnóstico, não receita remédio e não
> recomenda tratamento. Quando um tutor descreve um sintoma, a IA orienta a agendar uma consulta
> presencial. Não há prontuário clínico no sistema (o campo de observações é administrativo).

## 1. Profissionais (`/dashboard/pet-professionals`)

- **Novo profissional:** nome e especialidade (ex.: "Veterinário", "Banho & tosa").
- **Ativo/inativo:** o checkbox tira/coloca o profissional na agenda que a IA oferece.
- **Excluir:** bloqueado se houver agendamentos — desative-o em vez disso.

## 2. Serviços (`/dashboard/pet-services`)

- **Novo serviço:** nome, categoria (Banho, Tosa, Consulta…), duração em minutos, preço (opcional) e
  **restrição de espécie** (opcional: só cães, só gatos, só outros — ou qualquer espécie).
- A restrição de espécie limita quem pode agendar: um serviço "só para gatos" não pode ser agendado
  para um cão (a IA respeita isso e o sistema reforça).
- **Ativo/inativo** e **excluir** (bloqueado se houver agendamentos) como nos profissionais.

## 3. Animais (`/dashboard/pet-animals`)

- Cada animal pertence a um **tutor** — que é um contato do WhatsApp. Um tutor pode ter vários animais.
- **Novo animal:** escolha o tutor (contato), informe nome, espécie (cão/gato/outro), raça, sexo e ano
  de nascimento (todos exceto nome/espécie são opcionais).
- **Filtros:** por espécie, busca por nome, e a opção de mostrar arquivados.
- **Arquivar** (preferido a excluir): tira o animal da lista ativa sem perder o histórico de
  agendamentos. **Excluir** é bloqueado se o animal tiver agendamentos.
- A IA também cadastra animais sozinha: quando um tutor novo pede um agendamento, ela pergunta o nome
  e a espécie do pet e cadastra junto com o agendamento.

## 4. Agenda (`/dashboard/pet-appointments`)

- **Lista por dia** com filtro de status e de profissional.
- **Novo agendamento (manual):** escolha o **animal** (a lista de serviços passa a mostrar só os
  compatíveis com a espécie dele), o profissional, o serviço, a data e a hora. Se o profissional já
  tiver agendamento no horário, o sistema avisa o conflito; se o serviço não combinar com a espécie,
  recusa.
- **Detalhe + status:**
  - `agendado → confirmado/cancelado`; `confirmado → realizado/cancelado/falta`; os demais são finais.
  - Em **confirmar** e **cancelar**, o tutor é notificado automaticamente (se veio do WhatsApp).

## 5. Configurações (`/dashboard/pet-settings`)

- **Horário de funcionamento** (abre/fecha) e intervalo entre agendamentos. Vale para agendamentos
  futuros.

## 6. Como a IA atende

A IA conhece os profissionais, os serviços (com restrição de espécie e preço quando informado), os
animais já cadastrados de cada tutor e os horários livres de cada profissional nos próximos dias.
No WhatsApp ela:

1. Identifica o tutor pelo telefone e oferece os animais já cadastrados (ou pergunta os dados de um
   pet novo).
2. Sugere um serviço e um profissional com horário livre, respeitando a restrição de espécie.
3. Confirma animal + serviço + profissional + dia + hora e cria o agendamento.

O conflito de agenda é **por profissional**: dois profissionais podem atender no mesmo horário, mas o
mesmo profissional não.

## 7. O que o PetBot NÃO faz (ainda)

Prontuário/histórico clínico, carteira de vacinas com agenda, prescrição, internação, pacotes de
banho recorrentes (assinatura), foto do pet, pagamento online e lembretes automáticos. Esses são
temas de fases futuras.
