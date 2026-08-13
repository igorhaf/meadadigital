import { apiFetch } from '@/lib/api/client'

/**
 * Conexão do WhatsApp do tenant (Fase 0). O número NÃO é digitado — é PAREADO por QR code.
 * O que a tela exibe como "seu número" vem do `ownerJid` que a Evolution devolve após o
 * pareamento; é a fonte da verdade, não um input.
 */
export type WhatsappStatus = 'not_configured' | 'connecting' | 'connected' | 'disconnected'

export type WhatsappConnection = {
  /** false = o servidor não tem a API key global da Evolution → conexão pelo painel desligada. */
  available: boolean
  status: WhatsappStatus
  /** E.164 do número conectado (ex.: +5511988887777). null enquanto não houver pareamento. */
  phoneNumber: string | null
  profileName: string | null
  instanceName: string | null
}

/** Estado atual, sincronizado com a Evolution (ela é a fonte da verdade do pareamento). */
export function getWhatsappConnection(): Promise<WhatsappConnection> {
  return apiFetch<WhatsappConnection>('/admin/whatsapp')
}

/** Provisiona (ou retoma) o pareamento e devolve o QR (data-URI base64) para exibir. */
export function connectWhatsapp(): Promise<{ qrCode: string; status: WhatsappStatus }> {
  return apiFetch<{ qrCode: string; status: WhatsappStatus }>('/admin/whatsapp/connect', {
    method: 'POST',
  })
}

/** Encerra a sessão. A instância e o histórico de conversas permanecem. */
export function disconnectWhatsapp(): Promise<void> {
  return apiFetch<void>('/admin/whatsapp/disconnect', { method: 'POST' })
}
