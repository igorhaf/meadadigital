import { createClient } from './client'

/**
 * Serviço (oferta da empresa) — shape do frontend, mapeado das colunas de public.services.
 * priceCents é nullable (nem todo serviço tem preço cadastrado). createdAt ISO string.
 */
export type Service = {
  id: string
  name: string
  description: string | null
  priceCents: number | null
  active: boolean
  createdAt: string
}

/**
 * Lista os serviços ATIVOS (não soft-deleted) da empresa do tenant logado, via SDK + RLS
 * (services_select: company_id = app.company_id()). O isolamento é do banco — o tenant só
 * vê os próprios serviços. Ordenado por created_at desc (mais novos primeiro).
 *
 * <p>deleted_at is null: services tem soft delete; filtramos os removidos aqui (o RLS
 * não filtra soft delete, é responsabilidade da query).
 */
export async function getMyServices(): Promise<Service[]> {
  const supabase = createClient()
  const { data, error } = await supabase
    .from('services')
    .select('id, name, description, price_cents, active, created_at')
    .is('deleted_at', null)
    .order('created_at', { ascending: false })

  if (error) {
    throw error
  }

  return (data ?? []).map((s) => ({
    id: s.id,
    name: s.name,
    description: s.description,
    priceCents: s.price_cents,
    active: s.active,
    createdAt: s.created_at,
  }))
}

/**
 * Cria um serviço para a empresa do tenant. company_id é OBRIGATÓRIO no payload e DEVE ser
 * o da própria empresa (vem do me.companyId): a policy services_insert tem
 * WITH CHECK (company_id = app.company_id()) — o banco rejeita se não bater. Defesa em
 * profundidade: mesmo que o front mande errado, o RLS barra.
 *
 * <p>active/timestamps são do banco (active default true). Retorna o serviço criado
 * (RETURNING via .select().single()).
 */
export async function createService(payload: {
  companyId: string
  name: string
  description: string | null
  priceCents: number | null
}): Promise<Service> {
  const supabase = createClient()
  const { data, error } = await supabase
    .from('services')
    .insert({
      company_id: payload.companyId,
      name: payload.name,
      description: payload.description,
      price_cents: payload.priceCents,
    })
    .select('id, name, description, price_cents, active, created_at')
    .single()

  if (error) {
    throw error
  }

  return {
    id: data.id,
    name: data.name,
    description: data.description,
    priceCents: data.price_cents,
    active: data.active,
    createdAt: data.created_at,
  }
}

/**
 * Edita um serviço existente do tenant (camada 5.5). UPDATE via SDK + RLS: a policy
 * services_update tem USING + WITH CHECK (company_id = app.company_id()), então o tenant
 * só altera serviço da própria empresa. Audita via trigger app.audit_trigger (fase-5.3).
 *
 * <p>description e priceCents são nullable (null = sem descrição / sem preço). Retorna o
 * serviço atualizado.
 */
export async function updateService(
  id: string,
  payload: { name: string; description: string | null; priceCents: number | null },
): Promise<Service> {
  const supabase = createClient()
  const { data, error } = await supabase
    .from('services')
    .update({
      name: payload.name,
      description: payload.description,
      price_cents: payload.priceCents,
    })
    .eq('id', id)
    .select('id, name, description, price_cents, active, created_at')
    .single()

  if (error) {
    throw error
  }

  return {
    id: data.id,
    name: data.name,
    description: data.description,
    priceCents: data.price_cents,
    active: data.active,
    createdAt: data.created_at,
  }
}
