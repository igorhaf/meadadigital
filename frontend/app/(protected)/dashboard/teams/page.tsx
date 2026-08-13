'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Pencil, Users } from 'lucide-react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Button } from '@/components/ui/button'
import { DataTable, type Column } from '@/components/ui/data-table'
import { EmptyState } from '@/components/ui/empty-state'
import { Modal } from '@/components/ui/modal'
import { getMe } from '@/lib/api/me'
import { createTeam, deleteTeam, getMyTeams, updateTeam, type Team } from '@/lib/api/teams'
import { useResetWhen } from '@/lib/use-synced-form'

const columns: Column<Team>[] = [
  { key: 'name', header: 'Nome' },
  {
    key: 'createdAt',
    header: 'Criado em',
    render: (t) => new Date(t.createdAt).toLocaleDateString('pt-BR'),
  },
]

/**
 * Times/departamentos da empresa do tenant (camada 5.20 #76), via backend REST (/admin/teams).
 * CRUD: criar/renomear via modal dual, remover via confirm(). Super-admin não usa: redireciona
 * para /dashboard. Distinta de /dashboard/team (singular = convites de admin extra).
 */
export default function TeamsPage() {
  const router = useRouter()
  const queryClient = useQueryClient()
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editingTeam, setEditingTeam] = useState<Team | undefined>(undefined)

  const { data: me } = useQuery({ queryKey: ['me'], queryFn: getMe })
  const isTenant = me?.role === 'tenant_admin'

  useEffect(() => {
    if (me && me.role !== 'tenant_admin') {
      router.replace('/dashboard')
    }
  }, [me, router])

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['my-teams'],
    queryFn: getMyTeams,
    enabled: isTenant,
  })

  const remove = useMutation({
    mutationFn: (id: string) => deleteTeam(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['my-teams'] }),
    onError: (err) => console.error('deleteTeam failed:', err),
  })

  const isEmpty = !isPending && !isError && (data?.length ?? 0) === 0

  if (me && !isTenant) {
    return <div className="text-sm text-muted-foreground">Redirecionando…</div>
  }

  if (isError) {
    console.error('failed to load teams:', error)
    return (
      <div className="space-y-6">
        <PageHeader title="Times" />
        <p className="text-sm text-destructive">Erro ao carregar times.</p>
        <Link href="/dashboard">
          <Button variant="outline">Voltar ao dashboard</Button>
        </Link>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Times"
        description="Times e departamentos (ex.: Suporte, Vendas) para organizar quem cuida de cada conversa."
        actions={
          <Button
            onClick={() => {
              setEditingTeam(undefined)
              setDialogOpen(true)
            }}
          >
            Novo time
          </Button>
        }
      />

      {isEmpty ? (
        <EmptyState
          icon={<Users />}
          title="Sem times ainda"
          description="Crie times/departamentos (ex.: Suporte, Vendas) para organizar quem cuida de cada conversa."
          action={
            <Button
              onClick={() => {
                setEditingTeam(undefined)
                setDialogOpen(true)
              }}
            >
              Criar primeiro time
            </Button>
          }
        />
      ) : (
        <DataTable<Team>
          data={data ?? []}
          columns={columns}
          loading={isPending}
          emptyMessage="Nenhum time cadastrado."
          searchPlaceholder="Buscar time…"
          searchFn={(t, q) => t.name.toLowerCase().includes(q)}
          actions={(t) => (
            <div className="flex items-center gap-1.5">
              <Button
                variant="outline"
                className="h-7 px-2 text-xs"
                onClick={() => {
                  setEditingTeam(t)
                  setDialogOpen(true)
                }}
              >
                <Pencil className="size-3" />
                Editar
              </Button>
              <Button
                variant="outline"
                className="h-7 px-2 text-xs"
                disabled={remove.isPending && remove.variables === t.id}
                onClick={() => {
                  if (
                    confirm(`Remover o time "${t.name}"? As conversas atribuídas ficam sem time.`)
                  ) {
                    remove.mutate(t.id)
                  }
                }}
              >
                Remover
              </Button>
            </div>
          )}
        />
      )}

      <TeamDialog
        open={dialogOpen}
        onClose={() => {
          setDialogOpen(false)
          setEditingTeam(undefined)
        }}
        team={editingTeam}
      />
    </div>
  )
}

/**
 * Modal dual de time: cria (team ausente) ou renomeia (team presente). Campo único de nome
 * (1..60 chars — espelha o CHECK do banco). Invalida ['my-teams'] no sucesso. Mantido inline
 * (sem componente separado) — é simples, só um input.
 */
function TeamDialog({ open, onClose, team }: { open: boolean; onClose: () => void; team?: Team }) {
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  const [serverError, setServerError] = useState<string | null>(null)
  const isEdit = team != null

  useResetWhen(open ? (team?.id ?? 'create') : null, () => {
    setName(team?.name ?? '')
    setServerError(null)
  })

  const mutation = useMutation({
    mutationFn: (value: string) => (isEdit ? updateTeam(team!.id, value) : createTeam(value)),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['my-teams'] })
      onClose()
    },
    onError: (err) => {
      console.error(isEdit ? 'updateTeam failed:' : 'createTeam failed:', err)
      setServerError(
        isEdit
          ? 'Erro ao salvar alterações. Tente novamente.'
          : 'Erro ao criar time. Tente novamente.',
      )
    },
  })

  function onSubmit(e: React.FormEvent) {
    e.preventDefault()
    const trimmed = name.trim()
    if (!trimmed) {
      setServerError('Informe o nome do time.')
      return
    }
    setServerError(null)
    mutation.mutate(trimmed)
  }

  return (
    <Modal open={open} onClose={onClose} title={isEdit ? 'Editar time' : 'Novo time'}>
      <form onSubmit={onSubmit} className="space-y-4">
        <div>
          <label htmlFor="team-name" className="mb-1 block text-sm font-medium">
            Nome
          </label>
          <input
            id="team-name"
            type="text"
            maxLength={60}
            placeholder="Suporte"
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="w-full rounded-md border border-border px-3 py-2 text-sm"
          />
        </div>

        {serverError && <p className="text-sm text-destructive">{serverError}</p>}

        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button type="submit" disabled={mutation.isPending}>
            {mutation.isPending
              ? isEdit
                ? 'Salvando…'
                : 'Criando…'
              : isEdit
                ? 'Salvar alterações'
                : 'Criar'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
