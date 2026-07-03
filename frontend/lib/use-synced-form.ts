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
