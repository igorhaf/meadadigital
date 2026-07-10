'use client'

import { AlertDialog as AlertDialogPrimitive } from '@base-ui/react/alert-dialog'
import { useState, type ReactNode } from 'react'

import { Button } from '@/components/ui/button'

/**
 * AlertDialog de confirmação (camada 6) sobre @base-ui/react (NÃO Radix). Controlado via
 * open/onOpenChange. Toda ação destrutiva do admin passa por aqui — o aviso lembra que a
 * ação é registrada na auditoria.
 *
 * <p>confirmText opcional: quando passado, exige que o usuário DIGITE exatamente esse texto
 * (ex.: o nome da empresa) para liberar o botão de confirmar — proteção extra para deletes
 * irreversíveis. Sem confirmText, o botão fica habilitado direto.
 *
 * @param open           controlado
 * @param onOpenChange   fecha/abre
 * @param title          título do diálogo
 * @param description    texto explicativo (já inclua o aviso de irreversibilidade quando for o caso)
 * @param confirmLabel   rótulo do botão de confirmar (default "Confirmar")
 * @param confirmText    se presente, o usuário deve digitá-lo para habilitar o confirmar
 * @param destructive    estiliza o botão de confirmar como destrutivo (default true)
 * @param loading        desabilita os botões enquanto a ação roda
 * @param onConfirm      callback ao confirmar
 */
export function AlertDialog({
  open,
  onOpenChange,
  title,
  description,
  confirmLabel = 'Confirmar',
  confirmText,
  destructive = true,
  loading = false,
  onConfirm,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description: ReactNode
  confirmLabel?: string
  confirmText?: string
  destructive?: boolean
  loading?: boolean
  onConfirm: () => void
}) {
  const [typed, setTyped] = useState('')
  const confirmEnabled = !loading && (!confirmText || typed.trim() === confirmText)

  function handleOpenChange(next: boolean) {
    if (!next) {
      setTyped('')
    }
    onOpenChange(next)
  }

  return (
    <AlertDialogPrimitive.Root open={open} onOpenChange={handleOpenChange}>
      <AlertDialogPrimitive.Portal>
        <AlertDialogPrimitive.Backdrop className="fixed inset-0 z-50 bg-black/40" />
        <AlertDialogPrimitive.Popup className="fixed top-1/2 left-1/2 z-50 w-full max-w-md -translate-x-1/2 -translate-y-1/2 rounded-xl border border-border bg-card p-6 text-card-foreground shadow-xl outline-none">
          <AlertDialogPrimitive.Title className="text-base font-semibold">
            {title}
          </AlertDialogPrimitive.Title>
          <AlertDialogPrimitive.Description className="mt-2 text-sm text-muted-foreground">
            {description}
          </AlertDialogPrimitive.Description>

          <p className="mt-3 text-xs text-muted-foreground">Esta ação é registrada na auditoria.</p>

          {confirmText && (
            <div className="mt-4">
              <label className="mb-1 block text-sm font-medium">
                Digite <span className="font-mono text-foreground">{confirmText}</span> para
                confirmar
              </label>
              <input
                type="text"
                value={typed}
                onChange={(e) => setTyped(e.target.value)}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
                autoComplete="off"
              />
            </div>
          )}

          <div className="mt-6 flex justify-end gap-2">
            <Button variant="outline" onClick={() => handleOpenChange(false)} disabled={loading}>
              Cancelar
            </Button>
            <Button
              variant={destructive ? 'destructive' : 'default'}
              onClick={onConfirm}
              disabled={!confirmEnabled}
            >
              {loading ? 'Processando…' : confirmLabel}
            </Button>
          </div>
        </AlertDialogPrimitive.Popup>
      </AlertDialogPrimitive.Portal>
    </AlertDialogPrimitive.Root>
  )
}
