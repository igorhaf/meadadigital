import { apiFetch } from '@/lib/api/client'

import { createClient } from './client'

const API_BASE = process.env.NEXT_PUBLIC_API_URL

/**
 * Documento de conhecimento (PDF) do tenant — base do RAG (camada 5.13). Espelha o
 * KnowledgeDocument do backend. status acompanha o processamento síncrono.
 */
export type KnowledgeDocument = {
  id: string
  title: string
  storagePath: string
  status: 'processing' | 'ready' | 'failed'
  errorMessage: string | null
  charCount: number
  chunkCount: number
  active: boolean
  createdAt: string
  updatedAt: string
}

/**
 * Lista os documentos do tenant (GET /admin/knowledge/documents no backend Spring).
 * Via apiFetch (injeta o Bearer da sessão Supabase + trata 401). NÃO é Supabase direto:
 * o backend é o dono do RAG (lê chunks/embeddings como service_role).
 */
export async function getMyDocuments(): Promise<KnowledgeDocument[]> {
  return apiFetch<KnowledgeDocument[]>('/admin/knowledge/documents')
}

/**
 * Envia um PDF para ingestão (multipart). Não usa apiFetch porque o upload é
 * multipart/form-data: o browser PRECISA definir o Content-Type com o boundary, então
 * NÃO setamos header de content-type (o apiFetch forçaria application/json). Reusa o
 * mesmo token da sessão Supabase. A request é SÍNCRONA no backend (extrai+chunka+embeda):
 * pode levar 20-40s — o caller mostra estado de processamento.
 */
export async function uploadDocument(file: File, title: string): Promise<KnowledgeDocument> {
  if (!API_BASE) {
    throw new Error('NEXT_PUBLIC_API_URL não configurada.')
  }
  const supabase = createClient()
  const { data } = await supabase.auth.getSession()
  const token = data?.session?.access_token

  const form = new FormData()
  form.append('file', file)
  form.append('title', title)

  const response = await fetch(`${API_BASE}/admin/knowledge/documents`, {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: form,
  })

  if (!response.ok) {
    let reason = 'upload_failed'
    try {
      const body = await response.json()
      reason = body.reason ?? reason
    } catch {
      // resposta sem JSON — mantém o reason genérico
    }
    throw new Error(reason)
  }
  return response.json() as Promise<KnowledgeDocument>
}

/** Remove (soft delete) um documento. */
export async function deleteDocument(id: string): Promise<void> {
  await apiFetch<void>(`/admin/knowledge/documents/${id}`, { method: 'DELETE' })
}

/** Ativa/desativa um documento (desativado não entra no retrieval da IA). */
export async function setDocumentActive(id: string, active: boolean): Promise<void> {
  await apiFetch<void>(`/admin/knowledge/documents/${id}/active`, {
    method: 'PATCH',
    body: JSON.stringify({ active }),
  })
}
