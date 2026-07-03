'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { ApiError } from '@/lib/api/client'
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
} from '@/lib/api/moda-infantil/products'
import { KIDS_SIZES, type KidsSizeId } from '@/profiles/moda-infantil/kids-size'
import {
  MODA_INFANTIL_CATEGORIES,
  type ModaInfantilCategoryId,
} from '@/profiles/moda-infantil/moda-infantil-categories'
import { formatBrl, type Product, type Variant } from '@/profiles/moda-infantil/moda-infantil-types'

type FormState = {
  name: string
  description: string
  price: string // reais (basePrice)
  category: ModaInfantilCategoryId
}

const EMPTY_FORM: FormState = { name: '', description: '', price: '', category: 'bebe' }

// size, color, sku, price (R$ ou vazio = herda base), stock
type VariantForm = { size: KidsSizeId; color: string; sku: string; price: string; stock: string }
const EMPTY_VARIANT: VariantForm = { size: '1a', color: '', sku: '', price: '', stock: '0' }

/**
 * Catálogo do ModaInfantilBot (roupa de criança / varejo). Produtos agrupados por categoria, toggle
 * de disponibilidade inline, criação/edição via Modal, busca por nome. Cada produto tem um modal
 * secundário "Variantes" — a ESCAPADA desta camada: grade tamanho (FAIXA ETÁRIA) × cor com SKU,
 * preço próprio (vazio = herda o preço base) e ESTOQUE (stockQty) editável. Variante sem estoque é
 * destacada. Preços salvos em centavos.
 */
