'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'

import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { createInvitation, type Invitation } from '@/lib/api/invitations'

const inviteSchema = z.object({
  email: z.string().email('Email inválido'),
})

type InviteForm = z.infer<typeof inviteSchema>

/**
 * Modal de novo convite (camada 5.16 #6). Três estados:
 *  - form: campo email + "Gerar link";
 *  - success: mostra a inviteUrl em <code> com botão "Copiar" + aviso de validade;
 *  - error: mensagem (email inválido vem do zod antes; erro do backend cai aqui).
 *
 * O sucesso NÃO fecha o modal automaticamente — o admin precisa copiar o link primeiro.
 * Fechar (ou "Novo convite") reseta para o estado de form. Invalida a lista no sucesso.
 */
export function CreateInvitationDialog({
  open,
  onClose,
}: {
  open: boolean
  onClose: () => void
}) {
  const queryClient = useQueryClient()
  const [created, setCreated] = useState<Invitation | null>(null)
  const [serverError, setServerError] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<InviteForm>({ resolver: zodResolver(inviteSchema) })

  useEffect(() => {
    if (open) {
      reset({ email: '' })
      setCreated(null)
      setServerError(null)
      setCopied(false)
    }
  }, [open, reset])

  const mutation = useMutation({
    mutationFn: (values: InviteForm) => createInvitation(values.email),
    onSuccess: (inv) => {
      setCreated(inv)
      queryClient.invalidateQueries({ queryKey: ['my-invitations'] })
    },
    onError: (err) => {
      console.error('createInvitation failed:', err)
      setServerError('Erro ao gerar o convite. Verifique o email e tente novamente.')
    },
  })

  function onSubmit(values: InviteForm) {
    setServerError(null)
    mutation.mutate(values)
  }

  async function copyLink() {
    if (!created) return
    try {
      await navigator.clipboard.writeText(created.inviteUrl)
      setCopied(true)
    } catch (err) {
      console.error('clipboard write failed:', err)
    }
  }

  return (
    <Modal open={open} onClose={onClose} title="Novo convite">
      {created ? (
        <div className="space-y-4">
          <p className="text-sm text-muted-foreground">
            Convite gerado para <span className="font-medium text-foreground">{created.email}</span>.
            Envie este link pra pessoa. Ele expira em 7 dias.
          </p>
          <div className="flex items-center gap-2">
            <code className="flex-1 overflow-x-auto whitespace-nowrap rounded-md border border-border bg-muted px-3 py-2 text-xs">
              {created.inviteUrl}
            </code>
            <Button type="button" variant="outline" onClick={copyLink}>
              {copied ? 'Copiado!' : 'Copiar'}
            </Button>
          </div>
          <div className="flex justify-end">
            <Button type="button" onClick={onClose}>
              Fechar
            </Button>
          </div>
        </div>
      ) : (
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div>
            <label htmlFor="invite-email" className="mb-1 block text-sm font-medium">
              Email da pessoa
            </label>
            <input
              id="invite-email"
              type="email"
              placeholder="colega@empresa.com"
              className="w-full rounded-md border border-border px-3 py-2 text-sm"
              {...register('email')}
            />
            {errors.email && (
              <p className="mt-1 text-sm text-destructive">{errors.email.message}</p>
            )}
          </div>

          {serverError && <p className="text-sm text-destructive">{serverError}</p>}

          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="outline" onClick={onClose}>
              Cancelar
            </Button>
            <Button type="submit" disabled={isSubmitting || mutation.isPending}>
              {mutation.isPending ? 'Gerando…' : 'Gerar link'}
            </Button>
          </div>
        </form>
      )}
    </Modal>
  )
}
