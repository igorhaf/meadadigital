'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { use, useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { AlertDialog } from '@/components/ui/alert-dialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, Section } from '@/components/ui/card'
import {
  createNote,
  deleteCompany,
  deleteNote,
  getCompany,
  getCompanyAdminEmail,
  impersonateCompany,
  listNotes,
  reactivateCompany,
  suspendCompany,
  type AdminNote,
  type CompanyDetail,
} from '@/lib/api/admin/companies'
import { getUsers } from '@/lib/api/admin/users'
import { ApiError } from '@/lib/api/client'
import { getProfile } from '@/lib/profiles/profile-type'

/** Formata um Instant ISO (ou null) em pt-BR; "—" quando ausente. */
function fmtDate(iso: string | null): string {
  return iso ? new Date(iso).toLocaleString('pt-BR') : '—'
}

/** Formata um limite nullable: null = "sem limite". */
function fmtLimit(n: number | null): string {
  return n == null ? 'sem limite' : String(n)
}

/**
 * Drill-down de uma empresa (camada 6.1). KPIs + plano + owner + notas internas, com ações
 * de lifecycle (suspender/reativar, editar, excluir). Toda ação destrutiva passa por
 * AlertDialog; o excluir exige digitar o nome da empresa (confirmText). Super-admin only —
 * a autorização é do backend (403 forbidden_not_super_admin → tratado inline).
 */
