---
name: spring-error-handling
description: Padrão de tratamento de erros do backend do Meada. Use ao lançar/mapear exceções, definir códigos reason, mexer em GlobalExceptionHandler, desenhar respostas de erro da API Spring, ou escrever handlers de tag/notifiers/schedulers (best-effort).
---

# Tratamento de erros (contrato `{error, reason}`)

## O contrato de erro

TODA resposta de erro da API tem o mesmo corpo:

```json
{ "error": "Conflict", "reason": "duplicate_coupon" }
```

- `error`: o nome HTTP genérico ("Bad Request", "Forbidden", "Not Found", "Conflict",
  "Unprocessable Entity").
- `reason`: código snake_case ESTÁVEL, específico do domínio — é o contrato que o frontend usa
  no `ApiError.reason` para mensagens amigáveis. NUNCA renomear um reason existente (quebra as
  telas silenciosamente). NUNCA vazar stacktrace, SQL ou mensagem interna no corpo.

## Duas camadas (ambas canônicas — não substituir uma pela outra)

1. **Catch local no controller** (padrão dominante): exceções de DOMÍNIO declaradas como nested
   static RuntimeException no Service e traduzidas no endpoint que as provoca:

```java
// Service — a exceção documenta a regra
public static class DepositRequiredException extends RuntimeException {}
...
if (newStatus == FECHADA && current.depositCents() > 0 && !current.depositPaid()) {
    throw new DepositRequiredException();
}

// Controller — tradução explícita, um catch por reason
} catch (DepositRequiredException e) {
    return error(409, "Conflict", "deposit_required");
} catch (InvalidStatusTransitionException e) {
    return error(409, "Conflict", "invalid_status_transition");
}
```

2. **`com.meada.common.GlobalExceptionHandler` (@RestControllerAdvice)**: rede de segurança para
   erros GENÉRICOS (validação @Valid → 400, JSON malformado → 400, erro inesperado → 500 com
   corpo genérico). Não adicionar regra de domínio lá — domínio fica no catch local, perto do
   endpoint que o provoca.

Exceção de domínio pode carregar payload quando o cliente precisa de detalhe
(ex.: `SlotConflictException` leva quem ocupa o horário; `MixedDyeLotsException` leva as cores
ofensoras) — o controller monta o corpo com os campos extras AO LADO de `error`/`reason`.

## Mapa código HTTP → famílias de reason (exemplos reais do projeto)

| HTTP | Quando | Exemplos |
|------|--------|----------|
| 400 | formato/domínio inválido | `invalid_coupon`, `invalid_blocks`, `empty_budget`, `species_mismatch`, `outside_hours`, `unknown_profile` |
| 401 | sem/má autenticação | `missing_auth_header`, `invalid_token` |
| 403 | perfil/role errado | `forbidden_wrong_profile`, `forbidden_not_super_admin`, `feature_disabled` |
| 404 | não achou NO TENANT | `*_not_found` (query já escopada por company_id) |
| 409 | conflito de estado | `duplicate_*`, `*_in_use`, `*_locked`, `conflict_slot`, `invalid_status_transition`, `out_of_stock`, `domain_taken`, `already_active`, `art_not_approved` |
| 422 | pré-condição de negócio | `address_required`, `age_not_confirmed`, `lead_time_violation`, `turnaround_violation`, `mixed_dye_lots`, `invalid_schedule_date`, `vehicle_not_available` |

Convenção de nome: prefixo pelo PROBLEMA, não pela tabela (`duplicate_payment`, não
`payment_error`). Guard de perfil: sempre `forbidden_wrong_profile`.

## Best-effort nos fluxos assíncronos (regra de ouro: nunca derrubar o pipeline)

**Handlers de tag da IA** — falha → `Optional.empty()` + `log.warn`; a mensagem da IA segue sem
o efeito colateral. NUNCA propagar pro pipeline de outbound:

```java
// ✅ CERTO (handler de tag)
} catch (RuntimeException e) {
    log.warn("nicho: falha ao criar pedido p/ conversa {} ({}) — mensagem segue sem pedido",
        conversationId, e.getMessage());
    return Optional.empty();
}
```

**Notifiers (notificação outbound por status)** — enviar DEPOIS da regra de negócio; falha de
envio loga warn e NUNCA reverte a transação que mudou o status. Sem canal (contato sem WhatsApp,
POST manual sem conversation) → pula em silêncio.

**Scheduler jobs** — cada execução registrada via `ScheduledJobRunRepository`
(start/finishSuccess/finishFailed); exceção marca a run como failed com a mensagem, sem matar o
scheduler. Idempotência via marcador persistido — reprocessar não duplica efeito.

## Gotcha cravado: FK dentro de @Transactional aborta o commit em silêncio

`AuditLogger` insere com FK para `auth.users`; se o user não existe, a transação INTEIRA vira
rollback sem stacktrace útil. Em teste de service, SEMPRE semear `auth.users` (id) +
`public.users` (id, company_id, email, role) antes de exercitar código que audita.

## Lado do frontend (fecha o contrato)

`apiFetch` lança `ApiError` com `status` + `reason`; o `onError` da mutation trata reason por
reason com fallback genérico por último (ver skill nextjs-data-fetching). Reason novo no backend
= mensagem amigável nova na tela que o consome.
