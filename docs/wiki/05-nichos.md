# 05 — Catálogo dos Nichos

[← Home](00-HOME.md) · ver também [04 — Chassis](04-multiperfil-chassis.md)

São **34 nichos verticais** (+ `generic`). Cada um clona um dos 9 chassis e adiciona uma **escapada
estrutural**. As colunas: chassi clonado, escapada, tag(s) de IA, trava de comportamento, máquina de
status. Para o detalhe granular de cada um, ver `docs/PROMPT_NICHO_{NICHO}.md` (implementados) /
`docs/prompts-nicho/PROMPT_NICHO_{NICHO}.md` (fila) e as seções `## Perfil X` do `CLAUDE.md`.

> Numeração "camada" = ordem histórica de construção. Migration = arquivo em `supabase/migrations/`.

## Tabela-mestra

| Camada | profile_id | Produto | Migration | Chassi | Escapada estrutural | Tag(s) de IA |
|:---:|---|---|:---:|---|---|---|
| 7.1 | `sushi` | Sushi | 30 | Order-based+gate | Categorias/status/cupom/fidelidade/agendamento/retirada dinâmicos (mig. 69) | `<pedido>` |
| 7.2 | `legal` | Legal | 31 | Consulta read-only | Cliente jurídico desacoplado do contato; CNJ validado (mód 97); contexto de processos | (sem tag de escrita) |
| 7.3 | `restaurant` | Restaurante | 32 | Agenda (por company) | Reserva de mesa por slot 30min, conflito half-open transacional | `<reserva>` |
| 7.4 | `dental` | Dental | 33 | Agenda (por company) + sub-entidade | Paciente sub-entidade; **trava clínica** | `<consulta>` |
| 7.5 | `salon` | Salão | 34 | Agenda (por profissional) | Múltiplos profissionais (paralelismo); duração por serviço | `<agendamento>` |
| 7.6 | `pousada` | Pousada | 35 | Intervalo de dias | Reserva como intervalo `[check_in, check_out)`; nights/total materializados | `<reserva_pousada>` |
| 7.7 | `academia` | Academia | 36 | Assinatura | Recorrência indefinida; vaga por aula; anti-dupla; pagamento manual | `<matricula>` |
| 7.8 | `pet` | Pet | 37 | Agenda + sub-entidade | Animal sub-entidade do tutor; **species match**; **trava vet** | `<agendamento_pet>` (2 modos) |
| 7.9 | `oficina` | Oficina | 38 | Proposta+aprovação 2 fases | OS order-based; veículo sub-entidade; gate de aprovação | `<ordem_servico>` + `<aprovacao_os>` |
| 8.0 | `nutri` | Nutri | 39 | Agenda + entrega read-only | Sub-entidade **aninhada** (paciente→plano); plano entregue VERBATIM; **trava clínica forte** + guarda transtorno | `<consulta_nutri>` + `<entrega_plano>` |
| 8.1 | `barbearia` | Barbearia | 43 | Agenda + **fila walk-in** | Posição **derivada** (sem coluna); ETA estimada; IA não chama | `<agendamento_barbearia>` + `<fila_barbearia>` |
| 8.2 | `eventos` | Eventos | 45 | Proposta+aprovação 2 fases | Cronograma do dia ordenado (2 tipos de sub-item: preço + tempo) | `<proposta_evento>` + `<aprovacao_proposta>` |
| 8.3 | `estetica` | Estética | 46 | Agenda + **pacote com saldo** | Pacote multi-sessão; saldo decrementa transacionalmente; ficha por sessão; **trava estética** | `<agendamento_estetica>` + `<compra_pacote>` |
| 8.4 | `comida` | Comida | 47 | Order-based+gate | **Modifiers/opções** (price_delta, tabela-filha de snapshot) | `<pedido_comida>` |
| 8.5 | `floricultura` | Floricultura | 49 | Order-based+gate | **Entrega agendada** (data+período+destinatário+cartão) | `<pedido_flor>` |
| 8.6 | `pizzaria` | Pizzaria | 50 | Order-based (comida) | **Meio-a-meio** (sabores fracionados; preço pela regra do maior valor) | `<pedido_pizza>` |
| 8.7 | `casamento` | Casamento | 51 | Proposta (eventos) | **3 tipos de sub-item:** orçamento + cronograma + **checklist** (done boolean) | `<proposta_casamento>` + `<aprovacao_casamento>` |
| 8.8 | `padaria` | Padaria | 52 | Order-based (floricultura) | **Pronta-entrega × sob-encomenda** com lead time + personalização de bolo + fulfillment | `<pedido_padaria>`/`<encomenda_padaria>` |
| 8.9 | `adega` | Adega | 53 | Order-based (comida) | **Trava +18** (`age_confirmed` → 422) | `<pedido_adega>` |
| 8.10 | `lavanderia` | Lavanderia | 54 | Order-based agendado | **Duas datas** (coleta+entrega) acopladas por turnaround (MAX) | `<pedido_lavanderia>` |
| 8.11 | `dermatologia` | Dermatologia | 55 | Agenda (dental/nutri) | **Tipos de atendimento** com duração própria + preparo entregue read-only; **trava clínica** | `<consulta_derma>` + `<entrega_preparo>` |
| 8.12 | `otica` | Ótica | 56 | **Híbrido** (dental+padaria) | Agenda de exame **+** encomenda de óculos com receita + lead de laboratório | `<exame_otica>` + `<encomenda_otica>` |
| 8.13 | `projetos` | Projetos | 57 | Proposta (eventos/casamento) | **Etapas de execução** pós-aprovação (3 estados + ordem); 1 perfil p/ móveis/marcenaria/paisagismo | `<proposta_projeto>` + `<aprovacao_projeto>` |
| 8.14 | `atelie` | Ateliê | 58 | Proposta (eventos) | **Provas/ajustes** ordenadas (status binário); costura/arte/design via `project_type` | `<proposta_atelie>` + `<aprovacao_atelie>` |
| 8.15 | `papelaria` | Papelaria | 59 | Order-based (padaria) | **Prova de arte** (`art_approved`) — gate de aprovação da arte dentro do pedido + tiragem | `<pedido_papelaria>` + `<aprovacao_arte>` |
| 8.16 | `fotografia` | Fotografia | 60 | Agenda + pacote + entrega read-only | Sessão+pacote; **link de material** entregue read-only; prazo materializado | `<sessao_foto>` + `<entrega_material>` |
| 8.17 | `concessionaria` | Concessionária | 61 | **Híbrido** (dental+eventos) | Veículo = **item de estoque** (disponível→reservado→vendido) + test-drive + lead | `<testdrive_carro>` + `<lead_carro>` |
| 8.18 | `viagens` | Viagens | 62 | Proposta (eventos) | **Itinerário multi-dia** (1 linha por dia, ordenado por data) | `<proposta_viagem>` + `<aprovacao_viagem>` |
| 8.19 | `escola` | Escola | 63 | Assinatura (academia) | **Aluno sub-entidade do responsável** + **visita** agendada (dia+período) | `<matricula_escola>` (2 modos) + `<visita_escola>` |
| 8.20 | `cursos` | Cursos | 64 | Assinatura + entrega read-only | **Trilha de módulos** ordenados + progresso individual + próximo módulo VERBATIM | `<matricula_curso>` + `<entrega_modulo>` |
| 8.21 | `lingerie` | Lingerie | 65 | **Varejo com variantes** (novo) | Grade **tamanho×cor** + estoque por variante (decremento transacional, 409 out_of_stock) | `<pedido_lingerie>` |
| 8.22 | `moda_infantil` | Moda Infantil | 66 | Varejo (lingerie) | Eixo de tamanho por **faixa etária** + sugestão idade→tamanho + restock ao cancelar | `<pedido_moda_infantil>` |
| 8.23 | `las` | Lãs | 67 | Varejo (lingerie) | Variante **cor×dye_lot** + regra "mesmo lote preferencial" (`same_lot_guaranteed`) | `<pedido_las>` |
| 8.24 | `suplementos` | Suplementos | 68 | Varejo (lingerie) + nutri | Variante **sabor×peso** + **trava de saúde** (não-prescrição) | `<pedido_suplementos>` |

