---
name: nextjs-data-fetching
description: Padrões de data fetching do frontend do Meada. Use ao buscar ou mutar dados em frontend/ — TanStack Query, apiFetch, clients em lib/api, queryKeys, invalidação de cache, tratamento de erro por reason, paginação, loading/error states, Supabase SDK vs Spring REST.
---

# Data fetching (TanStack Query + apiFetch)

Padrão canônico: 186 páginas usam `useQuery`; ZERO usam fetch em `useEffect`. Nunca introduzir
fetch manual em componente.

## As duas vias de dados (não misturar)

1. **Spring REST via `apiFetch`** — TODOS os recursos de nicho (`/api/<nicho>/...`) e de root
   (`/admin/...`). É a via padrão para código novo.
2. **Supabase SDK + RLS** — só o CRUD interno LEGADO do tenant no core (services, faqs,
   business-hours do genérico), decisão da camada 4. Não estender a via SDK para nichos.

## Client de API por recurso (`lib/api/<dominio>/<recurso>.ts`)

Funções tipadas finas sobre `apiFetch` (que injeta o token Supabase e lança `ApiError` com o
`reason` do backend):

```ts
import { apiFetch } from '@/lib/api/client'
import type { AtelieCoupon } from '@/profiles/atelie/atelie-types'

export type CreateCouponInput = { code: string; kind: 'percent' | 'fixed'; value: number }

export function listCoupons(): Promise<{ items: AtelieCoupon[] }> {
  return apiFetch<{ items: AtelieCoupon[] }>('/api/atelie/coupons')
}

export function createCoupon(input: CreateCouponInput): Promise<AtelieCoupon> {
  return apiFetch<AtelieCoupon>('/api/atelie/coupons', { method: 'POST', body: JSON.stringify(input) })
}
```

- Inputs como `export type XInput = {...}`; PATCH parcial usa campos opcionais + flags `clearX`
  quando o backend precisa distinguir "não veio" de "limpar o campo".
- Valores monetários SEMPRE em cents (`priceCents`, `totalCents`) — número inteiro na API;
  conversão pra R$ só na borda da UI (`formatBrl` do `profiles/<nicho>-types`).
- Datas na API como string ISO (`YYYY-MM-DD` / timestamptz); parse/format na borda da UI.

## Leitura (useQuery)

```tsx
const { data, isPending, isError } = useQuery({
  queryKey: ['atelie-coupons'],
  queryFn: () => listCoupons(),
})
```

Taxonomia de queryKey (kebab-case, prefixada pelo nicho):

- Lista simples: `['atelie-coupons']` · Detalhe: `['atelie-proposal', id]`
- Lista filtrada/paginada: `['atelie-proposals', status, page]` — TODO filtro que muda o
  resultado entra na key (+ `placeholderData: keepPreviousData` para paginação sem flicker).
- Agregado/relatório: `['concessionaria-reports', months]`.
- Query dependente: `enabled: detailId !== null` (nunca condicional em volta do hook).

Render em estados, nesta ordem (erro → carregando → vazio → conteúdo):

```tsx
{isError ? (
  <p className="text-sm text-destructive">Erro ao carregar…</p>
) : isPending ? (
  <p className="text-sm text-muted-foreground">Carregando…</p>
) : items.length === 0 ? (
  <p className="text-sm text-muted-foreground">Nenhum registro ainda.</p>
) : ( /* lista */ )}
```

## Escrita (useMutation)

```tsx
const qc = useQueryClient()
const saveMutation = useMutation({
  mutationFn: () => createCoupon(payload),
  onSuccess: () => {
    qc.invalidateQueries({ queryKey: ['atelie-coupons'] }) // TODAS as keys afetadas
    setModalOpen(false)
    setForm(EMPTY)
    setFormError(null)
  },
  onError: (e) => {
    // ✅ trata por reason do backend (contrato de erro), fallback genérico por último
    if (e instanceof ApiError && e.reason === 'duplicate_coupon')
      setFormError('Já existe um cupom com esse código.')
    else if (e instanceof ApiError && e.reason === 'invalid_coupon')
      setFormError('Dados do cupom inválidos.')
    else setFormError('Erro ao salvar o cupom.')
  },
})
```

- Mutação que muda dado exibido em N telas invalida N queryKeys (detalhe + lista + agregados —
  ex.: mudar status do pedido invalida `['x-orders']` E `['x-reports']`).
- `disabled={mutation.isPending}` no submit; label alterna (`{isPending ? 'Salvando…' : 'Salvar'}`).
- Feedback de sucesso em settings: `setSaved(true); setTimeout(() => setSaved(false), 2500)`.
- SEM optimistic updates no projeto — invalidação simples; não introduzir sem decisão do arquiteto.

## Query → formulário (useSyncedForm)

O dado da query alimenta formulário controlado via `useSyncedForm(data, toForm)` de
`@/lib/use-synced-form` (sync durante o render — ver skill frontend-components). Refetch re-seta
o form; edição não salva é descartada (comportamento aceito nas telas de settings).

## O que NÃO fazer

```tsx
// ❌ fetch manual em efeito (zero ocorrências no projeto — manter assim)
useEffect(() => { fetch('/api/...').then(...) }, [])

// ❌ engolir o erro sem reason (perde o contrato de erro do backend)
onError: () => alert('erro')

// ❌ estado global de servidor (Redux/Zustand p/ dado de API) — o cache do Query É o estado
```
