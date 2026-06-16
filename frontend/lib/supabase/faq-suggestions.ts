import { createClient } from './client'

/**
 * Sugestão de FAQ (camada 5.18 #54): uma pergunta recente do cliente (mensagem inbound)
 * vinda de conversa que caiu para atendimento humano (handled_by='human') — ou seja, algo
 * que a IA não respondeu com confiança. Vira candidata a virar FAQ. content é o texto da
 * pergunta; conversationId é a origem; lastAt é o created_at da mensagem (ISO string).
 */
export type FaqSuggestion = {
  content: string
  conversationId: string
  lastAt: string
}

/** Linha crua de messages vinda do PostgREST (snake_case) para a agregação de sugestões. */
type SuggestionRow = {
  content: string
  conversation_id: string
  created_at: string
}

/**
 * Agrega sugestões de FAQ no cliente (SDK + RLS) — sem endpoint novo, sem migration. Pega
 * as mensagens inbound das conversas em atendimento humano (handled_by='human'), mais
 * recentes primeiro, deduplica por conteúdo e limita a 15.
 *
 * Estratégia em dois passos (mais robusta que filtrar o join aninhado no PostgREST):
 *   1. busca os ids das conversas com handled_by='human' (RLS já restringe ao tenant);
 *   2. busca as mensagens inbound dessas conversas (created_at desc, limit 50).
 * Depois deduplica em JS por conteúdo trimado+lowercased (mantém a 1ª ocorrência, a mais
 * recente) e corta em 15. Lista vazia de conversas → retorna [] sem segunda query.
 *
 * <p>RLS de conversations e messages ambos filtram por company_id = app.company_id() — o
 * tenant só enxerga as próprias conversas/mensagens. Isolamento garantido pelo banco.
 */
export async function getFaqSuggestions(): Promise<FaqSuggestion[]> {
  const supabase = createClient()

  const { data: convs, error: convError } = await supabase
    .from('conversations')
    .select('id')
    .eq('handled_by', 'human')

  if (convError) {
    throw convError
  }

  const ids = (convs ?? []).map((c) => c.id as string)
  if (ids.length === 0) {
    return []
  }

  const { data, error } = await supabase
    .from('messages')
    .select('content, conversation_id, created_at')
    .eq('direction', 'inbound')
    .in('conversation_id', ids)
    .order('created_at', { ascending: false })
    .limit(50)

  if (error) {
    throw error
  }

  // Deduplica por conteúdo (trim + lowercase): mantém a 1ª ocorrência (a mais recente,
  // pois já vem desc). Corta em 15 sugestões — suficiente para a seção sem poluir a tela.
  const seen = new Set<string>()
  const suggestions: FaqSuggestion[] = []
  for (const raw of (data ?? []) as SuggestionRow[]) {
    const key = raw.content.trim().toLowerCase()
    if (key.length === 0 || seen.has(key)) {
      continue
    }
    seen.add(key)
    suggestions.push({
      content: raw.content,
      conversationId: raw.conversation_id,
      lastAt: raw.created_at,
    })
    if (suggestions.length >= 15) {
      break
    }
  }

  return suggestions
}
