import { createClient } from './client'

/**
 * Horário de funcionamento de um dia da semana. weekday 0..6 (0=domingo). opensAt/closesAt
 * no formato "HH:MM" (input time do HTML); null quando closed=true. id presente quando a
 * linha já existe no banco (vinda do getMyBusinessHours).
 *
 * <p>Coerência (espelha o CHECK chk_business_hours_shape do banco): closed=true ⇒ horas
 * null; closed=false ⇒ ambas presentes e opensAt ≠ closesAt. A UI valida via zod; o banco
 * revalida.
 */
export type BusinessHour = {
  id?: string
  weekday: number
  opensAt: string | null
  closesAt: string | null
  closed: boolean
}

/** Postgres time vem "HH:MM:SS"; o input time do HTML usa "HH:MM". Trunca para exibição. */
function toHHMM(t: string | null): string | null {
  return t ? t.slice(0, 5) : null
}

/**
 * Horários da empresa do tenant (SDK + RLS), ordenados por weekday asc. Retorna só as
 * linhas existentes (0 a 7) — a tela completa os dias faltantes com defaults (fechado).
 */
export async function getMyBusinessHours(): Promise<BusinessHour[]> {
  const supabase = createClient()
  const { data, error } = await supabase
    .from('business_hours')
    .select('id, weekday, opens_at, closes_at, closed')
    .order('weekday', { ascending: true })

  if (error) {
    throw error
  }

  return (data ?? []).map((r) => ({
    id: r.id,
    weekday: r.weekday,
    opensAt: toHHMM(r.opens_at),
    closesAt: toHHMM(r.closes_at),
    closed: r.closed,
  }))
}

/**
 * Salva o CONJUNTO inteiro de horários (estratégia delete-then-insert).
 *
 * <p>Por que delete-then-insert e não upsert: o UNIQUE é (company_id, weekday, opens_at)
 * — inclui opens_at, que muda quando o usuário edita o horário. Um upsert por
 * (company_id, weekday) não existe como constraint, então onConflict não se aplica
 * limpo. Como a tela sempre envia o conjunto fixo (7 dias), apagamos os do tenant e
 * reinserimos. RLS (delete + insert ambos = company_id) garante isolamento; WITH CHECK
 * do insert exige company_id explícito (defesa em profundidade).
 *
 * <p>Não é transação atômica (SDK não faz multi-statement): se o insert falhar após o
 * delete, a UI recarrega (getMyBusinessHours) e o usuário re-salva. Risco aceito no MVP.
 *
 * <p>closed=true ⇒ opens/closes null no payload; closed=false ⇒ ambos presentes (a UI
 * já validou via zod antes de chamar).
 */
export async function saveMyBusinessHours(
  companyId: string,
  rows: BusinessHour[],
): Promise<void> {
  const supabase = createClient()

  const { error: delError } = await supabase
    .from('business_hours')
    .delete()
    .eq('company_id', companyId)

  if (delError) {
    throw delError
  }

  const payload = rows.map((r) => ({
    company_id: companyId,
    weekday: r.weekday,
    closed: r.closed,
    opens_at: r.closed ? null : r.opensAt,
    closes_at: r.closed ? null : r.closesAt,
  }))

  const { error: insError } = await supabase.from('business_hours').insert(payload)

  if (insError) {
    throw insError
  }
}
