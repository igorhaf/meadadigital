import { apiFetch } from '@/lib/api/client'

/**
 * SDK das feature flags por nicho do super-admin (camada 9.0). A grade é computada no backend
 * (nichos × features, com as flags do banco sobrepostas; ausência = OFF). O toggle é um PUT por
 * célula. Tudo via apiFetch (injeta o JWT); a autorização é do backend (403
 * forbidden_not_super_admin para não-root).
 */

/** Uma feature (coluna da grade). */
export type FeatureColumn = { key: string; label: string }

/** Uma linha da grade: o nicho + o estado de cada feature. */
export type NicheRow = { profileId: string; label: string; flags: Record<string, boolean> }

/** A grade completa devolvida por GET /admin/profile-features. */
export type ProfileFeatureGrid = { features: FeatureColumn[]; niches: NicheRow[] }

export function getProfileFeatures(): Promise<ProfileFeatureGrid> {
  return apiFetch<ProfileFeatureGrid>('/admin/profile-features')
}

export function setProfileFeature(profileId: string, featureKey: string, enabled: boolean): Promise<unknown> {
  return apiFetch<unknown>(`/admin/profile-features/${profileId}/${featureKey}`, {
    method: 'PUT',
    body: JSON.stringify({ enabled }),
  })
}
