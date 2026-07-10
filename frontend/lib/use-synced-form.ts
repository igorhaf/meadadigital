import { useState, type Dispatch, type SetStateAction } from 'react'

/**
 * Estado de formulário SINCRONIZADO com um dado assíncrono (ex.: config vinda do useQuery):
 * quando {@code data} muda de referência (primeiro load ou refetch), o form é re-derivado por
 * {@code toForm}; entre syncs, o usuário edita livremente via o setter retornado.
 *
 * É o padrão oficial do React de "adjusting state when props change" (setState DURANTE o render,
 * condicionado à mudança) — substitui o antigo `useEffect(() => setForm(...), [data])`, que
 * disparava a regra react-hooks/set-state-in-effect e causava um render extra. Mesmo
 * comportamento observável: refetch re-seta o form (edições não salvas são descartadas, como
 * sempre foi nas telas de settings).
 */
export function useSyncedForm<D, F>(
  data: D | null | undefined,
  toForm: (data: D) => F,
): [F | null, Dispatch<SetStateAction<F | null>>] {
  const [form, setForm] = useState<F | null>(null)
  const [synced, setSynced] = useState<D | null | undefined>(undefined)
  if (data != null && data !== synced) {
    setSynced(data)
    setForm(toForm(data))
  }
  return [form, setForm]
}

/**
 * Variante multi-setter do {@link useSyncedForm}: executa {@code onSync} DURANTE o render quando
 * {@code value} muda de referência (e não é null/undefined). Para telas que sincronizam vários
 * estados independentes a partir de um dado assíncrono. Os setters chamados devem ser do PRÓPRIO
 * componente (regra do setState-durante-render do React).
 */
export function useOnSync<D>(value: D | null | undefined, onSync: (value: D) => void): void {
  const [synced, setSynced] = useState<D | null | undefined>(undefined)
  if (value != null && value !== synced) {
    setSynced(value)
    onSync(value)
  }
}

/**
 * Reset de formulário de DIÁLOGO sem useEffect: dispara {@code reset} durante o render quando
 * {@code trigger} MUDA para um valor truthy (abertura, ou troca do registro em edição com o
 * diálogo aberto). Substitui o antigo `useEffect(() => { if (open) reset() }, [open, ...])`.
 * Use um trigger que codifique o "episódio" de abertura — ex.: {@code open ? (editing?.id ?? 'new') : null}.
 */
export function useResetWhen(trigger: unknown, reset: () => void): void {
  const [prev, setPrev] = useState<unknown>(undefined)
  if (trigger !== prev) {
    setPrev(trigger)
    if (trigger) reset()
  }
}
