/**
 * Catálogo de APRESENTAÇÃO dos produtos verticais (preço + copy por nicho) — usado na vitrine
 * ("Vitrine de Nichos" / página de Produtos). Complementa {@link PROFILES} de `profile-type.ts`
 * (id/productName/subdomain/paleta), que é a fonte de verdade espelhada no enum Java.
 *
 * Por que é um arquivo SEPARADO (TS-only, não no enum Java):
 *  - preço e copy são DISPLAY, não estado persistido — não precisam do enum Java nem entram no
 *    ProfileTypeParityTest (que casa só `{ id: '...' }` em profile-type.ts).
 *  - mantê-los aqui evita reescrever o parity test a cada ajuste de preço/texto.
 *
 * Preço (`priceMonthly`, R$/mês): faixa por COMPLEXIDADE do chassi do produto, não número solto.
 *  - 149 — varejo/pedido simples (catálogo + Kanban): comida, pizzaria, adega, lingerie, lãs,
 *          moda infantil, suplementos, floricultura, padaria, papelaria, sushi.
 *  - 199 — agenda/recorrência (slot, conflito, assinatura): restaurante, salão, barbearia, pet,
 *          academia, escola, cursos, pousada, lavanderia.
 *  - 249 — clínica/perfil regulado (trava de conduta, dado sensível): dental, nutri, estética,
 *          dermatologia, ótica, legal, oficina.
 *  - 299 — assessoria/proposta de alto ticket (orçamento + aprovação): eventos, casamento,
 *          viagens, ateliê, concessionária, fotografia.
 *
 * `tagline` (1 linha: o que o produto faz) e `highlights` (3-4 marcos REAIS, extraídos das seções
 * cravadas do CLAUDE.md / do código de cada perfil — a "escapada" que justifica o nicho).
 */
export type ProfileCatalogEntry = {
  priceMonthly: number
  tagline: string
  highlights: string[]
}

