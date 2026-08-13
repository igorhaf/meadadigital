# 04 — Multi-perfil e Chassis Transversais

[← Home](00-HOME.md)

## O conceito de "perfil"

Meada é **um monolito que se apresenta como N produtos verticais ("perfis")**. Cada perfil
(generic, sushi, legal, dental, …) parece um produto distinto ao cliente final: subdomínio, nome de
produto, tom de IA e features próprias. O core (mensageria + IA + outbound) é compartilhado; o que
muda é o **segmento de perfil** injetado no prompt e o conjunto de tabelas/telas/handlers do nicho.

## Perfis são HARDCODED (sem tabela de perfis)

Fonte de verdade dupla e espelhada:

- `src/main/java/com/meada/profiles/ProfileType.java` (enum Java) — **33 nichos** + `generic` (34 ids no total).
- `frontend/lib/profiles/profile-type.ts` (const TS) — espelho.
- `ProfileTypeParityTest` falha o build se divergirem.

Cada perfil tem `id` (estável, persistido em `companies.profile_id`), `productName`, `subdomain` e
`defaultPaletteId`. **Adicionar um perfil** = editar os 2 arquivos + uma migration que estende o
CHECK de `companies.profile_id` + rodar a paridade. **NUNCA remover** um nicho ao adicionar outro
(regra cravada: a lista do CHECK só ACRESCENTA).

`companies.profile_id` é NOT NULL DEFAULT `'generic'`, com CHECK nos 34 ids. **Um tenant tem
exatamente 1 perfil**, cravado pelo root ao editar a empresa.

## Anatomia de um diretório de perfil

`src/main/java/com/meada/profiles/{perfil}/` segue um padrão:

| Classe | Papel |
|--------|-------|
| `{Perfil}Controller` | Endpoints `/api/{perfil}/**`. |
| `{Perfil}Service` / `{Perfil}Repository` | Regra de negócio + JDBC. |
| `{Perfil}ProfileGuard` | `require{Perfil}(user)` → 403 `forbidden_wrong_profile` se o tenant for de outro perfil. |
| `{Perfil}ContextCache` | Caffeine cache (TTL 10–60s) do bloco de contexto dinâmico injetado no prompt; invalidado em toda mutação. |
| `{Perfil}ConfirmHandler` / `Aprovacao…Handler` / `Entrega…Handler` | Parseiam as tags da IA (ver abaixo) e criam/mutam artefatos. |
| `{...}StatusParityTest` | Garante que a máquina de status hardcoded Java == TS. |

A persona (texto) de cada perfil e os branches de `segmentFor(...)` vivem em
`profiles/ProfilePromptContext.java`. O `JwtAuthenticationFilter` autentica `/api/{perfil}/**`. O
`OutboundService` encadeia os handlers de tag do perfil. O `getNavForProfile()` do frontend injeta
o grupo de sidebar do perfil.

## Tags da IA (texto livre, não tool-calling)

Como o Gemini trata `responseSchema` e tool-calling como mutuamente exclusivos (e o fluxo usa
`responseSchema`), os perfis usam **tags em texto livre** que a IA emite e o backend parseia por
regex, recalcula e **remove antes de enviar** ao cliente. Cada perfil tem namespace próprio de tag
(`<pedido>`, `<pedido_comida>`, `<consulta_nutri>`, `<proposta_evento>`, …). Lista completa em
[05 — Nichos](05-nichos.md).

---

## Os 9 chassis transversais

Os 33 nichos não são 33 implementações independentes — eles reusam **9 chassis** de regra de
negócio. Conhecer o chassi explica 90% do comportamento de um nicho; a "escapada estrutural" é o
restante (o que o diferencia). Cada chassi tem um **perfil-avô** (o exemplar que o inaugurou) —
as regras abaixo foram verificadas no código do avô e valem para TODA a família. (As letras
A–G entre parênteses são a nomenclatura do CLAUDE.md.)