export default function CompanyDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params)
  const router = useRouter()
  const queryClient = useQueryClient()

  const [suspendOpen, setSuspendOpen] = useState(false)
  const [reactivateOpen, setReactivateOpen] = useState(false)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['company', id],
    queryFn: () => getCompany(id),
  })

  // email determinístico do tenant-admin (meada_{slug}_{token}@…) — login do tenant, pro root copiar.
  const adminEmailQuery = useQuery({
    queryKey: ['company-admin-email', id],
    queryFn: () => getCompanyAdminEmail(id),
  })

  // usuários (admins/operadores) desta empresa — lista no detalhe (endpoint /admin/users?companyId).
  const usersQuery = useQuery({
    queryKey: ['company-users', id],
    queryFn: () => getUsers({ companyId: id, pageSize: 100 }),
  })

  const suspendMut = useMutation({
    mutationFn: () => suspendCompany(id),
    onSuccess: () => {
      setSuspendOpen(false)
      queryClient.invalidateQueries({ queryKey: ['company', id] })
      queryClient.invalidateQueries({ queryKey: ['companies'] })
    },
    onError: (err) => {
      setActionError(
        err instanceof ApiError && err.reason === 'already_suspended'
          ? 'Esta empresa já está suspensa.'
          : 'Erro ao suspender. Tente novamente.',
      )
      setSuspendOpen(false)
    },
  })

  const reactivateMut = useMutation({
    mutationFn: () => reactivateCompany(id),
    onSuccess: () => {
      setReactivateOpen(false)
      queryClient.invalidateQueries({ queryKey: ['company', id] })
      queryClient.invalidateQueries({ queryKey: ['companies'] })
    },
    onError: (err) => {
      setActionError(
        err instanceof ApiError && err.reason === 'already_active'
          ? 'Esta empresa já está ativa.'
          : 'Erro ao reativar. Tente novamente.',
      )
      setReactivateOpen(false)
    },
  })

  // "Acessar admin" (impersonation): abre a nova aba JÁ no clique (síncrono — senão o
  // browser bloqueia o popup), depois aponta ela pro /auth/confirm com o token do magic link.
  // A nova aba abre no SUBDOMÍNIO do tenant ({slug}.dominio), NÃO no host do root — domínio
  // distinto = cookie de sessão separado, então a sessão do super-admin NÃO é derrubada.
  const impersonateMut = useMutation({
    mutationFn: async () => {
      const win = window.open('about:blank', '_blank')
      try {
        const { tokenHash, slug } = await impersonateCompany(id)
        // base do domínio atual (meadadigital.local[:porta]) → {slug}.base[:porta]
        const { protocol, host } = window.location
        const hostname = host.split(':')[0]
        const port = host.split(':')[1]
        const parts = hostname.split('.')
        const base = parts.length <= 2 ? hostname : parts.slice(1).join('.')
        const tenantHost = `${slug}.${base}${port ? `:${port}` : ''}`
        const url = `${protocol}//${tenantHost}/auth/confirm?token_hash=${encodeURIComponent(tokenHash)}&type=email&next=/dashboard`
        if (win) win.location.href = url
        else window.open(url, '_blank') // fallback se o popup inicial falhou
      } catch (e) {
        if (win) win.close()
        throw e
      }
    },
    onError: (err) => {
      setActionError(
        err instanceof ApiError && err.reason === 'company_has_no_admin'
          ? 'Esta empresa não tem um admin para acessar.'
          : err instanceof ApiError && err.reason === 'impersonation_unavailable'
            ? 'Acesso como empresa não está disponível neste ambiente.'
            : 'Erro ao acessar o admin da empresa. Tente novamente.',
      )
    },
  })

  const deleteMut = useMutation({
    mutationFn: () => deleteCompany(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['companies'] })
      router.push('/dashboard/companies')
    },
    onError: () => {
      setActionError('Erro ao excluir a empresa. Tente novamente.')
      setDeleteOpen(false)
    },
  })

  if (isError && error instanceof ApiError && error.status === 403) {
    return (
      <div className="space-y-6">
        <PageHeader title="Acesso restrito" description="Esta área é restrita ao super-admin." />
        <Link href="/dashboard">
          <Button variant="outline">Voltar ao dashboard</Button>
        </Link>
      </div>
    )
  }

  if (isError) {
    const notFound = error instanceof ApiError && error.status === 404
    return (
      <div className="space-y-6">
        <PageHeader
          title={notFound ? 'Empresa não encontrada' : 'Empresa'}
          breadcrumb={[{ label: 'Empresas', href: '/dashboard/companies' }, { label: '—' }]}
        />
        <p className="text-sm text-destructive">
          {notFound ? 'Esta empresa não existe ou foi removida.' : 'Erro ao carregar a empresa.'}
        </p>
        <Link href="/dashboard/companies">
          <Button variant="outline">Voltar à lista</Button>
        </Link>
      </div>
    )
  }

  if (isPending || !data) {
    return (
      <div className="space-y-6">
        <PageHeader
          title="Carregando…"
          breadcrumb={[{ label: 'Empresas', href: '/dashboard/companies' }, { label: '…' }]}
        />
      </div>
    )
  }

  const company: CompanyDetail = data
  const isActive = company.status === 'active'

  return (
    <div className="space-y-6">
      <PageHeader
        title={company.name}
        breadcrumb={[{ label: 'Empresas', href: '/dashboard/companies' }, { label: company.name }]}
        actions={
          <>
            <Link href={`/dashboard/companies/${id}/edit`}>
              <Button variant="outline">Editar</Button>
            </Link>
            {isActive && (
              <Button onClick={() => impersonateMut.mutate()} disabled={impersonateMut.isPending}>
                {impersonateMut.isPending ? 'Abrindo…' : 'Acessar admin'}
              </Button>
            )}
            {adminEmailQuery.data?.adminEmail && (
              <span
                className="self-center text-xs text-muted-foreground"
                title="Login do tenant-admin (clique para copiar)"
                onClick={() => navigator.clipboard?.writeText(adminEmailQuery.data!.adminEmail)}
                role="button"
              >
                👤 {adminEmailQuery.data.adminEmail}
              </span>
            )}
            {isActive ? (
              <Button variant="outline" onClick={() => setSuspendOpen(true)}>
                Suspender
              </Button>
            ) : (
              <Button variant="outline" onClick={() => setReactivateOpen(true)}>
                Reativar
              </Button>
            )}
            <Button variant="destructive" onClick={() => setDeleteOpen(true)}>
              Excluir
            </Button>
          </>
        }
      />

      <div className="flex items-center gap-2">
        <Badge variant={isActive ? 'success' : 'danger'}>{isActive ? 'ativa' : 'suspensa'}</Badge>
        {/* Perfil (produto) do tenant — camada 7.0. */}
        <Badge variant="info">
          {getProfile(company.profileId)?.productName ?? company.profileId}
        </Badge>
        <span className="font-mono text-xs text-muted-foreground">{company.slug}</span>
      </div>

      {actionError && <p className="text-sm text-destructive">{actionError}</p>}

      {/* KPIs */}
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Kpi label="Usuários" value={company.usersCount} />
        <Kpi label="Contatos" value={company.contactsCount} />
        <Kpi label="Conversas abertas" value={company.openConversations} />
        <Kpi label="Mensagens (30d)" value={company.messagesLast30d} />
      </div>

      <Card>
        <Section title="Plano e limites">
          <dl className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <Field label="Admins" value={fmtLimit(company.maxAdmins)} />
            <Field label="FAQs" value={fmtLimit(company.maxFaqs)} />
            <Field label="Conversas/mês" value={fmtLimit(company.maxConversationsMonth)} />
            <Field label="Paleta" value={company.paletteId} />
            <Field label="Criada em" value={fmtDate(company.createdAt)} />
            <Field label="Última atividade" value={fmtDate(company.lastActivityAt)} />
          </dl>
        </Section>
      </Card>

      <Card>
        <Section title="Owner" description="Responsável pela empresa (companies.owner_id).">
          {company.ownerEmail ? (
            <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <Field label="Nome" value={company.ownerName ?? '—'} />
              <Field label="Email" value={company.ownerEmail} />
            </dl>
          ) : (
            <p className="text-sm text-muted-foreground">Nenhum owner definido.</p>
          )}
        </Section>
      </Card>

      {/* Usuários da empresa (admins/operadores). Lista via /admin/users?companyId. */}
      <Card>
        <Section
          title="Usuários"
          description="Admins e operadores com acesso ao painel desta empresa."
        >
          {usersQuery.isPending ? (
            <p className="text-sm text-muted-foreground">Carregando…</p>
          ) : usersQuery.isError ? (
            <p className="text-sm text-destructive">Erro ao carregar os usuários.</p>
          ) : (usersQuery.data?.items.length ?? 0) === 0 ? (
            <p className="text-sm text-muted-foreground">
              Nenhum usuário cadastrado nesta empresa.
            </p>
          ) : (
            <div className="divide-y divide-border rounded-lg border border-border">
              {usersQuery.data!.items.map((u) => (
                <div key={u.id} className="flex flex-wrap items-center gap-3 px-4 py-3 text-sm">
                  <span
                    className="min-w-0 flex-1 truncate font-medium"
                    title="Clique para copiar o email"
                    role="button"
                    onClick={() => navigator.clipboard?.writeText(u.email)}
                  >
                    {u.email}
                  </span>
                  <Badge variant="muted">{u.role}</Badge>
                  <Badge variant={u.suspended ? 'danger' : 'success'}>
                    {u.suspended ? 'suspenso' : 'ativo'}
                  </Badge>
                  <span className="text-xs text-muted-foreground">
                    {u.lastLoginAt
                      ? `último acesso ${new Date(u.lastLoginAt).toLocaleDateString('pt-BR')}`
                      : 'nunca acessou'}
                  </span>
                  <Link
                    href={`/dashboard/users/${u.id}`}
                    className="text-xs text-primary hover:underline"
                  >
                    ver
                  </Link>
                </div>
              ))}
            </div>
          )}
        </Section>
      </Card>

      <NotesSection companyId={id} />

      <AlertDialog
        open={suspendOpen}
        onOpenChange={setSuspendOpen}
        title="Suspender empresa"
        description="A empresa ficará suspensa. Os logins e o atendimento são bloqueados até a reativação."
        confirmLabel="Suspender"
        loading={suspendMut.isPending}
        onConfirm={() => suspendMut.mutate()}
      />

      <AlertDialog
        open={reactivateOpen}
        onOpenChange={setReactivateOpen}
        title="Reativar empresa"
        description="A empresa volta ao status ativo."
        confirmLabel="Reativar"
        destructive={false}
        loading={reactivateMut.isPending}
        onConfirm={() => reactivateMut.mutate()}
      />

      <AlertDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title="Excluir empresa"
        description="Esta ação é IRREVERSÍVEL. Todos os dados da empresa (usuários, contatos, conversas, mensagens, FAQs, etc.) serão apagados permanentemente."
        confirmLabel="Excluir definitivamente"
        confirmText={company.name}
        loading={deleteMut.isPending}
        onConfirm={() => deleteMut.mutate()}
      />
    </div>
  )
}

