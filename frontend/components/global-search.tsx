'use client'

import { useRouter } from 'next/navigation'
import { useEffect, useRef, useState } from 'react'

import { Modal } from '@/components/ui/modal'
import { globalSearch, type SearchResults } from '@/lib/api/search'

/** Resultado vazio — estado inicial e quando q tem menos de 2 chars. */
const EMPTY: SearchResults = { contacts: [], conversations: [], messages: [] }

/**
 * Paleta de comandos (Cmd+K / Ctrl+K), camada 5.22 #84. Componente global montado uma
 * única vez no layout (protected) — fica disponível em todas as telas do dashboard.
 *
 * <p>Abre com Cmd+K (mac) ou Ctrl+K. Debounce de 250ms nas chamadas a globalSearch para
 * não bater no backend a cada tecla. Resultados agrupados (Contatos/Conversas/Mensagens),
 * cada item leva à tela relevante (contato → /dashboard/contacts/[id]; conversa e mensagem
 * → /dashboard/conversations/[id]). Fecha no Escape (via Modal) ou ao escolher um resultado.
 *
 * <p>Dependency-free de propósito (sem cmdk): keydown nativo + Modal do projeto. O input
 * recebe foco ao abrir; o erro de busca é silencioso (resultados vazios) — a busca é um
 * atalho, não um fluxo crítico.
 */
export function GlobalSearch() {
  const router = useRouter()
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<SearchResults>(EMPTY)
  const inputRef = useRef<HTMLInputElement>(null)

  // Atalho global Cmd+K / Ctrl+K abre a paleta. preventDefault evita o "abrir histórico"
  // do navegador em alguns SOs. Listener no document porque a paleta pode abrir de qualquer
  // tela, sem foco num elemento específico.
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        setOpen((v) => !v)
      }
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [])

  // Foco no input ao abrir (microtask para garantir que o Modal já montou o input).
  useEffect(() => {
    if (open) {
      const t = setTimeout(() => inputRef.current?.focus(), 0)
      return () => clearTimeout(t)
    }
    // Limpa ao fechar para a próxima abertura começar do zero.
    /* eslint-disable react-hooks/set-state-in-effect -- reset atrelado ao ciclo do modal (open), não derivável no render */
    setQuery('')
    setResults(EMPTY)
    /* eslint-enable react-hooks/set-state-in-effect */
  }, [open])

  // Busca com debounce de 250ms. q < 2 chars → limpa (o backend devolveria vazio mesmo).
  // Flag cancelled evita aplicar resposta atrasada de uma query já substituída.
  useEffect(() => {
    const q = query.trim()
    if (q.length < 2) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- zera resultados junto do debounce (efeito é dono do ciclo da busca)
      setResults(EMPTY)
      return
    }
    let cancelled = false
    const t = setTimeout(() => {
      globalSearch(q)
        .then((r) => {
          if (!cancelled) setResults(r)
        })
        .catch((err) => {
          if (!cancelled) {
            console.error('globalSearch failed:', err)
            setResults(EMPTY)
          }
        })
    }, 250)
    return () => {
      cancelled = true
      clearTimeout(t)
    }
  }, [query])

  // Navega e fecha a paleta ao escolher um resultado.
  function go(href: string) {
    setOpen(false)
    router.push(href)
  }

  const hasResults =
    results.contacts.length > 0 || results.conversations.length > 0 || results.messages.length > 0

  return (
    <Modal open={open} onClose={() => setOpen(false)} title="Buscar" size="lg">
      <input
        ref={inputRef}
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder="Buscar contatos, conversas, mensagens…"
        className="w-full rounded-md border border-border px-3 py-2 text-sm focus:ring-2 focus:ring-ring focus:outline-none"
      />

      <div className="mt-4 space-y-4">
        {query.trim().length < 2 && (
          <p className="text-sm text-muted-foreground">Digite ao menos 2 caracteres.</p>
        )}

        {query.trim().length >= 2 && !hasResults && (
          <p className="text-sm text-muted-foreground">Nenhum resultado.</p>
        )}

        {results.contacts.length > 0 && (
          <div>
            <h3 className="mb-1 text-xs font-medium tracking-wide text-muted-foreground uppercase">
              Contatos
            </h3>
            <ul className="space-y-1">
              {results.contacts.map((c) => (
                <li key={c.id}>
                  <button
                    onClick={() => go(`/dashboard/contacts/${c.id}`)}
                    className="w-full rounded-md px-3 py-2 text-left text-sm hover:bg-muted"
                  >
                    <span className="font-medium">{c.name ?? 'Sem nome'}</span>
                    <span className="ml-2 text-muted-foreground">{c.phoneNumber}</span>
                  </button>
                </li>
              ))}
            </ul>
          </div>
        )}

        {results.conversations.length > 0 && (
          <div>
            <h3 className="mb-1 text-xs font-medium tracking-wide text-muted-foreground uppercase">
              Conversas
            </h3>
            <ul className="space-y-1">
              {results.conversations.map((c) => (
                <li key={c.id}>
                  <button
                    onClick={() => go(`/dashboard/conversations/${c.id}`)}
                    className="w-full rounded-md px-3 py-2 text-left text-sm hover:bg-muted"
                  >
                    <span className="font-medium">{c.contactName ?? 'Sem nome'}</span>
                    <span className="ml-2 text-muted-foreground">{c.phoneNumber}</span>
                  </button>
                </li>
              ))}
            </ul>
          </div>
        )}

        {results.messages.length > 0 && (
          <div>
            <h3 className="mb-1 text-xs font-medium tracking-wide text-muted-foreground uppercase">
              Mensagens
            </h3>
            <ul className="space-y-1">
              {results.messages.map((m) => (
                <li key={m.id}>
                  <button
                    onClick={() => go(`/dashboard/conversations/${m.conversationId}`)}
                    className="w-full rounded-md px-3 py-2 text-left text-sm hover:bg-muted"
                  >
                    <span className="line-clamp-1 text-muted-foreground">{m.content}</span>
                  </button>
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>
    </Modal>
  )
}
