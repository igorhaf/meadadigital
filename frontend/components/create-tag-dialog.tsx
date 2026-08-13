'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'

import { TagColorPicker } from '@/components/tag-color-picker'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { createTag, TAG_COLORS, updateTag, type Tag, type TagColor } from '@/lib/supabase/tags'

// name 1..30 chars (espelha o CHECK do banco); color restrita à paleta de 8.
const tagSchema = z.object({
  name: z.string().trim().min(1, 'Informe o nome').max(30, 'Máximo 30 caracteres'),
  color: z.enum(TAG_COLORS as [TagColor, ...TagColor[]]),
})

type TagForm = z.infer<typeof tagSchema>

/**
 * Modal dual de tag (camada 5.14 #22): cria ou edita conforme a prop `tag`.
 *  - tag ausente → CRIAÇÃO (createTag com companyId, por causa do WITH CHECK do INSERT).
 *  - tag presente → EDIÇÃO (pré-popula, updateTag pelo id; companyId não é necessário no
 *    UPDATE — o RLS já garante que a tag é da empresa do tenant).
 *
 * Cor é controlada manualmente (TagColorPicker não é um input nativo) via setValue +
 * watch. O reset num useEffect sincroniza ao abrir/trocar de registro (sem isso o RHF
 * manteria valores stale entre aberturas).
 */
export function CreateTagDialog({
  open,
  onClose,
  companyId,
  tag,
}: {
  open: boolean
  onClose: () => void
  companyId: string
  tag?: Tag
}) {
  const queryClient = useQueryClient()
  const [serverError, setServerError] = useState<string | null>(null)
  const isEdit = tag != null

  const {
    register,
    handleSubmit,
    reset,
    setValue,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<TagForm>({ resolver: zodResolver(tagSchema), defaultValues: { color: 'slate' } })

  const color = watch('color')

  useEffect(() => {
    if (open) {
      reset({ name: tag?.name ?? '', color: tag?.color ?? 'slate' })
      setServerError(null)
    }
  }, [open, tag, reset])

  const mutation = useMutation({
    mutationFn: (values: TagForm) =>
      isEdit
        ? updateTag(tag!.id, values)
        : createTag({ companyId, name: values.name, color: values.color }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['my-tags'] })
      handleClose()
    },
    onError: (err) => {
      console.error(isEdit ? 'updateTag failed:' : 'createTag failed:', err)
      setServerError(
        isEdit
          ? 'Erro ao salvar alterações. Tente novamente.'
          : 'Erro ao criar tag. Tente novamente.',
      )
    },
  })

  function handleClose() {
    reset({ name: '', color: 'slate' })
    setServerError(null)
    onClose()
  }

  function onSubmit(values: TagForm) {
    setServerError(null)
    mutation.mutate(values)
  }

  return (
    <Modal open={open} onClose={handleClose} title={isEdit ? 'Editar tag' : 'Nova tag'}>
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <div>
          <label htmlFor="name" className="mb-1 block text-sm font-medium">
            Nome
          </label>
          <input
            id="name"
            type="text"
            maxLength={30}
            placeholder="Cliente VIP"
            className="w-full rounded-md border border-border px-3 py-2 text-sm"
            {...register('name')}
          />
          {errors.name && <p className="mt-1 text-sm text-destructive">{errors.name.message}</p>}
        </div>

        <div>
          <span className="mb-1 block text-sm font-medium">Cor</span>
          <TagColorPicker value={color} onChange={(c) => setValue('color', c)} />
          {errors.color && <p className="mt-1 text-sm text-destructive">{errors.color.message}</p>}
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