function Kpi({ label, value }: { label: string; value: number }) {
  return (
    <Card className="p-4">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="mt-1 text-2xl font-semibold text-foreground">{value}</p>
    </Card>
  )
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="mt-0.5 text-sm text-foreground">{value}</dd>
    </div>
  )
}

/** Bloco "Notas internas": lista + textarea inline para adicionar + apagar por nota. */
function NotesSection({ companyId }: { companyId: string }) {
  const queryClient = useQueryClient()
  const [draft, setDraft] = useState('')
  const [noteToDelete, setNoteToDelete] = useState<AdminNote | null>(null)

  const { data: notes, isPending } = useQuery({
    queryKey: ['company-notes', companyId],
    queryFn: () => listNotes(companyId),
  })

  const createMut = useMutation({
    mutationFn: (content: string) => createNote(companyId, content),
    onSuccess: () => {
      setDraft('')
      queryClient.invalidateQueries({ queryKey: ['company-notes', companyId] })
    },
  })

  const deleteMut = useMutation({
    mutationFn: (noteId: string) => deleteNote(companyId, noteId),
    onSuccess: () => {
      setNoteToDelete(null)
      queryClient.invalidateQueries({ queryKey: ['company-notes', companyId] })
    },
  })

  return (
    <Card>
      <Section
        title="Notas internas"
        description="Visíveis só ao super-admin — o tenant nunca as vê."
      >
        <div className="space-y-2">
          <textarea
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            placeholder="Escreva uma nota interna…"
            rows={3}
            maxLength={5000}
            className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
          />
          <div className="flex justify-end">
            <Button
              size="sm"
              disabled={!draft.trim() || createMut.isPending}
              onClick={() => createMut.mutate(draft.trim())}
            >
              {createMut.isPending ? 'Salvando…' : 'Adicionar nota'}
            </Button>
          </div>
        </div>

        <div className="space-y-3">
          {isPending ? (
            <p className="text-sm text-muted-foreground">Carregando notas…</p>
          ) : !notes || notes.length === 0 ? (
            <p className="text-sm text-muted-foreground">Nenhuma nota ainda.</p>
          ) : (
            notes.map((note) => (
              <div key={note.id} className="rounded-md border border-border bg-background p-3">
                <div className="flex items-start justify-between gap-3">
                  <p className="text-sm whitespace-pre-wrap text-foreground">{note.content}</p>
                  <Button
                    variant="ghost"
                    size="sm"
                    className="shrink-0"
                    onClick={() => setNoteToDelete(note)}
                  >
                    Remover
                  </Button>
                </div>
                <p className="mt-2 text-xs text-muted-foreground">
                  Admin · {new Date(note.createdAt).toLocaleString('pt-BR')}
                </p>
              </div>
            ))
          )}
        </div>
      </Section>

      <AlertDialog
        open={noteToDelete !== null}
        onOpenChange={(open) => {
          if (!open) setNoteToDelete(null)
        }}
        title="Remover nota"
        description="A nota interna será apagada."
        confirmLabel="Remover"
        loading={deleteMut.isPending}
        onConfirm={() => {
          if (noteToDelete) deleteMut.mutate(noteToDelete.id)
        }}
      />
    </Card>
  )
}
