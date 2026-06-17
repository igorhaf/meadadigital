# PousadaBot — guia operacional da pousada (camada 7.6)

O PousadaBot é o produto do Meada para pousadas e hospedagens pequenas. Seus clientes falam pelo
WhatsApp; a IA atende com tom acolhedor, mostra os quartos por número de pessoas e datas, calcula o
total da estadia (diária × noites) e faz a reserva. Você acompanha tudo pela tela de reservas.

## 1. Quartos (`/dashboard/rooms`)

- **Novo quarto:** nome (ex.: "Standard", "Suíte"), capacidade (máx. de hóspedes, 1–20), diária em
  R$ e descrição.
- **Descrição importa:** a IA só promete o que estiver na descrição do quarto. Não escreva nada que
  o quarto não tenha.
- **Ativo/inativo:** o checkbox tira/coloca o quarto na disponibilidade que a IA enxerga.
- **Excluir:** bloqueado se o quarto tiver reservas (proteção de histórico) — desative-o.

## 2. Configurações (`/dashboard/pousada-settings`)

- **Check-in a partir de** e **check-out até**: os horários informados ao hóspede.
- **Política de cancelamento:** texto livre que a IA repassa ao cliente (opcional).
- Mudanças afetam reservas **futuras**.

## 3. Reservas (`/dashboard/pousada-reservations`)

- **Lista por mês**, com filtro por **status** e por **quarto**.
- **Nova reserva (manual):** escolha o quarto (mostra capacidade e diária), informe check-in e
  check-out, número de hóspedes (validado contra a capacidade), nome do hóspede titular e telefone.
  O **total é calculado na hora** (diárias × noites) antes de você confirmar. Se o quarto já estiver
  reservado naquele período, o sistema recusa e mostra **quem** ocupa e **de que data a que data**.
- **Rotação no mesmo dia:** o check-out de uma reserva e o check-in de outra **no mesmo dia** são
  permitidos — o quarto roda no mesmo dia.
- **Detalhe + status:** clique numa reserva para ver os dados e mudar o status (reservado →
  confirmado → check-in → check-out; ou cancelado). Ao **confirmar** ou **cancelar**, o hóspede é
  notificado automaticamente (se veio do WhatsApp); a confirmação inclui quarto, datas e total.

## 4. Como a IA atende

- A IA conhece os quartos ativos (nome, capacidade, diária, descrição), a política, o histórico do
  hóspede e a disponibilidade de cada quarto nos próximos 30 dias.
- Quando o cliente pede uma reserva, ela pergunta número de hóspedes e datas, ajuda a escolher um
  quarto que comporte o grupo, **calcula o total** e confirma tudo antes de reservar.
- **A IA NUNCA promete estrutura, vista ou comodidade que não esteja na descrição** do quarto, e não
  faz promessa de "experiência única".

## LGPD

- As **observações** da reserva são administrativas. **Não** registre RG/CPF/documento ali — esta
  versão não modela dados de identificação formal do hóspede.

## Limitações conhecidas (honestas)

- **Reserva é por DIAS** (check-in/check-out), não por horas. Uma diária = uma noite.
- **Sem tarifa sazonal/promocional** — a diária é fixa por quarto.
- **Sem pagamento/sinal/Pix**, sem programa de fidelidade, sem foto do quarto (bloqueador técnico de
  Storage), sem integração Booking/Airbnb, sem café da manhã/serviços extras.
- **Hóspede não é cadastro formal** — o histórico vem do contato do WhatsApp.
- **Sem auto-transição** de status (uma reserva não vira "check-out" sozinha).
- **Fuso fixo** America/Sao_Paulo (usado para validar que o check-in não é no passado).
- **Risco aceito no MVP:** se a IA prometer um período e o backend detectar conflito ao gravar, a
  reserva não é criada — contorne manualmente. É raro.
