'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'

import { SignOutButton } from '@/components/sign-out-button'
import { ThemeToggle } from '@/components/theme-toggle'
import { Button } from '@/components/ui/button'
import { getMe } from '@/lib/api/me'
import { getMyAiSettings, upsertMyAiSettings } from '@/lib/supabase/ai_settings'

/** Converte string do textarea para o valor a gravar: vazio/whitespace → null. */
function orNull(s: string): string | null {
  return s.trim() === '' ? null : s.trim()
}

/**
 * Configuração da IA do tenant (SDK + RLS). Form único (1:1 por empresa via UPSERT). 4
 * campos de texto opcionais; model_provider não aparece (fica 'gemini' default no banco).
 * Carrega vazio se nunca configurado; a linha nasce no primeiro Salvar.
 */
export default function AiSettingsPage() {
  const router = useRouter()
  const queryClient = useQueryClient()
  const [tone, setTone] = useState('')
  const [systemRules, setSystemRules] = useState('')
  const [restrictions, setRestrictions] = useState('')
  const [handoffTriggers, setHandoffTriggers] = useState('')
  const [saved, setSaved] = useState(false)

  const { data: me } = useQuery({ queryKey: ['me'], queryFn: getMe })
  const isTenant = me?.role === 'tenant_admin'

  useEffect(() => {
    if (me && me.role !== 'tenant_admin') {
      router.replace('/dashboard')
    }
  }, [me, router])

  const { data, isPending, isError } = useQuery({
    queryKey: ['my-ai-settings'],
    queryFn: getMyAiSettings,
    enabled: isTenant,
  })

  // Sincroniza o estado local quando os dados chegam (data === null → mantém vazio).
  useEffect(() => {
    if (data) {
      setTone(data.tone ?? '')
      setSystemRules(data.systemRules ?? '')
      setRestrictions(data.restrictions ?? '')
      setHandoffTriggers(data.handoffTriggers ?? '')
    }
  }, [data])

  const mutation = useMutation({
    mutationFn: () =>
      upsertMyAiSettings(me!.companyId!, {
        tone: orNull(tone),
        systemRules: orNull(systemRules),
        restrictions: orNull(restrictions),
        handoffTriggers: orNull(handoffTriggers),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['my-ai-settings'] })
      setSaved(true)
      setTimeout(() => setSaved(false), 3000)
    },
    onError: (err) => console.error('upsertMyAiSettings failed:', err),
  })

  if (me && !isTenant) {
    return (
      <div className="mx-auto max-w-3xl p-8 text-sm text-muted-foreground">Redirecionando…</div>
    )
  }

  if (isError) {
    return (
      <div className="mx-auto max-w-3xl p-8">
        <h1 className="mb-2 text-xl font-semibold">Configuração da IA</h1>
        <p className="mb-4 text-sm text-destructive">Erro ao carregar a configuração.</p>
        <Link href="/dashboard">
          <Button variant="outline">Voltar ao dashboard</Button>
        </Link>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-3xl p-8">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-xl font-semibold">Configuração da IA</h1>
        <div className="flex items-center gap-2">
          <Link href="/dashboard">
            <Button variant="outline">Voltar</Button>
          </Link>
          <ThemeToggle />
          <SignOutButton />
        </div>
      </div>

      {isPending && <p className="text-sm text-muted-foreground">Carregando…</p>}

      {!isPending && (
        <div className="space-y-4 rounded-xl border border-border p-6">
          <div>
            <label htmlFor="tone" className="mb-1 block text-sm font-medium">
              Tom de comunicação <span className="text-muted-foreground">(opcional)</span>
            </label>
            <textarea
              id="tone"
              rows={2}
              value={tone}
              onChange={(e) => {
                setTone(e.target.value)
                setSaved(false)
              }}
              placeholder="ex.: 'Cordial e profissional. Trate o cliente por você. Evite gírias.'"
              className="w-full rounded-md border border-border px-3 py-2 text-sm"
            />
          </div>

          <div>
            <label htmlFor="systemRules" className="mb-1 block text-sm font-medium">
              Regras do sistema <span className="text-muted-foreground">(opcional)</span>
            </label>
            <textarea
              id="systemRules"
              rows={3}
              value={systemRules}
              onChange={(e) => {
                setSystemRules(e.target.value)
                setSaved(false)
              }}
              placeholder="ex.: 'Sempre confirme dados de agendamento antes de gravar. Nunca prometa prazo sem antes consultar a agenda. Em caso de dúvida sobre preço, peça pra cliente aguardar e acione um humano.'"
              className="w-full rounded-md border border-border px-3 py-2 text-sm"
            />
          </div>

          <div>
            <label htmlFor="restrictions" className="mb-1 block text-sm font-medium">
              Restrições <span className="text-muted-foreground">(opcional)</span>
            </label>
            <textarea
              id="restrictions"
              rows={2}
              value={restrictions}
              onChange={(e) => {
                setRestrictions(e.target.value)
                setSaved(false)
              }}
              placeholder="ex.: 'Não envie áudio. Não dê descontos sem confirmação humana. Não compartilhe informações de outros clientes.'"
              className="w-full rounded-md border border-border px-3 py-2 text-sm"
            />
          </div>

          <div>
            <label htmlFor="handoffTriggers" className="mb-1 block text-sm font-medium">
              Gatilhos de transferência para humano{' '}
              <span className="text-muted-foreground">(opcional)</span>
            </label>
            <textarea
              id="handoffTriggers"
              rows={2}
              value={handoffTriggers}
              onChange={(e) => {
                setHandoffTriggers(e.target.value)
                setSaved(false)
              }}
              placeholder="ex.: 'Quando o cliente pedir falar com pessoa humana, quando expressar irritação ou frustração, quando o assunto envolver reclamação formal ou jurídico.'"
              className="w-full rounded-md border border-border px-3 py-2 text-sm"
            />
          </div>
        </div>
      )}

      {mutation.isError && (
        <p className="mt-3 text-sm text-destructive">Erro ao salvar. Tente novamente.</p>
      )}
      {saved && <p className="mt-3 text-sm text-green-600">Salvo!</p>}

      {!isPending && (
        <div className="mt-4">
          <Button onClick={() => mutation.mutate()} disabled={mutation.isPending || !me?.companyId}>
            {mutation.isPending ? 'Salvando…' : 'Salvar'}
          </Button>
        </div>
      )}
    </div>
  )
}
