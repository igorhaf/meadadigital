'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'

import { PaletteSelect } from '@/components/palette-select'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { ApiError } from '@/lib/api/client'
import { createCompany } from '@/lib/api/companies'
import { DEFAULT_PALETTE_ID } from '@/lib/themes/palettes'

// Espelha a validação do backend (CreateCompanyRequest): name obrigatório; slug em
// minúsculas/números/hífens (mesmo regex do @Pattern Java); paletteId obrigatório
// (@NotBlank no backend). O zod é a 1ª barreira; o backend revalida (400 defensivo).
// paletteId não precisa de regra além de min(1): o PaletteSelect é controlled e sempre
// tem um id válido (inicia em meada-default), então nunca fica vazio na prática.
const createCompanySchema = z.object({
  name: z.string().min(1, 'Informe o nome'),
  slug: z.string().regex(/^[a-z0-9]+(-[a-z0-9]+)*$/, 'slug inválido: minúsculas, números e hífens'),
  paletteId: z.string().min(1, 'Selecione uma paleta'),
})

type CreateCompanyForm = z.infer<typeof createCompanySchema>

/** Deriva um slug a partir do name: minúsculas, não-alfanumérico vira hífen, colapsa e
 *  apara hífens das pontas. Sugestão (decisão 4.2): preenche o slug enquanto o usuário
 *  não o editou manualmente; o valor final é sempre dele. */
function slugify(name: string): string {
  return name
    .toLowerCase()
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '') // remove acentos
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
}

export function CreateCompanyDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient()
  const [serverError, setServerError] = useState<string | null>(null)
  const [slugEditedManually, setSlugEditedManually] = useState(false)

  const {
    register,
    handleSubmit,
    setValue,
    watch,
    setError,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<CreateCompanyForm>({
    resolver: zodResolver(createCompanySchema),
    // paletteId inicia em meada-default: o campo é obrigatório (banco NOT NULL) e o
    // PaletteSelect é controlled, então pré-selecionar o verde-Meada satisfaz o
    // obrigatório sem o super-admin precisar abrir o dropdown.
    defaultValues: { paletteId: DEFAULT_PALETTE_ID },
  })

  const mutation = useMutation({
    mutationFn: createCompany,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['companies'] })
      handleClose()
    },
    onError: (err) => {
      if (err instanceof ApiError && err.reason === 'slug_already_exists') {
        setError('slug', { message: 'Já existe uma empresa com este slug.' })
        return
      }
      console.error('createCompany failed:', err)
      setServerError('Erro ao criar empresa. Tente novamente.')
    },
  })

  function handleClose() {
    reset({ paletteId: DEFAULT_PALETTE_ID })
    setServerError(null)
    setSlugEditedManually(false)
    onClose()
  }

  function onSubmit(values: CreateCompanyForm) {
    setServerError(null)
    mutation.mutate(values)
  }

  const nameField = register('name')
  const slugField = register('slug')
  // PaletteSelect é controlled (não <input>): liga ao RHF via watch + setValue, não register.
  const paletteId = watch('paletteId')

  return (
    <Modal open={open} onClose={handleClose} title="Nova empresa">
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <div>
          <label htmlFor="name" className="mb-1 block text-sm font-medium">
            Nome
          </label>
          <input
            id="name"
            type="text"
            className="w-full rounded-md border border-border px-3 py-2 text-sm"
            {...nameField}
            onChange={(e) => {
              nameField.onChange(e)
              if (!slugEditedManually) {
                setValue('slug', slugify(e.target.value), { shouldValidate: true })
              }
            }}
          />
          {errors.name && <p className="mt-1 text-sm text-destructive">{errors.name.message}</p>}
        </div>

        <div>
          <label htmlFor="slug" className="mb-1 block text-sm font-medium">
            Slug
          </label>
          <input
            id="slug"
            type="text"
            className="w-full rounded-md border border-border px-3 py-2 text-sm"
            {...slugField}
            onChange={(e) => {
              slugField.onChange(e)
              setSlugEditedManually(true)
            }}
          />
          {errors.slug && <p className="mt-1 text-sm text-destructive">{errors.slug.message}</p>}
        </div>

        <div>
          <label className="mb-1 block text-sm font-medium">Paleta de tema</label>
          <PaletteSelect
            value={paletteId}
            onChange={(id) => setValue('paletteId', id, { shouldValidate: true })}
            disabled={mutation.isPending}
          />
          {errors.paletteId && (
            <p className="mt-1 text-sm text-destructive">{errors.paletteId.message}</p>
          )}
        </div>

        {serverError && <p className="text-sm text-destructive">{serverError}</p>}

        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="outline" onClick={handleClose}>
            Cancelar
          </Button>
          <Button type="submit" disabled={isSubmitting || mutation.isPending}>
            {mutation.isPending ? 'Criando…' : 'Criar'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
