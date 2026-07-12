import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'

import { apiFetch } from '../api'
import { useAuth } from '../auth'
import NewPageDialog from '../components/NewPageDialog'
import PageView from '../components/PageView'
import TelegramDialog from '../components/TelegramDialog'
import TreeView from '../components/TreeView'
import type { PageKind, PageScope, Tree, TreeNode } from '../types'

type NewPageTarget = { scope: PageScope; parentId: number; parentTitle: string; parentKind: PageKind }

export default function Dashboard() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { pageId } = useParams()
  const activeId = pageId ? Number(pageId) : null
  const [newPage, setNewPage] = useState<NewPageTarget | null>(null)
  const [showTelegram, setShowTelegram] = useState(false)

  const { data: tree, isLoading } = useQuery({
    queryKey: ['tree'],
    queryFn: () => apiFetch<Tree>('/tree'),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => apiFetch(`/pages/${id}`, { method: 'DELETE' }),
    onSuccess: (_data, id) => {
      queryClient.invalidateQueries({ queryKey: ['tree'] })
      if (id === activeId) {
        navigate('/')
      }
    },
  })

  function handleDelete(node: TreeNode) {
    const suffix = node.children.length > 0 ? ' Todas as subpáginas também serão excluídas.' : ''
    if (window.confirm(`Excluir "${node.title}"?${suffix}`)) {
      deleteMutation.mutate(node.id)
    }
  }

  return (
    <div className="flex h-full">
      {/* ── Sidebar ─────────────────────────────────────────────────────── */}
      <aside className="flex w-64 shrink-0 flex-col border-r border-[#e9e9e7] bg-[#f7f7f5]">
        <div className="flex items-center gap-2 px-4 py-3">
          <span className="text-lg">🪁</span>
          <span className="text-sm font-semibold">Soar</span>
        </div>

        <nav className="flex-1 space-y-6 overflow-y-auto px-2 pb-4">
          <Section
            label="Compartilhado"
            hint="Visível para a família toda"
            nodes={tree?.shared ?? []}
            loading={isLoading}
            activeId={activeId}
            onCreateChild={(parent) =>
              setNewPage({ scope: 'shared', parentId: parent.id, parentTitle: parent.title, parentKind: parent.kind })
            }
            onDelete={handleDelete}
          />
          <Section
            label="Pessoal"
            hint="Só você vê estas páginas"
            nodes={tree?.personal ?? []}
            loading={isLoading}
            activeId={activeId}
            onCreateChild={(parent) =>
              setNewPage({ scope: 'personal', parentId: parent.id, parentTitle: parent.title, parentKind: parent.kind })
            }
            onDelete={handleDelete}
          />
        </nav>

        <div className="border-t border-[#e9e9e7] px-3 py-2">
          <button
            type="button"
            onClick={() => setShowTelegram(true)}
            className="w-full rounded-md px-2 py-1.5 text-left text-sm text-[#5f5e5b] hover:bg-[#efefed]"
          >
            🤖 Telegram
          </button>
        </div>

        <div className="flex items-center justify-between border-t border-[#e9e9e7] px-4 py-3">
          <div className="min-w-0">
            <p className="truncate text-sm font-medium">{user?.name}</p>
            <p className="truncate text-xs text-[#9b9a97]">{user?.email}</p>
          </div>
          <button
            type="button"
            onClick={() => logout().then(() => navigate('/login'))}
            className="rounded-md px-2 py-1 text-xs text-[#787774] hover:bg-[#efefed]"
          >
            Sair
          </button>
        </div>
      </aside>

      {/* ── Conteúdo ────────────────────────────────────────────────────── */}
      <main className="flex-1 overflow-y-auto">
        {activeId !== null ? (
          <PageView key={activeId} pageId={activeId} />
        ) : (
          <div className="flex h-full flex-col items-center justify-center px-6 text-center">
            <div className="text-5xl">🪁</div>
            <h2 className="mt-4 text-xl font-semibold">Bem-vindo ao Soar, {user?.name}!</h2>
            <p className="mt-2 max-w-md text-sm text-[#787774]">
              Cada página da barra lateral é uma mini-aplicação da família: agenda, tarefas,
              gastos, senhas, remédios, dietas… As compartilhadas são de todos; as pessoais, só
              suas. E o assistente no Telegram opera tudo por mensagem.
            </p>
          </div>
        )}
      </main>

      {newPage && (
        <NewPageDialog
          scope={newPage.scope}
          parentId={newPage.parentId}
          parentTitle={newPage.parentTitle}
          parentKind={newPage.parentKind}
          onClose={() => setNewPage(null)}
          onCreated={(page) => {
            setNewPage(null)
            queryClient.invalidateQueries({ queryKey: ['tree'] })
            navigate(`/p/${page.id}`)
          }}
        />
      )}
      {showTelegram && <TelegramDialog onClose={() => setShowTelegram(false)} />}
    </div>
  )
}

type SectionProps = {
  label: string
  hint: string
  nodes: TreeNode[]
  loading: boolean
  activeId: number | null
  onCreateChild: (parent: TreeNode) => void
  onDelete: (node: TreeNode) => void
}

function Section({
  label,
  hint,
  nodes,
  loading,
  activeId,
  onCreateChild,
  onDelete,
}: SectionProps) {
  return (
    <div>
      <div className="flex items-center justify-between px-2 pb-1">
        <span className="text-xs font-semibold tracking-wide text-[#9b9a97] uppercase" title={hint}>
          {label}
        </span>
      </div>
      {loading ? (
        <p className="px-3 py-1 text-xs text-[#9b9a97]">Carregando…</p>
      ) : (
        <TreeView
          nodes={nodes}
          activeId={activeId}
          onCreateChild={onCreateChild}
          onDelete={onDelete}
        />
      )}
    </div>
  )
}
