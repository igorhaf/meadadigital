import { createClient } from './client'

/**
 * Cor de tag — paleta fixa de 8 (espelha o CHECK de public.tags.color). Sem hex livre:
 * o tenant escolhe da paleta visual. Cada cor mapeia para classes Tailwind no
 * componente de chip (TagChip / tag-color-picker).
 */
export type TagColor = 'slate' | 'red' | 'orange' | 'amber' | 'green' | 'blue' | 'violet' | 'pink'

export const TAG_COLORS: TagColor[] = [
  'slate',
  'red',
  'orange',
  'amber',
  'green',
  'blue',
  'violet',
  'pink',
]

/**
 * Tag/etiqueta da empresa — shape do frontend, mapeado de public.tags (camada 5.14 #22).
 * name 1..30 chars (CHECK no banco); color da paleta fixa. createdAt ISO string.
 */
export type Tag = {
  id: string
  name: string
  color: TagColor
  createdAt: string
}

/**
 * Lista as tags ATIVAS (não soft-deleted) da empresa do tenant, via SDK + RLS
 * (tags_select: company_id = app.company_id()). Ordenado por created_at desc.
 *
 * <p>deleted_at is null: tags tem soft delete; a query filtra as removidas (o RLS não
 * filtra soft delete — responsabilidade da query, espelha faqs/services).
 */
export async function getMyTags(): Promise<Tag[]> {
  const supabase = createClient()
  const { data, error } = await supabase
    .from('tags')
    .select('id, name, color, created_at')
    .is('deleted_at', null)
    .order('created_at', { ascending: false })

  if (error) {
    throw error
  }

  return (data ?? []).map((t) => ({
    id: t.id,
    name: t.name,
    color: t.color as TagColor,
    createdAt: t.created_at,
  }))
}

/**
 * Cria uma tag para a empresa do tenant. company_id OBRIGATÓRIO no payload e DEVE ser o
 * da própria empresa (vem do me.companyId): a policy tags_insert tem
 * WITH CHECK (company_id = app.company_id()) — o banco rejeita se não bater (defesa em
 * profundidade). Retorna a tag criada.
 */
export async function createTag(payload: {
  companyId: string
  name: string
  color: TagColor
}): Promise<Tag> {
  const supabase = createClient()
  const { data, error } = await supabase
    .from('tags')
    .insert({
      company_id: payload.companyId,
      name: payload.name,
      color: payload.color,
    })
    .select('id, name, color, created_at')
    .single()

  if (error) {
    throw error
  }

  return {
    id: data.id,
    name: data.name,
    color: data.color as TagColor,
    createdAt: data.created_at,
  }
}

/**
 * Edita uma tag existente do tenant. UPDATE via SDK + RLS (tags_update: USING + WITH CHECK
 * = company_id) — não precisa passar company_id (a linha já é da empresa do tenant e o RLS
 * revalida). Toca updated_at? Não automaticamente (sem trigger de updated_at); o campo
 * fica como estava — não exposto na UI. Retorna a tag atualizada.
 */
export async function updateTag(
  id: string,
  payload: { name: string; color: TagColor },
): Promise<Tag> {
  const supabase = createClient()
  const { data, error } = await supabase
    .from('tags')
    .update({ name: payload.name, color: payload.color })
    .eq('id', id)
    .select('id, name, color, created_at')
    .single()

  if (error) {
    throw error
  }

  return {
    id: data.id,
    name: data.name,
    color: data.color as TagColor,
    createdAt: data.created_at,
  }
}

/**
 * Remove (soft delete) uma tag do tenant: UPDATE deleted_at = now() via SDK + RLS
 * (tags_update). Espelha o padrão de services/faqs — sem DELETE físico (a policy de DELETE
 * não existe para authenticated). Os vínculos em conversation_tags permanecem no banco
 * mas a tag some das listas (filtro deleted_at is null) e do autocomplete.
 */
export async function deleteTag(id: string): Promise<void> {
  const supabase = createClient()
  const { error } = await supabase
    .from('tags')
    .update({ deleted_at: new Date().toISOString() })
    .eq('id', id)

  if (error) {
    throw error
  }
}
