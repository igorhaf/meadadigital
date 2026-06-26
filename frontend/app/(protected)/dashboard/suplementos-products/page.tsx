'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { ApiError } from '@/lib/api/client'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import {
  createProduct,
  createVariant,
  deleteProduct,
  deleteVariant,
  listProducts,
  toggleProduct,
  toggleVariant,
  updateProduct,
  updateVariant,
} from '@/lib/api/suplementos/products'
import {
  SUPLEMENTOS_CATEGORIES,
  type SuplementosCategoryId,
} from '@/profiles/suplementos/suplementos-categories'
import { formatBrl, type Product, type Variant } from '@/profiles/suplementos/suplementos-types'

type FormState = {
  name: string
  brand: string
  description: string
  category: SuplementosCategoryId
}

const EMPTY_FORM: FormState = { name: '', brand: '', description: '', category: 'proteinas' }

// flavor (opcional), sizeLabel, sku, price (R$), stock, expiryDate (opcional, YYYY-MM-DD)
type VariantForm = {
  flavor: string
  sizeLabel: string
  sku: string
  price: string
  stock: string
  expiry: string
}
const EMPTY_VARIANT: VariantForm = { flavor: '', sizeLabel: '', sku: '', price: '', stock: '0', expiry: '' }

/**
 * Catálogo do SuplementosBot (loja de saúde / nutrição esportiva / varejo). Produtos agrupados por
 * categoria, com marca + descrição, toggle de disponibilidade inline, criação/edição via Modal,
 * busca por nome. Cada produto tem um modal secundário "Variantes" — a ESCAPADA desta camada:
 * grade SABOR × TAMANHO (peso/tamanho) com SKU, preço próprio, ESTOQUE (stockQuantity) editável e
 * VALIDADE opcional. Variante sem estoque é destacada. Preços salvos em centavos.
 */
