'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'

import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { createService, updateService, type Service } from '@/lib/supabase/services'

// name obrigatório; description opcional; preço em REAIS (campo amigável), convertido para
// price_cents na submissão. Preço vazio → null (serviço sem preço). Preço presente deve
// ser número >= 0.
const serviceSchema = z.object({
  name: z.string().min(1, 'Informe o nome do serviço'),
  description: z.string().optional(),
  priceReais: z
    .string()
    .optional()
    .refine(
      (v) => !v || (!Number.isNaN(Number(v.replace(',', '.'))) && Number(v.replace(',', '.')) >= 0),
      'Preço inválido (use número, ex.: 99.90)',
    ),
})

type ServiceForm = z.infer<typeof serviceSchema>

/** Converte "99,90" / "99.90" → 9990 centavos; vazio → null. */
function reaisToCents(v: string | undefined): number | null {
  if (!v || v.trim() === '') return null
  return Math.round(Number(v.replace(',', '.')) * 100)
}

/** Converte 9990 centavos → "99.90" para pré-popular o form na edição; null → "". */
function centsToReais(cents: number | null): string {
  if (cents == null) return ''
  return (cents / 100).toFixed(2)
}

/**
 * Modal dual de serviço (camada 5.5): cria ou edita conforme a prop `service`.
 *  - service ausente → modo CRIAÇÃO (createService com companyId).
 *  - service presente → modo EDIÇÃO (pré-popula, updateService pelo id; companyId não é
 *    necessário no UPDATE — o RLS garante que o serviço é da empresa do tenant).
 *
 * O reset() num useEffect sincroniza o form ao abrir/trocar de registro (evita valores
 * stale entre aberturas).
 */
export function CreateServiceDialog({
  open,
  onClose,
  companyId,
  service,
}: {
  open: boolean
  onClose: () => void
  companyId: string
  service?: Service
}) {
  const queryClient = useQueryClient()
  const [serverError, setServerError] = useState<string | null>(null)
  const isEdit = service != null

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<ServiceForm>({ resolver: zodResolver(serviceSchema) })

  useEffect(() => {
    if (open) {
      reset({
        name: service?.name ?? '',
        description: service?.description ?? '',
        priceReais: centsToReais(service?.priceCents ?? null),
      })
      setServerError(null)
    }
  }, [open, service, reset])

  const mutation = useMutation({
    mutationFn: (values: ServiceForm) => {
      const payload = {
        name: values.name,
        description: values.description?.trim() ? values.description.trim() : null,
        priceCents: reaisToCents(values.priceReais),
      }
      return isEdit
        ? updateService(service!.id, payload)
        : createService({ companyId, ...payload })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['my-services'] })
      handleClose()
    },
    onError: (err) => {
      console.error(isEdit ? 'updateService failed:' : 'createService failed:', err)
      setServerError(
        isEdit
          ? 'Erro ao salvar alterações. Tente novamente.'
          : 'Erro ao criar serviço. Tente novamente.',
      )
    },
  })

  function handleClose() {
    reset({ name: '', description: '', priceReais: '' })
    setServerError(null)
    onClose()
  }

  function onSubmit(values: ServiceForm) {
    setServerError(null)
    mutation.mutate(values)
  }

  return (
    <Modal open={open} onClose={handleClose} title={isEdit ? 'Editar serviço' : 'Novo serviço'}>
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <div>
          <label htmlFor="name" className="mb-1 block text-sm font-medium">
            Nome
          </label>
          <input
            id="name"
            type="text"
            className="w-full rounded-md border border-border px-3 py-2 text-sm"
            {...register('name')}
          />
          {errors.name && <p className="mt-1 text-sm text-destructive">{errors.name.message}</p>}
        </div>

        <div>
          <label htmlFor="description" className="mb-1 block text-sm font-medium">
            Descrição <span className="text-muted-foreground">(opcional)</span>
          </label>
          <textarea
            id="description"
            rows={3}
            placeholder="O que esse serviço entrega?"
            className="w-full rounded-md border border-border px-3 py-2 text-sm"
            {...register('description')}
          />
        </div>

        <div>
          <label htmlFor="priceReais" className="mb-1 block text-sm font-medium">
            Preço (R$) <span className="text-muted-foreground">(opcional)</span>
          </label>
          <input
            id="priceReais"
            type="text"
            inputMode="decimal"
            placeholder="99.90"
            className="w-full rounded-md border border-border px-3 py-2 text-sm"
            {...register('priceReais')}
          />
          {errors.priceReais && (
            <p className="mt-1 text-sm text-destructive">{errors.priceReais.message}</p>
          )}
        </div>

        {serverError && <p className="text-sm text-destructive">{serverError}</p>}

        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="outline" onClick={handleClose}>
            Cancelar
          </Button>
          <Button type="submit" disabled={isSubmitting || mutation.isPending}>
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
