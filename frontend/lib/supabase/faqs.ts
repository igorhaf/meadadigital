import { createClient } from './client'

/**
 * FAQ (pergunta/resposta) da empresa — shape do frontend, mapeado das colunas de
 * public.faqs. question e answer são ambos obrigatórios (text NOT NULL no banco).
 * createdAt ISO string.
 */
export type Faq = {
  id: string
  question: string
  answer: string
  active: boolean
  createdAt: string
}

/**
 * Lista as FAQs ATIVAS (não soft-deleted) da empresa do tenant logado, via SDK + RLS
 * (faqs_select: company_id = app.company_id()). O isolamento é do banco — o tenant só
 * vê as próprias FAQs. Ordenado por created_at desc (mais novas primeiro).
 *
 * <p>deleted_at is null: faqs tem soft delete; filtramos as removidas aqui (o RLS não
 * filtra soft delete, é responsabilidade da query).
 */
export async function getMyFaqs(): Promise<Faq[]> {
  const supabase = createClient()
  const { data, error } = await supabase
    .from('faqs')
    .select('id, question, answer, active, created_at')
    .is('deleted_at', null)
    .order('created_at', { ascending: false })

  if (error) {
    throw error
  }

  return (data ?? []).map((f) => ({
    id: f.id,
    question: f.question,
    answer: f.answer,
    active: f.active,
    createdAt: f.created_at,
  }))
}

/**
 * Cria uma FAQ para a empresa do tenant. company_id é OBRIGATÓRIO no payload e DEVE ser
 * o da própria empresa (vem do me.companyId): a policy faqs_insert tem
 * WITH CHECK (company_id = app.company_id()) — o banco rejeita se não bater. Defesa em
 * profundidade: mesmo que o front mande errado, o RLS barra.
 *
 * <p>active/timestamps são do banco (active default true). Retorna a FAQ criada
 * (RETURNING via .select().single()).
 */
export async function createFaq(payload: {
  companyId: string
  question: string
  answer: string
}): Promise<Faq> {
  const supabase = createClient()
  const { data, error } = await supabase
    .from('faqs')
    .insert({
      company_id: payload.companyId,
      question: payload.question,
      answer: payload.answer,
    })
    .select('id, question, answer, active, created_at')
    .single()

  if (error) {
    throw error
  }

  return {
    id: data.id,
    question: data.question,
    answer: data.answer,
    active: data.active,
    createdAt: data.created_at,
  }
}

/**
 * Edita uma FAQ existente do tenant (camada 5.5). UPDATE via SDK + RLS: a policy
 * faqs_update tem USING + WITH CHECK (company_id = app.company_id()), então o tenant só
 * altera FAQ da própria empresa — não precisa passar company_id (a linha já é da empresa
 * dele e o RLS revalida). Audita automaticamente via trigger app.audit_trigger (fase-5.3).
 *
 * <p>Retorna a FAQ atualizada (RETURNING via .select().single()).
 */
export async function updateFaq(
  id: string,
  payload: { question: string; answer: string },
): Promise<Faq> {
  const supabase = createClient()
  const { data, error } = await supabase
    .from('faqs')
    .update({
      question: payload.question,
      answer: payload.answer,
    })
    .eq('id', id)
    .select('id, question, answer, active, created_at')
    .single()

  if (error) {
    throw error
  }

  return {
    id: data.id,
    question: data.question,
    answer: data.answer,
    active: data.active,
    createdAt: data.created_at,
  }
}
