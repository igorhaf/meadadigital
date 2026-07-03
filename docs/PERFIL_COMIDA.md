# Perfil Comida (delivery) — camada 8.4

Guia operacional do tenant **comida** (`profile_id='comida'`). Delivery de comida genérico, estilo
iFood: a equipe gerencia o cardápio (itens + opções/adicionais), a IA atende clientes pelo WhatsApp
e monta o pedido na conversa, e o restaurante acompanha o fluxo do pedido num Kanban com **gate de
aceite**.

É o **14º perfil vertical** (15º contando o generic). Clona o chassi do **SushiBot** (cardápio +
carrinho-na-conversa + tag de pedido + recálculo de total no backend + snapshot do pedido + taxa de
entrega/pedido mínimo + Kanban) e adiciona **duas escapadas estruturais**: o **gate de aceite do
restaurante** (ação humana) e **itens com opções/adicionais** (modifiers).

## Telas (sidebar "Comida")

| Tela | Rota | O que faz |
|------|------|-----------|
| Cardápio | `/dashboard/comida-menu` | CRUD de itens (nome, descrição, preço, categoria, disponível) **e** das opções/adicionais de cada item (grupos com delta de preço). |
| Pedidos | `/dashboard/comida-orders` | Kanban do pedido + **gate de aceite** (aceitar/recusar) + histórico (entregues/recusados/cancelados). |
| Cupons | `/dashboard/comida-coupons` | Cupons percent/fixed (mínimo, validade, max usos) que a IA repassa na tag (onda 1, backlog #1). |
| Fidelidade | `/dashboard/comida-loyalty` | A cada N pedidos entregues, o próximo ganha desconto automático (onda 1, backlog #2). |
| Zonas de entrega | `/dashboard/comida-zones` | Taxa por bairro/zona; a taxa flat vira fallback (onda 1, backlog #8). |
| Relatórios | `/dashboard/comida-reports` | Faturamento entregue, ticket médio, top itens e horário de pico (onda 1, backlog #15). |
| Configurações | `/dashboard/comida-settings` | Taxa de entrega (flat, fallback das zonas) + pedido mínimo. |

## ESCAPADA 1 — Gate de aceite do restaurante (ação humana, não da IA)

O pedido nasce **aguardando** — a IA já confirmou o RECEBIMENTO na própria mensagem ao cliente, mas
o restaurante ainda precisa decidir. O restaurante então **ACEITA** (→ `em_preparo`) ou **RECUSA**
(→ `recusado`, terminal, com motivo opcional). Isso é **ação humana no painel** (PATCH de status),
**NUNCA da IA** — mesmo padrão do "cancelamento bloqueado pra IA" do dental e do "chamar o próximo"
humano da barbearia. Como a IA já confirmou o recebimento, o estado **aguardando** **NÃO notifica**
o cliente (evita mensagem duplicada).

```
aguardando ──aceitar──→ em_preparo ──→ saiu_entrega ──→ entregue
    │                        │                │
    └──recusar──→ recusado   └──→ cancelado ←─┘
```

Transição inválida → 409 `invalid_status_transition`. Terminais: `entregue`, `recusado`,
`cancelado`.

### Notificações outbound (texto fixo ao ENTRAR no status)

| Status | Notifica? | Texto |
|--------|-----------|-------|
| `aguardando` | **Não** (silencioso) | — (a IA já confirmou o recebimento na conversa) |
| `em_preparo` | Sim | "Seu pedido foi aceito e já entrou em preparo! 🍳" |
| `saiu_entrega` | Sim | "Seu pedido saiu pra entrega. Já já chega aí!" |
| `entregue` | Sim | "Pedido entregue. Bom apetite e obrigado pela preferência!" |
| `recusado` | Sim | Texto defensivo + motivo opcional. |
| `cancelado` | Sim | "Seu pedido foi cancelado. Se quiser refazer, é só me chamar." |

## ESCAPADA 2 — Itens com opções/adicionais (modifiers)

Um item do cardápio pode ter **grupos de opções** — por exemplo, *Tamanho* (Médio +R$5, Grande
+R$10) e *Adicionais* (Bacon +R$3). Cada opção é modelada como uma linha da sub-entidade
**`comida_menu_item_options`** (cada linha = uma opção de um grupo, com `price_delta_cents`).

No pedido, cada item carrega o **snapshot** das opções escolhidas numa tabela-filha
**`comida_order_item_options`**. **Decisão: tabela-filha, NÃO JSONB** — o snapshot preserva o
histórico do pedido mesmo que a opção seja apagada do cardápio depois.

O `unit_price` de cada item do pedido é **`preço base + Σ deltas das opções escolhidas`**,
**recalculado no backend**. A IA inclui na tag os `option_id` escolhidos; um `option_id` inválido
**aborta a criação** do pedido.

## Onda 1 do backlog (docs/FEATURES_SUGERIDAS_COMIDA.md #1/#2/#4/#8/#10/#15 — migration 85)

- **#1 Cupom (`comida_coupons`)**: clone do motor sushi/adega. A IA repassa SÓ o código no campo
  `cupom` da tag; o backend valida (active + validade + mínimo sobre o subtotal + max usos) e aplica
  com clamp; **inválido NÃO aborta** (o pedido sai sem desconto); `uses` incrementa na MESMA
  transação. CRUD `/api/comida/coupons`.
- **#2 Fidelidade (`comida_loyalty_config`, 1:1)**: conta os pedidos ENTREGUES do contato ANTES do
  insert; `count > 0 && count % threshold == 0` → desconto automático + `loyalty_applied`. O contexto
  anuncia o PROGRESSO ("faltam N pedidos") — quem aplica é o sistema. Cupom + fidelidade SOMAM,
  clampados ao subtotal; `total = subtotal − discount + delivery_fee`. Seed idempotente por company.
- **#8 Taxa por zona (`comida_delivery_zones`)**: a IA pergunta o BAIRRO, escolhe a zona da lista do
  contexto (id EXATO) e passa `zona_id` na tag; o backend resolve a taxa da zona ativa e SNAPSHOTA o
  nome no pedido (`zone_name_snapshot`). Zona ausente/inválida/inativa → taxa FLAT da config
  (fallback — nunca aborta).
- **#10 Endereço salvo (sem DDL)**: o contexto injeta o endereço do ÚLTIMO pedido do contato e a IA
  pergunta "mesmo endereço da última vez?" antes de pedir pra digitar.
- **#4 Upsell (sem DDL)**: UMA sugestão de complemento do PRÓPRIO cardápio (bebida/sobremesa/
  adicional) no fechamento, sem insistir, nada fora do cardápio.
- **#15 Relatórios (sem DDL)**: GET `/api/comida/reports/summary` — faturamento dos ENTREGUES
  (líquido), ticket médio, top itens e horário de pico (demanda por hora local).
- **ADIADOS por WIP não-commitado no tree** (drag-drop do Kanban nas telas de pedidos + extração de
  config): **#3 retirada no balcão** (exige selo de fulfillment no Kanban) e **#9 horário próprio do
  delivery** (toca o config em extração). Retomar quando esse WIP fechar. O ComidaMenuCache passou a
  ser keyed por `(companyId, contactId)` (TTL 60s) para o progresso de fidelidade e o endereço salvo.

## O que a IA faz

- Monta o pedido com as opções escolhidas em **linguagem livre** na conversa.
- **Confirma SEMPRE** com o valor total e o endereço antes de fechar (oferecendo o endereço do
  último pedido, se houver).
- Repassa o **cupom** informado pelo cliente e a **zona** do bairro na tag (quem valida/calcula é o
  sistema); pode anunciar o progresso da fidelidade.
- Pode sugerir **UMA vez** um complemento do próprio cardápio (upsell controlado).
- Emite a tag `<pedido_comida>`; o pedido nasce **aguardando**.
- Avisa o cliente que o pedido vai para **confirmação do restaurante**.

## O que a IA NÃO faz

- **Não aceita nem recusa** pedido — isso é o restaurante no painel (gate de aceite).
- **Não inventa** item, opção ou preço fora do cardápio.
- **Não define o total** — é recalculado pelo backend; o `total_cents` da tag é **descartado**.
- **Não promete nem calcula desconto** de cupom/fidelidade — só repassa o código/anuncia o progresso.
- **Não cria** item de cardápio.

## Tag `<pedido_comida>`

Formato JSON em texto livre (NÃO é tool calling do Gemini — texto livre + regex, igual ao
`<pedido>` do sushi). O `OutboundService` remove a tag antes de enviar a mensagem ao cliente.

```json
{
  "items": [
    { "item_id": "UUID", "qtd": 2, "options": ["UUID_OPCAO", "UUID_OPCAO"] }
  ],
  "endereco": "Rua X, 10",
  "cupom": "CODIGO (opcional)",
  "zona_id": "UUID da zona (opcional)",
  "total_cents": 0
}
```

- `options` é **opcional** por item; `cupom` e `zona_id` também (onda 1 #1/#8).
- `total_cents` é **ignorado** pelo backend (recalcula a partir do cardápio + deltas das opções,
  aplica cupom/fidelidade e a taxa da zona/flat).

## O que NÃO existe nesta fase

- **Foto** de item/cardápio (bloqueador `SERVICE_ROLE_KEY`).
- **Pagamento online** (Stripe).
- **Rastreio** de entregador / mapa / ETA dinâmica.
- **Avaliação/nota** do pedido (motor de campanha/NPS da Onda 3).
- **Retirada no balcão** e **horário próprio do delivery** — ADIADOS: colidem com o WIP
  não-commitado do tree (drag-drop do Kanban + extração de config); ver seção da onda 1.
- **Combos/promoção agendada** e **agendamento de pedido** (sushi cobre o padrão; fases futuras).
- **Regra de min/max de seleção obrigatória** por grupo de opção — cada grupo é livre.
- **Edição de pedido** depois de criado — só transição de status.
- **Entregador como entidade** / rastreio / mapa.

> **Nota (seam dos modifiers):** se a ESCAPADA 2 (opções/adicionais) for futuramente revertida ou
> adiada, a reintrodução é **puramente aditiva** (sem migration destrutiva) — ver o seam no
> histórico de planejamento.

## Notas técnicas

- Migration `47_comida.sql`.
- Categorias **hardcoded** (lanches / pizzas / pratos / porcoes / bebidas / sobremesas / combos) em
  sync `ComidaCategory.java` ↔ `comida-categories.ts`.
- Status **hardcoded** `ComidaOrderStatus` (Java) ↔ union em `comida-types.ts`.
- Guard de perfil: `/api/comida/**` → 403 `forbidden_wrong_profile` para tenant de outro perfil.
- Paleta `terracota`.
- Tenant de teste: `igorhaf16` (Comida Modelo).