### 1. Order-based + gate de aceite humano (chassi B)

Cardápio/catálogo → carrinho montado na conversa → tag de pedido → total recalculado no backend →
pedido nasce `aguardando` → a loja humana aceita/recusa no painel → Kanban de status.
**Avô:** sushi; **comida** consolidou o gate + modifiers.
**Nichos:** sushi, comida, floricultura, pizzaria, adega, padaria, lavanderia, papelaria,
suplementos, otica (encomenda) — e os varejos do chassi 8 herdam o gate.

**Regras invariantes (verificadas em `profiles/comida/orders/` e `profiles/sushi/orders/`):**

- **Carrinho vive NA CONVERSA** — não existe entidade de carrinho. A IA relê o histórico e só na
  confirmação emite a tag com as linhas do pedido (`PedidoComidaConfirmHandler`).
- **Total SEMPRE recalculado do catálogo.** O `total_cents` da tag é DESCARTADO; o repositório
  recalcula preço-base + Σ `price_delta_cents` dos modifiers/opções, e grava **snapshot por item**
  (nome/preço no momento do pedido — mudar o cardápio não altera pedidos passados).
- **Gate de aceite humano:** o pedido nasce `aguardando`; aceite/recusa é AÇÃO HUMANA no painel
  (Kanban) — a IA nunca aceita nem recusa.
- **`aguardando` não notifica** (`ComidaOrderNotifier` retorna antes de enviar): a IA já confirmou
  na conversa; as transições seguintes notificam com texto defensivo, best-effort.
- **Retirada × entrega:** `fulfillment=entrega` sem endereço → **422 `address_required`**
  (`SushiOrderService`); taxa de entrega vem da config do tenant.
- **Mínimo de pedido:** `subtotal >= minOrderCents` validado no repositório
  (`ComidaOrderRepository`).

**Variantes do chassi (cada uma verificada no seu perfil):**

- **Entrega agendada** dia+período ≥ hoje (floricultura, escola/visita).
- **Lead time por item** `made_to_order`: data prometida ≥ hoje + MAX(lead dos itens) →
  **422 `lead_time_violation`** (padaria, papelaria, otica/`OticaOrderService`).
- **Turnaround** coleta→entrega: `delivery_date` ≥ `collect_date` + MAX(turnaround dos itens) →
  **422 `turnaround_violation`** (lavanderia/`LavanderiaOrderService`).
- **Trava +18:** sem `age_confirmed` NENHUM pedido é criado → **422 `age_not_confirmed`**
  (adega/`AdegaOrderService`).
- **Meio-a-meio pela regra do MAIOR valor:** preço da pizza = MAX(preço dos sabores no tamanho)
  + Σ deltas; o `menu_item_id` do item é o sabor de maior preço
  (pizzaria/`PizzariaOrderRepository`).
- **Prova de ARTE:** estado extra + tag de aprovação do cliente; avançar sem `art_approved` →
  **409 `art_not_approved`** (papelaria/`PapelariaOrderService`).

### 2. Agenda com conflito de horário (chassi A)

Slot configurável com defesa transacional de corrida. **Avô:** restaurant/dental (por company);
**salon** inaugurou o conflito por profissional.
**Nichos:** restaurant, dental, salon, pet, nutri, dermatologia, fotografia, barbearia, estetica,
otica (exame), concessionaria (test-drive).

**Regras invariantes (verificadas em `profiles/salon/appointments/`):**

- **Conflito half-open:** `NOT (existing.end_at <= :newStart OR existing.start_at >= :newEnd)` —
  fim exclusivo, então um slot que termina 10:00 não conflita com um que começa 10:00. Só status
  BLOQUEANTES contam (cancelado não segura vaga).
- **RE-verificado DENTRO da transação do INSERT** (defesa de corrida): `findConflict` roda de novo
  na transação; conflito → `SlotConflictException` → **409 `conflict_slot`** com os detalhes do
  agendamento conflitante no corpo (`SalonAppointmentConflict`).
