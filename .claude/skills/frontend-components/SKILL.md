---
name: frontend-components
description: Padrões de componentes e páginas do frontend Next.js/TypeScript do Meada. Use ao criar ou editar páginas em frontend/app, componentes em frontend/components, types em frontend/profiles ou clients em frontend/lib — nomenclatura, Server vs Client Components, types vs interface, imports absolutos, estrutura de pastas, FormState, hooks de sync (useSyncedForm), diálogos, sidebar por perfil.
---

# Componentes e páginas (Next.js + TypeScript)

Padrão canônico extraído da maioria do código (auditoria 2026-07: 540 `type` vs 3 `interface`;
1869 imports `@/` vs 2 relativos; 189 de 192 páginas client; ZERO fetch em useEffect).
Formatação é mecânica: `npm run format` (Prettier + plugin Tailwind + sort-imports) — não
discuta estilo de formatação em review, rode o comando.

## Estrutura de pastas (App Router — NÃO existe pages/)

- `app/(protected)/dashboard/<área>/page.tsx` — telas do painel (1 arquivo por rota; kebab-case
  prefixado pelo nicho: `atelie-proposals`, `barber-coupons`, `comida-zones`). Diálogo reusado
  só por uma tela vive ao lado dela (`create-service-dialog.tsx`), não em components/.
- `app/(auth)/login/page.tsx` — login universal (perfil pelo subdomínio, resolvido pós-mount).
- `app/p/...` — páginas públicas do CMS (server components, sem shell do painel).
- `components/ui/` — primitivos compartilhados (Button, Card, Section, Modal, Badge, AlertDialog,
  Skeleton). `components/layout/` — casca do painel (sidebar, page-header, nav-config).
- `components/<coisa>.tsx` — componentes compartilhados entre telas (global-search,
  create-invitation-dialog). Se só UMA tela usa, fica na pasta da tela.
- `lib/api/<dominio>/<recurso>.ts` — funções de fetch tipadas por recurso (1 arquivo por recurso).
- `lib/<utilitario>.ts` — hooks e helpers transversais (`use-synced-form.ts`, `utils.ts` com cn()).
- `profiles/<nicho>/<nicho>-types.ts` — types e helpers do nicho (espelham os records Java 1:1;
  paridade garantida por `*ParityTest` no backend — mudou lá, muda aqui, senão o build do backend
  quebra).

## Server vs Client

- Painel (`(protected)/dashboard`) = **Client Component**: primeira linha do arquivo é
  `'use client'` (antes de qualquer import).
- Páginas públicas do CMS (`app/p/`) = **Server Component** (sem `'use client'`, fetch no servidor
  via `lib/cms/public-fetch.ts`, que usa `CMS_BACKEND_URL` na rede do compose).
- Não misturar: página de painel nunca faz fetch no servidor; página pública nunca usa hooks.

## Types, não interfaces

```ts
// ✅ CERTO (padrão do projeto — espelha o record Java)
export type AtelieProposal = {
  id: string
  customerName: string
  totalCents: number
}

// ❌ ERRADO (interface só sobrevive em 2 arquivos legados; não criar novas)
export interface AtelieProposal { ... }
```

Union literais para ids de status/categoria, derivadas do array canônico (fonte única):

```ts
export const FITTING_STATUSES = [
  { id: 'pendente', label: 'Pendente' },
  { id: 'realizada', label: 'Realizada' },
] as const
export type FittingStatusId = (typeof FITTING_STATUSES)[number]['id']

// helpers de exibição moram no mesmo arquivo de types do nicho:
export function formatBrl(cents: number): string { ... }
```

## Imports

- SEMPRE absolutos com `@/`: `import { Button } from '@/components/ui/button'`. NUNCA `../`.
- Ordem por blocos (MECANIZADA pelo sort-imports do Prettier — `npm run format` corrige):
  1) libs externas (react, @tanstack, next), 2) linha em branco, 3) bloco `@/`,
  4) linha em branco, 5) relativos (raros).
- `import type { X }` para o que é só type (o formatter preserva).

## Nomenclatura

