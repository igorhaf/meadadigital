'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { use, useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'

import { PageHeader } from '@/components/layout/page-header'
import { PaletteSelect } from '@/components/palette-select'
import { Button } from '@/components/ui/button'
import { Card, Section } from '@/components/ui/card'
import { getCompany, updateCompany, type UpdateCompanyPayload } from '@/lib/api/admin/companies'
import { getProfiles } from '@/lib/api/admin/profiles'
import { ApiError } from '@/lib/api/client'
import { getProfile } from '@/lib/profiles/profile-type'

/**
 * Edição de empresa (camada 6.1). Identidade (name/slug/paleta) + limites do plano. O zod
 * espelha a validação do backend (UpdateCompanyRequest): name obrigatório; slug no mesmo
 * regex; limites opcionais >= 0. Colisão de slug (409) vira erro de campo. Super-admin only
 * — autorização no backend (403 tratado inline).
 */
const editCompanySchema = z.object({
  name: z.string().min(1, 'Informe o nome'),
  slug: z.string().regex(/^[a-z0-9]+(-[a-z0-9]+)*$/, 'slug inválido: minúsculas, números e hífens'),
  paletteId: z.string().min(1, 'Selecione uma paleta'),
  profileId: z.string().min(1, 'Selecione um perfil'),
  // Limites: strings no form (input number devolve string); "" = sem limite → null.
  maxAdmins: z.string(),
  maxFaqs: z.string(),
  maxConversationsMonth: z.string(),
})

type EditCompanyForm = z.infer<typeof editCompanySchema>

/** Converte o campo de limite do form (string) em number|null para o payload. */
function toLimit(v: string): number | null {
  const t = v.trim()
  if (t === '') return null
  const n = Number(t)
  return Number.isFinite(n) && n >= 0 ? Math.floor(n) : null
}