- **Janela opens/closes avaliada no fuso `America/Sao_Paulo`** → fora da janela =
  **400 `outside_hours`**.
- **`end_at` materializado em Java no INSERT** (nunca coluna GENERATED — ver regras de
  plataforma); a duração vem da config OU do serviço/pacote/tipo escolhido.
- **Snapshots** de nome/preço/duração do serviço gravados no agendamento.
- **Notifica só confirmado + cancelado** (texto defensivo); os demais status são silenciosos.
- **Escopo do conflito:** por `company_id` (1 recurso — restaurant, dental) ou por
  `professional_id` (paralelismo entre profissionais — salon e herdeiros).

### 3. Intervalo de dias (reserva multi-dia)

Conflito por overlap half-open `[início, fim)` **em DATE** por recurso; check-out e check-in no
mesmo dia não conflitam (o recurso rotaciona). Valores derivados (`nights`, `total`,
`delivery_date`) materializados em Java. **Nichos:** pousada (quarto × `[check_in, check_out)`),
lavanderia (coleta + entrega acopladas por turnaround — ver variante do chassi 1).

### 4. Assinatura / matrícula (chassi E — recorrência indefinida)

Matrícula = assinatura ativa-até-cancelar. **Avô:** academia.
**Nichos:** academia, escola, cursos.

**Regras invariantes (verificadas em `profiles/academia/`):**

- **Capacity validado TRANSACIONALMENTE no INSERT:** `count(matrículas não-canceladas) + 1 <=
  capacity` re-checado dentro da transação (`AcademiaMembershipRepository`) → estourou =
  **409 `class_full`** (o INSERT não acontece).
- **Anti-dupla por índice parcial UNIQUE:** `uniq_active_membership_per_contact (company_id,
  contact_id) WHERE status = 'ativa'` (migration 36) — defesa final contra dupla matrícula via IA.
- **Suspensa/trancada MANTÉM a vaga** (vagas restantes = capacity − matrículas ativas E
  suspensas — `AcademiaClassService`); **cancelada é TERMINAL, LIBERA a vaga** e materializa
  `end_date = hoje` em Java na transição. Ativa ⇄ suspensa é reversível.
- **Pagamento MANUAL mensal:** registro por `reference_month` com UNIQUE (membership,
  reference_month) → **409 `duplicate_payment`**. Sem gateway, sem cobrança automática, sem
  máquina de inadimplência (ver [06 — Pagamentos](06-pagamentos.md)).
- **Snapshots** de plano/turma/curso (nome, preço, horário) gravados na matrícula.

### 5. Proposta + aprovação em 2 fases (chassi D)

A IA abre; a equipe orça; a IA captura a decisão do cliente. **Avô:** oficina (com a OS);
**eventos** generalizou. **Nichos:** oficina, eventos, atelie, casamento, viagens (e o lead da
concessionaria).

**Regras invariantes (verificadas em `profiles/eventos/proposals/` e `profiles/oficina/`):**

- **A IA ABRE o artefato VAZIO** (rascunho/aberta, total 0, sem itens) a partir do briefing da
  conversa — nunca com itens ou valores.
- **A equipe orça NO PAINEL:** itens de orçamento com `line_total` e `total_cents`
  **MATERIALIZADOS em Java** a cada mutação de item (nunca calculados em SQL/coluna gerada).
- **`orcada` exige total > 0** → **400 `empty_budget`** (`EventProposalController`,
  `ServiceOrderController`).
- **A tag de APROVAÇÃO é a ÚNICA mutação de estado que a IA faz:** `orcada` → aprovada/recusada
  (`AprovacaoPropostaHandler`). Todo o resto do funil (…→ fechada → realizada) é ação humana.
