import { useState } from 'react'
import { useNavigate } from 'react-router-dom'

import type { TreeNode } from '../types'
import { KIND_INFO } from '../types'

type TreeViewProps = {
  nodes: TreeNode[]
  activeId: number | null
  onCreateChild: (parent: TreeNode) => void
  onDelete: (node: TreeNode) => void
}

export default function TreeView({ nodes, activeId, onCreateChild, onDelete }: TreeViewProps) {
  if (nodes.length === 0) {
    return <p className="px-3 py-1 text-xs text-[#9b9a97]">Nenhuma página ainda.</p>
  }

  return (
    <div>
      {nodes.map((node) => (
        <TreeItem
          key={node.id}
          node={node}
          depth={0}
          activeId={activeId}
          onCreateChild={onCreateChild}
          onDelete={onDelete}
        />
      ))}
    </div>
  )
}

function subtreeHasId(node: TreeNode, id: number | null): boolean {
  if (id === null) return false
  if (node.id === id) return true
  return node.children.some((child) => subtreeHasId(child, id))
}

type TreeItemProps = {
  node: TreeNode
  depth: number
  activeId: number | null
  onCreateChild: (parent: TreeNode) => void
  onDelete: (node: TreeNode) => void
}

function TreeItem({ node, depth, activeId, onCreateChild, onDelete }: TreeItemProps) {
  const navigate = useNavigate()
  const [open, setOpen] = useState(() => depth === 0 || subtreeHasId(node, activeId))
  const isActive = node.id === activeId
  const hasChildren = node.children.length > 0

  return (
    <div>
      <div
        className={`group flex cursor-pointer items-center gap-1 rounded-md py-1 pr-1 text-sm transition-colors ${
          isActive
            ? 'bg-[#efefed] font-medium text-[#37352f]'
            : 'text-[#5f5e5b] hover:bg-[#efefed]'
        }`}
        style={{ paddingLeft: `${depth * 14 + 4}px` }}
        onClick={() => navigate(`/p/${node.id}`)}
      >
        <button
          type="button"
          aria-label={open ? 'Recolher' : 'Expandir'}
          onClick={(e) => {
            e.stopPropagation()
            setOpen((v) => !v)
          }}
          className={`flex h-5 w-5 shrink-0 items-center justify-center rounded text-[#9b9a97] hover:bg-[#e3e2e0] ${
            hasChildren ? '' : 'invisible'
          }`}
        >
          <svg
            viewBox="0 0 12 12"
            className={`h-3 w-3 transition-transform ${open ? 'rotate-90' : ''}`}
            fill="currentColor"
          >
            <path d="M4 2l4 4-4 4V2z" />
          </svg>
        </button>

        <span className="w-5 shrink-0 text-center">{node.icon ?? KIND_INFO[node.kind].icon}</span>
        <span className="min-w-0 flex-1 truncate">{node.title}</span>

        <span className="hidden shrink-0 items-center gap-0.5 group-hover:flex">
          {node.kind !== 'registro_item' && (
            <button
              type="button"
              title={node.kind === 'registro' ? 'Novo item' : 'Nova subpágina'}
              aria-label="Nova subpágina"
              onClick={(e) => {
                e.stopPropagation()
                setOpen(true)
                onCreateChild(node)
              }}
              className="flex h-5 w-5 items-center justify-center rounded text-[#9b9a97] hover:bg-[#e3e2e0] hover:text-[#37352f]"
            >
              +
            </button>
          )}
          {!node.is_system && (
            <button
              type="button"
              title="Excluir página"
              aria-label="Excluir página"
              onClick={(e) => {
                e.stopPropagation()
                onDelete(node)
              }}
              className="flex h-5 w-5 items-center justify-center rounded text-[#9b9a97] hover:bg-[#e3e2e0] hover:text-red-600"
            >
              <svg viewBox="0 0 16 16" className="h-3 w-3" fill="currentColor">
                <path d="M6 2h4v1h4v1.5H2V3h4V2zm-2.5 3.5h9L12 14H4L3.5 5.5zM6.5 7v5h1V7h-1zm2 0v5h1V7h-1z" />
              </svg>
            </button>
          )}
        </span>
      </div>

      {open && hasChildren && (
        <div>
          {node.children.map((child) => (
            <TreeItem
              key={child.id}
              node={child}
              depth={depth + 1}
              activeId={activeId}
              onCreateChild={onCreateChild}
              onDelete={onDelete}
            />
          ))}
        </div>
      )}
    </div>
  )
}