export default function SuplementosProductsPage() {
  const qc = useQueryClient()
  const [search, setSearch] = useState('')
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<Product | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY_FORM)
  const [formError, setFormError] = useState<string | null>(null)

  // Modal secundário de variantes: aberto pelo botão "Variantes" do card.
  const [variantsProduct, setVariantsProduct] = useState<Product | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['suplementos-products'],
    queryFn: () => listProducts(),
  })

  const saveMutation = useMutation({
    mutationFn: async () => {
      const payload = {
        name: form.name,
        brand: form.brand || null,
        description: form.description || null,
        category: form.category,
      }
      if (editing) return updateProduct(editing.id, payload)
      return createProduct(payload)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['suplementos-products'] })
      setModalOpen(false)
      setEditing(null)
      setForm(EMPTY_FORM)
      setFormError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'invalid_category') {
        setFormError('Categoria inválida.')
      } else {
        setFormError('Erro ao salvar o produto.')
      }
    },
  })

  const toggleMutation = useMutation({
    mutationFn: (p: Product) => toggleProduct(p.id, !p.active),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['suplementos-products'] }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteProduct(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['suplementos-products'] }),
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'product_in_use') {
        alert('Este produto está em pedidos — não pode ser excluído. Desative-o em vez disso.')
      }
    },
  })

  function openCreate() {
    setEditing(null)
    setForm(EMPTY_FORM)
    setFormError(null)
    setModalOpen(true)
  }

  function openEdit(p: Product) {
    setEditing(p)
    setForm({
      name: p.name,
      brand: p.brand ?? '',
      description: p.description ?? '',
      category: p.category,
    })
    setFormError(null)
    setModalOpen(true)
  }

  const products = (data?.items ?? []).filter((p) =>
    p.name.toLowerCase().includes(search.trim().toLowerCase()),
  )

  // O modal de variantes precisa do produto SEMPRE fresco da query (após mutações de variante).
  const liveVariantsProduct =
    variantsProduct && (data?.items ?? []).find((p) => p.id === variantsProduct.id)

  return (
    <div className="space-y-6">
      <PageHeader
        title="Catálogo"
        description="Produtos da sua loja. A IA usa este catálogo (com as variantes e estoque) para atender os clientes."
        actions={<Button onClick={openCreate}>Novo produto</Button>}
      />

      <input
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        placeholder="Buscar por nome…"
        className="w-full max-w-sm rounded-md border border-border bg-background px-3 py-2 text-sm"
      />

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar o catálogo.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : (
        <div className="space-y-8">
          {SUPLEMENTOS_CATEGORIES.map((cat) => {
            const catProducts = products.filter((p) => p.category === cat.id)
            return (
              <section key={cat.id} className="space-y-2">
                <h2 className="text-sm font-semibold text-muted-foreground">{cat.label}</h2>
                {catProducts.length === 0 ? (
                  <p className="text-xs text-muted-foreground">Nenhum produto nesta categoria ainda.</p>
                ) : (
                  <div className="divide-y divide-border rounded-lg border border-border">
                    {catProducts.map((p) => {
                      const inStock = p.variants.reduce((sum, v) => sum + v.stockQuantity, 0)
                      const fromPrice =
                        p.variants.length > 0 ? Math.min(...p.variants.map((v) => v.priceCents)) : null
                      return (
                        <div key={p.id} className="flex items-center justify-between gap-3 px-4 py-3">
                          <div className="min-w-0">
                            <div className="flex items-center gap-2">
                              <span className="font-medium">{p.name}</span>
                              {p.brand && <span className="text-xs text-muted-foreground">{p.brand}</span>}
                              {!p.active && <Badge variant="muted">indisponível</Badge>}
                              {p.variants.length > 0 ? (
                                <Badge variant={inStock > 0 ? 'info' : 'danger'}>
                                  {p.variants.length} var. · {inStock} em estoque
                                </Badge>
                              ) : (
                                <Badge variant="muted">sem variantes</Badge>
                              )}
                            </div>
                            {p.description && (
                              <p className="truncate text-xs text-muted-foreground">{p.description}</p>
                            )}
                          </div>
                          <div className="flex shrink-0 items-center gap-3">
                            <span className="tabular-nums">
                              {fromPrice == null ? '—' : `a partir de ${formatBrl(fromPrice)}`}
                            </span>
                            <label className="flex items-center gap-1 text-xs text-muted-foreground">
                              <input
                                type="checkbox"
                                checked={p.active}
                                disabled={toggleMutation.isPending}
                                onChange={() => toggleMutation.mutate(p)}
                              />
                              disponível
                            </label>
                            <Button variant="outline" className="h-7 px-2 text-xs" onClick={() => setVariantsProduct(p)}>
                              Variantes
                            </Button>
                            <Button variant="outline" className="h-7 px-2 text-xs" onClick={() => openEdit(p)}>
                              Editar
                            </Button>
                            <Button
                              variant="outline"
                              className="h-7 px-2 text-xs"
                              disabled={deleteMutation.isPending}
                              onClick={() => deleteMutation.mutate(p.id)}
                            >
                              Excluir
                            </Button>
                          </div>
                        </div>
                      )
                    })}
                  </div>
                )}
              </section>
            )
          })}
        </div>
      )}

      {/* Modal: criar/editar produto */}
      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title={editing ? 'Editar produto' : 'Novo produto'} size="md">
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault()
            saveMutation.mutate()
          }}
        >
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Nome</label>
            <input value={form.name} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))} required
              maxLength={120} className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Marca (opcional)</label>
            <input value={form.brand} onChange={(e) => setForm((f) => ({ ...f, brand: e.target.value }))}
              maxLength={80} placeholder="Ex.: Growth, Max Titanium…"
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Descrição (opcional)</label>
            <textarea value={form.description} onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
              rows={2} className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Categoria</label>
            <select value={form.category}
              onChange={(e) => setForm((f) => ({ ...f, category: e.target.value as SuplementosCategoryId }))}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm">
              {SUPLEMENTOS_CATEGORIES.map((c) => (
                <option key={c.id} value={c.id}>{c.label}</option>
              ))}
            </select>
          </div>
          {formError && <p className="text-sm text-destructive">{formError}</p>}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>Cancelar</Button>
            <Button type="submit" disabled={saveMutation.isPending}>
              {saveMutation.isPending ? 'Salvando…' : editing ? 'Salvar' : 'Criar'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Modal secundário: grade de variantes (sabor × tamanho) com estoque */}
      <Modal
        open={variantsProduct !== null}
        onClose={() => setVariantsProduct(null)}
        title={liveVariantsProduct ? `Variantes — ${liveVariantsProduct.name}` : 'Variantes'}
        size="lg"
      >
        {liveVariantsProduct ? (
          <VariantsEditor product={liveVariantsProduct} />
        ) : (
          <p className="text-sm text-muted-foreground">Produto não encontrado.</p>
        )}
      </Modal>
    </div>
  )
}

