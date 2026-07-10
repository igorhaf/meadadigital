---
name: tailwind-styling
description: Padrões de estilização Tailwind 4 do frontend do Meada. Use ao escrever ou editar className em frontend/ — ordem das classes, tokens de tema vs valores arbitrários, quando usar cn() vs template literal, primitivos de UI, formulários, tabelas, badges de status, paleta por nicho.
---

# Estilização (Tailwind 4)

A ORDEM das classes é mecânica desde 2026-07: `npm run format` (prettier-plugin-tailwindcss,
configurado com `tailwindStylesheet: app/globals.css` e `tailwindFunctions: ["cn"]`) reordena
tudo sozinho. Não reordene na mão, não discuta ordem em review — rode o comando.

## Tokens do tema, não valores arbitrários

Use os tokens semânticos do tema (definidos em `app/globals.css`) — eles respondem ao dark mode
e à paleta por nicho:

```tsx
// ✅ CERTO
<p className="text-sm text-muted-foreground">…</p>
<div className="rounded-lg border border-border bg-background">…</div>
<p className="text-sm text-destructive">{error}</p>

// ❌ ERRADO (cor crua quebra dark mode/paleta do nicho)
<p className="text-sm text-[#6b7280]">…</p>
<div className="border-gray-200 bg-white">…</div>
```

Tokens correntes: `background`, `foreground`, `border`, `muted`, `muted-foreground`, `primary`,
`primary-foreground`, `destructive`, `accent`. Exceções toleradas (já majoritárias no código):
verdes/vermelhos utilitários de feedback (`text-emerald-600` para "salvo", `text-red-600`,
`bg-green-100`) — manter consistência com o Badge.

## Paleta por nicho

Cada perfil vertical tem uma paleta nomeada (grafite, âmbar, beringela, mostarda, oliva, ameixa,
ferrugem, abóbora, lavanda, ardósia, floresta, eucalipto…) aplicada via tema — o componente NÃO
escolhe cor por nicho. Se a tela precisa "da cor do nicho", usa `primary`; a paleta certa chega
pelo tema do perfil. NUNCA hardcodar a cor de um nicho num componente.

## Condicionais: template literal como padrão; cn() para merge de className

```tsx
// ✅ CERTO — condicional simples (padrão dominante nas páginas)
className={`rounded-full border px-3 py-1 text-xs ${active ? 'border-primary bg-primary/10' : 'border-border'}`}

// ✅ CERTO — componente de UI que aceita className externo (cn de @/lib/utils = clsx + twMerge)
import { cn } from '@/lib/utils'
<div className={cn('rounded-lg border p-4', className)} />

// ❌ ERRADO — concatenação manual de className de props (conflitos não resolvidos)
<div className={'rounded-lg border p-4 ' + className} />
```

Regra de escolha: página compondo suas PRÓPRIAS classes → template literal; componente que
RECEBE `className` de fora → `cn()` (o twMerge resolve conflito `p-4` vs `p-2`).

## Primitivos antes de classes soltas

Para padrões já encapsulados, use os componentes de `components/ui/` em vez de recriar:

- `<Badge variant="success|warning|danger|info|muted">` — status de registro (pedido, proposta,
  consulta). O mapeamento status→variant mora na tela ou no types do nicho, não no Badge.
- `<Button variant="outline" className="h-7 px-2 text-xs">` — ação secundária compacta em linha
  de tabela/lista; default para ação primária.
- `<Card>` — bloco de conteúdo; `<Section title="…">` — subdivisão titulada dentro do Card.
- `<Modal size="sm|md|lg">` e `<AlertDialog>` — criar/editar e confirmação destrutiva.
- `<PageHeader title description>` — topo de TODA tela do painel.

## Ritmo da página e espaçamento

- Raiz de toda tela: `<div className="space-y-6">`. Grupos de campos: `space-y-4`.
- Linhas flexíveis: `flex items-center justify-between gap-3` (gap-2 para chips, gap-4 para
  colunas largas). Grids de cards de métrica: `grid grid-cols-3 gap-4`.

## Formulários

```tsx
// label + campo canônicos
<label className="mb-1 block text-xs font-medium text-muted-foreground">Nome</label>
<input className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
// checkbox com texto explicativo
<label className="flex items-start gap-2 text-sm">
  <input type="checkbox" className="mt-0.5" … />
  <span>Título <span className="block text-xs text-muted-foreground">explicação</span></span>
</label>
// feedback pós-submit
{error && <p className="text-sm text-destructive">{error}</p>}
{saved && <p className="text-sm text-emerald-600">Configurações salvas.</p>}
// rodapé
<div className="flex justify-end"><Button type="submit" disabled={m.isPending}>…</Button></div>
```

## Tabelas e listas

- Lista de registros: `divide-y divide-border rounded-lg border border-border` com linhas
  `flex items-center justify-between gap-3 px-4 py-3`.
- Tabela de relatório: wrapper `overflow-x-auto rounded-lg border border-border` +
  `<table className="w-full text-sm">`; thead
  `border-b border-border text-left text-xs text-muted-foreground` com `<th className="px-3 py-2
  font-medium">`; tbody `divide-y divide-border`; números SEMPRE `text-right tabular-nums`.
- Kanban: colunas dinâmicas em flex com `min-w-` fixo; card com Badge de status e seletor de
  transição (não botão "avançar" linear).

## Estados interativos

- Hover em linha clicável: `hover:bg-muted/40`. Seleção ativa: `border-primary bg-primary/10`.
- Desabilitado: prop `disabled` no Button (nunca simular com opacity manual).
- Dark mode: NÃO escrever `dark:` na mão nas telas — os tokens já respondem; `dark:` só nos
  primitivos de ui/ e no CSS do tema.
