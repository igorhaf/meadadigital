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
| Configurações | `/dashboard/comida-settings` | Taxa de entrega (flat) + pedido mínimo. |

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

## O que a IA faz

- Monta o pedido com as opções escolhidas em **linguagem livre** na conversa.
- **Confirma SEMPRE** com o valor total e o endereço antes de fechar.
- Emite a tag `<pedido_comida>`; o pedido nasce **aguardando**.
- Avisa o cliente que o pedido vai para **confirmação do restaurante**.

## O que a IA NÃO faz

- **Não aceita nem recusa** pedido — isso é o restaurante no painel (gate de aceite).
- **Não inventa** item, opção ou preço fora do cardápio.
- **Não define o total** — é recalculado pelo backend; o `total_cents` da tag é **descartado**.
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
  "total_cents": 0
}
```

- `options` é **opcional** por item.
- `total_cents` é **ignorado** pelo backend (recalcula a partir do cardápio + deltas das opções).

## O que NÃO existe nesta fase

- **Foto** de item/cardápio (bloqueador `SERVICE_ROLE_KEY`).
- **Pagamento online** (Stripe).
- **Rastreio** de entregador / mapa / ETA dinâmica.
- **Avaliação/nota** do pedido.
- **Cupom/desconto/promoção**.
- **Horário de funcionamento próprio** do delivery (usa o comercial genérico).
- **Múltiplos endereços salvos** — o endereço vem na tag, texto livre.
- **Regra de min/max de seleção obrigatória** por grupo de opção — cada grupo é livre.
- **Edição de pedido** depois de criado — só transição de status.
- **Entregador como entidade.**
- **Taxa por bairro/distância** — a taxa é flat, 1 valor por company.

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