/** Editor da grade de variantes de um produto: lista (sabor × tamanho) + form inline de adicionar/editar. */
function VariantsEditor({ product }: { product: Product }) {
  const qc = useQueryClient()
  const [form, setForm] = useState<VariantForm>(EMPTY_VARIANT)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  function invalidate() {
    qc.invalidateQueries({ queryKey: ['suplementos-products'] })
  }

  const saveMutation = useMutation({
    mutationFn: () => {
      const payload = {
        flavor: form.flavor.trim() || null,
        sizeLabel: form.sizeLabel.trim(),
        sku: form.sku.trim() || null,
        priceCents: Math.round(Number(form.price) * 100),
        stockQuantity: Math.max(0, Math.round(Number(form.stock || 0))),
        expiryDate: form.expiry.trim() || null,
      }
      if (editingId) return updateVariant(product.id, editingId, payload)
      return createVariant(product.id, payload)
    },
    onSuccess: () => {
      invalidate()
      setForm(EMPTY_VARIANT)
      setEditingId(null)
      setError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'duplicate_variant') {
        setError('Já existe uma variante com este sabor e tamanho.')
      } else {
        setError('Erro ao salvar a variante.')
      }
    },
  })

  const toggleMutation = useMutation({
    mutationFn: (v: Variant) => toggleVariant(product.id, v.id, !v.active),
    onSuccess: invalidate,
  })

  const deleteMutation = useMutation({
    mutationFn: (variantId: string) => deleteVariant(product.id, variantId),
    onSuccess: invalidate,
    onError: () => setError('Erro ao remover a variante.'),
  })

  function startEdit(v: Variant) {
    setEditingId(v.id)
    setForm({
      flavor: v.flavor ?? '',
      sizeLabel: v.sizeLabel,
      sku: v.sku ?? '',
      price: String(v.priceCents / 100),
      stock: String(v.stockQuantity),
      expiry: v.expiryDate ?? '',
    })
    setError(null)
  }

  function cancelEdit() {
    setEditingId(null)
    setForm(EMPTY_VARIANT)
    setError(null)
  }

  return (
    <div className="space-y-4">
      {product.variants.length === 0 ? (
        <p className="text-xs text-muted-foreground">Nenhuma variante cadastrada ainda.</p>
      ) : (
        <div className="divide-y divide-border rounded-lg border border-border">
          {product.variants.map((v) => {
            const outOfStock = v.stockQuantity <= 0
            return (
              <div key={v.id} className="flex items-center justify-between gap-3 px-3 py-2 text-sm">
                <div className="min-w-0">
                  <span className="font-medium">
                    {v.flavor ? `${v.flavor} / ${v.sizeLabel}` : v.sizeLabel}
                  </span>
                  {v.sku && <span className="ml-2 text-xs text-muted-foreground">SKU {v.sku}</span>}
                  {v.expiryDate && (
                    <span className="ml-2 text-xs text-muted-foreground">val. {v.expiryDate}</span>
                  )}
                  {!v.active && <Badge variant="muted" className="ml-2">indisponível</Badge>}
                  {outOfStock && <Badge variant="danger" className="ml-2">sem estoque</Badge>}
                </div>
                <div className="flex shrink-0 items-center gap-2">
                  <span className="tabular-nums text-xs text-muted-foreground">{formatBrl(v.priceCents)}</span>
                  <span className={`tabular-nums text-xs ${outOfStock ? 'font-semibold text-destructive' : 'text-muted-foreground'}`}>
                    {v.stockQuantity} un.
                  </span>
                  <label className="flex items-center gap-1 text-xs text-muted-foreground">
                    <input
                      type="checkbox"
                      checked={v.active}
                      disabled={toggleMutation.isPending}
                      onChange={() => toggleMutation.mutate(v)}
                    />
                    disp.
                  </label>
                  <Button variant="outline" className="h-6 px-2 text-xs" onClick={() => startEdit(v)}>
                    Editar
                  </Button>
                  <Button
                    variant="outline"
                    className="h-6 px-2 text-xs"
                    disabled={deleteMutation.isPending}
                    onClick={() => deleteMutation.mutate(v.id)}
                  >
                    Remover
                  </Button>
                </div>
              </div>
            )
          })}
        </div>
      )}

      <form
        className="flex flex-wrap items-end gap-2 rounded-lg border border-dashed border-border p-3"
        onSubmit={(e) => {
          e.preventDefault()
          saveMutation.mutate()
        }}
      >
        <div className="flex-1 min-w-[7rem]">
          <label className="mb-1 block text-xs font-medium text-muted-foreground">Sabor (opcional)</label>
          <input value={form.flavor} onChange={(e) => setForm((f) => ({ ...f, flavor: e.target.value }))}
            maxLength={60} placeholder="Chocolate, Baunilha…"
            className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm" />
        </div>
        <div className="w-28">
          <label className="mb-1 block text-xs font-medium text-muted-foreground">Tamanho/Peso</label>
          <input value={form.sizeLabel} onChange={(e) => setForm((f) => ({ ...f, sizeLabel: e.target.value }))} required
            maxLength={40} placeholder="900g, 60 caps…"
            className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm" />
        </div>
        <div className="w-28">
          <label className="mb-1 block text-xs font-medium text-muted-foreground">SKU (opcional)</label>
          <input value={form.sku} onChange={(e) => setForm((f) => ({ ...f, sku: e.target.value }))}
            maxLength={60} placeholder="ABC-123"
            className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm" />
        </div>
        <div className="w-24">
          <label className="mb-1 block text-xs font-medium text-muted-foreground">Preço R$</label>
          <input type="number" min="0" step="0.01" value={form.price} required
            onChange={(e) => setForm((f) => ({ ...f, price: e.target.value }))}
            className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm" />
        </div>
        <div className="w-20">
          <label className="mb-1 block text-xs font-medium text-muted-foreground">Estoque</label>
          <input type="number" min="0" step="1" value={form.stock}
            onChange={(e) => setForm((f) => ({ ...f, stock: e.target.value }))}
            className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm" />
        </div>
        <div className="w-36">
          <label className="mb-1 block text-xs font-medium text-muted-foreground">Validade (opcional)</label>
          <input type="date" value={form.expiry}
            onChange={(e) => setForm((f) => ({ ...f, expiry: e.target.value }))}
            className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm" />
        </div>
        <Button type="submit" className="h-8 px-3 text-xs" disabled={saveMutation.isPending}>
          {saveMutation.isPending ? 'Salvando…' : editingId ? 'Salvar' : 'Adicionar'}
        </Button>
        {editingId && (
          <Button type="button" variant="outline" className="h-8 px-3 text-xs" onClick={cancelEdit}>
            Cancelar edição
          </Button>
        )}
      </form>
      {error && <p className="text-sm text-destructive">{error}</p>}
    </div>
  )
}