## Travas de comportamento por nicho (resumo)

| Nicho | A IA NUNCA… |
|---|---|
| sushi/comida/floricultura/pizzaria/padaria | inventa item/opção/preço fora do cardápio; aceita/recusa o pedido (gate humano); o total é recalculado. |
| adega | vende a menor de idade; fecha pedido sem `age_confirmed`. |
| lingerie/moda_infantil/las/suplementos | oferece variante esgotada; inventa produto/tamanho/cor/preço. |
| suplementos | prescreve dosagem/uso terapêutico; recomenda como conduta de saúde. |
| dental/dermatologia/pet | diagnostica, recomenda tratamento/medicação, opina sobre lesão/sintoma. |
| nutri | cria/calcula/ajusta plano, dá caloria/macro/porção, responde "posso comer X". + guarda transtorno alimentar. |
| restaurant/salon/estetica/barbearia/fotografia | inventa recurso/preço; opina sobre aparência; promete resultado estético. |
| oficina/eventos/casamento/projetos/atelie/viagens/concessionaria | fecha contrato/preço/desconto; confirma data não confirmada; inventa item/valor/fornecedor; promete resultado. |
| legal | dá parecer/aconselhamento jurídico (encaminha ao advogado). |

## Máquinas de status (por família)

- **Order-based (sushi/comida/floricultura/pizzaria/adega/lingerie/moda_infantil/las/suplementos):**
  `aguardando → em_preparo/separando → saiu_entrega/enviado → entregue` (+ `recusado`/`cancelado`).
  Aceite = `aguardando → em_preparo`. `aguardando` não notifica.
