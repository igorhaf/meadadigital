'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  deleteWishlist,
  listWishlists,
  setWishlistActive,
} from '@/lib/api/concessionaria/wishlists'
import {
  formatBrl,
  formatDate,
  type ConcessionariaWishlist,
} from '@/profiles/concessionaria/concessionaria-types'

/**
 * Lista de desejos do ConcessionariaBot (onda 1, backlog #1). O desejo nasce na CONVERSA (a IA
 * registra via <desejo_carro> quando a vitrine não tem o carro); aqui a equipe acompanha, reativa
 * ou exclui. Quando um veículo disponível casa com o desejo, o cliente é avisado automaticamente e
 * o desejo desativa (one-shot).
 */
export default function ConcessionariaWishlistsPage() {
  const qc = useQueryClient()
  const [onlyActive, setOnlyActive] = useState(true)

  const { data, isPending, isError } = useQuery({
    queryKey: ['concessionaria-wishlists', onlyActive],
    queryFn: () => listWishlists({ onlyActive }),
  })

  const toggleMutation = useMutation({
    mutationFn: (w: ConcessionariaWishlist) => setWishlistActive(w.id, !w.active),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['concessionaria-wishlists'] }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteWishlist(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['concessionaria-wishlists'] }),
  })

  const items = data?.items ?? []

  function criteria(w: ConcessionariaWishlist): string {
    const parts: string[] = []
    if (w.brand) parts.push(w.brand)
    if (w.model) parts.push(w.model)
    if (w.minYear) parts.push(`${w.minYear}+`)
    if (w.maxPriceCents) parts.push(`até ${formatBrl(w.maxPriceCents)}`)
    return parts.join(' · ')
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Lista de desejos"
        description="Carros que os clientes procuram e não estão na vitrine — o aviso dispara sozinho quando um veículo compatível entra no estoque."
      />

      <div className="flex flex-wrap items-center gap-2">
        <button
          onClick={() => setOnlyActive(true)}
          className={`rounded-full border px-3 py-1 text-xs ${onlyActive ? 'border-primary bg-primary/10' : 'border-border'}`}
        >
          Ativos
        </button>
        <button
          onClick={() => setOnlyActive(false)}
          className={`rounded-full border px-3 py-1 text-xs ${!onlyActive ? 'border-primary bg-primary/10' : 'border-border'}`}
        >
          Todos
        </button>
      </div>

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar a lista de desejos.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">
          Nenhum desejo registrado ainda — a IA registra quando o cliente não encontra o carro na
          vitrine.
        </p>
      ) : (
        <div className="divide-y divide-border rounded-lg border border-border">
          {items.map((w) => (
            <div key={w.id} className="flex items-center justify-between gap-3 px-4 py-3">
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <span className="truncate font-medium">{w.contactName ?? 'Contato'}</span>
                  {w.active ? (
                    <Badge variant="info">aguardando</Badge>
                  ) : w.notifiedAt ? (
                    <Badge variant="success">avisado {formatDate(w.notifiedAt)}</Badge>
                  ) : (
                    <Badge variant="muted">inativo</Badge>
                  )}
                </div>
                <p className="truncate text-xs text-muted-foreground">
                  {criteria(w)}
                  {w.notes ? ` — ${w.notes}` : ''}
                </p>
              </div>
              <div className="flex shrink-0 items-center gap-2">
                <span className="text-xs text-muted-foreground">{formatDate(w.createdAt)}</span>
                <Button
                  variant="outline"
                  className="h-7 px-2 text-xs"
                  disabled={toggleMutation.isPending}
                  onClick={() => toggleMutation.mutate(w)}
                >
                  {w.active ? 'Desativar' : 'Reativar'}
                </Button>
                <Button
                  variant="outline"
                  className="h-7 px-2 text-xs"
                  disabled={deleteMutation.isPending}
                  onClick={() => deleteMutation.mutate(w.id)}
                >
                  Excluir
                </Button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
