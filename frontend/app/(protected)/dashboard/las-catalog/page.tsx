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
} from '@/lib/api/las/products'
import { LAS_CATEGORIES, type LasCategoryId } from '@/profiles/las/las-categories'
import { formatBrl, type Product, type Variant } from '@/profiles/las/las-types'

type FormState = {
  name: string
  description: string
  price: string // reais (basePrice)
  category: LasCategoryId
}

const EMPTY_FORM: FormState = { name: '', description: '', price: '', category: 'las' }

// color, dyeLot (lote de tingimento), sku, price (R$ ou vazio = herda base), stock
type VariantForm = { color: string; dyeLot: string; sku: string; price: string; stock: string }
const EMPTY_VARIANT: VariantForm = { color: '', dyeLot: '', sku: '', price: '', stock: '0' }

/**
 * Catálogo do LasBot (loja de lãs / novelos / tricô-crochê — varejo). Produtos agrupados por
 * categoria, toggle de disponibilidade inline, criação/edição via Modal, busca por nome. Cada
 * produto tem um modal secundário "Variantes" — a ESCAPADA desta camada: grade COR × LOTE DE
 * TINGIMENTO (dye lot) com SKU, preço próprio (vazio = herda o preço base) e ESTOQUE (stockQty)
 * editável. Variante sem estoque é destacada. Preços salvos em centavos.
 */
