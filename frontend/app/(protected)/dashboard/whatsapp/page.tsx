'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { ApiError } from '@/lib/api/client'
import { getMe } from '@/lib/api/me'
import {
  connectWhatsapp,
  disconnectWhatsapp,
  getWhatsappConnection,
  type WhatsappStatus,
} from '@/lib/api/whatsapp'

const CONNECT_ERRORS: Record<string, string> = {
  already_connected: 'Já existe um número conectado. Desconecte antes de parear outro.',
  instance_name_taken:
    'O nome da instância já existe no servidor de WhatsApp. Fale com o suporte para liberar.',
  whatsapp_unavailable: 'A conexão pelo painel não está habilitada neste servidor.',
  evolution_error: 'O servidor de WhatsApp não respondeu. Tente novamente em instantes.',
}

function statusBadge(status: WhatsappStatus) {
  if (status === 'connected') return <Badge variant="success">conectado</Badge>
  if (status === 'connecting') return <Badge>aguardando leitura do QR…</Badge>
  if (status === 'disconnected') return <Badge variant="danger">desconectado</Badge>
  return <Badge variant="muted">não configurado</Badge>
}

/**
 * Conexão do WhatsApp do tenant — o número que os clientes dele usam para falar com a IA.
 *
 * O número NÃO é digitado: é PAREADO. O tenant clica em conectar, escaneia o QR com o celular do
 * número dele, e a Evolution devolve o número real (ownerJid) — que é o que exibimos. Um input
 * "digite seu número" salvaria um texto e não conectaria nada.
 *
 * Enquanto o status é 'connecting', a tela faz polling (o pareamento acontece no celular, fora daqui).
 */
export default function WhatsappPage() {
  const router = useRouter()
  const qc = useQueryClient()
  const [qrCode, setQrCode] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  const { data: me } = useQuery({ queryKey: ['me'], queryFn: getMe })
  useEffect(() => {
    // Super-admin não tem company_id — não conecta WhatsApp de ninguém (o backend também barra).
    if (me && me.role !== 'tenant_admin') {
      router.replace('/dashboard')
    }
  }, [me, router])

  const isTenant = me?.role === 'tenant_admin'
  const { data, isPending, isError } = useQuery({
    queryKey: ['whatsapp-connection'],
    queryFn: getWhatsappConnection,
    enabled: isTenant,
    // Enquanto aguarda o QR ser escaneado, o estado muda FORA do painel (no celular) → polling.
    refetchInterval: (query) => (query.state.data?.status === 'connecting' ? 3000 : false),
  })

  // Pareou → o QR não serve mais para nada. DERIVADO do estado (não um useEffect que
  // faria setState em cascata — o eslint react-hooks reprova, com razão).
  const activeQrCode = data?.status === 'connected' ? null : qrCode

  const connectMut = useMutation({
    mutationFn: connectWhatsapp,
    onSuccess: (res) => {
      setQrCode(res.qrCode)
      setActionError(null)
      qc.invalidateQueries({ queryKey: ['whatsapp-connection'] })
    },
    onError: (e) => {
      setQrCode(null)
      setActionError(
        e instanceof ApiError
          ? (CONNECT_ERRORS[e.reason] ?? 'Não foi possível iniciar a conexão.')
          : 'Não foi possível iniciar a conexão.',
      )
    },
  })

  const disconnectMut = useMutation({
    mutationFn: disconnectWhatsapp,
    onSuccess: () => {
      setQrCode(null)
      setActionError(null)
      qc.invalidateQueries({ queryKey: ['whatsapp-connection'] })
    },
    onError: (e) => {
      setActionError(
        e instanceof ApiError
          ? (CONNECT_ERRORS[e.reason] ?? 'Não foi possível desconectar.')
          : 'Não foi possível desconectar.',
      )
    },
  })

  if (!isTenant) return null

  return (
    <div className="space-y-6">
      <PageHeader
        title="WhatsApp"
        description="Conecte o número que seus clientes vão usar para falar com o atendente de IA."
      />

      {isError && <p className="text-sm text-destructive">Erro ao carregar o estado da conexão.</p>}
      {actionError && <p className="text-sm text-destructive">{actionError}</p>}

      {isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : !data?.available ? (
        <div className="rounded-lg border border-border bg-card p-6">
          <p className="text-sm text-muted-foreground">
            A conexão do WhatsApp pelo painel não está habilitada neste servidor. Fale com o suporte
            para conectar seu número.
          </p>
        </div>
      ) : (
        <div className="rounded-lg border border-border bg-card p-6">
          <div className="flex flex-wrap items-center gap-3">
            <span className="text-sm font-medium">Status:</span>
            {statusBadge(data.status)}
            {data.phoneNumber && (
              <span className="text-sm text-muted-foreground">
                {data.phoneNumber}
                {data.profileName ? ` · ${data.profileName}` : ''}
              </span>
            )}
          </div>

          {data.status === 'connected' ? (
            <div className="mt-6 space-y-4">
              <p className="text-sm text-muted-foreground">
                Este é o número que atende seus clientes. Para trocar de número, desconecte e
                conecte outro — seu histórico de conversas é preservado.
              </p>
              <Button
                variant="outline"
                disabled={disconnectMut.isPending}
                onClick={() => disconnectMut.mutate()}
              >
                {disconnectMut.isPending ? 'Desconectando…' : 'Desconectar'}
              </Button>
            </div>
          ) : (
            <div className="mt-6 space-y-4">
              {activeQrCode || data.status === 'connecting' ? (
                <div className="space-y-3">
                  <p className="text-sm text-muted-foreground">
                    Abra o WhatsApp no celular do número que vai atender, vá em{' '}
                    <strong>Aparelhos conectados → Conectar um aparelho</strong> e escaneie o código
                    abaixo. A tela atualiza sozinha quando o pareamento concluir.
                  </p>
                  {activeQrCode ? (
                    // eslint-disable-next-line @next/next/no-img-element -- data-URI vindo da Evolution
                    <img
                      src={activeQrCode}
                      alt="QR code para conectar o WhatsApp"
                      className="h-64 w-64 rounded-lg border border-border bg-white p-2"
                    />
                  ) : (
                    <p className="text-sm text-muted-foreground">
                      O código expirou. Gere um novo para continuar.
                    </p>
                  )}
                  <Button
                    variant="outline"
                    disabled={connectMut.isPending}
                    onClick={() => connectMut.mutate()}
                  >
                    {connectMut.isPending ? 'Gerando…' : 'Gerar novo código'}
                  </Button>
                </div>
              ) : (
                <>
                  <p className="text-sm text-muted-foreground">
                    Ao conectar, você vai escanear um QR code com o celular do número desejado. O
                    número aparece aqui automaticamente depois do pareamento.
                  </p>
                  <Button disabled={connectMut.isPending} onClick={() => connectMut.mutate()}>
                    {connectMut.isPending ? 'Conectando…' : 'Conectar WhatsApp'}
                  </Button>
                </>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
