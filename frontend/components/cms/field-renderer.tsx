'use client'

import type { FieldDef } from '@/lib/cms/cms-block-schemas'

/**
 * Renderiza UM campo do schema de um bloco (cms-block-schemas), montando o input adequado ao tipo.
 * Port do FieldRenderer do alegria, re-estilizado pros tokens do whatsapp (border-border/bg-background/
 * text-muted-foreground). O `repeater` é recursivo (array de objetos com itemSchema próprio).
 */

const inputClass =
  'w-full rounded-md border border-border bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40'

export function FieldRenderer({
  field,
  value,
  onChange,
}: {
  field: FieldDef
  value: unknown
  onChange: (v: unknown) => void
}) {
  if (field.type === 'repeater') {
    return (
      <RepeaterField
        field={field}
        value={Array.isArray(value) ? value : []}
        onChange={onChange as (v: unknown[]) => void}
      />
    )
  }

  if (field.type === 'textarea') {
    return (
      <Wrapper field={field}>
        <textarea
          value={(value as string) ?? ''}
          onChange={(e) => onChange(e.target.value)}
          rows={3}
          className={inputClass}
        />
      </Wrapper>
    )
  }

  if (field.type === 'select') {
    return (
      <Wrapper field={field}>
        <select
          value={(value as string) ?? ''}
          onChange={(e) => onChange(e.target.value)}
          className={inputClass}
        >
          <option value="">— escolha —</option>
          {field.options?.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
      </Wrapper>
    )
  }

  if (field.type === 'checkbox') {
    return (
      <label className="flex cursor-pointer items-center gap-2 py-1.5 text-sm">
        <input type="checkbox" checked={!!value} onChange={(e) => onChange(e.target.checked)} />
        {field.label}
        {field.hint && <span className="ml-2 text-xs text-muted-foreground">{field.hint}</span>}
      </label>
    )
  }

  if (field.type === 'color') {
    return (
      <Wrapper field={field}>
        <div className="flex items-center gap-2">
          <input
            type="color"
            value={(value as string) || '#cccccc'}
            onChange={(e) => onChange(e.target.value)}
            className="h-10 w-10 cursor-pointer rounded"
          />
          <input
            type="text"
            value={(value as string) ?? ''}
            onChange={(e) => onChange(e.target.value)}
            className={inputClass}
            placeholder="#000000"
          />
        </div>
      </Wrapper>
    )
  }

  if (field.type === 'number') {
    return (
      <Wrapper field={field}>
        <input
          type="number"
          value={(value as number | string) ?? ''}
          onChange={(e) => onChange(e.target.value === '' ? null : Number(e.target.value))}
          className={inputClass}
        />
      </Wrapper>
    )
  }

  // text, url
  return (
    <Wrapper field={field}>
      <input
        type="text"
        value={(value as string) ?? ''}
        onChange={(e) => onChange(e.target.value)}
        className={inputClass}
        placeholder={field.placeholder ?? (field.type === 'url' ? 'https://…' : '')}
      />
    </Wrapper>
  )
}

function Wrapper({ field, children }: { field: FieldDef; children: React.ReactNode }) {
  return (
    <div className="space-y-1">
      <label className="block text-xs font-medium text-muted-foreground">{field.label}</label>
      {children}
      {field.hint && <p className="text-[11px] text-muted-foreground">{field.hint}</p>}
    </div>
  )
}

function RepeaterField({
  field,
  value,
  onChange,
}: {
  field: FieldDef
  value: unknown[]
  onChange: (v: unknown[]) => void
}) {
  const items = value as Record<string, unknown>[]

  function updateItem(idx: number, key: string, v: unknown) {
    const next = [...items]
    next[idx] = { ...next[idx], [key]: v }
    onChange(next)
  }
  function removeItem(idx: number) {
    onChange(items.filter((_, i) => i !== idx))
  }
  function addItem() {
    const empty: Record<string, unknown> = {}
    field.itemSchema?.forEach((f) => {
      empty[f.key] = f.type === 'checkbox' ? false : ''
    })
    onChange([...items, empty])
  }
  function move(idx: number, dir: -1 | 1) {
    const target = idx + dir
    if (target < 0 || target >= items.length) return
    const next = [...items]
    ;[next[idx], next[target]] = [next[target], next[idx]]
    onChange(next)
  }

  return (
    <div className="space-y-3">
      <div className="text-xs font-medium text-muted-foreground">{field.label}</div>
      {items.length === 0 && (
        <p className="text-xs text-muted-foreground italic">Nenhum item ainda.</p>
      )}
      {items.map((item, idx) => (
        <div key={idx} className="space-y-2.5 rounded-lg border border-border bg-muted/30 p-3">
          <div className="flex items-center justify-between">
            <span className="text-[11px] tracking-widest text-muted-foreground uppercase">
              {field.itemLabel ?? 'item'} {idx + 1}
            </span>
            <div className="flex gap-1">
              <button
                type="button"
                onClick={() => move(idx, -1)}
                disabled={idx === 0}
                className="rounded px-2 py-1 text-xs hover:bg-muted disabled:opacity-30"
              >
                ↑
              </button>
              <button
                type="button"
                onClick={() => move(idx, 1)}
                disabled={idx === items.length - 1}
                className="rounded px-2 py-1 text-xs hover:bg-muted disabled:opacity-30"
              >
                ↓
              </button>
              <button
                type="button"
                onClick={() => removeItem(idx)}
                className="rounded px-2 py-1 text-xs text-destructive hover:bg-destructive/10"
              >
                Excluir
              </button>
            </div>
          </div>
          {field.itemSchema?.map((f) => (
            <FieldRenderer
              key={f.key}
              field={f}
              value={item[f.key]}
              onChange={(v) => updateItem(idx, f.key, v)}
            />
          ))}
        </div>
      ))}
      <button
        type="button"
        onClick={addItem}
        className="w-full rounded-lg border border-dashed border-border py-2 text-xs font-medium text-muted-foreground hover:bg-muted"
      >
        + Adicionar {field.itemLabel ?? 'item'}
      </button>
    </div>
  )
}
