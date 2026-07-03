# Prompts de Nicho (T5) — fila de nichos verticais

Cada arquivo deste diretório é um **prompt de nicho formato T5**: um brief denso e
temático-completo que um agente executor usa pra erguer um nicho vertical inteiro do
Meada num único passo (migration + backend + frontend + testes + seed + smoke).
Foram escritos a partir de **regra de negócio** (análise empírica do domínio), não de
análise de código.

## ⚠️ Atribuição de slot — FONTE ÚNICA DE VERDADE

Os prompts foram gerados em paralelo e, individualmente, cada um diz que seu número de
migration/camada/tenant é "provisório, confirmar no arranque". **Esta tabela resolve a
colisão**: é a ordem de execução cravada e a atribuição sequencial. Quando executar um
prompt, use os valores da linha dele AQUI — eles têm precedência sobre o que estiver
escrito no corpo do prompt.

Baseline no disco hoje: último perfil fechado = **floricultura** (camada 8.5, migration
`49_floricultura.sql`, tenant `igorhaf16`=comida/8.4). Logo a fila começa em 8.6/50/igorhaf17.

| Ordem | Nicho | profile_id | Camada | Migration | Tenant | company/user sufixo |
|------:|-------|-----------|:------:|:---------:|:------:|:-------------------:|
| 1 | Pizzaria      | `pizzaria`     | 8.6  | `50_pizzaria.sql`     | igorhaf17 | -017 |
| 2 | Casamento     | `casamento`    | 8.7  | `51_casamento.sql`    | igorhaf18 | -018 |
| 3 | Padaria       | `padaria`      | 8.8  | `52_padaria.sql`      | igorhaf19 | -019 |
| 4 | Adega         | `adega`        | 8.9  | `53_adega.sql`        | igorhaf20 | -020 |
| 5 | Lavanderia    | `lavanderia`   | 8.10 | `54_lavanderia.sql`   | igorhaf21 | -021 |
| 6 | Dermatologia  | `dermatologia` | 8.11 | `55_dermatologia.sql` | igorhaf22 | -022 |
| 7 | Ótica         | `otica`        | 8.12 | `56_otica.sql`        | igorhaf23 | -023 |
| 8 | Projetos      | `projetos`     | 8.13 | `57_projetos.sql`     | igorhaf24 | -024 |
| 9 | Ateliê        | `atelie`       | 8.14 | `58_atelie.sql`        | igorhaf25 | -025 |
| 10 | Papelaria     | `papelaria`    | 8.15 | `59_papelaria.sql`    | igorhaf26 | -026 |
| 11 | Fotografia    | `fotografia`   | 8.16 | `60_fotografia.sql`   | igorhaf27 | -027 |
| 12 | Concessionária | `concessionaria` | 8.17 | `61_concessionaria.sql` | igorhaf28 | -028 |
| 13 | Viagens       | `viagens`      | 8.18 | `62_viagens.sql`      | igorhaf29 | -029 |
| 14 | Escola        | `escola`       | 8.19 | `63_escola.sql`       | igorhaf30 | -030 |
| 15 | Cursos        | `cursos`       | 8.20 | `64_cursos.sql`       | igorhaf31 | -031 |
| 16 | Lingerie      | `lingerie`     | 8.21 | `65_lingerie.sql`     | igorhaf32 | -032 |
| 17 | Moda Infantil | `moda_infantil` | 8.22 | `66_moda_infantil.sql` | igorhaf33 | -033 |
| 18 | Lãs           | `las`          | 8.23 | `67_las.sql`          | igorhaf34 | -034 |
| 19 | Suplementos   | `suplementos`  | 8.24 | `68_suplementos.sql`  | igorhaf35 | -035 |

> A ordem reflete dependência de chassi: os order-based (pizzaria/padaria/adega/lavanderia)
> e os de proposta (casamento/projetos) e de agenda-clínica (dermatologia/ótica) são
> independentes entre si — a ordem é só pra fixar números únicos. Pode-se reordenar, desde
> que cada nicho fique com UM slot único (migration, camada e tenant distintos dos demais).

