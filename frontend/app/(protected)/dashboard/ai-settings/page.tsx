'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Button } from '@/components/ui/button'
import { Card, Section } from '@/components/ui/card'
import { PageSkeleton } from '@/components/ui/skeleton'
import { getMe } from '@/lib/api/me'
import { getMyAiSettings, upsertMyAiSettings } from '@/lib/supabase/ai_settings'
import { useOnSync } from '@/lib/use-synced-form'

/** Converte string do textarea para o valor a gravar: vazio/whitespace → null. */
function orNull(s: string): string | null {
  return s.trim() === '' ? null : s.trim()
}

/** Converte o campo numérico (string) para int positivo, ou null se vazio/inválido. */
function orNullInt(s: string): number | null {
  const trimmed = s.trim()
  if (trimmed === '') return null
  const n = Number.parseInt(trimmed, 10)
  return Number.isFinite(n) && n > 0 ? n : null
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
  const [welcomeMessage, setWelcomeMessage] = useState('')
  const [reactivationDays, setReactivationDays] = useState('')
  const [reactivationMessage, setReactivationMessage] = useState('')
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
  useOnSync(data, (data) => {
      setTone(data.tone ?? '')
      setSystemRules(data.systemRules ?? '')
      setRestrictions(data.restrictions ?? '')
      setHandoffTriggers(data.handoffTriggers ?? '')
      setWelcomeMessage(data.welcomeMessage ?? '')
      setReactivationDays(data.reactivationDays != null ? String(data.reactivationDays) : '')
      setReactivationMessage(data.reactivationMessage ?? '')
  })

  const mutation = useMutation({
    mutationFn: () =>
      upsertMyAiSettings(me!.companyId!, {
        tone: orNull(tone),
        systemRules: orNull(systemRules),
        restrictions: orNull(restrictions),
        handoffTriggers: orNull(handoffTriggers),
        welcomeMessage: orNull(welcomeMessage),
        reactivationDays: orNullInt(reactivationDays),
        reactivationMessage: orNull(reactivationMessage),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['my-ai-settings'] })
      setSaved(true)
      setTimeout(() => setSaved(false), 3000)
    },
    onError: (err) => console.error('upsertMyAiSettings failed:', err),
  })

  if (me && !isTenant) {
    return <div className="text-sm text-muted-foreground">Redirecionando…</div>
  }

  if (isError) {
    return (
      <div className="space-y-4">
        <PageHeader title="IA" />
        <p className="text-sm text-destructive">Erro ao carregar a configuração.</p>
        <Link href="/dashboard">
          <Button variant="outline">Voltar ao dashboard</Button>
        </Link>
      </div>
    )
  }

  if (isPending) {
    return <PageSkeleton />
  }

  return (
    <div className="space-y-6">
      <PageHeader title="IA" description="Configure como a IA atende seus clientes" />

      {/* Tom e estilo — como a IA se comunica. */}
      <Card>
        <Section
          title="Tom e estilo"
          description="Como a IA fala com seus clientes (formalidade, tratamento, vocabulário)."
        >
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
              className="w-full rounded-md border border-border bg-card px-3 py-2 text-sm"
            />
          </div>
        </Section>
      </Card>

      {/* Regras e restrições — o que a IA deve/não deve fazer e quando passar para humano. */}
      <Card>
        <Section
          title="Regras e restrições"
          description="Limites e gatilhos que guiam o comportamento da IA."
        >
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
              className="w-full rounded-md border border-border bg-card px-3 py-2 text-sm"
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
              className="w-full rounded-md border border-border bg-card px-3 py-2 text-sm"
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
              className="w-full rounded-md border border-border bg-card px-3 py-2 text-sm"
            />
          </div>
        </Section>
      </Card>

      {/* Boas-vindas — primeira mensagem automática a cada cliente novo. */}
      <Card>
        <Section
          title="Boas-vindas"
          description="Mensagem enviada automaticamente na primeira mensagem de cada cliente."
        >
          <div>
            <label htmlFor="welcomeMessage" className="mb-1 block text-sm font-medium">
              Mensagem de boas-vindas <span className="text-muted-foreground">(opcional)</span>
            </label>
            <textarea
              id="welcomeMessage"
              rows={2}
              maxLength={500}
              value={welcomeMessage}
              onChange={(e) => {
                setWelcomeMessage(e.target.value)
                setSaved(false)
              }}
              placeholder="ex.: 'Olá! Seja bem-vindo. Sou o atendente virtual e vou te ajudar com agendamentos e dúvidas.'"
              className="w-full rounded-md border border-border bg-card px-3 py-2 text-sm"
            />
            <p className="mt-1 text-xs text-muted-foreground">
              Enviada automaticamente na primeira mensagem de cada cliente. Até 500 caracteres.
            </p>
          </div>
        </Section>
      </Card>

      {/* Reativação — reengajar clientes inativos há N dias. */}
      <Card>
        <Section
          title="Reativação"
          description="Reengaja clientes que ficaram sem conversar por um período."
        >
          <div>
            <label htmlFor="reactivationDays" className="mb-1 block text-sm font-medium">
              Dias para reativação <span className="text-muted-foreground">(opcional)</span>
            </label>
            <input
              id="reactivationDays"
              type="number"
              min={1}
              value={reactivationDays}
              onChange={(e) => {
                setReactivationDays(e.target.value)
                setSaved(false)
              }}
              placeholder="ex.: 30"
              className="w-32 rounded-md border border-border bg-card px-3 py-2 text-sm"
            />
            <p className="mt-1 text-xs text-muted-foreground">
              Clientes sem mensagem por esse número de dias recebem a mensagem de reativação.
              Deixe vazio para desativar.
            </p>
          </div>

          <div>
            <label htmlFor="reactivationMessage" className="mb-1 block text-sm font-medium">
              Mensagem de reativação <span className="text-muted-foreground">(opcional)</span>
            </label>
            <textarea
              id="reactivationMessage"
              rows={2}
              value={reactivationMessage}
              onChange={(e) => {
                setReactivationMessage(e.target.value)
                setSaved(false)
              }}
              placeholder="ex.: 'Sentimos sua falta! Que tal agendar um horário? Estamos à disposição.'"
              className="w-full rounded-md border border-border bg-card px-3 py-2 text-sm"
            />
          </div>
        </Section>
      </Card>

      {mutation.isError && (
        <p className="text-sm text-destructive">Erro ao salvar. Tente novamente.</p>
      )}
      {saved && <p className="text-sm text-green-600">Salvo!</p>}

      <div>
        <Button onClick={() => mutation.mutate()} disabled={mutation.isPending || !me?.companyId}>
          {mutation.isPending ? 'Salvando…' : 'Salvar'}
        </Button>
      </div>
    </div>
  )
}