export default function ModaInfantilCatalogPage() {
  const qc = useQueryClient()
  const [search, setSearch] = useState('')
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<Product | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY_FORM)
  const [formError, setFormError] = useState<string | null>(null)

  // Modal secundário de variantes: aberto pelo botão "Variantes" do card.
  const [variantsProduct, setVariantsProduct] = useState<Product | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['moda-infantil-products'],
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
      qc.invalidateQueries({ queryKey: ['moda-infantil-products'] })
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
    onSuccess: () => qc.invalidateQueries({ queryKey: ['moda-infantil-products'] }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteProduct(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['moda-infantil-products'] }),
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
          {MODA_INFANTIL_CATEGORIES.map((cat) => {
            const catProducts = products.filter((p) => p.category === cat.id)
            return (
              <section key={cat.id} className="space-y-2">
                <h2 className="text-sm font-semibold text-muted-foreground">{cat.label}</h2>
                {catProducts.length === 0 ? (
                  <p className="text-xs text-muted-foreground">
                    Nenhum produto nesta categoria ainda.
                  </p>
                ) : (
                  <div className="divide-y divide-border rounded-lg border border-border">
                    {catProducts.map((p) => {
                      const inStock = p.variants.reduce((sum, v) => sum + v.stockQty, 0)
                      return (
                        <div
                          key={p.id}
                          className="flex items-center justify-between gap-3 px-4 py-3"
                        >
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
                              <p className="truncate text-xs text-muted-foreground">
                                {p.description}
                              </p>
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
                            <Button
                              variant="outline"
                              className="h-7 px-2 text-xs"
                              onClick={() => setVariantsProduct(p)}
                            >
                              Variantes
                            </Button>
                            <Button
                              variant="outline"
                              className="h-7 px-2 text-xs"
                              onClick={() => openEdit(p)}
                            >
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
      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title={editing ? 'Editar produto' : 'Novo produto'}
        size="md"
      >
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault()
            saveMutation.mutate()
          }}
        >
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Nome</label>
            <input
              value={form.name}
              onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
              required
              maxLength={120}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">
              Descrição (opcional)
            </label>
            <textarea
              value={form.description}
              onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
              rows={2}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Preço base (R$)
              </label>
              <input
                type="number"
                min="0"
                step="0.01"
                value={form.price}
                required
                onChange={(e) => setForm((f) => ({ ...f, price: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Categoria
              </label>
              <select
                value={form.category}
                onChange={(e) =>
                  setForm((f) => ({ ...f, category: e.target.value as ModaInfantilCategoryId }))
                }
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              >
                {MODA_INFANTIL_CATEGORIES.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.label}
                  </option>
                ))}
              </select>
            </div>
          </div>
          {formError && <p className="text-sm text-destructive">{formError}</p>}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={saveMutation.isPending}>
              {saveMutation.isPending ? 'Salvando…' : editing ? 'Salvar' : 'Criar'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Modal secundário: grade de variantes (tamanho × cor) com estoque */}
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

/** Editor da grade de variantes de um produto: lista (tamanho × cor) + form inline de adicionar/editar. */
function VariantsEditor({ product }: { product: Product }) {
  const qc = useQueryClient()
  const [form, setForm] = useState<VariantForm>(EMPTY_VARIANT)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  function invalidate() {
    qc.invalidateQueries({ queryKey: ['moda-infantil-products'] })
  }

  const saveMutation = useMutation({
    mutationFn: () => {
      const trimmedPrice = form.price.trim()
      const payload = {
        size: form.size,
        color: form.color,
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
        setError('Já existe uma variante com este tamanho e cor.')
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
      size: (v.size as KidsSizeId) ?? '1a',
      color: v.color,
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
      {product.variants.length === 0 ? (
        <p className="text-xs text-muted-foreground">Nenhuma variante cadastrada ainda.</p>
      ) : (
        <div className="divide-y divide-border rounded-lg border border-border">
          {product.variants.map((v) => {
            const outOfStock = v.stockQty <= 0
            return (
              <div key={v.id} className="flex items-center justify-between gap-3 px-3 py-2 text-sm">
                <div className="min-w-0">
                  <span className="font-medium">
                    {v.size} / {v.color}
                  </span>
                  {v.sku && <span className="ml-2 text-xs text-muted-foreground">SKU {v.sku}</span>}
                  {!v.available && (
                    <Badge variant="muted" className="ml-2">
                      indisponível
                    </Badge>
                  )}
                  {outOfStock && (
                    <Badge variant="danger" className="ml-2">
                      sem estoque
                    </Badge>
                  )}
                </div>
                <div className="flex shrink-0 items-center gap-2">
                  <span className="text-xs text-muted-foreground tabular-nums">
                    {v.priceCents == null ? 'herda base' : formatBrl(v.priceCents)}
                  </span>
                  <span
                    className={`text-xs tabular-nums ${outOfStock ? 'font-semibold text-destructive' : 'text-muted-foreground'}`}
                  >
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
                  <Button
                    variant="outline"
                    className="h-6 px-2 text-xs"
                    onClick={() => startEdit(v)}
                  >
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
        <div className="w-32">
          <label className="mb-1 block text-xs font-medium text-muted-foreground">Tamanho</label>
          <select
            value={form.size}
            onChange={(e) => setForm((f) => ({ ...f, size: e.target.value as KidsSizeId }))}
            className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm"
          >
            {KIDS_SIZES.map((s) => (
              <option key={s.id} value={s.id}>
                {s.label}
              </option>
            ))}
          </select>
        </div>
        <div className="min-w-[7rem] flex-1">
          <label className="mb-1 block text-xs font-medium text-muted-foreground">Cor</label>
          <input
            value={form.color}
            onChange={(e) => setForm((f) => ({ ...f, color: e.target.value }))}
            required
            maxLength={40}
            placeholder="Azul, Rosa…"
            className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm"
          />
        </div>
        <div className="w-28">
          <label className="mb-1 block text-xs font-medium text-muted-foreground">
            SKU (opcional)
          </label>
          <input
            value={form.sku}
            onChange={(e) => setForm((f) => ({ ...f, sku: e.target.value }))}
            maxLength={60}
            placeholder="ABC-123"
            className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm"
          />
        </div>
        <div className="w-28">
          <label className="mb-1 block text-xs font-medium text-muted-foreground">
            Preço R$ (vazio = base)
          </label>
          <input
            type="number"
            min="0"
            step="0.01"
            value={form.price}
            onChange={(e) => setForm((f) => ({ ...f, price: e.target.value }))}
            placeholder="herda base"
            className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm"
          />
        </div>
        <div className="w-20">
          <label className="mb-1 block text-xs font-medium text-muted-foreground">Estoque</label>
          <input
            type="number"
            min="0"
            step="1"
            value={form.stock}
            onChange={(e) => setForm((f) => ({ ...f, stock: e.target.value }))}
            className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm"
          />
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
