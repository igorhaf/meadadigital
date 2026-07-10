
## Onda 1 do backlog (migration 106)

Entregue a partir de `docs/FEATURES_SUGERIDAS_FLORICULTURA.md` (#3, #4, #7, #8, #9 e #13):

- **#3 RECOMPRA DE 1 CLIQUE:** o contexto da IA agora é por **company:contact**
  (`FloriculturaCatalogCache.catalogSegment(companyId, contactId)`, resolvido no
  `ProfilePromptContext` — padrão comida) e injeta os últimos 3 pedidos ENTREGUES do contato
  (destinatário, itens, endereço, data) com instrução de oferecer "repetir o buquê da Ana".
- **#4 UPSELL controlado:** `floricultura_catalog_items.suggestible` — a IA sugere UM adicional
  marcado pelo tenant no fechamento (preço do catálogo, sem insistência). Checkbox no modal do
  catálogo + badge.
- **#7 CUPOM** (`floricultura_coupons`, motor comum — clone adega): campo `cupom` na tag
  `<pedido_flor>`; valida+recalcula no backend; inválido NÃO aborta. Tela "Cupons".
- **#8 FIDELIDADE por contagem** (`floricultura_loyalty_config`, default threshold 5 — "a cada 5
  buquês, brinde"): a cada N entregues do contato, o próximo ganha o reward. Tela "Fidelidade".
- **#9 CONFIRMAÇÃO D-1 DA ENTREGA:** `FloriculturaReminderJob` (cron 10:10) avisa o COMPRADOR na
  véspera (destinatário + endereço + período, "se algo mudou, me avisa"). Só pedidos ACEITOS
  (`em_preparo`); 1x por data (`delivery_reminded_date`, remarcar REARMA); toggle
  `delivery_reminder_enabled` (default ON) na config/settings.
- **#13 PRESENTE SURPRESA:** `"anonimo":true` na tag → `anonymous` no pedido; badge destacada no
  card do Kanban ("não revelar remetente") orienta o entregador; a IA confirma o anonimato com o
  comprador.

Teste: `FloriculturaOnda1IntegrationTest` (cupom/fidelidade/anonimato + lembrete D-1 com
rearm/toggle). Kanban ganhou badges (surpresa/cupom/fidelidade) e desconto no total.

**Fica pra onda 2** (registrado, não pedido): #1 sinal/pagamento no aceite (bloqueado por #50),
#2 lembrete de datas comemorativas (exige captura de datas do contato — o LTV play do nicho),
#5 taxa por bairro/CEP, #6 slots com capacidade por período, #11 assinatura/clube de flores
(chassi E), #15 corte de estoque em data de pico.
