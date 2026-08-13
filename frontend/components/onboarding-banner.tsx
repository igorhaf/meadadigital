'use client'

import { Check } from 'lucide-react'
import Link from 'next/link'

import { useOnboardingStatus } from '@/lib/hooks/use-onboarding-status'

/**
 * Banner de onboarding do tenant (camada 5.14 #46) — discreto, no topo do hub. Lista os 4
 * passos de configuração da IA com check verde nos completos. Some quando completedSteps
 * >= 3 (isOnboarding false): heurística pura, sem localStorage "vi o tour" nem modal.
 *
 * Reusa o cache das telas (mesmos queryKeys: my-faqs/my-services/my-business-hours/
 * my-ai-settings), então não dispara refetch redundante quando o tenant volta ao hub
 * depois de configurar — o banner já reflete o estado.
 *
 * enabled controla as queries: só busca quando confirmado tenant (evita 0-rows do
 * super-admin). Enquanto isPending, não renderiza nada (evita flash do banner que some).
 */
export function OnboardingBanner({ enabled }: { enabled: boolean }) {
  const status = useOnboardingStatus(enabled)

  if (!enabled || status.isPending || !status.isOnboarding) {
    return null
  }

  const steps = [
    { label: 'Cadastrar FAQs', href: '/dashboard/faqs', done: status.faqsConfigured },
    { label: 'Cadastrar serviços', href: '/dashboard/services', done: status.servicesConfigured },
    {
      label: 'Definir horário comercial',
      href: '/dashboard/business-hours',
      done: status.hoursConfigured,
    },
    { label: 'Configurar tom da IA', href: '/dashboard/ai-settings', done: status.aiConfigured },
  ]

  return (
    <div className="mb-6 rounded-xl border border-border bg-muted/40 p-6">
      <h2 className="text-base font-semibold">Bem-vindo! Configure sua IA</h2>
      <p className="mb-4 text-sm text-muted-foreground">
        Complete os passos pra IA responder seus clientes bem
        <span className="ml-1">
          ({status.completedSteps}/{status.totalSteps})
        </span>
      </p>
      <ul className="space-y-2">
        {steps.map((step) => (
          <li key={step.href} className="flex items-center gap-2 text-sm">
            <span
              className={`flex size-5 items-center justify-center rounded-full border ${
                step.done
                  ? 'border-green-600 bg-green-100 text-green-700'
                  : 'border-border text-transparent'
              }`}
            >
              <Check className="size-3" />
            </span>
            {step.done ? (
              <span className="text-muted-foreground line-through">{step.label}</span>
            ) : (
              <Link href={step.href} className="font-medium text-foreground hover:underline">
                {step.label}
              </Link>
            )}
          </li>
        ))}
      </ul>
    </div>
  )
}
