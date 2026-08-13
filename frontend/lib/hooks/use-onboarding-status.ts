import { useQuery } from '@tanstack/react-query'

import { getMyAiSettings } from '@/lib/supabase/ai_settings'
import { getMyBusinessHours } from '@/lib/supabase/business_hours'
import { getMyFaqs } from '@/lib/supabase/faqs'
import { getMyServices } from '@/lib/supabase/services'

/**
 * Status de onboarding do tenant (camada 5.14 #46) — heurística PURA no frontend, sem
 * migration nem flag persistida. Conta, via os SDKs já existentes, quantos dos 4 passos
 * de configuração a empresa completou:
 *   1. FAQs    — ao menos 1 FAQ ativa.
 *   2. Serviços — ao menos 1 serviço ativo.
 *   3. Horários — ao menos 1 dia de horário configurado (linha em business_hours).
 *   4. IA       — ai_settings existe E tem ao menos um dos campos de conteúdo preenchido
 *                 (a linha pode nascer "vazia" num upsert default; só conta se há tom/
 *                 regras/restrições/handoff de fato).
 *
 * isOnboarding = completedSteps < 3: o banner aparece enquanto faltam 2+ passos; some
 * quando a empresa já configurou 3 dos 4 (decisão de produto — não exige os 4 pra sumir,
 * o último é "nice to have"). Cada query é independente; o agregado espera todas.
 */
export type OnboardingStatus = {
  faqsConfigured: boolean
  servicesConfigured: boolean
  hoursConfigured: boolean
  aiConfigured: boolean
  completedSteps: number
  totalSteps: number
  isOnboarding: boolean
  isPending: boolean
}

export function useOnboardingStatus(enabled: boolean): OnboardingStatus {
  const faqs = useQuery({ queryKey: ['my-faqs'], queryFn: getMyFaqs, enabled })
  const services = useQuery({ queryKey: ['my-services'], queryFn: getMyServices, enabled })
  const hours = useQuery({
    queryKey: ['my-business-hours'],
    queryFn: getMyBusinessHours,
    enabled,
  })
  const ai = useQuery({ queryKey: ['my-ai-settings'], queryFn: getMyAiSettings, enabled })

  const faqsConfigured = (faqs.data ?? []).some((f) => f.active)
  const servicesConfigured = (services.data ?? []).some((s) => s.active)
  const hoursConfigured = (hours.data ?? []).length > 0
  const aiConfigured =
    ai.data != null &&
    [ai.data.tone, ai.data.systemRules, ai.data.restrictions, ai.data.handoffTriggers].some(
      (v) => v != null && v.trim().length > 0,
    )

  const completedSteps = [faqsConfigured, servicesConfigured, hoursConfigured, aiConfigured].filter(
    Boolean,
  ).length

  const isPending =
    enabled && (faqs.isPending || services.isPending || hours.isPending || ai.isPending)

  return {
    faqsConfigured,
    servicesConfigured,
    hoursConfigured,
    aiConfigured,
    completedSteps,
    totalSteps: 4,
    isOnboarding: completedSteps < 3,
    isPending,
  }
}