- **Itens TRAVADOS a partir de fechada** (e nos estados terminais): mutar item →
  **409 `proposal_locked`** / **409 `order_locked`**.
- **Sub-itens que NÃO entram no total** (gerenciados SÓ no painel, SEM tag): cronograma por hora
  (eventos, casamento), checklist binário com `due_date` NULLS LAST (casamento), provas/ajustes
  ordenados (atelie), itinerário multi-dia (viagens).

### 6. Sub-entidade de cliente (chassi G)

O "cliente" tem entidades-filhas; o atendimento referencia a sub-entidade. **Avô:** pet (o animal
do tutor). **Nichos:** pet (animal), oficina (veículo), nutri (paciente → plano, **dois níveis**),
dermatologia (paciente), escola (aluno do responsável), dental (paciente).

**Regras invariantes (verificadas em `profiles/pet/`):**

- **A sub-entidade pertence ao contact:** `contact_id NOT NULL`, relação 1→N (um tutor, N
  animais).
- **A tag tem 2 MODOS** (`AgendamentoPetConfirmHandler`): `x_id` referencia sub-entidade
  existente, OU `new_x {name, species, …}` **cadastra a sub-entidade E age no mesmo turno**
  (cadastra o animal e agenda de uma vez). Payload incompleto/ inválido → warn + nada criado
  (best-effort).
- **Matching de escopo** onde o guia do perfil manda: species do animal × restrição do serviço
  (pet), placa do veículo (oficina).
- **Excluir em uso → 409 `*_in_use`** (`animal_in_use`, `professional_in_use`,
  `service_in_use` — DELETE protegido por FK); o caminho preferido é ARQUIVAR.

### 7. Entrega read-only (chassi F)

Conteúdo profissional entregue pelo backend, nunca gerado pela IA. **Avô:** nutri (plano
alimentar). **Nichos:** nutri (plano), dermatologia (preparo de procedimento), fotografia (link de
material), cursos (próximo módulo).

**Regras invariantes (verificadas em `profiles/nutri/appointments/EntregaPlanoHandler`):**

- **Conteúdo escrito SÓ pelo painel** — o service do painel é o único caminho de escrita; a IA
  NUNCA gera nem edita o corpo.
- **Entrega VERBATIM:** a tag de entrega dispara `notifier.sendText` com o TEXTO EXATO gravado
  pelo profissional — o conteúdo NÃO passa pela geração da IA e nunca é reescrito/resumido.
- **BARREIRA DE CONTATO:** o conteúdo só é entregue se o `contact_id` do dono (paciente/cliente)
  coincidir com o contato DA PRÓPRIA CONVERSA — impede que a IA, induzida por um id alheio, vaze
  conteúdo de outra pessoa.
- **O contexto da IA indica QUE o conteúdo existe** (para ela oferecer a entrega), mas **NUNCA
  injeta o corpo** no prompt.
- Sem conteúdo ativo → a tag resolve para vazio e a IA foi instruída a oferecer alternativa
  (ex.: agendar consulta). Em cursos, a entrega de módulo **soma o avanço de progresso** do aluno.

### 8. Varejo com variantes (chassi C — inaugurado pela lingerie)

Produto com **grade de variantes** e **estoque por variante**. **Avô:** lingerie.
**Nichos:** lingerie (tamanho×cor), moda_infantil (faixa etária×cor), las (cor×dye_lot),
suplementos (sabor×peso). Todos herdam também o gate de aceite do chassi 1.

**Regras invariantes (verificadas em `profiles/lingerie/` e `profiles/modainfantil/`):**

- **Variante = SKU real:** eixos por nicho, UNIQUE por combinação, preço próprio OU herda o do
  produto-base.
- **Estoque decrementado por UPDATE condicional** dentro da transação do pedido:
  `UPDATE … SET stock = stock - :qtd WHERE … AND stock >= :qtd`. **0 linhas afetadas →
  `OutOfStockException` → rollback TOTAL** — o pedido inteiro aborta, nenhum item é gravado
  (`LingerieOrderRepository`). No fluxo da tag (único caminho de criação de pedido nos varejos —
  não há POST manual), o handler captura e devolve vazio, best-effort.
