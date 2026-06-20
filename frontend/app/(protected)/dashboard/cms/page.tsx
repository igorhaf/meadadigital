'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import Link from 'next/link'
import { useEffect, useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { ApiError } from '@/lib/api/client'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, Section } from '@/components/ui/card'
import { CmsBlockCanvas } from '@/components/cms/cms-block-canvas'
import {
  createCmsPage,
  deleteCmsPage,
  getCmsSite,
  saveCmsPage,
  setCmsDomain,
  setCmsHome,
  setCmsPublished,
  setCmsTheme,
  startDomainVerification,
  verifyDomain,
  type CmsPage,
  type CmsSiteView,
} from '@/lib/api/cms'
import { type CmsBlock } from '@/lib/cms/cms-block-type'

function newId(): string {
  return 'b-' + Math.random().toString(36).slice(2, 10)
}

/**
 * Editor do CMS multi-página (SM-N). Atrás do gate de feature (403 feature_disabled). Gerencia:
 * - SITE: publicar, tema (cor + claro/escuro), domínio próprio + verificação de posse (TXT).
 * - PÁGINAS: criar/excluir/definir home, e por página: título, blocos (add/remover/reordenar
 *   drag-drop + ↑↓), publicar.
 */
export default function CmsEditorPage() {
  const qc = useQueryClient()
  const { data, isPending, isError, error } = useQuery<CmsSiteView>({ queryKey: ['cms-site'], queryFn: getCmsSite })

  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [title, setTitle] = useState('')
  const [blocks, setBlocks] = useState<CmsBlock[]>([])
  const [pagePublished, setPagePublished] = useState(false)
  const [savedAt, setSavedAt] = useState<string | null>(null)

  const [domain, setDomain] = useState('')
  const [domainError, setDomainError] = useState<string | null>(null)
  const [primaryColor, setPrimaryColor] = useState('#0f172a')
  const [dark, setDark] = useState(false)

  const [newSlug, setNewSlug] = useState('')
  const [newTitle, setNewTitle] = useState('')
  const [createError, setCreateError] = useState<string | null>(null)

  const site = data?.site
  const pages = data?.pages ?? []
  const selected = pages.find((p) => p.id === selectedId) ?? null

  // sincroniza estado do site quando carrega.
  useEffect(() => {
    if (site) {
      setDomain(site.domain ?? '')
      setPrimaryColor(site.theme?.primaryColor ?? '#0f172a')
      setDark(site.theme?.dark === true)
    }
  }, [site])

  // seleciona a 1ª página (home preferida) ao carregar.
  useEffect(() => {
    if (pages.length > 0 && (selectedId === null || !pages.some((p) => p.id === selectedId))) {
      const home = pages.find((p) => p.isHome) ?? pages[0]
      setSelectedId(home.id)
    }
  }, [pages, selectedId])

  // carrega o conteúdo da página selecionada no editor.
  useEffect(() => {
    if (selected) {
      setTitle(selected.title)
      setBlocks(selected.blocks ?? [])
      setPagePublished(selected.published)
    }
  }, [selectedId]) // eslint-disable-line react-hooks/exhaustive-deps

  const savePageMut = useMutation({
    mutationFn: () => {
      if (!selectedId) throw new Error('sem página')
      return saveCmsPage(selectedId, { title, blocks, published: pagePublished })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['cms-site'] })
      setSavedAt(new Date().toLocaleTimeString('pt-BR'))
    },
  })
  const createPageMut = useMutation({
    mutationFn: () => createCmsPage(newSlug, newTitle),
    onSuccess: (p: CmsPage) => {
      qc.invalidateQueries({ queryKey: ['cms-site'] })
      setSelectedId(p.id); setNewSlug(''); setNewTitle(''); setCreateError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'page_slug_taken') setCreateError('Já existe uma página com esse endereço.')
      else if (e instanceof ApiError && e.reason === 'invalid_page_slug') setCreateError('Endereço inválido (use letras, números e hífen).')
      else if (e instanceof ApiError && e.reason === 'too_many_pages') setCreateError('Limite de páginas atingido.')
      else setCreateError('Erro ao criar a página.')
    },
  })
  const deletePageMut = useMutation({
    mutationFn: (id: string) => deleteCmsPage(id),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['cms-site'] }); setSelectedId(null) },
  })
  const setHomeMut = useMutation({
    mutationFn: (id: string) => setCmsHome(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['cms-site'] }),
  })
  const publishMut = useMutation({
    mutationFn: (p: boolean) => setCmsPublished(p),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['cms-site'] }),
  })
  const themeMut = useMutation({
    mutationFn: () => setCmsTheme({ primaryColor, dark }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['cms-site'] }),
  })
  const domainMut = useMutation({
    mutationFn: () => setCmsDomain(domain.trim() === '' ? null : domain.trim()),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['cms-site'] }); setDomainError(null) },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'domain_taken') setDomainError('Esse domínio já está em uso.')
      else if (e instanceof ApiError && e.reason === 'invalid_domain') setDomainError('Domínio inválido (ex.: minhaempresa.com.br).')
      else setDomainError('Erro ao salvar o domínio.')
    },
  })
  const verifyStartMut = useMutation({ mutationFn: () => startDomainVerification(), onSuccess: () => qc.invalidateQueries({ queryKey: ['cms-site'] }) })
  const verifyMut = useMutation({ mutationFn: () => verifyDomain(), onSuccess: () => qc.invalidateQueries({ queryKey: ['cms-site'] }) })

  if (isError && error instanceof ApiError && error.reason === 'feature_disabled') {
    return (
      <div className="space-y-6">
        <PageHeader title="Site" description="Este recurso não está habilitado para o seu plano." />
        <Link href="/dashboard"><Button variant="outline">Voltar ao dashboard</Button></Link>
      </div>
    )
  }


  return (
    <div className="space-y-6">
      <PageHeader
        title="Site"
        description="Monte as páginas públicas do seu negócio com blocos. Publique o site e cada página."
        actions={
          site && (
            <Button disabled={publishMut.isPending} onClick={() => publishMut.mutate(!site.published)}>
              {site.published ? 'Despublicar site' : 'Publicar site'}
            </Button>
          )
        }
      />

      {isPending || !site ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : (
        <div className="space-y-6">
          <div className="flex flex-wrap items-center gap-3">
            <Badge variant={site.published ? 'success' : 'muted'}>{site.published ? 'site publicado' : 'site em rascunho'}</Badge>
            {site.published && (
              <a href={`/p/${site.slug}`} target="_blank" rel="noopener noreferrer" className="text-xs underline">
                ver em /p/{site.slug}
              </a>
            )}
          </div>

          {/* Páginas */}
          <Card>
            <Section title="Páginas">
              <div className="flex flex-wrap gap-2">
                {pages.map((p) => (
                  <button key={p.id} onClick={() => setSelectedId(p.id)}
                    className={'rounded-md border px-3 py-1.5 text-sm ' + (p.id === selectedId ? 'border-primary bg-primary/10' : 'border-border')}>
                    {p.title || p.pageSlug}
                    {p.isHome && <span className="ml-1 text-xs text-muted-foreground">(home)</span>}
                    {!p.published && <span className="ml-1 text-xs text-amber-600">rascunho</span>}
                  </button>
                ))}
              </div>
              <div className="mt-4 flex flex-wrap items-end gap-2 border-t border-border pt-4">
                <div>
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">Endereço (slug)</label>
                  <input value={newSlug} onChange={(e) => setNewSlug(e.target.value)} placeholder="servicos"
                    className="rounded-md border border-border bg-background px-3 py-2 text-sm" />
                </div>
                <div>
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">Título</label>
                  <input value={newTitle} onChange={(e) => setNewTitle(e.target.value)} placeholder="Serviços"
                    className="rounded-md border border-border bg-background px-3 py-2 text-sm" />
                </div>
                <Button type="button" variant="outline" disabled={createPageMut.isPending || !newSlug.trim()}
                  onClick={() => createPageMut.mutate()}>Nova página</Button>
              </div>
              {createError && <p className="mt-2 text-sm text-destructive">{createError}</p>}
            </Section>
          </Card>

          {/* Editor da página selecionada */}
          {selected && (
            <Card>
              <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
                <div className="flex items-center gap-2">
                  <span className="font-medium">Editando: {selected.pageSlug}</span>
                  {selected.isHome && <Badge variant="info">home</Badge>}
                </div>
                <div className="flex items-center gap-2">
                  {!selected.isHome && (
                    <Button type="button" variant="outline" className="h-8 px-3 text-xs" disabled={setHomeMut.isPending}
                      onClick={() => setHomeMut.mutate(selected.id)}>Definir como home</Button>
                  )}
                  <label className="flex items-center gap-1 text-xs text-muted-foreground">
                    <input type="checkbox" checked={pagePublished} onChange={(e) => setPagePublished(e.target.checked)} /> publicada
                  </label>
                  <Button type="button" variant="outline" className="h-8 px-3 text-xs" disabled={deletePageMut.isPending}
                    onClick={() => deletePageMut.mutate(selected.id)}>Excluir página</Button>
                </div>
              </div>

              <Section title="Título da página">
                <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Título"
                  className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
              </Section>

              <div className="mt-4 space-y-3">
                <span className="text-sm font-medium">Blocos</span>
                <CmsBlockCanvas
                  blocks={blocks}
                  setBlocks={setBlocks}
                  theme={{ primaryColor, dark }}
                  newId={newId}
                />
                <div className="flex items-center gap-3">
                  <Button disabled={savePageMut.isPending} onClick={() => savePageMut.mutate()}>
                    {savePageMut.isPending ? 'Salvando…' : 'Salvar página'}
                  </Button>
                  {savedAt && <span className="text-xs text-muted-foreground">Salvo às {savedAt}</span>}
                </div>
              </div>
            </Card>
          )}

          {/* Tema */}
          <Card>
            <Section title="Tema">
              <div className="flex flex-wrap items-end gap-4">
                <div>
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">Cor primária</label>
                  <input type="color" value={primaryColor} onChange={(e) => setPrimaryColor(e.target.value)}
                    className="h-9 w-16 rounded-md border border-border bg-background" />
                </div>
                <label className="flex items-center gap-2 text-sm">
                  <input type="checkbox" checked={dark} onChange={(e) => setDark(e.target.checked)} /> Fundo escuro
                </label>
                <Button type="button" variant="outline" disabled={themeMut.isPending} onClick={() => themeMut.mutate()}>Salvar tema</Button>
              </div>
            </Section>
          </Card>

          {/* Domínio + verificação */}
          <Card>
            <Section title="Domínio próprio (opcional)">
              <div className="flex flex-wrap items-end gap-2">
                <div className="flex-1 min-w-[14rem]">
                  <input value={domain} onChange={(e) => setDomain(e.target.value)} placeholder="minhaempresa.com.br"
                    className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
                </div>
                <Button type="button" variant="outline" disabled={domainMut.isPending} onClick={() => domainMut.mutate()}>Salvar domínio</Button>
              </div>
              {domainError && <p className="mt-2 text-sm text-destructive">{domainError}</p>}

              {site.domain && (
                <div className="mt-4 space-y-2 border-t border-border pt-4">
                  <div className="flex items-center gap-2">
                    <span className="text-sm">Verificação de posse:</span>
                    <Badge variant={site.domainVerified ? 'success' : 'muted'}>{site.domainVerified ? 'verificado' : 'não verificado'}</Badge>
                  </div>
                  {!site.domainVerified && (
                    <>
                      {site.verifyToken ? (
                        <p className="text-xs text-muted-foreground">
                          Crie um registro <strong>TXT</strong> no DNS de <span className="font-mono">{site.domain}</span> com o valor:{' '}
                          <code className="rounded bg-muted px-1">_meada-verify={site.verifyToken}</code>, depois clique em Verificar.
                        </p>
                      ) : (
                        <Button type="button" variant="outline" className="h-8 px-3 text-xs" disabled={verifyStartMut.isPending}
                          onClick={() => verifyStartMut.mutate()}>Gerar token de verificação</Button>
                      )}
                      {site.verifyToken && (
                        <Button type="button" variant="outline" className="h-8 px-3 text-xs" disabled={verifyMut.isPending}
                          onClick={() => verifyMut.mutate()}>{verifyMut.isPending ? 'Verificando…' : 'Verificar agora'}</Button>
                      )}
                    </>
                  )}
                  <p className="text-xs text-muted-foreground">
                    Após verificar, aponte o domínio para o nosso servidor. O certificado HTTPS é emitido automaticamente quando o domínio responde por aqui.
                  </p>
                </div>
              )}
            </Section>
          </Card>
        </div>
      )}
    </div>
  )
}