export default function LasCatalogPage() {
  const qc = useQueryClient()
  const [search, setSearch] = useState('')
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<Product | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY_FORM)
  const [formError, setFormError] = useState<string | null>(null)

  // Modal secundário de variantes: aberto pelo botão "Variantes" do card.
  const [variantsProduct, setVariantsProduct] = useState<Product | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['las-products'],
    queryFn: () => listProducts(),
  })

  const saveMutation = useMutation({
    mutationFn: async () => {
      const payload = {
        name: form.name,
        description: form.description || null,
        basePriceCents: Math.round(Number(form.price) * 100),
        category: form.category,
      }
      if (editing) return updateProduct(editing.id, payload)
      return createProduct(payload)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['las-products'] })
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
    mutationFn: (p: Product) => toggleProduct(p.id, !p.available),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['las-products'] }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteProduct(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['las-products'] }),
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
      description: p.description ?? '',
      price: String(p.basePriceCents / 100),
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
          {LAS_CATEGORIES.map((cat) => {
            const catProducts = products.filter((p) => p.category === cat.id)
            return (
              <section key={cat.id} className="space-y-2">
                <h2 className="text-sm font-semibold text-muted-foreground">{cat.label}</h2>
                {catProducts.length === 0 ? (
                  <p className="text-xs text-muted-foreground">Nenhum produto nesta categoria ainda.</p>
                ) : (
                  <div className="divide-y divide-border rounded-lg border border-border">
                    {catProducts.map((p) => {
                      const inStock = p.variants.reduce((sum, v) => sum + v.stockQty, 0)
                      return (
                        <div key={p.id} className="flex items-center justify-between gap-3 px-4 py-3">
                          <div className="min-w-0">
                            <div className="flex items-center gap-2">
                              <span className="font-medium">{p.name}</span>
                              {!p.available && <Badge variant="muted">indisponível</Badge>}
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
                            <span className="tabular-nums">{formatBrl(p.basePriceCents)}</span>
                            <label className="flex items-center gap-1 text-xs text-muted-foreground">
                              <input
                                type="checkbox"
                                checked={p.available}
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
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Descrição (opcional)</label>
            <textarea value={form.description} onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
              rows={2} className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Preço base (R$)</label>
              <input type="number" min="0" step="0.01" value={form.price} required
                onChange={(e) => setForm((f) => ({ ...f, price: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Categoria</label>
              <select value={form.category}
                onChange={(e) => setForm((f) => ({ ...f, category: e.target.value as LasCategoryId }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm">
                {LAS_CATEGORIES.map((c) => (
                  <option key={c.id} value={c.id}>{c.label}</option>
                ))}
              </select>
            </div>
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

      {/* Modal secundário: grade de variantes (cor × lote de tingimento) com estoque */}
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

/** Editor da grade de variantes de um produto: lista (cor × lote) + form inline de adicionar/editar. */
function VariantsEditor({ product }: { product: Product }) {
  const qc = useQueryClient()
  const [form, setForm] = useState<VariantForm>(EMPTY_VARIANT)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  function invalidate() {
    qc.invalidateQueries({ queryKey: ['las-products'] })
  }

  const saveMutation = useMutation({
    mutationFn: () => {
      const trimmedPrice = form.price.trim()
      const payload = {
        color: form.color,
        dyeLot: form.dyeLot,
        sku: form.sku.trim() || null,
        priceCents: trimmedPrice === '' ? null : Math.round(Number(trimmedPrice) * 100),
        stockQty: Math.max(0, Math.round(Number(form.stock || 0))),
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
        setError('Já existe uma variante com esta cor e lote de tingimento.')
      } else {
        setError('Erro ao salvar a variante.')
      }
    },
  })

  const toggleMutation = useMutation({
    mutationFn: (v: Variant) => toggleVariant(product.id, v.id, !v.available),
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
      color: v.color,
      dyeLot: v.dyeLot,
      sku: v.sku ?? '',
      price: v.priceCents == null ? '' : String(v.priceCents / 100),
      stock: String(v.stockQty),
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
      <p className="text-xs text-muted-foreground">
        O <strong>lote de tingimento</strong> (dye lot) identifica o lote em que os novelos foram
        tingidos: novelos do <strong>mesmo lote têm o mesmo tom</strong>. Lotes diferentes da mesma
        cor podem ter pequenas variações.
      </p>

      {product.variants.length === 0 ? (
        <p className="text-xs text-muted-foreground">Nenhuma variante cadastrada ainda.</p>
      ) : (
        <div className="divide-y divide-border rounded-lg border border-border">
          {product.variants.map((v) => {
            const outOfStock = v.stockQty <= 0
            return (
              <div key={v.id} className="flex items-center justify-between gap-3 px-3 py-2 text-sm">
                <div className="min-w-0">
                  <span className="font-medium">{v.color} / lote {v.dyeLot}</span>
                  {v.sku && <span className="ml-2 text-xs text-muted-foreground">SKU {v.sku}</span>}
                  {!v.available && <Badge variant="muted" className="ml-2">indisponível</Badge>}
                  {outOfStock && <Badge variant="danger" className="ml-2">sem estoque</Badge>}
                </div>
                <div className="flex shrink-0 items-center gap-2">
                  <span className="tabular-nums text-xs text-muted-foreground">
                    {v.priceCents == null ? 'herda base' : formatBrl(v.priceCents)}
                  </span>
                  <span className={`tabular-nums text-xs ${outOfStock ? 'font-semibold text-destructive' : 'text-muted-foreground'}`}>
                    {v.stockQty} un.
                  </span>
                  <label className="flex items-center gap-1 text-xs text-muted-foreground">
                    <input
                      type="checkbox"
                      checked={v.available}
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
          <label className="mb-1 block text-xs font-medium text-muted-foreground">Cor</label>
          <input value={form.color} onChange={(e) => setForm((f) => ({ ...f, color: e.target.value }))} required
            maxLength={40} placeholder="Azul, Vermelho…"
            className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm" />
        </div>
        <div className="w-32">
          <label className="mb-1 block text-xs font-medium text-muted-foreground">Lote de tingimento</label>
          <input value={form.dyeLot} onChange={(e) => setForm((f) => ({ ...f, dyeLot: e.target.value }))} required
            maxLength={40} placeholder="L2024-A"
            className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm" />
        </div>
        <div className="w-28">
          <label className="mb-1 block text-xs font-medium text-muted-foreground">SKU (opcional)</label>
          <input value={form.sku} onChange={(e) => setForm((f) => ({ ...f, sku: e.target.value }))}
            maxLength={60} placeholder="ABC-123"
            className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm" />
        </div>
        <div className="w-28">
          <label className="mb-1 block text-xs font-medium text-muted-foreground">Preço R$ (vazio = base)</label>
          <input type="number" min="0" step="0.01" value={form.price}
            onChange={(e) => setForm((f) => ({ ...f, price: e.target.value }))}
            placeholder="herda base"
            className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm" />
        </div>
        <div className="w-20">
          <label className="mb-1 block text-xs font-medium text-muted-foreground">Estoque</label>
          <input type="number" min="0" step="1" value={form.stock}
            onChange={(e) => setForm((f) => ({ ...f, stock: e.target.value }))}
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
