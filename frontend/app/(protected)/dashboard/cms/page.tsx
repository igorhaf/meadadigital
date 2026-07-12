'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Menu, MessagesSquare, Settings, X } from 'lucide-react'
import Link from 'next/link'
import { useEffect, useState } from 'react'

import { cmsShellStyle, RowSection } from '@/components/cms/cms-render'
import {
  addBlockToColumn,
  addColumn,
  addRow,
  moveBlockAcross,
  moveBlockWithin,
  moveColumn,
  moveRow,
  removeBlock,
  removeColumn,
  removeRow,
  reorderColumn,
  reorderRow,
  setColumnWidth,
  updateBlockProps,
  updateRowProps,
} from '@/components/cms/explorer/tree-ops'
import { TreePanel, type Selection } from '@/components/cms/explorer/tree-panel'
import { FieldRenderer } from '@/components/cms/field-renderer'
import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Section } from '@/components/ui/card'
import { Modal } from '@/components/ui/modal'
import { ApiError } from '@/lib/api/client'
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
import { getMe } from '@/lib/api/me'
import { allBlockSchemas, blockSchema } from '@/lib/cms/cms-block-schemas'
import { BLOCK_SLOTS, slotsForType, validateSlotKeys } from '@/lib/cms/cms-block-slots'
import type {
  CmsBlock,
  CmsBlockTypeId,
  CmsColumnWidth,
  CmsRow,
  CmsRowProps,
} from '@/lib/cms/cms-block-type'
import { columnSchema, rowSchema } from '@/lib/cms/container-schemas'
import { addItem, getItems, moveItem, removeItem, updateItem } from '@/lib/cms/repeater-ops'
import {
  pageTemplateById,
  TEMPLATE_CATEGORIES,
  templatesForProfile,
} from '@/lib/cms/templates/page-templates'
import { recommendedArchetypes, themesForProfile } from '@/lib/cms/themes/theme-catalog'
import { useCmsBack } from '@/lib/cms/use-cms-return'
import { useOnSync } from '@/lib/use-synced-form'
import { cn } from '@/lib/utils'

/** Alvo pendente do catálogo: ao escolher um bloco, cria uma coluna na linha (kind 'column') ou
 * empilha um bloco na coluna (kind 'block'). */
type CatalogTarget =
  { kind: 'column'; rowId: string } | { kind: 'block'; rowId: string; colId: string } | null

/**
 * Editor do CMS multi-página — TELA CHEIA, page builder ESTRUTURAL (árvore root → linhas → colunas →
 * blocos). O AppShell esconde o shell admin nas rotas /dashboard/cms; aqui montamos o editor próprio:
 * topbar (logo Meada + Voltar + seletor de página + Configurações + Salvar + Publicar), painel ESQUERDO
 * "explorer" (árvore de linhas/colunas/blocos, ↑↓✕ por nó), PREVIEW central (linhas reais via
 * RowSection), e painel DIREITO de propriedades em 3 modos (linha/coluna/bloco). Configs do site num
 * MODAL. A árvore persiste no campo `blocks` (agora CmsRow[]); o flat legado é normalizado na leitura.
 */