export const PROFILE_CATALOG: Record<string, ProfileCatalogEntry> = {
  generic: {
    priceMonthly: 149,
    tagline: 'Atendimento ao cliente por WhatsApp com IA treinada nos seus dados.',
    highlights: [
      'IA responde com seus serviços, horários, FAQs e preços',
      'Base de conhecimento por documento (RAG)',
      'Site próprio por tenant (CMS, quando habilitado)',
    ],
  },
  legal: {
    priceMonthly: 249,
    tagline: 'Escritório de advocacia: clientes, processos e a IA consultando os autos do cliente.',
    highlights: [
      'Clientes desacoplados do contato + processos por CNJ validado (mód. 97)',
      'A IA identifica pelo telefone e injeta os processos do cliente',
      'Notificação de mudança de status do processo',
    ],
  },
  dental: {
    priceMonthly: 249,
    tagline: 'Clínica odontológica: pacientes e agenda, com a IA agendando consultas.',
    highlights: [
      'Agenda com conflito transacional (slot 30min)',
      'Tag <consulta> agenda; a IA NUNCA dá diagnóstico',
      'Cancelamento por IA bloqueado (ação humana)',
    ],
  },
  sushi: {
    priceMonthly: 149,
    tagline: 'Restaurante: cardápio, pedidos por mensagem livre e Kanban de status.',
    highlights: [
      'Carrinho montado na conversa + tag <pedido>',
      'Cupom, fidelidade por contagem, retirada × entrega, agendamento',
      'Categorias/status/notificações dinâmicas por tenant',
    ],
  },
  restaurant: {
    priceMonthly: 199,
    tagline: 'Reservas de restaurante: mesas, disponibilidade e confirmação pela IA.',
    highlights: [
      'Conflito de reserva em SQL transacional (janela 2h)',
      'Tag <reserva> com re-verificação dentro da transação',
      'Cache de agenda TTL 15s (a agenda muda rápido)',
    ],
  },
  salon: {
    priceMonthly: 199,
    tagline: 'Salão de beleza: profissionais, serviços e agenda por profissional.',
    highlights: [
      'Conflito POR profissional (paralelismo entre eles)',
      'Slot fino 15min, duração por serviço',
      'Snapshots de preço/profissional no agendamento',
    ],
  },
  pousada: {
    priceMonthly: 199,
    tagline: 'Hospedagem: quartos, diárias e reserva por intervalo de dias.',
    highlights: [
      'Reserva por intervalo half-open [check-in, check-out)',
      'Total = diária × noites, materializado',
      'Disponibilidade por quarto nos próximos 30 dias',
    ],
  },
  academia: {
    priceMonthly: 199,
    tagline: 'Academia/studio: planos, aulas semanais e matrícula por assinatura.',
    highlights: [
      'Matrícula = assinatura (ativa-até-cancelar)',
      'Vaga por capacity por aula, validada na transação',
      'Pagamento mensal manual + anti-dupla matrícula',
    ],
  },
  pet: {
    priceMonthly: 199,
    tagline: 'Pet shop/veterinária: animais do tutor e agenda por profissional.',
    highlights: [
      'Animal como sub-entidade do tutor (contato)',
      'Species match: serviço restrito por espécie',
      'Tag agenda animal existente OU cadastra+agenda no mesmo turno',
    ],
  },
  oficina: {
    priceMonthly: 249,
    tagline: 'Oficina mecânica: veículos, ordens de serviço e aprovação do orçamento.',
    highlights: [
      'OS order-based com itens (peça/mão-de-obra) e total',
      'Gate de aprovação em 2 fases (a IA muta o estado da OS)',
      'Veículo como sub-entidade do cliente',
    ],
  },
  nutri: {
    priceMonthly: 249,
    tagline: 'Consultório de nutrição: pacientes, planos alimentares e agenda.',
    highlights: [
      'Trava clínica: a IA NUNCA monta/calcula plano ou caloria',
      'Entrega do plano read-only (texto verbatim do nutricionista)',
      'Guarda de transtorno alimentar na conversa',
    ],
  },
  barbearia: {
    priceMonthly: 199,
    tagline: 'Barbearia: agenda por barbeiro + fila de walk-in por ordem de chegada.',
    highlights: [
      'Fila com posição DERIVADA (sem coluna de posição)',
      'Dois caminhos: marcar horário ou entrar na fila',
      'Cache TTL 10s — a fila muda a cada cliente',
    ],
  },
  eventos: {
    priceMonthly: 299,
    tagline: 'Casa de festas/buffet: propostas com orçamento, cronograma e aprovação.',
    highlights: [
      'Proposta order-based + cronograma ordenado do dia',
      'Gate de aprovação em 2 fases via tag',
      'Itens travam quando o contrato fecha',
    ],
  },
  estetica: {
    priceMonthly: 249,
    tagline: 'Clínica de estética: procedimentos, pacotes de sessões e ficha por sessão.',
    highlights: [
      'Pacote multi-sessão com saldo que decrementa na transação',
      'Cancelar agendamento devolve a sessão ao pacote',
      'Trava estética: a IA não indica nem promete resultado',
    ],
  },
  comida: {
    priceMonthly: 149,
    tagline: 'Delivery de comida: cardápio com opções, pedido na conversa e Kanban.',
    highlights: [
      'Carrinho na conversa + modifiers (base + Σ deltas)',
      'Gate de aceite humano (a loja aceita/recusa)',
      'Total recalculado no backend (descarta o da IA)',
    ],
  },
  floricultura: {
    priceMonthly: 149,
    tagline: 'Floricultura: catálogo, pedido agendado por dia/período e entrega.',
    highlights: [
      'Pedido agendado (data + período manhã/tarde)',
      'Gate de aceite humano + Kanban',
      'Total recalculado no backend',
    ],
  },
  pizzaria: {
    priceMonthly: 149,
    tagline: 'Pizzaria: cardápio, pizza meio-a-meio e pedido pela IA.',
    highlights: [
      'Pizza meio-a-meio: preço pela regra do MAIOR valor',
      'Modifiers de tamanho/borda somados por cima',
      'Gate de aceite humano + total recalculado',
    ],
  },
  adega: {
    priceMonthly: 149,
    tagline: 'Adega/delivery de bebidas: catálogo, pedido na conversa e Kanban.',
    highlights: [
      'Trava +18: confirmação de maioridade obrigatória (422 sem o flag)',
      'Modifiers de volume/temperatura',
      '"Beba com moderação" + gate de aceite humano',
    ],
  },
  escola: {
    priceMonthly: 199,
    tagline: 'Escola/educação infantil: turmas, alunos, matrícula e visita.',
    highlights: [
      'Aluno como sub-entidade do responsável',
      'Matrícula = assinatura com vaga por turma',
      'Visita agendada (dia + período), independente de matrícula',
    ],
  },
  atelie: {
    priceMonthly: 299,
    tagline: 'Ateliê (costura/arte/design): proposta sob encomenda com provas e ajustes.',
    highlights: [
      'Proposta order-based + gate de aprovação em 2 fases',
      'Etapas de prova/ajuste (1ª prova → ajuste → entrega)',
      'project_type costura/arte/design por proposta',
    ],
  },
  casamento: {
    priceMonthly: 299,
    tagline: 'Assessoria de casamento: proposta com orçamento, cronograma e checklist.',
    highlights: [
      'Três sub-entidades: orçamento, cronograma e checklist',
      'Gate de aprovação em 2 fases via tag',
      'Checklist pré-casamento ordenado por prazo',
    ],
  },
  concessionaria: {
    priceMonthly: 299,
    tagline: 'Loja de carros: estoque, test-drive e lead de compra (híbrido triplo).',
    highlights: [
      'Veículo como item de estoque com status próprio',
      'Test-drive com conflito por vendedor + lead com preço do catálogo',
      'A IA nunca fecha preço/financiamento',
    ],
  },
  lavanderia: {
    priceMonthly: 199,
    tagline: 'Lavanderia: coleta e entrega agendadas com turnaround por serviço.',
    highlights: [
      'Duas datas: coleta + entrega = coleta + MAX(turnaround)',
      'Gate de aceite humano + Kanban de status',
      'Sempre coleta+entrega (sem balcão)',
    ],
  },
  dermatologia: {
    priceMonthly: 249,
    tagline: 'Clínica dermatológica: tipos de atendimento, pacientes e agenda.',
    highlights: [
      'Tipos de atendimento como tabela (duração + preparo por tipo)',
      'Trava clínica: a IA não diagnostica nem avalia lesão/foto',
      'Entrega da nota de preparo read-only',
    ],
  },
  fotografia: {
    priceMonthly: 299,
    tagline: 'Estúdio fotográfico: pacotes, agenda e entrega do material.',
    highlights: [
      'Catálogo de pacotes (duração + prazo de entrega)',
      'Entrega do link do material read-only (barreira de contato)',
      'Prazo de entrega materializado por sessão',
    ],
  },
  cursos: {
    priceMonthly: 199,
    tagline: 'Escola livre/curso online: trilha de módulos e matrícula por assinatura.',
    highlights: [
      'Trilha de módulos ordenados (position 0..N)',
      'Entrega do próximo módulo read-only + avanço de progresso',
      'Matrícula = assinatura, mensalidade manual',
    ],
  },
  lingerie: {
    priceMonthly: 149,
    tagline: 'Moda íntima/varejo: produtos com grade de variantes e estoque.',
    highlights: [
      'Grade de variantes tamanho×cor com estoque',
      'Decremento transacional (409 se esgotar)',
      'Gate de aceite humano + Kanban',
    ],
  },
  moda_infantil: {
    priceMonthly: 149,
    tagline: 'Moda infantil/varejo: variantes por faixa etária com estoque.',
    highlights: [
      'Tamanho = faixa etária (sugestão pela idade)',
      'Devolução de estoque ao cancelar (idempotente)',
      'Grade de variantes + gate de aceite',
    ],
  },
  las: {
    priceMonthly: 149,
    tagline: 'Loja de lãs/tricô: novelos por cor × lote de tingimento.',
    highlights: [
      'Variante por cor × dye lot (lote de tingimento)',
      'Mesmo lote garantido (mesmo tom no projeto)',
      'Decremento transacional de estoque',
    ],
  },
  padaria: {
    priceMonthly: 149,
    tagline: 'Padaria & confeitaria: pronta-entrega e sob-encomenda com lead time.',
    highlights: [
      'Pronta-entrega × sob-encomenda com lead time por item',
      'Personalização do bolo (mensagem da placa)',
      'Retirada × entrega + gate de aceite',
    ],
  },
  otica: {
    priceMonthly: 249,
    tagline: 'Ótica: exame de vista (agenda) + óculos sob receita (encomenda).',
    highlights: [
      'Híbrido: agenda de exame + encomenda com receita',
      'Receita administrativa (a IA não prescreve grau)',
      'Lead time de montagem do óculos',
    ],
  },
  papelaria: {
    priceMonthly: 299,
    tagline: 'Gráfica/convites: encomenda com prova de arte e tiragem.',
    highlights: [
      'Prova de arte: a IA captura a aprovação do layout',
      'Tiragem escala o total (50/100/200)',
      'Lead time + retirada × entrega',
    ],
  },
  viagens: {
    priceMonthly: 299,
    tagline: 'Agência de viagens: cotações de pacote com roteiro multi-dia.',
    highlights: [
      'Proposta order-based + roteiro multi-dia',
      'Gate de aprovação em 2 fases via tag',
      'A IA nunca emite passagem nem confirma voo/hotel',
    ],
  },
  suplementos: {
    priceMonthly: 149,
    tagline: 'Loja de suplementos: variantes (sabor × peso) com estoque e entrega.',
    highlights: [
      'Grade de variantes com decremento transacional',
      'Trava de saúde: a IA não prescreve dose nem recomenda uso',
      'Gate de aceite humano (só entrega)',
    ],
  },
}

/** Formata o preço mensal como "R$ 149/mês". */
export function formatMonthlyPrice(value: number): string {
  return `R$ ${value}/mês`
}

/** Entrada do catálogo de display do nicho (preço + copy), ou undefined se não cadastrado. */
export function getCatalogEntry(profileId: string): ProfileCatalogEntry | undefined {
  return PROFILE_CATALOG[profileId]
}
