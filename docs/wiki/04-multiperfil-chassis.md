# 04 — Multi-perfil e Chassis Transversais

[← Home](00-HOME.md)

## O conceito de "perfil"

Meada é **um monolito que se apresenta como N produtos verticais ("perfis")**. Cada perfil
(generic, sushi, legal, dental, …) parece um produto distinto ao cliente final: subdomínio, nome de
produto, tom de IA e features próprias. O core (mensageria + IA + outbound) é compartilhado; o que
muda é o **segmento de perfil** injetado no prompt e o conjunto de tabelas/telas/handlers do nicho.

## Perfis são HARDCODED (sem tabela de perfis)

Fonte de verdade dupla e espelhada:

- `src/main/java/com/meada/profiles/ProfileType.java` (enum Java) — **34 perfis** + `generic`.
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

Os 34 nichos não são 34 implementações independentes — eles reusam **9 chassis** de regra de
negócio. Conhecer o chassi explica 90% do comportamento de um nicho; a "escapada estrutural" é o
restante (o que o diferencia).

### 1. Order-based + gate de aceite humano
Cardápio/catálogo → carrinho montado na conversa → tag de pedido → **total recalculado no backend**
(descarta o da IA) → pedido nasce `aguardando` → **a loja humana aceita/recusa no painel** (a IA
não aceita/recusa) → Kanban de status. `aguardando` não notifica (a IA já confirmou na conversa).
**Nichos:** sushi, comida, floricultura, pizzaria, adega, lingerie, moda_infantil, las, suplementos.

### 2. Agenda com conflito de horário
Slot configurável → conflito verificado em SQL **half-open** (`NOT (end_at <= newStart OR start_at
>= newEnd)`), re-verificado dentro da transação (defesa de corrida) → `end_at` materializado no
INSERT. Duas variantes:
- **Conflito por company** (1 recurso): restaurant, dental.
- **Conflito por professional_id** (múltiplos → paralelismo): salon, estetica, barbearia, nutri,
  fotografia, dermatologia (e os híbridos otica/concessionaria).

### 3. Intervalo de dias (reserva multi-dia)
Conflito por overlap half-open `[início, fim)` por recurso; check-out e check-in no mesmo dia não
conflitam (o recurso rotaciona). Valores materializados (`nights`, `total`, `delivery_date`).
**Nichos:** pousada (quarto × datas), lavanderia (coleta + entrega acopladas por turnaround).

### 4. Assinatura / matrícula (recorrência indefinida)
Matrícula = assinatura ativa-até-cancelar; **anti-dupla** (índice parcial UNIQUE por
aluno/contato × turma/curso onde status='ativa'); capacidade validada transacionalmente; pagamento
**manual** (sem gateway — ver [06 — Pagamentos](06-pagamentos.md)). Suspensa mantém vaga; cancelada
libera. **Nichos:** academia, escola, cursos.

### 5. Proposta + aprovação em 2 fases
A IA **abre** um artefato vazio (total 0, sem itens) a partir do briefing; a equipe **orça** no
painel; num turno posterior a IA **captura a aprovação/recusa** (muta o estado). Itens travam
(`*_locked`) após certos estados. **Nichos:** oficina, eventos, atelie, casamento, projetos,
viagens (e o lead da concessionaria).

### 6. Sub-entidade de cliente
O "cliente" tem entidades-filhas; o atendimento referencia a sub-entidade; excluir/arquivar é
protegido se em uso. **Exemplos:** dental (paciente), pet (animal), oficina (veículo), nutri
(paciente → plano, **dois níveis**), escola (aluno do responsável). Tags com 2 modos
(`{id existente}` OU `new_{...}` cadastra+usa) aparecem aqui.

### 7. Entrega read-only
Conteúdo gravado só pelo profissional/admin; a IA tem um modo de **entrega VERBATIM** (fora da
geração da IA) com barreira de contato. **Nichos:** nutri (plano), dermatologia (preparo),
fotografia (link de material), cursos (próximo módulo).

### 8. Varejo com variantes (chassi novo, inaugurado pela lingerie)
Produto com **grade de variantes** (eixo configurável) e **estoque por variante**, decrementado
**transacionalmente** no pedido (`UPDATE ... WHERE stock >= qtd` → **409 `out_of_stock`**, rollback
total). **Nichos:** lingerie (tamanho×cor), moda_infantil (faixa etária×cor), las (cor×dye_lot),
suplementos (sabor×peso).

### 9. Fila de walk-in com posição derivada (único: barbearia)
A **posição não é coluna** — é derivada por query (`COUNT` dos tickets `aguardando` à frente, por
escopo). ETA = soma das durações à frente. Atender/desistir recomputa tudo sem UPDATE de
reordenação. A IA só enfileira/informa; **quem chama o cliente é o barbeiro** (ação humana).

---

## Travas de comportamento da IA

Transversal a vários nichos, sempre embutida na persona:

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