export default function CmsEditorPage() {
  const qc = useQueryClient()
  const back = useCmsBack()
  const { data, isPending, isError, error } = useQuery<CmsSiteView>({
    queryKey: ['cms-site'],
    queryFn: getCmsSite,
  })

  const [selectedId, setSelectedId] = useState<string | null>(null) // página selecionada
  const [title, setTitle] = useState('')
  const [tree, setTree] = useState<CmsRow[]>([])
  const [pagePublished, setPagePublished] = useState(false)
  const [savedAt, setSavedAt] = useState<string | null>(null)
  const [saveError, setSaveError] = useState<string | null>(null)

  // estado do EDITOR (tela-cheia)
  const [selection, setSelection] = useState<Selection>(null) // nó selecionado (linha/coluna/bloco)
  const [expanded, setExpanded] = useState<Set<string>>(new Set())
  const [leftOpen, setLeftOpen] = useState(true)
  const [catalogTarget, setCatalogTarget] = useState<CatalogTarget>(null)
  const [settingsOpen, setSettingsOpen] = useState(false)

  const [domain, setDomain] = useState('')
  const [domainError, setDomainError] = useState<string | null>(null)
  const [primaryColor, setPrimaryColor] = useState('#0f172a')
  const [dark, setDark] = useState(false)
  const [preset, setPreset] = useState<'' | 'meada-dark'>('')
  const [themeId, setThemeId] = useState<string>('') // '' = não usa catálogo (preset/genérico)

  // perfil do tenant: define quais 10 temas do catálogo aparecem (afinidade por nicho).
  const meQuery = useQuery({ queryKey: ['me'], queryFn: getMe })
  const profileId = meQuery.data?.profileId ?? 'generic'
  const catalogThemes = themesForProfile(profileId)
  const recommended = recommendedArchetypes(profileId)

  const [newSlug, setNewSlug] = useState('')
  const [newTitle, setNewTitle] = useState('')
  const [newTemplate, setNewTemplate] = useState<string>('landing') // template de estrutura inicial
  const [createError, setCreateError] = useState<string | null>(null)

  const site = data?.site
  const pages = data?.pages ?? []
  const selected = pages.find((p) => p.id === selectedId) ?? null

  // sincroniza estado do site quando carrega.
  useOnSync(site, (s) => {
    setDomain(s.domain ?? '')
    setPrimaryColor(s.theme?.primaryColor ?? '#0f172a')
    setDark(s.theme?.dark === true)
    setPreset(s.theme?.preset === 'meada-dark' ? 'meada-dark' : '')
    setThemeId(s.theme?.themeId ?? '')
  })

  // seleciona a 1ª página (home preferida) ao carregar — ajuste de estado durante o render
  // (padrão React de estado derivado; roda de novo imediatamente com o novo selectedId).
  if (pages.length > 0 && (selectedId === null || !pages.some((p) => p.id === selectedId))) {
    const home = pages.find((p) => p.isHome) ?? pages[0]
    setSelectedId(home.id)
  }

  // carrega o conteúdo da página selecionada no editor; zera seleção e expande todas as linhas (troca de página).
  useOnSync(selectedId, () => {
    if (selected) {
      const rows = selected.blocks ?? []
      setTitle(selected.title)
      setTree(rows)
      setPagePublished(selected.published)
      setExpanded(new Set(rows.map((r) => r.id)))
    }
    setSelection(null)
  })

  // se o nó selecionado sumiu da árvore atual, fecha o painel direito (ajuste durante o render:
  // setSelection(null) só dispara quando a seleção ficou inválida — um re-render e converge).
  const selectionValida = (): boolean => {
    if (!selection) return true
    const row = tree.find((r) => r.id === selection.rowId)
    if (!row) return false
    if (selection.kind === 'row') return true
    const col = row.columns.find((c) => c.id === selection.colId)
    if (!col) return false
    if (selection.kind === 'column') return true
    const block = col.blocks.find((b) => b.id === selection.blockId)
    if (!block) return false
    if (selection.kind === 'block') return true
    if (selection.kind === 'slot') {
      // slotId tem que ser um slot válido do tipo do bloco.
      return slotsForType(block.type).some((s) => s.id === selection.slotId)
    }
    // kind === 'item': o fieldKey tem que ser um repeater do schema E o índice existir no array.
    const items = getItems(block.props as Record<string, unknown>, selection.fieldKey)
    const isRepeater = blockSchema(block.type)?.fields.some(
      (f) => f.key === selection.fieldKey && f.type === 'repeater',
    )
    return !!isRepeater && selection.itemIndex >= 0 && selection.itemIndex < items.length
  }
  if (!selectionValida()) setSelection(null)

  // Escape fecha o painel direito.
  useEffect(() => {
    if (!selection) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setSelection(null)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [selection])

  // DEV: salvaguarda contra "prop fantasma" nos SlotDefs — sem test runner no front, valida em runtime
  // (uma vez) que os keys de cada slot existem no schema e são disjuntos. Some no build de produção.
  useEffect(() => {
    if (process.env.NODE_ENV === 'production') return
    for (const type of Object.keys(BLOCK_SLOTS)) {
      const fields = blockSchema(type)?.fields ?? []
      const problems = validateSlotKeys(
        type,
        fields.map((f) => f.key),
      )
      if (problems.length) console.warn('[CMS slots] problemas de cobertura:', problems)
    }
  }, [])

  const savePageMut = useMutation({
    mutationFn: () => {
      if (!selectedId) throw new Error('sem página')
      return saveCmsPage(selectedId, { title, blocks: tree, published: pagePublished })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['cms-site'] })
      setSavedAt(new Date().toLocaleTimeString('pt-BR'))
      setSaveError(null)
    },
    onError: (e) => {
      // Falha silenciosa deixava o tenant achando que salvou. invalid_blocks = limites do
      // backend (30 linhas / 6 colunas / 50 blocos por página).
      setSavedAt(null)
      setSaveError(
        e instanceof ApiError && e.reason === 'invalid_blocks'
          ? 'Página excede os limites do editor (linhas/colunas/blocos). Reduza e salve de novo.'
          : 'Erro ao salvar a página. Tente novamente.',
      )
    },
  })
  const createPageMut = useMutation({
    // cria a página e, se um template de estrutura foi escolhido, grava os blocos do template nela.
    mutationFn: async () => {
      const p = await createCmsPage(newSlug, newTitle)
      const tpl = pageTemplateById(newTemplate)
      if (tpl && tpl.id !== 'blank') {
        const blocks = tpl.build()
        if (blocks.length > 0) await saveCmsPage(p.id, { blocks })
      }
      return p
    },
    onSuccess: (p: CmsPage) => {
      qc.invalidateQueries({ queryKey: ['cms-site'] })
      setSelectedId(p.id)
      setNewSlug('')
      setNewTitle('')
      setNewTemplate('landing')
      setCreateError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'page_slug_taken')
        setCreateError('Já existe uma página com esse endereço.')
      else if (e instanceof ApiError && e.reason === 'invalid_page_slug')
        setCreateError('Endereço inválido (use letras, números e hífen).')
      else if (e instanceof ApiError && e.reason === 'too_many_pages')
        setCreateError('Limite de páginas atingido.')
      else setCreateError('Erro ao criar a página.')
    },
  })
  const deletePageMut = useMutation({
    mutationFn: (id: string) => deleteCmsPage(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['cms-site'] })
      setSelectedId(null)
    },
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
    // themeId (catálogo) tem precedência; se setado, vai sozinho. Senão, preset/genérico.
    mutationFn: () =>
      themeId
        ? setCmsTheme({ themeId })
        : setCmsTheme({ primaryColor, dark, preset: preset || undefined }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['cms-site'] }),
  })
  const domainMut = useMutation({
    mutationFn: () => setCmsDomain(domain.trim() === '' ? null : domain.trim()),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['cms-site'] })
      setDomainError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'domain_taken')
        setDomainError('Esse domínio já está em uso.')
      else if (e instanceof ApiError && e.reason === 'invalid_domain')
        setDomainError('Domínio inválido (ex.: minhaempresa.com.br).')
      else setDomainError('Erro ao salvar o domínio.')
    },
  })
  const verifyStartMut = useMutation({
    mutationFn: () => startDomainVerification(),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['cms-site'] }),
  })
  const verifyMut = useMutation({
    mutationFn: () => verifyDomain(),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['cms-site'] }),
  })

  // ---- ações da árvore (linha / coluna / bloco) ----
  function toggleRow(rowId: string) {
    setExpanded((s) => {
      const next = new Set(s)
      if (next.has(rowId)) next.delete(rowId)
      else next.add(rowId)
      return next
    })
  }
  function handleAddRow() {
    setTree((t) => {
      const next = addRow(t)
      const created = next[next.length - 1]
      setExpanded((s) => new Set(s).add(created.id))
      setSelection({ kind: 'row', rowId: created.id })
      return next
    })
  }
  function handleMoveRow(rowId: string, dir: -1 | 1) {
    setTree((t) => moveRow(t, rowId, dir))
  }
  function handleRemoveRow(rowId: string) {
    setTree((t) => removeRow(t, rowId))
    if (selection?.rowId === rowId) setSelection(null)
  }
  function handleMoveColumn(rowId: string, colId: string, dir: -1 | 1) {
    setTree((t) => moveColumn(t, rowId, colId, dir))
  }
  function handleRemoveColumn(rowId: string, colId: string) {
    setTree((t) => removeColumn(t, rowId, colId))
    if (selection && 'colId' in selection && selection.colId === colId) setSelection(null)
  }
  function handleMoveBlock(rowId: string, colId: string, blockId: string, dir: -1 | 1) {
    setTree((t) => moveBlockWithin(t, rowId, colId, blockId, dir))
  }
  function handleRemoveBlock(rowId: string, colId: string, blockId: string) {
    setTree((t) => removeBlock(t, rowId, colId, blockId))
    if (selection?.kind === 'block' && selection.blockId === blockId) setSelection(null)
  }

  // ---- itens de repeater (2ª família) — operam no array props[fieldKey] e gravam via updateBlockProps.
  function withBlock(
    rowId: string,
    colId: string,
    blockId: string,
    fn: (block: CmsBlock) => CmsBlock['props'],
  ): void {
    setTree((t) => {
      const block = t
        .find((r) => r.id === rowId)
        ?.columns.find((c) => c.id === colId)
        ?.blocks.find((b) => b.id === blockId)
      if (!block) return t
      return updateBlockProps(t, rowId, colId, blockId, fn(block))
    })
  }
  function repItemSchema(type: string, fieldKey: string) {
    return blockSchema(type)?.fields.find((f) => f.key === fieldKey)?.itemSchema
  }
  function handleAddItem(rowId: string, colId: string, blockId: string, fieldKey: string) {
    withBlock(rowId, colId, blockId, (block) => {
      const items = getItems(block.props as Record<string, unknown>, fieldKey)
      return {
        ...block.props,
        [fieldKey]: addItem(items, repItemSchema(block.type, fieldKey)),
      } as CmsBlock['props']
    })
  }
  function handleMoveItem(
    rowId: string,
    colId: string,
    blockId: string,
    fieldKey: string,
    itemIndex: number,
    dir: -1 | 1,
  ) {
    withBlock(rowId, colId, blockId, (block) => {
      const items = getItems(block.props as Record<string, unknown>, fieldKey)
      return { ...block.props, [fieldKey]: moveItem(items, itemIndex, dir) } as CmsBlock['props']
    })
  }
  function handleRemoveItem(
    rowId: string,
    colId: string,
    blockId: string,
    fieldKey: string,
    itemIndex: number,
  ) {
    withBlock(rowId, colId, blockId, (block) => {
      const items = getItems(block.props as Record<string, unknown>, fieldKey)
      return { ...block.props, [fieldKey]: removeItem(items, itemIndex) } as CmsBlock['props']
    })
    if (
      selection?.kind === 'item' &&
      selection.blockId === blockId &&
      selection.fieldKey === fieldKey &&
      selection.itemIndex === itemIndex
    )
      setSelection(null)
  }

  // catálogo escolhe o bloco e cria coluna (na linha) ou empilha bloco (na coluna).
  function pickFromCatalog(type: CmsBlockTypeId) {
    if (!catalogTarget) return
    if (catalogTarget.kind === 'column') {
      const rowId = catalogTarget.rowId
      setTree((t) => {
        const next = addColumn(t, rowId, type)
        const row = next.find((r) => r.id === rowId)
        const col = row?.columns[row.columns.length - 1]
        if (col) setSelection({ kind: 'column', rowId, colId: col.id })
        return next
      })
    } else {
      const { rowId, colId } = catalogTarget
      setTree((t) => {
        const next = addBlockToColumn(t, rowId, colId, type)
        const col = next.find((r) => r.id === rowId)?.columns.find((c) => c.id === colId)
        const block = col?.blocks[col.blocks.length - 1]
        if (block) setSelection({ kind: 'block', rowId, colId, blockId: block.id })
        return next
      })
    }
    setCatalogTarget(null)
  }

  // ---- estados de borda ----
  if (isError && error instanceof ApiError && error.reason === 'feature_disabled') {
    return (
      <div className="mx-auto max-w-3xl space-y-6 p-8">
        <PageHeader title="Site" description="Este recurso não está habilitado para o seu plano." />
        <Link href="/dashboard">
          <Button variant="outline">Voltar ao dashboard</Button>
        </Link>
      </div>
    )
  }
  if (isPending || !site) {
    return (
      <div className="flex h-full items-center justify-center text-sm text-muted-foreground">
        Carregando…
      </div>
    )
  }

  const shell = cmsShellStyle(
    themeId ? { themeId } : { primaryColor, dark, preset: preset || undefined },
  )

  // nós selecionados resolvidos a partir da árvore (pro painel direito).
  const selRow = selection ? (tree.find((r) => r.id === selection.rowId) ?? null) : null
  const selCol =
    selRow && selection && 'colId' in selection
      ? (selRow.columns.find((c) => c.id === selection.colId) ?? null)
      : null
  // selBlock resolve nos modos bloco/slot/item (slot e item vivem dentro de um bloco).
  const selBlock =
    selCol &&
    (selection?.kind === 'block' || selection?.kind === 'slot' || selection?.kind === 'item')
      ? (selCol.blocks.find((b) => b.id === selection.blockId) ?? null)
      : null
  const selBlockSchema = selBlock ? blockSchema(selBlock.type) : undefined
  // SlotDef selecionado (modo slot): qual partição de props o painel filtra.
  const selSlotDef =
    selBlock && selection?.kind === 'slot'
      ? (slotsForType(selBlock.type).find((s) => s.id === selection.slotId) ?? null)
      : null
  // Repeater-field selecionado (modo item): o FieldDef do repeater (tem itemSchema/itemLabel).
  const selItemField =
    selBlock && selection?.kind === 'item'
      ? (selBlockSchema?.fields.find(
          (f) => f.key === selection.fieldKey && f.type === 'repeater',
        ) ?? null)
      : null

  return (
    <div className="flex h-full flex-col">
      {/* ---- TOPBAR ---- */}
      <div className="flex shrink-0 items-center gap-3 border-b border-border px-3 py-2">
        {/* logo Meada — 2ª saída sempre disponível */}
        <Link
          href="/dashboard"
          className="flex items-center gap-2 text-sm font-semibold"
          aria-label="Ir para o dashboard"
        >
          <MessagesSquare className="size-5 text-primary" /> Meada
        </Link>
        <Button variant="ghost" size="sm" onClick={back}>
          ← Voltar
        </Button>
        <div className="h-5 w-px bg-border" />
        {/* seletor de página */}
        <select
          value={selectedId ?? ''}
          onChange={(e) => setSelectedId(e.target.value)}
          className="rounded-md border border-border bg-background px-2 py-1.5 text-sm"
        >
          {pages.map((p) => (
            <option key={p.id} value={p.id}>
              {p.title || p.pageSlug}
              {p.isHome ? ' (home)' : ''}
              {!p.published ? ' · rascunho' : ''}
            </option>
          ))}
        </select>
        {savedAt && <span className="text-xs text-muted-foreground">Salvo às {savedAt}</span>}
        {saveError && <span className="text-xs text-destructive">{saveError}</span>}

        <div className="ml-auto flex items-center gap-2">
          <Badge variant={site.published ? 'success' : 'muted'}>
            {site.published ? 'publicado' : 'rascunho'}
          </Badge>
          <Button variant="outline" size="sm" onClick={() => setSettingsOpen(true)}>
            <Settings className="size-4" /> Configurações
          </Button>
          <Button
            size="sm"
            disabled={savePageMut.isPending || !selected}
            onClick={() => savePageMut.mutate()}
          >
            {savePageMut.isPending ? 'Salvando…' : 'Salvar página'}
          </Button>
          <Button
            variant="outline"
            size="sm"
            disabled={publishMut.isPending}
            onClick={() => publishMut.mutate(!site.published)}
          >
            {site.published ? 'Despublicar' : 'Publicar site'}
          </Button>
        </div>
      </div>

      {/* ---- CORPO: explorer (push) + preview + propriedades (overlay) ---- */}
      <div className="relative flex min-h-0 flex-1">
        {/* ESQUERDO — explorer (empurra o preview) */}
        <aside
          className={cn(
            'shrink-0 overflow-hidden border-r border-border transition-[width] duration-200 ease-out',
            leftOpen ? 'w-72' : 'w-0 border-r-0',
          )}
        >
          <div className="h-full w-72">
            <TreePanel
              tree={tree}
              selection={selection}
              expanded={expanded}
              onSelect={setSelection}
              onToggle={toggleRow}
              onAddRow={handleAddRow}
              onMoveRow={handleMoveRow}
              onRemoveRow={handleRemoveRow}
              onAddColumn={(rowId) => setCatalogTarget({ kind: 'column', rowId })}
              onMoveColumn={handleMoveColumn}
              onRemoveColumn={handleRemoveColumn}
              onAddBlock={(rowId, colId) => setCatalogTarget({ kind: 'block', rowId, colId })}
              onMoveBlock={handleMoveBlock}
              onRemoveBlock={handleRemoveBlock}
              onReorderRow={(dragId, targetId) => setTree((t) => reorderRow(t, dragId, targetId))}
              onReorderColumn={(rowId, dragColId, targetColId) =>
                setTree((t) => reorderColumn(t, rowId, dragColId, targetColId))
              }
              onMoveBlockAcross={(from, to) => setTree((t) => moveBlockAcross(t, from, to))}
              onAddItem={handleAddItem}
              onMoveItem={handleMoveItem}
              onRemoveItem={handleRemoveItem}
            />
          </div>
        </aside>

        {/* abinha (clique-toggle) — acompanha a borda do aside esquerdo */}
        <button
          type="button"
          onClick={() => setLeftOpen((v) => !v)}
          aria-label={leftOpen ? 'Recolher explorer' : 'Abrir explorer'}
          className="absolute top-1/2 z-20 flex h-16 w-6 -translate-y-1/2 items-center justify-center rounded-r-xl border border-l-0 border-border bg-background text-muted-foreground shadow-md transition-[left] duration-200 hover:bg-muted hover:text-foreground"
          style={{ left: leftOpen ? '18rem' : '0' }}
        >
          {leftOpen ? <X className="size-4" /> : <Menu className="size-4" />}
        </button>

        {/* PREVIEW — clicar em área neutra (alvo = container) fecha o painel direito */}
        <div
          className="min-w-0 flex-1 overflow-auto"
          onClick={(e) => {
            if (e.target === e.currentTarget) setSelection(null)
          }}
        >
          {tree.length === 0 ? (
            <div className="flex h-full items-center justify-center text-sm text-muted-foreground">
              Adicione uma linha pela árvore à esquerda — o preview aparece aqui.
            </div>
          ) : (
            <div
              style={shell}
              onClick={(e) => {
                if (e.target === e.currentTarget) setSelection(null)
              }}
            >
              {tree.map((row) => (
                <RowSection
                  key={row.id}
                  row={row}
                  interactive={{
                    selectedRow: selection?.kind === 'row' && selection.rowId === row.id,
                    // contexto da linha vale tanto pro bloco quanto pro slot selecionado (ambos vivem na linha).
                    containsSelectedBlock:
                      (selection?.kind === 'block' || selection?.kind === 'slot') &&
                      selection.rowId === row.id,
                    selectedBlockId: selection?.kind === 'block' ? selection.blockId : null,
                    selectedSlotBlockId: selection?.kind === 'slot' ? selection.blockId : null,
                    selectedSlotId: selection?.kind === 'slot' ? selection.slotId : null,
                    onSelectRow: (rowId) => setSelection({ kind: 'row', rowId }),
                    onSelectBlock: (rowId, colId, blockId) =>
                      setSelection({ kind: 'block', rowId, colId, blockId }),
                  }}
                />
              ))}
            </div>
          )}
        </div>

        {/* DIREITO — propriedades (3 modos: linha / coluna / bloco) */}
        {selection && (selRow || selBlock) && (
          <aside className="absolute inset-y-0 right-0 z-40 flex w-[340px] flex-col overflow-y-auto border-l border-border bg-card shadow-xl">
            {/* MODO LINHA */}
            {selection.kind === 'row' && selRow && (
              <>
                <div className="flex items-center justify-between border-b border-border px-4 py-3">
                  <span className="flex items-center gap-2 font-medium">
                    <span aria-hidden>{rowSchema.emoji}</span> {rowSchema.label}
                  </span>
                  <button type="button" onClick={() => setSelection(null)} aria-label="Fechar">
                    <X className="size-4" />
                  </button>
                </div>
                <div className="space-y-3 p-4">
                  <p className="text-xs text-muted-foreground">
                    Layout da linha (seção) — colunas lado a lado, responsivo. No celular as colunas
                    empilham.
                  </p>
                  {rowSchema.fields.map((f) => (
                    <FieldRenderer
                      key={f.key}
                      field={f}
                      value={(selRow.props as Record<string, unknown>)[f.key]}
                      onChange={(v) =>
                        setTree((t) =>
                          updateRowProps(t, selRow.id, {
                            ...selRow.props,
                            [f.key]: v,
                          } as CmsRowProps),
                        )
                      }
                    />
                  ))}
                </div>
              </>
            )}

            {/* MODO COLUNA */}
            {selection.kind === 'column' && selCol && (
              <>
                <div className="flex items-center justify-between border-b border-border px-4 py-3">
                  <span className="flex items-center gap-2 font-medium">
                    <span aria-hidden>{columnSchema.emoji}</span> {columnSchema.label}
                  </span>
                  <button type="button" onClick={() => setSelection(null)} aria-label="Fechar">
                    <X className="size-4" />
                  </button>
                </div>
                <div className="space-y-3 p-4">
                  <p className="text-xs text-muted-foreground">
                    Largura da coluna no grid de 12. Some as larguras das colunas de uma linha pra
                    chegar a 12 (ex.: 6 + 6, 4 + 4 + 4).
                  </p>
                  {columnSchema.fields.map((f) => (
                    <FieldRenderer
                      key={f.key}
                      field={f}
                      value={typeof selCol.width === 'number' ? String(selCol.width) : ''}
                      onChange={(v) => {
                        const n = parseInt(String(v), 10)
                        const width: CmsColumnWidth = Number.isFinite(n)
                          ? Math.max(1, Math.min(12, n))
                          : 'auto'
                        setTree((t) => setColumnWidth(t, selRow!.id, selCol.id, width))
                      }}
                    />
                  ))}
                </div>
              </>
            )}

            {/* MODO BLOCO */}
            {selection.kind === 'block' && selBlock && selBlockSchema && (
              <>
                <div className="flex items-center justify-between border-b border-border px-4 py-3">
                  <span className="flex items-center gap-2 font-medium">
                    <span aria-hidden>{selBlockSchema.emoji}</span> {selBlockSchema.label}
                  </span>
                  <button type="button" onClick={() => setSelection(null)} aria-label="Fechar">
                    <X className="size-4" />
                  </button>
                </div>
                <div className="space-y-3 p-4">
                  <p className="text-xs text-muted-foreground">{selBlockSchema.description}</p>
                  {selBlockSchema.fields.map((f) => (
                    <FieldRenderer
                      key={f.key}
                      field={f}
                      value={(selBlock.props as Record<string, unknown>)[f.key]}
                      onChange={(v) =>
                        setTree((t) =>
                          updateBlockProps(t, selRow!.id, selCol!.id, selBlock.id, {
                            ...selBlock.props,
                            [f.key]: v,
                          } as CmsBlock['props']),
                        )
                      }
                    />
                  ))}
                </div>
              </>
            )}

            {/* MODO SLOT — sub-parte de um macro (ex. hero). Mostra SÓ os campos do slot, editando as
                MESMAS props via a MESMA tree-op. O pai (modo bloco) continua mostrando tudo. */}
            {selection.kind === 'slot' &&
              selBlock &&
              selBlockSchema &&
              selSlotDef &&
              (() => {
                const SlotIcon = selSlotDef.icon
                return (
                  <>
                    <div className="flex items-center justify-between border-b border-border px-4 py-3">
                      <span className="flex items-center gap-2 font-medium">
                        <SlotIcon className="size-4 text-primary" aria-hidden /> {selSlotDef.label}
                      </span>
                      <button type="button" onClick={() => setSelection(null)} aria-label="Fechar">
                        <X className="size-4" />
                      </button>
                    </div>
                    <div className="space-y-3 p-4">
                      <p className="text-xs text-muted-foreground">
                        Parte de <span className="font-medium">{selBlockSchema.label}</span>.{' '}
                        <button
                          type="button"
                          className="text-primary underline-offset-2 hover:underline"
                          onClick={() =>
                            setSelection({
                              kind: 'block',
                              rowId: selRow!.id,
                              colId: selCol!.id,
                              blockId: selBlock.id,
                            })
                          }
                        >
                          Editar bloco inteiro
                        </button>
                      </p>
                      {selBlockSchema.fields
                        .filter((f) => selSlotDef.keys.includes(f.key))
                        .map((f) => (
                          <FieldRenderer
                            key={f.key}
                            field={f}
                            value={(selBlock.props as Record<string, unknown>)[f.key]}
                            onChange={(v) =>
                              setTree((t) =>
                                updateBlockProps(t, selRow!.id, selCol!.id, selBlock.id, {
                                  ...selBlock.props,
                                  [f.key]: v,
                                } as CmsBlock['props']),
                              )
                            }
                          />
                        ))}
                    </div>
                  </>
                )
              })()}

            {/* MODO ITEM — um item de um repeater (ex. um serviço de services.items). Edita os campos do
                itemSchema do item, gravando props[fieldKey][itemIndex] via updateItem + updateBlockProps. */}
            {selection.kind === 'item' &&
              selBlock &&
              selItemField &&
              (() => {
                const items = getItems(
                  selBlock.props as Record<string, unknown>,
                  selection.fieldKey,
                )
                const item = items[selection.itemIndex] ?? {}
                const itemLabel = selItemField.itemLabel ?? 'item'
                return (
                  <>
                    <div className="flex items-center justify-between border-b border-border px-4 py-3">
                      <span className="flex items-center gap-2 font-medium capitalize">
                        {itemLabel} {selection.itemIndex + 1}
                      </span>
                      <button type="button" onClick={() => setSelection(null)} aria-label="Fechar">
                        <X className="size-4" />
                      </button>
                    </div>
                    <div className="space-y-3 p-4">
                      <p className="text-xs text-muted-foreground">
                        Item de <span className="font-medium">{selItemField.label}</span>.{' '}
                        <button
                          type="button"
                          className="text-primary underline-offset-2 hover:underline"
                          onClick={() =>
                            setSelection({
                              kind: 'block',
                              rowId: selRow!.id,
                              colId: selCol!.id,
                              blockId: selBlock.id,
                            })
                          }
                        >
                          Editar bloco inteiro
                        </button>
                      </p>
                      {(selItemField.itemSchema ?? []).map((f) => (
                        <FieldRenderer
                          key={f.key}
                          field={f}
                          value={(item as Record<string, unknown>)[f.key]}
                          onChange={(v) => {
                            const nextItem = { ...(item as Record<string, unknown>), [f.key]: v }
                            const nextArr = updateItem(items, selection.itemIndex, nextItem)
                            setTree((t) =>
                              updateBlockProps(t, selRow!.id, selCol!.id, selBlock.id, {
                                ...selBlock.props,
                                [selection.fieldKey]: nextArr,
                              } as CmsBlock['props']),
                            )
                          }}
                        />
                      ))}
                    </div>
                  </>
                )
              })()}
          </aside>
        )}
      </div>

      {/* ---- CATÁLOGO (escolhe o bloco; cria coluna ou empilha) ---- */}
      <Modal
        open={catalogTarget !== null}
        onClose={() => setCatalogTarget(null)}
        title={
          catalogTarget?.kind === 'column'
            ? 'Nova coluna — escolha o bloco'
            : 'Adicionar bloco à coluna'
        }
        size="lg"
      >
        <div className="grid gap-3 sm:grid-cols-2">
          {allBlockSchemas().map((s) => (
            <button
              key={s.type}
              type="button"
              onClick={() => pickFromCatalog(s.type)}
              className="rounded-lg border border-border p-4 text-left transition-colors hover:border-primary hover:bg-primary/5"
            >
              <div className="flex items-center gap-2 font-medium">
                <span aria-hidden className="text-lg">
                  {s.emoji}
                </span>{' '}
                {s.label}
              </div>
              <p className="mt-1 text-xs text-muted-foreground">{s.description}</p>
            </button>
          ))}
        </div>
      </Modal>

      {/* ---- CONFIGURAÇÕES DO SITE (Páginas / Tema / Domínio) ---- */}
      <Modal
        open={settingsOpen}
        onClose={() => setSettingsOpen(false)}
        title="Configurações do site"
        size="lg"
      >
        <div className="space-y-6">
          {/* Página atual: título / home / publicar / excluir */}
          {selected && (
            <Section title={`Página: ${selected.pageSlug}`}>
              <div className="space-y-3">
                <div>
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">
                    Título da página
                  </label>
                  <input
                    value={title}
                    onChange={(e) => setTitle(e.target.value)}
                    placeholder="Título"
                    className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                  />
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  <label className="flex items-center gap-1 text-xs text-muted-foreground">
                    <input
                      type="checkbox"
                      checked={pagePublished}
                      onChange={(e) => setPagePublished(e.target.checked)}
                    />{' '}
                    página publicada
                  </label>
                  {!selected.isHome && (
                    <Button
                      type="button"
                      variant="outline"
                      className="h-8 px-3 text-xs"
                      disabled={setHomeMut.isPending}
                      onClick={() => setHomeMut.mutate(selected.id)}
                    >
                      Definir como home
                    </Button>
                  )}
                  <Button
                    type="button"
                    variant="outline"
                    className="h-8 px-3 text-xs"
                    disabled={deletePageMut.isPending}
                    onClick={() => {
                      deletePageMut.mutate(selected.id)
                      setSettingsOpen(false)
                    }}
                  >
                    Excluir página
                  </Button>
                </div>
              </div>
            </Section>
          )}

          {/* Páginas: lista + criar */}
          <Section title="Páginas">
            <div className="flex flex-wrap gap-2">
              {pages.map((p) => (
                <button
                  key={p.id}
                  onClick={() => setSelectedId(p.id)}
                  className={
                    'rounded-md border px-3 py-1.5 text-sm ' +
                    (p.id === selectedId ? 'border-primary bg-primary/10' : 'border-border')
                  }
                >
                  {p.title || p.pageSlug}
                  {p.isHome && <span className="ml-1 text-xs text-muted-foreground">(home)</span>}
                  {!p.published && <span className="ml-1 text-xs text-amber-600">rascunho</span>}
                </button>
              ))}
            </div>
            <div className="mt-4 border-t border-border pt-4">
              {/* SELETOR DE TIPO DE PÁGINA: o tenant escolhe um template de estrutura (landing,
                  sobre, serviços...) e a página já nasce montada com os blocos do propósito. */}
              <p className="mb-1 text-xs font-medium text-muted-foreground">
                Tipo de página (começa pronta para editar)
              </p>
              <p className="mb-3 text-[11px] text-muted-foreground">
                As marcadas com <span className="font-medium text-primary">★</span> combinam com o
                seu segmento.
              </p>
              {TEMPLATE_CATEGORIES.map((cat) => {
                const items = templatesForProfile(profileId).filter((t) => t.category === cat.id)
                if (items.length === 0) return null
                return (
                  <div key={cat.id} className="mb-3">
                    <p className="mb-1.5 text-[11px] font-semibold tracking-wide text-muted-foreground/70 uppercase">
                      {cat.label}
                    </p>
                    <div className="grid grid-cols-2 gap-2 sm:grid-cols-4 lg:grid-cols-6">
                      {items.map((tpl) => {
                        const active = newTemplate === tpl.id
                        const aff = tpl.affinity.includes(profileId)
                        return (
                          <button
                            key={tpl.id}
                            type="button"
                            onClick={() => setNewTemplate(tpl.id)}
                            title={tpl.description}
                            className={`relative flex flex-col items-center gap-1 rounded-lg border p-2 text-center transition ${
                              active
                                ? 'border-primary bg-primary/5 ring-2 ring-primary/30'
                                : 'border-border hover:border-primary/50'
                            }`}
                          >
                            {aff && (
                              <span className="absolute top-1 right-1 text-[10px] text-primary">
                                ★
                              </span>
                            )}
                            <span className="text-xl">{tpl.icon}</span>
                            <span className="text-[11px] leading-tight font-medium">
                              {tpl.name}
                            </span>
                          </button>
                        )
                      })}
                    </div>
                  </div>
                )
              })}
              {pageTemplateById(newTemplate) && (
                <p className="mt-1 mb-3 text-xs text-muted-foreground">
                  {pageTemplateById(newTemplate)!.description}
                </p>
              )}
              <div className="flex flex-wrap items-end gap-2">
                <div>
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">
                    Endereço (slug)
                  </label>
                  <input
                    value={newSlug}
                    onChange={(e) => setNewSlug(e.target.value)}
                    placeholder="servicos"
                    className="rounded-md border border-border bg-background px-3 py-2 text-sm"
                  />
                </div>
                <div>
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">
                    Título
                  </label>
                  <input
                    value={newTitle}
                    onChange={(e) => setNewTitle(e.target.value)}
                    placeholder="Serviços"
                    className="rounded-md border border-border bg-background px-3 py-2 text-sm"
                  />
                </div>
                <Button
                  type="button"
                  disabled={createPageMut.isPending || !newSlug.trim()}
                  onClick={() => createPageMut.mutate()}
                >
                  {createPageMut.isPending ? 'Criando…' : 'Criar página'}
                </Button>
              </div>
            </div>
            {createError && <p className="mt-2 text-sm text-destructive">{createError}</p>}
          </Section>

          {/* Tema */}
          <Section title="Tema">
            {/* GALERIA DE TEMAS do catálogo (10 por nicho, recomendados destacados). Escolher um
                tema do catálogo desliga o preset/genérico (themeId tem precedência no render). */}
            <p className="mb-3 text-sm text-muted-foreground">
              Escolha um tema pronto para o seu segmento — paleta, tipografia e layout combinando
              com o nicho. Os marcados com{' '}
              <span className="font-medium text-primary">★ Recomendado</span> são os que mais
              combinam com o seu negócio.
            </p>
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
              {catalogThemes.map((t) => {
                const isRec = recommended.includes(t.archetype)
                const active = themeId === t.id
                return (
                  <button
                    key={t.id}
                    type="button"
                    onClick={() => {
                      setThemeId(t.id)
                      setPreset('')
                    }}
                    className={`group rounded-lg border p-2 text-left transition ${
                      active
                        ? 'border-primary ring-2 ring-primary/40'
                        : 'border-border hover:border-primary/50'
                    }`}
                  >
                    {/* swatch do tema: barra com bg + primária/secundária/accent */}
                    <div
                      className="mb-2 flex h-14 items-end gap-1 overflow-hidden rounded-md p-1.5"
                      style={{ background: t.palette.bg }}
                    >
                      <span
                        className="h-6 flex-1 rounded"
                        style={{ background: t.palette.primary }}
                      />
                      <span
                        className="h-6 w-2 rounded"
                        style={{ background: t.palette.secondary }}
                      />
                      <span className="h-6 w-2 rounded" style={{ background: t.palette.accent }} />
                    </div>
                    <div className="flex items-center justify-between gap-1">
                      <span className="text-xs font-semibold">{t.name}</span>
                      {isRec && <span className="text-[10px] font-medium text-primary">★</span>}
                    </div>
                    <p className="mt-0.5 line-clamp-2 text-[10px] leading-tight text-muted-foreground">
                      {t.description}
                    </p>
                  </button>
                )
              })}
            </div>

            {/* Opções avançadas: presets antigos (genérico/Meada). Escolher um deles limpa o themeId. */}
            <details className="mt-4">
              <summary className="cursor-pointer text-xs font-medium text-muted-foreground">
                Opções avançadas (cor manual / preset Meada)
              </summary>
              <div className="mt-3 flex flex-wrap items-end gap-4">
                <div>
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">
                    Preset
                  </label>
                  <select
                    value={preset}
                    onChange={(e) => {
                      setPreset(e.target.value as '' | 'meada-dark')
                      setThemeId('')
                    }}
                    className="h-9 rounded-md border border-border bg-background px-2 text-sm"
                  >
                    <option value="">Genérico (cor + claro/escuro)</option>
                    <option value="meada-dark">Meada (dark-glass + gradiente)</option>
                  </select>
                </div>
                {preset === '' && !themeId && (
                  <>
                    <div>
                      <label className="mb-1 block text-xs font-medium text-muted-foreground">
                        Cor primária
                      </label>
                      <input
                        type="color"
                        value={primaryColor}
                        onChange={(e) => setPrimaryColor(e.target.value)}
                        className="h-9 w-16 rounded-md border border-border bg-background"
                      />
                    </div>
                    <label className="flex items-center gap-2 text-sm">
                      <input
                        type="checkbox"
                        checked={dark}
                        onChange={(e) => setDark(e.target.checked)}
                      />{' '}
                      Fundo escuro
                    </label>
                  </>
                )}
                {preset === 'meada-dark' && (
                  <p className="max-w-xs text-xs text-muted-foreground">
                    Preset da marca Meada: fundo near-black, gradiente azul→roxo→rosa e fonte Geist.
                    Use com os blocos <strong>Meada · *</strong>.
                  </p>
                )}
              </div>
            </details>

            <div className="mt-4">
              <Button type="button" disabled={themeMut.isPending} onClick={() => themeMut.mutate()}>
                {themeMut.isPending ? 'Salvando…' : 'Salvar tema'}
              </Button>
              {themeId && (
                <span className="ml-3 text-xs text-muted-foreground">
                  Tema selecionado:{' '}
                  <strong>{catalogThemes.find((t) => t.id === themeId)?.name}</strong>
                </span>
              )}
            </div>
          </Section>

          {/* Domínio */}
          <Section title="Domínio próprio (opcional)">
            <div className="flex flex-wrap items-end gap-2">
              <div className="min-w-[14rem] flex-1">
                <input
                  value={domain}
                  onChange={(e) => setDomain(e.target.value)}
                  placeholder="minhaempresa.com.br"
                  className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                />
              </div>
              <Button
                type="button"
                variant="outline"
                disabled={domainMut.isPending}
                onClick={() => domainMut.mutate()}
              >
                Salvar domínio
              </Button>
            </div>
            {domainError && <p className="mt-2 text-sm text-destructive">{domainError}</p>}

            {site.domain && (
              <div className="mt-4 space-y-2 border-t border-border pt-4">
                <div className="flex items-center gap-2">
                  <span className="text-sm">Verificação de posse:</span>
                  <Badge variant={site.domainVerified ? 'success' : 'muted'}>
                    {site.domainVerified ? 'verificado' : 'não verificado'}
                  </Badge>
                </div>
                {!site.domainVerified && (
                  <>
                    {site.verifyToken ? (
                      <p className="text-xs text-muted-foreground">
                        Crie um registro <strong>TXT</strong> no DNS de{' '}
                        <span className="font-mono">{site.domain}</span> com o valor:{' '}
                        <code className="rounded bg-muted px-1">
                          _meada-verify={site.verifyToken}
                        </code>
                        , depois clique em Verificar.
                      </p>
                    ) : (
                      <Button
                        type="button"
                        variant="outline"
                        className="h-8 px-3 text-xs"
                        disabled={verifyStartMut.isPending}
                        onClick={() => verifyStartMut.mutate()}
                      >
                        Gerar token de verificação
                      </Button>
                    )}
                    {site.verifyToken && (
                      <Button
                        type="button"
                        variant="outline"
                        className="h-8 px-3 text-xs"
                        disabled={verifyMut.isPending}
                        onClick={() => verifyMut.mutate()}
                      >
                        {verifyMut.isPending ? 'Verificando…' : 'Verificar agora'}
                      </Button>
                    )}
                  </>
                )}
                <p className="text-xs text-muted-foreground">
                  Após verificar, aponte o domínio para o nosso servidor. O certificado HTTPS é
                  emitido automaticamente quando o domínio responde por aqui.
                </p>
              </div>
            )}
          </Section>

          {site.published && (
            <p className="text-xs text-muted-foreground">
              Site publicado — ver em{' '}
              <a
                href={`/p/${site.slug}`}
                target="_blank"
                rel="noopener noreferrer"
                className="underline"
              >
                /p/{site.slug}
              </a>
            </p>
          )}
        </div>
      </Modal>
    </div>
  )
}