export default function CompanyEditPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params)
  const router = useRouter()
  const queryClient = useQueryClient()
  const [serverError, setServerError] = useState<string | null>(null)

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['company', id],
    queryFn: () => getCompany(id),
  })

  const {
    register,
    handleSubmit,
    setValue,
    watch,
    setError,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<EditCompanyForm>({
    resolver: zodResolver(editCompanySchema),
    defaultValues: {
      name: '',
      slug: '',
      paletteId: 'meada-default',
      profileId: 'generic',
      maxAdmins: '',
      maxFaqs: '',
      maxConversationsMonth: '',
    },
  })

  // Catálogo de perfis (camada 7.0) para o dropdown.
  const { data: profilesData } = useQuery({ queryKey: ['profiles'], queryFn: getProfiles })

  // Preenche o form quando o detalhe carrega (reset é idempotente).
  useEffect(() => {
    if (data) {
      reset({
        name: data.name,
        slug: data.slug,
        paletteId: data.paletteId,
        profileId: data.profileId,
        maxAdmins: data.maxAdmins == null ? '' : String(data.maxAdmins),
        maxFaqs: data.maxFaqs == null ? '' : String(data.maxFaqs),
        maxConversationsMonth:
          data.maxConversationsMonth == null ? '' : String(data.maxConversationsMonth),
      })
    }
  }, [data, reset])

  const mutation = useMutation({
    mutationFn: (payload: UpdateCompanyPayload) => updateCompany(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['company', id] })
      queryClient.invalidateQueries({ queryKey: ['companies'] })
      router.push(`/dashboard/companies/${id}`)
    },
    onError: (err) => {
      if (err instanceof ApiError && err.reason === 'slug_already_exists') {
        setError('slug', { message: 'Já existe uma empresa com este slug.' })
        return
      }
      console.error('updateCompany failed:', err)
      setServerError('Erro ao salvar. Tente novamente.')
    },
  })

  if (isError && error instanceof ApiError && error.status === 403) {
    return (
      <div className="space-y-6">
        <PageHeader title="Acesso restrito" description="Esta área é restrita ao super-admin." />
        <Link href="/dashboard">
          <Button variant="outline">Voltar ao dashboard</Button>
        </Link>
      </div>
    )
  }

  if (isError) {
    return (
      <div className="space-y-6">
        <PageHeader title="Empresa" />
        <p className="text-sm text-destructive">Erro ao carregar a empresa.</p>
        <Link href="/dashboard/companies">
          <Button variant="outline">Voltar à lista</Button>
        </Link>
      </div>
    )
  }

  if (isPending || !data) {
    return (
      <div className="space-y-6">
        <PageHeader title="Carregando…" />
      </div>
    )
  }

  function onSubmit(values: EditCompanyForm) {
    setServerError(null)
    mutation.mutate({
      name: values.name,
      slug: values.slug,
      paletteId: values.paletteId,
      profileId: values.profileId,
      maxAdmins: toLimit(values.maxAdmins),
      maxFaqs: toLimit(values.maxFaqs),
      maxConversationsMonth: toLimit(values.maxConversationsMonth),
    })
  }

  const paletteId = watch('paletteId')
  const profileId = watch('profileId')
  const profileChanged = !!data && profileId !== data.profileId

  return (
    <div className="space-y-6">
      <PageHeader
        title={`Editar ${data.name}`}
        breadcrumb={[
          { label: 'Empresas', href: '/dashboard/companies' },
          { label: data.name, href: `/dashboard/companies/${id}` },
          { label: 'Editar' },
        ]}
      />

      <Card>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
          <Section title="Identidade">
            <div className="space-y-4">
              <div>
                <label htmlFor="name" className="mb-1 block text-sm font-medium">
                  Nome
                </label>
                <input
                  id="name"
                  type="text"
                  className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                  {...register('name')}
                />
                {errors.name && (
                  <p className="mt-1 text-sm text-destructive">{errors.name.message}</p>
                )}
              </div>

              <div>
                <label htmlFor="slug" className="mb-1 block text-sm font-medium">
                  Slug
                </label>
                <input
                  id="slug"
                  type="text"
                  className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                  {...register('slug')}
                />
                {errors.slug && (
                  <p className="mt-1 text-sm text-destructive">{errors.slug.message}</p>
                )}
              </div>

              <div>
                <label className="mb-1 block text-sm font-medium">Paleta de tema</label>
                <PaletteSelect
                  value={paletteId}
                  onChange={(pid) => setValue('paletteId', pid, { shouldValidate: true })}
                  disabled={mutation.isPending}
                />
                {errors.paletteId && (
                  <p className="mt-1 text-sm text-destructive">{errors.paletteId.message}</p>
                )}
              </div>

              <div>
                <label htmlFor="profileId" className="mb-1 block text-sm font-medium">
                  Perfil (produto)
                </label>
                <select
                  id="profileId"
                  className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                  value={profileId}
                  onChange={(e) => {
                    const newProfile = e.target.value
                    setValue('profileId', newProfile, { shouldValidate: true })
                    // ao trocar o nicho, a paleta acompanha o padrão do perfil (o root pode
                    // sobrescrever depois no seletor de paleta). Evita tudo cair no verde-Meada.
                    const def = getProfile(newProfile)?.defaultPaletteId
                    if (def) setValue('paletteId', def, { shouldValidate: true })
                  }}
                >
                  {(profilesData?.items ?? []).map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.productName} ({p.id})
                    </option>
                  ))}
                </select>
                {errors.profileId && (
                  <p className="mt-1 text-sm text-destructive">{errors.profileId.message}</p>
                )}
                {profileChanged && (
                  <p className="mt-2 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-900 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-200">
                    Trocar o perfil muda o produto inteiro para este tenant (tom da IA, navegação e,
                    futuramente, as features disponíveis). Confirme antes de salvar.
                  </p>
                )}
              </div>
            </div>
          </Section>

          <Section title="Limites do plano" description="Deixe em branco para 'sem limite'.">
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
              <div>
                <label htmlFor="maxAdmins" className="mb-1 block text-sm font-medium">
                  Máx. admins
                </label>
                <input
                  id="maxAdmins"
                  type="number"
                  min={0}
                  className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                  {...register('maxAdmins')}
                />
              </div>
              <div>
                <label htmlFor="maxFaqs" className="mb-1 block text-sm font-medium">
                  Máx. FAQs
                </label>
                <input
                  id="maxFaqs"
                  type="number"
                  min={0}
                  className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                  {...register('maxFaqs')}
                />
              </div>
              <div>
                <label htmlFor="maxConversationsMonth" className="mb-1 block text-sm font-medium">
                  Máx. conversas/mês
                </label>
                <input
                  id="maxConversationsMonth"
                  type="number"
                  min={0}
                  className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                  {...register('maxConversationsMonth')}
                />
              </div>
            </div>
          </Section>

          {serverError && <p className="text-sm text-destructive">{serverError}</p>}

          <div className="flex justify-end gap-2">
            <Link href={`/dashboard/companies/${id}`}>
              <Button type="button" variant="outline">
                Cancelar
              </Button>
            </Link>
            <Button type="submit" disabled={isSubmitting || mutation.isPending}>
              {mutation.isPending ? 'Salvando…' : 'Salvar'}
            </Button>
          </div>
        </form>
      </Card>
    </div>
  )
}
