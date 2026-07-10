'use client'

import { useEffect } from 'react'

export function Modal({
  open,
  onClose,
  title,
  children,
  size = 'md',
}: {
  open: boolean
  onClose: () => void
  title: string
  children: React.ReactNode
  size?: 'sm' | 'md' | 'lg'
}) {
  useEffect(() => {
    if (!open) return
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open, onClose])

  if (!open) return null

  const widths = { sm: 'max-w-sm', md: 'max-w-md', lg: 'max-w-lg' }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
      <div
        className={`w-full rounded-xl border border-border bg-card text-card-foreground shadow-xl ${widths[size]} relative max-h-[90vh] overflow-y-auto p-6`}
      >
        <button
          onClick={onClose}
          className="absolute top-4 right-4 text-xl leading-none text-muted-foreground hover:text-foreground"
          aria-label="Fechar"
        >
          ×
        </button>
        <h2 className="mb-4 text-base font-semibold text-foreground">{title}</h2>
        {children}
      </div>
    </div>
  )
}
