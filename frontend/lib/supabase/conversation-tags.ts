import { createClient } from './client'
import type { Tag, TagColor } from './tags'

/**
 * Tags aplicadas a uma conversa (camada 5.14 #22). Lê conversation_tags com a tag
 * embutida via join PostgREST, filtrando tags soft-deleted. O RLS de conversation_tags
 * (via company_id da conversa) + o de tags garantem isolamento por tenant.
 *
 * <p>Join: tag:tags(id, name, color, created_at) — alias "tag" para o objeto aninhado.
 * Linhas cuja tag foi removida (deleted_at != null) são filtradas no map (o join pode
 * trazer a tag mesmo soft-deleted; descartamos).
 */
export async function getConversationTags(conversationId: string): Promise<Tag[]> {
  const supabase = createClient()
  const { data, error } = await supabase
    .from('conversation_tags')
    .select('tag:tags(id, name, color, created_at, deleted_at)')
    .eq('conversation_id', conversationId)

  if (error) {
    throw error
  }

  return (data ?? [])
    .map((row) => (Array.isArray(row.tag) ? row.tag[0] : row.tag))
    .filter(
      (
        t,
      ): t is {
        id: string
        name: string
        color: string
        created_at: string
        deleted_at: string | null
      } => t != null && t.deleted_at == null,
    )
    .map((t) => ({
      id: t.id,
      name: t.name,
      color: t.color as TagColor,
      createdAt: t.created_at,
    }))
}

/**
 * Mapa conversa→tags de TODAS as conversas do tenant numa única query (evita N+1 na lista
 * de conversas). RLS de conversation_tags (via company_id da conversa) limita ao tenant.
 * Retorna um Record<conversationId, Tag[]>; conversas sem tag não aparecem no mapa (o
 * caller usa ?? [] ). Tags soft-deleted são filtradas.
 */
export async function getAllConversationTags(): Promise<Record<string, Tag[]>> {
  const supabase = createClient()
  const { data, error } = await supabase
    .from('conversation_tags')
    .select('conversation_id, tag:tags(id, name, color, created_at, deleted_at)')

  if (error) {
    throw error
  }

  const map: Record<string, Tag[]> = {}
  for (const row of data ?? []) {
    const raw = Array.isArray(row.tag) ? row.tag[0] : row.tag
    if (!raw || raw.deleted_at != null) {
      continue
    }
    const tag: Tag = {
      id: raw.id,
      name: raw.name,
      color: raw.color as TagColor,
      createdAt: raw.created_at,
    }
    ;(map[row.conversation_id] ??= []).push(tag)
  }
  return map
}

/**
 * Aplica uma tag existente a uma conversa: INSERT em conversation_tags via SDK + RLS
 * (conversation_tags_insert: EXISTS conversa do tenant). PK composta impede duplicado —
 * se a tag já estiver aplicada, o INSERT viola a PK; o caller (autocomplete) só oferece
 * tags ainda não aplicadas, então o caso normal não colide.
 */
export async function addTagToConversation(conversationId: string, tagId: string): Promise<void> {
  const supabase = createClient()
  const { error } = await supabase
    .from('conversation_tags')
    .insert({ conversation_id: conversationId, tag_id: tagId })

  if (error) {
    throw error
  }
}

/**
 * Remove uma tag de uma conversa: DELETE do vínculo em conversation_tags via SDK + RLS
 * (conversation_tags_delete: EXISTS conversa do tenant). A tag em si permanece (só o
 * vínculo some).
 */
export async function removeTagFromConversation(
  conversationId: string,
  tagId: string,
): Promise<void> {
  const supabase = createClient()
  const { error } = await supabase
    .from('conversation_tags')
    .delete()
    .eq('conversation_id', conversationId)
    .eq('tag_id', tagId)

  if (error) {
    throw error
  }
}
