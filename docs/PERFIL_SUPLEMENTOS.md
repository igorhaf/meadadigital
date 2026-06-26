# Perfil Suplementos (loja de saúde · nutrição esportiva) — camada 8.24

Guia operacional do tenant **suplementos** (`profile_id='suplementos'`). Loja de varejo de suplementos
(whey, creatina, vitaminas, pré-treino, termogênicos, acessórios) com entrega. A IA atende clientes pelo
WhatsApp, monta o pedido pela VARIANTE (sabor×peso), e a loja acompanha num Kanban com gate de aceite. Tom
prestativo-informativo de balconista — **sem jamais virar conselheiro de saúde**.

É o **34º perfil vertical** (33 + generic). CLONA o chassi order-based do Comida + o chassi de variantes
com estoque do Lingerie, e inaugura, num perfil de varejo, a TRAVA DE NÃO-PRESCRIÇÃO.

## Telas (sidebar "Suplementos")

| Tela | Rota | O que faz |
|------|------|-----------|
| Produtos | `/dashboard/suplementos-products` | CRUD de produtos + grade de variantes (sabor×peso, preço, estoque, validade). |
| Pedidos | `/dashboard/suplementos-orders` | Kanban com gate de aceite + entrega. |
| Configurações | `/dashboard/suplementos-settings` | Taxa de entrega + pedido mínimo. |

## ESCAPADA 1 — Catálogo com variantes (sabor × peso) e estoque

Um produto (`sup_products`: "Whey Protein", com marca + categoria + descrição) tem N **variantes vendáveis**
(`sup_variants`): "Chocolate 900g", "Baunilha 2kg" — cada uma com SEU `price_cents`, `sku` e `stock_quantity`
(+ `expiry_date` administrativo). A variante é o SKU real; o pedido referencia a VARIANTE.

**Decremento transacional de estoque:** ao criar o pedido, por linha o backend faz `UPDATE sup_variants SET
stock_quantity = stock_quantity - qtd WHERE id = ? AND active = true AND stock_quantity >= qtd`. Se 0 linhas
→ **409 `out_of_stock`** e o pedido inteiro ABORTA (rollback, sem pedido parcial). `expiry_date` é
informativo (a IA não promete validade). Cancelar NÃO devolve estoque nesta SM.

`flavor`/`size_label`/`sku` são texto livre (acessório pode ter flavor null). UNIQUE(company, sku) quando
sku não-null.

## ESCAPADA 2 (o coração) — TRAVA DE SAÚDE / não-prescrição

Suplemento NÃO é medicamento e a IA NÃO é profissional de saúde. A IA, em TODA a conversa:

- NUNCA prescreve DOSAGEM/posologia/"quanto tomar"/horário; NUNCA recomenda como TRATAMENTO/conduta por
  objetivo (emagrecer/ganhar massa/curar) ou sintoma; NUNCA responde "isso serve pra [objetivo]?"/"isso
  engorda/emagrece?"/"posso tomar com [remédio]?"/"qual o melhor pra mim?"; NUNCA opina sobre saúde/
  patologia/interação medicamentosa/contraindicação.
- Para QUALQUER dúvida de uso/dosagem/objetivo → acolhe e ORIENTA consultar nutricionista/médico/educador
  físico. Aviso defensivo: "este produto não substitui orientação de um profissional de saúde".
- A IA SÓ: mostra catálogo, tira dúvida de PRODUTO (sabor/peso/preço/disponibilidade/estoque), monta o
  pedido.

A trava vive em DOIS lugares (igual nutri): na **persona** (`ProfilePromptContext.SUPLEMENTOS`) E no bloco
de INSTRUÇÕES do contexto injetado (`SuplementosMenuCache`).

## Chassi de varejo / pedido (comida + gate)

Pedido nasce `aguardando` (**gate de aceite humano**: a loja ACEITA → `em_preparo` ou RECUSA → `recusado`
com motivo; a IA NÃO aceita/recusa). Status `SuplementosOrderStatus` ↔ TS (parity): aguardando→em_preparo→
saiu_entrega→entregue + recusado/cancelado. SÓ ENTREGA (`delivery_address` NOT NULL → senão 422
`address_required`, + taxa). Total materializado (descarta o da IA). Snapshots de produto/variante/preço.
Categorias hardcoded (`SuplementosCategory` parity): proteinas, aminoacidos, vitaminas, pre_treino,
emagrecedores, acessorios. Tag `<pedido_suplementos>{"delivery_address","items":[{"variant_id","qtd"}],
"notes"}`.

## O que NÃO existe nesta fase

Pagamento real (Stripe #50); foto de produto (SERVICE_ROLE_KEY); recomendação personalizada / quiz de
suplemento (PROIBIDO pela trava); assinatura/recorrência (academia cobre); devolução de estoque ao cancelar;
lote/FEFO/inventário; tabela nutricional estruturada; combo/cupom; retirada na loja (só entrega).

## Notas técnicas

- Migration `68_suplementos.sql` (5 tabelas: config, products, variants, orders, order_items). A CHECK
  ACRESCENTA `'suplementos'` preservando os 33 perfis. Entra por ÚLTIMO no `SCRIPTS` do
  `AbstractIntegrationTest` (sua CHECK tem os 34).
- Cache `SuplementosMenuCache` TTL 60s (ignora conversationId; carrega catálogo + a trava). Base de
  conhecimento (RAG): disponível como em todo perfil.
- Guard `/api/suplementos/**` → 403 `forbidden_wrong_profile`. Paleta `eucalipto`. Tenant: `igorhaf35`.
