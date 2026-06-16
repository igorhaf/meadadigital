import { createClient } from './client'

/**
 * Configuração da IA do tenant (1:1 por empresa — UNIQUE(company_id) no banco). Os 4
 * campos de conteúdo são opcionais (nullable); model_provider tem default 'gemini' no
 * banco e NÃO é exposto na UI (o tenant não escolhe provider no MVP).
 */
export type AiSettings = {
  tone: string | null
  systemRules: string | null
  restrictions: string | null
  handoffTriggers: string | null
  modelProvider: string
  // Engajamento (camada 5.21): boas-vindas na 1ª mensagem (#82) e reativação automática (#81).
  welcomeMessage: string | null
  reactivationDays: number | null
  reactivationMessage: string | null
}

/**
 * Configuração da IA da empresa do tenant (SDK + RLS). Retorna null se ainda não existe
 * linha (.maybeSingle() — 0 ou 1; UNIQUE(company_id) garante no máximo 1). A tela carrega
 * vazia nesse caso; a linha nasce no primeiro upsert.
 */
export async function getMyAiSettings(): Promise<AiSettings | null> {
  const supabase = createClient()
  const { data, error } = await supabase
    .from('ai_settings')
    .select(
      'tone, system_rules, restrictions, handoff_triggers, model_provider, welcome_message, reactivation_days, reactivation_message',
    )
    .maybeSingle()

  if (error) {
    throw error
  }

  if (!data) {
    return null
  }

  return {
    tone: data.tone,
    systemRules: data.system_rules,
    restrictions: data.restrictions,
    handoffTriggers: data.handoff_triggers,
    modelProvider: data.model_provider,
    welcomeMessage: data.welcome_message,
    reactivationDays: data.reactivation_days,
    reactivationMessage: data.reactivation_message,
  }
}

/**
 * Cria ou atualiza a configuração da IA (semântica UPSERT por causa do 1:1). onConflict:
 * 'company_id' → primeira vez INSERT (RLS WITH CHECK do insert), depois UPDATE (USING +
 * WITH CHECK). company_id explícito no payload (defesa em profundidade). model_provider
 * NÃO é enviado: no INSERT usa o default 'gemini' do banco; no UPDATE não é tocado.
 * Os 4 campos vêm como string|null (vazio → null, para não gravar string vazia).
 */
export async function upsertMyAiSettings(
  companyId: string,
  payload: {
    tone: string | null
    systemRules: string | null
    restrictions: string | null
    handoffTriggers: string | null
    welcomeMessage: string | null
    reactivationDays: number | null
    reactivationMessage: string | null
  },
): Promise<AiSettings> {
  const supabase = createClient()
  const { data, error } = await supabase
    .from('ai_settings')
    .upsert(
      {
        company_id: companyId,
        tone: payload.tone,
        system_rules: payload.systemRules,
        restrictions: payload.restrictions,
        handoff_triggers: payload.handoffTriggers,
        welcome_message: payload.welcomeMessage,
        reactivation_days: payload.reactivationDays,
        reactivation_message: payload.reactivationMessage,
      },
      { onConflict: 'company_id' },
    )
    .select(
      'tone, system_rules, restrictions, handoff_triggers, model_provider, welcome_message, reactivation_days, reactivation_message',
    )
    .single()

  if (error) {
    throw error
  }

  return {
    tone: data.tone,
    systemRules: data.system_rules,
    restrictions: data.restrictions,
    handoffTriggers: data.handoff_triggers,
    modelProvider: data.model_provider,
    welcomeMessage: data.welcome_message,
    reactivationDays: data.reactivation_days,
    reactivationMessage: data.reactivation_message,
  }
}
