'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import Link from 'next/link'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Button } from '@/components/ui/button'
import {
  getProfileFeatures,
  setProfileFeature,
  type NicheRow,
  type ProfileFeatureGrid,
} from '@/lib/api/admin/profile-features'
import { ApiError } from '@/lib/api/client'

/**
 * Grade de Feature Flags por Nicho (super-admin — camada 9.0). Linhas = nichos (todos os perfis,
 * labels vindas do backend), colunas = features (CMS por enquanto), células = toggle que chama
 * PUT /admin/profile-features/{profileId}/{featureKey}. Ausência de linha no banco = OFF (default).
 *
 * A 1ª feature é o CMS (página pessoal por tenant) — esta tela só ADMINISTRA a flag; o CMS real
 * vem na SM-M, atrás do gate hasFeature('cms'). A autorização é do backend (403
 * forbidden_not_super_admin → tratado inline, não quebra a UI).
 */
export default function ProfileFeaturesPage() {
  const qc = useQueryClient()
  const [pending, setPending] = useState<string | null>(null) // "profileId:featureKey" em voo

  const { data, isPending, isError, error } = useQuery<ProfileFeatureGrid>({
    queryKey: ['profile-features'],
    queryFn: getProfileFeatures,
  })

  const toggleMutation = useMutation({
    mutationFn: ({
      profileId,
      featureKey,
      enabled,
    }: {
      profileId: string
      featureKey: string
      enabled: boolean
    }) => setProfileFeature(profileId, featureKey, enabled),
    onMutate: ({ profileId, featureKey }) => setPending(`${profileId}:${featureKey}`),
    onSettled: () => {
      setPending(null)
      qc.invalidateQueries({ queryKey: ['profile-features'] })
    },
  })

  // 403: tenant-admin acessou a rota direto. Trata inline (não quebra).
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
    console.error('failed to load /admin/profile-features:', error)
    return (
      <div className="space-y-6">
        <PageHeader title="Features por nicho" />
        <p className="text-sm text-destructive">Erro ao carregar as feature flags.</p>
        <Link href="/dashboard">
          <Button variant="outline">Voltar ao dashboard</Button>
        </Link>
      </div>
    )
  }

  const features = data?.features ?? []
  const niches = data?.niches ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        title="Features por nicho"
        description="Ligue/desligue features por nicho. Toda feature nasce desligada (opt-in do root)."
      />

      <div className="overflow-hidden rounded-lg border border-border bg-card">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border">
                <th className="px-4 py-3 text-left text-xs font-medium tracking-wide text-muted-foreground uppercase">
                  Nicho
                </th>
                {features.map((f) => (
                  <th
                    key={f.key}
                    className="px-4 py-3 text-center text-xs font-medium tracking-wide text-muted-foreground uppercase"
                  >
                    {f.label}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {isPending ? (
                <tr>
                  <td
                    colSpan={1 + features.length}
                    className="px-4 py-8 text-center text-muted-foreground"
                  >
                    Carregando…
                  </td>
                </tr>
              ) : niches.length === 0 ? (
                <tr>
                  <td
                    colSpan={1 + features.length}
                    className="px-4 py-8 text-center text-muted-foreground"
                  >
                    Nenhum nicho.
                  </td>
                </tr>
              ) : (
                niches.map((n: NicheRow) => (
                  <tr
                    key={n.profileId}
                    className="border-t border-border first:border-t-0 hover:bg-muted/40"
                  >
                    <td className="px-4 py-3.5">
                      <span className="font-medium">{n.label}</span>
                      <span className="ml-2 font-mono text-xs text-muted-foreground">
                        {n.profileId}
                      </span>
                    </td>
                    {features.map((f) => {
                      const enabled = n.flags[f.key] === true
                      const cellKey = `${n.profileId}:${f.key}`
                      const busy = pending === cellKey
                      return (
                        <td key={f.key} className="px-4 py-3.5 text-center">
                          <button
                            type="button"
                            disabled={busy}
                            onClick={() =>
                              toggleMutation.mutate({
                                profileId: n.profileId,
                                featureKey: f.key,
                                enabled: !enabled,
                              })
                            }
                            aria-pressed={enabled}
                            aria-label={`${enabled ? 'Desligar' : 'Ligar'} ${f.label} para ${n.label}`}
                            className={
                              'relative inline-flex h-6 w-11 items-center rounded-full transition-colors disabled:opacity-50 ' +
                              (enabled ? 'bg-primary' : 'bg-muted')
                            }
                          >
                            <span
                              className={
                                'inline-block h-4 w-4 transform rounded-full bg-background shadow transition-transform ' +
                                (enabled ? 'translate-x-6' : 'translate-x-1')
                              }
                            />
                          </button>
                        </td>
                      )
                    })}
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      <p className="text-xs text-muted-foreground">
        A 1ª feature é o <strong>CMS</strong> (página pessoal por tenant). Esta tela só administra a
        flag — a funcionalidade em si chega numa próxima entrega, já gateada por esta configuração.
      </p>
    </div>
  )
}