### Regras ao executar (valem pra todos)
- **IDs de namespace compartilhado no seed** (contacts/instance/conversation): cada nicho usa
  um **sufixo próprio** pra não colidir FK entre seeds. Sugestão: `-04x` pizzaria, `-05x`
  casamento, `-06x` padaria, `-07x` adega, `-08x` lavanderia, `-09x` dermatologia, `-10x`
  ótica, `-11x` projetos, `-12x` ateliê, `-13x` papelaria, `-14x` fotografia, `-15x`
  concessionária, `-16x` viagens, `-17x` escola, `-18x` cursos, `-19x` lingerie, `-20x`
  moda infantil, `-21x` lãs, `-22x` suplementos. Conferir no arranque os sufixos já
  usados por seeds anteriores.
- **Antes de escrever a migration**, reconfirmar o maior nº presente em `supabase/migrations/`
  e o maior `igorhafN` já provisionado — se a fila avançou, deslocar a partir da tabela.
- Os demais constraints duros (gate 3× → pausar, `git add` explícito, `at time zone
  'America/Sao_Paulo'` no seed, tabela DENTRO da migration, webhook OFF) estão no corpo de
  cada prompt.

## Catálogo (chassi + escapada de cada nicho)

| Nicho | Clona | Escapada estrutural |
|-------|-------|---------------------|
| Pizzaria | Comida | Meio-a-meio (sabores fracionados, regra do maior valor) |
| Casamento | Eventos | Checklist pré-casamento com prazo (3ª sub-entidade) |
| Padaria | Floricultura | Pronta-entrega × sob-encomenda c/ lead time + personalização de bolo |
| Adega | Comida | Trava +18 (`age_confirmed` → 422 `age_not_confirmed`) |
| Lavanderia | Floricultura | Duas datas (coleta+entrega) acopladas por turnaround |
| Dermatologia | Dental/Nutri | Tipos de procedimento c/ duração própria + preparo read-only; trava clínica forte |
| Ótica | Dental + Padaria | 1º híbrido: agenda de exame + encomenda de óculos c/ receita e lead de laboratório |
| Projetos | Eventos/Casamento | Etapas de execução pós-aprovação (3 estados + ordem); 1 perfil p/ móveis/marcenaria/paisagismo |
| Ateliê | Projetos | Provas/ajustes ordenados (status binário); costura/arte/design via project_type |
| Papelaria | Padaria | Prova de arte (`art_approved`) — gate de aprovação da arte dentro do pedido |
| Fotografia | Dental + Estética | Sessão+pacote agendados + entrega de link read-only (verbatim) |
| Concessionária | Dental + Eventos | Veículo = item de estoque (disponível→reservado→vendido) + test-drive + lead |
| Viagens | Eventos/Projetos | Itinerário multi-dia (1 linha por dia, ordenado por data) |
| Escola | Academia + Pet | Matrícula-assinatura + aluno sub-entidade do responsável + visita agendada |
| Cursos | Academia + Nutri | Trilha de módulos ordenados + progresso individual + próximo módulo read-only |
| Lingerie | Comida (+ chassi novo de variantes) | Grade de variantes tamanho×cor + estoque por variante (decremento transacional, 409 out_of_stock) |
| Moda Infantil | Lingerie | Variantes com eixo de tamanho por faixa etária + sugestão idade→tamanho |
| Lãs | Lingerie | Variante cor×dye_lot + regra "mesmo lote preferencial" (same_lot_guaranteed) |
| Suplementos | Lingerie + Nutri | Variantes sabor×peso + trava de saúde (IA nunca prescreve dosagem/uso) |

> **Chassi de varejo com variantes** (introduzido pela Lingerie, ordem 16): catálogo de produto
> com matriz de variantes (eixo configurável por nicho) + estoque por variante decrementado
> transacionalmente na criação do pedido. Reusado por Moda Infantil (faixa etária), Lãs (dye lot)
> e Suplementos (sabor×peso). É um chassi distinto dos delivery sem estoque (comida/flor/adega).
