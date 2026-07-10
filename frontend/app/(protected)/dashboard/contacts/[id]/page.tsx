'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { use, useEffect, useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, Section } from '@/components/ui/card'
import { Modal } from '@/components/ui/modal'
import { eraseContact, exportContactData } from '@/lib/api/lgpd'
import { getMe } from '@/lib/api/me'
import {
  getContact,
  getContactConversations,
  setContactBlocked,
  updateContactBirthDate,
  updateContactName,
} from '@/lib/supabase/contacts'
import { useOnSync } from '@/lib/use-synced-form'

/**
 * Detalhe de um contato (SDK + RLS). Mostra nome (editável inline), telefone (read-only),
 * status de bloqueio (toggle), e a lista de conversas do contato (link para cada).
 *
 * Next 16: params é Promise — desembrulhado com use(). Guard de papel como nas outras
 * telas do tenant. Contato de outro tenant → RLS faz getContact lançar → erro.
 */
export default function ContactDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params)
  const router = useRouter()
  const queryClient = useQueryClient()
  const [nameDraft, setNameDraft] = useState<string>('')
  const [nameSaved, setNameSaved] = useState(false)
  const [birthDraft, setBirthDraft] = useState<string>('')
  const [birthSaved, setBirthSaved] = useState(false)
  // LGPD (camada 5.24): diálogo de exclusão com confirmação por digitação.
  const [eraseOpen, setEraseOpen] = useState(false)
  const [eraseConfirm, setEraseConfirm] = useState('')
  const [exporting, setExporting] = useState(false)
  const [exportError, setExportError] = useState<string | null>(null)

  const { data: me } = useQuery({ queryKey: ['me'], queryFn: getMe })
  const isTenant = me?.role === 'tenant_admin'

  useEffect(() => {
    if (me && me.role !== 'tenant_admin') {
      router.replace('/dashboard')
    }
  }, [me, router])

  const { data: contact, isError: contactError } = useQuery({
    queryKey: ['contact', id],
    queryFn: () => getContact(id),
    enabled: isTenant,
  })

  const { data: conversations } = useQuery({
    queryKey: ['contact-conversations', id],
    queryFn: () => getContactConversations(id),
    enabled: isTenant,
  })

  // Sincroniza os rascunhos (nome + nascimento) quando o contato carrega.
  useOnSync(contact, (c) => {
    setNameDraft(c.name ?? '')
    setBirthDraft(c.birthDate ?? '')
  })

  const nameMutation = useMutation({
    mutationFn: (name: string) => updateContactName(id, name),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['contact', id] })
      queryClient.invalidateQueries({ queryKey: ['my-contacts'] })
      setNameSaved(true)
      setTimeout(() => setNameSaved(false), 3000)
    },
    onError: (err) => console.error('updateContactName failed:', err),
  })

  const birthMutation = useMutation({
    mutationFn: (birthDate: string | null) => updateContactBirthDate(id, birthDate),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['contact', id] })
      setBirthSaved(true)
      setTimeout(() => setBirthSaved(false), 3000)
    },
    onError: (err) => console.error('updateContactBirthDate failed:', err),
  })

  const blockMutation = useMutation({
    mutationFn: (blocked: boolean) => setContactBlocked(id, blocked),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['contact', id] })
      queryClient.invalidateQueries({ queryKey: ['my-contacts'] })
    },
    onError: (err) => console.error('setContactBlocked failed:', err),
  })

  // O texto que o usuário precisa digitar para liberar a exclusão: nome do contato, ou o
  // telefone quando não há nome. Espelha o título mostrado na tela.
  const eraseTarget = contact?.name?.trim() || contact?.phoneNumber || ''

  // Export LGPD (#90): baixa o JSON do backend como arquivo (Blob + objectURL).
  async function handleExport() {
    setExportError(null)
    setExporting(true)
    try {
      const data = await exportContactData(id)
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `contato-${id}.json`
      document.body.appendChild(a)
      a.click()
      a.remove()
      URL.revokeObjectURL(url)
    } catch (err) {
      console.error('exportContactData failed:', err)
      setExportError('Não foi possível exportar os dados. Tente novamente.')
    } finally {
      setExporting(false)
    }
  }

  // Erase LGPD (#89): hard delete irreversível. Após apagar, volta à lista de contatos.
  const eraseMutation = useMutation({
    mutationFn: () => eraseContact(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['my-contacts'] })
      setEraseOpen(false)
      router.push('/dashboard/contacts')
    },
    onError: (err) => console.error('eraseContact failed:', err),
  })

  if (me && !isTenant) {
    return <p className="text-sm text-muted-foreground">Redirecionando…</p>
  }

  if (contactError) {
    return (
      <div className="space-y-6">
        <PageHeader
          title="Contato"
          breadcrumb={[{ label: 'Contatos', href: '/dashboard/contacts' }, { label: 'Contato' }]}
        />
        <p className="text-sm text-destructive">
          Erro ao carregar o contato (ou ele não pertence à sua empresa).
        </p>
      </div>
    )
  }

  const contactLabel = contact?.name ?? contact?.phoneNumber ?? 'Contato'

  return (
    <div className="space-y-6">
      <PageHeader
        title={contactLabel}
        breadcrumb={[{ label: 'Contatos', href: '/dashboard/contacts' }, { label: contactLabel }]}
      />

      {contact && (
        <Card className="space-y-4">
          <div>
            <label htmlFor="name" className="mb-1 block text-sm font-medium">
              Nome
            </label>
            <div className="flex items-center gap-2">
              <input
                id="name"
                type="text"
                value={nameDraft}
                onChange={(e) => {
                  setNameDraft(e.target.value)
                  setNameSaved(false)
                }}
                placeholder="Sem nome"
                className="w-full max-w-xs rounded-md border border-border px-3 py-2 text-sm"
              />
              <Button
                onClick={() => nameMutation.mutate(nameDraft.trim())}
                disabled={nameMutation.isPending || nameDraft.trim() === (contact.name ?? '')}
              >
                {nameMutation.isPending ? 'Salvando…' : 'Salvar'}
              </Button>
              {nameSaved && <span className="text-sm text-green-600">Salvo!</span>}
            </div>
          </div>

          <div>
            <dt className="text-xs text-muted-foreground uppercase">Telefone</dt>
            <dd className="text-sm font-medium">{contact.phoneNumber}</dd>
          </div>

          {/* Data de nascimento (migration 79): opcional; hoje alimenta a saudação automática
              de aniversário do perfil academia. Limpar o campo remove a data. */}
          <div>
            <label htmlFor="birth-date" className="mb-1 block text-sm font-medium">
              Data de nascimento
            </label>
            <div className="flex items-center gap-2">
              <input
                id="birth-date"
                type="date"
                value={birthDraft}
                onChange={(e) => {
                  setBirthDraft(e.target.value)
                  setBirthSaved(false)
                }}
                className="rounded-md border border-border px-3 py-2 text-sm"
              />
              <Button
                onClick={() => birthMutation.mutate(birthDraft || null)}
                disabled={birthMutation.isPending || birthDraft === (contact.birthDate ?? '')}
              >
                {birthMutation.isPending ? 'Salvando…' : 'Salvar'}
              </Button>
              {birthSaved && <span className="text-sm text-green-600">Salvo!</span>}
            </div>
          </div>

          {/* Canais (#74 unificação multi-canal): mostra os canais em que o contato existe
              (whatsapp/web/email) com o identificador de cada. Some quando ainda não há nenhum. */}
          {contact.channels && Object.keys(contact.channels).length > 0 && (
            <div>
              <dt className="text-xs text-muted-foreground uppercase">Canais</dt>
              <dd className="mt-1 flex flex-wrap items-center gap-1.5">
                {Object.entries(contact.channels).map(([channel, identifier]) => (
                  <span key={channel} title={String(identifier)}>
                    <Badge variant="default">{channel}</Badge>
                  </span>
                ))}
              </dd>
            </div>
          )}

          <div className="flex items-center gap-3">
            <div>
              <dt className="text-xs text-muted-foreground uppercase">Status</dt>
              <dd className="mt-1">
                <Badge variant={contact.blocked ? 'danger' : 'success'}>
                  {contact.blocked ? 'bloqueado' : 'ativo'}
                </Badge>
              </dd>
            </div>
            <Button
              variant="outline"
              disabled={blockMutation.isPending}
              onClick={() => blockMutation.mutate(!contact.blocked)}
            >
              {contact.blocked ? 'Desbloquear' : 'Bloquear'}
            </Button>
          </div>
          {contact.blocked && (
            <p className="text-xs text-muted-foreground">
              Contato bloqueado: as mensagens dele são registradas no histórico, mas a IA não
              responde automaticamente.
            </p>
          )}
        </Card>
      )}

      {contact && (
        <Card className="space-y-3 border-destructive/40">
          <div>
            <h2 className="text-base font-semibold">Privacidade (LGPD)</h2>
            <p className="mt-1 text-xs text-muted-foreground">
              Exporte todos os dados deste contato ou exclua-os definitivamente. A exclusão remove o
              contato, suas conversas, mensagens e agendamentos — é irreversível.
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <Button variant="outline" onClick={handleExport} disabled={exporting}>
              {exporting ? 'Exportando…' : 'Exportar dados (LGPD)'}
            </Button>
            <Button
              variant="outline"
              className="border-destructive text-destructive hover:bg-destructive/10"
              onClick={() => {
                setEraseConfirm('')
                setEraseOpen(true)
              }}
            >
              Excluir dados (LGPD)
            </Button>
          </div>
          {exportError && <p className="text-sm text-destructive">{exportError}</p>}
        </Card>
      )}

      <Section title="Conversas">
        <div className="space-y-2 rounded-lg border border-border bg-card p-4">
          {conversations == null && <p className="text-sm text-muted-foreground">Carregando…</p>}
          {conversations?.length === 0 && (
            <p className="text-sm text-muted-foreground">Nenhuma conversa com este contato.</p>
          )}
          {conversations?.map((c) => (
            <Link
              key={c.id}
              href={`/dashboard/conversations/${c.id}`}
              className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm hover:bg-muted/40"
            >
              <span className="flex items-center gap-2">
                <Badge variant={c.status === 'open' ? 'success' : 'danger'}>{c.status}</Badge>
                <Badge variant={c.handledBy === 'ai' ? 'default' : 'warning'}>{c.handledBy}</Badge>
                {/* Canal de origem da conversa (#74): só destaca quando não é o whatsapp default. */}
                {c.channel !== 'whatsapp' && <Badge variant="default">{c.channel}</Badge>}
              </span>
              <span className="text-muted-foreground">
                {c.lastMessageAt ? new Date(c.lastMessageAt).toLocaleString('pt-BR') : '—'}
              </span>
            </Link>
          ))}
        </div>
      </Section>

      <Modal
        open={eraseOpen}
        onClose={() => setEraseOpen(false)}
        title="Excluir dados do contato (LGPD)"
        size="md"
      >
        <div className="space-y-4">
          <p className="text-sm text-muted-foreground">
            Esta ação é <strong>irreversível</strong>. Serão apagados o contato, todas as suas
            conversas, mensagens e agendamentos. Para confirmar, digite{' '}
            <span className="font-mono font-medium text-foreground">{eraseTarget}</span> abaixo.
          </p>
          <input
            type="text"
            value={eraseConfirm}
            onChange={(e) => setEraseConfirm(e.target.value)}
            placeholder={eraseTarget}
            className="w-full rounded-md border border-border px-3 py-2 text-sm"
            autoComplete="off"
          />
          {eraseMutation.isError && (
            <p className="text-sm text-destructive">
              Não foi possível excluir os dados. Tente novamente.
            </p>
          )}
          <div className="flex items-center justify-end gap-2">
            <Button variant="outline" onClick={() => setEraseOpen(false)}>
              Cancelar
            </Button>
            <Button
              className="bg-destructive text-white hover:bg-destructive/90"
              disabled={eraseConfirm.trim() !== eraseTarget || eraseMutation.isPending}
              onClick={() => eraseMutation.mutate()}
            >
              {eraseMutation.isPending ? 'Excluindo…' : 'Excluir definitivamente'}
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  )
}