- **Order-based estendido:** padaria (`pronto → retirado` × `saiu_entrega → entregue`, fulfillment);
  lavanderia (`coletado → em_processo → pronto → saiu_entrega → entregue`); papelaria
  (`…art_aprovacao → em_producao…`).
- **Agenda:** `agendado/agendada → confirmado → realizado` (+ `cancelado` e `falta`/`no_show`).
  Variações: pousada (6 estados de hospedagem: `reservado → confirmado → checked_in → checked_out`),
  fotografia (`…realizada → entregue`).
- **Proposta:** `rascunho → orcada → aprovada → fechada → realizada` (+ `recusada`/`cancelada`);
  `orcada` exige total > 0 (`empty_budget`); itens travam após `fechada`. Oficina varia
  (`aberta → orcada → aprovada → em_execucao → concluida → entregue`).
- **Assinatura:** `ativa ⇄ suspensa/trancada`; ambas → `cancelada` (academia/escola) ou
  `concluida/cancelada` (cursos).
- **Concessionária:** veículo `disponivel → reservado → vendido`; test-drive como agenda; lead
  `novo → em_negociacao → fechado/perdido`.

Cada máquina é hardcoded (enum Java) e espelhada em TS com teste de paridade
(`{Nicho}…StatusParityTest`). Transição inválida → **409** `invalid_status_transition`.

## Erros de domínio comuns

| Erro (HTTP) | Onde |
|---|---|
| `403 forbidden_wrong_profile` | bater `/api/{nicho}/**` com tenant de outro perfil (guard). |
| `409 invalid_status_transition` | transição de status não permitida. |
| `409 out_of_stock` | varejo: estoque de variante insuficiente. |
| `409 conflict_slot` | agenda: horário ocupado (por company ou por profissional). |
| `400 empty_budget` | proposta indo a `orcada` sem itens. |
| `422 age_not_confirmed` | adega sem `age_confirmed`. |
| `422 lead_time_violation` / `turnaround_violation` | padaria / lavanderia: data antes do prazo mínimo. |
| `409 {entidade}_in_use` | excluir entidade referenciada (mecânico, animal, item de cardápio…). |
| `species_mismatch` | pet: serviço restrito a espécie diferente do animal. |
