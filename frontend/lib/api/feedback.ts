import { apiFetch } from './client'

/**
 * Feedback de uma resposta da IA (modo treinamento — camada 5.25 #57). O tenant marca a resposta
 * como boa/ruim e opcionalmente fornece uma correção, material para curadoria do prompt.
 *
 * <p>Shape espelhando o backend (/admin/message-feedback, camelCase). messageContent é o texto da
 * mensagem da IA juntado pelo backend (para a tela de revisão exibir sem outra query).
 */
export type MessageFeedback = {
  id: string
  messageId: string
  rating: 'good' | 'bad'
  correction: string | null
  messageContent: string
  createdAt: string
}

/**
 * Registra (ou atualiza) o feedback de uma mensagem da IA. Upsert por message_id no backend: chamar
 * de novo na mesma mensagem muda o rating/correção. 201 cria, 200 atualiza — o apiFetch trata ambos
 * como sucesso (não distinguimos no frontend).
 */
export async function submitMessageFeedback(
  messageId: string,
  rating: 'good' | 'bad',
  correction?: string,
): Promise<void> {
  await apiFetch<void>('/admin/message-feedback', {
    method: 'POST',
    body: JSON.stringify({ messageId, rating, correction }),
  })
}

/**
 * Lista as respostas marcadas como ruins (50 mais recentes) — view de "revisar respostas ruins".
 * Reusa o endpoint de list com o filtro rating=bad.
 */
export async function getBadFeedback(): Promise<MessageFeedback[]> {
  return apiFetch<MessageFeedback[]>('/admin/message-feedback?rating=bad')
}
