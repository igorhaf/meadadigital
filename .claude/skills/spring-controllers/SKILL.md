---
name: spring-controllers
description: Padrões de Controller/Service/Repository do backend Spring Boot do Meada. Use ao criar ou editar classes em src/main/java — camadas por feature, DTOs como records, guard de perfil, Bean Validation, DI por construtor, JdbcTemplate (não JPA), REST, conflito transacional, snapshots, máquinas de status, handlers de tag da IA, testes de integração.
---

# Backend Spring Boot (camadas e REST)

Padrão canônico (auditoria 2026-07: 186 controllers, 100% injeção por construtor — zero
`@Autowired`; DTOs 100% records; JdbcTemplate — o projeto NÃO usa JPA, Lombok nem WebFlux;
HTTP outbound síncrono via RestClient).

## Pacote por feature, dentro do domínio

```
com.meada.profiles.<nicho>.<recurso>/
  <Recurso>.java                 // record de saída (espelha a tabela)
  <Recurso>Repository.java       // JdbcTemplate + RowMapper
  <Recurso>Service.java          // regras + exceções nested + auditoria
  <Recurso>Controller.java       // rotas + DTOs de request + mapeamento de erro
```

Core fora de profiles segue o mesmo shape (`com.meada.admin.companies`, `com.meada.outbound`…).
Enum de status/categoria do nicho vive no pacote do perfil e tem espelho TS + `*ParityTest`
(o build FALHA se Java e TS divergirem — sempre editar os dois lados).

## Onde a lógica mora

- **Controller**: parse/validação de formato (datas ISO → `LocalDate`, flags `clearX`), guard de
  perfil, tradução exceção→HTTP. NADA de regra de negócio.
- **Service**: regras, validações de domínio, `@Transactional`, `AuditLogger`, invalidação de
  cache de contexto da IA. Exceções de domínio como **nested static RuntimeException**.
- **Repository**: SQL via `JdbcTemplate`, `RowMapper` estático, escopo `company_id` em TODO WHERE
  (defesa multi-tenant), derivados MATERIALIZADOS na mesma transação.

## DI por construtor (sem @Autowired)

```java
// ✅ CERTO — único construtor, campos final
private final AtelieCouponRepository repository;
private final AuditLogger auditLogger;

public AtelieCouponService(AtelieCouponRepository repository, AuditLogger auditLogger) {
    this.repository = repository;
    this.auditLogger = auditLogger;
}

// ❌ ERRADO — field injection
@Autowired private AtelieCouponRepository repository;
```

## Controller: shape canônico

```java
@RestController
public class AtelieCouponController {

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    // DTOs de request como records aninhados, com Bean Validation jakarta:
    public record CreateRequest(
        @NotBlank @Size(max = 40) String code,
        @NotNull Integer value) {}

    @PostMapping("/api/atelie/coupons")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAtelie(user);   // guard SEMPRE primeiro
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.status(201).body(service.create(companyId, user.userId(), ...));
        } catch (InvalidCouponException e) {
            return error(400, "Bad Request", "invalid_coupon");
        } catch (DuplicateCouponException e) {
            return error(409, "Conflict", "duplicate_coupon");
        }
    }
}
```

Rota nova de tenant SÓ funciona se o prefixo do perfil (`/api/<nicho>/**`) estiver autenticado
no `JwtAuthenticationFilter` — perfil novo = adicionar o prefixo lá.

## Convenções REST

- Rotas: `/api/<nicho>/<recurso>` (tenant, plural kebab-case) e `/admin/...` (root, gate
  super-admin inline `notSuperAdmin(user)` → 403 `forbidden_not_super_admin`).
- Verbos: GET lista/detalhe; POST cria (201); PATCH parcial (flags `clearX` para anular campo);
  PUT upsert de config 1:1; DELETE → 204. Sub-recurso de status: `PATCH .../{id}/status`.
- Listas paginadas: `Map.of("items", ..., "total", total, "page", page, "pageSize", size)`;
  listas simples: `Map.of("items", ...)`.
- Dinheiro em cents (int), datas `LocalDate`/`OffsetDateTime`, fuso de negócio
  `America/Sao_Paulo` para "hoje"/janelas de horário.

## Padrões estruturais dos nichos (reusar, não reinventar)

- **Conflito de agenda transacional**: `findConflict` com janela half-open
  (`NOT (end_at <= :start OR start_at >= :end)`), filtrado por recurso disputado
  (`professional_id`/`barber_id`/company) e só status bloqueantes; o INSERT RE-VERIFICA dentro
  da `@Transactional` (fecha a corrida) → `SlotConflictException` → 409 `conflict_slot`.
- **Derivados materializados no INSERT em Java** (não em coluna GENERATED nem na SET clause):
  `end_at = start_at + duration`, `total_cents`, `line_total_cents`, `delivery_date`
  (timestamptz+interval não é IMMUTABLE no Postgres — lição cravada).
- **Snapshots**: pedido/agendamento congela nome/preço/duração do catálogo no momento — mudar o
  catálogo depois NÃO altera registros passados.
- **Estoque/saldo com UPDATE condicional**: `set stock = stock - :q where id = :id and stock >= :q`;
  0 linhas afetadas → exceção → rollback do pedido inteiro (409 `out_of_stock`).
- **Máquina de status hardcoded**: enum Java + espelho TS + ParityTest; transição inválida →
  409 `invalid_status_transition`; notificação outbound por status é opt-in explícito e
  best-effort (Notifier nunca reverte a transação).
- **Total da IA é DESCARTADO**: handler de tag recalcula tudo do catálogo; a IA nunca fixa preço.

## Handlers de tag da IA (`<tag>{json}</tag>`)

Shape canônico (`hasTag` / `stripTag` / `parseAndCreate`), parse por regex (NÃO tool calling —
conflita com o responseSchema do fluxo outbound). O `OutboundService` ganha um
`maybeProcessX(...)` encadeado após os dos outros perfis (perfil é único → só um age) e REMOVE a
tag antes de enviar ao cliente. Falha no handler → `Optional.empty()` + `log.warn` (best-effort;
a mensagem segue sem o efeito). Contexto pro prompt via `<Nicho>ContextCache` (Caffeine, TTL
10–60s conforme a volatilidade), invalidado EXPLICITAMENTE em toda mutação do service.

## Scheduler jobs

Job `@Scheduled` fino, instrumentado por `ScheduledJobRunRepository.start/finishSuccess/
finishFailed`, com método público testável, idempotência por marcador persistido
(`reminded_due_date`, `followup_sent_at`) e respeito ao `EVOLUTION_DRY_RUN` (marca sem enviar
quando não há canal).

## Testes (gate: `mvn -B clean test` — contagem do Surefire, nunca grep @Test)

- Integração: estender `AbstractIntegrationTest`; migration NOVA entra no array `SCRIPTS` — a
  migration que REESCREVE a CHECK de `companies.profile_id` precisa ser a ÚLTIMA e conter TODOS
  os perfis (lição atelie/casamento).
- Service test com `AuditLogger` DEVE semear `auth.users` + `public.users` antes (FK dentro da
  transação aborta o commit em silêncio — gotcha cravado).
- Clonar teste por sed: conferir depois que valores case-sensitive foram trocados nos DOIS casos
  (lição `PRIMEIRA10`/`primeira10`).
