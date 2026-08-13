'use client'

import { useCallback, useState } from 'react'

/**
 * Drag-and-drop nativo (HTML5) compartilhado pelos Kanbans de pedido. Sem lib externa — usa os
 * eventos draggable/onDragStart/onDragOver/onDrop do DOM (mesmo padrão do page-builder do CMS).
 *
 * Cada Kanban fornece:
 *  - canDrop(orderId, targetStatus): se soltar este card NA coluna alvo é uma transição VÁLIDA.
 *    (cada nicho conhece sua própria máquina de status / gates — ex.: papelaria não deixa
 *    arte_aprovacao→em_producao sem arte aprovada.)
 *  - onDrop(orderId, targetStatus): executa a transição (normalmente a mesma mutation do botão
 *    "Avançar").
 *
 * Devolve:
 *  - draggingId: id do card em arraste (pra estilizar/opacar o card de origem).
 *  - overColumn: status da coluna sob o cursor (pra realçar a coluna alvo).
 *  - cardProps(orderId): props a espalhar no card arrastável (draggable + handlers).
 *  - columnProps(targetStatus, statusOf): props a espalhar no container da coluna (onDragOver/Drop).
 *    statusOf(id) resolve o status atual do card arrastado (pra validar canDrop no momento do drop).
 */
export function useKanbanDnd(opts: {
  canDrop: (orderId: string, targetStatus: string) => boolean
  onDrop: (orderId: string, targetStatus: string) => void
}) {
  const { canDrop, onDrop } = opts
  const [draggingId, setDraggingId] = useState<string | null>(null)
  const [overColumn, setOverColumn] = useState<string | null>(null)

  const cardProps = useCallback(
    (orderId: string) => ({
      draggable: true,
      onDragStart: (e: React.DragEvent) => {
        e.dataTransfer.setData('text/plain', orderId)
        e.dataTransfer.effectAllowed = 'move'
        setDraggingId(orderId)
      },
      onDragEnd: () => {
        setDraggingId(null)
        setOverColumn(null)
      },
      'data-dragging': draggingId === orderId ? 'true' : undefined,
    }),
    [draggingId],
  )

  const columnProps = useCallback(
    (targetStatus: string) => ({
      onDragOver: (e: React.DragEvent) => {
        // só aceita o drop (preventDefault) se a transição for válida pro card em arraste.
        if (draggingId && canDrop(draggingId, targetStatus)) {
          e.preventDefault()
          e.dataTransfer.dropEffect = 'move'
          if (overColumn !== targetStatus) setOverColumn(targetStatus)
        }
      },
      onDragLeave: () => {
        if (overColumn === targetStatus) setOverColumn(null)
      },
      onDrop: (e: React.DragEvent) => {
        e.preventDefault()
        const id = e.dataTransfer.getData('text/plain') || draggingId
        setDraggingId(null)
        setOverColumn(null)
        if (id && canDrop(id, targetStatus)) onDrop(id, targetStatus)
      },
      'data-over': overColumn === targetStatus ? 'true' : undefined,
    }),
    [draggingId, overColumn, canDrop, onDrop],
  )

  return { draggingId, overColumn, cardProps, columnProps }
}