- **Restock idempotente SÓ onde o guia diz** (moda_infantil): ao entrar em recusado/cancelado, o
  estoque é DEVOLVIDO na mesma transação, guardado pela flag `stock_returned` — lida e marcada
  juntas, então duplo cancelamento não devolve duas vezes (`ModaInfantilOrderRepository`).
- **Lote único garantido** (las): com `same_lot_guaranteed`, itens da mesma cor em dye lots
  diferentes → **422 `mixed_dye_lots`** (pedido aborta).

### 9. Fila de walk-in com posição derivada (único: barbearia)

A **posição não é coluna** — é derivada por query (`COUNT` dos tickets `aguardando` à frente, por
escopo). ETA = soma das durações à frente. Atender/desistir recomputa tudo sem UPDATE de
reordenação. A IA só enfileira/informa; **quem chama o cliente é o barbeiro** (ação humana).

---

## Regras de plataforma (valem para TODO nicho)

Além dos chassis, um conjunto de regras transversais vale para qualquer perfil — novo ou
existente. São o "contrato do monolito":

1. **Tag em texto livre, NUNCA tool calling.** O Gemini trata tool calling e `responseSchema`
   como mutuamente exclusivos, e o outbound usa `responseSchema` — então TODA ação da IA é uma
   tag em texto livre, parseada por regex em handler **best-effort** (`hasTag` / `stripTag` /
   `parseAndCreate`). A tag é **REMOVIDA antes do envio** ao cliente; falha de parse = warn no
   log + resposta segue sem o artefato (o fluxo de mensagem nunca quebra). O `OutboundService`
   encadeia os `maybeProcessX` de todos os perfis — como o tenant tem UM perfil, só um handler
   age por mensagem.
2. **Total/preço vindo da tag é DESCARTADO.** Em todo chassi que envolve dinheiro, o backend
   recalcula do catálogo (preço-base + deltas + regras do nicho) e grava snapshots. A IA nunca é
   fonte de verdade de valor.
3. **Contrato de erro `{error, reason}`.** Toda resposta de erro da API tem `error` (texto HTTP)
   e `reason` (snake_case ESTÁVEL — é contrato de API, o frontend mapeia mensagem por `reason`).
   Mapa típico: 400 regra de domínio violada (`outside_hours`, `empty_budget`), 403 gate
   (`forbidden_wrong_profile`, `feature_disabled`), 409 conflito/estado (`conflict_slot`,
   `*_in_use`, `*_locked`, `duplicate_payment`, `invalid_status_transition`), 422 pré-condição
   de domínio (`address_required`, `age_not_confirmed`, `lead_time_violation`). Bean Validation
   cai no `GlobalExceptionHandler` (`com.meada.common`): log do servidor sempre completo (sem
   VALORES — PII), corpo revelador em dev e opaco em prod.
4. **Materialização em Java, NUNCA coluna GENERATED.** `timestamptz + interval` e
   `date + interval` não são IMMUTABLE no Postgres — coluna gerada quebraria. Todo valor
   derivado (`end_at`, `total_cents`, `line_total`, `delivery_date`, `end_date`, `nights`) é
   calculado em Java no INSERT; UPDATEs materializam os valores FINAIS em Java, nunca aritmética
   na SET clause. (Zero `GENERATED ALWAYS` nas migrations — verificado.)
5. **Notificação outbound é defensiva e best-effort.** Notifier nunca propaga exceção (falha =
   warn, a operação principal não desfaz); texto defensivo (não promete o que o painel não
   confirmou); `aguardando` não notifica; POST manual do tenant sem conversation vinculada não
   notifica; em dev, `EVOLUTION_DRY_RUN=true` loga em vez de enviar.