- Arquivos: kebab-case (`barber-types.ts`, `page-header.tsx`). Componentes: PascalCase, export
  default nas páginas (`export default function AtelieProposalsPage()`).
- Handlers: `handleX` ou arrow inline no JSX. Mutations: `xMutation` (`saveMutation`,
  `deleteMutation`, `statusMutation`). Query results: destructuring `{ data, isPending, isError }`
  (com rename quando há várias: `const proposalsQuery = useQuery(...)`).
- Textos de UI em pt-BR, sem i18n (regra do workspace: pt-BR hardcoded sempre).

## Esqueleto canônico de uma tela de painel

```tsx
'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Button } from '@/components/ui/button'
import { Card, Section } from '@/components/ui/card'
import { getConfig, updateConfig } from '@/lib/api/atelie/config'
import { useSyncedForm } from '@/lib/use-synced-form'

type FormState = { businessName: string; notes: string }

export default function AtelieSettingsPage() {
  const qc = useQueryClient()
  const { data, isPending, isError } = useQuery({ queryKey: ['atelie-config'], queryFn: () => getConfig() })
  const [form, setForm] = useSyncedForm(data, (d): FormState => ({
    businessName: d.businessName ?? '',
    notes: d.notes ?? '',
  }))
  // mutations…
  return (
    <div className="space-y-6">
      <PageHeader title="Configurações" description="…" />
      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar.</p>
      ) : isPending || !form ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : (
        <Card>{/* form */}</Card>
      )}
    </div>
  )
}
```

## Estado e formulários

- Estado local com `useState`; formulários controlados; sem lib de formulário nas telas de painel
  (react-hook-form/zod só onde já existe — diálogos do core).
- Estado de formulário: `type FormState = {...}` + (quando há criação) `const EMPTY: FormState`.
- Erros de mutação em estado local (`const [formError, setFormError] = useState<string | null>(null)`)
  renderizados como `<p className="text-sm text-destructive">`.

## Sync de estado com dado assíncrono — NUNCA setState em useEffect

A regra `react-hooks/set-state-in-effect` é ERRO no lint. Os três hooks de
`@/lib/use-synced-form` cobrem todos os casos (padrão oficial do React de "adjusting state when
props change" — setState durante o render, condicionado à mudança de referência):

```tsx
// ✅ form derivado de UMA query (o caso dominante — telas de settings)
const [form, setForm] = useSyncedForm(data, (d): FormState => ({ name: d.name ?? '' }))

// ✅ vários estados independentes sincronizados do mesmo dado
useOnSync(data, (d) => { setRows(buildRows(d)); setMeta(d.meta) })

// ✅ reset de formulário de diálogo ao abrir/trocar o registro em edição
useResetWhen(open ? (editing?.id ?? 'create') : null, () => { setForm(toForm(editing)); setError(null) })

// ❌ ERRADO — o padrão antigo (eliminado do projeto em 2026-07)
useEffect(() => { if (data) setForm({ ... }) }, [data])
```

`useEffect` continua correto para SISTEMA EXTERNO: listener de DOM/teclado, timer de debounce,
hidratação de localStorage, guard de montagem SSR. Nesses casos, se o efeito precisar de um
setState síncrono, use `// eslint-disable-next-line react-hooks/set-state-in-effect` com
justificativa em linha (há 6 casos legítimos no projeto — login, theme, sidebar, global-search;
siga o formato deles).

## Diálogos (criar/editar/excluir)

- Criar/editar: `<Modal>` com formulário controlado; excluir: `<AlertDialog>` com confirmação.
- Estado na tela-mãe: `const [editing, setEditing] = useState<X | null>(null)` +
  `const [modalOpen, setModalOpen] = useState(false)`.
- Reset ao abrir via `useResetWhen` (acima). Fechar SÓ no `onSuccess` da mutation — erro mantém
  o diálogo aberto com a mensagem em `formError`.

## Sidebar por perfil (nav-config)

Item novo de menu entra em `components/layout/nav-config.tsx`, no branch do perfil em
`getNavForProfile(profileId, features)` — NUNCA no grupo genérico (feature de nicho não pode
vazar pra outro nicho). Item gateado por feature flag testa `features?.<key> === true`.