6. **Cache com invalidação EXPLÍCITA na mutação.** O contexto dinâmico da IA
   (`{Nicho}ContextCache`/`MenuCache`, Caffeine) tem TTL 10–60s conforme volatilidade, mas NÃO
   se confia no TTL: **toda mutação do service chama `invalidate(companyId)`**. O cache de
   feature flags (TTL 20s) segue o mesmo padrão no toggle do root.
7. **RLS + `company_id` em toda tabela de nicho.** Toda tabela tem `company_id` e policies
   `company_id = app.company_id()` com `FORCE ROW LEVEL SECURITY`. O super-admin opera via
   service_role FORA do RLS; tabelas de plataforma (ex.: `profile_features`) são só
   service_role. WRITE via SDK exige `company_id` explícito (a policy WITH CHECK revalida).
8. **Tudo que é enum de domínio é HARDCODED com parity test.** Perfis
   (`ProfileType` ↔ `profile-type.ts` + `ProfileTypeParityTest`), features
   (`ProfileFeature` ↔ `profile-feature.ts`) e máquinas de status por nicho
   (enum Java ↔ const TS + `*StatusParityTest`) — o build falha se Java e TS divergirem.
   A CHECK de `companies.profile_id` só ACRESCENTA ids, nunca remove.
9. **Feature flags: default OFF, opt-in do root.** A tabela `profile_features` guarda SÓ
   desvios (ausência de linha = OFF); gate por
   `ProfileFeatureGuard.requireFeature(user, ProfileFeature.X)` → 403 `feature_disabled`.
   Ver [07 — Plataforma](07-plataforma.md).
10. **Unificação de motores (estado 2026-08).** Programa de emagrecimento em andamento
    (`docs/UNIFICACAO_CHASSIS.md`): clones estruturais dos perfis migram para motores
    parametrizados em `com.meada.common.*` SEM mudança de contrato/comportamento (rotas nunca
    parametrizadas — `/api/{nicho}/**` continua por perfil; gate = suíte completa verde).
    Hoje `com.meada.common` contém: `GlobalExceptionHandler` + `ValidationErrorResponse`,
    `audit/AuditLogger` e `coupons/` (`CouponRecord`, `CouponRepositoryBase`,
    `CouponServiceBase` — fatia 1 concluída em 2026-07-03, 7 clones de cupom unificados,
    −997 linhas). Fatias pendentes, na ordem recomendada: ContextCache base → Notifier base →
    CRUD de catálogo simples → TagHandler base → máquina de status → chassi de pedido.

---

## Travas de comportamento da IA

Transversal a vários nichos, sempre embutida na persona (e reforçada pelo schema — a IA não tem a
capacidade perigosa):

- **Travas clínicas/saúde:** dental, nutri, dermatologia, pet (vet), suplementos — a IA **nunca**
  diagnostica, prescreve dosagem/conduta, opina sobre sintoma/lesão/corpo. Encaminha ao
  profissional. Nutri e dermatologia têm guarda extra (transtorno alimentar / sinais de alarme).
- **Travas comerciais:** nos perfis de proposta a IA **nunca** fecha contrato/preço/desconto, nunca
  confirma data não confirmada, nunca inventa item/valor/fornecedor, nunca promete resultado.
- **Trava +18:** adega exige `age_confirmed` (422 `age_not_confirmed` sem ele).
- **Total sempre recalculado:** em todo order-based, o backend descarta o total que a IA chutar.

## Feature flags por nicho (camada 9.0)

O root liga/desliga features por nicho num lugar só (tela `/dashboard/profile-features`). A 1ª
feature é o **CMS** (site por tenant). Default OFF (ausência de linha = off). `generic` não entra
na grade. Gate: `ProfileFeatureGuard.requireFeature(user, ProfileFeature.CMS)` → 403
`feature_disabled`. Ver [07 — Plataforma](07-plataforma.md).
